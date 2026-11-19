package io.embodit.bandagewrap.domain

import kotlin.math.abs
import kotlin.math.acos

/** Hand joints in the SDK / OpenXR order (X-ray Hand HandRig). */
enum class Joint {
    PALM, WRIST,
    THUMB_METACARPAL, THUMB_PROXIMAL, THUMB_DISTAL, THUMB_TIP,
    INDEX_METACARPAL, INDEX_PROXIMAL, INDEX_INTERMEDIATE, INDEX_DISTAL, INDEX_TIP,
    MIDDLE_METACARPAL, MIDDLE_PROXIMAL, MIDDLE_INTERMEDIATE, MIDDLE_DISTAL, MIDDLE_TIP,
    RING_METACARPAL, RING_PROXIMAL, RING_INTERMEDIATE, RING_DISTAL, RING_TIP,
    LITTLE_METACARPAL, LITTLE_PROXIMAL, LITTLE_INTERMEDIATE, LITTLE_DISTAL, LITTLE_TIP,
}

/** One hand's joint positions (Stage metres) and how far it pinches. */
data class HandJoints(val right: Boolean, val at: Map<Joint, Vec3>, val pinch: Float = 0f) {
    operator fun get(j: Joint): Vec3? = at[j]
}

/** Where the arm sits in Stage space: wrist centre + rotation of the arm-local frame (ArmModel axes). */
data class ArmPose(val wrist: Vec3, val rotation: Quat4) {
    fun toStage(local: Vec3): Vec3 = wrist + rotation.rotate(local)

    /** Forearm axis in Stage space, pointing from the wrist to the elbow. */
    val towardElbow: Vec3
        get() = rotation.rotate(Vec3(0f, 0f, -1f))
}

/**
 * "放到我手臂上" (design armModel.alignFromTracking, T9): the arm frame from one hand's joints (X-ray Hand HandRig:
 * O = MIDDLE_METACARPAL, Y → MIDDLE_PROXIMAL, Z palmar from INDEX_MC − LITTLE_MC, hand size s = |MIDDLE_PROXIMAL −
 * MIDDLE_METACARPAL| / 0.0636), and the 1 s steady lock (WRIST moving < 1 cm/s). Pure, unit-tested with injected joints.
 */
object Align {
    const val HAND_REF_M = 0.0636f
    const val STEADY_MS = 1000L
    const val STEADY_SPEED = 0.01f

    /** Arm pose and hand scale from one hand, or null when joints are missing. */
    fun fromHand(h: HandJoints): Pair<ArmPose, Float>? {
        val wrist = h[Joint.WRIST] ?: return null
        val mcp = h[Joint.MIDDLE_METACARPAL] ?: return null
        val prox = h[Joint.MIDDLE_PROXIMAL] ?: return null
        val idx = h[Joint.INDEX_METACARPAL] ?: return null
        val lit = h[Joint.LITTLE_METACARPAL] ?: return null
        val distal = (prox - wrist).normalized()
        val across = idx - lit
        // palmar normal (right hand: across × distal points to the palm with the X-ray Hand convention h = +1)
        val sign = if (h.right) 1f else -1f
        val palmar = (across.cross(distal) * sign).normalized()
        val dorsal = (-palmar).rejectFrom(distal).normalized()
        val x = dorsal.cross(distal).normalized()
        val s = (prox - mcp).length / HAND_REF_M
        return ArmPose(wrist, Quat4.fromBasis(x, dorsal, distal)) to s
    }

    /** Injected joints for tests and debug (`pose x y z yaw`): a flat hand, palm down, fingers along [distal]. */
    fun synthHand(right: Boolean, wrist: Vec3, distal: Vec3, s: Float = 1f): HandJoints {
        val d = distal.normalized()
        val up = Vec3.UP
        // thumb side: right hand palm down → thumb on the left of the fingers' direction
        val left = up.cross(d).normalized()
        val thumbSide = if (right) left else -left
        val m = HashMap<Joint, Vec3>()
        m[Joint.WRIST] = wrist
        m[Joint.PALM] = wrist + d * (0.045f * s)
        m[Joint.MIDDLE_METACARPAL] = wrist + d * (0.03f * s)
        m[Joint.MIDDLE_PROXIMAL] = wrist + d * ((0.03f + HAND_REF_M) * s)
        m[Joint.INDEX_METACARPAL] = wrist + d * (0.03f * s) + thumbSide * (0.022f * s)
        m[Joint.LITTLE_METACARPAL] = wrist + d * (0.03f * s) - thumbSide * (0.022f * s)
        m[Joint.MIDDLE_TIP] = wrist + d * (0.18f * s)
        m[Joint.THUMB_TIP] = wrist + d * (0.08f * s) + thumbSide * (0.06f * s)
        return HandJoints(right, m)
    }

    fun angleDeg(a: Vec3, b: Vec3): Float = Math.toDegrees(acos(a.normalized().dot(b.normalized()).coerceIn(-1f, 1f).toDouble())).toFloat()
}

/** The 1 s steady lock (design T9: WRIST displacement < 1 cm/s for 1 s). */
class SteadyLock(private val holdMs: Long = Align.STEADY_MS, private val maxSpeed: Float = Align.STEADY_SPEED) {
    private var since = -1L
    private var last: Vec3? = null
    private var lastMs = 0L

    fun reset() {
        since = -1L
        last = null
    }

    /** True once the wrist has stayed still for [holdMs]. */
    fun step(now: Long, wrist: Vec3?): Boolean {
        if (wrist == null) {
            reset()
            return false
        }
        val l = last
        last = wrist
        if (l == null) {
            since = now
            lastMs = now
            return false
        }
        val dt = (now - lastMs).coerceAtLeast(1L) / 1000f
        lastMs = now
        val speed = (wrist - l).length / dt
        if (speed > maxSpeed && abs(speed) > 0f) {
            since = now
            return false
        }
        if (since < 0) since = now
        return now - since >= holdMs
    }
}
