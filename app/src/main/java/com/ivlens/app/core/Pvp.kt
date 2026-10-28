package com.ivlens.app.core

enum class League(val label: String, val short: String, val cap: Int?) {
    GREAT("Great League", "GL", 1500),
    ULTRA("Ultra League", "UL", 2500),
    MASTER("Master League", "ML", null),
    ;

    /**
     * Whether a Pokémon whose CP tops out at [maxCp] (fully powered) can compete here: within 3%
     * of the cap, or 3,700 CP for Master League (Togekiss and Snorlax in, Venusaur out). A good
     * IV rank means little otherwise: a maxed Sneasel is 2,320 CP, so its "Ultra #8" was no pick.
     */
    fun canCompete(maxCp: Int): Boolean = if (cap != null) maxCp >= cap * 0.97 else maxCp >= 3700
}

/** Rank of one IV combo in a league, at its best level under the cap. */
data class PvpRank(
    val rank: Int,
    val level: Int,
    val cp: Int,
    val statProduct: Double,
    val percentOfBest: Double,
)

class RankTable(private val ranks: Array<PvpRank?>, val eligible: Int) {
    operator fun get(a: Int, d: Int, s: Int): PvpRank? = ranks[a * 256 + d * 16 + s]
    val best: PvpRank? get() = ranks.filterNotNull().minByOrNull { it.rank }

    /** The IVs ranked #1, as (attack, defense, HP); for the Ranks tab (1.23). */
    val bestIvs: Triple<Int, Int, Int>?
        get() = ranks.indices.filter { ranks[it] != null }.minByOrNull { ranks[it]!!.rank }?.let { Triple(it / 256, it / 16 % 16, it % 16) }
}

class PvpRanker(private val gd: GameData) {
    private val cache = object : LinkedHashMap<String, RankTable>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RankTable>?) = size > 48
    }

    fun table(sp: Species, league: League, maxLevel: Int = Levels.L50): RankTable {
        val key = "${sp.id}|${league.name}|$maxLevel"
        synchronized(cache) { cache[key]?.let { return it } }
        val t = build(sp, league, maxLevel.coerceAtMost(gd.maxIndex))
        synchronized(cache) { cache[key] = t }
        return t
    }

    /** Highest level index <= maxLevel whose CP fits under [cap]; -1 if even level 1 is over. */
    fun bestLevel(sp: Species, a: Int, d: Int, s: Int, cap: Int?, maxLevel: Int): Int {
        if (cap == null) return maxLevel
        if (Stats.cp(sp, gd.cpm[0], a, d, s) > cap) return -1
        var lo = 0
        var hi = maxLevel
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (Stats.cp(sp, gd.cpm[mid], a, d, s) <= cap) lo = mid else hi = mid - 1
        }
        return lo
    }

    private fun build(sp: Species, league: League, maxLevel: Int): RankTable {
        val levels = IntArray(4096)
        val products = DoubleArray(4096)
        val order = ArrayList<Int>(4096)
        for (a in 0..15) for (d in 0..15) for (s in 0..15) {
            val k = a * 256 + d * 16 + s
            val li = bestLevel(sp, a, d, s, league.cap, maxLevel)
            levels[k] = li
            if (li >= 0) {
                products[k] = Stats.statProduct(sp, gd.cpm[li], a, d, s)
                order += k
            }
        }
        order.sortWith(compareByDescending<Int> { products[it] }.thenBy { it })
        val ranks = arrayOfNulls<PvpRank>(4096)
        val best = order.firstOrNull()?.let { products[it] } ?: 1.0
        var rank = 0
        var prev = Double.NaN
        order.forEachIndexed { i, k ->
            if (products[k] != prev) {
                rank = i + 1
                prev = products[k]
            }
            val a = k / 256
            val d = (k / 16) % 16
            val s = k % 16
            val li = levels[k]
            ranks[k] = PvpRank(rank, li, Stats.cp(sp, gd.cpm[li], a, d, s), products[k], products[k] / best * 100.0)
        }
        return RankTable(ranks, order.size)
    }
}
