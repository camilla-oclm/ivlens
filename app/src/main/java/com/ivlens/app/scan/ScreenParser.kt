package com.ivlens.app.scan

import com.ivlens.app.core.PowerUpCosts

/** One line of recognized text with its box in screen pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val cx: Double get() = (left + right) / 2.0
    val cy: Double get() = (top + bottom) / 2.0
    val height: Int get() = bottom - top
}

/** Everything read from one Pokémon screen. Each list is ordered from most to least likely. */
data class ScreenReading(
    val cp: List<Int>,
    val hp: List<Int>,
    val dust: List<Int>,
    val names: List<String>,
    val candyName: String?,
    val lucky: Boolean,
    /** A PURIFY button or "Shadow bonus" on the moves means it's a Shadow. */
    val shadow: Boolean = false,
    /** Move names as read, fast move first. */
    val moves: List<String> = emptyList(),
    val weightKg: Double? = null,
    val heightM: Double? = null,
    /** Looks like part of a Pokémon's own screen, even when scrolled. */
    val pokemonPage: Boolean = false,
    /** Type labels under the weight and height ("DARK / POISON"), lower case like the game data. */
    val types: List<String> = emptyList(),
    /** Where the name was read; the ♂ / ♀ icon sits just right of it and a little lower. */
    val nameLine: OcrLine? = null,
    /** From the ♂ / ♀ icon ([GenderReader]), filled in from the pixels after parsing. */
    val gender: com.ivlens.app.core.Gender? = null,
    /** For the debug log: the text that said LUCKY, and where ("LUCKY POKéMON" at 120,880). */
    val luckyText: String? = null,
)

object ScreenParser {

    private val digitFix = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', 'i' to '1', '|' to '1', '!' to '1',
        'S' to '5', 's' to '5', 'B' to '8', 'Z' to '2', 'z' to '2',
    )

    private fun fixDigits(s: String): String = buildString { for (c in s) append(digitFix[c] ?: c) }

    private val cpRun = Regex("[0-9OoDQIl|SBZz]{2,5}")
    private val hpA = Regex("""([0-9OoIlSBZ]{1,4})\s*[/7|]\s*([0-9OoIlSBZ]{1,4})\s*H\s*P""", RegexOption.IGNORE_CASE)
    private val hpB = Regex("""H\s*P\s*([0-9OoIlSBZ]{1,4})\s*/\s*([0-9OoIlSBZ]{1,4})""", RegexOption.IGNORE_CASE)
    private val weightRe = Regex("""(\d{1,4}(?:[.,]\d{1,2})?)\s*kg""", RegexOption.IGNORE_CASE)
    private val heightRe = Regex("""^\s*(\d{1,2}[.,]\d{1,2})\s*m\s*$""", RegexOption.IGNORE_CASE)
    private val number = Regex("""(?<![\d.,])(\d{1,2}[,.]\d{3}|\d{2,5})(?![\d.,]*\d)""")

    fun parse(lines: List<OcrLine>, width: Int, height: Int): ScreenReading {
        val w = width.toDouble()
        val h = height.toDouble()
        val up = lines.map { it.text.uppercase() }

        // ---- CP: big number near the top, horizontally centred ----
        data class CpCand(val value: Int, val score: Double)
        val cps = mutableListOf<CpCand>()
        lines.forEachIndexed { i, ln ->
            if (ln.cy < 0.03 * h || ln.cy > 0.22 * h) return@forEachIndexed
            if (ln.cx < 0.2 * w || ln.cx > 0.8 * w) return@forEachIndexed
            if (ln.text.contains(':')) return@forEachIndexed
            // When scrolled, the HP line, weight and stardust total can sit up here too.
            if (hpA.containsMatchIn(ln.text) || hpB.containsMatchIn(ln.text)) return@forEachIndexed
            if (ln.text.contains("kg", ignoreCase = true) || heightRe.containsMatchIn(ln.text)) return@forEachIndexed
            val compact = up[i].replace(" ", "")
            val hasCp = compact.contains("CP")
            if (!hasCp && (Regex("""\d[.,/]\d""").containsMatchIn(ln.text) || ln.height < 0.022 * h)) return@forEachIndexed
            for (m in cpRun.findAll(compact)) {
                val raw = fixDigits(m.value)
                val options = linkedSetOf(raw)
                if (raw.length > 2 && !m.value[0].isDigit()) options += raw.drop(1)
                for (o in options) {
                    val v = o.toIntOrNull() ?: continue
                    // Small numbers only count when "CP" is readable: a Pokémon covering the CP
                    // often leaves a fragment like "18" behind.
                    if (v !in 10..6000 || (v < 100 && !hasCp)) continue
                    cps += CpCand(v, (if (hasCp) 1000.0 else 0.0) + ln.height + (if (m.value[0].isDigit()) 1 else 0))
                }
            }
        }
        val cp = cps.sortedByDescending { it.score }.map { it.value }.distinct()

        // ---- HP: "86 / 171 HP" ----
        var hpLine: OcrLine? = null
        val hps = mutableListOf<Int>()
        for (ln in lines.sortedBy { kotlin.math.abs(it.cy - 0.47 * h) }) {
            val m = hpA.find(ln.text) ?: hpB.find(ln.text) ?: continue
            val cur = fixDigits(m.groupValues[1]).toIntOrNull() ?: continue
            val max = fixDigits(m.groupValues[2]).toIntOrNull() ?: continue
            if (max !in 10..999 || cur > max) continue
            if (hpLine == null) hpLine = ln
            hps += max
        }

        // ---- Name: the line just above the HP text ----
        val names = mutableListOf<String>()
        var nameLine: OcrLine? = null
        val nameLines = if (hpLine != null) {
            lines.filter { it !== hpLine && it.bottom <= hpLine.top + hpLine.height && it.top >= hpLine.top - 0.10 * h }
                .sortedBy { hpLine.top - it.bottom }
        } else {
            lines.filter { it.cy in (0.33 * h)..(0.52 * h) }.sortedByDescending { it.height }
        }
        for (ln in nameLines) {
            if (ln.cx < 0.15 * w || ln.cx > 0.85 * w) continue
            val cleaned = cleanName(ln.text)
            if (cleaned.count { it.isLetter() } < 3 || ln.text.count { it.isDigit() } > 1) continue
            names += cleaned
            if (nameLine == null) nameLine = ln
            cleaned.split(' ').firstOrNull()?.takeIf { it.length >= 3 && it != cleaned }?.let { names += it }
        }

        // ---- Candy family: "MAGIKARP CANDY" (sometimes wrapped onto two lines) ----
        var candy: String? = null
        for ((i, ln) in lines.withIndex()) {
            val t = up[i]
            val idx = t.indexOf("CANDY")
            if (idx < 0) continue
            val before = cleanName(t.substring(0, idx)).trim()
            if (before.count { it.isLetter() } >= 3) { candy = before; break }
            val above = lines.filter {
                it !== ln && it.bottom <= ln.top + ln.height * 0.4 && it.top >= ln.top - ln.height * 2.6 &&
                    it.left < ln.right && it.right > ln.left
            }.maxByOrNull { it.bottom }
            val name = above?.let { cleanName(it.text).trim() }
            if (name != null && name.count { it.isLetter() } >= 3 && !name.uppercase().contains("STARDUST")) {
                candy = name; break
            }
        }

        // ---- Stardust cost on the POWER UP row ----
        fun compact(i: Int) = up[i].replace(" ", "")
        val tabLine = lines.indices.filter { i ->
            val t = compact(i)
            t.contains("GYMS") || t.contains("RAIDS") || t.contains("TRAINERBATTLE")
        }.minByOrNull { lines[it].top }?.let { lines[it] }
        val stop = tabLine?.top?.toDouble() ?: h
        // Other buttons whose costs look like stardust amounts (Purify 3,000, Evolve 200...).
        val otherButtons = lines.indices.filter { i ->
            val t = compact(i)
            t.contains("PURIFY") || t.contains("EVOLVE") || t.contains("NEWATTACK") || t.contains("MEGA")
        }.map { lines[it] }
        fun onOtherButtonRow(ln: OcrLine) = otherButtons.any { b -> kotlin.math.abs(ln.cy - b.cy) <= maxOf(b.height * 1.3, 0.02 * h) }
        val dust = mutableListOf<Int>()
        fun addTokens(ln: OcrLine, allowShort: Boolean) {
            for (m in number.findAll(ln.text)) {
                val v = m.value.replace(",", "").replace(".", "").toIntOrNull() ?: continue
                if (v < 100 && !allowShort) continue
                if (v in PowerUpCosts.allShownValues) dust += v
            }
        }
        val power = lines.withIndex().firstOrNull { compact(it.index).contains("POWERUP") }?.value
        if (power != null) {
            val band = maxOf(power.height * 1.3, 0.02 * h)
            lines.filter { kotlin.math.abs(it.cy - power.cy) <= band && it.right > power.left }
                .sortedBy { it.left }
                .forEach { addTokens(it, allowShort = true) }
        }
        val label = lines.withIndex().firstOrNull { up[it.index].trim() == "STARDUST" }?.value
        val startY = label?.bottom?.toDouble() ?: (0.55 * h)
        lines.filter { it.top > startY && it.cy < stop && !it.text.uppercase().contains("CANDY") && !onOtherButtonRow(it) }
            .sortedBy { it.top }
            .forEach { addTokens(it, allowShort = false) }

        // ---- Moves: names listed under the GYMS & RAIDS / TRAINER BATTLES tabs ----
        val moves = mutableListOf<String>()
        if (tabLine != null) {
            val endY = lines.filter { ln ->
                val t = ln.text.uppercase().replace(" ", "")
                ln.top > tabLine.bottom && (t.contains("NEWATTACK") || t.contains("CAUGHT") || t.contains("MAXMOVES") ||
                    t.contains("SWAPBUDD") || t.contains("TOTALWALKED") || t.startsWith("TEACH"))
            }.minOfOrNull { it.top } ?: height
            for (ln in lines.filter { it.top > tabLine.bottom && it.top < endY }.sortedBy { it.top }) {
                if (ln.left > 0.35 * w) continue
                val t = ln.text.uppercase()
                if (t.contains("BONUS") || t.contains("WEATHER") || t.contains("SHADOW")) continue
                // Move names are in Title Case; buttons such as DRAGON ASCENT are in capitals.
                val letters = ln.text.filter { it.isLetter() }
                if (letters.length > 3 && letters.all { it.isUpperCase() }) continue
                val name = cleanName(ln.text).trim()
                if (name.count { it.isLetter() } >= 3) moves += name
            }
        }

        // ---- Types: "DARK / POISON" in capitals, between the HP and the stardust ----
        // They tell apart forms that share a name (Qwilfish and Hisuian Qwilfish). A label cut off
        // by the Appraise screen's leader ("POIS") is skipped; move names are in Title Case.
        val types = mutableListOf<String>()
        // Scrolled down, the HP is gone and the labels sit near the top of the screen.
        val typeTop = hpLine?.bottom?.toDouble() ?: 0.0
        val typeBottom = listOfNotNull(label?.top, power?.top, tabLine?.top).minOrNull()?.toDouble() ?: (0.75 * h)
        for (ln in lines.filter { it.cy > typeTop && it.cy < typeBottom && it.cx > 0.2 * w && it.cx < 0.8 * w }.sortedBy { it.left }) {
            val words = ln.text.split(Regex("[^A-Za-z]+")).filter { it.length >= 3 }
            if (words.isEmpty() || words.any { wd -> wd != wd.uppercase() || TYPES.none { it.startsWith(wd.lowercase()) } }) continue
            types += words.map { it.lowercase() }.filter { it in TYPES }
        }

        // ---- Weight and height, which identify this Pokémon while scrolling ----
        val weight = lines.firstNotNullOfOrNull { weightRe.find(it.text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() }
        val heightM = lines.firstNotNullOfOrNull { ln ->
            if (ln.text.contains("kg", ignoreCase = true)) null
            else heightRe.find(ln.text)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        }

        val luckyLine = lines.firstOrNull { it.text.uppercase().contains("LUCKY") }
        val lucky = luckyLine != null
        val shadow = up.any { it.replace(" ", "").contains("PURIFY") || it.contains("SHADOW BONUS") }
        val pokemonPage = hps.isNotEmpty() || weight != null || tabLine != null || power != null || label != null ||
            up.any { t -> listOf("STARDUST", "CAUGHT", "SWAP BUDD", "WEIGHT", "HEIGHT").any { t.contains(it) } }
        return ScreenReading(
            cp = cp,
            hp = hps.distinct(),
            dust = dust.distinct(),
            names = names.distinct(),
            candyName = candy,
            lucky = lucky,
            shadow = shadow,
            moves = moves,
            weightKg = weight,
            heightM = heightM,
            pokemonPage = pokemonPage,
            types = types.distinct().take(2),
            nameLine = nameLine,
            luckyText = luckyLine?.let { "\"${it.text}\" at ${it.left},${it.top}" },
        )
    }

    private val TYPES = listOf(
        "normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
        "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
    )

    fun cleanName(s: String): String =
        s.filter { it.isLetter() || it == ' ' || it == '\'' || it == '.' || it == '-' || it == '♀' || it == '♂' || it == ':' }
            .replace(Regex("\\s+"), " ").trim()
}
