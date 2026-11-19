package io.embodit.bandagewrap.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Which arm is shown (design armModel.mirror, settings 看哪只手臂). */
enum class ArmSide { LEFT, RIGHT }

/** Wrist girth band (design armModel.segments.forearm: wrist radius 0.024 / 0.027 / 0.031 m). */
enum class Girth(val wristR: Float) { THIN(0.024f), MID(0.027f), THICK(0.031f) }

/**
 * Parameters of the arm (design armModel). [forearmLen] wrist → elbow, metres; [handScale] s (hand size, 1 = the design's
 * 0.090 m wrist → knuckles); [upright] = the elbow-lesson pose (forearm up, elbow 90°, L7) instead of the table pose.
 */
data class ArmParams(
    val forearmLen: Float = DEFAULT_FOREARM,
    val girth: Girth = Girth.MID,
    val handScale: Float = 1f,
    val side: ArmSide = ArmSide.LEFT,
    val upright: Boolean = false,
) {
    companion object {
        const val DEFAULT_FOREARM = 0.25f
    }
}

/**
 * The parametric limb (design armModel, entities3d.tableArm fallback / ghostArm), in ARM-LOCAL metres:
 * origin = centre of the wrist, +Z = distal (towards the fingertips), +Y = dorsal (back of the hand; up when the palm is
 * down), +X = Y × Z. Built for the RIGHT arm (thumb on +X); the left arm is the mirror x → −x ([mirror]).
 *
 * The limb is a spine with elliptic (superelliptic on the hand) cross-sections. Spine parameter u = metres from the wrist,
 * + towards the elbow: hand u ∈ [−[handLen], 0], forearm [0, L], elbow bend [L, L + R·β] (radius [BEND_R], angle β:
 * 25° up in the table pose, 90° in the elbow-lesson pose, bending towards the palm side), upper arm 0.12 m after that.
 * A point on the skin is (u, θ): θ = 0 on +X, π/2 on the dorsal top, −π/2 on the palm side. The thumb is a separate
 * short tapered cylinder (v along it, φ around it).
 *
 * The bandage geometry ([WrapBuilder]) is computed on this same surface, so the bandage always sits on the arm.
 */
class ArmModel(val p: ArmParams) {
    val L = p.forearmLen
    val s = p.handScale
    private val rw = p.girth.wristR
    private val re = rw + 0.013f
    val handLen = 0.17f * s
    val palmLen = 0.09f * s
    val bend = if (p.upright) (PI / 2).toFloat() else Math.toRadians(25.0).toFloat()
    val bendDir = if (p.upright) Vec3(0f, -1f, 0f) else Vec3(0f, 1f, 0f)
    val bendLen = BEND_R * bend
    val upperStart = L + bendLen
    val uMax = upperStart + UPPER_LEN
    val uMin = -handLen
    private val mirrorX = p.side == ArmSide.LEFT

    // ---------------------------------------------------------------------------------------------- spine

    /** Spine frame at u: centre, lateral X, dorsal Y, distal tangent T (unit, X = Y × T up to the bend rotation). */
    data class Frame(val c: Vec3, val x: Vec3, val y: Vec3, val t: Vec3)

    private val axisA = Vec3(0f, 0f, -1f).cross(bendDir).normalized()

    fun frame(u: Float): Frame {
        if (u <= L) return Frame(Vec3(0f, 0f, -u), Vec3.RIGHT, Vec3.UP, Vec3(0f, 0f, 1f))
        val phi = min(u - L, bendLen) / BEND_R
        val o = Vec3(0f, 0f, -L) + bendDir * BEND_R
        val q = Quat4.axisAngle(axisA, phi)
        var c = o - bendDir * (BEND_R * cos(phi)) + Vec3(0f, 0f, -1f) * (BEND_R * sin(phi))
        val prox = q.rotate(Vec3(0f, 0f, -1f))
        if (u > upperStart) c = c + prox * (u - upperStart)
        return Frame(c, q.rotate(Vec3.RIGHT), q.rotate(Vec3.UP), -prox)
    }

    // ---------------------------------------------------------------------------------------------- cross-section

    /** Half-widths (rx along X, ry along Y) and the superellipse exponent at u. */
    data class Section(val rx: Float, val ry: Float, val n: Float)

    fun section(u: Float): Section {
        val rx0 = rw * 1.25f
        val ry0 = rw * 0.75f
        return when {
            u >= upperStart -> {
                val t = ((u - upperStart) / UPPER_LEN).coerceIn(0f, 1f)
                val r = 0.042f + 0.006f * t
                Section(r * 1.02f, r * 0.98f, 2f)
            }
            u >= L -> {
                val t = ((u - L) / max(bendLen, 1e-4f)).coerceIn(0f, 1f)
                val r = re + (0.042f - re) * t
                Section(r * 1.06f, r * 0.96f, 2f)
            }
            u >= 0f -> {
                val t = smooth(u / L)
                val r = rw + (re - rw) * t
                Section(r * (1.25f + (1.07f - 1.25f) * t), r * (0.75f + (0.92f - 0.75f) * t), 2f)
            }
            u >= -palmLen -> {
                val t = smooth(-u / palmLen)
                Section(lerp(rx0, 0.0425f * s, t), lerp(ry0, 0.0155f * s, t), lerp(2f, 3.2f, t))
            }
            else -> {
                val d = -u - palmLen
                val fl = handLen - palmLen
                val t = (d / fl).coerceIn(0f, 1f)
                var rx = lerp(0.0405f * s, 0.032f * s, t)
                var ry = lerp(0.0115f * s, 0.0080f * s, t)
                val tipZone = 0.024f * s
                val left = handLen - d
                if (left < tipZone) {
                    val k = sqrt(max(0f, 1f - ((tipZone - left) / tipZone).pow(2)))
                    rx *= max(0.25f, k)
                    ry *= max(0.2f, k)
                }
                Section(rx, ry, 3.4f)
            }
        }
    }

    private fun se(v: Float, n: Float): Float = (if (v < 0) -1f else 1f) * abs(v).pow(2f / n)

    // ---------------------------------------------------------------------------------------------- skin points

    /** Unmirrored skin point at (u, θ), [off] metres out along the normal. */
    private fun rawPoint(u: Float, theta: Float, off: Float): Vec3 {
        val f = frame(u)
        val sec = section(u)
        val c = cos(theta)
        val sn = sin(theta)
        val ex = se(c, sec.n) * sec.rx
        var ey = se(sn, sec.n) * sec.ry
        if (u < -palmLen) {
            // four fingers: shallow grooves between them (x = −½, 0, +½ of the width), fading in past the knuckles
            val fade = ((-u - palmLen) / (0.015f * s)).coerceIn(0f, 1f)
            val g = cos(4 * PI.toFloat() * (ex / sec.rx)).coerceAtLeast(0f)
            ey *= 1f - 0.22f * fade * g * g * g * g
        }
        val n = rawNormal(u, theta, f, sec)
        return f.c + f.x * ex + f.y * ey + n * off
    }

    private fun rawNormal(u: Float, theta: Float, f: Frame, sec: Section): Vec3 {
        // normal of the superellipse in the section plane (gradient of |x/rx|^n + |y/ry|^n)
        val c = cos(theta)
        val sn = sin(theta)
        val ex = se(c, sec.n)
        val ey = se(sn, sec.n)
        val gx = (if (ex < 0) -1f else 1f) * abs(ex).pow(sec.n - 1) / sec.rx
        val gy = (if (ey < 0) -1f else 1f) * abs(ey).pow(sec.n - 1) / sec.ry
        val v = f.x * gx + f.y * gy
        return if (v.length < 1e-6f) f.y else v.normalized()
    }

    private fun mirror(v: Vec3): Vec3 = if (mirrorX) Vec3(-v.x, v.y, v.z) else v

    /** Skin point (mirrored for the left arm). θ is in the right-arm convention: mirror x keeps it meaningful. */
    fun point(u: Float, theta: Float, off: Float = 0f): Vec3 = mirror(rawPoint(u.coerceIn(uMin, uMax), theta, off))

    fun normal(u: Float, theta: Float): Vec3 {
        val uu = u.coerceIn(uMin, uMax)
        return mirror(rawNormal(uu, theta, frame(uu), section(uu)))
    }

    // ---------------------------------------------------------------------------------------------- thumb

    private val thumbBaseU = -0.022f * s
    val thumbLen = 0.060f * s
    private val thumbR0 = 0.0125f * s
    private val thumbR1 = 0.0098f * s

    /** Unmirrored thumb axis: base (inside the hand, on the thumb side) and unit direction (40° abduction, a little down). */
    private val thumbBase: Vec3 by lazy {
        val sec = section(thumbBaseU)
        Vec3(sec.rx * 0.62f, -sec.ry * 0.35f, -thumbBaseU)
    }
    private val thumbDir: Vec3 by lazy {
        val ab = Math.toRadians(THUMB_ABDUCT_DEG).toFloat()
        Vec3(sin(ab), -0.32f, cos(ab)).normalized()
    }
    private val thumbX: Vec3 by lazy { Vec3.UP.cross(thumbDir).normalized().let { if (it.x < 0) it else it } }
    private val thumbY: Vec3 by lazy { thumbDir.cross(thumbX).normalized() }

    fun thumbRadius(v: Float): Float {
        val t = (v / thumbLen).coerceIn(0f, 1f)
        var r = lerp(thumbR0, thumbR1, t)
        val tip = 0.011f * s
        val left = thumbLen - v
        if (left < tip) r *= max(0.25f, sqrt(max(0f, 1f - ((tip - left) / tip).pow(2))))
        return r
    }

    /** Point on the thumb at v (from its base along its axis) and φ (0 = dorsal side, + round towards the palm). */
    fun thumbPoint(v: Float, phi: Float, off: Float = 0f): Vec3 {
        val vv = v.coerceIn(0f, thumbLen)
        val r = thumbRadius(vv) + off
        val dirOut = thumbY * cos(phi) + thumbX * sin(phi)
        return mirror(thumbBase + thumbDir * vv + dirOut * r)
    }

    fun thumbNormal(phi: Float): Vec3 = mirror((thumbY * cos(phi) + thumbX * sin(phi)).normalized())

    /** Thumb tip (arm-local, mirrored): the cap of the thumb mesh. */
    fun thumbAxis(): Pair<Vec3, Vec3> = mirror(thumbBase) to mirror(thumbDir)

    /** The thumb's root as a skin point of the hand: where a bandage leaves the hand surface for the thumb. */
    val thumbRootU: Float
        get() = thumbBaseU

    // ---------------------------------------------------------------------------------------------- projection

    /** Spine samples for projecting a point back to (u, θ). */
    private val spineU: FloatArray by lazy { FloatArray(((uMax - uMin) / 0.002f).toInt() + 1) { uMin + it * 0.002f } }
    private val spineC: Array<Vec3> by lazy { Array(spineU.size) { frame(spineU[it]).c } }

    /** (u, θ) of the skin point nearest to [pArm] (arm-local, mirrored space) — used to keep the bandage on the skin. */
    fun project(pArm: Vec3, hintU: Float? = null): Pair<Float, Float> {
        val p = mirror(pArm)
        var best = Float.MAX_VALUE
        var bu = 0f
        val lo = if (hintU == null) 0 else max(0, ((hintU - 0.06f - uMin) / 0.002f).toInt())
        val hi = if (hintU == null) spineU.size - 1 else min(spineU.size - 1, ((hintU + 0.06f - uMin) / 0.002f).toInt())
        for (i in lo..hi) {
            val f = frame(spineU[i])
            val d = p - spineC[i]
            // distance along the tangent (we want the section plane through p) plus a small radial term
            val along = abs(d.dot(f.t))
            if (along < best) {
                best = along
                bu = spineU[i]
            }
        }
        val f = frame(bu)
        val sec = section(bu)
        val d = p - f.c
        val th = atan2(d.dot(f.y) / sec.ry, d.dot(f.x) / sec.rx)
        return bu to th
    }

    /** (v, φ) on the thumb nearest to [pArm]. */
    fun projectThumb(pArm: Vec3): Pair<Float, Float> {
        val p = mirror(pArm)
        val d = p - thumbBase
        val v = d.dot(thumbDir).coerceIn(0f, thumbLen)
        val r = d - thumbDir * d.dot(thumbDir)
        val phi = atan2(r.dot(thumbX), r.dot(thumbY))
        return v to phi
    }

    // ---------------------------------------------------------------------------------------------- bounds

    /** Arm-local axis-aligned bounds of the whole limb (collider, D35 box, centring). */
    fun bounds(): Pair<Vec3, Vec3> {
        var mn = Vec3(1e9f, 1e9f, 1e9f)
        var mx = Vec3(-1e9f, -1e9f, -1e9f)
        var u = uMin
        while (u <= uMax) {
            for (k in 0 until 16) {
                val q = point(u, (2 * PI * k / 16).toFloat())
                mn = Vec3(min(mn.x, q.x), min(mn.y, q.y), min(mn.z, q.z))
                mx = Vec3(max(mx.x, q.x), max(mx.y, q.y), max(mx.z, q.z))
            }
            u += 0.01f
        }
        val (b, d) = thumbAxis()
        val tip = b + d * thumbLen
        mn = Vec3(min(mn.x, tip.x), min(mn.y, tip.y), min(mn.z, tip.z))
        mx = Vec3(max(mx.x, tip.x), max(mx.y, tip.y), max(mx.z, tip.z))
        return mn to mx
    }

    /** Mid-forearm point: the arm's pivot for dragging and turning. */
    fun pivot(): Vec3 = mirror(frame(L * 0.5f).c)

    companion object {
        const val BEND_R = 0.055f
        const val UPPER_LEN = 0.12f
        const val THUMB_ABDUCT_DEG = 40.0

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

        fun smooth(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3 - 2 * x)
        }
    }
}
