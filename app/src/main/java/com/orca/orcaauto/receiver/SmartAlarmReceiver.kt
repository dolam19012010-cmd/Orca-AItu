package com.orca.orcaauto.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.orca.orcaauto.R
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.service.FloatingBubbleService
import com.orca.orcaauto.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Singleton Audio Player for Loud System Alarm Ringtones.
 * Uses STREAM_ALARM / USAGE_ALARM to ensure loud playback even when device is on silent/vibrate,
 * and automatically cuts off after 45 seconds if unattended to conserve battery.
 */
object AlarmRingtonePlayer {
    private const val TAG = "AlarmRingtonePlayer"
    private var ringtone: Ringtone? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    fun play(context: Context) {
        stop()
        try {
            var alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            if (alertUri == null) {
                alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            val r = RingtoneManager.getRingtone(context.applicationContext, alertUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            r.play()
            ringtone = r

            stopRunnable = Runnable {
                stop()
            }
            mainHandler.postDelayed(stopRunnable!!, 45000L) // 45 seconds max ring
            Log.d(TAG, "Đang phát chuông báo thức hệ thống...")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi phát chuông báo thức: ${e.message}")
        }
    }

    fun stop() {
        try {
            stopRunnable?.let { mainHandler.removeCallbacks(it) }
            stopRunnable = null
            ringtone?.let {
                if (it.isPlaying) it.stop()
            }
            ringtone = null
            Log.d(TAG, "Đã tắt chuông báo thức.")
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi tắt chuông: ${e.message}")
        }
    }
}

class SmartAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()

        Log.i(TAG, "SmartAlarmReceiver nhận action: $action, alarmId: $alarmId, title: $taskTitle")

        // 1. Action: User tapped "TẮT CHUÔNG" on notification
        if (action == ACTION_DISMISS_ALARM_RING) {
            AlarmRingtonePlayer.stop()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notifId != -1) nm.cancel(notifId)
            if (taskTitle.isNotBlank()) {
                SystemAlarmHelper.markTaskCompleted(context, taskTitle, alarmId)
            }
            Toast.makeText(context, "✓ Đã tắt chuông báo!", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Action: User tapped "BÁO LẠI 5 PHÚT" on notification
        if (action == ACTION_SNOOZE_ALARM) {
            AlarmRingtonePlayer.stop()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notifId != -1) nm.cancel(notifId)
            if (taskTitle.isNotBlank()) {
                val snoozeCal = Calendar.getInstance().apply { add(Calendar.MINUTE, 5) }
                SystemAlarmHelper.scheduleSmartAlarms(context, taskTitle, snoozeCal, AlarmCascadeMode.X1)
                Toast.makeText(context, "⏰ Đã báo lại sau 5 phút: ${taskTitle.take(24)}...", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 3. Staging Event for long-term (> 24h) alarms
        if (action == ACTION_STAGE_CLOCK_ALARM && alarmId.isNotBlank()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                    val alarm = dao.getAlarmById(alarmId)
                    // Strict verification: only stage if STILL ACTIVE!
                    if (alarm != null && alarm.status == SmartAlarmStatus.ACTIVE && !alarm.isClockAppStaged) {
                        val cal = Calendar.getInstance().apply { timeInMillis = alarm.targetTimeMillis }
                        val mode = AlarmCascadeMode.fromCode(alarm.cascadeMode)
                        SystemAlarmHelper.scheduleSmartAlarms(context, alarm.taskTitle, cal, mode)
                        dao.updateClockAppStaged(alarmId, true)
                    }
                } catch (_: Exception) {} finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // 4. Exact Alarm Trigger Event (ACTION_FIRE_ALARM or default)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL).orEmpty()
        val isPrimary = intent.getBooleanExtra(EXTRA_IS_PRIMARY, true)

        if (taskTitle.isBlank() && label.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                val alarm = if (alarmId.isNotBlank()) dao.getAlarmById(alarmId) else null
                val allActive = dao.getActiveAlarms()
                val matchingActive = alarm ?: allActive.firstOrNull {
                    SystemAlarmHelper.isTitleMatching(it.taskTitle, taskTitle) ||
                    SystemAlarmHelper.normalizeTitle(it.taskTitle) == SystemAlarmHelper.normalizeTitle(taskTitle)
                }

                // =========================================================================
                // CRITICAL GATEKEEPER BARRIER:
                // If the user cancelled, disarmed (PROPOSED), or completed this task:
                // IMMEDIATELY ABORT! ZERO NOISE! ZERO NOTIFICATION! ZERO VIBRATION!
                // =========================================================================
                if (matchingActive == null || matchingActive.status != SmartAlarmStatus.ACTIVE) {
                    Log.w(TAG, "⛔ [CHẶN BÁO THỨC MA] Báo thức đã bị người dùng tắt hoặc hủy trong DB! Từ chối phát chuông: alarmId=$alarmId, title=$taskTitle")
                    return@launch
                }

                Log.i(TAG, "🔔 [BÁO THỨC HỢP LỆ] Đang kích hoạt chuông và thông báo: $taskTitle")

                withContext(Dispatchers.Main) {
                    // 1. Play loud System Alarm Ringtone
                    AlarmRingtonePlayer.play(context)

                    // 2. Light up screen via WakeLock
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    @Suppress("DEPRECATION")
                    val wakeLockFlags = PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
                    val wakeLock = powerManager?.newWakeLock(wakeLockFlags, "orca:SmartAlarmWakeLock")
                    try {
                        wakeLock?.acquire(45000L) // 45 seconds
                    } catch (_: Exception) {}

                    // 3. Show Heads-Up Alarm Notification
                    showAlarmNotification(context, matchingActive.alarmId, taskTitle, label, isPrimary)

                    // 4. Wake Floating Bubble to State 1 with clean Red Dot!
                    try {
                        UserPreferences.getInstance(context).setHasUnreadSummary(true)
                        FloatingBubbleService.wakeToState1(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể đánh thức bong bóng chat: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi xử lý báo thức: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showAlarmNotification(
        context: Context,
        alarmId: String,
        taskTitle: String,
        label: String,
        isPrimary: Boolean
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "orca_smart_alarm_loud_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel == null) {
                val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    "Chuông Báo Thức Nhiệm Vụ Orca",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Kênh chuông báo thức chính xác cao của OrcaAuto"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                    setSound(alarmSoundUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        val notificationId = (taskTitle.hashCode() and 0x7FFFFFFF) + if (isPrimary) 1 else 0

        // 1. Action: Tap body to open OrcaAuto
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "history")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: "TẮT CHUÔNG" button
        val dismissIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM_RING
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Action: "BÁO LẠI 5P" button
        val snoozeIntent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleText = if (isPrimary) "⏰ [ĐẾN HẠN CÔNG VIỆC]" else "🔔 [NHẮC CHUẨN BỊ]"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titleText)
            .setContentText(taskTitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$label\n\n📌 Nhiệm vụ: $taskTitle"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(0, "✓ TẮT CHUÔNG", dismissPendingIntent)
            .addAction(0, "⏰ BÁO LẠI 5P", snoozePendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        private const val TAG = "SmartAlarmReceiver"
        const val ACTION_FIRE_ALARM = "com.orca.orcaauto.ACTION_FIRE_ALARM"
        const val ACTION_STAGE_CLOCK_ALARM = "com.orca.orcaauto.ACTION_STAGE_CLOCK_ALARM"
        const val ACTION_DISMISS_ALARM_RING = "com.orca.orcaauto.ACTION_DISMISS_ALARM_RING"
        const val ACTION_SNOOZE_ALARM = "com.orca.orcaauto.ACTION_SNOOZE_ALARM"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_IS_PRIMARY = "extra_is_primary"
        const val EXTRA_OFFSET_MINUTES = "extra_offset_minutes"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
