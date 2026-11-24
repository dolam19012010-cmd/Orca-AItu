package com.orca.orcaauto.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.ui.wallpaper.WallpaperMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages loading, caching, and applying custom frosted wallpaper backgrounds
 * to the floating card in [FloatingBubbleService].
 *
 * Preserves zero-allocation draw cycles (commit 89f2938) and eliminates View reference leaks.
 */
class BubbleWallpaperManager(
    context: Context,
    private val serviceScope: CoroutineScope
) {
    companion object {
        private const val TAG = "BubbleWallpaperMgr"
    }

    private val appContext = context.applicationContext

    @Volatile
    private var cachedWallpaperBmp: Bitmap? = null
    private var lastWallpaperPath: String? = null
    private var lastWallpaperModified: Long = 0L

    val cachedBitmap: Bitmap?
        get() = cachedWallpaperBmp

    fun preWarmWallpaper(targetCardProvider: (() -> View?)? = null) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dm = appContext.resources.displayMetrics
                val cardWidth = (dm.widthPixels * 0.94f).toInt().coerceAtMost(dpToPx(500))
                val cardHeight = (dm.heightPixels * 0.74f).toInt().coerceAtMost(dpToPx(620))
                val bmp = loadWallpaperBitmapOptimized(cardWidth, cardHeight)
                if (bmp != null && targetCardProvider != null) {
                    withContext(Dispatchers.Main) {
                        targetCardProvider()?.let { card ->
                            applyCardBackground(card, bmp)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }
    }

    fun loadWallpaperBitmapOptimized(maxWidth: Int, maxHeight: Int): Bitmap? {
        val prefs = UserPreferences.getInstance(appContext)
        val path = prefs.getWallpaperPath()
        if (path.isNullOrBlank()) {
            cachedWallpaperBmp = null
            lastWallpaperPath = null
            return null
        }

        val file = File(path)
        if (!file.exists()) {
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
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            cachedWallpaperBmp = bmp
            lastWallpaperPath = path
            lastWallpaperModified = modTime
            bmp
        } catch (t: Throwable) {
            Log.e(TAG, "Lỗi tải ảnh nền: ${t.message}")
            cachedWallpaperBmp = null
            null
        }
    }

    fun applyCardBackground(card: View, wallpaperBmp: Bitmap? = cachedWallpaperBmp) {
        val prefs = UserPreferences.getInstance(appContext)
        val wpMode = prefs.getWallpaperMode()
        if (wallpaperBmp != null) {
            val scrimColor = when (wpMode) {
                WallpaperMode.CLEAR -> Color.parseColor("#7309090B")
                WallpaperMode.DYNAMIC -> Color.parseColor("#8009090B")
                WallpaperMode.BLURRED -> Color.parseColor("#8C09090B")
            }
            card.background = FrostedWallpaperDrawable(
                bitmap = wallpaperBmp,
                cornerRadius = dpToPx(24).toFloat(),
                scrimColor = scrimColor,
                strokeColor = Color.parseColor("#4DFFFFFF"),
                strokeWidth = dpToPx(1).toFloat()
            )
        } else {
            card.background = GradientDrawable().apply {
                setColor(Color.parseColor("#F509090B"))
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#33FFFFFF"))
            }
        }
    }

    fun release() {
        synchronized(this) {
            cachedWallpaperBmp?.recycle()
            cachedWallpaperBmp = null
            lastWallpaperPath = null
            lastWallpaperModified = 0L
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            appContext.resources.displayMetrics
        ).toInt()
    }
}

/**
 * Custom [Drawable] that renders a frosted-glass background using the user's wallpaper.
 *
 * Implements zero-allocation draw cycles (commit 89f2938) by caching [RectF] bounds objects
 * and updating them exclusively in [onBoundsChange].
 */
internal class FrostedWallpaperDrawable(
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
    private val drawRectF = RectF()
    private val strokeRectF = RectF()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        drawRectF.set(bounds)
        strokeRectF.set(bounds)
        if (strokeWidth > 0f) strokeRectF.inset(strokeWidth / 2f, strokeWidth / 2f)
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
        canvas.drawRoundRect(drawRectF, cornerRadius, cornerRadius, paint)
        canvas.drawRoundRect(drawRectF, cornerRadius, cornerRadius, scrimPaint)
        if (strokeWidth > 0f) {
            val halfStroke = strokeWidth / 2f
            canvas.drawRoundRect(strokeRectF, cornerRadius - halfStroke, cornerRadius - halfStroke, strokePaint)
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
