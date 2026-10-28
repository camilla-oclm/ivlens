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
import com.ivlens.app.ui.APPRAISE_HINT
import com.ivlens.app.ui.miniFromVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chip waits for moves only when the answer depends on them. */
class PendingMovesTest {
    private val gd = TestData.gd
    private val calc = IvCalculator(gd)
    private val engine = VerdictEngine(gd, PvpRanker(gd))

    private fun judge(id: String, combo: IvCombo, fast: String? = null, charged: List<String> = emptyList()) =
        run {
            val sp = gd.byId.getValue(id)
            val cpm = gd.cpm[combo.level]
            val input = CalcInput(id, Stats.cp(sp, cpm, combo.atk, combo.def, combo.sta), Stats.hp(sp, cpm, combo.sta), fast = fast, charged = charged)
            val r = calc.calculate(input)!!
            engine.judge(r, combo, VerdictRules())!!
        }

    private val good = IvCombo(Levels.L40, 14, 12, 13)

    @Test
    fun metagrossWithoutMovesWaits() {
        val v = judge("metagross", good)
        println("no moves: ${v.primary} also ${v.also}")
        assertTrue(v.pendingMoves)
        val chip = miniFromVerdict(v, "Metagross · 87%")
        assertEquals("MOVES?", chip.word)
        assertTrue(chip.line2!!.contains("Bullet Punch"))
    }

    @Test
    fun metagrossWithItsBestMovesIsARaider() {
        val v = judge("metagross", good, "BULLET_PUNCH", listOf("METEOR_MASH"))
        println("best moves: ${v.primary}")
        assertFalse(v.pendingMoves)
        assertEquals(VerdictKind.RAIDS, v.primary.kind)
        assertFalse(v.primary.title.startsWith("Raids after TM"))
        assertEquals("RAIDS", miniFromVerdict(v, "x").word)
    }

    @Test
    fun metagrossWithPoorMovesNeedsATm() {
        val v = judge("metagross", good, "ZEN_HEADBUTT", listOf("EARTHQUAKE"))
        println("poor moves: ${v.primary}")
        assertFalse(v.pendingMoves)
        assertEquals("Raids after TM", v.primary.title)
        assertTrue(miniFromVerdict(v, "x").outline)
    }

    @Test
    fun beldumDoesNotWaitBecauseEvolvingRerollsMoves() {
        val v = judge("beldum", IvCombo(Levels.indexOf(20.0), 14, 12, 13))
        println("beldum: ${v.primary}")
        assertFalse(v.pendingMoves)
        assertEquals(VerdictKind.RAIDS, v.primary.kind)
        assertTrue(v.primary.detail.startsWith("as Metagross"))
    }

    @Test
    fun weakPokemonNeverWaits() {
        val v = judge("bidoof", IvCombo(Levels.indexOf(20.0), 5, 7, 3))
        assertFalse(v.pendingMoves)
        assertEquals(VerdictKind.TRANSFER, v.primary.kind)
        assertEquals("TRANSFER", miniFromVerdict(v, "x").word)
    }

    /** 1.22: with several IVs still possible, the chip points to the Appraise screen. */
    @Test
    fun chipSaysAppraiseWhileTheIvsAreARange() {
        val v = judge("bidoof", IvCombo(Levels.indexOf(20.0), 5, 7, 3))
        assertEquals(APPRAISE_HINT, miniFromVerdict(v, "x", appraise = true).line2!!.lines().first())
        assertFalse(miniFromVerdict(v, "x").line2.orEmpty().contains("Appraise"))
    }

    @Test
    fun movesComeBeforeTheAppraiseHint() {
        val v = judge("metagross", good)
        val chip = miniFromVerdict(v, "Metagross · 87%", appraise = true)
        assertEquals("MOVES?", chip.word)
        assertFalse(chip.line2!!.contains("Appraise"))
    }
}
