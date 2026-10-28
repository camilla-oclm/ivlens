package com.ivlens.app.scan

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Exact IVs read from the game's Appraise screen. */
data class Appraisal(val atk: Int, val def: Int, val sta: Int) {
    val ivText: String get() = "$atk/$def/$sta"
}

/**
 * Reads the three stat bars on the Appraise screen by their look alone, with no text reading:
 * three bars of the same length, stacked at an even spacing low on the left of the screen. Each
 * runs in three equal segments of five points and is filled in a strong colour (orange, or red
 * when the stat is 15) over a light grey track.
 *
 * Text recognition used to find the "Attack / Defense / HP" labels first, and missed them often
 * enough on the real screen (tinted page, animated leader) that nothing was read at all.
 *
 * A bar only counts when its fill stops cleanly on a whole point; anything fuzzier (an animation
 * still running, a finger on the screen) is rejected rather than guessed.
 */
object AppraisalReader {

    /** Where the bars can be, as fractions of the screen: the left part of the lower half. */
    private const val RIGHT = 0.7
    private const val TOP = 0.55
    private const val BOTTOM = 0.97

    /** The part of a [w]×[h] screen worth reading for [findIn]: left, top, right, bottom. */
    fun region(w: Int, h: Int): IntArray = intArrayOf(0, (TOP * h).toInt(), (RIGHT * w).toInt(), (BOTTOM * h).toInt())

    /** Three bars on screen. [appraisal] is null while their fill can't be read yet (still animating). */
    data class Bars(val appraisal: Appraisal?)

    private enum class Px { FILL, TRACK, OTHER }

    private fun classify(p: Int): Px {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val hi = maxOf(r, g, b)
        val lo = minOf(r, g, b)
        return when {
            // Orange / red fill: warm and saturated.
            hi - lo >= 70 && r >= 170 && r >= g && r > b + 50 -> Px.FILL
            // Light grey track (white background and dark text excluded).
            hi - lo <= 24 && lo >= 150 && hi <= 238 -> Px.TRACK
            else -> Px.OTHER
        }
    }

    /** One bar, measured along its middle row [y]. */
    internal data class Bar(val start: Int, val end: Int, val segments: List<Pair<Int, Int>>, val y: Int)

    /** The bars on a whole screen ([px] is [w]×[h]), or null when there aren't three. */
    fun find(px: IntArray, w: Int, h: Int): Bars? {
        val (l, t, r, b) = region(w, h)
        val pw = r - l
        val sub = IntArray(pw * (b - t))
        for (y in t until b) System.arraycopy(px, y * w + l, sub, (y - t) * pw, pw)
        return findIn(sub, pw, b - t, w)
    }

    /**
     * The bars in [px], a [pw]×[ph] block cut from a screen [screenW] wide along its left edge
     * (see [region]), or null when there aren't three.
     */
    fun findIn(px: IntArray, pw: Int, ph: Int, screenW: Int): Bars? {
        val bars = threeBars(px, pw, ph, screenW) ?: return null
        val values = bars.map { valueOf(px, pw, it) }
        val (a, d, s) = values
        return Bars(if (a != null && d != null && s != null) Appraisal(a, d, s) else null)
    }

    private data class Run(val y: Int, val start: Int, val end: Int)

    /** Longest stretch of bar pixels on row [y], bridging gaps up to [gapMax]. */
    private fun longestRun(px: IntArray, pw: Int, y: Int, gapMax: Int): Run? {
        var best: Run? = null
        var start = -1
        var last = -1
        for (x in 0 until pw) {
            if (classify(px[y * pw + x]) == Px.OTHER) continue
            if (start < 0 || x - last > gapMax) start = x
            last = x
            if (best == null || last - start > best.end - best.start) best = Run(y, start, last)
        }
        return best
    }

    private fun threeBars(px: IntArray, pw: Int, ph: Int, screenW: Int): List<Bar>? {
        // Bridges the small gaps between a bar's segments, but not the gap to a leader standing close.
        val gapMax = maxOf(3, (0.012 * screenW).roundToInt())
        val minLen = (0.25 * screenW).roundToInt()
        val minRows = maxOf(4, (0.008 * screenW).roundToInt())
        // Rows with a long run, grouped into bands of neighbouring rows: one band per bar.
        val bands = mutableListOf<MutableList<Run>>()
        var lastY = -10
        for (y in 0 until ph) {
            val run = longestRun(px, pw, y, gapMax) ?: continue
            if (run.end - run.start < minLen) continue
            if (y - lastY > 2) bands += mutableListOf<Run>()
            bands.last() += run
            lastY = y
        }
        val bars = bands.filter { it.size >= minRows }.mapNotNull { barOf(px, pw, it) }
        // Three in a row with the same ends, evenly spaced.
        val tol = 0.02 * screenW
        for (i in 0..bars.size - 3) {
            val (a, b, c) = bars.subList(i, i + 3)
            if (maxOf(a.start, b.start, c.start) - minOf(a.start, b.start, c.start) > tol) continue
            if (maxOf(a.end, b.end, c.end) - minOf(a.end, b.end, c.end) > tol) continue
            val g1 = b.y - a.y
            val g2 = c.y - b.y
            if (g1 <= 0 || abs(g1 - g2) > 0.15 * maxOf(g1, g2)) continue
            return listOf(a, b, c)
        }
        return null
    }

    /** The bar in one band of rows, or null when it isn't three equal segments. */
    private fun barOf(px: IntArray, pw: Int, band: List<Run>): Bar? {
        // Rounded ends make the outer rows shorter; measure on the middle of the longest rows.
        val longest = band.maxOf { it.end - it.start }
        val core = band.filter { it.end - it.start >= longest * 0.97 }
        val mid = core[core.size / 2]
        // Segments: stretches of bar pixels split by short background gaps.
        val segs = mutableListOf<Pair<Int, Int>>()
        var s = -1
        var e = -1
        for (x in mid.start..mid.end) {
            if (classify(px[mid.y * pw + x]) == Px.OTHER) continue
            if (s < 0) s = x
            else if (x - e > 2) {
                segs += s to e
                s = x
            }
            e = x
        }
        if (s >= 0) segs += s to e
        // The first three equal segments make the bar; anything after them (a leader standing
        // close) isn't part of it.
        val three = segs.filter { it.second - it.first >= 4 }.take(3)
        if (three.size < 3) return null
        val lens = three.map { it.second - it.first }
        if (lens.max() - lens.min() > 0.1 * lens.max()) return null
        return Bar(three.first().first, three.last().second, three, mid.y)
    }

    /** Stat value 0–15 from one bar, or null when the fill doesn't land on a whole point. */
    private fun valueOf(px: IntArray, pw: Int, bar: Bar): Int? {
        fun fillShare(a: Int, b: Int): Double {
            var fill = 0
            var all = 0
            for (x in a..b) {
                val c = classify(px[bar.y * pw + x])
                if (c == Px.FILL) fill++
                if (c != Px.OTHER) all++
            }
            return if (all == 0) 0.0 else fill.toDouble() / all
        }
        // Each segment is five points; earlier segments fill first.
        val shares = bar.segments.map { fillShare(it.first, it.second) }
        for (i in 1 until 3) if (shares[i] > 0.08 && shares[i - 1] < 0.92) return null
        val raw = shares.sumOf { it * 5 }
        val v = raw.roundToInt()
        if (abs(raw - v) > 0.3) return null
        return v.coerceIn(0, 15)
    }
}

/** Matching appraised IVs to what CP, HP and stardust allow. */
object AppraisalFit {
    /**
     * Key for remembering an appraisal this session: species, CP and HP. With the CP hidden, those
     * could be another of the kind with the same HP and stardust (two Dragonite, HP 162 at 5,000
     * stardust, fit each other's bars), so the weight is part of it, and without the weight nothing
     * is kept (1.19). Before, the second Dragonite showed the first one's IVs as exact.
     */
    fun memoryKey(input: com.ivlens.app.core.CalcInput, weightKg: Double?): String? {
        val sp = input.speciesId ?: return null
        val hp = input.hp ?: return null
        input.cp?.let { return "$sp|$it|$hp" }
        val w = weightKg ?: return null
        return "$sp|null|$hp|${(w * 100).roundToLong()}"
    }

    /**
     * The keys to remember an appraisal under (1.24): [memoryKey], and with the weight read, the
     * hidden-CP key too. The owner's Drifblim wears a hat over its CP: swiped away and back, the CP
     * came out garbled a new way, so the appraisal kept under the CP read before wasn't found, and
     * the CP had to be typed. Under the hidden-CP key, a read whose CP is hidden or garbled finds it.
     */
    fun memoryKeys(input: com.ivlens.app.core.CalcInput, weightKg: Double?): List<String> =
        listOfNotNull(memoryKey(input, weightKg), memoryKey(input.copy(cp = null), weightKg)).distinct()

    /** Where to look a read up: a CP that fits no IVs ([cpFits] false) may be garbled, so the hidden-CP key is tried too. */
    fun recallKeys(input: com.ivlens.app.core.CalcInput, weightKg: Double?, cpFits: Boolean): List<String> =
        if (cpFits) listOfNotNull(memoryKey(input, weightKg)) else memoryKeys(input, weightKg)

    /**
     * The result and combo for [ap]: from [result] when it already has it, otherwise from CP and
     * HP with fewer assumptions (no level arc, then no stardust). With the CP hidden, the HP and
     * stardust find it ([com.ivlens.app.core.IvCalculator.cpFor]). Null when nothing fits, which
     * means the bars belong to another Pokémon or were misread (or the level can't be told).
     */
    fun fit(calc: com.ivlens.app.core.IvCalculator, input: com.ivlens.app.core.CalcInput, result: com.ivlens.app.core.CalcResult?, ap: Appraisal, maxLevel: Int):
        Pair<com.ivlens.app.core.CalcResult, com.ivlens.app.core.IvCombo>? {
        fun find(r: com.ivlens.app.core.CalcResult?) = r?.combos?.firstOrNull { it.atk == ap.atk && it.def == ap.def && it.sta == ap.sta }?.let { r to it }
        if (input.speciesId == null) return null
        if (input.cp == null) {
            // CP hidden behind the Pokémon: with exact IVs, the HP and stardust pin the level and so the CP.
            val cp = calc.cpFor(input, ap.atk, ap.def, ap.sta, maxLevel) ?: return null
            return find(calc.calculate(input.copy(cp = cp), maxLevel))
        }
        return find(result)
            ?: find(calc.calculate(input.copy(levelHint = null), maxLevel))
            ?: input.dust?.let { find(calc.calculate(input.copy(levelHint = null, dust = null), maxLevel)) }
            ?: misreadCp(calc, input, ap, maxLevel)
    }

    /**
     * The CP when the one read can't be right (1.24): the owner's Drifblim wears a hat over its
     * CP 223, read as "23", "29", "290", "292" and "293" at HP 82, where no IVs fit at all (the
     * Electrike's 410 read as 10 was the same). Such a CP says nothing, so it's worked out as for a
     * hidden one. Rules on the digits read were tried first and each new misread broke them. A CP
     * that fits some IVs is never overruled, and the level arc, if read, must agree.
     */
    private fun misreadCp(calc: com.ivlens.app.core.IvCalculator, input: com.ivlens.app.core.CalcInput, ap: Appraisal, maxLevel: Int):
        Pair<com.ivlens.app.core.CalcResult, com.ivlens.app.core.IvCombo>? {
        if (input.cp == null) return null
        if (calc.calculate(input.copy(levelHint = null), maxLevel)?.combos?.isEmpty() != true) return null
        val cp = calc.cpFor(input.copy(cp = null), ap.atk, ap.def, ap.sta, maxLevel) ?: return null
        val result = calc.calculate(input.copy(cp = cp), maxLevel) ?: return null
        val combo = result.combos.firstOrNull { it.atk == ap.atk && it.def == ap.def && it.sta == ap.sta } ?: return null
        val arc = input.levelHint.orEmpty()
        return (result to combo).takeIf { arc.isEmpty() || combo.level in arc }
    }

    /**
     * Like [fit], but when the bars don't fit the species in [input], its other forms in
     * [candidates] (same name, such as Qwilfish and Hisuian Qwilfish) get a try. The bars, CP and
     * HP together tell the forms apart, even when the leader covers the type labels.
     */
    fun fitForms(
        calc: com.ivlens.app.core.IvCalculator, input: com.ivlens.app.core.CalcInput, result: com.ivlens.app.core.CalcResult?,
        ap: Appraisal, maxLevel: Int, candidates: List<com.ivlens.app.core.Species>,
    ): Pair<com.ivlens.app.core.CalcResult, com.ivlens.app.core.IvCombo>? {
        fit(calc, input, result, ap, maxLevel)?.let { return it }
        val base = candidates.firstOrNull { it.id == input.speciesId }?.base ?: return null
        return candidates.asSequence()
            .filter { it.base == base && it.id != input.speciesId }
            .firstNotNullOfOrNull { fit(calc, input.copy(speciesId = it.id), null, ap, maxLevel) }
    }
}
