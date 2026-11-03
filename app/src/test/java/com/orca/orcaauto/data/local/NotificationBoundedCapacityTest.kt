package com.orca.orcaauto.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBoundedCapacityTest {

    data class MockNotification(
        val id: Long,
        val timestamp: Long,
        var isProcessed: Boolean
    )

    /**
     * Simulates NotificationDao.enforceMaxCapacity algorithm logic in memory.
     */
    private fun simulateEnforceCapacity(
        list: MutableList<MockNotification>,
        maxCapacity: Int = 200
    ) {
        val total = list.size
        if (total > maxCapacity) {
            val toRemove = total - maxCapacity
            // 1. Prioritize deleting already processed notifications (isProcessed = true)
            val processed = list.filter { it.isProcessed }.sortedBy { it.timestamp }.take(toRemove)
            val processedIds = processed.map { it.id }.toSet()
            list.removeAll { it.id in processedIds }

            // 2. Only if still exceeding maxCapacity, trim oldest
            if (list.size > maxCapacity) {
                val excess = list.size - maxCapacity
                val oldestIds = list.sortedBy { it.timestamp }.take(excess).map { it.id }.toSet()
                list.removeAll { it.id in oldestIds }
            }
        }
    }

    @Test
    fun testProcessedMessagesArePrunedFirstToProtectPendingMessages() {
        val items = mutableListOf<MockNotification>()
        // 100 unprocessed messages (vital)
        for (i in 1..100) {
            items.add(MockNotification(id = i.toLong(), timestamp = i * 1000L, isProcessed = false))
        }
        // 140 already processed messages (safe to prune)
        for (i in 101..240) {
            items.add(MockNotification(id = i.toLong(), timestamp = i * 1000L, isProcessed = true))
        }
        assertEquals(240, items.size)

        // Enforce max 200 capacity
        simulateEnforceCapacity(items, maxCapacity = 200)

        assertEquals(200, items.size)
        // All 100 unprocessed messages must remain intact!
        val unprocessedRemaining = items.count { !it.isProcessed }
        assertEquals(100, unprocessedRemaining)
        // 100 processed messages remain (40 were trimmed)
        val processedRemaining = items.count { it.isProcessed }
        assertEquals(100, processedRemaining)
    }

    @Test
    fun testEnforceCapacityDoesNothingWhenUnderLimit() {
        val items = mutableListOf<MockNotification>()
        for (i in 1..50) {
            items.add(MockNotification(id = i.toLong(), timestamp = i * 1000L, isProcessed = false))
        }
        simulateEnforceCapacity(items, maxCapacity = 200)
        assertEquals(50, items.size)
    }
}
