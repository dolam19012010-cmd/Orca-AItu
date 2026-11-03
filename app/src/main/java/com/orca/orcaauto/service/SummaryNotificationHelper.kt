package com.orca.orcaauto.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.orca.orcaauto.R
import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.ui.MainActivity

object SummaryNotificationHelper {

    private const val CHANNEL_ID = "orca_auto_report_channel"
    private const val NOTIFICATION_ID = 2002

    /**
     * Sends a reliable, clean notification report.
     * Uses setOnlyAlertOnce(true) to completely prevent sound/vibration spamming.
     */
    fun showSummaryReportNotification(
        context: Context,
        timeSlot: String,
        messageCount: Int,
        parsed: ParsedSummary,
        rawSummary: String = "",
        modelInfo: String = ""
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createReportChannel(notificationManager)

        val urgentCount = parsed.tasksToDo.size
        val title = "🎯 Báo Cáo AI [$timeSlot]: $urgentCount Việc Cần Làm!"

        // 1. PendingIntent for opening History in MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "history")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 2. PendingIntent for popping up Floating Bubble
        val bubbleIntent = Intent(context, FloatingBubbleService::class.java).apply {
            putExtra(FloatingBubbleService.EXTRA_SUMMARY_CONTENT, rawSummary.ifBlank { parsed.rawCleanText })
            putExtra(FloatingBubbleService.EXTRA_MODEL_INFO, modelInfo)
        }
        val bubblePendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_ID + 1,
            bubbleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Format clean preview body
        val cleanAdvice = SummaryParser.cleanAdviceText(parsed.conciseAdvice)
        val bigBodyText = buildString {
            if (cleanAdvice.isNotBlank()) {
                append("💡 $cleanAdvice\n\n")
            }
            if (parsed.tasksToDo.isNotEmpty()) {
                append("🔴 Việc Cần Làm (${parsed.tasksToDo.size}):\n")
                parsed.tasksToDo.take(3).forEach { task ->
                    append("• ${task.title}")
                    if (task.deadline.isNotBlank()) append(" (${task.deadline})")
                    append("\n")
                }
            }
            if (parsed.tasksPlanned.isNotEmpty()) {
                append("\n🟡 Kế Hoạch (${parsed.tasksPlanned.size}):\n")
                parsed.tasksPlanned.take(2).forEach { task ->
                    append("• ${task.title}\n")
                }
            }
        }.ifBlank { "Đã tóm tắt $messageCount tin nhắn trong ca." }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(cleanAdvice.ifBlank { "Đã tóm tắt $messageCount tin nhắn trong ca." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setOnlyAlertOnce(true) // CRITICAL: Never spam chime/vibrate on re-post
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "Xem Ứng Dụng",
                contentPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                "Bong Bóng Nổi",
                bubblePendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createReportChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Báo Cáo Tóm Tắt AI",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo tóm tắt công việc và lời khuyên điều phối từ Trợ lý AI"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }
}
