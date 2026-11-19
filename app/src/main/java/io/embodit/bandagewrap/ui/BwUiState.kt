package io.embodit.bandagewrap.ui

import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Girth

enum class Tab { WRAPS, SETTINGS }

/** What the 包扎 page shows (design states: S02 list, S05 / S08 watching, S07 done, S04 / S04b align, S15 unsupported). */
enum class Screen { LIST, WATCH, DONE, ALIGN, MANUAL, UNSUPPORTED }

enum class Lang { SYSTEM, ZH, EN }

/** Settings (design dataModel.Settings). [forearmCm] / [girth] null = estimated from the hand ("我的手臂"). */
data class Prefs(
    val lang: Lang = Lang.SYSTEM,
    val side: ArmSide = ArmSide.LEFT,
    val forearmCm: Int? = null,
    val girth: Girth? = null,
    val sound: Boolean = true,
    val volume: Int = 80,
)

/** In-place confirmations / sub-views of the settings page. */
enum class SettingsAsk { NONE, EXIT, HOWTO, ABOUT }

/**
 * Everything the window and the scene read (low frequency: per-turn, never per-frame). Playback progress inside a turn
 * lives in [BwController.playback] and is read by the scene each frame.
 */
data class BwUiState(
    val loading: Boolean = true,
    val tab: Tab = Tab.WRAPS,
    val screen: Screen = Screen.LIST,
    val wrapId: String? = null,
    val turn: Int = 0,
    val turns: Int = 0,
    val playing: Boolean = false,
    val slow: Boolean = false,
    val xray: Boolean = false,
    val moreOpen: Boolean = false,
    /** S08: watching on your own arm (after S04 / S04b). */
    val onMyArm: Boolean = false,
    /** S04: the arm's hand is tracked now / waited 10 s without seeing it. */
    val handSeen: Boolean = false,
    val alignWaitedLong: Boolean = false,
    val seen: Set<String> = emptySet(),
    val prefs: Prefs = Prefs(),
    val ask: SettingsAsk = SettingsAsk.NONE,
    val spinning: Boolean = false,
    /** The arm geometry for this wrap is being built (S05 before the first frame). */
    val building: Boolean = false,
    /** "窗口移到面前": waiting for a steady head. */
    val recentering: Boolean = false,
) {
    val watching: Boolean
        get() = screen == Screen.WATCH || screen == Screen.DONE

    /** Design state id (STATE log lines, QA screenshots): S00 S02 S05 S05p S05m S07 S04 S04+ S04b S08 S08m S15 S14 S14b. */
    val designState: String
        get() =
            when {
                loading -> "S00"
                tab == Tab.SETTINGS -> if (ask == SettingsAsk.EXIT) "S14b" else if (ask == SettingsAsk.HOWTO) "S14h" else if (ask == SettingsAsk.ABOUT) "S14a" else "S14"
                screen == Screen.LIST -> "S02"
                screen == Screen.WATCH && onMyArm -> if (moreOpen) "S08m" else "S08"
                screen == Screen.WATCH -> if (moreOpen) "S05m" else if (!playing && slow) "S05p" else "S05"
                screen == Screen.DONE -> "S07"
                screen == Screen.ALIGN -> if (alignWaitedLong && !handSeen) "S04+" else "S04"
                screen == Screen.MANUAL -> "S04b"
                else -> "S15"
            }
}

/** Every input of the app: window keys, 3D gestures, the stick, debug commands — one path (factory rule 2026-10-29). */
sealed class Ui {
    data class TabTo(val tab: Tab) : Ui()
    data class Pick(val id: String) : Ui()
    data object PlayPause : Ui()
    data object Prev : Ui()
    data object Next : Ui()
    data object Slow : Ui()
    data object More : Ui()
    data object XRay : Ui()
    data object Spin : Ui()
    data object Mute : Ui()
    data object OnMyArm : Ui()
    data object BackToTable : Ui()
    data object Realign : Ui()
    data object ManualAlign : Ui()
    data object AlignWait : Ui()
    data object AlignRetry : Ui()
    data object Aligned : Ui()
    data object NextWrap : Ui()
    data object Again : Ui()
    data object ToList : Ui()
    data class SetPrefs(val why: String, val f: (Prefs) -> Prefs) : Ui()
    data class Ask(val ask: SettingsAsk) : Ui()
    data object Recenter : Ui()
    data object QuitYes : Ui()
}
