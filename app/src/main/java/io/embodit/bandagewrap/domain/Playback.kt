package io.embodit.bandagewrap.domain

/**
 * The turn-by-turn player (design input.playback, T2–T4, T12): each turn takes [TURN_S] seconds (slow: twice that), the
 * roll stops where it is while paused, previous / next work in both states and keep the state, the last turn ends in
 * [finished]. Time comes from the wall clock in [step] (not frames: the system may drop the container to 10 fps, D55).
 * Pure: the scene steps it, the controller reads it, unit tests drive it.
 */
class Playback(val turns: Int) {
    var turn = 0
        private set

    /** Progress through [turn], 0..1. */
    var prog = 0f
        private set
    var playing = true
    var slow = false
    var finished = false
        private set

    /** Turns that ended since the last [takeEvents] call (for the "沙" sound and the turn-done flash). */
    private val ended = ArrayList<Int>()

    val turnSeconds: Float
        get() = if (slow) TURN_S * 2 else TURN_S

    /** Advances by [dtSec] of wall time. Returns true when the visible turn or progress changed. */
    fun step(dtSec: Float): Boolean {
        if (!playing || finished || turns <= 0) return false
        // a long gap (background) must not skip whole turns: at most half a second at once
        var left = dtSec.coerceIn(0f, MAX_STEP_S) / turnSeconds
        while (left > 0f && !finished) {
            val room = 1f - prog
            if (left < room) {
                prog += left
                left = 0f
            } else {
                left -= room
                ended += turn
                if (turn == turns - 1) {
                    prog = 1f
                    finished = true
                    playing = false
                } else {
                    turn++
                    prog = 0f
                }
            }
        }
        return true
    }

    /** 上一圈: past 15 % of this turn → back to its start; else to the start of the previous turn. */
    fun prev() {
        if (turns <= 0) return
        finished = false
        if (prog > PREV_RESTART || turn == 0) prog = 0f
        else {
            turn--
            prog = 0f
        }
    }

    /** 下一圈: this turn is laid in full, the next starts at 0; on the last turn the wrap is done. */
    fun next() {
        if (turns <= 0 || finished) return
        ended += turn
        if (turn >= turns - 1) {
            prog = 1f
            finished = true
            playing = false
        } else {
            turn++
            prog = 0f
        }
    }

    /** Jump (debug `turn n [prog]`, "回到桌上看" keeps the place). */
    fun seek(t: Int, p: Float = 0f) {
        if (turns <= 0) return
        turn = t.coerceIn(0, turns - 1)
        prog = p.coerceIn(0f, 1f)
        finished = false
    }

    fun restart() {
        turn = 0
        prog = 0f
        finished = false
        playing = true
        ended.clear()
    }

    /** Fills the whole wrap at once (debug `turn last`, S07 screenshots). */
    fun finishNow() {
        turn = turns - 1
        prog = 1f
        finished = true
        playing = false
    }

    fun takeEvents(): List<Int> {
        if (ended.isEmpty()) return emptyList()
        val out = ArrayList(ended)
        ended.clear()
        return out
    }

    companion object {
        const val TURN_S = 3f
        const val PREV_RESTART = 0.15f
        const val MAX_STEP_S = 0.5f
    }
}
