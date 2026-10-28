package com.ivlens.app

import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenReading
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's Minior (CP 296, HP 46, 600 stardust; 2026-10-20) was read as no Pokémon at all:
 * PvPoke still marks Minior unreleased, so it wasn't in the game data. Its numbers fit the Core
 * form's stats (218/131/155) and not the Meteor form's.
 */
class MiniorTest {
    private val gd = TestData.gd
    private val resolver = ScanResolver(gd, IvCalculator(gd), SpeciesMatcher(gd))

    @Test
    fun miniorIsReadAsTheCoreForm() {
        val reading = ScreenReading(
            cp = listOf(296), hp = listOf(46), dust = listOf(600), names = listOf("Minior"),
            candyName = "MINIOR", lucky = false, types = listOf("rock", "flying"), pokemonPage = true,
        )
        val outcome = resolver.resolve(reading, null, 50)
        assertNull(outcome.problem)
        assertEquals("minior_core", outcome.input.speciesId)
        val combos = outcome.result!!.combos
        assertEquals(10, combos.size)
        assertTrue(combos.all { it.level == Levels.indexOf(5.0) })
    }
}
