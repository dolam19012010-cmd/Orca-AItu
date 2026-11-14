package com.orca.orcaauto.domain.assistant

import com.orca.orcaauto.data.local.NotificationDao
import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmDao
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.local.SummaryDao
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.domain.notes.FakeSharedNoteDao
import com.orca.orcaauto.domain.notes.SharedNoteRepository
import com.orca.orcaauto.domain.notes.SharedNoteRepositoryImpl
import com.orca.orcaauto.domain.prompt.PromptPersona
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Pure JUnit4 Unit Tests for [LocalRetriever] covering Milestone M1:
 * - Injection of activeAlarms into Round 2 prompt.
 * - Fallback to smartAlarmDao when activeAlarms parameter is empty.
 * - Injection of previousSummary into Round 2 prompt.
 * - Inclusion of sessionInfo in Round 2 prompt.
 * - Inclusion of notesCatalog (smart_notes) in Round 2 prompt with 0-byte private note leak.
 * - Task vs Note disambiguation rules & quick action instructions in systemInstruction.
 * - Full backward compatibility when optional parameters take defaults.
 */
class LocalRetrieverTest {

    private lateinit var fakeNoteDao: FakeSharedNoteDao
    private lateinit var fakeNotificationDao: FakeNotificationDao
    private lateinit var fakeSmartAlarmDao: FakeSmartAlarmDao
    private lateinit var noteRepository: SharedNoteRepository
    private lateinit var localRetriever: LocalRetriever

    @Before
    fun setUp() {
        fakeNoteDao = FakeSharedNoteDao()
        fakeNotificationDao = FakeNotificationDao()
        fakeSmartAlarmDao = FakeSmartAlarmDao()
        noteRepository = SharedNoteRepositoryImpl(fakeNoteDao)
        localRetriever = LocalRetriever(
            noteRepository = noteRepository,
            notificationDao = fakeNotificationDao,
            smartAlarmDao = fakeSmartAlarmDao
        )
    }

    @Test
    fun retrieve_withActiveAlarms_injectsTaskBoardSectionIntoPrompt() = runTest {
        val alarms = listOf(
            SmartAlarmEntity(
                alarmId = "ALM_DELIVERY",
                taskTitle = "Giao hàng cho khách",
                targetTimeMillis = System.currentTimeMillis() + 3600000L,
                targetDateLabel = "15:00 Hôm nay",
                status = SmartAlarmStatus.ACTIVE
            ),
            SmartAlarmEntity(
                alarmId = "ALM_MEETING",
                taskTitle = "Họp giao ban ban quản lý",
                targetTimeMillis = System.currentTimeMillis() + 7200000L,
                targetDateLabel = "09:00 Ngày mai",
                status = SmartAlarmStatus.ACTIVE
            )
        )

        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Test alarms")
        val context = localRetriever.retrieve(
            userPrompt = "Hôm nay tôi có việc gì không?",
            decision = decision,
            activeAlarms = alarms
        )

        val prompt = context.assembledRound2Prompt
        assertTrue(prompt.contains("=== DANH SÁCH BÁO THỨC & NHIỆM VỤ ĐANG HOẠT ĐỘNG (TASKBOARD / SMART ALARMS) ==="))
        assertTrue(prompt.contains("[Mã: ALM_DELIVERY] \"Giao hàng cho khách\" | Hạn: 15:00 Hôm nay"))
        assertTrue(prompt.contains("[Mã: ALM_MEETING] \"Họp giao ban ban quản lý\" | Hạn: 09:00 Ngày mai"))
        assertTrue(prompt.contains("TUYỆT ĐỐI KHÔNG coi đây là Ghi chú (Notes) và KHÔNG ĐƯỢC đòi Note ID!"))
    }

    @Test
    fun retrieve_withSmartAlarmDaoFallback_queriesActiveAlarmsFromDao() = runTest {
        fakeSmartAlarmDao.insertOrUpdate(
            SmartAlarmEntity(
                alarmId = "ALM_DAO_1",
                taskTitle = "Kiểm tra hệ thống báo khói",
                targetTimeMillis = System.currentTimeMillis() + 1800000L,
                targetDateLabel = "14:30 Hôm nay",
                status = SmartAlarmStatus.ACTIVE
            )
        )
        fakeSmartAlarmDao.insertOrUpdate(
            SmartAlarmEntity(
                alarmId = "ALM_DAO_2",
                taskTitle = "Đầu việc đã hủy",
                targetTimeMillis = System.currentTimeMillis() + 100000L,
                targetDateLabel = "Đã hủy",
                status = SmartAlarmStatus.CANCELLED
            )
        )

        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Dao fallback test")
        val context = localRetriever.retrieve(
            userPrompt = "Danh sách việc",
            decision = decision,
            activeAlarms = emptyList() // Empty: should fallback to smartAlarmDao.getActiveAlarms()
        )

        val prompt = context.assembledRound2Prompt
        assertTrue("Prompt phải chứa nhiệm vụ ACTIVE từ fakeSmartAlarmDao", prompt.contains("ALM_DAO_1"))
        assertTrue("Prompt phải chứa tên việc ACTIVE", prompt.contains("Kiểm tra hệ thống báo khói"))
        assertFalse("Prompt KHÔNG được chứa nhiệm vụ CANCELLED", prompt.contains("Đầu việc đã hủy"))
    }

    @Test
    fun retrieve_withPreviousSummary_injectsSummarySectionIntoPrompt() = runTest {
        val prevSummary = "Ca trước: Đã kiểm tra 12 tầng nhà, hệ thống quạt thông gió hoạt động bình thường lúc 18:00"
        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Summary test")

        val context = localRetriever.retrieve(
            userPrompt = "Tình hình ca trước thế nào?",
            decision = decision,
            previousSummary = prevSummary
        )

        val prompt = context.assembledRound2Prompt
        assertTrue(prompt.contains("=== BỐI CẢNH TÓM TẮT PHIÊN TRƯỚC (PREVIOUS SESSION SUMMARY) ==="))
        assertTrue(prompt.contains(prevSummary))
        assertTrue(prompt.contains("LƯU Ý: Đây là tóm tắt phiên làm việc gần nhất để bạn nắm được bối cảnh liên tục của người dùng."))
    }

    @Test
    fun retrieve_withSessionInfo_injectsSessionInfoSectionIntoPrompt() = runTest {
        val sessionInfo = "Tên phiên: Ca Đêm An Ninh [ID: session_night_guard] (🌙) | Chỉ thị riêng: Tập trung tuần tra tầng hầm"
        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Session test")

        val context = localRetriever.retrieve(
            userPrompt = "Bắt đầu làm việc",
            decision = decision,
            sessionInfo = sessionInfo
        )

        val prompt = context.assembledRound2Prompt
        assertTrue(prompt.contains("=== THÔNG TIN PHIÊN LÀM VIỆC HIỆN TẠI ==="))
        assertTrue(prompt.contains("Ca Đêm An Ninh"))
        assertTrue(prompt.contains("session_night_guard"))
        assertTrue(prompt.contains("Tập trung tuần tra tầng hầm"))
    }

    @Test
    fun retrieve_withNotesCatalog_injectsCatalogSnippetWithZeroLeakForPrivateNotes() = runTest {
        val publicNote = SharedNoteEntity(
            id = 101L,
            title = "Sổ tay an toàn lao động",
            content = "Nội dung an toàn công khai",
            isPrivate = false
        )
        val privateNote = SharedNoteEntity(
            id = 102L,
            title = "Mật mã phòng kỹ thuật",
            content = "SUPER_SECRET_PIN_9999",
            isPrivate = true
        )

        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Catalog test")
        val context = localRetriever.retrieve(
            userPrompt = "Xem danh mục ghi chú",
            decision = decision,
            notesCatalog = listOf(publicNote, privateNote)
        )

        val prompt = context.assembledRound2Prompt
        assertTrue("Prompt phải có tiêu đề mục lục kho ghi chú", prompt.contains("=== MỤC LỤC KHO GHI CHÚ (SMART NOTES CATALOG) ==="))
        assertTrue("Prompt phải chứa tên ghi chú công khai", prompt.contains("Sổ tay an toàn lao động"))
        assertTrue("Prompt phải chứa tên ghi chú riêng tư", prompt.contains("Mật mã phòng kỹ thuật"))
        assertTrue("Ghi chú riêng tư phải có nhãn bảo mật trong mục lục", prompt.contains("RIÊNG TƯ") || prompt.contains("BẢO MẬT"))
        assertFalse(
            "Tuyệt đối 0-byte nội dung mật của ghi chú riêng tư được rò rỉ trong mục lục!",
            prompt.contains("SUPER_SECRET_PIN_9999")
        )
    }

    @Test
    fun retrieve_notesCatalogFallbackToRepository_whenParameterIsEmpty() = runTest {
        noteRepository.createNote("Quy trình giao ca", "Nội dung quy trình", isPrivate = false)
        noteRepository.createNote("Mật khẩu wifi kho", "WIFI_PASS_SECRET", isPrivate = true)

        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Repo catalog fallback")
        val context = localRetriever.retrieve(
            userPrompt = "Có ghi chú nào trong kho không?",
            decision = decision,
            notesCatalog = emptyList() // triggers fallback to noteRepository.getCatalogForAi()
        )

        val prompt = context.assembledRound2Prompt
        assertTrue(prompt.contains("Quy trình giao ca"))
        assertTrue(prompt.contains("Mật khẩu wifi kho"))
        assertFalse("Nội dung riêng tư không được lọt vào prompt", prompt.contains("WIFI_PASS_SECRET"))
    }

    @Test
    fun systemInstruction_taskVsNoteDisambiguation_andQuickActionsPresent() = runTest {
        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Disambiguation test")
        val context = localRetriever.retrieve(
            userPrompt = "Sửa lại thời gian họp giao ban sang ngày mai",
            decision = decision
        )

        val sys = context.systemInstruction
        assertTrue("Phải có tiêu đề phân biệt rõ ràng giữa Nhiệm Vụ và Ghi Chú", sys.contains("PHÂN BIỆT RÕ RÀNG GIỮA NHIỆM VỤ") || sys.contains("TASKS / SMART ALARMS"))
        assertTrue("Phải cấm đòi Note ID đối với nhiệm vụ/báo thức", sys.contains("TUYỆT ĐỐI KHÔNG ĐƯỢC đòi hỏi Note ID") || sys.contains("không yêu cầu người dùng cung cấp ID ghi chú"))
        assertTrue("Phải cấm nhầm lẫn nhiệm vụ với ghi chú", sys.contains("TUYỆT ĐỐI KHÔNG ĐƯỢC nhầm lẫn") || sys.contains("TUYỆT ĐỐI KHÔNG tìm kiếm trong Kho Ghi Chú"))
        assertTrue("Phải có câu hướng dẫn thao tác nhanh cho người dùng", sys.contains("nhấn giữ thẻ để chỉnh sửa thời gian/chuông báo, vuốt phải hoàn thành, vuốt trái hủy"))
        assertFalse("Prompt systemInstruction không được chứa từ khóa hardcode PCCC trong ví dụ minh họa", sys.contains("PCCC"))
        assertTrue("Phải chứa các ví dụ công việc tổng quát", sys.contains("họp giao ban 15h") && sys.contains("uống thuốc 20h") && sys.contains("giao hàng"))
    }

    @Test
    fun backwardCompatibility_defaultParameters_emptyAlarmsAndSummary_runsGracefully() = runTest {
        val decision = Round1Decision(neededNoteIds = emptyList(), senderFilter = emptyList(), reasoning = "Backward compat")
        val context = localRetriever.retrieve(
            userPrompt = "Xin chào",
            decision = decision
        )

        val prompt = context.assembledRound2Prompt
        assertTrue(prompt.contains("=== DANH SÁCH BÁO THỨC & NHIỆM VỤ ĐANG HOẠT ĐỘNG (TASKBOARD / SMART ALARMS) ==="))
        assertTrue(prompt.contains("(Hiện không có báo thức hay nhiệm vụ nào đang chạy)"))
        assertFalse("Khi previousSummary = null, không được in phần tóm tắt", prompt.contains("=== BỐI CẢNH TÓM TẮT PHIÊN TRƯỚC"))
        assertFalse("Khi sessionInfo = null, không được in phần thông tin phiên", prompt.contains("=== THÔNG TIN PHIÊN LÀM VIỆC HIỆN TẠI"))
        assertTrue(prompt.contains("=== YÊU CẦU CỦA NGƯỜI DÙNG ==="))
        assertTrue(prompt.contains("Xin chào"))
    }
}

/**
 * Thread-safe JVM in-memory implementation of [SmartAlarmDao] for unit testing.
 */
internal class FakeSmartAlarmDao : SmartAlarmDao {
    private val lock = Any()
    private val _alarmsFlow = MutableStateFlow<Map<String, SmartAlarmEntity>>(emptyMap())
    val alarmsFlow: Flow<Map<String, SmartAlarmEntity>> = _alarmsFlow.asStateFlow()

    override suspend fun insertOrUpdate(alarm: SmartAlarmEntity): Unit = synchronized(lock) {
        _alarmsFlow.value = _alarmsFlow.value + (alarm.alarmId to alarm)
    }

    override fun getAllAlarmsFlow(): Flow<List<SmartAlarmEntity>> {
        return _alarmsFlow.map { map -> map.values.sortedBy { it.targetTimeMillis } }
    }

    override suspend fun getAllAlarms(): List<SmartAlarmEntity> = synchronized(lock) {
        _alarmsFlow.value.values.sortedBy { it.targetTimeMillis }
    }

    override fun getActiveAlarmsFlow(): Flow<List<SmartAlarmEntity>> {
        return _alarmsFlow.map { map ->
            map.values.filter { it.status == SmartAlarmStatus.ACTIVE }.sortedBy { it.targetTimeMillis }
        }
    }

    override suspend fun getActiveAlarms(): List<SmartAlarmEntity> = synchronized(lock) {
        _alarmsFlow.value.values
            .filter { it.status == SmartAlarmStatus.ACTIVE }
            .sortedBy { it.targetTimeMillis }
    }

    override suspend fun getCancelledAlarms(): List<SmartAlarmEntity> = synchronized(lock) {
        _alarmsFlow.value.values
            .filter { it.status == SmartAlarmStatus.CANCELLED }
            .sortedByDescending { it.updatedAt }
            .take(50)
    }

    override suspend fun getCompletedAlarms(): List<SmartAlarmEntity> = synchronized(lock) {
        _alarmsFlow.value.values
            .filter { it.status == SmartAlarmStatus.COMPLETED }
            .sortedByDescending { it.updatedAt }
            .take(20)
    }

    override suspend fun getAlarmById(alarmId: String): SmartAlarmEntity? = synchronized(lock) {
        _alarmsFlow.value[alarmId]
    }

    override fun getAlarmByIdFlow(alarmId: String): Flow<SmartAlarmEntity?> {
        return _alarmsFlow.map { map -> map[alarmId] }
    }

    override suspend fun updateStatus(alarmId: String, status: SmartAlarmStatus, updatedAt: Long) = synchronized(lock) {
        val existing = _alarmsFlow.value[alarmId] ?: return@synchronized
        _alarmsFlow.value = _alarmsFlow.value + (alarmId to existing.copy(status = status, updatedAt = updatedAt))
    }

    override suspend fun updateStatusByTitle(title: String, status: SmartAlarmStatus, updatedAt: Long) = synchronized(lock) {
        val target = _alarmsFlow.value.values.find { it.taskTitle.trim().equals(title.trim(), ignoreCase = true) } ?: return@synchronized
        _alarmsFlow.value = _alarmsFlow.value + (target.alarmId to target.copy(status = status, updatedAt = updatedAt))
    }

    override suspend fun getAlarmByTitle(title: String): SmartAlarmEntity? = synchronized(lock) {
        _alarmsFlow.value.values.find { it.taskTitle.trim().equals(title.trim(), ignoreCase = true) }
    }

    override suspend fun updateClockAppStaged(alarmId: String, staged: Boolean, updatedAt: Long) = synchronized(lock) {
        val existing = _alarmsFlow.value[alarmId] ?: return@synchronized
        _alarmsFlow.value = _alarmsFlow.value + (alarmId to existing.copy(isClockAppStaged = staged, updatedAt = updatedAt))
    }

    override suspend fun getUnstagedAlarmsReadyForClockApp(nowMillis: Long): List<SmartAlarmEntity> = synchronized(lock) {
        _alarmsFlow.value.values
            .filter { it.status == SmartAlarmStatus.ACTIVE && !it.isClockAppStaged && it.clockAppStageTimeMillis <= nowMillis }
    }

    override suspend fun markExpiredAlarms(nowMillis: Long) = synchronized(lock) {
        val updated = _alarmsFlow.value.toMutableMap()
        for ((id, item) in updated) {
            if (item.status == SmartAlarmStatus.PROPOSED && item.targetTimeMillis < nowMillis) {
                updated[id] = item.copy(status = SmartAlarmStatus.EXPIRED, updatedAt = nowMillis)
            }
        }
        _alarmsFlow.value = updated
    }

    override suspend fun autoMarkPastActiveAlarms(nowMillis: Long) = synchronized(lock) {
        val updated = _alarmsFlow.value.toMutableMap()
        for ((id, item) in updated) {
            if (item.status == SmartAlarmStatus.ACTIVE && item.targetTimeMillis < nowMillis - 3600000L) {
                updated[id] = item.copy(status = SmartAlarmStatus.COMPLETED, updatedAt = nowMillis)
            }
        }
        _alarmsFlow.value = updated
    }

    override suspend fun deleteOldAlarms(cutoffMillis: Long) = synchronized(lock) {
        _alarmsFlow.value = _alarmsFlow.value.filterValues {
            !(it.status in listOf(SmartAlarmStatus.COMPLETED, SmartAlarmStatus.CANCELLED, SmartAlarmStatus.EXPIRED) && it.targetTimeMillis < cutoffMillis)
        }
    }

    override suspend fun deleteAlarmById(alarmId: String) = synchronized(lock) {
        _alarmsFlow.value = _alarmsFlow.value - alarmId
    }

    fun clearAll() = synchronized(lock) {
        _alarmsFlow.value = emptyMap()
    }
}

/**
 * Thread-safe JVM in-memory implementation of [SummaryDao] for unit testing.
 */
internal class FakeSummaryDao : SummaryDao {
    private val lock = Any()
    private val nextId = AtomicLong(1L)
    private val _summariesFlow = MutableStateFlow<Map<Long, SummaryHistoryEntity>>(emptyMap())
    val summariesFlow: Flow<Map<Long, SummaryHistoryEntity>> = _summariesFlow.asStateFlow()

    override suspend fun insert(summary: SummaryHistoryEntity): Long = synchronized(lock) {
        val id = if (summary.id == 0L) nextId.getAndIncrement() else summary.id
        val entity = summary.copy(id = id)
        _summariesFlow.value = _summariesFlow.value + (id to entity)
        id
    }

    override suspend fun insertAll(summaries: List<SummaryHistoryEntity>): List<Long> {
        return summaries.map { insert(it) }
    }

    override suspend fun getAllSummaries(): List<SummaryHistoryEntity> = synchronized(lock) {
        _summariesFlow.value.values.sortedByDescending { it.timestamp }
    }

    override fun getAllSummariesFlow(): Flow<List<SummaryHistoryEntity>> {
        return _summariesFlow.map { map -> map.values.sortedByDescending { it.timestamp } }
    }

    override suspend fun getLatestSummary(): SummaryHistoryEntity? = synchronized(lock) {
        _summariesFlow.value.values.maxByOrNull { it.timestamp }
    }

    override fun getLatestSummaryFlow(): Flow<SummaryHistoryEntity?> {
        return _summariesFlow.map { map -> map.values.maxByOrNull { it.timestamp } }
    }

    override suspend fun getLatestSummaryForSession(sessionId: String): SummaryHistoryEntity? = synchronized(lock) {
        _summariesFlow.value.values
            .filter { it.sessionId == sessionId || (sessionId == SummarySessionConfig.MAIN_SESSION_ID && it.sessionId.isBlank()) }
            .maxByOrNull { it.timestamp }
    }

    override fun getLatestSummaryForSessionFlow(sessionId: String): Flow<SummaryHistoryEntity?> {
        return _summariesFlow.map { map ->
            map.values
                .filter { it.sessionId == sessionId || (sessionId == SummarySessionConfig.MAIN_SESSION_ID && it.sessionId.isBlank()) }
                .maxByOrNull { it.timestamp }
        }
    }

    override fun getSummariesForSessionFlow(sessionId: String): Flow<List<SummaryHistoryEntity>> {
        return _summariesFlow.map { map ->
            map.values
                .filter { it.sessionId == sessionId || (sessionId == SummarySessionConfig.MAIN_SESSION_ID && it.sessionId.isBlank()) }
                .sortedByDescending { it.timestamp }
        }
    }

    override suspend fun getSummariesInRange(fromTimestamp: Long, toTimestamp: Long): List<SummaryHistoryEntity> = synchronized(lock) {
        _summariesFlow.value.values
            .filter { it.timestamp in fromTimestamp..toTimestamp }
            .sortedBy { it.timestamp }
    }

    override suspend fun deleteOldSummaries(beforeTimestamp: Long) = synchronized(lock) {
        _summariesFlow.value = _summariesFlow.value.filterValues { it.timestamp >= beforeTimestamp }
    }

    override suspend fun deleteById(id: Long) = synchronized(lock) {
        _summariesFlow.value = _summariesFlow.value - id
    }

    override suspend fun deleteBySessionId(sessionId: String): Int = synchronized(lock) {
        val toRemove = _summariesFlow.value.values.filter { it.sessionId == sessionId }.map { it.id }
        _summariesFlow.value = _summariesFlow.value - toRemove.toSet()
        toRemove.size
    }

    fun clearAll() = synchronized(lock) {
        _summariesFlow.value = emptyMap()
        nextId.set(1L)
    }
}
