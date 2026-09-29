// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** A single-purpose, Chinese wireless CarPlay receiver. */
class AndroidPlayActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var status: TextView? = null
    private var connectButton: Button? = null
    private var setupError: String? = null
    private var requestingPermissions = false
    private var connectAfterPermission = false
    private var connectAfterPhone = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestingPermissions = false
        render()
        if (connectAfterPermission) {
            connectAfterPermission = false
            if (connectionPermissions().all(::granted)) connect()
            else showPermissionHelp()
        }
    }
    private val tick = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 1500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlayWindow.apply(this)
        AirPlayPersistence.saveWirelessEnabled(this, true)
        AirPlayPersistence.saveWirelessHotspotMode(this, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveClusterMapEnabled(this, false)
        AirPlayPersistence.saveLocationReportingEnabled(this, false)
        AirPlayPersistence.saveHideTopBar(this, true)
        AirPlayPersistence.saveHideBottomBar(this, true)
        if (!CarPlayBackgroundSession.hasSession()) AndroidPlayHotspot.importSystemConfiguration(this)
        setupError = runCatching { AndroidPlayBootstrap.ensure(this) }.exceptionOrNull()?.let {
            "CarPlay认证加载失败：${it.javaClass.simpleName}：${it.message ?: "未知原因"}"
        }
        render()
        // Request every runtime permission used by this wireless-only app at startup.
        if (savedInstanceState == null) requestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
        if (intent.getStringExtra("page") == "wireless-recovery") resetWifi()
    }
    override fun onResume() {
        super.onResume()
        AndroidPlayWindow.apply(this)
        render()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AndroidPlayWindow.apply(this)
        render()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) AndroidPlayWindow.apply(this)
    }

    private fun connectionPermissions() = buildList {
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    private fun allPermissions() = connectionPermissions() + buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun requestPermissions() {
        val missing = allPermissions().filterNot(::granted)
        if (missing.isNotEmpty() && !requestingPermissions) {
            requestingPermissions = true
            permissions.launch(missing.toTypedArray())
        }
    }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val content = column().apply { setPadding(dp(32), dp(20), dp(32), dp(24)) }
        // Only controls avoid the camera hole. The window background and projection cover it.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(32) + cutout.left, dp(20) + cutout.top, dp(32) + cutout.right, dp(24) + cutout.bottom)
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_androidplay)
            contentDescription = "AndroidPlay"
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(14) })
        header.addView(column().apply {
            addView(label("AndroidPlay", 28, true))
            addView(label("系统热点CarPlay", 16))
        })
        content.addView(header)
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = column().apply { setPadding(0, dp(12), dp(24), 0) }
        status = label("准备连接", 24, true)
        left.addView(status)
        left.addView(label("已选设备：${AndroidPlayPreferences.phoneName(this)}", 15))
        left.addView(label("先在本机系统中开启WPA2热点，让iPhone连接该热点；首次使用仍需蓝牙配对。", 15))
        setupError?.let { left.addView(label(it, 14).apply { setTextColor(Color.rgb(255, 199, 122)) }) }
        if (!granted(Manifest.permission.RECORD_AUDIO)) left.addView(label("麦克风未授权，Siri和通话语音不可用。", 14))
        val right = column()
        connectButton = button("打开CarPlay") { connect() }
        right.addView(connectButton)
        right.addView(button("CarPlay设置") { displaySettings() })
        right.addView(button("选择iPhone") { choosePhone() })
        right.addView(button("连接设置") { connectionSettings() })
        body.addView(left, LinearLayout.LayoutParams(0, -2, 1.2f))
        body.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(body)
        scroll.addView(content)
        setContentView(scroll)
        ViewCompat.requestApplyInsets(scroll)
        refreshStatus()
    }
    private fun refreshStatus() {
        val running = CarPlayBackgroundSession.hasSession()
        status?.text = when {
            CarPlayBackgroundSession.active -> "CarPlay已连接"
            running -> CarPlayBackgroundSession.connectionStatus
            !connectionPermissions().all(::granted) -> "需要连接权限"
            setupError != null -> "需要CarPlay认证文件"
            else -> "准备连接"
        }
        connectButton?.text = "打开CarPlay"
    }
    private fun connect() {
        if (!connectionPermissions().all(::granted)) {
            connectAfterPermission = true
            requestPermissions()
            return
        }
        if (setupError != null) { toast(setupError!!); return }
        if (!CarPlayBackgroundSession.hasSession()) AndroidPlayHotspot.importSystemConfiguration(this)
        if (AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            (AirPlayPersistence.loadManualHotspotSsid(this).isBlank() ||
                AirPlayPersistence.loadManualHotspotPassphrase(this).length !in 8..63)) {
            toast("请填写本机系统热点的名称和密码")
            editHotspot()
            return
        }
        if (AndroidPlayPreferences.phoneAddress(this) == null) {
            connectAfterPhone = true; choosePhone(); return
        }
        AirPlayPersistence.saveWirelessEnabled(this, true)
        startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    private fun choosePhone() {
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            showPermissionHelp(); return
        }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this).setTitle("打开蓝牙")
                .setMessage("请打开本机蓝牙，并与iPhone配对。")
                .setPositiveButton("蓝牙设置") { _, _ -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
                .setNegativeButton("取消", null).show(); return
        }
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        AlertDialog.Builder(this).setTitle(if (devices.isEmpty()) "请先配对iPhone" else "选择iPhone")
            .setItems(devices.map { "${it.name ?: "已配对设备"} · ${it.address.takeLast(5)}" }.toTypedArray()) { _, index ->
                val device = devices[index]
                AndroidPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                val resume = connectAfterPhone; connectAfterPhone = false
                CarPlayBackgroundSession.stop { runOnUiThread { render(); if (resume) connect() } }
            }
            .setNeutralButton("配对新设备") { _, _ -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
            .setNegativeButton("取消") { _, _ -> connectAfterPhone = false }.show()
    }
    private fun connectionSettings() {
        AlertDialog.Builder(this).setTitle("连接设置")
            .setItems(arrayOf("自动读取热点信息", "设置热点信息", "打开系统热点设置", "打开蓝牙设置", "检查应用权限", "断开连接")) { _, index ->
                when (index) {
                    0 -> CarPlayBackgroundSession.stop { runOnUiThread {
                        val read = AndroidPlayHotspot.importSystemConfiguration(this)
                        render()
                        toast(if (read) "已自动读取系统热点名称和密码" else "系统不允许读取完整热点信息，已保留原配置；需要手动填写一次。")
                    } }
                    1 -> editHotspot()
                    2 -> openHotspotSettings()
                    3 -> openSystem(Settings.ACTION_BLUETOOTH_SETTINGS)
                    4 -> showPermissionHelp()
                    5 -> resetWifi()
                }
            }.setNegativeButton("关闭", null).show()
    }
    private fun displaySettings() {
        AlertDialog.Builder(this).setTitle("CarPlay设置")
            .setItems(arrayOf("CarPlay帧率", "图标和文字缩放")) { _, index ->
                if (index == 0) {
                    val values = listOf(30, 60, 90, 120)
                    AlertDialog.Builder(this).setTitle("CarPlay帧率（高刷为实验性请求）")
                        .setSingleChoiceItems(arrayOf("30fps", "60fps（推荐）", "90fps（实验性）", "120fps（实验性）"), values.indexOf(AirPlayPersistence.loadFps(this))) { dialog, which ->
                            dialog.dismiss()
                            updateNetwork(reconnect = true) { AirPlayPersistence.saveFps(this, values[which]) }
                        }.setNegativeButton("取消", null).show()
                } else {
                    val values = listOf(75, 100, 125, 150)
                    AlertDialog.Builder(this).setTitle("图标和文字大小；选择后自动重连")
                        .setSingleChoiceItems(arrayOf("75%（小）", "100%（默认）", "125%（大）", "150%（更大）"), values.indexOf(AirPlayPersistence.loadUiScalePercent(this))) { dialog, which ->
                            dialog.dismiss()
                            updateNetwork(reconnect = true) { AirPlayPersistence.saveUiScalePercent(this, values[which]) }
                        }.setNegativeButton("取消", null).show()
                }
            }.setNegativeButton("关闭", null).show()
    }
    private fun openHotspotSettings() {
        val intent = Intent("android.settings.TETHER_SETTINGS")
        runCatching { startActivity(intent) }.onFailure { openSystem(Settings.ACTION_WIRELESS_SETTINGS) }
    }
    private fun editHotspot() {
        val content = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val ssid = EditText(this).apply { hint = "热点名称"; setSingleLine(); setText(AirPlayPersistence.loadManualHotspotSsid(this@AndroidPlayActivity)) }
        val password = EditText(this).apply { hint = "WPA2热点密码（8至63位）"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(AirPlayPersistence.loadManualHotspotPassphrase(this@AndroidPlayActivity)) }
        content.addView(label("请先在Android系统中开启WPA2热点，在这里填写完全相同的名称和密码，再让iPhone连接该热点。", 15))
        content.addView(ssid); content.addView(password)
        val dialog = AlertDialog.Builder(this).setTitle("本机系统热点").setView(content).setPositiveButton("保存", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ssid.text.toString().trim(); val key = password.text.toString()
                if (name.isBlank() || name.toByteArray().size > 32 || key.length !in 8..63) {
                    toast("请填写有效的热点名称和8至63位密码"); return@setOnClickListener
                }
                updateNetwork {
                    AirPlayPersistence.saveManualHotspotSsid(this, name)
                    AirPlayPersistence.saveManualHotspotPassphrase(this, key)
                    AirPlayPersistence.saveManualHotspotSecurity(this, ManualHotspotSecurity.WPA2)
                    AirPlayPersistence.saveManualHotspotBand(this, ManualHotspotBand.AUTO)
                    AirPlayPersistence.saveManualHotspotChannel(this, 0)
                    AirPlayPersistence.saveWirelessHotspotMode(this, WirelessHotspotMode.MANUAL)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun updateNetwork(reconnect: Boolean = false, save: () -> Unit) {
        CarPlayBackgroundSession.stop { runOnUiThread {
            save()
            render()
            if (reconnect) connect() else toast("已保存，请重新连接")
        } }
    }
    private fun resetWifi() {
        CarPlayBackgroundSession.stop { runOnUiThread {
            render()
            toast("连接已停止，系统热点保持开启。请点击打开CarPlay重试。")
        } }
    }
    private fun showPermissionHelp() {
        AlertDialog.Builder(this).setTitle("应用权限")
            .setMessage("连接需要蓝牙和附近设备权限，旧版Android还需要精确位置权限。麦克风用于Siri和通话，通知用于显示连接状态。请在系统弹窗中允许；已拒绝的权限可在应用设置中开启。")
            .setPositiveButton("重新申请") { _, _ -> requestPermissions() }
            .setNeutralButton("应用设置") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }.setNegativeButton("关闭", null).show()
    }
    private fun openSystem(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure { toast("无法打开系统设置，请手动打开") }
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(Color.rgb(232, 239, 250)); setPadding(0, dp(6), 0, dp(6))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 16f; setTextColor(BG)
        background = GradientDrawable().apply { setColor(Color.rgb(166, 200, 255)); cornerRadius = dp(16).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) }
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private companion object { val BG = Color.rgb(12, 17, 27) }
}
