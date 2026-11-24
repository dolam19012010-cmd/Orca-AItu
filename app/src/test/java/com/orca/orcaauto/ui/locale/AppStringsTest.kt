package com.orca.orcaauto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStringsTest {

    @Test
    fun testProviderReturnsCorrectLanguage() {
        val vi = AppStringsProvider.get(AppLanguage.VIETNAMESE)
        assertEquals(AppLanguage.VIETNAMESE, vi.language)
        assertEquals("Bảng Điều Khiển", vi.navDashboard)
        assertEquals("Lịch Sử & Việc", vi.navHistory)
        assertEquals("Kho Ghi Chú", vi.navNotes)
        assertEquals("Cài Đặt", vi.navSettings)

        val en = AppStringsProvider.get(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, en.language)
        assertEquals("Dashboard", en.navDashboard)
        assertEquals("History & Tasks", en.navHistory)
        assertEquals("Notes", en.navNotes)
        assertEquals("Settings", en.navSettings)
    }

    @Test
    fun testBilingualCompletenessAcrossKeySections() {
        val pairs = listOf(
            AppStringsVi to AppStringsEn
        )

        for ((vi, en) in pairs) {
            // Navigation
            assertTrue(vi.navDashboard.isNotBlank())
            assertTrue(en.navDashboard.isNotBlank())
            assertTrue(vi.navHistory.isNotBlank())
            assertTrue(en.navHistory.isNotBlank())
            assertTrue(vi.navNotes.isNotBlank())
            assertTrue(en.navNotes.isNotBlank())
            assertTrue(vi.navSettings.isNotBlank())
            assertTrue(en.navSettings.isNotBlank())

            // Dashboard
            assertTrue(vi.dashboardTitle.isNotBlank())
            assertTrue(en.dashboardTitle.isNotBlank())
            assertTrue(vi.btnInstantAi.isNotBlank())
            assertTrue(en.btnInstantAi.isNotBlank())
            assertTrue(vi.btnOpenBubble.isNotBlank())
            assertTrue(en.btnOpenBubble.isNotBlank())

            // History
            assertTrue(vi.tabSummaries.isNotBlank())
            assertTrue(en.tabSummaries.isNotBlank())
            assertTrue(vi.tabRawMessages.isNotBlank())
            assertTrue(en.tabRawMessages.isNotBlank())
            assertTrue(vi.filterAll.isNotBlank())
            assertTrue(en.filterAll.isNotBlank())
            assertTrue(vi.btnVoiceBriefing.isNotBlank())
            assertTrue(en.btnVoiceBriefing.isNotBlank())

            // Notes
            assertTrue(vi.notesTitle.isNotBlank())
            assertTrue(en.notesTitle.isNotBlank())
            assertTrue(vi.notesSearchPlaceholder.isNotBlank())
            assertTrue(en.notesSearchPlaceholder.isNotBlank())
            assertTrue(vi.notesAssistantButton.isNotBlank())
            assertTrue(en.notesAssistantButton.isNotBlank())

            // Settings & Localization
            assertTrue(vi.settingsTitle.isNotBlank())
            assertTrue(en.settingsTitle.isNotBlank())
            assertTrue(vi.permExactAlarmTitle.isNotBlank())
            assertTrue(en.permExactAlarmTitle.isNotBlank())
            assertTrue(vi.permExactAlarmDesc.isNotBlank())
            assertTrue(en.permExactAlarmDesc.isNotBlank())
            assertTrue(vi.catLanguageTitle.isNotBlank())
            assertTrue(en.catLanguageTitle.isNotBlank())
            assertTrue(vi.labelUiLanguage.isNotBlank())
            assertTrue(en.labelUiLanguage.isNotBlank())
            assertTrue(vi.labelAiLanguage.isNotBlank())
            assertTrue(en.labelAiLanguage.isNotBlank())
            assertTrue(vi.langAiHint.isNotBlank())
            assertTrue(en.langAiHint.isNotBlank())

            // Floating Bubble
            assertTrue(vi.bubbleAskAi.isNotBlank())
            assertTrue(en.bubbleAskAi.isNotBlank())
            assertTrue(vi.bubbleSend.isNotBlank())
            assertTrue(en.bubbleSend.isNotBlank())
            assertTrue(vi.bubbleThinking.isNotBlank())
            assertTrue(en.bubbleThinking.isNotBlank())
        }
    }
}
