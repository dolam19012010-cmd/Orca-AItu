package com.orca.orcaauto.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.util.Log
import kotlin.math.sqrt

/**
 * ShakeDetector: Bộ nhận diện cử chỉ lắc cổ tay chuẩn công nghiệp với cơ chế Kích Xung Tần Số Động (Pre-Peak Dynamic Boost).
 *
 * 1. Chế độ Chờ (Standby - Tiết kiệm pin tối đa):
 *    - Cảm biến chạy ở tần số thấp 16Hz (SENSOR_DELAY_UI ~60ms/mẫu).
 *
 * 2. Kích Xung Đón Đầu (Pre-Peak Dynamic Boost):
 *    - Khi tay bắt đầu di chuyển lắc, gia tốc nhích lên vượt ngưỡng PRE_PEAK_THRESHOLD (4.2 m/s²).
 *    - Ngay lập tức kích xung nhịp cảm biến lên 50Hz (SENSOR_DELAY_GAME ~20ms/mẫu) trong 1.2s.
 *    - Vì cú lắc tay thực tế kéo dài 400 - 600ms, xung nhịp 50Hz đón trọn vẹn cả đỉnh xung lực nhịp 1
 *      và toàn bộ pha đảo chiều nhịp 2 mà không bao giờ bị trượt mẫu.
 *
 * 3. Nhận diện Đảo chiều 3D bằng Tích vô hướng (3D Dot Product):
 *    - Xác định vector gia tốc cực đại ban đầu (v1).
 *    - Vector đảo chiều ngược lại thỏa mãn v2 · v1 < 0.
 *    - Kích hoạt chuẩn xác sau đúng 2 nhịp (reversalCount >= 2).
 *
 * 4. Tự động Hồi về 16Hz:
 *    - Sau 1.2s nếu không có cử chỉ lắc tiếp theo, tự động hạ tần số về 16Hz.
 *    - Zero-allocation trong runtime loop để tránh tạo rác và giật khung hình (GC pauses).
 */
class ShakeDetector(
    private val onShakeListener: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"

        // Ngưỡng phát hiện bắt đầu vung tay để kích xung tần số cao (4.2 m/s²)
        const val PRE_PEAK_THRESHOLD = 4.2f

        // Ngưỡng gia tốc đỉnh của cú lắc tay thật (9.5 m/s² ~ 1.0g tuyến tính)
        const val SHAKE_THRESHOLD = 9.5f

        // Chuẩn 2 nhịp lắc tự nhiên: Vung qua -> Vung lại (v2 · v1 < 0)
        const val MIN_REVERSALS = 2

        // Cửa sổ thời gian tối đa để hoàn thành 2 nhịp (800ms)
        const val TIME_WINDOW_NS = 800_000_000L

        // Lọc nhiễu cơ học tần số cao (> 20Hz)
        const val MIN_INTERVAL_NS = 50_000_000L

        // Giới hạn thời gian tối đa giữa 2 nhịp đảo chiều (450ms)
        const val MAX_INTERVAL_NS = 450_000_000L

        // Thời gian hồi chiêu chống kích hoạt lặp (1.2s)
        const val COOLDOWN_NS = 1_200_000_000L

        // Thời gian duy trì tần số cao 50Hz trước khi tự hạ về 16Hz (1200ms)
        const val BOOST_DURATION_MS = 1200L
    }

    private var isRunning = false
    private var isBoosted = false
    private var lastShakeTimestampNs = 0L
    private var lastPeakTimestampNs = 0L
    private var reversalCount = 0

    // Vector gia tốc chuẩn của nhịp đỉnh đầu tiên để so khớp đảo chiều trong không gian 3D
    private var refPeakX = 0f
    private var refPeakY = 0f
    private var refPeakZ = 0f
    private var currentPhaseSign = 0 // 1: cùng hướng ref, -1: ngược hướng ref

    // Low-Pass Filter lưu trữ trọng lực cho trường hợp Fallback
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var isLinearSensor = false

    // Tham chiếu Sensor & Handler để kích xung nhịp động
    private var currentSensorManager: SensorManager? = null
    private var currentSensor: Sensor? = null
    private var currentHandler: Handler? = null

    private val downgradeRunnable = Runnable {
        downgradeToLowRate()
    }

    @Synchronized
    fun start(sensorManager: SensorManager, handler: Handler): Boolean {
        if (isRunning) return true

        currentSensorManager = sensorManager
        currentHandler = handler

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
        currentSensor = sensor

        // Khởi động ở chế độ chờ 16Hz (SENSOR_DELAY_UI) để bảo toàn 100% pin
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
            handler
        )
        if (registered) {
            isRunning = true
            isBoosted = false
            resetState()
            Log.d(TAG, "Đã khởi động ShakeDetector ở chế độ chờ 16Hz (Linear=$isLinearSensor)")
        } else {
            Log.e(TAG, "Không thể đăng ký SensorEventListener với SensorManager")
        }
        return registered
    }

    @Synchronized
    fun stop(sensorManager: SensorManager) {
        if (!isRunning) return
        currentHandler?.removeCallbacks(downgradeRunnable)
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi unregister SensorEventListener: ${e.message}")
        }
        isRunning = false
        isBoosted = false
        resetState()
        currentSensorManager = null
        currentSensor = null
        currentHandler = null
        Log.d(TAG, "Đã dừng ShakeDetector và đưa vào chế độ ngủ đông")
    }

    fun isStarted(): Boolean = isRunning
    fun isRateBoosted(): Boolean = isBoosted

    /**
     * Kích xung cảm biến lên 50Hz (SENSOR_DELAY_GAME) khi phát hiện cử động vung tay sớm.
     */
    private fun boostToHighRate() {
        if (isBoosted || !isRunning) return
        val sm = currentSensorManager ?: return
        val s = currentSensor ?: return
        val h = currentHandler ?: return

        try {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, h)
            isBoosted = true
            Log.d(TAG, "⚡ Kích xung cảm biến lên 50Hz (GAME) đón đỉnh xung lực lắc tay!")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể kích xung cảm biến: ${e.message}")
        }
    }

    /**
     * Tự động hạ xung nhịp cảm biến về lại 16Hz (SENSOR_DELAY_UI) để tiết kiệm pin.
     */
    private fun downgradeToLowRate() {
        if (!isBoosted || !isRunning) return
        val sm = currentSensorManager ?: return
        val s = currentSensor ?: return
        val h = currentHandler ?: return

        try {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI, h)
            isBoosted = false
            resetState()
            Log.d(TAG, "Đã tự động hạ xung cảm biến về 16Hz tiết kiệm pin")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể hạ xung cảm biến: ${e.message}")
        }
    }

    private fun scheduleDowngradeTimeout() {
        currentHandler?.let { h ->
            h.removeCallbacks(downgradeRunnable)
            h.postDelayed(downgradeRunnable, BOOST_DURATION_MS)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
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

        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        // Nếu gia tốc bắt đầu tăng (vượt ngưỡng Pre-Peak), lập tức kích xung lên 50Hz để đón đỉnh!
        if (magnitude > PRE_PEAK_THRESHOLD && !isBoosted) {
            boostToHighRate()
            scheduleDowngradeTimeout()
        } else if (isBoosted) {
            // Gia hạn thời gian duy trì 50Hz nếu người dùng vẫn đang có cử động mạnh
            if (magnitude > PRE_PEAK_THRESHOLD) {
                scheduleDowngradeTimeout()
            }
        }

        if (processAcceleration(linearX, linearY, linearZ, event.timestamp)) {
            Log.i(TAG, "⚡ Phát hiện cử chỉ lắc tay hợp lệ (2 nhịp)! Kích hoạt callback.")
            downgradeToLowRate()
            onShakeListener.invoke()
        }
    }

    /**
     * Thuật toán nhận diện cử chỉ lắc cổ tay dựa trên vector gia tốc 3 chiều.
     * Tách biệt logic để dễ dàng viết Unit Test độc lập với phần cứng.
     */
    fun processAcceleration(linearX: Float, linearY: Float, linearZ: Float, now: Long): Boolean {
        if (now - lastShakeTimestampNs < COOLDOWN_NS) return false // Đang trong thời gian hồi chiêu 1.2s

        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        if (magnitude > SHAKE_THRESHOLD) {
            if (reversalCount == 0) {
                // Nhịp 1: Lưu vector gia tốc mốc làm hướng chuẩn
                refPeakX = linearX
                refPeakY = linearY
                refPeakZ = linearZ
                currentPhaseSign = 1
                lastPeakTimestampNs = now
                reversalCount = 1
            } else {
                // Nhịp tiếp theo: Dùng tích vô hướng 3D (3D Dot Product) để xác định đảo chiều
                // dot < 0 nghĩa là góc giữa 2 vector > 90 độ (tay đang chuyển động ngược hướng ban đầu)
                val dot = (linearX * refPeakX) + (linearY * refPeakY) + (linearZ * refPeakZ)
                val newSign = if (dot < 0) -1 else 1

                if (newSign != currentPhaseSign) {
                    val timeDiff = now - lastPeakTimestampNs

                    if (timeDiff in MIN_INTERVAL_NS..MAX_INTERVAL_NS) {
                        reversalCount++
                        currentPhaseSign = newSign
                        lastPeakTimestampNs = now

                        // Thỏa mãn đúng 2 nhịp đảo chiều chuẩn -> Kích hoạt!
                        if (reversalCount >= MIN_REVERSALS) {
                            lastShakeTimestampNs = now
                            resetState()
                            return true
                        }
                    } else if (timeDiff > MAX_INTERVAL_NS) {
                        // Khoảng cách nhịp quá dài -> Khởi tạo lại chu kỳ lắc mới
                        refPeakX = linearX
                        refPeakY = linearY
                        refPeakZ = linearZ
                        currentPhaseSign = 1
                        lastPeakTimestampNs = now
                        reversalCount = 1
                    }
                }
            }
        } else {
            // Quá thời gian quan sát mà không có dao động mới -> Reset trạng thái tĩnh
            if (reversalCount > 0 && (now - lastPeakTimestampNs > TIME_WINDOW_NS)) {
                resetState()
            }
        }
        return false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Không cần xử lý khi độ chính xác cảm biến thay đổi
    }

    fun resetState() {
        reversalCount = 0
        refPeakX = 0f
        refPeakY = 0f
        refPeakZ = 0f
        currentPhaseSign = 0
        lastPeakTimestampNs = 0L
    }
}
