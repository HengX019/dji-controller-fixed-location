#!/bin/bash
cd "$(dirname "$0")" || exit 1
ADB="${ADB:-$(command -v adb)}"
if [ -z "$ADB" ] && [ -n "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]; then
  ADB="${ANDROID_SDK_ROOT:-$ANDROID_HOME}/platform-tools/adb"
fi
APK="${APK:-../build/FixedLocation-1.1.0.apk}"
finish() { printf '\n按回车键关闭窗口。'; read -r; }
trap finish EXIT
printf 'RC Pro 2 定点定位初始化\n\n请开启遥控器的开发者选项和 USB 调试，用数据线连接，并在遥控器上允许 USB 调试。\n\n'
if [ ! -x "$ADB" ] || [ ! -f "$APK" ]; then
  printf '未找到 adb 或 APK。请先安装 Android platform-tools 并构建应用。\n'; exit 1
fi
"$ADB" start-server >/dev/null 2>&1 || { printf '无法启动连接工具。\n'; exit 1; }
printf '正在查找设备；如屏幕上出现授权提示，请点击允许。\n'
for attempt in {1..30}; do
  connected=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  count=$(printf '%s\n' "$connected" | awk 'NF{n++} END{print n+0}')
  [ "$count" -gt 0 ] && break
  sleep 2
done
if [ "$count" != 1 ]; then
  printf '请只连接一台已授权的遥控器后重新运行。\n'; exit 1
fi
serial="$connected"
model=$("$ADB" -s "$serial" shell getprop ro.product.model | tr -d '\r')
sdk=$("$ADB" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$model" != 'DJI RC PRO 2' ] || [ "$sdk" != '30' ]; then
  printf '这份工具只针对 Android 11 的 DJI RC Pro 2；已停止。检测到型号：%s，Android API：%s\n' "$model" "$sdk"; exit 1
fi
printf '已连接 RC Pro 2，正在安装应用……\n'
"$ADB" -s "$serial" install -r "$APK" || { printf '安装失败，请检查遥控器剩余空间和安装权限。\n'; exit 1; }
printf '正在打开系统定位……\n'
"$ADB" -s "$serial" shell cmd location set-location-enabled true || { printf '开启失败，请检查系统限制。\n'; exit 1; }
mode=$("$ADB" -s "$serial" shell settings get secure location_mode | tr -d '\r')
if [ "$mode" != '3' ]; then
  printf '未能确认系统定位已开启，请检查遥控器设置。\n'; exit 1
fi
"$ADB" -s "$serial" shell am start -n cn.vitamin.mocklocation/.MainActivity >/dev/null
printf '\n安装完成，系统定位已开启。请在遥控器的开发者选项中将「定点定位」设为模拟位置信息应用。\n'
printf '飞行前请停止模拟定位，并取消模拟应用选择。\n'
