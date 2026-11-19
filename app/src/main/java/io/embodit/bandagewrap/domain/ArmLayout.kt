package io.embodit.bandagewrap.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Where the table arm goes (design spatialLayout.tableArm, D9 / D14 / D25): from the head pose at the moment the window
 * opens (yaw only), on the arm's side, at desk height; the hand towards you, the elbow away. The rig is an [Upright] at
 * the arm's pivot (mid forearm) whose +Z faces you; [poseRotation] turns arm-local axes into rig axes.
 */
object ArmLayout {
    /** Pivot (mid forearm) 0.62 m ahead, so the wrist is ~0.50 m and the fingertips ~0.33 m from the head (horizontal). */
    const val AHEAD_M = 0.62f
    const val SIDE_M = 0.20f
    const val BELOW_M = 0.30f

    /** Extra turn of the table arm across your front (deg): the forearm reads as a forearm, not end-on. */
    const val ACROSS_DEG = 25f

    /** Elbow lesson (L7): the elbow 0.65 m ahead, 0.46 m below the eyes, forearm up and leaning 35° towards you (hand top ≥ 5 cm below the eyes). */
    const val ELBOW_AHEAD_M = 0.65f
    const val ELBOW_BELOW_M = 0.46f
    const val UPRIGHT_LEAN_DEG = 35f

    /** D14 for the table arm: not closer than 0.45 m (horizontal) to the head (design spatialLayout.clearance). */
    const val MIN_DIST_M = 0.45f

    /** Arm-local → rig rotation: table pose = identity (distal +Z towards you, dorsal up); elbow pose = forearm up. */
    fun poseRotation(upright: Boolean): Quat4 {
        if (!upright) return Quat4.IDENTITY
        val a = Math.toRadians(UPRIGHT_LEAN_DEG.toDouble()).toFloat()
        val z = Vec3(0f, cos(a), sin(a))
        val y = Vec3(0f, -sin(a), cos(a))
        val x = y.cross(z).normalized()
        return Quat4.fromBasis(x, y, z)
    }

    /** Side of the arm in the head's frame: the left arm sits to the left (−), the right arm to the right. */
    fun sideSign(side: ArmSide) = if (side == ArmSide.LEFT) -1f else 1f

    /** The rig for the current head (first open, 窗口移到面前 / re-place, switching the arm side or the pose). */
    fun place(head: HeadPose, arm: ArmModel): Upright {
        val yaw = head.yaw(0f)
        val fwd = Yaw.dir(yaw)
        val right = Vec3(-fwd.z, 0f, fwd.x) // fwd × up, the head's right on the floor plane
        val side = sideSign(arm.p.side)
        if (!arm.p.upright) {
            val p = head.position + fwd * AHEAD_M + right * (side * SIDE_M) - Vec3(0f, BELOW_M, 0f)
            // hand towards you and a little across, so the forearm is seen at an angle, not end-on
            val f = facing(p, head.position)
            return Upright(p, Yaw.wrap(f.yaw - side * Math.toRadians(ACROSS_DEG.toDouble()).toFloat()))
        }
        val target = head.position + fwd * ELBOW_AHEAD_M + right * (side * SIDE_M) - Vec3(0f, ELBOW_BELOW_M, 0f)
        val face = facing(target, head.position)
        val elbowLocal = poseRotation(true).rotate(arm.frame(arm.L + arm.bendLen / 2).c.let { mirrorFor(arm, it) } - arm.pivot())
        return Upright(target - face.rotation.rotate(elbowLocal), face.yaw)
    }

    private fun mirrorFor(arm: ArmModel, v: Vec3) = if (arm.p.side == ArmSide.LEFT) Vec3(-v.x, v.y, v.z) else v

    /** An upright pose at [p] whose +Z points at [viewer] (horizontally). */
    fun facing(p: Vec3, viewer: Vec3): Upright {
        val to = Vec3(viewer.x - p.x, 0f, viewer.z - p.z)
        if (to.length < 1e-4f) return Upright(p, 0f)
        // rig +Z = to ⇒ rig −Z = −to ⇒ yaw = Yaw.of(−to)
        return Upright(p, Yaw.of(-to))
    }

    /** Arm-local → Stage for a rig. */
    fun toStage(rig: Upright, arm: ArmModel, local: Vec3): Vec3 = rig.position + rig.rotation.rotate(poseRotation(arm.p.upright).rotate(local - arm.pivot()))

    fun rotationToStage(rig: Upright, arm: ArmModel): Quat4 = rig.rotation * poseRotation(arm.p.upright)

    /** After a drag: not closer than [MIN_DIST_M] to the head (pushed straight out), height kept, yaw kept (D1). */
    fun pushOut(rig: Upright, head: Vec3): Upright {
        val flat = Vec3(rig.position.x - head.x, 0f, rig.position.z - head.z)
        val d = flat.length
        if (d >= MIN_DIST_M || d < 1e-4f) return rig
        val p = Vec3(head.x, rig.position.y, head.z) + flat.normalized() * MIN_DIST_M
        return Upright(p, rig.yaw)
    }

    /** Horizontal distance and bearing (deg, + = left) of a point from the head. */
    fun bearingDeg(head: HeadPose, p: Vec3): Float {
        val yaw = Yaw.of(Vec3(p.x - head.position.x, 0f, p.z - head.position.z))
        return Math.toDegrees(Yaw.wrap(yaw - head.yaw(0f)).toDouble()).toFloat()
    }

    fun flatDist(a: Vec3, b: Vec3) = hypot(a.x - b.x, a.z - b.z)

    /** Below-eye angle (deg) of a point. */
    fun downDeg(head: Vec3, p: Vec3): Float = Math.toDegrees(atan2((head.y - p.y).toDouble(), hypot((p.x - head.x).toDouble(), (p.z - head.z).toDouble()))).toFloat()
}

/**
 * D35 / panelGeometryCheck: the main window (where the system opens it: straight ahead, [WINDOW_DIST_M], centred on the
 * line of sight at a level head) must not be crossed by the arm. Tests sample the arm's skin and check no point lies
 * inside the eye → window pyramid in front of the window.
 */
object WindowGeometry {
    const val WINDOW_DIST_M = 1.75f

    /** Window half extents in metres for its dp size at [dpM] metres per dp. */
    fun halfExtents(wDp: Float, hDp: Float, dpM: Float) = Vec3(wDp * dpM / 2, hDp * dpM / 2, 0f)

    /** True when [p] lies inside the pyramid from the eye to the window (level head looking along −Z). */
    fun inSightCone(head: HeadPose, p: Vec3, half: Vec3, distM: Float = WINDOW_DIST_M): Boolean {
        val yaw = head.yaw(0f)
        val q = Quat4.axisAngle(Vec3.UP, yaw).conjugate()
        val r = q.rotate(p - head.position) // head-yaw frame: −Z ahead
        val depth = -r.z
        if (depth <= 0.05f || depth >= distM) return false
        val k = depth / distM
        return kotlin.math.abs(r.x) <= half.x * k && kotlin.math.abs(r.y) <= half.y * k
    }
}
