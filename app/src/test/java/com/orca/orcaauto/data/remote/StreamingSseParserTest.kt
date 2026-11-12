package com.orca.orcaauto.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [StreamingSseParser].
 *
 * Verifies:
 * 1. Google Gemini SSE line parsing with thoughts (parts[].thought == true) and content.
 * 2. OpenRouter / OpenAI compatible SSE line parsing with delta.reasoning, delta.reasoning_content, and delta.content.
 * 3. Stream termination [DONE], keep-alive comments, SSE metadata events.
 * 4. Error resilience on malformed, corrupted, or non-JSON lines.
 */
class StreamingSseParserTest {

    // =========================================================================
    // 1. STREAM TERMINATION [DONE]
    // =========================================================================

    @Test
    fun isDone_detectsStandardSseDoneAndRawDone() {
        assertTrue(StreamingSseParser.isDone("data: [DONE]"))
        assertTrue(StreamingSseParser.isDone("[DONE]"))
        assertTrue(StreamingSseParser.isDone("   data: [DONE]   "))
        assertTrue(StreamingSseParser.isDone("   [DONE]   "))

        assertFalse(StreamingSseParser.isDone("data: {\"content\":\"DONE\"}"))
        assertFalse(StreamingSseParser.isDone("data: [DONE]something"))
        assertFalse(StreamingSseParser.isDone(""))
        assertFalse(StreamingSseParser.isDone(": keep-alive"))
    }

    // =========================================================================
    // 2. GOOGLE GEMINI SSE PARSING
    // =========================================================================

    @Test
    fun parseGeminiLine_thoughtPart_parsedAsThoughtDelta() {
        val line = """
            data: {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Tôi cần tra cứu ghi chú người dùng yêu cầu.",
                        "thought": true
                      }
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = StreamingSseParser.parseGeminiLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Tôi cần tra cứu ghi chú người dùng yêu cầu.", parsed[0].first)
        assertTrue("thought=true phải được phân giải thành isThought=true", parsed[0].second)
    }

    @Test
    fun parseGeminiLine_contentPart_parsedAsContentDelta() {
        val line = """
            data: {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "Xin chào! Tôi có thể giúp gì cho bạn hôm nay?",
                        "thought": false
                      }
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = StreamingSseParser.parseGeminiLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Xin chào! Tôi có thể giúp gì cho bạn hôm nay?", parsed[0].first)
        assertFalse("thought=false phải được phân giải thành isThought=false", parsed[0].second)
    }

    @Test
    fun parseGeminiLine_nullThoughtField_defaultsToContentDelta() {
        // Standard Gemini models without thinking return parts with text only (no thought property)
        val line = """
            data: {"candidates":[{"content":{"parts":[{"text":"Đây là câu trả lời."}]}}]}
        """.trimIndent()

        val parsed = StreamingSseParser.parseGeminiLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Đây là câu trả lời.", parsed[0].first)
        assertFalse("Thiếu trường thought phải mặc định là false (ContentDelta)", parsed[0].second)
    }

    @Test
    fun parseGeminiLine_multiplePartsInSingleChunk_preservesOrderAndTypes() {
        val line = """
            data: {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "Suy nghĩ 1", "thought": true },
                      { "text": "Suy nghĩ 2", "thought": true },
                      { "text": "Câu trả lời chính thức" }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = StreamingSseParser.parseGeminiLine(line)
        assertEquals(3, parsed.size)
        assertEquals("Suy nghĩ 1", parsed[0].first)
        assertTrue(parsed[0].second)
        assertEquals("Suy nghĩ 2", parsed[1].first)
        assertTrue(parsed[1].second)
        assertEquals("Câu trả lời chính thức", parsed[2].first)
        assertFalse(parsed[2].second)
    }

    @Test
    fun parseGeminiLine_commentsAndKeepAlive_returnsEmpty() {
        assertTrue(StreamingSseParser.parseGeminiLine(": keep-alive").isEmpty())
        assertTrue(StreamingSseParser.parseGeminiLine("event: message").isEmpty())
        assertTrue(StreamingSseParser.parseGeminiLine("id: 42").isEmpty())
        assertTrue(StreamingSseParser.parseGeminiLine("").isEmpty())
        assertTrue(StreamingSseParser.parseGeminiLine("   \n\t  ").isEmpty())
        assertTrue(StreamingSseParser.parseGeminiLine("data: [DONE]").isEmpty())
    }

    // =========================================================================
    // 3. OPENROUTER / OPENAI-COMPATIBLE SSE PARSING
    // =========================================================================

    @Test
    fun parseOpenAiCompatibleLine_standardContent_parsedAsContentDelta() {
        val line = """
            data: {"id":"gen-123","choices":[{"index":0,"delta":{"content":"Chào bạn!"}}]}
        """.trimIndent()

        val parsed = StreamingSseParser.parseOpenAiCompatibleLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Chào bạn!", parsed[0].first)
        assertFalse("delta.content phải là isThought=false", parsed[0].second)
    }

    @Test
    fun parseOpenAiCompatibleLine_reasoningField_parsedAsThoughtDelta() {
        // OpenAI o1 / o3 style reasoning
        val line = """
            data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"reasoning":"Analyzing user query..."}}]}
        """.trimIndent()

        val parsed = StreamingSseParser.parseOpenAiCompatibleLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Analyzing user query...", parsed[0].first)
        assertTrue("delta.reasoning phải là isThought=true", parsed[0].second)
    }

    @Test
    fun parseOpenAiCompatibleLine_reasoningContentField_parsedAsThoughtDelta() {
        // DeepSeek-R1 / OpenRouter reasoning_content style
        val line = """
            data: {"id":"chatcmpl-2","choices":[{"index":0,"delta":{"reasoning_content":"Người dùng đang hỏi về kho ghi chú."}}]}
        """.trimIndent()

        val parsed = StreamingSseParser.parseOpenAiCompatibleLine(line)
        assertEquals(1, parsed.size)
        assertEquals("Người dùng đang hỏi về kho ghi chú.", parsed[0].first)
        assertTrue("delta.reasoning_content phải là isThought=true", parsed[0].second)
    }

    @Test
    fun parseOpenAiCompatibleLine_bothReasoningAndContent_parsedInOrder() {
        val line = """
            data: {"choices":[{"delta":{"reasoning":"Thinking chunk...","content":"Answer chunk..."}}]}
        """.trimIndent()

        val parsed = StreamingSseParser.parseOpenAiCompatibleLine(line)
        assertEquals(2, parsed.size)
        assertEquals("Thinking chunk...", parsed[0].first)
        assertTrue(parsed[0].second)
        assertEquals("Answer chunk...", parsed[1].first)
        assertFalse(parsed[1].second)
    }

    @Test
    fun parseOpenAiCompatibleLine_commentsAndKeepAlive_returnsEmpty() {
        assertTrue(StreamingSseParser.parseOpenAiCompatibleLine(": ping").isEmpty())
        assertTrue(StreamingSseParser.parseOpenAiCompatibleLine("event: completion").isEmpty())
        assertTrue(StreamingSseParser.parseOpenAiCompatibleLine("data: [DONE]").isEmpty())
        assertTrue(StreamingSseParser.parseOpenAiCompatibleLine("[DONE]").isEmpty())
    }

    // =========================================================================
    // 4. MALFORMED LINES & ADVERSARIAL INPUTS (RESILIENCE)
    // =========================================================================

    @Test
    fun malformedLines_gracefullyHandledWithoutCrashing() {
        val malformedInputs = listOf(
            "data: {truncated json",
            "data: not a json string",
            "data: 123456",
            "data: true",
            "data: []",
            "data: {}",
            "data: {\"choices\": null}",
            "data: {\"candidates\": []}",
            "data: {\"candidates\": [{\"content\": null}]}",
            "data: {\"candidates\": [{\"content\": {\"parts\": [{\"text\": null}]}}]}",
            "data: {\"choices\": [{\"delta\": {\"content\": null}}]}",
            "{{{<<<corrupted byte stream>>>}}}"
        )

        for (input in malformedInputs) {
            val geminiResult = StreamingSseParser.parseGeminiLine(input)
            assertTrue("Gemini parser không được crash với input: $input", geminiResult.isEmpty())

            val openAiResult = StreamingSseParser.parseOpenAiCompatibleLine(input)
            assertTrue("OpenAI parser không được crash với input: $input", openAiResult.isEmpty())
        }
    }
}
