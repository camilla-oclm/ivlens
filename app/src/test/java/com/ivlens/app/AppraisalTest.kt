package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.scan.AppraisalReader
import com.ivlens.app.scan.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic Appraise screens: a white box over a dimmed page, three labels with a three-segment
 * bar under each (grey track, orange fill, red when 15).
 */
class AppraisalTest {
    private val w = 1080
    private val h = 2340
    private val dim = 0xFF5A5A5A.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val track = 0xFFE2E2E2.toInt()
    private val orange = 0xFFF6A045.toInt()
    private val red = 0xFFE8695D.toInt()

    private class Screen(val px: IntArray, val lines: List<OcrLine>)

    /** [values] may be fractional to fake a bar caught mid-animation. */
    private fun screen(values: List<Double>, barLeft: Int = 120, barWidth: Int = 620, gap: Int = 10): Screen {
        val px = IntArray(w * h) { dim }
        fun rect(l: Int, t: Int, r: Int, b: Int, c: Int) {
            for (y in t until b) for (x in l until r) px[y * w + x] = c
        }
        rect(60, 1620, 1020, 2180, white)
        val lines = mutableListOf<OcrLine>()
        val segW = (barWidth - 2 * gap) / 3
        listOf("Attack", "Defense", "HP").forEachIndexed { i, label ->
            val top = 1680 + i * 150
            lines += OcrLine(label, barLeft, top, barLeft + 18 * label.length, top + 36)
            // Label text: a few dark strokes so it isn't mistaken for a bar.
            rect(barLeft, top + 10, barLeft + 18 * label.length, top + 26, 0xFF444444.toInt())
            val y0 = top + 56
            val v = values[i]
            val fillColor = if (v >= 15.0) red else orange
            for (s in 0 until 3) {
                val l = barLeft + s * (segW + gap)
                rect(l, y0, l + segW, y0 + 26, track)
                val share = ((v - 5 * s) / 5.0).coerceIn(0.0, 1.0)
                rect(l, y0, l + (segW * share).toInt(), y0 + 26, fillColor)
            }
        }
        return Screen(px, lines)
    }

    @Test
    fun readsWholeValues() {
        for (ivs in listOf(listOf(12, 15, 13), listOf(0, 7, 15), listOf(15, 15, 15), listOf(5, 10, 14), listOf(1, 0, 4))) {
            val s = screen(ivs.map { it.toDouble() })
            val ap = AppraisalReader.find(s.px, w, h)?.appraisal
            println("$ivs -> $ap")
            assertEquals(ivs, ap?.let { listOf(it.atk, it.def, it.sta) })
        }
    }

    @Test
    fun widerAndNarrowerBarsToo() {
        for ((left, width) in listOf(90 to 640, 200 to 420)) {
            val s = screen(listOf(13.0, 11.0, 14.0), left, width)
            assertEquals("13/11/14", AppraisalReader.find(s.px, w, h)?.appraisal?.ivText)
        }
    }

    @Test
    fun rejectsABarStillFilling() {
        val s = screen(listOf(12.0, 8.5, 13.0))
        // The bars are there, but not readable yet.
        val bars = AppraisalReader.find(s.px, w, h)
        assertNotNull(bars)
        assertNull(bars!!.appraisal)
    }

    @Test
    fun ignoresScreensWithoutThreeBars() {
        assertNull(AppraisalReader.find(IntArray(w * h) { dim }, w, h))
        // Two bars aren't the Appraise screen.
        val s = screen(listOf(12.0, 15.0, 13.0))
        for (i in 2000 * w until w * h) s.px[i] = dim
        assertNull(AppraisalReader.find(s.px, w, h))
    }

    @Test
    fun pinsMetagrossToOneCombo() {
        // From the user's Metagross (CP 2132, HP 121, 2,500 dust, arc at level 20): 12/15/13 or 13/12/14.
        val r = IvCalculator(TestData.gd).calculate(CalcInput("metagross", 2132, 121, 2500, levelHint = listOf(38)))!!
        val s = screen(listOf(12.0, 15.0, 13.0))
        val ap = AppraisalReader.find(s.px, w, h)!!.appraisal!!
        val match = r.combos.filter { it.atk == ap.atk && it.def == ap.def && it.sta == ap.sta }
        println("metagross combos ${r.combos.map { it.ivText }} -> ${match.map { it.ivText + " " + it.percent + "%" }}")
        assertEquals(1, match.size)
        assertTrue(match.single().percent == 89)
    }
}
