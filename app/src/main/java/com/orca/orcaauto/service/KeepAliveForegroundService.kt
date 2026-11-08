package com.orca.orcaauto.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service này đã được tắt hoàn toàn để giữ thanh thông báo luôn sạch sẽ.
 * Giữ lại định nghĩa để đảm bảo khả năng tương thích ngược và tự dừng ngay nếu được gọi.
 */
class KeepAliveForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _isRunningFlow.value = false
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunningFlow.value = false
    }

    companion object {
        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        fun start(context: Context) {
            // Đã vô hiệu hóa: Không khởi động dịch vụ thường trực
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, KeepAliveForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }
}
