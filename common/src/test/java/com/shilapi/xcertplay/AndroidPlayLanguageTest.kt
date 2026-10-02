package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AndroidPlayLanguageTest {
    @Test fun explicitSelectionOverridesSystemWithoutChangingTheDevice() {
        assertEquals(Locale.SIMPLIFIED_CHINESE, AndroidPlayLanguage.resolveLocale("zh-Hans", Locale.ENGLISH))
        assertEquals(Locale.TRADITIONAL_CHINESE, AndroidPlayLanguage.resolveLocale("zh-Hant", Locale.SIMPLIFIED_CHINESE))
        assertEquals(Locale.ENGLISH, AndroidPlayLanguage.resolveLocale("en", Locale.TRADITIONAL_CHINESE))
    }
    @Test fun followSystemResolvesChineseScriptAndUsesEnglishForOtherLanguages() {
        assertEquals(Locale.TRADITIONAL_CHINESE, AndroidPlayLanguage.resolveLocale("system", Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals(Locale.TRADITIONAL_CHINESE, AndroidPlayLanguage.resolveLocale("system", Locale("zh", "TW")))
        assertEquals(Locale.SIMPLIFIED_CHINESE, AndroidPlayLanguage.resolveLocale("system", Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals(Locale.ENGLISH, AndroidPlayLanguage.resolveLocale("system", Locale.JAPANESE))
    }
}
