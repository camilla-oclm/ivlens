package com.ivlens.app

import com.ivlens.app.core.Analyzer
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.League
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PowerUpCosts
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val ranker = PvpRanker(gd)

    @Test
    fun costTotalsMatchKnownValues() {
        val toForty = PowerUpCosts.between(0, Levels.L40, Variant.NORMAL)
        assertEquals(270_000, toForty.stardust)
        assertEquals(304, toForty.candy)
        val toFifty = PowerUpCosts.between(Levels.L40, Levels.L50, Variant.NORMAL)
        assertEquals(250_000, toFifty.stardust)
        assertEquals(296, toFifty.candyXl)
        assertEquals(listOf(76, 77, 78, 79), PowerUpCosts.levelsForDust(10_000, Variant.NORMAL))
        assertEquals(3000, PowerUpCosts.dust(36, Variant(shadow = true)))
    }

    private fun check(id: String, cp: Int, hp: Int, dust: Int, combos: Int, minIv: Int, maxIv: Int) {
        val r = calc.calculate(CalcInput(id, cp, hp, dust))!!
        assertEquals("$id combos", combos, r.combos.size)
        assertEquals("$id min", minIv, r.ivMin)
        assertEquals("$id max", maxIv, r.ivMax)
    }

    @Test
    fun screenshotsGiveConsistentIvs() {
        check("gyarados", 2923, 171, 6000, 6, 69, 84)
        check("dialga", 2309, 130, 2500, 4, 78, 80)
        check("rayquaza", 2666, 149, 4000, 10, 31, 78)
        check("mewtwo_armored", 2694, 167, 5000, 2, 82, 87)
        check("snorlax", 2782, 255, 6000, 4, 78, 84)
        check("alakazam", 2643, 119, 6000, 2, 84, 89)
        assertTrue(calc.calculate(CalcInput("mewtwo", 2694, 167, 5000))!!.combos.isEmpty())
    }

    @Test
    fun levelHintNarrowsOnlyWhenConsistent() {
        val wide = calc.calculate(CalcInput("rayquaza", 2666, 149, 4000))!!
        val narrow = calc.calculate(CalcInput("rayquaza", 2666, 149, 4000, levelHint = listOf(Levels.indexOf(25.0))))!!
        assertTrue(narrow.usedArc)
        assertTrue(narrow.combos.size < wide.combos.size)
        assertTrue(narrow.combos.all { it.level == Levels.indexOf(25.0) })
        val wrong = calc.calculate(CalcInput("rayquaza", 2666, 149, 4000, levelHint = listOf(Levels.indexOf(40.0))))!!
        assertEquals(wide.combos.size, wrong.combos.size)
    }

    @Test
    fun pvpRanksAreSane() {
        val medicham = gd.byId["medicham"]!!
        // Before level 50 existed, a perfect Medicham was the best Great League one.
        assertEquals(1, ranker.table(medicham, League.GREAT, Levels.L40)[15, 15, 15]!!.rank)
        assertTrue(ranker.table(medicham, League.GREAT)[15, 15, 15]!!.rank > 1)
        val ml = ranker.table(gd.byId["dialga"]!!, League.MASTER)
        assertEquals(1, ml[15, 15, 15]!!.rank)
        assertEquals(Levels.L50, ml[15, 15, 15]!!.level)
        val gl = ranker.table(gd.byId["azumarill"]!!, League.GREAT)
        val best = gl.best!!
        assertTrue(best.cp <= 1500)
        assertEquals(100.0, best.percentOfBest, 1e-9)
        assertTrue(gl[0, 0, 0]!!.rank > 1)
    }

    @Test
    fun analysisCoversEvolutionsAndPowerUps() {
        val r = calc.calculate(CalcInput("dratini", 500, 60, null))
        assertNotNull(r)
        val gyara = calc.calculate(CalcInput("gyarados", 2923, 171, 6000))!!
        val a = Analyzer(gd, ranker).analyze(gyara, gyara.combos.first())
        assertEquals("gyarados", a.forms.first().species.id)
        assertTrue(a.forms.first().leagues.first { it.league == League.GREAT }.tooHigh)
        assertTrue(a.powerUps.any { it.level == Levels.L40 })
        val karp = gd.byId["magikarp"]!!
        assertTrue(gd.evolutionChain(karp).any { it.first.id == "gyarados" })
    }
}
