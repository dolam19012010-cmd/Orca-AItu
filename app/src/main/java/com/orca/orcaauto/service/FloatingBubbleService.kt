package com.orca.orcaauto.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.orca.orcaauto.R
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.LiveAiChatHelper
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.domain.filter.TaskItem
import com.orca.orcaauto.domain.filter.TaskStatus
import com.orca.orcaauto.ui.MainActivity
import com.orca.orcaauto.ui.wallpaper.WallpaperMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleRootView: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isCallingAi = false
    private var isExpanded = false
    private var latestSummaryText: String = ""
    private var latestModelInfo: String = ""
    private val dismissedTaskTitles = mutableSetOf<String>()
    private var alarmDbMap = mapOf<String, SmartAlarmEntity>()
    private var currentAnimator: ValueAnimator? = null
    private var cachedWallpaperBmp: Bitmap? = null
    private var lastWallpaperPath: String? = null
    private var lastWallpaperModified: Long = 0L

    // 3-State Lifecycle for Floating Bubble (Chu trình 3 trạng thái duy nhất chuẩn mực)
    enum class BubbleState {
        STATE_1_ACTIVE,  // Sáng rõ 100%, nhô ra 8dp, scale 1.0f. Tự động sang State 2 sau 1.5s
        STATE_2_DIMMED,  // Mờ sâu 20%, thụt sâu vào mép (chỉ nhô 12dp), scale 0.85f. Tự động sang State 3 sau 4.0s
        STATE_3_HIDDEN   // Ẩn hoàn toàn ngoài mép màn hình (alpha 0f, 100% màn hình thông suốt). Lắc nhẹ cổ tay (Shake to Wake) để mở lại State 2
    }

    private var currentBubbleState = BubbleState.STATE_1_ACTIVE
    private var activeBubbleView: FrameLayout? = null
    private val bubbleLifecycleHandler = Handler(Looper.getMainLooper())
    private val toState2Runnable = Runnable {
        transitionToBubbleState(BubbleState.STATE_2_DIMMED)
    }
    private val toState3Runnable = Runnable {
        transitionToBubbleState(BubbleState.STATE_3_HIDDEN)
    }

    // Messenger-Style Drag-to-Dismiss Bottom Target & Outside-Tap Collapse
    private var dismissTargetView: FrameLayout? = null
    private var dismissCircleView: FrameLayout? = null
    private var isOverDismissTarget = false
    private var isAttachedLeft = true
    private var lastCollapsedX = 0
    private var lastCollapsedY = 0
    private var isCollapsing = false

    // Shake to Wake Sensor (Chỉ bật khi ở State 3 và màn hình sáng, 100% không che màn hình)
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var isScreenOn = true
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Setup Shake to Wake Sensor (Chạy trên HandlerThread riêng để bảo vệ 120Hz Main UI)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val st = HandlerThread("OrcaShakeSensorThread").apply { start() }
        sensorThread = st
        val sh = Handler(st.looper)
        sensorHandler = sh

        shakeDetector = ShakeDetector {
            Handler(Looper.getMainLooper()).post {
                if (currentBubbleState == BubbleState.STATE_3_HIDDEN) {
                    val prefs = UserPreferences.getInstance(this@FloatingBubbleService)
                    if (prefs.isShakeToWakeEnabled()) {
                        Log.i(TAG, "⚡ Lắc tay thành công! Kích hoạt bong bóng trồi ra State 2.")
                        triggerHapticFeedback()
                        transitionToBubbleState(BubbleState.STATE_2_DIMMED, animate = true)
                    }
                }
            }
        }

        // Lắng nghe sự kiện Bật/Tắt màn hình để ngắt cảm biến triệt để (0% pin khi tắt màn hình)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        updateShakeDetectorState()
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        isScreenOn = true
                        updateShakeDetectorState()
                    }
                }
            }
        }
        screenReceiver = receiver
        try {
            registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi đăng ký screenReceiver: ${e.message}")
        }

        // 1. Reactive DB Flow: Real-time Alarms & Tasks status sync
        serviceScope.launch {
            try {
                AppDatabase.getDatabase(this@FloatingBubbleService)
                    .smartAlarmDao()
                    .getAllAlarmsFlow()
                    .collect { alarms ->
                        alarmDbMap = alarms.associateBy { it.alarmId }
                        if (isExpanded) updateBubbleContent()
                    }
            } catch (_: Exception) {}
        }

        // 2. Reactive DB Flow: Real-time latest AI summary updates (from Background Worker or App)
        serviceScope.launch {
            try {
                AppDatabase.getDatabase(this@FloatingBubbleService)
                    .summaryDao()
                    .getLatestSummaryFlow()
                    .collect { latest ->
                        if (latest != null && latest.summaryContent.isNotBlank()) {
                            latestSummaryText = latest.summaryContent
                            if (latest.modelInfo.isNotBlank()) {
                                latestModelInfo = latest.modelInfo
                            }
                            if (isExpanded) updateBubbleContent()
                        }
                    }
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val summary = intent?.getStringExtra(EXTRA_SUMMARY_CONTENT).orEmpty()
        if (summary.isNotBlank()) {
            latestSummaryText = summary
        }
        val model = intent?.getStringExtra(EXTRA_MODEL_INFO).orEmpty()
        if (model.isNotBlank()) {
            latestModelInfo = model
        }

        // Always ensure we have latest summary if memory is blank
        if (latestSummaryText.isBlank()) {
            serviceScope.launch {
                val latest = AppDatabase.getDatabase(this@FloatingBubbleService).summaryDao().getLatestSummary()
                if (latest != null && latest.summaryContent.isNotBlank()) {
                    latestSummaryText = latest.summaryContent
                    if (latest.modelInfo.isNotBlank()) latestModelInfo = latest.modelInfo
                    if (isExpanded) updateBubbleContent()
                }
            }
        }

        if (Settings.canDrawOverlays(this)) {
            if (bubbleRootView == null) {
                createFloatingBubble()
            } else {
                updateBubbleContent()
            }
        } else {
            Log.w(TAG, "Chưa cấp quyền SYSTEM_ALERT_WINDOW!")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        val bubbleSize = dpToPx(44)
        isAttachedLeft = true
        lastCollapsedX = dpToPx(8)
        lastCollapsedY = dpToPx(180)

        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = lastCollapsedX
            y = lastCollapsedY
        }
        windowParams = params

        val rootLayout = FrameLayout(this).apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    windowParams?.let { p ->
                        try {
                            windowManager.updateViewLayout(v, p)
                        } catch (_: Exception) {}
                    }
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }
        bubbleRootView = rootLayout

        createDismissTargetView()
        renderCollapsedBubble(rootLayout)

        try {
            windowManager.addView(rootLayout, params)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi thêm bubbleRootView: ${e.message}")
        }
        transitionToBubbleState(BubbleState.STATE_1_ACTIVE, animate = false)
    }

    private fun updateShakeDetectorState() {
        val prefs = UserPreferences.getInstance(this)
        val shouldListen = currentBubbleState == BubbleState.STATE_3_HIDDEN &&
                isScreenOn &&
                prefs.isShakeToWakeEnabled()

        val sm = sensorManager ?: return
        val detector = shakeDetector ?: return
        val sh = sensorHandler ?: return

        if (shouldListen) {
            detector.start(sm, sh)
        } else {
            detector.stop(sm)
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(45L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(45L)
                }
            }
        } catch (_: Throwable) {}
    }

    private fun createDismissTargetView() {
        if (dismissTargetView != null) return
        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val targetSize = dpToPx(84)
        val circleSize = dpToPx(56)
        val params = WindowManager.LayoutParams(
            targetSize,
            targetSize,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(56) // Đẩy nút X tròn lên cao hơn tránh che thanh điều hướng
        }

        val container = FrameLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }

        val circle = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(circleSize, circleSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#B30F172A"))
                setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
            }
            elevation = dpToPx(8).toFloat()
        }

        val xIcon = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        circle.addView(xIcon)
        container.addView(circle)

        dismissTargetView = container
        dismissCircleView = circle

        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tạo dismissTargetView: ${e.message}")
        }
    }

    private fun showDismissTarget() {
        val target = dismissTargetView ?: return
        target.animate().cancel()
        dismissCircleView?.animate()?.cancel()
        target.visibility = View.VISIBLE
        target.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(160)
            .start()
        updateDismissTargetHover(false)
    }

    private fun hideDismissTarget(immediate: Boolean = false) {
        val target = dismissTargetView ?: return
        target.animate().cancel()
        dismissCircleView?.animate()?.cancel()
        isOverDismissTarget = false

        (dismissCircleView?.background as? GradientDrawable)?.apply {
            setColor(Color.parseColor("#B30F172A"))
            setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
        }
        dismissCircleView?.scaleX = 1.0f
        dismissCircleView?.scaleY = 1.0f

        if (immediate) {
            target.alpha = 0f
            target.scaleX = 0.8f
            target.scaleY = 0.8f
            target.visibility = View.GONE
            return
        }

        target.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(140)
            .withEndAction {
                target.visibility = View.GONE
                target.alpha = 0f
            }
            .start()
    }

    private fun updateDismissTargetHover(isHovered: Boolean) {
        if (isOverDismissTarget == isHovered) return
        isOverDismissTarget = isHovered
        val circle = dismissCircleView ?: return
        circle.animate().cancel()
        if (isHovered) {
            circle.animate().scaleX(1.24f).scaleY(1.24f).setDuration(120).start()
            (circle.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#EF4444"))
                setStroke(dpToPx(2), Color.WHITE)
            }
        } else {
            circle.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            (circle.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#B30F172A"))
                setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderCollapsedBubble(root: FrameLayout) {
        root.removeAllViews()
        root.setBackgroundColor(Color.TRANSPARENT)
        root.setOnClickListener(null)
        root.isClickable = true
        root.isFocusable = false
        isExpanded = false
        root.alpha = 1.0f

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bubbleSize = dpToPx(44)
        val minY = dpToPx(28) // Giảm bớt giới hạn trên, cho phép kéo lên gần sát thanh trạng thái
        val maxY = screenHeight - bubbleSize - dpToPx(32) // Giảm bớt giới hạn dưới, cho phép kéo xuống thấp hơn

        val isLeft = isAttachedLeft
        val currentX = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else screenWidth - bubbleSize - dpToPx(8)
            BubbleState.STATE_2_DIMMED -> if (isLeft) -dpToPx(32) else screenWidth - bubbleSize + dpToPx(32)
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -dpToPx(44) else screenWidth
        }
        val currentAlpha = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.20f
            BubbleState.STATE_3_HIDDEN -> 0.0f
        }
        val currentScale = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.85f
            BubbleState.STATE_3_HIDDEN -> 0.85f
        }

        val pColl = windowParams
        if (pColl != null) {
            lastCollapsedY = if (lastCollapsedY <= 0) dpToPx(180) else lastCollapsedY.coerceIn(minY, maxY)
            lastCollapsedX = currentX

            pColl.gravity = Gravity.TOP or Gravity.START
            pColl.width = bubbleSize
            pColl.height = bubbleSize
            pColl.x = currentX
            pColl.y = lastCollapsedY
            var flags = (pColl.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            flags = flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN.inv()
            if (currentBubbleState == BubbleState.STATE_3_HIDDEN) {
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            pColl.flags = flags
            try {
                if (bubbleRootView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(bubbleRootView, pColl)
                }
            } catch (e: Exception) {}
        }

        val parsed = SummaryParser.parse(latestSummaryText)
        val urgentCount = parsed.tasksToDo.size

        // Bubble Container: 44dp compact circle with sleek indigo-purple gradient
        val bubble = FrameLayout(this).apply {
            alpha = currentAlpha
            scaleX = currentScale
            scaleY = currentScale
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(Color.parseColor("#27272A"), Color.parseColor("#09090B"))
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = bubbleSize.toFloat()
                setStroke(dpToPx(1), Color.parseColor("#FFFFFF"))
            }
            elevation = dpToPx(6).toFloat()
        }
        activeBubbleView = bubble

        // Inner Icon (28dp) - Sleek Vector Orca Logo
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_orca_logo)
            val iconSize = dpToPx(28)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
        }
        bubble.addView(icon)

        // Badge (Shows number of urgent tasks if > 0)
        if (urgentCount > 0) {
            val badgeSize = dpToPx(16)
            val badge = TextView(this).apply {
                text = if (urgentCount > 9) "9+" else "$urgentCount"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF4444"))
                    setStroke(dpToPx(1), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                    gravity = Gravity.TOP or Gravity.END
                }
            }
            bubble.addView(badge)
        }

        root.addView(bubble)

        // Touch & Drag Handling with 3-State Lifecycle, Long-Press & Edge-Wakeup
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false
        var downBubbleState = currentBubbleState
        var isDragging = false
        var isLongPressTriggered = false
        val bubbleLongPressTimeout = 550L
        val bubbleLongPressRunnable = Runnable {
            if (!isDragging && isClick) {
                isLongPressTriggered = true
                // Nhấn giữ 550ms trên quả bóng chat -> Mở thẻ AI mở rộng ngay lập tức!
                expandFloatingCard(root)
            }
        }

        root.setOnTouchListener { _, event ->
            val p = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
                    bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
                    bubbleLifecycleHandler.removeCallbacks(bubbleLongPressRunnable)
                    currentAnimator?.cancel()
                    downBubbleState = currentBubbleState
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    isDragging = false
                    isLongPressTriggered = false

                    // Lên lịch kiểm tra Nhấn Giữ 550ms (không rung, thoải mái theo lệnh user)
                    bubbleLifecycleHandler.postDelayed(bubbleLongPressRunnable, bubbleLongPressTimeout)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        bubbleLifecycleHandler.removeCallbacks(bubbleLongPressRunnable)
                        if (isClick) {
                            isClick = false
                            isDragging = true
                            if (currentBubbleState != BubbleState.STATE_1_ACTIVE) {
                                currentBubbleState = BubbleState.STATE_1_ACTIVE
                                activeBubbleView?.alpha = 1.0f
                                activeBubbleView?.scaleX = 1.0f
                                activeBubbleView?.scaleY = 1.0f
                            }
                            showDismissTarget()
                        }
                    }

                    if (isDragging) {
                        p.x = initialX + dx
                        p.y = (initialY + dy).coerceIn(minY, maxY)
                        try {
                            if (bubbleRootView?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(bubbleRootView, p)
                            }
                        } catch (_: Exception) {}

                        // Kiểm tra khoảng cách tới nút hủy ở đáy màn hình
                        val bubbleCenterX = p.x + bubbleSize / 2f
                        val bubbleCenterY = p.y + bubbleSize / 2f
                        val targetCenterX = dm.widthPixels / 2f
                        val targetCenterY = dm.heightPixels - dpToPx(56 + 42).toFloat()
                        val dist = Math.hypot((bubbleCenterX - targetCenterX).toDouble(), (bubbleCenterY - targetCenterY).toDouble()).toFloat()
                        val hoverThreshold = dpToPx(76).toFloat()
                        updateDismissTargetHover(dist < hoverThreshold)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    bubbleLifecycleHandler.removeCallbacks(bubbleLongPressRunnable)
                    if (isLongPressTriggered) {
                        return@setOnTouchListener true
                    }

                    if (isDragging && isOverDismissTarget) {
                        hideDismissTarget(immediate = true)
                        Toast.makeText(this@FloatingBubbleService, "Đã đóng bong bóng chat", Toast.LENGTH_SHORT).show()
                        stopSelf()
                        return@setOnTouchListener true
                    }

                    hideDismissTarget(immediate = false)

                    if (isClick) {
                        when (downBubbleState) {
                            BubbleState.STATE_2_DIMMED -> {
                                // Đang ở State 2 (mờ nép viền) -> Chạm vào để bung sang State 1 (nhô 8dp, sáng rõ 100%)
                                transitionToBubbleState(BubbleState.STATE_1_ACTIVE)
                            }
                            BubbleState.STATE_1_ACTIVE -> {
                                // Đang ở State 1 sáng rõ và click -> Mở thẻ AI mở rộng!
                                expandFloatingCard(root)
                            }
                            BubbleState.STATE_3_HIDDEN -> {}
                        }
                    } else if (isDragging) {
                        // Thả tay sau khi kéo -> Snap mượt mà về mép an toàn (State 1 nhô ra 8dp)
                        val isLeft = (p.x + bubbleSize / 2) < (screenWidth / 2)
                        isAttachedLeft = isLeft
                        val targetX = if (isLeft) dpToPx(8) else screenWidth - bubbleSize - dpToPx(8)

                        val startX = p.x
                        currentAnimator?.cancel()
                        var isCanceled = false
                        val animator = ValueAnimator.ofInt(startX, targetX).apply {
                            duration = 180
                            interpolator = DecelerateInterpolator()
                            addUpdateListener { va ->
                                p.x = va.animatedValue as Int
                                p.y = p.y.coerceIn(minY, maxY)
                                lastCollapsedX = p.x
                                lastCollapsedY = p.y
                                try {
                                    if (bubbleRootView?.isAttachedToWindow == true) {
                                        windowManager.updateViewLayout(bubbleRootView, p)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        animator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationCancel(animation: Animator) {
                                isCanceled = true
                            }
                            override fun onAnimationEnd(animation: Animator) {
                                if (isCanceled) return
                                lastCollapsedX = targetX
                                lastCollapsedY = p.y.coerceIn(minY, maxY)
                                transitionToBubbleState(BubbleState.STATE_1_ACTIVE, animate = false)
                            }
                        })
                        currentAnimator = animator
                        animator.start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    bubbleLifecycleHandler.removeCallbacks(bubbleLongPressRunnable)
                    hideDismissTarget(immediate = true)
                    isDragging = false
                    isClick = false

                    val isLeft = (p.x + bubbleSize / 2) < (screenWidth / 2)
                    isAttachedLeft = isLeft
                    val targetX = if (isLeft) dpToPx(8) else screenWidth - bubbleSize - dpToPx(8)
                    p.x = targetX
                    p.y = p.y.coerceIn(minY, maxY)
                    lastCollapsedX = p.x
                    lastCollapsedY = p.y
                    try {
                        if (bubbleRootView?.isAttachedToWindow == true) {
                            windowManager.updateViewLayout(bubbleRootView, p)
                        }
                    } catch (_: Exception) {}

                    transitionToBubbleState(BubbleState.STATE_1_ACTIVE, animate = false)
                    true
                }
                else -> false
            }
        }
    }

    private fun transitionToBubbleState(newState: BubbleState, animate: Boolean = true) {
        if (isExpanded || bubbleRootView == null || windowParams == null) return
        val p = windowParams ?: return
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val bubbleSize = dpToPx(44)
        val isLeft = isAttachedLeft

        bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
        bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
        currentAnimator?.cancel()

        val oldState = currentBubbleState
        currentBubbleState = newState

        // Khắc phục lỗi WindowManager: Luôn giữ bubbleRootView là View.VISIBLE để tránh chết Surface / drop layout pass
        bubbleRootView?.visibility = View.VISIBLE

        val bubbleView = activeBubbleView ?: bubbleRootView

        // State 1: Nhô hẳn ra ngoài mép 8dp, sáng rõ 100%, scale 1.0f
        // State 2: Thụt sâu vào mép (chỉ nhô ~12dp), mờ sâu 20% (alpha 0.20f), scale 0.85f
        // State 3: Giấu bóng ngoài mép màn hình (startX = -44dp hoặc screenWidth), alpha 0.0f, scale 0.85f
        val targetX = when (newState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else (screenWidth - bubbleSize - dpToPx(8))
            BubbleState.STATE_2_DIMMED -> if (isLeft) -dpToPx(32) else (screenWidth - bubbleSize + dpToPx(32))
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -dpToPx(44) else screenWidth
        }
        val targetAlpha = when (newState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.20f
            BubbleState.STATE_3_HIDDEN -> 0.0f
        }
        val targetScale = when (newState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.85f
            BubbleState.STATE_3_HIDDEN -> 0.85f
        }

        p.y = lastCollapsedY

        if (!animate) {
            p.x = targetX
            lastCollapsedX = targetX
            bubbleView?.alpha = targetAlpha
            bubbleView?.scaleX = targetScale
            bubbleView?.scaleY = targetScale
            if (newState == BubbleState.STATE_3_HIDDEN) {
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                if (bubbleRootView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(bubbleRootView, p)
                }
            } catch (_: Exception) {}

            when (newState) {
                BubbleState.STATE_1_ACTIVE -> {
                    updateShakeDetectorState()
                    bubbleLifecycleHandler.postDelayed(toState2Runnable, 1500L)
                }
                BubbleState.STATE_2_DIMMED -> {
                    updateShakeDetectorState()
                    bubbleLifecycleHandler.postDelayed(toState3Runnable, 4000L)
                }
                BubbleState.STATE_3_HIDDEN -> {
                    updateShakeDetectorState()
                }
            }
            return
        }

        if (oldState == BubbleState.STATE_3_HIDDEN && newState == BubbleState.STATE_2_DIMMED) {
            // Đánh thức từ State 3 (Ẩn) sang State 2: set alpha thẳng 20% (0.20f, scale = 0.85f),
            // trượt nhẹ nhàng từ ngoài mép kính (startX = if (isLeft) -dpToPx(44) else screenWidth)
            // trồi ra đúng vị trí nép của Trạng thái 2 (targetX = if (isLeft) -dpToPx(32) else screenWidth - bubbleSize + dpToPx(32)).
            // Thời gian animation ~280ms với DecelerateInterpolator.
            updateShakeDetectorState()
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            bubbleView?.alpha = 0.20f
            bubbleView?.scaleX = 0.85f
            bubbleView?.scaleY = 0.85f

            val startX = if (isLeft) -dpToPx(44) else screenWidth
            p.x = startX
            p.y = lastCollapsedY
            try {
                if (bubbleRootView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(bubbleRootView, p)
                }
            } catch (_: Exception) {}

            var isCanceled = false
            val animator = ValueAnimator.ofInt(startX, targetX).apply {
                duration = 280L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    p.x = va.animatedValue as Int
                    p.y = lastCollapsedY
                    lastCollapsedX = p.x
                    try {
                        if (bubbleRootView?.isAttachedToWindow == true) {
                            windowManager.updateViewLayout(bubbleRootView, p)
                        }
                    } catch (_: Exception) {}
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        isCanceled = true
                    }
                    override fun onAnimationEnd(animation: Animator) {
                        if (isCanceled) return
                        p.x = targetX
                        lastCollapsedX = targetX
                        // Sau khi trồi ra State 2, bắt đầu đếm 4.0s để tự động lặn về State 3 nếu không thao tác
                        bubbleLifecycleHandler.postDelayed(toState3Runnable, 4000L)
                    }
                })
            }
            currentAnimator = animator
            animator.start()
        } else {
            val startX = p.x
            val startAlpha = bubbleView?.alpha ?: 1.0f
            val startScale = bubbleView?.scaleX ?: 1.0f
            val animDuration = when (newState) {
                BubbleState.STATE_1_ACTIVE -> 180L
                BubbleState.STATE_2_DIMMED -> 260L
                BubbleState.STATE_3_HIDDEN -> 280L
            }

            if (newState != BubbleState.STATE_3_HIDDEN) {
                p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }

            var isCanceled = false
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    p.x = (startX + (targetX - startX) * f).toInt()
                    p.y = lastCollapsedY
                    val currentA = startAlpha + (targetAlpha - startAlpha) * f
                    val currentS = startScale + (targetScale - startScale) * f
                    bubbleView?.alpha = currentA
                    bubbleView?.scaleX = currentS
                    bubbleView?.scaleY = currentS
                    lastCollapsedX = p.x
                    try {
                        if (bubbleRootView?.isAttachedToWindow == true) {
                            windowManager.updateViewLayout(bubbleRootView, p)
                        }
                    } catch (_: Exception) {}
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationCancel(animation: Animator) {
                        isCanceled = true
                    }
                    override fun onAnimationEnd(animation: Animator) {
                        if (isCanceled) return
                        p.x = targetX
                        lastCollapsedX = targetX
                        bubbleView?.alpha = targetAlpha
                        bubbleView?.scaleX = targetScale
                        bubbleView?.scaleY = targetScale
                        if (newState == BubbleState.STATE_3_HIDDEN) {
                            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        }
                        try {
                            if (bubbleRootView?.isAttachedToWindow == true) {
                                windowManager.updateViewLayout(bubbleRootView, p)
                            }
                        } catch (_: Exception) {}

                        when (newState) {
                            BubbleState.STATE_1_ACTIVE -> {
                                updateShakeDetectorState()
                                bubbleLifecycleHandler.postDelayed(toState2Runnable, 1500L)
                            }
                            BubbleState.STATE_2_DIMMED -> {
                                updateShakeDetectorState()
                                bubbleLifecycleHandler.postDelayed(toState3Runnable, 4000L)
                            }
                            BubbleState.STATE_3_HIDDEN -> {
                                updateShakeDetectorState()
                            }
                        }
                    }
                })
            }
            currentAnimator = animator
            animator.start()
        }
    }

    private fun loadWallpaperBitmapOptimized(maxWidth: Int, maxHeight: Int): Bitmap? {
        val prefs = UserPreferences.getInstance(this)
        val path = prefs.getWallpaperPath()
        if (path.isNullOrBlank()) {
            cachedWallpaperBmp?.recycle()
            cachedWallpaperBmp = null
            lastWallpaperPath = null
            return null
        }

        val file = File(path)
        if (!file.exists()) {
            cachedWallpaperBmp?.recycle()
            cachedWallpaperBmp = null
            lastWallpaperPath = null
            return null
        }

        val modTime = file.lastModified()
        if (cachedWallpaperBmp != null && path == lastWallpaperPath && modTime == lastWallpaperModified) {
            return cachedWallpaperBmp
        }

        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            var sample = 1
            while (boundsOpts.outWidth / sample > maxWidth * 2 || boundsOpts.outHeight / sample > maxHeight * 2) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            cachedWallpaperBmp?.recycle()
            cachedWallpaperBmp = bmp
            lastWallpaperPath = path
            lastWallpaperModified = modTime
            bmp
        } catch (e: Throwable) {
            Log.e(TAG, "Lỗi tải ảnh nền: ${e.message}")
            null
        }
    }

    private class FrostedWallpaperDrawable(
        private val bitmap: Bitmap,
        private val cornerRadius: Float,
        private val scrimColor: Int,
        private val strokeColor: Int,
        private val strokeWidth: Float
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scrimColor }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        private val matrix = Matrix()
        private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val vw = bounds.width().toFloat()
            val vh = bounds.height().toFloat()
            val scale: Float
            var dx = 0f
            var dy = 0f
            if (bw * vh > vw * bh) {
                scale = vh / bh
                dx = (vw - bw * scale) * 0.5f
            } else {
                scale = vw / bw
                dy = (vh - bh * scale) * 0.5f
            }
            matrix.setScale(scale, scale)
            matrix.postTranslate(dx + bounds.left, dy + bounds.top)
            shader.setLocalMatrix(matrix)
            paint.shader = shader
        }

        override fun draw(canvas: Canvas) {
            val rectF = RectF(bounds)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, scrimPaint)
            if (strokeWidth > 0f) {
                val halfStroke = strokeWidth / 2f
                rectF.inset(halfStroke, halfStroke)
                canvas.drawRoundRect(rectF, cornerRadius - halfStroke, cornerRadius - halfStroke, strokePaint)
            }
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            scrimPaint.alpha = (Color.alpha(scrimColor) * (alpha / 255f)).toInt()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun collapseExpandedCard(root: FrameLayout) {
        if (isCollapsing || !isExpanded) return
        isCollapsing = true
        hideDismissTarget(immediate = true)
        val card = root.getChildAt(0)
        if (card != null) {
            card.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .alpha(0f)
                .translationY(dpToPx(24).toFloat())
                .setDuration(160)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        root.animate()
            .alpha(0f)
            .setDuration(160)
            .withEndAction {
                isCollapsing = false
                currentBubbleState = BubbleState.STATE_1_ACTIVE
                renderCollapsedBubble(root)
                transitionToBubbleState(BubbleState.STATE_1_ACTIVE)
            }
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun expandFloatingCard(root: FrameLayout, animate: Boolean = true) {
        bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
        bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
        hideDismissTarget(immediate = true)
        currentAnimator?.cancel()
        isCollapsing = false
        updateShakeDetectorState()
        root.removeAllViews()
        root.alpha = 1.0f

        val parsed = SummaryParser.parse(latestSummaryText)

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels

        // Enlarge expanded card: covers comfortable viewing area (~94% width, ~74% height for keyboard clearance)
        val cardWidth = (screenWidth * 0.94f).toInt().coerceAtMost(dpToPx(500))
        val cardHeight = (screenHeight * 0.74f).toInt().coerceAtMost(dpToPx(620))

        // Make window focusable so soft keyboard opens for query input, expand root to MATCH_PARENT
        val p = windowParams
        if (p != null) {
            if (!isExpanded) {
                lastCollapsedX = p.x
                lastCollapsedY = p.y
            }
            p.gravity = Gravity.CENTER
            p.width = WindowManager.LayoutParams.MATCH_PARENT
            p.height = WindowManager.LayoutParams.MATCH_PARENT
            p.x = 0
            p.y = 0
            p.flags = (p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) and
                      WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv() and
                      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() or
                      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            @Suppress("DEPRECATION")
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            try {
                if (bubbleRootView?.isAttachedToWindow == true) {
                    windowManager.updateViewLayout(bubbleRootView, p)
                }
            } catch (e: Exception) {}
        }
        isExpanded = true

        // Clean transparent backdrop without black wash spilling outside
        root.setBackgroundColor(Color.TRANSPARENT)
        root.isClickable = false
        root.isFocusable = false

        val wallpaperBmp = loadWallpaperBitmapOptimized(cardWidth, cardHeight)
        val prefs = UserPreferences.getInstance(this)
        val wpMode = prefs.getWallpaperMode()

        // Card Container (Translucent Liquid Glass Modal)
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight).apply {
                gravity = Gravity.CENTER
            }
            elevation = dpToPx(24).toFloat()
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            isClickable = true
            isFocusable = true

            if (wallpaperBmp != null) {
                val scrimColor = when (wpMode) {
                    WallpaperMode.CLEAR -> Color.parseColor("#B309090B")
                    WallpaperMode.BLURRED -> Color.parseColor("#CC09090B")
                    WallpaperMode.DYNAMIC -> Color.parseColor("#BF09090B")
                }
                background = FrostedWallpaperDrawable(
                    bitmap = wallpaperBmp,
                    cornerRadius = dpToPx(24).toFloat(),
                    scrimColor = scrimColor,
                    strokeColor = Color.parseColor("#4DFFFFFF"),
                    strokeWidth = dpToPx(1).toFloat()
                )
            } else {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F509090B"))
                    cornerRadius = dpToPx(24).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
                }
            }
        }

        // Outside-tap detector: tapping empty space above/below collapses back to floating bubble
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val cardRect = Rect()
                cardLayout.getGlobalVisibleRect(cardRect)
                if (!cardRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    collapseExpandedCard(root)
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Header: Sparkle + Title + Model Info + Translucent "Mở app ↗" Button (Zero close X button!)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, dpToPx(8))
        }

        val iconHeader = ImageView(this).apply {
            setImageResource(R.drawable.ic_orca_logo)
            val iconSize = dpToPx(20)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = dpToPx(8)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        headerRow.addView(iconHeader)

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = "AItu"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        titleContainer.addView(titleTv)

        val modelTv = TextView(this).apply {
            text = if (latestModelInfo.isNotBlank()) latestModelInfo else "Trợ lý AI điều phối thông minh"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10.5f
        }
        titleContainer.addView(modelTv)
        headerRow.addView(titleContainer)

        val openAppBtn = TextView(this).apply {
            text = "Mở app ↗"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#66FFFFFF"))
            }
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
            setOnClickListener {
                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "history")
                }
                startActivity(intent)
                collapseExpandedCard(root)
            }
        }
        headerRow.addView(openAppBtn)

        cardLayout.addView(headerRow)

        // Flexible Center ScrollView: expands to take all available height
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dpToPx(4)
                bottomMargin = dpToPx(6)
            }
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(2), 0, dpToPx(4))
        }

        // Section 1: Clean Advice Box
        val cleanAdvice = SummaryParser.cleanAdviceText(parsed.conciseAdvice)
        if (cleanAdvice.isNotBlank()) {
            val sec1Box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1F18181B"))
                    cornerRadius = dpToPx(12).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
                }
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
            }

            val adviceTv = TextView(this).apply {
                text = cleanAdvice
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setLineSpacing(dpToPx(2).toFloat(), 1.15f)
            }
            sec1Box.addView(adviceTv)
            contentLayout.addView(sec1Box)
        }

        // Render all tasks directly with state-driven styling (Active / Done / Cancelled)
        val allTasks = parsed.tasksToDo + parsed.tasksPlanned + parsed.tasksDone
        if (allTasks.isEmpty()) {
            val emptyBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(36), 0, dpToPx(36))
            }
            val emptyIcon = TextView(this).apply {
                text = "✨"
                textSize = 28f
                gravity = Gravity.CENTER
            }
            emptyBox.addView(emptyIcon)
            val emptyTv = TextView(this).apply {
                text = "Không có nhiệm vụ tồn đọng. Mọi thứ đã hoàn tất!"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(6), 0, 0)
            }
            emptyBox.addView(emptyTv)
            contentLayout.addView(emptyBox)
        } else {
            parsed.tasksToDo.forEach { task ->
                contentLayout.addView(renderTaskRow(task, isPlanned = false))
            }
            parsed.tasksPlanned.forEach { task ->
                contentLayout.addView(renderTaskRow(task, isPlanned = true))
            }
            parsed.tasksDone.forEach { task ->
                contentLayout.addView(renderTaskRow(task, isPlanned = false))
            }
        }

        scrollView.addView(contentLayout)
        cardLayout.addView(scrollView)

        // Interactive Live AI Input Bar at the BOTTOM edge (ergonomic thumb reach)
        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#331E293B"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4)
            }
        }

        val queryEt = EditText(this).apply {
            hint = if (isCallingAi) "AI đang xử lý..." else "Nhập lệnh hoặc câu hỏi..."
            setHintTextColor(Color.parseColor("#71717A"))
            setTextColor(Color.WHITE)
            textSize = 12.5f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18181B"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(10), dpToPx(9), dpToPx(10), dpToPx(9))
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isEnabled = !isCallingAi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isForceDarkAllowed = false
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    scrollView.postDelayed({
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }, 200L)
                }
            }
        }

        val executeAiCall: (String) -> Unit = { queryText ->
            if (queryText.isNotBlank() && !isCallingAi) {
                isCallingAi = true
                updateBubbleContent()
                serviceScope.launch {
                    val result = LiveAiChatHelper.sendUserPrompt(this@FloatingBubbleService, queryText)
                    isCallingAi = false
                    if (result.isSuccess) {
                        val (newSummary, key) = result.getOrThrow()
                        latestSummaryText = newSummary
                        latestModelInfo = "${key.provider.displayName} • ${key.modelName}"
                        Toast.makeText(this@FloatingBubbleService, "⚡ Đã cập nhật từ $latestModelInfo!", Toast.LENGTH_SHORT).show()
                    } else {
                        val err = result.exceptionOrNull()?.message ?: "Lỗi gọi AI"
                        Toast.makeText(this@FloatingBubbleService, "Thất bại: $err", Toast.LENGTH_LONG).show()
                    }
                    updateBubbleContent()
                }
            }
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        inputRow.addView(queryEt)

        val sendBtn = TextView(this).apply {
            text = if (isCallingAi) "..." else "Gửi"
            setTextColor(if (isCallingAi) Color.parseColor("#94A3B8") else Color.BLACK)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(if (isCallingAi) Color.parseColor("#3327272A") else Color.WHITE)
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), if (isCallingAi) Color.parseColor("#4DFFFFFF") else Color.WHITE)
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(60), dpToPx(38)).apply {
                leftMargin = dpToPx(8)
            }
            isClickable = !isCallingAi
            setOnClickListener {
                if (!isCallingAi) {
                    val q = queryEt.text.toString().trim()
                    if (q.isNotBlank()) {
                        executeAiCall(q)
                    }
                }
            }
        }
        inputRow.addView(sendBtn)
        inputCard.addView(inputRow)
        cardLayout.addView(inputCard)

        root.addView(cardLayout)

        // Smooth Entrance Animation
        if (animate) {
            root.alpha = 0f
            root.animate().alpha(1f).setDuration(220).start()

            cardLayout.alpha = 0f
            cardLayout.scaleX = 0.85f
            cardLayout.scaleY = 0.85f
            cardLayout.translationY = dpToPx(24).toFloat()
            cardLayout.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(1.04f))
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderTaskRow(task: TaskItem, isPlanned: Boolean): View {
        val cleanTitle = task.title.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        val parsedDt = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
        val targetCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
        val existingAlarm = alarmDbMap[generatedAlarmId] ?: SystemAlarmHelper.findMatchingAlarm(cleanTitle, alarmDbMap)
        val alarmId = existingAlarm?.alarmId ?: generatedAlarmId

        val isCancelled = existingAlarm?.status == SmartAlarmStatus.CANCELLED
        val isCompleted = existingAlarm?.status == SmartAlarmStatus.COMPLETED || task.status == TaskStatus.DONE
        val isAlarmActive = existingAlarm?.status == SmartAlarmStatus.ACTIVE
        val proposedTime = parsedDt?.timeLabel ?: "Hẹn giờ"

        // Root container for gesture swiping
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }

        // Swipe Background Action View
        val bgSwipeLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val leftSwipeAction = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = dpToPx(10).toFloat()
            }
            setPadding(dpToPx(14), 0, dpToPx(14), 0)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE

            val tv = TextView(context).apply {
                text = "✓ Hoàn thành"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(tv)
        }
        bgSwipeLayout.addView(leftSwipeAction)

        val rightSwipeAction = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = dpToPx(10).toFloat()
            }
            setPadding(dpToPx(14), 0, dpToPx(14), 0)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE

            val tv = TextView(context).apply {
                text = "Hủy việc ✕"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(tv)
        }
        bgSwipeLayout.addView(rightSwipeAction)
        container.addView(bgSwipeLayout)

        // Foreground Card
        val taskRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(if (isCancelled) Color.parseColor("#1A1E293B") else Color.parseColor("#261E293B"))
                cornerRadius = dpToPx(10).toFloat()
                if (isCancelled) {
                    setStroke(dpToPx(1), Color.parseColor("#59EF4444"))
                } else if (isCompleted) {
                    setStroke(dpToPx(1), Color.parseColor("#10B981"))
                } else if (isAlarmActive) {
                    setStroke(dpToPx(1), Color.parseColor("#0284C7"))
                } else if (isPlanned) {
                    setStroke(dpToPx(1), Color.parseColor("#59F59E0B"))
                } else {
                    setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
                }
            }
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
        }

        // Title row: Checkbox + Title + Action
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val checkCircle = TextView(this).apply {
            text = when {
                isCompleted -> "✓"
                isCancelled -> "✕"
                else -> "○"
            }
            setTextColor(when {
                isCompleted -> Color.parseColor("#10B981")
                isCancelled -> Color.parseColor("#EF4444")
                else -> Color.parseColor("#E4E4E7")
            })
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dpToPx(8), 0)
            isClickable = true
            setOnClickListener {
                if (isCancelled) {
                    SystemAlarmHelper.restoreCancelledTask(this@FloatingBubbleService, cleanTitle, alarmId)
                    val restoredAlarm = (existingAlarm ?: SmartAlarmEntity(
                        alarmId = alarmId,
                        taskTitle = cleanTitle,
                        targetTimeMillis = System.currentTimeMillis(),
                        targetDateLabel = proposedTime,
                        status = SmartAlarmStatus.PROPOSED
                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                    alarmDbMap = alarmDbMap + (alarmId to restoredAlarm) + (generatedAlarmId to restoredAlarm)
                    Toast.makeText(this@FloatingBubbleService, "✓ Đã khôi phục: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                    updateBubbleContent()
                } else if (isCompleted) {
                    showFloatingConfirmationDialog(
                        title = "Đưa về chưa hoàn thành?",
                        message = "Bạn có muốn đưa nhiệm vụ \"$cleanTitle\" về lại danh sách việc cần làm (chưa hoàn thành) để tiếp tục theo dõi không?",
                        confirmText = "Xác nhận",
                        confirmColor = Color.WHITE
                    ) {
                        SystemAlarmHelper.uncompleteTask(this@FloatingBubbleService, cleanTitle, alarmId)
                        val uncompletedAlarm = (existingAlarm ?: SmartAlarmEntity(
                            alarmId = alarmId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = System.currentTimeMillis(),
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.PROPOSED
                        )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                        alarmDbMap = alarmDbMap + (alarmId to uncompletedAlarm) + (generatedAlarmId to uncompletedAlarm)
                        Toast.makeText(this@FloatingBubbleService, "Đã đưa về trạng thái chưa hoàn thành!", Toast.LENGTH_SHORT).show()
                        updateBubbleContent()
                    }
                } else {
                    SystemAlarmHelper.markTaskCompleted(this@FloatingBubbleService, cleanTitle, alarmId)
                    val completedAlarm = (existingAlarm ?: SmartAlarmEntity(
                        alarmId = alarmId,
                        taskTitle = cleanTitle,
                        targetTimeMillis = System.currentTimeMillis(),
                        targetDateLabel = "Đã xong",
                        status = SmartAlarmStatus.COMPLETED
                    )).copy(status = SmartAlarmStatus.COMPLETED, updatedAt = System.currentTimeMillis())
                    alarmDbMap = alarmDbMap + (alarmId to completedAlarm) + (generatedAlarmId to completedAlarm)
                    Toast.makeText(this@FloatingBubbleService, "✓ Đã hoàn thành: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                    updateBubbleContent()
                }
            }
        }
        titleRow.addView(checkCircle)

        val title = TextView(this).apply {
            text = task.title
            setTextColor(if (isCompleted || isCancelled) Color.parseColor("#94A3B8") else Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            if (isCompleted || isCancelled) {
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)

        taskRow.addView(titleRow)

        // Badge row
        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(6)
                leftMargin = dpToPx(20)
            }
        }

        if (task.deadline.isNotBlank()) {
            val deadlinePill = TextView(this).apply {
                text = "⏰ ${task.deadline}"
                setTextColor(if (isPlanned) Color.parseColor("#FDE68A") else Color.parseColor("#FCA5A5"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(if (isPlanned) Color.parseColor("#2E2005") else Color.parseColor("#2D1519"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), if (isPlanned) Color.parseColor("#78350F") else Color.parseColor("#7F1D1D"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(6)
                }
            }
            badgeRow.addView(deadlinePill)
        }

        if (task.source.isNotBlank()) {
            val sourcePill = TextView(this).apply {
                text = "💬 ${task.source}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#334155"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(6)
                }
            }
            badgeRow.addView(sourcePill)
        }

        // Quick Alarm pill button or Cancelled status pill
        var alarmPill: TextView? = null
        if (isCancelled) {
            val cancelledPill = TextView(this).apply {
                text = "↺ Đã hủy (Nhấn giữ khôi phục)"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#26EF4444"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#4DEF4444"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                isClickable = true
                setOnClickListener {
                    Toast.makeText(this@FloatingBubbleService, "Nhấn giữ để khôi phục nhiệm vụ đã hủy", Toast.LENGTH_SHORT).show()
                }
            }
            badgeRow.addView(cancelledPill)
        } else if (!isCompleted) {
            val pill = TextView(this).apply {
                text = if (isAlarmActive) "✓ Đã đặt $proposedTime" else "🔔 Đặt $proposedTime"
                setTextColor(if (isAlarmActive) Color.parseColor("#10B981") else Color.parseColor("#38BDF8"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(if (isAlarmActive) Color.parseColor("#062E24") else Color.parseColor("#082F49"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), if (isAlarmActive) Color.parseColor("#059669") else Color.parseColor("#0284C7"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                isClickable = true
                setOnClickListener {
                    if (isAlarmActive) {
                        SystemAlarmHelper.cancelSmartAlarms(this@FloatingBubbleService, cleanTitle, alarmId)
                        val disarmed = (existingAlarm ?: SmartAlarmEntity(
                            alarmId = alarmId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = targetCal.timeInMillis,
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.PROPOSED
                        )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                        alarmDbMap = alarmDbMap + (alarmId to disarmed)
                        Toast.makeText(this@FloatingBubbleService, "Đã tắt chuông báo: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                    } else {
                        val result = SystemAlarmHelper.scheduleSmartAlarms(
                            context = this@FloatingBubbleService,
                            taskTitle = cleanTitle,
                            targetCalendar = targetCal,
                            mode = AlarmCascadeMode.X1
                        )
                        result.onSuccess {
                            val armed = (existingAlarm ?: SmartAlarmEntity(
                                alarmId = alarmId,
                                taskTitle = cleanTitle,
                                targetTimeMillis = targetCal.timeInMillis,
                                targetDateLabel = proposedTime,
                                status = SmartAlarmStatus.ACTIVE
                            )).copy(status = SmartAlarmStatus.ACTIVE, updatedAt = System.currentTimeMillis())
                            alarmDbMap = alarmDbMap + (alarmId to armed)
                            Toast.makeText(this@FloatingBubbleService, "🔔 Đã đặt chuông báo $proposedTime: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(this@FloatingBubbleService, "Không thể đặt báo thức: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    updateBubbleContent()
                }
            }
            alarmPill = pill
            badgeRow.addView(pill)
        }

        taskRow.addView(badgeRow)
        container.addView(taskRow)

        // Unified Gesture Handler for taskRow: Horizontal Swipe (Complete/Cancel), Long Press 550ms (Dialog), Short Tap (Quick alarm)
        var startX = 0f
        var startY = 0f
        var isSwiping = false
        var isLongPressTriggered = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val swipeThreshold = dpToPx(72) // Tăng thêm 20% ngưỡng kéo theo lệnh user (60dp * 1.2 = 72dp)
        val taskHandler = Handler(Looper.getMainLooper())

        val longPressRunnable = Runnable {
            if (!isSwiping) {
                isLongPressTriggered = true
                if (isCancelled) {
                    showFloatingConfirmationDialog(
                        title = "Khôi phục nhiệm vụ?",
                        message = "Bạn có muốn khôi phục lại nhiệm vụ \"$cleanTitle\" về danh sách việc cần làm không?",
                        confirmText = "Khôi phục",
                        confirmColor = Color.parseColor("#10B981")
                    ) {
                        SystemAlarmHelper.restoreCancelledTask(this@FloatingBubbleService, cleanTitle, alarmId)
                        val restoredAlarm = (existingAlarm ?: SmartAlarmEntity(
                            alarmId = alarmId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = System.currentTimeMillis(),
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.PROPOSED
                        )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                        alarmDbMap = alarmDbMap + (alarmId to restoredAlarm) + (generatedAlarmId to restoredAlarm)
                        Toast.makeText(this@FloatingBubbleService, "Đã khôi phục nhiệm vụ thành công!", Toast.LENGTH_SHORT).show()
                        updateBubbleContent()
                    }
                } else if (isCompleted) {
                    showFloatingConfirmationDialog(
                        title = "Đưa về chưa hoàn thành?",
                        message = "Bạn có muốn đưa nhiệm vụ \"$cleanTitle\" về lại danh sách việc cần làm (chưa hoàn thành) để tiếp tục theo dõi không?",
                        confirmText = "Xác nhận",
                        confirmColor = Color.WHITE
                    ) {
                        SystemAlarmHelper.uncompleteTask(this@FloatingBubbleService, cleanTitle, alarmId)
                        val uncompletedAlarm = (existingAlarm ?: SmartAlarmEntity(
                            alarmId = alarmId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = System.currentTimeMillis(),
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.PROPOSED
                        )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                        alarmDbMap = alarmDbMap + (alarmId to uncompletedAlarm) + (generatedAlarmId to uncompletedAlarm)
                        Toast.makeText(this@FloatingBubbleService, "Đã đưa về trạng thái chưa hoàn thành!", Toast.LENGTH_SHORT).show()
                        updateBubbleContent()
                    }
                } else {
                    showFloatingTaskConfigDialog(task, alarmId, existingAlarm)
                }
            }
        }

        taskRow.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isSwiping = false
                    isLongPressTriggered = false
                    taskHandler.removeCallbacks(longPressRunnable)
                    // Nhấn giữ 550ms cho thoải mái, không cần rung theo lệnh user
                    taskHandler.postDelayed(longPressRunnable, 550L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        taskHandler.removeCallbacks(longPressRunnable)
                    }

                    if (!isSwiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        val allowRight = !isCompleted && !isCancelled
                        val allowLeft = !isCancelled
                        if ((dx > 0 && allowRight) || (dx < 0 && allowLeft)) {
                            isSwiping = true
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }

                    if (isSwiping) {
                        val clampedDx = when {
                            dx > 0 && (isCompleted || isCancelled) -> 0f
                            dx < 0 && isCancelled -> 0f
                            else -> dx
                        }
                        v.translationX = clampedDx
                        if (clampedDx > 0) {
                            leftSwipeAction.visibility = View.VISIBLE
                            rightSwipeAction.visibility = View.GONE
                        } else if (clampedDx < 0) {
                            leftSwipeAction.visibility = View.GONE
                            rightSwipeAction.visibility = View.VISIBLE
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    taskHandler.removeCallbacks(longPressRunnable)
                    if (isLongPressTriggered) {
                        return@setOnTouchListener true
                    }

                    if (isSwiping) {
                        isSwiping = false
                        val dx = event.rawX - startX
                        if (dx > swipeThreshold && !isCompleted && !isCancelled) {
                            // Vuốt phải vượt 72dp -> Hoàn thành
                            v.animate().translationX(v.width.toFloat()).setDuration(160).withEndAction {
                                SystemAlarmHelper.markTaskCompleted(this@FloatingBubbleService, cleanTitle, alarmId)
                                val completedAlarm = (existingAlarm ?: SmartAlarmEntity(
                                    alarmId = alarmId,
                                    taskTitle = cleanTitle,
                                    targetTimeMillis = System.currentTimeMillis(),
                                    targetDateLabel = "Đã xong",
                                    status = SmartAlarmStatus.COMPLETED
                                )).copy(status = SmartAlarmStatus.COMPLETED, updatedAt = System.currentTimeMillis())
                                alarmDbMap = alarmDbMap + (alarmId to completedAlarm) + (generatedAlarmId to completedAlarm)
                                Toast.makeText(this@FloatingBubbleService, "✓ Đã hoàn thành: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                                updateBubbleContent()
                            }.start()
                        } else if (dx < -swipeThreshold && !isCancelled) {
                            // Vuốt trái vượt 72dp -> Hủy việc
                            v.animate().translationX(-v.width.toFloat()).setDuration(160).withEndAction {
                                SystemAlarmHelper.markTaskCancelled(this@FloatingBubbleService, cleanTitle, alarmId)
                                val cancelledAlarm = (existingAlarm ?: SmartAlarmEntity(
                                    alarmId = alarmId,
                                    taskTitle = cleanTitle,
                                    targetTimeMillis = System.currentTimeMillis(),
                                    targetDateLabel = "Đã hủy",
                                    status = SmartAlarmStatus.CANCELLED
                                )).copy(status = SmartAlarmStatus.CANCELLED, updatedAt = System.currentTimeMillis())
                                alarmDbMap = alarmDbMap + (alarmId to cancelledAlarm) + (generatedAlarmId to cancelledAlarm)
                                Toast.makeText(this@FloatingBubbleService, "✕ Đã hủy: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                                updateBubbleContent()
                            }.start()
                        } else {
                            v.animate().translationX(0f).setDuration(180).withEndAction {
                                leftSwipeAction.visibility = View.GONE
                                rightSwipeAction.visibility = View.GONE
                            }.start()
                        }
                        true
                    } else {
                        // Chạm nhẹ nhanh (< 550ms, không kéo vuốt)
                        val dx = Math.abs(event.rawX - startX)
                        val dy = Math.abs(event.rawY - startY)
                        if (dx < touchSlop && dy < touchSlop) {
                            if (isCancelled) {
                                Toast.makeText(this@FloatingBubbleService, "Nhấn giữ để khôi phục nhiệm vụ đã hủy", Toast.LENGTH_SHORT).show()
                            } else if (isCompleted) {
                                Toast.makeText(this@FloatingBubbleService, "Nhấn giữ để đưa về trạng thái chưa hoàn thành", Toast.LENGTH_SHORT).show()
                            } else {
                                alarmPill?.performClick()
                            }
                        }
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    taskHandler.removeCallbacks(longPressRunnable)
                    if (isSwiping) {
                        isSwiping = false
                        v.animate().translationX(0f).setDuration(180).withEndAction {
                            leftSwipeAction.visibility = View.GONE
                            rightSwipeAction.visibility = View.GONE
                        }.start()
                    }
                    false
                }
                else -> false
            }
        }

        return container
    }

    private fun showFloatingConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        confirmColor: Int = Color.WHITE,
        onConfirm: () -> Unit
    ) {
        val root = bubbleRootView ?: return
        if (root.findViewWithTag<View>("CONFIRM_MODAL_SCRIM") != null) return

        val dm = resources.displayMetrics
        val dialogWidth = (dm.widthPixels * 0.86f).toInt().coerceAtMost(dpToPx(380))

        val modalScrim = FrameLayout(this).apply {
            tag = "CONFIRM_MODAL_SCRIM"
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#80000000"))
            elevation = dpToPx(100).toFloat()
            translationZ = dpToPx(100).toFloat()
            isClickable = true
            isFocusable = true
        }

        val dialogBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            elevation = dpToPx(110).toFloat()
            translationZ = dpToPx(110).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5141418"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18))
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        dialogBox.addView(titleTv)

        val msgTv = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setLineSpacing(dpToPx(2).toFloat(), 1.15f)
            setPadding(0, dpToPx(8), 0, dpToPx(16))
        }
        dialogBox.addView(msgTv)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = TextView(this).apply {
            text = "Hủy bỏ"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            isClickable = true
            setOnClickListener {
                root.removeView(modalScrim)
            }
        }
        btnRow.addView(cancelBtn)

        val confirmBtn = TextView(this).apply {
            text = confirmText
            setTextColor(if (confirmColor == Color.WHITE) Color.BLACK else Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(confirmColor)
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            isClickable = true
            setOnClickListener {
                root.removeView(modalScrim)
                onConfirm()
            }
        }
        btnRow.addView(confirmBtn)

        dialogBox.addView(btnRow)
        modalScrim.addView(dialogBox)

        modalScrim.setOnClickListener {
            root.removeView(modalScrim)
        }
        dialogBox.setOnClickListener { /* consume click */ }

        root.addView(modalScrim)
        modalScrim.bringToFront()
    }

    private fun showFloatingTaskConfigDialog(
        task: TaskItem,
        alarmId: String,
        existingAlarm: SmartAlarmEntity?
    ) {
        val root = bubbleRootView ?: return
        if (root.findViewWithTag<View>("TASK_CONFIG_SCRIM") != null) return

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val dialogWidth = (screenWidth * 0.92f).toInt().coerceAtMost(dpToPx(440))
        val dialogMaxHeight = (screenHeight * 0.74f).toInt().coerceAtMost(dpToPx(620))

        val cleanTitle = task.title.replace(Regex("""^[\[\(\-•*\d.]+\s*"""), "").trim()
        val prefs = UserPreferences.getInstance(this)
        val initialNote = prefs.getTaskNote(cleanTitle).orEmpty()

        val parsedDateTime = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
        val targetCal = (parsedDateTime?.calendar?.clone() as? Calendar) ?: Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }
        var hour = targetCal.get(Calendar.HOUR_OF_DAY)
        var minute = targetCal.get(Calendar.MINUTE)
        var cascadeMode = AlarmCascadeMode.X1
        var cascadeIntervalMinutes = 15
        var isAlarmEnabled = existingAlarm?.status == SmartAlarmStatus.ACTIVE || task.deadline.isNotBlank()

        val modalScrim = FrameLayout(this).apply {
            tag = "TASK_CONFIG_SCRIM"
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#99000000"))
            elevation = dpToPx(100).toFloat()
            translationZ = dpToPx(100).toFloat()
            isClickable = true
            isFocusable = true
        }

        val dialogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            elevation = dpToPx(110).toFloat()
            translationZ = dpToPx(110).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5141418"))
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            isClickable = true
        }

        // Title Header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(10))
        }
        val iconTv = TextView(this).apply {
            text = "✏️"
            textSize = 16f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val headerTitle = TextView(this).apply {
            text = "Ghi Chú & Báo Thức Nhiệm Vụ"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSub = TextView(this).apply {
            text = cleanTitle
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11.5f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        titleCol.addView(headerTitle)
        titleCol.addView(headerSub)
        headerRow.addView(iconTv)
        headerRow.addView(titleCol)
        dialogContainer.addView(headerRow)

        // Scrollable Body with safe max height constraint (tự co giãn vừa vặn, không bị sập 0dp)
        val bodyScroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = (resources.displayMetrics.heightPixels * 0.60f).toInt().coerceAtMost(dpToPx(480))
                val customHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, customHeightSpec)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFillViewport = true
        }
        val bodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Section 1: Ghi chú cho phiên sau
        val noteSectionLabel = TextView(this).apply {
            text = "📝 GHI CHÚ / LỜI DẶN CHO PHIÊN SAU"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        bodyLayout.addView(noteSectionLabel)

        val noteEt = EditText(this).apply {
            setText(initialNote)
            hint = "Nhập dặn dò AI cho các ca tiếp theo (tùy chọn)..."
            setHintTextColor(Color.parseColor("#71717A"))
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18181B"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            minLines = 2
            maxLines = 3
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isForceDarkAllowed = false
            }
        }
        bodyLayout.addView(noteEt)

        val noteTip = TextView(this).apply {
            text = "💡 Lời dặn này sẽ được nạp trực tiếp vào AI trong các phiên tóm tắt kế tiếp để AI ưu tiên xử lý."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10f
            setPadding(0, dpToPx(2), 0, dpToPx(10))
        }
        bodyLayout.addView(noteTip)

        // Section 2: Checkbox Báo Thức
        val toggleAlarmRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A38BDF8"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#3338BDF8"))
            }
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            isClickable = true
        }

        val checkTv = TextView(this).apply {
            text = if (isAlarmEnabled) "☑" else "☐"
            setTextColor(if (isAlarmEnabled) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
            textSize = 18f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val labelCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val labelMain = TextView(this).apply {
            text = "Đặt báo thức nhắc nhở"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val labelSub = TextView(this).apply {
            text = if (isAlarmEnabled) "Bật chuông báo hệ thống khi đến hạn" else "Không đặt chuông báo cho việc này"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10.5f
        }
        labelCol.addView(labelMain)
        labelCol.addView(labelSub)
        toggleAlarmRow.addView(checkTv)
        toggleAlarmRow.addView(labelCol)
        bodyLayout.addView(toggleAlarmRow)

        // Alarm configuration controls container
        val alarmConfigContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, 0)
            visibility = if (isAlarmEnabled) View.VISIBLE else View.GONE
        }

        // Time display
        val timeDisplayBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1F1E293B"))
                cornerRadius = dpToPx(10).toFloat()
            }
            setPadding(0, dpToPx(8), 0, dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(6)
            }
        }
        val timeTv = TextView(this).apply {
            text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        val dateSubTv = TextView(this).apply {
            text = "📅 ${parsedDateTime?.dateLabel ?: "Hôm nay"} • ${if (task.deadline.isNotBlank()) task.deadline else "Gợi ý tự động"}"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
        }
        timeDisplayBox.addView(timeTv)
        timeDisplayBox.addView(dateSubTv)
        alarmConfigContainer.addView(timeDisplayBox)

        fun updateTimeDisplay() {
            timeTv.text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            targetCal.set(Calendar.HOUR_OF_DAY, hour)
            targetCal.set(Calendar.MINUTE, minute)
        }

        // Delta buttons (-15, -5, +5, +15)
        val deltaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }
        listOf(-15, -5, 5, 15).forEach { delta ->
            val deltaBtn = TextView(this).apply {
                text = if (delta > 0) "+${delta}p" else "${delta}p"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#26334155"))
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#475569"))
                }
                setPadding(0, dpToPx(6), 0, dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                isClickable = true
                setOnClickListener {
                    val total = (hour * 60 + minute + delta + 1440) % 1440
                    hour = total / 60
                    minute = total % 60
                    updateTimeDisplay()
                }
            }
            deltaRow.addView(deltaBtn)
        }
        alarmConfigContainer.addView(deltaRow)

        // Cascade mode selector
        val cascadeLabel = TextView(this).apply {
            text = "CHẾ ĐỘ CHUÔNG BÁO LIÊN TIẾP:"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(4))
        }
        alarmConfigContainer.addView(cascadeLabel)

        val cascadeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val cascadeViews = mutableListOf<TextView>()
        AlarmCascadeMode.entries.forEach { mode ->
            val modeBtn = TextView(this).apply {
                text = "${mode.displayName}\n${mode.subtitle}"
                setTextColor(if (mode == cascadeMode) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (mode == cascadeMode) Color.parseColor("#260284C7") else Color.parseColor("#1A1E293B"))
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), if (mode == cascadeMode) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                isClickable = true
                setOnClickListener {
                    cascadeMode = mode
                    cascadeViews.forEachIndexed { idx, v ->
                        val m = AlarmCascadeMode.entries[idx]
                        val sel = m == cascadeMode
                        v.setTextColor(if (sel) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                        (v.background as? GradientDrawable)?.apply {
                            setColor(if (sel) Color.parseColor("#260284C7") else Color.parseColor("#1A1E293B"))
                            setStroke(dpToPx(1), if (sel) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                        }
                    }
                }
            }
            cascadeViews.add(modeBtn)
            cascadeRow.addView(modeBtn)
        }
        alarmConfigContainer.addView(cascadeRow)

        // Cancel existing alarm button if active
        if (existingAlarm?.status == SmartAlarmStatus.ACTIVE) {
            val cancelAlarmBtn = TextView(this).apply {
                text = "🗑 Hủy bỏ chuông báo này ngay lập tức"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AEF4444"))
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#4DEF4444"))
                }
                setPadding(0, dpToPx(6), 0, dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(4)
                }
                isClickable = true
                setOnClickListener {
                    SystemAlarmHelper.cancelSmartAlarms(this@FloatingBubbleService, cleanTitle, alarmId)
                    isAlarmEnabled = false
                    checkTv.text = "☐"
                    checkTv.setTextColor(Color.parseColor("#94A3B8"))
                    labelSub.text = "Không đặt chuông báo cho việc này"
                    alarmConfigContainer.visibility = View.GONE
                    Toast.makeText(this@FloatingBubbleService, "Đã hủy chuông báo cho: ${cleanTitle.take(20)}...", Toast.LENGTH_SHORT).show()
                }
            }
            alarmConfigContainer.addView(cancelAlarmBtn)
        }

        toggleAlarmRow.setOnClickListener {
            isAlarmEnabled = !isAlarmEnabled
            checkTv.text = if (isAlarmEnabled) "☑" else "☐"
            checkTv.setTextColor(if (isAlarmEnabled) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
            labelSub.text = if (isAlarmEnabled) "Bật chuông báo hệ thống khi đến hạn" else "Không đặt chuông báo cho việc này"
            alarmConfigContainer.visibility = if (isAlarmEnabled) View.VISIBLE else View.GONE
        }

        bodyLayout.addView(alarmConfigContainer)
        bodyScroll.addView(bodyLayout)
        dialogContainer.addView(bodyScroll)

        // Bottom Actions Row (Đóng / Lưu thiết lập)
        val bottomActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        val closeBtn = TextView(this).apply {
            text = "Đóng"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            isClickable = true
            setOnClickListener {
                root.removeView(modalScrim)
            }
        }
        bottomActions.addView(closeBtn)

        val saveBtn = TextView(this).apply {
            text = "Lưu thiết lập"
            setTextColor(Color.BLACK)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(8).toFloat()
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            isClickable = true
            setOnClickListener {
                val noteText = noteEt.text.toString().trim()
                prefs.setTaskNote(cleanTitle, noteText)

                if (isAlarmEnabled) {
                    if (existingAlarm != null) {
                        SystemAlarmHelper.cancelSmartAlarms(this@FloatingBubbleService, cleanTitle, alarmId)
                    }
                    val res = SystemAlarmHelper.scheduleSmartAlarms(
                        context = this@FloatingBubbleService,
                        taskTitle = cleanTitle,
                        targetCalendar = targetCal,
                        mode = cascadeMode,
                        intervalMinutes = cascadeIntervalMinutes
                    )
                    res.onSuccess {
                        val noteMsg = if (noteText.isNotBlank()) " và đã lưu ghi chú" else ""
                        Toast.makeText(this@FloatingBubbleService, "Đã lên lịch báo thức$noteMsg (${parsedDateTime?.dateLabel ?: "Hôm nay"})!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(this@FloatingBubbleService, "Lỗi đặt báo thức: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (existingAlarm?.status == SmartAlarmStatus.ACTIVE) {
                        SystemAlarmHelper.cancelSmartAlarms(this@FloatingBubbleService, cleanTitle, alarmId)
                    }
                    val msg = if (noteText.isNotBlank()) "Đã lưu ghi chú cho phiên sau!" else "Đã lưu thay đổi!"
                    Toast.makeText(this@FloatingBubbleService, msg, Toast.LENGTH_SHORT).show()
                }

                root.removeView(modalScrim)
                updateBubbleContent()
            }
        }
        bottomActions.addView(saveBtn)

        dialogContainer.addView(bottomActions)
        modalScrim.addView(dialogContainer)

        modalScrim.setOnClickListener {
            root.removeView(modalScrim)
        }
        dialogContainer.setOnClickListener { /* prevent closing */ }

        root.addView(modalScrim)
        modalScrim.bringToFront()
    }

    private fun updateBubbleContent() {
        val root = bubbleRootView as? FrameLayout ?: return
        if (isExpanded) {
            expandFloatingCard(root, animate = false)
        } else {
            renderCollapsedBubble(root)
            transitionToBubbleState(currentBubbleState, animate = false)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bubbleSize = dpToPx(44)
        val minY = dpToPx(28)
        val maxY = (screenHeight - bubbleSize - dpToPx(32)).coerceAtLeast(minY)

        lastCollapsedY = lastCollapsedY.coerceIn(minY, maxY)
        val isLeft = isAttachedLeft
        lastCollapsedX = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else (screenWidth - bubbleSize - dpToPx(8))
            BubbleState.STATE_2_DIMMED -> if (isLeft) -dpToPx(32) else (screenWidth - bubbleSize + dpToPx(32))
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -dpToPx(44) else screenWidth
        }

        if (isExpanded) {
            (bubbleRootView as? FrameLayout)?.let { expandFloatingCard(it, animate = false) }
        } else {
            windowParams?.let { p ->
                p.x = lastCollapsedX
                p.y = lastCollapsedY
                try {
                    if (bubbleRootView?.isAttachedToWindow == true) {
                        windowManager.updateViewLayout(bubbleRootView, p)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
        bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
        currentAnimator?.cancel()
        currentAnimator = null
        cachedWallpaperBmp?.recycle()
        cachedWallpaperBmp = null
        hideDismissTarget(immediate = true)

        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        screenReceiver = null

        sensorManager?.let { sm ->
            shakeDetector?.stop(sm)
        }
        shakeDetector = null
        sensorThread?.quitSafely()
        sensorThread = null

        bubbleRootView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi gỡ bỏ bong bóng: ${e.message}")
            }
        }
        bubbleRootView = null

        dismissTargetView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi gỡ bỏ dismissTargetView: ${e.message}")
            }
        }
        dismissTargetView = null
        dismissCircleView = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val TAG = "FloatingBubble"
        const val EXTRA_SUMMARY_CONTENT = "extra_summary_content"
        const val EXTRA_MODEL_INFO = "extra_model_info"

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun getOverlayPermissionIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }

        fun show(context: Context, summaryContent: String = "", modelInfo: String = "") {
            if (!isOverlayPermissionGranted(context)) return
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                putExtra(EXTRA_SUMMARY_CONTENT, summaryContent)
                putExtra(EXTRA_MODEL_INFO, modelInfo)
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }
}
