package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCombo
import com.ivlens.app.store.SavedMerge
import com.ivlens.app.store.SavedMon
import com.ivlens.app.store.decodeSavedFile
import com.ivlens.app.store.encodeSavedFile
import com.ivlens.app.store.mergeSaved
import com.ivlens.app.store.putSaved
import com.ivlens.app.ui.importNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved list: one entry per Pokémon, so "Auto save" doesn't pile up copies. */
class SavedListTest {
    private fun mon(id: Long, cp: Int, hp: Int, selected: IvCombo? = null, fast: String? = null) = SavedMon(
        id = id, savedAt = id, name = "Minun",
        input = CalcInput(speciesId = "minun", cp = cp, hp = hp, dust = 3500, fast = fast),
        selected = selected, ivMin = selected?.percent ?: 69, ivMax = selected?.percent ?: 82,
    )

    private val exact = IvCombo(level = 50, atk = 11, def = 11, sta = 13)
    private val other = mon(1, 1700, 120)

    @Test
    fun newPokemonGoesOnTop() {
        val out = putSaved(listOf(other), mon(2, 1112, 109), keepExact = true)!!
        assertEquals(listOf(2L, 1L), out.map { it.id })
    }

    @Test
    fun samePokemonReplacesItsEntryAndKeepsTheId() {
        val first = mon(2, 1112, 109)
        val out = putSaved(listOf(other, first), mon(3, 1112, 109, selected = exact), keepExact = true)!!
        assertEquals(listOf(2L, 1L), out.map { it.id })
        assertEquals(exact, out[0].selected)
    }

    @Test
    fun autoSaveKeepsExactIvsOverARange() {
        val saved = listOf(mon(2, 1112, 109, selected = exact))
        assertNull(putSaved(saved, mon(3, 1112, 109), keepExact = true))
        // A Save tapped on the card always wins.
        assertEquals(null, putSaved(saved, mon(3, 1112, 109), keepExact = false)!![0].selected)
    }

    @Test
    fun movesReadLaterUpdateTheEntry() {
        val saved = listOf(mon(2, 1112, 109))
        val out = putSaved(saved, mon(3, 1112, 109, fast = "SPARK"), keepExact = true)!!
        assertEquals(1, out.size)
        assertEquals("SPARK", out[0].input.fast)
    }

    @Test
    fun unchangedReadChangesNothing() {
        assertNull(putSaved(listOf(other, mon(2, 1112, 109)), mon(9, 1112, 109), keepExact = true))
    }

    @Test
    fun differentCpOrHpIsAnotherPokemon() {
        val out = putSaved(listOf(mon(2, 1112, 109)), mon(3, 1113, 109), keepExact = true)!!
        assertEquals(2, out.size)
    }

    // Export / import (1.24): the way from the old app ID (com.ivlens.app) to the Play one.

    @Test
    fun exportedFileReadsBack() {
        val saved = listOf(mon(3, 1112, 109, selected = exact, fast = "SPARK"), other)
        val text = encodeSavedFile(saved, now = 5)
        assertTrue(text.contains("\"app\":\"IV Lens\""))
        assertEquals(saved, decodeSavedFile(text))
    }

    @Test
    fun bareCollectionFileReadsToo() {
        assertEquals(listOf(other), decodeSavedFile("""[{"id":1,"savedAt":1,"name":"Minun","input":{"speciesId":"minun","cp":1700,"hp":120,"dust":3500},"ivMin":69,"ivMax":82}]"""))
    }

    @Test
    fun otherFilesAreRefused() {
        assertNull(decodeSavedFile("""{"app":"Other app","format":1,"exportedAt":0,"saved":[]}"""))
        assertNull(decodeSavedFile("""{"saved":[]}"""))
        assertNull(decodeSavedFile("not json"))
    }

    @Test
    fun importAddsNewPokemonAndKeepsSavedOnes() {
        val mine = mon(2, 1112, 109, selected = exact)
        val r = mergeSaved(listOf(mine), listOf(mon(7, 1112, 109), other))
        assertEquals(1, r.added)
        assertEquals(0, r.updated)
        // Newest first; the saved Minun keeps its exact IVs over the file's range.
        assertEquals(listOf(2L, 1L), r.items.map { it.id })
        assertEquals(exact, r.items[0].selected)
    }

    @Test
    fun importFillsInExactIvs() {
        val r = mergeSaved(listOf(mon(2, 1112, 109)), listOf(mon(7, 1112, 109, selected = exact)))
        assertEquals(0, r.added)
        assertEquals(1, r.updated)
        assertEquals(listOf(2L), r.items.map { it.id })
        assertEquals(exact, r.items[0].selected)
    }

    @Test
    fun importedIdInUseGetsAFreeOne() {
        val r = mergeSaved(listOf(mon(2, 1112, 109), mon(3, 900, 80)), listOf(mon(2, 1500, 130)))
        assertEquals(listOf(2L, 3L, 4L), r.items.map { it.id }.sorted())
    }

    @Test
    fun importTwiceAddsNothing() {
        val file = listOf(mon(2, 1112, 109), other)
        val once = mergeSaved(emptyList(), file)
        val twice = mergeSaved(once.items, file)
        assertEquals(once.items, twice.items)
        assertEquals("Nothing new: you had them all.", importNote(2, twice))
        assertEquals("Added 2 Pokémon.", importNote(2, once))
    }

    @Test
    fun importNoteSaysWhatChanged() {
        assertEquals("Added 3 Pokémon. Exact IVs filled in for 1. 2 already saved.", importNote(6, SavedMerge(emptyList(), 3, 1)))
        assertEquals("That file has no saved Pokémon.", importNote(0, SavedMerge(emptyList(), 0, 0)))
    }
}
