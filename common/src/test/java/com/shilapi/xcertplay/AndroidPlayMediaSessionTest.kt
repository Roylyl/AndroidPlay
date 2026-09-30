package com.shilapi.xcertplay

import android.graphics.Bitmap
import java.time.Duration
import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowMediaSession
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [RecordingMediaSession::class])
class AndroidPlayMediaSessionTest {
    @Test fun lyricsRetainCachedCoverAndProgressTickerDoesNotRepublishMetadata() {
        val context = RuntimeEnvironment.getApplication()
        val publisher = AndroidPlayMediaSession(context, {}, {}, {})
        val cover = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        ReflectionHelpers.setField(publisher, "bitmap", cover)
        val initial = CarPlayNowPlaying(revision = 1, title = "Song", trackRevision = 1,
            durationMillis = 90_000, positionMillis = 15_000,
            updatedAtMillis = SystemClock.elapsedRealtime(), playing = true, canSeek = true)
        publisher.update(initial)
        shadowOf(Looper.getMainLooper()).idle()
        val recorded = Shadow.extract<RecordingMediaSession>(ReflectionHelpers.getField<MediaSession>(publisher, "session"))
        val cached = recorded.lastMetadata!!.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        assertNotNull(cached)
        publisher.update(initial.copy(revision = 2, title = "歌词第一行"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("歌词第一行", recorded.lastMetadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
        assertSame(cached, recorded.lastMetadata!!.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        assertEquals(90_000L, recorded.lastMetadata!!.getLong(MediaMetadata.METADATA_KEY_DURATION))
        val count = recorded.metadataCalls
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(17_000L, recorded.lastPlayback!!.position)
        assertEquals(count, recorded.metadataCalls)
        publisher.update(initial.copy(revision = 3, title = "歌词第一行", positionMillis = 40_000,
            updatedAtMillis = SystemClock.elapsedRealtime(), playing = false))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(count, recorded.metadataCalls)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(40_000L, recorded.lastPlayback!!.position)
        publisher.close()
    }
    @Test fun systemControlsReceiveMetadataProgressAndSendCommandsBackToPhone() {
        val context = RuntimeEnvironment.getApplication()
        val commands = mutableListOf<Int>()
        val seeks = mutableListOf<Long>()
        val publisher = AndroidPlayMediaSession(context, { commands += it }, { seeks += it }, {})
        publisher.update(CarPlayNowPlaying(revision = 2, title = "Song", artist = "Artist", durationMillis = 90_000,
            positionMillis = 15_000, updatedAtMillis = SystemClock.elapsedRealtime(), playing = true, canSeek = true))
        shadowOf(Looper.getMainLooper()).idle()
        val notification = publisher.decorate(Notification.Builder(context, "test")).build()
        @Suppress("DEPRECATION") val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)!!
        assertNotNull(token)
        // Robolectric's stock MediaController does not bridge the mock media-session Binder.
        // Capture the actual framework calls instead; real-system rendering is a device check.
        val platformSession = ReflectionHelpers.getField<MediaSession>(publisher, "session")
        val recorded = Shadow.extract<RecordingMediaSession>(platformSession)
        assertEquals("Song", recorded.lastMetadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
        assertEquals(90_000L, recorded.lastMetadata!!.getLong(MediaMetadata.METADATA_KEY_DURATION))
        assertEquals(15_000L, recorded.lastPlayback!!.position)
        assertEquals(PlaybackState.STATE_PLAYING, recorded.lastPlayback!!.state)
        assertTrue(recorded.lastPlayback!!.actions and PlaybackState.ACTION_SEEK_TO != 0L)
        recorded.callback!!.onPause()
        recorded.callback!!.onSkipToNext()
        recorded.callback!!.onSeekTo(30_000)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(2, 4), commands)
        assertEquals(listOf(30_000L), seeks)
        // A late artwork worker's older snapshot must never replace the current track.
        publisher.update(CarPlayNowPlaying(revision = 1, title = "Old"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Song", recorded.lastMetadata!!.getString(MediaMetadata.METADATA_KEY_TITLE))
        publisher.update(CarPlayNowPlaying(revision = 3, title = "Song", durationMillis = 90_000, canSeek = false))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0L, recorded.lastPlayback!!.actions and PlaybackState.ACTION_SEEK_TO)
        recorded.callback!!.onSeekTo(40_000)
        assertEquals(listOf(30_000L), seeks)
        publisher.close()
        assertNull(recorded.lastMetadata)
        assertEquals(PlaybackState.STATE_STOPPED, recorded.lastPlayback!!.state)
    }
}

@Implements(MediaSession::class)
class RecordingMediaSession : ShadowMediaSession() {
    var lastMetadata: MediaMetadata? = null
    var metadataCalls = 0
    var lastPlayback: PlaybackState? = null
    var callback: MediaSession.Callback? = null
    @Implementation fun setMetadata(value: MediaMetadata?) { lastMetadata = value; metadataCalls++ }
    @Implementation fun setPlaybackState(value: PlaybackState?) { lastPlayback = value }
    @Implementation fun setCallback(value: MediaSession.Callback?, handler: android.os.Handler?) { callback = value }
}
