package com.orca.orcaauto.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.GsonBuilder
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.SummarySessionConfigDeserializer
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.DailyMemoryEntity
import com.orca.orcaauto.data.local.SummaryHistoryEntity
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OrcaBackupData(
    val version: Int = 1,
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
    val sessions: List<com.orca.orcaauto.data.preferences.SummarySessionConfig> = emptyList()
)

object BackupManager {

    private const val TAG = "BackupManager"
    private const val AUTO_SNAPSHOT_FILENAME = "orca_auto_snapshot.json"
    private val gson = GsonBuilder()
        .registerTypeAdapter(SummarySessionConfig::class.java, SummarySessionConfigDeserializer())
        .setPrettyPrinting()
        .create()

    /**
     * Creates a full backup model from current Preferences and Room Database.
     */
    suspend fun createBackup(context: Context): OrcaBackupData = withContext(Dispatchers.IO) {
        val prefs = UserPreferences.getInstance(context)
        val db = AppDatabase.getDatabase(context)

        val summaries = try {
            db.summaryDao().getAllSummaries()
        } catch (e: Exception) {
            emptyList()
        }

        val dailyMemories = try {
            db.dailyMemoryDao().getAllDailyMemories()
        } catch (e: Exception) {
            emptyList()
        }

        OrcaBackupData(
            keyPool = prefs.getKeyPool(),
            customFixedContext = prefs.getCustomFixedContext(),
            scheduledHours = prefs.getScheduledHours(),
            promptPersonaId = prefs.getPromptPersona().id,
            zaloEnabled = prefs.isZaloEnabled(),
            telegramEnabled = prefs.isTelegramEnabled(),
            messengerEnabled = prefs.isMessengerEnabled(),
            summaries = summaries,
            dailyMemories = dailyMemories,
            sessions = prefs.loadSessions()
        )
    }

    /**
     * Serializes backup data into formatted JSON string.
     */
    suspend fun exportBackupJson(context: Context): String = withContext(Dispatchers.IO) {
        val data = createBackup(context)
        gson.toJson(data)
    }

    /**
     * Saves backup JSON to public Downloads directory or external files directory.
     * Returns the absolute path of the created file.
     */
    suspend fun saveBackupToFile(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val json = exportBackupJson(context)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "AItu_Backup_$timeStamp.json"

            // Target public Download directory if available, else app external files
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = if (downloadDir != null && downloadDir.exists()) downloadDir else context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(targetDir, fileName)

            file.writeText(json, Charsets.UTF_8)
            Log.i(TAG, "Đã lưu bản sao lưu vào: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi lưu file sao lưu: ${e.message}", e)
            null
        }
    }

    /**
     * Restores data from a backup JSON string.
     * Merges summaries without duplicating and restores all configurations.
     */
    suspend fun restoreFromJson(context: Context, json: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backup = gson.fromJson(json, OrcaBackupData::class.java)
                ?: return@withContext Result.failure(IllegalArgumentException("Dữ liệu sao lưu không đúng định dạng!"))

            val prefs = UserPreferences.getInstance(context)
            val db = AppDatabase.getDatabase(context)

            // 1. Restore API Keys
            if (backup.keyPool.isNotEmpty()) {
                val currentKeys = prefs.getKeyPool().toMutableList()
                var addedCount = 0
                for (key in backup.keyPool) {
                    if (currentKeys.none { it.rawKey == key.rawKey }) {
                        currentKeys.add(key)
                        addedCount++
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
            if (!backup.scheduledHours.isNullOrEmpty()) {
                prefs.setScheduledHours(backup.scheduledHours)
                WorkScheduler.scheduleDailySummaries(context)
            }

            // 3. Restore App toggles
            prefs.setZaloEnabled(backup.zaloEnabled)
            prefs.setTelegramEnabled(backup.telegramEnabled)
            prefs.setMessengerEnabled(backup.messengerEnabled)

            // 4. Restore Room summaries
            var summaryCount = 0
            if (!backup.summaries.isNullOrEmpty()) {
                db.summaryDao().insertAll(backup.summaries)
                summaryCount = backup.summaries.size
            }

            if (!backup.dailyMemories.isNullOrEmpty()) {
                db.dailyMemoryDao().insertAll(backup.dailyMemories)
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

            // Save new auto-snapshot immediately
            saveAutoSnapshot(context)

            val msg = "Khôi phục thành công: ${backup.keyPool.size} API Keys, ${backup.scheduledHours.size} mốc giờ, $summaryCount báo cáo lịch sử."
            Result.success(msg)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi khôi phục dữ liệu: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Automatically writes an internal snapshot to app files directory.
     * Called whenever settings or keys change so app updates never lose data.
     */
    fun saveAutoSnapshot(context: Context) {
        try {
            val prefs = UserPreferences.getInstance(context)
            val data = OrcaBackupData(
                keyPool = prefs.getKeyPool(),
                customFixedContext = prefs.getCustomFixedContext(),
                scheduledHours = prefs.getScheduledHours(),
                promptPersonaId = prefs.getPromptPersona().id,
                zaloEnabled = prefs.isZaloEnabled(),
                telegramEnabled = prefs.isTelegramEnabled(),
                messengerEnabled = prefs.isMessengerEnabled(),
                sessions = prefs.loadSessions()
            )
            val file = File(context.filesDir, AUTO_SNAPSHOT_FILENAME)
            file.writeText(gson.toJson(data), Charsets.UTF_8)
            Log.d(TAG, "Đã lưu auto-snapshot an toàn.")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể lưu auto-snapshot: ${e.message}")
        }
    }

    /**
     * Checks if preferences are empty on fresh start/update, and restores from auto-snapshot if available.
     */
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
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi kiểm tra phục hồi snapshot: ${e.message}")
        }
    }
}
