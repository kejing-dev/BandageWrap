#!/usr/bin/env bash
# Factory gate evidence with REAL taps (pico-cli interact): cold start = default-view, real tap 前臂擦破了 / 暂停 / 下一圈 /
# 慢放, 设置 = settings-firstscreen, 退出应用 = newgame-confirm (this app has no "new game": quitting is its only
# progress-losing entry), 退出 = exit-recents. tools/evidence.sh OUTDIR
set -e
cd "$(dirname "$0")/.."
OUT="${1:-.scratch/ev}"; mkdir -p "$OUT"; D=tools/dev.sh
act() { pico-cli interact act --package io.embodit.bandagewrap --text "$1" --action click --format json >/dev/null 2>&1 || echo "act $1 failed"; sleep "${2:-2}"; }
$D relaunch >/dev/null; $D wait "GEOM built" 60 >/dev/null; $D cmd "lang zh; mute off" >/dev/null; sleep 3
$D shot default-view "$OUT" >/dev/null
act "前臂擦破了" 1; $D wait "WRAP built L2" 30 >/dev/null; sleep 4; act "暂停"; act "下一圈"; act "慢放"
act "设置"; $D shot settings-firstscreen "$OUT" >/dev/null
act "退出应用"; $D shot newgame-confirm "$OUT" >/dev/null
act "退出" 4
adb -s "${DEV:-emulator-5554}" shell dumpsys activity recents > "$OUT/exit-recents.txt"
$D log "INPUT|PLAY|TURN|SLOW|STATE|EXIT" 14
