package com.shilapi.xcertplay

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.SoftApConfiguration
import android.os.Build
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity

/** Best effort only: many stock systems restrict access to the tethering password. */
internal object AndroidPlayHotspot {
    fun importSystemConfiguration(context: Context): Boolean {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return false
        return runCatching {
            val (name, password, security) = if (Build.VERSION.SDK_INT >= 30) {
                val config = WifiManager::class.java.getMethod("getSoftApConfiguration").invoke(wifi) as SoftApConfiguration
                val security = when (config.securityType) {
                    SoftApConfiguration.SECURITY_TYPE_WPA2_PSK -> ManualHotspotSecurity.WPA2
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE_TRANSITION -> ManualHotspotSecurity.WPA3_TRANSITION
                    SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> ManualHotspotSecurity.WPA3
                    else -> return false
                }
                Triple(config.ssid, config.passphrase, security)
            } else {
                val config = WifiManager::class.java.getMethod("getWifiApConfiguration").invoke(wifi) as WifiConfiguration
                Triple(config.SSID?.removeSurrounding("\""), config.preSharedKey?.removeSurrounding("\""), ManualHotspotSecurity.WPA2)
            }
            if (name.isNullOrBlank() || password == null || password.length !in 8..63 ||
                password.all { it == '*' } || name.toByteArray().size > 32) return false
            AirPlayPersistence.saveManualHotspotSsid(context, name)
            AirPlayPersistence.saveManualHotspotPassphrase(context, password)
            AirPlayPersistence.saveManualHotspotSecurity(context, security)
            true
        }.getOrDefault(false)
    }
}
