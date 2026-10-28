package com.ivlens.app

import com.ivlens.app.TestData.at
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PowerUpCosts
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.RaidRanker
import com.ivlens.app.core.Variant
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictRules
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenParser
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaidTest {
    private val gd = TestData.gd
    private val raids = RaidRanker(gd)
    private val calc = IvCalculator(gd)
    private val engine = VerdictEngine(gd, PvpRanker(gd), raids)
    private val resolver = ScanResolver(gd, calc, SpeciesMatcher(gd))

    @Test
    fun leaderboardsLookSensible() {
        for (t in listOf("psychic", "water", "dragon", "fighting")) {
            val top = raids.board(t).take(8).joinToString { (if (it.shadow) "S." else "") + it.species.name + " (" + it.fast.name + "/" + it.charged.name + ")" }
            println("$t: $top")
        }
        val dragon = raids.board("dragon").take(15).map { it.species.id }
        assertTrue(dragon.contains("rayquaza"))
        val psychic = raids.board("psychic").take(10).map { it.species.id }
        assertTrue(psychic.contains("mewtwo"))
    }

    @Test
    fun shadowStardustRoundsUp() {
        // From the Shadow Fomantis screenshot: 800 x 1.2 shows as 961.
        assertEquals(961, PowerUpCosts.dust(Levels.indexOf(7.0), Variant(shadow = true)))
        val r = calc.calculate(CalcInput("fomantis", 138, 45, 961, shadow = true))!!
        println("fomantis: ${r.combos.map { it.ivText + "@" + Levels.label(it.level) }}")
        assertTrue(r.combos.isNotEmpty())
    }

    @Test
    fun readsMovesShadowAndSkipsOtherButtonCosts() {
        // Shadow Fomantis, scrolled: PURIFY 3,000 sits above POWER UP 961.
        val fomantis = TestData.mlkitLike(
            "195,439" to at(205, 152, 150, 36),
            "STARDUST" to at(205, 203, 138, 18),
            "FOMANTIS CANDY" to at(487, 203, 240, 18),
            "PURIFY" to at(248, 346, 140, 30),
            "3,000" to at(552, 346, 90, 32),
            "3" to at(730, 346, 20, 32),
            "POWER UP" to at(248, 480, 210, 30),
            "961" to at(555, 480, 60, 32),
            "2" to at(756, 480, 20, 32),
            "EVOLVE" to at(308, 613, 150, 30),
            "50" to at(737, 613, 50, 32),
            "GYMS & RAIDS" to at(310, 749, 180, 20),
            "TRAINER BATTLES" to at(612, 749, 220, 20),
            "Fury Cutter" to at(235, 850, 210, 40),
            "4+1" to at(815, 850, 70, 36),
            "SHADOW BONUS" to at(310, 912, 210, 22),
            "Frustration" to at(230, 990, 200, 40),
            "10+2" to at(805, 990, 90, 36),
            "SHADOW BONUS" to at(310, 1054, 210, 22),
            "NEW ATTACK" to at(248, 1162, 260, 30),
            "60,000" to at(545, 1162, 110, 32),
            "CAUGHT AFTER DEFEATING GRUNT" to at(432, 1386, 505, 22),
        )
        val r = ScreenParser.parse(fomantis, 1080, 2340)
        assertEquals(listOf(961), r.dust)
        assertTrue(r.shadow)
        assertTrue(r.pokemonPage)
        assertTrue(r.hp.isEmpty())
        assertEquals(listOf("Fury Cutter", "Frustration"), r.moves)

        // Gyarados scrolled down: weight and moves, no HP.
        val gyara = TestData.mlkitLike(
            "256.09kg" to at(196, 52, 175, 38),
            "WEIGHT" to at(196, 100, 108, 18),
            "WATER / FLYING" to at(490, 100, 228, 18),
            "5.72m" to at(755, 52, 115, 38),
            "POWER UP" to at(248, 446, 210, 30),
            "6,000" to at(570, 446, 90, 32),
            "MEGA" to at(290, 583, 110, 30),
            "EVOLVE" to at(310, 626, 150, 30),
            "300" to at(562, 605, 60, 30),
            "GYMS & RAIDS" to at(310, 873, 180, 20),
            "TRAINER BATTLES" to at(612, 873, 220, 20),
            "Waterfall" to at(215, 972, 170, 40),
            "13" to at(828, 972, 40, 36),
            "Outrage" to at(205, 1053, 150, 40),
            "110" to at(816, 1053, 70, 36),
            "NEW ATTACK" to at(248, 1162, 260, 30),
            "10,000" to at(545, 1162, 110, 32),
        )
        val g = ScreenParser.parse(gyara, 1080, 2340)
        assertEquals(256.09, g.weightKg!!, 1e-9)
        assertEquals(5.72, g.heightM!!, 1e-9)
        assertEquals(listOf("Waterfall", "Outrage"), g.moves)
        assertEquals(listOf(6000), g.dust)
        assertTrue(g.pokemonPage)
    }

    @Test
    fun raidVerdictsUseMoves() {
        val rules = VerdictRules()
        fun judge(input: CalcInput, combo: IvCombo) = engine.judge(calc.calculate(input)!!, combo, rules)!!

        val rr = raids.rankOf(gd.byId["rayquaza"]!!, false, IvCombo(Levels.indexOf(25.0), 12, 12, 11), "DRAGON_TAIL", listOf("OUTRAGE"))
        println("rayquaza rank: ${rr?.typeLabel} #${rr?.rank}; top dragon: " + raids.board("dragon").take(25).mapIndexed { i, e -> "${i + 1}." + (if (e.shadow) "S." else "") + e.species.name + "(" + e.charged.name + ")" }.joinToString(" "))
        val ray = judge(CalcInput("rayquaza", 2666, 149, 4000, fast = "DRAGON_TAIL", charged = listOf("OUTRAGE")), IvCombo(Levels.indexOf(25.0), 12, 12, 11))
        println("rayquaza DT/Outrage: ${ray.primary} also=${ray.also}")
        assertEquals(VerdictKind.RAIDS, ray.primary.kind)
        assertTrue(ray.primary.title.contains("Dragon"))

        for ((id, combo) in listOf("gyarados" to IvCombo(Levels.indexOf(31.5), 14, 9, 15), "snorlax" to IvCombo(Levels.indexOf(32.0), 13, 10, 15), "mewtwo_armored" to IvCombo(Levels.indexOf(30.0), 14, 10, 15))) {
            val sp = gd.byId[id]!!
            println("$id best TM: ${raids.bestWithTm(sp, false, combo)?.let { it.typeLabel + " #" + it.rank + " " + it.fast.name + "/" + it.charged.name }}")
        }
        val gyaraInput = CalcInput("gyarados", 2923, 171, 6000, fast = "WATERFALL", charged = listOf("OUTRAGE"))
        val gyara = judge(gyaraInput, IvCombo(Levels.indexOf(31.5), 14, 9, 15))
        println("gyarados Waterfall/Outrage: ${gyara.primary} also=${gyara.also}")
        val gyaraUnknown = judge(gyaraInput.copy(fast = null, charged = emptyList()), IvCombo(Levels.indexOf(31.5), 14, 9, 15))
        println("gyarados unknown moves: ${gyaraUnknown.primary} also=${gyaraUnknown.also}")

        val mewtwo = judge(CalcInput("mewtwo_armored", 2694, 167, 5000, fast = "CONFUSION", charged = listOf("DYNAMIC_PUNCH")), IvCombo(Levels.indexOf(30.0), 14, 10, 15))
        println("armored mewtwo Confusion/Dynamic Punch: ${mewtwo.primary} also=${mewtwo.also}")

        val snorlax = judge(CalcInput("snorlax", 2782, 255, 6000, fast = "PSYWAVE", charged = listOf("EARTHQUAKE")), IvCombo(Levels.indexOf(32.0), 13, 10, 15))
        println("snorlax Psywave/Earthquake: ${snorlax.primary} also=${snorlax.also}")

        val kubfu = judge(CalcInput("kubfu", 707, 86, 1900, fast = "ROCK_SMASH", charged = listOf("DYNAMIC_PUNCH")),
            calc.calculate(CalcInput("kubfu", 707, 86, 1900))!!.combos.first())
        println("kubfu: ${kubfu.primary} also=${kubfu.also}")
        assertNull(kubfu.also.firstOrNull { it.kind == VerdictKind.RAIDS && it.title.startsWith("Raids ·") && !it.detail.contains("as ") })
    }

    @Test
    fun resolverAttachesMovesAndShadow() {
        val lines = TestData.mlkitLike(
            "CP138" to at(430, 145, 190, 62),
            "Fomantis" to at(460, 850, 250, 56),
            "45 / 45 HP" to at(460, 945, 120, 22),
            "STARDUST" to at(205, 1342, 138, 18),
            "FOMANTIS CANDY" to at(487, 1342, 240, 18),
            "PURIFY" to at(248, 1486, 140, 30),
            "3,000" to at(552, 1486, 90, 32),
            "POWER UP" to at(248, 1620, 210, 30),
            "961" to at(555, 1620, 60, 32),
            "EVOLVE" to at(308, 1753, 150, 30),
            "GYMS & RAIDS" to at(310, 1889, 180, 20),
            "Fury Cutter" to at(235, 1985, 210, 40),
        )
        val out = resolver.resolve(ScreenParser.parse(lines, 1080, 2340), null, 50)
        assertNull(out.problem)
        assertTrue(out.input.shadow)
        assertEquals("FURY_CUTTER", out.input.fast)
        assertTrue(out.result!!.combos.isNotEmpty())
    }
}

class SecondBatchScreensTest {
    private val gd = TestData.gd
    private val resolver = ScanResolver(gd, IvCalculator(gd), SpeciesMatcher(gd))

    @Test
    fun tesseractReadsOfScrolledScreens() {
        for (n in listOf("gyarados_moves", "mewtwo_moves", "mewtwo_pvp_tab", "mewtwo_banner", "kubfu", "rayquaza_moves", "kubfu_moves", "fomantis_shadow", "fomantis_moves", "snorlax_moves")) {
            val fx = TestData.tesseract(n)
            val r = ScreenParser.parse(with(TestData) { fx.ocrLines() }, fx.w, fx.h)
            val out = resolver.resolve(r, null, 50)
            println("$n: cp=${r.cp} hp=${r.hp} dust=${r.dust} shadow=${r.shadow} page=${r.pokemonPage} kg=${r.weightKg} m=${r.heightM} moves=${r.moves} -> ${out.input.speciesId} ${out.problem ?: ""} fast=${out.input.fast} charged=${out.input.charged} combos=${out.result?.combos?.size}")
        }
    }
}

class ShadowArcFallbackTest {
    private val gd = TestData.gd
    private val resolver = ScanResolver(gd, IvCalculator(gd), SpeciesMatcher(gd))

    @Test
    fun arcStandsInWhenStardustIsMisread() {
        val img = TestData.screenshot("fomantis")
        val arc = com.ivlens.app.scan.ArcReader.read(img.px, img.w, img.h)
        println("fomantis arc: ${arc?.dotAngle} -> ${arc?.let { com.ivlens.app.scan.ArcReader.levelsNear(gd, it, Levels.L50).map { Levels.label(it) } }}")
        val fx = TestData.tesseract("fomantis_shadow")
        val r = ScreenParser.parse(with(TestData) { fx.ocrLines() }, fx.w, fx.h)
        val out = resolver.resolve(r, arc, 50)
        println("fomantis with arc: ${out.problem} shadow=${out.input.shadow} dust=${out.input.dust} combos=${out.result?.combos?.map { it.ivText + "@" + Levels.label(it.level) }}")
        val k = TestData.screenshot("kubfu")
        val ka = com.ivlens.app.scan.ArcReader.read(k.px, k.w, k.h)
        println("kubfu arc: ${ka?.dotAngle} -> ${ka?.let { com.ivlens.app.scan.ArcReader.levelsNear(gd, it, Levels.L50).map { Levels.label(it) } }} (stardust 1,900 means 15-16.5)")
    }
}
