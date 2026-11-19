package io.embodit.bandagewrap.domain

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design acceptance M1 / M7: turn counts, overlap, crossing side, bandage on the skin, left = mirror of right. */
class WrapGeometryTest {
    private fun arm(side: ArmSide = ArmSide.RIGHT, upright: Boolean = false, l: Float = 0.25f, g: Girth = Girth.MID) = ArmModel(ArmParams(forearmLen = l, girth = g, side = side, upright = upright))

    @Test
    fun turnCountsMatchTheDesign() {
        val want = mapOf("L1" to 4, "L2" to 9, "L3" to 9, "L4" to 10, "L5" to 18, "L6" to 12, "L7" to 10)
        for (w in Wraps.all) {
            val n = WrapBuilder.turnCount(w, arm(upright = w.id == "L7"))
            assertEquals("turns of ${w.id}", want[w.id], n)
            assertEquals(n, WrapBuilder.build(w, arm(upright = w.id == "L7")).size)
        }
    }

    @Test
    fun shorterForearmGivesFewerTurns() {
        assertTrue(WrapBuilder.turnCount(Wraps.byId("L2")!!, arm(l = 0.20f)) < 9)
    }

    @Test
    fun spiralTurnsOverlapTwoThirds() {
        val w = Wraps.byId("L2")!!
        val t = WrapBuilder.build(w, arm())
        val spirals = t.filter { it.kind == TurnKind.SPIRAL }
        for (i in 0 until spirals.size - 1) {
            // same θ on two consecutive spiral turns: u differs by the pitch, so they overlap by w − pitch
            val a = spirals[i].path
            val b = spirals[i + 1].path
            val du = abs(b[b.size / 2].a - a[a.size / 2].a)
            val overlap = w.width - du
            assertEquals("overlap of turn ${i + 3}", w.width * 2 / 3, overlap, 0.002f)
        }
    }

    @Test
    fun figureEightsCrossOnTheDesignedSide() {
        // L4 / L5 / L6 cross on the back of the hand (θ = π/2), L7 in the bend of the elbow (θ = −π/2)
        for ((id, kind, want) in listOf(Triple("L4", TurnKind.EIGHT_OUT, PI / 2), Triple("L5", TurnKind.EIGHT_OUT, PI / 2), Triple("L7", TurnKind.ELBOW_UP, -PI / 2))) {
            val w = Wraps.byId(id)!!
            val t = WrapBuilder.build(w, arm(upright = id == "L7"))
            val th = WrapBuilder.crossingTheta(t, kind)!!
            var d = th - want
            while (d > PI) d -= 2 * PI
            while (d < -PI) d += 2 * PI
            assertTrue("$id crosses at θ=$th, want $want", abs(d) < 0.5)
        }
    }

    @Test
    fun bandageSitsOnTheSkin() {
        for (w in Wraps.all) {
            val a = arm(upright = w.id == "L7")
            for (t in WrapBuilder.build(w, a)) {
                for (i in 0 until t.samples) {
                    val c = t.centres[i]
                    val p = t.path[i]
                    val skin = if (p.thumb) a.thumbPoint(p.a, p.b) else a.point(p.a, p.b)
                    val d = (c - skin).length
                    assertTrue("${w.id} turn ${t.index + 1} row $i off the skin by $d", d >= WrapBuilder.BASE_OFF - 1e-4f && d <= WrapBuilder.lift(WrapBuilder.LAYER_CAP, t.index) + 1e-3f)
                }
            }
        }
    }

    @Test
    fun laterTurnsLieOverEarlierOnes() {
        val t = WrapBuilder.build(Wraps.byId("L2")!!, arm())
        assertTrue(t.last().maxLayer >= 2)
        assertEquals(1, t.first().maxLayer)
    }

    @Test
    fun leftArmIsTheMirrorOfTheRight() {
        for (w in Wraps.all) {
            val r = WrapBuilder.build(w, arm(ArmSide.RIGHT, w.id == "L7"))
            val l = WrapBuilder.build(w, arm(ArmSide.LEFT, w.id == "L7"))
            assertEquals(r.size, l.size)
            for (k in r.indices) for (i in 0 until r[k].samples step 7) {
                val a = r[k].centres[i]
                val b = l[k].centres[i]
                assertEquals("${w.id} turn $k row $i x", -a.x, b.x, 1e-4f)
                assertEquals(a.y, b.y, 1e-4f)
                assertEquals(a.z, b.z, 1e-4f)
            }
        }
    }

    @Test
    fun armHasElbowAndUpperArm() {
        val a = arm()
        val (mn, mx) = a.bounds()
        // hand (0.17) + forearm (0.25) + bend + 0.12 m upper arm: longer than 0.5 m along the arm
        assertTrue(mx.z - mn.z > 0.45f)
        val up = arm(upright = true)
        val elbow = up.frame(up.L + up.bendLen).c
        val upper = up.frame(up.uMax).c
        // elbow lesson: the upper arm leaves at 90° to the forearm
        val dir = (upper - elbow).normalized()
        assertEquals(0f, dir.z, 0.02f)
    }

    @Test
    fun copyHasNoTreatmentWords() {
        for (w in Wraps.all) {
            val texts = listOf(w.scene, w.tagline, w.why) + TurnKind.entries.map { Wraps.copy(w, it, 0, 9) }.flatMap { listOf(it.cue, it.label, it.why) }
            for (t in texts) for (b in Wraps.banned) {
                assertTrue("'$b' in ${t.zh}", !t.zh.contains(b))
            }
            for (t in TurnKind.entries.map { Wraps.copy(w, it, 0, 9) }) {
                assertTrue("label too long: ${t.label.zh}", t.label.zh.length <= 8)
                assertTrue("label too long: ${t.label.en}", t.label.en.length <= 18)
            }
        }
    }
}
