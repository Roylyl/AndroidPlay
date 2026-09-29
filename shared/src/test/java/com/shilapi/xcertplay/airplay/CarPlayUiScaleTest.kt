package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class CarPlayUiScaleTest {
    private val display = AirPlayDisplayConfig(1920, 1080, 300, 168, fps = 60,
        safeArea = AirPlayInsets(1, 2, 3, 4), viewArea = AirPlayInsets(5, 6, 7, 8))

    @Test fun eachPresetChangesPhysicalSizeWithoutChangingVideoOrTouchCoordinates() {
        for ((scale, width, height) in listOf(Triple(75,400,224), Triple(100,300,168), Triple(125,240,134), Triple(150,200,112))) {
            val result = CarPlayUiScale.apply(display, scale)
            assertEquals(width, result.widthPhysicalMm)
            assertEquals(height, result.heightPhysicalMm)
            assertEquals(display, result.copy(widthPhysicalMm = 300, heightPhysicalMm = 168))
        }
    }
    @Test fun phoneInfoContainsTheSelectedPhysicalSize() {
        for (scale in CarPlayUiScale.presets) {
            val main = CarPlayUiScale.apply(display, scale)
            val info = AirPlayInfoPlist.build(AirPlayConfig("Test", "00:11:22:33:44:55", "00:11:22:33:44:55", sourceVersion = "950.7.1", main = main))
            val screen = (info["displays"] as List<*>).first() as Map<*, *>
            assertEquals(main.widthPhysicalMm, screen["widthPhysical"])
            assertEquals(main.heightPhysicalMm, screen["heightPhysical"])
            assertEquals(1920, screen["widthPixels"])
            assertEquals(1080, screen["heightPixels"])
        }
    }
    @Test fun unknownSettingsFallBackWithoutMutatingDisplay() {
        assertSame(display, CarPlayUiScale.apply(display, 85))
        assertSame(display, CarPlayUiScale.apply(display, 100))
    }
    @Test fun missingDimensionsUseUpstreamPhysicalWidthAndAspectRatio() {
        val result = CarPlayUiScale.apply(AirPlayDisplayConfig(1920,1080),150)
        assertEquals(200,result.widthPhysicalMm)
        assertEquals(113,result.heightPhysicalMm)
    }
}
