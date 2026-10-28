package com.ivlens.app.scan

import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.CalcResult
import com.ivlens.app.core.GameData
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.Levels
import com.ivlens.app.core.Species
import com.ivlens.app.core.Variant

data class ScanOutcome(
    val reading: ScreenReading,
    val arc: ArcReading?,
    /** Species the screen could be showing, best guess first. */
    val candidates: List<Species>,
    val input: CalcInput,
    val result: CalcResult?,
    val problem: Problem?,
    /** Exact IVs when this was the game's Appraise screen. */
    val appraisal: Appraisal? = null,
    /** The Appraise screen's stat labels are showing (the bars may still be filling). */
    val appraising: Boolean = false,
) {
    enum class Problem { NO_SPECIES, NO_CP, NO_MATCH }
}

class ScanResolver(
    private val gd: GameData,
    private val calc: IvCalculator,
    private val matcher: SpeciesMatcher,
) {
    /** Highest level a trainer of [trainerLevel] can power a Pokémon to. */
    fun maxLevelIndex(trainerLevel: Int): Int = Levels.indexOf(minOf(trainerLevel + 10, 50).toDouble())

    fun candidates(reading: ScreenReading): List<Species> {
        val families = reading.candyName?.let { matcher.familiesForCandy(it) } ?: emptyList()
        val byName = reading.names.asSequence().map { matcher.matchName(it) }.firstOrNull { it.isNotEmpty() } ?: emptyList()
        val cands = if (byName.isNotEmpty()) {
            val inFamily = byName.filter { it.fam in families }
            if (inFamily.isNotEmpty()) inFamily else byName
        } else {
            families.flatMap { matcher.familyMembers(it) }
        }
        // Forms that share a name differ in type: keep the ones with the types on screen. Before
        // 1.8, a Hisuian Qwilfish was read as a plain one, whose CP and HP happened to fit too.
        val typed = cands.filter { sp -> reading.types.isNotEmpty() && reading.types.all { it in sp.types } }
        return typed.ifEmpty { cands }
    }

    private val moves = MoveMatcher(gd)

    fun resolve(reading: ScreenReading, arc: ArcReading?, trainerLevel: Int, useArc: Boolean = true): ScanOutcome {
        // Moves and gender come from the screen whichever species and variant is tried.
        fun CalcInput.withMoves(sp: Species?): CalcInput {
            val (f, c) = moves.read(reading.moves, sp)
            return copy(fast = f?.id, charged = c.map { it.id }, gender = reading.gender)
        }
        val cands = candidates(reading)
        val maxIdx = maxLevelIndex(trainerLevel)
        val hint = if (useArc && arc != null) ArcReader.levelsNear(gd, arc, maxIdx) else null
        val variants = when {
            reading.shadow -> listOf(Variant(shadow = true)) + Variant.autoOrder.filter { !it.shadow }
            reading.lucky -> listOf(Variant(lucky = true), Variant(purified = true, lucky = true)) + Variant.autoOrder.filter { !it.lucky }
            else -> Variant.autoOrder
        }
        val first = cands.firstOrNull()
        val baseInput = CalcInput(
            speciesId = first?.id,
            cp = reading.cp.firstOrNull(),
            hp = reading.hp.firstOrNull(),
            dust = reading.dust.firstOrNull(),
            lucky = reading.lucky && !reading.shadow,
            shadow = reading.shadow,
            levelHint = hint,
        ).withMoves(first)
        if (first == null) return ScanOutcome(reading, arc, cands, baseInput, null, ScanOutcome.Problem.NO_SPECIES)
        if (reading.cp.isEmpty()) return ScanOutcome(reading, arc, cands, baseInput, null, ScanOutcome.Problem.NO_CP)

        val hps: List<Int?> = reading.hp.take(2).ifEmpty { listOf(null) }
        // A stardust cost that was read is trusted: dropping it would let a misread CP
        // match some unrelated level and look like a confident answer.
        val dusts: List<Int?> = reading.dust.take(3).ifEmpty { listOf(null) }
        for (cp in reading.cp.take(3)) for (hp in hps) for (dust in dusts) {
            val vs = if (dust == null) variants.take(1) else variants
            for (v in vs) for (sp in cands) {
                val input = CalcInput(sp.id, cp, hp, dust, levelHint = hint).withVariant(v).withMoves(sp)
                val r = calc.calculate(input, maxIdx) ?: continue
                if (r.combos.isNotEmpty()) return ScanOutcome(reading, arc, cands, input, r, null)
            }
        }
        // The stardust may have been misread (e.g. another button's cost). If the level arc
        // was read, it can stand in for the stardust: CP, HP and arc must all agree.
        if (!hint.isNullOrEmpty() && reading.dust.isNotEmpty()) {
            for (cp in reading.cp.take(3)) for (hp in hps) for (sp in cands) {
                for (v in variants.take(if (reading.shadow) 1 else 3)) {
                    val input = CalcInput(sp.id, cp, hp, null, levelHint = hint).withVariant(v).withMoves(sp)
                    val r = calc.calculate(input, maxIdx) ?: continue
                    if (r.usedArc && r.combos.isNotEmpty()) return ScanOutcome(reading, arc, cands, input, r, null)
                }
            }
        }
        val fallback = calc.calculate(baseInput, maxIdx)
        return ScanOutcome(reading, arc, cands, baseInput, fallback, ScanOutcome.Problem.NO_MATCH)
    }
}
