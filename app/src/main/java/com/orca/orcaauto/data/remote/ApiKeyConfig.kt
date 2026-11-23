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
            "gemini-3.8-flash",        // Gemini 3.8 Flash (Flagship 2026 - Siêu tốc & Agentic workflow)
            "gemini-3.5-flash-lite",   // Gemini 3.5 Flash-Lite (Phản hồi tức thì, tối ưu Vòng 1 & Micro-tasks)
            "gemini-4-argon",          // Gemini 4 Argon (Frontier reasoning thế hệ 4 mới nhất, 1M context)
            "gemini-3.6-flash",        // Gemini 3.6 Flash (Cân bằng token & suy luận phản biện)
            "gemini-3.0-flash",        // Gemini 3.0 Flash
            "gemini-2.5-flash",        // Gemini 2.5 Flash (Legacy / Cũ)
            "gemini-2.5-pro"           // Gemini 2.5 Pro (Legacy / Cũ)
        )
    ),
    GROQ(
        displayName = "Groq Cloud (LPU)",
        badgeColor = "#F59E0B",
        defaultModel = "llama-3.3-70b-versatile",
        supportedModels = listOf(
            "llama-3.3-70b-versatile",         // Meta Llama 3.3 70B (Tốc độ 300+ tok/s, hiểu tiếng Việt xuất sắc)
            "qwen/qwen3.8-27b",                // Qwen 3.8 đa ngôn ngữ thông minh
            "openai/gpt-oss-120b",             // OpenAI GPT-OSS 120B (Open-weights reasoning trên LPU)
            "openai/gpt-oss-20b",              // OpenAI GPT-OSS 20B (Siêu tốc độ phản hồi tức thì)
            "llama-3.1-8b-instant",            // Llama 3.1 8B (500+ tok/s, phân loại siêu tốc ánh sáng)
            "deepseek-r1-distill-llama-70b",   // DeepSeek R1 Distill (Reasoning chuyên sâu)
            "qwen-2.5-32b",                    // Qwen 2.5 32B Coder / Multilingual
            "mistral-saba-24b",                // Mistral Saba 24B
            "mixtral-8x7b-32768"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek Platform",
        badgeColor = "#10B981",
        defaultModel = "deepseek-chat", // DeepSeek-V4.1-Flash / V3 alias
        supportedModels = listOf(
            "deepseek-flash",    // DeepSeek-V4.1-Flash (Mới nhất, siêu rẻ $0.15/1M, native tools & thinking)
            "deepseek-v4-pro",   // DeepSeek-V4 Pro (Flagship MoE mới nhất, $0.66/1M, suy luận nghiệp vụ chuyên sâu)
            "deepseek-chat",     // DeepSeek Chat Alias (Tự động map sang V4.1-Flash / V3)
            "deepseek-reasoner"  // DeepSeek Reasoner Alias (Chain of Thought reasoning V4 / R1)
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter (Multi-AI)",
        badgeColor = "#06B6D4",
        defaultModel = "openrouter/free",
        supportedModels = listOf(
            "openrouter/free",                             // Tự động điều hướng model Free tốt nhất theo tác vụ
            "nvidia/nemotron-3-ultra-550b-a55b:free",       // NVIDIA Nemotron 550B MoE (Siêu mạnh, 1M context - Free)
            "nvidia/nemotron-3.5-super-vl:free",           // NVIDIA Nemotron 3.5 Super VL (Vision/Text - Free)
            "google/gemma-4-31b-it:free",                   // Google Gemma 4 31B Multimodal (Free)
            "meta-llama/llama-3.3-70b-instruct:free",       // Llama 3.3 70B Instruct (Free)
            "deepseek/deepseek-r1:free",                    // DeepSeek R1 Reasoning (Free)
            "deepseek/deepseek-chat:free",                  // DeepSeek V3/V4 (Free)
            "anthropic/claude-sonnet-5-5",                 // Claude Sonnet 5.5 (Flagship Agentic, suy luận tiếng Việt đỉnh cao)
            "anthropic/claude-haiku-5-5",                  // Claude Haiku 5.5 ($0.10/1M, siêu tốc cho routing Vòng 1 & tóm tắt)
            "anthropic/claude-3.7-sonnet",                 // Claude 3.7 Sonnet (Hybrid Reasoning - Thinking budget)
            "anthropic/claude-3.5-haiku",                  // Claude 3.5 Haiku (Siêu tốc & Tiết kiệm chi phí)
            "openai/gpt-5-mini",                           // GPT-5 Mini ($0.25/1M in, thế hệ 5 thông minh, 400K context)
            "openai/gpt-4o-mini"                           // GPT-4o Mini ($0.15/1M in, phổ thông qua OpenRouter)
        )
    ),
    NVIDIA(
        displayName = "NVIDIA NIM",
        badgeColor = "#76B900",
        defaultModel = "nvidia/llama-3.1-nemotron-70b-instruct",
        supportedModels = listOf(
            "nvidia/llama-3.1-nemotron-70b-instruct",
            "nvidia/nemotron-3-ultra-550b-a55b",
            "meta/llama-3.3-70b-instruct",
            "deepseek-ai/deepseek-r1",
            "nvidia/nemotron-4-340b-instruct",
            "mistralai/mixtral-8x22b-instruct-v0.1"
        )
    ),
    OPENAI_COMPATIBLE(
        displayName = "OpenAI / Custom",
        badgeColor = "#E4E4E7",
        defaultModel = "gpt-4o-mini",
        supportedModels = listOf(
            "gpt-4o-mini",      // GPT-4o Mini ($0.15/1M in, siêu tốc & kinh tế cho tóm tắt & Vòng 1)
            "gpt-5-mini",       // GPT-5 Mini ($0.25/1M in, thế hệ 5 thông minh, 400K context)
            "gpt-5",            // GPT-5 Flagship ($1.25/1M in, 400K context, suy luận cao cấp)
            "o3-mini",          // o3-mini ($1.10/1M in, suy luận logic tốc độ cao, hỗ trợ effort low/med/high)
            "o3",               // o3 Flagship ($2.00/1M in, $8.00/1M out, reasoning thế hệ mới chi phí tối ưu)
            "gpt-4o",           // GPT-4o Flagship đa phương thức ($2.50/1M in)
            "o1"                // o1 Flagship reasoning chuyên sâu ($15.00/1M in)
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
                trimmed.startsWith("AIzaSy") || trimmed.startsWith("AQ.") -> {
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
                trimmed.startsWith("sk-proj-") || trimmed.startsWith("sk-admin-") -> {
                    val model = customModel ?: AiProviderType.OPENAI_COMPATIBLE.defaultModel
                    ApiKeyConfig(
                        rawKey = trimmed,
                        provider = AiProviderType.OPENAI_COMPATIBLE,
                        modelName = model,
                        endpointUrl = "https://api.openai.com/v1/chat/completions"
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
