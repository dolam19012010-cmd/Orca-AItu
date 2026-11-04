package com.orca.orcaauto.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action
        Log.i(TAG, "BootReceiver nhận tín hiệu: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            try {
                // Reschedule WorkManager tasks for scheduled hours
                WorkScheduler.scheduleDailySummaries(context)
                Log.i(TAG, "Đã lập lịch WorkManager thành công sau khi khởi động máy.")

                // Reschedule only ACTIVE alarms whose target time is in the future
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val dao = com.orca.orcaauto.data.local.AppDatabase.getDatabase(context).smartAlarmDao()
                        val now = System.currentTimeMillis()
                        val activeAlarms = dao.getActiveAlarms().filter { it.targetTimeMillis > now }
                        for (alarm in activeAlarms) {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = alarm.targetTimeMillis }
                            val mode = com.orca.orcaauto.domain.alarm.AlarmCascadeMode.fromCode(alarm.cascadeMode)
                            com.orca.orcaauto.domain.alarm.SystemAlarmHelper.scheduleSmartAlarms(context, alarm.taskTitle, cal, mode)
                        }
                        Log.i(TAG, "Đã khôi phục ${activeAlarms.size} báo thức ACTIVE sau khi khởi động máy.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Lỗi khôi phục báo thức sau boot: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi phục hồi tiến trình sau khi boot: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
