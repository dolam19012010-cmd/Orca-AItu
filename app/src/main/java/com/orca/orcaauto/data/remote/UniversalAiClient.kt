package com.orca.orcaauto.data.remote

import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.orca.orcaauto.domain.filter.SummaryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Immutable
data class KeyTestReport(
    val config: ApiKeyConfig,
    val isSuccess: Boolean,
    val latencyMs: Long,
    val responsePreview: String,
    val errorMessage: String = ""
)

class UniversalAiClient {

    private val httpClient: OkHttpClient get() = sharedHttpClient
    private val gson: Gson get() = sharedGson

    /**
     * Executes AI summarization with Automatic Failover through the pool of keys.
     * Supports latest models: Gemini 2.0/2.5 Flash, Groq Llama 3.3 70B, DeepSeek V3/R1, OpenRouter.
     */
    suspend fun executeWithFailover(
        keyPool: List<ApiKeyConfig>,
        prompt: String
    ): Result<Pair<String, ApiKeyConfig>> = withContext(Dispatchers.IO) {
        val activeKeys = keyPool.filter { it.isEnabled && it.rawKey.isNotBlank() }

        if (activeKeys.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Chưa cấu hình bất kỳ API Key nào! Vui lòng vào mục Cài đặt để thêm Key.")
            )
        }

        val errors = mutableListOf<String>()

        for ((index, keyConfig) in activeKeys.withIndex()) {
            Log.i(TAG, "Đang thử gọi [${keyConfig.provider.displayName} - ${keyConfig.modelName}] (Key #${index + 1}: ${keyConfig.maskedKey})...")

            val singleResult = executeSingleKey(keyConfig, prompt)

            if (singleResult.isSuccess) {
                val cleanText = singleResult.getOrNull().orEmpty()
                Log.i(TAG, "=> Thành công với [${keyConfig.provider.displayName} - ${keyConfig.modelName}]!")
                return@withContext Result.success(Pair(cleanText, keyConfig))
            } else {
                val errorMsg = singleResult.exceptionOrNull()?.message ?: "Lỗi không xác định"
                Log.w(TAG, "=> Key #${index + 1} (${keyConfig.provider.displayName}) gặp sự cố: $errorMsg. Tự động chuyển sang Key tiếp theo...")
                errors.add("${keyConfig.provider.displayName} (${keyConfig.modelName}): $errorMsg")
            }
        }

        // All keys in the pool failed
        Result.failure(
            IOException("Toàn bộ ${activeKeys.size} API Key trong danh sách dự phòng đều thất bại:\n" + errors.joinToString("\n"))
        )
    }

    private fun executeSingleKey(config: ApiKeyConfig, prompt: String): Result<String> {
        return try {
            when (config.provider) {
                AiProviderType.GEMINI -> executeGemini(config, prompt)
                AiProviderType.GROQ,
                AiProviderType.DEEPSEEK,
                AiProviderType.OPENROUTER,
                AiProviderType.NVIDIA,
                AiProviderType.OPENAI_COMPATIBLE -> executeOpenAiCompatible(config, prompt)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGemini(config: ApiKeyConfig, prompt: String): Result<String> {
        val jsonBody = JsonObject().apply {
            val contentsArray = JsonArray().apply {
                val contentObj = JsonObject().apply {
                    val partsArray = JsonArray().apply {
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

        val requestBodyString = jsonBody.toString()
        val modelsToTry = listOf(
            config.modelName,
            "gemini-3.8-flash",
            "gemini-4-hydrogen",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.0-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash"
        ).distinct()

        var lastError: String = ""
        for (model in modelsToTry) {
            val requestBody = requestBodyString.toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.rawKey}"
            val maskedUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.maskedKey}"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val singleStart = System.currentTimeMillis()
            val response = try {
                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                val dur = System.currentTimeMillis() - singleStart
                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = config.provider.displayName,
                        model = model,
                        endpoint = maskedUrl,
                        httpCode = null,
                        isSuccess = false,
                        durationMs = dur,
                        promptPreview = prompt.take(160),
                        errorMessage = "Network/Socket Exception: ${e.message}"
                    )
                )
                return Result.failure(e)
            }

            val code = response.code
            val responseBody = response.body?.string().orEmpty()
            val dur = System.currentTimeMillis() - singleStart

            if (!response.isSuccessful) {
                lastError = "HTTP $code ($model): $responseBody"
                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = config.provider.displayName,
                        model = model,
                        endpoint = maskedUrl,
                        httpCode = code,
                        isSuccess = false,
                        durationMs = dur,
                        promptPreview = prompt.take(160),
                        rawResponseSnippet = responseBody.take(400),
                        errorMessage = lastError
                    )
                )
                if (code == 404 || code == 503 || code == 429 || code == 500 || code == 502 || code == 504 || code == 505) {
                    Log.w(TAG, "Gemini $model gặp sự cố HTTP $code (quá tải spike demand hoặc chưa mở). Tự động chuyển model tiếp theo...")
                    continue // Thử model tiếp theo trong danh sách dự phòng
                } else {
                    return Result.failure(IOException(lastError))
                }
            }

            val parsedResponse = try { gson.fromJson(responseBody, JsonObject::class.java) } catch (_: Exception) { null }
            val candidates = parsedResponse?.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val firstCandidate = candidates.get(0).asJsonObject
                val content = firstCandidate.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                if (parts != null && parts.size() > 0) {
                    val rawText = parts.get(0).asJsonObject.get("text")?.asString.orEmpty()
                    ApiDebugLogManager.log(
                        ApiDebugLogEntry(
                            provider = config.provider.displayName,
                            model = model,
                            endpoint = maskedUrl,
                            httpCode = code,
                            isSuccess = true,
                            durationMs = dur,
                            promptPreview = prompt.take(160),
                            rawResponseSnippet = rawText.take(400)
                        )
                    )
                    return Result.success(SummaryParser.parse(rawText).rawCleanText)
                }
            }
            lastError = "Không thể trích xuất phản hồi từ Gemini ($model): $responseBody"
            ApiDebugLogManager.log(
                ApiDebugLogEntry(
                    provider = config.provider.displayName,
                    model = model,
                    endpoint = maskedUrl,
                    httpCode = code,
                    isSuccess = false,
                    durationMs = dur,
                    promptPreview = prompt.take(160),
                    rawResponseSnippet = responseBody.take(400),
                    errorMessage = lastError
                )
            )
        }

        return Result.failure(IOException(lastError.ifBlank { "Không thể kết nối đến máy chủ Gemini" }))
    }

    suspend fun testSingleKey(config: ApiKeyConfig): KeyTestReport = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val result = executeSingleKey(config, "Kiểm tra kết nối: Vui lòng chỉ trả lời từ 'OK' đúng 2 chữ cái.")
        val latency = System.currentTimeMillis() - start
        if (result.isSuccess) {
            KeyTestReport(
                config = config,
                isSuccess = true,
                latencyMs = latency,
                responsePreview = result.getOrNull().orEmpty().take(80)
            )
        } else {
            KeyTestReport(
                config = config,
                isSuccess = false,
                latencyMs = latency,
                responsePreview = "",
                errorMessage = result.exceptionOrNull()?.message ?: "Lỗi kết nối không xác định"
            )
        }
    }

    suspend fun testAllKeys(keyPool: List<ApiKeyConfig>): List<KeyTestReport> = withContext(Dispatchers.IO) {
        keyPool.map { testSingleKey(it) }
    }

    private fun executeOpenAiCompatible(config: ApiKeyConfig, prompt: String): Result<String> {
        val modelsToTry = if (config.provider == AiProviderType.OPENROUTER) {
            listOf(
                config.modelName,
                "openrouter/free",
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "nvidia/llama-3.1-nemotron-70b-instruct:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "deepseek/deepseek-r1:free",
                "google/gemma-4-31b-it:free",
                "mistralai/mistral-small-24b-instruct-2501:free"
            ).distinct()
        } else {
            listOf(config.modelName)
        }

        var lastError = ""
        for (model in modelsToTry) {
            val jsonBody = JsonObject().apply {
                addProperty("model", model)
                addProperty("temperature", 0.2)

                val messagesArray = JsonArray().apply {
                    val sysMsg = JsonObject().apply {
                        addProperty("role", "system")
                        addProperty("content", "Bạn là một Trợ lý Điều hành AI Chuyên nghiệp.")
                    }
                    add(sysMsg)

                    val userMsg = JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    }
                    add(userMsg)
                }
                add("messages", messagesArray)
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(config.endpointUrl)
                .addHeader("Authorization", "Bearer ${config.rawKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)

            // OpenRouter specific headers for unthrottled free tier
            if (config.provider == AiProviderType.OPENROUTER) {
                requestBuilder.addHeader("HTTP-Referer", "https://github.com/orcaauto")
                requestBuilder.addHeader("X-Title", "OrcaAuto Android")
            }

            val singleStart = System.currentTimeMillis()
            val response = try {
                httpClient.newCall(requestBuilder.build()).execute()
            } catch (e: Exception) {
                val dur = System.currentTimeMillis() - singleStart
                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = config.provider.displayName,
                        model = model,
                        endpoint = config.endpointUrl,
                        httpCode = null,
                        isSuccess = false,
                        durationMs = dur,
                        promptPreview = prompt.take(160),
                        errorMessage = "Network Exception: ${e.message}"
                    )
                )
                return Result.failure(e)
            }

            val code = response.code
            val responseBody = response.body?.string().orEmpty()
            val dur = System.currentTimeMillis() - singleStart

            if (!response.isSuccessful) {
                lastError = "HTTP $code ($model): $responseBody"
                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = config.provider.displayName,
                        model = model,
                        endpoint = config.endpointUrl,
                        httpCode = code,
                        isSuccess = false,
                        durationMs = dur,
                        promptPreview = prompt.take(160),
                        rawResponseSnippet = responseBody.take(400),
                        errorMessage = lastError
                    )
                )
                if (config.provider == AiProviderType.OPENROUTER && (code == 404 || code == 400 || code == 402 || code == 429 || code == 503)) {
                    Log.w(TAG, "OpenRouter model $model trả về mã $code. Tự động chuyển model dự phòng...")
                    continue
                } else {
                    return Result.failure(IOException(lastError))
                }
            }

            val parsedResponse = try { gson.fromJson(responseBody, JsonObject::class.java) } catch (_: Exception) { null }
            val choices = parsedResponse?.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices.get(0).asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                // For DeepSeek R1 or reasoning models, prioritize content
                val content = message?.get("content")?.asString.orEmpty()
                if (content.isNotBlank()) {
                    ApiDebugLogManager.log(
                        ApiDebugLogEntry(
                            provider = config.provider.displayName,
                            model = model,
                            endpoint = config.endpointUrl,
                            httpCode = code,
                            isSuccess = true,
                            durationMs = dur,
                            promptPreview = prompt.take(160),
                            rawResponseSnippet = content.take(400)
                        )
                    )
                    return Result.success(SummaryParser.parse(content).rawCleanText)
                }
            }

            lastError = "Không thể đọc phản hồi từ OpenAI Compatible API ($model): $responseBody"
            ApiDebugLogManager.log(
                ApiDebugLogEntry(
                    provider = config.provider.displayName,
                    model = model,
                    endpoint = config.endpointUrl,
                    httpCode = code,
                    isSuccess = false,
                    durationMs = dur,
                    promptPreview = prompt.take(160),
                    rawResponseSnippet = responseBody.take(400),
                    errorMessage = lastError
                )
            )
        }

        return Result.failure(IOException(lastError.ifBlank { "Không thể kết nối đến máy chủ AI" }))
    }

    companion object {
        private const val TAG = "UniversalAiClient"

        /**
         * Shared OkHttpClient singleton with connection pooling.
         * Reuses TLS sockets and prevents socket leakages.
         */
        val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        val sharedGson: Gson by lazy { Gson() }
    }
}
