package com.orca.orcaauto.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class StreamingAiClientTest {

    @Test
    fun geminiStream_emitsTokensAndIncludesAntiBufferingHeaders() = runBlocking {
        val capturedRequest = AtomicReference<Request?>(null)

        val geminiSse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Suy nghĩ bước 1...","thought":true}]}}]}
            data: {"candidates":[{"content":{"parts":[{"text":"Xin chào! "}]}}]}
            data: {"candidates":[{"content":{"parts":[{"text":"Tôi là trợ lý AI."}]}}]}
            data: [DONE]
        """.trimIndent()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedRequest.set(chain.request())
                val body = geminiSse.toResponseBody("text/event-stream".toMediaType())
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
            rawKey = "AIzaSyTest_Key_123",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSyTest_Key_123",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(apiKey),
            prompt = "Xin chào AI"
        ).toList()

        // 1. Verify anti-buffering headers in outgoing request
        val request = capturedRequest.get()
        assertNotNull("Request phải được thực thi", request)
        assertEquals("no-cache", request?.header("Cache-Control"))
        assertEquals("no", request?.header("X-Accel-Buffering"))
        assertEquals("text/event-stream", request?.header("Accept"))

        // 2. Verify incremental token-by-token emission
        assertTrue(events.any { it is StreamUiEvent.Started })
        val thoughts = events.filterIsInstance<StreamUiEvent.ThoughtDelta>()
        assertEquals(1, thoughts.size)
        assertEquals("Suy nghĩ bước 1...", thoughts[0].text)

        val contents = events.filterIsInstance<StreamUiEvent.ContentDelta>()
        assertEquals(2, contents.size)
        assertEquals("Xin chào! ", contents[0].text)
        assertEquals("Tôi là trợ lý AI.", contents[1].text)

        val complete = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull(complete)
        assertEquals("Xin chào! Tôi là trợ lý AI.", complete?.fullContent)
        assertEquals("Suy nghĩ bước 1...", complete?.fullThought)
    }

    @Test
    fun openAiCompatibleStream_emitsReasoningAndContentWithAntiBufferingHeaders() = runBlocking {
        val capturedRequest = AtomicReference<Request?>(null)

        val openRouterSse = """
            data: {"choices":[{"delta":{"reasoning":"Phân tích dữ liệu..."}}]}
            data: {"choices":[{"delta":{"content":"Dưới đây là "}}]}
            data: {"choices":[{"delta":{"content":"kết quả tìm kiếm."}}]}
            data: [DONE]
        """.trimIndent()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                capturedRequest.set(chain.request())
                val body = openRouterSse.toResponseBody("text/event-stream".toMediaType())
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
            rawKey = "sk-or-v1-testkey123",
            modelName = "deepseek/deepseek-r1",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(apiKey),
            prompt = "Tìm kiếm thông tin"
        ).toList()

        // 1. Verify anti-buffering headers
        val request = capturedRequest.get()
        assertNotNull("Request phải được thực thi", request)
        assertEquals("no-cache", request?.header("Cache-Control"))
        assertEquals("no", request?.header("X-Accel-Buffering"))
        assertEquals("text/event-stream", request?.header("Accept"))
        assertEquals("Bearer sk-or-v1-testkey123", request?.header("Authorization"))

        // 2. Verify incremental token-by-token emission
        val thoughts = events.filterIsInstance<StreamUiEvent.ThoughtDelta>()
        assertEquals(1, thoughts.size)
        assertEquals("Phân tích dữ liệu...", thoughts[0].text)

        val contents = events.filterIsInstance<StreamUiEvent.ContentDelta>()
        assertEquals(2, contents.size)
        assertEquals("Dưới đây là ", contents[0].text)
        assertEquals("kết quả tìm kiếm.", contents[1].text)

        val complete = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull(complete)
        assertEquals("Dưới đây là kết quả tìm kiếm.", complete?.fullContent)
        assertEquals("Phân tích dữ liệu...", complete?.fullThought)
    }

    @Test
    fun stallWatchdog_emitsSpikeDetectedWithInFlightResetTrue() = runBlocking {
        val callCount = AtomicInteger(0)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val call = chain.call()
                val reqNum = callCount.incrementAndGet()

                if (reqNum == 1) {
                    // Leader worker: emit partial content, then stall until cancelled
                    val firstChunk = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Partial leader output... \"}]}}]}\n\n"
                    val bytes = firstChunk.toByteArray(Charsets.UTF_8)

                    val stallingStream = object : InputStream() {
                        private var offset = 0

                        override fun read(): Int {
                            if (offset < bytes.size) {
                                return bytes[offset++].toInt() and 0xFF
                            }
                            // Stall until watchdog cancels the call
                            while (!call.isCanceled()) {
                                try {
                                    Thread.sleep(40)
                                } catch (e: InterruptedException) {
                                    throw IOException("Worker 1 call interrupted")
                                }
                            }
                            throw IOException("Worker 1 cancelled by stall watchdog")
                        }

                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            if (offset < bytes.size) {
                                val toRead = Math.min(len, bytes.size - offset)
                                System.arraycopy(bytes, offset, b, off, toRead)
                                offset += toRead
                                return toRead
                            }
                            while (!call.isCanceled()) {
                                try {
                                    Thread.sleep(40)
                                } catch (e: InterruptedException) {
                                    throw IOException("Worker 1 call interrupted")
                                }
                            }
                            throw IOException("Worker 1 cancelled by stall watchdog")
                        }
                    }

                    val body = object : ResponseBody() {
                        override fun contentType() = "text/event-stream".toMediaType()
                        override fun contentLength() = -1L
                        override fun source() = stallingStream.source().buffer()
                    }

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body)
                        .build()
                } else {
                    // Backup worker launched after watchdog stall reset: emit full clean response
                    val backupSse = """
                        data: {"candidates":[{"content":{"parts":[{"text":"Clean answer from backup model."}]}}]}
                        data: [DONE]
                    """.trimIndent()

                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(backupSse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)

        val leaderKey = ApiKeyConfig(
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyTest_Leader",
            modelName = "gemini-3.5-flash-lite",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=AIzaSyTest_Leader",
            isEnabled = true
        )
        val backupKey = ApiKeyConfig(
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyTest_Backup",
            modelName = "gemini-2.0-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyTest_Backup",
            isEnabled = true
        )

        val events = client.streamWithSpikeRacing(
            keyPool = listOf(leaderKey, backupKey),
            prompt = "Kiểm tra cơ chế stall watchdog",
            stallThresholdMs = 400L,
            spikeThresholdMs = 10_000L
        ).toList()

        // 1. Verify SpikeDetected was emitted with isInFlightReset == true
        val spikes = events.filterIsInstance<StreamUiEvent.SpikeDetected>()
        assertEquals(1, spikes.size)
        val spike = spikes.first()
        assertTrue("isInFlightReset phải là true khi leader bị stall", spike.isInFlightReset)
        assertEquals(backupKey.provider.displayName, spike.backupProvider)
        assertEquals(backupKey.modelName, spike.backupModel)

        // 2. Verify Complete event only contains content from the backup worker (in-flight accumulators were reset)
        val complete = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull(complete)
        assertEquals("Clean answer from backup model.", complete?.fullContent)
        assertEquals(backupKey.rawKey, complete?.usedKey?.rawKey)
    }

    @Test
    fun buildGeminiRequest_configuresThinkingBudgetAndIncludeThoughts() {
        val client = StreamingAiClient()
        val geminiKey = ApiKeyConfig(
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyTest_Key",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSyTest_Key",
            isEnabled = true
        )

        // 1. FAST: thinkingBudget = 0, includeThoughts = false
        val reqFast = invokeBuildGeminiRequest(client, geminiKey, thinkingLevel = AssistantThinkingLevel.FAST)
        val jsonFast = extractRequestBodyJson(reqFast)
        val fastThinking = jsonFast.getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
        assertEquals(0, fastThinking.get("thinkingBudget").asInt)
        assertFalse(fastThinking.get("includeThoughts").asBoolean)

        // 2. BALANCED: thinkingBudget = 1024, includeThoughts = true
        val reqBalanced = invokeBuildGeminiRequest(client, geminiKey, thinkingLevel = AssistantThinkingLevel.BALANCED)
        val jsonBalanced = extractRequestBodyJson(reqBalanced)
        val balancedThinking = jsonBalanced.getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
        assertEquals(1024, balancedThinking.get("thinkingBudget").asInt)
        assertTrue(balancedThinking.get("includeThoughts").asBoolean)

        // 3. DEEP: thinkingBudget = 4096, includeThoughts = true
        val reqDeep = invokeBuildGeminiRequest(client, geminiKey, thinkingLevel = AssistantThinkingLevel.DEEP)
        val jsonDeep = extractRequestBodyJson(reqDeep)
        val deepThinking = jsonDeep.getAsJsonObject("generationConfig").getAsJsonObject("thinkingConfig")
        assertEquals(4096, deepThinking.get("thinkingBudget").asInt)
        assertTrue(deepThinking.get("includeThoughts").asBoolean)
    }

    @Test
    fun buildOpenRouterRequest_configuresReasoningEffortOnlyForOpenRouter() {
        val client = StreamingAiClient()

        val openRouterKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            rawKey = "sk-or-v1-test",
            modelName = "deepseek/deepseek-r1",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val groqKey = ApiKeyConfig(
            provider = AiProviderType.GROQ,
            rawKey = "gsk_test123",
            modelName = "llama-3.1-8b-instant",
            endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
            isEnabled = true
        )

        // 1. OpenRouter with FAST -> effort = "low"
        val reqFast = invokeBuildOpenAiCompatibleRequest(client, openRouterKey, thinkingLevel = AssistantThinkingLevel.FAST)
        val jsonFast = extractRequestBodyJson(reqFast)
        assertTrue(jsonFast.has("reasoning"))
        assertEquals("low", jsonFast.getAsJsonObject("reasoning").get("effort").asString)
        assertEquals("https://github.com/orcaauto", reqFast.header("HTTP-Referer"))

        // 2. OpenRouter with BALANCED -> effort = "medium"
        val reqBalanced = invokeBuildOpenAiCompatibleRequest(client, openRouterKey, thinkingLevel = AssistantThinkingLevel.BALANCED)
        val jsonBalanced = extractRequestBodyJson(reqBalanced)
        assertTrue(jsonBalanced.has("reasoning"))
        assertEquals("medium", jsonBalanced.getAsJsonObject("reasoning").get("effort").asString)

        // 3. OpenRouter with DEEP -> effort = "high"
        val reqDeep = invokeBuildOpenAiCompatibleRequest(client, openRouterKey, thinkingLevel = AssistantThinkingLevel.DEEP)
        val jsonDeep = extractRequestBodyJson(reqDeep)
        assertTrue(jsonDeep.has("reasoning"))
        assertEquals("high", jsonDeep.getAsJsonObject("reasoning").get("effort").asString)

        // 4. Groq: must NOT contain reasoning object regardless of thinking level
        val reqGroq = invokeBuildOpenAiCompatibleRequest(client, groqKey, thinkingLevel = AssistantThinkingLevel.DEEP)
        val jsonGroq = extractRequestBodyJson(reqGroq)
        assertFalse("Groq request tuyệt đối không chứa reasoning object", jsonGroq.has("reasoning"))
        assertNull(reqGroq.header("HTTP-Referer"))
    }

    @Test
    fun buildOpenAiCompatibleRequest_mergesSystemInstructionIntoUserPrompt() {
        val client = StreamingAiClient()
        val openRouterKey = ApiKeyConfig(
            provider = AiProviderType.OPENROUTER,
            rawKey = "sk-or-test1234",
            modelName = "nvidia/nemotron-4-340b-instruct",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val customSystemInstruction = "Bạn là trợ lý điều phối ca trực chuyên nghiệp của OrcaAuto."
        val userPrompt = "Tóm tắt các nhiệm vụ cần thực hiện hôm nay"

        val request = invokeBuildOpenAiCompatibleRequest(
            client = client,
            config = openRouterKey,
            prompt = userPrompt,
            systemInstruction = customSystemInstruction
        )

        val json = extractRequestBodyJson(request)
        val messages = json.getAsJsonArray("messages")

        // Must have exactly 1 message (user role) with system instruction merged at the beginning
        assertEquals("Phải có duy nhất 1 tin nhắn user với system instruction được gộp", 1, messages.size())
        val msgObj = messages[0].asJsonObject
        assertEquals("user", msgObj.get("role").asString)
        val content = msgObj.get("content").asString
        assertTrue("Nội dung tin nhắn phải bắt đầu bằng system instruction", content.startsWith(customSystemInstruction))
        assertTrue("Nội dung tin nhắn phải chứa user prompt", content.contains(userPrompt))
    }

    @Test
    fun buildOpenAiCompatibleRequest_withBlankSystemInstruction_prependsDefaultInstructionIntoUserPrompt() {
        val client = StreamingAiClient()
        val deepseekKey = ApiKeyConfig(
            provider = AiProviderType.DEEPSEEK,
            rawKey = "sk-deepseek-test1234",
            modelName = "deepseek-chat",
            endpointUrl = "https://api.deepseek.com/chat/completions",
            isEnabled = true
        )

        val userPrompt = "Kiểm tra danh sách phòng trực ca đêm"

        val request = invokeBuildOpenAiCompatibleRequest(
            client = client,
            config = deepseekKey,
            prompt = userPrompt,
            systemInstruction = "   " // Blank/whitespace system instruction
        )

        val json = extractRequestBodyJson(request)
        val messages = json.getAsJsonArray("messages")

        assertEquals(1, messages.size())
        val msgObj = messages[0].asJsonObject
        assertEquals("user", msgObj.get("role").asString)
        val content = msgObj.get("content").asString

        val defaultPrompt = "Bạn là một Trợ lý Điều hành AI Chuyên nghiệp."
        assertTrue("Khi system instruction trống, phải dùng fallback mặc định", content.startsWith(defaultPrompt))
        assertTrue("Nội dung tin nhắn phải chứa user prompt", content.contains(userPrompt))
    }

    @Test
    fun buildOpenAiCompatibleRequest_forAllCompatibleProviders_neverOutputsSystemRoleMessage() {
        val client = StreamingAiClient()
        val compatibleProviders = listOf(
            AiProviderType.OPENROUTER to "https://openrouter.ai/api/v1/chat/completions",
            AiProviderType.GROQ to "https://api.groq.com/openai/v1/chat/completions",
            AiProviderType.DEEPSEEK to "https://api.deepseek.com/chat/completions",
            AiProviderType.NVIDIA to "https://integrate.api.nvidia.com/v1/chat/completions"
        )

        val sysPrompt = "Chỉ thị điều phối vận hành hệ thống"
        val userPrompt = "Lập báo cáo ca trực"

        for ((provider, url) in compatibleProviders) {
            val keyConfig = ApiKeyConfig(
                provider = provider,
                rawKey = "test-key-for-${provider.name}",
                modelName = provider.defaultModel,
                endpointUrl = url,
                isEnabled = true
            )

            val request = invokeBuildOpenAiCompatibleRequest(
                client = client,
                config = keyConfig,
                prompt = userPrompt,
                systemInstruction = sysPrompt
            )

            val json = extractRequestBodyJson(request)
            val messages = json.getAsJsonArray("messages")

            assertEquals(
                "Provider ${provider.displayName} phải có duy nhất 1 message (user role)",
                1,
                messages.size()
            )

            for (i in 0 until messages.size()) {
                val role = messages[i].asJsonObject.get("role").asString
                assertFalse(
                    "Provider ${provider.displayName} tuyệt đối không gửi riêng message có role=system",
                    role.equals("system", ignoreCase = true)
                )
            }

            val userMsg = messages[0].asJsonObject
            assertEquals("user", userMsg.get("role").asString)
            val content = userMsg.get("content").asString
            assertTrue(
                "Nội dung user message cho ${provider.displayName} phải bắt đầu bằng system prompt",
                content.startsWith(sysPrompt)
            )
            assertTrue(
                "Nội dung user message cho ${provider.displayName} phải chứa user prompt",
                content.contains(userPrompt)
            )
        }
    }

    // =========================================================================
    // REFLECTION HELPERS
    // =========================================================================

    private fun invokeBuildGeminiRequest(
        client: StreamingAiClient,
        config: ApiKeyConfig,
        prompt: String = "Test prompt",
        systemInstruction: String = "",
        thinkingLevel: AssistantThinkingLevel = AssistantThinkingLevel.BALANCED
    ): Request {
        val method = StreamingAiClient::class.java.getDeclaredMethod(
            "buildGeminiRequest",
            ApiKeyConfig::class.java,
            String::class.java,
            String::class.java,
            AssistantThinkingLevel::class.java
        )
        method.isAccessible = true
        return method.invoke(client, config, prompt, systemInstruction, thinkingLevel) as Request
    }

    private fun invokeBuildOpenAiCompatibleRequest(
        client: StreamingAiClient,
        config: ApiKeyConfig,
        prompt: String = "Test prompt",
        systemInstruction: String = "",
        thinkingLevel: AssistantThinkingLevel = AssistantThinkingLevel.BALANCED
    ): Request {
        val method = StreamingAiClient::class.java.getDeclaredMethod(
            "buildOpenAiCompatibleRequest",
            ApiKeyConfig::class.java,
            String::class.java,
            String::class.java,
            AssistantThinkingLevel::class.java
        )
        method.isAccessible = true
        return method.invoke(client, config, prompt, systemInstruction, thinkingLevel) as Request
    }

    private fun extractRequestBodyJson(request: Request): JsonObject {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val bodyStr = buffer.readUtf8()
        return JsonParser.parseString(bodyStr).asJsonObject
    }

    // =========================================================================
    // 429 RATE LIMIT DETECTION & FAILOVER TESTS (ITEM 3)
    // =========================================================================

    @Test
    fun streamWithSpikeRacing_whenPrimaryReturns429AndBackupKeyExists_failsOverImmediatelyToBackupKey() = runBlocking {
        val key1 = ApiKeyConfig(
            id = "key_1",
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyPrimary_429",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSyPrimary_429",
            isEnabled = true
        )
        val key2 = ApiKeyConfig(
            id = "key_2",
            provider = AiProviderType.GROQ,
            rawKey = "gsk_backup_ok",
            endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
            modelName = "llama-3.3-70b-versatile",
            isEnabled = true
        )

        val requestsReceived = mutableListOf<String>()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("AIzaSyPrimary_429")) {
                    requestsReceived.add("primary_429")
                    val errJson = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded"}}"""
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body(errJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    requestsReceived.add("backup_ok")
                    val openAiSse = """
                        data: {"choices":[{"delta":{"content":"Câu trả lời từ backup"}}]}
                        data: [DONE]
                    """.trimIndent()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(openAiSse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val events = client.streamWithSpikeRacing(
            keyPool = listOf(key1, key2),
            prompt = "Test failover"
        ).toList()

        val spikeEvent = events.filterIsInstance<StreamUiEvent.SpikeDetected>().firstOrNull()
        assertNotNull("Phải phát hiện và emit sự kiện SpikeDetected khi key 1 bị 429", spikeEvent)
        assertTrue("SpikeDetected phải có cờ isInFlightReset = true", spikeEvent?.isInFlightReset == true)
        assertEquals("Groq Cloud (LPU)", spikeEvent?.backupProvider)

        val deltas = events.filterIsInstance<StreamUiEvent.ContentDelta>()
        assertTrue("Deltas phải nhận được từ backup key", deltas.any { it.text.contains("Câu trả lời từ backup") })

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Stream phải hoàn thành thành công", completeEvent)
        assertEquals("key_2", completeEvent?.usedKey?.id)
        assertEquals("Câu trả lời từ backup", completeEvent?.fullContent)
    }

    @Test
    fun streamWithSpikeRacing_whenSingleKeyReturns429_cancelsImmediatelyAndZeroRacingRetries() = runBlocking {
        val singleKey = ApiKeyConfig(
            id = "single_key",
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSy_Only_One_Key",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSy_Only_One_Key",
            isEnabled = true
        )

        val requestCount = AtomicInteger(0)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                requestCount.incrementAndGet()
                val errJson = """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED","message":"Quota exceeded for key"}}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body(errJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val events = client.streamWithSpikeRacing(
            keyPool = listOf(singleKey),
            prompt = "Test single key 429"
        ).toList()

        val errorEvent = events.filterIsInstance<StreamUiEvent.Error>().firstOrNull()
        assertNotNull("Phải emit StreamUiEvent.Error", errorEvent)
        assertTrue(
            "Lỗi phải là RateLimitException hoặc có chứa thông tin hạn mức",
            errorEvent?.throwable is RateLimitException || RateLimitDetector.isRateLimit(errorEvent?.throwable)
        )
        assertEquals("Chỉ được gửi duy nhất 1 request, tuyệt đối không được khuếch đại bão lỗi", 1, requestCount.get())
    }

    @Test
    fun streamWithSpikeRacing_whenPrimaryReturns503AndBackupKeyExists_failsOverImmediatelyToBackupKey() = runBlocking {
        val key1 = ApiKeyConfig(
            id = "key_1",
            provider = AiProviderType.OPENROUTER,
            rawKey = "sk-or-primary-503",
            modelName = "openrouter/free",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )
        val key2 = ApiKeyConfig(
            id = "key_2",
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyBackup_Gemini",
            modelName = "gemini-2.5-flash",
            endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=AIzaSyBackup_Gemini",
            isEnabled = true
        )

        val requestCountKey1 = AtomicInteger(0)
        val requestCountKey2 = AtomicInteger(0)

        val geminiBackupSse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Phản hồi từ Gemini dự phòng"}]}}]}
            data: [DONE]
        """.trimIndent()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val auth = chain.request().header("Authorization").orEmpty()
                val url = chain.request().url.toString()

                if (auth.contains("sk-or-primary-503")) {
                    requestCountKey1.incrementAndGet()
                    val errJson = """{"error":{"code":503,"message":"No available provider for this model"}}"""
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Service Unavailable")
                        .body(errJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    requestCountKey2.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(geminiBackupSse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val events = client.streamWithSpikeRacing(
            keyPool = listOf(key1, key2),
            prompt = "Test 503 failover"
        ).toList()

        assertEquals("Key 1 chỉ gửi duy nhất 1 request khi bị 503, không được lặp lại chính nó", 1, requestCountKey1.get())
        assertEquals("Key 2 phải được kích hoạt tự động", 1, requestCountKey2.get())

        val spikeEvent = events.filterIsInstance<StreamUiEvent.SpikeDetected>().firstOrNull()
        assertNotNull("Phải emit SpikeDetected khi failover sang Key 2", spikeEvent)
        assertEquals("Google Gemini", spikeEvent?.backupProvider)

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Stream phải hoàn thành qua backup key", completeEvent)
        assertEquals("key_2", completeEvent?.usedKey?.id)
        assertEquals("Phản hồi từ Gemini dự phòng", completeEvent?.fullContent)
    }

    @Test
    fun rateLimitDetector_correctlyIdentifiesHttp429AndQuotaKeywords() {
        assertTrue(RateLimitDetector.isRateLimit(429))
        assertTrue(RateLimitDetector.isRateLimit(200, "error: RESOURCE_EXHAUSTED"))
        assertTrue(RateLimitDetector.isRateLimit(500, "Quota exceeded for project"))
        assertTrue(RateLimitDetector.isRateLimit(500, "Rate limit reached"))
        assertTrue(RateLimitDetector.isRateLimit(500, "Too many requests"))

        assertFalse(RateLimitDetector.isRateLimit(200, "OK"))
        assertFalse(RateLimitDetector.isRateLimit(500, "Internal Server Error"))
        assertFalse(RateLimitDetector.isRateLimit(404, "Model not found"))

        val ex429 = RateLimitException("HTTP 429: Too Many Requests")
        val nestedEx = IOException("Failed call", ex429)
        assertTrue(RateLimitDetector.isRateLimit(ex429))
        assertTrue(RateLimitDetector.isRateLimit(nestedEx))
        assertFalse(RateLimitDetector.isRateLimit(IOException("Socket closed")))
    }

    @Test
    fun streamWithSpikeRacing_whenGeminiReturns400MentioningThinking_retriesOnceWithoutThinkingConfig() = runBlocking {
        val key = ApiKeyConfig(
            id = "key_gemini_thinking_400",
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyTestThinkingFallback",
            modelName = "gemini-2.0-flash",
            endpointUrl = "",
            isEnabled = true
        )

        val requestBodies = mutableListOf<String>()
        val geminiSse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Đã trả lời sau khi bỏ thinkingConfig"}]}}]}
            data: [DONE]
        """.trimIndent()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val bodyBuffer = Buffer()
                req.body?.writeTo(bodyBuffer)
                requestBodies.add(bodyBuffer.readUtf8())

                if (requestBodies.size == 1) {
                    val errJson = """{"error":{"code":400,"message":"Unknown name \"thinkingBudget\" at generation_config.thinking_config"}}"""
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(400)
                        .message("Bad Request")
                        .body(errJson.toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(geminiSse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val events = client.streamWithSpikeRacing(
            keyPool = listOf(key),
            prompt = "Test Gemini 400 thinking fallback",
            thinkingLevel = AssistantThinkingLevel.DEEP
        ).toList()

        assertEquals("Phải thử đúng 2 lần trên cùng 1 key (1 lần có thinkingConfig, 1 lần bỏ thinkingConfig)", 2, requestBodies.size)
        assertTrue("Lần gọi đầu phải có thinkingConfig", requestBodies[0].contains("thinkingConfig"))
        assertFalse("Lần retry phải bỏ thinkingConfig", requestBodies[1].contains("thinkingConfig"))

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Phải hoàn thành sau retry 400 thinkingConfig", completeEvent)
        assertEquals("Đã trả lời sau khi bỏ thinkingConfig", completeEvent?.fullContent)
    }

    @Test
    fun streamWithSpikeRacing_whenPrimaryEmitsOnlyThoughtsThenEnds_failsOverToBackupKey() = runBlocking {
        val key1 = ApiKeyConfig(
            id = "key_thought_only",
            provider = AiProviderType.GEMINI,
            rawKey = "AIzaSyPrimaryThoughtOnly",
            modelName = "gemini-2.5-flash",
            endpointUrl = "",
            isEnabled = true
        )
        val key2 = ApiKeyConfig(
            id = "key_backup_visible",
            provider = AiProviderType.OPENROUTER,
            rawKey = "sk-or-backup-visible",
            modelName = "openrouter/free",
            endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
            isEnabled = true
        )

        val thoughtOnlySse = """
            data: {"candidates":[{"content":{"parts":[{"thought":true,"text":"Đang suy luận nội bộ..."}]}}]}
            data: [DONE]
        """.trimIndent()
        val backupSse = """
            data: {"choices":[{"delta":{"content":"Câu trả lời chính thức từ key dự phòng"}}]}
            data: [DONE]
        """.trimIndent()

        val countKey1 = AtomicInteger(0)
        val countKey2 = AtomicInteger(0)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("AIzaSyPrimaryThoughtOnly")) {
                    countKey1.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(thoughtOnlySse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                } else {
                    countKey2.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(backupSse.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                }
            })
            .build()

        val client = StreamingAiClient(httpClient = okHttpClient)
        val events = client.streamWithSpikeRacing(
            keyPool = listOf(key1, key2),
            prompt = "Test thought-only stream failover"
        ).toList()

        assertEquals(1, countKey1.get())
        assertEquals(1, countKey2.get())

        val spikeEvent = events.filterIsInstance<StreamUiEvent.SpikeDetected>().firstOrNull()
        assertNotNull("Phải emit SpikeDetected khi key chính chỉ trả về thought rồi ngắt", spikeEvent)

        val completeEvent = events.filterIsInstance<StreamUiEvent.Complete>().firstOrNull()
        assertNotNull("Key dự phòng phải hoàn thành stream với nội dung hiển thị", completeEvent)
        assertEquals("key_backup_visible", completeEvent?.usedKey?.id)
        assertEquals("Câu trả lời chính thức từ key dự phòng", completeEvent?.fullContent)
    }
}
