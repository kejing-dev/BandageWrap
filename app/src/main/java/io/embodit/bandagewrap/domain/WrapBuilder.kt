package io.embodit.bandagewrap.domain

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A point on the skin: on the limb (a = u, b = θ) or on the thumb (a = v, b = φ). */
data class SkinPt(val thumb: Boolean, val a: Float, val b: Float)

/** One turn's path (design: 圈 = the bandage once round the limb; an 8 = 2 turns) and its strip, arm-local metres. */
class TurnGeom(
    val index: Int,
    val kind: TurnKind,
    val width: Float,
    /** Centre line on the skin (not lifted), for the next-turn line and coverage tests. */
    val path: List<SkinPt>,
    /** Strip rows: [COLS] vertices across the bandage per path sample, already lifted by their layer. */
    val rows: List<Array<Vec3>>,
    val normals: List<Array<Vec3>>,
    /** Centre of each row (lifted) and its outward normal: where the roll rides. */
    val centres: List<Vec3>,
    val centreNormals: List<Vec3>,
    /** Across direction at each row (the roll's axis). */
    val across: List<Vec3>,
    /** Arc length along the centre up to each row (texture u). */
    val arc: FloatArray,
    /** Spine u of each row (the 3D label rides along the limb at this u). */
    val spineU: FloatArray,
    /** Highest layer under this turn (1 = straight on the skin). */
    val maxLayer: Int,
) {
    val samples: Int
        get() = rows.size
}

/**
 * Builds the turns of a wrap on an [ArmModel] (design armModel.turnCount / skinOffset / overlap). Pure, unit-tested.
 * Winding: the roll goes over the top from the inner (little-finger) side to the outer (thumb) side: θ decreasing.
 */
object WrapBuilder {
    const val COLS = 4
    const val SAMPLES = 72
    const val BASE_OFF = 0.003f
    const val LAYER_OFF = 0.0009f

    /** Layers stop stacking after this many (a real crepe layer is ~0.5 mm; the wrist of a hand 8 gets 10+ passes). */
    const val LAYER_CAP = 7

    /** Per-turn lift so a later turn always draws over an earlier one at the same layer count (no z-fighting). */
    const val TURN_EPS = 0.00008f

    fun lift(layers: Int, turn: Int): Float = BASE_OFF + LAYER_OFF * kotlin.math.min(layers, LAYER_CAP) + TURN_EPS * turn
    private val PIf = PI.toFloat()

    /** Turn counts with the design's default arm (L1 4 / L2 9 / L3 9 / L4 10 / L5 18 / L6 12 / L7 10). */
    fun turnCount(w: Wrap, arm: ArmModel): Int = plan(w, arm).size

    /** pitch = w / 3 (each turn covers two thirds of the last, design armModel.overlap). */
    fun pitch(width: Float) = width / 3f

    // ---------------------------------------------------------------------------------------------- plans

    /** A turn as keyframes in (θ, u) on the limb, or a thumb loop. */
    private class Plan(val kind: TurnKind, val parts: List<Part>)

    private sealed class Part {
        /** θ from [t0] to [t1] while u goes from [u0] to [u1] (linear), optionally kinked at [kneeT]/[kneeU]. */
        class Limb(val t0: Float, val t1: Float, val u0: Float, val u1: Float, val kneeT: Float? = null, val kneeU: Float? = null) : Part()

        class Thumb(val v: Float, val p0: Float, val p1: Float) : Part()
    }

    private fun flat(u: Float, t0: Float = PIf, t1: Float = -PIf) = Part.Limb(t0, t1, u, u)

    private fun plan(w: Wrap, arm: ArmModel): List<Plan> {
        val p = pitch(w.width)
        val out = ArrayList<Plan>()
        when (w.id) {
            "L1" -> {
                val u0 = 0.005f + w.width / 2
                out += Plan(TurnKind.ANCHOR, listOf(flat(u0)))
                out += Plan(TurnKind.ANCHOR, listOf(flat(u0)))
                out += Plan(TurnKind.FINISH, listOf(Part.Limb(PIf, -PIf, u0, u0 + p)))
                out += Plan(TurnKind.FINISH, listOf(flat(u0 + p)))
            }
            "L2", "L3" -> {
                val uA = 0.03f + w.width / 2
                val usable = arm.L - 0.03f - 0.02f
                val n = ceil(((usable - w.width) / p).toDouble() - 1e-6).toInt().coerceAtLeast(1)
                out += Plan(TurnKind.ANCHOR, listOf(flat(uA)))
                out += Plan(TurnKind.ANCHOR, listOf(flat(uA)))
                for (k in 0 until n) {
                    val a = uA + k * p
                    val b = a + p
                    out +=
                        if (w.id == "L2") Plan(TurnKind.SPIRAL, listOf(Part.Limb(PIf, -PIf, a, b)))
                        // reverse spiral: steep up over the top, folded at the outer side (θ = 0), down a little underneath
                        else Plan(TurnKind.REVERSE, listOf(Part.Limb(PIf, -PIf, a, b, kneeT = 0f, kneeU = a + 1.5f * p)))
                }
                val uE = uA + n * p
                out += Plan(TurnKind.FINISH, listOf(flat(uE)))
                out += Plan(TurnKind.FINISH, listOf(flat(uE)))
            }
            "L4", "L5" -> {
                val uW = 0.005f + w.width / 2
                val eights: Int
                val hand: (Int) -> Float
                val wrist: (Int) -> Float
                if (w.id == "L4") {
                    eights = 3
                    hand = { k -> -0.045f * arm.s - 0.010f * k }
                    wrist = { k -> uW + 0.006f * k }
                } else {
                    val cover = 0.155f * arm.s
                    eights = ceil(((cover - w.width) / p).toDouble() - 1e-6).toInt().coerceAtLeast(1)
                    val first = -0.06f * arm.s
                    val lastU = -(arm.handLen - 0.015f - w.width / 2)
                    hand = { k -> first + (lastU - first) * k / max(1, eights - 1) }
                    wrist = { k -> uW + 0.004f * k }
                }
                out += Plan(TurnKind.ANCHOR, listOf(flat(uW)))
                out += Plan(TurnKind.ANCHOR, listOf(flat(uW)))
                for (k in 0 until eights) {
                    val uh = hand(k)
                    val uw0 = wrist(k)
                    val uw1 = wrist(k + 1)
                    // out: over the back from the wrist to the hand, then round the palm side; back: over the back to the
                    // wrist (the two diagonals cross on the back of the hand), then round the wrist underneath
                    out += Plan(TurnKind.EIGHT_OUT, listOf(Part.Limb(PIf, 0f, uw0, uh), Part.Limb(0f, -PIf, uh, uh)))
                    out += Plan(TurnKind.EIGHT_BACK, listOf(Part.Limb(PIf, 0f, uh, uw1), Part.Limb(0f, -PIf, uw1, uw1)))
                }
                val uE = wrist(eights) + p * 0.5f
                out += Plan(TurnKind.FINISH, listOf(Part.Limb(PIf, -PIf, wrist(eights), uE)))
                out += Plan(TurnKind.FINISH, listOf(flat(uE)))
            }
            "L6" -> {
                val uW = 0.005f + 0.05f / 2
                val uR = arm.thumbRootU
                out += Plan(TurnKind.ANCHOR, listOf(flat(uW)))
                out += Plan(TurnKind.ANCHOR, listOf(flat(uW)))
                val loops = 4
                for (k in 0 until loops) {
                    // each loop round the thumb climbs towards its root, two thirds over the last
                    val v = arm.thumbLen * 0.55f - k * pitch(w.width) * 0.7f
                    val uw0 = uW + 0.004f * k
                    val uw1 = uW + 0.004f * (k + 1)
                    out += Plan(TurnKind.SPICA_OUT, listOf(Part.Limb(PIf, 0.15f, uw0, uR), Part.Thumb(v, 0f, 2 * PIf)))
                    out += Plan(TurnKind.SPICA_BACK, listOf(Part.Limb(0f, -PIf, uR, uR), Part.Limb(PIf, 0f, uR, uw1), Part.Limb(0f, -PIf, uw1, uw1)))
                }
                val uE = uW + 0.004f * loops + p * 0.5f
                out += Plan(TurnKind.FINISH, listOf(Part.Limb(PIf, -PIf, uW + 0.004f * loops, uE)))
                out += Plan(TurnKind.FINISH, listOf(flat(uE)))
            }
            "L7" -> {
                val uE = arm.L + arm.bendLen / 2
                val reach = 0.10f
                val n = ceil(((reach - w.width / 2) / p).toDouble() - 1e-6).toInt().coerceAtLeast(1)
                val d = { k: Int -> 0.035f + (reach - 0.035f) * k / max(1, n - 1) * 0.85f }
                // 1.5 turns over the point of the elbow (counted as 2)
                out += Plan(TurnKind.ELBOW_POINT, listOf(flat(uE)))
                out += Plan(TurnKind.ELBOW_POINT, listOf(Part.Limb(PIf, -PIf, uE, uE + d(0) * 0.5f)))
                // then 8s: flat over the outside (θ π → 0 through the point), diagonal across the bend (θ 0 → −π through
                // the crease, −π/2): the diagonals cross in the bend of the elbow
                for (k in 0 until n) {
                    val up = uE + d(k)
                    val dn = uE - d(k)
                    val next = uE + d(min(k + 1, n - 1)) + if (k == n - 1) 0.01f else 0f
                    out += Plan(TurnKind.ELBOW_UP, listOf(Part.Limb(PIf, 0f, if (k == 0) uE + d(0) * 0.5f else up, up), Part.Limb(0f, -PIf, up, dn)))
                    out += Plan(TurnKind.ELBOW_DOWN, listOf(Part.Limb(PIf, 0f, dn, dn), Part.Limb(0f, -PIf, dn, if (k == n - 1) dn - 0.02f else next)))
                }
                val uF = uE - d(n - 1) - 0.02f
                out += Plan(TurnKind.FINISH, listOf(flat(uF)))
                out += Plan(TurnKind.FINISH, listOf(flat(uF)))
            }
        }
        return out
    }

    // ---------------------------------------------------------------------------------------------- sampling

    private fun sample(pl: Plan, arm: ArmModel, n: Int): List<SkinPt> {
        // split the samples over the parts by their angular length
        val lens = pl.parts.map { part ->
            when (part) {
                is Part.Limb -> kotlin.math.abs(part.t1 - part.t0)
                is Part.Thumb -> kotlin.math.abs(part.p1 - part.p0) * 0.6f
            }
        }
        val total = lens.sum().coerceAtLeast(1e-3f)
        val out = ArrayList<SkinPt>(n + pl.parts.size)
        pl.parts.forEachIndexed { pi, part ->
            val m = max(4, (n * lens[pi] / total).toInt())
            for (i in 0 until m + (if (pi == pl.parts.size - 1) 1 else 0)) {
                val f = i.toFloat() / m
                out +=
                    when (part) {
                        is Part.Limb -> {
                            val t = part.t0 + (part.t1 - part.t0) * f
                            val u =
                                if (part.kneeT != null && part.kneeU != null) {
                                    val fk = (part.kneeT - part.t0) / (part.t1 - part.t0)
                                    if (f <= fk) part.u0 + (part.kneeU - part.u0) * (f / fk) else part.kneeU + (part.u1 - part.kneeU) * ((f - fk) / (1 - fk))
                                } else part.u0 + (part.u1 - part.u0) * f
                            SkinPt(false, u, t)
                        }
                        is Part.Thumb -> SkinPt(true, part.v, part.p0 + (part.p1 - part.p0) * f)
                    }
            }
        }
        return out
    }

    private fun pos(arm: ArmModel, p: SkinPt, off: Float): Vec3 = if (p.thumb) arm.thumbPoint(p.a, p.b, off) else arm.point(p.a, p.b, off)

    private fun nrm(arm: ArmModel, p: SkinPt): Vec3 = if (p.thumb) arm.thumbNormal(p.b) else arm.normal(p.a, p.b)

    private fun reproject(arm: ArmModel, ref: SkinPt, q: Vec3): SkinPt =
        if (ref.thumb) arm.projectThumb(q).let { SkinPt(true, it.first, it.second) }
        else arm.project(q, ref.a).let { SkinPt(false, it.first, it.second) }

    /** All turns of [w] on [arm], with their strips lifted by layer. Runs off the main thread (a few ms). */
    fun build(w: Wrap, arm: ArmModel): List<TurnGeom> {
        val plans = plan(w, arm)
        val width = w.width
        // pass 1: centre lines and the strip on the skin (base offset)
        val paths = plans.map { sample(it, arm, SAMPLES) }
        val baseRows = ArrayList<List<Array<SkinPt>>>(plans.size)
        val baseCentres = ArrayList<List<Vec3>>(plans.size)
        val acrossAll = ArrayList<List<Vec3>>(plans.size)
        for (path in paths) {
            val cs = path.map { pos(arm, it, BASE_OFF) }
            val rows = ArrayList<Array<SkinPt>>(path.size)
            val ac = ArrayList<Vec3>(path.size)
            for (i in path.indices) {
                val prev = cs[max(0, i - 1)]
                val next = cs[min(cs.size - 1, i + 1)]
                var t = next - prev
                if (t.length < 1e-6f) t = Vec3(0f, 0f, 1f)
                val n = nrm(arm, path[i])
                var a = t.cross(n)
                a = if (a.length < 1e-6f) Vec3(0f, 0f, 1f) else a.normalized()
                ac += a
                rows += Array(COLS) { c ->
                    val k = -1f + 2f * c / (COLS - 1)
                    reproject(arm, path[i], cs[i] + a * (k * width / 2))
                }
            }
            baseRows += rows
            baseCentres += cs
            acrossAll += ac
        }
        // pass 2: layers — how many earlier turns lie under each vertex
        val boxes = baseCentres.map { cs ->
            var mn = Vec3(1e9f, 1e9f, 1e9f)
            var mx = Vec3(-1e9f, -1e9f, -1e9f)
            for (c in cs) {
                mn = Vec3(min(mn.x, c.x), min(mn.y, c.y), min(mn.z, c.z))
                mx = Vec3(max(mx.x, c.x), max(mx.y, c.y), max(mx.z, c.z))
            }
            mn to mx
        }
        val r2 = (width / 2 * 0.92f).let { it * it }
        val out = ArrayList<TurnGeom>(plans.size)
        for (k in plans.indices) {
            var maxLayer = 1
            val rows = ArrayList<Array<Vec3>>(baseRows[k].size)
            val norms = ArrayList<Array<Vec3>>(baseRows[k].size)
            val centres = ArrayList<Vec3>(baseRows[k].size)
            val centreN = ArrayList<Vec3>(baseRows[k].size)
            val layersOf = { q: Vec3 ->
                var c = 0
                for (j in 0 until k) {
                    val (mn, mx) = boxes[j]
                    val h = width / 2
                    if (q.x < mn.x - h || q.y < mn.y - h || q.z < mn.z - h || q.x > mx.x + h || q.y > mx.y + h || q.z > mx.z + h) continue
                    if (distToPolyline2(q, baseCentres[j]) < r2) c++
                }
                c
            }
            for (i in baseRows[k].indices) {
                val sp = baseRows[k][i]
                val verts = Array(COLS) { c ->
                    val base = pos(arm, sp[c], BASE_OFF)
                    val layer = layersOf(base)
                    if (layer + 1 > maxLayer) maxLayer = layer + 1
                    pos(arm, sp[c], lift(layer, k))
                }
                rows += verts
                norms += Array(COLS) { c -> nrm(arm, sp[c]) }
                val pc = paths[k][i]
                val lc = layersOf(baseCentres[k][i])
                centres += pos(arm, pc, lift(lc, k))
                centreN += nrm(arm, pc)
            }
            val arc = FloatArray(centres.size)
            for (i in 1 until centres.size) arc[i] = arc[i - 1] + (centres[i] - centres[i - 1]).length
            val spineU = FloatArray(centres.size) { i -> paths[k][i].let { if (it.thumb) arm.thumbRootU else it.a } }
            out += TurnGeom(k, plans[k].kind, width, paths[k], rows, norms, centres, centreN, acrossAll[k], arc, spineU, maxLayer)
        }
        return out
    }

    private fun distToPolyline2(q: Vec3, pts: List<Vec3>): Float {
        var best = Float.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val ab = b - a
            val l2 = ab.dot(ab)
            val t = if (l2 < 1e-12f) 0f else ((q - a).dot(ab) / l2).coerceIn(0f, 1f)
            val d = q - (a + ab * t)
            val d2 = d.dot(d)
            if (d2 < best) best = d2
        }
        return best
    }

    /** Skin positions of a turn's centre line lifted [off] (next-turn line). */
    fun centreLine(arm: ArmModel, t: TurnGeom, off: Float): List<Vec3> = t.path.map { pos(arm, it, off) }

    /**
     * Where two diagonals of an 8 cross (design mustPass: 八字交叉点落在设计包写的那一面): the skin θ of the point
     * where an OUT turn's diagonal and the following BACK turn's diagonal are closest, in the right-arm convention.
     */
    fun crossingTheta(turns: List<TurnGeom>, outKind: TurnKind): Float? {
        val i = turns.indexOfFirst { it.kind == outKind }
        if (i < 0 || i + 1 >= turns.size) return null
        val a = turns[i].path.filter { !it.thumb }
        val b = turns[i + 1].path.filter { !it.thumb }
        var best = Float.MAX_VALUE
        var th = 0f
        for (p in a) for (q in b) {
            if (kotlin.math.abs(p.b - q.b) > 0.06f) continue
            val du = kotlin.math.abs(p.a - q.a)
            if (du < best) {
                best = du
                th = p.b
            }
        }
        return th
    }
}
