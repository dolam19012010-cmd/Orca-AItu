package com.orca.orcaauto.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ShakeDetector: Bộ nhận diện cử chỉ lắc cổ tay chuẩn công nghiệp.
 *
 * Thuật toán dựa trên nguyên lý dao động lưỡng cực (Bipolar Oscillation) tương tự Moto Gestures / iOS Shake to Undo:
 * 1. Bóc tách vector gia tốc tuyến tính (loại bỏ vector trọng lực g = 9.80665 m/s²).
 * 2. Đếm số lần đảo chiều cực tính gia tốc (Direction Reversals) trên trục dao động chính:
 *    Chỉ kích hoạt khi cổ tay lắc qua - lắc lại liên tiếp ít nhất 3 nhịp (reversalCount >= 3)
 *    trong khung thời gian 750ms.
 * 3. Chống kích hoạt nhầm (False Positives):
 *    - Đi bộ / chạy bộ / xoay điện thoại: Chỉ có 1 nhịp gia tốc đơn hướng, không đủ số lần đảo chiều -> Bỏ qua.
 *    - Đi xe máy / đường dằn xóc: Tần số rung quá cao (thời gian giữa 2 đỉnh < 70ms) -> Bị lọc bởi MIN_INTERVAL_NS.
 *    - Đặt máy xuống bàn / rơi nệm: Gia tốc một chiều tắt dần -> Bỏ qua.
 * 4. Zero-allocation trong runtime loop để tránh tạo rác và giật khung hình (GC pauses).
 */
class ShakeDetector(
    private val onShakeListener: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"

        // Tham số vàng (Golden Parameters) được chuẩn hóa cho cử chỉ lắc cổ tay tự nhiên
        private const val SHAKE_THRESHOLD = 12.0f        // m/s² gia tốc tuyến tính (khoảng 1.25g)
        private const val MIN_REVERSALS = 3              // Tối thiểu 3 lần đổi cực tính (+ -> - -> +)
        private const val TIME_WINDOW_NS = 750_000_000L  // Cửa sổ quan sát 750ms
        private const val MIN_INTERVAL_NS = 70_000_000L  // Lọc nhiễu cơ học tần số cao (> 14Hz)
        private const val MAX_INTERVAL_NS = 420_000_000L // Giới hạn thời gian tối đa giữa 2 nhịp lắc (420ms)
        private const val COOLDOWN_NS = 1_200_000_000L   // 1.2s hồi chiêu chống kích hoạt lặp
    }

    private var isRunning = false
    private var lastShakeTimestampNs = 0L
    private var lastPeakTimestampNs = 0L
    private var reversalCount = 0
    private var lastSign = 0 // -1: cực âm, 1: cực dương, 0: trạng thái tĩnh

    // Low-Pass Filter lưu trữ trọng lực cho trường hợp Fallback
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var isLinearSensor = false

    @Synchronized
    fun start(sensorManager: SensorManager, handler: Handler): Boolean {
        if (isRunning) return true

        // Ưu tiên cảm biến TYPE_LINEAR_ACCELERATION (HAL loại bỏ g bằng phần cứng)
        var sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        if (sensor != null) {
            isLinearSensor = true
        } else {
            // Fallback sang Accelerometer thông thường kết hợp High-Pass Filter
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            isLinearSensor = false
        }

        if (sensor == null) {
            Log.w(TAG, "Thiết bị không hỗ trợ cảm biến gia tốc!")
            return false
        }

        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
            handler
        )
        if (registered) {
            isRunning = true
            resetState()
            Log.d(TAG, "Đã khởi động ShakeDetector (Linear=$isLinearSensor, Delay=GAME)")
        } else {
            Log.e(TAG, "Không thể đăng ký SensorEventListener với SensorManager")
        }
        return registered
    }

    @Synchronized
    fun stop(sensorManager: SensorManager) {
        if (!isRunning) return
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi unregister SensorEventListener: ${e.message}")
        }
        isRunning = false
        resetState()
        Log.d(TAG, "Đã dừng ShakeDetector và đưa vào chế độ ngủ đông")
    }

    fun isStarted(): Boolean = isRunning

    override fun onSensorChanged(event: SensorEvent) {
        val now = event.timestamp
        if (now - lastShakeTimestampNs < COOLDOWN_NS) return // Đang trong thời gian hồi chiêu 1.2s

        val linearX: Float
        val linearY: Float
        val linearZ: Float

        if (isLinearSensor) {
            linearX = event.values[0]
            linearY = event.values[1]
            linearZ = event.values[2]
        } else {
            // Tách trọng lực g = 9.81 bằng bộ lọc High-Pass với alpha = 0.8
            val alpha = 0.8f
            gravityX = alpha * gravityX + (1 - alpha) * event.values[0]
            gravityY = alpha * gravityY + (1 - alpha) * event.values[1]
            gravityZ = alpha * gravityZ + (1 - alpha) * event.values[2]
            linearX = event.values[0] - gravityX
            linearY = event.values[1] - gravityY
            linearZ = event.values[2] - gravityZ
        }

        // Tính độ lớn vector gia tốc tuyến tính 3 chiều
        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        if (magnitude > SHAKE_THRESHOLD) {
            // Xác định trục chiếm ưu thế biên độ lớn nhất (thường là trục X hoặc Y khi lắc cổ tay)
            val dominantVal = if (abs(linearX) > abs(linearY)) {
                if (abs(linearX) > abs(linearZ)) linearX else linearZ
            } else {
                if (abs(linearY) > abs(linearZ)) linearY else linearZ
            }
            val currentSign = if (dominantVal > 0) 1 else -1

            if (lastSign == 0) {
                // Nhịp đỉnh đầu tiên
                lastSign = currentSign
                lastPeakTimestampNs = now
                reversalCount = 1
            } else if (currentSign != lastSign) {
                val timeDiff = now - lastPeakTimestampNs

                // Kiểm tra khoảng cách thời gian giữa 2 nhịp đảo chiều (70ms <= dt <= 420ms)
                if (timeDiff in MIN_INTERVAL_NS..MAX_INTERVAL_NS) {
                    reversalCount++
                    lastSign = currentSign
                    lastPeakTimestampNs = now

                    // Kích hoạt khi thỏa mãn đủ số lần đảo chiều trong cửa sổ quan sát
                    if (reversalCount >= MIN_REVERSALS) {
                        lastShakeTimestampNs = now
                        resetState()
                        Log.i(TAG, "⚡ Phát hiện cử chỉ lắc tay hợp lệ! Kích hoạt callback.")
                        onShakeListener.invoke()
                    }
                } else if (timeDiff > MAX_INTERVAL_NS) {
                    // Khoảng cách nhịp quá dài (nhịp đi bộ chậm) -> Reset bắt đầu nhịp mới
                    lastSign = currentSign
                    lastPeakTimestampNs = now
                    reversalCount = 1
                }
            }
        } else {
            // Quá thời gian TIME_WINDOW_NS mà không có dao động mới -> Reset trạng thái tĩnh
            if (now - lastPeakTimestampNs > TIME_WINDOW_NS) {
                resetState()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Không cần xử lý khi độ chính xác cảm biến thay đổi
    }

    private fun resetState() {
        reversalCount = 0
        lastSign = 0
        lastPeakTimestampNs = 0L
    }
}
