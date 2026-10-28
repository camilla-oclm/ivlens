package com.ivlens.app

import com.ivlens.app.core.League
import com.ivlens.app.core.Levels
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.RaidRanker
import com.ivlens.app.core.Rankings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.23: the Ranks tab's lists. */
class RankingsTest {
    private val gd = TestData.gd
    private val raids = RaidRanker(gd)

    @Test
    fun raidListsAreBestFirstWithEveryTypeFilled() {
        for (t in Rankings.TYPES) {
            val rows = Rankings.raidRows(raids, t)
            assertEquals("$t has 20 rows", 20, rows.size)
            assertEquals(100, rows.first().percent)
            assertTrue(rows.zipWithNext().all { (a, b) -> a.percent!! >= b.percent!! })
            assertTrue("$t: ranked with $t charged moves", rows.all { it.charged!!.type == t })
            assertEquals((1..20).toList(), rows.map { it.rank })
        }
        println("Dragon: " + Rankings.raidRows(raids, "dragon").take(5).map { (if (it.shadow) "Shadow " else "") + it.species.name + " ${it.percent}%" })
    }

    @Test
    fun pvpListsFollowPvPokeWithShadowsOnTheirOwn() {
        for (lg in League.entries) {
            val rows = Rankings.pvpRows(gd, lg)
            assertEquals(50, rows.size)
            assertEquals(1, rows.first().rank)
            assertTrue(rows.zipWithNext().all { (a, b) -> a.rank < b.rank })
            println("${lg.short}: " + rows.take(5).map { (if (it.shadow) "Shadow " else "") + it.species.name })
        }
        assertTrue("some Shadows in Great League's top 50", Rankings.pvpRows(gd, League.GREAT).any { it.shadow })
    }

    @Test
    fun bestIvsAreRankOneUnderTheCap() {
        val ranker = PvpRanker(gd)
        val medicham = gd.byId.getValue("medicham")
        val b = Rankings.bestIvs(ranker, medicham, League.GREAT, Levels.L50)!!
        println("Medicham GL best: ${b.ivText} Lv ${Levels.label(b.level)} CP ${b.cp}")
        assertEquals(1, ranker.table(medicham, League.GREAT)[b.atk, b.def, b.sta]!!.rank)
        assertTrue(b.cp <= 1500)
        // No cap in Master League: perfect IVs at the top level.
        val m = Rankings.bestIvs(ranker, gd.byId.getValue("dragonite"), League.MASTER, Levels.L50)!!
        assertEquals("15/15/15", m.ivText)
        assertEquals(Levels.L50, m.level)
    }
}
