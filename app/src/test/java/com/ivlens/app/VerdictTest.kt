package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.League
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PowerUpCosts
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.Stats
import com.ivlens.app.core.Variant
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerdictTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val engine = VerdictEngine(gd, PvpRanker(gd))
    private val rules = VerdictRules()

    /** Input for a Pokémon with known IVs, as the screen would show it. */
    private fun input(id: String, level: Double, a: Int, d: Int, s: Int, v: Variant = Variant()): CalcInput {
        val sp = gd.byId[id]!!
        val li = Levels.indexOf(level)
        val m = gd.cpm[li]
        return CalcInput(id, Stats.cp(sp, m, a, d, s), Stats.hp(sp, m, s), PowerUpCosts.dust(li, v), levelHint = listOf(li)).withVariant(v)
    }

    private fun verdict(i: CalcInput) = engine.judge(calc.calculate(i)!!, null, rules)!!

    /** Verdict for one exact combo, as when the user taps it on the card. */
    private fun exact(id: String, level: Double, a: Int, d: Int, s: Int, v: Variant = Variant()) =
        engine.judge(calc.calculate(input(id, level, a, d, s, v))!!, IvCombo(Levels.indexOf(level), a, d, s), rules)!!

    @Test
    fun hundoComesFirst() {
        val v = verdict(input("dialga", 20.0, 15, 15, 15))
        assertEquals(VerdictKind.HUNDO, v.primary.kind)
        assertTrue(v.also.any { it.kind == VerdictKind.RAIDS })
    }

    @Test
    fun bestGreatLeagueMarillIsPvpAsAzumarill() {
        val best = PvpRanker(gd).table(gd.byId["azumarill"]!!, League.GREAT).let { t ->
            (0..4095).map { Triple(it / 256, (it / 16) % 16, it % 16) }.first { (a, d, s) -> t[a, d, s]?.rank == 1 }
        }
        val v = exact("marill", 15.0, best.first, best.second, best.third)
        println("marill $best: ${v.primary} also=${v.also}")
        assertEquals(VerdictKind.PVP, v.primary.kind)
        assertTrue(v.primary.title.contains("Great"))
        assertTrue(v.primary.detail.contains("Azumarill"))
    }

    @Test
    fun screenshotVerdicts() {
        val gyara = verdict(CalcInput("gyarados", 2923, 171, 6000, levelHint = listOf(Levels.indexOf(31.5))))
        println("gyarados: ${gyara.primary} also=${gyara.also} confident=${gyara.confident}")
        assertEquals(VerdictKind.RAIDS, gyara.primary.kind)

        val snorlax = verdict(CalcInput("snorlax", 2782, 255, 6000, levelHint = listOf(Levels.indexOf(32.0))))
        println("snorlax: ${snorlax.primary} also=${snorlax.also} confident=${snorlax.confident}")
        assertTrue(snorlax.primary.kind in setOf(VerdictKind.KEEP, VerdictKind.TRANSFER))

        val dialga = verdict(CalcInput("dialga", 2309, 130, 2500))
        println("dialga: ${dialga.primary} also=${dialga.also} confident=${dialga.confident}")
        assertTrue(dialga.primary.kind != VerdictKind.TRANSFER)
    }

    @Test
    fun weakCommonPokemonIsTransfer() {
        val v = exact("rattata", 15.0, 5, 3, 7)
        assertEquals(VerdictKind.TRANSFER, v.primary.kind)
        val lucky = exact("rattata", 15.0, 5, 3, 7, Variant(lucky = true))
        assertEquals(VerdictKind.KEEP, lucky.primary.kind)
        val shadowRaider = exact("machop", 15.0, 11, 2, 3, Variant(shadow = true))
        assertEquals(VerdictKind.RAIDS, shadowRaider.primary.kind)
    }

    /**
     * The owner's two Electrike (2026-10-20): the 76% one was PVP (Ultra #85 as Manectric) and the
     * 96% one only KEEP. Since 1.16 PVP also needs the species to be used in that league, and
     * PvPoke ranks Manectric #672 in Ultra League, so the card says why neither is a pick.
     */
    @Test
    fun electrikeIsNoPvpPickSinceManectricIsntUsed() {
        val low = exact("electrike", 14.0, 8, 11, 15)
        assertEquals(VerdictKind.TRANSFER, low.primary.kind)
        assertEquals("Good Ultra League IVs (#85 as Manectric), but Manectric isn't used there (not in PvPoke's top 100).", low.primary.note)

        val high = exact("electrike", 15.0, 15, 14, 14)
        assertEquals(VerdictKind.KEEP, high.primary.kind)
        assertNull(high.primary.note)

        // Not exact yet (CP 410, HP 69 fits 15 combos): no rank to quote.
        val loose = calc.calculate(CalcInput("electrike", 410, 69, 1900))!!
        assertTrue(loose.combos.size > 1)
        assertNull(engine.judge(loose, null, rules)!!.primary.note)
    }

    /**
     * The owner's Flabébé (CP 12 at L1, 0/11/4; 2026-10-23) said "PVP · Ultra #67 as Florges", which
     * looked odd for 33%. It's right: 0 Attack suits PvP, and PvPoke ranks Florges #8 in Ultra League.
     */
    @Test
    fun flabebeIsPvpAsFlorges() {
        val v = exact("flabebe", 1.0, 0, 11, 4)
        assertEquals(VerdictKind.PVP, v.primary.kind)
        assertEquals("PvP · Ultra #67", v.primary.title)
        assertTrue(v.primary.detail.startsWith("as Florges"))

        // Too much Attack for PvP on a used species (and short of 12 for raids): KEEP, and the card
        // says why it isn't PvP. (15/14/14 would be RAIDS: Florges is a top Fairy raider.)
        val strong = exact("flabebe", 1.0, 11, 15, 15)
        assertEquals(VerdictKind.KEEP, strong.primary.kind)
        val note = strong.primary.note!!
        assertTrue(note, note.startsWith("Not for PvP: ") && "as Florges" in note && note.endsWith("Too much Attack: it hits the CP limit early."))
    }
}
