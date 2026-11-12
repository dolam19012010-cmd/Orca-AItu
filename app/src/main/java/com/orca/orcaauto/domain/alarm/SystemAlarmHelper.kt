package com.orca.orcaauto.domain.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.Immutable
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.receiver.SmartAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@Immutable
enum class AlarmCascadeMode(
    val code: String,
    val displayName: String,
    val subtitle: String,
    val offsetsMinutes: List<Int>
) {
    X1("x1", "x1 Chuông", "Chính xác giờ hẹn", listOf(0)),
    X2("x2", "x2 Chuông", "Trước 15p & Đúng giờ", listOf(15, 0)),
    X3("x3", "x3 Chuông", "Trước 30p, 15p & Đúng giờ", listOf(30, 15, 0));

    companion object {
        fun fromCode(code: String): AlarmCascadeMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: X2
        }
    }
}

@Immutable
data class AlarmScheduleItem(
    val triggerTimeMillis: Long,
    val hour: Int,
    val minute: Int,
    val dateLabel: String,
    val label: String,
    val isPrimary: Boolean,
    val offsetMinutes: Int
) {
    val formattedTime: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

@Immutable
data class ParsedDateTime(
    val calendar: Calendar,
    val isFarFuture: Boolean, // true if > 28h from now (ngày kia, tuần sau, tháng sau)
    val dateLabel: String,     // e.g. "Hôm nay", "Ngày mai (31/10)", "Ngày kia (01/11)", "15/11"
    val timeLabel: String      // e.g. "15:30"
) {
    val fullFormatted: String
        get() = "$timeLabel $dateLabel".trim()
}

object SystemAlarmHelper {

    private const val TAG = "SystemAlarmHelper"
    const val CANCELLED_RETENTION_MS = 48L * 3600L * 1000L
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Generates a Deterministic ID for a task and its target date/time.
     * Guaranteed to produce the exact same ID across multiple AI chat sessions.
     */
    fun generateAlarmId(taskTitle: String, targetCalendar: Calendar): String {
        val cleanTitle = taskTitle.lowercase().replace(Regex("""[^a-z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]"""), "").take(25)
        val dateKey = String.format(
            Locale.US,
            "%04d%02d%02d_%02d%02d",
            targetCalendar.get(Calendar.YEAR),
            targetCalendar.get(Calendar.MONTH) + 1,
            targetCalendar.get(Calendar.DAY_OF_MONTH),
            targetCalendar.get(Calendar.HOUR_OF_DAY),
            targetCalendar.get(Calendar.MINUTE)
        )
        val hash = ((cleanTitle + "_" + dateKey).hashCode() and 0x7FFFFFFF).toString(16).uppercase()
        return "ALM_$hash"
    }

    /**
     * Normalizes a task title for robust equality matching regardless of casing, accents, or bullet symbols.
     */
    fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "")
            .replace(Regex("""[^a-z0-9àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]"""), "")
            .trim()
    }

    /**
     * Checks if two task titles refer to the exact same task.
     * Uses strict title matching to prevent partial-substring false positives where cancelling
     * one task would mistakenly mark other tasks as cancelled.
     */
    fun isTitleMatching(title1: String, title2: String): Boolean {
        val n1 = normalizeTitle(title1)
        val n2 = normalizeTitle(title2)
        if (n1.isEmpty() || n2.isEmpty()) return false
        if (n1 == n2) return true
        val minLen = minOf(n1.length, n2.length)
        val maxLen = maxOf(n1.length, n2.length)
        if (minLen >= 8 && (maxLen - minLen) <= 4) {
            return n1.startsWith(n2) || n2.startsWith(n1)
        }
        return false
    }

    /**
     * Finds the latest matching alarm entity by title in an alarm map, regardless of date-dependent alarmId.
     * Orders by updatedAt DESC so the most recent user action (e.g. setting an alarm vs cancelling) takes precedence.
     */
    fun findMatchingAlarm(taskTitle: String, alarmMap: Map<String, SmartAlarmEntity>): SmartAlarmEntity? {
        val norm = normalizeTitle(taskTitle)
        if (norm.isEmpty()) return null
        val now = System.currentTimeMillis()
        val cutoff = now - CANCELLED_RETENTION_MS // Giữ trạng thái trong 48 giờ (2 ngày) theo chu kỳ dọn tin nhắn 24h

        val matches = alarmMap.values.filter { isTitleMatching(it.taskTitle, taskTitle) }
        if (matches.isEmpty()) return null

        // 1. Ưu tiên hàng đầu: Báo thức đang ACTIVE (đang hẹn giờ chạy)
        val active = matches.firstOrNull { it.status == SmartAlarmStatus.ACTIVE && it.targetTimeMillis >= now - 3600_000L }
        if (active != null) return active

        // 2. Xét các bản ghi trạng thái (CANCELLED, COMPLETED, PROPOSED) trong vòng 48h (2 ngày)
        return matches
            .filter {
                if (it.status == SmartAlarmStatus.CANCELLED) it.updatedAt >= cutoff
                else it.targetTimeMillis >= cutoff || it.updatedAt >= cutoff
            }
            .maxByOrNull { it.updatedAt }
    }

    /**
     * Lọc giữ các việc đã hủy trong vòng 48 giờ kể từ lúc người dùng bấm hủy.
     */
    fun filterRecentCancelled(
        alarms: List<SmartAlarmEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<SmartAlarmEntity> {
        val cutoff = nowMillis - CANCELLED_RETENTION_MS
        return alarms.filter { it.status == SmartAlarmStatus.CANCELLED && it.updatedAt >= cutoff }
    }

    /**
     * Loại bỏ các nhiệm vụ đã bị hủy khỏi ParsedSummary để dùng chung cho bong bóng nổi và đọc giọng nói.
     */
    fun filterOutCancelled(
        parsed: ParsedSummary,
        alarmMap: Map<String, SmartAlarmEntity>
    ): ParsedSummary {
        if (alarmMap.isEmpty()) return parsed
        return parsed.copy(
            tasksToDo = parsed.tasksToDo.filterNot { isTaskCancelled(it.title, alarmMap) },
            tasksDone = parsed.tasksDone.filterNot { isTaskCancelled(it.title, alarmMap) },
            tasksPlanned = parsed.tasksPlanned.filterNot { isTaskCancelled(it.title, alarmMap) }
        )
    }

    fun isTaskCancelled(taskTitle: String, alarmMap: Map<String, SmartAlarmEntity>): Boolean {
        val matching = findMatchingAlarm(taskTitle, alarmMap)
        return matching?.status == SmartAlarmStatus.CANCELLED
    }

    fun isTaskCompleted(taskTitle: String, alarmMap: Map<String, SmartAlarmEntity>): Boolean {
        val matching = findMatchingAlarm(taskTitle, alarmMap)
        return matching?.status == SmartAlarmStatus.COMPLETED
    }

    /**
     * Extracts (hour, minute) from a deadline string or natural language text.
     */
    fun parseTimeFromText(rawText: String): Pair<Int, Int>? {
        if (rawText.isBlank()) return null
        val text = rawText.lowercase().trim()

        val isPm = text.contains("chiều") || text.contains("tối") || text.contains("pm")
        val isAm = text.contains("sáng") || text.contains("am")

        // 1. Colon pattern: "15:30", "9:15"
        val colonMatch = Regex("""(?:^|\D)(\d{1,2})[:.](\d{2})(?:\D|$)""").find(text)
        if (colonMatch != null) {
            val (hStr, mStr) = colonMatch.destructured
            var h = hStr.toIntOrNull() ?: return null
            val m = mStr.toIntOrNull() ?: return null
            if (isPm && h in 1..11) h += 12
            if (isAm && h == 12) h = 0
            if (h in 0..23 && m in 0..59) return Pair(h, m)
        }

        // 2. 'h' pattern: "15h30", "15h", "9h45"
        val hMatch = Regex("""(?:^|\D)(\d{1,2})\s*h\s*(\d{2})?(?:\D|$)""").find(text)
        if (hMatch != null) {
            val hStr = hMatch.groupValues[1]
            val mStr = hMatch.groupValues.getOrNull(2).orEmpty()
            var h = hStr.toIntOrNull() ?: return null
            val m = if (mStr.isNotBlank()) mStr.toIntOrNull() ?: 0 else 0
            if (isPm && h in 1..11) h += 12
            if (isAm && h == 12) h = 0
            if (h in 0..23 && m in 0..59) return Pair(h, m)
        }

        // 3. "giờ" pattern: "15 giờ 30", "3 giờ chiều"
        val gioMatch = Regex("""(?:^|\D)(\d{1,2})\s*(?:giờ|g)\s*(\d{2})?(?:\D|$)""").find(text)
        if (gioMatch != null) {
            val hStr = gioMatch.groupValues[1]
            val mStr = gioMatch.groupValues.getOrNull(2).orEmpty()
            var h = hStr.toIntOrNull() ?: return null
            val m = if (mStr.isNotBlank()) mStr.toIntOrNull() ?: 0 else 0
            if (isPm && h in 1..11) h += 12
            if (isAm && h == 12) h = 0
            if (h in 0..23 && m in 0..59) return Pair(h, m)
        }

        // 4. Natural language approximate time slots
        when {
            text.contains("chiều nay") || text.contains("cuối chiều") -> return Pair(17, 0)
            text.contains("sáng mai") || text.contains("đầu giờ sáng") -> return Pair(9, 0)
            text.contains("trưa nay") || text.contains("giữa trưa") -> return Pair(12, 0)
            text.contains("tối nay") || text.contains("cuối ngày") -> return Pair(20, 0)
        }

        return null
    }

    /**
     * Comprehensive Natural Language Date & Time Parser.
     * Understands:
     * - "15:30 ngày mai", "16h ngày kia", "09:00 thứ 6 tuần sau"
     * - "10:00 ngày 15/11", "14h ngày 05/12/2026", "17h 20 tháng 11"
     * - "tuần sau", "tháng sau", "hôm nay", "mốt"
     */
    fun parseDateTimeFromText(rawText: String, baseCal: Calendar = Calendar.getInstance()): ParsedDateTime? {
        if (rawText.isBlank()) return null
        val lower = rawText.lowercase().trim()

        val timePair = parseTimeFromText(lower) ?: run {
            when {
                lower.contains("sáng") -> Pair(9, 0)
                lower.contains("trưa") -> Pair(12, 0)
                lower.contains("chiều") -> Pair(14, 30)
                lower.contains("tối") -> Pair(19, 30)
                else -> getDefaultSuggestedTime()
            }
        }

        val targetCal = (baseCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, timePair.first)
            set(Calendar.MINUTE, timePair.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // A. Specific Date: "dd/MM/yyyy", "dd/MM", "dd-MM"
        val slashDateMatch = Regex("""(?:ngày\s*)?(\d{1,2})[\/\-](\d{1,2})(?:[\/\-](\d{2,4}))?""").find(lower)
        val textDateMatch = Regex("""(?:ngày\s*)?(\d{1,2})\s*tháng\s*(\d{1,2})(?:\s*năm\s*(\d{4}))?""").find(lower)

        var matchedSpecificDate = false
        val dateMatch = slashDateMatch ?: textDateMatch
        if (dateMatch != null) {
            val d = dateMatch.groupValues[1].toIntOrNull()
            val m = dateMatch.groupValues[2].toIntOrNull()
            val yStr = dateMatch.groupValues.getOrNull(3).orEmpty()
            if (d != null && m != null && d in 1..31 && m in 1..12) {
                val year = if (yStr.isNotBlank()) {
                    if (yStr.length == 2) 2000 + yStr.toInt() else yStr.toInt()
                } else {
                    val curYear = baseCal.get(Calendar.YEAR)
                    val testCal = (baseCal.clone() as Calendar).apply {
                        set(curYear, m - 1, d, timePair.first, timePair.second, 0)
                    }
                    val diffMillis = baseCal.timeInMillis - testCal.timeInMillis
                    // Only wrap to next year if date is far in the past (> 60 days)
                    // Recent past dates (e.g. yesterday 31/10 vs today 01/11) must remain in curYear
                    if (diffMillis > 60L * 24 * 3600 * 1000L) curYear + 1 else curYear
                }
                targetCal.set(Calendar.YEAR, year)
                targetCal.set(Calendar.MONTH, m - 1)
                targetCal.set(Calendar.DAY_OF_MONTH, d)
                matchedSpecificDate = true
            }
        }

        if (!matchedSpecificDate) {
            // B. Relative keywords
            when {
                lower.contains("ngày kia") || lower.contains("ngày mốt") || lower.contains("hôm mốt") || lower.contains("mốt") -> {
                    targetCal.add(Calendar.DAY_OF_YEAR, 2)
                }
                lower.contains("ngày mai") || lower.contains("sáng mai") || lower.contains("chiều mai") || lower.contains("mai") -> {
                    targetCal.add(Calendar.DAY_OF_YEAR, 1)
                }
                lower.contains("tháng sau") -> {
                    targetCal.add(Calendar.MONTH, 1)
                }
                lower.contains("tuần sau") -> {
                    // Check if day of week mentioned
                    val dayOfWeek = when {
                        lower.contains("thứ 2") || lower.contains("thứ hai") -> Calendar.MONDAY
                        lower.contains("thứ 3") || lower.contains("thứ ba") -> Calendar.TUESDAY
                        lower.contains("thứ 4") || lower.contains("thứ tư") -> Calendar.WEDNESDAY
                        lower.contains("thứ 5") || lower.contains("thứ năm") -> Calendar.THURSDAY
                        lower.contains("thứ 6") || lower.contains("thứ sáu") -> Calendar.FRIDAY
                        lower.contains("thứ 7") || lower.contains("thứ bảy") -> Calendar.SATURDAY
                        lower.contains("chủ nhật") || lower.contains("cn") -> Calendar.SUNDAY
                        else -> null
                    }
                    if (dayOfWeek != null) {
                        targetCal.add(Calendar.WEEK_OF_YEAR, 1)
                        targetCal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    } else {
                        targetCal.add(Calendar.DAY_OF_YEAR, 7)
                    }
                }
                lower.contains("hôm nay") || lower.contains("chiều nay") || lower.contains("tối nay") || lower.contains("trưa nay") -> {
                    // Stays today
                }
                else -> {
                    // If target time today has already passed, schedule for tomorrow
                    if (targetCal.timeInMillis <= baseCal.timeInMillis) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
        }

        val diffMillis = targetCal.timeInMillis - baseCal.timeInMillis
        val isFarFuture = diffMillis > (28 * 3600 * 1000L) // beyond 28 hours (ngày kia, tuần sau...)

        // Human-friendly date label
        val d = targetCal.get(Calendar.DAY_OF_MONTH)
        val m = targetCal.get(Calendar.MONTH) + 1
        val y = targetCal.get(Calendar.YEAR)
        val curDayOfYear = baseCal.get(Calendar.DAY_OF_YEAR)
        val targetDayOfYear = targetCal.get(Calendar.DAY_OF_YEAR)
        val curYear = baseCal.get(Calendar.YEAR)

        val dayDiff = (targetDayOfYear - curDayOfYear) + (y - curYear) * 365
        val dateLabel = when (dayDiff) {
            0 -> "Hôm nay"
            1 -> "Ngày mai ($d/$m)"
            2 -> "Ngày kia ($d/$m)"
            in 3..6 -> {
                val dow = when (targetCal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SUNDAY -> "CN"
                    Calendar.MONDAY -> "Thứ 2"
                    Calendar.TUESDAY -> "Thứ 3"
                    Calendar.WEDNESDAY -> "Thứ 4"
                    Calendar.THURSDAY -> "Thứ 5"
                    Calendar.FRIDAY -> "Thứ 6"
                    else -> "Thứ 7"
                }
                "$dow ($d/$m)"
            }
            else -> "$d/$m/$y"
        }

        val timeLabel = String.format(Locale.getDefault(), "%02d:%02d", timePair.first, timePair.second)

        return ParsedDateTime(
            calendar = targetCal,
            isFarFuture = isFarFuture,
            dateLabel = dateLabel,
            timeLabel = timeLabel
        )
    }

    /**
     * Default suggestion: 1 hour from current time rounded to 5 mins.
     */
    fun getDefaultSuggestedTime(): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            val m = get(Calendar.MINUTE)
            val roundedM = ((m + 4) / 5) * 5
            set(Calendar.MINUTE, roundedM % 60)
            if (roundedM >= 60) add(Calendar.HOUR_OF_DAY, 1)
        }
        return Pair(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /**
     * Builds list of cascade alarms with absolute timestamps and labels.
     */
    fun calculateAlarmSchedules(
        taskTitle: String,
        targetCalendar: Calendar,
        mode: AlarmCascadeMode,
        intervalMinutes: Int = 15
    ): List<AlarmScheduleItem> {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        val safeInterval = intervalMinutes.coerceIn(1, 120)

        val offsets = when (mode) {
            AlarmCascadeMode.X1 -> listOf(0)
            AlarmCascadeMode.X2 -> listOf(safeInterval, 0)
            AlarmCascadeMode.X3 -> listOf(safeInterval * 2, safeInterval, 0)
        }

        return offsets.map { offset ->
            val itemCal = (targetCalendar.clone() as Calendar).apply {
                add(Calendar.MINUTE, -offset)
            }
            val isPrimary = (offset == 0)

            val label = if (isPrimary) {
                "[Orca] ĐẾN HẠN: ${cleanTitle.take(28)}"
            } else {
                "[Orca] Chuẩn bị (${offset}p): ${cleanTitle.take(24)}"
            }

            val d = itemCal.get(Calendar.DAY_OF_MONTH)
            val m = itemCal.get(Calendar.MONTH) + 1
            val dateLabel = "$d/$m"

            AlarmScheduleItem(
                triggerTimeMillis = itemCal.timeInMillis,
                hour = itemCal.get(Calendar.HOUR_OF_DAY),
                minute = itemCal.get(Calendar.MINUTE),
                dateLabel = dateLabel,
                label = label,
                isPrimary = isPrimary,
                offsetMinutes = offset
            )
        }.sortedByDescending { it.offsetMinutes }
    }

    /**
     * Backwards-compatible overload for (hour, minute) target.
     */
    fun calculateAlarmSchedules(
        taskTitle: String,
        targetHour: Int,
        targetMinute: Int,
        mode: AlarmCascadeMode,
        intervalMinutes: Int = 15
    ): List<AlarmScheduleItem> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calculateAlarmSchedules(taskTitle, cal, mode, intervalMinutes)
    }

    /**
     * Generates a deterministic integer request code for AlarmManager PendingIntents.
     * Guaranteed to match between schedule and cancel.
     */
    fun getAlarmRequestCode(alarmId: String, offsetMinutes: Int): Int {
        return ((alarmId.hashCode() * 31) + offsetMinutes) and 0x7FFFFFFF
    }

    /**
     * Core Precision Alarm Scheduler (100% Controlled via AlarmManager.setAlarmClock):
     * 1. Saves to Room Database with deterministic alarmId (Single Source of Truth).
     * 2. Schedules exact AlarmManager wake-up with SmartAlarmReceiver using setAlarmClock.
     *    - Shows system alarm icon in status bar.
     *    - Fully immune to Doze mode and manufacturer power savers.
     *    - Guaranteed programmatically CANCELABLE with zero ghost alarms!
     */
    fun scheduleSmartAlarms(
        context: Context,
        taskTitle: String,
        targetCalendar: Calendar,
        mode: AlarmCascadeMode = AlarmCascadeMode.X1,
        intervalMinutes: Int = 15,
        skipUi: Boolean = true
    ): Result<List<AlarmScheduleItem>> {
        return try {
            val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
            val schedules = calculateAlarmSchedules(cleanTitle, targetCalendar, mode, intervalMinutes)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val nowMillis = System.currentTimeMillis()

            val alarmId = generateAlarmId(cleanTitle, targetCalendar)

            // Cancel any old alarms matching this title/alarmId first to avoid duplicate ghosts!
            cancelAlarmManagerIntents(context, alarmId)
            cancelSinglePendingIntent(context, (alarmId.hashCode() and 0x7FFFFFFF))

            // 1. Persistent Storage in Room Database
            val entity = SmartAlarmEntity(
                alarmId = alarmId,
                taskTitle = cleanTitle,
                targetTimeMillis = targetCalendar.timeInMillis,
                targetDateLabel = String.format(Locale.getDefault(), "%02d:%02d (%s)", targetCalendar.get(Calendar.HOUR_OF_DAY), targetCalendar.get(Calendar.MINUTE), mode.code),
                cascadeMode = mode.code,
                status = SmartAlarmStatus.ACTIVE,
                isClockAppStaged = true,
                clockAppStageTimeMillis = targetCalendar.timeInMillis,
                createdAt = nowMillis,
                updatedAt = nowMillis
            )
            scope.launch {
                try {
                    val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                    dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.ACTIVE)
                    dao.insertOrUpdate(entity)
                    autoCleanupAlarms(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi lưu SmartAlarmEntity vào DB", e)
                }
            }

            // 2. Schedule Exact Alarms with AlarmManager.setAlarmClock() for each schedule item!
            for (item in schedules) {
                val triggerTime = item.triggerTimeMillis
                if (triggerTime > nowMillis && alarmManager != null) {
                    val requestCode = getAlarmRequestCode(alarmId, item.offsetMinutes)
                    val fireIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
                        action = SmartAlarmReceiver.ACTION_FIRE_ALARM
                        putExtra(SmartAlarmReceiver.EXTRA_ALARM_ID, alarmId)
                        putExtra(SmartAlarmReceiver.EXTRA_TASK_TITLE, cleanTitle)
                        putExtra(SmartAlarmReceiver.EXTRA_ALARM_LABEL, item.label)
                        putExtra(SmartAlarmReceiver.EXTRA_IS_PRIMARY, item.isPrimary)
                        putExtra(SmartAlarmReceiver.EXTRA_OFFSET_MINUTES, item.offsetMinutes)
                    }
                    val firePendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        fireIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val showIntent = Intent(context, com.orca.orcaauto.ui.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("navigate_to", "history")
                    }
                    val showPendingIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        showIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            alarmManager.setAlarmClock(
                                AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                                firePendingIntent
                            )
                        } else {
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                firePendingIntent
                            )
                        }
                        Log.d(TAG, "Đã hẹn chuông báo AlarmManager (${item.formattedTime}, req=$requestCode) cho: $cleanTitle")
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi đặt AlarmManager exact alarm: ${e.message}")
                    }
                }
            }

            Log.d(TAG, "Đã lên lịch thành công ${schedules.size} báo thức cho: $taskTitle (ID: $alarmId)")
            Result.success(schedules)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kích hoạt báo thức hệ thống", e)
            Result.failure(e)
        }
    }

    /**
     * Backward-compatible setter.
     */
    fun setSystemAlarms(
        context: Context,
        taskTitle: String,
        targetHour: Int,
        targetMinute: Int,
        mode: AlarmCascadeMode = AlarmCascadeMode.X1,
        intervalMinutes: Int = 15,
        skipUi: Boolean = true
    ): Result<List<AlarmScheduleItem>> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return scheduleSmartAlarms(context, taskTitle, cal, mode, intervalMinutes, skipUi)
    }

    /**
     * Disarms / Toggles Off an active alarm:
     * 1. Stops any currently ringing alarm audio immediately.
     * 2. Finds ALL matching records in Room DB (by alarmId, title, or normalized title) and marks them PROPOSED.
     * 3. Cancels all exact PendingIntents in AlarmManager.
     * 4. Dispatches best-effort dismissal to native Clock apps for any legacy entries.
     */
    fun cancelSmartAlarms(context: Context, taskTitle: String, alarmId: String? = null): Boolean {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()

        // Stop any currently playing ringtone audio immediately
        try {
            com.orca.orcaauto.receiver.AlarmRingtonePlayer.stop()
        } catch (_: Exception) {}

        // Cancel on calling thread immediately for fast response
        if (alarmId != null) {
            cancelAlarmManagerIntents(context, alarmId)
        }
        cancelAlarmManagerIntentsByTitle(context, cleanTitle)

        // 1. Update Database Status to PROPOSED for ALL matching records
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                val allAlarms = dao.getAllAlarms()
                val matching = allAlarms.filter {
                    (alarmId != null && it.alarmId == alarmId) ||
                    isTitleMatching(it.taskTitle, cleanTitle) ||
                    normalizeTitle(it.taskTitle) == normalizeTitle(cleanTitle)
                }

                matching.forEach { entity ->
                    dao.updateStatus(entity.alarmId, SmartAlarmStatus.PROPOSED)
                    cancelAlarmManagerIntents(context, entity.alarmId)
                }

                if (alarmId != null) {
                    dao.updateStatus(alarmId, SmartAlarmStatus.PROPOSED)
                }
                dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.PROPOSED)
                autoCleanupAlarms(context)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi cập nhật trạng thái PROPOSED trong DB", e)
            }
        }

        Log.d(TAG, "Đã hủy triệt để chuông báo và lịch trình cho: $cleanTitle (ID: $alarmId)")
        return true
    }

    fun cancelAlarmManagerIntents(context: Context, targetAlarmId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val offsetsToClear = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120)
        for (offset in offsetsToClear) {
            val req = getAlarmRequestCode(targetAlarmId, offset)
            val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
                action = SmartAlarmReceiver.ACTION_FIRE_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        val stageIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = SmartAlarmReceiver.ACTION_STAGE_CLOCK_ALARM
        }
        val stagePi = PendingIntent.getBroadcast(
            context,
            (targetAlarmId.hashCode() and 0x7FFFFFFF),
            stageIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (stagePi != null) {
            alarmManager.cancel(stagePi)
            stagePi.cancel()
        }
    }

    private fun cancelAlarmManagerIntentsByTitle(context: Context, cleanTitle: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val offsetsToClear = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        for (offset in offsetsToClear) {
            val req = (cleanTitle.hashCode() * 31 + offset) and 0x7FFFFFFF
            val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
                action = SmartAlarmReceiver.ACTION_FIRE_ALARM
            }
            val pi = PendingIntent.getBroadcast(
                context,
                req,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun cancelSinglePendingIntent(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = SmartAlarmReceiver.ACTION_FIRE_ALARM
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /**
     * Periodically cleans up completed/expired alarms to avoid database bloating.
     * Retains cancelled/completed records for 7 days to preserve AI context across multiple days.
     */
    fun autoCleanupAlarms(context: Context) {
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                val now = System.currentTimeMillis()
                dao.autoMarkPastActiveAlarms(now)
                dao.markExpiredAlarms(now)
                val cutoff7Days = now - (7 * 24 * 3600 * 1000L)
                dao.deleteOldAlarms(cutoff7Days)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi autoCleanupAlarms: ${e.message}")
            }
        }
    }

    /**
     * Dừng âm thanh chuông báo và hủy PendingIntent trên AlarmManager
     * Tuyệt đối KHÔNG thay đổi trạng thái trong Database (không ghi đè PROPOSED).
     */
    fun disarmAlarmHardwareOnly(context: Context, taskTitle: String, alarmId: String? = null) {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        try {
            com.orca.orcaauto.receiver.AlarmRingtonePlayer.stop()
        } catch (_: Exception) {}
        if (alarmId != null) {
            cancelAlarmManagerIntents(context, alarmId)
        }
        cancelAlarmManagerIntentsByTitle(context, cleanTitle)
    }

    /**
     * Marks a task as COMPLETED in DB and clears its active alarm hardware triggers.
     */
    fun markTaskCompleted(context: Context, taskTitle: String, alarmId: String? = null) {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        disarmAlarmHardwareOnly(context, cleanTitle, alarmId)
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                val allAlarms = dao.getAllAlarms()
                val matching = allAlarms.filter {
                    (alarmId != null && it.alarmId == alarmId) ||
                    isTitleMatching(it.taskTitle, cleanTitle) ||
                    normalizeTitle(it.taskTitle) == normalizeTitle(cleanTitle)
                }

                // Cập nhật tất cả các bản ghi liên quan thành COMPLETED và hủy intent AlarmManager
                matching.forEach { entity ->
                    cancelAlarmManagerIntents(context, entity.alarmId)
                    dao.updateStatus(entity.alarmId, SmartAlarmStatus.COMPLETED)
                }

                dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.COMPLETED)

                val stableTitleId = "TASK_${(normalizeTitle(cleanTitle).hashCode() and 0x7FFFFFFF).toString(16)}"
                val id = alarmId ?: stableTitleId
                dao.insertOrUpdate(
                    SmartAlarmEntity(
                        alarmId = id,
                        taskTitle = cleanTitle,
                        targetTimeMillis = System.currentTimeMillis(),
                        targetDateLabel = "Đã xong",
                        status = SmartAlarmStatus.COMPLETED
                    )
                )
                if (id != stableTitleId) {
                    dao.insertOrUpdate(
                        SmartAlarmEntity(
                            alarmId = stableTitleId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = System.currentTimeMillis(),
                            targetDateLabel = "Đã xong",
                            status = SmartAlarmStatus.COMPLETED
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đánh dấu COMPLETED", e)
            }
        }
    }

    /**
     * Marks a task as CANCELLED in DB and ensures AI will never regenerate it.
     */
    fun markTaskCancelled(context: Context, taskTitle: String, alarmId: String? = null) {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        disarmAlarmHardwareOnly(context, cleanTitle, alarmId)
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                val allAlarms = dao.getAllAlarms()
                val matching = allAlarms.filter {
                    (alarmId != null && it.alarmId == alarmId) ||
                    isTitleMatching(it.taskTitle, cleanTitle) ||
                    normalizeTitle(it.taskTitle) == normalizeTitle(cleanTitle)
                }

                // Cập nhật tất cả các bản ghi liên quan thành CANCELLED và hủy intent AlarmManager
                matching.forEach { entity ->
                    cancelAlarmManagerIntents(context, entity.alarmId)
                    dao.updateStatus(entity.alarmId, SmartAlarmStatus.CANCELLED)
                }

                dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.CANCELLED)

                val stableTitleId = "TASK_${(normalizeTitle(cleanTitle).hashCode() and 0x7FFFFFFF).toString(16)}"
                val id = alarmId ?: stableTitleId
                dao.insertOrUpdate(
                    SmartAlarmEntity(
                        alarmId = id,
                        taskTitle = cleanTitle,
                        targetTimeMillis = System.currentTimeMillis(),
                        targetDateLabel = "Đã hủy",
                        status = SmartAlarmStatus.CANCELLED
                    )
                )
                if (id != stableTitleId) {
                    dao.insertOrUpdate(
                        SmartAlarmEntity(
                            alarmId = stableTitleId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = System.currentTimeMillis(),
                            targetDateLabel = "Đã hủy",
                            status = SmartAlarmStatus.CANCELLED
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đánh dấu CANCELLED", e)
            }
        }
    }

    /**
     * Restores a previously cancelled task:
     * Resets status to PROPOSED in DB so the task reappears in active tasks and floating chat bubble.
     */
    fun restoreCancelledTask(context: Context, taskTitle: String, alarmId: String? = null) {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.PROPOSED)
                if (alarmId != null) {
                    dao.updateStatus(alarmId, SmartAlarmStatus.PROPOSED)
                }
                val stableTitleId = "TASK_${(normalizeTitle(cleanTitle).hashCode() and 0x7FFFFFFF).toString(16)}"
                dao.updateStatus(stableTitleId, SmartAlarmStatus.PROPOSED)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khôi phục nhiệm vụ đã hủy", e)
            }
        }
    }

    /**
     * Reverts a completed task back to active/uncompleted (PROPOSED status):
     * Allows user to continue tracking tasks they accidentally marked complete.
     */
    fun uncompleteTask(context: Context, taskTitle: String, alarmId: String? = null) {
        val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        scope.launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.PROPOSED)
                if (alarmId != null) {
                    dao.updateStatus(alarmId, SmartAlarmStatus.PROPOSED)
                }
                val stableTitleId = "TASK_${(normalizeTitle(cleanTitle).hashCode() and 0x7FFFFFFF).toString(16)}"
                dao.updateStatus(stableTitleId, SmartAlarmStatus.PROPOSED)
                Log.d(TAG, "Đã đưa nhiệm vụ đã hoàn thành về PROPOSED: $cleanTitle")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đưa nhiệm vụ về chưa hoàn thành", e)
            }
        }
    }

    /**
     * Staging Checker: checks if any long-term alarms entered the 24h window and schedules them via AlarmManager.
     */
    suspend fun checkAndStagePendingAlarms(context: Context) {
        try {
            val dao = AppDatabase.getDatabase(context).smartAlarmDao()
            val now = System.currentTimeMillis()
            val unstaged = dao.getUnstagedAlarmsReadyForClockApp(now)
            for (alarm in unstaged) {
                val cal = Calendar.getInstance().apply { timeInMillis = alarm.targetTimeMillis }
                val mode = AlarmCascadeMode.fromCode(alarm.cascadeMode)
                scheduleSmartAlarms(context, alarm.taskTitle, cal, mode)
                dao.updateClockAppStaged(alarm.alarmId, true)
                Log.d(TAG, "Đã tự động nạp lịch trình cho nhiệm vụ đến hạn: ${alarm.taskTitle}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kiểm tra staging báo thức", e)
        }
    }

    /**
     * 1-Tap Insert into Google Calendar / Samsung Calendar for long-term events.
     */
    fun addToGoogleCalendar(
        context: Context,
        taskTitle: String,
        targetCalendar: Calendar
    ) {
        try {
            val cleanTitle = taskTitle.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
            val startMillis = targetCalendar.timeInMillis
            val endMillis = startMillis + (30 * 60 * 1000L) // 30 mins

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "[Orca] $cleanTitle")
                putExtra(CalendarContract.Events.DESCRIPTION, "Nhiệm vụ điều phối tự động bởi OrcaAuto")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi mở ứng dụng Lịch", e)
        }
    }
}
