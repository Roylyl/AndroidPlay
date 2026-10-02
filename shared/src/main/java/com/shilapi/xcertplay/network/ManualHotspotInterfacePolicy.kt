package com.shilapi.xcertplay.network

/** Never advertise a cellular, VPN or Wi-Fi client endpoint as the local hotspot. */
internal object ManualHotspotInterfacePolicy {
    fun isCandidate(name: String, upstreamInterfaces: Set<String>): Boolean =
        name !in upstreamInterfaces && name.matches(Regex("(?:ap|swlan|wlan|softap|p2p)[0-9_].*"))
}
