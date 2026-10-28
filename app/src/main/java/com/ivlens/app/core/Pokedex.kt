package com.ivlens.app.core

import java.text.Normalizer

/**
 * A link to the species' Pokémon GO page on pokemon.gameinfo.io (1.23, the owner's pick: movesets,
 * counters and CP, where the official Pokédex covers the main games). It opens in the browser, so
 * IV Lens still needs no internet access.
 *
 * The site finds a page by its Pokédex number, so a slug it spells differently still lands right
 * (checked 2026-10-26: 29-nidoran-female goes to 29-nidoran, 199-wrongname to 199-slowking).
 * Forms go to their species' page: "Palkia (Origin)" to 484-palkia.
 */
object Pokedex {
    private const val BASE = "https://pokemon.gameinfo.io/en/pokemon/"

    fun url(sp: Species): String = BASE + sp.dex + "-" + slug(sp.base.ifBlank { sp.name })

    /** "Mr. Mime" → mr-mime, "Farfetch'd" → farfetchd, "Flabébé" → flabebe, "Palkia (Origin)" → palkia. */
    fun slug(name: String): String =
        Normalizer.normalize(name.substringBefore(" (").trim(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .lowercase()
            .replace(Regex("""['’.:]"""), "")
            .trim()
            .replace(Regex("""\s+"""), "-")
}
