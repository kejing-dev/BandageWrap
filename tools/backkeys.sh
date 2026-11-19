#!/usr/bin/env bash
# D60 back-key self-test with real keys (adb input keyevent) after REAL taps on the window and the TabBar (pico-cli
# interact). Each line: key, design state before, the BACK decision logged, foreground activity and pid after.
#   tools/backkeys.sh OUTFILE
cd "$(dirname "$0")/.."
OUT="${1:-.scratch/back-key.txt}"; D=tools/dev.sh; DEV="${DEV:-emulator-5554}"; PKG=io.embodit.bandagewrap
act() { pico-cli interact act --package $PKG --text "$1" --action click --format json >/dev/null 2>&1 || echo "act $1 failed" | tee -a "$OUT"; sleep "${2:-2}"; }
front() { adb -s $DEV shell dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | sed -E 's/.*\{[^ ]+ [^ ]+ ([^ ]+).*/\1/'; }
state() { $D log "STATE" 1 | sed -E 's/.*STATE ([^ ]+).*/\1/'; }
key() { # key label
  adb -s $DEV logcat -c; adb -s $DEV shell input keyevent "$1"; sleep 2
  local dec; dec=$($D log "BACK |KEY eaten" 3 | sed -E 's/.*(BACK |KEY )/\1/' | tr '\n' '|')
  local after; after=$(state); echo "$2 | $1 | $dec | now=${after:-unchanged} | front=$(front) | pid=$(adb -s $DEV shell pidof $PKG)" | tee -a "$OUT"
}
: > "$OUT"; echo "# back-key self-test $(date '+%F %T') $(adb -s $DEV shell dumpsys package $PKG | grep -m1 versionName)" | tee -a "$OUT"
$D relaunch >/dev/null; $D wait "GEOM built" 60 >/dev/null; $D cmd "lang zh" >/dev/null; sleep 3
key KEYCODE_BACK "S02 list, nothing tapped yet"
act "前臂擦破了" 1; $D wait "WRAP built L2" 30 >/dev/null; sleep 2
key KEYCODE_BACK "S05 watching (tapped a row)"
act "设置"
key KEYCODE_BUTTON_B "S14 settings (tapped the 设置 tab)"
act "退出应用"
key KEYCODE_BUTTON_Y "S14b exit confirm, Y"
key KEYCODE_BUTTON_A "S14b exit confirm, A"
key KEYCODE_ENTER "S14b exit confirm, ENTER"
key KEYCODE_BACK "S14b exit confirm (tab tapped before)"
act "包扎"
act "前臂擦破了" 1; $D wait "WRAP built L2" 30 >/dev/null; sleep 2; act "更多"
key KEYCODE_BUTTON_B "S05m more menu (tab tapped before)"
key KEYCODE_BUTTON_B "S05 watching"
key KEYCODE_BUTTON_B "S02 list"
$D cmd "closewin" >/dev/null; sleep 4
key KEYCODE_BUTTON_B "window closed (system close)"
sleep 4; echo "window after B: $($D log 'WINDOW lifecycle' 1 | sed -E 's/.*lifecycle //')" | tee -a "$OUT"
$D cmd "closewin" >/dev/null; sleep 4
key KEYCODE_BACK "window closed again"
sleep 4; echo "window after BACK: $($D log 'WINDOW lifecycle' 1 | sed -E 's/.*lifecycle //')" | tee -a "$OUT"
for i in 1 2 3; do key KEYCODE_BACK "S02 repeat $i"; done
