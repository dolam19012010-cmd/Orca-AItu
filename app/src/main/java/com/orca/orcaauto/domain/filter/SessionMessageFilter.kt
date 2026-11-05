package com.orca.orcaauto.domain.filter

import com.orca.orcaauto.data.local.NotificationEntity
import com.orca.orcaauto.data.preferences.ChatFilterMode
import com.orca.orcaauto.data.preferences.SummarySessionConfig

/**
 * Filters recorded notifications specifically for a SummarySessionConfig.
 * Supports exact matching, normalized accents, and Asterisk (*) wildcards.
 */
object SessionMessageFilter {

    /**
     * Filters a list of NotificationEntity according to the session's whitelist, banlist, and mode.
     */
    fun filter(
        notifications: List<NotificationEntity>,
        session: SummarySessionConfig
    ): List<NotificationEntity> {
        // If main session and no custom rules set, allow all notifications
        if (session.isMain && session.whitelist.isEmpty() && session.banlist.isEmpty()) {
            return notifications
        }

        return notifications.filter { entity ->
            isNotificationAllowed(entity, session)
        }
    }

    /**
     * Determines whether a single notification entity matches the session's criteria.
     */
    fun isNotificationAllowed(
        entity: NotificationEntity,
        session: SummarySessionConfig
    ): Boolean {
        val senderNorm = NotificationFilter.normalize(entity.sender)
        val appNameNorm = NotificationFilter.normalize(entity.appName)

        // 1. Check banlist first: if matches any pattern in banlist, immediately reject
        if (session.banlist.isNotEmpty()) {
            val isBanned = session.banlist.any { banPattern ->
                val normPattern = NotificationFilter.normalize(banPattern)
                if (normPattern.isBlank()) false
                else NotificationFilter.matchesGlobPattern(senderNorm, normPattern) ||
                     NotificationFilter.matchesGlobPattern(appNameNorm, normPattern)
            }
            if (isBanned) return false
        }

        // 2. Check whitelist according to filterMode
        return when (session.filterMode) {
            ChatFilterMode.STRICT_WHITELIST -> {
                if (session.whitelist.isEmpty()) {
                    false // Strict whitelist with no members rejects all
                } else {
                    session.whitelist.any { wlPattern ->
                        val normPattern = NotificationFilter.normalize(wlPattern)
                        if (normPattern.isBlank()) false
                        else NotificationFilter.matchesGlobPattern(senderNorm, normPattern) ||
                             NotificationFilter.matchesGlobPattern(appNameNorm, normPattern)
                    }
                }
            }
            ChatFilterMode.NORMAL_WITH_BLOCK -> {
                if (session.whitelist.isEmpty()) {
                    true // Normal mode without specific whitelist allows all (non-banned)
                } else {
                    session.whitelist.any { wlPattern ->
                        val normPattern = NotificationFilter.normalize(wlPattern)
                        if (normPattern.isBlank()) false
                        else NotificationFilter.matchesGlobPattern(senderNorm, normPattern) ||
                             NotificationFilter.matchesGlobPattern(appNameNorm, normPattern)
                    }
                }
            }
        }
    }
}
