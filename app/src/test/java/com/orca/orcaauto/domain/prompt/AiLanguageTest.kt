package com.orca.orcaauto.domain.prompt

import com.orca.orcaauto.ui.locale.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AiLanguageTest {

    @Test
    fun testCodes() {
        assertEquals("auto", AiLanguage.AUTO.code)
        assertEquals("vi", AiLanguage.VIETNAMESE.code)
        assertEquals("en", AiLanguage.ENGLISH.code)
    }

    @Test
    fun testFromCode() {
        assertEquals(AiLanguage.AUTO, AiLanguage.fromCode("auto"))
        assertEquals(AiLanguage.AUTO, AiLanguage.fromCode("AUTO"))
        assertEquals(AiLanguage.VIETNAMESE, AiLanguage.fromCode("vi"))
        assertEquals(AiLanguage.ENGLISH, AiLanguage.fromCode("en"))
        assertEquals(AiLanguage.AUTO, AiLanguage.fromCode("other"))
        assertEquals(AiLanguage.AUTO, AiLanguage.fromCode(null))
    }

    @Test
    fun testResolve() {
        assertEquals(AppLanguage.VIETNAMESE, AiLanguage.AUTO.resolve(AppLanguage.VIETNAMESE))
        assertEquals(AppLanguage.ENGLISH, AiLanguage.AUTO.resolve(AppLanguage.ENGLISH))
        assertEquals(AppLanguage.VIETNAMESE, AiLanguage.VIETNAMESE.resolve(AppLanguage.ENGLISH))
        assertEquals(AppLanguage.ENGLISH, AiLanguage.ENGLISH.resolve(AppLanguage.VIETNAMESE))
    }

    @Test
    fun testGetDisplayName() {
        assertEquals("Đồng bộ theo ngôn ngữ giao diện", AiLanguage.AUTO.getDisplayName(AppLanguage.VIETNAMESE))
        assertEquals("Match UI Language", AiLanguage.AUTO.getDisplayName(AppLanguage.ENGLISH))
        assertEquals("Tiếng Việt (Vietnamese)", AiLanguage.VIETNAMESE.getDisplayName(AppLanguage.VIETNAMESE))
        assertEquals("Vietnamese (Tiếng Việt)", AiLanguage.VIETNAMESE.getDisplayName(AppLanguage.ENGLISH))
        assertEquals("Tiếng Anh (English)", AiLanguage.ENGLISH.getDisplayName(AppLanguage.VIETNAMESE))
        assertEquals("English (Tiếng Anh)", AiLanguage.ENGLISH.getDisplayName(AppLanguage.ENGLISH))
    }
}
