package com.orca.orcaauto.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyConfigTest {

    @Test
    fun testDetectGoogleGeminiKey() {
        val raw = "AIzaSyAbc1234567890TestKey1234"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.GEMINI, config.provider)
        assertEquals("gemini-3.8-flash", config.modelName)
        assertTrue(config.endpointUrl.contains("generativelanguage.googleapis.com"))
        assertEquals("AIza...1234", config.maskedKey)
    }

    @Test
    fun testDetectGoogleGeminiKeyWithAqPrefix() {
        val raw = "AQ.Ab8SampleGeminiAuthKey1234567890"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.GEMINI, config.provider)
        assertEquals("gemini-3.8-flash", config.modelName)
        assertTrue(config.endpointUrl.contains("generativelanguage.googleapis.com"))
    }

    @Test
    fun testDetectGroqKey() {
        val raw = "gsk_SampleGroqKey9876543210ABCDEF"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.GROQ, config.provider)
        assertEquals("llama-3.3-70b-versatile", config.modelName)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", config.endpointUrl)
    }

    @Test
    fun testDetectOpenRouterKey() {
        val raw = "sk-or-v1-SampleOpenRouterKey123456"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.OPENROUTER, config.provider)
        assertEquals("openrouter/free", config.modelName)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", config.endpointUrl)
    }

    @Test
    fun testDetectNvidiaKey() {
        val raw = "nvapi-sampleNvidiaNimKey12345678"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.NVIDIA, config.provider)
        assertEquals("https://integrate.api.nvidia.com/v1/chat/completions", config.endpointUrl)
    }

    @Test
    fun testDetectDeepSeekKey() {
        val raw = "sk-deepseekSampleKey12345678"
        val config = ApiKeyConfig.fromRawKey(raw)
        assertEquals(AiProviderType.DEEPSEEK, config.provider)
        assertEquals("https://api.deepseek.com/chat/completions", config.endpointUrl)
    }

    @Test
    fun testCustomModelOverride() {
        val raw = "AIzaSyTest"
        val config = ApiKeyConfig.fromRawKey(raw, "gemini-3.0-flash")
        assertEquals("gemini-3.0-flash", config.modelName)
    }
}
