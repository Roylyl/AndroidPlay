package com.shilapi.xcertplay.orchestration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualHotspotConfigTest {
    @Test
    fun autoBandAcceptsBoth2GhzAnd5GhzChannels() {
        assertTrue(isManualHotspotChannelCompatible(ManualHotspotBand.AUTO, 6))
        assertTrue(isManualHotspotChannelCompatible(ManualHotspotBand.AUTO, 36))
    }

    @Test
    fun explicitBandRejectsChannelsFromTheOtherBand() {
        assertTrue(isManualHotspotChannelCompatible(ManualHotspotBand.GHZ_2_4, 11))
        assertFalse(isManualHotspotChannelCompatible(ManualHotspotBand.GHZ_2_4, 36))
        assertTrue(isManualHotspotChannelCompatible(ManualHotspotBand.GHZ_5, 149))
        assertFalse(isManualHotspotChannelCompatible(ManualHotspotBand.GHZ_5, 11))
    }
}
