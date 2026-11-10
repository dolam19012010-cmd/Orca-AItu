package com.orca.orcaauto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import com.orca.orcaauto.ui.locale.AppStringsProvider
import com.orca.orcaauto.ui.locale.LocalAppStrings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.service.FloatingBubbleService
import com.orca.orcaauto.service.KeepAliveForegroundService
import com.orca.orcaauto.service.NotificationMonitorService
import com.orca.orcaauto.service.OemSurvivalHelper
import com.orca.orcaauto.ui.components.AppWallpaperBackground
import com.orca.orcaauto.ui.components.OnboardingGuideDialog
import com.orca.orcaauto.ui.navigation.Screen
import com.orca.orcaauto.ui.screens.DashboardScreen
import com.orca.orcaauto.ui.screens.HistoryScreen
import com.orca.orcaauto.ui.screens.SettingsScreen
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentRose
import com.orca.orcaauto.ui.theme.GlassBorderSubtle
import com.orca.orcaauto.ui.theme.GlassSurfaceDark
import com.orca.orcaauto.ui.theme.OrcaAutoTheme
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark

class MainActivity : ComponentActivity() {

    private val navTargetState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNavigationIntent(intent)

        try {
            setContent {
                OrcaAutoTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainAppNavHost(navigationTarget = navTargetState.value)
                    }
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Lỗi render giao diện: ${e.message}", e)
            showEmergencyErrorUi(e)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    private fun handleNavigationIntent(intent: android.content.Intent?) {
        val target = intent?.getStringExtra("navigate_to")
        if (!target.isNullOrBlank()) {
            navTargetState.value = target
        }
        val targetSessionId = intent?.getStringExtra("target_session_id")
        if (!targetSessionId.isNullOrBlank()) {
            UserPreferences.getInstance(this).setSelectedSessionId(targetSessionId)
        }
    }

    private fun showEmergencyErrorUi(throwable: Throwable) {
        val scrollView = android.widget.ScrollView(this)
        val textView = android.widget.TextView(this).apply {
            text = "⚠️ OrcaAuto Khởi Động Gặp Lỗi:\n\n${throwable.message}\n\n${throwable.stackTraceToString()}"
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
            setPadding(40, 60, 40, 60)
            textSize = 13f
        }
        scrollView.addView(textView)
        setContentView(scrollView)
    }
}

@Composable
fun MainAppNavHost(navigationTarget: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember { UserPreferences.getInstance(context) }

    // Screen.History is the default landing / home screen
    var currentScreen by remember { mutableStateOf<Screen>(Screen.History) }

    androidx.compose.runtime.LaunchedEffect(navigationTarget) {
        when (navigationTarget) {
            "advanced_sessions", "settings" -> currentScreen = Screen.Settings
            "history" -> currentScreen = Screen.History
            "dashboard" -> currentScreen = Screen.Dashboard
        }
    }

    // System Permissions missing check for the Settings badge indicator
    var isMissingPermissions by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                try {
                    // Tắt chuông báo thức nếu người dùng mở ứng dụng xem
                    com.orca.orcaauto.receiver.AlarmRingtonePlayer.stop()

                    // Tự động xóa chấm đỏ thông báo khi người dùng mở ứng dụng xem
                    preferences.setHasUnreadSummary(false)

                    // Đảm bảo dừng triệt để Foreground Service thường trực để không hiển thị thông báo dai dẳng
                    KeepAliveForegroundService.stop(context)

                    val notifGranted = OemSurvivalHelper.isNotificationAccessGranted(context)
                    if (notifGranted && !NotificationMonitorService.isConnected) {
                        OemSurvivalHelper.forceRebindNotificationListener(context)
                    }
                    val batteryIgnored = OemSurvivalHelper.isBatteryOptimizationIgnored(context)
                    val overlayGranted = FloatingBubbleService.isOverlayPermissionGranted(context)
                    isMissingPermissions = !notifGranted || !batteryIgnored || !overlayGranted
                } catch (e: Throwable) {
                    android.util.Log.w("MainActivity", "Lỗi kiểm tra quyền: ${e.message}")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Onboarding Guide Dialog on first launch
    var showFirstTimeIntro by remember { mutableStateOf(!preferences.isIntroDismissedPermanently()) }

    // Request POST_NOTIFICATIONS permission safely in Compose after first frame
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Không tự động bật KeepAliveForegroundService để giữ thanh thông báo sạch sẽ
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Dừng Foreground Service thường trực ngay khi khởi động app
        KeepAliveForegroundService.stop(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val wallpaperPath by preferences.wallpaperPathFlow.collectAsState()
    val wallpaperMode by preferences.wallpaperModeFlow.collectAsState()
    val appLanguage by preferences.appLanguageFlow.collectAsState()
    val strings = remember(appLanguage) { AppStringsProvider.get(appLanguage) }

    // Primary Tab Order: Lịch sử AI first, then Trạng thái, then Cấu hình
    val navItems = remember { listOf(Screen.History, Screen.Dashboard, Screen.Settings) }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        AppWallpaperBackground(
            wallpaperPath = wallpaperPath,
            mode = wallpaperMode
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(
                        containerColor = if (wallpaperPath != null) GlassSurfaceDark else SurfaceDark,
                        contentColor = TextPrimaryDark,
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = if (wallpaperPath != null) GlassBorderSubtle else Color.Transparent
                        )
                    ) {
                        navItems.forEach { screen ->
                            val selected = currentScreen == screen
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentScreen = screen },
                                icon = {
                                    if (screen == Screen.Settings && isMissingPermissions) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = AccentRose,
                                                    modifier = Modifier.size(7.dp)
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = screen.icon,
                                                contentDescription = screen.getTitle(strings)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.getTitle(strings)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = screen.getTitle(strings),
                                        color = if (selected) AccentCyan else TextSecondaryDark
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentCyan,
                                    unselectedIconColor = TextSecondaryDark,
                                    indicatorColor = AccentCyan.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    color = Color.Transparent
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "MainAppScreenTransition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            is Screen.Dashboard -> DashboardScreen()
                            is Screen.History -> HistoryScreen()
                            is Screen.Settings -> SettingsScreen(
                                initialExpandedCategory = if (navigationTarget == "advanced_sessions") 99 else null
                            )
                        }
                    }
                }
            }
        }

        if (showFirstTimeIntro) {
            OnboardingGuideDialog(
                initialDontShowAgain = preferences.isIntroDismissedPermanently(),
                onDismiss = { dontShowAgain ->
                    if (dontShowAgain) {
                        preferences.setIntroDismissedPermanently(true)
                    }
                    showFirstTimeIntro = false
                }
            )
        }
    }
}
