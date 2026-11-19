package io.embodit.bandagewrap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design acceptance M2 and D55 (playback runs on wall time, not frames). */
class PlaybackTest {
    private fun run(p: Playback, seconds: Float, fps: Int) {
        val dt = 1f / fps
        var t = 0f
        while (t < seconds - 1e-4f) {
            p.step(dt)
            t += dt
        }
    }

    @Test
    fun aTurnTakesThreeSecondsAndSixSlow() {
        val p = Playback(9)
        run(p, 2.9f, 90)
        assertEquals(0, p.turn)
        run(p, 0.2f, 90)
        assertEquals(1, p.turn)
        val s = Playback(9).also { it.slow = true }
        run(s, 5.9f, 90)
        assertEquals(0, s.turn)
        run(s, 0.2f, 90)
        assertEquals(1, s.turn)
    }

    @Test
    fun tenFpsAndNinetyFpsAgree() {
        val a = Playback(9)
        val b = Playback(9)
        run(a, 10f, 10)
        run(b, 10f, 90)
        assertEquals(a.turn, b.turn)
        assertEquals(a.prog, b.prog, 0.01f)
    }

    @Test
    fun pausedDoesNotMove() {
        val p = Playback(9)
        run(p, 1f, 30)
        p.playing = false
        val before = p.prog
        run(p, 5f, 30)
        assertEquals(before, p.prog, 0f)
    }

    @Test
    fun prevAndNextKeepThePlayState() {
        val p = Playback(9)
        run(p, 4f, 30) // turn 2, 33 %
        p.playing = false
        p.prev()
        assertEquals(1, p.turn) // past 15 % → back to this turn's start
        assertEquals(0f, p.prog, 0f)
        p.prev()
        assertEquals(0, p.turn)
        assertFalse(p.playing)
        p.next()
        assertEquals(1, p.turn)
        assertFalse(p.playing)
        p.playing = true
        p.next()
        assertTrue(p.playing)
        assertEquals(2, p.turn)
    }

    @Test
    fun lastTurnFinishes() {
        val p = Playback(4)
        run(p, 12.2f, 30)
        assertTrue(p.finished)
        assertFalse(p.playing)
        assertEquals(3, p.turn)
        val q = Playback(2)
        q.next()
        q.next()
        assertTrue(q.finished)
    }

    @Test
    fun aLongGapDoesNotSkipTurns() {
        val p = Playback(9)
        p.step(30f)
        assertEquals(0, p.turn)
    }
}
