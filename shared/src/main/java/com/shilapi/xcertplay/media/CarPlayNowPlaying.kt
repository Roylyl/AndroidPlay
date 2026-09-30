package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.io.ByteArrayOutputStream

/** A delta-merged snapshot. Position/time share the caller's monotonic clock. */
data class CarPlayNowPlaying(
    val revision: Long = 0,
    val trackId: Long? = null,
    val trackRevision: Long = 0,
    val queueIndex: Long? = null,
    val appBundleId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val appName: String = "",
    val durationMillis: Long = 0,
    val positionMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val canSeek: Boolean = false,
    val artwork: ByteArray? = null,
) {
    fun positionAt(now: Long): Long = (positionMillis + if (playing) {
        ((now - updatedAtMillis).coerceAtLeast(0) * speed).toLong()
    } else 0).coerceAtLeast(0).let { if (durationMillis > 0) it.coerceAtMost(durationMillis) else it }
}

/** CSM updates and the separate iAP2 file-transfer session can arrive on different workers. */
class CarPlayNowPlayingStore(private val clock: () -> Long) {
    private data class TransferKey(val channel: Any?, val id: Int)
    private data class Transfer(val generation: Int, val emptyTrack: Boolean, val data: ByteArrayOutputStream)
    private var value = CarPlayNowPlaying()
    private var generation = 0
    private var revision = 0L
    private var artworkId: Int? = null
    private val transfers = mutableMapOf<TransferKey, Transfer>()
    private val completed = linkedMapOf<TransferKey, Pair<Transfer, ByteArray>>()
    private var seekUntil = 0L
    @Synchronized fun snapshot(): CarPlayNowPlaying = value

    @Synchronized fun update(frame: Iap2Frame): CarPlayNowPlaying? {
        if (frame.messageId != 0x5001) return null
        val body = Iap2BodyReader.of(frame)
        val item = body.optionalGroup(0)
        val playback = body.optionalGroup(1)
        val now = clock()
        val id = item?.optionalU64(0)
        val title = item?.optionalString(1)
        val artist = item?.optionalString(12)
        val album = item?.optionalString(6)
        val duration = item?.optionalU32(4)
        val appName = playback?.optionalString(7)
        val queueIndex = playback?.optionalU32(2)
        val appBundleId = playback?.optionalString(16)
        val incomingPosition = playback?.optionalU32(1)
        val state = playback?.optionalU8(0)
        val speed = playback?.optionalU16(12)?.let { it / 100f }
        val canSeek = playback?.optionalRaw(13)?.let { it.isEmpty() || it[0].toInt() != 0 }
        val incomingArtworkId = item?.optionalU8(26)
        // Title may be a live lyric line (QQMusic), never a track identifier.
        val changed = (id != null && value.trackId != null && id != value.trackId) ||
            (queueIndex != null && value.queueIndex != null && queueIndex != value.queueIndex) ||
            (appBundleId != null && value.appBundleId.isNotEmpty() && appBundleId != value.appBundleId)
        if (changed) {
            generation++
            value = CarPlayNowPlaying(trackRevision = generation.toLong(), appName = value.appName, playing = value.playing, speed = value.speed, updatedAtMillis = now)
            artworkId = null
            seekUntil = 0
        }
        var position = value.positionAt(now)
        incomingPosition?.let {
            // Accept the phone's seek acknowledgement early; suppress stale in-flight positions.
            if (now >= seekUntil || kotlin.math.abs(it - position) <= 1_000) {
                position = it
                seekUntil = 0
            }
        }
        val playing = state?.let { it in listOf(1, 3, 4) } ?: value.playing
        value = value.copy(
            trackId = id ?: value.trackId,
            queueIndex = queueIndex ?: value.queueIndex,
            appBundleId = appBundleId ?: value.appBundleId,
            title = title ?: value.title,
            artist = artist ?: value.artist,
            album = album ?: value.album,
            durationMillis = duration ?: value.durationMillis,
            appName = appName ?: value.appName,
            positionMillis = position,
            updatedAtMillis = now,
            playing = playing,
            speed = speed ?: value.speed,
            canSeek = canSeek ?: value.canSeek,
        )
        incomingArtworkId?.let { next ->
            // Retain this track's previous cover until the replacement transfer completes.
            artworkId = next
            val entry = completed.entries.firstOrNull { it.key.id == next &&
                (it.value.first.generation == generation || (it.value.first.emptyTrack && it.value.first.generation + 1 == generation)) }
            entry?.let { completed.remove(it.key); applyArtwork(next, it.value.first, it.value.second) }
        }
        value = value.copy(revision = ++revision)
        return value
    }

    /** Returns the file-transfer acknowledgement and any updated snapshot. */
    @Synchronized fun fileTransfer(packet: ByteArray, channel: Any? = null): Pair<ByteArray?, CarPlayNowPlaying?> {
        if (packet.size < 2) return null to null
        val id = packet[0].toInt() and 0xff
        val key = TransferKey(channel, id)
        val command = packet[1].toInt() and 0xff
        val payload = packet.copyOfRange(2, packet.size)
        when (command) {
            0x04 -> {
                if (transfers.size >= MAX_TRANSFERS && key !in transfers) return byteArrayOf(id.toByte(), 0x02) to null
                transfers[key] = Transfer(generation, value.title.isEmpty(), ByteArrayOutputStream())
                completed.remove(key)
                return byteArrayOf(id.toByte(), 0x01) to null
            }
            0x02 -> { transfers.remove(key); completed.remove(key); return null to null }
            0x80, 0x00, 0x40, 0xc0 -> {
                // Single-frame transfers may omit setup; chunked transfers must have a start.
                val transfer = transfers[key] ?: if (command == 0xc0 || command == 0x80) {
                    Transfer(generation, value.title.isEmpty(), ByteArrayOutputStream()).also { transfers[key] = it }
                } else return null to null
                if (command == 0x80) transfer.data.reset()
                if (transfer.data.size() + payload.size > MAX_ARTWORK_BYTES) {
                    transfers.remove(key)
                    return byteArrayOf(id.toByte(), 0x02) to null
                }
                transfer.data.write(payload)
                if (command == 0x40 || command == 0xc0) {
                    transfers.remove(key)
                    val bytes = transfer.data.toByteArray()
                    val updated = applyArtwork(id, transfer, bytes)
                    if (!updated && (transfer.generation == generation || transfer.emptyTrack)) {
                        completed[key] = transfer to bytes
                        while (completed.size > MAX_TRANSFERS) completed.remove(completed.keys.first())
                    }
                    return byteArrayOf(id.toByte(), 0x05) to if (updated) value else null
                }
            }
        }
        return null to null
    }

    private fun applyArtwork(id: Int, transfer: Transfer, bytes: ByteArray): Boolean {
        if (id != artworkId || (transfer.generation != generation &&
                !(transfer.emptyTrack && transfer.generation + 1 == generation))) return false
        value = value.copy(artwork = bytes, revision = ++revision)
        return true
    }

    @Synchronized fun seek(position: Long): CarPlayNowPlaying {
        val now = clock()
        seekUntil = now + 2_000
        value = value.copy(positionMillis = position.coerceIn(0, value.durationMillis), updatedAtMillis = now, revision = ++revision)
        return value
    }
    @Synchronized fun clear() {
        generation++
        value = CarPlayNowPlaying(revision = ++revision, trackRevision = generation.toLong())
        artworkId = null
        transfers.clear(); completed.clear(); seekUntil = 0
    }
    companion object {
        private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        private const val MAX_TRANSFERS = 4
    }
}
