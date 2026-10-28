package com.ivlens.app

import com.ivlens.app.TestData.ocrLines
import com.ivlens.app.core.Analyzer
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.Gender
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictRules
import com.ivlens.app.scan.GenderReader
import com.ivlens.app.scan.ScreenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ♂ / ♀ icon, and what it changes. The owner's Kirlia (♀, 15/14/13 at L29) showed Gallade
 * rows on its card, but only a male Kirlia can become one.
 */
class GenderTest {
    private val gd = TestData.gd

    private fun genderOn(name: String): Gender? {
        val img = TestData.screenshot(name)
        // Anchored on the name line where Tesseract read it, as the app does with ML Kit.
        val nameLine = runCatching { TestData.tesseract(name) }.getOrNull()
            ?.let { ScreenParser.parse(it.ocrLines(), it.w, it.h).nameLine }
        val (l, t, r, b) = GenderReader.region(img.w, img.h, nameLine)
        val part = IntArray((r - l) * (b - t)) { i -> img.px[(t + i / (r - l)) * img.w + l + i % (r - l)] }
        return GenderReader.findIn(part, r - l, img.w)
    }

    @Test
    fun readsTheIcon() {
        for (n in listOf("dragonite", "kubfu", "snorlax", "venusaur")) assertEquals(n, Gender.MALE, genderOn(n))
        // The Appraise screens tint the page, which hides the bottom of the ♀.
        for (n in listOf("alakazam", "fomantis", "gyarados", "cloyster_appraise", "zoroark_appraise", "qwilfish_hisuian_appraise")) {
            assertEquals(n, Gender.FEMALE, genderOn(n))
        }
        for (n in listOf("dialga", "metagross2", "mewtwo", "rayquaza")) assertEquals(n, null, genderOn(n))
    }

    private val calc = IvCalculator(gd)
    private val kirlia = IvCombo(Levels.indexOf(29.0), 15, 14, 13)
    private fun kirliaAs(g: Gender?) = calc.calculate(CalcInput("kirlia", 791, 92, 5000, gender = g))!!

    @Test
    fun femaleKirliaCantBecomeGallade() {
        val forms = Analyzer(gd, PvpRanker(gd)).analyze(kirliaAs(Gender.FEMALE), kirlia).forms.map { it.species.id }
        assertEquals(listOf("kirlia", "gardevoir"), forms)
        val male = Analyzer(gd, PvpRanker(gd)).analyze(kirliaAs(Gender.MALE), kirlia).forms.map { it.species.id }
        assertTrue("gallade" in male && "gardevoir" in male)
        assertTrue("gallade" in Analyzer(gd, PvpRanker(gd)).analyze(kirliaAs(null), kirlia).forms.map { it.species.id })
    }

    @Test
    fun cardSaysWhatToDo() {
        val v = VerdictEngine(gd, PvpRanker(gd)).judge(kirliaAs(Gender.FEMALE), kirlia, VerdictRules())!!
        assertEquals(VerdictKind.RAIDS, v.primary.kind)
        assertEquals(
            listOf(
                "Evolve to Gardevoir",
                "It needs Charm + Dazzling Gleam: use TMs if evolving doesn't give them",
                "Power up to Lv 40 for raids",
            ),
            v.primary.steps,
        )
        assertFalse(v.primary.steps.any { "Gallade" in it })
    }
}
