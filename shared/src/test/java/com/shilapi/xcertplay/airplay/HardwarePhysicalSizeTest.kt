package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class HardwarePhysicalSizeTest {
    @Test fun portraitPanelIsReportedInLandscapeWithCorrectPhysicalAxes() {
        // 1080/400 and 2400/420 inches; no logical density or width setting involved.
        assertEquals(AirPlayPhysicalSizeMm(145, 69), AirPlayDisplaySettings.hardwarePhysicalSizeMm(1080, 2400, 400f, 420f))
    }
    @Test fun landscapePanelKeepsItsPhysicalAxes() {
        assertEquals(AirPlayPhysicalSizeMm(254, 127), AirPlayDisplaySettings.hardwarePhysicalSizeMm(2000, 1000, 200f, 200f))
    }
    @Test fun unavailableDensityIsOmittedInsteadOfUsingDefault200mm() {
        for (dpi in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(AirPlayDisplaySettings.hardwarePhysicalSizeMm(1080, 2400, dpi, 400f))
            assertNull(AirPlayDisplaySettings.hardwarePhysicalSizeMm(1080, 2400, 400f, dpi))
        }
        assertNull(AirPlayDisplaySettings.hardwarePhysicalSizeMm(1080, 2400, 1f, 1f))
    }
}
