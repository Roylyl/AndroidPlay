package com.shilapi.xcertplay.airplay

/** One-shot guard per connection attempt; pairing alone never arms a downgrade. */
class CarPlayFrameRateFallback(private val fps: Int) {
    private var armed = false
    private var finished = false

    fun negotiationStarted(): Boolean {
        if (finished || armed || fps !in listOf(90, 120)) return false
        armed = true
        return true
    }

    fun failed(): Int? {
        if (!armed || finished) return null
        cancel()
        return if (fps == 120) 90 else 60
    }

    fun cancel() {
        finished = true
        armed = false
    }
}
