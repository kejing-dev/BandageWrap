#!/usr/bin/env bash
# Bandage Wrap dev helper (mac1): build / install / relaunch / cmd / shot / log on one device.
#   DEV=emulator-5554 tools/dev.sh build|test|install|relaunch|cmd 'wrap L2; turn 3'|shot NAME [DIR]|crop NAME [DIR]|log PATTERN [N]|crash|wait PATTERN
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.pico/primer-cli/jdk/jdk-21.0.12.1+1/Contents/Home}"
DEV="${DEV:-emulator-5554}"
PKG=io.embodit.bandagewrap
APK=app/build/outputs/apk/debug/app-debug.apk
case "$1" in
  build) ./gradlew --offline -q assembleDebug ;;
  test) ./gradlew --offline -q testDebugUnitTest ;;
  install) adb -s "$DEV" install -r "$APK" | tail -1 ;;
  relaunch) adb -s "$DEV" shell am force-stop $PKG; adb -s "$DEV" logcat -c; adb -s "$DEV" shell am start -W -n $PKG/.platform.LaunchActivity | tail -2 ;;
  cmd) adb -s "$DEV" shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$2'" >/dev/null; echo "sent: $2" ;;
  shot) out="${3:-.}/$2.png"; pico-cli capture screenshot -d "$DEV" -o "$out" --replace --spatial --size 2560x1440 >/dev/null 2>&1; echo "$out" ;;
  log) adb -s "$DEV" logcat -d -s BandageWrap:V | /usr/bin/grep -E "$2" | tail -"${3:-40}" ;;
  wait) for i in $(seq 1 "${3:-40}"); do adb -s "$DEV" logcat -d -s BandageWrap:V | /usr/bin/grep -qE "$2" && exit 0; sleep 1; done; echo "timeout waiting for $2"; exit 1 ;;
  crash) adb -s "$DEV" logcat -d -b crash | tail -40 ;;
  *) sed -n 2,3p "$0" ;;
esac
