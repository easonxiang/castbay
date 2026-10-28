package com.weenas.castbay.service

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun traditionalChineseUsesTaiwanResources() {
        val locale = AppLanguage.localeFor(ReceiverSettings.LANGUAGE_ZH_TW)
        assertEquals(Locale.TRADITIONAL_CHINESE, locale)
        assertEquals("TW", locale?.country)
    }

    @Test
    fun existingSimplifiedPreferenceKeepsItsMeaning() {
        assertEquals("zh", ReceiverSettings.LANGUAGE_ZH)
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLanguage.localeFor("zh"))
        assertTrue(ReceiverSettings.LANGUAGES.containsAll(listOf("system", "zh", "zh-TW", "en")))
    }

    @Test
    fun systemLanguageRemainsUnchangedAndEnglishIsExplicit() {
        assertNull(AppLanguage.localeFor(ReceiverSettings.LANGUAGE_SYSTEM))
        assertEquals(Locale.ENGLISH, AppLanguage.localeFor(ReceiverSettings.LANGUAGE_EN))
    }
}
