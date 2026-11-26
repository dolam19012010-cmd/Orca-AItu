package com.orca.orcaauto.service

import android.app.Activity
import android.os.Bundle

/**
 * Translucent foreground trampoline used by notification actions to open or wake
 * [FloatingBubbleService] cleanly on Android 8+ without bringing MainActivity in front
 * of the user's active messaging app or throwing background start exceptions.
 */
class BubbleWakeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val summary = intent?.getStringExtra(FloatingBubbleService.EXTRA_SUMMARY_CONTENT).orEmpty()
        val model = intent?.getStringExtra(FloatingBubbleService.EXTRA_MODEL_INFO).orEmpty()
        val sessionId = intent?.getStringExtra(FloatingBubbleService.EXTRA_SESSION_ID).orEmpty()

        FloatingBubbleService.show(
            context = applicationContext,
            summaryContent = summary,
            modelInfo = model,
            sessionId = sessionId,
            wakeToState1 = true
        )

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
