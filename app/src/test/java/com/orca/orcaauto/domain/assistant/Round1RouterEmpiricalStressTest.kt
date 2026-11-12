package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.AiProviderType
import com.orca.orcaauto.domain.notes.NoteCatalogItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Adversarial & Stress Tests for [Round1Router]
 * Focus:
 * 1. Heuristic keyword and phrase matching with Vietnamese 2-letter words ("đá", "đi", "xe", "vé", "ca", "ăn").
 * 2. Exact and partial title matching for target notes (e.g. "Tip đi làm quay chụp bóng đá", "vé xe", "ca", "đi ăn").
 * 3. Unaccented queries (e.g. "tip di lam quay chup bong da", "ve xe", "xe", "ca", "di an").
 * 4. Noisy queries with chatter, filler words, question marks, and punctuation.
 * 5. Correct note ID routing and isolation against false positives.
 */
class Round1RouterEmpiricalStressTest {

    private val testApiKey = ApiKeyConfig(
        provider = AiProviderType.GEMINI,
        rawKey = "AIzaSyTest_Gemini_Key_Empirical",
        modelName = "gemini-2.0-flash",
        endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyTest_Gemini_Key_Empirical",
        isEnabled = true
    )

    private val noteFootballId = 101L
    private val noteVeXeId = 102L
    private val noteXeMayId = 103L
    private val noteCaTrucId = 104L
    private val noteDiAnId = 105L
    private val noteTaiChinhId = 106L

    private val sampleCatalog = listOf(
        NoteCatalogItem(noteFootballId, "Tip đi làm quay chụp bóng đá", false, 1000L),
        NoteCatalogItem(noteVeXeId, "Ghi chú vé xe", false, 2000L),
        NoteCatalogItem(noteXeMayId, "Bảo dưỡng xe máy", false, 3000L),
        NoteCatalogItem(noteCaTrucId, "Phân ca trực", false, 4000L),
        NoteCatalogItem(noteDiAnId, "Địa điểm đi ăn uống cuối tuần", false, 5000L),
        NoteCatalogItem(noteTaiChinhId, "Kế hoạch tài chính cá nhân", true, 6000L)
    )

    private val validCatalogIds = sampleCatalog.map { it.id }.toSet()

    private fun createPackagedContext(userPrompt: String): PackagedContext {
        return PackagedContext(
            userPrompt = userPrompt,
            catalogItems = sampleCatalog,
            catalogPromptSnippet = sampleCatalog.joinToString("\n") { "- [ID: ${it.id}] \"${it.title}\"" },
            notificationSummarySnippet = "(Không có thông báo)",
            fullOverviewPrompt = "DANH MỤC:\n" + sampleCatalog.joinToString("\n") { "- [ID: ${it.id}] \"${it.title}\"" }
        )
    }

    private fun createFailingRouter(): Round1Router {
        return Round1Router(
            aiCaller = { _, _ -> Result.failure(java.io.IOException("Simulation Network Timeout")) }
        )
    }

    @Test
    fun testTwoLetterVietnameseWords_FootballNote_AllPass() {
        val router = Round1Router()

        val queries = listOf(
            "Tip đi làm quay chụp bóng đá",
            "quay chụp bóng đá",
            "kinh nghiệm quay chụp bóng đá",
            "cho tôi xem tip đi làm quay chụp bóng đá",
            "đi làm quay chụp",
            "kinh nghiệm đi làm quay chụp"
        )

        for (query in queries) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue("Query '$query' should match note $noteFootballId", matched.contains(noteFootballId))
        }
    }

    @Test
    fun testTwoLetterVietnameseWords_VeXe_Passes() {
        val router = Round1Router()
        val queries = listOf("vé xe", "tìm vé xe", "ghi chú vé xe")
        for (query in queries) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue("Query '$query' should match note $noteVeXeId", matched.contains(noteVeXeId))
        }
    }

    @Test
    fun testTwoLetterVietnameseWords_CaTruc_Passes() {
        val router = Round1Router()
        val queries = listOf("ca", "ca trực", "phân ca trực")
        for (query in queries) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue("Query '$query' should match note $noteCaTrucId", matched.contains(noteCaTrucId))
        }
    }

    @Test
    fun testTwoLetterVietnameseWords_DiAn_ExactSubphrasePasses() {
        val router = Round1Router()
        val queries = listOf("đi ăn", "đi ăn uống")
        for (query in queries) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue("Query '$query' should match note $noteDiAnId", matched.contains(noteDiAnId))
        }
    }

    @Test
    fun testTwoLetterVietnameseWords_DiAn_ModifierWordRevealsHeuristicFalseNegative() {
        // CHALLENGE CASE: When user adds a natural modifier like "quán" ("quán đi ăn"),
        // the whole phrase is not contained in "địa điểm đi ăn uống cuối tuần".
        // Match count is 2 ("đi", "ăn") out of 7 title words = 28.5% < 40% threshold.
        // Thus heuristicMatchNoteIds drops it!
        val router = Round1Router()
        val query = "quán đi ăn"
        val context = createPackagedContext(query)
        val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
        // Document empirical result: false negative in local heuristic fallback
        val isMatched = matched.contains(noteDiAnId)
        assertFalse(
            "EMPIRICAL FINDING: Local heuristic drops 'quán đi ăn' for title 'Địa điểm đi ăn uống cuối tuần' due to 2/7 (28.5% < 40%) threshold",
            isMatched
        )
    }

    @Test
    fun testUnaccentedQueriesMatching() {
        val router = Round1Router()

        val unaccentedTestCases = listOf(
            "tip di lam quay chup bong da" to noteFootballId,
            "quay chup bong da" to noteFootballId,
            "kinh nghiem di lam quay chup" to noteFootballId,
            "ve xe" to noteVeXeId,
            "ghi chu ve xe" to noteVeXeId,
            "ca truc" to noteCaTrucId,
            "phan ca truc" to noteCaTrucId,
            "di an" to noteDiAnId,
            "di an uong" to noteDiAnId,
            "dia diem di an" to noteDiAnId
        )

        for ((query, expectedId) in unaccentedTestCases) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue(
                "Unaccented query '$query' must match note ID $expectedId, but got $matched",
                matched.contains(expectedId)
            )
        }
    }

    @Test
    fun testNoisyAndChattyUserQueriesWithVietnameseSlangAndPunctuation() = runTest {
        val failingRouter = createFailingRouter()

        val noisyQueries = listOf(
            "Alo trợ lý ơi, tìm giúp anh cái ghi chú về vé xe để thanh toán công tác phí nhé!" to noteVeXeId,
            "Nhắc lại giùm tôi kinh nghiệm đi làm quay chụp bóng đá của hôm nọ xem nào ???" to noteFootballId,
            "Ê cuối tuần này cả nhóm đi ăn ở đâu ấy nhỉ, có ghi chú lại không?" to noteDiAnId,
            "Tuần này ai phân ca trực thế? Kiểm tra lại ca trực giùm tui nha" to noteCaTrucId
        )

        for ((query, expectedId) in noisyQueries) {
            val context = createPackagedContext(query)
            val decision = failingRouter.route(query, context, listOf(testApiKey))
            assertTrue(
                "Noisy query '$query' must resolve to note ID $expectedId, but got ${decision.neededNoteIds}",
                decision.neededNoteIds.contains(expectedId)
            )
        }
    }

    @Test
    fun testIrrelevantQueriesProduceNoFalsePositiveMatches() {
        val router = Round1Router()

        val irrelevantQueries = listOf(
            "Xin chào AI, bạn tên là gì?",
            "Thời tiết hôm nay tại Hà Nội như thế nào?",
            "Dịch đoạn văn này sang tiếng Nhật giúp tôi",
            "1 + 1 bằng mấy?",
            "Viết cho tôi một bài thơ về mùa thu"
        )

        for (query in irrelevantQueries) {
            val context = createPackagedContext(query)
            val matched = router.heuristicMatchNoteIds(query, context, validCatalogIds)
            assertTrue(
                "Irrelevant query '$query' must not match any note IDs, but matched: $matched",
                matched.isEmpty()
            )
        }
    }

    @Test
    fun testRouterEndToEndWithFailingAiFallbackToHeuristic() = runTest {
        val failingRouter = createFailingRouter()

        val context = createPackagedContext("Tôi cần xem ghi chú vé xe")
        val decision = failingRouter.route(
            userPrompt = "Tôi cần xem ghi chú vé xe",
            packagedContext = context,
            keyPool = listOf(testApiKey)
        )

        assertEquals(listOf(noteVeXeId), decision.neededNoteIds)
        assertTrue(decision.reasoning.contains("cục bộ"))
    }

    @Test
    fun testRouterRejectsBogusAndHallucinatedNoteIds() = runTest {
        val routerWithBogusAi = Round1Router(
            aiCaller = { _, _ ->
                val json = """
                    {
                      "neededNoteIds": [101, 8888, 9999],
                      "reasoning": "Model hallucinations with nonexistent IDs"
                    }
                """.trimIndent()
                Result.success(Pair(json, testApiKey))
            }
        )

        val context = createPackagedContext("Xem ghi chú")
        val decision = routerWithBogusAi.route("Xem ghi chú", context, listOf(testApiKey))

        assertEquals(listOf(101L), decision.neededNoteIds)
        assertFalse(decision.neededNoteIds.contains(8888L))
        assertFalse(decision.neededNoteIds.contains(9999L))
    }
}
