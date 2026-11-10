package com.orca.orcaauto.service

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import java.util.Locale

object OemSurvivalHelper {

    private const val TAG = "OemSurvivalHelper"

    /**
     * Checks if Notification Access permission is granted to this app.
     */
    fun isNotificationAccessGranted(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    /**
     * Returns an Intent to open the Notification Listener Settings page.
     */
    fun getNotificationAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, NotificationMonitorService::class.java).flattenToString()
                )
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    /**
     * Checks if the app is exempt from Battery Optimizations (Doze Mode).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Returns an Intent to request ignoring battery optimization.
     */
    @SuppressLint("BatteryLife")
    fun getIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Component Toggle Trick: Vượt qua cơ chế đóng băng kết nối ngầm của Xiaomi/OPPO/Vivo.
     * Tắt rồi bật lại Component của NotificationListenerService trong tích tắc để ép
     * NotificationManagerService của Android quét lại danh sách và tái kết nối service ngay lập tức.
     */
    fun forceRebindNotificationListener(context: Context): Boolean {
        return try {
            val component = ComponentName(context, NotificationMonitorService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(component)
            }
            Log.i(TAG, "Component Toggle: Đã kích hoạt tái kết nối NotificationListenerService!")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi chạy Component Toggle: ${e.message}")
            false
        }
    }

    /**
     * Returns user-friendly name of the current OEM device brand.
     */
    fun getDeviceBrandDisplayName(): String {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> "Xiaomi (MIUI / HyperOS)"
            manufacturer.contains("samsung") -> "Samsung (OneUI)"
            manufacturer.contains("oppo") -> "OPPO (ColorOS)"
            manufacturer.contains("realme") -> "Realme (Realme UI)"
            manufacturer.contains("oneplus") -> "OnePlus (OxygenOS)"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "Vivo (OriginOS / FuntouchOS)"
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> "Huawei / Honor (EMUI / MagicOS)"
            manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") || manufacturer.contains("itel") -> "Transsion (HiOS / XOS)"
            manufacturer.contains("google") -> "Google Pixel"
            else -> Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    /**
     * Returns Intent to open Autostart / Background management settings for OEM devices
     * (Xiaomi/HyperOS, Samsung/OneUI, Oppo/ColorOS, Vivo, Huawei).
     */
    fun getOemAutostartIntent(context: Context): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")))
                intents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")))
            }
            manufacturer.contains("samsung") -> {
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")))
                intents.add(Intent().setComponent(ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.ram.AutoRunActivity")))
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgauge.PowerUsageModelActivity")))
                intents.add(Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")))
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                intents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")))
                intents.add(Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
                intents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")))
                intents.add(Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            }
            manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") || manufacturer.contains("itel") -> {
                intents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.itel.autostart.AutoStartActivity")))
                intents.add(Intent().setComponent(ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.view.AutoStartListActivity")))
            }
        }

        // Return first resolvable intent
        for (intent in intents) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    return intent
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot resolve intent: ${e.message}")
            }
        }

        // Fallback to standard App Details Settings
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
