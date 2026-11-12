package com.orca.orcaauto.data.remote

import com.google.gson.JsonParser

/**
 * Pure SSE (Server-Sent Events) line parser for LLM streaming responses.
 *
 * Fully testable on JVM without network dependencies or Android framework components.
 * Supports:
 * - Google Gemini (:streamGenerateContent?alt=sse): detects candidates[].content.parts[].thought == true
 *   for internal reasoning (ThoughtDelta) and false/null for standard text (ContentDelta).
 * - OpenRouter / OpenAI-compatible (stream: true): detects delta.reasoning / delta.reasoning_content
 *   for internal reasoning (ThoughtDelta) and delta.content for standard text (ContentDelta).
 */
object StreamingSseParser {

    /**
     * Checks if the given SSE line indicates stream termination (e.g. [DONE]).
     */
    fun isDone(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "data: [DONE]" || trimmed == "[DONE]"
    }

    /**
     * Parses a single SSE line from Google Gemini API.
     *
     * @param line Raw SSE line (may start with "data: " or be raw JSON).
     * @return List of Pair(text, isThought) where isThought == true means ThoughtDelta,
     *         and isThought == false means ContentDelta. Empty list if line contains no text.
     */
    fun parseGeminiLine(line: String): List<Pair<String, Boolean>> {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":") || trimmed.startsWith("event:") || trimmed.startsWith("id:")) {
            return emptyList()
        }
        val jsonStr = if (trimmed.startsWith("data:")) trimmed.substring(5).trim() else trimmed
        if (jsonStr.isEmpty() || jsonStr == "[DONE]") {
            return emptyList()
        }

        return try {
            val element = JsonParser.parseString(jsonStr)
            if (!element.isJsonObject) return emptyList()
            val root = element.asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: return emptyList()

            val result = mutableListOf<Pair<String, Boolean>>()
            for (candElem in candidates) {
                if (!candElem.isJsonObject) continue
                val candObj = candElem.asJsonObject
                val content = candObj.getAsJsonObject("content") ?: continue
                val parts = content.getAsJsonArray("parts") ?: continue

                for (partElem in parts) {
                    if (!partElem.isJsonObject) continue
                    val partObj = partElem.asJsonObject
                    val text = partObj.get("text")?.let { if (!it.isJsonNull) it.asString else null }
                    if (!text.isNullOrEmpty()) {
                        val isThought = partObj.get("thought")?.let {
                            if (!it.isJsonNull) it.asBoolean else false
                        } ?: false
                        result.add(Pair(text, isThought))
                    }
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a single SSE line from OpenRouter / OpenAI-compatible API.
     *
     * @param line Raw SSE line (may start with "data: " or be raw JSON).
     * @return List of Pair(text, isThought) where isThought == true means ThoughtDelta,
     *         and isThought == false means ContentDelta. Empty list if line contains no text.
     */
    fun parseOpenAiCompatibleLine(line: String): List<Pair<String, Boolean>> {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(":") || trimmed.startsWith("event:") || trimmed.startsWith("id:")) {
            return emptyList()
        }
        val jsonStr = if (trimmed.startsWith("data:")) trimmed.substring(5).trim() else trimmed
        if (jsonStr.isEmpty() || jsonStr == "[DONE]") {
            return emptyList()
        }

        return try {
            val element = JsonParser.parseString(jsonStr)
            if (!element.isJsonObject) return emptyList()
            val root = element.asJsonObject
            val choices = root.getAsJsonArray("choices") ?: return emptyList()

            val result = mutableListOf<Pair<String, Boolean>>()
            for (choiceElem in choices) {
                if (!choiceElem.isJsonObject) continue
                val choiceObj = choiceElem.asJsonObject
                val delta = choiceObj.getAsJsonObject("delta") ?: continue

                // Check reasoning / thought fields first (DeepSeek R1 / OpenRouter reasoning)
                val reasoning = delta.get("reasoning")?.let { if (!it.isJsonNull) it.asString else null }
                    ?: delta.get("reasoning_content")?.let { if (!it.isJsonNull) it.asString else null }

                if (!reasoning.isNullOrEmpty()) {
                    result.add(Pair(reasoning, true))
                }

                // Check standard content field
                val content = delta.get("content")?.let { if (!it.isJsonNull) it.asString else null }
                if (!content.isNullOrEmpty()) {
                    result.add(Pair(content, false))
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
