package com.ivlens.app.scan

import com.ivlens.app.core.GameData
import com.ivlens.app.core.Move
import com.ivlens.app.core.Species

class SpeciesMatcher(private val gd: GameData) {

    private val byBase: Map<String, List<Species>> = gd.species.groupBy { norm(it.base) }

    /** All forms whose name best matches [text] (exact, else within a small OCR error budget). */
    fun matchName(text: String): List<Species> {
        val key = norm(text)
        if (key.length < 3) return emptyList()
        byBase[key]?.let { return it }
        val budget = when {
            key.length <= 4 -> 0
            key.length <= 7 -> 1
            else -> 2
        }
        if (budget == 0) return emptyList()
        var best = Int.MAX_VALUE
        val hits = mutableListOf<Species>()
        for ((k, list) in byBase) {
            if (kotlin.math.abs(k.length - key.length) > budget) continue
            val dist = levenshtein(k, key, budget)
            if (dist > budget) continue
            if (dist < best) {
                best = dist
                hits.clear()
            }
            if (dist == best) hits += list
        }
        return hits
    }

    /** Family ids for a candy label such as "MAGIKARP" (from "MAGIKARP CANDY"). */
    fun familiesForCandy(text: String): List<String> =
        matchName(text).map { it.fam }.distinct()

    /** Members of a family, earliest stage first. */
    fun familyMembers(fam: String): List<Species> =
        (gd.byFamily[fam] ?: emptyList()).sortedWith(compareBy({ it.dex }, { it.id }))

    /** Prefix search for the species picker. */
    fun search(query: String, limit: Int = 12): List<Species> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val starts = gd.species.filter { norm(it.name).startsWith(q) }
        val contains = gd.species.filter { norm(it.name).contains(q) && it !in starts }
        return (starts.sortedBy { it.name.length } + contains).take(limit)
    }

    companion object {
        fun norm(s: String): String = buildString {
            for (c in s.uppercase()) {
                when {
                    c == '♀' -> append('F')
                    c == '♂' -> append('M')
                    c in 'A'..'Z' -> append(c)
                    c == 'É' -> append('E')
                }
            }
        }

        fun levenshtein(a: String, b: String, cap: Int): Int {
            if (a == b) return 0
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                var rowMin = cur[0]
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin > cap) return cap + 1
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }
    }
}

/** Matches move names read from the screen to moves, preferring the species' own movepool. */
class MoveMatcher(private val gd: GameData) {
    // Battle-only variants ("Dynamic Punch+") share names with the real moves; those lose.
    private fun real(m: Move) = !m.id.endsWith("_PLUS") && !m.id.startsWith("AEGISLASH_CHARGE")
    private val candidates = gd.moves.filter(::real)
    private val byName: Map<String, Move> = gd.moves
        .sortedWith(compareBy({ real(it) }, { it.hasRaidStats }))
        .associateBy { SpeciesMatcher.norm(it.name) }

    fun match(text: String, sp: Species?): Move? {
        val key = SpeciesMatcher.norm(text)
        if (key.length < 3) return null
        byName[key]?.let { return it }
        val pool = sp?.let { (it.fm + it.cm + listOf("FRUSTRATION", "RETURN")).mapNotNull { id -> gd.moveById[id] } }?.filter(::real) ?: emptyList()
        if (key == "HIDDENPOWER") return pool.firstOrNull { it.id.startsWith("HIDDEN_POWER") }
        fun best(cands: Collection<Move>, budget: Int): Move? = cands
            .map { it to SpeciesMatcher.levenshtein(SpeciesMatcher.norm(it.name), key, budget) }
            .filter { it.second <= budget }
            .minByOrNull { it.second }?.first
        // Generous with the Pokémon's own moves, strict with everything else.
        return best(pool, maxOf(1, key.length / 4)) ?: best(candidates, if (key.length >= 8) 1 else 0)
    }

    /** Also tries without stray one- or two-letter bits from the type icon and energy bars. */
    private fun matchLoose(text: String, sp: Species?): Move? {
        match(text, sp)?.let { return it }
        val words = text.split(' ').filter { it.isNotBlank() }
        val trimmed = words.dropWhile { it.length <= 2 }.dropLastWhile { it.length <= 2 }
        return if (trimmed.isNotEmpty() && trimmed.size < words.size) match(trimmed.joinToString(" "), sp) else null
    }

    /** Fast move and up to two charged moves, in screen order. */
    fun read(names: List<String>, sp: Species?): Pair<Move?, List<Move>> {
        val found = names.mapNotNull { matchLoose(it, sp) }.distinctBy { it.id }
        return found.firstOrNull { it.fast } to found.filter { !it.fast }.take(2)
    }
}
