package com.shilapi.xcertplay

import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CarPlaySettingsTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    @Test fun nativeUiFollowsAndroidAndIgnoresRemovedCarPlayThemePreference() {
        context.getSharedPreferences("androidplay", 0).edit().putString("theme", "LIGHT").apply()
        assertTrue(CarPlaySettings.night(context, Configuration.UI_MODE_NIGHT_YES))
        context.getSharedPreferences("androidplay", 0).edit().putString("theme", "DARK").apply()
        assertFalse(CarPlaySettings.night(context, Configuration.UI_MODE_NIGHT_NO))
    }
    @Test fun inputAndOutputPreferencesAreIndependentAndDefaultCanBeRestored() {
        assertNull(CarPlaySettings.selectedDevice(context, true))
        assertNull(CarPlaySettings.selectedDevice(context, false))
        CarPlaySettings.saveDevice(context, true, "input")
        CarPlaySettings.saveDevice(context, false, "output")
        assertEquals("input", CarPlaySettings.selectedDevice(context, true))
        assertEquals("output", CarPlaySettings.selectedDevice(context, false))
        CarPlaySettings.saveDevice(context, true, null)
        assertNull(CarPlaySettings.selectedDevice(context, true))
        assertEquals("output", CarPlaySettings.selectedDevice(context, false))
    }
}
