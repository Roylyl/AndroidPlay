package com.shilapi.xcertplay.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaAudioBufferTest {
    @Test
    fun `music buffers the chosen delay plus headroom`() {
        // 48 kHz stereo 16-bit = 192 000 bytes/s.
        val plan = MediaAudioBuffer.plan("media", 48_000, 2, minBufferBytes = 7_680, mediaMillis = 500)
        assertEquals(96_000, plan.startBytes)
        assertEquals(134_400, plan.trackBufferBytes)
    }

    @Test
    fun `calls and prompts keep the low-latency buffer`() {
        val plan = MediaAudioBuffer.plan("telephony", 16_000, 1, minBufferBytes = 1_280, mediaMillis = 1000)
        assertEquals(4 * 1024, plan.startBytes)
        assertEquals(16 * 1024, plan.trackBufferBytes)
        assertEquals(plan, MediaAudioBuffer.plan("alert", 16_000, 1, minBufferBytes = 1_280, mediaMillis = 1000))
    }

    @Test
    fun `unknown delay falls back to the default`() {
        assertEquals(MediaAudioBuffer.DEFAULT_MILLIS, MediaAudioBuffer.sanitize(250))
        assertEquals(57_600, MediaAudioBuffer.plan("media", 48_000, 2, 7_680, mediaMillis = 42).startBytes)
    }

    @Test
    fun `start level stays below a smaller granted buffer`() {
        assertEquals(98_000, MediaAudioBuffer.startBytesFor(192_000, 100_000, 2_048 - 48))
        assertEquals(57_600, MediaAudioBuffer.startBytesFor(57_600, 134_400, 2_048))
        assertEquals(57_600, MediaAudioBuffer.startBytesFor(57_600, 0, 2_048))
    }
}
