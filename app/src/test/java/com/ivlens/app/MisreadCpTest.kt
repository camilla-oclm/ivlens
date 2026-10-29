package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import com.ivlens.app.scan.ArcReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's Drifblim (CP 223, HP 82, 400 stardust; 2026-10-27, on 1.24) wears a hat over its CP,
 * read as "23", "29", "290", "292" and "293". No IVs fit those, so its Appraise bars (4/5/11)
 * "didn't fit" and the chip asked to check them. Like a hidden CP, the bars, HP and stardust find
 * the real one.
 *
 * Another Drifblim with a hat (CP 345, HP 101, 600 stardust, bars 4/3/4; 2026-10-29, on 1.25) read
 * as CP 45, and its bars still didn't fit: it's Lv 6, and the arc read its dot as Lv 5.5.
 */
class MisreadCpTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val bars = Appraisal(4, 5, 11)

    @Test
    fun barsFixACpReadWrong() {
        for (read in listOf(23, 29, 290, 292, 293)) {
            val input = CalcInput("drifblim", read, 82, 400)
            assertTrue(calc.calculate(input)!!.combos.isEmpty())
            val (result, combo) = AppraisalFit.fit(calc, input, calc.calculate(input), bars, Levels.L50)!!
            assertEquals("CP read as $read", 223, result.input.cp)
            assertEquals(Triple(4, 5, 11), Triple(combo.atk, combo.def, combo.sta))
            assertEquals("4", Levels.label(combo.level))
        }
    }

    @Test
    fun aCpThatFitsIsNeverOverruled() {
        // CP 223 is right and fits 36 IV combos; bars of another Drifblim (swiped to) don't fit it.
        val input = CalcInput("drifblim", 223, 82, 400)
        assertTrue(calc.calculate(input)!!.combos.isNotEmpty())
        assertNull(AppraisalFit.fit(calc, input, calc.calculate(input), Appraisal(15, 15, 15), Levels.L50))
    }

    @Test
    fun anAppraisalIsFoundAgainWhenTheCpComesOutGarbledAnotherWay() {
        // Appraised while the CP read as 29 (worked out as 223); swiped away and back, it read as 290.
        val w = 7.34
        val stored = AppraisalFit.memoryKeys(CalcInput("drifblim", 29, 82, 400), w) + AppraisalFit.memoryKeys(CalcInput("drifblim", 223, 82, 400), w)
        val back = CalcInput("drifblim", 290, 82, 400)
        assertTrue(AppraisalFit.recallKeys(back, w, cpFits = false).any { it in stored })
        // The garbled CP is no hindrance to the fit itself.
        assertEquals(223, AppraisalFit.fit(calc, back, calc.calculate(back), bars, Levels.L50)!!.first.input.cp)
        // A CP that fits is looked up under itself only; a hidden one under the weight.
        assertEquals(listOf("drifblim|223|82"), AppraisalFit.recallKeys(CalcInput("drifblim", 223, 82, 400), w, cpFits = true))
        assertTrue(AppraisalFit.recallKeys(back.copy(cp = null), w, cpFits = true).any { it in stored })
        // Without the weight nothing new is found: two of a kind can share species, HP and stardust.
        assertEquals(listOf("drifblim|290|82"), AppraisalFit.recallKeys(back, null, cpFits = false))
    }

    @Test
    fun theLevelArcMustAgree() {
        // The bars give Lv 4; an arc read at Lv 10 says they belong to another Drifblim.
        val input = CalcInput("drifblim", 290, 82, 400, levelHint = listOf(Levels.indexOf(10.0)))
        assertNull(AppraisalFit.fit(calc, input, calc.calculate(input), bars, Levels.L50))
        val agrees = input.copy(levelHint = listOf(Levels.indexOf(4.0)))
        assertEquals(223, AppraisalFit.fit(calc, agrees, calc.calculate(agrees), bars, Levels.L50)!!.first.input.cp)
        // Half a level off is the arc's own error; a whole level off is too far.
        val near = input.copy(levelHint = listOf(Levels.indexOf(4.5)))
        assertEquals(223, AppraisalFit.fit(calc, near, calc.calculate(near), bars, Levels.L50)!!.first.input.cp)
        val far = input.copy(levelHint = listOf(Levels.indexOf(5.0)))
        assertNull(AppraisalFit.fit(calc, far, calc.calculate(far), bars, Levels.L50))
    }

    @Test
    fun anArcHalfALevelOffDoesntRefuseTheBars() {
        // Its page (600 stardust) and behind the Appraise screen (stardust dropped), arc read as Lv 5.5.
        for (dust in listOf(600, null)) {
            val input = CalcInput("drifblim", 45, 101, dust, levelHint = listOf(Levels.indexOf(5.5)))
            assertTrue(calc.calculate(input)!!.combos.isEmpty())
            val (result, combo) = AppraisalFit.fit(calc, input, calc.calculate(input), Appraisal(4, 3, 4), Levels.L50)!!
            assertEquals("dust $dust", 345, result.input.cp)
            assertEquals("6", Levels.label(combo.level))
        }
    }

    @Test
    fun theArcOnItsScreenshot() {
        // The real screen (only where screens/ has it): the dot sits at 125.1°, where the
        // calibration puts Lv 6 at 123.1°, so it reads as Lv 5.5.
        val img = TestData.screenshot("drifblim_hat")
        val arc = ArcReader.read(img.px, img.w, img.h)!!
        val near = ArcReader.levelsNear(gd, arc, Levels.L50)
        assertEquals(listOf(5.5), near.map { Levels.level(it) })
        val input = CalcInput("drifblim", 45, 101, 600, levelHint = near)
        assertEquals(345, AppraisalFit.fit(calc, input, calc.calculate(input), Appraisal(4, 3, 4), Levels.L50)!!.first.input.cp)
    }
}
