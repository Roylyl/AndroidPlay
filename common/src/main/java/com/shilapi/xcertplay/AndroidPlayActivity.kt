// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
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
import androidx.activity.OnBackPressedCallback
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
    private var page = "home"
    private val pageHistory = ArrayDeque<String>()
    private var pageContainer: FrameLayout? = null
    private var pageTransition = 0
    private var setupError: String? = null
    private var requestingPermissions = false
    private var connectAfterPermission = false
    private var connectAfterPhone = false
    private var hotspotConfirmed = false
    private var requestingHotspot = false
    private var connectAfterHotspotSettings = false
    private val hotspotSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (connectAfterHotspotSettings) {
            connectAfterHotspotSettings = false
            when (AndroidPlayHotspot.isEnabled(this)) {
                true -> { hotspotConfirmed = true; connect() }
                false -> requestHotspotSettings()
                null -> dialogBuilder().setTitle("确认热点已开启")
                    .setMessage("系统未提供热点状态。请确认本机移动热点已开启，并让iPhone连接该热点。")
                    .setPositiveButton("已开启，继续") { _, _ -> hotspotConfirmed = true; connect() }
                    .setNeutralButton("热点设置") { _, _ -> launchHotspotSettingsForConnection() }
                    .setNegativeButton("取消", null).show()
            }
        }
    }
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
        connectAfterHotspotSettings = savedInstanceState?.getBoolean("connectAfterHotspotSettings") ?: false
        hotspotConfirmed = savedInstanceState?.getBoolean("hotspotConfirmed") ?: false
        page = savedInstanceState?.getString("settingsPage")?.takeUnless { it == "connection" } ?: "home"
        savedInstanceState?.getStringArrayList("settingsHistory")?.let { pageHistory.addAll(it.filterNot { value -> value == "connection" }) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (page != "home") returnPage()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
        AndroidPlayWindow.apply(this)
        AirPlayPersistence.saveWirelessEnabled(this, true)
        AirPlayPersistence.saveWirelessHotspotMode(this, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveClusterMapEnabled(this, false)
        AirPlayPersistence.saveLocationReportingEnabled(this, false)
        AirPlayPersistence.saveHideTopBar(this, true)
        AirPlayPersistence.saveHideBottomBar(this, true)
        setupError = runCatching { AndroidPlayBootstrap.ensure(this) }.exceptionOrNull()?.let {
            "CarPlay认证加载失败：${it.javaClass.simpleName}：${it.message ?: "未知原因"}"
        }
        render()
        // Request every runtime permission used by this wireless-only app at startup.
        if (savedInstanceState == null) requestPermissions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("connectAfterHotspotSettings", connectAfterHotspotSettings)
        outState.putBoolean("hotspotConfirmed", hotspotConfirmed)
        outState.putString("settingsPage", page)
        outState.putStringArrayList("settingsHistory", ArrayList(pageHistory))
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        page = "home"; pageHistory.clear()
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
        CarPlayBackgroundSession.applySettings(this)
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
        if (page != "home") { status = null; connectButton = null; renderSettingsPage(); return }
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val content = column().apply { setPadding(dp(32), dp(20), dp(32), dp(28)) }
        // Only controls avoid the camera hole. The window background and projection cover it.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(32) + cutout.left, dp(20) + cutout.top, dp(32) + cutout.right, dp(28) + cutout.bottom)
            insets
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_androidplay)
            contentDescription = "AndroidPlay"
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(14) })
        header.addView(column().apply {
            addView(label("AndroidPlay", 26, true))
            addView(label("系统热点CarPlay", 16))
        })
        content.addView(header)
        val body = column()
        val left = column().apply { setPadding(dp(20), dp(12), dp(20), dp(12)); background = cardBackground() }
        status = label("准备连接", 24, true)
        left.addView(status)
        left.addView(label("已选设备：${AndroidPlayPreferences.phoneName(this)}", 15))
        left.addView(label("连接前会尝试请求开启本机移动热点，受限时按提示打开系统热点设置；让iPhone接入，首次使用需蓝牙配对。", 15))
        left.addView(label("建议开启热点时不要同时连接其他无线局域网，以免降低CarPlay连接稳定性。", 14).apply { setTextColor(Color.rgb(125, 139, 160)) })
        setupError?.let { left.addView(label(it, 14).apply { setTextColor(Color.rgb(255, 199, 122)) }) }
        if (!granted(Manifest.permission.RECORD_AUDIO)) left.addView(label("麦克风未授权，Siri和通话语音不可用。", 14))
        val right = column()
        connectButton = button("打开CarPlay") { connect() }
        right.addView(connectButton)
        right.addView(button("自动获取热点信息") { acquireHotspotInformation() })
        right.addView(button("CarPlay设置") { carPlaySettings() })
        body.addView(left, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        body.addView(right, LinearLayout.LayoutParams(-1, -2))
        content.addView(body)
        scroll.addView(content)
        presentPage(scroll)
        ViewCompat.requestApplyInsets(scroll)
        refreshStatus()
    }
    /** Animate only navigation; status updates, rotation and preference refreshes stay still. */
    private fun presentPage(view: View) {
        val container = pageContainer ?: FrameLayout(this).apply {
            setBackgroundColor(BG)
            pageContainer = this
            setContentView(this)
        }
        container.setBackgroundColor(BG)
        val direction = pageTransition
        pageTransition = 0
        val previous = container.getChildAt(container.childCount - 1)
        // Finish any interrupted transition before starting another navigation.
        for (index in container.childCount - 1 downTo 0) container.getChildAt(index).animate().cancel()
        container.removeAllViews()
        if (direction == 0 || previous == null || !ValueAnimator.areAnimatorsEnabled()) {
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
            return
        }
        previous.alpha = 1f
        previous.translationX = 0f
        previous.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        container.addView(previous, FrameLayout.LayoutParams(-1, -1))
        container.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.alpha = 0f
        view.translationX = dp(24).toFloat() * direction
        val easing = DecelerateInterpolator()
        previous.animate().alpha(0f).translationX(-dp(12).toFloat() * direction)
            .setDuration(220).setInterpolator(easing).withEndAction { container.removeView(previous) }.start()
        view.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(easing).start()
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
        if (CarPlayBackgroundSession.hasSession() && CarPlayBackgroundSession.settingsRevision != CarPlaySettings.revision(this)) {
            CarPlayBackgroundSession.stop { runOnUiThread { if (!isFinishing && !isDestroyed) connect() } }
            return
        }
        if (!CarPlayBackgroundSession.hasSession()) {
            val enabled = AndroidPlayHotspot.isEnabled(this)
            if (enabled == false || (enabled == null && !hotspotConfirmed)) {
                requestHotspotForConnection()
                return
            }
            AndroidPlayHotspot.importSystemConfiguration(this)
        }
        connectWithHotspot()
    }
    private fun connectWithHotspot() {
        if (AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.MANUAL &&
            (AirPlayPersistence.loadManualHotspotSsid(this).isBlank() ||
                AirPlayPersistence.loadManualHotspotPassphrase(this).length !in 8..63)) {
            editHotspot(resumeConnection = true)
            return
        }
        if (AndroidPlayPreferences.phoneAddress(this) == null) {
            connectAfterPhone = true; choosePhone(); return
        }
        AirPlayPersistence.saveWirelessEnabled(this, true)
        hotspotConfirmed = false
        CarPlayBackgroundSession.actualVideoParameters?.let { text ->
            Toast.makeText(this, text, Toast.LENGTH_SHORT)
                .apply { setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(48)) }.show()
        }
        startActivity(Intent(this, CarPlayHostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    private fun acquireHotspotInformation() {
        if (AndroidPlayHotspot.importSystemConfiguration(this)) {
            CarPlaySettings.changed(this)
            render()
            toast("已获取系统热点名称和密码")
        } else editHotspot(readFailed = true)
    }
    private fun requestHotspotForConnection() {
        if (requestingHotspot) return
        requestingHotspot = true
        var finished = false
        val timeout = Runnable {
            if (!finished && !isFinishing && !isDestroyed) {
                finished = true; requestingHotspot = false; requestHotspotSettings()
            }
        }
        val submitted = AndroidPlayHotspot.requestStart(this) { started ->
            if (finished || isFinishing || isDestroyed) return@requestStart
            finished = true
            requestingHotspot = false
            handler.removeCallbacks(timeout)
            if (started) {
                hotspotConfirmed = true
                // The successful system callback is authoritative even when a vendor's state API is unavailable.
                continueAfterHotspotStarted()
            } else requestHotspotSettings()
        }
        if (submitted) {
            toast("正在向系统申请开启移动热点")
            handler.postDelayed(timeout, 10_000)
        } else {
            finished = true; requestingHotspot = false; requestHotspotSettings()
        }
    }
    private fun continueAfterHotspotStarted() {
        AndroidPlayHotspot.importSystemConfiguration(this)
        connectWithHotspot()
    }
    private fun requestHotspotSettings() {
        dialogBuilder().setTitle("开启移动热点")
            .setMessage("请在系统设置中开启本机WPA2移动热点，并让iPhone连接该热点。返回后继续连接CarPlay。")
            .setPositiveButton("打开热点设置") { _, _ -> launchHotspotSettingsForConnection() }
            .setNegativeButton("取消", null).show()
    }
    private fun launchHotspotSettingsForConnection() {
        connectAfterHotspotSettings = true
        val opened = runCatching { hotspotSettings.launch(Intent("android.settings.TETHER_SETTINGS")) }.isSuccess ||
            runCatching { hotspotSettings.launch(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.isSuccess
        if (!opened) { connectAfterHotspotSettings = false; toast("无法打开热点设置，请手动在系统设置中开启移动热点") }
    }
    private fun showPage(next: String) {
        if (page == next) return
        pageTransition = 1
        pageHistory.addLast(page)
        page = next
        render()
    }
    private fun returnPage() {
        pageTransition = -1
        page = if (pageHistory.isEmpty()) "home" else pageHistory.removeLast()
        if (page == "home") connectAfterPhone = false
        render()
    }
    private fun choosePhone() = showPage("phone")
    private fun carPlaySettings() = showPage("settings")
    private fun soundSettings() = showPage("sound")
    private fun displaySettings() = showPage("fps")
    private fun audioDeviceSettings(input: Boolean) = showPage(if (input) "input" else "output")

    private fun renderSettingsPage() {
        val titles = mapOf("settings" to "CarPlay设置", "phone" to "iPhone选择", "fps" to "帧率", "sound" to "声音", "input" to "输入设备", "output" to "输出设备", "orientation" to "显示方向")
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        val content = column().apply { setPadding(dp(32), dp(20), dp(32), dp(28)) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("‹ 返回", 17).apply {
            setTextColor(Color.rgb(110, 157, 230)); setPadding(0, dp(14), dp(24), dp(14))
            contentDescription = "返回上一级"; isClickable = true; setOnClickListener { returnPage() }
        })
        header.addView(label(titles[page] ?: "CarPlay设置", 26, true))
        content.addView(header)
        fun row(title: String, detail: String = "", value: String = "›", action: () -> Unit) {
            val card = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12)); minimumHeight = dp(72)
                background = cardBackground()
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
                isClickable = true; isFocusable = true; setOnClickListener { action() }
                val ripple = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
                foreground = ripple.getDrawable(0); ripple.recycle()
            }
            card.addView(column().apply {
                addView(label(title, 18, true))
                if (detail.isNotEmpty()) addView(label(detail, 14).apply { setTextColor(Color.rgb(125, 139, 160)) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(label(value, 18).apply { setPadding(dp(20), 0, 0, 0); setTextColor(Color.rgb(110, 157, 230)) })
            content.addView(card)
        }
        when (page) {
            "settings" -> {
                content.addView(label("连接、显示与音频偏好", 15))
                row("iPhone选择", AndroidPlayPreferences.phoneName(this)) { choosePhone() }
                row("帧率", "下次连接生效，失败时在后台尝试较低帧率", "${AirPlayPersistence.loadFps(this)}fps  ›") { displaySettings() }
                row("显示方向", "下次进入CarPlay时生效", if (CarPlaySettings.portrait(this)) "竖屏  ›" else "横屏  ›") { showPage("orientation") }
                row("声音", "输入/输出设备与实时音量") { soundSettings() }
                row("检查应用权限", "连接、麦克风和通知权限") { showPermissionHelp() }
                row("断开连接", "结束CarPlay会话，系统热点保持开启") { resetWifi() }
            }
            "orientation" -> {
                content.addView(label("AndroidPlay自身页面支持横竖屏旋转；CarPlay默认只允许横屏。更改后下次进入CarPlay时生效。", 15))
                for (portrait in listOf(false, true)) row(if (portrait) "竖屏" else "横屏", value = if (portrait == CarPlaySettings.portrait(this)) "✓" else "") {
                    if (portrait != CarPlaySettings.portrait(this)) CarPlaySettings.changed(this)
                    CarPlaySettings.savePortrait(this, portrait); render()
                }
            }
            "fps" -> {
                content.addView(label("只保存偏好，不自动连接或中断当前会话。120fps失败时后台尝试90fps、60fps，设置中的选择保持不变。", 15))
                for (fps in listOf(30, 60, 90, 120)) row("${fps}fps", when (fps) { 60 -> "推荐"; 90, 120 -> "实验性请求"; else -> "" }, if (fps == AirPlayPersistence.loadFps(this)) "✓" else "") {
                    if (fps != AirPlayPersistence.loadFps(this)) CarPlaySettings.changed(this)
                    AirPlayPersistence.saveFps(this, fps); render(); toast("帧率已保存，下次连接生效")
                }
            }
            "sound" -> {
                row("输入设备", audioSelectionLabel(true)) { audioDeviceSettings(true) }
                row("输出设备", audioSelectionLabel(false)) { audioDeviceSettings(false) }
                addVolumeCard(content, false)
                addVolumeCard(content, true)
                content.addView(label("音乐和导航使用媒体音量，通话使用通话音量；Siri保持原有音频通道。", 14))
            }
            "input", "output" -> {
                val input = page == "input"
                val selected = CarPlaySettings.selectedDevice(this, input)
                fun select(key: String?) { if (key != CarPlaySettings.selectedDevice(this, input)) CarPlaySettings.changed(this); CarPlaySettings.saveDevice(this, input, key); CarPlayBackgroundSession.applySettings(this); render() }
                row("系统默认设备", "由Android自动选择音频路由", if (selected == null) "✓" else "") { select(null) }
                val devices = CarPlaySettings.devices(this, input)
                for (device in devices) row(CarPlaySettings.deviceLabel(device), value = if (CarPlaySettings.deviceKey(device) == selected) "✓" else "") { select(CarPlaySettings.deviceKey(device)) }
                content.addView(label("设备列表来自Android，实际可用路由由系统和设备驱动决定。", 14))
            }
            "phone" -> {
                if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                    row("需要蓝牙权限", "允许附近设备权限后选择iPhone") { showPermissionHelp() }
                } else {
                    val adapter = getSystemService(BluetoothManager::class.java)?.adapter
                    val devices = runCatching { adapter?.bondedDevices?.sortedBy { it.name ?: "" }.orEmpty() }.getOrDefault(emptyList())
                    content.addView(label(if (adapter?.isEnabled == true) "选择已在系统蓝牙设置中配对的iPhone" else "请先打开蓝牙并与iPhone配对", 15))
                    for (device in devices) row(device.name ?: "已配对设备", "设备标识：${device.address.takeLast(5)}", if (device.address == AndroidPlayPreferences.phoneAddress(this)) "✓" else "") {
                        val resume = connectAfterPhone; connectAfterPhone = false
                        if (device.address != AndroidPlayPreferences.phoneAddress(this)) CarPlaySettings.changed(this)
                        AndroidPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                        returnPage(); if (resume) connect()
                    }
                }
                row("打开蓝牙设置", "配对新设备或打开蓝牙") { openSystem(Settings.ACTION_BLUETOOTH_SETTINGS) }
            }

        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            content.setPadding(dp(32) + cutout.left, dp(20) + cutout.top, dp(32) + cutout.right, dp(28) + cutout.bottom)
            insets
        }
        scroll.addView(content); presentPage(scroll); ViewCompat.requestApplyInsets(scroll)
    }
    private fun audioSelectionLabel(input: Boolean): String {
        val key = CarPlaySettings.selectedDevice(this, input) ?: return "系统默认设备"
        return CarPlaySettings.devices(this, input).firstOrNull { CarPlaySettings.deviceKey(it) == key }?.let(CarPlaySettings::deviceLabel) ?: "已保存的设备暂不可用，使用系统默认"
    }
    private fun addVolumeCard(content: LinearLayout, call: Boolean) {
        val manager = getSystemService(android.media.AudioManager::class.java)
        val stream = if (call) android.media.AudioManager.STREAM_VOICE_CALL else android.media.AudioManager.STREAM_MUSIC
        val card = column().apply {
            setPadding(dp(20), dp(14), dp(20), dp(16))
            background = cardBackground()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        }
        val title = label("", 18, true)
        fun refresh() { title.text = "${if (call) "通话音量" else "媒体音量（音乐/导航）"} · ${manager.getStreamVolume(stream)}/${manager.getStreamMaxVolume(stream)}" }
        refresh(); card.addView(title)
        card.addView(SeekBar(this).apply {
            min = manager.getStreamMinVolume(stream); max = manager.getStreamMaxVolume(stream); progress = manager.getStreamVolume(stream)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(110, 157, 230))
            thumbTintList = progressTintList
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) { manager.setStreamVolume(stream, value, 0); refresh() } }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) { CarPlaySettings.changed(this@AndroidPlayActivity) }
            })
        })
        content.addView(card)
    }
    private fun openHotspotSettings() {
        val intent = Intent("android.settings.TETHER_SETTINGS")
        runCatching { startActivity(intent) }.onFailure { openSystem(Settings.ACTION_WIRELESS_SETTINGS) }
    }
    private fun editHotspot(resumeConnection: Boolean = false, readFailed: Boolean = false) {
        val content = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val ssid = EditText(dialogContext).apply { hint = "热点名称"; setSingleLine(); setText(AirPlayPersistence.loadManualHotspotSsid(this@AndroidPlayActivity)) }
        val password = EditText(dialogContext).apply { hint = "WPA2热点密码（8至63位）"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(AirPlayPersistence.loadManualHotspotPassphrase(this@AndroidPlayActivity)) }
        content.addView(label((if (readFailed) "自动获取热点信息失败，请手动输入。" else "") + "请先在Android系统中开启WPA2热点，在这里填写完全相同的名称和密码，再让iPhone连接该热点。", 15))
        content.addView(ssid); content.addView(password)
        val dialog = dialogBuilder().setTitle("本机系统热点").setView(content).setPositiveButton("保存", null).setNegativeButton("取消") { _, _ -> if (resumeConnection) hotspotConfirmed = false }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ssid.text.toString().trim(); val key = password.text.toString()
                if (name.isBlank() || name.toByteArray().size > 32 || key.length !in 8..63) {
                    toast("请填写有效的热点名称和8至63位密码"); return@setOnClickListener
                }
                updateNetwork(afterSave = if (resumeConnection) ({ connectWithHotspot() }) else null) {
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
    private fun updateNetwork(reconnect: Boolean = false, afterSave: (() -> Unit)? = null, save: () -> Unit) {
        save()
        CarPlaySettings.changed(this)
        render()
        if (afterSave != null) afterSave() else if (reconnect) connect() else toast("已保存，请点击打开CarPlay重新协商")
    }
    private fun resetWifi() {
        CarPlayBackgroundSession.stop { runOnUiThread {
            render()
            toast("连接已停止，系统热点保持开启。请点击打开CarPlay重试。")
        } }
    }
    private fun showPermissionHelp() {
        dialogBuilder().setTitle("应用权限")
            .setMessage("连接需要蓝牙和附近设备权限，旧版Android还需要精确位置权限。麦克风用于Siri和通话，通知用于显示连接状态。请在系统弹窗中允许；已拒绝的权限可在应用设置中开启。")
            .setPositiveButton("重新申请") { _, _ -> requestPermissions() }
            .setNeutralButton("应用设置") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }.setNegativeButton("关闭", null).show()
    }
    private fun openSystem(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure { toast("无法打开系统设置，请手动打开") }
    }
    private val dialogContext: android.content.Context get() = android.view.ContextThemeWrapper(this,
        if (CarPlaySettings.night(this)) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert)
    private fun dialogBuilder() = AlertDialog.Builder(dialogContext)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(if (CarPlaySettings.night(this@AndroidPlayActivity)) Color.rgb(232, 239, 250) else Color.rgb(22, 32, 48)); setPadding(0, dp(6), 0, dp(6))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun cardBackground() = GradientDrawable().apply {
        setColor(if (CarPlaySettings.night(this@AndroidPlayActivity)) Color.rgb(25, 34, 49) else Color.WHITE)
        cornerRadius = dp(18).toFloat()
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(Color.rgb(110, 157, 230)); setPadding(dp(20), dp(12), dp(20), dp(12))
        background = cardBackground()
        layoutParams = LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(10) }
        val ripple = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = ripple.getDrawable(0); ripple.recycle()
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val BG: Int get() = if (CarPlaySettings.night(this)) Color.rgb(12, 17, 27) else Color.rgb(245, 247, 250)
}
