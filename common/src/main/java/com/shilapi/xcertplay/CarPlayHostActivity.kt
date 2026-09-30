package com.shilapi.xcertplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import com.shilapi.xcertplay.airplay.AirPlayPhysicalSizeMm
import com.shilapi.xcertplay.airplay.CarPlayFrameRateFallback
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayIcon
import com.shilapi.xcertplay.airplay.AirPlaySafeArea
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.CarPlayMediaEngine
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.location.AndroidCarPlayLocationProvider
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.media.CarPlayTouchMapper
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import com.shilapi.xcertplay.orchestration.CarPlayTransport
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen CarPlay host. It renders decoded video through a [TextureView], forwards touch to
 * the active AirPlay session, and drives wireless bring-up through
 * [CarPlayController].

 */
class CarPlayHostActivity : ComponentActivity() {
    private var connectionPanel: View? = null
    private var wifiRecoveryButton: View? = null
    private var reconnectAttempts = 0
    private lateinit var airPlayIdentity: AirPlayIdentity

    private fun createRuntimeConfig(): CarPlayRuntimeConfig = CarPlayRuntimeConfig(
        mfiTarget = MfiTarget.LOCAL,
        identification = Iap2IdentificationConfig(
            name = "AndroidPlay",
            modelIdentifier = normalizedModel(),
            manufacturer = normalizedManufacturer(),
            serialNumber = "ANDROIDPLAY-" + AndroidPlayBootstrap.deviceId(airPlayIdentity).replace(":", ""),
            firmwareVersion = "1.0.1",
            hardwareVersion = "1.0",
            carPlayUsbInterfaceNumber = 3,
            locationInformationEnabled = locationReportingEnabled,
        ),
        label = "AndroidPlay",
        hostName = "androidplay-" + AndroidPlayBootstrap.deviceId(airPlayIdentity).replace(":", "").lowercase(),
        hostMac = AndroidPlayBootstrap.deviceId(airPlayIdentity).split(":").map { it.toInt(16).toByte() }.toByteArray(),
        wirelessBluetoothDeviceAddress = AndroidPlayPreferences.phoneAddress(this),
        transport = CarPlayTransport.WIRELESS,
        wirelessHotspotMode = WirelessHotspotMode.MANUAL,
        manualHotspotSsid = manualHotspotSsid,
        manualHotspotPassphrase = manualHotspotPassphrase,
        manualHotspotBand = manualHotspotBand,
        manualHotspotChannel = manualHotspotChannel,
        manualHotspotSecurity = manualHotspotSecurity,
        locationReportingEnabled = locationReportingEnabled,
    )

    private val wirelessPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            awaitingWirelessPermissions = false
            wirelessPermissionsReady = hasRequiredWirelessPermissions()
            appendLog(
                if (wirelessPermissionsReady) {
                    "Wireless startup permissions granted"
                } else {
                    "Wireless startup permissions denied"
                },
            )
            maybeStartCarPlay()
        }
    private val microphonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            microphoneAvailable = granted
            microphonePermissionResolved = true
            appendLog(if (granted) "Microphone permission granted" else "Microphone permission denied")
            requestStartupPrerequisites()
        }
    private var videoView: TextureView? = null
    private var gestureOverlay: View? = null
    private var statusView: TextView? = null
    private var statusScrollView: ScrollView? = null
    private var stageStatusView: TextView? = null
    private var sink: AndroidMediaSink? = null
    private var controller: CarPlayController? = null
    private var currentSurface: Surface? = null
    private var currentSurfaceTexture: SurfaceTexture? = null
    private var activeDisplaySize: DisplaySize? = null
    private var pendingDisplaySize: DisplaySize? = null
    private var displayDiagnosticAttempt: String? = null
    private var hevcEnabled = true
    private var hevcSoftwareDecoderEnabled = false
    private var advancedAudioChannelMappingSupported = false
    private var advancedAudioChannelMapping = false
    private var debugLogsEnabled = false
    private var autoStartOnBoot = false
    private var manufacturer = AirPlayPersistence.DEFAULT_MANUFACTURER
    private var model = AirPlayPersistence.DEFAULT_MODEL
    private var oemLabel = AirPlayPersistence.DEFAULT_OEM_LABEL
    private var fps = AirPlayDisplaySettings.DEFAULT_FPS
    private var widthPhysicalMm = AirPlayDisplaySettings.DEFAULT_WIDTH_PHYSICAL_MM
    private var physicalSizeBasis = AirPlayDisplaySettings.DEFAULT_PHYSICAL_SIZE_BASIS
    private var maximumDetectedWidthPixels = 0
    private var maximumDetectedHeightPixels = 0
    private var rightHandDrive = false
    private var hideTopBar = true
    private var hideBottomBar = true
    private var safeAreaDrawOutside = true
    private var locationReportingEnabled = false
    private var locationPermissionAvailable = false
    private var microphoneAvailable = false
    private var microphonePermissionResolved = false
    private var wirelessEnabled = true
    private var mfiTarget = MfiTarget.LOCAL
    private var mfiI2cPath = AirPlayPersistence.DEFAULT_MFI_I2C_PATH
    private var remoteMfiServer = ""
    private var remoteMfiToken = ""
    private var wirelessPermissionsReady = false
    private var wirelessHotspotMode = WirelessHotspotMode.MANUAL
    private var manualHotspotSsid = ""
    private var manualHotspotPassphrase = ""
    private var manualHotspotBand = ManualHotspotBand.AUTO
    private var manualHotspotChannel = 0
    private var manualHotspotSecurity = ManualHotspotSecurity.OPEN
    private var awaitingWirelessPermissions = false
    private var menuOpen = false
    private var latestStage = "Preparing CarPlay"
    private var darkMode = false
    private var activeAirPlaySession: AirPlaySession? = null
    private val activeScreenStreamTypes = mutableSetOf<Int>()
    private var handshakeResetInProgress = false
    private var restartGeneration = 0
    private var reconnectScheduled = false
    private var sessionLog: SessionLogFile? = null
    private var gestureSequenceActive = false
    private var gestureTracking = false
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private val shuttingDown = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var frameRateGuard = CarPlayFrameRateFallback(60)
    private var frameRateTimeout: Runnable? = null

    private fun armFrameRateFallback(generation: Int) {
        if (generation != restartGeneration || !frameRateGuard.negotiationStarted()) return
        val timeout = Runnable {
            if (generation == restartGeneration) retryLowerFrameRate()
        }
        frameRateTimeout = timeout
        mainHandler.postDelayed(timeout, 20_000L)
    }

    private fun cancelFrameRateFallback() {
        frameRateTimeout?.let(mainHandler::removeCallbacks)
        frameRateTimeout = null
        frameRateGuard.cancel()
    }

    private fun retryLowerFrameRate(): Boolean {
        if (shuttingDown.get() || menuOpen || handshakeResetInProgress ||
            !CarPlayBackgroundSession.isOwner(this)) return false
        val previous = fps
        val next = frameRateGuard.failed() ?: return false
        cancelFrameRateFallback()
        AirPlayPersistence.saveFps(this, next)
        fps = next
        val message = "${previous}fps协商未启动视频，改用${next}fps重连"
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        restartCarPlay(message)
        return true
    }
    private val teardownExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val airPlayCommandExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val logLines = ArrayDeque<LogEntry>()
    private val expireOldLogLines = Runnable { refreshLogView(System.currentTimeMillis()) }
    private val applyDisplaySize = Runnable {
        val size = pendingDisplaySize ?: return@Runnable
        pendingDisplaySize = null
        applyDisplaySize(size)
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            val existing = currentSurface
            val surface = if (
                existing != null &&
                currentSurfaceTexture === texture &&
                existing.isValid
            ) {
                existing
            } else {
                Surface(texture).also {
                    existing?.release()
                    currentSurface = it
                    currentSurfaceTexture = texture
                }
            }
            appendLog(if (existing === surface) "Texture surface reused" else "Texture surface created")
            attachSurface(surface)
            scheduleDisplaySize(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            scheduleDisplaySize(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            if (currentSurfaceTexture !== texture) return true
            currentSurface?.let { surface ->
                sink?.clearSurface(SCREEN_TYPE_MAIN, surface)
                sink?.clearSurface(SCREEN_TYPE_ALT, surface)
                surface.release()
            }
            currentSurface = null
            currentSurfaceTexture = null
            appendLog("Texture surface destroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidPlayWindow.apply(this)
        AirPlayPersistence.saveWirelessEnabled(this, true)
        if (runCatching { AndroidPlayBootstrap.ensure(this) }.isFailure) {
            startActivity(Intent(this, AndroidPlayActivity::class.java))
            finish(); return
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initializeSessionLog()
        darkMode = isDarkMode(resources.configuration.uiMode)
        advancedAudioChannelMappingSupported =
            resources.getBoolean(R.bool.config_advanced_audio_channel_mapping)
        airPlayIdentity = AirPlayPersistence.loadIdentity(this)
        loadPersistedSettings()
        locationPermissionAvailable = hasFineLocationPermission()
        setContentView(buildContentView())
        applyFullscreenMode()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val current = controller
                    if (current == null || !CarPlayBackgroundSession.active) {
                        showAndroidPlayHome()
                        return
                    }
                    val generation = restartGeneration
                    current.sendBack { sent ->
                        if (generation != restartGeneration || controller !== current) return@sendBack
                        if (!sent) {
                            android.widget.Toast.makeText(this@CarPlayHostActivity,
                                "CarPlay返回操作未发送，请等待连接恢复", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )

        appendLog(
            "Host started; MFI target=${mfiTargetLabel(mfiTarget)}; " +
                "transport=${if (wirelessEnabled) "wireless" else "wired"}",
        )
        val reusedBackgroundSession = adoptBackgroundSession()
        microphoneAvailable =
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        microphonePermissionResolved = microphoneAvailable
        if (reusedBackgroundSession) {
            updateDebugOverlays()
        } else if (microphonePermissionResolved) {
            requestStartupPrerequisites()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun loadPersistedSettings() {
        // Apply the user setting to the physical dimensions advertised in the next session.
        hevcEnabled = AirPlayPersistence.loadHevcEnabled(this)
        hevcSoftwareDecoderEnabled =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                AirPlayPersistence.loadHevcSoftwareDecoderEnabled(this)
        advancedAudioChannelMapping =
            advancedAudioChannelMappingSupported &&
                AirPlayPersistence.loadAdvancedAudioChannelMapping(this)
        debugLogsEnabled = AirPlayPersistence.loadDebugLogsEnabled(this)
        autoStartOnBoot = AirPlayPersistence.loadAutoStartOnBoot(this)
        manufacturer = AirPlayPersistence.loadManufacturer(this)
        model = AirPlayPersistence.loadModel(this)
        oemLabel = AirPlayPersistence.loadOemLabel(this)
        fps = AirPlayPersistence.loadFps(this)
        widthPhysicalMm = AirPlayPersistence.loadWidthPhysicalMm(this)
        physicalSizeBasis = AirPlayPersistence.loadPhysicalSizeBasis(this)
        AirPlayPersistence.loadMaximumDetectedDisplay(this).let { (width, height) ->
            maximumDetectedWidthPixels = width
            maximumDetectedHeightPixels = height
        }
        rightHandDrive = AirPlayPersistence.loadRightHandDrive(this)
        hideTopBar = true
        hideBottomBar = true
        safeAreaDrawOutside = AirPlayPersistence.loadSafeAreaDrawOutside(this)
        locationReportingEnabled = false
        locationPermissionAvailable = hasFineLocationPermission()
        wirelessEnabled = true
        mfiTarget = AirPlayPersistence.loadMfiTarget(this)
        mfiI2cPath = AirPlayPersistence.loadMfiI2cPath(this)
        remoteMfiServer = AirPlayPersistence.loadRemoteMfiServer(this)
        remoteMfiToken = AirPlayPersistence.loadRemoteMfiToken(this)
        wirelessHotspotMode = WirelessHotspotMode.MANUAL
        manualHotspotSsid = AirPlayPersistence.loadManualHotspotSsid(this)
        manualHotspotPassphrase = AirPlayPersistence.loadManualHotspotPassphrase(this)
        manualHotspotBand = AirPlayPersistence.loadManualHotspotBand(this)
        manualHotspotChannel = AirPlayPersistence.loadManualHotspotChannel(this)
        manualHotspotSecurity = AirPlayPersistence.loadManualHotspotSecurity(this)
        wirelessPermissionsReady = !wirelessEnabled || hasRequiredWirelessPermissions()
    }

    private fun requestStartupPrerequisites() {
        requestWirelessPermissions()
    }

    private fun hasFineLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestWirelessPermissions() {
        val permissions = requiredWirelessPermissions()
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            wirelessPermissionsReady = true
            maybeStartCarPlay()
            return
        }
        wirelessPermissionsReady = false
        awaitingWirelessPermissions = true
        wirelessPermissions.launch(permissions.toTypedArray())
    }

    private fun hasRequiredWirelessPermissions(): Boolean =
        requiredWirelessPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requiredWirelessPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        else -> listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFullscreenMode()
    }

    override fun onResume() {
        super.onResume()
        locationPermissionAvailable = hasFineLocationPermission()
        wirelessPermissionsReady = !wirelessEnabled || hasRequiredWirelessPermissions()
        maybeStartCarPlay()
        applyFullscreenMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreenMode()
    }

    override fun onStop() {
        // The controller, USB/iAP2 link, and VPN attachment intentionally outlive the UI.
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val nextDarkMode = isDarkMode(newConfig.uiMode)
        if (nextDarkMode != darkMode) {
            darkMode = nextDarkMode
            syncAirPlayDarkMode()
        }
        applyFullscreenMode()
        stageStatusView?.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        scrollLogsToBottom()
        videoView?.post {
            val view = videoView ?: return@post
            scheduleDisplaySize(view.width, view.height)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(applyDisplaySize)
        mainHandler.removeCallbacks(expireOldLogLines)
        currentSurface?.let { surface ->
            sink?.clearSurface(SCREEN_TYPE_MAIN, surface)
            sink?.clearSurface(SCREEN_TYPE_ALT, surface)
            surface.release()
        }
        currentSurface = null
        currentSurfaceTexture = null
        sessionLog?.append("Activity destroyed")
        sessionLog?.close()
        sessionLog = null
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(12, 17, 27)) }
        val video = TextureView(this).apply {
            isOpaque = false
            surfaceTextureListener = textureListener
        }
        val gestureLayer = View(this).apply {
            isClickable = true
            setOnTouchListener { view, event -> onHostTouch(view, event) }
        }
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        root.addView(gestureLayer, FrameLayout.LayoutParams(-1, -1))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.rgb(12, 17, 27))
            isClickable = true
        }
        panel.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_androidplay); contentDescription = "AndroidPlay"
        }, LinearLayout.LayoutParams(dp(88), dp(88)))
        panel.addView(TextView(this).apply {
            text = "AndroidPlay"; textSize = 34f; setTextColor(Color.rgb(241, 245, 252))
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(14))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        val stage = TextView(this).apply {
            text = "正在准备CarPlay…"; textSize = 22f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(241, 245, 252))
        }
        panel.addView(stage)
        panel.addView(TextView(this).apply {
            text = "请让iPhone连接本机系统热点，并保持两端蓝牙开启。\n接收服务会在热点网络广播；首次使用请允许CarPlay。"
            textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.rgb(168, 182, 202))
            setPadding(0, dp(14), 0, dp(24))
        })
        panel.addView(Button(this).apply {
            text = "停止并重试"; isAllCaps = false; textSize = 18f
            visibility = View.GONE
            setOnClickListener { showAndroidPlayHome("wireless-recovery") }
            wifiRecoveryButton = this
        }, LinearLayout.LayoutParams(dp(300), dp(64)).apply { bottomMargin = dp(12) })
        panel.addView(Button(this).apply {
            text = "返回AndroidPlay"; isAllCaps = false; textSize = 18f
            setTextColor(Color.rgb(12, 17, 27))
            background = GradientDrawable().apply { setColor(Color.rgb(166, 200, 255)); cornerRadius = dp(20).toFloat() }
            setOnClickListener { showAndroidPlayHome() }
        }, LinearLayout.LayoutParams(dp(300), dp(64)))
        panel.addView(TextView(this).apply {
            text = "在CarPlay中三指下滑，可返回连接管理。"
            textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.rgb(168, 182, 202)); setPadding(0, dp(20), 0, 0)
        })
        val waitingScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(12, 17, 27))
            addView(panel)
        }
        root.addView(waitingScroll, FrameLayout.LayoutParams(-1, -1))
        videoView = video
        gestureOverlay = gestureLayer
        stageStatusView = stage
        connectionPanel = waitingScroll
        updateDebugOverlays()
        return root
    }

    private fun mfiTargetLabel(target: MfiTarget): String = when (target) {
        MfiTarget.LOCAL -> "Local offline"
        MfiTarget.USB_CH341 -> "USB/CH341"
        MfiTarget.I2C -> "I2C"
        MfiTarget.REMOTE -> "Remote"
    }

    private fun createAirPlayConfig(size: DisplaySize): AirPlayConfig {
        val physical = resolvePhysicalSize(size)
        val baseDisplay = AirPlayDisplayConfig(
            widthPixels = size.width,
            heightPixels = size.height,
            widthPhysicalMm = physical.widthMm,
            heightPhysicalMm = physical.heightMm,
            fps = fps,
        )
        val display = baseDisplay.copy(
            safeArea = AirPlaySafeArea.toInsets(
                mapping = null,
                activityWidthPixels = size.width,
                activityHeightPixels = size.height,
                displayWidthPixels = baseDisplay.widthPixels,
                displayHeightPixels = baseDisplay.heightPixels,
            ),
            safeAreaDrawOutside = safeAreaDrawOutside,
        )
        val screen = windowManager.defaultDisplay
        val mode = screen.supportedModes.filter {
            it.physicalWidth == screen.mode.physicalWidth &&
                it.physicalHeight == screen.mode.physicalHeight && it.refreshRate >= display.fps - 1f
        }.minByOrNull { it.refreshRate }
        window.attributes = window.attributes.apply { preferredDisplayModeId = mode?.modeId ?: 0 }
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching { currentSurface?.setFrameRate(display.fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
        val requestSummary = "Display request native=${size.width}x${size.height} fps=$fps " +
            "codec=${if (hevcEnabled) "HEVC" else "H.264"} softwareHevc=$hevcSoftwareDecoderEnabled"
        val effectiveSummary = "Display effective canvas=${display.widthPixels}x${display.heightPixels} " +
            "physical=${display.widthPhysicalMm}x${display.heightPhysicalMm}mm safeArea=${display.safeArea} " +
            "drawOutside=${display.safeAreaDrawOutside}"
        displayDiagnosticAttempt = DisplayDiagnosticSnapshot.begin(this, requestSummary, "Native physical dimensions; no scaling", effectiveSummary)
        appendLog(requestSummary)
        appendLog(effectiveSummary)
        return AirPlayConfig(
            deviceName = "AndroidPlay",
            deviceId = AndroidPlayBootstrap.deviceId(airPlayIdentity),
            btMac = AndroidPlayBluetooth.localAddress(this) ?: AndroidPlayBootstrap.deviceId(airPlayIdentity),
            sourceVersion = "950.7.1",
            main = display,
            cluster = null,
            rightHandDrive = rightHandDrive,
            hevc = hevcEnabled,
            microphone = microphoneAvailable,
            manufacturer = normalizedManufacturer(),
            model = normalizedModel(),
            oemLabel = oemLabel,
            icons = listOf(loadAirPlayIcon()),
        )
    }

    private fun loadAirPlayIcon(): AirPlayIcon =
        decodeAirPlayIcon(defaultAirPlayIconBytes())
            ?: throw IllegalStateException("Packaged AndroidPlay icon is invalid")

    private fun decodeAirPlayIcon(encoded: ByteArray): AirPlayIcon? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth != bounds.outHeight
        ) {
            return null
        }
        return AirPlayIcon(bounds.outWidth, bounds.outHeight, encoded)
    }

    private fun defaultAirPlayIconBytes(): ByteArray =
        // Shown in CarPlay's app list as the "back to the car" button.
        resources.openRawResource(R.raw.androidplay_carplay_icon).use { it.readBytes() }

    private fun resolvePhysicalSize(size: DisplaySize): AirPlayPhysicalSizeMm =
        AirPlayDisplaySettings.resolvePhysicalSizeMm(
            currentWidthPixels = size.width,
            currentHeightPixels = size.height,
            maximumWidthPixels = maxOf(maximumDetectedWidthPixels, size.width),
            maximumHeightPixels = maxOf(maximumDetectedHeightPixels, size.height),
            referenceMillimeters = widthPhysicalMm,
            basis = physicalSizeBasis,
        )

    private fun normalizedManufacturer(): String = "AndroidPlay"

    private fun normalizedModel(): String = "AndroidPlay"

    private fun createMediaSink(
        videoWidth: Int,
        videoHeight: Int,
        controllerGeneration: Int,
    ): AndroidMediaSink {
        // Capture this session's log: late decoder shutdown must not write into a new session.
        val diagnosticLog = sessionLog
        return AndroidMediaSink(
            surface = null,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            preferSoftwareHevcDecoder = hevcSoftwareDecoderEnabled,
            advancedAudioChannelMapping = advancedAudioChannelMapping,
            onScreenStreamActiveChanged = { type, active ->
                onScreenStreamStateChanged(controllerGeneration, type, active)
            },
            mediaBufferMillis = AirPlayPersistence.loadMediaBufferMillis(this),
            onAudioDiagnostic = { message ->
                diagnosticLog?.append(formattedLogLine(message, System.currentTimeMillis()))
            },
        )
    }

    private fun createMediaEngine(sink: AndroidMediaSink): CarPlayMediaEngine =
        CarPlayMediaEngine(
            sink = sink,
            microphoneEnabled = microphoneAvailable,
            audioCaptureDirectory = null,
        )

    private fun createSessionListener(controllerGeneration: Int): AirPlaySessionListener =
        object : AirPlaySessionListener {
            override fun onSessionActive(session: AirPlaySession) {
                runOnUiThread {
                    if (controllerGeneration != restartGeneration) {
                        return@runOnUiThread
                    }
                    armFrameRateFallback(controllerGeneration)
                    activeAirPlaySession = session
                    CarPlayBackgroundSession.active = true
                    reconnectAttempts = 0
                    syncAirPlayDarkMode()
                    if (menuOpen) return@runOnUiThread
                    appendLog("AirPlay session active")
                }
            }

            override fun onHostUiRequested(session: AirPlaySession) {
                runOnUiThread {
                    if (controllerGeneration != restartGeneration || shuttingDown.get()) return@runOnUiThread
                    showAndroidPlayHome()
                }
            }

            override fun onSessionEnded(session: AirPlaySession) {
                runOnUiThread {
                    if (activeAirPlaySession === session) activeAirPlaySession = null
                    CarPlayBackgroundSession.active = false
                    if (menuOpen || controllerGeneration != restartGeneration) {
                        return@runOnUiThread
                    }
                    if (retryLowerFrameRate()) return@runOnUiThread
                    activeScreenStreamTypes.clear()
                    setConnectionStage("CarPlay session ended; reconnecting")
                    appendLog("AirPlay session ended; reconnecting from scratch")
                    reconnectAfterLoss("AirPlay session ended")
                }
            }

            override fun onTransportError(message: String) {
                runOnUiThread {
                    if (menuOpen || controllerGeneration != restartGeneration) {
                        return@runOnUiThread
                    }
                    if (retryLowerFrameRate()) return@runOnUiThread
                    activeScreenStreamTypes.clear()
                    setConnectionStage("Transport error; reconnecting")
                    appendLog("CarPlay transport error: $message; reconnecting from scratch")
                    reconnectAfterLoss("CarPlay transport error: $message")
                }
            }

            override fun onDebugLog(message: String) {
                if (DiagnosticRedactor.redact(message) == null) return
                runOnUiThread {
                    if (controllerGeneration != restartGeneration) {
                        return@runOnUiThread
                    }
                    if (message.contains("iap2 tx=0x4301 carplay-start-session")) {
                        armFrameRateFallback(controllerGeneration)
                    }
                    DisplayDiagnosticSnapshot.record(this@CarPlayHostActivity, displayDiagnosticAttempt, message)
                    if (menuOpen) return@runOnUiThread
                    if (message.startsWith(PROTOCOL_TRACE_PREFIX)) {
                        appendFileLog(message)
                    } else {
                        appendLog(message)
                    }
                }
            }
        }

    private fun createStatusReporter(
        controllerGeneration: Int,
    ): (CarPlayStatus) -> Unit = { status ->
        if (!menuOpen && controllerGeneration == restartGeneration) {
            val description = status.describe()
            setConnectionStage(description)
            when (status) {
                is CarPlayStatus.Failed -> if (status.wifiResetRequired) {
                    wifiRecoveryButton?.visibility = View.VISIBLE
                } else {
                    wifiRecoveryButton?.visibility = View.GONE
                    appendLog(description)
                    retryLowerFrameRate()
                    // Keep the actual failure visible instead of hiding it in an endless retry loop.
                }
                else -> Unit
            }
        }
    }

    private fun adoptBackgroundSession(): Boolean {
        val snapshot = CarPlayBackgroundSession.snapshot() ?: return false
        if (snapshot.controller.isClosed()) {
            CarPlayBackgroundSession.clear(snapshot.controller)
            return false
        }
        displayDiagnosticAttempt = DisplayDiagnosticSnapshot.currentAttempt(this)
        controller = snapshot.controller
        sink = snapshot.sink
        CarPlayBackgroundSession.store(snapshot.controller, snapshot.sink, snapshot.width, snapshot.height, this) { completion ->
            runOnUiThread {
                shutdown(false, "AndroidPlay disconnect", completion)
                finish()
            }
        }
        if (snapshot.width > 0 && snapshot.height > 0) {
            activeDisplaySize = DisplaySize(snapshot.width, snapshot.height)
        }
        val generation = restartGeneration
        snapshot.controller.attachUi(
            createSessionListener(generation),
            createStatusReporter(generation),
        )
        snapshot.sink.setScreenStreamActiveChangedListener { type, active ->
            onScreenStreamStateChanged(restartGeneration, type, active)
        }
        currentSurface?.let(::attachSurface)
        val serviceReused = snapshot.controller.hasActiveAirPlayAttachment()
        appendLog(
            if (serviceReused) {
                "Reusing existing background CarPlay service"
            } else {
                "Reusing existing background CarPlay session"
            },
        )
        setConnectionStage(
            if (serviceReused) {
                "CarPlay service already running"
            } else {
                "CarPlay session already running"
            },
        )
        updateDebugOverlays()
        return true
    }

    private fun startCarPlay(size: DisplaySize) {
        if (CarPlayBackgroundSession.hasSession() && !CarPlayBackgroundSession.isOwner(this)) return
        if (shuttingDown.get() || menuOpen || handshakeResetInProgress || controller != null) return
        loadPersistedSettings() // Refresh even when Android reuses the host Activity.
        cancelFrameRateFallback()
        frameRateGuard = CarPlayFrameRateFallback(fps)
        val controllerGeneration = restartGeneration
        val config = createRuntimeConfig()
        val airPlayConfig = createAirPlayConfig(size)
        val locationProvider: Iap2LocationProvider? =
            if (config.locationReportingEnabled) {
                AndroidCarPlayLocationProvider(this)
            } else {
                null
            }
        appendLog(
            "Starting CarPlay controller at ${size.width}x${size.height} -> " +
                "${airPlayConfig.main.widthPixels}x${airPlayConfig.main.heightPixels} " +
                "physical=${airPlayConfig.main.widthPhysicalMm}x" +
                "${airPlayConfig.main.heightPhysicalMm}mm " +
                "video=${if (airPlayConfig.hevc) "HEVC" else "H.264"} " +
                "decoder=${if (airPlayConfig.hevc && hevcSoftwareDecoderEnabled) "software" else "hardware"} " +
                "microphone=${airPlayConfig.microphone} " +
                "location=${if (config.locationReportingEnabled) "enabled" else "disabled"} " +
                "mfi=${mfiTargetLabel(config.mfiTarget)}",
        )
        Log.i(
            TAG,
            "starting controller display=${size.width}x${size.height} " +
                "negotiated=${airPlayConfig.main.widthPixels}x${airPlayConfig.main.heightPixels} " +
                "hevc=${airPlayConfig.hevc} " +
                "softwareHevc=${airPlayConfig.hevc && hevcSoftwareDecoderEnabled} " +
                "microphone=${airPlayConfig.microphone} " +
                "location=${config.locationReportingEnabled} " +
                "mfi=${config.mfiTarget}",
        )
        val renderer = createMediaSink(
            videoWidth = airPlayConfig.main.widthPixels,
            videoHeight = airPlayConfig.main.heightPixels,
            controllerGeneration = controllerGeneration,
        )
        sink = renderer
        currentSurface?.let(::attachSurface)
        val media = createMediaEngine(renderer)
        val pairings = AirPlayPersistence.loadPairings(this) { id, key ->
            AirPlayPersistence.savePairing(this, id, key)
        }
        val next = CarPlayController(
            context = this,
            config = config,
            airPlayConfig = airPlayConfig,
            identity = airPlayIdentity,
            pairings = pairings,
            listener = createSessionListener(controllerGeneration),
            media = media,
            reportStatus = createStatusReporter(controllerGeneration),
            loadPairRecord = { AirPlayPersistence.loadLockdownRecord(this) },
            savePairRecord = { record -> AirPlayPersistence.saveLockdownRecord(this, record) },
            clearPairRecord = { AirPlayPersistence.clearLockdownRecord(this) },
            locationProvider = locationProvider,
        )
        controller = next
        CarPlayBackgroundSession.store(next, renderer, size.width, size.height, this) { completion ->
            runOnUiThread {
                shutdown(terminateProcess = false, reason = "AndroidPlay disconnect", completion = completion)
                finish()
            }
        }
        try {
            startForegroundService(Intent(this, AndroidPlaySessionService::class.java))
            next.start()
        } catch (error: RuntimeException) {
            appendLog("Connection could not start: ${error.javaClass.simpleName}")
            shutdown(false, "foreground service could not start")
            setConnectionStage("Could not start CarPlay. Return to AndroidPlay and check app permissions.")
        }
    }

    private fun syncAirPlayDarkMode() {
        val session = activeAirPlaySession ?: return
        val night = darkMode
        airPlayCommandExecutor.execute {
            try {
                val sent = session.setNightMode(night)
                Log.i(
                    TAG,
                    "AirPlay dark mode=${if (night) "dark" else "light"} eventChannelReady=$sent",
                )
            } catch (error: Throwable) {
                Log.w(TAG, "Could not send AirPlay dark mode update", error)
            }
        }
    }

    private fun scheduleDisplaySize(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || shuttingDown.get()) return
        // Display.Mode reports physical pixels, unlike view bounds or density-scaled metrics.
        // Both activities are landscape-only; map the panel dimensions to that orientation.
        val mode = windowManager.defaultDisplay.mode
        val size = DisplaySize(maxOf(mode.physicalWidth, mode.physicalHeight), minOf(mode.physicalWidth, mode.physicalHeight))
        if (size == activeDisplaySize || size == pendingDisplaySize) return
        pendingDisplaySize = size
        mainHandler.removeCallbacks(applyDisplaySize)
        mainHandler.postDelayed(applyDisplaySize, DISPLAY_CHANGE_DEBOUNCE_MILLIS)
    }

    private fun applyDisplaySize(size: DisplaySize) {
        if (shuttingDown.get() || size == activeDisplaySize) return
        val previous = activeDisplaySize
        activeDisplaySize = size
        recordDetectedMaximum(size)
        if (previous == null) {
            appendLog("Display detected: ${size.width}x${size.height}")
            maybeStartCarPlay()
        } else if (menuOpen || handshakeResetInProgress) {
            appendLog(
                "Display updated while handshake is reset: " +
                    "${previous.width}x${previous.height} -> ${size.width}x${size.height}",
            )
        } else {
            restartCarPlay(
                "Display changed ${previous.width}x${previous.height} -> ${size.width}x${size.height}",
            )
        }
    }

    private fun recordDetectedMaximum(size: DisplaySize) {
        val width = maxOf(maximumDetectedWidthPixels, size.width)
        val height = maxOf(maximumDetectedHeightPixels, size.height)
        if (width == maximumDetectedWidthPixels && height == maximumDetectedHeightPixels) return
        maximumDetectedWidthPixels = width
        maximumDetectedHeightPixels = height
        AirPlayPersistence.saveMaximumDetectedDisplay(this, width, height)
    }

    private fun maybeStartCarPlay() {
        if (shuttingDown.get()) return
        if (CarPlayBackgroundSession.hasSession() && !CarPlayBackgroundSession.isOwner(this)) {
            if (!adoptBackgroundSession()) mainHandler.postDelayed({ maybeStartCarPlay() }, 500)
            return
        }
        if (controller == null && adoptBackgroundSession()) return
        val size = activeDisplaySize ?: return
        val transportReady = wirelessPermissionsReady
        val locationReady = !locationReportingEnabled || locationPermissionAvailable
        if (
            !transportReady ||
            !locationReady ||
            !microphonePermissionResolved ||
            shuttingDown.get() ||
            menuOpen ||
            handshakeResetInProgress ||
            controller != null
        ) {
            return
        }
        startCarPlay(size)
    }

    private fun reconnectAfterLoss(reason: String) {
        if (!CarPlayBackgroundSession.isOwner(this)) return
        if (shuttingDown.get() || menuOpen || handshakeResetInProgress) return
        if (reconnectScheduled) return
        reconnectScheduled = true
        val generation = restartGeneration
        val delayMillis = if (reason.contains("AirPlay iAP tunnel", ignoreCase = true)) {
            IAP_TUNNEL_RECONNECT_DELAY_MILLIS
        } else {
            (RECONNECT_DELAY_MILLIS * (1L shl reconnectAttempts.coerceAtMost(4))).coerceAtMost(30_000L)
        }
        reconnectAttempts += 1
        appendLog("$reason; retrying in ${delayMillis}ms")
        mainHandler.postDelayed(
            {
                reconnectScheduled = false
                if (
                    shuttingDown.get() ||
                    menuOpen ||
                    handshakeResetInProgress ||
                    generation != restartGeneration
                ) {
                    return@postDelayed
                }
                restartCarPlay("Reconnecting after $reason")
            },
            delayMillis,
        )
    }

    /** Full-stack fallback when an AirPlay-only reconnect is unavailable. */
    private fun restartCarPlay(reason: String) {
        if (!CarPlayBackgroundSession.isOwner(this)) return
        if (shuttingDown.get() || menuOpen || handshakeResetInProgress) return
        val size = activeDisplaySize ?: return
        appendLog(reason)
        activeScreenStreamTypes.clear()
        setConnectionStage(reason)
        Log.i(TAG, "$reason; rebuilding stack at ${size.width}x${size.height}")
        cancelFrameRateFallback()
        val generation = ++restartGeneration
        handshakeResetInProgress = true
        val oldController = controller
        val oldSink = sink
        CarPlayBackgroundSession.clear(oldController, keepOwner = true)
        controller = null
        sink = null
        teardownExecutor.execute {
            oldController?.close()
            oldController?.awaitClosed(CONTROLLER_CLOSE_TIMEOUT_MILLIS)
            oldSink?.close()
            runOnUiThread {
                if (!shuttingDown.get() && generation == restartGeneration) {
                    handshakeResetInProgress = false
                    startCarPlay(size)
                }
            }
        }
    }

    private fun showAndroidPlayHome(page: String = "home") {
        controller?.sendTouch(emptyList())
        startActivity(Intent(this, AndroidPlayActivity::class.java)
            .putExtra("page", page).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun openSettingsMenu() = showAndroidPlayHome("settings")

    private fun shutdown(terminateProcess: Boolean, reason: String, completion: () -> Unit = {}) {
        if (!shuttingDown.compareAndSet(false, true)) { completion(); return }
        cancelFrameRateFallback()
        restartGeneration += 1
        mainHandler.removeCallbacks(applyDisplaySize)
        val oldController = controller
        val oldSink = sink
        CarPlayBackgroundSession.clear(oldController)
        controller = null
        sink = null
        Log.i(TAG, "shutdown reason=$reason terminateProcess=$terminateProcess")
        teardownExecutor.execute {
            oldController?.close()
            val clean = oldController?.awaitClosed(CONTROLLER_CLOSE_TIMEOUT_MILLIS) ?: true
            oldSink?.close()
            airPlayCommandExecutor.shutdown()
            Log.i(TAG, "shutdown complete clean=$clean")
            applicationContext.stopService(Intent(applicationContext, AndroidPlaySessionService::class.java))
            teardownExecutor.shutdown()
            mainHandler.post { completion() }
            if (terminateProcess) Process.killProcess(Process.myPid())
        }
    }

    private fun attachSurface(surface: Surface) {
        sink?.setSurface(SCREEN_TYPE_MAIN, surface)
        sink?.setSurface(SCREEN_TYPE_ALT, surface)
    }

    private fun onHostTouch(view: View, event: MotionEvent): Boolean {
        if (menuOpen) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureSequenceActive = false
                gestureTracking = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == THREE_FINGER_COUNT && !gestureSequenceActive) {
                    gestureSequenceActive = true
                    gestureTracking = true
                    gestureStartX = pointerCentroid(event, horizontal = true)
                    gestureStartY = pointerCentroid(event, horizontal = false)
                    controller?.sendTouch(emptyList())
                    appendLog("Three-finger swipe tracking started")
                    return true
                }
            }
        }

        if (gestureSequenceActive) {
            if (!gestureTracking || event.pointerCount != THREE_FINGER_COUNT) {
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    gestureSequenceActive = false
                    gestureTracking = false
                } else if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                    gestureTracking = false
                }
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                val deltaX = Math.abs(pointerCentroid(event, horizontal = true) - gestureStartX)
                val deltaY = pointerCentroid(event, horizontal = false) - gestureStartY
                if (
                    deltaY >= dp(THREE_FINGER_SWIPE_DISTANCE_DP) &&
                    deltaY >= deltaX * THREE_FINGER_SWIPE_DIRECTION_RATIO
                ) {
                    gestureSequenceActive = false
                    gestureTracking = false
                    openSettingsMenu()
                    return true
                }
            }
            return true
        }

        val contacts = CarPlayTouchMapper.contacts(event, view.width, view.height)
        val queued = controller?.sendTouch(contacts) ?: false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> Log.i(
                TAG,
                "touch action=${MotionEvent.actionToString(event.actionMasked)} " +
                    "pointers=${event.pointerCount} queued=$queued",
            )
        }
        return true
    }

    private fun pointerCentroid(event: MotionEvent, horizontal: Boolean): Float {
        var total = 0f
        for (index in 0 until event.pointerCount) {
            total += if (horizontal) event.getX(index) else event.getY(index)
        }
        return total / event.pointerCount
    }

    private fun onScreenStreamStateChanged(generation: Int, type: Int, active: Boolean) {
        runOnUiThread {
            if (shuttingDown.get() || generation != restartGeneration) return@runOnUiThread
            if (active) {
                if (type == SCREEN_TYPE_MAIN) cancelFrameRateFallback()
                activeScreenStreamTypes.add(type)
            } else {
                activeScreenStreamTypes.remove(type)
            }
            if (type == SCREEN_TYPE_ALT) {
                Log.i(TAG, "cluster stream active=$active")
                appendLog("Cluster map: stream active=$active")
            }
            updateDebugOverlays()
        }
    }

    private fun setStatus(message: String) {
        runOnUiThread {
            setConnectionStage(message)
            appendLog(message)
        }
    }

    private fun setConnectionStage(message: String) {
        latestStage = message
        val visible = friendlyStage(message)
        CarPlayBackgroundSession.connectionStatus = visible
        stageStatusView?.text = visible
        updateDebugOverlays()
    }

    private fun updateDebugOverlays() {
        statusScrollView?.visibility = View.GONE
        connectionPanel?.visibility = if (activeScreenStreamTypes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun friendlyStage(message: String): String = when {
        message.startsWith("热点接收服务已广播") -> message
        message.contains("hotspot is off", true) -> "本机系统热点未开启，请在系统设置中开启后重试。"
        message.contains("SSID does not match", true) -> "热点名称不一致，请核对应用内的信息与本机系统热点。"
        message.contains("manual hotspot", true) && message.contains("Timed out", true) ->
            "找不到系统热点的网络接口，请确认热点已开启、iPhone已接入。"
        message.startsWith("Failed:") -> "连接失败，请返回首页检查后重试。\n" + message.removePrefix("Failed:").trim()
        message.contains("Starting wireless hotspot", true) -> "正在检查本机系统热点…"
        message.contains("Hotspot ready:", true) -> "系统热点已就绪，正在启动接收服务…"
        message.contains("Starting AirPlay service", true) -> "正在启动热点接收服务和网络广播…"
        message.contains("Connecting Bluetooth", true) -> "热点广播已启动，正在通过蓝牙引导CarPlay…"
        message.contains("Wireless CarPlay control running", true) -> "热点广播已启动，正在进行CarPlay认证…"
        message.contains("Waiting for paired", true) -> "正在检查已配对的iPhone…"
        message.contains("permission", true) -> "请允许连接所需权限后重试。"
        message.contains("Transport error", true) -> "音视频连接中断，正在重连…"
        message.contains("reconnect", true) || message.contains("ended", true) -> "正在重新连接iPhone…"
        message.contains("active", true) || message.contains("running", true) -> "正在打开CarPlay…"
        else -> "正在准备CarPlay…"
    }

    private fun appendLog(message: String) {
        val safe = DiagnosticRedactor.redact(message) ?: return
        sessionLog?.append(formattedLogLine(safe, System.currentTimeMillis()))
    }

    private fun appendFileLog(message: String) {
        sessionLog?.append(formattedLogLine(message, System.currentTimeMillis()))
    }

    private fun formattedLogLine(message: String, nowMillis: Long): String =
        "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(nowMillis))}  $message"

    private fun initializeSessionLog() {
        val logFile = File(File(filesDir, "logs"), "androidplay.log")
        val activeLog = SessionLogFile(logFile)
        runCatching {
            activeLog.reset(
                "AndroidPlay log started " +
                    "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} " +
                    "pid=${Process.myPid()} path=${logFile.absolutePath}",
            )
        }
        sessionLog = activeLog
    }

    private fun refreshLogView(nowMillis: Long) {
        val cutoff = nowMillis - LOG_RETENTION_MILLIS
        while (logLines.firstOrNull()?.timestampMillis?.let { it <= cutoff } == true) {
            logLines.removeFirst()
        }
        statusView?.text = logLines.joinToString("\n") { it.text }
        scrollLogsToBottom()

        mainHandler.removeCallbacks(expireOldLogLines)
        logLines.firstOrNull()?.let { oldest ->
            val delay = (oldest.timestampMillis + LOG_RETENTION_MILLIS - nowMillis + 1L)
                .coerceAtLeast(1L)
            mainHandler.postDelayed(expireOldLogLines, delay)
        }
    }

    private fun scrollLogsToBottom() {
        statusScrollView?.post {
            statusScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun applyFullscreenMode() = AndroidPlayWindow.apply(this)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun CarPlayStatus.describe(): String = when (this) {
        is CarPlayStatus.BroadcastReady -> "热点接收服务已广播：$address"
        CarPlayStatus.DiscoveringMfi -> "Preparing MFi authentication"
        CarPlayStatus.WaitingForMfi -> "Waiting for MFi coprocessor"
        CarPlayStatus.RequestingMfiPermission -> "Requesting MFi USB permission"
        CarPlayStatus.MfiReady -> "MFi authentication ready"
        CarPlayStatus.StartingHotspot -> "Starting wireless hotspot"
        is CarPlayStatus.HotspotReady ->
            "Hotspot ready: $backend, $ssid, $band, " +
                "channel ${if (channel == 0) "auto" else channel}"
        CarPlayStatus.WaitingForPairedIphone -> "Waiting for paired iPhone"
        CarPlayStatus.ConnectingBluetooth -> "Connecting Bluetooth"
        CarPlayStatus.RunningWireless -> "Wireless CarPlay control running"
        CarPlayStatus.WirelessActive -> "Wireless CarPlay active"
        CarPlayStatus.DiscoveringIphone -> "Discovering iPhone"
        CarPlayStatus.WaitingForIphone -> "Waiting for iPhone over USB"
        CarPlayStatus.RequestingIphonePermission -> "Requesting iPhone USB permission"
        CarPlayStatus.WaitingForReenumeration -> "Waiting for iPhone re-enumeration"
        CarPlayStatus.SelectingConfiguration -> "Selecting CarPlay configuration"
        CarPlayStatus.OpeningDataPaths -> "Opening USB data paths"
        CarPlayStatus.Pairing -> "Pairing with iPhone"
        CarPlayStatus.ConnectingControl -> "Connecting iAP2 control"
        CarPlayStatus.AttachingNetwork ->
            if (wirelessEnabled) "Starting AirPlay service" else "Attaching NCM/AirPlay network"
        CarPlayStatus.RunningControl -> "CarPlay control running"
        CarPlayStatus.ControlEnded -> "CarPlay control window ended"
        is CarPlayStatus.Failed -> "Failed: ${message}"
    }

    private companion object {
        const val TAG = "xcertplay-usb"
        const val SCREEN_TYPE_MAIN = 110
        const val SCREEN_TYPE_ALT = 111
        const val LOG_RETENTION_MILLIS = 5 * 60_000L
        const val DISPLAY_CHANGE_DEBOUNCE_MILLIS = 500L
        const val RECONNECT_DELAY_MILLIS = 2_000L
        const val IAP_TUNNEL_RECONNECT_DELAY_MILLIS = 15_000L
        const val CONTROLLER_CLOSE_TIMEOUT_MILLIS = 4_000L
        const val AUDIO_CAPTURE_MARKER = "audio-capture.enabled"
        const val AUDIO_CAPTURE_DIRECTORY = "audio-captures"
        const val PROTOCOL_TRACE_PREFIX = "TRACE "
        const val THREE_FINGER_COUNT = 3
        const val THREE_FINGER_SWIPE_DISTANCE_DP = 72
        const val THREE_FINGER_SWIPE_DIRECTION_RATIO = 1.15f
        const val MAX_SETTINGS_MENU_WIDTH_PX = 1200
        val MENU_BACKGROUND = Color.rgb(12, 16, 19)
        val MENU_SECONDARY = Color.rgb(170, 180, 190)
        val MENU_ACCENT = Color.rgb(127, 205, 154)
        val MENU_ACCENT_TRACK = Color.rgb(78, 143, 102)
        val MENU_TRACK_OFF = Color.rgb(64, 74, 80)
        val MENU_BUTTON_TEXT = Color.rgb(8, 17, 11)
        val MENU_DANGER = Color.rgb(190, 45, 45)
        val NO_VIDEO_BACKGROUND = Color.rgb(0x16, 0x16, 0x18)
    }

    private data class DisplaySize(val width: Int, val height: Int)
    private data class LogEntry(val timestampMillis: Long, val text: String)
    private data class HotspotStatus(
        val state: String,
        val ssid: String? = null,
        val band: String? = null,
        val channel: Int? = null,
        val backend: String? = null,
    )
}

/** Process-local hand-off for keeping the CarPlay session alive while no Activity is visible. */
internal object CarPlayBackgroundSession {
    @Volatile var active = false
    @Volatile var connectionStatus = "正在准备CarPlay…"
    private var stopAction: (((() -> Unit)) -> Unit)? = null
    private var stopping = false
    private var owner: Any? = null
    @Synchronized fun isOwner(candidate: Any): Boolean = owner === candidate
    @Synchronized fun hasSession(): Boolean = stopAction != null || stopping
    private val stopWaiters = mutableListOf<() -> Unit>()

    fun stop(completion: () -> Unit = {}) {
        val action: (((() -> Unit)) -> Unit)?
        synchronized(this) {
            if (stopping) { stopWaiters.add(completion); return }
            action = stopAction
            if (action != null) { stopping = true; stopWaiters.add(completion) }
        }
        if (action == null) { completion(); return }
        action.invoke {
            val callbacks = synchronized(this) {
                stopping = false
                stopWaiters.toList().also { stopWaiters.clear() }
            }
            callbacks.forEach { it() }
        }
    }

    data class Snapshot(
        val controller: CarPlayController,
        val sink: AndroidMediaSink,
        val width: Int,
        val height: Int,
    )

    private var controller: CarPlayController? = null
    private var sink: AndroidMediaSink? = null
    private var width = 0
    private var height = 0

    @Synchronized
    fun snapshot(): Snapshot? {
        val currentController = controller ?: return null
        val currentSink = sink ?: return null
        return Snapshot(currentController, currentSink, width, height)
    }

    @Synchronized
    fun store(controller: CarPlayController, sink: AndroidMediaSink, width: Int, height: Int, owner: Any, stop: (() -> Unit) -> Unit) {
        this.stopAction = stop
        this.owner = owner
        this.controller = controller
        this.sink = sink
        this.width = width
        this.height = height
    }

    @Synchronized
    fun clear(expected: CarPlayController? = null, keepOwner: Boolean = false) {
        if (expected != null && controller !== expected) return
        controller = null
        sink = null
        if (!keepOwner) { stopAction = null; owner = null }
        active = false
        connectionStatus = "准备连接"
        width = 0
        height = 0
    }
}
