package com.ivlens.app.core

import java.util.Locale

data class LeagueInfo(
    val league: League,
    val best: PvpRank?,
    val worst: PvpRank?,
    /** Every possible IV combo is already above the league's CP cap at its current level. */
    val tooHigh: Boolean,
    /** No IV combo can fit under the cap, even at level 1. */
    val ineligible: Boolean,
    /** Highest CP it can reach (fully powered, best of the possible IVs). */
    val maxCp: Int = 0,
    /** Can't get near the cap (or big enough for Master League) even fully powered. */
    val tooSmall: Boolean = false,
) {
    val single: Boolean get() = best != null && best == worst

    fun rankText(): String = when {
        ineligible -> "—"
        tooHigh -> "Over cap"
        tooSmall -> "Too small"
        best == null || worst == null -> "—"
        best.rank == worst.rank -> "#${best.rank}"
        else -> "#${best.rank}–${worst.rank}"
    }

    fun detailText(): String {
        if (tooSmall && !tooHigh && !ineligible) return "max CP $maxCp"
        val b = best ?: return ""
        if (tooHigh || ineligible) return ""
        val pct = String.format(Locale.US, "%.1f%%", b.percentOfBest)
        return if (single) "$pct · Lv ${Levels.label(b.level)} · CP ${b.cp}" else "best $pct"
    }
}

data class FormInfo(
    val species: Species,
    val depth: Int,
    val cpMin: Int,
    val cpMax: Int,
    val leagues: List<LeagueInfo>,
) {
    fun cpText(): String = if (cpMin == cpMax) "CP $cpMin" else "CP $cpMin–$cpMax"
}

data class PowerTarget(
    val label: String,
    val level: Int,
    val cpMin: Int,
    val cpMax: Int,
    val cost: Cost,
) {
    fun cpText(): String = if (cpMin == cpMax) "CP $cpMin" else "CP $cpMin–$cpMax"
}

data class Analysis(
    val forms: List<FormInfo>,
    val fromLevel: Int?,
    val powerUps: List<PowerTarget>,
)

class Analyzer(private val gd: GameData, private val ranker: PvpRanker) {

    fun analyze(
        result: CalcResult,
        selected: IvCombo?,
        pvpMaxLevel: Int = Levels.L50,
        powerMaxLevel: Int = Levels.L50,
    ): Analysis {
        val combos = selected?.let { listOf(it) } ?: result.combos
        if (combos.isEmpty()) return Analysis(emptyList(), null, emptyList())

        val chain = listOf(result.species to 0) + gd.evolutionChain(result.species, result.input.gender)
        val forms = chain.map { (sp, depth) -> formInfo(sp, depth, combos, pvpMaxLevel) }

        val from = combos.minOf { it.level }
        val variant = result.input.variant
        val targets = mutableListOf<Pair<String, Int>>()
        if (combos.size == 1) {
            val c = combos[0]
            val maxCp = Stats.cp(result.species, gd.cpm[pvpMaxLevel.coerceAtMost(gd.maxIndex)], c.atk, c.def, c.sta)
            for (lg in listOf(League.GREAT, League.ULTRA)) {
                if (!lg.canCompete(maxCp)) continue
                val r = ranker.table(result.species, lg, pvpMaxLevel)[c.atk, c.def, c.sta] ?: continue
                if (r.level > from && r.level <= powerMaxLevel) targets += "${lg.short} best" to r.level
            }
        }
        if (Levels.L40 in (from + 1)..powerMaxLevel) targets += "Level 40" to Levels.L40
        if (Levels.L50 in (from + 1)..powerMaxLevel) targets += "Level 50" to Levels.L50
        val powerUps = targets.distinctBy { it.second }.sortedBy { it.second }.map { (label, lvl) ->
            val cps = combos.map { Stats.cp(result.species, gd.cpm[lvl], it.atk, it.def, it.sta) }
            PowerTarget(label, lvl, cps.min(), cps.max(), PowerUpCosts.between(from, lvl, variant))
        }
        return Analysis(forms, from, powerUps)
    }

    private fun formInfo(sp: Species, depth: Int, combos: List<IvCombo>, pvpMaxLevel: Int): FormInfo {
        val cpNow = combos.map { Stats.cp(sp, gd.cpm[it.level], it.atk, it.def, it.sta) }
        val maxCp = combos.maxOf { Stats.cp(sp, gd.cpm[pvpMaxLevel.coerceAtMost(gd.maxIndex)], it.atk, it.def, it.sta) }
        val leagues = League.entries.map { lg ->
            val table = ranker.table(sp, lg, pvpMaxLevel)
            val ranks = combos.map { table[it.atk, it.def, it.sta] }
            val usable = combos.indices.filter { i ->
                ranks[i] != null && (lg.cap == null || cpNow[i] <= lg.cap)
            }.map { ranks[it]!! }
            LeagueInfo(
                league = lg,
                best = usable.minByOrNull { it.rank },
                worst = usable.maxByOrNull { it.rank },
                tooHigh = lg.cap != null && cpNow.all { it > lg.cap },
                ineligible = ranks.all { it == null },
                maxCp = maxCp,
                tooSmall = !lg.canCompete(maxCp),
            )
        }
        return FormInfo(sp, depth, cpNow.min(), cpNow.max(), leagues)
    }
}
