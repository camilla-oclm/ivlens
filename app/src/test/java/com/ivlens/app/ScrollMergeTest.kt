package com.ivlens.app

import com.ivlens.app.TestData.ocrLines
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.scan.OcrLine
import com.ivlens.app.scan.ScanOutcome
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenParser
import com.ivlens.app.scan.ScrollMerge
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** From the user's screenshots: scrolling down must not turn a good read into "CP hidden". */
class ScrollMergeTest {
    private val gd = TestData.gd
    private val resolver = ScanResolver(gd, IvCalculator(gd), SpeciesMatcher(gd))

    // Our chip and bubble as they sat on screen (1080x2340); the app ignores text under them.
    private val overlays = listOf(intArrayOf(64, 815, 745, 966), intArrayOf(912, 798, 1064, 950))

    private fun read(name: String): ScanOutcome {
        val fx = TestData.tesseract(name)
        // Tesseract misses Venusaur's thin CP over the green background; the phone's reader got it.
        val extra = if (name == "venusaur") listOf(OcrLine("CP1899", 366, 130, 640, 205)) else emptyList()
        val lines = (fx.ocrLines() + extra).filter { l -> overlays.none { o -> l.left < o[2] && l.right > o[0] && l.top < o[3] && l.bottom > o[1] } }
        val reading = ScreenParser.parse(lines, fx.w, fx.h)
        return resolver.resolve(reading, null, 50)
    }

    private fun describe(o: ScanOutcome) = "cp=${o.reading.cp} hp=${o.reading.hp} kg=${o.reading.weightKg} names=${o.reading.names} " +
        "moves=${o.reading.moves} page=${o.reading.pokemonPage} problem=${o.problem} combos=${o.result?.combos?.size}"

    @Test
    fun scrolledVenusaurAndMetagrossKeepTheirCp() {
        for ((top, scrolled, species) in listOf(
            Triple("venusaur", "venusaur_scrolled", "venusaur"),
            Triple("metagross2", "metagross2_scrolled", "metagross"),
        )) {
            val first = read(top)
            val second = read(scrolled)
            println("$top: ${describe(first)}")
            println("$scrolled: ${describe(second)}")
            assertEquals(species, first.input.speciesId)
            assertTrue("$top should read fully", first.problem == null && first.result!!.combos.isNotEmpty())
            // The scrolled page has the HP but no CP: before 1.4 this replaced the result with "CP hidden".
            assertTrue(second.reading.cp.isEmpty())
            assertTrue("$scrolled is the same Pokémon, scrolled", ScrollMerge.samePokemonScrolled(first.input, first.reading.weightKg, second))
            val (fast, charged) = com.ivlens.app.scan.MoveMatcher(gd).read(second.reading.moves, gd.byId[species])
            println("$scrolled moves: ${fast?.id} + ${charged.map { it.id }}")
            assertTrue("$scrolled fast move", fast != null && charged.isNotEmpty())
        }
    }

    @Test
    fun aDifferentPokemonIsNotMerged() {
        val venusaur = read("venusaur")
        val metagrossScrolled = read("metagross2_scrolled")
        println("v: ${describe(venusaur)}\nms: ${describe(metagrossScrolled)}")
        assertFalse(ScrollMerge.samePokemonScrolled(venusaur.input, venusaur.reading.weightKg, metagrossScrolled))
        val metagross = read("metagross2")
        val venusaurScrolled = read("venusaur_scrolled")
        assertFalse(ScrollMerge.samePokemonScrolled(metagross.input, metagross.reading.weightKg, venusaurScrolled))
    }

    @Test
    fun aFullReadIsNeverMerged() {
        val a = read("venusaur")
        assertFalse(ScrollMerge.samePokemonScrolled(a.input, a.reading.weightKg, a))
    }
}
