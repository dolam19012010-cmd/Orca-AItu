package com.orca.orcaauto.data.remote

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.orca.orcaauto.domain.filter.SummaryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

sealed class GeminiError(message: String, val statusCode: Int? = null) : Exception(message) {
    class InvalidApiKey(msg: String, code: Int = 401) : GeminiError(msg, code)
    class RateLimitExceeded(msg: String, code: Int = 429) : GeminiError(msg, code)
    class ServerUnavailable(msg: String, code: Int = 500) : GeminiError(msg, code)
    class NetworkOffline(msg: String) : GeminiError(msg, null)
    class EmptyResponse(msg: String) : GeminiError(msg, null)
    class GenericError(msg: String, code: Int? = null) : GeminiError(msg, code)
}

class GeminiClient(
    private val apiKey: String,
    private val modelName: String = "gemini-1.5-flash",
    private val httpClient: OkHttpClient = UniversalAiClient.sharedHttpClient
) {

    private val gson: Gson get() = UniversalAiClient.sharedGson

    /**
     * Generates a clean, post-processed summary from Gemini.
     * Automatically handles network glitches, classification of HTTP status codes,
     * and strips code block fences.
     */
    suspend fun generateSummary(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                GeminiError.InvalidApiKey("Chưa cấu hình Gemini API Key. Vui lòng vào Cài đặt để nhập Key.")
            )
        }

        // 1. Attempt using official Google Generative AI SDK
        try {
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )
            val response = generativeModel.generateContent(prompt)
            val rawText = response.text
            if (!rawText.isNullOrBlank()) {
                val cleaned = SummaryParser.parse(rawText).rawCleanText
                return@withContext Result.success(cleaned)
            }
        } catch (e: Exception) {
            val classified = classifyException(e)
            // If it's an Auth error (Invalid Key), fail immediately without wasting time on REST retry
            if (classified is GeminiError.InvalidApiKey) {
                return@withContext Result.failure(classified)
            }
            Log.w(TAG, "SDK call failed: ${e.message}, falling back to Direct REST API...", e)
        }

        // 2. Direct REST Fallback via OkHttp
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

            val jsonBody = JsonObject().apply {
                val contentsArray = com.google.gson.JsonArray().apply {
                    val contentObj = JsonObject().apply {
                        val partsArray = com.google.gson.JsonArray().apply {
                            val partObj = JsonObject().apply {
                                addProperty("text", prompt)
                            }
                            add(partObj)
                        }
                        add("parts", partsArray)
                    }
                    add(contentObj)
                }
                add("contents", contentsArray)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val error = when (code) {
                    400, 401, 403 -> GeminiError.InvalidApiKey("API Key không hợp lệ hoặc không có quyền truy cập (HTTP $code): $responseBody", code)
                    429 -> GeminiError.RateLimitExceeded("Vượt quá hạn ngạch API (HTTP 429). Sẽ thử lại sau 15-30 phút.", code)
                    500, 503 -> GeminiError.ServerUnavailable("Máy chủ Google AI tạm thời quá tải (HTTP $code).", code)
                    else -> GeminiError.GenericError("Lỗi Gemini API (HTTP $code): $responseBody", code)
                }
                Log.e(TAG, error.message.orEmpty())
                return@withContext Result.failure(error)
            }

            val parsedResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = parsedResponse.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates.get(0).asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val rawSummaryText = parts.get(0).asJsonObject.get("text")?.asString.orEmpty()
                    if (rawSummaryText.isNotBlank()) {
                        val cleaned = SummaryParser.parse(rawSummaryText).rawCleanText
                        return@withContext Result.success(cleaned)
                    }
                }
            }

            Result.failure(GeminiError.EmptyResponse("Gemini trả lời phản hồi rỗng. Dữ liệu sẽ được bảo toàn để thử lại."))
        } catch (e: Exception) {
            val classified = classifyException(e)
            Log.e(TAG, "Direct REST failed: ${classified.message}", e)
            Result.failure(classified)
        }
    }

    private fun classifyException(e: Exception): GeminiError {
        return when (e) {
            is UnknownHostException, is SocketTimeoutException -> {
                GeminiError.NetworkOffline("Mất kết nối mạng hoặc đường truyền timeout. Giữ nguyên tin nhắn để thử lại sau 15 phút.")
            }
            is IOException -> {
                if (e.message?.contains("429") == true) {
                    GeminiError.RateLimitExceeded("Vượt quá giới hạn tần suất API (429).")
                } else if (e.message?.contains("401") == true || e.message?.contains("403") == true) {
                    GeminiError.InvalidApiKey("API Key không hợp lệ (401/403).")
                } else {
                    GeminiError.NetworkOffline("Lỗi I/O mạng: ${e.message}")
                }
            }
            else -> GeminiError.GenericError(e.message ?: "Lỗi không xác định")
        }
    }

    companion object {
        private const val TAG = "GeminiClient"
    }
}
