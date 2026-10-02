// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal data class AppReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppReleaseVersion> {
    override fun compareTo(other: AppReleaseVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(text: String): AppReleaseVersion? {
            val match = Regex("(?<![0-9])([0-9]+)\\.([0-9]+)\\.([0-9]+)(?![0-9.])").find(text) ?: return null
            val numbers = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppReleaseVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

internal data class AndroidPlayRelease(val version: AppReleaseVersion, val page: String, val installer: String?)
internal class AndroidPlayUpdates(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("androidplay_updates", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    val currentVersion: String = app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.2.0"
    val build: Long = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
    var checking = false; private set
    var status = prefs.getString("status", "尚未检查更新") ?: "尚未检查更新"; private set
    var detail = prefs.getString("detail", "") ?: ""; private set
    var release: AndroidPlayRelease? = prefs.getString("latestVersion", null)?.let { tag ->
        val version = AppReleaseVersion.parse(tag) ?: return@let null
        val current = AppReleaseVersion.parse(currentVersion) ?: return@let null
        if (version <= current) return@let null
        AndroidPlayRelease(version, prefs.getString("releasePage", RELEASES) ?: RELEASES,
            prefs.getString("installer", null)?.takeIf { validDownload(it) })
    }; private set
    init {
        if (status == "发现新版本" && release == null) { status = "当前已是最新版本"; detail = "" }
    }
    var exportStatus = ""; private set
    var exportDetail = ""; private set
    var automatic: Boolean
        get() = prefs.getBoolean("automatic", true)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }
    fun shouldNotify() = release?.version?.toString()?.let { prefs.getString("notified", "") != it } ?: false
    fun markNotified() { release?.let { prefs.edit().putString("notified", it.version.toString()).apply() } }
    fun check(automatically: Boolean = false, finished: () -> Unit) {
        if (checking || automatically && !automatic) return
        val now = System.currentTimeMillis()
        if (automatically && now - prefs.getLong("lastCheck", 0) < 86_400_000) { finished(); return }
        if (automatically) prefs.edit().putLong("lastCheck", now).apply()
        checking = true; status = "正在检查更新…"; detail = ""
        io.execute {
            var result: AndroidPlayRelease? = null
            var message = "当前已是最新版本"
            var explanation = ""
            try {
                val connection = URL("https://api.github.com/repos/Roylyl/AndroidPlay/releases?per_page=100").openConnection() as HttpURLConnection
                val response = try {
                    connection.connectTimeout = 15000; connection.readTimeout = 15000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "AndroidPlay/$currentVersion")
                    kotlin.check(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally { connection.disconnect() }
                val items = JSONArray(response)
                val releases = (0 until items.length()).mapNotNull { index ->
                    val item = items.getJSONObject(index)
                    if (item.optBoolean("draft") || item.optBoolean("prerelease")) return@mapNotNull null
                    val version = AppReleaseVersion.parse(item.optString("tag_name")) ?: return@mapNotNull null
                    val assets = item.optJSONArray("assets") ?: JSONArray()
                    val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name").endsWith(".apk", true) }
                    val asset = apks.firstOrNull { it.optString("name").contains("AndroidPlay", true) } ?: apks.firstOrNull()
                    val download = asset?.optString("browser_download_url")?.takeIf { validDownload(it) }
                    val page = item.optString("html_url").takeIf { it.startsWith("https://github.com/Roylyl/AndroidPlay/releases/") } ?: RELEASES
                    AndroidPlayRelease(version, page, download)
                }
                val latest = releases.maxByOrNull { it.version }
                val current = AppReleaseVersion.parse(currentVersion) ?: error("Invalid installed version")
                if (latest == null) message = "未找到正式发行版"
                else if (latest.version > current) {
                    result = releases.firstOrNull { it.version == latest.version && it.installer != null } ?: latest
                    message = "发现新版本"; explanation = latest.version.toString()
                }
                prefs.edit().putLong("lastCheck", now).apply()
            } catch (error: Exception) {
                message = "检查更新失败"; explanation = error.localizedMessage ?: error.javaClass.simpleName
            }
            val stored = prefs.edit().putString("status", message).putString("detail", explanation)
            if (message != "检查更新失败") {
                stored.putString("latestVersion", result?.version?.toString())
                    .putString("releasePage", result?.page).putString("installer", result?.installer)
            }
            stored.apply()
            main.post {
                if (message != "检查更新失败") release = result
                status = message; detail = explanation; checking = false; finished()
            }
        }
    }
    fun hasLogs(): Boolean = logFiles().isNotEmpty()
    private fun logFiles(): List<File> = File(app.filesDir, "logs").listFiles()?.filter {
        it.isFile && (it.name == "androidplay.log" || it.name.matches(Regex("previous(?:-[2-7])?\\.log")))
    }?.sortedBy { it.lastModified() } ?: emptyList()
    fun export(uri: Uri, finished: () -> Unit) {
        io.execute {
            val result = runCatching {
                app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.appendLine("AndroidPlay $currentVersion ($build)")
                    writer.appendLine(java.time.Instant.now().toString())
                    for (file in logFiles()) {
                        writer.appendLine("\n=== ${file.name} ===")
                        file.bufferedReader().useLines { lines -> lines.forEach { line -> DiagnosticRedactor.redact(line)?.let { writer.appendLine(it) } } }
                    }
                } ?: error("Cannot open destination")
            }
            main.post { exportStatus = if (result.isSuccess) "日志已导出" else "日志导出失败"; exportDetail = result.exceptionOrNull()?.localizedMessage ?: ""; finished() }
        }
    }
    companion object {
        const val RELEASES = "https://github.com/Roylyl/AndroidPlay/releases"
        private val io = Executors.newSingleThreadExecutor()
        private fun validDownload(url: String): Boolean = url.startsWith("https://github.com/Roylyl/AndroidPlay/releases/download/")
    }
}
