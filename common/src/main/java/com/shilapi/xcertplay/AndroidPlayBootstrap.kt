package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.orchestration.MfiTarget
import java.io.File
import java.security.MessageDigest

/** Installs the private beta's experimental identity. It has no remote fallback. */
internal object AndroidPlayBootstrap {
    @Volatile private var ready = false

    @Synchronized fun ensure(context: Context) {
        if (ready) return
        val target = File(context.noBackupFilesDir, LocalMfiAuthenticationClient.DIRECTORY)
        if (runCatching { LocalMfiAuthenticationClient.load(target) }.isFailure) {
            val staging = File(context.noBackupFilesDir, "offline-mfi-staging")
            staging.deleteRecursively()
            check(staging.mkdirs()) { "Could not prepare local authentication" }
            staging.setReadable(false, false); staging.setReadable(true, true)
            staging.setExecutable(false, false); staging.setExecutable(true, true)
            try {
                for (name in listOf("identity.pk8", "certificate.p7b")) {
                    val file = File(staging, name)
                    context.assets.open("offline-mfi/$name").use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file.setReadable(false, false); file.setReadable(true, true)
                    file.setWritable(false, false); file.setWritable(true, true)
                }
                LocalMfiAuthenticationClient.load(staging)
                if (target.exists()) check(target.deleteRecursively()) { "无法清理旧认证缓存" }
                check(staging.renameTo(target)) { "Could not install local authentication" }
            } finally {
                staging.deleteRecursively()
            }
        }
        LocalMfiAuthenticationClient.load(target)
        AirPlayPersistence.saveMfiTarget(context, MfiTarget.LOCAL)
        AirPlayPersistence.saveDebugLogsEnabled(context, false)
        ready = true
    }

    fun deviceId(identity: AirPlayIdentity): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(identity.publicKey).take(6).toByteArray()
        bytes[0] = ((bytes[0].toInt() and 0xfc) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
}

internal object AndroidPlayPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("androidplay", Context.MODE_PRIVATE)
    fun phoneAddress(context: Context): String? = prefs(context).getString("phone_address", null)
    fun phoneName(context: Context): String = prefs(context).getString("phone_name", null) ?: "未选择iPhone"
    fun savePhone(context: Context, address: String, name: String) {
        prefs(context).edit().putString("phone_address", address).putString("phone_name", name).apply()
    }
    fun autoConnect(context: Context) = prefs(context).getBoolean("auto_connect", false)
    fun saveAutoConnect(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("auto_connect", value).apply()
    }
}
