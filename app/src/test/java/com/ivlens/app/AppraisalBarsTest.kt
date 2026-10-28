package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import com.ivlens.app.scan.AppraisalReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bars on the real Appraise screen, found with no text reading. Zoroark is the screen where
 * 1.5 showed nothing at all (captured over adb on 2026-10-17; the leader's caught-at line is painted out).
 */
class AppraisalBarsTest {
    private val gd = TestData.gd

    @Test
    fun readsZoroarksBars() {
        val img = TestData.screenshot("zoroark_appraise")
        assertEquals(Appraisal(10, 12, 7), AppraisalReader.find(img.px, img.w, img.h)?.appraisal)
    }

    @Test
    fun readsCloystersBars() {
        val img = TestData.screenshot("cloyster_appraise")
        assertEquals(Appraisal(15, 7, 15), AppraisalReader.find(img.px, img.w, img.h)?.appraisal)
    }

    @Test
    fun zoroarksBarsFitItsCpAndHp() {
        val calc = IvCalculator(gd)
        val input = CalcInput("zoroark", 1879, 112, null)
        val all = calc.calculate(input)!!
        val (_, combo) = AppraisalFit.fit(calc, input, all, Appraisal(10, 12, 7), Levels.L50)!!
        println("zoroark CP 1879 / HP 112: ${all.combos.size} combos; bars give ${combo.ivText} ${combo.percent}% Lv ${Levels.label(combo.level)}")
        assertEquals("10/12/7", combo.ivText)
    }

    @Test
    fun noBarsOnAPokemonPage() {
        for (name in listOf("venusaur", "gyarados", "metagross2")) {
            val img = TestData.screenshot(name)
            assertNull(name, AppraisalReader.find(img.px, img.w, img.h))
        }
    }

    @Test
    fun aLeaderStandingCloseIsNotPartOfTheBars() {
        val img = TestData.screenshot("zoroark_appraise")
        // Orange cloth starting just past each bar's end, closer than the gap the reader bridges.
        for (y in 1780 until 2050) for (x in 510 until 700) img.px[y * img.w + x] = 0xFFE7A95C.toInt()
        assertEquals(Appraisal(10, 12, 7), AppraisalReader.find(img.px, img.w, img.h)?.appraisal)
    }

    @Test
    fun aBarUnderTheChipIsNotGuessed() {
        val img = TestData.screenshot("zoroark_appraise")
        // The service blanks our own chip out of the frame; here it covers the end of Defense's bar.
        for (y in 1880 until 1940) for (x in 300 until 520) img.px[y * img.w + x] = 0
        val bars = AppraisalReader.find(img.px, img.w, img.h)
        assertNull(bars?.appraisal)
    }

    @Test
    fun readsJustTheRegionTheServiceCopies() {
        val img = TestData.screenshot("zoroark_appraise")
        val (l, t, r, b) = AppraisalReader.region(img.w, img.h)
        val sub = IntArray((r - l) * (b - t))
        for (y in t until b) System.arraycopy(img.px, y * img.w + l, sub, (y - t) * (r - l), r - l)
        val bars = AppraisalReader.findIn(sub, r - l, b - t, img.w)
        assertNotNull(bars)
        assertEquals(Appraisal(10, 12, 7), bars!!.appraisal)
    }
}
