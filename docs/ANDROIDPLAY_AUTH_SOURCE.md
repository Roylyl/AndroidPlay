# AndroidPlay认证来源

AndroidPlay1.0.0使用原作者随DiPlay0.2.6公开发布APK提供的实验性本地配件身份。没有生成新的MFi身份，也没有使用Android应用签名密钥代替CarPlay认证。

- 发布页：[DiPlay0.2.6](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.6)
- 原始安装包：[DiPlay-0.2.6.apk](https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.6/DiPlay-0.2.6.apk)
- 发布接口记录的发布时间：2026-09-28T17:59:32Z。
- 提取的APK条目：assets/offline-mfi/identity.pk8和assets/offline-mfi/certificate.p7b。
- 原始APK缓存：~/Library/Caches/AndroidPlay/DiPlay-0.2.6.apk。
- 本机外部资源目录：~/Library/Application Support/AndroidPlay/runtime-assets。

已直接调用工程编译后的LocalMfiAuthenticationClient进行加载检查：证书容器和P-256参数符合要求，私钥能够签名，证书公钥能够验证匹配的签名，认证挑战返回预期长度，证书有效期检查通过。证书标注的截止日期为2049-12-31T23:59:59Z。

这些检查仅确认文件可被本地认证实现使用。它们不证明苹果信任链、撤销状态、特定iOS版本接受情况，也不证明已在用户设备上连接成功。上游发布说明同样将这份身份列为实验性身份，并不保证未来iOS兼容性。

认证文件保留在源码树外，由scripts/build-androidplay.sh通过ANDROIDPLAY_AUTH_ASSETS_DIR显式接入。包含认证文件的安装包可以被接收者提取出同一身份，这与原作者公开发布包的方式一致。

最终AndroidPlay1.0.0已完成独立认证版构建，并确认APK内两份认证文件与通过上述加载检查的本地输入一致。桌面AndroidPlay.apk为本次交付包。尚未进行真机CarPlay连接测试。
