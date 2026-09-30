// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.media.AndroidMediaSink

internal object CarPlaySettings {
    private fun prefs(context: Context) = context.getSharedPreferences("androidplay", Context.MODE_PRIVATE)
    // Only native Android UI follows the system; old CarPlay theme preferences are ignored.
    fun night(context: Context, uiMode: Int = context.resources.configuration.uiMode): Boolean = isDarkMode(uiMode)
    fun revision(context: Context): Long = prefs(context).getLong("settings_revision", 0)
    fun changed(context: Context) { prefs(context).edit().putLong("settings_revision", revision(context) + 1).apply() }
    fun portrait(context: Context): Boolean = prefs(context).getBoolean("carplay_portrait", false)
    fun savePortrait(context: Context, value: Boolean) { prefs(context).edit().putBoolean("carplay_portrait", value).apply() }
    fun deviceKey(device: AudioDeviceInfo) = "${device.type}:${device.address}:${device.productName}"
    fun selectedDevice(context: Context, input: Boolean): String? = prefs(context).getString(if (input) "audio_input" else "audio_output", null)
    fun saveDevice(context: Context, input: Boolean, value: String?) { prefs(context).edit().putString(if (input) "audio_input" else "audio_output", value).apply() }
    fun devices(context: Context, input: Boolean): List<AudioDeviceInfo> = context.getSystemService(AudioManager::class.java)
        .getDevices(if (input) AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS).toList()
    fun deviceLabel(device: AudioDeviceInfo): String = "${device.productName}（${when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "麦克风"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        else -> "设备${device.id}"
    }}）"
    fun applyAudio(context: Context, sink: AndroidMediaSink) {
        fun resolve(input: Boolean) = devices(context, input).firstOrNull { deviceKey(it) == selectedDevice(context, input) }
        sink.setAudioDevices(resolve(true), resolve(false))
    }
}

/** Reapply the user's preferred route on plug/unplug; a missing device falls back to the system. */
internal class CarPlayAudioDeviceWatcher(context: Context, private val sink: AndroidMediaSink) : AutoCloseable {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AudioManager::class.java)
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { apply() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { apply() }
    }
    init {
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        apply()
    }
    fun apply() = CarPlaySettings.applyAudio(app, sink)
    override fun close() { manager.unregisterAudioDeviceCallback(callback) }
}
