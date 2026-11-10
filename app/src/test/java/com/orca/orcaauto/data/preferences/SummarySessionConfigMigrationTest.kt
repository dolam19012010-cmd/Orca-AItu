package com.orca.orcaauto.data.preferences

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarySessionConfigMigrationTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(SummarySessionConfig::class.java, SummarySessionConfigDeserializer())
        .create()

    @Test
    fun testDeserializeLegacySessionWithoutAiLanguage() {
        // Legacy JSON from older versions before aiLanguage was introduced
        val legacyJson = """
            [
                {
                    "id": "session_main_default",
                    "name": "Phiên chính",
                    "isMain": true,
                    "createdAt": 0,
                    "filterMode": "NORMAL_WITH_BLOCK"
                },
                {
                    "id": "custom_session_1",
                    "name": "Công việc",
                    "isMain": false,
                    "createdAt": 1000
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<SummarySessionConfig>>() {}.type
        val sessions: List<SummarySessionConfig> = gson.fromJson(legacyJson, type)

        assertEquals(2, sessions.size)

        val main = sessions[0]
        assertEquals("session_main_default", main.id)
        assertEquals("Phiên chính", main.name)
        assertTrue(main.isMain)
        assertEquals("auto", main.aiLanguage)
        assertEquals("auto", main.safeAiLanguage)
        assertNotNull(main.scheduledHours)
        assertEquals(listOf("12:00", "18:00"), main.scheduledHours)
        assertEquals("gentle_assistant", main.promptPersonaId)
        assertNotNull(main.whitelist)
        assertNotNull(main.banlist)
        assertNotNull(main.keyPriorityIds)

        // Verify calling copy() does NOT throw NullPointerException
        val copied = main.copy(name = "Phiên cập nhật")
        assertEquals("Phiên cập nhật", copied.name)
        assertEquals("auto", copied.aiLanguage)

        val custom = sessions[1]
        assertEquals("custom_session_1", custom.id)
        assertEquals("Công việc", custom.name)
        assertFalse(custom.isMain)
        assertEquals("auto", custom.aiLanguage)

        val copiedCustom = custom.copy(promptPersonaId = "direct_analytical")
        assertEquals("direct_analytical", copiedCustom.promptPersonaId)
        assertEquals("auto", copiedCustom.aiLanguage)
    }

    @Test
    fun testDeserializeCorruptedOrNullFields() {
        val corruptedJson = """
            [
                {
                    "id": null,
                    "name": null,
                    "aiLanguage": null,
                    "iconEmoji": null,
                    "scheduledHours": null,
                    "whitelist": null,
                    "banlist": null
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<SummarySessionConfig>>() {}.type
        val sessions: List<SummarySessionConfig> = gson.fromJson(corruptedJson, type)

        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertNotNull(s.id)
        assertEquals("Phiên chính", s.name)
        assertEquals("\u26A1", s.iconEmoji)
        assertEquals("auto", s.aiLanguage)
        assertEquals("auto", s.safeAiLanguage)
        assertEquals(listOf("12:00", "18:00"), s.scheduledHours)
        assertTrue(s.whitelist.isEmpty())
        assertTrue(s.banlist.isEmpty())

        // Ensure copy works seamlessly
        val copyResult = s.copy(aiLanguage = "en")
        assertEquals("en", copyResult.aiLanguage)
    }

    @Test
    fun testDeserializeModernSessionWithAiLanguage() {
        val modernJson = """
            [
                {
                    "id": "s_en",
                    "name": "English Work",
                    "aiLanguage": "en",
                    "promptPersonaId": "gentle_assistant",
                    "scheduledHours": ["09:00", "17:00"]
                }
            ]
        """.trimIndent()

        val type = object : TypeToken<List<SummarySessionConfig>>() {}.type
        val sessions: List<SummarySessionConfig> = gson.fromJson(modernJson, type)

        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertEquals("s_en", s.id)
        assertEquals("en", s.aiLanguage)
        assertEquals("en", s.safeAiLanguage)
        assertEquals(listOf("09:00", "17:00"), s.scheduledHours)
    }
}
