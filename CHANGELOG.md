# 1.2.0 — Language and disconnect navigation

- Add app language choices: system default, Simplified Chinese, Traditional Chinese and English.
- Apply the selected language to home/settings pages, connection guidance, permission explanations and connection/media notifications.
- Close the CarPlay activity after explicit or passive disconnect and restore the AndroidPlay home page.
- Changing app language preserves the CarPlay session and the iPhone's own interface language.

# 0.2.0 — BYD navigation and connection improvements

- Standalone windshield HUD arrows, distance and street names on the verified DiLink5.1 firmware; no ADB, root or computer helper.
- Retain contributor cluster/SOME-IP navigation, route parsing, BYD CarPlay icon and display-size presets.
- Fix Car hotspot startup by using scoped IPv6 when available and binding discovery/probing to the AP interface. Physically confirmed on the development car.
- Drain asynchronously decoded audio during packet gaps and rebuild the music buffer after starvation. Wi-Fi Direct is much better in the user retest; occasional audio cutouts remain for a later version.
- Preserve bounded music-buffer choices, USB read improvements and decoder recovery; fix USB request/close races and keep vendor output outside phone callbacks.
- Save audio/video/receive timing and discovery diagnostics without road names or protocol payloads.
- HUD cleanup on normal end/disconnect/off/stale input; interrupted sessions recover on the next app launch. Force-stop may leave guidance visible until reopening.
- Thanks to @romanchukg-cloud and @georgiyrr for PR #3 and vehicle testing.

# 0.1.0 release restored — 2026-09-25

- Rebuilt and signed the APK locally with explicitly supplied runtime authentication assets.
- Restored release downloads; no app behavior or version-code change from 0.1.0.
- Accessory identity remains in the APK only. No credential files enter Git or the source archive.
- Retained generated test identities and public-source credential checks.
- Source/CI builds omit runtime identity assets by default; local packaging requires an explicit external directory.

# Source reset — 2026-09-25

- Withdrew the 0.1.0 APK and removed its release tag.
- Reset the public branch after preserving restricted local incident records.
- Removed static synthetic test private keys; generate test identities at runtime.
- Removed automatic private-asset packaging and disabled the old release build script.
- Added a build guard rejecting credential asset files.
- Replaced the download site with a five-language suspension notice.

The APK was subsequently rebuilt and restored as described above. Existing copies cannot be recalled by a Git history reset.

- 新增独立关于页，显示版本号和构建号。
- 支持手动检查GitHub正式发行版，每天自动检查并提醒新版，提供安装包或发行说明入口。
- 支持导出TXT连接日志，过滤网络密码、认证内容与常见设备标识。

- 返回箭头、列表箭头和选中标记改用矢量图形，与文字垂直居中对齐。
- 返回入口保留至少48dp点击区域，适配不同字号。
- 设置页避让屏幕挖孔；首页按钮按内容调整高度，避免大字号裁切。
- 自动检查使用开关，移除更新、日志操作的常驻解释文字，保留实际结果与错误。
- 版本号和构建号行不再响应无作用的点击。
- 避免重复的发行说明入口；关闭自动检查后不再产生新的自动更新提醒。

### 1.2.0构建45

- 修正手动热点误选移动数据接口的问题。
- 热点与Wi-Fi同时开启时显示底部连接稳定性提醒。
