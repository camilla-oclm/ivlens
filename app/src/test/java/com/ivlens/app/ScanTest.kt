package com.ivlens.app

import com.ivlens.app.TestData.at
import com.ivlens.app.TestData.ocrLines
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.ArcReader
import com.ivlens.app.scan.ScanOutcome
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenParser
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTest {
    private val gd = TestData.gd
    private val matcher = SpeciesMatcher(gd)
    private val resolver = ScanResolver(gd, IvCalculator(gd), matcher)

    /** Levels each screenshot's Pokémon can be at, from CP/HP/stardust alone. */
    private val truth = mapOf(
        "gyarados" to listOf(31.5, 32.0, 32.5),
        "dragonite" to listOf(24.0, 24.5),
        "dialga" to listOf(20.5),
        "rayquaza" to listOf(25.0, 25.5, 26.0, 26.5),
        "mewtwo" to listOf(30.0),
        "snorlax" to listOf(32.0, 32.5),
        "alakazam" to listOf(31.5, 32.0),
    )

    @Test
    fun arcFindsTheLevelDot() {
        for ((name, levels) in truth) {
            val img = TestData.screenshot(name)
            val arc = ArcReader.read(img.px, img.w, img.h)
            assertNotNull("$name arc", arc)
            arc!!
            val near = ArcReader.levelsNear(gd, arc, Levels.L50).map { Levels.level(it) }
            println("$name: r=%.4f cy=%.4f coverage=%d dot=%.2f° found=%b -> %s (truth %s)".format(
                arc.geometry.rRatio, arc.geometry.cyRatio, arc.coverage, arc.dotAngle, arc.dotFound, near, levels))
            assertTrue("$name arc levels $near vs $levels", near.any { it in levels } || System.getenv("CALIBRATE") != null)
        }
    }

    @Test
    fun matcherHandlesOcrNoise() {
        assertEquals("gyarados", matcher.matchName("Gyarados").single().id)
        assertEquals("gyarados", matcher.matchName("Gyarad0s").single().id)
        assertTrue(matcher.matchName("Mewtwo").map { it.id }.containsAll(listOf("mewtwo", "mewtwo_armored")))
        assertEquals(listOf("FAMILY_RAYQUAZA"), matcher.familiesForCandy("RAY QUAZA"))
        assertTrue(matcher.familiesForCandy("MAGIKARP").contains(gd.byId["gyarados"]!!.fam))
    }

    @Test
    fun parsesMlKitStyleGyarados() {
        val lines = TestData.mlkitLike(
            "9:41" to at(78, 45, 70, 26),
            "100" to at(850, 45, 50, 22),
            "CP2923" to at(430, 145, 240, 62),
            "Gyarados" to at(460, 850, 260, 56),
            "86 / 171 HP" to at(460, 945, 140, 22),
            "256.09kg" to at(196, 1092, 175, 38),
            "WEIGHT" to at(196, 1141, 108, 18),
            "WATER / FLYING" to at(490, 1141, 228, 18),
            "190,904" to at(205, 1292, 150, 36),
            "STARDUST" to at(205, 1342, 138, 18),
            "5" to at(487, 1292, 20, 34),
            "MAGIKARP" to at(487, 1342, 145, 18),
            "CANDY" to at(487, 1373, 90, 18),
            "GYARADOS MEGA" to at(768, 1342, 235, 18),
            "ENERGY" to at(768, 1373, 100, 18),
            "POWER UP" to at(248, 1487, 210, 30),
            "6,000" to at(570, 1487, 90, 32),
            "6" to at(756, 1487, 20, 32),
            "MEGA" to at(290, 1625, 110, 30),
            "EVOLVE" to at(310, 1665, 150, 30),
            "300" to at(562, 1645, 60, 30),
            "GYMS & RAIDS" to at(310, 1913, 180, 20),
        )
        val r = ScreenParser.parse(lines, 1080, 2340)
        assertEquals(2923, r.cp.first())
        assertEquals(171, r.hp.first())
        assertEquals(6000, r.dust.first())
        assertEquals("Gyarados", r.names.first())
        assertEquals("MAGIKARP", r.candyName)
        val img = TestData.screenshot("gyarados")
        val out = resolver.resolve(r, ArcReader.read(img.px, img.w, img.h), trainerLevel = 50)
        assertNull(out.problem)
        assertEquals("gyarados", out.result!!.species.id)
        println("gyarados -> ${out.result!!.combos.map { it.ivText + "@" + Levels.label(it.level) }} arc=${out.result!!.usedArc}")
    }

    @Test
    fun hiddenCpAsksForInput() {
        val lines = TestData.mlkitLike(
            "18" to at(515, 145, 70, 56),
            "Dragonite" to at(460, 850, 270, 56),
            "146 / 146 HP" to at(460, 945, 150, 22),
            "190,904" to at(270, 1274, 150, 36),
            "STARDUST" to at(268, 1325, 138, 18),
            "DRATINI CANDY" to at(653, 1325, 210, 18),
            "DRATINI CANDY XL" to at(270, 1478, 250, 18),
            "POWER UP" to at(248, 1604, 210, 30),
            "3,500" to at(570, 1604, 90, 32),
            "3" to at(756, 1604, 20, 32),
        )
        val r = ScreenParser.parse(lines, 1080, 2340)
        assertTrue(r.cp.isEmpty())
        val out = resolver.resolve(r, null, 50)
        assertEquals(ScanOutcome.Problem.NO_CP, out.problem)
        assertEquals("dragonite", out.input.speciesId)
        assertEquals(3500, out.input.dust)
        assertEquals(146, out.input.hp)
    }

    @Test
    fun formIsPickedByWhatFits() {
        val lines = TestData.mlkitLike(
            "c 2694" to at(440, 145, 240, 62),
            "Mewtwo" to at(460, 850, 245, 56),
            "167 / 167 HP" to at(460, 945, 150, 22),
            "STARDUST" to at(268, 1325, 138, 18),
            "MEWTWO CANDY" to at(653, 1325, 230, 18),
            "POWER UP" to at(248, 1604, 210, 30),
            "5,000" to at(570, 1604, 90, 32),
            "4" to at(756, 1604, 20, 32),
        )
        val out = resolver.resolve(ScreenParser.parse(lines, 1080, 2340), null, 50)
        assertNull(out.problem)
        assertEquals("mewtwo_armored", out.result!!.species.id)
        assertEquals(2, out.result!!.combos.size)
    }

    @Test
    fun tesseractReadingsOfRealScreens() {
        // Tesseract is much weaker than ML Kit on these screens; this checks the parser copes
        // with noisy text. CP is unreadable by Tesseract on Dragonite and Rayquaza.
        val expectCp = mapOf("gyarados" to 2923, "dialga" to 2309, "mewtwo" to 2694, "alakazam" to 2643)
        val expectHp = mapOf("gyarados" to null, "dragonite" to 146, "dialga" to 130, "rayquaza" to 149,
            "mewtwo" to 167, "snorlax" to 255, "alakazam" to 119)
        for (name in truth.keys) {
            val fx = TestData.tesseract(name)
            val r = ScreenParser.parse(fx.ocrLines(), fx.w, fx.h)
            val img = TestData.screenshot(name)
            val out = resolver.resolve(r, ArcReader.read(img.px, img.w, img.h), 50)
            println("$name: cp=${r.cp} hp=${r.hp} dust=${r.dust} names=${r.names} candy=${r.candyName} -> " +
                "${out.result?.species?.id} ${out.problem ?: ""} ${out.result?.combos?.map { it.ivText + "@" + Levels.label(it.level) }}")
            expectCp[name]?.let { assertEquals("$name cp", it, r.cp.firstOrNull()) }
            expectHp[name]?.let { assertEquals("$name hp", it, r.hp.firstOrNull()) }
            if (name in expectCp && expectHp[name] != null) {
                assertNull("$name problem", out.problem)
                assertTrue(out.result!!.combos.all { Levels.level(it.level) in truth[name]!! })
            }
        }
    }
}
