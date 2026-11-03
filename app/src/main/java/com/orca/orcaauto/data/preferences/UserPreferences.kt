package com.orca.orcaauto.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.domain.prompt.PromptPersona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_FILENAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    private val _keyPoolFlow = MutableStateFlow(loadKeyPool())
    val keyPoolFlow: StateFlow<List<ApiKeyConfig>> = _keyPoolFlow.asStateFlow()

    private val _zaloEnabledFlow = MutableStateFlow(isZaloEnabled())
    val zaloEnabledFlow: StateFlow<Boolean> = _zaloEnabledFlow.asStateFlow()

    private val _telegramEnabledFlow = MutableStateFlow(isTelegramEnabled())
    val telegramEnabledFlow: StateFlow<Boolean> = _telegramEnabledFlow.asStateFlow()

    private val _messengerEnabledFlow = MutableStateFlow(isMessengerEnabled())
    val messengerEnabledFlow: StateFlow<Boolean> = _messengerEnabledFlow.asStateFlow()

    private val _customFixedContextFlow = MutableStateFlow(getCustomFixedContext())
    val customFixedContextFlow: StateFlow<String> = _customFixedContextFlow.asStateFlow()

    private val _scheduledHoursFlow = MutableStateFlow(getScheduledHours())
    val scheduledHoursFlow: StateFlow<List<String>> = _scheduledHoursFlow.asStateFlow()

    private val _promptPersonaFlow = MutableStateFlow(getPromptPersona())
    val promptPersonaFlow: StateFlow<PromptPersona> = _promptPersonaFlow.asStateFlow()

    private val _userTimezoneFlow = MutableStateFlow(getUserTimezone())
    val userTimezoneFlow: StateFlow<String> = _userTimezoneFlow.asStateFlow()

    private val _recentSummaryContextEnabledFlow = MutableStateFlow(isRecentSummaryContextEnabled())
    val recentSummaryContextEnabledFlow: StateFlow<Boolean> = _recentSummaryContextEnabledFlow.asStateFlow()

    private val _longTermTasksContextEnabledFlow = MutableStateFlow(isLongTermTasksContextEnabled())
    val longTermTasksContextEnabledFlow: StateFlow<Boolean> = _longTermTasksContextEnabledFlow.asStateFlow()

    private val _wallpaperPathFlow = MutableStateFlow(getWallpaperPath())
    val wallpaperPathFlow: StateFlow<String?> = _wallpaperPathFlow.asStateFlow()

    private val _wallpaperModeFlow = MutableStateFlow(getWallpaperMode())
    val wallpaperModeFlow: StateFlow<com.orca.orcaauto.ui.wallpaper.WallpaperMode> = _wallpaperModeFlow.asStateFlow()

    private val _bubbleDisplayModeFlow = MutableStateFlow(getBubbleDisplayMode())
    val bubbleDisplayModeFlow: StateFlow<BubbleDisplayMode> = _bubbleDisplayModeFlow.asStateFlow()

    private fun loadKeyPool(): List<ApiKeyConfig> {
        val json = try {
            prefs.getString(KEY_API_KEY_POOL, null)
        } catch (e: Throwable) {
            null
        }
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<ApiKeyConfig>>() {}.type
            return try {
                gson.fromJson(json, type)
            } catch (e: Throwable) {
                emptyList()
            }
        }

        // Backward compatibility: migrate legacy single key if present
        val legacyKey = try {
            prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        } catch (e: Throwable) {
            ""
        }
        if (legacyKey.isNotBlank()) {
            val initialConfig = ApiKeyConfig.fromRawKey(legacyKey)
            saveKeyPool(listOf(initialConfig))
            return listOf(initialConfig)
        }
        return emptyList()
    }

    fun getKeyPool(): List<ApiKeyConfig> {
        return _keyPoolFlow.value
    }

    private fun saveKeyPool(list: List<ApiKeyConfig>) {
        try {
            val json = gson.toJson(list)
            prefs.edit().putString(KEY_API_KEY_POOL, json).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Không thể lưu KeyPool: ${e.message}")
        }
        _keyPoolFlow.value = list
    }

    fun saveKeyPoolExternal(list: List<ApiKeyConfig>) {
        saveKeyPool(list)
    }

    fun addApiKey(rawKey: String, customModel: String? = null): ApiKeyConfig? {
        val trimmed = rawKey.trim()
        if (trimmed.isBlank()) return null

        val currentList = _keyPoolFlow.value.toMutableList()
        // Prevent duplicate keys
        if (currentList.any { it.rawKey == trimmed }) return null

        val newConfig = ApiKeyConfig.fromRawKey(trimmed, customModel)
        currentList.add(newConfig)
        saveKeyPool(currentList)
        return newConfig
    }

    fun removeApiKey(id: String) {
        val currentList = _keyPoolFlow.value.filter { it.id != id }
        saveKeyPool(currentList)
    }

    fun toggleApiKey(id: String, enabled: Boolean) {
        val currentList = _keyPoolFlow.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
        saveKeyPool(currentList)
    }

    fun getCustomFixedContext(): String = try {
        prefs.getString(KEY_CUSTOM_FIXED_CONTEXT, "") ?: ""
    } catch (e: Throwable) {
        ""
    }

    fun setCustomFixedContext(text: String) {
        try {
            prefs.edit().putString(KEY_CUSTOM_FIXED_CONTEXT, text.trim()).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setCustomFixedContext: ${e.message}")
        }
        _customFixedContextFlow.value = text.trim()
    }

    fun getPromptPersona(): PromptPersona {
        val id = try {
            prefs.getString(KEY_PROMPT_PERSONA, PromptPersona.GENTLE_ASSISTANT.id)
        } catch (e: Throwable) {
            PromptPersona.GENTLE_ASSISTANT.id
        }
        return PromptPersona.fromId(id)
    }

    fun setPromptPersona(persona: PromptPersona) {
        try {
            prefs.edit().putString(KEY_PROMPT_PERSONA, persona.id).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setPromptPersona: ${e.message}")
        }
        _promptPersonaFlow.value = persona
    }

    fun getScheduledHours(): List<String> {
        val json = try {
            prefs.getString(KEY_SCHEDULED_HOURS, null)
        } catch (e: Throwable) {
            null
        }
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<List<String>>() {}.type
            return try {
                gson.fromJson<List<String>>(json, type).sorted()
            } catch (e: Throwable) {
                listOf("12:00", "18:00")
            }
        }
        return listOf("12:00", "18:00")
    }

    fun setScheduledHours(hours: List<String>) {
        val sorted = hours.map { it.trim() }.filter { it.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$")) }.sorted()
        try {
            prefs.edit().putString(KEY_SCHEDULED_HOURS, gson.toJson(sorted)).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setScheduledHours: ${e.message}")
        }
        _scheduledHoursFlow.value = sorted
    }

    fun addScheduledHour(hourStr: String): Boolean {
        val formatted = hourStr.trim()
        if (!formatted.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) return false
        val current = getScheduledHours().toMutableList()
        val normalized = if (formatted.length == 4) "0$formatted" else formatted
        if (current.contains(normalized)) return false
        current.add(normalized)
        current.sort()
        try {
            prefs.edit().putString(KEY_SCHEDULED_HOURS, gson.toJson(current)).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi addScheduledHour: ${e.message}")
        }
        _scheduledHoursFlow.value = current
        return true
    }

    fun removeScheduledHour(hourStr: String) {
        val current = getScheduledHours().filter { it != hourStr.trim() }
        try {
            prefs.edit().putString(KEY_SCHEDULED_HOURS, gson.toJson(current)).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi removeScheduledHour: ${e.message}")
        }
        _scheduledHoursFlow.value = current
    }

    fun isZaloEnabled(): Boolean = try {
        prefs.getBoolean(KEY_APP_ZALO, true)
    } catch (e: Throwable) {
        true
    }

    fun setZaloEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_APP_ZALO, enabled).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setZaloEnabled: ${e.message}")
        }
        _zaloEnabledFlow.value = enabled
    }

    fun isTelegramEnabled(): Boolean = try {
        prefs.getBoolean(KEY_APP_TELEGRAM, true)
    } catch (e: Throwable) {
        true
    }

    fun setTelegramEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_APP_TELEGRAM, enabled).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setTelegramEnabled: ${e.message}")
        }
        _telegramEnabledFlow.value = enabled
    }

    fun isMessengerEnabled(): Boolean = try {
        prefs.getBoolean(KEY_APP_MESSENGER, true)
    } catch (e: Throwable) {
        true
    }

    fun setMessengerEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_APP_MESSENGER, enabled).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setMessengerEnabled: ${e.message}")
        }
        _messengerEnabledFlow.value = enabled
    }

    fun isAppMonitored(packageName: String): Boolean {
        return when (packageName) {
            PACKAGE_ZALO -> isZaloEnabled()
            PACKAGE_TELEGRAM -> isTelegramEnabled()
            PACKAGE_MESSENGER -> isMessengerEnabled()
            else -> false
        }
    }

    fun isIntroDismissedPermanently(): Boolean = try {
        prefs.getBoolean(KEY_INTRO_DISMISSED, false)
    } catch (e: Throwable) {
        false
    }

    fun setIntroDismissedPermanently(dismissed: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_INTRO_DISMISSED, dismissed).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setIntroDismissedPermanently: ${e.message}")
        }
    }

    fun isPermissionBannerDismissed(): Boolean = try {
        prefs.getBoolean(KEY_PERMISSION_BANNER_DISMISSED, false)
    } catch (e: Throwable) {
        false
    }

    fun setPermissionBannerDismissed(dismissed: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_PERMISSION_BANNER_DISMISSED, dismissed).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setPermissionBannerDismissed: ${e.message}")
        }
    }

    fun getUserTimezone(): String = try {
        prefs.getString(KEY_USER_TIMEZONE, "Asia/Ho_Chi_Minh") ?: "Asia/Ho_Chi_Minh"
    } catch (e: Throwable) {
        "Asia/Ho_Chi_Minh"
    }

    fun setUserTimezone(timezone: String) {
        val trimmed = timezone.trim().ifBlank { "Asia/Ho_Chi_Minh" }
        try {
            prefs.edit().putString(KEY_USER_TIMEZONE, trimmed).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setUserTimezone: ${e.message}")
        }
        _userTimezoneFlow.value = trimmed
    }

    fun isRecentSummaryContextEnabled(): Boolean = try {
        prefs.getBoolean(KEY_RECENT_SUMMARY_CONTEXT, true)
    } catch (e: Throwable) {
        true
    }

    fun setRecentSummaryContextEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_RECENT_SUMMARY_CONTEXT, enabled).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setRecentSummaryContextEnabled: ${e.message}")
        }
        _recentSummaryContextEnabledFlow.value = enabled
    }

    fun isLongTermTasksContextEnabled(): Boolean = try {
        prefs.getBoolean(KEY_LONG_TERM_TASKS_CONTEXT, true)
    } catch (e: Throwable) {
        true
    }

    fun setLongTermTasksContextEnabled(enabled: Boolean) {
        try {
            prefs.edit().putBoolean(KEY_LONG_TERM_TASKS_CONTEXT, enabled).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setLongTermTasksContextEnabled: ${e.message}")
        }
        _longTermTasksContextEnabledFlow.value = enabled
    }

    fun updateKeyModel(rawKey: String, newModelName: String) {
        val currentPool = loadKeyPool().toMutableList()
        val index = currentPool.indexOfFirst { it.rawKey == rawKey }
        if (index != -1) {
            currentPool[index] = currentPool[index].copy(modelName = newModelName.trim())
            saveKeyPool(currentPool)
        }
    }

    fun getWallpaperPath(): String? = try {
        prefs.getString(KEY_WALLPAPER_PATH, null)
    } catch (e: Throwable) {
        null
    }

    fun setWallpaperPath(path: String?) {
        try {
            prefs.edit().putString(KEY_WALLPAPER_PATH, path).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setWallpaperPath: ${e.message}")
        }
        _wallpaperPathFlow.value = path
    }

    fun getWallpaperMode(): com.orca.orcaauto.ui.wallpaper.WallpaperMode = try {
        val code = prefs.getString(KEY_WALLPAPER_MODE, "clear")
        com.orca.orcaauto.ui.wallpaper.WallpaperMode.fromCode(code)
    } catch (e: Throwable) {
        com.orca.orcaauto.ui.wallpaper.WallpaperMode.CLEAR
    }

    fun setWallpaperMode(mode: com.orca.orcaauto.ui.wallpaper.WallpaperMode) {
        try {
            prefs.edit().putString(KEY_WALLPAPER_MODE, mode.code).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setWallpaperMode: ${e.message}")
        }
        _wallpaperModeFlow.value = mode
    }

    fun clearWallpaper(context: Context) {
        try {
            val oldPath = getWallpaperPath()
            if (!oldPath.isNullOrBlank()) {
                val f = java.io.File(oldPath)
                if (f.exists()) f.delete()
            }
        } catch (_: Throwable) {}
        setWallpaperPath(null)
        setWallpaperMode(com.orca.orcaauto.ui.wallpaper.WallpaperMode.CLEAR)
    }

    fun isSummaryStatusBarNotificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_SUMMARY_STATUS_BAR_NOTIF, false)
    }

    fun setSummaryStatusBarNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUMMARY_STATUS_BAR_NOTIF, enabled).apply()
    }

    fun getBubbleDisplayMode(): BubbleDisplayMode = try {
        val code = prefs.getString(KEY_BUBBLE_DISPLAY_MODE, BubbleDisplayMode.OPTION_A.code)
        BubbleDisplayMode.fromCode(code)
    } catch (_: Throwable) {
        BubbleDisplayMode.OPTION_A
    }

    fun setBubbleDisplayMode(mode: BubbleDisplayMode) {
        prefs.edit().putString(KEY_BUBBLE_DISPLAY_MODE, mode.code).apply()
        _bubbleDisplayModeFlow.value = mode
    }

    fun getTaskNotes(): Map<String, String> {
        val json = try {
            prefs.getString(KEY_TASK_NOTES, null)
        } catch (_: Throwable) {
            null
        }
        if (!json.isNullOrBlank()) {
            val type = object : TypeToken<Map<String, String>>() {}.type
            return try {
                gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
            } catch (_: Throwable) {
                emptyMap()
            }
        }
        return emptyMap()
    }

    fun getTaskNote(taskTitle: String): String? {
        val clean = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        val notes = getTaskNotes()
        return notes[clean] ?: notes[taskTitle.trim()]
    }

    fun setTaskNote(taskTitle: String, note: String) {
        val clean = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        val current = getTaskNotes().toMutableMap()
        if (note.isBlank()) {
            current.remove(clean)
            current.remove(taskTitle.trim())
        } else {
            current[clean] = note.trim()
        }
        try {
            prefs.edit().putString(KEY_TASK_NOTES, gson.toJson(current)).apply()
        } catch (e: Throwable) {
            android.util.Log.e("UserPreferences", "Lỗi setTaskNote: ${e.message}")
        }
    }

    fun removeTaskNote(taskTitle: String) {
        setTaskNote(taskTitle, "")
    }

    fun getTargetAppName(packageName: String): String {
        return when (packageName) {
            PACKAGE_ZALO -> "Zalo"
            PACKAGE_TELEGRAM -> "Telegram"
            PACKAGE_MESSENGER -> "Messenger"
            else -> packageName
        }
    }

    companion object {
        private const val PREFS_FILENAME = "orca_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "key_gemini_api_key"
        private const val KEY_API_KEY_POOL = "key_api_key_pool"
        private const val KEY_APP_ZALO = "key_app_zalo"
        private const val KEY_APP_TELEGRAM = "key_app_telegram"
        private const val KEY_APP_MESSENGER = "key_app_messenger"
        private const val KEY_CUSTOM_FIXED_CONTEXT = "key_custom_fixed_context"
        private const val KEY_SCHEDULED_HOURS = "key_scheduled_hours"
        private const val KEY_PROMPT_PERSONA = "key_prompt_persona"
        private const val KEY_INTRO_DISMISSED = "key_intro_dismissed"
        private const val KEY_PERMISSION_BANNER_DISMISSED = "key_permission_banner_dismissed"
        private const val KEY_USER_TIMEZONE = "key_user_timezone"
        private const val KEY_RECENT_SUMMARY_CONTEXT = "key_recent_summary_context"
        private const val KEY_LONG_TERM_TASKS_CONTEXT = "key_long_term_tasks_context"
        private const val KEY_WALLPAPER_PATH = "key_wallpaper_path"
        private const val KEY_WALLPAPER_MODE = "key_wallpaper_mode"
        private const val KEY_SUMMARY_STATUS_BAR_NOTIF = "key_summary_status_bar_notif"
        private const val KEY_BUBBLE_DISPLAY_MODE = "key_bubble_display_mode"
        private const val KEY_TASK_NOTES = "key_task_notes"

        const val PACKAGE_ZALO = "com.zing.zalo"
        const val PACKAGE_TELEGRAM = "org.telegram.messenger"
        const val PACKAGE_MESSENGER = "com.facebook.orca"

        @Volatile
        private var INSTANCE: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
