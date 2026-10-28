# RC 一代 / RC 2 测试版

这是从 RC Pro 2 版本调整的测试版，最低要求 Android 10（API 29），支持输入坐标和地图选点。APK 不包含 CPU 架构专用本地库，可供 32 位和 64 位 Android 系统尝试。

**尚未在 RC 一代、RC 2 或 32 位系统实测。** DJI 表示 RC 2 不支持安装第三方应用；设备系统、固件、安装限制、开发者选项、模拟定位权限以及 WebView 和网络可用性都可能影响结果。此版本不保证能够安装或影响其他应用的定位。

- [下载预编译测试版 APK](../../downloads/FixedLocation-RC1-RC2-test.apk)
- 独立构建命令：`python3 build.py`，输出 `build/FixedLocation-RC1-RC2-test.apk`。需要 Python 3、JDK 11+、Android SDK Platform 30 和 Build-Tools 35.0.0；也可使用 `ANDROID_JAR` 和 `ANDROID_BUILD_TOOLS` 指定路径。
- 先在设备上开启系统定位，并在开发者选项选择“定点定位”为模拟位置应用；输入坐标或通过地图选择，点击开始。地图需要网络和系统 WebView。
- 使用结束后点击停止，取消模拟应用选择。飞行前确认真实定位恢复；不要在连接飞行器或飞行时使用模拟定位。
- 不要使用根目录的 RC Pro 2 专用初始化脚本。

应用包名与 RC Pro 2 版相同。仓库提供的两份 APK 使用相同发布签名，不能作为独立应用同时安装；本测试版版本号较高，回装 RC Pro 2 版属于降级，普通安装可能失败。自行构建的密钥不同则不能直接覆盖，卸载会移除应用数据。构建生成的 `.local/` 含密钥和密码，切勿上传。

测试：坐标与输入校验 28 项通过；Android 11 64 位模拟器集成检查 78 项通过。Android 10、32 位及两款遥控器实机仍需测试。

项目代码遵循根目录 MIT 许可证；Leaflet 的 BSD 2-Clause 许可见 `assets/LEAFLET-LICENSE.txt`。
