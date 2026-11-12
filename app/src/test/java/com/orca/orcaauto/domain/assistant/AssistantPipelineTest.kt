package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.AiProviderType
import com.orca.orcaauto.data.remote.StreamingAiClient
import com.orca.orcaauto.domain.notes.FakeSharedNoteDao
import com.orca.orcaauto.domain.notes.NoteCatalogItem
import com.orca.orcaauto.domain.notes.RetrievedNote
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Method

/**
 * Comprehensive Unit Test Suite for [AssistantPipeline] and its 5 Stages:
 * - Stage 1: [ContextPacker] lightweight index packing (0-byte private note leak).
 * - Stage 2: [Round1Router] routing resilience (JSON with code fences, regex fallback, keyword fallback, bogus ID rejection).
 * - Stage 3: [LocalRetriever] local retrieval with Sealed Pointer invariant.
 * - Stage 4: [StreamingAiClient] SSE stream with live command tag stripping.
 * - Stage 5: Post-processing command execution (SHOW_NOTE, CREATE, APPEND, and SecurityViolation on private UPDATE/DELETE).
 */
class AssistantPipelineTest {

    private lateinit var fakeNoteDao: FakeSharedNoteDao
    private lateinit var fakeNotificationDao: FakeNotificationDao
    private lateinit var noteRepository: SharedNoteRepository
    private lateinit var contextPacker: ContextPacker
    private lateinit var round1Router: Round1Router
    private lateinit var localRetriever: LocalRetriever

    private val testApiKey = ApiKeyConfig(
        provider = AiProviderType.GEMINI,
        rawKey = "AIzaSyTest_Gemini_Key_123456",
        modelName = "gemini-2.0-flash",
        endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=AIzaSyTest_Gemini_Key_123456",
        isEnabled = true
    )

    @Before
    fun setUp() {
        fakeNoteDao = FakeSharedNoteDao()
        fakeNotificationDao = FakeNotificationDao()
        noteRepository = SharedNoteRepositoryImpl(fakeNoteDao)
        contextPacker = ContextPacker(noteRepository, fakeNotificationDao)
        round1Router = createMockRound1Router()
        localRetriever = LocalRetriever(noteRepository, fakeNotificationDao)
    }

    private fun createMockRound1Router(
        customDecision: ((userPrompt: String, routerPrompt: String) -> String)? = null
    ): Round1Router {
        return Round1Router(
            aiCaller = { keyPool, routerPrompt ->
                val responseJson = if (customDecision != null) {
                    customDecision(routerPrompt, routerPrompt)
                } else {
                    // Smart keyword-matching in-memory AI router:
                    // 1. Extract user prompt from router prompt
                    val userPromptRegex = Regex("""Yêu cầu của người dùng:\s*"([^"]*)"""")
                    val userPrompt = userPromptRegex.find(routerPrompt)?.groupValues?.get(1)?.lowercase().orEmpty()

                    // 2. Extract catalog items: - [ID: 1] "Title" [tag]
                    val noteRegex = Regex("""-\s*\[ID:\s*(\d+)\]\s*"([^"]+)"""")
                    val matchingIds = mutableListOf<Long>()

                    for (match in noteRegex.findAll(routerPrompt)) {
                        val id = match.groupValues[1].toLongOrNull() ?: continue
                        val title = match.groupValues[2].lowercase()

                        val titleWords = title.split(Regex("""[\s,._\-:;!?]+""")).filter { it.length >= 3 }
                        val isMatched = userPrompt.contains(title) ||
                                titleWords.any { word -> userPrompt.contains(word) } ||
                                userPrompt.split(Regex("""[\s,._\-:;!?]+""")).filter { it.length >= 3 }.any { promptWord -> title.contains(promptWord) }

                        if (isMatched) {
                            matchingIds.add(id)
                        }
                    }

                    val jsonArray = JSONArray()
                    matchingIds.distinct().forEach { jsonArray.put(it) }

                    JSONObject().apply {
                        put("neededNoteIds", jsonArray)
                        put("senderFilter", JSONArray())
                        put("reasoning", "Mock in-memory router selected ${matchingIds.size} notes")
                    }.toString()
                }

                val activeKey = keyPool.firstOrNull() ?: testApiKey
                Result.success(Pair(responseJson, activeKey))
            }
        )
    }

    private fun createMockStreamingClient(sseBody: String): StreamingAiClient {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val body = sseBody.toResponseBody("text/event-stream".toMediaType())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            })
            .build()
        return StreamingAiClient(httpClient = okHttpClient)
    }

    // =========================================================================
    // 1. STAGE 1: CONTEXT PACKER TESTS
    // =========================================================================

    @Test
    fun stage1_contextPacker_packsLightweightCatalog_zeroPrivateContentLeak() = runTest {
        // Create a public note and a private note with sensitive password
        val secretPassword = "SUPER_SECRET_TOKEN_999888"
        noteRepository.createNote("Kế hoạch tuần", "Nội dung thường", isPrivate = false)
        noteRepository.createNote("Mật khẩu ngân hàng", secretPassword, isPrivate = true)

        fakeNotificationDao.insert(
            NotificationEntity(
                appPackage = "com.zing.zalo",
                appName = "Zalo",
                sender = "Sếp Hùng",
                content = "Gửi báo cáo cho anh trước 17h nhé",
                timestamp = System.currentTimeMillis() - 1000L
            )
        )

        val packaged = contextPacker.pack("Cho tôi xem mật khẩu ngân hàng")

        // 1. Check catalog snippet contains note titles and privacy flags
        assertTrue(packaged.catalogPromptSnippet.contains("Mật khẩu ngân hàng"))
        assertTrue(packaged.catalogPromptSnippet.contains("[🔒 RIÊNG TƯ - CHỈ POINTER CỤC BỘ]"))

        // 2. CRITICAL INVARIANT: Zero bytes of private note content in Stage 1 prompt
        assertFalse(
            "Nội dung ghi chú riêng tư tuyệt đối KHÔNG được xuất hiện trong catalog snippet",
            packaged.catalogPromptSnippet.contains(secretPassword)
        )
        assertFalse(
            "Nội dung ghi chú riêng tư tuyệt đối KHÔNG được xuất hiện trong fullOverviewPrompt",
            packaged.fullOverviewPrompt.contains(secretPassword)
        )

        // 3. Check notifications grouped snippet
        assertTrue(packaged.notificationSummarySnippet.contains("Zalo: Sếp Hùng: 1 tin nhắn"))
    }

    @Test
    fun stage1_contextPacker_emptyCatalogAndNoNotifications_handlesGracefully() = runTest {
        val packaged = contextPacker.pack("Xin chào AI")
        assertTrue(packaged.catalogPromptSnippet.contains("(Kho ghi chú hiện tại đang trống)"))
        assertTrue(packaged.notificationSummarySnippet.contains("(Không có thông báo/tin nhắn mới trong 24 giờ qua)"))
    }

    // =========================================================================
    // 2. STAGE 2: ROUND 1 ROUTER RESILIENCE TESTS
    // =========================================================================

    @Test
    fun stage2_round1Router_parseJsonWithMarkdownCodeFences() {
        val rawAiResponse = """
            ```json
            {
              "neededNoteIds": [1, 2],
              "senderFilter": ["Zalo", "Telegram"],
              "reasoning": "Người dùng cần tra cứu ghi chú 1 và 2"
            }
            ```
        """.trimIndent()

        val packaged = PackagedContext(
            userPrompt = "Xem ghi chú 1 và 2",
            catalogItems = listOf(
                NoteCatalogItem(1L, "Ghi chú 1", false, 1000L),
                NoteCatalogItem(2L, "Ghi chú 2", true, 2000L),
                NoteCatalogItem(3L, "Ghi chú 3", false, 3000L)
            ),
            catalogPromptSnippet = "",
            notificationSummarySnippet = "",
            fullOverviewPrompt = ""
        )

        val decision = invokeParseRouterResponse(
            router = round1Router,
            rawResponse = rawAiResponse,
            userPrompt = packaged.userPrompt,
            packagedContext = packaged,
            validCatalogIds = setOf(1L, 2L, 3L)
        )

        assertEquals(listOf(1L, 2L), decision.neededNoteIds)
        assertEquals(listOf("Zalo", "Telegram"), decision.senderFilter)
        assertEquals("Người dùng cần tra cứu ghi chú 1 và 2", decision.reasoning)
    }

    @Test
    fun stage2_round1Router_regexFallback_extractsIdsFromLooseText() {
        val rawAiResponse = "Tôi nhận thấy bạn đang hỏi về ghi chú #2 và note: 3. Bạn nên xem qua chúng."

        val packaged = PackagedContext(
            userPrompt = "Tìm lại thông tin",
            catalogItems = listOf(
                NoteCatalogItem(1L, "Note 1", false, 1000L),
                NoteCatalogItem(2L, "Note 2", true, 2000L),
                NoteCatalogItem(3L, "Note 3", false, 3000L)
            ),
            catalogPromptSnippet = "",
            notificationSummarySnippet = "",
            fullOverviewPrompt = ""
        )

        val decision = invokeParseRouterResponse(
            router = round1Router,
            rawResponse = rawAiResponse,
            userPrompt = packaged.userPrompt,
            packagedContext = packaged,
            validCatalogIds = setOf(1L, 2L, 3L)
        )

        assertEquals(listOf(2L, 3L), decision.neededNoteIds)
        assertTrue(decision.reasoning.contains("biểu thức chính quy"))
    }

    @Test
    fun stage2_round1Router_bogusIdsFilteredOut() {
        // AI hallucinates ID 99999 which does NOT exist in catalog
        val rawAiResponse = """
            {"neededNoteIds": [1, 99999], "reasoning": "Chọn ID có sẵn và ID bịa"}
        """.trimIndent()

        val packaged = PackagedContext(
            userPrompt = "Xem ghi chú",
            catalogItems = listOf(NoteCatalogItem(1L, "Ghi chú hợp lệ", false, 1000L)),
            catalogPromptSnippet = "",
            notificationSummarySnippet = "",
            fullOverviewPrompt = ""
        )

        val decision = invokeParseRouterResponse(
            router = round1Router,
            rawResponse = rawAiResponse,
            userPrompt = packaged.userPrompt,
            packagedContext = packaged,
            validCatalogIds = setOf(1L)
        )

        assertEquals(listOf(1L), decision.neededNoteIds)
        assertFalse("ID ảo 99999 không được có mặt trong neededNoteIds", decision.neededNoteIds.contains(99999L))
    }

    @Test
    fun stage2_round1Router_localKeywordHeuristicFallback() {
        // Raw response is empty or failed
        val rawAiResponse = ""
        val packaged = PackagedContext(
            userPrompt = "Cho tôi xem lại kế hoạch du lịch đà nẵng đã ghi",
            catalogItems = listOf(
                NoteCatalogItem(1L, "Kế hoạch du lịch Đà Nẵng", false, 1000L),
                NoteCatalogItem(2L, "Mua sắm chợ Cồn", false, 2000L)
            ),
            catalogPromptSnippet = "",
            notificationSummarySnippet = "",
            fullOverviewPrompt = ""
        )

        val decision = invokeParseRouterResponse(
            router = round1Router,
            rawResponse = rawAiResponse,
            userPrompt = packaged.userPrompt,
            packagedContext = packaged,
            validCatalogIds = setOf(1L, 2L)
        )

        assertEquals(listOf(1L), decision.neededNoteIds)
        assertTrue(decision.reasoning.contains("bộ lọc từ khóa cục bộ"))
    }

    @Test
    fun stage2_round1Router_inMemoryAiCaller_endToEndRoutingSuccess() = runTest {
        val packaged = PackagedContext(
            userPrompt = "Xem lại nhật ký bí mật",
            catalogItems = listOf(
                NoteCatalogItem(10L, "Kế hoạch tuần", false, 1000L),
                NoteCatalogItem(20L, "Nhật ký bí mật", true, 2000L)
            ),
            catalogPromptSnippet = "- [ID: 10] \"Kế hoạch tuần\" [Thường]\n- [ID: 20] \"Nhật ký bí mật\" [🔒 RIÊNG TƯ]",
            notificationSummarySnippet = "(Không có thông báo)",
            fullOverviewPrompt = "DANH MỤC:\n- [ID: 10] \"Kế hoạch tuần\"\n- [ID: 20] \"Nhật ký bí mật\"\nYêu cầu của người dùng: \"Xem lại nhật ký bí mật\""
        )

        val router = createMockRound1Router()
        val decision = router.route("Xem lại nhật ký bí mật", packaged, listOf(testApiKey))

        assertEquals(listOf(20L), decision.neededNoteIds)
        assertFalse(decision.neededNoteIds.contains(10L))
        assertTrue(decision.reasoning.contains("Mock in-memory router"))
    }

    @Test
    fun stage2_round1Router_inMemoryAiCaller_failureFallsBackToHeuristic() = runTest {
        val packaged = PackagedContext(
            userPrompt = "Kế hoạch tuần tới của tôi",
            catalogItems = listOf(
                NoteCatalogItem(10L, "Kế hoạch tuần", false, 1000L),
                NoteCatalogItem(20L, "Nhật ký bí mật", true, 2000L)
            ),
            catalogPromptSnippet = "- [ID: 10] \"Kế hoạch tuần\" [Thường]",
            notificationSummarySnippet = "(Không có thông báo)",
            fullOverviewPrompt = "DANH MỤC:\n- [ID: 10] \"Kế hoạch tuần\"\nYêu cầu của người dùng: \"Kế hoạch tuần tới của tôi\""
        )

        val failingRouter = Round1Router(
            aiCaller = { _, _ -> Result.failure(java.io.IOException("Mô phỏng mạng lỗi")) }
        )

        val decision = failingRouter.route("Kế hoạch tuần tới của tôi", packaged, listOf(testApiKey))

        assertEquals(listOf(10L), decision.neededNoteIds)
        assertTrue(decision.reasoning.contains("cục bộ"))
    }

    // =========================================================================
    // 3. STAGE 3: LOCAL RETRIEVER & SEALED POINTER TESTS
    // =========================================================================

    @Test
    fun stage3_localRetriever_enforcesSealedPointerInvariant() = runTest {
        val publicContent = "Nội dung công khai hoàn toàn"
        val privateSecretContent = "SECRET_PIN_CODE_8888"

        val publicId = noteRepository.createNote("Ghi chú công việc", publicContent, isPrivate = false)
        val privateId = noteRepository.createNote("Ghi chú riêng tư", privateSecretContent, isPrivate = true)

        val decision = Round1Decision(
            neededNoteIds = listOf(publicId, privateId),
            senderFilter = emptyList(),
            reasoning = "Cần cả 2 ghi chú"
        )

        val retrieved = localRetriever.retrieve("Cho tôi xem cả 2 ghi chú", decision)

        // 1. Retrieved notes list check
        assertEquals(2, retrieved.retrievedNotes.size)
        val publicNote = retrieved.retrievedNotes.filterIsInstance<RetrievedNote.PublicNote>().first()
        val privateNote = retrieved.retrievedNotes.filterIsInstance<RetrievedNote.SealedPrivatePointer>().first()

        assertEquals(publicContent, publicNote.content)
        assertTrue(privateNote.pointerNotice.contains("bảo mật cục bộ", ignoreCase = true))

        // 2. Round 2 assembled prompt invariant
        assertTrue(retrieved.assembledRound2Prompt.contains(publicContent))
        assertFalse(
            "assembledRound2Prompt tuyệt đối không chứa nội dung bí mật của ghi chú riêng tư!",
            retrieved.assembledRound2Prompt.contains(privateSecretContent)
        )

        // 3. Check instructions forbid UPDATE/DELETE on private notes
        assertTrue(retrieved.systemInstruction.contains("CẤM dùng trên ghi chú riêng tư"))
    }

    // =========================================================================
    // 4. STAGE 4 & 5: END-TO-END ASSISTANT PIPELINE EXECUTION
    // =========================================================================

    @Test
    fun stage4And5_endToEndPipeline_showNoteAndAppend_successFlow() = runBlocking {
        val note1Id = noteRepository.createNote("Kế hoạch Alpha", "Nội dung ban đầu", isPrivate = false)
        val note2Id = noteRepository.createNote("Nhật ký bí mật", "Bí mật riêng tư", isPrivate = true)

        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Đang suy nghĩ về yêu cầu...","thought":true}]}}]}
            data: {"candidates":[{"content":{"parts":[{"text":"Chào bạn! Đây là ghi chú của bạn: [SHOW_NOTE:$note2Id] và tôi đã bổ sung [APPEND:$note2Id:Đã hoàn thành mục 1]"}]}}]}
            data: [DONE]
        """.trimIndent()

        val streamingClient = createMockStreamingClient(sseResponse)

        val pipeline = AssistantPipeline(
            noteRepository = noteRepository,
            streamingAiClient = streamingClient,
            contextPacker = contextPacker,
            round1Router = round1Router,
            localRetriever = localRetriever,
            keyPoolProvider = { listOf(testApiKey) }
        )

        val emittedStates = pipeline.askStreaming("Xem và cập nhật nhật ký bí mật").toList()

        // 1. Verify all 5 stages were traversed
        val steps = emittedStates.map { it.step }
        assertTrue(steps.contains(AssistantStep.STAGE_1_PACKING_INDEX))
        assertTrue(steps.contains(AssistantStep.STAGE_2_ROUND_1_ROUTING))
        assertTrue(steps.contains(AssistantStep.STAGE_3_LOCAL_RETRIEVAL))
        assertTrue(steps.contains(AssistantStep.STAGE_4_STREAMING_ANSWER))
        assertTrue(steps.contains(AssistantStep.STAGE_5_EXECUTING_ACTIONS))
        assertTrue(steps.contains(AssistantStep.COMPLETED))

        // 2. Verify final completed state
        val finalState = emittedStates.last()
        assertEquals(AssistantStep.COMPLETED, finalState.step)
        assertNull(finalState.errorMessage)

        // 3. Command stripping verification: raw command tags are cleaned from UI text
        assertFalse(finalState.streamedContentClean.contains("[SHOW_NOTE:$note2Id]"))
        assertFalse(finalState.streamedContentClean.contains("[APPEND:"))
        assertTrue(finalState.streamedContentClean.contains("Chào bạn! Đây là ghi chú của bạn:"))

        // 4. Local note card rendering: shownNoteCards contains note2
        assertEquals(1, finalState.shownNoteCards.size)
        assertEquals(note2Id, finalState.shownNoteCards[0].id)

        // 5. Execution result verification
        assertEquals(2, finalState.executedActions.size)
        assertTrue(finalState.executedActions.all { it.isSuccess })

        // 6. DB verification: APPEND succeeded on private note with newline delimiter
        val updatedNote2 = noteRepository.getNoteById(note2Id)
        assertNotNull(updatedNote2)
        assertEquals("Bí mật riêng tư\n\nĐã hoàn thành mục 1", updatedNote2!!.content)
        assertTrue(updatedNote2.isPrivate)
    }

    @Test
    fun stage5_securityViolationOnPrivateNote_updateAndDeleteBlockedSafely() = runBlocking {
        val privateId = noteRepository.createNote("Tài khoản bí mật", "Dữ liệu tối mật", isPrivate = true)

        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Đang kiểm tra bảo mật...","thought":true}]}}]}
            data: {"candidates":[{"content":{"parts":[{"text":"Thực hiện thao tác: [UPDATE:$privateId:Hack nội dung] [DELETE:$privateId]"}]}}]}
            data: [DONE]
        """.trimIndent()

        val streamingClient = createMockStreamingClient(sseResponse)

        val pipeline = AssistantPipeline(
            noteRepository = noteRepository,
            streamingAiClient = streamingClient,
            contextPacker = contextPacker,
            round1Router = round1Router,
            localRetriever = localRetriever,
            keyPoolProvider = { listOf(testApiKey) }
        )

        val states = pipeline.askStreaming("Sửa và xóa ghi chú bí mật").toList()
        val finalState = states.last()

        assertEquals(AssistantStep.COMPLETED, finalState.step)
        assertEquals(2, finalState.executedActions.size)

        // Both UPDATE and DELETE on private note must report security violation
        val updateAction = finalState.executedActions[0]
        assertFalse("AI UPDATE trên ghi chú riêng tư phải thất bại", updateAction.isSuccess)
        assertTrue(updateAction.message.contains("VIOLATION"))

        val deleteAction = finalState.executedActions[1]
        assertFalse("AI DELETE trên ghi chú riêng tư phải thất bại", deleteAction.isSuccess)
        assertTrue(deleteAction.message.contains("VIOLATION"))

        // Verify private note in DB was NOT modified and NOT deleted
        val noteInDb = noteRepository.getNoteById(privateId)
        assertNotNull(noteInDb)
        assertEquals("Dữ liệu tối mật", noteInDb!!.content)
        assertFalse(noteInDb.isDeleted)
    }

    // =========================================================================
    // 5. ERROR PATHS & BOUNDARY CONDITIONS
    // =========================================================================

    @Test
    fun pipeline_blankUserPrompt_emitsErrorState() = runTest {
        val pipeline = AssistantPipeline(
            noteRepository = noteRepository,
            contextPacker = contextPacker,
            round1Router = round1Router,
            localRetriever = localRetriever,
            keyPoolProvider = { listOf(testApiKey) }
        )

        val states = pipeline.askStreaming("   \t\n  ").toList()
        assertEquals(1, states.size)
        assertEquals(AssistantStep.ERROR, states[0].step)
        assertTrue(states[0].errorMessage!!.contains("không được để trống"))
    }

    @Test
    fun pipeline_emptyKeyPool_emitsErrorState() = runTest {
        val pipeline = AssistantPipeline(
            noteRepository = noteRepository,
            contextPacker = contextPacker,
            round1Router = round1Router,
            localRetriever = localRetriever,
            keyPoolProvider = { emptyList() }
        )

        val states = pipeline.askStreaming("Xin chào").toList()
        assertEquals(1, states.size)
        assertEquals(AssistantStep.ERROR, states[0].step)
        assertTrue(states[0].errorMessage!!.contains("Chưa cấu hình API Key"))
    }

    @Test
    fun pipeline_coroutineCancellation_propagatesCleanly() = runTest {
        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"text":"Bắt đầu trả lời..."}]}}]}
            data: [DONE]
        """.trimIndent()

        val streamingClient = createMockStreamingClient(sseResponse)
        val pipeline = AssistantPipeline(
            noteRepository = noteRepository,
            streamingAiClient = streamingClient,
            contextPacker = contextPacker,
            round1Router = round1Router,
            localRetriever = localRetriever,
            keyPoolProvider = { listOf(testApiKey) }
        )

        val job = launch {
            pipeline.askStreaming("Hủy giữa chừng").collect {
                // Collect states
            }
        }

        // Cancel job immediately to simulate user closing chat sheet
        job.cancelAndJoin()
        assertTrue("Job phải kết thúc ở trạng thái cancelled", job.isCancelled)
    }

    // =========================================================================
    // REFLECTION HELPER
    // =========================================================================

    private fun invokeParseRouterResponse(
        router: Round1Router,
        rawResponse: String,
        userPrompt: String,
        packagedContext: PackagedContext,
        validCatalogIds: Set<Long>
    ): Round1Decision {
        val method: Method = Round1Router::class.java.getDeclaredMethod(
            "parseRouterResponse",
            String::class.java,
            String::class.java,
            PackagedContext::class.java,
            Set::class.java
        )
        method.isAccessible = true
        return method.invoke(router, rawResponse, userPrompt, packagedContext, validCatalogIds) as Round1Decision
    }
}

/**
 * In-memory Fake implementation of [NotificationDao] for testing.
 */
internal class FakeNotificationDao : NotificationDao {
    private val lock = Any()
    private val notifications = mutableListOf<NotificationEntity>()
    private var nextId = 1L

    override suspend fun insert(notification: NotificationEntity): Long = synchronized(lock) {
        val id = if (notification.id == 0L) nextId++ else notification.id
        notifications.add(notification.copy(id = id))
        id
    }

    override suspend fun getUnprocessedNotifications(): List<NotificationEntity> {
        return notifications.filter { !it.isProcessed }.sortedBy { it.timestamp }
    }

    override suspend fun getUnprocessedNotificationsSince(sinceTimestamp: Long): List<NotificationEntity> {
        return notifications.filter { !it.isProcessed && it.timestamp >= sinceTimestamp }.sortedBy { it.timestamp }
    }

    override suspend fun getNotificationsSinceWithLimit(sinceTimestamp: Long, limit: Int): List<NotificationEntity> {
        return notifications.filter { it.timestamp >= sinceTimestamp }
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    override suspend fun getTotalNotificationCount(): Int = notifications.size

    override suspend fun getOldestProcessedIds(count: Int): List<Long> {
        return notifications.filter { it.isProcessed }.sortedBy { it.timestamp }.take(count).map { it.id }
    }

    override suspend fun getOldestNotificationIds(count: Int): List<Long> {
        return notifications.sortedBy { it.timestamp }.take(count).map { it.id }
    }

    override suspend fun deleteByIds(ids: List<Long>): Int {
        val initial = notifications.size
        notifications.removeAll { it.id in ids }
        return initial - notifications.size
    }

    override suspend fun getNotificationsSince(sinceTimestamp: Long): List<NotificationEntity> {
        return notifications.filter { it.timestamp >= sinceTimestamp }.sortedByDescending { it.timestamp }
    }

    override fun getUnprocessedCountFlow(): Flow<Int> = flowOf(notifications.count { !it.isProcessed })

    override suspend fun getUnprocessedCount(): Int = notifications.count { !it.isProcessed }

    override suspend fun countRecentDuplicates(appPackage: String, sender: String, content: String, afterTimestamp: Long): Int {
        return notifications.count { it.appPackage == appPackage && it.sender == sender && it.content == content && it.timestamp >= afterTimestamp }
    }

    override suspend fun markAsProcessed(ids: List<Long>) {
        val targetIds = ids.toSet()
        for (i in notifications.indices) {
            if (notifications[i].id in targetIds) {
                notifications[i] = notifications[i].copy(isProcessed = true)
            }
        }
    }

    override fun getRecentNotificationsFlow(): Flow<List<NotificationEntity>> = flowOf(notifications.sortedByDescending { it.timestamp }.take(100))

    override suspend fun getRecentNotifications(limit: Int): List<NotificationEntity> {
        return notifications.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun deleteOldNotifications(beforeTimestamp: Long): Int {
        val initial = notifications.size
        notifications.removeAll { it.timestamp < beforeTimestamp }
        return initial - notifications.size
    }

    override suspend fun deleteOldProcessed(beforeTimestamp: Long) {
        notifications.removeAll { it.isProcessed && it.timestamp < beforeTimestamp }
    }

    override suspend fun clearAllNotifications() {
        notifications.clear()
    }
}
