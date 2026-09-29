package com.shilapi.xcertplay.orchestration

/** Rules an existing (car) hotspot must meet before CarPlay can hand its credentials to the iPhone. */
object ManualHotspotValidation {
    /** Security implied by the password: the car hotspot UI only offers open or WPA2 networks. */
    fun securityFor(passphrase: String): ManualHotspotSecurity =
        if (passphrase.isEmpty()) ManualHotspotSecurity.OPEN else ManualHotspotSecurity.WPA2

    /** Returns a message for the user, or null when the name and password can be used. */
    fun validate(ssid: String, passphrase: String): String? = when {
        ssid.isBlank() -> "Enter the car hotspot name"
        ssid.encodeToByteArray().size > 32 -> "The hotspot name must be at most 32 bytes"
        '\u0000' in ssid || '\u0000' in passphrase -> "The name or password contains an invalid character"
        passphrase.isNotEmpty() && passphrase.length !in 8..63 -> "The hotspot password must be 8–63 characters"
        else -> null
    }
}
