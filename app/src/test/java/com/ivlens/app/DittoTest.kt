package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.Stats
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictRules
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import com.ivlens.app.scan.MoveMatcher
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenReading
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The owner's Ditto (CP 668, HP 105, 5,000 stardust, bars 11/13/10 at L30; 2026-10-24) was read as no
 * Pokémon at all (NO_SPECIES), so its bars showed CHECK · BARS. PvPoke marks Ditto unreleased
 * (it can't battle normally: Transform and Struggle), so it wasn't in the game data.
 */
class DittoTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val resolver = ScanResolver(gd, calc, SpeciesMatcher(gd))

    private val reading = ScreenReading(
        cp = listOf(668), hp = listOf(105), dust = listOf(5000), names = listOf("Ditto"),
        candyName = "DITTO", lucky = false, types = listOf("normal"), pokemonPage = true,
    )

    @Test
    fun dittoIsRead() {
        val outcome = resolver.resolve(reading, null, 50)
        assertNull(outcome.problem)
        assertEquals("ditto", outcome.input.speciesId)
    }

    @Test
    fun itsBarsFitAndGiveAnAnswer() {
        val outcome = resolver.resolve(reading, null, 50)
        val (result, combo) = AppraisalFit.fit(calc, outcome.input, outcome.result, Appraisal(11, 13, 10), Levels.L50)!!
        assertEquals(Levels.indexOf(30.0), combo.level)
        // 34/45 = 76%, under the 82% keep line, and no league use.
        val v = VerdictEngine(gd, PvpRanker(gd)).judge(result, combo, VerdictRules())!!
        assertFalse(v.pendingMoves)
        assertEquals(VerdictKind.TRANSFER, v.primary.kind)
    }

    @Test
    fun aPerfectDittoIsNoRaiderAndNeverWaitsForMoves() {
        // Attack 15 gets past the raid verdict's IV check, so its moves are really looked at.
        val sp = gd.byId.getValue("ditto")
        val combo = IvCombo(Levels.L40, 15, 15, 15)
        val m = gd.cpm[combo.level]
        val r = calc.calculate(CalcInput("ditto", Stats.cp(sp, m, 15, 15, 15), Stats.hp(sp, m, 15)))!!
        val v = VerdictEngine(gd, PvpRanker(gd)).judge(r, combo, VerdictRules())!!
        assertFalse(v.pendingMoves)
        assertFalse(v.primary.kind == VerdictKind.RAIDS || v.also.any { it.kind == VerdictKind.RAIDS })
    }

    @Test
    fun transformIsItsFastMove() {
        // PvPoke gives Transform no energy, so before 1.19 it was filed as a charged move.
        val (fast, charged) = MoveMatcher(gd).read(listOf("Transform", "Struggle"), gd.byId["ditto"])
        assertEquals("TRANSFORM", fast?.id)
        assertEquals(listOf("STRUGGLE"), charged.map { it.id })
    }
}
