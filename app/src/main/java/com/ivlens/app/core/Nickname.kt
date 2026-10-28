package com.ivlens.app.core

/**
 * A name to paste when renaming a Pokémon in the game (1.23), so its IVs show in the Pokémon
 * list from then on. A nickname takes the species name's place there, so the name leads.
 *
 * The game allows 12 characters, so the IVs are circled numbers, one character each (⓪ to ⑮, as
 * Calcy IV made common): "Torchic71⑦⑭⑪" is 71% with 7/14/11. A PvP pick shows its league letter
 * and rank instead of the percent ("Medich U8⓪⑮⑭"), a hundo "100". A long name is cut short.
 */
object Nickname {
    const val MAX = 12
    private val PVP = Regex("""PvP · (Great|Ultra|Master)(?: #(\d+))?""")

    fun of(c: IvCombo, primary: VerdictTag?, speciesName: String): String {
        val pvp = primary?.takeIf { it.kind == VerdictKind.PVP }?.let { PVP.find(it.title) }
        val head = pvp?.let { it.groupValues[1].take(1) + it.groupValues[2] } ?: "${c.percent}"
        val tail = head + circled(c.atk) + circled(c.def) + circled(c.sta)
        // "Mewtwo (Armored)": the form doesn't fit, and the game shows it anyway.
        val name = speciesName.substringBefore(" (").trim()
        val room = MAX - tail.length
        return when {
            name.isEmpty() -> tail
            name.length + 1 <= room -> "$name $tail"
            // Letters straight into digits still read apart: "Torchic71⑦⑭⑪".
            name.length <= room && tail.first().isDigit() -> "$name$tail"
            else -> "${name.take(room - 1).trimEnd()} $tail"
        }
    }

    /** 0 to 15 as ⓪ to ⑮. */
    fun circled(n: Int): String = if (n == 0) "⓪" else (0x2460 + n - 1).toChar().toString()
}
