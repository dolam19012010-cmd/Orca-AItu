package com.orca.orcaauto.data.remote

import androidx.compose.runtime.Immutable

@Immutable
enum class AiProviderType(
    val displayName: String,
    val badgeColor: String,
    val defaultModel: String,
    val supportedModels: List<String>
) {
    GEMINI(
        displayName = "Google Gemini",
        badgeColor = "#FFFFFF",
        defaultModel = "gemini-3.8-flash",
        supportedModels = listOf(
            "gemini-3.8-flash",        // Gemini 3.8 Flash (Flagship 2026 - Siêu tốc & Agentic)
            "gemini-3.6-flash",        // Gemini 3.6 Flash (Tối ưu token & phản biện đa tầng)
            "gemini-3.5-flash-lite",   // Gemini 3.5 Flash-Lite (Phản hồi tức thì)
            "gemini-3.0-flash",        // Gemini 3.0 Flash
            "gemini-2.0-flash",        // Gemini 2.0 Flash (Ổn định cao)
            "gemini-1.5-flash"         // Gemini 1.5 Flash (Miễn phí phổ quát)
        )
    ),
    GROQ(
        displayName = "Groq Cloud (LPU)",
        badgeColor = "#F59E0B",
        defaultModel = "llama-3.3-70b-versatile",
        supportedModels = listOf(
            "llama-3.3-70b-versatile",         // Meta Llama 3.3 70B (Tốc độ 300+ tok/s)
            "deepseek-r1-distill-llama-70b",   // DeepSeek R1 Reasoning
            "llama-3.1-8b-instant",            // Phân loại siêu tốc
            "qwen/qwen3.8-27b",                // Qwen đa ngôn ngữ thông minh
            "mixtral-8x7b-32768"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek Platform",
        badgeColor = "#10B981",
        defaultModel = "deepseek-chat", // DeepSeek-V3
        supportedModels = listOf(
            "deepseek-chat",     // DeepSeek-V3 Flagship
            "deepseek-reasoner"  // DeepSeek-R1 Chain of Thought
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter (Multi-AI)",
        badgeColor = "#06B6D4",
        defaultModel = "openrouter/free",
        supportedModels = listOf(
            "openrouter/free",                             // Tự động điều hướng model Free tốt nhất (Không bao giờ 404)
            "nvidia/nemotron-3-ultra-550b-a55b:free",       // NVIDIA Nemotron 550B MoE (Siêu mạnh)
            "nvidia/llama-3.1-nemotron-70b-instruct:free", // NVIDIA Nemotron 70B
            "meta-llama/llama-3.3-70b-instruct:free",       // Llama 3.3 70B Instruct (Free)
            "deepseek/deepseek-r1:free",                    // DeepSeek R1 Reasoning (Free)
            "deepseek/deepseek-chat:free",                  // DeepSeek V3 (Free)
            "google/gemma-4-31b-it:free",                   // Google Gemma 4 31B (Free)
            "mistralai/mistral-small-24b-instruct-2501:free",// Mistral Small 24B (Free)
            "qwen/qwen-2.5-72b-instruct:free"              // Qwen 2.5 72B (Free)
        )
    ),
    NVIDIA(
        displayName = "NVIDIA NIM",
        badgeColor = "#76B900",
        defaultModel = "nvidia/llama-3.1-nemotron-70b-instruct",
        supportedModels = listOf(
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "meta/llama-3.3-70b-instruct",
            "mistralai/mixtral-8x22b-instruct-v0.1"
        )
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI / Custom",
        badgeColor = "#E4E4E7",
        defaultModel = "gpt-4o-mini",
        supportedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o"
        )
    )
}

@Immutable
data class ApiKeyConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawKey: String,
    val provider: AiProviderType,
    val modelName: String,
    val endpointUrl: String,
    val isEnabled: Boolean = true
) {
    val maskedKey: String
        get() = if (rawKey.length > 8) "${rawKey.take(4)}...${rawKey.takeLast(4)}" else "****"

    companion object {
        /**
         * Automatically detects provider and default model from key format.
         */
        fun fromRawKey(key: String, customModel: String? = null): ApiKeyConfig {
            val trimmed = key.trim()
            return when {
                trimmed.startsWith("AIzaSy") -> {
                    val model = customModel ?: AiProviderType.GEMINI.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.GEMINI,
                        modelName = model,
                        endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmed"
                    )
                }
                trimmed.startsWith("gsk_") -> {
                    val model = customModel ?: AiProviderType.GROQ.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.GROQ,
                        modelName = model,
                        endpointUrl = "https://api.groq.com/openai/v1/chat/completions"
                    )
                }
                trimmed.startsWith("sk-or-") -> {
                    val model = customModel ?: AiProviderType.OPENROUTER.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.OPENROUTER,
                        modelName = model,
                        endpointUrl = "https://openrouter.ai/api/v1/chat/completions"
                    )
                }
                trimmed.startsWith("nvapi-") -> {
                    val model = customModel ?: AiProviderType.NVIDIA.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.NVIDIA,
                        modelName = model,
                        endpointUrl = "https://integrate.api.nvidia.com/v1/chat/completions"
                    )
                }
                trimmed.startsWith("sk-") -> {
                    val model = customModel ?: AiProviderType.DEEPSEEK.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.DEEPSEEK,
                        modelName = model,
                        endpointUrl = "https://api.deepseek.com/chat/completions"
                    )
                }
                else -> {
                    val model = customModel ?: AiProviderType.GEMINI.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.GEMINI,
                        modelName = model,
                        endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$trimmed"
                    )
                }
            }
        }
    }
}
