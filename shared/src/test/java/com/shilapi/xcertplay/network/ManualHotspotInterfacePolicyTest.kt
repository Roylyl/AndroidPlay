package com.shilapi.xcertplay.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualHotspotInterfacePolicyTest {
    @Test fun excludesCellularVpnAndEveryClientNetwork() {
        for (name in listOf("ccmni3", "rmnet_data0", "tun0", "eth0", "apcli0", "wlan0")) {
            assertFalse(name, ManualHotspotInterfacePolicy.isCandidate(name, setOf("wlan0", "wlan1")))
        }
        assertFalse(ManualHotspotInterfacePolicy.isCandidate("wlan1", setOf("wlan0", "wlan1")))
    }
    @Test fun keepsSupportedHotspotInterfaces() {
        for (name in listOf("ap0", "swlan0", "wlan1", "softap0", "p2p0", "ap_br0")) {
            assertTrue(name, ManualHotspotInterfacePolicy.isCandidate(name, setOf("wlan0")))
        }
    }
}
