package com.ivlens.app

import com.ivlens.app.core.Analyzer
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.League
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's Sneasel (15/13/15 at L25.5, CP 1485, 2026-10-17) said "PVP · Ultra #8" and showed
 * Sneasler. A maxed Sneasel is about 2,310 CP, nowhere near Ultra League's 2,500, and only a
 * Hisuian Sneasel becomes Sneasler.
 */
class LeagueReachTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val ranker = PvpRanker(gd)
    private val sneasel = IvCombo(Levels.indexOf(25.5), 15, 13, 15)
    private val result by lazy { calc.calculate(CalcInput("sneasel", 1485, 108, 4000))!! }

    @Test
    fun regionalFormsAreTheirOwnSpecies() {
        assertEquals(listOf("weavile"), gd.byId["sneasel"]!!.evo)
        assertEquals(listOf("sneasler"), gd.byId["sneasel_hisuian"]!!.evo)
        assertEquals(listOf("ninetales"), gd.byId["vulpix"]!!.evo)
        assertEquals(listOf("ice"), gd.byId["vulpix_alolan"]!!.types)
        assertEquals(listOf("clodsire"), gd.byId["wooper_paldean"]!!.evo)
        // A plain Darmanitan used to be filed under the Galarian (Ice) one.
        assertEquals(listOf("fire"), gd.byId["darmanitan_standard"]!!.types)
    }

    @Test
    fun noPvpVerdictForALeagueItCantReach() {
        val v = VerdictEngine(gd, ranker).judge(result, sneasel, VerdictRules())!!
        val all = listOf(v.primary) + v.also
        assertFalse(all.any { it.kind == VerdictKind.PVP })
        // Its real use: an Ice raider as Weavile.
        assertEquals(VerdictKind.RAIDS, v.primary.kind)
        assertEquals("Evolve to Weavile", v.primary.steps.first())
    }

    @Test
    fun cardShowsTooSmall() {
        val a = Analyzer(gd, ranker).analyze(result, sneasel)
        assertEquals(listOf("sneasel", "weavile"), a.forms.map { it.species.id })
        val own = a.forms.first().leagues
        val ultra = own.first { it.league == League.ULTRA }
        assertTrue(ultra.tooSmall)
        assertEquals("Too small", ultra.rankText())
        assertTrue(ultra.maxCp in 2300..2330)
        assertTrue(own.first { it.league == League.MASTER }.tooSmall)
        // No "UL best" power-up target for a league it can't compete in.
        assertFalse(a.powerUps.any { it.label.startsWith("UL") })
    }

    @Test
    fun masterLeagueNeedsSomethingBig() {
        assertTrue(League.MASTER.canCompete(3767)) // Togekiss, fully powered
        assertFalse(League.MASTER.canCompete(3075)) // Venusaur
        assertTrue(League.ULTRA.canCompete(2493)) // Talonflame, just under the cap
        assertFalse(League.ULTRA.canCompete(2310))
    }
}
