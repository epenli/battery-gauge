# 小哈锂电电池电量 App（验证版 0.1）

这是为小哈锂电电池开发的非官方 Android 电量查看工具，通过接收附近电池的蓝牙广播显示剩余电量，目标是实现随电池附赠的 USB 电量显示器的查看功能。手机无需通过 USB 连接电池，也无需与电池配对。

目前仅针对本次实测小哈锂电电池的广播格式进行适配，不代表支持所有小哈锂电型号或其他品牌电池。

通过前台 BLE 扫描，解析实测名称格式 HBB<数字编号>SOC<三位电量>。不连接电池，不发送控制命令，不申请网络权限。当前默认电池为本次实测设备，也可在应用内选择其他符合格式的附近电池。

Android 9 及以上；目前安装测试设备为一加 6 / Android 10。Android 10 需要前台定位权限及开启定位服务才能扫描 BLE；Android 12 及以上使用附近设备权限，尚未实机验证。离开应用停止扫描；超过 15 秒未收到目标广播时标记为上次读数。只验证过 33% 样本，其他电量与不同型号兼容性仍需实测。

本地构建：在相邻 battery-app-tools 目录准备 Temurin JDK 17、官方 Android SDK Platform 35 与 Build Tools 35.0.0，分别置于 jdk、platform、build-tools 下，运行 python build.py。签名密钥首次构建自动生成并保留在本地；源代码包不包含签名密钥。更新现有安装需使用原密钥。

验证：BatteryNameTest 覆盖实测名称、非法格式、越界及 0/100 边界；APK 签名验证通过。33% 广播样本由 nRF Connect 观察并由用户核对；本应用已安装，首次权限授权后的实时扫描仍待验证。


## 自动编译与 Releases

推送 main、提交 Pull Request 或手动运行 Actions 会编译 APK、运行广播解析测试并校验签名；APK 和 SHA256SUMS 在 Actions 的 Artifacts 中保留 14 天。

推送与 AndroidManifest.xml 中 versionName 一致的版本标签（例如 v0.1）会自动创建 GitHub 预发布 Release，附带 APK 和 SHA256SUMS。发布前同时递增 versionCode，以便覆盖更新。当前仍为验证版，因此自动发布标记为预发布。

正式分发的签名保存在仓库 Actions Secrets：ANDROID_KEYSTORE_BASE64（PKCS12 密钥的 Base64）和 ANDROID_KEYSTORE_PASSWORD（密码）。应使用首次安装时的原密钥，以保持更新兼容；不要提交密钥文件。版本标签构建缺少签名配置时会停止，不会发布随机签名 APK。普通构建缺少 Secrets 或外部 PR 使用临时签名，只供测试，不能保证覆盖安装。

CI 使用 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。也支持设置 JAVA_HOME、ANDROID_HOME 后在本地运行 python build.py，原 Windows 便携工具目录布局仍可使用。
