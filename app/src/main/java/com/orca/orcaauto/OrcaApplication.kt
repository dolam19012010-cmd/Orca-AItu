package com.orca.orcaauto

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrcaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OrcaApplication initializing...")

        // Theo dõi trạng thái màn hình của app (đang mở hay đang chạy ngầm)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumedActivities = 0
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = resumedActivities > 0
            }
            override fun onActivityPaused(activity: Activity) {
                resumedActivities--
                isAppInForeground = resumedActivities > 0
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        // Global crash guard to prevent silent termination
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL CRASH trên luồng ${thread.name}: ${throwable.message}", throwable)
            try {
                // CHỈ mở màn hình CrashActivity nếu người dùng ĐANG MỞ APP trực tiếp.
                // Nếu app đang chạy dịch vụ ngầm (người dùng đang chơi game, lướt web), TUYỆT ĐỐI KHÔNG cướp màn hình!
                if (isAppInForeground) {
                    val intent = android.content.Intent(this, com.orca.orcaauto.ui.CrashActivity::class.java).apply {
                        putExtra(com.orca.orcaauto.ui.CrashActivity.EXTRA_ERROR_TYPE, throwable.javaClass.name)
                        putExtra(com.orca.orcaauto.ui.CrashActivity.EXTRA_ERROR_MSG, throwable.message ?: "Không có chi tiết")
                        putExtra(com.orca.orcaauto.ui.CrashActivity.EXTRA_ERROR_STACK, throwable.stackTraceToString())
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    startActivity(intent)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // 1. Initialize Database & Secure Preferences singletons safely
        try {
            AppDatabase.getDatabase(this)
            UserPreferences.getInstance(this)
            com.orca.orcaauto.data.backup.BackupManager.checkAndRecoverFromSnapshot(this)
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi khi khởi tạo Database/Preferences: ${e.message}", e)
        }

        // 2. Schedule summaries lazily via Coroutine so onCreate never blocks or allocates unmanaged OS threads
        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(1500)
                WorkScheduler.scheduleDailySummaries(this@OrcaApplication)
            } catch (e: Throwable) {
                Log.w(TAG, "Lỗi khi lập lịch WorkScheduler: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "OrcaApplication"
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}
