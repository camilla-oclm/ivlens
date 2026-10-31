package com.ivlens.app

import com.ivlens.app.core.CalcInput
import com.ivlens.app.store.CollectionStore
import com.ivlens.app.store.SavedMon
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The saved list on disk (1.28): one writer, in order, after the file is read. Before, each change
 * launched its own write, and two in a row could land in either order.
 */
class CollectionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val list = ListSerializer(SavedMon.serializer())

    private fun mon(id: Long, cp: Int) =
        SavedMon(id = id, savedAt = id, name = "Minun", input = CalcInput(speciesId = "minun", cp = cp, hp = 100))

    private fun onDisk(f: File): List<Long> = json.decodeFromString(list, f.readText()).map { it.id }

    @Test
    fun aBurstOfChangesLeavesTheLatestListOnDisk() = runBlocking {
        val f = File(tmp.root, "collection.json")
        val store = CollectionStore(f)
        for (i in 1..30) store.put(mon(i.toLong(), 1000 + i))
        store.removeAll((1..30).filter { it % 3 == 0 }.map { it.toLong() }.toSet())
        store.put(mon(31, 2000))
        store.flush()
        assertEquals(store.items.value.map { it.id }, onDisk(f))
        assertEquals(21, onDisk(f).size)
    }

    @Test
    fun aSaveMadeBeforeTheFileIsReadKeepsBoth() = runBlocking {
        val f = File(tmp.root, "collection.json")
        f.writeText(json.encodeToString(list, listOf(mon(1, 1001), mon(2, 1002))))
        val store = CollectionStore(f)
        store.put(mon(3, 1003))
        store.flush()
        assertEquals(setOf(1L, 2L, 3L), store.items.value.map { it.id }.toSet())
        assertEquals(setOf(1L, 2L, 3L), onDisk(f).toSet())
    }

    @Test
    fun nothingIsWrittenWithoutAChange() = runBlocking {
        val f = File(tmp.root, "collection.json")
        val store = CollectionStore(f)
        store.flush()
        assertFalse(f.exists())
    }
}
