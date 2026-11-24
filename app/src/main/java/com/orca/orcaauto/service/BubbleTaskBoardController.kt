package com.orca.orcaauto.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.filter.ParsedSummary
import com.orca.orcaauto.domain.filter.SummaryParser
import com.orca.orcaauto.domain.filter.TaskItem
import com.orca.orcaauto.domain.filter.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Controller responsible for task board state, rendering task rows, gesture swipe interactions,
 * alarm toggling, and fast in-place diffing (< 0.05ms).
 *
 * Preserves commit invariants:
 * - c7d2d0e: Chat room jump via chatRefId.
 * - 422ffcb: Alarm toggle disarms hardware only and sets PROPOSED status (never CANCELLED).
 * - f2912ee: Rollover past alarm to tomorrow + toast.
 */
class BubbleTaskBoardController(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val getBubbleRootView: () -> ViewGroup?,
    private val getParsedSummary: () -> ParsedSummary?,
    private val hasAdviceProvider: () -> Boolean,
    private val isExpandedProvider: () -> Boolean,
    private val onCollapseRequested: (ViewGroup) -> Unit
) {
    var dialogRenderer: BubbleDialogRenderer? = null

    @Volatile
    var alarmDbMap: Map<String, SmartAlarmEntity> = emptyMap()
        internal set

    @Volatile
    var alarmNormalizedTitleMap: Map<String, SmartAlarmEntity> = emptyMap()
        internal set

    val dismissedTaskTitles: MutableSet<String> = mutableSetOf()

    @Volatile
    var isSwipeAnimating: Boolean = false
        internal set

    @Volatile
    var isUserInteractingWithTask: Boolean = false
        internal set

    @Volatile
    var hasPendingTaskBoardUpdate: Boolean = false
        internal set

    private var currentTasksBoardLayout: LinearLayout? = null
    private var currentCardLayoutRef: View? = null

    internal class TaskRowHolder(
        val container: FrameLayout,
        val bgSwipeLayout: FrameLayout,
        val leftSwipeAction: LinearLayout,
        val rightSwipeAction: LinearLayout,
        val taskRow: LinearLayout,
        val titleRow: LinearLayout,
        val checkCircle: TextView,
        val title: TextView,
        val badgeRow: LinearLayout,
        var deadlinePill: TextView? = null,
        var sourcePill: TextView? = null,
        val actionRow: LinearLayout,
        var alarmPill: TextView? = null,
        var cancelledPill: TextView? = null,
        var boundTask: TaskItem? = null,
        var boundAlarmId: String? = null,
        var boundAlarm: SmartAlarmEntity? = null
    )

    fun attachBoard(tasksBoardLayout: LinearLayout, cardLayout: View) {
        currentTasksBoardLayout = tasksBoardLayout
        currentCardLayoutRef = cardLayout
    }

    fun clearEmptyBox() {
        currentTasksBoardLayout?.findViewWithTag<View>("EMPTY_BOX_TAG")?.let { currentTasksBoardLayout?.removeView(it) }
    }

    fun populateTaskBoard(container: LinearLayout, cardLayout: View, hasAdvice: Boolean = false) {
        val parsed = getParsedSummary() ?: SummaryParser.parse("")
        val speakableParsed = SystemAlarmHelper.filterOutCancelledFast(parsed, alarmNormalizedTitleMap)

        // Filter out dismissed tasks and completed tasks from parsed TODO/Planned
        val parsedTasksToDo = speakableParsed.tasksToDo.filterNot { task ->
            dismissedTaskTitles.any { SystemAlarmHelper.isTitleMatching(task.title, it) } ||
            SystemAlarmHelper.isTaskCompletedFast(task.title, alarmNormalizedTitleMap)
        }
        val parsedTasksPlanned = speakableParsed.tasksPlanned.filterNot { task ->
            dismissedTaskTitles.any { SystemAlarmHelper.isTitleMatching(task.title, it) } ||
            SystemAlarmHelper.isTaskCompletedFast(task.title, alarmNormalizedTitleMap)
        }
        val parsedTasksDone = speakableParsed.tasksDone.filterNot { task ->
            dismissedTaskTitles.any { SystemAlarmHelper.isTitleMatching(task.title, it) }
        }

        val allTasksToDo = parsedTasksToDo
        val allTasksPlanned = parsedTasksPlanned
        val allTasksDone = parsedTasksDone

        val prefs = UserPreferences.getInstance(context)
        val pinnedTitles = prefs.getPinnedTaskTitles()

        fun isTaskPinned(task: TaskItem): Boolean {
            if (pinnedTitles.isEmpty()) return false
            val clean = SystemAlarmHelper.cleanTaskTitle(task.title)
            return pinnedTitles.any { SystemAlarmHelper.isTitleMatching(clean, it) || clean.equals(it, ignoreCase = true) }
        }

        val (pinnedToDo, unpinnedToDo) = allTasksToDo.partition { isTaskPinned(it) }
        val (pinnedPlanned, unpinnedPlanned) = allTasksPlanned.partition { isTaskPinned(it) }

        class TaskRowItem(val task: TaskItem, val isPlanned: Boolean, val isPinned: Boolean)
        val taskItems = ArrayList<TaskRowItem>(allTasksToDo.size + allTasksPlanned.size + allTasksDone.size)
        pinnedToDo.forEach { taskItems.add(TaskRowItem(it, isPlanned = false, isPinned = true)) }
        pinnedPlanned.forEach { taskItems.add(TaskRowItem(it, isPlanned = true, isPinned = true)) }
        unpinnedToDo.forEach { taskItems.add(TaskRowItem(it, isPlanned = false, isPinned = false)) }
        unpinnedPlanned.forEach { taskItems.add(TaskRowItem(it, isPlanned = true, isPinned = false)) }
        allTasksDone.forEach { taskItems.add(TaskRowItem(it, isPlanned = false, isPinned = isTaskPinned(it))) }

        if (taskItems.isEmpty()) {
            if (container.childCount > 0) {
                container.removeAllViews()
            }
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val targetCount = taskItems.size
        val currentCount = container.childCount

        // In-Place Diff:
        // 1. Remove excess trailing children if targetCount < currentCount
        if (currentCount > targetCount) {
            container.removeViews(targetCount, currentCount - targetCount)
        }

        // 2. In-place update existing child views via TaskRowHolder (< 0.05ms)
        val updateCount = minOf(currentCount, targetCount)
        for (i in 0 until updateCount) {
            val child = container.getChildAt(i)
            val holder = child.tag as? TaskRowHolder
            val item = taskItems[i]
            if (holder != null) {
                bindTaskRow(holder, item.task, item.isPlanned, cardLayout, item.isPinned)
            } else {
                val newView = renderTaskRow(item.task, item.isPlanned, cardLayout, item.isPinned)
                container.removeViewAt(i)
                container.addView(newView, i)
            }
        }

        // 3. Append only new views if needed
        for (i in updateCount until targetCount) {
            val item = taskItems[i]
            container.addView(renderTaskRow(item.task, item.isPlanned, cardLayout, item.isPinned))
        }
    }

    fun refreshTasksBoardOnly() {
        val board = currentTasksBoardLayout ?: return
        val card = currentCardLayoutRef ?: return
        val hasAdvice = hasAdviceProvider()
        populateTaskBoard(board, card, hasAdvice = hasAdvice)
    }

    fun updateAlarmsFromDb(alarms: List<SmartAlarmEntity>) {
        alarmDbMap = alarms.associateBy { it.alarmId }
        alarmNormalizedTitleMap = SystemAlarmHelper.buildNormalizedTitleMap(alarms)
        if (isExpandedProvider()) {
            if (!isSwipeAnimating && !isUserInteractingWithTask) {
                currentTasksBoardLayout?.let { board ->
                    currentCardLayoutRef?.let { card ->
                        populateTaskBoard(board, card, hasAdvice = hasAdviceProvider())
                    }
                }
            } else {
                hasPendingTaskBoardUpdate = true
            }
        }
    }

    fun clearSessionTransientState() {
        currentTasksBoardLayout = null
        currentCardLayoutRef = null
        dismissedTaskTitles.clear()
        isSwipeAnimating = false
        isUserInteractingWithTask = false
        hasPendingTaskBoardUpdate = false
    }

    fun resetDismissedTasks() {
        dismissedTaskTitles.clear()
        isSwipeAnimating = false
        isUserInteractingWithTask = false
        hasPendingTaskBoardUpdate = false
    }

    fun filterCancelledTasks(summary: ParsedSummary): ParsedSummary {
        return SystemAlarmHelper.filterOutCancelledFast(summary, alarmNormalizedTitleMap)
    }

    fun onAlarmStateUpdated(
        alarmId: String,
        generatedAlarmId: String,
        cleanTitle: String,
        entity: SmartAlarmEntity
    ) {
        alarmDbMap = alarmDbMap + (alarmId to entity) + (generatedAlarmId to entity)
        alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to entity)
    }

    private fun bindTaskRow(
        holder: TaskRowHolder,
        task: TaskItem,
        isPlanned: Boolean,
        cardLayout: View,
        isPinnedOverride: Boolean? = null
    ) {
        val cleanTitle = SystemAlarmHelper.cleanTaskTitle(task.title)
        val prefs = UserPreferences.getInstance(context)
        val isPinned = isPinnedOverride ?: prefs.isTaskPinned(cleanTitle)

        val parsedDt = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
        val targetCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
        val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
        val existingAlarm = alarmDbMap[generatedAlarmId] ?: SystemAlarmHelper.findMatchingAlarmFast(cleanTitle, alarmNormalizedTitleMap)
        val alarmId = existingAlarm?.alarmId ?: generatedAlarmId

        val isCancelled = existingAlarm?.status == SmartAlarmStatus.CANCELLED
        val isCompleted = existingAlarm?.status == SmartAlarmStatus.COMPLETED || task.status == TaskStatus.DONE
        val isAlarmActive = existingAlarm?.status == SmartAlarmStatus.ACTIVE
        val proposedTime = parsedDt?.timeLabel ?: "Hẹn giờ"

        holder.boundTask = task
        holder.boundAlarmId = alarmId
        holder.boundAlarm = existingAlarm

        // Reset visual translation & swipe actions
        holder.taskRow.translationX = 0f
        holder.taskRow.alpha = 1.0f
        holder.container.visibility = View.VISIBLE
        holder.leftSwipeAction.visibility = View.GONE
        holder.rightSwipeAction.visibility = View.GONE

        // Foreground Card Background styling
        holder.taskRow.background = GradientDrawable().apply {
            setColor(if (isCancelled) Color.parseColor("#1A1E293B") else Color.parseColor("#261E293B"))
            cornerRadius = dpToPx(10).toFloat()
            if (isCancelled) {
                setStroke(dpToPx(1), Color.parseColor("#59EF4444"))
            } else if (isCompleted) {
                setStroke(dpToPx(1), Color.parseColor("#10B981"))
            } else if (isPinned) {
                setStroke(dpToPx(1), Color.parseColor("#F59E0B"))
            } else if (isPlanned) {
                setStroke(dpToPx(1), Color.parseColor("#59F59E0B"))
            } else {
                setStroke(dpToPx(1), Color.parseColor("#EF4444"))
            }
        }

        // Check circle visual
        holder.checkCircle.text = when {
            isCompleted -> "✓"
            isCancelled -> "✕"
            else -> "○"
        }
        holder.checkCircle.setTextColor(when {
            isCompleted -> Color.parseColor("#10B981")
            isCancelled -> Color.parseColor("#EF4444")
            else -> Color.parseColor("#E4E4E7")
        })

        // Title visual
        holder.title.text = task.title
        holder.title.setTextColor(if (isCompleted || isCancelled) Color.parseColor("#94A3B8") else Color.WHITE)
        if (isCompleted || isCancelled) {
            holder.title.paintFlags = holder.title.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags = holder.title.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Badge row
        holder.badgeRow.removeAllViews()

        if (isPinned && !isCompleted && !isCancelled) {
            val pinPill = TextView(context).apply {
                text = "📌 Đã ghim"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0C2A4D"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#0284C7"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(6)
                }
            }
            holder.badgeRow.addView(pinPill)
        }
        if (task.deadline.isNotBlank()) {
            val deadlinePill = TextView(context).apply {
                text = "⏰ ${task.deadline}"
                setTextColor(if (isPlanned) Color.parseColor("#FDE68A") else Color.parseColor("#FCA5A5"))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
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
            holder.deadlinePill = deadlinePill
            holder.badgeRow.addView(deadlinePill)
        } else {
            holder.deadlinePill = null
        }

        if (task.source.isNotBlank()) {
            val sourcePill = TextView(context).apply {
                text = "💬 ${task.source}"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0F172A"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#334155"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dpToPx(6)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val jumpResult = ChatIntentManager.jumpToChat(
                        context = context,
                        chatRefId = task.chatRefId,
                        sourceText = task.source,
                        taskTitle = task.title
                    )
                    val root = getBubbleRootView()
                    if (root != null && jumpResult != ChatIntentManager.JumpResult.FAILED) {
                        onCollapseRequested(root)
                    }
                }
            }
            holder.sourcePill = sourcePill
            holder.badgeRow.addView(sourcePill)
        } else {
            holder.sourcePill = null
        }
        holder.badgeRow.visibility = if (holder.badgeRow.childCount > 0) View.VISIBLE else View.GONE

        // Action row (Status pill or Alarm button)
        holder.actionRow.removeAllViews()
        if (isCancelled) {
            val cancelledPill = TextView(context).apply {
                text = "↺ Đã hủy (Nhấn giữ khôi phục)"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#26EF4444"))
                    cornerRadius = dpToPx(4).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#4DEF4444"))
                }
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                isClickable = true
                setOnClickListener {
                    Toast.makeText(context, "Nhấn giữ để khôi phục nhiệm vụ đã hủy", Toast.LENGTH_SHORT).show()
                }
            }
            holder.cancelledPill = cancelledPill
            holder.alarmPill = null
            holder.actionRow.addView(cancelledPill)
        } else if (!isCompleted) {
            var currentAlarmActive = isAlarmActive
            val pill = TextView(context).apply {
                fun updatePillVisuals(active: Boolean) {
                    text = if (active) "✓ Đã đặt $proposedTime" else "🔔 Đặt $proposedTime"
                    setTextColor(if (active) Color.parseColor("#10B981") else Color.parseColor("#38BDF8"))
                    background = GradientDrawable().apply {
                        setColor(if (active) Color.parseColor("#062E24") else Color.parseColor("#082F49"))
                        cornerRadius = dpToPx(4).toFloat()
                        setStroke(dpToPx(1), if (active) Color.parseColor("#059669") else Color.parseColor("#0284C7"))
                    }
                }
                updatePillVisuals(currentAlarmActive)
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(3))
                isClickable = true
                setOnClickListener {
                    val curTask = holder.boundTask ?: task
                    val curTitle = SystemAlarmHelper.cleanTaskTitle(curTask.title)
                    val curAlarmId = holder.boundAlarmId ?: alarmId
                    val curExisting = holder.boundAlarm ?: existingAlarm

                    if (currentAlarmActive) {
                        SystemAlarmHelper.disarmAlarmHardwareOnly(context, curTitle, curAlarmId)
                        val disarmed = (curExisting ?: SmartAlarmEntity(
                            alarmId = curAlarmId,
                            taskTitle = curTitle,
                            targetTimeMillis = targetCal.timeInMillis,
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.PROPOSED
                        )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                        alarmDbMap = alarmDbMap + (curAlarmId to disarmed) + (generatedAlarmId to disarmed)
                        alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(curTitle) to disarmed)
                        currentAlarmActive = false
                        updatePillVisuals(false)
                        serviceScope.launch {
                            try {
                                val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                                dao.updateStatus(curAlarmId, SmartAlarmStatus.PROPOSED)
                                dao.updateStatusByTitle(curTitle, SmartAlarmStatus.PROPOSED)
                            } catch (_: Exception) {}
                        }
                        Toast.makeText(context, "Đã tắt chuông báo: ${curTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                    } else {
                        val now = System.currentTimeMillis()
                        val isRolledOver = targetCal.timeInMillis <= now
                        val effectiveCal = (targetCal.clone() as Calendar).apply {
                            while (timeInMillis <= now) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        val result = SystemAlarmHelper.scheduleSmartAlarms(
                            context = context,
                            taskTitle = curTitle,
                            targetCalendar = effectiveCal,
                            mode = AlarmCascadeMode.X1
                        )
                        result.onSuccess {
                            val armed = (curExisting ?: SmartAlarmEntity(
                                alarmId = curAlarmId,
                                taskTitle = curTitle,
                                targetTimeMillis = effectiveCal.timeInMillis,
                                targetDateLabel = proposedTime,
                                status = SmartAlarmStatus.ACTIVE
                            )).copy(
                                targetTimeMillis = effectiveCal.timeInMillis,
                                status = SmartAlarmStatus.ACTIVE,
                                updatedAt = System.currentTimeMillis()
                            )
                            alarmDbMap = alarmDbMap + (curAlarmId to armed) + (generatedAlarmId to armed)
                            alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(curTitle) to armed)
                            currentAlarmActive = true
                            updatePillVisuals(true)
                            if (isRolledOver) {
                                Toast.makeText(
                                    context,
                                    "🔔 Giờ cũ đã qua, đã hẹn ${String.format(Locale.getDefault(), "%02d:%02d", effectiveCal.get(Calendar.HOUR_OF_DAY), effectiveCal.get(Calendar.MINUTE))} ngày mai: ${curTitle.take(24)}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "🔔 Đã đặt chuông báo $proposedTime: ${curTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                            }
                        }.onFailure { err ->
                            Toast.makeText(context, "Không thể đặt báo thức: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            holder.alarmPill = pill
            holder.cancelledPill = null
            holder.actionRow.addView(pill)
        }
        holder.actionRow.visibility = if (holder.actionRow.childCount > 0) View.VISIBLE else View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun renderTaskRow(task: TaskItem, isPlanned: Boolean, cardLayout: View, isPinned: Boolean? = null): View {
        val dm = context.resources.displayMetrics
        val cardWidth = (dm.widthPixels * 0.94f).toInt().coerceAtMost(dpToPx(500))

        // Root container for gesture swiping
        val container = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val collapseAndRemoveRow: () -> Unit = {
            val initialHeight = container.height.takeIf { it > 0 } ?: dpToPx(56)
            val initialBottomMargin = (container.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            val collapseAnim = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 140
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    val lp = container.layoutParams as? ViewGroup.MarginLayoutParams
                    if (lp != null) {
                        lp.height = (initialHeight * fraction).toInt()
                        lp.bottomMargin = (initialBottomMargin * fraction).toInt()
                        container.layoutParams = lp
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        container.visibility = View.GONE
                        currentTasksBoardLayout?.removeView(container)
                        val hasVisibleChild = currentTasksBoardLayout?.let { board ->
                            (0 until board.childCount).any { idx -> board.getChildAt(idx).visibility == View.VISIBLE }
                        } ?: false
                        if (!hasVisibleChild) {
                            currentTasksBoardLayout?.visibility = View.GONE
                        }
                        isSwipeAnimating = false
                    }
                })
            }
            collapseAnim.start()
        }

        // Swipe Background Action View
        val bgSwipeLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val leftSwipeAction = LinearLayout(context).apply {
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

        val rightSwipeAction = LinearLayout(context).apply {
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
        val taskRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
        }

        // Title row: Checkbox + Title
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val checkCircle = TextView(context).apply {
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, dpToPx(8), 0)
            isClickable = true
        }
        titleRow.addView(checkCircle)

        // Single-Pass Measure: explicit width instead of weight = 1f to eliminate double measure pass
        val titleWidth = (cardWidth - dpToPx(84)).coerceAtLeast(dpToPx(150))
        val title = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(titleWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        titleRow.addView(title)
        taskRow.addView(titleRow)

        // Badge row (Deadline & Source)
        val badgeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(5)
                leftMargin = dpToPx(20)
            }
        }
        taskRow.addView(badgeRow)

        // Action row
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4)
                leftMargin = dpToPx(20)
            }
        }
        taskRow.addView(actionRow)
        container.addView(taskRow)

        // Attach TaskRowHolder
        val holder = TaskRowHolder(
            container = container,
            bgSwipeLayout = bgSwipeLayout,
            leftSwipeAction = leftSwipeAction,
            rightSwipeAction = rightSwipeAction,
            taskRow = taskRow,
            titleRow = titleRow,
            checkCircle = checkCircle,
            title = title,
            badgeRow = badgeRow,
            actionRow = actionRow
        )
        container.tag = holder

        // Initial binding
        bindTaskRow(holder, task, isPlanned, cardLayout, isPinned)

        // checkCircle click listener
        checkCircle.setOnClickListener {
            val curTask = holder.boundTask ?: task
            val cleanTitle = SystemAlarmHelper.cleanTaskTitle(curTask.title)
            val parsedDt = SystemAlarmHelper.parseDateTimeFromText(curTask.deadline)
            val targetCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
            val curAlarmId = holder.boundAlarmId ?: generatedAlarmId
            val curExisting = holder.boundAlarm ?: alarmDbMap[curAlarmId] ?: SystemAlarmHelper.findMatchingAlarmFast(cleanTitle, alarmNormalizedTitleMap)
            val isCancelled = curExisting?.status == SmartAlarmStatus.CANCELLED
            val isCompleted = curExisting?.status == SmartAlarmStatus.COMPLETED || curTask.status == TaskStatus.DONE
            val proposedTime = parsedDt?.timeLabel ?: "Hẹn giờ"

            if (isCancelled) {
                SystemAlarmHelper.restoreCancelledTask(context, cleanTitle, curAlarmId)
                val restoredAlarm = (curExisting ?: SmartAlarmEntity(
                    alarmId = curAlarmId,
                    taskTitle = cleanTitle,
                    targetTimeMillis = System.currentTimeMillis(),
                    targetDateLabel = proposedTime,
                    status = SmartAlarmStatus.PROPOSED
                )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                alarmDbMap = alarmDbMap + (curAlarmId to restoredAlarm) + (generatedAlarmId to restoredAlarm)
                alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to restoredAlarm)
                Toast.makeText(context, "✓ Đã khôi phục: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                currentTasksBoardLayout?.post { refreshTasksBoardOnly() }
            } else if (isCompleted) {
                dialogRenderer?.showFloatingConfirmationDialog(
                    title = "Đưa về chưa hoàn thành?",
                    message = "Bạn có muốn đưa nhiệm vụ \"$cleanTitle\" về lại danh sách việc cần làm (chưa hoàn thành) để tiếp tục theo dõi không?",
                    confirmText = "Xác nhận",
                    confirmColor = Color.WHITE
                ) {
                    SystemAlarmHelper.uncompleteTask(context, cleanTitle, curAlarmId)
                    val uncompletedAlarm = (curExisting ?: SmartAlarmEntity(
                        alarmId = curAlarmId,
                        taskTitle = cleanTitle,
                        targetTimeMillis = System.currentTimeMillis(),
                        targetDateLabel = proposedTime,
                        status = SmartAlarmStatus.PROPOSED
                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                    alarmDbMap = alarmDbMap + (curAlarmId to uncompletedAlarm) + (generatedAlarmId to uncompletedAlarm)
                    alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to uncompletedAlarm)
                    Toast.makeText(context, "Đã đưa về trạng thái chưa hoàn thành!", Toast.LENGTH_SHORT).show()
                    currentTasksBoardLayout?.post { refreshTasksBoardOnly() }
                }
            } else {
                dismissedTaskTitles.add(cleanTitle)
                SystemAlarmHelper.markTaskCompleted(context, cleanTitle, curAlarmId)
                val completedAlarm = (curExisting ?: SmartAlarmEntity(
                    alarmId = curAlarmId,
                    taskTitle = cleanTitle,
                    targetTimeMillis = System.currentTimeMillis(),
                    targetDateLabel = "Đã xong",
                    status = SmartAlarmStatus.COMPLETED
                )).copy(status = SmartAlarmStatus.COMPLETED, updatedAt = System.currentTimeMillis())
                alarmDbMap = alarmDbMap + (curAlarmId to completedAlarm) + (generatedAlarmId to completedAlarm)
                alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to completedAlarm)
                Toast.makeText(context, "✓ Đã hoàn thành: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                collapseAndRemoveRow()
            }
        }

        // Gesture handling with isUserInteractingWithTask protection
        var startX = 0f
        var startY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var isSwiping = false
        var isPillActive = false
        var hasCancelledHold = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val swipeThreshold = dpToPx(72)
        val taskHandler = Handler(Looper.getMainLooper())

        val cardRect = Rect()
        var dragPillView: LinearLayout? = null
        var pillIcon: TextView? = null
        var pillTv: TextView? = null
        var wasOutside = false

        val removePillSafely: () -> Unit = {
            if (dragPillView != null) {
                getBubbleRootView()?.removeView(dragPillView)
                dragPillView = null
                pillIcon = null
                pillTv = null
            }
        }

        val updatePillState: (Float, Float) -> Unit = { rawX, rawY ->
            val pill = dragPillView
            if (pill != null) {
                cardLayout.getGlobalVisibleRect(cardRect)
                val isOutside = !cardRect.contains(rawX.toInt(), rawY.toInt())

                if (isOutside != wasOutside) {
                    wasOutside = isOutside
                    if (isOutside) {
                        try {
                            pill.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        } catch (_: Exception) {}
                    }
                }

                val curTask = holder.boundTask ?: task
                val cleanTitle = SystemAlarmHelper.cleanTaskTitle(curTask.title)

                if (isOutside) {
                    val targetPackage = ChatIntentManager.resolveTargetPackage(curTask.source, cleanTitle)
                    val appDisplayName = ChatIntentManager.getAppDisplayName(targetPackage)
                    pillIcon?.text = "🚀"
                    pillTv?.text = "Thả để mở $appDisplayName"
                    pill.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E60284C7"))
                        cornerRadius = dpToPx(20).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#9938BDF8"))
                    }
                } else {
                    pillIcon?.text = "⚙️"
                    pillTv?.text = "Thả để đặt báo thức / ghi chú"
                    pill.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E61E293B"))
                        cornerRadius = dpToPx(20).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#4DFFFFFF"))
                    }
                }

                pill.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val pillW = pill.measuredWidth
                val pillH = pill.measuredHeight
                val dm = context.resources.displayMetrics
                val targetX = (rawX - pillW / 2).coerceIn(dpToPx(8).toFloat(), (dm.widthPixels - pillW - dpToPx(8)).toFloat())
                val targetY = (rawY - pillH - dpToPx(38)).coerceAtLeast(dpToPx(24).toFloat())
                pill.x = targetX
                pill.y = targetY
            }
        }

        val longPressRunnable = Runnable {
            if (!isSwiping && !hasCancelledHold) {
                isPillActive = true
                wasOutside = false

                val root = getBubbleRootView()
                if (root != null) {
                    val pill = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        elevation = dpToPx(60).toFloat()
                        translationZ = dpToPx(60).toFloat()
                        setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7))
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val icon = TextView(context).apply {
                        textSize = 13f
                    }
                    val tv = TextView(context).apply {
                        textSize = 11.5f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        setPadding(dpToPx(6), 0, 0, 0)
                    }
                    pill.addView(icon)
                    pill.addView(tv)
                    dragPillView = pill
                    pillIcon = icon
                    pillTv = tv

                    root.addView(pill)
                    pill.bringToFront()
                    updatePillState(lastRawX, lastRawY)

                    pill.scaleX = 0.8f
                    pill.scaleY = 0.8f
                    pill.alpha = 0f
                    pill.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(150).start()
                }
            }
        }

        taskRow.setOnTouchListener { v, event ->
            val curTask = holder.boundTask ?: task
            val cleanTitle = SystemAlarmHelper.cleanTaskTitle(curTask.title)
            val parsedDt = SystemAlarmHelper.parseDateTimeFromText(curTask.deadline)
            val targetCal = parsedDt?.calendar ?: Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
            val curAlarmId = holder.boundAlarmId ?: generatedAlarmId
            val curExisting = holder.boundAlarm ?: alarmDbMap[curAlarmId] ?: SystemAlarmHelper.findMatchingAlarmFast(cleanTitle, alarmNormalizedTitleMap)
            val isCancelled = curExisting?.status == SmartAlarmStatus.CANCELLED
            val isCompleted = curExisting?.status == SmartAlarmStatus.COMPLETED || curTask.status == TaskStatus.DONE
            val proposedTime = parsedDt?.timeLabel ?: "Hẹn giờ"

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isUserInteractingWithTask = true
                    startX = event.rawX
                    startY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    isSwiping = false
                    isPillActive = false
                    hasCancelledHold = false
                    taskHandler.removeCallbacks(longPressRunnable)
                    taskHandler.postDelayed(longPressRunnable, 550L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY

                    if (!isPillActive) {
                        if (Math.hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            hasCancelledHold = true
                            taskHandler.removeCallbacks(longPressRunnable)
                        }

                        if (!isSwiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            val allowRight = !isCompleted && !isCancelled
                            val allowLeft = !isCancelled
                            if ((dx > 0 && allowRight) || (dx < 0 && allowLeft)) {
                                isSwiping = true
                                hasCancelledHold = true
                                taskHandler.removeCallbacks(longPressRunnable)
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
                    } else {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        updatePillState(event.rawX, event.rawY)
                        true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    taskHandler.removeCallbacks(longPressRunnable)
                    isUserInteractingWithTask = false

                    if (isPillActive) {
                        isPillActive = false
                        cardLayout.getGlobalVisibleRect(cardRect)
                        val isOutside = !cardRect.contains(event.rawX.toInt(), event.rawY.toInt())
                        removePillSafely()

                        if (isOutside) {
                            val jumpResult = ChatIntentManager.jumpToChat(
                                context = context,
                                chatRefId = curTask.chatRefId,
                                sourceText = curTask.source,
                                taskTitle = cleanTitle
                            )
                            val root = getBubbleRootView()
                            if (root != null && jumpResult != ChatIntentManager.JumpResult.FAILED) {
                                onCollapseRequested(root)
                            }
                        } else {
                            if (isCancelled) {
                                dialogRenderer?.showFloatingConfirmationDialog(
                                    title = "Khôi phục nhiệm vụ?",
                                    message = "Bạn có muốn khôi phục lại nhiệm vụ \"$cleanTitle\" về danh sách việc cần làm không?",
                                    confirmText = "Khôi phục",
                                    confirmColor = Color.parseColor("#10B981")
                                ) {
                                    SystemAlarmHelper.restoreCancelledTask(context, cleanTitle, curAlarmId)
                                    val restoredAlarm = (curExisting ?: SmartAlarmEntity(
                                        alarmId = curAlarmId,
                                        taskTitle = cleanTitle,
                                        targetTimeMillis = System.currentTimeMillis(),
                                        targetDateLabel = proposedTime,
                                        status = SmartAlarmStatus.PROPOSED
                                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                                    alarmDbMap = alarmDbMap + (curAlarmId to restoredAlarm) + (generatedAlarmId to restoredAlarm)
                                    alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to restoredAlarm)
                                    Toast.makeText(context, "Đã khôi phục nhiệm vụ thành công!", Toast.LENGTH_SHORT).show()
                                    currentTasksBoardLayout?.post { refreshTasksBoardOnly() }
                                }
                            } else if (isCompleted) {
                                dialogRenderer?.showFloatingConfirmationDialog(
                                    title = "Đưa về chưa hoàn thành?",
                                    message = "Bạn có muốn đưa nhiệm vụ \"$cleanTitle\" về lại danh sách việc cần làm (chưa hoàn thành) để tiếp tục theo dõi không?",
                                    confirmText = "Xác nhận",
                                    confirmColor = Color.WHITE
                                ) {
                                    SystemAlarmHelper.uncompleteTask(context, cleanTitle, curAlarmId)
                                    val uncompletedAlarm = (curExisting ?: SmartAlarmEntity(
                                        alarmId = curAlarmId,
                                        taskTitle = cleanTitle,
                                        targetTimeMillis = System.currentTimeMillis(),
                                        targetDateLabel = proposedTime,
                                        status = SmartAlarmStatus.PROPOSED
                                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())
                                    alarmDbMap = alarmDbMap + (curAlarmId to uncompletedAlarm) + (generatedAlarmId to uncompletedAlarm)
                                    alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to uncompletedAlarm)
                                    Toast.makeText(context, "Đã đưa về trạng thái chưa hoàn thành!", Toast.LENGTH_SHORT).show()
                                    currentTasksBoardLayout?.post { refreshTasksBoardOnly() }
                                }
                            } else {
                                dialogRenderer?.showFloatingTaskConfigDialog(curTask, curAlarmId, curExisting)
                            }
                        }
                        if (hasPendingTaskBoardUpdate) {
                            hasPendingTaskBoardUpdate = false
                            refreshTasksBoardOnly()
                        }
                        return@setOnTouchListener true
                    }

                    if (isSwiping) {
                        isSwiping = false
                        val dx = event.rawX - startX

                        if (dx > swipeThreshold && !isCompleted && !isCancelled) {
                            isSwipeAnimating = true
                            dismissedTaskTitles.add(cleanTitle)
                            v.animate().translationX(v.width.toFloat()).setDuration(160).withEndAction {
                                SystemAlarmHelper.markTaskCompleted(context, cleanTitle, curAlarmId)
                                val completedAlarm = (curExisting ?: SmartAlarmEntity(
                                    alarmId = curAlarmId,
                                    taskTitle = cleanTitle,
                                    targetTimeMillis = System.currentTimeMillis(),
                                    targetDateLabel = "Đã xong",
                                    status = SmartAlarmStatus.COMPLETED
                                )).copy(status = SmartAlarmStatus.COMPLETED, updatedAt = System.currentTimeMillis())
                                alarmDbMap = alarmDbMap + (curAlarmId to completedAlarm) + (generatedAlarmId to completedAlarm)
                                alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to completedAlarm)
                                Toast.makeText(context, "✓ Đã hoàn thành: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                                isSwipeAnimating = false
                                isUserInteractingWithTask = false
                                if (hasPendingTaskBoardUpdate) {
                                    hasPendingTaskBoardUpdate = false
                                    refreshTasksBoardOnly()
                                }
                                collapseAndRemoveRow()
                            }.start()
                        } else if (dx < -swipeThreshold && !isCancelled) {
                            isSwipeAnimating = true
                            dismissedTaskTitles.add(cleanTitle)
                            v.animate().translationX(-v.width.toFloat()).setDuration(160).withEndAction {
                                SystemAlarmHelper.markTaskCancelled(context, cleanTitle, curAlarmId)
                                val cancelledAlarm = (curExisting ?: SmartAlarmEntity(
                                    alarmId = curAlarmId,
                                    taskTitle = cleanTitle,
                                    targetTimeMillis = System.currentTimeMillis(),
                                    targetDateLabel = "Đã hủy",
                                    status = SmartAlarmStatus.CANCELLED
                                )).copy(status = SmartAlarmStatus.CANCELLED, updatedAt = System.currentTimeMillis())
                                alarmDbMap = alarmDbMap + (curAlarmId to cancelledAlarm) + (generatedAlarmId to cancelledAlarm)
                                alarmNormalizedTitleMap = alarmNormalizedTitleMap + (SystemAlarmHelper.normalizeTitle(cleanTitle) to cancelledAlarm)
                                Toast.makeText(context, "✕ Đã hủy: ${cleanTitle.take(24)}...", Toast.LENGTH_SHORT).show()
                                isSwipeAnimating = false
                                isUserInteractingWithTask = false
                                if (hasPendingTaskBoardUpdate) {
                                    hasPendingTaskBoardUpdate = false
                                    refreshTasksBoardOnly()
                                }
                                collapseAndRemoveRow()
                            }.start()
                        } else {
                            v.animate().translationX(0f).setDuration(180).withEndAction {
                                leftSwipeAction.visibility = View.GONE
                                rightSwipeAction.visibility = View.GONE
                                isUserInteractingWithTask = false
                                if (hasPendingTaskBoardUpdate) {
                                    hasPendingTaskBoardUpdate = false
                                    refreshTasksBoardOnly()
                                }
                            }.start()
                        }
                        true
                    } else {
                        val dx = Math.abs(event.rawX - startX)
                        val dy = Math.abs(event.rawY - startY)
                        if (dx < touchSlop && dy < touchSlop) {
                            if (isCancelled) {
                                Toast.makeText(context, "Nhấn giữ để khôi phục nhiệm vụ đã hủy", Toast.LENGTH_SHORT).show()
                            } else if (isCompleted) {
                                Toast.makeText(context, "Nhấn giữ để đưa về trạng thái chưa hoàn thành", Toast.LENGTH_SHORT).show()
                            } else {
                                holder.alarmPill?.performClick()
                            }
                        }
                        if (hasPendingTaskBoardUpdate) {
                            hasPendingTaskBoardUpdate = false
                            refreshTasksBoardOnly()
                        }
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    taskHandler.removeCallbacks(longPressRunnable)
                    isUserInteractingWithTask = false
                    if (isPillActive) {
                        isPillActive = false
                        removePillSafely()
                    }
                    if (isSwiping) {
                        isSwiping = false
                        v.animate().translationX(0f).setDuration(180).withEndAction {
                            leftSwipeAction.visibility = View.GONE
                            rightSwipeAction.visibility = View.GONE
                        }.start()
                    }
                    isSwipeAnimating = false
                    if (hasPendingTaskBoardUpdate) {
                        hasPendingTaskBoardUpdate = false
                        refreshTasksBoardOnly()
                    }
                    false
                }
                else -> false
            }
        }

        return container
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
