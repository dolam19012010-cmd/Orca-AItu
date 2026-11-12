package com.orca.orcaauto.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.wallpaper.WallpaperMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppWallpaperBackground(
    wallpaperPath: String?,
    mode: WallpaperMode,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var loadedBitmap by remember(wallpaperPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(wallpaperPath) {
        if (!wallpaperPath.isNullOrBlank()) {
            val file = File(wallpaperPath)
            if (file.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        val opts = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(file.absolutePath, opts)
                        // Downsample if very high resolution to prevent memory spikes
                        var sample = 1
                        while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) {
                            sample *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }
                        val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                        loadedBitmap = bmp
                    } catch (e: Throwable) {
                        android.util.Log.e("Wallpaper", "Lỗi tải ảnh nền: ${e.message}")
                        loadedBitmap = null
                    }
                }
            } else {
                loadedBitmap = null
            }
        } else {
            loadedBitmap = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (loadedBitmap == null) {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0F172A),
                            Color(0xFF161F38),
                            Color(0xFF1E1B4B),
                            Color(0xFF0F172A)
                        )
                    )
                } else {
                    Brush.linearGradient(listOf(BackgroundDark, BackgroundDark))
                }
            )
    ) {
        if (loadedBitmap != null) {
            val imageBitmap = remember(loadedBitmap) { loadedBitmap!!.asImageBitmap() }

            // Render Wallpaper according to selected Mode
            when (mode) {
                WallpaperMode.CLEAR -> {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // High-legibility translucent dark scrim (Liquid Glass friendly)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color(0xFF0F172A).copy(alpha = 0.48f)
                                    )
                                )
                            )
                    )
                }

                WallpaperMode.BLURRED -> {
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(22.dp)
                    } else {
                        Modifier
                    }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(blurModifier)
                    )
                    // Frosted glass dark overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A).copy(alpha = 0.50f))
                    )
                }

                WallpaperMode.DYNAMIC -> {
                    // Isolated in separate sub-composable: only dynamic wallpaper redraws, child content() never recomposes
                    DynamicWallpaperLayer(imageBitmap = imageBitmap)
                }
            }
        }

        // Child App UI on top (Zero unnecessary recompositions during gyro motion)
        content()
    }
}

@Composable
private fun DynamicWallpaperLayer(imageBitmap: ImageBitmap) {
    val (rawTiltX, rawTiltY) = rememberGyroscopeTilt(enabled = true)

    val smoothTiltX by animateFloatAsState(
        targetValue = rawTiltX,
        animationSpec = spring(stiffness = 260f, dampingRatio = 0.72f),
        label = "TiltX"
    )
    val smoothTiltY by animateFloatAsState(
        targetValue = rawTiltY,
        animationSpec = spring(stiffness = 260f, dampingRatio = 0.72f),
        label = "TiltY"
    )

    Image(
        bitmap = imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Subtle baseline zoom (1.10x) - fixed scale, zero sudden zoom pop on launch
                scaleX = 1.10f
                scaleY = 1.10f

                // Generous 2D translation amplitude (18dp horizontal & vertical) comfortably inside 1.10x margin
                translationX = smoothTiltX * 18.dp.toPx()
                translationY = smoothTiltY * 18.dp.toPx()

                // Lively 3D perspective rotation on both axes
                rotationY = smoothTiltX * 4.2f
                rotationX = -smoothTiltY * 4.2f
                cameraDistance = 16f * density
            }
    )
    // Translucent dark gradient scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.30f),
                        Color(0xFF0F172A).copy(alpha = 0.42f)
                    )
                )
            )
    )
}

@Composable
private fun rememberGyroscopeTilt(enabled: Boolean): Pair<Float, Float> {
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var isAppResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAppResumed = true
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                isAppResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    val shouldListen = enabled && isAppResumed

    DisposableEffect(shouldListen) {
        if (!shouldListen) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            // Dynamic Continuous Auto-Centering (Quy hồi góc tham chiếu)
            private var hasInit = false
            private var refX = 0f
            private var refY = 0f
            private var currentTiltX = 0f
            private var currentTiltY = 0f

            // Sensitivity threshold (~0.22 rad or ~12.6 degrees tilt for full deflection)
            private val maxSpan = 0.22f
            // Regression rate: continuously pulls reference center towards current holding posture (~1s half-life)
            // Ensures continuous auto-centering active at every instant without getting pegged at the edges
            private val regressionRate = 0.022f

            private fun angleDiff(target: Float, current: Float): Float {
                var diff = target - current
                while (diff < -Math.PI.toFloat()) diff += (2 * Math.PI).toFloat()
                while (diff > Math.PI.toFloat()) diff -= (2 * Math.PI).toFloat()
                return diff
            }

            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val rawX: Float
                val rawY: Float

                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    rawX = orientation[2] // roll (radians)
                    rawY = orientation[1] // pitch (radians)
                } else {
                    val ax = event.values[0] / 9.81f
                    val ay = event.values[1] / 9.81f
                    rawX = -ax
                    rawY = ay
                }

                if (!hasInit) {
                    hasInit = true
                    refX = rawX
                    refY = rawY
                    tiltX = 0f
                    tiltY = 0f
                    return
                }

                // Calculate angular delta relative to current reference center
                val deltaX = angleDiff(rawX, refX)
                val deltaY = angleDiff(rawY, refY)

                // Continuous regression: smoothly pulls reference center towards current posture
                // Prevents hitting hard limits ("tránh việc xoay điện thoại là kịch góc hiệu ứng về sau")
                refX += deltaX * regressionRate
                refY += deltaY * regressionRate

                val targetX = (deltaX / maxSpan).coerceIn(-1f, 1f)
                val targetY = (deltaY / maxSpan).coerceIn(-1f, 1f)

                // Responsive low-pass filter for smooth, lively motion without micro-jitter
                currentTiltX += (targetX - currentTiltX) * 0.45f
                currentTiltY += (targetY - currentTiltY) * 0.45f

                tiltX = currentTiltX
                tiltY = currentTiltY
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return Pair(tiltX, tiltY)
}
