package com.shilapi.xcertplay

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeTest {
    @Test
    fun darkModeIsDetectedWithUnrelatedConfigurationBits() {
        assertTrue(isDarkMode(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun lightAndUndefinedModesAreNotDark() {
        assertFalse(isDarkMode(Configuration.UI_MODE_NIGHT_NO))
        assertFalse(isDarkMode(Configuration.UI_MODE_NIGHT_UNDEFINED))
    }
}
