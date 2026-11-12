package com.orca.orcaauto.data.remote

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
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
}
