# RC Pro 2 定点定位

一个面向 DJI RC Pro 2、Android 11 的免 Root 模拟定位应用。可以输入经纬度或通过地图选择位置，并在后台持续提供固定坐标。项目与 DJI 无关联，也不是官方产品。

## 功能

- 设置 GPS、网络和 Android 系统融合定位的模拟位置；支持手动停止和状态通知。
- 使用在线地图选点，或输入 WGS-84、GCJ-02、BD-09 坐标；后两种在本地近似转换为 WGS-84。
- 提供 Mac USB 初始化脚本，安装 APK 并打开设备的系统定位。

目标应用可能使用其他定位来源或忽略模拟位置，**不能保证任何游戏或其他应用会采用该位置**。设备固件、系统版本和网络环境也会影响可用性。

## 使用

1. 在设备的开发者选项中启用 USB 调试，连接 Mac 并在设备上允许调试。
2. 先按照下面的步骤构建 APK，然后运行 `installer/RCPro2-setup.command`。脚本检测到多台设备、非 RC Pro 2 或非 Android 11 时会停止；它会安装 APK 并打开系统定位。
3. 在设备的开发者选项 → **选择模拟位置信息应用** 中选择“定点定位”。
4. 在应用中输入坐标，或打开地图点击位置、选择“使用此位置”，然后点击“开始模拟定位”。
5. 使用结束后点击“停止模拟定位”。飞行前停止模拟定位，并在开发者选项中取消模拟应用选择；不要在连接飞行器时测试。

系统可能隐藏定位开关；普通应用无法直接修改该安全设置。本项目使用 Mac 脚本和经用户授权的 USB 调试开启定位，不需要 Root。脚本不更改游戏数据或自动选择模拟定位应用。

地图需要联网，使用 Leaflet 和 OpenStreetMap France 瓦片；浏览的地图区域及网络地址会被地图服务看到。地图数据、网络质量和中文标签显示可能因地区而异。关闭地图后，持续模拟本身无需联网。

## 构建

需要 Python 3、JDK 11 或更高版本、Android SDK Platform 30、Android SDK Build-Tools 35.0.0 和 platform-tools。源码仓库不包含 Android SDK、adb、签名密钥或已签名 APK。

设置 `ANDROID_SDK_ROOT`（或 `ANDROID_HOME`）指向标准 Android SDK 目录，然后运行：

```sh
python3 build.py
```

生成的 APK 位于 `build/FixedLocation-1.1.0.apk`。若 SDK 路径布局不同，可分别设置 `ANDROID_JAR` 和 `ANDROID_BUILD_TOOLS`。构建脚本使用 SDK 工具，无需 Gradle。

首次构建在已被 Git 忽略的 `.local/` 中创建本地签名密钥和密码文件，供后续构建复用。**不要上传或公开这两个文件**；保管好密钥才能用同一签名更新安装。不同签名的 APK 无法直接覆盖已安装版本，卸载旧版本会移除应用数据。为正式发布应自行管理和备份签名材料。

已构建 APK 后，Mac 初始化脚本可这样运行；`adb` 需位于 PATH，或通过 `ADB` 指向它：

```sh
bash installer/RCPro2-setup.command
```

## 测试

坐标转换测试：

```sh
mkdir -p build/coordinate-test
javac -d build/coordinate-test src/cn/vitamin/mocklocation/Coordinates.java tests/CoordinatesTest.java
java -cp build/coordinate-test cn.vitamin.mocklocation.CoordinatesTest
```

设备集成测试会修改测试设备的模拟定位授权，**仅在可丢弃的 Android 11 模拟器中运行**：

```sh
python3 build.py
python3 tests/build_instrumentation.py
adb -s emulator-5554 install -r build/FixedLocation-1.1.0.apk
adb -s emulator-5554 install -r build/instrumentation/SmokeTests.apk
adb -s emulator-5554 shell am instrument -w cn.vitamin.mocklocation.tests/cn.vitamin.mocklocation.tests.SmokeTests
```

将 `emulator-5554` 替换为测试模拟器的实际编号。当前版本曾在一台 Android 11 RC Pro 2 上测试地图和 USB 初始化；其他设备未验证。

## 许可与第三方组件

本项目代码以 [MIT 许可证](LICENSE)提供。`assets/leaflet.js` 和 `assets/leaflet.css` 是 Leaflet 1.9.4，遵循其 [BSD 2-Clause 许可证](assets/LEAFLET-LICENSE.txt)。地图数据由 OpenStreetMap 贡献者提供，地图画面标有相应署名；地图服务可用性和使用条件由其提供方决定。
