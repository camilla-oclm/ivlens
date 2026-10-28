package com.ivlens.app.core

import kotlin.math.roundToInt

/** One line of the Ranks tab: a species (or its Shadow) at [rank]. */
data class RankRow(
    val rank: Int,
    val species: Species,
    val shadow: Boolean,
    /** Raids: the moves it's ranked with, and its score as a percent of #1's. */
    val fast: Move? = null,
    val charged: Move? = null,
    val percent: Int? = null,
)

/**
 * The Ranks tab's lists (1.23), from data the verdicts already use: the raid boards
 * ([RaidRanker.board]) and PvPoke's league rankings ([Species.pvp], [Species.pvps]).
 */
object Rankings {
    /** The game's type order, for the type picker. */
    val TYPES = listOf(
        "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )

    /** Best attackers whose charged move is [type], at Lv 40 with perfect IVs and their best moves. */
    fun raidRows(ranker: RaidRanker, type: String, top: Int = 20): List<RankRow> {
        val board = ranker.board(type).take(top)
        val best = board.firstOrNull()?.score ?: return emptyList()
        return board.mapIndexed { i, e -> RankRow(i + 1, e.species, e.shadow, e.fast, e.charged, (e.score * 100 / best).roundToInt()) }
    }

    /** The IVs ranked #1 for [sp] in [league], with the level and CP they reach there. */
    data class BestIvs(val atk: Int, val def: Int, val sta: Int, val level: Int, val cp: Int) {
        val ivText: String get() = "$atk/$def/$sta"
    }

    fun bestIvs(ranker: PvpRanker, sp: Species, league: League, maxLevel: Int): BestIvs? {
        val t = ranker.table(sp, league, maxLevel)
        val (a, d, s) = t.bestIvs ?: return null
        val r = t[a, d, s] ?: return null
        return BestIvs(a, d, s, r.level, r.cp)
    }

    /** PvPoke's top [top] for [league]; a Shadow is ranked on its own, as PvPoke does. */
    fun pvpRows(gd: GameData, league: League, top: Int = 50): List<RankRow> =
        gd.species.flatMap { sp ->
            listOfNotNull(
                sp.pvp[league.short]?.let { RankRow(it, sp, false) },
                sp.pvps[league.short]?.let { RankRow(it, sp, true) },
            )
        }.sortedBy { it.rank }.take(top)
}
