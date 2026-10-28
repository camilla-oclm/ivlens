package com.ivlens.app.scan

import com.ivlens.app.core.GameData
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Arc circle as fractions of the screen width (centre x is always the middle). */
data class ArcGeometry(val rRatio: Double, val cyRatio: Double)

/** Where the white level dot sits on the arc above the Pokémon. Angles in degrees, 0 = right end. */
data class ArcReading(
    val geometry: ArcGeometry,
    /** How much of the arc's left side the circle explains (half-degree bins). */
    val coverage: Int,
    val dotAngle: Double,
    val dotFound: Boolean,
)

/**
 * Reads the level arc. The arc is a thin white semicircle; the part left of the dot is solid
 * white and the rest is translucent. The dot's angle is linear in the CP multiplier, from
 * level 1 at [ANGLE_L1] to the trainer's max level at [ANGLE_L1] - [SPAN]
 * (calibrated on real screenshots).
 */
object ArcReader {
    const val ANGLE_L1 = 176.7
    const val SPAN = 176.1
    private const val DOT_HALF_WIDTH_DEG = 1.15

    /** Layout measured on a 1080x2340 phone; used as a tie-breaker. */
    val DEFAULT_GEOMETRY = ArcGeometry(0.4096, 0.638)

    /** Coverage at which a fit is trusted enough to remember for this device. */
    const val CONFIDENT_COVERAGE = 95

    private fun isWhite(p: Int, min: Int, spread: Int): Boolean {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val lo = minOf(r, g, b)
        val hi = maxOf(r, g, b)
        return lo >= min && hi - lo <= spread
    }

    private fun collectPoints(px: IntArray, w: Int, h: Int): List<DoubleArray> {
        val pts = ArrayList<DoubleArray>()
        val step = maxOf(1, w / 360)
        val y0 = (0.03 * h).toInt()
        val y1 = minOf(h - 1, (0.5 * h).toInt())
        val maxRun = maxOf(3, (0.02 * w).toInt())
        fun white(x: Int, y: Int) = isWhite(px[y * w + x], 230, 30)
        // Columns catch the flatter part of the arc ...
        var x = (0.07 * w).toInt()
        while (x <= (0.30 * w).toInt()) {
            var y = y0
            while (y <= y1) {
                if (white(x, y)) {
                    val s = y
                    while (y <= y1 && white(x, y)) y++
                    if (y - s in 1..maxRun) pts += doubleArrayOf(x.toDouble(), (s + y - 1) / 2.0)
                }
                y++
            }
            x += step
        }
        // ... rows catch the near-vertical part by the left end.
        val x0 = (0.03 * w).toInt()
        val x1 = (0.30 * w).toInt()
        var y = y0
        while (y <= y1) {
            var xx = x0
            while (xx <= x1) {
                if (white(xx, y)) {
                    val s = xx
                    while (xx <= x1 && white(xx, y)) xx++
                    if (xx - s in 1..maxRun) pts += doubleArrayOf((s + xx - 1) / 2.0, y.toDouble())
                }
                xx++
            }
            y += step
        }
        return pts
    }

    private fun coverage(pts: List<DoubleArray>, cx: Double, r: Double, cy: Double): Int {
        val bins = HashSet<Int>()
        for (p in pts) {
            val dx = p[0] - cx
            val dy = p[1] - cy
            if (abs(sqrt(dx * dx + dy * dy) - r) < 2.0) bins += (Math.toDegrees(atan2(-dy, dx)) * 2).toInt()
        }
        return bins.size
    }

    /** Best circle by Hough vote over radius and centre height, scored by coverage. */
    private fun search(pts: List<DoubleArray>, w: Int, h: Int, cx: Double): Pair<Double, Double>? {
        var bestScore = 0
        var best: Pair<Double, Double>? = null
        var r = 0.36 * w
        while (r <= 0.46 * w) {
            val cys = ArrayList<Double>(pts.size)
            for (p in pts) {
                val dx = p[0] - cx
                if (abs(dx) < r) cys += p[1] + sqrt(r * r - dx * dx)
            }
            cys.sort()
            var j = 0
            var lastCenter = Double.NaN
            for (i in cys.indices) {
                while (cys[i] - cys[j] > 4.0) j++
                if (i - j + 1 < 10) continue
                val c = (cys[i] + cys[j]) / 2
                if (c < 0 || c > h || abs(c - lastCenter) < 1.0) continue
                lastCenter = c
                val score = coverage(pts, cx, r, c)
                if (score > bestScore) {
                    bestScore = score
                    best = r to c
                }
            }
            r += 1.0
        }
        return best
    }

    fun read(px: IntArray, w: Int, h: Int, known: ArcGeometry? = null): ArcReading? {
        if (w < 200 || h < 300) return null
        val cx = w / 2.0
        val pts = collectPoints(px, w, h)
        if (pts.size < 12) return null

        fun score(g: ArcGeometry) = coverage(pts, cx, g.rRatio * w, g.cyRatio * w)
        val found = search(pts, w, h, cx)?.let { ArcGeometry(it.first / w, it.second / w) }
        val foundScore = found?.let { score(it) } ?: 0
        // A remembered fit for this device, or the usual layout, wins unless the new fit
        // explains clearly more of the arc (white backgrounds can fool the search).
        val geometry = listOfNotNull(
            known?.let { it to 0.7 },
            DEFAULT_GEOMETRY to 0.8,
        ).firstOrNull { (g, ratio) -> score(g) >= 20 && score(g) >= ratio * foundScore }?.first
            ?: found ?: return null
        val cov = score(geometry)
        if (cov < 20) return null
        val r = geometry.rRatio * w
        val cy = geometry.cyRatio * w

        fun whiteAt(deg: Double, window: Int): Int {
            val t = Math.toRadians(deg)
            var n = 0
            for (dr in -window..window) {
                val xx = (cx + (r + dr) * cos(t)).roundToInt()
                val yy = (cy - (r + dr) * sin(t)).roundToInt()
                if (xx in 0 until w && yy in 0 until h && isWhite(px[yy * w + xx], 235, 25)) n++
            }
            return n
        }

        val window = maxOf(8, (0.036 * r).roundToInt())
        val thick = maxOf(6, (0.0225 * r).roundToInt())

        // Every solid-white edge along the arc, scanning from the right end: the real dot's right
        // edge, plus sparkles, bright background spots or a white Pokémon on the translucent part.
        val solid = BooleanArray(1801) { whiteAt(it / 10.0, 3) > 0 }
        fun solidAt(deg: Double): Boolean = solid[(deg * 10).roundToInt().coerceIn(0, 1800)]
        val edges = mutableListOf<Double>()
        var k = 0
        while (k <= 1800) {
            val deg = k / 10.0
            if (solid[k] && listOf(1.5, 3.0, 4.5, 6.0).count { solidAt(deg + it) } >= 2) {
                edges += deg
                // Skip past this solid stretch before looking for the next edge.
                while (k <= 1800 && (solid[k] || (k + 15 <= 1800 && solid[k + 15]))) k++
            }
            k++
        }
        if (edges.isEmpty()) return null

        fun dotAt(edge: Double): Pair<Double, Boolean> {
            // The dot is much thicker than the line; take the middle of the thick stretch.
            val hits = (0..80).map { edge + it / 10.0 }.filter { whiteAt(it, window) >= thick }
            val run = mutableListOf<Double>()
            for (d in hits) {
                if (run.isEmpty() || d - run.last() <= 0.25) run += d else break
            }
            val found = run.isNotEmpty() && run.first() - edge < 1.0
            return (if (found) (run.first() + run.last()) / 2 else edge + DOT_HALF_WIDTH_DEG) to found
        }

        // The real dot has the solid line running on to its left and only the translucent line
        // to its right. Score each candidate by that contrast; ties go to the first from the right.
        fun share(from: Double, to: Double): Double {
            val lo = from.coerceIn(0.0, 180.0)
            val hi = to.coerceIn(0.0, 180.0)
            if (hi - lo < 2.0) return -1.0
            val steps = ((hi - lo) / 0.5).toInt()
            return (0..steps).count { solidAt(lo + it * 0.5) }.toDouble() / (steps + 1)
        }
        var best: Triple<Double, Boolean, Double>? = null
        for (e in edges.take(12)) {
            val (dot, found) = dotAt(e)
            val left = share(dot + 3.0, dot + 33.0).let { if (it < 0) 1.0 else it }
            val right = share(dot - 33.0, dot - 3.0).let { if (it < 0) 0.0 else it }
            val score = left - right + (if (found) 0.15 else 0.0)
            if (best == null || score > best.third + 0.05) best = Triple(dot, found, score)
        }
        val (dot, dotFound) = best!!.let { it.first to it.second }
        return ArcReading(geometry, cov, dot, dotFound)
    }

    /** Expected dot angle for a level index, given the trainer's max Pokémon level index. */
    fun angleFor(gd: GameData, levelIndex: Int, maxIndex: Int): Double {
        val c0 = gd.cpm[0]
        val frac = (gd.cpm[levelIndex] - c0) / (gd.cpm[maxIndex] - c0)
        return ANGLE_L1 - SPAN * frac
    }

    /** Level indices whose dot position is within [tolerance] degrees of the reading. */
    fun levelsNear(gd: GameData, reading: ArcReading, maxIndex: Int, tolerance: Double = 0.6): List<Int> {
        val all = (0..maxIndex).map { it to abs(angleFor(gd, it, maxIndex) - reading.dotAngle) }
        val near = all.filter { it.second <= tolerance }.map { it.first }
        return near.ifEmpty { listOf(all.minBy { it.second }.first) }
    }
}
