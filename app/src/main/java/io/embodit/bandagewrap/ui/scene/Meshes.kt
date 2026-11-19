package io.embodit.bandagewrap.ui.scene

import android.graphics.Bitmap
import com.pico.spatial.core.math.Vector2
import io.embodit.bandagewrap.domain.ArmModel
import io.embodit.bandagewrap.domain.TurnGeom
import io.embodit.bandagewrap.domain.Vec3
import io.embodit.bandagewrap.domain.WrapBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Programmatic meshes of the arm, the bandage strips, the roll, the arrow and the next-turn line (arm-local metres). */
internal object Meshes {
    private const val SEG = 40

    /** The limb (rings along u, superelliptic sections, smooth normals) + the thumb, with closed fingertip and thumb caps. */
    fun arm(arm: ArmModel): MeshBuilder {
        val b = MeshBuilder(12000)
        // u samples: dense on the hand, sparser on the forearm / upper arm
        val us = ArrayList<Float>()
        var u = arm.uMin
        while (u < arm.uMax) {
            us += u
            u += if (u < 0.01f) 0.004f else 0.008f
        }
        us += arm.uMax
        val rings = ArrayList<IntArray>()
        for (uu in us) {
            val ring = IntArray(SEG + 1)
            for (k in 0..SEG) {
                val th = (2 * PI * k / SEG).toFloat()
                ring[k] = b.vertex(arm.point(uu, th), arm.normal(uu, th), Vector2(k.toFloat() / SEG, (uu - arm.uMin) / (arm.uMax - arm.uMin)))
            }
            rings += ring
        }
        // the left arm is a mirror image: flip the winding so faces stay outward (culling is off anyway)
        val flip = arm.p.side == io.embodit.bandagewrap.domain.ArmSide.LEFT
        for (i in 0 until rings.size - 1) for (k in 0 until SEG) {
            val a = rings[i][k]
            val c = rings[i][k + 1]
            val d = rings[i + 1][k + 1]
            val e = rings[i + 1][k]
            if (flip) b.quad(a, c, d, e) else b.quad(a, e, d, c)
        }
        // fingertip cap and the cut end of the upper arm
        cap(b, rings.first(), arm.point(arm.uMin, 0f) .let { p -> centreOf(arm, arm.uMin) }, arm, true, flip)
        cap(b, rings.last(), centreOf(arm, arm.uMax), arm, false, flip)
        thumb(b, arm, flip)
        return b
    }

    private fun centreOf(arm: ArmModel, u: Float): Vec3 {
        var c = Vec3.ZERO
        for (k in 0 until 8) c = c + arm.point(u, (2 * PI * k / 8).toFloat())
        return c / 8f
    }

    private fun cap(b: MeshBuilder, ring: IntArray, centre: Vec3, arm: ArmModel, distal: Boolean, flip: Boolean) {
        val n = (if (distal) Vec3(0f, 0f, 1f) else (centre - centreOf(arm, arm.uMax - 0.01f)).normalized())
        val c = b.vertex(centre, n)
        for (k in 0 until ring.size - 1) if (distal xor flip) b.tri(c, ring[k], ring[k + 1]) else b.tri(c, ring[k + 1], ring[k])
    }

    private fun thumb(b: MeshBuilder, arm: ArmModel, flip: Boolean) {
        val seg = 20
        val rings = ArrayList<IntArray>()
        var v = 0f
        val vs = ArrayList<Float>()
        while (v < arm.thumbLen) {
            vs += v
            v += 0.003f
        }
        vs += arm.thumbLen
        for (vv in vs) {
            rings += IntArray(seg + 1) { k ->
                val phi = (2 * PI * k / seg).toFloat()
                b.vertex(arm.thumbPoint(vv, phi), arm.thumbNormal(phi))
            }
        }
        for (i in 0 until rings.size - 1) for (k in 0 until seg) {
            val a = rings[i][k]
            val c = rings[i][k + 1]
            val d = rings[i + 1][k + 1]
            val e = rings[i + 1][k]
            if (flip) b.quad(a, c, d, e) else b.quad(a, e, d, c)
        }
        val (base, dir) = arm.thumbAxis()
        val tip = b.vertex(base + dir * arm.thumbLen, dir)
        val last = rings.last()
        for (k in 0 until seg) b.tri(tip, last[k], last[k + 1])
    }

    /** A turn's strip, the first [rowsShown] rows (all when null); a partial last row is interpolated by [frac]. */
    fun strip(t: TurnGeom, rowsShown: Int? = null, frac: Float = 0f): MeshBuilder {
        val n = (rowsShown ?: t.samples).coerceIn(0, t.samples)
        val b = MeshBuilder(n * WrapBuilder.COLS + 8)
        if (n < 1) return b
        val total = max(t.arc.last(), 1e-3f)
        val rows = ArrayList<IntArray>(n + 1)
        fun addRow(verts: Array<Vec3>, norms: Array<Vec3>, arc: Float) {
            rows += IntArray(WrapBuilder.COLS) { c -> b.vertex(verts[c], norms[c], Vector2(arc / total, c.toFloat() / (WrapBuilder.COLS - 1))) }
        }
        for (i in 0 until n) addRow(t.rows[i], t.normals[i], t.arc[i])
        if (rowsShown != null && n < t.samples && frac > 0f) {
            val a = t.rows[n - 1]
            val c = t.rows[n]
            addRow(Array(WrapBuilder.COLS) { k -> a[k].lerp(c[k], frac) }, t.normals[n], t.arc[n - 1] + (t.arc[n] - t.arc[n - 1]) * frac)
        }
        for (i in 0 until rows.size - 1) for (c in 0 until WrapBuilder.COLS - 1) b.quad(rows[i][c], rows[i][c + 1], rows[i + 1][c + 1], rows[i + 1][c])
        return b
    }

    /** The next-turn line: 3 mm dashes along a centre line (arm-local), lifted a little over everything. */
    fun dashes(points: List<Vec3>, normals: List<Vec3>, width: Float = 0.003f, dash: Float = 0.010f, gap: Float = 0.007f): MeshBuilder {
        val b = MeshBuilder(points.size * 4)
        var acc = 0f
        var on = true
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val seg = (p1 - p0).length
            acc += seg
            val limit = if (on) dash else gap
            if (on) {
                val t = (p1 - p0).let { if (it.length < 1e-6f) Vec3(0f, 0f, 1f) else it.normalized() }
                val n = normals[i]
                val a = t.cross(n).normalized() * (width / 2)
                b.quad(b.vertex(p0 - a, n), b.vertex(p0 + a, n), b.vertex(p1 + a, n), b.vertex(p1 - a, n))
            }
            if (acc >= limit) {
                acc = 0f
                on = !on
            }
        }
        return b
    }

    /** The bandage roll: a short cylinder about local X (radius [r], width [w]) with its two end faces. */
    fun roll(r: Float, w: Float): MeshBuilder {
        val b = MeshBuilder(256)
        val seg = 28
        val h = w / 2
        for (i in 0 until seg) {
            val a0 = 2 * PI * i / seg
            val a1 = 2 * PI * (i + 1) / seg
            val y0 = (r * cos(a0)).toFloat()
            val z0 = (r * sin(a0)).toFloat()
            val y1 = (r * cos(a1)).toFloat()
            val z1 = (r * sin(a1)).toFloat()
            val n0 = Vec3(0f, y0, z0).normalized()
            val n1 = Vec3(0f, y1, z1).normalized()
            b.quad(b.vertex(Vec3(-h, y0, z0), n0, Vector2(0f, i.toFloat() / seg)), b.vertex(Vec3(h, y0, z0), n0, Vector2(1f, i.toFloat() / seg)), b.vertex(Vec3(h, y1, z1), n1, Vector2(1f, (i + 1f) / seg)), b.vertex(Vec3(-h, y1, z1), n1, Vector2(0f, (i + 1f) / seg)))
        }
        return b
    }

    /** End faces of the roll (the spiral of the rolled bandage is in the texture). */
    fun rollEnds(r: Float, w: Float): MeshBuilder {
        val b = MeshBuilder(128)
        val seg = 28
        for (s in intArrayOf(-1, 1)) {
            val n = Vec3(s.toFloat(), 0f, 0f)
            val c = b.vertex(Vec3(s * w / 2, 0f, 0f), n, Vector2(0.5f, 0.5f))
            for (i in 0 until seg) {
                val a0 = 2 * PI * i / seg
                val a1 = 2 * PI * (i + 1) / seg
                val p0 = b.vertex(Vec3(s * w / 2, (r * cos(a0)).toFloat(), (r * sin(a0)).toFloat()), n, Vector2((0.5 + 0.5 * cos(a0)).toFloat(), (0.5 + 0.5 * sin(a0)).toFloat()))
                val p1 = b.vertex(Vec3(s * w / 2, (r * cos(a1)).toFloat(), (r * sin(a1)).toFloat()), n, Vector2((0.5 + 0.5 * cos(a1)).toFloat(), (0.5 + 0.5 * sin(a1)).toFloat()))
                b.tri(c, p0, p1)
            }
        }
        return b
    }

    /** A flat 4 cm arrow in local XZ (normal +Y), pointing along −Z. */
    fun arrow(len: Float = 0.04f): MeshBuilder {
        val b = MeshBuilder(16)
        val n = Vec3(0f, 1f, 0f)
        val sw = len * 0.12f
        val hw = len * 0.32f
        val hl = len * 0.42f
        b.quad(b.vertex(Vec3(-sw, 0f, 0f), n), b.vertex(Vec3(sw, 0f, 0f), n), b.vertex(Vec3(sw, 0f, -(len - hl)), n), b.vertex(Vec3(-sw, 0f, -(len - hl)), n))
        b.tri(b.vertex(Vec3(-hw, 0f, -(len - hl)), n), b.vertex(Vec3(hw, 0f, -(len - hl)), n), b.vertex(Vec3(0f, 0f, -len), n))
        return b
    }

    /** A small smooth ball (manual-align handles, design alignHandles r 0.012). */
    fun ball(r: Float): MeshBuilder {
        val b = MeshBuilder(300)
        val st = 10
        val sl = 16
        val rows = ArrayList<IntArray>()
        for (i in 0..st) {
            val th = PI * i / st
            rows += IntArray(sl + 1) { j ->
                val ph = 2 * PI * j / sl
                val n = Vec3((sin(th) * cos(ph)).toFloat(), cos(th).toFloat(), (sin(th) * sin(ph)).toFloat())
                b.vertex(n * r, n)
            }
        }
        for (i in 0 until st) for (j in 0 until sl) b.quad(rows[i][j], rows[i][j + 1], rows[i + 1][j + 1], rows[i + 1][j])
        return b
    }

    /** Crepe bandage texture: cream with fine lengthwise threads and the darker pressed edges (design visual.bandage). */
    fun crepeBitmap(): Bitmap {
        val s = 128
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val base = intArrayOf(0xF2, 0xEB, 0xDD)
        for (y in 0 until s) for (x in 0 until s) {
            // y = across the bandage (v), x = along it (u)
            var k = 1f
            if (y % 6 == 0) k -= 0.05f
            if ((x / 3 + y / 6) % 2 == 0) k -= 0.02f
            val edge = min(y, s - 1 - y)
            if (edge < 6) k -= 0.10f * (1f - edge / 6f)
            bmp.setPixel(x, y, android.graphics.Color.rgb((base[0] * k).toInt(), (base[1] * k).toInt(), (base[2] * k).toInt()))
        }
        return bmp
    }

    /** Roll texture: across = the rolled layers (thin spiral lines on the end faces read the same way). */
    fun rollBitmap(): Bitmap {
        val s = 64
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        for (y in 0 until s) for (x in 0 until s) {
            val r = kotlin.math.hypot((x - s / 2f), (y - s / 2f)) / (s / 2f)
            var k = if (((r * 14).toInt()) % 2 == 0) 0.93f else 1f
            if (r < 0.32f) k = 0.75f
            bmp.setPixel(x, y, android.graphics.Color.rgb((0xF2 * k).toInt(), (0xEB * k).toInt(), (0xDD * k).toInt()))
        }
        return bmp
    }
}
