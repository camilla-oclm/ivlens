package com.ivlens.app.scan

import com.ivlens.app.core.CalcInput
import kotlin.math.roundToLong

/**
 * Moves seen for each Pokémon this session, so a Pokémon swiped away from and back to doesn't ask
 * for them again (the owner's Kyogre, CP 2459, HP 133; 2026-10-24). The full set shows only after
 * scrolling down, and the scroll merge keeps it only while the chip stays on that Pokémon.
 *
 * A Pokémon is told apart by species, CP, HP **and weight** (1.19): two raid catches with the same
 * IVs share CP and HP, but their moves differ, and so does their weight.
 */
class MoveMemory {
    private val seen = mutableMapOf<String, Pair<String, List<String>>>()

    /** Only a fully read Pokémon has a key: with CP, HP or weight missing, two could mix up. */
    private fun keyOf(i: CalcInput, weightKg: Double?): String? {
        val sp = i.speciesId ?: return null
        val cp = i.cp ?: return null
        val hp = i.hp ?: return null
        val w = weightKg?.let { (it * 100).roundToLong() } ?: return null
        return "$sp|$cp|$hp|$w"
    }

    /** True when the moves read ([fast] may be missing) are some of [known], not other moves. */
    private fun partOf(fast: String?, charged: List<String>, known: Pair<String, List<String>>) =
        (fast == null || fast == known.first) && known.second.containsAll(charged)

    /**
     * Keeps the moves on [i], if it has a fast move. The top of the page often shows the fast
     * move and only the first charged move (Dialga, Mewtwo): such a part of the known set keeps
     * the full set. Other moves (a TM) replace it.
     */
    fun note(i: CalcInput, weightKg: Double?) {
        val key = keyOf(i, weightKg) ?: return
        val fast = i.fast ?: return
        val known = seen[key]
        if (known != null && partOf(fast, i.charged, known)) return
        seen[key] = fast to i.charged
    }

    /**
     * The moves seen earlier for the Pokémon in [i], when the read shows none of them or only
     * some. Null when there's nothing to add, or the read shows other moves (those stand).
     */
    fun recall(i: CalcInput, weightKg: Double?): Pair<String, List<String>>? {
        val known = keyOf(i, weightKg)?.let { seen[it] } ?: return null
        if (i.fast == known.first && i.charged == known.second) return null
        return known.takeIf { partOf(i.fast, i.charged, it) }
    }
}
