package com.ivlens.app.core


/** Shadow / purified / lucky flags; they change power-up costs. */
data class Variant(val shadow: Boolean = false, val purified: Boolean = false, val lucky: Boolean = false) {
    val dustMultiplier: Double
        get() = (if (shadow) 1.2 else if (purified) 0.9 else 1.0) * (if (lucky) 0.5 else 1.0)
    val candyMultiplier: Double
        get() = if (shadow) 1.2 else if (purified) 0.9 else 1.0

    val label: String
        get() = listOfNotNull(
            if (shadow) "Shadow" else null,
            if (purified) "Purified" else null,
            if (lucky) "Lucky" else null,
        ).joinToString(" · ").ifEmpty { "Normal" }

    companion object {
        val NORMAL = Variant()
        /** Order tried when the screen doesn't say which kind of Pokémon it is. */
        val autoOrder = listOf(
            Variant(),
            Variant(lucky = true),
            Variant(shadow = true),
            Variant(purified = true),
            Variant(purified = true, lucky = true),
        )
    }
}

data class Cost(val stardust: Int = 0, val candy: Int = 0, val candyXl: Int = 0) {
    operator fun plus(o: Cost) = Cost(stardust + o.stardust, candy + o.candy, candyXl + o.candyXl)
}

object PowerUpCosts {
    private val DUST = intArrayOf(
        200, 400, 600, 800, 1000, 1300, 1600, 1900, 2200, 2500,
        3000, 3500, 4000, 4500, 5000, 6000, 7000, 8000, 9000, 10000,
        11000, 12000, 13000, 14000, 15000,
    )

    /** Base stardust to power up from level index i (levels 1..49.5). */
    fun baseDust(i: Int): Int? = if (i in 0 until Levels.L50) DUST[i / 4] else null

    fun baseCandy(i: Int): Int = when (i) {
        in 0..19 -> 1
        in 20..39 -> 2
        in 40..49 -> 3
        in 50..59 -> 4
        in 60..63 -> 6
        in 64..67 -> 8
        in 68..71 -> 10
        in 72..75 -> 12
        in 76..77 -> 15
        else -> 0
    }

    fun baseXl(i: Int): Int = when (i) {
        in 78..81 -> 10
        in 82..85 -> 12
        in 86..89 -> 15
        in 90..93 -> 17
        in 94..97 -> 20
        else -> 0
    }

    /**
     * Stardust shown on the POWER UP button at level index i. The game multiplies in
     * single precision and rounds up, which is why a Shadow's 800 shows as 961.
     */
    fun dust(i: Int, v: Variant): Int? = baseDust(i)?.let { ceilF(it, v.dustMultiplier) }

    private fun ceilF(n: Int, m: Double): Int =
        if (m == 1.0) n else kotlin.math.ceil((n.toFloat() * m.toFloat()).toDouble()).toInt()

    fun step(i: Int, v: Variant): Cost {
        val d = dust(i, v) ?: return Cost()
        fun mult(n: Int) = if (n == 0) 0 else ceilF(n, v.candyMultiplier)
        return Cost(d, mult(baseCandy(i)), mult(baseXl(i)))
    }

    fun between(from: Int, to: Int, v: Variant): Cost {
        var c = Cost()
        for (i in from until to) c += step(i, v)
        return c
    }

    /**
     * Level indices whose power-up cost matches [shown] for variant [v]. Multiplied costs
     * allow a 1-stardust rounding difference.
     */
    fun levelsForDust(shown: Int, v: Variant): List<Int> {
        val slack = if (v.dustMultiplier == 1.0) 0 else 1
        return (0 until Levels.L50).filter { i -> dust(i, v)?.let { kotlin.math.abs(it - shown) <= slack } == true }
    }

    /** Every stardust amount the POWER UP button can show, for any variant. */
    val allShownValues: Set<Int> by lazy {
        Variant.autoOrder.flatMap { v ->
            (0 until Levels.L50).mapNotNull { dust(it, v) }.flatMap { d -> if (v.dustMultiplier == 1.0) listOf(d) else listOf(d - 1, d, d + 1) }
        }.toSet()
    }
}
