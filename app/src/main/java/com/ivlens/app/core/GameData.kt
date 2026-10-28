package com.ivlens.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class Gender { MALE, FEMALE }

@Serializable
data class Species(
    val id: String,
    val name: String,
    val base: String,
    val dex: Int,
    val a: Int,
    val d: Int,
    val s: Int,
    val fam: String,
    val evo: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    /** Legendary, mythical or Ultra Beast. */
    val rare: Boolean = false,
    /** Can be caught as a Shadow. */
    val sh: Boolean = false,
    /** Fast and charged move ids it can learn, including elite ones listed in [el]. */
    val fm: List<String> = emptyList(),
    val cm: List<String> = emptyList(),
    val el: List<String> = emptyList(),
    /** Where PvPoke ranks it in each league, best first, by [League.short] (GL, UL, ML). */
    val pvp: Map<String, Int> = emptyMap(),
    /** The same for its Shadow form. */
    val pvps: Map<String, Int> = emptyMap(),
) {
    /** PvPoke's rank in [league] (the Shadow one for a Shadow, when ranked); null if unranked. */
    fun leagueRank(league: League, shadow: Boolean): Int? =
        (if (shadow) pvps[league.short] else null) ?: pvp[league.short]
}

/** A move with its gym and raid stats ([p] power, [ms] duration, [e] energy gained or spent). */
@Serializable
data class Move(
    val id: String,
    val name: String,
    val type: String,
    val fast: Boolean,
    val p: Double = 0.0,
    val ms: Int = 0,
    val e: Int = 0,
) {
    val hasRaidStats: Boolean get() = ms > 0
}

@Serializable
private data class GameDataFile(
    val source: String = "",
    val cpm: List<Double>,
    val species: List<Species>,
    val moves: List<Move> = emptyList(),
)

/** Level index i <-> level 1 + i/2. Index 98 is level 50, index 100 is level 51. */
object Levels {
    const val L40 = 78
    const val L50 = 98
    const val L51 = 100
    fun level(index: Int): Double = 1.0 + index * 0.5
    fun label(index: Int): String {
        val l = level(index)
        return if (l % 1.0 == 0.0) l.toInt().toString() else l.toString()
    }
    fun indexOf(level: Double): Int = Math.round((level - 1.0) * 2).toInt()
}

class GameData private constructor(file: GameDataFile) {
    val source: String = file.source

    /** The game master's date in [source] ("PvPoke gamemaster 2026-10-21 03:50:13"), shown in About and Ranks (1.23). */
    val date: LocalDate? = Regex("""\d{4}-\d{2}-\d{2}""").find(source)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** [date] as "21 Oct 2026", or "" without one. */
    val dateText: String get() = date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)) ?: ""

    val cpm: DoubleArray = file.cpm.toDoubleArray()
    val species: List<Species> = file.species
    val byId: Map<String, Species> = species.associateBy { it.id }
    val byFamily: Map<String, List<Species>> = species.groupBy { it.fam }
    val moves: List<Move> = file.moves
    val moveById: Map<String, Move> = moves.associateBy { it.id }

    /** Highest level index the table covers (level 51 when present). */
    val maxIndex: Int get() = cpm.size - 1

    fun evolutions(sp: Species): List<Species> = sp.evo.mapNotNull { byId[it] }

    /**
     * All forward evolutions with their stage depth (1 = next stage). With a [gender], those open
     * only to the other gender are left out (no Gallade for a female Kirlia).
     */
    fun evolutionChain(sp: Species, gender: Gender? = null): List<Pair<Species, Int>> {
        val out = mutableListOf<Pair<Species, Int>>()
        val seen = mutableSetOf(sp.id)
        fun walk(s: Species, depth: Int) {
            for (e in evolutions(s)) {
                if (gender != null && (GENDER_ONLY[e.id] ?: gender) != gender) continue
                if (seen.add(e.id)) {
                    out += e to depth
                    walk(e, depth + 1)
                }
            }
        }
        walk(sp, 1)
        return out
    }

    /** Display name with the form in brackets, e.g. "Mewtwo (Armored)". */
    fun displayName(sp: Species): String = sp.name

    companion object {
        /** Evolutions open to one gender only, by the evolution's id. PvPoke's data has no genders. */
        private val GENDER_ONLY = mapOf(
            "gallade" to Gender.MALE, "mothim" to Gender.MALE, "meowstic" to Gender.MALE, "oinkologne" to Gender.MALE,
            "froslass" to Gender.FEMALE, "vespiquen" to Gender.FEMALE, "salazzle" to Gender.FEMALE,
            "wormadam_plant" to Gender.FEMALE, "wormadam_sandy" to Gender.FEMALE, "wormadam_trash" to Gender.FEMALE,
            "meowstic_female" to Gender.FEMALE, "oinkologne_female" to Gender.FEMALE,
        )
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): GameData {
            val f = json.decodeFromString(GameDataFile.serializer(), text)
            require(f.cpm.size >= 99) { "CP multiplier table is incomplete" }
            return GameData(f)
        }
    }
}
