package com.ivlens.app

import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.Nickname
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.23: the name COPY NAME puts on the clipboard, for renaming in the game (12 characters at most).
 * The owner's first try copied "71 7/14/11": no name, though a nickname replaces the species name
 * in the game's list. Now the name leads and the IVs are circled numbers.
 */
class NicknameTest {
    private val l20 = Levels.indexOf(20.0)
    private fun tag(kind: VerdictKind, title: String) = VerdictTag(kind, title, "")

    @Test
    fun nameThenPercentAndIvs() {
        assertEquals("Torchic71⑦⑭⑪", Nickname.of(IvCombo(l20, 7, 14, 11), tag(VerdictKind.TRANSFER, "Transfer"), "Torchic"))
        assertEquals("Mew 91⑭⑫⑮", Nickname.of(IvCombo(l20, 14, 12, 15), tag(VerdictKind.KEEP, "Keep"), "Mew"))
        assertEquals("Drago 100⑮⑮⑮", Nickname.of(IvCombo(l20, 15, 15, 15), tag(VerdictKind.HUNDO, "Hundo"), "Dragonite"))
        assertEquals("Mewtwo 98⑮⑭⑮", Nickname.of(IvCombo(l20, 15, 14, 15), null, "Mewtwo (Armored)"))
    }

    @Test
    fun pvpPicksShowLeagueAndRank() {
        assertEquals("Medich U8⓪⑮⑭", Nickname.of(IvCombo(l20, 0, 15, 14), tag(VerdictKind.PVP, "PvP · Ultra #8"), "Medicham"))
        assertEquals("Flabé G67⓪⑪④", Nickname.of(IvCombo(l20, 0, 11, 4), tag(VerdictKind.PVP, "PvP · Great #67"), "Flabébé"))
        assertEquals("Snorlax M⑮⑭⑮", Nickname.of(IvCombo(l20, 15, 14, 15), tag(VerdictKind.PVP, "PvP · Master"), "Snorlax"))
    }

    @Test
    fun circledNumbers() {
        assertEquals("⓪①⑩⑮", (listOf(0, 1, 10, 15)).joinToString("") { Nickname.circled(it) })
    }

    @Test
    fun neverLongerThanTheGameAllows() {
        for (a in 0..15) for (d in 0..15) for (s in 0..15) {
            val c = IvCombo(l20, a, d, s)
            for (name in listOf("Crabominable", "Mew", "")) {
                assertTrue(Nickname.of(c, tag(VerdictKind.PVP, "PvP · Ultra #100"), name).length <= Nickname.MAX)
                assertTrue(Nickname.of(c, null, name).length <= Nickname.MAX)
            }
        }
    }
}
