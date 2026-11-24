package com.orca.orcaauto.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun testNavigationItems_containsOnlyThreeTabsInCorrectOrder() {
        val items = Screen.items
        assertEquals("Thanh điều hướng chỉ được chứa chính xác 3 tab", 3, items.size)
        assertEquals(Screen.History, items[0])
        assertEquals(Screen.Notes, items[1])
        assertEquals(Screen.Settings, items[2])
    }

    @Test
    fun testNavigationItems_doesNotContainDashboard() {
        val items = Screen.items
        assertFalse("Thanh điều hướng không được chứa Dashboard", items.contains(Screen.Dashboard))
    }

    @Test
    fun testScreenRoutes_remainCompatible() {
        assertEquals("history", Screen.History.route)
        assertEquals("notes", Screen.Notes.route)
        assertEquals("dashboard", Screen.Dashboard.route)
        assertEquals("settings", Screen.Settings.route)
    }
}
