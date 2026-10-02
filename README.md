<p align="center"><img src="asset/androidplay-icon.png" width="104" alt="AndroidPlay应用图标"></p>

<h1 align="center">AndroidPlay</h1>

<p align="center">让Android手机、平板或车机成为无线CarPlay接收端，通过系统热点与蓝牙连接iPhone，显示画面并播放声音。</p>

<p align="center">
  <a href="mobile/build.gradle.kts"><img src="https://img.shields.io/badge/version-1.1.0-2563eb?style=flat-square" alt="版本1.1.0"></a>
  <a href="#使用条件"><img src="https://img.shields.io/badge/platform-Android%209%2B-555555?style=flat-square" alt="运行平台"></a>
  <a href="#来源与许可"><img src="https://img.shields.io/badge/license-GPLv3%20%2F%20AGPLv3-2563eb?style=flat-square" alt="项目许可"></a>
</p>

<p align="center">
  <a href="https://github.com/Roylyl/AndroidPlay/releases"><img src="https://img.shields.io/github/downloads/Roylyl/AndroidPlay/total?style=flat-square&amp;label=downloads&amp;color=2563eb" alt="发行附件累计下载量"></a>
  <a href="https://github.com/Roylyl/AndroidPlay/stargazers"><img src="https://img.shields.io/github/stars/Roylyl/AndroidPlay?style=flat-square&amp;label=stars&amp;color=2563eb" alt="GitHub Star数"></a>
  <a href="https://github.com/Roylyl/AndroidPlay/forks"><img src="https://img.shields.io/github/forks/Roylyl/AndroidPlay?style=flat-square&amp;label=forks&amp;color=555555" alt="GitHub Fork数"></a>
  <a href="https://github.com/Roylyl/AndroidPlay/issues"><img src="https://img.shields.io/github/issues/Roylyl/AndroidPlay?style=flat-square&amp;label=issues&amp;color=555555" alt="开放Issue数"></a>
  <a href="https://github.com/Roylyl/AndroidPlay/pulls"><img src="https://img.shields.io/github/issues-pr/Roylyl/AndroidPlay?style=flat-square&amp;label=pull%20requests&amp;color=555555" alt="开放Pull Request数"></a>
  <a href="https://github.com/Roylyl/AndroidPlay/commits"><img src="https://img.shields.io/github/last-commit/Roylyl/AndroidPlay?style=flat-square&amp;label=last%20commit&amp;color=555555" alt="最近提交时间"></a>
</p>

<p align="center"><a href="#快速入门">快速入门</a> · <a href="#主要功能">主要功能</a> · <a href="#其他平台">其他平台</a> · <a href="#从源码构建">源码构建</a> · <a href="#来源与许可">来源与许可</a></p>

<p align="center">其他设备：<a href="https://github.com/Roylyl/MacPlay">MacPlay</a> · <a href="https://github.com/Roylyl/WinPlay">WinPlay</a></p>

## 快速入门

### 使用条件

- Android9（API28）及以上，具备移动热点、蓝牙和视频解码能力。
- 支持CarPlay的iPhone，开启Wi-Fi与蓝牙并允许CarPlay连接。
- Android端使用WPA2系统热点，密码为8—63位。
- 安装包含可用配件认证资源的APK；自行构建时按下文配置认证目录。

在[GitHubReleases](https://github.com/Roylyl/AndroidPlay/releases)获取APK，或[从源码构建](#从源码构建)。应用包名为`com.androidplay.app`，当前版本1.1.0。

### 安装与连接

1. 在Android端安装APK，打开AndroidPlay并按系统提示授权。
2. 在Android系统设置中开启WPA2热点，让iPhone加入。建议开启热点时不要同时连接其他无线局域网。
3. 两端开启蓝牙，首次使用先完成系统蓝牙配对。
4. 点击“自动获取热点信息”；读取失败时，在弹窗中填写系统热点的实际名称和密码。
5. 进入“CarPlay设置→iPhone选择”，选择已配对的iPhone。
6. 点击“打开CarPlay”，在iPhone上确认允许连接。

热点未开启时，Android16及以上会尝试请求系统开启；系统拒绝、接口不可用或旧版Android会引导手动开启。厂商限制可能影响热点状态和密码读取，应用不会用仅本地热点代替系统移动热点。

## 主要功能

- 无线连接：Android系统热点承载音视频，蓝牙负责连接引导。
- 全屏画面：支持横屏与竖屏方向，CarPlay默认横屏，按屏幕物理像素请求视频。
- 连接管理：通过前台服务保持会话，可从CarPlay返回首页；断开后关闭画面并清理音频与媒体会话。
- 帧率选择：30/60/90/120fps，默认60fps，高帧率启动失败后按120→90→60回退。
- 声音与设备：实时调整媒体/通话音量，分别选择系统可见的输入和输出设备。
- 系统媒体控件：在通知栏、锁屏和播放面板显示歌曲、封面与进度，回传播放、暂停、切歌及可用的进度跳转。

## 其他平台

使用Android设备时选择AndroidPlay；如果更习惯在电脑上操作CarPlay，可以看看下面两个桌面项目。

| 项目 | 平台 | 适合的使用场景 |
| --- | --- | --- |
| [MacPlay](https://github.com/Roylyl/MacPlay) | macOS14及以上 | 适合Mac用户。支持USB直连和共用Wi-Fi无线连接，提供原生设置界面、多屏显示与系统媒体控件。 |
| [WinPlay](https://github.com/Roylyl/WinPlay) | Windows10/11x64 | 适合Windows电脑。支持本机移动热点和现有局域网两种无线模式，提供独立画面窗口与托盘操作。 |

## 使用与设置

首页提供“打开CarPlay”“自动获取热点信息”和“CarPlay设置”三个入口。设置页使用逐级导航，可通过顶部返回入口或Android返回键返回。

| 设置 | 用途 |
| --- | --- |
| iPhone选择 | 选择已配对的连接目标 |
| 帧率 | 保存30/60/90/120fps请求档位，下次主动连接生效 |
| 显示方向 | 横屏或竖屏，下次进入CarPlay时生效 |
| 声音 | 实时媒体/通话音量，输入与输出设备 |
| 检查应用权限 | 重新申请连接、麦克风和通知权限 |
| 断开连接 | 结束会话，保留系统热点 |

更改显示或帧率不会自动重连。回到首页点击“打开CarPlay”时，设置有变化会重新协商；设置未变化则返回已有画面。投屏页的Android返回键向CarPlay发送返回操作，CarPlay中的AndroidPlay入口可返回首页并保持连接。

### 显示与帧率

应用按显示器最高物理分辨率模式和所选方向请求视频，不使用dp、窗口尺寸或系统缩放倍率。物理尺寸按Android提供的xdpi/ydpi换算；实际布局受iPhone及设备显示策略影响。

120的视频启动失败后依次尝试90和60，90失败后尝试60；认证前失败、主动断开或已出画面后的断线不触发回退。降档只影响当前连接，不更改保存档位，也不插帧。实际帧率取决于iPhone、网络和解码能力。

收到视频后，系统Toast显示解码尺寸和根据运动峰值估算的帧率档位。它反映解码输出采样，不是屏幕刷新率或协议确认值；静止画面可能暂不显示结果。

### 声音与系统媒体控件

音乐与导航使用媒体音量，通话使用通话音量，Siri保留独立音频用途。输入/输出默认跟随系统，设备选择立即应用；设备拔出后暂用系统默认路由，重新接入时恢复偏好。具体路由由Android和设备驱动决定。

封面按曲目身份关联，标题或歌词变化不作为切歌，迟到的上一首封面不会覆盖新歌。QQ音乐等App通过标题字段发送的歌词可在媒体标题位置显示，本版没有独立歌词字段。音乐App提供时长并允许跳转时，可拖动系统进度条；未提供的字段可能为空，应用不会从网络搜索封面。

## 权限与常见问题

| 权限 | 用途 |
| --- | --- |
| 蓝牙/附近设备 | 枚举已配对设备并建立连接 |
| 位置 | Android12及以下无线网络流程所需 |
| 麦克风 | Siri与通话输入 |
| 通知 | Android13及以上的连接和媒体通知 |

拒绝麦克风权限不阻止连接，但无法使用对应语音输入。已拒绝的权限可从“CarPlay设置→检查应用权限”或系统应用设置重新授权。

| 现象 | 处理方法 |
| --- | --- |
| 热点信息读取失败 | 在弹窗中填写与系统热点一致的名称和密码 |
| 找不到iPhone | 完成系统蓝牙配对，检查附近设备权限，再选择设备 |
| 连接失败 | 核对热点、密码、配对和权限，在设置中断开后重试 |
| 有黑边或方向异常 | 检查厂商全屏、挖孔和多窗口设置，再确认显示方向 |
| 系统媒体控件信息为空 | 音乐App需上报对应信息；直播可能没有时长或跳转能力 |

## 从源码构建

使用AndroidStudio打开工程并配置JDK、AndroidSDK与NDK。工程采用Gradle9.7.1、AGP9.4.1、compileSdk37、targetSdk35、minSdk28和NDK29.0.14206865。版本见[应用配置](mobile/build.gradle.kts)、[依赖配置](gradle/libs.versions.toml)与[GradleWrapper](gradle/wrapper/gradle-wrapper.properties)。

### 配置认证资源

认证文件放在仓库外，目录结构为：

~~~text
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
~~~

将`ANDROIDPLAY_AUTH_ASSETS_DIR`设为包含`offline-mfi`的资源根目录，使用有权使用且互相匹配的文件。APK签名证书不能代替CarPlay配件认证。macOS默认目录为`~/Library/Application Support/AndroidPlay/runtime-assets`。

macOS/Linux在仓库根目录执行：

~~~sh
export ANDROIDPLAY_AUTH_ASSETS_DIR="$HOME/AndroidPlay-runtime-assets"
./scripts/build-androidplay.sh
~~~

脚本生成`build/AndroidPlay.apk`。Windows的PowerShell示例：

~~~powershell
$env:ANDROIDPLAY_AUTH_ASSETS_DIR = Join-Path $HOME 'AndroidPlay-runtime-assets'
.\gradlew.bat :mobile:assembleStandaloneDebug
~~~

Windows产物位于`mobile/build/outputs/apk/debug/mobile-debug.apk`。这些命令生成调试签名APK，不同签名不能直接覆盖安装。正常构建会将认证资源打入APK；缺失文件时构建会失败。公开分发APK前应确认资源允许相应分发，见[认证来源说明](docs/ANDROIDPLAY_AUTH_SOURCE.md)。

源码树不包含认证私钥或证书。`androidplay.sourceOnly`构建模式不接入认证资源，其APK无法完成CarPlay认证，不作为可连接的发行包。

### 工程结构

| 目录 | 内容 |
| --- | --- |
| `mobile/` | 应用标识、版本和APK构建 |
| `common/` | 首页、设置、投屏页面和连接管理 |
| `shared/` | CarPlay协议、无线连接、音视频和认证 |
| `asset/` | 图标源文件与导出素材 |
| `scripts/` | 本地构建与图标脚本 |

## 更新日志

1.1.0整理首页与逐级设置，加入显示方向选择、实时音量与音频设备、系统媒体控件、封面关联及进度跳转，完善断开清理和高帧率回退。设置变更在下次主动连接时应用，首页保留热点获取与权限引导。

## 来源与许可

AndroidPlay基于[DiPlay](https://github.com/shihabal3amri/DiPlay)，接收器源自[xcertplay](https://github.com/shilapi/xcertplay)。原有代码、文档和素材的版权归各自权利人，保留源码中的版权与SPDX标识。

仓库包含GPLv3与AGPLv3相关代码：根目录保留[GPLv3](LICENSE)，首页相关许可见[DiAuto的AGPL文本](docs/licenses/DiAuto-AGPL-3.0.txt)。第三方依赖和素材见[第三方声明](docs/THIRD_PARTY_NOTICES.md)，图标源文件见[SVG](asset/androidplay-icon.svg)。分发时应按所用部分及组合方式履行适用许可要求，提供对应源码、构建资料和声明。

认证数据和商标不因项目代码许可获得再分发授权。AndroidPlay是独立修改项目，不代表Apple或上游作者，不声称获得MFi认证。不要公开认证私钥、热点密码或含个人信息的日志与截图。
