package io.embodit.bandagewrap.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.embodit.bandagewrap.domain.ArmPose
import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Playback
import io.embodit.bandagewrap.domain.Wraps
import io.embodit.bandagewrap.platform.BwLog
import io.embodit.bandagewrap.platform.Sound
import io.embodit.bandagewrap.platform.Store
import io.embodit.bandagewrap.ui.window.WindowCtl
import kotlin.system.exitProcess
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * All behaviour of the app (design states S00–S15, interactions T1–T17). Window keys, 3D gestures, the stick and debug
 * commands all come in through [onEvent]; the scene steps [tick] every frame (playback runs on wall time) and reads
 * [playback] for the roll's place. Main thread only.
 */
object BwController {
    private val _state = MutableStateFlow(BwUiState())
    val state: StateFlow<BwUiState> = _state

    private val main = Handler(Looper.getMainLooper())
    private var store: Store? = null
    var soundOrNull: Sound? = null
        private set
    private var activity: Activity? = null
    private var started = false
    private var lastLogged = ""

    /** The player of the wrap on screen (null on the list). The scene reads turn / prog every frame. */
    @Volatile var playback: Playback? = null
        private set

    /** Bumped when the arm or its bandage must be rebuilt (wrap, side, own-arm pose, arm size). */
    @Volatile var geometryEpoch = 0
        private set

    /** Bumped when the arm should turn once round (自动转一圈, entering S07). */
    @Volatile var spinEpoch = 0
        private set

    /** Locked pose of your own arm (S08) and its hand scale, or null on the table. */
    @Volatile var myArm: Pair<ArmPose, Float>? = null
        private set

    /** The scene releases trackers before the process ends (D26). */
    var quitHook: (() -> Unit)? = null

    private var alignStartMs = 0L

    fun start(context: Context) {
        if (started) return
        started = true
        activity = context as? Activity ?: (context as? android.content.ContextWrapper)?.baseContext as? Activity
        val st = Store(context.applicationContext)
        store = st
        val prefs = st.prefs()
        soundOrNull = Sound(context.applicationContext).also { it.start(); it.setVolume(prefs.volume, !prefs.sound) }
        set("start") { it.copy(loading = true, prefs = prefs, seen = st.seen()) }
        BwLog.i("START prefs=$prefs seen=${st.seen()}")
    }

    fun setActivity(a: Activity?) {
        if (a != null) activity = a
    }

    /** The scene says the Stage is up (first frame with a head pose, or the no-head fallback): S00 → S02. */
    fun onReady() {
        if (!_state.value.loading) return
        set("ready") { it.copy(loading = false) }
    }

    /** The app went to the background: stop the playback where it is (design lifecycle). */
    fun onBackground() {
        pauseIfPlaying("background")
    }

    fun onRecentering(r: Boolean) {
        if (_state.value.recentering != r) set("recentering=$r") { it.copy(recentering = r) }
    }

    private fun set(why: String, f: (BwUiState) -> BwUiState) {
        val after = f(_state.value)
        _state.value = after
        val ds = after.designState
        if (ds != lastLogged || why.startsWith("tab") || why.startsWith("turn") || why.startsWith("pick")) {
            lastLogged = ds
            BwLog.i("STATE $ds tab=${after.tab} wrap=${after.wrapId} turn=${after.turn + 1}/${after.turns} playing=${after.playing} slow=${after.slow} xray=${after.xray} why=$why")
        }
    }

    // ============================================================================================== events

    fun onEvent(e: Ui) {
        val s = _state.value
        if (s.loading && e !is Ui.SetPrefs && e !is Ui.TabTo) {
            BwLog.i("INPUT ignored while loading: $e")
            return
        }
        when (e) {
            is Ui.TabTo -> {
                // 切到设置再回来，「看」页停在原来那一圈（暂停）(design returnTo)
                if (e.tab == Tab.SETTINGS && s.watching) pauseIfPlaying("tab")
                set("tab ${e.tab}") { it.copy(tab = e.tab, ask = SettingsAsk.NONE, moreOpen = false, screen = if (e.tab == Tab.WRAPS && s.tab == Tab.WRAPS) Screen.LIST else it.screen) }
                if (e.tab == Tab.WRAPS && s.tab == Tab.WRAPS && s.screen != Screen.LIST) leaveWrap("tab wraps")
            }
            is Ui.Pick -> pick(e.id, "pick")
            Ui.PlayPause -> {
                val pb = playback ?: return
                if (pb.finished) return
                pb.playing = !pb.playing
                BwLog.i("PLAY ${if (pb.playing) "on" else "off"} turn=${pb.turn + 1}")
                sync("play")
            }
            Ui.Prev -> {
                val pb = playback ?: return
                pb.prev()
                BwLog.i("TURN ${pb.turn + 1} via=prev playing=${pb.playing}")
                if (s.screen == Screen.DONE) set("turn prev from done") { it.copy(screen = Screen.WATCH) }
                sync("turn prev")
            }
            Ui.Next -> {
                val pb = playback ?: return
                pb.next()
                BwLog.i("TURN ${pb.turn + 1} via=next playing=${pb.playing} finished=${pb.finished}")
                sync("turn next")
                if (pb.finished) finishWrap()
            }
            Ui.Slow -> {
                val pb = playback ?: return
                pb.slow = !pb.slow
                BwLog.i("SLOW ${if (pb.slow) "0.5x" else "1x"} turnSeconds=${pb.turnSeconds}")
                sync("slow")
            }
            Ui.More -> set("more") { it.copy(moreOpen = !it.moreOpen) }
            Ui.XRay -> set("xray") { it.copy(xray = !it.xray, moreOpen = false) }
            Ui.Spin -> {
                if (s.onMyArm) return
                spinEpoch++
                BwLog.i("SPIN start epoch=$spinEpoch")
                set("spin") { it.copy(moreOpen = false) }
            }
            Ui.Mute -> setPrefs("mute") { it.copy(sound = !it.sound) }
            Ui.OnMyArm -> {
                pauseIfPlaying("align")
                alignStartMs = SystemClock.elapsedRealtime()
                set("align") { it.copy(screen = Screen.ALIGN, moreOpen = false, handSeen = false, alignWaitedLong = false) }
            }
            Ui.Realign -> set("realign") { it.copy(screen = Screen.MANUAL, moreOpen = false) }
            Ui.ManualAlign -> {
                pauseIfPlaying("manual")
                set("manual") { it.copy(screen = Screen.MANUAL, moreOpen = false) }
            }
            Ui.AlignWait -> {
                alignStartMs = SystemClock.elapsedRealtime()
                set("align wait") { it.copy(alignWaitedLong = false) }
            }
            Ui.AlignRetry -> {
                alignStartMs = SystemClock.elapsedRealtime()
                set("align retry") { it.copy(screen = Screen.ALIGN, handSeen = false, alignWaitedLong = false) }
            }
            Ui.Aligned -> manualAligned()
            Ui.BackToTable -> {
                val was = s.onMyArm
                myArm = null
                if (was) geometryEpoch++
                set("back to table") { it.copy(screen = Screen.WATCH, onMyArm = false, moreOpen = false) }
            }
            Ui.NextWrap -> {
                val i = Wraps.index(s.wrapId ?: "")
                val nxt = Wraps.all.getOrNull(i + 1)
                if (nxt == null) {
                    leaveWrap("last done")
                    set("to list") { it.copy(screen = Screen.LIST) }
                } else pick(nxt.id, "pick next")
            }
            Ui.Again -> {
                val pb = playback ?: return
                pb.restart()
                BwLog.i("TURN 1 via=again")
                set("again") { it.copy(screen = Screen.WATCH) }
                sync("turn again")
            }
            Ui.ToList -> {
                leaveWrap("to list")
                set("to list") { it.copy(screen = Screen.LIST) }
            }
            is Ui.SetPrefs -> setPrefs(e.why, e.f)
            is Ui.Ask -> set("ask ${e.ask}") { it.copy(ask = e.ask) }
            Ui.Recenter -> {
                BwLog.i("RECENTER asked")
                set("recenter") { it.copy(ask = SettingsAsk.NONE) }
                WindowCtl.recenter("settings")
            }
            Ui.QuitYes -> quit()
        }
    }

    /** BACK / controller B from any layer (D60): one step back, the window reopened when closed, never a quit. */
    fun onBack(src: String) {
        val s = _state.value
        val a = BackRule.decide(s, WindowCtl.open, WindowCtl.recentering)
        BwLog.i("BACK $src state=${s.designState} window=${WindowCtl.panelState} -> $a")
        when (a) {
            BackAction.REOPEN_WINDOW -> WindowCtl.reopen("back")
            BackAction.WAIT, BackAction.NONE -> Unit
            BackAction.CLOSE_ASK -> onEvent(Ui.Ask(SettingsAsk.NONE))
            BackAction.CLOSE_MORE -> onEvent(Ui.More)
            BackAction.TO_WATCH -> set("back to watch") { it.copy(screen = Screen.WATCH, moreOpen = false) }
            BackAction.TO_TABLE -> onEvent(Ui.BackToTable)
            BackAction.TO_LIST -> onEvent(Ui.ToList)
        }
    }

    private fun pick(id: String, why: String) {
        val w = Wraps.byId(id) ?: return
        playback = null
        geometryEpoch++
        BwLog.i("WRAP pick ${w.id} why=$why epoch=$geometryEpoch")
        set(why) { it.copy(tab = Tab.WRAPS, screen = Screen.WATCH, wrapId = w.id, turn = 0, turns = 0, playing = true, moreOpen = false, building = true, xray = false) }
    }

    /** What each turn of the wrap on screen does (window copy). */
    @Volatile var kinds: List<io.embodit.bandagewrap.domain.TurnKind> = emptyList()
        private set

    fun kindOf(turn: Int): io.embodit.bandagewrap.domain.TurnKind? = kinds.getOrNull(turn)

    /** The scene built the geometry for the current wrap: [turns] turns. Starts playing from turn 1 (T1). */
    fun onBuilt(id: String, turnKinds: List<io.embodit.bandagewrap.domain.TurnKind>) {
        if (_state.value.wrapId != id) return
        kinds = turnKinds
        val turns = turnKinds.size
        val keep = playback?.takeIf { it.turns == turns }
        val pb = keep ?: Playback(turns).also { it.slow = _state.value.slow }
        playback = pb
        BwLog.i("WRAP built $id turns=$turns kept=${keep != null}")
        set("built") { it.copy(turns = turns, building = false) }
        sync("built")
    }

    private fun leaveWrap(why: String) {
        playback = null
        if (myArm != null) {
            myArm = null
        }
        geometryEpoch++
        set("leave $why") { it.copy(wrapId = null, turns = 0, turn = 0, playing = false, onMyArm = false, moreOpen = false, building = false) }
    }

    private fun pauseIfPlaying(why: String) {
        val pb = playback ?: return
        if (pb.playing) {
            pb.playing = false
            BwLog.i("PLAY off turn=${pb.turn + 1} via=$why")
            sync(why)
        }
    }

    /** Copies the player's low-frequency facts into the state (turn index, play / slow). */
    private fun sync(why: String) {
        val pb = playback ?: return
        val s = _state.value
        if (s.turn == pb.turn && s.playing == pb.playing && s.slow == pb.slow) return
        set("turn $why") { it.copy(turn = pb.turn, playing = pb.playing, slow = pb.slow) }
    }

    /** One frame (scene): steps the player on wall time; turn-done sounds; the end of the wrap. */
    fun tick(dtSec: Float) {
        val pb = playback ?: return
        val s = _state.value
        if (s.screen != Screen.WATCH) return
        val changed = pb.step(dtSec)
        val ended = pb.takeEvents()
        if (ended.isNotEmpty() && !pb.finished) {
            soundOrNull?.turnDone()
            BwLog.i("TURN ${pb.turn + 1} via=play ended=${ended.map { it + 1 }}")
        }
        if (changed) sync("tick")
        if (pb.finished) finishWrap()
    }

    private fun finishWrap() {
        val s = _state.value
        if (s.screen == Screen.DONE) return
        val id = s.wrapId ?: return
        val seen = s.seen + id
        store?.saveSeen(seen)
        soundOrNull?.done()
        if (!s.onMyArm) spinEpoch++
        BwLog.i("WRAP done $id seen=${seen.size}/7 spin=${!s.onMyArm}")
        set("done") { it.copy(screen = Screen.DONE, seen = seen, playing = false, moreOpen = false) }
    }

    // ---- 放到我手臂上 (S04 / S04b / S15)

    /** Scene → controller while S04 is up: is the arm's hand tracked; has the support check failed. */
    fun onAlignFrame(handSeen: Boolean, unsupported: Boolean) {
        val s = _state.value
        if (s.screen != Screen.ALIGN) return
        if (unsupported) {
            BwLog.i("ALIGN unsupported -> S15")
            set("unsupported") { it.copy(screen = Screen.UNSUPPORTED) }
            return
        }
        val waited = !handSeen && SystemClock.elapsedRealtime() - alignStartMs > ALIGN_WAIT_MS
        if (s.handSeen != handSeen || s.alignWaitedLong != waited) set("align seen=$handSeen") { it.copy(handSeen = handSeen, alignWaitedLong = waited) }
    }

    /** Scene: the hand was steady 1 s → locked (T9). */
    fun onAlignLocked(pose: ArmPose, scale: Float) {
        if (_state.value.screen != Screen.ALIGN) return
        myArm = pose to scale
        geometryEpoch++
        soundOrNull?.aligned()
        BwLog.i("ALIGN locked wrist=${BwLog.pos(pose.wrist)} s=${"%.3f".format(scale)} epoch=$geometryEpoch")
        set("aligned") { it.copy(screen = Screen.WATCH, onMyArm = true, handSeen = true) }
    }

    /** Scene: the manual-align handles moved the ghost arm to this pose (S04b). */
    fun onManualPose(pose: ArmPose) {
        val scale = myArm?.second ?: 1f
        myArm = pose to scale
    }

    private fun manualAligned() {
        if (_state.value.screen != Screen.MANUAL) return
        if (myArm == null) BwLog.w("ALIGN manual done without a pose (ghost at its default)")
        geometryEpoch++
        soundOrNull?.aligned()
        BwLog.i("ALIGN manual done pose=${myArm?.first?.wrist?.let { BwLog.pos(it) }}")
        set("aligned manual") { it.copy(screen = Screen.WATCH, onMyArm = true) }
    }

    // ---- settings

    private fun setPrefs(why: String, f: (Prefs) -> Prefs) {
        val before = _state.value.prefs
        val p = f(before)
        store?.savePrefs(p)
        soundOrNull?.setVolume(p.volume, !p.sound)
        if (p.side != before.side || p.forearmCm != before.forearmCm || p.girth != before.girth) {
            geometryEpoch++
            BwLog.i("ARM side=${p.side} forearm=${p.forearmCm} girth=${p.girth} epoch=$geometryEpoch")
        }
        set("prefs $why") { it.copy(prefs = p, moreOpen = if (why == "mute") false else it.moreOpen) }
    }

    fun armSide(): ArmSide = _state.value.prefs.side

    // ---- quit (D26)

    private fun quit() {
        BwLog.i("EXIT confirmed: release + close window + closeStage + finishAndRemoveTask + exit")
        playback = null
        soundOrNull?.release()
        soundOrNull = null
        quitHook?.invoke()
        WindowCtl.closeForQuit()
        val nav = WindowCtl.navigator
        val a = activity
        MainScope().launch {
            runCatching { nav?.closeStage() }.onFailure { BwLog.w("EXIT closeStage: $it") }
            runCatching {
                val am = a?.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.appTasks?.forEach { runCatching { it.finishAndRemoveTask() } }
                a?.finishAndRemoveTask()
            }.onFailure { BwLog.w("EXIT finishAndRemoveTask: $it") }
            main.postDelayed({ exitProcess(0) }, 400)
        }
    }

    // ============================================================================================== debug seams

    fun debugSeek(turn: Int, prog: Float) {
        val pb = playback ?: return
        pb.seek(turn, prog)
        if (_state.value.screen == Screen.DONE) set("seek") { it.copy(screen = Screen.WATCH) }
        BwLog.i("TURN ${pb.turn + 1} via=debug prog=$prog")
        sync("turn debug")
    }

    fun debugFinish() {
        val pb = playback ?: return
        pb.finishNow()
        sync("turn debug-finish")
        finishWrap()
    }

    fun debugSlow(on: Boolean) {
        if ((playback?.slow ?: _state.value.slow) != on) onEvent(Ui.Slow)
    }

    fun debugPlay(on: Boolean) {
        val pb = playback ?: return
        if (pb.playing != on) onEvent(Ui.PlayPause)
    }

    fun debugUnsupported() {
        if (_state.value.screen != Screen.ALIGN) onEvent(Ui.OnMyArm)
        onAlignFrame(handSeen = false, unsupported = true)
    }

    /** Debug `alignwait`: as if 10 s passed in S04 without a hand. */
    fun debugAlignWaited() {
        alignStartMs = SystemClock.elapsedRealtime() - ALIGN_WAIT_MS - 1
        onAlignFrame(handSeen = false, unsupported = false)
    }

    fun resetSeen() {
        store?.saveSeen(emptySet())
        set("reset seen") { it.copy(seen = emptySet()) }
    }

    const val ALIGN_WAIT_MS = 10_000L
}
