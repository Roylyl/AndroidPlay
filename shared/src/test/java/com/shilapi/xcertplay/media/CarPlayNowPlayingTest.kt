package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test

class CarPlayNowPlayingTest {
    private var now = 1_000L
    private val store = CarPlayNowPlayingStore { now }
    private fun song(id: Long = 1, cover: Int = 7) = Iap2Messages.buildRaw(0x5001) {
        group(0) { u64(0, id); string(1, "Track$id"); string(12, "Artist"); u32(4, 60_000); u8(26, cover) }
        group(1) { u8(0, 1); u32(1, 10_000); void(13); u16(12, 100) }
    }
    private fun position(ms: Long) = Iap2Messages.buildRaw(0x5001) { group(1) { u32(1, ms) } }
    @Test fun progressDeltasKeepTitleAndArtworkAndPauseFreezesPosition() {
        store.update(song())
        store.fileTransfer(byteArrayOf(7, 4))
        store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 1, 2, 3))
        now += 1_000
        val value = store.update(position(11_000))!!
        assertEquals("Track1", value.title)
        assertArrayEquals(byteArrayOf(1, 2, 3), value.artwork)
        assertEquals(12_000L, value.positionAt(now + 1_000))
        now += 1_000
        val paused = store.update(Iap2Messages.buildRaw(0x5001) { group(1) { u8(0, 2) } })!!
        assertFalse(paused.playing)
        assertEquals(12_000L, paused.positionAt(now + 15_000))
    }
    @Test fun qqMusicLyricTitlesDoNotResetCoverDurationOrProgress() {
        val first = store.update(song())!!
        store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 1, 2))
        val cover = store.snapshot().artwork
        now += 1_000
        val lyric = store.update(Iap2Messages.buildRaw(0x5001) {
            group(0) { string(1, "第一行歌词") }
        })!!
        assertEquals("第一行歌词", lyric.title)
        assertEquals(first.trackRevision, lyric.trackRevision)
        assertSame(cover, lyric.artwork)
        assertEquals(60_000L, lyric.durationMillis)
        assertEquals(11_000L, lyric.positionMillis)
        assertTrue(lyric.canSeek)
        store.seek(30_000)
        now += 500
        val next = store.update(Iap2Messages.buildRaw(0x5001) {
            group(0) { string(1, "第二行歌词") }
            group(1) { u32(1, 11_500) }
        })!!
        assertEquals(first.trackRevision, next.trackRevision)
        assertEquals(30_500L, next.positionMillis)
        assertSame(cover, next.artwork)
    }
    @Test fun lateFirstPersistentIdLearnsIdentityWithoutResettingTrack() {
        val first = store.update(Iap2Messages.buildRaw(0x5001) {
            group(0) { string(1, "Song"); u32(4, 60_000); u8(26, 7) }
            group(1) { u32(1, 10_000); void(13) }
        })!!
        store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 1))
        val value = store.update(Iap2Messages.buildRaw(0x5001) { group(0) { u64(0, 1) } })!!
        assertEquals(first.trackRevision, value.trackRevision)
        assertEquals("Song", value.title)
        assertEquals(60_000L, value.durationMillis)
        assertEquals(10_000L, value.positionMillis)
        assertArrayEquals(byteArrayOf(1), value.artwork)
    }
    @Test fun queueIndexDetectsTrackChangeWithoutPersistentId() {
        store.update(Iap2Messages.buildRaw(0x5001) {
            group(0) { string(1, "Song"); u32(4, 60_000) }
            group(1) { u32(2, 0); u32(1, 10_000) }
        })
        val revision = store.snapshot().trackRevision
        val next = store.update(Iap2Messages.buildRaw(0x5001) {
            group(0) { string(1, "Next") }
            group(1) { u32(2, 1) }
        })!!
        assertNotEquals(revision, next.trackRevision)
        assertEquals(0L, next.durationMillis)
        assertEquals(0L, next.positionMillis)
    }
    @Test fun acknowledgedSeekAcceptsFurtherPhoneProgressImmediately() {
        store.update(song())
        store.seek(35_000)
        now += 200
        assertEquals(35_200L, store.update(position(35_200))!!.positionMillis)
        now += 200
        assertEquals(36_700L, store.update(position(36_700))!!.positionMillis)
    }
    @Test fun artworkAnnouncementKeepsExistingCoverUntilReplacementArrives() {
        store.update(song())
        store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 1))
        val cover = store.snapshot().artwork
        val pending = store.update(Iap2Messages.buildRaw(0x5001) { group(0) { u8(26, 8) } })!!
        assertSame(cover, pending.artwork)
        assertArrayEquals(byteArrayOf(2), store.fileTransfer(byteArrayOf(8, 0xc0.toByte(), 2)).second!!.artwork)
    }
    @Test fun artworkCanArriveBeforeMetadata() {
        store.fileTransfer(byteArrayOf(7, 4))
        store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 8))
        assertArrayEquals(byteArrayOf(8), store.update(song())!!.artwork)
    }
    @Test fun lateArtworkFromPreviousTrackNeverOverwritesCurrentCoverEvenIfIdReused() {
        store.update(song())
        store.fileTransfer(byteArrayOf(7, 4))
        store.fileTransfer(byteArrayOf(7, 0x80.toByte(), 1))
        store.update(song(2))
        val (_, late) = store.fileTransfer(byteArrayOf(7, 0x40, 2))
        assertNull(late)
        assertNull(store.snapshot().artwork)
        store.fileTransfer(byteArrayOf(7, 4))
        val (ack, current) = store.fileTransfer(byteArrayOf(7, 0xc0.toByte(), 3))
        assertArrayEquals(byteArrayOf(7, 5), ack)
        assertArrayEquals(byteArrayOf(3), current!!.artwork)
    }
    @Test fun bluetoothAndTunnelTransfersWithTheSameIdDoNotMixChunks() {
        store.update(song())
        val bluetooth = Any(); val tunnel = Any()
        store.fileTransfer(byteArrayOf(7, 4), bluetooth)
        store.fileTransfer(byteArrayOf(7, 4), tunnel)
        store.fileTransfer(byteArrayOf(7, 0x80.toByte(), 1), bluetooth)
        store.fileTransfer(byteArrayOf(7, 0x80.toByte(), 9), tunnel)
        assertArrayEquals(byteArrayOf(1, 2), store.fileTransfer(byteArrayOf(7, 0x40, 2), bluetooth).second!!.artwork)
        assertArrayEquals(byteArrayOf(9, 8), store.fileTransfer(byteArrayOf(7, 0x40, 8), tunnel).second!!.artwork)
    }
    @Test fun seekIgnoresOldProgressUntilPhoneCatchesUpThenAcceptsAuthoritativeUpdates() {
        store.update(song())
        store.seek(35_000)
        assertEquals(35_000L, store.update(position(10_500))!!.positionMillis)
        now += 2_100
        assertEquals(37_000L, store.update(position(37_000))!!.positionMillis)
    }
    @Test fun newTrackClearsDurationProgressAndSeekSupport() {
        store.update(song())
        val next = store.update(Iap2Messages.buildRaw(0x5001) { group(0) { u64(0, 2); string(1, "Next") } })!!
        assertEquals(0L, next.durationMillis)
        assertEquals(0L, next.positionMillis)
        assertFalse(next.canSeek)
        assertNull(next.artwork)
    }
    @Test fun disconnectDiscardsPartialTransfersAndMetadata() {
        store.update(song()); store.fileTransfer(byteArrayOf(7, 4))
        store.clear()
        assertEquals("", store.snapshot().title)
        assertNull(store.fileTransfer(byteArrayOf(7, 0x40, 3)).second)
    }
    @Test fun excessiveArtworkIsCancelledWithoutGrowingTheBuffer() {
        store.fileTransfer(byteArrayOf(7, 4))
        store.fileTransfer(byteArrayOf(7, 0x80.toByte()) + ByteArray(8 * 1024 * 1024))
        val (ack, value) = store.fileTransfer(byteArrayOf(7, 0x40, 1))
        assertArrayEquals(byteArrayOf(7, 2), ack)
        assertNull(value)
    }
}
