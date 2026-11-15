package com.orca.orcaauto.data.remote

import java.io.IOException

/**
 * Exception thrown when an AI provider returns HTTP 429 or quota exhaustion payloads.
 */
class RateLimitException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Utility for detecting rate-limiting (HTTP 429) or quota exhaustion across LLM providers.
 */
object RateLimitDetector {

    /**
     * Determines whether an HTTP status code or response body represents rate-limiting / quota exhaustion.
     */
    fun isRateLimit(httpCode: Int, responseBody: String = ""): Boolean {
        if (httpCode == 429) return true
        val lower = responseBody.lowercase()
        return lower.contains("resource_exhausted") ||
                lower.contains("quota exceeded") ||
                lower.contains("rate limit") ||
                lower.contains("too many requests") ||
                lower.contains("exhausted your capacity")
    }

    /**
     * Recursively inspects a Throwable to determine if it represents a rate-limit condition.
     */
    fun isRateLimit(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        var curr: Throwable? = throwable
        while (curr != null) {
            if (curr is RateLimitException) return true
            val msg = curr.message?.lowercase().orEmpty()
            if (msg.contains("429") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota exceeded") ||
                msg.contains("rate limit") ||
                msg.contains("too many requests")
            ) {
                return true
            }
            curr = curr.cause
        }
        return false
    }
}
