package com.ivlens.app.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** One attacker on a type's leaderboard: a species (or its Shadow) with its best moveset. */
data class RaidEntry(val species: Species, val shadow: Boolean, val fast: Move, val charged: Move, val score: Double)

/** Where a moveset places among all attackers of [type]. */
data class RaidRank(
    val type: String,
    val rank: Int,
    val score: Double,
    val fast: Move,
    val charged: Move,
    val form: Species,
) {
    val typeLabel: String get() = type.replaceFirstChar { it.uppercase() }
}

/**
 * Raid attacker rankings. Each moveset gets a damage-and-bulk score against a generic boss
 * weak to the charged move's type (at level 40), and is compared with every species' best
 * moveset of that type, Shadows included and Megas left out.
 */
class RaidRanker(private val gd: GameData) {
    private val level = Levels.L40
    private val targetDefense = 180.0

    private fun damage(move: Move, atk: Double, attacker: Species, type: String): Double {
        val stab = if (move.type in attacker.types) 1.2 else 1.0
        val eff = if (move.type == type) 1.6 else 1.0
        return floor(0.5 * move.p * atk / targetDefense * stab * eff) + 1
    }

    /** Higher is better; only comparable within the same [type]. */
    fun score(sp: Species, shadow: Boolean, a: Int, d: Int, s: Int, fast: Move, charged: Move, type: String = charged.type): Double {
        val m = gd.cpm[level]
        val atk = (sp.a + a) * m * (if (shadow) 1.2 else 1.0)
        val def = (sp.d + d) * m * (if (shadow) 5.0 / 6.0 else 1.0)
        val hp = floor((sp.s + s) * m)
        val df = damage(fast, atk, sp, type)
        val tf = fast.ms / 1000.0
        val ef = max(1, fast.e).toDouble()
        val dc = damage(charged, atk, sp, type)
        val tc = charged.ms / 1000.0
        val ec = max(1, -charged.e).toDouble()
        val n = ec / ef
        val dps = max((n * df + dc) / (n * tf + tc), df / tf)
        return dps * (hp * def).pow(0.25)
    }

    private fun movesOf(ids: List<String>) = ids.mapNotNull { gd.moveById[it] }.filter { it.hasRaidStats }

    /** Best entry per species and Shadow, for each charged-move type, strongest first. */
    private val boards: Map<String, List<RaidEntry>> by lazy {
        val best = HashMap<Triple<String, Boolean, String>, RaidEntry>()
        for (sp in gd.species) {
            val fasts = movesOf(sp.fm)
            val chargeds = movesOf(sp.cm)
            if (fasts.isEmpty() || chargeds.isEmpty()) continue
            for (shadow in if (sp.sh) listOf(false, true) else listOf(false)) {
                for (c in chargeds) for (f in fasts) {
                    val sc = score(sp, shadow, 15, 15, 15, f, c)
                    val key = Triple(sp.id, shadow, c.type)
                    if ((best[key]?.score ?: -1.0) < sc) best[key] = RaidEntry(sp, shadow, f, c, sc)
                }
            }
        }
        best.values.groupBy { it.charged.type }.mapValues { (_, v) -> v.sortedByDescending { it.score } }
    }

    fun board(type: String): List<RaidEntry> = boards[type] ?: emptyList()

    private fun rankFor(sp: Species, shadow: Boolean, c: IvCombo, fast: Move, charged: Move): RaidRank {
        val sc = score(sp, shadow, c.atk, c.def, c.sta, fast, charged)
        val better = board(charged.type).count { it.score > sc && !(it.species.id == sp.id && it.shadow == shadow) }
        return RaidRank(charged.type, better + 1, sc, fast, charged, sp)
    }

    /** Rank of this Pokémon with the moves it has, using its best charged move. */
    fun rankOf(sp: Species, shadow: Boolean, c: IvCombo, fastId: String?, chargedIds: List<String>): RaidRank? {
        val fast = fastId?.let { gd.moveById[it] }?.takeIf { it.hasRaidStats } ?: return null
        return chargedIds.mapNotNull { gd.moveById[it] }.filter { it.hasRaidStats }
            .map { rankFor(sp, shadow, c, fast, it) }
            .minByOrNull { it.rank }
    }

    /** Best rank this Pokémon could reach with moves from regular TMs (no elite moves). */
    fun bestWithTm(sp: Species, shadow: Boolean, c: IvCombo): RaidRank? {
        val fasts = movesOf(sp.fm.filter { it !in sp.el })
        val chargeds = movesOf(sp.cm.filter { it !in sp.el })
        var best: RaidRank? = null
        for (f in fasts) for (ch in chargeds) {
            val r = rankFor(sp, shadow, c, f, ch)
            if (best == null || r.rank < best.rank) best = r
        }
        return best
    }
}
