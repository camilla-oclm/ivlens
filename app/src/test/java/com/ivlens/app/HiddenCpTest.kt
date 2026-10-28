package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PowerUpCosts
import com.ivlens.app.core.Stats
import com.ivlens.app.core.Variant
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The owner's XXL Munna (HP 103, 1,900 stardust; 2026-10-23) covered its own CP, so nothing could
 * be worked out, even with the Appraise bars on screen. With exact IVs, the HP and stardust pin
 * the level, and the CP follows.
 */
class HiddenCpTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)

    @Test
    fun barsFindTheHiddenCp() {
        // Cloyster's known truth: 15/7/15 at L19.
        val sp = gd.byId["cloyster"]!!
        val li = Levels.indexOf(19.0)
        val m = gd.cpm[li]
        val input = CalcInput("cloyster", null, Stats.hp(sp, m, 15), PowerUpCosts.dust(li, Variant()))
        val (result, combo) = AppraisalFit.fit(calc, input, null, Appraisal(15, 7, 15), Levels.L50)!!
        assertEquals(Stats.cp(sp, m, 15, 7, 15), result.input.cp)
        assertEquals(li, combo.level)
    }

    @Test
    fun munnasHpIvPicksTheLevel() {
        // HP 103 at 1,900 stardust: HP IV 13 fits only L15.5, 10 only L16, 7 only L16.5.
        val sp = gd.byId["munna"]!!
        val input = CalcInput("munna", null, 103, 1900)
        for ((s, level) in listOf(13 to 15.5, 10 to 16.0, 7 to 16.5)) {
            assertEquals(Stats.cp(sp, gd.cpm[Levels.indexOf(level)], 10, 10, s), calc.cpFor(input, 10, 10, s))
        }
    }

    @Test
    fun noGuessWhenTwoLevelsFit() {
        // Magikarp, HP IV 15, 1,600 stardust: HP 49 at both L13.5 and L14. No CP is made up,
        // unless the level arc tells them apart.
        val sp = gd.byId["magikarp"]!!
        val input = CalcInput("magikarp", null, 49, 1600)
        assertNull(calc.cpFor(input, 8, 8, 15))
        val l14 = Levels.indexOf(14.0)
        assertEquals(Stats.cp(sp, gd.cpm[l14], 8, 8, 15), calc.cpFor(input.copy(levelHint = listOf(l14)), 8, 8, 15))
    }

    @Test
    fun anotherOfTheKindDoesntGetItsIvs() {
        // Two Dragonite at L30 with the same HP and stardust, both with the CP hidden: A's bars
        // (15/12/13) fit B too, so before 1.19 B showed A's IVs as exact. The weight tells them apart.
        val sp = gd.byId["dragonite"]!!
        val li = Levels.indexOf(30.0)
        val m = gd.cpm[li]
        val hp = Stats.hp(sp, m, 13)
        val b = CalcInput("dragonite", null, hp, PowerUpCosts.dust(li, Variant()))
        assertNotNull(AppraisalFit.fit(calc, b, null, Appraisal(15, 12, 13), Levels.L50))
        val keyA = AppraisalFit.memoryKey(b, 47.12)
        assertNotNull(keyA)
        assertNotEquals(keyA, AppraisalFit.memoryKey(b, 61.3))
        assertEquals(keyA, AppraisalFit.memoryKey(b, 47.12))
        // CP hidden and weight unread: nothing to tell them apart, so nothing is kept.
        assertNull(AppraisalFit.memoryKey(b, null))
        // With the CP read, the key is species, CP and HP, as before.
        val withCp = b.copy(cp = Stats.cp(sp, m, 15, 12, 13))
        assertEquals("dragonite|${withCp.cp}|$hp", AppraisalFit.memoryKey(withCp, null))
        assertEquals(AppraisalFit.memoryKey(withCp, null), AppraisalFit.memoryKey(withCp, 47.12))
    }
}
