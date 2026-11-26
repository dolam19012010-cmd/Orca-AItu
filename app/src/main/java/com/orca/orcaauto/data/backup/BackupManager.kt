package com.orca.orcaauto.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.GsonBuilder
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.DailyMemoryEntity
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.AppFilterRule
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.SummarySessionConfigDeserializer
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.prompt.AiLanguage
import com.orca.orcaauto.ui.locale.AppLanguage
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class OrcaBackupData(
    val version: Int = 2,
    val backupTime: String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
    val appPackage: String = "com.orca.orcaauto",
    val keyPool: List<ApiKeyConfig> = emptyList(),
    val customFixedContext: String = "",
    val scheduledHours: List<String> = emptyList(),
    val promptPersonaId: String = "gentle_assistant",
    val zaloEnabled: Boolean = true,
    val telegramEnabled: Boolean = true,
    val messengerEnabled: Boolean = true,
    val summaries: List<SummaryHistoryEntity> = emptyList(),
    val dailyMemories: List<DailyMemoryEntity> = emptyList(),
    val sessions: List<SummarySessionConfig> = emptyList(),
    val filterRules: Map<String, AppFilterRule>? = null,
    val notes: List<SharedNoteEntity>? = null,
    val smartAlarms: List<SmartAlarmEntity>? = null,
    val appLanguageCode: String? = null,
    val aiLanguageCode: String? = null,
    val assistantThinkingLevelId: String? = null,
    val pinnedTasks: Set<String>? = null,
    val taskNotes: Map<String, String>? = null,
    val contextualMemoryEnabled: Boolean? = null,
    val summaryStatusBarNotifEnabled: Boolean? = null
)

object BackupManager {

    private const val TAG = "BackupManager"
    private const val AUTO_SNAPSHOT_FILENAME = "orca_auto_snapshot.json"
    private val snapshotLock = Any()
    private val gson = GsonBuilder()
        .registerTypeAdapter(SummarySessionConfig::class.java, SummarySessionConfigDeserializer())
        .setPrettyPrinting()
        .create()

    suspend fun createBackup(context: Context): OrcaBackupData = withContext(Dispatchers.IO) {
        val prefs = UserPreferences.getInstance(context)
        val db = AppDatabase.getDatabase(context)

        val summaries = try { db.summaryDao().getAllSummaries() } catch (_: Exception) { emptyList() }
        val dailyMemories = try { db.dailyMemoryDao().getAllDailyMemories() } catch (_: Exception) { emptyList() }
        val notes = try { db.sharedNoteDao().getAllNotesIncludingTrash() } catch (_: Exception) { emptyList() }
        val alarms = try { db.smartAlarmDao().getAllAlarms() } catch (_: Exception) { emptyList() }

        OrcaBackupData(
            version = 2,
            keyPool = prefs.getKeyPool(),
            customFixedContext = prefs.getCustomFixedContext(),
            scheduledHours = prefs.getScheduledHours(),
            promptPersonaId = prefs.getPromptPersona().id,
            zaloEnabled = prefs.isZaloEnabled(),
            telegramEnabled = prefs.isTelegramEnabled(),
            messengerEnabled = prefs.isMessengerEnabled(),
            summaries = summaries,
            dailyMemories = dailyMemories,
            sessions = prefs.loadSessions(),
            filterRules = prefs.getAllFilterRules(),
            notes = notes,
            smartAlarms = alarms,
            appLanguageCode = prefs.getAppLanguage().code,
            aiLanguageCode = prefs.getAiLanguage().code,
            assistantThinkingLevelId = prefs.getAssistantThinkingLevel().id,
            pinnedTasks = prefs.getPinnedTaskTitles(),
            taskNotes = prefs.getTaskNotes(),
            contextualMemoryEnabled = prefs.isContextualMemoryRollupEnabled(),
            summaryStatusBarNotifEnabled = prefs.isSummaryStatusBarNotificationEnabled()
        )
    }

    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val data = createBackup(context)
        gson.toJson(data)
    }

    suspend fun exportBackupToUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = exportBackupJson(context)
            val out = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Không thể mở file đích để ghi"))
            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            Result.success(json)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi exportBackupToUri: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun readBackupFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Không thể mở file đã chọn"))
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi readBackupFromUri: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Saves backup JSON to Downloads using MediaStore on Android 10+ (Scoped Storage safe),
     * falling back gracefully on older SDKs or external files dir.
     */
    suspend fun saveBackupToFile(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val json = exportBackupJson(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "AItu_Backup_$timeStamp.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    return@withContext "Downloads/$fileName"
                }
            }

            @Suppress("DEPRECATION")
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = if (downloadDir != null && downloadDir.exists() && downloadDir.canWrite()) {
                downloadDir
            } else {
                context.getExternalFilesDir(null) ?: context.filesDir
            }
            val file = File(targetDir, fileName)
            file.writeText(json, Charsets.UTF_8)
            Log.i(TAG, "Đã lưu bản sao lưu vào: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu file sao lưu: ${e.message}", e)
            null
        }
    }

    suspend fun restoreFromJson(context: Context, json: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, OrcaBackupData::class.java)
                ?: return@withContext Result.failure(IllegalArgumentException("Dữ liệu sao lưu không đúng định dạng!"))

            val prefs = UserPreferences.getInstance(context)
            val db = AppDatabase.getDatabase(context)

            // 1. Restore API Keys
            if (backup.keyPool.isNotEmpty()) {
                val currentKeys = prefs.getKeyPool().toMutableList()
                for (key in backup.keyPool) {
                    if (currentKeys.none { it.rawKey == key.rawKey }) {
                        currentKeys.add(key)
                    }
                }
                prefs.saveKeyPoolExternal(currentKeys)
            }

            // 2. Restore Persona Context & Schedule
            if (!backup.customFixedContext.isNullOrBlank()) {
                prefs.setCustomFixedContext(backup.customFixedContext)
            }
            if (!backup.promptPersonaId.isNullOrBlank()) {
                prefs.setPromptPersona(com.orca.orcaauto.domain.prompt.PromptPersona.fromId(backup.promptPersonaId))
            }
            if (backup.scheduledHours.isNotEmpty() || backup.version >= 2) {
                prefs.setScheduledHours(backup.scheduledHours)
            }

            // 3. Restore App toggles & preferences
            prefs.setZaloEnabled(backup.zaloEnabled)
            prefs.setTelegramEnabled(backup.telegramEnabled)
            prefs.setMessengerEnabled(backup.messengerEnabled)

            backup.filterRules?.values?.forEach { rule ->
                prefs.saveAppFilterRule(rule)
            }
            backup.appLanguageCode?.takeIf { it.isNotBlank() }?.let { prefs.setAppLanguage(AppLanguage.fromCode(it)) }
            backup.aiLanguageCode?.takeIf { it.isNotBlank() }?.let { prefs.setAiLanguage(AiLanguage.fromCode(it)) }
            backup.assistantThinkingLevelId?.takeIf { it.isNotBlank() }?.let { prefs.setAssistantThinkingLevel(AssistantThinkingLevel.fromId(it)) }
            backup.pinnedTasks?.takeIf { it.isNotEmpty() }?.let { restoredPins ->
                prefs.setPinnedTaskTitles(prefs.getPinnedTaskTitles() + restoredPins)
            }
            backup.taskNotes?.takeIf { it.isNotEmpty() }?.let { restoredNotes ->
                val merged = prefs.getTaskNotes().toMutableMap()
                restoredNotes.forEach { (k, v) -> if (!merged.containsKey(k)) merged[k] = v }
                prefs.setTaskNotesMap(merged)
            }
            backup.contextualMemoryEnabled?.let { prefs.setContextualMemoryRollupEnabled(it) }
            backup.summaryStatusBarNotifEnabled?.let { prefs.setSummaryStatusBarNotificationEnabled(it) }

            // 4. Restore Room summaries safely without primary key id collision skipping
            var summaryCount = 0
            if (!backup.summaries.isNullOrEmpty()) {
                val existingKeys = db.summaryDao().getAllSummaries()
                    .map { "${it.timestamp}|${it.sessionId}|${it.targetTimeSlot}" }
                    .toHashSet()
                val toInsert = backup.summaries.filter {
                    "${it.timestamp}|${it.sessionId}|${it.targetTimeSlot}" !in existingKeys
                }.map { it.copy(id = 0L) }
                if (toInsert.isNotEmpty()) {
                    db.summaryDao().insertAll(toInsert)
                    summaryCount = toInsert.size
                }
            }

            if (!backup.dailyMemories.isNullOrEmpty()) {
                val existingDates = db.dailyMemoryDao().getAllDailyMemories().map { it.dateString }.toHashSet()
                val memoriesToInsert = backup.dailyMemories
                    .filter { it.dateString !in existingDates }
                    .map { it.copy(id = 0L) }
                if (memoriesToInsert.isNotEmpty()) {
                    db.dailyMemoryDao().insertAll(memoriesToInsert)
                }
            }

            var noteCount = 0
            if (!backup.notes.isNullOrEmpty()) {
                val existingNotes = db.sharedNoteDao().getAllNotesIncludingTrash()
                    .map { "${it.title}|${it.createdAt}" }
                    .toHashSet()
                for (note in backup.notes) {
                    if ("${note.title}|${note.createdAt}" !in existingNotes) {
                        db.sharedNoteDao().insert(note.copy(id = 0L))
                        noteCount++
                    }
                }
            }

            if (!backup.smartAlarms.isNullOrEmpty()) {
                val dao = db.smartAlarmDao()
                val now = System.currentTimeMillis()
                for (rawAlarm in backup.smartAlarms) {
                    val safeInterval = rawAlarm.intervalMinutes.takeIf { it in 1..120 } ?: 15
                    @Suppress("UselessCallOnNotNull")
                    val safeCascade = rawAlarm.cascadeMode.orEmpty().ifBlank { "x2" }
                    @Suppress("USELESS_ELVIS")
                    val safeStatus = rawAlarm.status ?: SmartAlarmStatus.PROPOSED
                    val sanitizedAlarm = rawAlarm.copy(
                        cascadeMode = safeCascade,
                        intervalMinutes = safeInterval,
                        status = safeStatus,
                        updatedAt = if (rawAlarm.updatedAt > 0L) rawAlarm.updatedAt else now
                    )
                    if (dao.getAlarmById(sanitizedAlarm.alarmId) == null) {
                        dao.insertOrUpdate(sanitizedAlarm)
                        if (safeStatus == SmartAlarmStatus.ACTIVE && sanitizedAlarm.targetTimeMillis > now) {
                            val cal = Calendar.getInstance().apply { timeInMillis = sanitizedAlarm.targetTimeMillis }
                            val mode = AlarmCascadeMode.fromCode(safeCascade)
                            SystemAlarmHelper.scheduleSmartAlarms(context, sanitizedAlarm.taskTitle, cal, mode, safeInterval)
                        }
                    }
                }
            }

            // 5. Restore Sessions
            if (!backup.sessions.isNullOrEmpty()) {
                val currentSessions = prefs.loadSessions().toMutableList()
                for (session in backup.sessions) {
                    val existingIndex = currentSessions.indexOfFirst { it.id == session.id }
                    if (existingIndex >= 0) {
                        currentSessions[existingIndex] = session
                    } else {
                        currentSessions.add(session)
                    }
                }
                prefs.saveSessions(currentSessions)
            }

            WorkScheduler.scheduleDailySummaries(context)
            saveAutoSnapshot(context)

            val msg = "Khôi phục thành công: ${backup.keyPool.size} API Keys, $summaryCount báo cáo, $noteCount ghi chú!"
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi khôi phục dữ liệu: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun saveAutoSnapshot(context: Context) {
        synchronized(snapshotLock) {
            try {
                val prefs = UserPreferences.getInstance(context)
                val data = OrcaBackupData(
                    version = 2,
                    keyPool = prefs.getKeyPool(),
                    customFixedContext = prefs.getCustomFixedContext(),
                    scheduledHours = prefs.getScheduledHours(),
                    promptPersonaId = prefs.getPromptPersona().id,
                    zaloEnabled = prefs.isZaloEnabled(),
                    telegramEnabled = prefs.isTelegramEnabled(),
                    messengerEnabled = prefs.isMessengerEnabled(),
                    sessions = prefs.loadSessions(),
                    filterRules = prefs.getAllFilterRules(),
                    appLanguageCode = prefs.getAppLanguage().code,
                    aiLanguageCode = prefs.getAiLanguage().code,
                    assistantThinkingLevelId = prefs.getAssistantThinkingLevel().id,
                    pinnedTasks = prefs.getPinnedTaskTitles(),
                    taskNotes = prefs.getTaskNotes(),
                    contextualMemoryEnabled = prefs.isContextualMemoryRollupEnabled(),
                    summaryStatusBarNotifEnabled = prefs.isSummaryStatusBarNotificationEnabled()
                )
                val targetFile = File(context.filesDir, AUTO_SNAPSHOT_FILENAME)
                val tmpFile = File(context.filesDir, "$AUTO_SNAPSHOT_FILENAME.tmp")
                tmpFile.writeText(gson.toJson(data), Charsets.UTF_8)
                if (!tmpFile.renameTo(targetFile)) {
                    targetFile.writeText(tmpFile.readText(Charsets.UTF_8), Charsets.UTF_8)
                    tmpFile.delete()
                }
                Log.d(TAG, "Đã lưu auto-snapshot an toàn.")
            } catch (e: Exception) {
                Log.w(TAG, "Không thể lưu auto-snapshot: ${e.message}")
            }
        }
    }

    fun checkAndRecoverFromSnapshot(context: Context) {
        try {
            val prefs = UserPreferences.getInstance(context)
            if (prefs.getKeyPool().isEmpty()) {
                val file = File(context.filesDir, AUTO_SNAPSHOT_FILENAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText(Charsets.UTF_8)
                    val backup = gson.fromJson(json, OrcaBackupData::class.java)
                    if (backup != null && backup.keyPool.isNotEmpty()) {
                        Log.i(TAG, "Phát hiện snapshot cũ sau khi cập nhật! Đang tự động phục hồi cấu hình...")
                        prefs.saveKeyPoolExternal(backup.keyPool)
                        if (!backup.customFixedContext.isNullOrBlank()) {
                            prefs.setCustomFixedContext(backup.customFixedContext)
                        }
                        if (!backup.promptPersonaId.isNullOrBlank()) {
                            prefs.setPromptPersona(com.orca.orcaauto.domain.prompt.PromptPersona.fromId(backup.promptPersonaId))
                        }
                        if (!backup.scheduledHours.isNullOrEmpty()) {
                            prefs.setScheduledHours(backup.scheduledHours)
                            WorkScheduler.scheduleDailySummaries(context)
                        }
                        if (!backup.sessions.isNullOrEmpty()) {
                            prefs.saveSessions(backup.sessions)
                        }
                        backup.filterRules?.values?.forEach { prefs.saveAppFilterRule(it) }
                        backup.appLanguageCode?.takeIf { it.isNotBlank() }?.let { prefs.setAppLanguage(AppLanguage.fromCode(it)) }
                        backup.aiLanguageCode?.takeIf { it.isNotBlank() }?.let { prefs.setAiLanguage(AiLanguage.fromCode(it)) }
                        backup.contextualMemoryEnabled?.let { prefs.setContextualMemoryRollupEnabled(it) }
                        backup.summaryStatusBarNotifEnabled?.let { prefs.setSummaryStatusBarNotificationEnabled(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi kiểm tra phục hồi snapshot: ${e.message}")
        }
    }
}
