package com.orca.orcaauto.ui.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun testAppLanguageCodes() {
        assertEquals("vi", AppLanguage.VIETNAMESE.code)
        assertEquals("en", AppLanguage.ENGLISH.code)
    }

    @Test
    fun testFromCode() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("vi"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("VI"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode("unknown"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromCode(null))
    }
}
