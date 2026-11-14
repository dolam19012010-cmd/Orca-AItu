package com.orca.orcaauto.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.orca.orcaauto.data.preferences.AssistantThinkingLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Dedicated streaming AI client with Hedged Requests (Spike Racing).
 *
 * Runs completely independent from UniversalAiClient and SequentialAiQueue.executionMutex,
 * ensuring background summary jobs never block real-time chat interactions.
 *
 * Key features:
 * - Server-Sent Events (SSE) streaming for Google Gemini and OpenAI-compatible endpoints.
 * - Dynamic spike detection via token arrival heartbeats (spikeThresholdMs, stallThresholdMs).
 * - Hedged race: first worker yielding valid stream content becomes Leader; loser streams
 *   are immediately terminated via call.cancel() and coroutine cancellation.
 * - Hard ceiling timeout (hardTimeoutMs, default 120s) with clean socket termination.
 * - Robust OkHttp response cleanup in try-finally blocks.
 */
class StreamingAiClient(
    private val httpClient: OkHttpClient = defaultStreamingClient
) {

    /**
     * Internal tracking state for an active streaming worker.
     */
    private class WorkerHandle(
        val workerId: Int,
        val config: ApiKeyConfig,
        val callRef: AtomicReference<Call?> = AtomicReference(null),
        val jobRef: AtomicReference<Job?> = AtomicReference(null)
    ) {
        fun cancel() {
            try {
                callRef.get()?.cancel()
            } catch (_: Throwable) {}
            try {
                jobRef.get()?.cancel()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Streams responses from AI models with Spike Racing (Hedged Requests).
     *
     * @param keyPool List of candidate API keys, ordered by priority.
     * @param prompt User prompt text.
     * @param systemInstruction Optional persona or system instruction.
     * @param spikeThresholdMs Initial wait threshold before launching a hedged backup worker (default 10s).
     * @param stallThresholdMs In-flight stall threshold before launching a hedged backup worker (default 15s).
     * @param hardTimeoutMs Overall hard timeout limit for the entire streaming flow (default 120s).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun streamWithSpikeRacing(
        keyPool: List<ApiKeyConfig>,
        prompt: String,
        systemInstruction: String = "",
        spikeThresholdMs: Long = 10_000L,
        stallThresholdMs: Long = 15_000L,
        hardTimeoutMs: Long = 120_000L,
        thinkingLevel: AssistantThinkingLevel = AssistantThinkingLevel.BALANCED
    ): Flow<StreamUiEvent> = channelFlow {
        if (prompt.isBlank()) {
            send(StreamUiEvent.Error(IllegalArgumentException("Prompt must not be blank")))
            return@channelFlow
        }

        val eligibleKeys = keyPool.filter { it.isEnabled && it.rawKey.isNotBlank() }
        if (eligibleKeys.isEmpty()) {
            send(StreamUiEvent.Error(IllegalStateException("No valid and enabled API keys available in pool")))
            return@channelFlow
        }

        val totalStartTime = System.currentTimeMillis()
        val leaderId = AtomicReference<Int?>(null)
        val lastHeartbeat = AtomicLong(totalStartTime)
        val activeWorkers = ConcurrentHashMap<Int, WorkerHandle>()
        val workerCounter = AtomicInteger(0)
        val keyIndex = AtomicInteger(0)
        val fullContentAcc = StringBuilder()
        val fullThoughtAcc = StringBuilder()
        val isCompleted = AtomicBoolean(false)
        val completionSignal = CompletableDeferred<Unit>()

        fun cancelAllWorkers() {
            for ((_, handle) in activeWorkers) {
                handle.cancel()
            }
            activeWorkers.clear()
        }

        fun launchWorker(key: ApiKeyConfig) {
            if (isCompleted.get()) return
            val workerId = workerCounter.incrementAndGet()
            val handle = WorkerHandle(workerId, key)
            activeWorkers[workerId] = handle

            val job = launch(Dispatchers.IO) {
                handle.jobRef.set(coroutineContext[Job])
                try {
                    executeSingleWorker(
                        handle = handle,
                        key = key,
                        prompt = prompt,
                        systemInstruction = systemInstruction,
                        totalStartTime = totalStartTime,
                        leaderId = leaderId,
                        lastHeartbeat = lastHeartbeat,
                        activeWorkers = activeWorkers,
                        fullContentAcc = fullContentAcc,
                        fullThoughtAcc = fullThoughtAcc,
                        isCompleted = isCompleted,
                        completionSignal = completionSignal,
                        thinkingLevel = thinkingLevel
                    )
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        return@launch
                    }

                    activeWorkers.remove(workerId)
                    if (isCompleted.get()) return@launch

                    val currentLeader = leaderId.get()
                    if (currentLeader == workerId) {
                        // Leader failed mid-stream after emitting content
                        if (!isCompleted.getAndSet(true)) {
                            send(StreamUiEvent.Error(e))
                            cancelAllWorkers()
                            completionSignal.complete(Unit)
                        }
                    } else if (currentLeader == null) {
                        // Failed before any leader was elected
                        if (activeWorkers.isEmpty()) {
                            val nextIdx = keyIndex.getAndIncrement()
                            if (nextIdx < eligibleKeys.size) {
                                launchWorker(eligibleKeys[nextIdx])
                            } else {
                                if (!isCompleted.getAndSet(true)) {
                                    send(StreamUiEvent.Error(e))
                                    cancelAllWorkers()
                                    completionSignal.complete(Unit)
                                }
                            }
                        }
                    }
                } finally {
                    activeWorkers.remove(workerId)
                    handle.cancel()
                }
            }
            handle.jobRef.set(job)
        }

        // Launch initial primary worker
        launchWorker(eligibleKeys[keyIndex.getAndIncrement()])

        // Watchdog coroutine monitoring token heartbeats for spike & stall detection
        val watchdogJob = launch(Dispatchers.Default) {
            while (!isCompleted.get() && isActive) {
                delay(300)
                if (isCompleted.get()) break
                val now = System.currentTimeMillis()
                val timeSinceHeartbeat = now - lastHeartbeat.get()
                val currentLeader = leaderId.get()

                if (currentLeader == null) {
                    // Initial connection spike detected: primary worker took longer than spikeThresholdMs
                    if (timeSinceHeartbeat >= spikeThresholdMs) {
                        val nextIdx = keyIndex.getAndIncrement()
                        if (nextIdx < eligibleKeys.size) {
                            val backupKey = eligibleKeys[nextIdx]
                            send(
                                StreamUiEvent.SpikeDetected(
                                    delayMs = timeSinceHeartbeat,
                                    backupProvider = backupKey.provider.displayName,
                                    backupModel = backupKey.modelName
                                )
                            )
                            lastHeartbeat.set(now)
                            launchWorker(backupKey)
                        }
                    }
                } else {
                    // In-flight stall detected: leader stopped yielding tokens for longer than stallThresholdMs
                    if (timeSinceHeartbeat >= stallThresholdMs) {
                        val nextIdx = keyIndex.getAndIncrement()
                        if (nextIdx < eligibleKeys.size) {
                            val backupKey = eligibleKeys[nextIdx]
                            send(
                                StreamUiEvent.SpikeDetected(
                                    delayMs = timeSinceHeartbeat,
                                    backupProvider = backupKey.provider.displayName,
                                    backupModel = backupKey.modelName,
                                    isInFlightReset = true
                                )
                            )
                            lastHeartbeat.set(now)

                            // Cancel stalled leader and re-open leader slot for backup worker
                            val stalledWorker = activeWorkers[currentLeader]
                            stalledWorker?.cancel()
                            activeWorkers.remove(currentLeader)
                            leaderId.set(null)
                            fullContentAcc.setLength(0)
                            fullThoughtAcc.setLength(0)

                            launchWorker(backupKey)
                        }
                    }
                }
            }
        }

        // Guard the entire flow with hard timeout ceiling
        try {
            withTimeout(hardTimeoutMs) {
                completionSignal.await()
            }
        } catch (e: TimeoutCancellationException) {
            if (!isCompleted.getAndSet(true)) {
                send(StreamUiEvent.Error(java.util.concurrent.TimeoutException("Streaming timed out after ${hardTimeoutMs}ms")))
            }
        } finally {
            watchdogJob.cancel()
            cancelAllWorkers()
        }
    }

    private suspend fun ProducerScope<StreamUiEvent>.executeSingleWorker(
        handle: WorkerHandle,
        key: ApiKeyConfig,
        prompt: String,
        systemInstruction: String,
        totalStartTime: Long,
        leaderId: AtomicReference<Int?>,
        lastHeartbeat: AtomicLong,
        activeWorkers: ConcurrentHashMap<Int, WorkerHandle>,
        fullContentAcc: StringBuilder,
        fullThoughtAcc: StringBuilder,
        isCompleted: AtomicBoolean,
        completionSignal: CompletableDeferred<Unit>,
        thinkingLevel: AssistantThinkingLevel
    ) {
        val request = if (key.provider == AiProviderType.GEMINI) {
            buildGeminiRequest(key, prompt, systemInstruction, thinkingLevel)
        } else {
            buildOpenAiCompatibleRequest(key, prompt, systemInstruction, thinkingLevel)
        }

        if (isCompleted.get()) return
        val existingLeader = leaderId.get()
        if (existingLeader != null && existingLeader != handle.workerId) {
            return
        }

        val call = httpClient.newCall(request)
        handle.callRef.set(call)

        val response = try {
            call.execute()
        } catch (e: Exception) {
            if (call.isCanceled() || e is CancellationException) {
                return
            }
            throw e
        }

        try {
            val code = response.code
            val maskedUrl = if (key.provider == AiProviderType.GEMINI) {
                "https://generativelanguage.googleapis.com/v1beta/models/${key.modelName}:streamGenerateContent?alt=sse&key=${key.maskedKey}"
            } else key.endpointUrl

            if (!response.isSuccessful) {
                val errBody = try { response.body?.string().orEmpty() } catch (_: Exception) { "" }
                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = key.provider.displayName,
                        model = key.modelName,
                        endpoint = maskedUrl,
                        httpCode = code,
                        isSuccess = false,
                        durationMs = System.currentTimeMillis() - totalStartTime,
                        promptPreview = prompt.take(160),
                        errorMessage = "HTTP $code: $errBody"
                    )
                )
                throw IOException("HTTP $code (${key.provider.displayName} - ${key.modelName}): $errBody")
            }

            val body = response.body ?: throw IOException("Empty response body from ${key.provider.displayName}")
            val source = body.source()
            var startedEmitted = false

            while (coroutineContext.isActive && !Thread.currentThread().isInterrupted) {
                val line = try {
                    source.readUtf8Line() ?: break
                } catch (e: Exception) {
                    if (call.isCanceled() || e is CancellationException) {
                        return
                    }
                    throw e
                }

                if (StreamingSseParser.isDone(line)) {
                    break
                }

                val parsedPairs = if (key.provider == AiProviderType.GEMINI) {
                    StreamingSseParser.parseGeminiLine(line)
                } else {
                    StreamingSseParser.parseOpenAiCompatibleLine(line)
                }

                if (parsedPairs.isEmpty()) {
                    continue
                }

                // First worker yielding valid stream content becomes Leader
                if (leaderId.compareAndSet(null, handle.workerId)) {
                    // Winner crowned! Immediately cancel all loser streams
                    for ((id, otherHandle) in activeWorkers) {
                        if (id != handle.workerId) {
                            otherHandle.cancel()
                        }
                    }
                }

                // Loser worker check: terminate immediately if another worker won the race
                if (leaderId.get() != handle.workerId) {
                    handle.cancel()
                    return
                }

                // Leader emits Started event on first valid output
                if (!startedEmitted) {
                    send(StreamUiEvent.Started(key.provider, key.modelName))
                    startedEmitted = true
                }

                // Token arrival updates the liveness heartbeat
                lastHeartbeat.set(System.currentTimeMillis())

                // Forward incremental tokens to flow
                for ((text, isThought) in parsedPairs) {
                    if (isThought) {
                        fullThoughtAcc.append(text)
                        send(StreamUiEvent.ThoughtDelta(text))
                    } else {
                        fullContentAcc.append(text)
                        send(StreamUiEvent.ContentDelta(text))
                    }
                }
            }

            // If this worker finished without emitting content and without claiming leader
            if (leaderId.get() == null) {
                throw IOException("Stream ended prematurely without emitting content from ${key.provider.displayName}")
            }

            // Stream completed successfully by the Leader
            if (leaderId.get() == handle.workerId && !isCompleted.getAndSet(true)) {
                val totalDuration = System.currentTimeMillis() - totalStartTime
                val finalContent = fullContentAcc.toString()
                val finalThought = fullThoughtAcc.toString()

                ApiDebugLogManager.log(
                    ApiDebugLogEntry(
                        provider = key.provider.displayName,
                        model = key.modelName,
                        endpoint = maskedUrl,
                        httpCode = 200,
                        isSuccess = true,
                        durationMs = totalDuration,
                        promptPreview = prompt.take(160),
                        rawResponseSnippet = finalContent.take(200)
                    )
                )

                send(
                    StreamUiEvent.Complete(
                        fullContent = finalContent,
                        fullThought = finalThought,
                        usedKey = key,
                        durationMs = totalDuration
                    )
                )
                completionSignal.complete(Unit)
            }
        } finally {
            response.close()
        }
    }

    private fun buildGeminiRequest(
        config: ApiKeyConfig,
        prompt: String,
        systemInstruction: String,
        thinkingLevel: AssistantThinkingLevel
    ): Request {
        val model = config.modelName.ifBlank { "gemini-3.8-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=${config.rawKey}"

        val jsonBody = JsonObject().apply {
            val contentsArray = JsonArray().apply {
                val contentObj = JsonObject().apply {
                    addProperty("role", "user")
                    val partsArray = JsonArray().apply {
                        val partObj = JsonObject().apply {
                            addProperty("text", prompt)
                        }
                        add(partObj)
                    }
                    add("parts", partsArray)
                }
                add(contentObj)
            }
            add("contents", contentsArray)

            if (systemInstruction.isNotBlank()) {
                val sysInstructionObj = JsonObject().apply {
                    val sysPartsArray = JsonArray().apply {
                        val sysPart = JsonObject().apply {
                            addProperty("text", systemInstruction)
                        }
                        add(sysPart)
                    }
                    add("parts", sysPartsArray)
                }
                add("systemInstruction", sysInstructionObj)
            }

            // Include thinking config for Gemini 2.0+ and 3.x models
            if (!model.contains("1.5")) {
                val budget = thinkingLevel.geminiBudget
                val genConfig = JsonObject().apply {
                    val thinkingConfig = JsonObject().apply {
                        addProperty("thinkingBudget", budget)
                        addProperty("includeThoughts", budget > 0)
                    }
                    add("thinkingConfig", thinkingConfig)
                }
                add("generationConfig", genConfig)
            }
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return Request.Builder()
            .url(url)
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("X-Accel-Buffering", "no")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
    }

    private fun buildOpenAiCompatibleRequest(
        config: ApiKeyConfig,
        prompt: String,
        systemInstruction: String,
        thinkingLevel: AssistantThinkingLevel
    ): Request {
        val endpointUrl = config.endpointUrl.ifBlank {
            when (config.provider) {
                AiProviderType.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                AiProviderType.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
                AiProviderType.NVIDIA -> "https://integrate.api.nvidia.com/v1/chat/completions"
                else -> "https://openrouter.ai/api/v1/chat/completions"
            }
        }

        val jsonBody = JsonObject().apply {
            addProperty("model", config.modelName.ifBlank { config.provider.defaultModel })
            addProperty("stream", true)
            addProperty("temperature", 0.3)

            val messagesArray = JsonArray().apply {
                val sysPrompt = systemInstruction.trim().ifBlank { "Bạn là một Trợ lý Điều hành AI Chuyên nghiệp." }
                val mergedUserContent = when {
                    sysPrompt.isNotBlank() && prompt.isNotBlank() -> "$sysPrompt\n\n$prompt"
                    sysPrompt.isNotBlank() -> sysPrompt
                    else -> prompt
                }

                val userMsg = JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", mergedUserContent)
                }
                add(userMsg)
            }
            add("messages", messagesArray)

            // OpenRouter reasoning effort support (low, medium, high)
            if (config.provider == AiProviderType.OPENROUTER) {
                val reasoningObj = JsonObject().apply {
                    addProperty("effort", thinkingLevel.openRouterEffort)
                }
                add("reasoning", reasoningObj)
            }
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val builder = Request.Builder()
            .url(endpointUrl)
            .addHeader("Authorization", "Bearer ${config.rawKey}")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("X-Accel-Buffering", "no")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)

        if (config.provider == AiProviderType.OPENROUTER) {
            builder.addHeader("HTTP-Referer", "https://github.com/orcaauto")
            builder.addHeader("X-Title", "OrcaAuto Android")
        }

        return builder.build()
    }

    companion object {
        val defaultStreamingClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
