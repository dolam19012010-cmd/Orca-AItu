package com.orca.orcaauto.service

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.orca.orcaauto.R
import com.orca.orcaauto.ui.components.SoundWaveVisualizerView
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.local.getBestSummaryForSession
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.RateLimitDetector
import com.orca.orcaauto.domain.LiveAiChatHelper
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.assistant.AssistantPipeline
import com.orca.orcaauto.domain.assistant.AssistantStep
import com.orca.orcaauto.domain.assistant.CommandStripper
import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.domain.filter.TaskItem
import com.orca.orcaauto.domain.filter.TaskStatus
import com.orca.orcaauto.ui.MainActivity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Custom [EditText] subclass that hooks into [onSelectionChanged] to notify listeners of text
 * selection changes, powering the in-card contextual quick action bar while preserving native
 * AOSP selection mechanics and drag handles.
 */
open class SelectionAwareEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : EditText(context, attrs, defStyleAttr) {

    var onSelectionChangedListener: ((selStart: Int, selEnd: Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }
}

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleRootView: FrameLayout? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isCallingAi = false
    private var activeStreamingJob: Job? = null
    private var activeWatchdogJob: Job? = null
    private var isExpanded = false
    private var latestSummaryText: String = ""
    private var latestModelInfo: String = ""
    // In-Memory Hot Cache for instantaneous session switching & bubble expansion (0ms flash bypass)
    private val sessionSummaryCache = ConcurrentHashMap<String, Pair<String, String>>()
    private var activeChatAnswer: String? = null
    private var currentCardLayoutRef: View? = null
    @Volatile private var cachedParsedSummary: ParsedSummary? = null
    private var activeSummaryParseJob: Job? = null
    private val summaryParseGen = AtomicLong(0L)
    private var currentAnimator: ValueAnimator? = null

    private val wallpaperManager by lazy { BubbleWallpaperManager(this, serviceScope) }
    private val dialogRenderer by lazy {
        BubbleDialogRenderer(
            context = this,
            serviceScope = serviceScope,
            rootViewProvider = { bubbleRootView }
        )
    }
    private val taskBoardController by lazy {
        BubbleTaskBoardController(
            context = this,
            serviceScope = serviceScope,
            getBubbleRootView = { bubbleRootView },
            getParsedSummary = { cachedParsedSummary ?: SummaryParser.parse(latestSummaryText).also { cachedParsedSummary = it } },
            hasAdviceProvider = { isCallingAi || !activeChatAnswer.isNullOrBlank() || latestSummaryText.isNotBlank() },
            isExpandedProvider = { isExpanded },
            onCollapseRequested = { root -> (root as? FrameLayout)?.let { collapseExpandedCard(it) } }
        ).also { controller ->
            dialogRenderer.taskBoardController = controller
            controller.dialogRenderer = dialogRenderer
        }
    }
    private var unreadDotView: View? = null
    private var lastKnownSummaryId: Long? = null
    private var prewarmedCardShell: CardShellHolder? = null
    private var activeSpeechRecognizer: SpeechRecognizer? = null
    private var isRecordingVoice: Boolean = false
    private var activeHoldRunnable: Runnable? = null
    private var activeOutsideHoldRunnable: Runnable? = null
    private var cancelActiveVoiceSession: (() -> Unit)? = null

    private fun cleanupVoiceRecording() {
        activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
        activeHoldRunnable = null
        activeOutsideHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
        activeOutsideHoldRunnable = null
        try {
            activeSpeechRecognizer?.cancel()
            activeSpeechRecognizer?.destroy()
        } catch (_: Exception) {}
        activeSpeechRecognizer = null
        isRecordingVoice = false
        cancelActiveVoiceSession?.invoke()
    }

    private val suppressActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode?) {}
    }

    private class CardShellHolder(
        val cardLayout: LinearLayout,
        val headerRow: LinearLayout,
        val titleTv: TextView,
        val modelTv: TextView,
        val openAppBtn: TextView,
        val sessionsScroll: HorizontalScrollView,
        val sessionsLayout: LinearLayout,
        val addSessionBtn: TextView,
        val scrollView: ScrollView,
        val contentLayout: LinearLayout,
        val sec1Box: LinearLayout,
        val adviceTv: TextView,
        val notesContainer: LinearLayout,
        val tasksBoardLayout: LinearLayout,
        val inputCard: LinearLayout,
        val queryEt: SelectionAwareEditText,
        val sendBtn: TextView,
        val quickActionBar: View,
        val thinkingChipRow: LinearLayout? = null,
        val voiceVisualizerBubble: View? = null,
        val soundVisualizerView: SoundWaveVisualizerView? = null
    )


    private fun updateSummaryTextAndParseEagerly(newSummary: String, scope: CoroutineScope = serviceScope, preParsed: ParsedSummary? = null) {
        latestSummaryText = newSummary
        val currentGen = summaryParseGen.incrementAndGet()
        activeSummaryParseJob?.cancel()
        if (newSummary.isBlank()) {
            activeSummaryParseJob = null
            cachedParsedSummary = ParsedSummary(
                rawCleanText = "", reasoningNotes = null, conciseAdvice = "",
                criticalQuestions = emptyList(), tasksToDo = emptyList(),
                tasksDone = emptyList(), tasksPlanned = emptyList()
            )
            return
        }
        if (preParsed != null) { activeSummaryParseJob = null; cachedParsedSummary = preParsed; return }
        // Retain existing cachedParsedSummary during in-session streaming to avoid UI flicker
        activeSummaryParseJob = scope.launch(Dispatchers.Default) {
            val parsed = SummaryParser.parse(newSummary)
            if (isActive && summaryParseGen.get() == currentGen) {
                cachedParsedSummary = parsed
            }
        }
    }

    private fun extractLiveStreamSnippet(raw: String): String {
        val isEn = try {
            UserPreferences.getInstance(this).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
        } catch (_: Exception) {
            false
        }
        return SummaryParser.extractLiveAdvicePreview(raw, isEnglish = isEn)
    }

    private enum class SendBtnState {
        CALLING_AI,
        HAS_TEXT,
        EMPTY
    }

    private val sendBtnBgCallingAi by lazy {
        GradientDrawable().apply {
            setColor(Color.parseColor("#3327272A"))
            cornerRadius = dpToPx(18).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
        }
    }
    private val sendBtnBgActive by lazy {
        GradientDrawable().apply {
            setColor(Color.parseColor("#38BDF8"))
            cornerRadius = dpToPx(18).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
        }
    }
    private val sendBtnBgInactive by lazy {
        GradientDrawable().apply {
            setColor(Color.parseColor("#27272A"))
            cornerRadius = dpToPx(18).toFloat()
            setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
        }
    }
    private var currentSendBtnState: SendBtnState? = null

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
    private var sessionSwitchJob: Job? = null

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
        isScreenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
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
                        updateShakeDetectorState(forceScreenOff = true)
                        stopTextShimmer(null); liveStatusShimmerAnimator?.cancel(); liveStatusShimmerAnimator = null
                        cleanupVoiceRecording(); cancelActiveVoiceSession?.invoke()
                        try { com.orca.orcaauto.domain.voice.VoiceBriefingManager.getInstance(this@FloatingBubbleService).stop() } catch (_: Exception) {}
                        if (isExpanded) {
                            bubbleRootView?.let { collapseExpandedCard(it, preserveStreamingJob = true, targetState = BubbleState.STATE_2_DIMMED) }
                            transitionToBubbleState(BubbleState.STATE_2_DIMMED, animate = false)
                        }
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
        wallpaperManager.preWarmWallpaper { if (isExpanded) currentCardLayoutRef else null }

        // 1. Reactive DB Flow: Real-time Alarms & Tasks status sync
        serviceScope.launch {
            try {
                AppDatabase.getDatabase(this@FloatingBubbleService)
                    .smartAlarmDao()
                    .getAllAlarmsFlow()
                    .collect { alarms ->
                        taskBoardController.updateAlarmsFromDb(alarms)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi lắng nghe alarm flow: ${e.message}")
            }
        }

        // 2. Reactive DB Flow: Real-time latest AI summary updates (from Background Worker or App)
        serviceScope.launch {
            try {
                AppDatabase.getDatabase(this@FloatingBubbleService)
                    .summaryDao()
                    .getLatestSummaryFlow()
                    .collect { latest ->
                        if (latest != null && latest.summaryContent.isNotBlank()) {
                            // Warm up in-memory cache for this session immediately
                            val sid = latest.sessionId ?: com.orca.orcaauto.data.preferences.SummarySessionConfig.MAIN_SESSION_ID
                            sessionSummaryCache[sid] = Pair(latest.summaryContent, latest.modelInfo)

                            // Đảm bảo chỉ xử lý bản ghi thuộc phiên hiện tại (hoặc phiên chính)
                            val prefs = UserPreferences.getInstance(this@FloatingBubbleService)
                            val currentSessionId = prefs.getSelectedSessionId()
                            val isMatchingSession = latest.sessionId == currentSessionId ||
                                    (currentSessionId == com.orca.orcaauto.data.preferences.SummarySessionConfig.MAIN_SESSION_ID &&
                                            (latest.sessionId.isNullOrBlank() || latest.sessionId == com.orca.orcaauto.data.preferences.SummarySessionConfig.MAIN_SESSION_ID))
                            if (!isMatchingSession) {
                                return@collect
                            }

                            val isNewSummary = lastKnownSummaryId != null && lastKnownSummaryId != latest.id
                            lastKnownSummaryId = latest.id

                            val parsedLatest = SummaryParser.parse(latest.summaryContent)
                            val hasTasks = parsedLatest.totalTaskCount > 0

                            val currentHasTasks = latestSummaryText.isNotBlank() && SummaryParser.parse(latestSummaryText).totalTaskCount > 0
                            if (hasTasks || !currentHasTasks) {
                                latestSummaryText = latest.summaryContent
                                cachedParsedSummary = parsedLatest
                                activeChatAnswer = null
                            }
                            if (latest.modelInfo.isNotBlank()) {
                                latestModelInfo = latest.modelInfo
                            }
                            if (isNewSummary && hasTasks) {
                                taskBoardController.resetDismissedTasks()
                                UserPreferences.getInstance(this@FloatingBubbleService).setHasUnreadSummary(true)
                                withContext(Dispatchers.Main) {
                                    if (bubbleRootView != null && !isExpanded) {
                                        transitionToBubbleState(BubbleState.STATE_1_ACTIVE, animate = true, activeDurationMs = 4000L)
                                    }
                                }
                            }
                            if (isExpanded) updateBubbleContent()
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi lắng nghe summary flow: ${e.message}")
            }
        }

        // 3. Reactive Flow for Unread Red Dot Indicator (Clean Red Dot, no numbers)
        serviceScope.launch {
            try {
                UserPreferences.getInstance(this@FloatingBubbleService)
                    .hasUnreadSummaryFlow
                    .collect { hasUnread ->
                        withContext(Dispatchers.Main) {
                            unreadDotView?.visibility = if (hasUnread) View.VISIBLE else View.GONE
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi lắng nghe unread flow: ${e.message}")
            }
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

        val shouldWake = intent?.getBooleanExtra(EXTRA_WAKE_TO_STATE_1, false) == true ||
                intent?.action == ACTION_WAKE_TO_STATE_1
        if (shouldWake) {
            UserPreferences.getInstance(this).setHasUnreadSummary(true)
        }

        val targetSessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        if (!targetSessionId.isNullOrBlank()) {
            if (UserPreferences.getInstance(this).getSelectedSessionId() != targetSessionId) {
                cachedParsedSummary = null
            }
            UserPreferences.getInstance(this).setSelectedSessionId(targetSessionId)
        }

        val activeSessionId = UserPreferences.getInstance(this).getSelectedSessionId()
        if (summary.isNotBlank()) {
            latestSummaryText = summary
            if (model.isNotBlank()) latestModelInfo = model
            sessionSummaryCache[activeSessionId] = Pair(summary, model)
        } else {
            sessionSummaryCache[activeSessionId]?.let { cached ->
                if (cached.first.isNotBlank()) {
                    latestSummaryText = cached.first
                    latestModelInfo = cached.second
                }
            }
        }

        // Synchronize latest summary strictly for the active session clone
        sessionSwitchJob?.cancel()
        sessionSwitchJob = serviceScope.launch {
            try {
                val prefs = UserPreferences.getInstance(this@FloatingBubbleService)
                val currentSessionId = prefs.getSelectedSessionId()
                val db = AppDatabase.getDatabase(this@FloatingBubbleService)
                val sessionRecord = db.summaryDao().getBestSummaryForSession(currentSessionId)

                val newSummary: String
                val newModel: String
                if (summary.isNotBlank()) {
                    newSummary = summary
                    newModel = model
                } else if (sessionRecord != null && sessionRecord.summaryContent.isNotBlank()) {
                    newSummary = sessionRecord.summaryContent
                    newModel = sessionRecord.modelInfo
                } else {
                    val activeSession = prefs.getSelectedSession()
                    newSummary = "Chưa có báo cáo cho '${activeSession.name}'.\n\nNhấn nút 'Gửi' bên dưới để tóm tắt các tin nhắn thuộc phiên này."
                    newModel = "${activeSession.iconEmoji} ${activeSession.name}"
                }
                sessionSummaryCache[currentSessionId] = Pair(newSummary, newModel)
                if (latestSummaryText != newSummary || latestModelInfo != newModel) {
                    latestSummaryText = newSummary
                    latestModelInfo = newModel
                    withContext(Dispatchers.Main) {
                        if (isExpanded) updateBubbleContent()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi đồng bộ tóm tắt khi khởi động: ${e.message}")
            }
        }

        if (Settings.canDrawOverlays(this)) {
            if (bubbleRootView == null) {
                createFloatingBubble()
            } else {
                updateBubbleContent()
                if (shouldWake && !isExpanded) {
                    transitionToBubbleState(BubbleState.STATE_1_ACTIVE, animate = true, activeDurationMs = 4000L)
                }
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
        bubbleLifecycleHandler.postDelayed({ preWarmCardShell() }, 400L)
    }

    private fun updateShakeDetectorState(forceScreenOff: Boolean = false) {
        val prefs = UserPreferences.getInstance(this)
        if (forceScreenOff) {
            isScreenOn = false
        } else if (isScreenOn) {
            isScreenOn = (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
        }
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false
        val shouldListen = !isExpanded &&
                currentBubbleState == BubbleState.STATE_3_HIDDEN &&
                isScreenOn &&
                !isLocked &&
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
        val halfBubble = bubbleSize / 2
        val currentX = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else screenWidth - bubbleSize - dpToPx(8)
            BubbleState.STATE_2_DIMMED -> if (isLeft) -halfBubble else (screenWidth - halfBubble)
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -bubbleSize else screenWidth
        }
        val currentAlpha = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.45f
            BubbleState.STATE_3_HIDDEN -> 0.0f
        }
        val currentScale = 1.0f

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

        val parsed = cachedParsedSummary ?: SummaryParser.parse(latestSummaryText).also { cachedParsedSummary = it }
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

        // Clean Red Dot Indicator (Chấm đỏ thông báo thuần, không số)
        val dotSize = dpToPx(10)
        val redDot = View(this).apply {
            tag = TAG_UNREAD_RED_DOT
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444"))
                setStroke(dpToPx(1.5f.toInt().coerceAtLeast(1)), Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dpToPx(2)
                marginEnd = dpToPx(2)
            }
            visibility = if (UserPreferences.getInstance(this@FloatingBubbleService).hasUnreadSummary()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        unreadDotView = redDot
        bubble.addView(redDot)

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
        var velocityTracker: VelocityTracker? = null
        val bubbleLongPressTimeout = 550L
        val bubbleLongPressRunnable = Runnable {
            if (!isDragging && isClick) {
                isLongPressTriggered = true
                val tapTimestampNs = SystemClock.elapsedRealtimeNanos()
                expandFloatingCard(root, tapTimestampNs = tapTimestampNs)
            }
        }

        root.setOnTouchListener { _, event ->
            val p = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    // Phương án 1: Nếu chuông báo thức đang kêu, chỉ cần chạm vào bóng chat là tắt tiếng ngay lập tức!
                    if (com.orca.orcaauto.receiver.AlarmRingtonePlayer.isPlaying) {
                        com.orca.orcaauto.receiver.AlarmRingtonePlayer.stop()
                        Toast.makeText(this@FloatingBubbleService, "✓ Đã tắt chuông báo!", Toast.LENGTH_SHORT).show()
                    }

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
                    velocityTracker?.addMovement(event)
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
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

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
                                val tapTimestampNs = SystemClock.elapsedRealtimeNanos()
                                expandFloatingCard(root, tapTimestampNs = tapTimestampNs)
                            }
                            BubbleState.STATE_3_HIDDEN -> {}
                        }
                    } else if (isDragging) {
                        // Thả tay sau khi kéo -> Snap mượt mà với quán tính vuốt & đàn hồi vật lý
                        val isLeft = if (Math.abs(vx) > 600f) {
                            vx < 0
                        } else {
                            (p.x + bubbleSize / 2) < (screenWidth / 2)
                        }
                        isAttachedLeft = isLeft
                        val targetX = if (isLeft) dpToPx(8) else screenWidth - bubbleSize - dpToPx(8)
                        val distance = Math.abs(targetX - p.x)
                        val animDuration = if (Math.abs(vx) > 800f) {
                            (distance / (Math.abs(vx) / 1000f)).toLong().coerceIn(140L, 220L)
                        } else {
                            180L
                        }

                        val startX = p.x
                        currentAnimator?.cancel()
                        var isCanceled = false
                        val animator = ValueAnimator.ofInt(startX, targetX).apply {
                            duration = animDuration
                            interpolator = OvershootInterpolator(1.10f)
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
                                if (prewarmedCardShell == null) preWarmCardShell()
                            }
                        })
                        currentAnimator = animator
                        animator.start()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
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

    private fun transitionToBubbleState(
        newState: BubbleState,
        animate: Boolean = true,
        activeDurationMs: Long = 2000L
    ) {
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

        val halfBubble = bubbleSize / 2
        // State 1: Nhô hẳn ra ngoài mép 8dp, sáng rõ 100%, scale 1.0f
        // State 2: Nép đúng 1 nửa hình tròn (bán nguyệt) ra mép, alpha 0.45f, scale 0.95f
        // State 3: Giấu bóng ngoài mép màn hình, alpha 0.0f, scale 0.85f
        val targetX = when (newState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else (screenWidth - bubbleSize - dpToPx(8))
            BubbleState.STATE_2_DIMMED -> if (isLeft) -halfBubble else (screenWidth - halfBubble)
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -bubbleSize else screenWidth
        }
        val targetAlpha = when (newState) {
            BubbleState.STATE_1_ACTIVE -> 1.0f
            BubbleState.STATE_2_DIMMED -> 0.45f
            BubbleState.STATE_3_HIDDEN -> 0.0f
        }
        val targetScale = 1.0f

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
                    bubbleLifecycleHandler.postDelayed(toState2Runnable, activeDurationMs)
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
            // Đánh thức từ State 3 (Ẩn) sang State 2: set alpha 0.45f, scale 1.0f (giữ sắc nét 100%)
            // trồi ra vị trí nép của Trạng thái 2 với curve lò xo mềm
            updateShakeDetectorState()
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            bubbleView?.alpha = 0.45f
            bubbleView?.scaleX = 1.0f
            bubbleView?.scaleY = 1.0f

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
                duration = 260L
                interpolator = OvershootInterpolator(1.08f)
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
                interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    p.x = (startX + (targetX - startX) * f).toInt()
                    p.y = lastCollapsedY
                    val currentA = startAlpha + (targetAlpha - startAlpha) * f
                    bubbleView?.alpha = currentA
                    bubbleView?.scaleX = 1.0f
                    bubbleView?.scaleY = 1.0f
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
                        bubbleView?.scaleX = 1.0f
                        bubbleView?.scaleY = 1.0f
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
                                bubbleLifecycleHandler.postDelayed(toState2Runnable, activeDurationMs)
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



    private fun cleanupEditorHandlesAndToolbar(shell: CardShellHolder?) {
        shell ?: return
        try {
            val plan = FloatingBubbleTextSelectionHelper.planLifecycleCleanup(
                hasText = !shell.queryEt.text.isNullOrEmpty()
            )
            if (plan.clearFocus) {
                shell.queryEt.clearFocus()
            }
            plan.targetSelection?.let { sel ->
                Selection.setSelection(shell.queryEt.text, sel)
            }
            if (plan.hideToolbar) {
                shell.quickActionBar.animate().cancel()
                shell.quickActionBar.visibility = View.GONE
                shell.quickActionBar.alpha = 0f
            }
            shell.voiceVisualizerBubble?.let { vb ->
                vb.animate().cancel()
                vb.visibility = View.GONE
                vb.alpha = 0f
            }
            shell.soundVisualizerView?.stopVisualizer()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(shell.queryEt.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun collapseExpandedCard(
        root: FrameLayout,
        preserveStreamingJob: Boolean = false,
        targetState: BubbleState = BubbleState.STATE_1_ACTIVE
    ) {
        if (!preserveStreamingJob) {
            activeStreamingJob?.cancel()
            activeStreamingJob = null
        }
        activeWatchdogJob?.cancel()
        activeWatchdogJob = null
        stopTextShimmer(null)
        activeChatAnswer = null
        currentCardLayoutRef = null
        taskBoardController.clearSessionTransientState()
        dialogRenderer.dismissActiveDialogs()
        currentSendBtnState = null
        cleanupVoiceRecording()
        activeOutsideHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
        activeOutsideHoldRunnable = null
        try {
            com.orca.orcaauto.domain.voice.VoiceBriefingManager.getInstance(this).stop()
        } catch (_: Exception) {}
        if (isCollapsing || !isExpanded) return
        isCollapsing = true
        hideDismissTarget(immediate = true)

        cleanupEditorHandlesAndToolbar(prewarmedCardShell)
        prewarmedCardShell?.thinkingChipRow?.let {
            it.animate().cancel()
            it.visibility = View.GONE
            it.alpha = 0f
        }

        val finalizeCollapse = {
            isCollapsing = false
            currentBubbleState = targetState
            cleanupEditorHandlesAndToolbar(prewarmedCardShell)
            prewarmedCardShell?.cardLayout?.let { cl -> (cl.parent as? ViewGroup)?.removeView(cl) }
            renderCollapsedBubble(root)
            transitionToBubbleState(targetState, animate = false)
            if (prewarmedCardShell == null) preWarmCardShell()
        }

        if (!isScreenOn) {
            finalizeCollapse()
            return
        }

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bubbleSize = dpToPx(44)
        val card = root.getChildAt(0)
        val cardWidth = card?.width?.takeIf { it > 0 } ?: (screenWidth * 0.94f).toInt().coerceAtMost(dpToPx(500))
        val cardHeight = card?.height?.takeIf { it > 0 } ?: (screenHeight * 0.74f).toInt().coerceAtMost(dpToPx(620))

        val bubbleCenterX = lastCollapsedX + bubbleSize / 2f
        val bubbleCenterY = lastCollapsedY + bubbleSize / 2f
        val cardCenterX = screenWidth / 2f
        val cardCenterY = screenHeight / 2f
        val deltaX = bubbleCenterX - cardCenterX
        val deltaY = bubbleCenterY - cardCenterY
        val targetScaleX = (bubbleSize.toFloat() / cardWidth).coerceIn(0.08f, 0.35f)
        val targetScaleY = (bubbleSize.toFloat() / cardHeight).coerceIn(0.08f, 0.35f)

        if (card != null) {
            card.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            card.animate()
                .translationX(deltaX)
                .translationY(deltaY)
                .scaleX(targetScaleX)
                .scaleY(targetScaleY)
                .alpha(0f)
                .setDuration(190)
                .setInterpolator(AccelerateInterpolator(1.2f))
                .start()
        }
        root.animate()
            .alpha(0f)
            .setDuration(190)
            .withEndAction { finalizeCollapse() }
            .start()
    }



    @SuppressLint("ClickableViewAccessibility")
    private fun View.addMicroPressEffect() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
            false
        }
    }

    private fun renderAiErrorUi(
        adviceTv: TextView,
        notesContainer: LinearLayout,
        title: String,
        detail: String,
        promptToRetry: String,
        onRetry: (String) -> Unit
    ) {
        adviceTv.text = "⚠️ $title\n$detail"
        adviceTv.setTextColor(Color.parseColor("#E2E8F0"))
        adviceTv.isClickable = true
        adviceTv.addMicroPressEffect()
        adviceTv.setOnClickListener {
            adviceTv.isClickable = false
            onRetry(promptToRetry)
        }

        notesContainer.removeAllViews()
        val retryBtn = TextView(this).apply {
            text = "🔄 Thử lại"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E3A5F"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
            ).apply {
                topMargin = dpToPx(8)
            }
            isClickable = true
            addMicroPressEffect()
            setOnClickListener {
                isClickable = false
                adviceTv.isClickable = false
                onRetry(promptToRetry)
            }
        }
        notesContainer.addView(retryBtn)
    }

    private fun renderStreamTimeoutUi(
        adviceTv: TextView,
        notesContainer: LinearLayout,
        promptToRetry: String,
        onRetry: (String) -> Unit
    ) {
        renderAiErrorUi(
            adviceTv,
            notesContainer,
            "Mất kết nối mạng",
            "Chạm để thử lại",
            promptToRetry,
            onRetry
        )
    }

    private var liveStatusShimmerAnimator: ValueAnimator? = null
    private var shimmerRunnable: Runnable? = null
    private val isShimmerActive = AtomicBoolean(false)
    private var shimmerTextViewRef: WeakReference<TextView>? = null

    private fun startTextShimmer(textView: TextView?) {
        if (textView == null) return
        isShimmerActive.set(true)
        shimmerTextViewRef = WeakReference(textView)
        liveStatusShimmerAnimator?.cancel()
        liveStatusShimmerAnimator = null
        shimmerRunnable?.let { textView.removeCallbacks(it) }

        val runnable = Runnable {
            if (!isShimmerActive.get()) return@Runnable
            val textStr = textView.text?.toString().orEmpty()
            if (textStr.isBlank()) return@Runnable
            val textWidth = textView.paint.measureText(textStr).coerceAtLeast(textView.width.toFloat()).coerceAtLeast(160f)
            val gradientWidth = (textWidth * 0.45f).coerceAtLeast(100f)
            val baseColor = Color.parseColor("#94A3B8")
            val highlightColor = Color.parseColor("#38BDF8")

            val gradient = LinearGradient(
                -gradientWidth, 0f, 0f, 0f,
                intArrayOf(baseColor, highlightColor, baseColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            val matrix = Matrix()
            textView.paint.shader = gradient

            if (!isShimmerActive.get()) {
                textView.paint.shader = null
                return@Runnable
            }

            liveStatusShimmerAnimator = ValueAnimator.ofFloat(0f, textWidth + gradientWidth * 1.5f).apply {
                duration = 1400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { anim ->
                    if (!isShimmerActive.get()) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    val v = anim.animatedValue as Float
                    matrix.setTranslate(v, 0f)
                    gradient.setLocalMatrix(matrix)
                    textView.invalidate()
                }
                start()
            }
        }
        shimmerRunnable = runnable
        textView.post(runnable)
    }

    private fun stopTextShimmer(textView: TextView?) {
        isShimmerActive.set(false)
        val targetTv = textView ?: shimmerTextViewRef?.get()
        shimmerRunnable?.let { runnable ->
            targetTv?.removeCallbacks(runnable)
            shimmerRunnable = null
        }
        liveStatusShimmerAnimator?.cancel()
        liveStatusShimmerAnimator = null
        targetTv?.paint?.shader = null
        targetTv?.setTextColor(Color.WHITE)
        targetTv?.invalidate()
    }

    private fun preWarmCardShell() {
        if (prewarmedCardShell != null) return
        val dm = resources.displayMetrics
        val cardWidth = (dm.widthPixels * 0.94f).toInt().coerceAtMost(dpToPx(500))
        val cardHeight = (dm.heightPixels * 0.74f).toInt().coerceAtMost(dpToPx(620))
        prewarmedCardShell = buildCardShell(cardWidth, cardHeight)
    }

    private fun renderSessionChips(sessionsLayout: LinearLayout, addSessionBtn: TextView, root: FrameLayout) {
        sessionsLayout.removeAllViews()
        val prefs = UserPreferences.getInstance(this)
        val sessionsList = prefs.loadSessions()
        val currentSessionId = prefs.getSelectedSessionId()

        for (session in sessionsList) {
            val isSelected = session.id == currentSessionId
            val chipView = TextView(this).apply {
                text = "${session.iconEmoji} ${session.name}"
                textSize = 12f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#E4E4E7"))
                background = GradientDrawable().apply {
                    if (isSelected) {
                        setColor(Color.parseColor("#2563EB"))
                        cornerRadius = dpToPx(14).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#93C5FD"))
                    } else {
                        setColor(Color.parseColor("#27272A"))
                        cornerRadius = dpToPx(14).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#52525B"))
                    }
                }
                setPadding(dpToPx(12), dpToPx(5), dpToPx(12), dpToPx(5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(6)
                }
                addMicroPressEffect()

                setOnClickListener {
                    val targetSessionId = session.id
                    if (prefs.getSelectedSessionId() != targetSessionId) {
                        cleanupEditorHandlesAndToolbar(prewarmedCardShell)
                        prefs.setSelectedSessionId(targetSessionId)
                        cachedParsedSummary = null

                        // Hot Cache Check: RAM hit -> 0ms immediate UI update
                        val cached = sessionSummaryCache[targetSessionId]
                        if (cached != null && cached.first.isNotBlank()) {
                            updateSummaryTextAndParseEagerly(cached.first)
                            latestModelInfo = cached.second
                            activeChatAnswer = null
                            updateBubbleContent()
                        }

                        sessionSwitchJob?.cancel()
                        sessionSwitchJob = serviceScope.launch {
                            try {
                                val db = AppDatabase.getDatabase(this@FloatingBubbleService)
                                val sessionSummary = db.summaryDao().getBestSummaryForSession(targetSessionId)
                                if (prefs.getSelectedSessionId() != targetSessionId) return@launch

                                val newSummaryText: String
                                val newModelInfo: String
                                if (sessionSummary != null && sessionSummary.summaryContent.isNotBlank()) {
                                    newSummaryText = sessionSummary.summaryContent
                                    newModelInfo = sessionSummary.modelInfo
                                } else {
                                    newSummaryText = "Chưa có báo cáo cho '${session.name}'.\n\nGõ 'tóm tắt' rồi nhấn 'Gửi' bên dưới để tóm tắt các tin nhắn thuộc phiên này."
                                    newModelInfo = "${session.iconEmoji} ${session.name}"
                                }

                                sessionSummaryCache[targetSessionId] = Pair(newSummaryText, newModelInfo)

                                if (latestSummaryText != newSummaryText || latestModelInfo != newModelInfo) {
                                    updateSummaryTextAndParseEagerly(newSummaryText)
                                    latestModelInfo = newModelInfo
                                    activeChatAnswer = null
                                    withContext(Dispatchers.Main) {
                                        if (prefs.getSelectedSessionId() == targetSessionId) {
                                            updateBubbleContent()
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Lỗi chuyển phiên trên bóng chat: ${e.message}")
                            }
                        }
                    }
                }
            }
            sessionsLayout.addView(chipView)
        }
        sessionsLayout.addView(addSessionBtn)
    }

    private fun buildCardShell(cardWidth: Int, cardHeight: Int): CardShellHolder {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight).apply {
                gravity = Gravity.CENTER
            }
            elevation = dpToPx(24).toFloat()
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            isClickable = true
            isFocusable = true
        }
        wallpaperManager.applyCardBackground(cardLayout)

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

        val isEn = UserPreferences.getInstance(this).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH

        val modelTv = TextView(this).apply {
            text = if (latestModelInfo.isNotBlank()) latestModelInfo else (if (isEn) "Smart AI Dispatch Assistant" else "Trợ lý AI điều phối thông minh")
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10.5f
        }
        titleContainer.addView(modelTv)
        headerRow.addView(titleContainer)

        val openAppBtn = TextView(this).apply {
            text = if (isEn) "Open app ↗" else "Mở app ↗"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#66FFFFFF"))
            }
            setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5))
            addMicroPressEffect()
            setOnClickListener {
                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "history")
                }
                startActivity(intent)
                val root = bubbleRootView
                if (root != null) collapseExpandedCard(root)
            }
        }
        headerRow.addView(openAppBtn)
        cardLayout.addView(headerRow)

        val sessionsScroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(3)
                bottomMargin = dpToPx(5)
            }
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val sessionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(1), 0, dpToPx(1))
        }

        val addSessionBtn = TextView(this).apply {
            text = "＋"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#38BDF8"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#27272A"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#0284C7"))
            }
            setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4))
            addMicroPressEffect()
            setOnClickListener {
                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "advanced_sessions")
                }
                startActivity(intent)
                val root = bubbleRootView
                if (root != null) collapseExpandedCard(root)
            }
        }
        sessionsScroll.addView(sessionsLayout)
        cardLayout.addView(sessionsScroll)

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
            isClickable = true
            isLongClickable = true
            setOnLongClickListener {
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    val p = cachedParsedSummary ?: SummaryParser.parse(latestSummaryText)
                    val sp = taskBoardController.filterCancelledTasks(p)
                    val isEn = UserPreferences.getInstance(this@FloatingBubbleService).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
                    com.orca.orcaauto.domain.voice.VoiceBriefingManager.getInstance(this@FloatingBubbleService)
                        .speakSummary(0L, sp, isEnglish = isEn)
                } catch (e: Exception) {
                    Log.e(TAG, "Voice briefing failed", e)
                }
                true
            }
            setOnClickListener {
                val vm = com.orca.orcaauto.domain.voice.VoiceBriefingManager.getInstance(this@FloatingBubbleService)
                if (vm.isSpeakingFlow.value) {
                    vm.stop()
                }
            }
        }

        val adviceTv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12.5f
            setLineSpacing(dpToPx(2).toFloat(), 1.15f)
            setTextIsSelectable(true)
            highlightColor = Color.parseColor("#4D38BDF8")
            customSelectionActionModeCallback = suppressActionModeCallback
        }
        sec1Box.addView(adviceTv)

        // R1: Double-tap to Copy Summary with haptic feedback & clean toast
        val copySummaryToClipboard = {
            if (isCallingAi) {
                val isEn = UserPreferences.getInstance(this@FloatingBubbleService).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
                Toast.makeText(
                    this@FloatingBubbleService,
                    if (isEn) "AI is generating summary..." else "AI đang tạo tóm tắt, vui lòng đợi...",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                val textToCopy = adviceTv.text?.toString()?.trim().orEmpty().ifBlank {
                    val parsed = cachedParsedSummary ?: SummaryParser.parse(latestSummaryText)
                    parsed.conciseAdvice.trim().ifBlank { latestSummaryText.trim() }
                }
                if (textToCopy.isNotBlank()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("OrcaAuto Summary", textToCopy)
                    clipboard?.setPrimaryClip(clip)
                    triggerHapticFeedback()
                    try {
                        sec1Box.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } catch (_: Throwable) {}
                    val isEn = UserPreferences.getInstance(this@FloatingBubbleService).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
                    Toast.makeText(
                        this@FloatingBubbleService,
                        if (isEn) "✓ Summary copied to clipboard" else "✓ Đã sao chép tóm tắt vào bộ nhớ tạm",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        var lastSummaryTapTime = 0L
        var lastSummaryDownX = 0f
        var lastSummaryDownY = 0f
        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong().coerceAtLeast(300L)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        val summaryDoubleTapListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastSummaryDownX = event.x
                    lastSummaryDownY = event.y
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.x - lastSummaryDownX)
                    val dy = Math.abs(event.y - lastSummaryDownY)
                    if (dx < touchSlop && dy < touchSlop) {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSummaryTapTime < doubleTapTimeout) {
                            lastSummaryTapTime = 0L
                            copySummaryToClipboard()
                            true
                        } else {
                            lastSummaryTapTime = now
                            false
                        }
                    } else {
                        lastSummaryTapTime = 0L
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    lastSummaryTapTime = 0L
                    false
                }
                else -> false
            }
        }

        sec1Box.setOnTouchListener(summaryDoubleTapListener)
        adviceTv.setOnTouchListener(summaryDoubleTapListener)

        val notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        sec1Box.addView(notesContainer)
        contentLayout.addView(sec1Box)

        val tasksBoardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        contentLayout.addView(tasksBoardLayout)

        scrollView.addView(contentLayout)
        cardLayout.addView(scrollView)

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

        val prefs = UserPreferences.getInstance(this)
        var activeThinkingLevel = prefs.getAssistantThinkingLevel()
        val thinkingChipViews = mutableMapOf<AssistantThinkingLevel, TextView>()

        fun updateThinkingChipStyle(chipView: TextView, isSelected: Boolean) {
            chipView.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            chipView.setTextColor(if (isSelected) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
            chipView.background = GradientDrawable().apply {
                if (isSelected) {
                    setColor(Color.parseColor("#1E3A5F"))
                    cornerRadius = dpToPx(12).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
                } else {
                    setColor(Color.parseColor("#18181B"))
                    cornerRadius = dpToPx(12).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
                }
            }
        }

        val thinkingChipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            alpha = 0f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val thinkingLevels = listOf(
            AssistantThinkingLevel.FAST to (if (isEn) "⚡ Fast" else "⚡ Nhanh"),
            AssistantThinkingLevel.BALANCED to (if (isEn) "⚖ Balanced" else "⚖ Cân bằng"),
            AssistantThinkingLevel.DEEP to (if (isEn) "🧠 Deep" else "🧠 Sâu sắc")
        )

        for ((index, item) in thinkingLevels.withIndex()) {
            val (level, label) = item
            val chip = TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                isSingleLine = true
                isFocusable = false
                setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply {
                    if (index > 0) leftMargin = dpToPx(6)
                }
                isClickable = true
                addMicroPressEffect()
                setOnClickListener {
                    if (activeThinkingLevel != level) {
                        activeThinkingLevel = level
                        prefs.setAssistantThinkingLevel(level)
                        thinkingChipViews.forEach { (lvl, view) ->
                            updateThinkingChipStyle(view, isSelected = (lvl == level))
                        }
                    }
                }
            }
            updateThinkingChipStyle(chip, isSelected = (level == activeThinkingLevel))
            thinkingChipViews[level] = chip
            thinkingChipRow.addView(chip)
        }
        inputCard.addView(thinkingChipRow)

        val quickActionScroll = HorizontalScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            alpha = 0f
            minimumHeight = dpToPx(32)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(6)
            }
        }

        val voiceVisualizerBubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            alpha = 0f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E618181B"))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(10), dpToPx(4), dpToPx(12), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(36)).apply {
                bottomMargin = dpToPx(6)
            }
        }

        val soundVisualizerView = SoundWaveVisualizerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(110), dpToPx(24))
        }

        val cancelHintTv = TextView(this).apply {
            text = if (isEn) "‹ Slide left to cancel" else "‹ Trượt sang trái để huỷ"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(8)
            }
        }

        voiceVisualizerBubble.addView(soundVisualizerView)
        voiceVisualizerBubble.addView(cancelHintTv)
        inputCard.addView(voiceVisualizerBubble)

        fun showQuickActionBar() {
            if (quickActionScroll.visibility == View.VISIBLE && quickActionScroll.alpha == 1f) return
            quickActionScroll.animate().cancel()
            quickActionScroll.visibility = View.VISIBLE
            quickActionScroll.animate()
                .alpha(1f)
                .setDuration(120L)
                .setListener(null)
                .start()
        }

        fun hideQuickActionBar(animated: Boolean = true) {
            if (quickActionScroll.visibility == View.GONE) return
            quickActionScroll.animate().cancel()
            if (animated) {
                quickActionScroll.animate()
                    .alpha(0f)
                    .setDuration(80L)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            quickActionScroll.visibility = View.GONE
                        }
                    })
                    .start()
            } else {
                quickActionScroll.alpha = 0f
                quickActionScroll.visibility = View.GONE
            }
        }

        var updateThinkingChipsVisibility: ((Boolean) -> Unit)? = null

        val queryEt = SelectionAwareEditText(this).apply {
            hint = if (isCallingAi) (if (isEn) "AI is thinking..." else "AI đang xử lý...") else (if (isEn) "Ask AI or command..." else "Nhập lệnh hoặc câu hỏi...")
            setHintTextColor(Color.parseColor("#71717A"))
            setTextColor(Color.WHITE)
            textSize = 12.5f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#18181B"))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            maxLines = 4
            minLines = 1
            isSingleLine = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isEnabled = !isCallingAi
            highlightColor = Color.parseColor("#4D38BDF8")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isForceDarkAllowed = false
            }

            customSelectionActionModeCallback = suppressActionModeCallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                customInsertionActionModeCallback = suppressActionModeCallback
            }

            setOnLongClickListener {
                if (!isEnabled || isCallingAi) return@setOnLongClickListener false
                if (FloatingBubbleTextSelectionHelper.shouldConsumeLongClickForInstantPaste(text)) {
                    pasteFromClipboard(this)
                } else {
                    false
                }
            }

            setOnFocusChangeListener { _, hasFocus ->
                updateThinkingChipsVisibility?.invoke(true)
                if (hasFocus) {
                    scrollView.postDelayed({
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }, 200L)
                } else {
                    hideQuickActionBar(animated = false)
                }
            }
        }

        updateThinkingChipsVisibility = { animated ->
            val shouldShow = FloatingBubbleVoiceHelper.shouldShowThinkingChips(
                hasFocus = queryEt.hasFocus(),
                text = queryEt.text
            )
            if (shouldShow) {
                if (!(thinkingChipRow.visibility == View.VISIBLE && thinkingChipRow.alpha == 1f)) {
                    thinkingChipRow.animate().cancel()
                    thinkingChipRow.visibility = View.VISIBLE
                    if (animated) {
                        thinkingChipRow.animate()
                            .alpha(1f)
                            .setDuration(120L)
                            .setListener(null)
                            .start()
                    } else {
                        thinkingChipRow.alpha = 1f
                    }
                }
            } else {
                if (thinkingChipRow.visibility != View.GONE) {
                    thinkingChipRow.animate().cancel()
                    if (animated) {
                        thinkingChipRow.animate()
                            .alpha(0f)
                            .setDuration(100L)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    thinkingChipRow.visibility = View.GONE
                                }
                            })
                            .start()
                    } else {
                        thinkingChipRow.alpha = 0f
                        thinkingChipRow.visibility = View.GONE
                    }
                }
            }
        }

        queryEt.onSelectionChangedListener = { selStart, selEnd ->
            if (!queryEt.isEnabled || isCallingAi) {
                hideQuickActionBar(animated = false)
            } else if (selStart != selEnd) {
                showQuickActionBar()
            } else {
                hideQuickActionBar(animated = true)
            }
        }

        val quickActionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E627272A"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        fun createActionPill(iconEmoji: String, title: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = "$iconEmoji $title"
                textSize = 11.5f
                setTextColor(Color.parseColor("#F4F4F5"))
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isSingleLine = true
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                isClickable = true
                isFocusable = false
                addMicroPressEffect()
                setOnClickListener {
                    onClick()
                }
            }
        }

        fun createPillDivider(): View {
            return View(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#26FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(1), dpToPx(14)).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
            }
        }

        val pastePill = createActionPill("📋", if (isEn) "Paste" else "Dán") {
            if (!queryEt.isEnabled || isCallingAi) return@createActionPill
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            val clipText = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.coerceToText(this@FloatingBubbleService)?.toString()
            } else {
                null
            }
            if (!clipText.isNullOrEmpty()) {
                queryEt.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val editable = queryEt.text
                if (editable != null) {
                    val (start, end) = FloatingBubbleTextSelectionHelper.resolvePasteRange(
                        textLength = editable.length,
                        selStart = queryEt.selectionStart,
                        selEnd = queryEt.selectionEnd
                    )
                    editable.replace(start, end, clipText)
                    val newCursor = FloatingBubbleTextSelectionHelper.resolveNewCursorPosition(
                        start = start,
                        insertedLength = clipText.length,
                        totalLength = editable.length
                    )
                    queryEt.setSelection(newCursor)
                }
                Toast.makeText(this@FloatingBubbleService, if (isEn) "✓ Pasted" else "✓ Đã dán", Toast.LENGTH_SHORT).show()
                hideQuickActionBar(animated = true)
            } else {
                Toast.makeText(this@FloatingBubbleService, if (isEn) "Clipboard is empty" else "Bộ nhớ tạm đang trống", Toast.LENGTH_SHORT).show()
            }
        }

        val copyPill = createActionPill("📑", if (isEn) "Copy" else "Sao chép") {
            val editable = queryEt.text
            if (editable.isNullOrEmpty()) {
                Toast.makeText(this@FloatingBubbleService, if (isEn) "No content to copy" else "Không có nội dung để sao chép", Toast.LENGTH_SHORT).show()
                return@createActionPill
            }
            val textToCopy = FloatingBubbleTextSelectionHelper.resolveCopyText(
                text = editable,
                selStart = queryEt.selectionStart,
                selEnd = queryEt.selectionEnd
            )
            if (textToCopy.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("text", textToCopy)
                clipboard?.setPrimaryClip(clip)
                queryEt.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                Toast.makeText(this@FloatingBubbleService, if (isEn) "✓ Copied" else "✓ Đã chép", Toast.LENGTH_SHORT).show()
            }
        }

        val cutPill = createActionPill("✂️", if (isEn) "Cut" else "Cắt") {
            if (!queryEt.isEnabled || isCallingAi) return@createActionPill
            val editable = queryEt.text
            if (editable.isNullOrEmpty()) {
                Toast.makeText(this@FloatingBubbleService, if (isEn) "No content to cut" else "Không có nội dung để cắt", Toast.LENGTH_SHORT).show()
                return@createActionPill
            }
            val cutRange = FloatingBubbleTextSelectionHelper.resolveCutRange(
                text = editable,
                selStart = queryEt.selectionStart,
                selEnd = queryEt.selectionEnd
            )
            if (cutRange != null) {
                val (start, end) = cutRange
                val textToCut = editable.substring(start, end)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("text", textToCut)
                clipboard?.setPrimaryClip(clip)
                editable.delete(start, end)
                queryEt.setSelection(start)
                queryEt.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                Toast.makeText(this@FloatingBubbleService, if (isEn) "✓ Cut" else "✓ Đã cắt", Toast.LENGTH_SHORT).show()
                hideQuickActionBar(animated = true)
            } else {
                Toast.makeText(this@FloatingBubbleService, if (isEn) "No text selected to cut" else "Chưa chọn đoạn chữ cần cắt", Toast.LENGTH_SHORT).show()
            }
        }

        val selectAllPill = createActionPill("🔘", if (isEn) "Select All" else "Chọn tất cả") {
            if (!queryEt.isEnabled || isCallingAi) return@createActionPill
            if (!queryEt.text.isNullOrEmpty()) {
                queryEt.requestFocus()
                queryEt.selectAll()
                queryEt.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        quickActionBar.addView(pastePill)
        quickActionBar.addView(createPillDivider())
        quickActionBar.addView(copyPill)
        quickActionBar.addView(createPillDivider())
        quickActionBar.addView(cutPill)
        quickActionBar.addView(createPillDivider())
        quickActionBar.addView(selectAllPill)
        quickActionScroll.addView(quickActionBar)

        inputCard.addView(quickActionScroll)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        inputRow.addView(queryEt)

        var sendBtnRef: TextView? = null

        fun updateSendBtnState(hasText: Boolean) {
            val btn = sendBtnRef ?: return
            if (isRecordingVoice) return
            btn.isClickable = !isCallingAi
            val targetState = when {
                isCallingAi -> SendBtnState.CALLING_AI
                hasText -> SendBtnState.HAS_TEXT
                else -> SendBtnState.EMPTY
            }
            if (targetState == currentSendBtnState) return
            currentSendBtnState = targetState

            btn.setPadding(0, 0, 0, 0)
            btn.setCompoundDrawables(null, null, null, null)
            when (targetState) {
                SendBtnState.CALLING_AI -> {
                    btn.text = "..."
                    btn.setTextColor(Color.parseColor("#94A3B8"))
                    btn.background = sendBtnBgCallingAi
                }
                SendBtnState.HAS_TEXT -> {
                    btn.text = if (isEn) "Send" else "Gửi"
                    btn.setTextColor(Color.parseColor("#0F172A"))
                    btn.background = sendBtnBgActive
                }
                SendBtnState.EMPTY -> {
                    btn.text = if (isEn) "Send" else "Gửi"
                    btn.setTextColor(Color.parseColor("#94A3B8"))
                    btn.background = sendBtnBgInactive
                }
            }
        }

        queryEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSendBtnState(!s.isNullOrBlank())
                updateThinkingChipsVisibility?.invoke(true)
                if (queryEt.selectionStart == queryEt.selectionEnd) {
                    hideQuickActionBar(animated = false)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val sendBtn = TextView(this).apply {
            text = if (isCallingAi) "..." else (if (isEn) "Send" else "Gửi")
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#27272A"))
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(56), dpToPx(36)).apply {
                leftMargin = dpToPx(8)
                bottomMargin = dpToPx(1)
            }
            isClickable = !isCallingAi
        }
        sendBtnRef = sendBtn
        updateSendBtnState(false)

        lateinit var executeAiCall: (String) -> Unit
        executeAiCall = { queryText ->
            if (!isCallingAi) {
                isCallingAi = true
                activeStreamingJob?.cancel()

                val promptToSend = queryText.ifBlank { if (isEn) "Summarize recent key tasks and messages" else "Tóm tắt các tin nhắn quan trọng gần đây" }
                queryEt.setText("")
                queryEt.clearFocus()
                queryEt.isEnabled = false
                queryEt.hint = if (isEn) "AI is thinking..." else "AI đang xử lý..."
                hideQuickActionBar(animated = false)
                updateThinkingChipsVisibility?.invoke(false)
                updateSendBtnState(false)

                sendBtnRef?.text = "..."
                sendBtnRef?.isClickable = false
                sendBtnRef?.setTextColor(Color.parseColor("#94A3B8"))
                sendBtnRef?.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#3327272A"))
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
                }

                taskBoardController.clearEmptyBox()

                val targetAdviceTv = adviceTv
                val targetNotesContainer = notesContainer
                val targetSec1Box = sec1Box

                targetSec1Box.visibility = View.VISIBLE
                targetNotesContainer.removeAllViews()
                targetAdviceTv.isClickable = false
                targetAdviceTv.text = if (isEn) "⏳ Connecting to AI..." else "⏳ Đang kết nối AI..."
                startTextShimmer(targetAdviceTv)

                val globalRequestStartedAt = System.currentTimeMillis()
                val requestStartedAt = AtomicLong(System.currentTimeMillis())
                val hasReceivedFirstToken = AtomicBoolean(false)
                val lastTokenReceivedAt = AtomicLong(0L)
                val isWatchdogTimedOut = AtomicBoolean(false)

                activeWatchdogJob?.cancel()
                activeWatchdogJob = serviceScope.launch(Dispatchers.Main) {
                    while (isActive) {
                        delay(500)
                        val now = System.currentTimeMillis()
                        val totalElapsed = now - globalRequestStartedAt
                        if (totalElapsed >= GLOBAL_REQUEST_TIMEOUT_MS) {
                            isWatchdogTimedOut.set(true)
                            activeStreamingJob?.cancel(CancellationException("GLOBAL_REQUEST_TIMEOUT"))
                            break
                        }
                        if (hasReceivedFirstToken.get()) {
                            val elapsedSinceLastToken = now - lastTokenReceivedAt.get()
                            if (elapsedSinceLastToken >= SSE_WATCHDOG_TIMEOUT_MS) {
                                isWatchdogTimedOut.set(true)
                                activeStreamingJob?.cancel(CancellationException("SSE_STREAM_WATCHDOG_TIMEOUT"))
                                break
                            }
                        } else {
                            val elapsedSinceStart = now - requestStartedAt.get()
                            if (elapsedSinceStart >= INITIAL_CONNECTION_TIMEOUT_MS) {
                                isWatchdogTimedOut.set(true)
                                activeStreamingJob?.cancel(CancellationException("INITIAL_CONNECTION_TIMEOUT"))
                                break
                            }
                        }
                    }
                }

                activeStreamingJob = serviceScope.launch(Dispatchers.Main) {
                    var streamThrottleJob: Job? = null
                    try {
                        val allSessions = prefs.loadSessions()
                        val selectedSession = prefs.getSelectedSession()
                        val currentSession = com.orca.orcaauto.domain.assistant.SessionSummaryIntentDetector.resolveTargetSession(
                            promptToSend,
                            allSessions,
                            selectedSession
                        )
                        if (currentSession.id != selectedSession.id) {
                            prefs.setSelectedSessionId(currentSession.id)
                            bubbleRootView?.let { root ->
                                renderSessionChips(sessionsLayout, addSessionBtn, root)
                            }
                        }
                        val accumulatedLive = StringBuilder()
                        var lastStreamUpdateMs = 0L
                        val latestLiveText = AtomicReference("")

                        val result = withContext(Dispatchers.IO) {
                            LiveAiChatHelper.summarizeSession(
                                context = this@FloatingBubbleService,
                                session = currentSession,
                                userQueryOrInstruction = promptToSend,
                                onTokenDelta = { delta ->
                                    hasReceivedFirstToken.set(true)
                                    lastTokenReceivedAt.set(System.currentTimeMillis())
                                    val raw = synchronized(accumulatedLive) {
                                        accumulatedLive.append(delta)
                                        accumulatedLive.toString()
                                    }
                                    val liveSnippet = extractLiveStreamSnippet(raw)
                                    val cleanLive = CommandStripper.cleanLive(liveSnippet)
                                    val displayText = if (cleanLive.isNotBlank()) cleanLive else "..."
                                    latestLiveText.set(displayText)

                                    val now = android.os.SystemClock.uptimeMillis()
                                    if (now - lastStreamUpdateMs >= 70L) {
                                        lastStreamUpdateMs = now
                                        streamThrottleJob?.cancel()
                                        streamThrottleJob = null
                                        serviceScope.launch(Dispatchers.Main) {
                                            stopTextShimmer(targetAdviceTv)
                                            targetAdviceTv.text = "${latestLiveText.get()} ▌"
                                        }
                                    } else if (streamThrottleJob == null || !streamThrottleJob!!.isActive) {
                                        streamThrottleJob = serviceScope.launch(Dispatchers.Main) {
                                            delay(70L)
                                            stopTextShimmer(targetAdviceTv)
                                            targetAdviceTv.text = "${latestLiveText.get()} ▌"
                                        }
                                    }
                                },
                                onStatusUpdate = { statusMsg ->
                                    requestStartedAt.set(System.currentTimeMillis())
                                    hasReceivedFirstToken.set(false)
                                    lastTokenReceivedAt.set(0L)
                                    synchronized(accumulatedLive) {
                                        accumulatedLive.setLength(0)
                                    }
                                    serviceScope.launch(Dispatchers.Main) {
                                        targetAdviceTv.text = statusMsg
                                        startTextShimmer(targetAdviceTv)
                                    }
                                }
                            )
                        }
                        streamThrottleJob?.cancel()
                        streamThrottleJob = null
                        stopTextShimmer(targetAdviceTv)
                        if (result.isSuccess) {
                            val (cleanSummary, usedKey, shownCards) = result.getOrThrow()
                            val (parsed, cleanAdvice) = withContext(Dispatchers.Default) {
                                val p = SummaryParser.parse(cleanSummary)
                                val a = SummaryParser.cleanAdviceText(p.conciseAdvice)
                                p to a
                            }
                            updateSummaryTextAndParseEagerly(cleanSummary, preParsed = parsed)
                            activeChatAnswer = null
                            latestModelInfo = "${usedKey.provider.displayName} • ${usedKey.modelName}"
                            sessionSummaryCache[currentSession.id] = Pair(cleanSummary, latestModelInfo)
                            modelTv.text = latestModelInfo

                            targetAdviceTv.text = if (cleanAdvice.isNotBlank()) cleanAdvice else parsed.conciseAdvice.ifBlank { cleanSummary }

                            dialogRenderer.renderLocalNoteCardsInBubble(targetNotesContainer, shownCards)
                            taskBoardController.refreshTasksBoardOnly()
                        } else {
                            val err = result.exceptionOrNull()
                            val (title, detail) = buildAiErrorMessage(this@FloatingBubbleService, err, isWatchdogTimedOut.get())
                            renderAiErrorUi(targetAdviceTv, targetNotesContainer, title, detail, promptToSend, executeAiCall)
                        }
                    } catch (e: CancellationException) {
                        stopTextShimmer(targetAdviceTv)
                        if (!isWatchdogTimedOut.get()) {
                            throw e
                        }
                        val (title, detail) = buildAiErrorMessage(this@FloatingBubbleService, e, true)
                        renderAiErrorUi(targetAdviceTv, targetNotesContainer, title, detail, promptToSend, executeAiCall)
                    } catch (t: Throwable) {
                        stopTextShimmer(targetAdviceTv)
                        val (title, detail) = buildAiErrorMessage(this@FloatingBubbleService, t, isWatchdogTimedOut.get())
                        renderAiErrorUi(targetAdviceTv, targetNotesContainer, title, detail, promptToSend, executeAiCall)
                    } finally {
                        streamThrottleJob?.cancel()
                        streamThrottleJob = null
                        stopTextShimmer(targetAdviceTv)
                        activeWatchdogJob?.cancel()
                        activeWatchdogJob = null
                        isCallingAi = false
                        queryEt.isEnabled = true
                        queryEt.hint = if (isEn) "Ask AI or command..." else "Nhập lệnh hoặc câu hỏi..."
                        sendBtnRef?.isClickable = true
                        currentSendBtnState = null
                        updateSendBtnState(queryEt.text?.isNotBlank() == true)
                        updateThinkingChipsVisibility?.invoke(false)
                        activeStreamingJob = null
                    }
                }
            }
        }

        var downRawX = 0f
        var downRawY = 0f
        var isHoldTriggered = false
        var isSlideCancelled = false
        var hasCrossedThreshold = false
        var isSlopExceeded = false
        var initialRecordedText = ""
        val cancelThresholdPx = dpToPx(50).toFloat()
        val touchSlopPx = dpToPx(8).toFloat()

        fun cleanupVoiceSession(revertText: Boolean, destroyRecognizer: Boolean = false) {
            activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
            activeHoldRunnable = null
            try {
                if (revertText) {
                    activeSpeechRecognizer?.cancel()
                    activeSpeechRecognizer?.destroy()
                    activeSpeechRecognizer = null
                } else if (destroyRecognizer) {
                    activeSpeechRecognizer?.destroy()
                    activeSpeechRecognizer = null
                } else {
                    activeSpeechRecognizer?.stopListening()
                }
            } catch (_: Exception) {}
            if (revertText || destroyRecognizer) {
                activeSpeechRecognizer = null
            }
            isRecordingVoice = false

            if (revertText) {
                queryEt.setText(initialRecordedText)
                queryEt.setSelection(initialRecordedText.length)
            }

            soundVisualizerView.stopVisualizer()
            voiceVisualizerBubble.animate().cancel()
            voiceVisualizerBubble.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(120L)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        voiceVisualizerBubble.visibility = View.GONE
                        voiceVisualizerBubble.scaleX = 1f
                        voiceVisualizerBubble.scaleY = 1f
                    }
                })
                .start()

            sendBtn.setPadding(0, 0, 0, 0)
            sendBtn.setCompoundDrawables(null, null, null, null)
            sendBtn.parent?.requestDisallowInterceptTouchEvent(false)
            sendBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100L).start()
            currentSendBtnState = null
            updateSendBtnState(!queryEt.text.isNullOrBlank())

            isHoldTriggered = false
            isSlideCancelled = false
            hasCrossedThreshold = false
            isSlopExceeded = false
        }

        cancelActiveVoiceSession = {
            cleanupVoiceSession(revertText = true, destroyRecognizer = true)
        }

        sendBtn.setOnTouchListener { v, event ->
            if (isCallingAi) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isHoldTriggered = false
                    isSlideCancelled = false
                    hasCrossedThreshold = false
                    isSlopExceeded = false
                    initialRecordedText = queryEt.text.toString()

                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).start()

                    activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                    val r = Runnable {
                        if (isCallingAi) return@Runnable

                        // Check RECORD_AUDIO permission
                        val hasMic = ContextCompat.checkSelfPermission(
                            this@FloatingBubbleService,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (!hasMic) {
                            isSlopExceeded = true
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                            showFloatingConfirmationDialog(
                                title = if (isEn) "Microphone Permission Required" else "Cần quyền Micro",
                                message = if (isEn) "To recognize voice and auto-fill prompts, Orca requires Microphone permission. Please grant it in Settings." else "Để nhận diện giọng nói tiếng Việt và tự động điền câu lệnh, Orca cần quyền Microphone. Vui lòng cấp quyền trong Cài đặt.",
                                confirmText = if (isEn) "Open Settings" else "Mở Cài đặt",
                                confirmColor = Color.parseColor("#38BDF8")
                            ) {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            return@Runnable
                        }

                        if (!SpeechRecognizer.isRecognitionAvailable(this@FloatingBubbleService)) {
                            isSlopExceeded = true
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                            Toast.makeText(this@FloatingBubbleService, if (isEn) "Voice recognition not supported on device" else "Thiết bị chưa hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show()
                            return@Runnable
                        }

                        // Checks passed -> Now officially trigger hold-to-talk
                        isHoldTriggered = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        // Trigger haptic feedback
                        sendBtn.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                        // Morph Send button to Mic
                        sendBtn.text = ""
                        val micDrawable = ContextCompat.getDrawable(this@FloatingBubbleService, R.drawable.ic_mic)?.mutate()?.apply {
                            val size = dpToPx(18)
                            setBounds(0, 0, size, size)
                            setTint(Color.parseColor("#FFFFFF"))
                        }
                        val pad = (sendBtn.width - dpToPx(18)) / 2
                        val horizontalPadding = if (pad > 0) pad else dpToPx(19)
                        sendBtn.setPadding(horizontalPadding, 0, 0, 0)
                        sendBtn.setCompoundDrawables(micDrawable, null, null, null)
                        sendBtn.setTextColor(Color.WHITE)
                        sendBtn.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1E3A5F"))
                            cornerRadius = dpToPx(18).toFloat()
                            setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
                        }
                        sendBtn.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()

                        // Reveal voice visualizer bubble
                        voiceVisualizerBubble.animate().cancel()
                        voiceVisualizerBubble.visibility = View.VISIBLE
                        voiceVisualizerBubble.alpha = 0f
                        voiceVisualizerBubble.scaleX = 0.95f
                        voiceVisualizerBubble.scaleY = 0.95f
                        voiceVisualizerBubble.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120L)
                            .setListener(null)
                            .start()

                        soundVisualizerView.startVisualizer()
                        soundVisualizerView.setCancelProgress(0f)
                        cancelHintTv.text = if (isEn) "‹ Slide left to cancel" else "‹ Trượt sang trái để huỷ"
                        cancelHintTv.setTextColor(Color.parseColor("#94A3B8"))

                        try {
                            activeSpeechRecognizer?.cancel()
                            activeSpeechRecognizer?.destroy()
                            activeSpeechRecognizer = null

                            val recognizer = SpeechRecognizer.createSpeechRecognizer(this@FloatingBubbleService)
                            activeSpeechRecognizer = recognizer

                            recognizer.setRecognitionListener(object : RecognitionListener {
                                override fun onReadyForSpeech(params: Bundle?) {}
                                override fun onBeginningOfSpeech() {}
                                override fun onRmsChanged(rmsdB: Float) {
                                    soundVisualizerView.setRmsDb(rmsdB)
                                }
                                override fun onBufferReceived(buffer: ByteArray?) {}
                                override fun onEndOfSpeech() {}
                                override fun onError(error: Int) {
                                    val message = when (error) {
                                        SpeechRecognizer.ERROR_NETWORK,
                                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> if (isEn) "Network connection error during voice recognition" else "Lỗi kết nối mạng khi nhận diện giọng nói"
                                        SpeechRecognizer.ERROR_AUDIO -> if (isEn) "Microphone audio recording error" else "Lỗi ghi âm từ microphone"
                                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> if (isEn) "Insufficient microphone permission" else "Chưa đủ quyền truy cập microphone"
                                        else -> null
                                    }
                                    if (message != null) {
                                        Toast.makeText(this@FloatingBubbleService, message, Toast.LENGTH_SHORT).show()
                                    }
                                    cleanupVoiceSession(revertText = false, destroyRecognizer = true)
                                }
                                override fun onResults(results: Bundle?) {
                                    if (!isSlideCancelled) {
                                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        val text = matches?.firstOrNull()?.trim()
                                        if (!text.isNullOrEmpty()) {
                                            val combined = FloatingBubbleVoiceHelper.combineTranscribedText(initialRecordedText, text)
                                            queryEt.setText(combined)
                                            queryEt.setSelection(combined.length)
                                        }
                                    }
                                    try {
                                        activeSpeechRecognizer?.destroy()
                                    } catch (_: Exception) {}
                                    activeSpeechRecognizer = null
                                }
                                override fun onPartialResults(partialResults: Bundle?) {
                                    if (!isSlideCancelled) {
                                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                        val text = matches?.firstOrNull()?.trim()
                                        if (!text.isNullOrEmpty()) {
                                            val combined = FloatingBubbleVoiceHelper.combineTranscribedText(initialRecordedText, text)
                                            queryEt.setText(combined)
                                            queryEt.setSelection(combined.length)
                                        }
                                    }
                                }
                                override fun onEvent(eventType: Int, params: Bundle?) {}
                            })

                            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                val langTag = if (isEn) "en-US" else "vi-VN"
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                            }
                            recognizer.startListening(speechIntent)
                            isRecordingVoice = true
                        } catch (e: Exception) {
                            Log.e("FloatingBubble", "Lỗi SpeechRecognizer: ${e.message}")
                            cleanupVoiceSession(revertText = false, destroyRecognizer = true)
                        }
                    }
                    activeHoldRunnable = r
                    bubbleLifecycleHandler.postDelayed(r, FloatingBubbleVoiceHelper.HOLD_THRESHOLD_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isHoldTriggered) {
                        val dx = Math.abs(event.rawX - downRawX)
                        val dy = Math.abs(event.rawY - downRawY)
                        if (dx > touchSlopPx || dy > touchSlopPx) {
                            isSlopExceeded = true
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                            activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                            activeHoldRunnable = null
                        }
                    } else if (isRecordingVoice) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        val dragProgress = FloatingBubbleVoiceHelper.calculateCancelProgress(
                            downRawX,
                            event.rawX,
                            cancelThresholdPx
                        )
                        val isExceeded = FloatingBubbleVoiceHelper.isCancelThresholdExceeded(
                            downRawX,
                            event.rawX,
                            cancelThresholdPx
                        )

                        soundVisualizerView.setCancelProgress(dragProgress)

                        if (FloatingBubbleVoiceHelper.didCrossCancelThreshold(hasCrossedThreshold, isExceeded)) {
                            hasCrossedThreshold = true
                            isSlideCancelled = true
                            sendBtn.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            voiceVisualizerBubble.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120L).start()
                            cancelHintTv.text = if (isEn) "✕ Release to cancel" else "✕ Thả để hủy"
                            cancelHintTv.setTextColor(Color.parseColor("#F87171"))
                        } else if (FloatingBubbleVoiceHelper.didRevertFromCancel(hasCrossedThreshold, isExceeded)) {
                            hasCrossedThreshold = false
                            isSlideCancelled = false
                            voiceVisualizerBubble.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120L).start()
                            cancelHintTv.text = if (isEn) "‹ Slide left to cancel" else "‹ Trượt sang trái để huỷ"
                            cancelHintTv.setTextColor(Color.parseColor("#94A3B8"))
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                    activeHoldRunnable = null

                    val action = FloatingBubbleVoiceHelper.resolveTouchAction(
                        isHoldTriggered = isHoldTriggered,
                        isSlideCancelled = isSlideCancelled,
                        isSlopExceeded = isSlopExceeded
                    )

                    when (action) {
                        FloatingBubbleVoiceHelper.TouchAction.TAP -> {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                            if (!isCallingAi) {
                                val q = queryEt.text.toString().trim()
                                if (q.isBlank()) {
                                    return@setOnTouchListener true
                                }
                                executeAiCall(q)
                            }
                        }
                        FloatingBubbleVoiceHelper.TouchAction.IGNORE -> {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                        }
                        FloatingBubbleVoiceHelper.TouchAction.HOLD_RELEASE_CANCEL -> {
                            sendBtn.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            cleanupVoiceSession(revertText = true, destroyRecognizer = true)
                        }
                        FloatingBubbleVoiceHelper.TouchAction.HOLD_RELEASE_COMMIT -> {
                            cleanupVoiceSession(revertText = false, destroyRecognizer = false)
                        }
                        else -> {
                            cleanupVoiceSession(revertText = false, destroyRecognizer = false)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    activeHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                    activeHoldRunnable = null
                    if (isHoldTriggered) {
                        cleanupVoiceSession(revertText = true, destroyRecognizer = true)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80L).start()
                    }
                    true
                }

                else -> false
            }
        }

        inputRow.addView(sendBtn)
        inputCard.addView(inputRow)
        cardLayout.addView(inputCard)

        return CardShellHolder(
            cardLayout = cardLayout,
            headerRow = headerRow,
            titleTv = titleTv,
            modelTv = modelTv,
            openAppBtn = openAppBtn,
            sessionsScroll = sessionsScroll,
            sessionsLayout = sessionsLayout,
            addSessionBtn = addSessionBtn,
            scrollView = scrollView,
            contentLayout = contentLayout,
            sec1Box = sec1Box,
            adviceTv = adviceTv,
            notesContainer = notesContainer,
            tasksBoardLayout = tasksBoardLayout,
            inputCard = inputCard,
            queryEt = queryEt,
            sendBtn = sendBtn,
            quickActionBar = quickActionScroll,
            thinkingChipRow = thinkingChipRow,
            voiceVisualizerBubble = voiceVisualizerBubble,
            soundVisualizerView = soundVisualizerView
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun expandFloatingCard(
        root: FrameLayout,
        animate: Boolean = true,
        tapTimestampNs: Long = SystemClock.elapsedRealtimeNanos()
    ) {
        UserPreferences.getInstance(this).setHasUnreadSummary(false)
        bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
        bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
        hideDismissTarget(immediate = true)
        currentAnimator?.cancel()
        isCollapsing = false
        updateShakeDetectorState()
        taskBoardController.resetDismissedTasks()
        currentSendBtnState = null

        // Tắt chuông báo thức nếu người dùng mở thẻ AI xem
        com.orca.orcaauto.receiver.AlarmRingtonePlayer.stop()

        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bubbleSize = dpToPx(44)

        // Enlarge expanded card: covers comfortable viewing area (~94% width, ~74% height for keyboard clearance)
        val cardWidth = (screenWidth * 0.94f).toInt().coerceAtMost(dpToPx(500))
        val cardHeight = (screenHeight * 0.74f).toInt().coerceAtMost(dpToPx(620))

        // Phase 1 (Frame 0): Keep activeBubbleView in root for continuous visual handoff (70ms fade out, 0 gap)
        activeBubbleView?.let { bubble ->
            val bubbleLp = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = lastCollapsedX
                topMargin = lastCollapsedY
            }
            bubble.layoutParams = bubbleLp
            bubble.animate()
                .alpha(0f)
                .setDuration(70)
                .withEndAction {
                    root.removeView(bubble)
                }
                .start()
        }

        // Expand root window to MATCH_PARENT
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
            } catch (_: Exception) {}
        }
        isExpanded = true
        updateShakeDetectorState()

        // Clean transparent backdrop
        root.setBackgroundColor(Color.TRANSPARENT)
        root.isClickable = false
        root.isFocusable = false
        root.alpha = 1.0f

        // Obtain Pre-Warmed Skeleton Shell or build immediately
        val shell = prewarmedCardShell ?: buildCardShell(cardWidth, cardHeight).also { prewarmedCardShell = it }
        val cardLayout = shell.cardLayout
        shell.quickActionBar.visibility = View.GONE
        shell.quickActionBar.alpha = 0f

        val shouldShowChips = FloatingBubbleVoiceHelper.shouldShowThinkingChips(
            hasFocus = shell.queryEt.hasFocus(),
            text = shell.queryEt.text
        )
        shell.thinkingChipRow?.visibility = if (shouldShowChips) View.VISIBLE else View.GONE
        shell.thinkingChipRow?.alpha = if (shouldShowChips) 1f else 0f
        shell.voiceVisualizerBubble?.visibility = View.GONE
        shell.voiceVisualizerBubble?.alpha = 0f
        shell.soundVisualizerView?.stopVisualizer()

        currentCardLayoutRef = cardLayout
        taskBoardController.attachBoard(shell.tasksBoardLayout, cardLayout)

        // Outside-tap detector with long-press abort:
        // - Closes on finger release (ACTION_UP), not on press-down (ACTION_DOWN)
        // - Holding down for >= 450ms cancels the close intent (haptic feedback confirms abort)
        val cardRect = Rect()
        var outsideDownStarted = false
        var outsideDownTime = 0L
        var isOutsideHoldAborted = false

        fun cleanupOutsideTouch() {
            activeOutsideHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
            activeOutsideHoldRunnable = null
            outsideDownStarted = false
            isOutsideHoldAborted = false
            outsideDownTime = 0L
        }

        root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cardLayout.getGlobalVisibleRect(cardRect)
                    val isOutside = !cardRect.contains(event.rawX.toInt(), event.rawY.toInt())
                    if (isOutside) {
                        outsideDownStarted = true
                        outsideDownTime = System.currentTimeMillis()
                        isOutsideHoldAborted = false

                        activeOutsideHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                        val r = Runnable {
                            if (outsideDownStarted) {
                                isOutsideHoldAborted = true
                                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        }
                        activeOutsideHoldRunnable = r
                        bubbleLifecycleHandler.postDelayed(r, FloatingBubbleVoiceHelper.OUTSIDE_HOLD_ABORT_THRESHOLD_MS)
                        return@setOnTouchListener true
                    }
                    cleanupOutsideTouch()
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (outsideDownStarted) {
                        cardLayout.getGlobalVisibleRect(cardRect)
                        val isInsideCard = cardRect.contains(event.rawX.toInt(), event.rawY.toInt())
                        if (isInsideCard) {
                            // User dragged finger back into the card -> abort closing
                            isOutsideHoldAborted = true
                            activeOutsideHoldRunnable?.let { bubbleLifecycleHandler.removeCallbacks(it) }
                            activeOutsideHoldRunnable = null
                        }
                        return@setOnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_UP -> {
                    if (outsideDownStarted) {
                        val duration = System.currentTimeMillis() - outsideDownTime
                        cardLayout.getGlobalVisibleRect(cardRect)
                        val isReleaseOutside = !cardRect.contains(event.rawX.toInt(), event.rawY.toInt())
                        val shouldCollapse = FloatingBubbleVoiceHelper.shouldCollapseOnOutsideRelease(
                            downStartedOutside = true,
                            isReleaseOutside = isReleaseOutside,
                            pressDurationMs = duration,
                            isHoldAborted = isOutsideHoldAborted
                        )
                        cleanupOutsideTouch()
                        if (shouldCollapse) {
                            collapseExpandedCard(root)
                        }
                        return@setOnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_CANCEL -> {
                    cleanupOutsideTouch()
                    false
                }

                else -> false
            }
        }

        // Detach cardLayout if already attached to any parent
        (cardLayout.parent as? ViewGroup)?.removeView(cardLayout)
        root.addView(cardLayout)
        cardLayout.bringToFront()

        // Update session chips and model header in skeleton
        renderSessionChips(shell.sessionsLayout, shell.addSessionBtn, root)
        shell.modelTv.text = if (latestModelInfo.isNotBlank()) latestModelInfo else "Trợ lý AI điều phối thông minh"

        // Populate Advice immediately from cachedParsedSummary (0.0ms pointer dereference)
        val parsed = cachedParsedSummary ?: SummaryParser.parse(latestSummaryText).also { cachedParsedSummary = it }
        val cleanAdvice = SummaryParser.cleanAdviceText(parsed.conciseAdvice)
        val adviceText = if (cleanAdvice.isNotBlank()) cleanAdvice else parsed.conciseAdvice.ifBlank { latestSummaryText }
        shell.adviceTv.text = adviceText
        shell.adviceTv.isClickable = true
        shell.sec1Box.visibility = if (adviceText.isNotBlank()) View.VISIBLE else View.GONE

        // Telemetry onPreDraw listener: tap-to-render latency
        var hasLoggedTelemetry = false
        cardLayout.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!hasLoggedTelemetry) {
                    hasLoggedTelemetry = true
                    cardLayout.viewTreeObserver.removeOnPreDrawListener(this)
                    val renderTimestampNs = SystemClock.elapsedRealtimeNanos()
                    val latencyMs = (renderTimestampNs - tapTimestampNs) / 1_000_000.0
                    Log.i(TAG, "⚡ [Telemetry] Bubble Tap-to-Render Latency: ${"%.2f".format(Locale.US, latencyMs)}ms (Frame 0 ready)")
                }
                return true
            }
        })

        if (animate) {
            // Immediate Frame 0 reveal (NO post {}, NO alpha = 0f, alpha = 1.0f immediately)
            cardLayout.alpha = 1.0f
            cardLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)

            val deltaX = (lastCollapsedX + bubbleSize / 2f) - (screenWidth / 2f)
            val deltaY = (lastCollapsedY + bubbleSize / 2f) - (screenHeight / 2f)
            cardLayout.translationX = deltaX
            cardLayout.translationY = deltaY
            cardLayout.scaleX = 0.12f
            cardLayout.scaleY = 0.12f

            // Continuous Morphing: 180ms, PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
            cardLayout.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(180)
                .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f))
                .withEndAction {
                    cardLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                    // Phase 2: Post-Animation Mount task rows in withEndAction
                    taskBoardController.populateTaskBoard(shell.tasksBoardLayout, cardLayout, hasAdvice = shell.sec1Box.visibility == View.VISIBLE)
                }
                .start()
        } else {
            cardLayout.alpha = 1.0f
            cardLayout.translationX = 0f
            cardLayout.translationY = 0f
            cardLayout.scaleX = 1.0f
            cardLayout.scaleY = 1.0f
            taskBoardController.populateTaskBoard(shell.tasksBoardLayout, cardLayout, hasAdvice = shell.sec1Box.visibility == View.VISIBLE)
        }
    }







    private fun showFloatingConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        confirmColor: Int = Color.WHITE,
        onConfirm: () -> Unit
    ) {
        dialogRenderer.showFloatingConfirmationDialog(
            title = title,
            message = message,
            confirmText = confirmText,
            confirmColor = confirmColor,
            onConfirm = onConfirm
        )
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
        prewarmedCardShell = null
        prewarmedCardShell = null
        val dm = resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val bubbleSize = dpToPx(44)
        val minY = dpToPx(28)
        val maxY = (screenHeight - bubbleSize - dpToPx(32)).coerceAtLeast(minY)

        lastCollapsedY = lastCollapsedY.coerceIn(minY, maxY)
        val isLeft = isAttachedLeft
        val halfBubble = bubbleSize / 2
        lastCollapsedX = when (currentBubbleState) {
            BubbleState.STATE_1_ACTIVE -> if (isLeft) dpToPx(8) else (screenWidth - bubbleSize - dpToPx(8))
            BubbleState.STATE_2_DIMMED -> if (isLeft) -halfBubble else (screenWidth - halfBubble)
            BubbleState.STATE_3_HIDDEN -> if (isLeft) -bubbleSize else screenWidth
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
        activeStreamingJob?.cancel()
        activeStreamingJob = null
        activeWatchdogJob?.cancel()
        activeWatchdogJob = null
        sessionSwitchJob?.cancel()
        sessionSwitchJob = null
        activeSummaryParseJob?.cancel()
        activeSummaryParseJob = null
        cleanupVoiceRecording()
        cancelActiveVoiceSession = null
        try {
            com.orca.orcaauto.domain.voice.VoiceBriefingManager.getInstance(this).stop()
        } catch (_: Exception) {}
        serviceScope.cancel()
        bubbleLifecycleHandler.removeCallbacks(toState2Runnable)
        bubbleLifecycleHandler.removeCallbacks(toState3Runnable)
        currentAnimator?.cancel()
        currentAnimator = null
        sessionSummaryCache.clear()
        wallpaperManager.release()
        dialogRenderer.dismissActiveDialogs()
        taskBoardController.clearSessionTransientState()
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

        cleanupEditorHandlesAndToolbar(prewarmedCardShell)
        prewarmedCardShell = null

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
        activeBubbleView = null
        unreadDotView = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    /**
     * Reads clipboard directly for TYPE_APPLICATION_OVERLAY EditText fields, where Android
     * often suppresses the native Activity-bound floating Paste popup toolbar or throws BadTokenException
     * on OEM ROMs (Xiaomi/Oppo/Samsung). Performs haptic feedback, cursor insertion, and Toast "✓ Đã dán".
     */
    private fun pasteFromClipboard(target: EditText): Boolean {
        return try {
            target.requestFocus()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            val clipText = if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0)?.coerceToText(this)?.toString()
            } else {
                null
            }
            if (!clipText.isNullOrEmpty()) {
                target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val editable = target.text ?: return false
                val rawStart = target.selectionStart.coerceAtLeast(0)
                val rawEnd = target.selectionEnd.coerceAtLeast(0)
                val start = minOf(rawStart, rawEnd).coerceAtMost(editable.length)
                val end = maxOf(rawStart, rawEnd).coerceAtMost(editable.length)
                editable.replace(start, end, clipText)
                val newCursor = (start + clipText.length).coerceAtMost(editable.length)
                target.setSelection(newCursor)
                val isEn = try { UserPreferences.getInstance(this).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH } catch (_: Exception) { false }
                Toast.makeText(this, if (isEn) "✓ Pasted" else "✓ Đã dán", Toast.LENGTH_SHORT).show()
                true
            } else {
                val isEn = try { UserPreferences.getInstance(this).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH } catch (_: Exception) { false }
                Toast.makeText(this, if (isEn) "Clipboard is empty" else "Bộ nhớ tạm đang trống", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi dán từ clipboard: ${e.message}", e)
            true
        }
    }

    companion object {
        private const val TAG = "FloatingBubble"
        const val GLOBAL_REQUEST_TIMEOUT_MS = 120_000L
        const val SSE_WATCHDOG_TIMEOUT_MS = 28_000L
        const val INITIAL_CONNECTION_TIMEOUT_MS = 95_000L
        const val TAG_UNREAD_RED_DOT = "tag_unread_red_dot"
        const val ACTION_WAKE_TO_STATE_1 = "com.orca.orcaauto.action.WAKE_TO_STATE_1"
        const val EXTRA_WAKE_TO_STATE_1 = "extra_wake_to_state_1"
        const val EXTRA_SUMMARY_CONTENT = "extra_summary_content"
        const val EXTRA_MODEL_INFO = "extra_model_info"
        const val EXTRA_SESSION_ID = "extra_session_id"

        fun isPhysicalNetworkOffline(context: Context?, t: Throwable?): Boolean {
            if (context != null) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    if (cm != null) {
                        val activeNet = cm.activeNetwork
                        if (activeNet == null) return true
                        val caps = cm.getNetworkCapabilities(activeNet)
                        if (caps == null ||
                            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        ) {
                            return true
                        }
                    }
                } catch (_: Throwable) {}
            }
            var curr = t
            while (curr != null) {
                if (curr is java.net.UnknownHostException ||
                    curr.message?.contains("Unable to resolve host", ignoreCase = true) == true
                ) {
                    return true
                }
                curr = curr.cause
            }
            return false
        }

        fun buildAiErrorMessage(context: Context?, t: Throwable?, isWatchdogTimedOut: Boolean): Pair<String, String> {
            val isEn = if (context != null) {
                try {
                    UserPreferences.getInstance(context).getAppLanguage() == com.orca.orcaauto.ui.locale.AppLanguage.ENGLISH
                } catch (_: Exception) {
                    false
                }
            } else false

            // 1. Device is physically offline or DNS failure
            if (isPhysicalNetworkOffline(context, t)) {
                return if (isEn) {
                    Pair("No network connection", "Device is offline. Tap to retry.")
                } else {
                    Pair("Mất kết nối mạng", "Thiết bị chưa có kết nối Internet. Chạm để thử lại.")
                }
            }

            // 2. Rate limit / Quota exhausted
            if (RateLimitDetector.isRateLimit(t)) {
                return if (isEn) {
                    Pair("AI Quota Exceeded (HTTP 429)", "AI account or all fallback keys have hit the quota limit. Please add a new Key in Settings.")
                } else {
                    Pair("Hết hạn mức AI (HTTP 429)", "Tài khoản AI hoặc toàn bộ Key dự phòng đã chạm hạn mức. Vui lòng thêm Key mới trong Cài đặt.")
                }
            }

            val msg = t?.message.orEmpty()

            // 2b. Global timeout ceiling across all failovers (120s)
            if (msg.contains("GLOBAL_REQUEST_TIMEOUT", ignoreCase = true)) {
                return if (isEn) {
                    Pair("Request timed out (>120s)", "AI session exceeded 120 seconds. Tap to retry.")
                } else {
                    Pair("Tổng thời gian chờ quá lâu (>120s)", "Toàn bộ phiên kết nối AI vượt quá 120 giây. Chạm để thử lại.")
                }
            }

            // 3. All keys in fallback pool failed
            if (msg.contains("danh sách dự phòng đều thất bại", ignoreCase = true) ||
                msg.contains("đều phản hồi quá lâu", ignoreCase = true)
            ) {
                return if (isEn) {
                    Pair("All API Keys Failed", "All keys in the fallback pool failed or timed out. Please check your keys in Settings.")
                } else {
                    Pair("Toàn bộ API Key đều lỗi", "Tất cả các Key trong danh sách dự phòng đều thất bại hoặc phản hồi quá lâu. Vui lòng kiểm tra lại Key trong Cài đặt.")
                }
            }

            // 4. Watchdog connection window timeout (90s)
            if (isWatchdogTimedOut || msg.contains("INITIAL_CONNECTION_TIMEOUT", ignoreCase = true)) {
                return if (isEn) {
                    Pair("Server response timeout (>90s)", "No response from AI after 90 seconds. Tap to retry.")
                } else {
                    Pair("Máy chủ phản hồi quá lâu (>90s)", "Không nhận được phản hồi từ AI sau 90 giây. Chạm để thử lại.")
                }
            }

            // 4b. AI provider timeout / slow response
            if (msg.contains("phản hồi quá lâu", ignoreCase = true)) {
                return if (isEn) {
                    Pair("Server slow response", "$msg. Tap to retry.")
                } else {
                    Pair("Máy chủ phản hồi quá lâu", "$msg. Chạm để thử lại.")
                }
            }

            // 5. In-flight streaming stall timeout (25s)
            if (msg.contains("SSE_STREAM_WATCHDOG_TIMEOUT", ignoreCase = true)) {
                return if (isEn) {
                    Pair("AI Stream Interrupted", "AI response stream was stalled for over 25s. Tap to retry.")
                } else {
                    Pair("Gián đoạn dữ liệu AI", "Dòng phản hồi AI bị ngắt quãng quá 25 giây. Chạm để thử lại.")
                }
            }

            // 6. HTTP 401 / 403 API Key unauthorized / rejected
            if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized", ignoreCase = true) || msg.contains("Forbidden", ignoreCase = true)) {
                return if (isEn) {
                    Pair("Invalid API Key (401/403)", "API Key was rejected or expired. Please check your key in Settings.")
                } else {
                    Pair("API Key không hợp lệ (401/403)", "Khóa API bị từ chối hoặc hết hạn. Vui lòng kiểm tra lại Key trong Cài đặt.")
                }
            }

            // 7. HTTP 500 / 502 / 503 / 504 Server Error
            if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") || msg.contains("Bad Gateway", ignoreCase = true) || msg.contains("Service Unavailable", ignoreCase = true)) {
                return if (isEn) {
                    Pair("AI Server Overload (50x)", "AI provider is busy or under maintenance. Tap to retry.")
                } else {
                    Pair("Máy chủ AI quá tải (50x)", "Nhà cung cấp AI đang bận hoặc bảo trì. Chạm để thử lại.")
                }
            }

            // 8. General AI error with clean extracted message
            val cleanErr = msg.ifBlank { if (isEn) "Unknown error calling AI" else "Lỗi không xác định khi gọi AI" }
            return if (isEn) {
                Pair("AI Request Error", "$cleanErr. Tap to retry.")
            } else {
                Pair("Lỗi gọi AI", "$cleanErr. Chạm để thử lại.")
            }
        }

        fun isNetworkException(t: Throwable?): Boolean {
            if (t == null) return false
            var curr: Throwable? = t
            while (curr != null) {
                if (curr is java.net.UnknownHostException ||
                    curr is java.net.SocketTimeoutException ||
                    curr is java.net.ConnectException ||
                    curr is java.util.concurrent.TimeoutException ||
                    curr.message?.contains("timeout", ignoreCase = true) == true ||
                    curr.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                    curr.message?.contains("Failed to connect", ignoreCase = true) == true ||
                    curr.message?.contains("Software caused connection abort", ignoreCase = true) == true ||
                    curr.message?.contains("SSE_STREAM_WATCHDOG_TIMEOUT", ignoreCase = true) == true ||
                    curr.message?.contains("INITIAL_CONNECTION_TIMEOUT", ignoreCase = true) == true
                ) {
                    return true
                }
                curr = curr.cause
            }
            return false
        }

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun getOverlayPermissionIntent(context: Context): Intent {
            return Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }

        fun wakeToState1(context: Context) {
            if (!isOverlayPermissionGranted(context)) return
            try {
                val intent = Intent(context, FloatingBubbleService::class.java).apply {
                    action = ACTION_WAKE_TO_STATE_1
                    putExtra(EXTRA_WAKE_TO_STATE_1, true)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("FloatingBubbleService", "Không thể gọi startService cho wakeToState1: ${e.message}")
            }
        }

        fun show(context: Context, summaryContent: String = "", modelInfo: String = "", sessionId: String = "", wakeToState1: Boolean = true) {
            if (!isOverlayPermissionGranted(context)) return
            try {
                val intent = Intent(context, FloatingBubbleService::class.java).apply {
                    putExtra(EXTRA_SUMMARY_CONTENT, summaryContent)
                    putExtra(EXTRA_MODEL_INFO, modelInfo)
                    if (sessionId.isNotBlank()) {
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    if (wakeToState1) {
                        action = ACTION_WAKE_TO_STATE_1
                        putExtra(EXTRA_WAKE_TO_STATE_1, true)
                    }
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("FloatingBubbleService", "Không thể gọi startService cho show: ${e.message}")
            }
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }
    }
}
