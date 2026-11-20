package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.ui.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptPersonaBilingualTest {

    @Test
    fun testAllPersonasHaveBilingualSupport() {
        for (persona in PromptPersona.entries) {
            val titleVi = persona.getTitle(AppLanguage.VIETNAMESE)
            val titleEn = persona.getTitle(AppLanguage.ENGLISH)
            assertTrue("Persona ${persona.id} titleVi non-empty", titleVi.isNotBlank())
            assertTrue("Persona ${persona.id} titleEn non-empty", titleEn.isNotBlank())

            val subVi = persona.getSubtitle(AppLanguage.VIETNAMESE)
            val subEn = persona.getSubtitle(AppLanguage.ENGLISH)
            assertTrue("Persona ${persona.id} subVi non-empty", subVi.isNotBlank())
            assertTrue("Persona ${persona.id} subEn non-empty", subEn.isNotBlank())

            val roleVi = persona.getSystemRole(AppLanguage.VIETNAMESE)
            val roleEn = persona.getSystemRole(AppLanguage.ENGLISH)
            assertTrue("Persona ${persona.id} roleVi non-empty", roleVi.isNotBlank())
            assertTrue("Persona ${persona.id} roleEn non-empty", roleEn.isNotBlank())

            val adviceTitleVi = persona.getAdviceSectionTitle(AppLanguage.VIETNAMESE)
            val adviceTitleEn = persona.getAdviceSectionTitle(AppLanguage.ENGLISH)
            assertTrue("Persona ${persona.id} adviceTitleVi non-empty", adviceTitleVi.isNotBlank())
            assertTrue("Persona ${persona.id} adviceTitleEn non-empty", adviceTitleEn.isNotBlank())

            val instructionsVi = persona.getPromptInstructions(AppLanguage.VIETNAMESE)
            val instructionsEn = persona.getPromptInstructions(AppLanguage.ENGLISH)
            assertTrue("Persona ${persona.id} instructionsVi non-empty", instructionsVi.isNotBlank())
            assertTrue("Persona ${persona.id} instructionsEn non-empty", instructionsEn.isNotBlank())
        }
    }

    @Test
    fun testDefaultPersonaFromId() {
        val persona = PromptPersona.fromId("gentle_assistant")
        assertEquals(PromptPersona.GENTLE_ASSISTANT, persona)
        assertEquals("Trợ Lý Nhẹ Nhàng & Tinh Tế (Khuyên dùng)", persona.getTitle(AppLanguage.VIETNAMESE))
        assertEquals("Gentle & Thoughtful Assistant (Recommended)", persona.getTitle(AppLanguage.ENGLISH))
    }
}
