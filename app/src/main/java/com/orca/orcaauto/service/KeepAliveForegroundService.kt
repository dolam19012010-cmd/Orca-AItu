package com.orca.orcaauto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.orca.orcaauto.R
import com.orca.orcaauto.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeepAliveForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = buildForegroundNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            _isRunningFlow.value = true
            return START_STICKY
        } catch (e: Throwable) {
            android.util.Log.e("KeepAliveService", "Không thể chạy Foreground Service (${e.message}). Dừng dịch vụ an toàn để không crash ứng dụng.", e)
            _isRunningFlow.value = false
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AItu đang hoạt động")
            .setContentText("Tiến trình ngầm đang theo dõi thông báo và bảo vệ lịch tóm tắt AI")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AItu Background Engine",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Kênh duy trì dịch vụ quét thông báo và bảo vệ tiến trình ngầm"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "orca_auto_keep_alive_channel"
        const val NOTIFICATION_ID = 1001

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        fun start(context: Context) {
            try {
                val intent = Intent(context, KeepAliveForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                android.util.Log.w("KeepAliveService", "Không thể gọi startForegroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
