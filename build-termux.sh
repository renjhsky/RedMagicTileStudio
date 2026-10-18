#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail
taskProjectRoot="$(cd "$(dirname "$0")" && pwd)"
cd "$taskProjectRoot"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [[ -z "${JAVA_HOME:-}" && -x "${PREFIX:-/data/data/com.termux/files/usr}/opt/openjdk/bin/java" ]]; then
 export JAVA_HOME="${PREFIX:-/data/data/com.termux/files/usr}/opt/openjdk"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v java >/dev/null; then echo '需要 Termux Java 21：pkg install openjdk-21'; exit 20; fi
if ! command -v aapt2 >/dev/null; then echo '缺少 ARM64 aapt2，请使用已配置的 Termux Studio。'; exit 21; fi
if [[ ! -f "$ANDROID_HOME/platforms/android-36/android.jar" ]]; then
 echo "缺少 Android SDK 36：$ANDROID_HOME/platforms/android-36/android.jar"
 echo '需自行安装 SDK 并确认其许可。'; exit 22
fi
taskGradle="$(command -v gradle || true)"
if [[ -z "$taskGradle" ]]; then echo '没有找到 Gradle，可尝试在本目录执行 studio build。'; exit 23; fi
# Preserve an existing locally generated v1 signing identity when available.
if [[ -n "${RMT_KEYSTORE:-}" ]]; then
 : # Keep the caller's explicit local signing identity.
elif [[ -s "$taskProjectRoot/.build-sign/tiles.jks" && -s "$HOME/.redmagic-tiles/password" ]]; then
 export RMT_KEYSTORE="$taskProjectRoot/.build-sign/tiles.jks"
 export RMT_SIGN_PASS="$(cat "$HOME/.redmagic-tiles/password")"
elif [[ -s "$taskProjectRoot/.build-sign/prebuilt.jks" ]]; then
 export RMT_KEYSTORE="$taskProjectRoot/.build-sign/prebuilt.jks"
 export RMT_SIGN_PASS='android'
fi
taskVersion="$(sed -n 's/^versionName=//p' "$taskProjectRoot/version.properties" | tr -d '\r')"
taskAapt2="$(command -v aapt2)"
mkdir -p "$HOME/storage/downloads"
taskLog="$HOME/storage/downloads/RedMagicTileStudio-build.log"
echo "开始编译 v${taskVersion}；日志：$taskLog"
if ! "$taskGradle" :app:assembleDebug --no-daemon -Pandroid.aapt2FromMavenOverride="$taskAapt2" > "$taskLog" 2>&1; then
 echo '编译失败，最后 50 行：'; tail -n 50 "$taskLog"; exit 24
fi
taskApk="$taskProjectRoot/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$taskApk" ]]; then echo '未找到输出 APK'; exit 25; fi
cp -f "$taskApk" "$HOME/storage/downloads/RedMagicTileStudio-NX789J-v${taskVersion}.apk"
echo "已生成 Download/RedMagicTileStudio-NX789J-v${taskVersion}.apk"
echo 'Vector：启用模块并勾选系统界面，再重启手机。随后打开模块开关。'
