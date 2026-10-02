// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.shilapi.xcertplay.host.R
import java.util.Locale

/** App-owned language setting; the projected iPhone interface keeps its own language. */
internal object AndroidPlayLanguage {
    val choices = listOf("system", "zh-Hans", "zh-Hant", "en")
    fun selection(context: Context): String = context.getSharedPreferences("androidplay", Context.MODE_PRIVATE)
        .getString("language", "system")?.takeIf { it in choices } ?: "system"
    fun save(context: Context, value: String) {
        context.getSharedPreferences("androidplay", Context.MODE_PRIVATE).edit()
            .putString("language", value.takeIf { it in choices } ?: "system").apply()
    }
    fun resolveLocale(selection: String, system: Locale): Locale = when (selection) {
        "zh-Hans" -> Locale.SIMPLIFIED_CHINESE
        "zh-Hant" -> Locale.TRADITIONAL_CHINESE
        "en" -> Locale.ENGLISH
        else -> if (system.language == "zh") {
            if (system.script == "Hant" || system.country in listOf("TW", "HK", "MO")) Locale.TRADITIONAL_CHINESE else Locale.SIMPLIFIED_CHINESE
        } else Locale.ENGLISH
    }
    fun context(context: Context): Context {
        val locale = resolveLocale(selection(context), Resources.getSystem().configuration.locales[0])
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
    }
    fun label(context: Context, choice: String): String = when (choice) {
        "zh-Hans" -> "简体中文"
        "zh-Hant" -> "繁體中文"
        "en" -> "English"
        else -> text(context, "跟随系统")
    }
    private val messages = linkedMapOf(
        "同时开启热点和无线局域网可能会造成CarPlay连接不稳定" to R.string.androidplay_hotspot_wifi_warning,
        "返回" to R.string.androidplay_back,
        "关于" to R.string.androidplay_text_124,
        "版本、更新与日志" to R.string.androidplay_text_125,
        "当前版本" to R.string.androidplay_text_126,
        "构建版本" to R.string.androidplay_text_127,
        "运行架构" to R.string.androidplay_text_128,
        "软件更新" to R.string.androidplay_text_129,
        "自动检查更新" to R.string.androidplay_text_130,
        "检查更新" to R.string.androidplay_text_131,
        "尚未检查更新" to R.string.androidplay_text_132,
        "正在检查更新…" to R.string.androidplay_text_133,
        "检查更新失败" to R.string.androidplay_text_134,
        "当前已是最新版本" to R.string.androidplay_text_135,
        "未找到正式发行版" to R.string.androidplay_text_136,
        "发现新版本" to R.string.androidplay_text_137,
        "下载更新" to R.string.androidplay_text_138,
        "查看发行说明" to R.string.androidplay_text_139,
        "项目主页" to R.string.androidplay_text_140,
        "稍后" to R.string.androidplay_text_141,
        "下载与安装由你确认。" to R.string.androidplay_text_142,
        "导出日志" to R.string.androidplay_text_143,
        "诊断日志" to R.string.androidplay_text_144,
        "暂无日志可导出" to R.string.androidplay_text_145,
        "日志已导出" to R.string.androidplay_text_146,
        "日志导出失败" to R.string.androidplay_text_147,
        "无法打开链接" to R.string.androidplay_text_148,
        "点击下载更新，安装由你确认。" to R.string.androidplay_text_149,
        "应用启动或回到前台时每天检查一次GitHub正式发行版。" to R.string.androidplay_text_150,
        "应用启动或回到前台时每天检查一次GitHub正式发行版。发现新版时提示，下载与安装由你确认。" to R.string.androidplay_text_151,
        "最新发行版暂无适合当前芯片的安装包，请查看发行说明。" to R.string.androidplay_text_152,
        "导出当前连接日志，不包含设置文件或认证文件，并过滤网络密码与设备标识。" to R.string.androidplay_text_153,
        "导出最近连接记录，过滤网络密码、认证内容与设备标识。" to R.string.androidplay_text_154,
        "下载与安装由你确认。自动更新提醒需要通知权限；未授权时在应用内提示。" to R.string.androidplay_text_155,
        "确认热点已开启" to R.string.androidplay_text_0,
        "系统未提供热点状态。请确认本机移动热点已开启，并让iPhone连接该热点。" to R.string.androidplay_text_1,
        "已开启，继续" to R.string.androidplay_text_2,
        "热点设置" to R.string.androidplay_text_3,
        "取消" to R.string.androidplay_text_4,
        "CarPlay认证加载失败：" to R.string.androidplay_text_5,
        "未知原因" to R.string.androidplay_text_6,
        "系统热点CarPlay" to R.string.androidplay_text_7,
        "准备连接" to R.string.androidplay_text_8,
        "已选设备：" to R.string.androidplay_text_9,
        "连接前会尝试请求开启本机移动热点，受限时按提示打开系统热点设置；让iPhone接入，首次使用需蓝牙配对。" to R.string.androidplay_text_10,
        "建议开启热点时不要同时连接其他无线局域网，以免降低CarPlay连接稳定性。" to R.string.androidplay_text_11,
        "麦克风未授权，Siri和通话语音不可用。" to R.string.androidplay_text_12,
        "打开CarPlay" to R.string.androidplay_text_13,
        "自动获取热点信息" to R.string.androidplay_text_14,
        "CarPlay设置" to R.string.androidplay_text_15,
        "CarPlay已连接" to R.string.androidplay_text_16,
        "需要连接权限" to R.string.androidplay_text_17,
        "需要CarPlay认证文件" to R.string.androidplay_text_18,
        "已获取系统热点名称和密码" to R.string.androidplay_text_19,
        "正在向系统申请开启移动热点" to R.string.androidplay_text_20,
        "开启移动热点" to R.string.androidplay_text_21,
        "请在系统设置中开启本机WPA2移动热点，并让iPhone连接该热点。返回后继续连接CarPlay。" to R.string.androidplay_text_22,
        "打开热点设置" to R.string.androidplay_text_23,
        "无法打开热点设置，请手动在系统设置中开启移动热点" to R.string.androidplay_text_24,
        "iPhone选择" to R.string.androidplay_text_25,
        "帧率" to R.string.androidplay_text_26,
        "声音" to R.string.androidplay_text_27,
        "输入设备" to R.string.androidplay_text_28,
        "输出设备" to R.string.androidplay_text_29,
        "显示方向" to R.string.androidplay_text_30,
        "‹ 返回" to R.string.androidplay_text_31,
        "返回上一级" to R.string.androidplay_text_32,
        "连接、显示与音频偏好" to R.string.androidplay_text_33,
        "下次连接生效，失败时在后台尝试较低帧率" to R.string.androidplay_text_34,
        "下次进入CarPlay时生效" to R.string.androidplay_text_35,
        "竖屏  ›" to R.string.androidplay_text_36,
        "横屏  ›" to R.string.androidplay_text_37,
        "输入/输出设备与实时音量" to R.string.androidplay_text_38,
        "检查应用权限" to R.string.androidplay_text_39,
        "连接、麦克风和通知权限" to R.string.androidplay_text_40,
        "断开连接" to R.string.androidplay_text_41,
        "结束CarPlay会话，系统热点保持开启" to R.string.androidplay_text_42,
        "AndroidPlay自身页面支持横竖屏旋转；CarPlay默认只允许横屏。更改后下次进入CarPlay时生效。" to R.string.androidplay_text_43,
        "竖屏" to R.string.androidplay_text_44,
        "横屏" to R.string.androidplay_text_45,
        "只保存偏好，不自动连接或中断当前会话。120fps失败时后台尝试90fps、60fps，设置中的选择保持不变。" to R.string.androidplay_text_46,
        "推荐" to R.string.androidplay_text_47,
        "实验性请求" to R.string.androidplay_text_48,
        "帧率已保存，下次连接生效" to R.string.androidplay_text_49,
        "音乐和导航使用媒体音量，通话使用通话音量；Siri保持原有音频通道。" to R.string.androidplay_text_50,
        "系统默认设备" to R.string.androidplay_text_51,
        "由Android自动选择音频路由" to R.string.androidplay_text_52,
        "设备列表来自Android，实际可用路由由系统和设备驱动决定。" to R.string.androidplay_text_53,
        "需要蓝牙权限" to R.string.androidplay_text_54,
        "允许附近设备权限后选择iPhone" to R.string.androidplay_text_55,
        "选择已在系统蓝牙设置中配对的iPhone" to R.string.androidplay_text_56,
        "请先打开蓝牙并与iPhone配对" to R.string.androidplay_text_57,
        "已配对设备" to R.string.androidplay_text_58,
        "设备标识：" to R.string.androidplay_text_59,
        "打开蓝牙设置" to R.string.androidplay_text_60,
        "配对新设备或打开蓝牙" to R.string.androidplay_text_61,
        "已保存的设备暂不可用，使用系统默认" to R.string.androidplay_text_62,
        "通话音量" to R.string.androidplay_text_63,
        "媒体音量（音乐/导航）" to R.string.androidplay_text_64,
        "热点名称" to R.string.androidplay_text_65,
        "WPA2热点密码（8至63位）" to R.string.androidplay_text_66,
        "自动获取热点信息失败，请手动输入。" to R.string.androidplay_text_67,
        "请先在Android系统中开启WPA2热点，在这里填写完全相同的名称和密码，再让iPhone连接该热点。" to R.string.androidplay_text_68,
        "本机系统热点" to R.string.androidplay_text_69,
        "保存" to R.string.androidplay_text_70,
        "请填写有效的热点名称和8至63位密码" to R.string.androidplay_text_71,
        "已保存，请点击打开CarPlay重新协商" to R.string.androidplay_text_72,
        "连接已停止，系统热点保持开启。请点击打开CarPlay重试。" to R.string.androidplay_text_73,
        "应用权限" to R.string.androidplay_text_74,
        "连接需要蓝牙和附近设备权限，旧版Android还需要精确位置权限。麦克风用于Siri和通话，通知用于显示连接状态。请在系统弹窗中允许；已拒绝的权限可在应用设置中开启。" to R.string.androidplay_text_75,
        "重新申请" to R.string.androidplay_text_76,
        "应用设置" to R.string.androidplay_text_77,
        "关闭" to R.string.androidplay_text_78,
        "无法打开系统设置，请手动打开" to R.string.androidplay_text_79,
        "正在连接CarPlay" to R.string.androidplay_text_80,
        "CarPlay返回操作未发送，请等待连接恢复" to R.string.androidplay_text_81,
        "正在准备CarPlay…" to R.string.androidplay_text_82,
        "请让iPhone连接本机系统热点，并保持两端蓝牙开启。\n接收服务会在热点网络广播；首次使用请允许CarPlay。" to R.string.androidplay_text_83,
        "停止并重试" to R.string.androidplay_text_84,
        "返回AndroidPlay" to R.string.androidplay_text_85,
        "在CarPlay中选择AndroidPlay入口，可返回连接管理。" to R.string.androidplay_text_86,
        "实际参数：" to R.string.androidplay_text_87,
        "CarPlay会话已断开" to R.string.androidplay_text_88,
        "CarPlay连接已断开：" to R.string.androidplay_text_89,
        "CarPlay连接已断开" to R.string.androidplay_text_90,
        "热点接收服务已广播" to R.string.androidplay_text_91,
        "本机系统热点未开启，请在系统设置中开启后重试。" to R.string.androidplay_text_92,
        "热点名称不一致，请核对应用内的信息与本机系统热点。" to R.string.androidplay_text_93,
        "找不到系统热点的网络接口，请确认热点已开启、iPhone已接入。" to R.string.androidplay_text_94,
        "连接失败，请返回首页检查后重试。\n" to R.string.androidplay_text_95,
        "正在检查本机系统热点…" to R.string.androidplay_text_96,
        "系统热点已就绪，正在启动接收服务…" to R.string.androidplay_text_97,
        "正在启动热点接收服务和网络广播…" to R.string.androidplay_text_98,
        "热点广播已启动，正在通过蓝牙引导CarPlay…" to R.string.androidplay_text_99,
        "热点广播已启动，正在进行CarPlay认证…" to R.string.androidplay_text_100,
        "正在检查已配对的iPhone…" to R.string.androidplay_text_101,
        "请允许连接所需权限后重试。" to R.string.androidplay_text_102,
        "音视频连接中断，正在重连…" to R.string.androidplay_text_103,
        "正在重新连接iPhone…" to R.string.androidplay_text_104,
        "正在打开CarPlay…" to R.string.androidplay_text_105,
        "未选择iPhone" to R.string.androidplay_text_106,
        "CarPlay连接与播放" to R.string.androidplay_text_107,
        "CarPlay连接服务正在运行" to R.string.androidplay_text_108,
        "上一首" to R.string.androidplay_text_109,
        "暂停" to R.string.androidplay_text_110,
        "播放" to R.string.androidplay_text_111,
        "下一首" to R.string.androidplay_text_112,
        "扬声器" to R.string.androidplay_text_113,
        "麦克风" to R.string.androidplay_text_114,
        "听筒" to R.string.androidplay_text_115,
        "蓝牙" to R.string.androidplay_text_116,
        "有线耳机" to R.string.androidplay_text_117,
        "设备" to R.string.androidplay_text_118,
        "语言" to R.string.androidplay_text_119,
        "跟随系统" to R.string.androidplay_text_120,
        "语言更改立即应用，不中断CarPlay连接。" to R.string.androidplay_text_121,
        "Could not start CarPlay. Return to AndroidPlay and check app permissions." to R.string.androidplay_text_122,
        "无法清理旧认证缓存" to R.string.androidplay_text_123,
    )
    // Dynamic protocol values and device names are retained; only known UI prefixes are translated.
    private val dynamicPrefixes = listOf("自动获取热点信息失败，请手动输入。", "CarPlay认证加载失败：", "已选设备：", "设备标识：", "实际参数：", "热点接收服务已广播", "连接失败，请返回首页检查后重试。\n", "CarPlay连接已断开：")
    fun setText(view: android.widget.TextView, source: String) {
        view.tag = source
        view.text = text(view.context, source)
    }
    fun refreshTexts(view: android.view.View) {
        if (view is android.widget.TextView && view.tag is String) view.text = text(view.context, view.tag as String)
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) refreshTexts(view.getChildAt(index))
    }
    fun text(context: Context, source: String): String {
        val localized = context(context)
        messages[source]?.let { return localized.getString(it) }
        dynamicPrefixes.firstOrNull { source.startsWith(it) }?.let { prefix ->
            return localized.getString(messages.getValue(prefix)) + text(context, source.removePrefix(prefix))
        }
        for (prefix in listOf("通话音量", "媒体音量（音乐/导航）")) {
            if (source.startsWith(prefix + " · ")) return localized.getString(messages.getValue(prefix)) + text(context, source.removePrefix(prefix))
        }
        if (source.endsWith("）") && source.contains("（")) {
            val type = source.substringAfterLast("（").removeSuffix("）")
            messages[type]?.let { return source.substringBeforeLast("（") + "（" + localized.getString(it) + "）" }
            if (type.startsWith("设备")) return source.substringBeforeLast("（") + "（" + localized.getString(messages.getValue("设备")) + type.removePrefix("设备") + "）"
        }
        return source
    }
}
