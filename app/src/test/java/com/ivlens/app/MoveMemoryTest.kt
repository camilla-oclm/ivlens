package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.scan.MoveMemory
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.ScreenReading
import com.ivlens.app.scan.ScrollMerge
import com.ivlens.app.scan.SpeciesMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's Kyogre (CP 2459, HP 133, 566.69 kg; 2026-10-24): its moves were read after scrolling
 * down, the owner swiped to a Dialga and back, and the chip asked for the moves again (MOVES? ·
 * SCROLL DOWN). The 1.18 review then found the cases below.
 */
class MoveMemoryTest {
    private val kyogre = CalcInput("kyogre", 2459, 133)
    private val kg = 566.69
    private val kyogreMoves = "WATERFALL" to listOf("SURF")
    private fun CalcInput.with(m: Pair<String?, List<String>>) = copy(fast = m.first, charged = m.second)

    @Test
    fun movesComeBackAfterSwipingAway() {
        val mem = MoveMemory()
        mem.note(kyogre.with(kyogreMoves), kg)
        mem.note(CalcInput("dialga", 2365, 132, fast = "DRAGON_BREATH", charged = listOf("IRON_HEAD")), 683.4)
        assertEquals(kyogreMoves, mem.recall(kyogre, kg))
    }

    @Test
    fun onlyTheSamePokemon() {
        val mem = MoveMemory()
        mem.note(kyogre.with(kyogreMoves), kg)
        assertNull(mem.recall(kyogre.copy(cp = 2460), kg))
        assertNull(mem.recall(kyogre.copy(hp = 134), kg))
        assertNull(mem.recall(kyogre.copy(speciesId = "palkia"), kg))
        // Two raid catches with the same IVs share CP and HP; their weights tell them apart.
        assertNull(mem.recall(kyogre, 402.13))
        // A hidden CP or an unread weight isn't enough to tell it apart.
        assertNull(mem.recall(kyogre.copy(cp = null), kg))
        assertNull(mem.recall(kyogre, null))
    }

    @Test
    fun aReadWithoutCpOrWeightIsNotKept() {
        val mem = MoveMemory()
        mem.note(kyogre.copy(cp = null).with(kyogreMoves), kg)
        mem.note(kyogre.with(kyogreMoves), null)
        assertNull(mem.recall(kyogre, kg))
    }

    @Test
    fun theTopOfThePageKeepsTheFullSet() {
        // An unscrolled Dialga shows Dragon Breath and only its first charged move.
        val dialga = CalcInput("dialga", 2309, 130)
        val full = "DRAGON_BREATH" to listOf("IRON_HEAD", "DRACO_METEOR")
        val top = "DRAGON_BREATH" to listOf("IRON_HEAD")
        val mem = MoveMemory()
        mem.note(dialga.with(full), 683.4)
        mem.note(dialga.with(top), 683.4)
        assertEquals(full, mem.recall(dialga.with(top), 683.4))
        assertEquals(full, mem.recall(dialga.with("DRAGON_BREATH" to emptyList()), 683.4))
        // Charged moves read without the fast move are filled in too.
        assertEquals(full, mem.recall(dialga.with(null to listOf("IRON_HEAD")), 683.4))
        // The full set on screen needs nothing.
        assertNull(mem.recall(dialga.with(full), 683.4))
    }

    @Test
    fun otherMovesOnScreenWin() {
        val mem = MoveMemory()
        mem.note(kyogre.with(kyogreMoves), kg)
        // After a TM: the read's moves stand, and replace the remembered ones.
        val tm = "WATERFALL" to listOf("ORIGIN_PULSE")
        assertNull(mem.recall(kyogre.with(tm), kg))
        assertNull(mem.recall(kyogre.with(null to listOf("ORIGIN_PULSE")), kg))
        mem.note(kyogre.with(tm), kg)
        assertEquals(tm, mem.recall(kyogre, kg))
    }

    @Test
    fun aScrollIsRememberedOnlyWhenItShowsTheSamePokemon() {
        val gd = TestData.gd
        val resolver = ScanResolver(gd, IvCalculator(gd), SpeciesMatcher(gd))
        fun scrolled(hp: List<Int>, weightKg: Double?) = resolver.resolve(
            ScreenReading(cp = emptyList(), hp = hp, dust = emptyList(), names = emptyList(), candyName = "KYOGRE",
                lucky = false, moves = listOf("Waterfall", "Surf"), weightKg = weightKg, pokemonPage = true),
            null, 50,
        )
        assertTrue(ScrollMerge.confirms(kyogre, kg, scrolled(listOf(133), kg)))
        assertTrue(ScrollMerge.confirms(kyogre, kg, scrolled(emptyList(), kg)))
        assertTrue(ScrollMerge.confirms(kyogre, kg, scrolled(listOf(133), null)))
        // Nothing on screen says which Kyogre it is: a quick swipe and scroll could be another one.
        assertFalse(ScrollMerge.confirms(kyogre, kg, scrolled(emptyList(), null)))
        assertFalse(ScrollMerge.confirms(kyogre, kg, scrolled(listOf(133), 402.13)))
    }
}
