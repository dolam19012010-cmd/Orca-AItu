package com.orca.orcaauto.data.remote

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Immutable
data class ApiDebugLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String,
    val model: String,
    val endpoint: String,
    val httpCode: Int? = null,
    val isSuccess: Boolean,
    val durationMs: Long = 0L,
    val promptPreview: String = "",
    val rawResponseSnippet: String = "",
    val errorMessage: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()).format(Date(timestamp))
}

object ApiDebugLogManager {
    private const val MAX_LOGS = 60
    private val logList = mutableListOf<ApiDebugLogEntry>()

    private val _logsFlow = MutableStateFlow<List<ApiDebugLogEntry>>(emptyList())
    val logsFlow: StateFlow<List<ApiDebugLogEntry>> = _logsFlow.asStateFlow()

    @Synchronized
    fun log(entry: ApiDebugLogEntry) {
        if (logList.size >= MAX_LOGS) {
            logList.removeAt(logList.size - 1)
        }
        logList.add(0, entry)
        _logsFlow.value = logList.toList()
    }

    @Synchronized
    fun clear() {
        logList.clear()
        _logsFlow.value = emptyList()
    }

    @Synchronized
    fun getLogs(): List<ApiDebugLogEntry> = logList.toList()

    @Synchronized
    fun exportFormattedText(): String {
        if (logList.isEmpty()) return "Chưa có nhật ký gọi API nào được ghi nhận."
        val sb = StringBuilder()
        sb.append("=== ORCA AUTO - NHẬT KÝ DEBUG API (DÀNH CHO NHÀ PHÁT TRIỂN) ===\n")
        sb.append("Thời gian xuất: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("Tổng số bản ghi: ${logList.size}\n\n")

        logList.forEachIndexed { index, log ->
            sb.append("--------------------------------------------------\n")
            sb.append("#${index + 1} [${log.formattedTime}] - ${if (log.isSuccess) "✓ THÀNH CÔNG" else "✗ THẤT BẠI"}\n")
            sb.append("Nhà cung cấp: ${log.provider} | Model: ${log.model}\n")
            sb.append("Mã phản hồi: ${log.httpCode?.let { "HTTP $it" } ?: "Lỗi kết nối / Không có HTTP Code"}\n")
            sb.append("Thời gian phản hồi: ${log.durationMs}ms\n")
            sb.append("Endpoint: ${log.endpoint}\n")
            if (log.promptPreview.isNotBlank()) {
                sb.append("Prompt tóm tắt: ${log.promptPreview.take(160)}...\n")
            }
            if (!log.errorMessage.isNullOrBlank()) {
                sb.append("Chi tiết lỗi máy chủ:\n${log.errorMessage}\n")
            }
            if (log.rawResponseSnippet.isNotBlank()) {
                sb.append("Phản hồi thô (Snippet):\n${log.rawResponseSnippet.take(300)}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}
