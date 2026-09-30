package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class CarPlayFrameRateFallbackTest {
    @Test fun fullChainRequests120Then90Then60AndStops() {
        val first = CarPlayFrameRateFallback(120)
        assertTrue(first.negotiationStarted())
        assertFalse(first.negotiationStarted())
        assertEquals(90, first.failed())
        assertNull(first.failed())
        val second = CarPlayFrameRateFallback(90)
        assertTrue(second.negotiationStarted())
        assertEquals(60, second.failed())
        val last = CarPlayFrameRateFallback(60)
        assertFalse(last.negotiationStarted())
        assertNull(last.failed())
    }
    @Test fun authenticationOrPairingFailureDoesNotDowngrade() {
        assertNull(CarPlayFrameRateFallback(120).failed())
    }
    @Test fun videoStartedOrManualStopCancelsDowngrade() {
        val guard = CarPlayFrameRateFallback(120)
        guard.negotiationStarted()
        guard.cancel()
        assertNull(guard.failed())
        assertFalse(guard.negotiationStarted())
    }
    @Test fun standardFrameRatesDoNotRetry() {
        for (fps in listOf(30, 60)) {
            val guard = CarPlayFrameRateFallback(fps)
            assertFalse(guard.negotiationStarted())
            assertNull(guard.failed())
        }
    }
}
