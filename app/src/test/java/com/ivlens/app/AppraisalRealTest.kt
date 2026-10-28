package com.ivlens.app

import com.ivlens.app.TestData.ocrLines
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.AppraisalReader
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenParser
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's Cloyster on the real Appraise screen (Instinct leader, tinted page behind). */
class AppraisalRealTest {
    private val gd = TestData.gd

    @Test
    fun readsCloystersBars() {
        val img = TestData.screenshot("cloyster_appraise")
        // The bars are found by their look alone; the text is only for the page behind.
        val lines = TestData.tesseract("cloyster_appraise").ocrLines()
        val ap = AppraisalReader.find(img.px, img.w, img.h)?.appraisal
        println("cloyster appraisal: $ap")
        assertEquals(15, ap!!.atk)
        assertEquals(15, ap.sta)

        // The page behind still shows CP, name and HP, so the appraisal can stand on its own.
        val reading = ScreenParser.parse(lines, img.w, img.h)
        println("behind: cp=${reading.cp} hp=${reading.hp} names=${reading.names} dust=${reading.dust}")
        val calc = IvCalculator(gd)
        val all = calc.calculate(CalcInput("cloyster", 1362, 88, null))!!
        val match = all.combos.filter { it.atk == ap.atk && it.def == ap.def && it.sta == ap.sta }
        println("CP 1362 / HP 88: ${all.combos.size} combos; appraisal fits ${match.map { it.ivText + " " + it.percent + "% Lv " + Levels.label(it.level) }}")
        assertEquals(1, match.size)
        val outcome = ScanResolver(gd, calc, SpeciesMatcher(gd)).resolve(reading, null, 50)
        assertTrue(outcome.input.speciesId == "cloyster")

        // What the app does with it: CP and HP from the page behind, no stardust visible.
        val fit = com.ivlens.app.scan.AppraisalFit.fit(calc, outcome.input, outcome.result, ap, Levels.L50)!!
        println("fit: ${fit.second.ivText} ${fit.second.percent}% Lv ${Levels.label(fit.second.level)}")
        assertEquals("15/7/15", fit.second.ivText)
        assertEquals(Levels.indexOf(19.0), fit.second.level)
        // Bars that don't belong to this Pokémon are refused.
        assertEquals(null, com.ivlens.app.scan.AppraisalFit.fit(calc, outcome.input, outcome.result, com.ivlens.app.scan.Appraisal(3, 4, 5), Levels.L50))
    }
}
