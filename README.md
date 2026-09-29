<p align="center">
  <img src="asset/androidplay-icon.png" width="112" alt="AndroidPlay应用图标">
</p>

<h1 align="center">AndroidPlay</h1>

<p align="center">基于DiPlay的安卓无线CarPlay接收应用，支持系统热点连接、横屏全屏显示、自定义帧率与界面缩放。</p>

<p align="center">
  <a href="mobile/build.gradle.kts"><img src="https://img.shields.io/badge/version-1.0.0-a6c8ff?style=flat-square" alt="版本1.0.0"></a>
  <a href="#安装与连接"><img src="https://img.shields.io/badge/Android-9%2B-555555?style=flat-square" alt="最低系统配置Android9"></a>
  <a href="#验证与限制"><img src="https://img.shields.io/badge/status-experimental-555555?style=flat-square" alt="实验性项目"></a>
</p>

<p align="center">
  <a href="#安装与连接">开始使用</a> ·
  <a href="#显示设置">显示设置</a> ·
  <a href="#从源码构建">源码构建</a> ·
  <a href="#验证与限制">验证与限制</a> ·
  <a href="#来源与许可">来源与许可</a>
</p>

AndroidPlay安装在Android手机或车机上，由iPhone提供CarPlay界面。连接方式固定为：**Android开启系统热点，iPhone连接该热点，再通过蓝牙完成连接引导。**本版聚焦无线CarPlay，不提供有线USB、Android Auto、Wi-Fi直连或HUD入口。

已在一组真实设备上完成iPhone投屏和四档缩放验证。项目仍使用实验性CarPlay认证身份；源码不包含认证文件，构建前需要单独配置。本文不提供已核实的AndroidPlay公开Release下载入口。

## 主要功能

- **中文操作界面：**首页、连接设置、设备选择及状态提示使用中文；CarPlay内的内容与语言由iPhone决定。
- **系统热点连接：**使用Android系统热点承载投屏，蓝牙用于首次配对及连接引导。
- **热点信息读取：**启动和连接前尝试读取热点配置；系统不允许读取时，可手动填写一次。
- **横屏全屏：**支持左右横屏，隐藏系统状态栏和导航栏，并配置挖孔区域显示。
- **显示调节：**提供四档界面缩放和四档帧率请求，选择后自动重连应用设置。
- **连接管理：**可返回首页管理连接，通过前台服务维持会话，并在连接设置中断开。

## 安装与连接

### 使用条件

| 项目 | 要求 |
| --- | --- |
| Android端 | 最低配置Android9（API28），具备热点、蓝牙和视频解码能力 |
| iPhone端 | 支持CarPlay，开启Wi-Fi与蓝牙，并允许CarPlay连接 |
| 网络 | Android系统开启WPA2热点，iPhone接入该热点 |
| 安装包 | 包含可用认证资源的AndroidPlay APK；源码构建方法见下文 |

最低系统版本来自工程配置，不代表所有Android9及以上设备都经过验证。应用包名为`com.androidplay.app`，显示版本为1.0.0，当前内部构建号为37。

1. 在Android端安装APK，打开AndroidPlay并按系统提示授权。
2. 在Android系统设置中开启WPA2热点，让iPhone连接该热点。热点密码为8至63位。
3. 两端开启蓝牙，首次使用先在系统蓝牙设置中完成配对。
4. 回到AndroidPlay。应用会尝试读取热点信息；如果提示读取失败，在“连接设置→设置热点信息”中填写实际名称和密码。
5. 点击“选择iPhone”，选择已配对的设备。
6. 点击“打开CarPlay”，在iPhone上确认允许CarPlay，等待投屏画面出现。

首页提供四个入口：

| 入口 | 用途 |
| --- | --- |
| 打开CarPlay | 发起连接或回到已有CarPlay画面 |
| CarPlay设置 | 调整帧率、图标和文字缩放 |
| 选择iPhone | 选择已配对的iPhone |
| 连接设置 | 读取或填写热点信息、打开系统设置、检查权限、断开连接 |

在投屏页使用系统返回操作可回到首页。断开连接不会关闭Android系统热点。应用会发布接收服务，但Bonjour广播本身不能保证AndroidPlay出现在iPhone“其他汽车”列表中；连接热点也不等于完成CarPlay配对。

## 显示设置

### 图标和文字缩放

进入“CarPlay设置→图标和文字缩放”，选择以下档位：

| 档位 | 显示效果 | 真机验证 |
| --- | --- | --- |
| 75% | 较小的图标、文字和控件 | 已观察到视觉变化 |
| 100% | 默认大小 | 已观察到视觉变化 |
| 125% | 较大的图标、文字和控件 | 已观察到视觉变化 |
| 150% | 四档中最大的控件 | 已观察到视觉变化 |

缩放通过调整上报给iPhone的屏幕物理尺寸实现，不修改视频像素宽高或触摸坐标。iPhone会重新排列界面，封面、留白等元素不一定严格等比缩放。

**四档均已在同一设备组合上实测，解码视频保持2400×1080，控件大小依次递增。**选择档位后，应用结束旧会话并自动重连；每次创建会话都会重新读取保存的设置。

应用按当前屏幕模式的物理像素尺寸请求视频。Android解码输出尺寸不等同于iPhone内部渲染尺寸：实测曾出现1912×860的iPhone原始CarPlay截图，该差异仍待查明，不能宣称已经实现全链路原生分辨率。

### 帧率

提供30fps、60fps、90fps和120fps，默认60fps。90fps与120fps为实验性请求，需要屏幕模式和解码能力满足条件；检查不通过时回落到60fps。

该设置不会插帧，也不保证iPhone按请求帧率输出。**90fps和120fps的实际视频帧率尚未测量确认。**帧率修改同样会触发自动重连。

## 权限与热点信息

应用启动时申请实际使用的运行时权限，仍需用户在系统弹窗中确认。

| 权限 | 用途 |
| --- | --- |
| 蓝牙/附近设备 | 访问已配对设备、建立连接及使用附近Wi-Fi相关能力 |
| 位置 | Android12及以下无线网络流程所需 |
| 麦克风 | Siri及通话语音输入；拒绝后不阻止连接，但无法提供该输入 |
| 通知 | Android13及以上显示连接状态通知 |

已拒绝的权限可通过“连接设置→检查应用权限”重新申请，或进入系统应用设置授权。应用无法静默批准权限，也无法保证普通APK能读取所有厂商系统的热点密码；读取失败会保留已有配置。

## 从源码构建

### 环境

当前工程配置与已有成功构建记录如下，JDK版本列为已使用环境，不表示最低要求：

| 工具 | 版本或配置 |
| --- | --- |
| JDK | 已使用Android Studio附带的JDK25 |
| Gradle Wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.1 |
| Android SDK | compileSdk37、targetSdk35、minSdk28 |
| Android NDK | 29.0.14206865 |
| 原生库架构 | arm64-v8a、armeabi-v7a、x86_64 |

使用Android Studio打开工程，配置Android SDK及NDK。所有命令均在工程根目录执行；首次构建需要下载依赖。版本来源见[应用配置](mobile/build.gradle.kts)、[依赖版本](gradle/libs.versions.toml)和[Gradle配置](gradle/wrapper/gradle-wrapper.properties)。

### 配置认证资源

认证文件放在源码目录之外，结构如下：

```text
runtime-assets/
└── offline-mfi/
    ├── identity.pk8
    └── certificate.p7b
```

将环境变量`ANDROIDPLAY_AUTH_ASSETS_DIR`设为实际`runtime-assets`目录的绝对路径。macOS未设置该变量时，默认读取当前用户的`Library/Application Support/AndroidPlay/runtime-assets`目录。

正常打包时，认证文件缺失或为空时构建会失败；Android Studio的Run与命令行构建使用同一资源配置。APK应用签名密钥与CarPlay配件认证身份用途不同，不能互相替代。认证来源及已有检查见[认证来源说明](docs/ANDROIDPLAY_AUTH_SOURCE.md)。

### 生成APK

配置好JDK、SDK和认证目录后执行：

```sh
./scripts/build-androidplay.sh
```

脚本执行`:mobile:assembleStandaloneDebug`，将产物复制为`build/AndroidPlay.apk`。也可直接执行：

```sh
./gradlew :mobile:assembleDebug
```

直接构建的产物为`mobile/build/outputs/apk/debug/mobile-debug.apk`。以上均为调试签名APK；使用不同签名重新构建时，无法直接覆盖原安装。APK和认证私钥不纳入源码版本管理。

### GitHub源码检查

CI显式使用`-Pandroidplay.sourceOnly=true`运行测试、lint和调试构建。该模式不接入本机外部认证目录，生成的APK不具备CarPlay认证能力，仅用于源码检查，不作为发行安装包。普通本地构建仍要求认证文件；`assembleStandaloneDebug`不允许使用无认证模式。

### 工程结构

| 路径 | 内容 |
| --- | --- |
| `mobile/` | 应用标识、版本及APK构建 |
| `common/` | 中文首页、投屏页面和连接管理 |
| `shared/` | CarPlay协议、无线连接、音视频及认证实现 |
| `asset/` | AndroidPlay图标源文件和导出素材 |
| `scripts/` | 安装包构建与图标生成脚本 |
| `docs/` | 认证来源、验证记录及第三方声明 |

## 验证与限制

2026年9月30日的构建37记录确认：APK构建与Android真机安装通过，iPhone能够连接投屏，75%/100%/125%/150%四档缩放可改变实际画面，四档解码输出均为2400×1080。详细过程见[验证记录](docs/ANDROIDPLAY_VALIDATION.md)。

以下事项尚未完成独立或全面验证：

- 实际90fps/120fps输出、延迟、温升和长时间连接稳定性。
- 音频、Siri与通话的完整流程。
- 不同Android厂商、挖孔屏及iOS版本的兼容性。
- iPhone原始截图尺寸与Android解码尺寸不一致的原因。

已有本地测试包使用DiPlay0.2.6发布包中的实验性配件身份，并非为AndroidPlay新签发的MFi身份，也不表示Apple认证。一次设备连接成功不保证未来iOS仍接受该身份。认证来源文档中的早期“尚未真机连接”结论属于当时记录，后续结果以构建37验证记录为准。

## 常见问题

| 现象 | 处理方法 |
| --- | --- |
| 需要手动填写热点信息 | 系统未允许读取完整配置，在连接设置中填写与系统热点一致的名称和密码 |
| 找不到iPhone | 先完成系统蓝牙配对，检查附近设备权限，再点击“选择iPhone” |
| 提示缺少认证文件 | 核对外部认证目录，重新构建包含所需资源的APK |
| 连接失败 | 记录界面显示的具体错误，检查热点、蓝牙配对及权限，在连接设置中断开后重试 |
| 缩放切换后暂时没有画面 | 等待自动重连完成；单纯切换菜单值不会修改已经建立的视频会话 |
| 仍有黑边或方向异常 | 检查厂商的应用全屏、挖孔和多窗口设置，系统策略可能覆盖应用窗口配置 |

## 来源与许可

AndroidPlay基于[DiPlay](https://github.com/shihabal3amri/DiPlay)修改，接收器源自[xcertplay](https://github.com/shilapi/xcertplay)。

- 根目录保留[GPLv3许可文本](LICENSE)。
- 首页代码标注`AGPL-3.0-only`，相关文本见[DiAuto许可](docs/licenses/DiAuto-AGPL-3.0.txt)。
- 图标使用本工程绘制的[SVG源文件](asset/androidplay-icon.svg)；依赖、界面来源及历史素材声明见[第三方声明](docs/THIRD_PARTY_NOTICES.md)。

CarPlay名称属于Apple，认证数据及第三方素材不因随工程使用而自动取得项目代码许可。`site/`和部分上游文档保留DiPlay历史内容，可能涉及本版已移除的功能；AndroidPlay的操作与构建以本README为准。

### 版权与分发

本项目是基于上游代码的独立修改版本。原有代码、文档和素材的版权归各自权利人所有；AndroidPlay的名称、改动或重新绘制的图标不替代上游版权，也不改变第三方文件的许可条件。保留源码中的版权标注、SPDX标识、许可文本及第三方声明。

仓库同时包含GPLv3与AGPLv3相关代码，不应仅依据根目录LICENSE将全部文件视为单一许可。复制、修改或分发时，应按所用部分和组合方式履行适用许可要求；分发二进制时，还需按适用条款提供对应源码、构建资料及许可声明。本文不额外添加“禁止商用”等与原许可证不一致的限制。参见[GNU许可说明](https://www.gnu.org/licenses/gpl-faq.html.en)。

### 商标与非关联声明

AndroidPlay是独立项目，未经Apple Inc.授权、赞助或认可，与Apple及上游作者不存在因本项目名称或兼容性描述而产生的官方合作关系。Apple、iPhone和CarPlay等名称属于其相应权利人，仅用于说明技术对象和兼容目标；本项目不声称获得MFi认证。参见[Apple第三方商标使用指引](https://www.apple.com/legal/intellectual-property/guidelinesfor3rdparties.html)。

### 认证数据与第三方内容

源码仓库不提供CarPlay认证私钥、配件证书或包含这些数据的APK。认证资源仅从仓库外的本地目录接入构建；公开来源、可下载或技术上可用，不等于已取得复制、再分发或商业使用授权。现有实验性身份的再分发授权尚未确认，不应将本地测试包作为已获授权的公开发行包。

运行截图可能包含地图、音乐封面、商标或个人信息，其权利不随项目代码许可转移。本地设备截图与安装包保留在忽略的构建目录中，不作为默认源码提交内容。上游历史文档及网站保留原有声明，不代表AndroidPlay为其中的下载、授权或兼容性作出承诺。

### 保证范围

软件的无担保与责任限制以适用许可证及法律为准；本项目不承诺所有设备兼容、认证持续有效或不存在第三方权利争议。“实验性”“学习研究”或本说明本身均不能替代必要授权，也不能免除依法应承担的责任。如需公开分发含第三方认证数据或素材的版本，应先核实相应权利与许可。
