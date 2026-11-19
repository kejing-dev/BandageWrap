package io.embodit.bandagewrap.domain

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design M3, M5, M8, M9, D9, D14, D35. */
class AlignAndLayoutTest {
    private fun head(yawDeg: Float = 0f, pitchDeg: Float = 0f, rollDeg: Float = 0f, eye: Float = 1.2f): HeadPose {
        val q = Quat4.axisAngle(Vec3.UP, Math.toRadians(yawDeg.toDouble()).toFloat()) *
            Quat4.axisAngle(Vec3.RIGHT, Math.toRadians(pitchDeg.toDouble()).toFloat()) *
            Quat4.axisAngle(Vec3(0f, 0f, 1f), Math.toRadians(rollDeg.toDouble()).toFloat())
        return HeadPose(Vec3(0.3f, eye, -0.2f), q)
    }

    @Test
    fun injectedHandLocksWithinTwoMillimetresAndOneDegree() {
        for (right in listOf(true, false)) for (yaw in listOf(0f, 1.2f, -2.4f)) {
            val wrist = Vec3(0.1f, 0.85f, -0.4f)
            val dir = Yaw.dir(yaw)
            val (pose, s) = Align.fromHand(Align.synthHand(right, wrist, dir, 1.07f))!!
            assertTrue((pose.wrist - wrist).length <= 0.002f)
            // forearm axis (wrist → elbow) is opposite the fingers
            assertTrue(Align.angleDeg(pose.towardElbow, -dir) <= 1f)
            assertEquals(1.07f, s, 0.01f)
            // palm down: the arm's dorsal (+Y) is up
            assertTrue(pose.rotation.rotate(Vec3.UP).y > 0.99f)
        }
    }

    @Test
    fun steadyLockNeedsOneStillSecond() {
        val lock = SteadyLock()
        var t = 0L
        var p = Vec3(0f, 1f, 0f)
        repeat(20) {
            p = p + Vec3(0.002f, 0f, 0f) // 2 mm per 50 ms = 4 cm/s: moving
            assertFalse(lock.step(t, p))
            t += 50
        }
        var locked = false
        repeat(30) {
            if (lock.step(t, p)) locked = true
            t += 50
        }
        assertTrue(locked)
    }

    @Test
    fun tableArmIsUprightBesideYouWhateverTheHeadTilt() {
        for (side in ArmSide.entries) for (pitch in listOf(-30f, 0f, 30f)) for (roll in listOf(0f, 20f)) {
            val arm = ArmModel(ArmParams(side = side))
            val h = head(yawDeg = 40f, pitchDeg = pitch, rollDeg = roll)
            val rig = ArmLayout.place(h, arm)
            val wrist = ArmLayout.toStage(rig, arm, Vec3.ZERO)
            // yaw only: the rig's up stays up (D9)
            assertEquals(1f, rig.rotation.rotate(Vec3.UP).y, 1e-4f)
            // on the arm's side, in front, at desk height
            val bearing = ArmLayout.bearingDeg(h, rig.position)
            if (side == ArmSide.LEFT) assertTrue("bearing $bearing", bearing > 5f) else assertTrue("bearing $bearing", bearing < -5f)
            assertEquals(h.position.y - ArmLayout.BELOW_M, rig.position.y, 1e-3f)
            // the hand points at you: the wrist is nearer than the pivot
            assertTrue(ArmLayout.flatDist(wrist, h.position) < ArmLayout.flatDist(rig.position, h.position))
            assertTrue(ArmLayout.flatDist(rig.position, h.position) >= ArmLayout.MIN_DIST_M)
        }
    }

    @Test
    fun dragIntoTheFaceIsPushedOut() {
        val h = head()
        val arm = ArmModel(ArmParams())
        val rig = ArmLayout.place(h, arm)
        val near = Upright(Vec3(h.position.x + 0.1f, rig.position.y, h.position.z - 0.15f), rig.yaw)
        val out = ArmLayout.pushOut(near, h.position)
        assertEquals(ArmLayout.MIN_DIST_M, ArmLayout.flatDist(out.position, h.position), 1e-3f)
        // four directions, three times each: released where let go (< 1 cm), never snapped
        var p = rig
        for (k in 0 until 12) {
            val d = listOf(Vec3(0.05f, 0f, 0f), Vec3(-0.05f, 0f, 0f), Vec3(0f, 0.05f, 0f), Vec3(0f, -0.05f, 0f))[k % 4]
            val target = Upright(p.position + d, p.yaw)
            val released = ArmLayout.pushOut(target, h.position)
            assertTrue((released.position - target.position).length < 0.01f)
            p = released
        }
    }

    @Test
    fun windowSightLinesNeverCrossTheArm() {
        val half = WindowGeometry.halfExtents(480f, 500f, 0.0008f)
        for (side in ArmSide.entries) for (l in listOf(0.20f, 0.30f)) for (g in listOf(Girth.THIN, Girth.THICK)) for (upright in listOf(false, true)) {
            val arm = ArmModel(ArmParams(forearmLen = l, girth = g, side = side, upright = upright))
            for (yaw in listOf(0f, 90f, -135f)) {
                val h = head(yawDeg = yaw)
                val rig = ArmLayout.place(h, arm)
                var u = arm.uMin
                while (u <= arm.uMax) {
                    for (k in 0 until 12) {
                        val p = ArmLayout.toStage(rig, arm, arm.point(u, (2 * PI * k / 12).toFloat()))
                        assertFalse("arm in the window's sight cone: side=$side L=$l g=$g upright=$upright u=$u", WindowGeometry.inSightCone(h, p, half))
                    }
                    u += 0.02f
                }
                // the turn label above the arm, too
                val top = arm.frame(arm.L / 2).c + Vec3(0f, 0.06f + arm.section(arm.L / 2).ry, 0f)
                assertFalse(WindowGeometry.inSightCone(h, ArmLayout.toStage(rig, arm, top), half))
            }
        }
    }

    @Test
    fun armStaysBelowTheEyesAndNotTooSteep() {
        for (upright in listOf(false, true)) {
            val arm = ArmModel(ArmParams(upright = upright))
            val h = head()
            val rig = ArmLayout.place(h, arm)
            val tips = ArmLayout.toStage(rig, arm, arm.point(arm.uMin, PI.toFloat() / 2))
            assertTrue("top of the hand below the eyes (upright=$upright): ${tips.y}", tips.y < h.position.y - 0.04f)
            val pivot = rig.position
            val down = ArmLayout.downDeg(h.position, pivot)
            assertTrue("pivot ${down}° below the eyes", down in 15f..45f)
        }
    }

    @Test
    fun labelFacesYouAboutTheVerticalOnly() {
        for (a in listOf(0f, 1f, 2.5f, -2f)) {
            val p = Vec3(0.2f * kotlin.math.cos(a), 0.7f, -0.5f + 0.2f * kotlin.math.sin(a))
            val f = FacePanel.facing(p, Vec3(0f, 1.2f, 0f), null, 0f)
            assertEquals(0f, f.pitch, 0f)
            // normal points at the viewer horizontally; up stays world up (never mirrored or upside down)
            val to = Vec3(-p.x, 0f, -p.z).normalized()
            assertTrue(f.normal.dot(to) > 0.999f)
            assertTrue(f.up.y > 0.999f)
            assertTrue(abs(f.right.y) < 1e-4f)
        }
    }
}
