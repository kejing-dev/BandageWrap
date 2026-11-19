package io.embodit.bandagewrap.debug

import android.content.Context
import io.embodit.bandagewrap.domain.Align
import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Vec3
import io.embodit.bandagewrap.domain.Yaw
import io.embodit.bandagewrap.domain.Wraps
import io.embodit.bandagewrap.platform.BwLog
import io.embodit.bandagewrap.ui.BwController
import io.embodit.bandagewrap.ui.Lang
import io.embodit.bandagewrap.ui.Screen
import io.embodit.bandagewrap.ui.SettingsAsk
import io.embodit.bandagewrap.ui.Tab
import io.embodit.bandagewrap.ui.Ui
import io.embodit.bandagewrap.ui.components.TextFit
import io.embodit.bandagewrap.ui.debug.Probe
import io.embodit.bandagewrap.ui.scene.Target
import io.embodit.bandagewrap.ui.window.WindowCtl

/**
 * Debug builds only (design verification.debugHooks). A command line is one or more commands separated by `;`:
 *
 *   adb shell "am broadcast -a io.embodit.bandagewrap.DEBUG -p io.embodit.bandagewrap --es cmd 'wrap L2; turn 4 0.5; play off'"
 *
 * Every command sends the window's own events ([BwController.onEvent]) or drives the scene's own paths (the arm drag, the
 * turn of the stick, the align lock from joints): no command writes an app state directly (factory rule 2026-10-29).
 * Each state change logs `STATE Sxx…`. List and screenshot recipes: AGENTS.md.
 */
object DebugCommands {
    private fun ev(e: Ui) = BwController.onEvent(e)

    fun run(context: Context, raw: String): String {
        val out = ArrayList<String>()
        for (part in raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }) out += one(part)
        return out.joinToString(" | ")
    }

    private fun one(cmd: String): String {
        val a = cmd.split(Regex("\\s+"))
        val v = a.getOrNull(1)
        val scene = Probe.host?.scene()
        val s = BwController.state.value
        when (a[0]) {
            "state" -> return v?.let { goState(it) } ?: "STATE ${s.designState}"
            "tab" -> ev(Ui.TabTo(if (v == "settings") Tab.SETTINGS else Tab.WRAPS))
            "list" -> ev(Ui.ToList)
            "wrap" -> ev(Ui.Pick(v ?: "L2"))
            "turn" -> if (v == "last") BwController.debugFinish() else BwController.debugSeek((v ?: "1").toInt() - 1, a.getOrNull(2)?.toFloat() ?: 0f)
            "play" -> BwController.debugPlay(v != "off")
            "slow" -> BwController.debugSlow(v != "off")
            "prev" -> ev(Ui.Prev)
            "next" -> ev(Ui.Next)
            "xray" -> if ((v != "off") != s.xray) ev(Ui.XRay)
            "more" -> if ((v != "off") != s.moreOpen) ev(Ui.More)
            "spin" -> ev(Ui.Spin)
            "again" -> ev(Ui.Again)
            "nextwrap" -> ev(Ui.NextWrap)
            "yaw" -> scene?.turnArm(Math.toRadians((v ?: "90").toDouble()).toFloat(), "debug")
            "move" -> scene?.debugMove(a[1].toFloat(), a[2].toFloat(), a[3].toFloat())
            "drag" -> scene?.debugDrag(if (v == "elbow") Target.ELBOW_HANDLE else Target.WRIST_HANDLE, a[2].toFloat(), a[3].toFloat(), a[4].toFloat())
            "onmyarm" -> ev(Ui.OnMyArm)
            "manual" -> ev(Ui.ManualAlign)
            "aligned" -> ev(Ui.Aligned)
            "table" -> ev(Ui.BackToTable)
            "realign" -> ev(Ui.Realign)
            "alignwait" -> BwController.debugAlignWaited()
            "wait" -> ev(Ui.AlignWait)
            "retry" -> ev(Ui.AlignRetry)
            "pose" -> {
                // injected joints of the arm's hand (design HandPoseSource.Injected): `pose ahead` or `pose x y z [yawDeg]`
                val head = scene?.headPose() ?: return "no scene"
                val right = s.prefs.side == ArmSide.RIGHT
                val (wrist, yawDeg) =
                    if (v == null || v == "ahead") {
                        val f = Yaw.dir(head.yaw(0f))
                        val r = Vec3(-f.z, 0f, f.x)
                        head.position + f * 0.40f + r * (if (right) 0.12f else -0.12f) - Vec3(0f, 0.38f, 0f) to 0f
                    } else Vec3(a[1].toFloat(), a[2].toFloat(), a[3].toFloat()) to (a.getOrNull(4)?.toFloat() ?: 0f)
                // fingers point away from you, a little inwards
                val yaw = head.yaw(0f) + Math.toRadians(yawDeg.toDouble()).toFloat() + (if (right) 0.35f else -0.35f)
                scene.injected = Align.synthHand(right, wrist, Yaw.dir(yaw), (a.getOrNull(5)?.toFloat() ?: 1f))
                return "POSE injected wrist=${BwLog.pos(wrist)} right=$right"
            }
            "hand" -> if (v == "none") scene?.injected = null
            "support" -> {
                scene?.debugSupport(if (v == "ok") null else (v ?: "DEVICE_NOT_SUPPORTED").uppercase())
                if (v != "ok") BwController.debugUnsupported()
            }
            "side" -> ev(Ui.SetPrefs("side") { it.copy(side = if (v == "right") ArmSide.RIGHT else ArmSide.LEFT) })
            "lang" -> ev(Ui.SetPrefs("lang") { it.copy(lang = when (v) { "zh" -> Lang.ZH; "en" -> Lang.EN; else -> Lang.SYSTEM }) })
            "mute" -> if ((v != "off") == s.prefs.sound) ev(Ui.Mute)
            "howto" -> { ev(Ui.TabTo(Tab.SETTINGS)); ev(Ui.Ask(SettingsAsk.HOWTO)) }
            "about" -> { ev(Ui.TabTo(Tab.SETTINGS)); ev(Ui.Ask(SettingsAsk.ABOUT)) }
            "exit" -> when (v) {
                "yes" -> ev(Ui.QuitYes)
                "no" -> ev(Ui.Ask(SettingsAsk.NONE))
                else -> { ev(Ui.TabTo(Tab.SETTINGS)); ev(Ui.Ask(SettingsAsk.EXIT)) }
            }
            "recenter" -> ev(Ui.Recenter)
            "recall" -> WindowCtl.recall("debug")
            "seen" -> if (v == "reset") BwController.resetSeen()
            "fit" -> { if (v == "reset") TextFit.reset(); return TextFit.summary() }
            "dump" -> return when (v) {
                "arm" -> scene?.where() ?: "no scene"
                "turn" -> scene?.dumpTurn() ?: "no scene"
                "window" -> "WINDOW state=${WindowCtl.panelState} dpM=${WindowCtl.dpM} level=${WindowCtl.levelHint} recenter=${WindowCtl.recentering}"
                "panels" -> Probe.controlsOf(a.getOrNull(2) ?: "window").entries.joinToString("; ") { (k, b) -> "%s=(%.0f,%.0f %.0fx%.0f)".format(k, b.cx, b.cy, b.w, b.h) }
                else -> "STATE ${s.designState} $s"
            }
            else -> return "unknown '$cmd'"
        }
        return "ok '${cmd}' -> ${BwController.state.value.designState}"
    }

    /** Jumps to a design state through the user's own events. */
    private fun goState(id: String): String {
        val s = BwController.state.value
        when (id) {
            "S02" -> { ev(Ui.TabTo(Tab.WRAPS)); if (s.screen != Screen.LIST) ev(Ui.ToList) }
            "S05" -> { if (s.wrapId == null) ev(Ui.Pick("L2")); if (s.onMyArm) ev(Ui.BackToTable) }
            "S07" -> { if (s.wrapId == null) ev(Ui.Pick("L2")); BwController.debugFinish() }
            "S04" -> { if (s.wrapId == null) ev(Ui.Pick("L2")); ev(Ui.OnMyArm) }
            "S04b" -> { if (s.wrapId == null) ev(Ui.Pick("L2")); ev(Ui.ManualAlign) }
            "S15" -> { if (s.wrapId == null) ev(Ui.Pick("L2")); BwController.debugUnsupported() }
            "S14" -> ev(Ui.TabTo(Tab.SETTINGS))
            else -> return "unknown state $id"
        }
        return "STATE ${BwController.state.value.designState}"
    }

    @Suppress("unused")
    private val ids = Wraps.all.map { it.id }
}
