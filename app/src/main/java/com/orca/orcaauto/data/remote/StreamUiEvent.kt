package com.orca.orcaauto.data.remote

import androidx.compose.runtime.Immutable

/**
 * Sealed hierarchy of UI events emitted by [StreamingAiClient].
 * Represents incremental tokens, internal reasoning/thoughts, connection state,
 * hedged spike detection, and final completion.
 */
@Immutable
sealed interface StreamUiEvent {
    /** Connection established and model actively streaming */
    data class Started(val provider: AiProviderType, val modelName: String) : StreamUiEvent

    /** Incremental reasoning/thinking token (Chain of Thought / Thoughts) */
    data class ThoughtDelta(val text: String) : StreamUiEvent

    /** Incremental answer content token displayed to the user */
    data class ContentDelta(val text: String) : StreamUiEvent

    /** Watchdog detected latency spike; hedged backup worker launched */
    data class SpikeDetected(
        val delayMs: Long,
        val backupProvider: String,
        val backupModel: String,
        val isInFlightReset: Boolean = false
    ) : StreamUiEvent

    /** Entire stream completed successfully */
    data class Complete(
        val fullContent: String,
        val fullThought: String,
        val usedKey: ApiKeyConfig,
        val durationMs: Long
    ) : StreamUiEvent

    /** Unrecoverable error encountered across all candidate keys or timeouts */
    data class Error(val throwable: Throwable) : StreamUiEvent
}
