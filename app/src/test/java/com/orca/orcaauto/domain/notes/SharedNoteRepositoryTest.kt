package com.orca.orcaauto.domain.notes

import com.orca.orcaauto.data.local.SharedNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Test Suite for [SharedNoteRepository] and [SharedNoteRepositoryImpl].
 *
 * Verifies:
 * 1. Sealed Pointer Security Invariants (zero-byte private content egress, UPDATE/DELETE blocked for AI,
 *    SHOW_NOTE and APPEND permitted).
 * 2. User CRUD and lifecycle operations (create, update, soft-delete, restore, permanent delete, pin, clear trash).
 * 3. Adversarial boundary conditions (colons in title, Vietnamese diacritics, empty catalog, invalid IDs).
 */
class SharedNoteRepositoryTest {

    private lateinit var fakeDao: FakeSharedNoteDao
    private lateinit var repository: SharedNoteRepository

    @Before
    fun setUp() {
        fakeDao = FakeSharedNoteDao()
        repository = SharedNoteRepositoryImpl(fakeDao)
    }

    // =========================================================================
    // 1. SEALED POINTER SECURITY INVARIANTS
    // =========================================================================

    @Test
    fun getCatalogForAi_strictlyZeroBytesContent_andExcludesDeletedNotes() = runTest {
        // Create public note, private note, and a deleted note
        repository.createNote(
            title = "Kế hoạch tuần",
            content = "Nội dung công khai rất dài cần bảo mật",
            isPrivate = false
        )
        val privateId = repository.createNote(
            title = "Mật khẩu ngân hàng",
            content = "TOP_SECRET_PASSWORD_123456",
            isPrivate = true
        )
        val deletedId = repository.createNote(
            title = "Ghi chú rác",
            content = "Sắp bị xóa",
            isPrivate = false
        )
        repository.softDeleteNote(deletedId)

        val catalog = repository.getCatalogForAi()

        // Catalog size should be 2 (deleted note must NOT be in catalog)
        assertEquals(2, catalog.size)

        val privateItem = catalog.firstOrNull { it.id == privateId }
        assertNotNull("Ghi chú riêng tư phải có mặt trong danh mục", privateItem)
        assertEquals("Mật khẩu ngân hàng", privateItem!!.title)
        assertTrue("isPrivate cờ phải là true", privateItem.isPrivate)

        // Reflection/structure proof: NoteCatalogItem has NO content property at all
        val fields = NoteCatalogItem::class.java.declaredFields.map { it.name }
        assertFalse("NoteCatalogItem không được chứa trường 'content'", fields.contains("content"))
    }

    @Test
    fun retrieveNotesForAi_privateNote_returnsSealedPrivatePointerWithNoContent() = runTest {
        val publicId = repository.createNote(
            title = "Công việc hôm nay",
            content = "1. Gửi email\n2. Họp team",
            isPrivate = false
        )
        val privateId = repository.createNote(
            title = "Nhật ký cá nhân",
            content = "NỘI DUNG TUYỆT MẬT KHÔNG ĐƯỢC LỌT RA NGOÀI",
            isPrivate = true
        )

        val retrieved = repository.retrieveNotesForAi(listOf(publicId, privateId))
        assertEquals(2, retrieved.size)

        val publicNote = retrieved.filterIsInstance<RetrievedNote.PublicNote>().firstOrNull()
        assertNotNull("Ghi chú thường phải là PublicNote", publicNote)
        assertEquals(publicId, publicNote!!.id)
        assertEquals("1. Gửi email\n2. Họp team", publicNote.content)
        assertFalse(publicNote.isPrivate)

        val privateNote = retrieved.filterIsInstance<RetrievedNote.SealedPrivatePointer>().firstOrNull()
        assertNotNull("Ghi chú riêng tư PHẢI là SealedPrivatePointer", privateNote)
        assertEquals(privateId, privateNote!!.id)
        assertEquals("Nhật ký cá nhân", privateNote.title)
        assertTrue(privateNote.isPrivate)
        assertTrue(privateNote.pointerNotice.contains("bảo mật cục bộ", ignoreCase = true))

        // Reflection proof: SealedPrivatePointer has NO content field
        val privateFields = RetrievedNote.SealedPrivatePointer::class.java.declaredFields.map { it.name }
        assertFalse("SealedPrivatePointer tuyệt đối không có trường 'content'", privateFields.contains("content"))
    }

    @Test
    fun executeAiCommand_updateOnPrivateNote_strictlyRejectedAsSecurityViolation() = runTest {
        val privateId = repository.createNote(
            title = "Mã PIN ATM",
            content = "9999",
            isPrivate = true
        )

        val result = repository.executeAiCommand(
            NoteCommand.Update(
                noteId = privateId,
                newContent = "0000 (AI cố gắng ghi đè)"
            )
        )

        assertTrue(
            "AI UPDATE trên ghi chú riêng tư phải trả về SecurityViolation",
            result is CommandResult.SecurityViolation
        )
        val violation = result as CommandResult.SecurityViolation
        assertTrue(violation.violationReason.contains("VIOLATION"))

        // Verify original content was NOT altered
        val noteInDb = repository.getNoteById(privateId)
        assertNotNull(noteInDb)
        assertEquals("9999", noteInDb!!.content)
    }

    @Test
    fun executeAiCommand_deleteOnPrivateNote_strictlyRejectedAsSecurityViolation() = runTest {
        val privateId = repository.createNote(
            title = "Hợp đồng tối mật",
            content = "Điều khoản bí mật...",
            isPrivate = true
        )

        val result = repository.executeAiCommand(NoteCommand.Delete(noteId = privateId))

        assertTrue(
            "AI DELETE trên ghi chú riêng tư phải trả về SecurityViolation",
            result is CommandResult.SecurityViolation
        )
        val violation = result as CommandResult.SecurityViolation
        assertTrue(violation.violationReason.contains("VIOLATION"))

        // Verify note is still active (not soft deleted)
        val noteInDb = repository.getNoteById(privateId)
        assertNotNull(noteInDb)
        assertFalse("Ghi chú riêng tư không được bị xóa", noteInDb!!.isDeleted)
    }

    @Test
    fun executeAiCommand_showNoteOnPrivateNote_succeedsAndReturnsLocalEntity() = runTest {
        val privateId = repository.createNote(
            title = "Tài khoản bảo hiểm",
            content = "Số thẻ: BH-987654",
            isPrivate = true
        )

        val result = repository.executeAiCommand(NoteCommand.Show(noteId = privateId))

        assertTrue("SHOW_NOTE trên ghi chú riêng tư phải thành công", result is CommandResult.Success)
        val success = result as CommandResult.Success
        assertEquals(privateId, success.affectedNoteId)
        assertNotNull(success.payload)
        assertEquals("Số thẻ: BH-987654", success.payload!!.content)
        assertTrue(success.payload!!.isPrivate)
    }

    @Test
    fun executeAiCommand_appendOnPrivateNote_succeedsWithNewlineDelimiter() = runTest {
        val privateId = repository.createNote(
            title = "Nhật ký chi tiêu",
            content = "Ngày 1: 50.000đ",
            isPrivate = true
        )

        val result = repository.executeAiCommand(
            NoteCommand.Append(
                noteId = privateId,
                textToAppend = "Ngày 2: 120.000đ"
            )
        )

        assertTrue("APPEND trên ghi chú riêng tư phải được phép", result is CommandResult.Success)
        val success = result as CommandResult.Success
        assertEquals(privateId, success.affectedNoteId)

        val updatedNote = repository.getNoteById(privateId)
        assertNotNull(updatedNote)
        assertEquals("Ngày 1: 50.000đ\n\nNgày 2: 120.000đ", updatedNote!!.content)
        assertTrue("Vẫn phải giữ cờ isPrivate = true", updatedNote.isPrivate)
    }

    @Test
    fun executeAiCommand_appendOnEmptyContent_doesNotPrependNewlines() = runTest {
        val noteId = repository.createNote(
            title = "Ghi chú mới",
            content = "",
            isPrivate = false
        )

        val result = repository.executeAiCommand(
            NoteCommand.Append(
                noteId = noteId,
                textToAppend = "Nội dung đầu tiên"
            )
        )

        assertTrue(result is CommandResult.Success)
        val updated = repository.getNoteById(noteId)
        assertEquals("Nội dung đầu tiên", updated!!.content)
    }

    @Test
    fun executeAiCommand_createPublicAndPrivate_succeeds() = runTest {
        val resultPublic = repository.executeAiCommand(
            NoteCommand.Create(
                title = "Kế hoạch du lịch",
                content = "Đà Nẵng 3N2Đ",
                isPrivate = false
            )
        )
        assertTrue(resultPublic is CommandResult.Success)
        val publicCreated = (resultPublic as CommandResult.Success).payload
        assertNotNull(publicCreated)
        assertFalse(publicCreated!!.isPrivate)

        val resultPrivate = repository.executeAiCommand(
            NoteCommand.Create(
                title = "Bí mật",
                content = "Thẻ tín dụng...",
                isPrivate = true
            )
        )
        assertTrue(resultPrivate is CommandResult.Success)
        val privateCreated = (resultPrivate as CommandResult.Success).payload
        assertNotNull(privateCreated)
        assertTrue(privateCreated!!.isPrivate)
    }

    @Test
    fun executeAiCommand_updateOnPublicNote_succeeds() = runTest {
        val publicId = repository.createNote(
            title = "Ghi chú công việc",
            content = "Bản thảo cũ",
            isPrivate = false
        )

        val result = repository.executeAiCommand(
            NoteCommand.Update(
                noteId = publicId,
                newContent = "Bản thảo mới cập nhật"
            )
        )

        assertTrue("AI UPDATE trên ghi chú thường phải thành công", result is CommandResult.Success)
        val updated = repository.getNoteById(publicId)
        assertEquals("Bản thảo mới cập nhật", updated!!.content)
    }

    @Test
    fun executeAiCommand_deleteOnPublicNote_succeedsSoftDeleting() = runTest {
        val publicId = repository.createNote(
            title = "Tài liệu tạm",
            content = "Xong việc rồi",
            isPrivate = false
        )

        val result = repository.executeAiCommand(NoteCommand.Delete(noteId = publicId))

        assertTrue("AI DELETE trên ghi chú thường phải thành công", result is CommandResult.Success)
        val note = repository.getNoteById(publicId)
        assertNotNull(note)
        assertTrue("Ghi chú thường phải bị chuyển vào thùng rác", note!!.isDeleted)
    }

    // =========================================================================
    // 2. USER CRUD & LIFECYCLE MANAGEMENT
    // =========================================================================

    @Test
    fun userCrud_createUpdateSoftDeleteRestorePermanentDelete() = runTest {
        // 1. Create
        val id = repository.createNote(
            title = "Học tập",
            content = "Học Kotlin Coroutines",
            isPrivate = false,
            isPinned = false
        )
        assertTrue(id > 0)

        // 2. Get by ID
        val created = repository.getNoteById(id)
        assertNotNull(created)
        assertEquals("Học tập", created!!.title)
        assertEquals("Học Kotlin Coroutines", created.content)

        // 3. Update
        val updateSuccess = repository.updateNote(
            id = id,
            title = "Học Kotlin & Room",
            content = "Nâng cấp lên Room v8",
            isPrivate = true,
            isPinned = true
        )
        assertTrue(updateSuccess)
        val updated = repository.getNoteById(id)
        assertEquals("Học Kotlin & Room", updated!!.title)
        assertEquals("Nâng cấp lên Room v8", updated.content)
        assertTrue(updated.isPrivate)
        assertTrue(updated.isPinned)

        // 4. Soft Delete
        val deleteSuccess = repository.softDeleteNote(id)
        assertTrue(deleteSuccess)
        val activeNotesAfterDelete = repository.getActiveNotesFlow().first()
        assertTrue(activeNotesAfterDelete.none { it.id == id })

        val trashNotesAfterDelete = repository.getTrashNotesFlow().first()
        assertTrue(trashNotesAfterDelete.any { it.id == id })

        // 5. Restore
        val restoreSuccess = repository.restoreNote(id)
        assertTrue(restoreSuccess)
        val activeNotesAfterRestore = repository.getActiveNotesFlow().first()
        assertTrue(activeNotesAfterRestore.any { it.id == id })

        // 6. Permanent Delete
        val permDeleteSuccess = repository.permanentDeleteNote(id)
        assertTrue(permDeleteSuccess)
        assertNull(repository.getNoteById(id))
    }

    @Test
    fun userPinToggle_updatesPinStatusCorrectly() = runTest {
        val id = repository.createNote(
            title = "Việc khẩn cấp",
            content = "Hạn chót trưa nay",
            isPrivate = false,
            isPinned = false
        )

        // Toggle to pinned
        val pin1 = repository.togglePinNote(id)
        assertTrue(pin1)
        assertTrue(repository.getNoteById(id)!!.isPinned)

        // Toggle back to unpinned
        val pin2 = repository.togglePinNote(id)
        assertTrue(pin2)
        assertFalse(repository.getNoteById(id)!!.isPinned)
    }

    @Test
    fun userClearTrash_removesOnlySoftDeletedNotes() = runTest {
        val active1 = repository.createNote("Active 1", "Content 1", false)
        val active2 = repository.createNote("Active 2", "Content 2", true)
        val trash1 = repository.createNote("Trash 1", "Content T1", false)
        val trash2 = repository.createNote("Trash 2", "Content T2", false)

        repository.softDeleteNote(trash1)
        repository.softDeleteNote(trash2)

        val clearedCount = repository.clearTrash()
        assertEquals(2, clearedCount)

        val trashNotes = repository.getTrashNotesFlow().first()
        assertTrue("Thùng rác phải trống sau clearTrash", trashNotes.isEmpty())

        assertNotNull("Active 1 vẫn còn", repository.getNoteById(active1))
        assertNotNull("Active 2 vẫn còn", repository.getNoteById(active2))
    }

    // =========================================================================
    // 3. SEARCH & FLOW EMISSIONS
    // =========================================================================

    @Test
    fun searchNotesFlow_matchesTitleOrContentCaseInsensitively() = runTest {
        repository.createNote("Họp dự án Alpha", "Báo cáo tiến độ", false)
        repository.createNote("Mua sắm cuối tuần", "Mua sữa tươi và táo Alpha", false)
        repository.createNote("Tập gym", "Bài tập chân", false)

        val searchResults = repository.searchNotesFlow("alpha").first()
        assertEquals(2, searchResults.size)

        val emptySearch = repository.searchNotesFlow("").first()
        assertEquals(3, emptySearch.size)
    }

    // =========================================================================
    // 4. ADVERSARIAL EDGE CASES & BOUNDARY CONDITIONS
    // =========================================================================

    @Test
    fun edgeCase_colonsAndVietnameseSpecialCharactersInTitleAndContent() = runTest {
        val titleWithColons = "Tài khoản: Ngân hàng VPBank: 0912345678"
        val contentSpecial = "Mật khẩu: P@ss:w0rd! [SHOW_NOTE:999] [DELETE:1] Tiếng Việt: Chữ đ, ơ, ư, ă, ê"

        val id = repository.createNote(title = titleWithColons, content = contentSpecial, isPrivate = true)
        val note = repository.getNoteById(id)
        assertNotNull(note)
        assertEquals(titleWithColons, note!!.title)
        assertEquals(contentSpecial, note.content)

        val catalog = repository.getCatalogForAi()
        val item = catalog.first { it.id == id }
        assertEquals(titleWithColons, item.title)
    }

    @Test
    fun edgeCase_emptyCatalog_returnsEmptyList() = runTest {
        val catalog = repository.getCatalogForAi()
        assertTrue(catalog.isEmpty())

        val retrieved = repository.retrieveNotesForAi(emptyList())
        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun edgeCase_commandsOnNonExistentOrNegativeIds_returnFailure() = runTest {
        val failShow = repository.executeAiCommand(NoteCommand.Show(99999L))
        assertTrue(failShow is CommandResult.Failure)

        val failAppend = repository.executeAiCommand(NoteCommand.Append(-5L, "Test append"))
        assertTrue(failAppend is CommandResult.Failure)

        val failUpdate = repository.executeAiCommand(NoteCommand.Update(0L, "New content"))
        assertTrue(failUpdate is CommandResult.Failure)

        val failDelete = repository.executeAiCommand(NoteCommand.Delete(-999L))
        assertTrue(failDelete is CommandResult.Failure)
    }

    @Test
    fun edgeCase_commandsOnSoftDeletedNote_returnFailure() = runTest {
        val id = repository.createNote("Tạm xóa", "Nội dung", isPrivate = false)
        repository.softDeleteNote(id)

        val resultShow = repository.executeAiCommand(NoteCommand.Show(id))
        assertTrue("Không cho phép SHOW_NOTE trên ghi chú đã xóa", resultShow is CommandResult.Failure)

        val resultAppend = repository.executeAiCommand(NoteCommand.Append(id, "Append text"))
        assertTrue("Không cho phép APPEND trên ghi chú đã xóa", resultAppend is CommandResult.Failure)

        val resultUpdate = repository.executeAiCommand(NoteCommand.Update(id, "Update text"))
        assertTrue("Không cho phép UPDATE trên ghi chú đã xóa", resultUpdate is CommandResult.Failure)

        val resultDelete = repository.executeAiCommand(NoteCommand.Delete(id))
        assertTrue("Không cho phép DELETE trên ghi chú đã xóa", resultDelete is CommandResult.Failure)
    }

    @Test
    fun edgeCase_appendBlankText_returnsFailure() = runTest {
        val id = repository.createNote("Tiêu đề", "Nội dung", isPrivate = false)
        val result = repository.executeAiCommand(NoteCommand.Append(id, "   \n\t  "))
        assertTrue(result is CommandResult.Failure)
        val failure = result as CommandResult.Failure
        assertTrue(failure.errorMessage.contains("không được để trống"))
    }
}
