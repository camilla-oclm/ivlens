package com.ivlens.app.core

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
data class IvCombo(val level: Int, val atk: Int, val def: Int, val sta: Int) {
    val sum: Int get() = atk + def + sta
    val percent: Int get() = (sum * 100.0 / 45.0).roundToInt()
    val ivText: String get() = "$atk/$def/$sta"
}

object Stats {
    fun cp(sp: Species, cpm: Double, a: Int, d: Int, s: Int): Int {
        val v = (sp.a + a) * sqrt((sp.d + d).toDouble()) * sqrt((sp.s + s).toDouble()) * cpm * cpm / 10.0
        return max(10, floor(v).toInt())
    }

    fun hp(sp: Species, cpm: Double, s: Int): Int = max(10, floor((sp.s + s) * cpm).toInt())

    fun statProduct(sp: Species, cpm: Double, a: Int, d: Int, s: Int): Double =
        (sp.a + a) * cpm * (sp.d + d) * cpm * hp(sp, cpm, s)
}

/** What the user (or the screen reader) knows about one Pokémon. */
@Serializable
data class CalcInput(
    val speciesId: String? = null,
    val cp: Int? = null,
    val hp: Int? = null,
    val dust: Int? = null,
    val shadow: Boolean = false,
    val purified: Boolean = false,
    val lucky: Boolean = false,
    /** Level indices suggested by the level arc; used only when consistent with the rest. */
    val levelHint: List<Int>? = null,
    /** Move ids read from the screen or picked in the calculator. */
    val fast: String? = null,
    val charged: List<String> = emptyList(),
    /** From the ♂ / ♀ icon; null when genderless or not read. Some evolutions need one gender. */
    val gender: Gender? = null,
) {
    val variant: Variant get() = Variant(shadow, purified && !shadow, lucky && !shadow)
    fun withVariant(v: Variant) = copy(shadow = v.shadow, purified = v.purified, lucky = v.lucky)
}

data class CalcResult(
    val species: Species,
    val input: CalcInput,
    val levels: List<Int>,
    val combos: List<IvCombo>,
    val usedArc: Boolean,
) {
    val ivMin: Int? get() = combos.minOfOrNull { it.percent }
    val ivMax: Int? get() = combos.maxOfOrNull { it.percent }
    val levelMin: Int? get() = combos.minOfOrNull { it.level }
    val levelMax: Int? get() = combos.maxOfOrNull { it.level }
    val exact: IvCombo? get() = combos.singleOrNull()

    fun ivRangeText(): String = when {
        combos.isEmpty() -> "–"
        ivMin == ivMax -> "${ivMin}%"
        else -> "${ivMin}–${ivMax}%"
    }

    fun levelRangeText(): String {
        val lo = levelMin ?: return "–"
        val hi = levelMax ?: return "–"
        return if (lo == hi) "Lv ${Levels.label(lo)}" else "Lv ${Levels.label(lo)}–${Levels.label(hi)}"
    }
}

class IvCalculator(private val gd: GameData) {

    fun combos(sp: Species, cp: Int, hp: Int?, levels: Collection<Int>): List<IvCombo> {
        val out = ArrayList<IvCombo>()
        for (li in levels) {
            if (li !in 0..Levels.L50) continue
            val m = gd.cpm[li]
            for (s in 0..15) {
                if (hp != null && Stats.hp(sp, m, s) != hp) continue
                for (a in 0..15) for (d in 0..15) {
                    if (Stats.cp(sp, m, a, d, s) == cp) out += IvCombo(li, a, d, s)
                }
            }
        }
        out.sortWith(compareByDescending<IvCombo> { it.sum }.thenByDescending { it.atk }.thenBy { it.level })
        return out
    }

    fun levelsFor(dust: Int?, v: Variant, maxLevel: Int = Levels.L50): List<Int> =
        if (dust == null) (0..maxLevel).toList() else PowerUpCosts.levelsForDust(dust, v)

    /**
     * The CP of exact IVs [a]/[d]/[s] when the CP itself is hidden (an XXL Munna covers it,
     * 2026-10-23). The level must fit the stardust, the HP and the level arc ([CalcInput.levelHint])
     * when it was read. Null unless exactly one level fits.
     */
    fun cpFor(input: CalcInput, a: Int, d: Int, s: Int, maxLevel: Int = Levels.L50): Int? {
        val sp = input.speciesId?.let { gd.byId[it] } ?: return null
        val hp = input.hp ?: return null
        val byHp = levelsFor(input.dust, input.variant, maxLevel).filter { it <= maxLevel && Stats.hp(sp, gd.cpm[it], s) == hp }
        val hint = input.levelHint.orEmpty()
        val level = byHp.filter { it in hint }.ifEmpty { byHp }.singleOrNull() ?: return null
        return Stats.cp(sp, gd.cpm[level], a, d, s)
    }

    /** Returns null when the species is unknown or CP is missing. */
    fun calculate(input: CalcInput, maxLevel: Int = Levels.L50): CalcResult? {
        val sp = input.speciesId?.let { gd.byId[it] } ?: return null
        val cp = input.cp ?: return null
        val base = levelsFor(input.dust, input.variant, maxLevel)
        val hint = input.levelHint
        if (!hint.isNullOrEmpty()) {
            val narrowed = if (input.dust == null) hint.filter { it <= maxLevel } else base.filter { it in hint }
            if (narrowed.isNotEmpty()) {
                val c = combos(sp, cp, input.hp, narrowed)
                if (c.isNotEmpty()) return CalcResult(sp, input, narrowed, c, usedArc = true)
            }
        }
        return CalcResult(sp, input, base, combos(sp, cp, input.hp, base), usedArc = false)
    }
}
