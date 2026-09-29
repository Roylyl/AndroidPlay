package com.shilapi.xcertplay.airplay

/** Receive-thread timing only: no payloads, endpoint addresses, or route data. */
internal class StreamReceiveStats(
    private val label: String,
    private val report: (String) -> Unit,
    private val nowNs: () -> Long = System::nanoTime,
) {
    private var windowStart = nowNs()
    private var readStart = windowStart
    private var processingStart = windowStart
    private var packets = 0
    private var bytes = 0L
    private var maxReadNs = 0L
    private var maxProcessNs = 0L
    private var nextSequence: Int? = null
    private var forwardGapPackets = 0
    private var lateOrDuplicate = 0

    fun reading() { readStart = nowNs() }

    fun received(size: Int, sequence: Int? = null) {
        processingStart = nowNs()
        maxReadNs = maxOf(maxReadNs, processingStart - readStart)
        packets++
        bytes += size
        if (sequence != null) {
            val expected = nextSequence
            val delta = if (expected == null) 0 else (sequence - expected) and 0xffff
            if (delta < 0x8000) {
                forwardGapPackets += delta
                nextSequence = (sequence + 1) and 0xffff
            } else lateOrDuplicate++
        }
    }

    fun processed() {
        maxProcessNs = maxOf(maxProcessNs, nowNs() - processingStart)
        flush()
    }

    fun flush(ended: Boolean = false) {
        val now = nowNs()
        if (!ended && now - windowStart < 5_000_000_000L) return
        runCatching { report("Receive: $label packets=$packets bytes=$bytes readMaxMs=${maxReadNs / 1_000_000} " +
            "processMaxUs=${maxProcessNs / 1000} seqForwardGaps=$forwardGapPackets " +
            "lateOrDuplicate=$lateOrDuplicate ended=$ended") }
        windowStart = now
        packets = 0
        bytes = 0
        maxReadNs = 0
        maxProcessNs = 0
        forwardGapPackets = 0
        lateOrDuplicate = 0
    }
}
