package com.ivlens.app

import com.ivlens.app.core.Pokedex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.23: the gameinfo.io page a Ranks name links to (the owner's example: /en/pokemon/199-slowking). */
class PokedexTest {
    private val gd = TestData.gd

    @Test
    fun linksLikeTheSite() {
        assertEquals("https://pokemon.gameinfo.io/en/pokemon/199-slowking", Pokedex.url(gd.byId.getValue("slowking")))
        assertEquals("https://pokemon.gameinfo.io/en/pokemon/122-mr-mime", Pokedex.url(gd.byId.getValue("mr_mime")))
        assertEquals("https://pokemon.gameinfo.io/en/pokemon/484-palkia", Pokedex.url(gd.byId.getValue("palkia_origin")))
    }

    @Test
    fun slugs() {
        assertEquals("farfetchd", Pokedex.slug("Farfetch'd"))
        assertEquals("flabebe", Pokedex.slug("Flabébé"))
        assertEquals("ho-oh", Pokedex.slug("Ho-Oh"))
        assertEquals("tapu-koko", Pokedex.slug("Tapu Koko"))
        assertEquals("nidoran-female", Pokedex.slug("Nidoran Female"))
    }

    /** The site goes by the number, so every species needs a real one. */
    @Test
    fun everySpeciesGetsANumberedLink() {
        val ok = Regex("""https://pokemon\.gameinfo\.io/en/pokemon/[1-9][0-9]*-[a-z0-9-]+""")
        for (sp in gd.species) assertTrue("${sp.name}: ${Pokedex.url(sp)}", ok.matches(Pokedex.url(sp)))
    }
}
