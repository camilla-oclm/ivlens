package com.ivlens.app.scan

import com.ivlens.app.core.CalcInput
import kotlin.math.abs

/**
 * Scrolling a Pokémon's page pushes the CP (and later the name and HP) off the top. Those reads
 * are the same Pokémon seen partly, not a new one with a hidden CP: their moves belong to the
 * Pokémon already on the chip, and everything else stays as it was.
 */
object ScrollMerge {

    /** True when [outcome] is a partial view of the Pokémon described by [prev]. */
    fun samePokemonScrolled(prev: CalcInput, prevWeight: Double?, outcome: ScanOutcome): Boolean {
        val r = outcome.reading
        if (!r.pokemonPage) return false
        // A complete read stands on its own.
        val partial = r.cp.isEmpty() || r.hp.isEmpty() || outcome.problem == ScanOutcome.Problem.NO_CP ||
            outcome.problem == ScanOutcome.Problem.NO_SPECIES
        if (!partial) return false
        // Whatever is still visible must agree.
        if (r.hp.isNotEmpty() && prev.hp != null && prev.hp !in r.hp) return false
        if (r.weightKg != null && prevWeight != null && abs(r.weightKg - prevWeight) > 0.015) return false
        val named = r.names.isNotEmpty() || r.candyName != null
        if (named && outcome.candidates.isNotEmpty() && outcome.candidates.none { it.id == prev.speciesId }) return false
        return true
    }

    /**
     * True when [outcome] shows it's [prev] (its weight, else its HP), not only that nothing on it
     * disagrees. Moves from a scroll are remembered for the session only then (1.19): a quick swipe
     * to another Gyarados and a scroll before its top was read could otherwise file its moves
     * under the first one for good.
     */
    fun confirms(prev: CalcInput, prevWeight: Double?, outcome: ScanOutcome): Boolean {
        val r = outcome.reading
        if (r.weightKg != null && prevWeight != null) return abs(r.weightKg - prevWeight) <= 0.015
        return prev.hp != null && prev.hp in r.hp
    }
}
