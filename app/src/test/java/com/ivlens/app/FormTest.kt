package com.ivlens.app

import com.ivlens.app.TestData.ocrLines
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import com.ivlens.app.scan.AppraisalReader
import com.ivlens.app.scan.OcrLine
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenParser
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Forms that share a name. The owner's Hisuian Qwilfish (CP 889, HP 90) on the Appraise screen,
 * 2026-10-17: plain Qwilfish fits that CP and HP too, so 1.7 took it for one and said its bars
 * (14/11/11) didn't fit. The type labels tell the forms apart, and so do the bars.
 */
class FormTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val resolver = ScanResolver(gd, calc, SpeciesMatcher(gd))
    private val img by lazy { TestData.screenshot("qwilfish_hisuian_appraise") }
    private val bars = Appraisal(14, 11, 11)

    private fun qwilfishLines(withTypes: Boolean): List<OcrLine> {
        // Tesseract misses the pale type labels; the leader covers the end of POISON.
        val extra = if (withTypes) listOf(OcrLine("DARK / POIS", 420, 1318, 625, 1348)) else emptyList()
        return TestData.tesseract("qwilfish_hisuian_appraise").ocrLines() + extra
    }

    @Test
    fun readsQwilfishsBars() {
        assertEquals(bars, AppraisalReader.find(img.px, img.w, img.h)?.appraisal)
    }

    @Test
    fun typeLabelsPickTheForm() {
        val reading = ScreenParser.parse(qwilfishLines(withTypes = true), img.w, img.h)
        // "POIS" is cut off, so only DARK counts; plain Qwilfish is Water / Poison.
        assertEquals(listOf("dark"), reading.types)
        val outcome = resolver.resolve(reading, null, 50)
        assertEquals("qwilfish_hisuian", outcome.input.speciesId)
        val fit = AppraisalFit.fit(calc, outcome.input, outcome.result, bars, Levels.L50)!!
        assertEquals(Levels.indexOf(15.0), fit.second.level)
    }

    @Test
    fun barsPickTheFormWhenTypesAreHidden() {
        val outcome = resolver.resolve(ScreenParser.parse(qwilfishLines(withTypes = false), img.w, img.h), null, 50)
        // Without the types, plain Qwilfish comes first and the bars don't fit it...
        assertEquals("qwilfish", outcome.input.speciesId)
        assertNull(AppraisalFit.fit(calc, outcome.input, outcome.result, bars, Levels.L50))
        // ...but they fit its Hisuian form, at one level only.
        val (r, combo) = AppraisalFit.fitForms(calc, outcome.input, outcome.result, bars, Levels.L50, outcome.candidates)!!
        assertEquals("qwilfish_hisuian", r.species.id)
        assertEquals(Levels.indexOf(15.0), combo.level)
        // Bars that fit no form are still refused.
        assertNull(AppraisalFit.fitForms(calc, outcome.input, outcome.result, Appraisal(3, 4, 5), Levels.L50, outcome.candidates))
    }

    @Test
    fun typeLabelsOnOtherScreens() {
        val expected = mapOf(
            "gyarados" to listOf("water", "flying"),
            "venusaur" to listOf("grass", "poison"),
            "dragonite" to listOf("dragon", "flying"),
            "rayquaza" to listOf("dragon", "flying"),
            "snorlax" to listOf("normal"),
            // Scrolled down: the labels are still above the moves, which must not count.
            "gyarados_moves" to listOf("water", "flying"),
            "rayquaza_moves" to listOf("dragon", "flying"),
            "mewtwo_moves" to listOf("psychic"),
            "kubfu_moves" to listOf("fighting"),
        )
        for ((name, types) in expected) {
            val fx = TestData.tesseract(name)
            assertEquals(name, types, ScreenParser.parse(fx.ocrLines(), fx.w, fx.h).types)
        }
    }
}
