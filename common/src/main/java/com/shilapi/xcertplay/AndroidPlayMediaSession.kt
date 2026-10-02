// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** One system media session per CarPlay connection, independent of projection visibility. */
internal class AndroidPlayMediaSession(private val context: Context, private val mediaCommand: (Int) -> Unit, private val seekCommand: (Long) -> Unit, private val disconnect: () -> Unit) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()
    private val artworkGeneration = AtomicLong()
    private var artworkBytes: ByteArray? = null
    private var bitmap: Bitmap? = null
    private var value = CarPlayNowPlaying()
    private var closed = false
    private var publishedMetadata: MediaMetadata? = null
    private var publishedBitmap: Bitmap? = null
    private var publishedTrackRevision = -1L
    private val progressTicker = object : Runnable {
        override fun run() {
            if (closed || !value.playing || value.durationMillis <= 0) return
            publishPlaybackState()
            handler.postDelayed(this, 1_000)
        }
    }
    private val session = MediaSession(context, "AndroidPlay").apply {
        setPlaybackToLocal(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        setSessionActivity(PendingIntent.getActivity(context, 10, Intent(context, CarPlayHostActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = mediaCommand(1)
            override fun onPause() = mediaCommand(2)
            override fun onSkipToNext() = mediaCommand(4)
            override fun onSkipToPrevious() = mediaCommand(5)
            override fun onSeekTo(pos: Long) { if (value.canSeek && value.durationMillis > 0) seekCommand(pos.coerceIn(0, value.durationMillis)) }
            override fun onStop() = disconnect()
        }, handler)
    }

    fun update(next: CarPlayNowPlaying) = handler.post {
        if (closed || next.revision < value.revision) return@post
        value = next
        session.isActive = next.title.isNotEmpty()
        if (artworkBytes !== next.artwork && !(artworkBytes?.contentEquals(next.artwork ?: byteArrayOf()) ?: (next.artwork == null))) {
            artworkBytes = next.artwork
            // Keep the current cover while its replacement decodes, unless the track changed.
            if (publishedTrackRevision != next.trackRevision || next.artwork == null) bitmap = null
            val generation = artworkGeneration.incrementAndGet()
            val bytes = next.artwork
            if (bytes != null && bytes.isNotEmpty()) decoder.execute {
                val decoded = decodeArtwork(bytes)
                handler.post {
                    if (!closed && generation == artworkGeneration.get()) {
                        bitmap = decoded
                        publish()
                    }
                }
            }
        }
        publish()
    }

    private fun publish() {
        val old = publishedMetadata
        val textChanged = old == null ||
            old.getString(MediaMetadata.METADATA_KEY_TITLE) != value.title ||
            old.getString(MediaMetadata.METADATA_KEY_ARTIST) != value.artist ||
            old.getString(MediaMetadata.METADATA_KEY_ALBUM) != value.album ||
            old.getLong(MediaMetadata.METADATA_KEY_DURATION) != value.durationMillis
        val coverChanged = publishedBitmap !== bitmap || publishedTrackRevision != value.trackRevision
        if (textChanged || coverChanged) {
            // Android accepts a whole metadata snapshot. Copy it to retain the cached cover
            // across title/lyric changes; only mutate the bitmap on a real cover/track update.
            val metadata = if (old == null) MediaMetadata.Builder() else MediaMetadata.Builder(old)
            metadata.putString(MediaMetadata.METADATA_KEY_TITLE, value.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, value.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, value.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, value.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, value.durationMillis)
            if (coverChanged) metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
            publishedBitmap = bitmap
            publishedTrackRevision = value.trackRevision
            publishedMetadata = metadata.build()
            session.setMetadata(publishedMetadata)
        }
        publishPlaybackState()
        handler.removeCallbacks(progressTicker)
        if (value.playing && value.durationMillis > 0) handler.postDelayed(progressTicker, 1_000)
        // Progress-only changes travel through MediaSession, without re-sending the cover notification.
        if (textChanged || coverChanged || notificationPlaying != value.playing || notificationApp != value.appName) {
            notificationPlaying = value.playing
            notificationApp = value.appName
            AndroidPlaySessionService.refreshNotification(context)
        }
    }

    private var notificationPlaying: Boolean? = null
    private var notificationApp = ""
    private fun publishPlaybackState() {
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        if (value.canSeek && value.durationMillis > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        val now = SystemClock.elapsedRealtime()
        session.setPlaybackState(PlaybackState.Builder().setActions(actions)
            .setState(if (value.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                value.positionAt(now), if (value.playing) value.speed else 0f, now).build())
    }

    fun decorate(builder: Notification.Builder): Notification.Builder {
        if (value.title.isEmpty()) return builder
        fun action(code: Int, label: String, icon: Int): Notification.Action {
            val pending = PendingIntent.getService(context, 100 + code,
                Intent(context, AndroidPlaySessionService::class.java).setAction(AndroidPlaySessionService.ACTION_MEDIA).putExtra("media", code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(icon, AndroidPlayLanguage.text(context, label), pending).build()
        }
        builder.setContentTitle(value.title).setContentText(value.artist.ifEmpty { value.appName })
            .setSubText(value.album).setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .addAction(action(5, "上一首", android.R.drawable.ic_media_previous))
            .addAction(action(if (value.playing) 2 else 1, if (value.playing) "暂停" else "播放", if (value.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
            .addAction(action(4, "下一首", android.R.drawable.ic_media_next))
        bitmap?.let(builder::setLargeIcon)
        return builder
    }

    override fun close() {
        closed = true
        handler.removeCallbacks(progressTicker)
        artworkGeneration.incrementAndGet()
        decoder.shutdownNow()
        session.isActive = false
        session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
        session.setMetadata(null)
        session.release()
        bitmap = null; artworkBytes = null
    }
    companion object {
        private fun decodeArtwork(bytes: ByteArray): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 256) inSampleSize *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }
}
