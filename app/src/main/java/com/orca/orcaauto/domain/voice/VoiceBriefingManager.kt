package com.orca.orcaauto.domain.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.orca.orcaauto.domain.filter.ParsedSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceBriefingManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    private val _currentSummaryIdFlow = MutableStateFlow<Long?>(null)
    val currentSummaryIdFlow: StateFlow<Long?> = _currentSummaryIdFlow.asStateFlow()

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
                Log.w(TAG, "Gói tiếng Việt chưa có sẵn trên thiết bị, sử dụng ngôn ngữ mặc định.")
            } else {
                Log.i(TAG, "TextToSpeech tiếng Việt khởi tạo thành công.")
            }
            tts?.setSpeechRate(1.05f) // Tốc độ tự nhiên, hơi nhanh nhẹ để nghe sảng khoái
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeakingFlow.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeakingFlow.value = false
                    _currentSummaryIdFlow.value = null
                    abandonAudioFocus()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeakingFlow.value = false
                    _currentSummaryIdFlow.value = null
                    abandonAudioFocus()
                }
            })
            isInitialized = true
        } else {
            Log.e(TAG, "Khởi tạo TextToSpeech thất bại: status=$status")
        }
    }

    fun speakSummary(summaryId: Long, parsed: ParsedSummary, isEnglish: Boolean = false) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TextToSpeech chưa sẵn sàng.")
            return
        }

        // Nếu đang đọc chính thẻ này thì dừng lại (Toggle Stop)
        if (_isSpeakingFlow.value && _currentSummaryIdFlow.value == summaryId) {
            stop()
            return
        }

        stop() // Dừng câu đọc cũ nếu có

        if (isEnglish) {
            tts?.setLanguage(Locale.US)
        } else {
            tts?.setLanguage(Locale("vi", "VN"))
        }

        val script = buildSpokenScript(parsed, isEnglish)
        if (script.isBlank()) return

        requestAudioFocus()
        _currentSummaryIdFlow.value = summaryId
        _isSpeakingFlow.value = true

        val utteranceId = "SUMMARY_VOICE_$summaryId"
        tts?.speak(script, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeakingFlow.value = false
        _currentSummaryIdFlow.value = null
        abandonAudioFocus()
    }

    fun buildSpokenScript(parsed: ParsedSummary, isEnglish: Boolean = false): String = Companion.buildSpokenScript(parsed, isEnglish)

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest = focusRequest
            am.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "VoiceBriefingManager"

        @Volatile
        private var INSTANCE: VoiceBriefingManager? = null

        fun getInstance(context: Context): VoiceBriefingManager {
            return INSTANCE ?: synchronized(this) {
                val instance = VoiceBriefingManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun buildSpokenScript(parsed: ParsedSummary, isEnglish: Boolean = false): String {
            val sb = StringBuilder()

            if (isEnglish) {
                // 1. Advice
                if (parsed.conciseAdvice.isNotBlank()) {
                    val cleanAdvice = cleanMarkdown(parsed.conciseAdvice)
                    sb.append("Executive advice for you: ").append(cleanAdvice).append(". ")
                }

                // 2. Tasks (To-Do & Planned)
                val activeTasks = parsed.tasksToDo + parsed.tasksPlanned
                if (activeTasks.isNotEmpty()) {
                    sb.append("Actionable tasks to complete. ")
                    activeTasks.forEachIndexed { index, task ->
                        val cleanTitle = cleanMarkdown(task.title)
                        sb.append("Task ").append(index + 1).append(": ").append(cleanTitle).append(". ")
                        if (task.deadline.isNotBlank()) {
                            sb.append("Deadline is ").append(cleanMarkdown(task.deadline)).append(". ")
                        }
                    }
                }

                // 3. Notes
                if (parsed.criticalQuestions.isNotEmpty()) {
                    sb.append("Key notes: ")
                    parsed.criticalQuestions.forEach { q ->
                        sb.append(cleanMarkdown(q)).append(". ")
                    }
                }
            } else {
                // 1. Đọc Phần Lời khuyên & Lưu ý
                if (parsed.conciseAdvice.isNotBlank()) {
                    val cleanAdvice = cleanMarkdown(parsed.conciseAdvice)
                    sb.append("Lời khuyên dành cho bạn: ").append(cleanAdvice).append(". ")
                }

                // 2. Đọc Phần Nhiệm vụ cần thực hiện (To-Do & Planned)
                val activeTasks = parsed.tasksToDo + parsed.tasksPlanned
                if (activeTasks.isNotEmpty()) {
                    sb.append("Các nhiệm vụ cần thực hiện. ")
                    activeTasks.forEachIndexed { index, task ->
                        val cleanTitle = cleanMarkdown(task.title)
                        sb.append("Nhiệm vụ ").append(index + 1).append(": ").append(cleanTitle).append(". ")
                        if (task.deadline.isNotBlank()) {
                            sb.append("Hạn chót là ").append(cleanMarkdown(task.deadline)).append(". ")
                        }
                    }
                }

                // 3. Đọc Phần Lưu ý / Phản biện nếu có
                if (parsed.criticalQuestions.isNotEmpty()) {
                    sb.append("Lưu ý quan trọng: ")
                    parsed.criticalQuestions.forEach { q ->
                        sb.append(cleanMarkdown(q)).append(". ")
                    }
                }
            }

            return sb.toString().trim()
        }

        fun cleanMarkdown(text: String): String {
            return text
                .replace(Regex("""[*#_~`>]"""), "")
                .replace(Regex("""-?\s*\[[ xX]?\]"""), "")
                .replace(Regex("""[\[\]]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }
}
