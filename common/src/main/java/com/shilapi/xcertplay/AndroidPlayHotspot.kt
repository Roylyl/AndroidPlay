package com.shilapi.xcertplay

import android.content.Context
import android.net.TetheringManager
import android.net.ConnectivityManager
import java.net.NetworkInterface
import java.net.Inet4Address
import java.util.Collections
import androidx.annotation.RequiresApi
import android.net.wifi.WifiManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.SoftApConfiguration
import android.os.Build
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity

/** Best effort only: many stock systems restrict access to the tethering password. */
internal object AndroidPlayHotspot {
    private var concurrencyWarningShown = false
    fun warnIfConcurrent(context: Context) {
        val wifiEnabled = runCatching {
            context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true
        }.getOrDefault(false)
        val concurrent = wifiEnabled && isEnabled(context) == true
        if (concurrent && !concurrencyWarningShown) {
            android.widget.Toast.makeText(context,
                AndroidPlayLanguage.text(context, "同时开启热点和无线局域网可能会造成CarPlay连接不稳定"),
                android.widget.Toast.LENGTH_LONG).show()
        }
        concurrencyWarningShown = concurrent
    }

    /** null means the firmware hides hotspot state; never mistake stored credentials for an active AP. */
    fun isEnabled(context: Context): Boolean? {
        val reported = runCatching {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return@runCatching null
            WifiManager::class.java.getMethod("isWifiApEnabled").invoke(wifi) as Boolean
        }.getOrNull()
        if (reported != null) return reported
        // Tethered AP interfaces aren't client networks. Exclude Wi-Fi client/VPN/mobile
        // interfaces before treating an AP-shaped interface with a private IPv4 as ready.
        val apPresent = runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val clients = connectivity?.allNetworks?.mapNotNull { connectivity.getLinkProperties(it)?.interfaceName }?.toSet().orEmpty()
            Collections.list(NetworkInterface.getNetworkInterfaces()).any { network ->
                network.name !in clients && !network.isLoopback && network.isUp &&
                    network.name.matches(Regex("^(ap|swlan|wlan|softap)[0-9_].*")) &&
                    Collections.list(network.inetAddresses).any { it is Inet4Address && it.isSiteLocalAddress }
            }
        }.getOrDefault(false)
        return if (apPresent) true else null
    }

    /** True only means a request was submitted. Success/failure is delivered asynchronously. */
    fun requestStart(context: Context, callback: (Boolean) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching { Api36.requestStart(context, callback); true }.getOrDefault(false)
    }
    @RequiresApi(36)
    private object Api36 {
        fun requestStart(context: Context, callback: (Boolean) -> Unit) {
            val manager = context.getSystemService(TetheringManager::class.java)
                ?: error("Tethering service unavailable")
            manager.startTethering(TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build(),
                context.mainExecutor, object : TetheringManager.StartTetheringCallback {
                    override fun onTetheringStarted() { callback(true) }
                    override fun onTetheringFailed(error: Int) { callback(false) }
                })
        }
    }

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
