package com.orca.orcaauto.domain.ai

import android.content.Context
import android.util.Log
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.data.preferences.UserPreferences
import com.orca.orcaauto.data.remote.ApiKeyConfig
import com.orca.orcaauto.data.remote.UniversalAiClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Strict FIFO Sequential AI Execution Queue with Priority Key Resolution.
 * Guarantees that:
 * 1. AI calls NEVER run concurrently (protects against rate limits, memory spikes, and device battery drain).
 * 2. Requests are processed First-In-First-Out (FIFO).
 * 3. Simultaneous requests are prioritized by session creation time (earlier created session runs first).
 * 4. Each session resolves its preferred API keys in priority order (Priority 1 -> Priority 2 -> ... -> Fallbacks).
 */
object SequentialAiQueue {

    private const val TAG = "SequentialAiQueue"

    private val executionMutex = Mutex()
    private val client = UniversalAiClient()

    /**
     * Enqueues an AI summary request for a given session and waits for its sequential execution.
     * Uses Mutex to ensure requests are processed sequentially.
     */
    suspend fun execute(
        context: Context,
        session: SummarySessionConfig,
        prompt: String
    ): Result<Pair<String, ApiKeyConfig>> {
        val userPrefs = UserPreferences.getInstance(context)
        val globalKeyPool = userPrefs.getKeyPool()

        // 1. Resolve prioritized keys for this session
        val resolvedKeys = resolveKeysForSession(session, globalKeyPool)
        if (resolvedKeys.none { it.isEnabled && it.rawKey.isNotBlank() }) {
            return Result.failure(
                IllegalArgumentException("Chưa cấu hình API Key nào hoặc các Key đang bị vô hiệu hóa trong Key Pool.")
            )
        }

        val requestedAt = System.currentTimeMillis()
        Log.i(TAG, "[AI Queue Enqueued] Session: '${session.name}' (ID: ${session.id}, Created: ${session.createdAt}, ReqAt: $requestedAt)")

        // 2. Acquire execution lock sequentially (Strict FIFO)
        return executionMutex.withLock {
            Log.i(TAG, "[AI Queue Running] Session: '${session.name}' acquired execution lock. Resolved keys: ${resolvedKeys.size}")
            try {
                val result = client.executeWithFailover(resolvedKeys, prompt)
                if (result.isSuccess) {
                    val (_, usedKey) = result.getOrThrow()
                    Log.i(TAG, "[AI Queue Completed] Session: '${session.name}' succeeded with key: ${usedKey.provider.displayName} (${usedKey.modelName})")
                } else {
                    Log.w(TAG, "[AI Queue Failed] Session: '${session.name}' failed all keys: ${result.exceptionOrNull()?.message}")
                }
                result
            } catch (e: Throwable) {
                Log.e(TAG, "[AI Queue Error] Session: '${session.name}' unhandled error: ${e.message}", e)
                Result.failure(e)
            } finally {
                Log.i(TAG, "[AI Queue Released] Session: '${session.name}' released execution lock.")
            }
        }
    }

    /**
     * Resolves and sorts the keys for a session based on its keyPriorityIds:
     * - Keys matching keyPriorityIds come first in the exact priority order.
     * - Any remaining active keys in the global pool act as fallback.
     * - If keyPriorityIds is empty, uses global pool order.
     */
    fun resolveKeysForSession(
        session: SummarySessionConfig,
        globalPool: List<ApiKeyConfig>
    ): List<ApiKeyConfig> {
        val activeKeys = globalPool.filter { it.isEnabled && it.rawKey.isNotBlank() }
        if (session.keyPriorityIds.isEmpty()) {
            return activeKeys
        }

        val prioritized = mutableListOf<ApiKeyConfig>()
        // Add keys explicitly configured in priority order
        for (id in session.keyPriorityIds) {
            val key = activeKeys.find { it.id == id }
            if (key != null && key !in prioritized) {
                prioritized.add(key)
            }
        }
        // Add remaining active keys as failover backups
        for (key in activeKeys) {
            if (key !in prioritized) {
                prioritized.add(key)
            }
        }
        return prioritized
    }

    /**
     * Orders a list of sessions for sequential batch execution (e.g. at a scheduled time slot).
     * The main session executes first, followed by sessions ordered by createdAt ascending.
     */
    fun orderSessionsForBatch(sessions: List<SummarySessionConfig>): List<SummarySessionConfig> {
        return sessions.sortedWith(
            compareByDescending<SummarySessionConfig> { it.isMain }
                .thenBy { it.createdAt }
        )
    }
}
