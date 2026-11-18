package com.orca.orcaauto.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

/**
 * High-performance, zero-allocation sound wave visualizer view.
 *
 * Renders dynamic, vertically symmetrical rounded bars along a horizontal baseline,
 * modulated by an acoustic envelope, idle sine breathing, and incoming speech RMS dB levels.
 *
 * Supports smooth color morphing from the normal Orca palette (Violet -> Cyan -> Pink)
 * towards a warm pastel coral-red on slide-to-cancel gestures.
 *
 * Strictly adheres to GEMINI.md zero-allocation standard during onDraw.
 */
class SoundWaveVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val BAR_COUNT = 22
        private const val MIN_BAR_HEIGHT_DP = 2.5f
        private const val MAX_BAR_HEIGHT_DP = 18f
        private const val BASE_BAR_WIDTH_DP = 2.5f
        private const val BASE_BAR_SPACING_DP = 2.5f

        // Normal palette stops: Soft Indigo -> Orca Cyan -> Warm Rose Pink
        private const val COLOR_VIOLET = 0xFF818CF8.toInt()
        private const val COLOR_CYAN = 0xFF38BDF8.toInt()
        private const val COLOR_PINK = 0xFFF472B6.toInt()

        // Cancellation warning color: Warm refined pastel coral-red (not harsh neon red)
        private const val WARNING_CORAL_RED = 0xFFF87171.toInt()
    }

    // Triple-crest acoustic envelope mimicking user reference waveform
    private val baseEnvelope = floatArrayOf(
        0.22f, 0.38f, 0.28f, 0.55f, 0.85f, 1.00f, 0.72f, 0.35f,
        0.68f, 0.92f, 0.78f, 0.42f, 0.58f, 0.88f, 0.62f, 0.32f,
        0.72f, 0.96f, 0.82f, 0.48f, 0.32f, 0.20f
    )

    // Pre-allocated arrays and primitives for 100% zero-allocation rendering loop
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val barXPositions = FloatArray(BAR_COUNT)
    private val currentHeights = FloatArray(BAR_COUNT)
    private val baseColors = IntArray(BAR_COUNT)
    private val displayColors = IntArray(BAR_COUNT)

    private var isVisualizing = false
    private var normRms = 0f
    private var idlePhase = 0f
    private var cancelProgress = 0f

    init {
        // Pre-compute horizontal gradient base colors across all bars
        val half = (BAR_COUNT - 1) / 2f
        for (i in 0 until BAR_COUNT) {
            val base = if (i <= half) {
                val ratio = (i / half).coerceIn(0f, 1f)
                interpolateColorInternal(COLOR_VIOLET, COLOR_CYAN, ratio)
            } else {
                val ratio = ((i - half) / half).coerceIn(0f, 1f)
                interpolateColorInternal(COLOR_CYAN, COLOR_PINK, ratio)
            }
            baseColors[i] = base
            displayColors[i] = base
        }
    }

    /**
     * Updates RMS dB level from SpeechRecognizer (typically in range [-2f, 10f]).
     */
    fun setRmsDb(rmsdB: Float) {
        normRms = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        if (!isVisualizing) {
            postInvalidateOnAnimation()
        }
    }

    /**
     * Updates slide-to-cancel progress ratio in [0f, 1f].
     * Interpolates all bar colors towards warm coral-red.
     */
    fun setCancelProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (cancelProgress == clamped) return
        cancelProgress = clamped
        for (i in 0 until BAR_COUNT) {
            displayColors[i] = interpolateColorInternal(baseColors[i], WARNING_CORAL_RED, cancelProgress)
        }
        postInvalidateOnAnimation()
    }

    /**
     * Activates continuous wave breathing and dynamic RMS animation.
     */
    fun startVisualizer() {
        if (isVisualizing) return
        isVisualizing = true
        postInvalidateOnAnimation()
    }

    /**
     * Stops continuous animation and resets levels.
     */
    fun stopVisualizer() {
        isVisualizing = false
        normRms = 0f
        setCancelProgress(0f)
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val defaultWidth = (BAR_COUNT * BASE_BAR_WIDTH_DP + (BAR_COUNT - 1) * BASE_BAR_SPACING_DP + 16f) * density
        val defaultHeight = 28f * density

        val width = resolveSize(defaultWidth.toInt(), widthMeasureSpec)
        val height = resolveSize(defaultHeight.toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val density = resources.displayMetrics.density
        val barWidth = BASE_BAR_WIDTH_DP * density
        val barSpacing = BASE_BAR_SPACING_DP * density
        val totalBarsWidth = (BAR_COUNT * barWidth) + ((BAR_COUNT - 1) * barSpacing)
        val startX = (w - totalBarsWidth) / 2f + (barWidth / 2f)

        barPaint.strokeWidth = barWidth
        for (i in 0 until BAR_COUNT) {
            barXPositions[i] = startX + i * (barWidth + barSpacing)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerY = height / 2f
        val density = resources.displayMetrics.density
        val minHalfH = (MIN_BAR_HEIGHT_DP * density) / 2f
        val maxHalfH = (MAX_BAR_HEIGHT_DP * density) / 2f
        val rangeHalfH = maxHalfH - minHalfH

        if (isVisualizing) {
            idlePhase += 0.05f
            if (idlePhase > 6.2831855f) {
                idlePhase -= 6.2831855f
            }
        }

        for (i in 0 until BAR_COUNT) {
            if (isVisualizing) {
                val idle = sin(idlePhase + i * 0.38f) * 0.15f
                val envelope = baseEnvelope[i]
                val targetNorm = (envelope * (0.15f + 0.85f * normRms) + idle).coerceIn(0.05f, 1.0f)
                val targetH = minHalfH + rangeHalfH * targetNorm
                currentHeights[i] += (targetH - currentHeights[i]) * 0.28f
            } else {
                val targetH = minHalfH * (baseEnvelope[i] * 0.8f + 0.2f)
                currentHeights[i] += (targetH - currentHeights[i]) * 0.20f
            }

            barPaint.color = displayColors[i]
            val x = barXPositions[i]
            val halfH = currentHeights[i]
            canvas.drawLine(x, centerY - halfH, x, centerY + halfH, barPaint)
        }

        if (isVisualizing) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopVisualizer()
        super.onDetachedFromWindow()
    }

    private fun interpolateColorInternal(colorA: Int, colorB: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val aA = Color.alpha(colorA)
        val rA = Color.red(colorA)
        val gA = Color.green(colorA)
        val bA = Color.blue(colorA)

        val aB = Color.alpha(colorB)
        val rB = Color.red(colorB)
        val gB = Color.green(colorB)
        val bB = Color.blue(colorB)

        val a = (aA + f * (aB - aA)).toInt()
        val r = (rA + f * (rB - rA)).toInt()
        val g = (gA + f * (gB - gA)).toInt()
        val b = (bA + f * (bB - bA)).toInt()
        return Color.argb(a, r, g, b)
    }
}
