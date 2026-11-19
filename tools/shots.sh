#!/usr/bin/env bash
# All design states (design states[].qaCapture) on one device: tools/shots.sh OUTDIR zh|en
# Window crops = OUTDIR/NAME-win.png; full 2560x1440 frames = OUTDIR/NAME.png. Arm close-ups drag the arm into the
# camera's view with the user's own drag path (`move`), because the emulator camera cannot turn (pose injection off).
set -e
cd "$(dirname "$0")/.."
OUT="${1:-.scratch/shots}"; L="${2:-zh}"; mkdir -p "$OUT"
D=tools/dev.sh
c() { $D cmd "$1" >/dev/null; sleep "${2:-2}"; }
shot() { $D shot "$1-$L" "$OUT" >/dev/null; sips -c 430 540 --cropOffset 510 1000 "$OUT/$1-$L.png" --out "$OUT/$1-$L-win.png" >/dev/null 2>&1; echo "$1-$L -> $($D log 'STATE' 1 | sed 's/.*STATE //' | cut -c1-90)"; }
$D relaunch >/dev/null; $D wait "GEOM built" 60 >/dev/null
c "lang $L; seen reset; mute off" 2
shot S02
c "wrap L2" 1; $D wait "WRAP built L2" 30 >/dev/null; c "turn 4 0.55" 1; shot S05
c "turn 6 0.4; play off; slow on" 2; shot S05p
c "slow off; more on" 2; shot S05m
c "more off; xray on; turn 7 0.5" 2; shot S05-xray
c "xray off; turn last" 3; shot S07
c "wrap L7" 1; $D wait "WRAP built L7" 30 >/dev/null; c "turn 5 0.5; play off" 2; shot S05-L7
c "wrap L5" 1; $D wait "WRAP built L5" 30 >/dev/null; c "turn 9 0.6; play off" 2; shot S05-L5
c "wrap L6" 1; $D wait "WRAP built L6" 30 >/dev/null; c "turn 5 0.6; play off" 2; shot S05-L6
c "wrap L2" 1; $D wait "WRAP built L2" 30 >/dev/null; c "play off; onmyarm" 2; shot S04
c "alignwait" 2; shot S04plus
c "manual" 2; shot S04b
c "aligned" 3; c "turn 3 0.5; play off" 2; shot S08
c "more on" 2; shot S08m
c "more off; table; support unsupported" 2; shot S15
c "support ok; table; tab settings" 2; shot S14
c "howto" 2; shot S14h
c "about" 2; shot S14a
c "exit" 2; shot S14b
c "exit no; tab wraps; list" 2
