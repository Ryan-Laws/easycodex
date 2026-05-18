package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppI18nTest {
    @Test
    fun languageOptionsOnlyExposeImplementedUiLanguages() {
        val values = appLanguageOptions().map { it.value }

        assertEquals(listOf("system", "zh", "en"), values)
        assertFalse(values.any { it in listOf("zh-Hant", "ja", "ko", "es", "fr", "de") })
    }

    @Test
    fun unsupportedLanguageCodesFallBackToSystemLanguage() {
        assertEquals(resolvedAppLanguage(DEFAULT_APP_LANGUAGE), resolvedAppLanguage("ja"))
        assertEquals(resolvedAppLanguage(DEFAULT_APP_LANGUAGE), resolvedAppLanguage("es"))
    }

    @Test
    fun traditionalChineseCodesUseImplementedChineseUi() {
        assertEquals("zh", resolvedAppLanguage("zh-Hant"))
        assertEquals("zh", resolvedAppLanguage("zh-TW"))
    }
}
