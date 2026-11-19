# Bandage Wrap (包扎课)

PICO OS 6 app, Kotlin, PICO Spatial SDK 6.1.9, package `io.embodit.bandagewrap`, v0.1.0. Pick one of seven arm bandage
wraps and watch a life-size bandage go round a 1:1 arm on your desk, turn by turn: play / pause, back / next turn, slow
0.5×, drag and turn the arm (stick), see-through for the layers, spin once; optionally line the arm up with your own
(hand tracking, one lock). Watch only (design scopeV1, 2026-11-18): no follow-along, no self-check, no quiz.

Design package (binding): `../spatial-app-factory/apps/bandage-wrap/design-package.json` (states S00–S15, T1–T17,
armModel, spatialLayout, content), sample `../spatial-app-factory/apps/bandage-wrap/sample.html` (copy in `screens.js`).
Skeleton from Cube Tutor (STAGE_MIXED + one system Planar window + TabBar, level gate, panel pool, theme, fonts, debug
receiver); hand tracking from X-ray Hand.

Container **STAGE_MIXED** (Full Space, passthrough, upper limbs Hidden). All 2D content is ONE system Planar window
(480 × 500 dp, 0.8 mm/dp) with the system TabBar (包扎 / 设置). No network, no camera, no runtime permissions.

## Structure

```
app/src/main/java/io/embodit/bandagewrap/
  Main.kt                DefaultStage (Mixed) -> BwScreen; WindowContainer -> BwWindow
  domain/                pure Kotlin, JVM-tested
    Arm.kt               ArmModel: parametric limb (spine u + superelliptic sections, hand, finger grooves, thumb,
                         elbow bend 25° table / 90° elbow lesson, 12 cm upper arm); point / normal / project
    Wraps.kt             the 7 wraps (copy zh + en), per-turn copy (cue / label / why), banned treatment words
    WrapBuilder.kt       turn plans (anchor, spiral, reverse, 8s, spica, elbow 8) -> sampled paths -> strips on the
                         skin, lifted by how many earlier turns lie under each vertex (layer cap 7)
    Playback.kt          3 s per turn (6 s slow), wall-clock steps, prev / next / seek / finish
    Hands.kt             Joint, HandJoints, Align (arm frame from a hand, synthetic hands), SteadyLock (1 s still)
    ArmLayout.kt         table / elbow-lesson placement from the head (yaw only), push-out 0.45 m, window sight cone
    LevelGate / SteadyGate / Geometry / Layout / Vec3
  platform/              BwLog (tag BandageWrap), Store (prefs + watched), Sound (SoundPool, 4 event sounds),
                         input/Controllers (stick), tracking/HeadTracker, tracking/HandTracker (+ Device.onEmulator)
  ui/                    BwUiState (+ designState), BwController (ALL behaviour), BwScreen
    window/              BwWindow + WindowCtl (level gate, recall / recenter), MainWindowTemplate
    components/          Pages.kt (list / watch / more / done / align / manual / unsupported / settings), BwStage
                         (SpatialView + board-input + 3 attachment panels: chip, hint, turn label), Strings, TextFit
    scene/               BwScene (rig -> poseNode -> arm, turn strips, growing turn, roll + arrow, next-turn line,
                         handles; per-frame playback, stick, spin, align), Meshes, SceneKit, PanelRig
    input/BoardInput.kt  factory template (drag the arm / handles; taps on 3D do nothing)
app/src/debug/           DebugCommandReceiver + DebugCommands
app/src/main/assets/sfx  rustle, rustle_2 (CC0 recordings), click (Kenney CC0), done (synth UI chime)
tools/                   dev.sh, shots.sh (all states zh / en), strings.py (single source of copy), make_icon.py
```

Conventions: Stage metres, −Z ahead. Arm-local: origin = wrist centre, +Z distal, +Y dorsal, built for the right arm
(thumb +X); the left arm mirrors x. The bandage is computed on the same parametric surface as the arm mesh (no gap).

## Build, run (mac1)

```bash
tools/dev.sh build && tools/dev.sh install && tools/dev.sh relaunch   # DEV=emulator-5554 by default
tools/dev.sh test                                                     # domain + hit-replay tests
tools/shots.sh OUTDIR zh|en                                           # every design state, window crops *-win.png
python3 -I tools/strings.py ; python3 tools/make_icon.py --png ../spatial-app-factory/apps/bandage-wrap/icon.png
./gradlew --offline assembleRelease                                   # unsigned release (Vibe Lab signs it)
```

## Debug broadcast (debug builds)

`tools/dev.sh cmd 'wrap L2; turn 4 0.5; play off'` = `adb shell "am broadcast -a io.embodit.bandagewrap.DEBUG -p
io.embodit.bandagewrap --es cmd '…'"`. Commands separated by `;`, each runs the user's own event / path.
`STATE Sxx …` is logged on every change. Wait for `GEOM built` after launch and `WRAP built Lx` after `wrap Lx`.

| command | effect |
|---|---|
| `state S02|S05|S07|S04|S04b|S15|S14` | jump through the user's events |
| `tab wraps|settings` `list` `wrap L1..L7` | TabBar / list / pick (starts playing) |
| `turn <n> [prog]` `turn last` `play on|off` `slow on|off` `prev` `next` `again` `nextwrap` | playback (needs the wrap built) |
| `more on|off` `xray on|off` `spin` `mute on|off` | More menu items, mute key |
| `yaw <deg>` `move dx dy dz` | turn the arm (stick path) / drag it (drag path, push-out applies) |
| `onmyarm` `pose ahead|x y z [yaw] [s]` `hand none` `alignwait` `wait` `retry` | S04: inject the arm's hand joints (locks after 1 s still), 10 s bar |
| `manual` `drag wrist|elbow dx dy dz` `aligned` `table` `realign` | S04b handles, 对上了, 回到桌上看 |
| `support unsupported|ok` | S15 |
| `side left|right` `lang zh|en|system` `howto` `about` `exit [yes|no]` `recenter` `recall` `seen reset` | settings |
| `fit [reset]` `dump arm|turn|window|panels [panel]|state` | diagnostics (overflow summary, placement) |

## Screenshot list (QA, design states[].qaCapture)

`tools/shots.sh` covers S02, S05 (L2 / L5 / L6 / L7), S05p, S05m, see-through, S07 (mid-spin), S04, S04+, S04b, S08,
S08m, S15, S14, S14h (how to), S14a (about), S14b (quit confirm). S00 lasts < 1 s on the emulator. Arm close-ups:
`move 0.2 0.12 0.14` drags the arm into the straight-ahead camera (the emulator camera cannot turn: tracking injection
is off, and turning it on hung the emulator shell once).

## Platform facts found here

- Emulator head at (0, 1.65, 0) standing; the window opens straight ahead (`WINDOW dp=0.00080`), the arm 18° left,
  25° below the eyes, wrist 0.53 m, fingertips 0.36 m (`STARTUP` lines).
- Two-sided PBR shading flips the normal for triangles wound inward: the limb mesh must be wound outward per arm side
  (fixed in `Meshes.arm`), or the top of the arm renders dark.
- `ControllerTrackingProvider` = DEVICE_NOT_SUPPORTED on the emulator (no stick there); the hand provider is never
  started on the emulator (Spatial Ruler lesson); S04 is driven by `pose` injection.
