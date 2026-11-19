package io.embodit.bandagewrap.domain

import kotlin.math.abs

/**
 * "看正前方" before a re-center (design S30, D29): the button is on the window, so the head is aimed at the window when
 * it is pressed; wait for the heading to hold still (< 2° for 300 ms) and the head to be level, then sample.
 */
class SteadyGate(val holdMs: Long = 300L, val maxDriftDeg: Float = 2f, val maxPitchDeg: Float = 10f) {
    private var since = -1L
    private var refYaw = 0f
    var waiting = false
        private set

    fun start() {
        waiting = true
        since = -1L
    }

    fun cancel() {
        waiting = false
    }

    /** Returns true once (and stops waiting) when the head has been steady and level for [holdMs]. */
    fun step(now: Long, yawRad: Float, pitchDeg: Float?): Boolean {
        if (!waiting) return false
        if (pitchDeg == null || abs(pitchDeg) > maxPitchDeg) {
            since = -1L
            return false
        }
        if (since < 0 || abs(Math.toDegrees(Yaw.wrap(yawRad - refYaw).toDouble())) > maxDriftDeg) {
            since = now
            refYaw = yawRad
            return false
        }
        if (now - since >= holdMs) {
            waiting = false
            return true
        }
        return false
    }
}
