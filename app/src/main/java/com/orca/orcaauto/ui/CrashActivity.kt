package com.orca.orcaauto.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorType = intent?.getStringExtra(EXTRA_ERROR_TYPE) ?: "Lỗi không xác định"
        val errorMsg = intent?.getStringExtra(EXTRA_ERROR_MSG) ?: ""
        val errorStack = intent?.getStringExtra(EXTRA_ERROR_STACK) ?: ""

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dpToPx(20), dpToPx(36), dpToPx(20), dpToPx(20))
        }

        val headerText = TextView(this).apply {
            text = "⚠️ OrcaAuto Đã Bắt Được Lỗi Khởi Động"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        rootLayout.addView(headerText)

        val descText = TextView(this).apply {
            text = "Thay vì văng app, OrcaAuto đã giữ lại chi tiết kỹ thuật này để bạn kiểm tra:"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, dpToPx(8), 0, dpToPx(14))
        }
        rootLayout.addView(descText)

        val errorBox = TextView(this).apply {
            text = "Loại lỗi: $errorType\nChi tiết: $errorMsg\n\nStack Trace:\n$errorStack"
            setTextColor(Color.parseColor("#F1F5F9"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            addView(errorBox)
        }
        rootLayout.addView(scrollView)

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(14), 0, 0)
        }

        val copyButton = Button(this).apply {
            text = "📋 Sao Chép Lỗi"
            setBackgroundColor(Color.parseColor("#18181B"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("OrcaAuto Crash", "$errorType\n$errorMsg\n\n$errorStack")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Đã sao chép mã lỗi vào bộ nhớ tạm!", Toast.LENGTH_SHORT).show()
            }
        }
        buttonLayout.addView(copyButton)

        val restartButton = Button(this).apply {
            text = "🔄 Thử Lại"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dpToPx(8)
            }
            setOnClickListener {
                val intent = Intent(this@CrashActivity, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                finish()
            }
        }
        buttonLayout.addView(restartButton)

        rootLayout.addView(buttonLayout)
        setContentView(rootLayout)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        const val EXTRA_ERROR_TYPE = "extra_error_type"
        const val EXTRA_ERROR_MSG = "extra_error_msg"
        const val EXTRA_ERROR_STACK = "extra_error_stack"
    }
}
