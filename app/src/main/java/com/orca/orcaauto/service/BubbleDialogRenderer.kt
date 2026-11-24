package com.orca.orcaauto.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.orca.orcaauto.data.local.AppDatabase
import com.orca.orcaauto.data.local.SharedNoteEntity
import com.orca.orcaauto.data.local.SmartAlarmEntity
import com.orca.orcaauto.data.local.SmartAlarmStatus
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.domain.alarm.AlarmCascadeMode
import com.orca.orcaauto.domain.alarm.SystemAlarmHelper
import com.orca.orcaauto.domain.filter.TaskItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Controller responsible for rendering floating confirmation dialogs,
 * modal task configuration dialogs, and extracted local note cards.
 *
 * Preserves zero View leaks across lifecycle changes and enforces commit invariants
 * (422ffcb: disarm hardware-only + PROPOSED status, f2912ee: rollover past alarms).
 */
class BubbleDialogRenderer(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val rootViewProvider: () -> ViewGroup?,
    var taskBoardController: BubbleTaskBoardController? = null
) {

    /**
     * Renders local note cards extracted from the user's device into the target notes container.
     */
    fun renderLocalNoteCardsInBubble(container: LinearLayout?, notes: List<SharedNoteEntity>) {
        if (container == null || notes.isEmpty()) return
        container.removeAllViews()

        val labelTv = TextView(context).apply {
            text = "📌 Ghi chú trích xuất từ máy:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }
        container.addView(labelTv)

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        for (note in notes) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#27272A"))
                    cornerRadius = dpToPx(8).toFloat()
                    setStroke(dpToPx(1), if (note.isPrivate) Color.parseColor("#38BDF8") else Color.parseColor("#3F3F46"))
                }
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(6)
                }
            }

            // Note title row
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconTv = TextView(context).apply {
                text = if (note.isPrivate) "🔒 " else "📝 "
                textSize = 12f
            }
            titleRow.addView(iconTv)

            val titleTv = TextView(context).apply {
                text = note.title
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleRow.addView(titleTv)

            if (note.isPrivate) {
                val privateBadge = TextView(context).apply {
                    text = "Riêng tư"
                    setTextColor(Color.parseColor("#38BDF8"))
                    textSize = 10f
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        cornerRadius = dpToPx(4).toFloat()
                        setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
                    }
                    setPadding(dpToPx(5), dpToPx(1), dpToPx(5), dpToPx(1))
                }
                titleRow.addView(privateBadge)
            }

            card.addView(titleRow)

            // Content
            if (note.content.isNotBlank()) {
                val contentTv = TextView(context).apply {
                    text = note.content
                    setTextColor(Color.parseColor("#E4E4E7"))
                    textSize = 11.5f
                    setLineSpacing(dpToPx(1).toFloat(), 1.15f)
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                }
                card.addView(contentTv)
            }

            // Time footer
            val timeStr = try { dateFormat.format(Date(note.updatedAt)) } catch (_: Exception) { "" }
            if (timeStr.isNotBlank()) {
                val timeTv = TextView(context).apply {
                    text = "Cập nhật: $timeStr"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 10f
                }
                card.addView(timeTv)
            }

            container.addView(card)
        }
    }

    /**
     * Displays a floating modal confirmation dialog (e.g. mic permission, task uncomplete, task restore).
     */
    fun showFloatingConfirmationDialog(
        title: String,
        message: String,
        confirmText: String = "Xác nhận",
        confirmColor: Int = Color.WHITE,
        cancelText: String = "Hủy bỏ",
        onCancel: (() -> Unit)? = null,
        onConfirm: () -> Unit
    ) {
        val root = rootViewProvider() ?: return
        if (root.findViewWithTag<View>("CONFIRM_MODAL_SCRIM") != null) return

        val dm = context.resources.displayMetrics
        val dialogWidth = (dm.widthPixels * 0.86f).toInt().coerceAtMost(dpToPx(380))

        val modalScrim = FrameLayout(context).apply {
            tag = "CONFIRM_MODAL_SCRIM"
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#80000000"))
            elevation = dpToPx(100).toFloat()
            translationZ = dpToPx(100).toFloat()
            isClickable = true
            isFocusable = true
        }

        val dialogBox = LinearLayout(context).apply {
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

        val titleTv = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        dialogBox.addView(titleTv)

        val msgTv = TextView(context).apply {
            text = message
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
            setLineSpacing(dpToPx(2).toFloat(), 1.15f)
            setPadding(0, dpToPx(8), 0, dpToPx(16))
        }
        dialogBox.addView(msgTv)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val cancelBtn = TextView(context).apply {
            text = cancelText
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            isClickable = true
            setOnClickListener {
                root.removeView(modalScrim)
                onCancel?.invoke()
            }
        }
        btnRow.addView(cancelBtn)

        val confirmBtn = TextView(context).apply {
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
            onCancel?.invoke()
        }
        dialogBox.setOnClickListener { /* consume click */ }

        root.addView(modalScrim)
        modalScrim.bringToFront()
    }

    /**
     * Displays a floating modal task configuration dialog allowing task pinning, custom alarm timing,
     * cascade alarm modes, and immediate alarm disarm.
     */
    fun showFloatingTaskConfigDialog(
        task: TaskItem,
        alarmId: String = "",
        existingAlarm: SmartAlarmEntity? = null,
        anchorView: View? = null
    ) {
        val root = rootViewProvider() ?: return
        if (root.findViewWithTag<View>("TASK_CONFIG_SCRIM") != null) return

        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val dialogWidth = (screenWidth * 0.92f).toInt().coerceAtMost(dpToPx(440))
        val dialogMaxHeight = (screenHeight * 0.74f).toInt().coerceAtMost(dpToPx(620))

        val cleanTitle = SystemAlarmHelper.cleanTaskTitle(task.title)
        val prefs = UserPreferences.getInstance(context)
        var isPinned = prefs.isTaskPinned(cleanTitle)

        val parsedDateTime = SystemAlarmHelper.parseDateTimeFromText(task.deadline)
        val targetCal = (parsedDateTime?.calendar?.clone() as? Calendar) ?: Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
        }
        var hour = targetCal.get(Calendar.HOUR_OF_DAY)
        var minute = targetCal.get(Calendar.MINUTE)
        var cascadeMode = AlarmCascadeMode.X1
        var cascadeIntervalMinutes = 15
        var isAlarmEnabled = existingAlarm?.status == SmartAlarmStatus.ACTIVE

        val modalScrim = FrameLayout(context).apply {
            tag = "TASK_CONFIG_SCRIM"
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#99000000"))
            elevation = dpToPx(100).toFloat()
            translationZ = dpToPx(100).toFloat()
            isClickable = true
            isFocusable = true
        }

        val dialogContainer = LinearLayout(context).apply {
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
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(10))
        }
        val iconTv = TextView(context).apply {
            text = "✏️"
            textSize = 16f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val headerTitle = TextView(context).apply {
            text = "Cấu Hình Nhiệm Vụ"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSub = TextView(context).apply {
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

        // Scrollable Body with safe max height constraint
        val bodyScroll = object : ScrollView(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maxHeight = (context.resources.displayMetrics.heightPixels * 0.60f).toInt().coerceAtMost(dpToPx(480))
                val customHeightSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, customHeightSpec)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isFillViewport = true
        }
        val bodyLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Task Pinning Toggle Row
        val togglePinRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(if (isPinned) Color.parseColor("#1AF59E0B") else Color.parseColor("#1A1E293B"))
                cornerRadius = dpToPx(8).toFloat()
                setStroke(dpToPx(1), if (isPinned) Color.parseColor("#33F59E0B") else Color.parseColor("#334155"))
            }
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(10)
            }
            isClickable = true
        }

        val pinIconTv = TextView(context).apply {
            text = if (isPinned) "📌" else "📍"
            textSize = 16f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val pinLabelCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val pinLabelMain = TextView(context).apply {
            text = "Ghim lên đầu danh sách"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val pinLabelSub = TextView(context).apply {
            text = if (isPinned) "Luôn hiển thị ở vị trí ưu tiên trên bảng nhiệm vụ" else "Không ghim ưu tiên"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10.5f
        }
        val pinCheckTv = TextView(context).apply {
            text = if (isPinned) "☑" else "☐"
            setTextColor(if (isPinned) Color.parseColor("#F59E0B") else Color.parseColor("#94A3B8"))
            textSize = 18f
        }
        pinLabelCol.addView(pinLabelMain)
        pinLabelCol.addView(pinLabelSub)
        togglePinRow.addView(pinIconTv)
        togglePinRow.addView(pinLabelCol)
        togglePinRow.addView(pinCheckTv)

        togglePinRow.setOnClickListener {
            isPinned = !isPinned
            pinIconTv.text = if (isPinned) "📌" else "📍"
            pinCheckTv.text = if (isPinned) "☑" else "☐"
            pinCheckTv.setTextColor(if (isPinned) Color.parseColor("#F59E0B") else Color.parseColor("#94A3B8"))
            pinLabelSub.text = if (isPinned) "Luôn hiển thị ở vị trí ưu tiên trên bảng nhiệm vụ" else "Không ghim ưu tiên"
            (togglePinRow.background as? GradientDrawable)?.apply {
                setColor(if (isPinned) Color.parseColor("#1AF59E0B") else Color.parseColor("#1A1E293B"))
                setStroke(dpToPx(1), if (isPinned) Color.parseColor("#33F59E0B") else Color.parseColor("#334155"))
            }
        }
        bodyLayout.addView(togglePinRow)

        // Section 2: Checkbox Báo Thức
        val toggleAlarmRow = LinearLayout(context).apply {
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

        val checkTv = TextView(context).apply {
            text = if (isAlarmEnabled) "☑" else "☐"
            setTextColor(if (isAlarmEnabled) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
            textSize = 18f
            setPadding(0, 0, dpToPx(8), 0)
        }
        val labelCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val labelMain = TextView(context).apply {
            text = "Đặt báo thức nhắc nhở"
            setTextColor(Color.WHITE)
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val labelSub = TextView(context).apply {
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
        val alarmConfigContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, 0)
            visibility = if (isAlarmEnabled) View.VISIBLE else View.GONE
        }

        // Time display
        val timeDisplayBox = LinearLayout(context).apply {
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
        val timeTv = TextView(context).apply {
            text = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        val dateSubTv = TextView(context).apply {
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
        val deltaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }
        listOf(-15, -5, 5, 15).forEach { delta ->
            val deltaBtn = TextView(context).apply {
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
        val cascadeLabel = TextView(context).apply {
            text = "CHẾ ĐỘ CHUÔNG BÁO LIÊN TIẾP:"
            setTextColor(Color.parseColor("#10B981"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(4))
        }
        alarmConfigContainer.addView(cascadeLabel)

        val cascadeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val cascadeViews = mutableListOf<TextView>()
        AlarmCascadeMode.entries.forEach { mode ->
            val modeBtn = TextView(context).apply {
                text = mode.code
                setTextColor(if (mode == cascadeMode) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (mode == cascadeMode) Color.parseColor("#260284C7") else Color.parseColor("#1A1E293B"))
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), if (mode == cascadeMode) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
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

        // Smart Alarm customizable interval selection: Options (5m, 10m, 15m default, 30m)
        val intervalLabel = TextView(context).apply {
            text = "KHOẢNG CÁCH GIỮA CÁC LẦN CHUÔNG:"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        alarmConfigContainer.addView(intervalLabel)

        val intervalRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }

        val intervalOptions = listOf(5, 10, 15, 30)
        val intervalViews = mutableListOf<TextView>()
        intervalOptions.forEach { minutes ->
            val intervalBtn = TextView(context).apply {
                text = "${minutes}p"
                setTextColor(if (minutes == cascadeIntervalMinutes) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(if (minutes == cascadeIntervalMinutes) Color.parseColor("#260284C7") else Color.parseColor("#1A1E293B"))
                    cornerRadius = dpToPx(6).toFloat()
                    setStroke(dpToPx(1), if (minutes == cascadeIntervalMinutes) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                }
                setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                isClickable = true
                setOnClickListener {
                    cascadeIntervalMinutes = minutes
                    intervalViews.forEachIndexed { idx, v ->
                        val m = intervalOptions[idx]
                        val sel = m == cascadeIntervalMinutes
                        v.setTextColor(if (sel) Color.parseColor("#38BDF8") else Color.parseColor("#94A3B8"))
                        (v.background as? GradientDrawable)?.apply {
                            setColor(if (sel) Color.parseColor("#260284C7") else Color.parseColor("#1A1E293B"))
                            setStroke(dpToPx(1), if (sel) Color.parseColor("#38BDF8") else Color.parseColor("#334155"))
                        }
                    }
                }
            }
            intervalViews.add(intervalBtn)
            intervalRow.addView(intervalBtn)
        }
        alarmConfigContainer.addView(intervalRow)

        // Cancel existing alarm button if active
        if (existingAlarm?.status == SmartAlarmStatus.ACTIVE) {
            val cancelAlarmBtn = TextView(context).apply {
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
                    val activeAlarmId = existingAlarm?.alarmId ?: alarmId
                    SystemAlarmHelper.disarmAlarmHardwareOnly(context, cleanTitle, activeAlarmId)
                    val proposedTime = parsedDateTime?.timeLabel ?: "Hẹn giờ"
                    val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
                    val disarmed = (existingAlarm ?: SmartAlarmEntity(
                        alarmId = activeAlarmId,
                        taskTitle = cleanTitle,
                        targetTimeMillis = targetCal.timeInMillis,
                        targetDateLabel = proposedTime,
                        status = SmartAlarmStatus.PROPOSED
                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())

                    taskBoardController?.onAlarmStateUpdated(
                        alarmId = activeAlarmId,
                        generatedAlarmId = generatedAlarmId,
                        cleanTitle = cleanTitle,
                        entity = disarmed
                    )

                    serviceScope.launch {
                        try {
                            val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                            dao.updateStatus(activeAlarmId, SmartAlarmStatus.PROPOSED)
                            dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.PROPOSED)
                        } catch (_: Exception) {}
                    }
                    isAlarmEnabled = false
                    checkTv.text = "☐"
                    checkTv.setTextColor(Color.parseColor("#94A3B8"))
                    labelSub.text = "Không đặt chuông báo cho việc này"
                    alarmConfigContainer.visibility = View.GONE
                    Toast.makeText(context, "Đã tắt chuông báo cho: ${cleanTitle.take(20)}...", Toast.LENGTH_SHORT).show()
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
        val bottomActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        val closeBtn = TextView(context).apply {
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

        val saveBtn = TextView(context).apply {
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
                prefs.setTaskPinned(cleanTitle, isPinned)

                if (isAlarmEnabled) {
                    if (existingAlarm != null) {
                        SystemAlarmHelper.disarmAlarmHardwareOnly(context, cleanTitle, existingAlarm.alarmId)
                    }
                    val res = SystemAlarmHelper.scheduleSmartAlarms(
                        context = context,
                        taskTitle = cleanTitle,
                        targetCalendar = targetCal,
                        mode = cascadeMode,
                        intervalMinutes = cascadeIntervalMinutes
                    )
                    res.onSuccess {
                        val activeAlarmId = existingAlarm?.alarmId ?: alarmId
                        val proposedTime = parsedDateTime?.timeLabel ?: "Hẹn giờ"
                        val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
                        val armed = (existingAlarm ?: SmartAlarmEntity(
                            alarmId = activeAlarmId,
                            taskTitle = cleanTitle,
                            targetTimeMillis = targetCal.timeInMillis,
                            targetDateLabel = proposedTime,
                            status = SmartAlarmStatus.ACTIVE
                        )).copy(status = SmartAlarmStatus.ACTIVE, updatedAt = System.currentTimeMillis())

                        taskBoardController?.onAlarmStateUpdated(
                            alarmId = activeAlarmId,
                            generatedAlarmId = generatedAlarmId,
                            cleanTitle = cleanTitle,
                            entity = armed
                        )

                        val pinMsg = if (isPinned) " (Đã ghim ưu tiên)" else ""
                        Toast.makeText(context, "Đã lên lịch báo thức$pinMsg (${parsedDateTime?.dateLabel ?: "Hôm nay"})!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "Lỗi đặt báo thức: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val activeAlarmId = existingAlarm?.alarmId ?: alarmId
                    SystemAlarmHelper.disarmAlarmHardwareOnly(context, cleanTitle, activeAlarmId)

                    val proposedTime = parsedDateTime?.timeLabel ?: "Hẹn giờ"
                    val generatedAlarmId = SystemAlarmHelper.generateAlarmId(cleanTitle, targetCal)
                    val disarmed = (existingAlarm ?: SmartAlarmEntity(
                        alarmId = activeAlarmId,
                        taskTitle = cleanTitle,
                        targetTimeMillis = targetCal.timeInMillis,
                        targetDateLabel = proposedTime,
                        status = SmartAlarmStatus.PROPOSED
                    )).copy(status = SmartAlarmStatus.PROPOSED, updatedAt = System.currentTimeMillis())

                    taskBoardController?.onAlarmStateUpdated(
                        alarmId = activeAlarmId,
                        generatedAlarmId = generatedAlarmId,
                        cleanTitle = cleanTitle,
                        entity = disarmed
                    )

                    serviceScope.launch {
                        try {
                            val dao = AppDatabase.getDatabase(context).smartAlarmDao()
                            dao.updateStatus(activeAlarmId, SmartAlarmStatus.PROPOSED)
                            dao.updateStatusByTitle(cleanTitle, SmartAlarmStatus.PROPOSED)
                        } catch (_: Exception) {}
                    }

                    val pinMsg = if (isPinned) "Đã ghim nhiệm vụ lên đầu!" else "Đã lưu thiết lập nhiệm vụ!"
                    Toast.makeText(context, pinMsg, Toast.LENGTH_SHORT).show()
                }

                root.removeView(modalScrim)
                taskBoardController?.refreshTasksBoardOnly()
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

    /**
     * Dismisses any active modal scrims to ensure zero memory leaks when bubble collapses or closes.
     */
    fun dismissActiveDialogs() {
        val root = rootViewProvider() ?: return
        root.findViewWithTag<View>("CONFIRM_MODAL_SCRIM")?.let { root.removeView(it) }
        root.findViewWithTag<View>("TASK_CONFIG_SCRIM")?.let { root.removeView(it) }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
