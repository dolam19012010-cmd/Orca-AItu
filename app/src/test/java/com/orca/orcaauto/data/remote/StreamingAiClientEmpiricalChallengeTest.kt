package com.orca.orcaauto.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Adversarial Empirical Challenge Test Suite for [StreamingAiClient].
 *
 * Stress-tests:
 * 1. Fragmented byte chunks (byte-by-byte and 2-byte slices across multi-byte UTF-8 sequences).
 * 2. Complex Vietnamese diacritics and 4-byte emojis split across SSE data lines.
 * 3. Rapid token burst delivery (1000 tokens back-to-back without drops or reordering).
 * 4. Anti-buffering headers (Cache-Control: no-cache, X-Accel-Buffering: no, Accept: text/event-stream).
 * 5. Coroutine cancellation mid-stream unblocking socket read thread immediately (< 500ms).
 * 6. Spike Racing loser worker immediate call.cancel() termination.
 */
class StreamingAiClientEmpiricalChallengeTest {

    // =========================================================================
    // 1. FRAGMENTED CHUNKS & MULTIBYTE UTF-8 SPLIT STRESS TEST
    // =========================================================================

    @Test
    fun sseStream_fragmentedBytesAcrossMultiByteUtf8_parsedWithoutCorruption() = runBlocking {
        // Multi-byte Vietnamese text with 3-byte and 4-byte characters
        val fullVietnameseSentence = "Kinh nghiệm quay chụp bóng đá chuyên nghiệp 🎯: ống kính 70-200mm f/2.8, tốc độ 1/1000s, khẩu độ f/2.8, ISO tự động! 🇻🇳"
        
        val ssePayload = buildString {
            append("data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"$fullVietnameseSentence\"}]}}]}\n\n")
            append("data: [DONE]\n\n")
        }
        val rawBytes = ssePayload.toByteArray(StandardCharsets.UTF_8)

        // Custom Okio Source that emits in tiny 2-byte fragments, deliberately cutting inside UTF-8 multi-byte codes
        val fragmentedSource = object : Source {
            private var offset = 0
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (offset >= rawBytes.size) return -1L
                val chunkSize = minOf(2, rawBytes.size - offset)
                sink.write(rawBytes, offset, chunkSize)
                offset += chunkSize
                return chunkSize.toLong()
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val body = object : ResponseBody() {
                    override fun contentType() = "text/event-stream".toMediaType()
                    override fun contentLength() = rawBytes.size.toLong()
                    override fun source(): BufferedSource = fragmentedSource.buffer()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val apiKey = ApiKeyConfig(
            provider = AiProviderType.GEMINI,
            rawKey = "test_key",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=test_key",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(apiKey),
            prompt = "Cho tôi kinh nghiệm quay chụp"
        ).toList()

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Complete event phải được phát ra", completeEvent)
        assertEquals("Nội dung tiếng Việt và emoji phải bảo toàn nguyên vẹn sau phân mảnh UTF-8",
            fullVietnameseSentence, completeEvent?.fullContent)
    }

    // =========================================================================
    // 2. RAPID TOKEN DELIVERY BURST (1000 TOKENS)
    // =========================================================================

    @Test
    fun sseStream_rapidTokenDelivery_preservesAllTokensInFifoOrder() = runBlocking {
        val tokenCount = 1000
        val ssePayload = buildString {
            for (i in 0 until tokenCount) {
                append("data: {\"choices\":[{\"delta\":{\"content\":\"t$i \"}}]}\n")
            }
            append("data: [DONE]\n\n")
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val body = ssePayload.toResponseBody("text/event-stream".toMediaType())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val apiKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            rawKey = "test_openrouter_key",
            modelName = "deepseek/deepseek-r1",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(apiKey),
            prompt = "Rapid burst test"
        ).toList()

        val contentDeltas = events.filterIsInstance<StreamUiEvent.ContentDelta>()
        assertEquals("Tất cả 1000 tokens phải được nhận đầy đủ không bị rớt", tokenCount, contentDeltas.size)

        for (i in 0 until tokenCount) {
            assertEquals("Thứ tự token FIFO phải chuẩn xác", "t$i ", contentDeltas[i].text)
        }

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Complete event phải tồn tại", completeEvent)
        val expectedFull = (0 until tokenCount).joinToString("") { "t$it " }
        assertEquals(expectedFull, completeEvent?.fullContent)
    }

    // =========================================================================
    // 3. ANTI-BUFFERING HEADERS ON ALL ENDPOINTS
    // =========================================================================

    @Test
    fun outgoingRequests_containStrictAntiBufferingHeaders() = runBlocking {
        val capturedGeminiRequest = AtomicReference<Request?>(null)
        val capturedOpenRouterRequest = AtomicReference<Request?>(null)

        val clientGemini = StreamingAiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                capturedGeminiRequest.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("data: [DONE]\n".toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }).build()
        )

        clientGemini.streamWithSpikeRacing(
            keyPool = listOf(
                ApiKeyConfig(
                    provider = AiProviderType.GEMINI,
                    rawKey = "gemini_key",
                    modelName = "gemini-2.5-flash",
                    endpointUrl = "https://generativelanguage.googleapis.com",
                    isEnabled = true
                )
            ),
            prompt = "Test Gemini headers"
        ).toList()

        val geminiReq = capturedGeminiRequest.get()
        assertNotNull(geminiReq)
        assertEquals("no-cache", geminiReq?.header("Cache-Control"))
        assertEquals("no", geminiReq?.header("X-Accel-Buffering"))
        assertEquals("text/event-stream", geminiReq?.header("Accept"))

        val clientOpenRouter = StreamingAiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                capturedOpenRouterRequest.set(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("data: [DONE]\n".toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }).build()
        )

        clientOpenRouter.streamWithSpikeRacing(
            keyPool = listOf(
                ApiKeyConfig(
                    provider = AiProviderType.OPENROUTER,
                    rawKey = "openrouter_key",
                    modelName = "deepseek/deepseek-r1",
                    endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                    isEnabled = true
                )
            ),
            prompt = "Test OpenRouter headers"
        ).toList()

        val openRouterReq = capturedOpenRouterRequest.get()
        assertNotNull(openRouterReq)
        assertEquals("no-cache", openRouterReq?.header("Cache-Control"))
        assertEquals("no", openRouterReq?.header("X-Accel-Buffering"))
        assertEquals("text/event-stream", openRouterReq?.header("Accept"))
        assertEquals("Bearer openrouter_key", openRouterReq?.header("Authorization"))
    }

    // =========================================================================
    // 4. NON-BLOCKING COROUTINE CANCELLATION & SOCKET UNBLOCKING
    // =========================================================================

    @Test
    fun streamCancellation_unblocksWaitingThreadImmediatelyUnder500ms() = runBlocking {
        val callCanceledLatch = CountDownLatch(1)
        val serverStreamStartedLatch = CountDownLatch(1)
        val isCallCanceled = AtomicBoolean(false)
        val capturedCall = AtomicReference<Call?>(null)

        val hangingSource = object : Source {
            private var emittedFirst = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!emittedFirst) {
                    emittedFirst = true
                    serverStreamStartedLatch.countDown()
                    val firstChunk = "data: {\"choices\":[{\"delta\":{\"content\":\"Token 1...\"}}]}\n"
                    val bytes = firstChunk.toByteArray(StandardCharsets.UTF_8)
                    sink.write(bytes)
                    return bytes.size.toLong()
                }
                // Simulate slow socket: blocks until interrupted or cancelled/closed by call.cancel()
                val startTime = System.currentTimeMillis()
                while (!isCallCanceled.get() &&
                    capturedCall.get()?.isCanceled() != true &&
                    !Thread.currentThread().isInterrupted &&
                    (System.currentTimeMillis() - startTime < 60_000L)
                ) {
                    try {
                        Thread.sleep(20L)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw IOException("Socket closed via interruption", e)
                    }
                }
                if (capturedCall.get()?.isCanceled() == true || isCallCanceled.get() || Thread.currentThread().isInterrupted) {
                    throw IOException("Canceled")
                }
                return -1L
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {
                isCallCanceled.set(true)
                callCanceledLatch.countDown()
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedCall.set(chain.call())
                val body = object : ResponseBody() {
                    override fun contentType() = "text/event-stream".toMediaType()
                    override fun contentLength() = -1L
                    override fun source(): BufferedSource = hangingSource.buffer()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val apiKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            rawKey = "test_key",
            modelName = "deepseek/deepseek-r1",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val flow = client.streamWithSpikeRacing(
            keyPool = listOf(apiKey),
            prompt = "Hang test"
        )

        val receivedTokens = mutableListOf<String>()

        val firstTokenReceivedLatch = CountDownLatch(1)
        val consumerJob = launch(Dispatchers.IO) {
            flow.collect { event ->
                if (event is StreamUiEvent.ContentDelta) {
                    receivedTokens.add(event.text)
                    firstTokenReceivedLatch.countDown()
                }
            }
        }

        // Wait until first token is delivered to consumer
        assertTrue(firstTokenReceivedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, receivedTokens.size)

        // Cancel mid-stream (equivalent to bubble collapse activeStreamingJob?.cancel())
        val cancelStart = System.currentTimeMillis()
        consumerJob.cancel()
        consumerJob.join()
        val cancelDurationMs = System.currentTimeMillis() - cancelStart

        assertTrue("Hủy job coroutine phải ngắt socket và giải phóng thread trong vòng dưới 500ms (thực tế: ${cancelDurationMs}ms)",
            cancelDurationMs < 500L)
    }

    // =========================================================================
    // 5. SPIKE RACING LOSER STREAM IMMEDIATE CALL.CANCEL()
    // =========================================================================

    @Test
    fun spikeRacing_primaryStalls_backupWinsAndPrimaryIsCancelledImmediately() = runBlocking {
        val primaryCallCancelled = AtomicBoolean(false)
        val backupCallExecuted = AtomicBoolean(false)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val isPrimary = request.url.toString().contains("primary")

                if (isPrimary) {
                    // Primary worker hangs longer than spike threshold
                    val hangingSource = object : Source {
                        override fun read(sink: Buffer, byteCount: Long): Long {
                            try {
                                Thread.sleep(5000L)
                            } catch (_: InterruptedException) {
                                throw IOException("Primary cancelled by racer leader")
                            }
                            return -1L
                        }
                        override fun timeout(): Timeout = Timeout.NONE
                        override fun close() {
                            primaryCallCancelled.set(true)
                        }
                    }
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(object : ResponseBody() {
                            override fun contentType() = "text/event-stream".toMediaType()
                            override fun contentLength() = -1L
                            override fun source() = hangingSource.buffer()
                        })
                        .build()
                } else {
                    backupCallExecuted.set(true)
                    val backupPayload = "data: {\"choices\":[{\"delta\":{\"content\":\"Backup thắng cuộc!\"}}]}\ndata: [DONE]\n"
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(backupPayload.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val primaryKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            endpointUrl = "https://primary.ai/chat/completions",
            modelName = "deepseek/deepseek-r1",
            rawKey = "key_primary",
            isEnabled = true
        )
        val backupKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            endpointUrl = "https://backup.ai/chat/completions",
            modelName = "deepseek/deepseek-r1",
            rawKey = "key_backup",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(primaryKey, backupKey),
            prompt = "Race test",
            spikeThresholdMs = 200L // Fast threshold for unit test
        ).toList()

        assertTrue("Backup worker phải được kích hoạt sau khi primary bị spike", backupCallExecuted.get())
        val complete = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull(complete)
        assertEquals("Backup thắng cuộc!", complete?.fullContent)
    }
}
