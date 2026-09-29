# AndroidPlay

AndroidPlay基于DiPlay修改，仅提供无线CarPlay接收功能。原项目许可证和第三方声明保留在LICENSE及docs/THIRD_PARTY_NOTICES.md中。

## 已修改

- 工程名、应用显示名和项目文件夹统一为AndroidPlay，安装包标识为com.androidplay.app。
- 首页、连接提示、权限说明、网络设置和通知使用中文。
- 仅保留本机系统WPA2热点连接，提供设备选择、连接、断开、热点信息配置和停止重试。
- 删除有线USB启动入口、Android Auto/Automotive应用入口、HUD/仪表盘、调试演示、图片裁剪和高级设置界面。控制器只接受无线连接配置。
- 启动时集中申请此版本实际需要的运行时权限。Android13及以上申请附近设备、麦克风、通知；Android12及以下按系统版本申请蓝牙和位置权限。
- 首页及投屏页锁定横屏，支持左右两个横屏方向。隐藏状态栏和导航栏，返回应用后恢复沉浸式全屏。
- 投屏覆盖挖孔区域，首页仅让按钮和文字避开摄像头，不为整个页面预留黑色顶部。

## 首次连接

1. 安装桌面的AndroidPlay.apk，已安装上一版时直接覆盖安装。
2. 在Android系统设置中开启WPA2个人热点，让iPhone连接这个热点。
3. 打开AndroidPlay并授权，进入“连接设置→设置热点信息”，填写与系统热点相同的名称和密码。
4. 两端开启蓝牙，在系统设置中完成蓝牙配对，再回到应用选择iPhone。
5. 点击“打开CarPlay”，应用会在热点网络广播AndroidPlay接收服务，并通过蓝牙进行首次引导。在iPhone上允许CarPlay。

iPhone“设置→通用→CarPlay”是否出现AndroidPlay尚待真机验证，网络广播不等于完成车辆注册。连接失败时会保留失败原因；通过“连接设置→断开连接”停止后再连接，系统热点保持开启。

## 权限与横屏

Android要求用户在系统弹窗中确认授权，普通应用不能静默取得所有权限。拒绝后可在“连接设置→检查应用权限”重新申请或打开应用设置。麦克风被拒绝时，连接仍可使用，但Siri和通话语音不可用。

compileSdk保留37，targetSdk调整为35，以保留大屏上的横屏限制；原来的targetSdk37会在新系统的大屏上忽略方向锁定。系统级强制多窗口、厂商显示策略和系统权限页面仍受系统控制。应用没有修改系统全局方向或显示设置。

## 构建

使用Android Studio导入本目录。当前工程使用Gradle9.5.0、AGP9.3.0、JDK25、AndroidSDK37及NDK29.0.14206865。local.properties中的SDK路径是当前电脑路径，换电脑时由Android Studio重新配置。

本机的认证文件已从原作者DiPlay0.2.6公开发布APK中提取，保存在源码目录之外：

```text
~/Library/Application Support/AndroidPlay/runtime-assets/offline-mfi/
├── identity.pk8
└── certificate.p7b
```

在当前电脑上重新生成包含认证文件的安装包：

```sh
./scripts/build-androidplay.sh
```

脚本默认读取上述目录，也可通过ANDROIDPLAY_AUTH_ASSETS_DIR指定其他目录。输出为build/AndroidPlay.apk。认证文件不会被自动放进源码目录。

Android Studio直接运行与下面的命令均默认接入本机外部认证目录，文件缺失时构建失败：

```sh
./gradlew :mobile:assembleDebug
```

认证来源和已完成的检查见[认证来源说明](docs/ANDROIDPLAY_AUTH_SOURCE.md)。本地加载成功不代表iPhone已经接受这份实验性身份。

## 验证范围

当前显示版本为1.0.0，versionCode为37，用于覆盖此前交付的测试包。认证文件继续通过外部资源目录接入。构建和验证记录见[验证记录](docs/ANDROIDPLAY_VALIDATION.md)。

系统热点及蓝牙引导的可用性由设备和iPhone系统决定。

已通过ADB完成Android真机覆盖安装、启动及本地认证加载验证，首页显示“准备连接”。真实的蓝牙配对、iPhone认证、Wi-Fi连接、音视频、Siri以及厂商挖孔屏显示，仍需在实际设备上确认。

[README.md](README.md)是当前AndroidPlay的主要说明；[README.zh-CN.md](README.zh-CN.md)提供同一说明的中文入口。site目录和其他保留的上游文档可能描述DiPlay历史功能，不能直接视为本版功能清单。

## 显示与热点更新

启动及连接前尝试自动读取系统热点，系统限制读取时仍需手动配置一次。连接设置提供热点信息自动读取，独立CarPlay设置，支持30/60/90/120fps实验性请求和75%/100%/125%/150%图标文字缩放，选择后自动结束旧会话并重连。120fps不代表iPhone实际输出已验证。

缩放按上游物理尺寸方式实现，读取已保存档位，不改变视频像素或触摸坐标。
