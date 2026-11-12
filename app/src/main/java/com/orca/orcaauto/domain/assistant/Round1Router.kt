package com.orca.orcaauto.domain.assistant

import android.util.Log
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.UniversalAiClient
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stage 2: Round1Router (Sub-ground lightweight model call).
 *
 * Runs completely hidden from the user interface.
 * Analyzes user prompt against lightweight catalog and 24h notification summary,
 * selecting relevant note IDs and sender filters.
 *
 * RESILIENCE INVARIANTS:
 * 1. Coroutine cancellation cleanly propagates (re-throws CancellationException).
 * 2. Robust JSON parsing: handles markdown code fences, embedded JSON, and loose text.
 * 3. Intelligent fallback: if AI fails, errors, or produces invalid output,
 *    heuristically matches keywords from user prompt against catalog titles.
 * 4. Bogus ID prevention: filters selected note IDs against actual catalog items.
 */
open class Round1Router(
    private val aiClient: UniversalAiClient = UniversalAiClient(),
    private val aiCaller: (suspend (keyPool: List<ApiKeyConfig>, prompt: String) -> Result<Pair<String, ApiKeyConfig>>)? = null
) {

    companion object {
        private const val TAG = "Round1Router"
        fun normalizeVietnamese(text: String): String {
            val nfd = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .replace('đ', 'd').replace('Đ', 'D')
                .trim()
        }
    }

    open suspend fun route(
        userPrompt: String,
        packagedContext: PackagedContext,
        keyPool: List<ApiKeyConfig>
    ): Round1Decision {
        val validCatalogIds = packagedContext.catalogItems.map { it.id }.toSet()

        // If catalog is completely empty and no recent notifications, skip AI call
        if (packagedContext.catalogItems.isEmpty() &&
            packagedContext.notificationSummarySnippet.contains("Không có thông báo")
        ) {
            return Round1Decision(
                neededNoteIds = emptyList(),
                senderFilter = emptyList(),
                reasoning = "Danh mục trống và không có thông báo gần đây",
                rawAiResponse = ""
            )
        }

        val promptForRouter = buildRouterPrompt(userPrompt, packagedContext)

        val rawResponse = try {
            val result = if (aiCaller != null) {
                aiCaller.invoke(keyPool, promptForRouter)
            } else {
                aiClient.executeWithFailover(keyPool, promptForRouter)
            }
            if (result.isSuccess) {
                result.getOrNull()?.first.orEmpty()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown router failure"
                Log.w(TAG, "Round 1 AI call failed: $err. Falling back to local heuristic...")
                ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Round 1 unexpected error: ${t.message}", t)
            ""
        }

        // Parse response or fallback
        val decision = parseRouterResponse(rawResponse, userPrompt, packagedContext, validCatalogIds)
        Log.i(TAG, "Round 1 decision: neededNotes=${decision.neededNoteIds}, senders=${decision.senderFilter}, reasoning='${decision.reasoning}'")
        return decision
    }

    private fun buildRouterPrompt(userPrompt: String, packagedContext: PackagedContext): String {
        return buildString {
            appendLine("Bạn là Bộ điều hướng bối cảnh (Context Router) chạy ngầm của OrcaAuto.")
            appendLine("Nhiệm vụ: Phân tích yêu cầu người dùng cùng với danh mục ghi chú và tổng quan tin nhắn để quyết định xem có cần truy xuất nội dung ghi chú cụ thể (noteIds) hoặc lọc tin nhắn của người gửi nào không.")
            appendLine()
            appendLine("QUY TẮC BẮT BUỘC:")
            appendLine("1. Chỉ chọn noteId nếu câu hỏi của người dùng thực sự liên quan hoặc nhắc đến ghi chú đó.")
            appendLine("2. ĐẶC BIỆT: Nếu câu hỏi hoặc yêu cầu của người dùng có chủ đề, từ khóa hoặc ý định trùng khớp/tương đồng với tiêu đề hoặc nội dung gợi ý của bất kỳ ghi chú nào trong danh mục (ví dụ người dùng hỏi về 'quay chụp bóng đá', 'kinh nghiệm quay chụp' hoặc 'Tip đi làm quay chụp bóng đá'), BẮT BUỘC phải đưa noteId đó vào 'neededNoteIds' để trích xuất nội dung thực tế trên máy, kể cả khi người dùng không viết rõ từ 'ghi chú'.")
            appendLine("3. Chỉ dùng ID có thực trong danh mục bên dưới. KHÔNG tự bịa ID.")
            appendLine("4. Nếu câu hỏi hoàn toàn chung chung và không liên quan gì đến danh mục ghi chú (ví dụ: chào hỏi, hỏi kiến thức chung, dịch thuật), trả về neededNoteIds là [].")
            appendLine("5. Trả về DUY NHẤT một đối tượng JSON hợp lệ theo cấu trúc sau (không kèm lời giải thích thừa ngoài JSON):")
            appendLine("```json")
            appendLine("{")
            appendLine("  \"neededNoteIds\": [1, 2],")
            appendLine("  \"senderFilter\": [\"tên người gửi nếu cần\"],")
            appendLine("  \"reasoning\": \"lý do ngắn gọn\"")
            appendLine("}")
            appendLine("```")
            appendLine()
            appendLine(packagedContext.fullOverviewPrompt)
        }
    }

    private fun parseRouterResponse(
        rawResponse: String,
        userPrompt: String,
        packagedContext: PackagedContext,
        validCatalogIds: Set<Long>
    ): Round1Decision {
        if (rawResponse.isBlank()) {
            return fallbackHeuristicDecision(userPrompt, packagedContext, validCatalogIds, "AI không phản hồi, dùng bộ lọc từ khóa cục bộ")
        }

        try {
            val jsonString = extractJsonSubstring(rawResponse)
            val jsonObject = JSONObject(jsonString)

            val neededIds = mutableListOf<Long>()
            val jsonArray = jsonObject.optJSONArray("neededNoteIds")
            if (jsonArray != null) {
                for (i in 0 until jsonArray.length()) {
                    val id = jsonArray.optLong(i, -1L)
                    if (id > 0 && validCatalogIds.contains(id)) {
                        neededIds.add(id)
                    }
                }
            }

            val senders = mutableListOf<String>()
            val sendersArray = jsonObject.optJSONArray("senderFilter")
            if (sendersArray != null) {
                for (i in 0 until sendersArray.length()) {
                    val s = sendersArray.optString(i, "").trim()
                    if (s.isNotBlank()) {
                        senders.add(s)
                    }
                }
            }

            val reasoning = jsonObject.optString("reasoning", "").trim()

            // If JSON was parsed but no note IDs found, do a quick heuristic check
            // to ensure we didn't miss an obvious title mention
            val combinedIds = if (neededIds.isEmpty()) {
                heuristicMatchNoteIds(userPrompt, packagedContext, validCatalogIds)
            } else {
                neededIds
            }

            return Round1Decision(
                neededNoteIds = combinedIds.distinct(),
                senderFilter = senders.distinct(),
                reasoning = reasoning.ifBlank { "Đã phân tích bối cảnh tự động" },
                rawAiResponse = rawResponse
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON from Round 1 response: ${e.message}. Attempting regex fallback...")
            return parseLooseTextOrFallback(rawResponse, userPrompt, packagedContext, validCatalogIds)
        }
    }

    private fun extractJsonSubstring(text: String): String {
        // Strip markdown code fences if present
        var clean = text.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }

        // Find outer curly braces
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return clean.substring(start, end + 1)
        }
        return clean
    }

    private fun parseLooseTextOrFallback(
        rawResponse: String,
        userPrompt: String,
        packagedContext: PackagedContext,
        validCatalogIds: Set<Long>
    ): Round1Decision {
        val extractedIds = mutableListOf<Long>()

        // Scan regex for note ID patterns like "ID: 4", "note #4", "ghi chú 4"
        val idRegex = Regex("""(?:id|ghi chú|note)\s*[:#]?\s*(\d+)""", RegexOption.IGNORE_CASE)
        for (match in idRegex.findAll(rawResponse)) {
            val num = match.groupValues[1].toLongOrNull()
            if (num != null && validCatalogIds.contains(num)) {
                extractedIds.add(num)
            }
        }

        if (extractedIds.isNotEmpty()) {
            return Round1Decision(
                neededNoteIds = extractedIds.distinct(),
                senderFilter = emptyList(),
                reasoning = "Trích xuất ID ghi chú qua biểu thức chính quy",
                rawAiResponse = rawResponse
            )
        }

        return fallbackHeuristicDecision(
            userPrompt = userPrompt,
            packagedContext = packagedContext,
            validCatalogIds = validCatalogIds,
            reason = "Phân giải phản hồi Vòng 1 thất bại, áp dụng tìm kiếm từ khóa cục bộ"
        )
    }

    private fun fallbackHeuristicDecision(
        userPrompt: String,
        packagedContext: PackagedContext,
        validCatalogIds: Set<Long>,
        reason: String
    ): Round1Decision {
        val matchedIds = heuristicMatchNoteIds(userPrompt, packagedContext, validCatalogIds)
        return Round1Decision(
            neededNoteIds = matchedIds,
            senderFilter = emptyList(),
            reasoning = reason,
            rawAiResponse = ""
        )
    }

    /**
     * Local heuristic keyword matcher:
     * Tokenizes user prompt and checks for semantic overlap with note titles in catalog.
     * Supports bidirectional containment, unaccented matching, Vietnamese 2-letter words ("đá", "đi", "xe", "ca", "vé", "ví", "ăn"),
     * and relaxed threshold (match >= 1 distinct key token or >= 40% title words).
     */
    internal fun heuristicMatchNoteIds(
        userPrompt: String,
        packagedContext: PackagedContext,
        validCatalogIds: Set<Long>
    ): List<Long> {
        val promptNormalized = userPrompt.lowercase().trim()
        val promptNoAccent = normalizeVietnamese(userPrompt)
        val cleanPrompt = promptNormalized.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val cleanPromptNoAccent = promptNoAccent.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

        val promptTokens = cleanPrompt.split(Regex("""\s+""")).filter { it.length >= 2 }.toSet()
        val promptTokensNoAccent = cleanPromptNoAccent.split(Regex("""\s+""")).filter { it.length >= 2 }.toSet()

        val paddedPrompt = " $cleanPrompt "
        val paddedPromptNoAccent = " $cleanPromptNoAccent "

        val matched = mutableListOf<Long>()

        for (item in packagedContext.catalogItems) {
            if (!validCatalogIds.contains(item.id)) continue

            val titleNormalized = item.title.lowercase().trim()
            val titleNoAccent = normalizeVietnamese(item.title)
            val cleanTitle = titleNormalized.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").replace(Regex("""\s+"""), " ").trim()
            val cleanTitleNoAccent = titleNoAccent.replace(Regex("""[^\p{L}\p{N}\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

            if (cleanTitle.isNotBlank()) {
                val paddedTitle = " $cleanTitle "
                val paddedTitleNoAccent = " $cleanTitleNoAccent "

                // 1. Bidirectional whole-phrase containment check (accented and unaccented)
                if (paddedPrompt.contains(paddedTitle) || paddedTitle.contains(paddedPrompt) ||
                    paddedPromptNoAccent.contains(paddedTitleNoAccent) || paddedTitleNoAccent.contains(paddedPromptNoAccent)
                ) {
                    matched.add(item.id)
                    continue
                }

                // 2. Token-level matching: Keep words length >= 2, preserving Vietnamese 2-letter words
                val titleWords = cleanTitle.split(Regex("""\s+"""))
                    .filter { it.length >= 2 && it !in commonStopWords }
                val titleWordsNoAccent = cleanTitleNoAccent.split(Regex("""\s+"""))
                    .filter { it.length >= 2 && it !in commonStopWordsNoAccent }

                if (titleWords.isEmpty()) continue

                var matchCount = 0

                for (idx in titleWords.indices) {
                    val word = titleWords[idx]
                    val wordNoAccent = if (idx < titleWordsNoAccent.size) titleWordsNoAccent[idx] else normalizeVietnamese(word)

                    val isWordMatched = promptTokens.contains(word) || promptTokensNoAccent.contains(wordNoAccent)

                    if (isWordMatched) {
                        matchCount++
                    }
                }

                val matchRatio = matchCount.toDouble() / titleWords.size
                // Threshold: match >= 40% of title words, or if title is short (1-2 words) match >= 1 key word
                if (matchRatio >= 0.4 || (titleWords.size <= 2 && matchCount >= 1)) {
                    matched.add(item.id)
                }
            }
        }

        return matched.distinct()
    }

    private val commonStopWords = setOf(
        "ghi", "chú", "danh", "sách", "của", "và", "các", "những", "cho", "với", "trong", "ngày", "này", "đó", "ở", "tại", "là", "bị", "được", "có", "về", "nhé", "nha", "ạ", "ơi"
    )

    private val commonStopWordsNoAccent = commonStopWords.map { normalizeVietnamese(it) }.toSet()
}
