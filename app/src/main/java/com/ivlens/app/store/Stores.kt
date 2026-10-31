package com.ivlens.app.store

import android.content.Context
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.VerdictRules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Light or dark look (1.22): PHONE follows the phone's dark mode. */
enum class ThemeMode { PHONE, LIGHT, DARK }

data class AppSettings(
    val trainerLevel: Int = 50,
    val useArc: Boolean = true,
    val bestBuddyPvp: Boolean = false,
    /** Scan automatically when a Pokémon's screen settles, without tapping the bubble. */
    val autoScan: Boolean = true,
    /** Put each Pokémon the chip reads in Saved, without tapping Save. */
    val autoSave: Boolean = false,
    val pvpRankCutoff: Int = 100,
    /** How high PvPoke must rank the species in a league for PVP (1.16). */
    val pvpSpeciesCutoff: Int = 100,
    val keepIv: Int = 82,
    val raidRankCutoff: Int = 30,
    /** Notes each read in the phone's log (`adb logcat -s IvLens`), for fixing problems; debug build only. */
    val debugLog: Boolean = false,
    val theme: ThemeMode = ThemeMode.PHONE,
) {
    val rules: VerdictRules get() = VerdictRules(
        pvpRankCutoff = pvpRankCutoff, pvpSpeciesCutoff = pvpSpeciesCutoff, keepIv = keepIv, raidRankCutoff = raidRankCutoff,
    )

    /** Highest level index this trainer can power a Pokémon to. */
    val powerMaxIndex: Int get() = Levels.indexOf(minOf(trainerLevel + 10, 50).toDouble())
    val pvpMaxIndex: Int get() = if (bestBuddyPvp) Levels.L51 else Levels.L50
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load() = AppSettings(
        trainerLevel = prefs.getInt("trainerLevel", 50),
        useArc = prefs.getBoolean("useArc", true),
        bestBuddyPvp = prefs.getBoolean("bestBuddyPvp", false),
        autoScan = prefs.getBoolean("autoScan", true),
        autoSave = prefs.getBoolean("autoSave", false),
        pvpRankCutoff = prefs.getInt("pvpRankCutoff", 100),
        pvpSpeciesCutoff = prefs.getInt("pvpSpeciesCutoff", 100),
        keepIv = prefs.getInt("keepIv", 82),
        raidRankCutoff = prefs.getInt("raidRankCutoff", 30),
        debugLog = prefs.getBoolean("debugLog", false),
        theme = prefs.getString("theme", null)?.let { t -> ThemeMode.entries.firstOrNull { it.name == t } } ?: ThemeMode.PHONE,
    )

    fun update(f: (AppSettings) -> AppSettings) {
        val next = f(_state.value).let { it.copy(trainerLevel = it.trainerLevel.coerceIn(1, 80)) }
        _state.value = next
        prefs.edit()
            .putInt("trainerLevel", next.trainerLevel)
            .putBoolean("useArc", next.useArc)
            .putBoolean("bestBuddyPvp", next.bestBuddyPvp)
            .putBoolean("autoScan", next.autoScan)
            .putBoolean("autoSave", next.autoSave)
            .putInt("pvpRankCutoff", next.pvpRankCutoff)
            .putInt("pvpSpeciesCutoff", next.pvpSpeciesCutoff)
            .putInt("keepIv", next.keepIv)
            .putInt("raidRankCutoff", next.raidRankCutoff)
            .putBoolean("debugLog", next.debugLog)
            .putString("theme", next.theme.name)
            .apply()
    }
}

@Serializable
data class SavedMon(
    val id: Long,
    val savedAt: Long,
    val name: String,
    val input: CalcInput,
    val selected: IvCombo? = null,
    val ivMin: Int? = null,
    val ivMax: Int? = null,
    val levelText: String = "",
    val greatRank: Int? = null,
    val ultraRank: Int? = null,
    val verdict: String? = null,
    val verdictKind: String? = null,
) {
    /** Same Pokémon: species, CP and HP all match. */
    fun samePokemon(o: SavedMon): Boolean = input.speciesId == o.input.speciesId && input.cp == o.input.cp && input.hp == o.input.hp
}

/**
 * [items] with [item] at the top. It replaces the entry for the same Pokémon (and keeps its id),
 * so each Pokémon is saved once. Null when nothing would change. With [keepExact], exact IVs
 * already saved aren't swapped for a range (an auto save of a later, vaguer read).
 */
fun putSaved(items: List<SavedMon>, item: SavedMon, keepExact: Boolean): List<SavedMon>? {
    val old = items.firstOrNull { it.samePokemon(item) }
    if (old != null) {
        if (keepExact && old.selected != null && item.selected == null) return null
        if (item.copy(id = old.id, savedAt = old.savedAt) == old) return null
    }
    val next = old?.let { item.copy(id = it.id) } ?: item
    return listOf(next) + items.filter { it.id != next.id }
}

/** The saved list as a file (1.24, Settings › Saved list): a backup, or to move it to another phone or app ID. */
@Serializable
data class SavedFile(val app: String, val format: Int, val exportedAt: Long, val saved: List<SavedMon>)

private const val SAVED_FILE_APP = "IV Lens"
private val savedJson = Json { ignoreUnknownKeys = true }

fun encodeSavedFile(items: List<SavedMon>, now: Long): String =
    savedJson.encodeToString(SavedFile.serializer(), SavedFile(SAVED_FILE_APP, 1, now, items))

/** The entries of an exported file, or of a bare `collection.json`. Null when it's neither. */
fun decodeSavedFile(text: String): List<SavedMon>? = runCatching {
    val t = text.trimStart()
    if (t.startsWith("[")) savedJson.decodeFromString(ListSerializer(SavedMon.serializer()), t)
    else savedJson.decodeFromString(SavedFile.serializer(), t).takeIf { it.app == SAVED_FILE_APP }?.saved
}.getOrNull()

/** What an import did: the new list, the entries added, and the saved ranges it gave exact IVs. */
data class SavedMerge(val items: List<SavedMon>, val added: Int, val updated: Int)

/**
 * [current] with [imported] added, newest first. A Pokémon already saved keeps its entry, unless
 * only the file has its exact IVs. An imported id already in use gets a free one.
 */
fun mergeSaved(current: List<SavedMon>, imported: List<SavedMon>): SavedMerge {
    val out = current.toMutableList()
    val ids = current.mapTo(HashSet()) { it.id }
    var added = 0
    var updated = 0
    for (m in imported) {
        val i = out.indexOfFirst { it.samePokemon(m) }
        if (i >= 0) {
            if (out[i].selected == null && m.selected != null) {
                out[i] = m.copy(id = out[i].id)
                updated++
            }
            continue
        }
        var id = m.id
        while (!ids.add(id)) id++
        out += m.copy(id = id)
        added++
    }
    return SavedMerge(out.sortedByDescending { it.savedAt }, added, updated)
}

/**
 * The saved list, kept in `collection.json` in the app's files. One writer (1.28): the file is read
 * first, and every change after that is written in the order it was made, the latest list each
 * time. Before, each change launched its own write on the IO pool, and two in a row (an auto save
 * and a delete, say) could land in either order, leaving the older list on disk until the next
 * change; a save made before the file was read was written over the list on its own.
 */
class CollectionStore(private val file: File, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    constructor(context: Context) : this(File(context.filesDir, "collection.json"))

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedMon.serializer())
    private val _items = MutableStateFlow<List<SavedMon>>(emptyList())
    val items: StateFlow<List<SavedMon>> = _items.asStateFlow()

    /** Each change bumps [changes]; the writer catches up and notes in [onDisk] the last change written. */
    private val changes = MutableStateFlow(0L)
    private val onDisk = MutableStateFlow(0L)
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val fromDisk = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
            // A save made in the meantime is merged, and then written after the file, never over it.
            _items.update { current -> (fromDisk + current).distinctBy { it.id } }
            loaded.complete(Unit)
            changes.collect { n ->
                if (n > onDisk.value) {
                    // A failed write (no space) is noted as done too, so the next change tries again.
                    runCatching { write(_items.value) }
                    onDisk.value = n
                }
            }
        }
    }

    /** Saves [item], replacing the same Pokémon's entry (see [putSaved]). True if the list changed. */
    fun put(item: SavedMon, keepExact: Boolean = false): Boolean {
        var changed = false
        _items.update { cur -> putSaved(cur, item, keepExact).also { changed = it != null } ?: cur }
        if (changed) persist()
        return changed
    }

    /** Adds the entries of an imported file (see [mergeSaved]). */
    fun merge(imported: List<SavedMon>): SavedMerge {
        var result = SavedMerge(emptyList(), 0, 0)
        _items.update { cur -> mergeSaved(cur, imported).also { result = it }.items }
        if (result.added + result.updated > 0) persist()
        return result
    }

    fun remove(id: Long) = removeAll(setOf(id))

    fun removeAll(ids: Set<Long>) {
        _items.update { it.filter { o -> o.id !in ids } }
        persist()
    }

    private fun persist() {
        changes.update { it + 1 }
    }

    private fun write(items: List<SavedMon>) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(serializer, items))
        tmp.renameTo(file)
    }

    /** Waits until the file has been read and every change made so far is on disk (for tests). */
    internal suspend fun flush() {
        loaded.await()
        val n = changes.value
        onDisk.first { it >= n }
    }
}
