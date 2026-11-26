package com.orca.orcaauto.domain.filter

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.SummarySessionConfig
import com.orca.orcaauto.domain.assistant.MessageCoalescer

/**
 * Filters recorded notifications specifically for a SummarySessionConfig.
 * Supports exact matching, normalized accents, group/conversation metadata, and Asterisk (*) wildcards.
 */
object SessionMessageFilter {

    private val UNREAD_COUNTER_SUFFIX_REGEX = Regex("""\s*\(\d+(?:\s+[^)]*)?\)\s*$""")

    /**
     * Filters a list of NotificationEntity according to the session's whitelist, banlist, and mode.
     */
    fun filter(
        notifications: List<NotificationEntity>,
        session: SummarySessionConfig
    ): List<NotificationEntity> {
        return notifications.filter { entity ->
            isNotificationAllowed(entity, session)
        }
    }

    /**
     * Extracts normalized matching candidates for sender, group/conversation title, speaker,
     * colon-separated prefix/suffix, unread-counter-stripped title, and app name.
     */
    internal fun extractNormalizedMatchCandidates(entity: NotificationEntity): Set<String> {
        val candidates = linkedSetOf<String>()
        val rawSender = entity.sender.trim()

        fun addIfNotBlank(raw: String) {
            val norm = NotificationFilter.normalize(raw)
            if (norm.isNotBlank()) candidates.add(norm)
        }

        if (rawSender.isNotBlank()) {
            addIfNotBlank(rawSender)
            val convTitle = MessageCoalescer.resolveConversationTitle(entity)
            addIfNotBlank(convTitle)
            addIfNotBlank(MessageCoalescer.resolveSpeaker(entity, convTitle))

            val strippedCounter = UNREAD_COUNTER_SUFFIX_REGEX.replace(rawSender, "").trim()
            addIfNotBlank(strippedCounter)

            if (rawSender.contains(":")) {
                addIfNotBlank(rawSender.substringBefore(":"))
                addIfNotBlank(rawSender.substringAfter(":"))
            }
        }

        addIfNotBlank(entity.appName)
        return candidates
    }

    /**
     * Determines whether a single notification entity matches the session's criteria.
     */
    fun isNotificationAllowed(
        entity: NotificationEntity,
        session: SummarySessionConfig
    ): Boolean {
        val appNameNorm = NotificationFilter.normalize(entity.appName)
        val appPkgLower = entity.appPackage.lowercase()

        // 0. Check Session-specific app switches
        if (!session.zaloEnabled && (appNameNorm.contains("zalo") || appPkgLower.contains("zalo"))) {
            return false
        }
        if (!session.telegramEnabled && (appNameNorm.contains("telegram") || appPkgLower.contains("telegram"))) {
            return false
        }
        if (!session.messengerEnabled && (appNameNorm.contains("messenger") || appPkgLower.contains("orca") || appPkgLower.contains("facebook"))) {
            return false
        }

        // If no whitelist/banlist rules are set, accept message (if app is enabled above)
        if (session.whitelist.isEmpty() && session.banlist.isEmpty()) {
            return true
        }

        val candidates = extractNormalizedMatchCandidates(entity)

        // 1. Check banlist first: if matches any pattern in banlist, immediately reject
        if (session.banlist.isNotEmpty() && matchesAnyPattern(session.banlist, candidates)) {
            return false
        }

        // 2. Check whitelist according to filterMode
        return when (session.filterMode) {
            ChatFilterMode.STRICT_WHITELIST -> {
                session.whitelist.isNotEmpty() && matchesAnyPattern(session.whitelist, candidates)
            }
            ChatFilterMode.NORMAL_WITH_BLOCK -> {
                session.whitelist.isEmpty() || matchesAnyPattern(session.whitelist, candidates)
            }
        }
    }

    private fun matchesAnyPattern(patterns: Set<String>, candidates: Set<String>): Boolean {
        return patterns.any { pattern ->
            val normPattern = NotificationFilter.normalize(pattern)
            normPattern.isNotBlank() && candidates.any { candidate ->
                NotificationFilter.matchesGlobPattern(candidate, normPattern)
            }
        }
    }
}
