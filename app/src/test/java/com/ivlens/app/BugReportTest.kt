package com.ivlens.app

import com.ivlens.app.ui.bugReportText
import com.ivlens.app.ui.deviceLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The text "Report a bug" copies or shares (1.23; written in a form since 1.24). */
class BugReportTest {
    private val device = deviceLine("1.24", "Google Pixel 8", "17", "1080×2340")

    @Test
    fun fromSettingsItHasWhatWasWrittenAndThePhone() {
        val t = bugReportText("  The chip covered the CP.\n", device, emptyList())
        assertEquals("IV Lens bug report\n\nWhat went wrong:\nThe chip covered the CP.\n\nIV Lens 1.24 · Google Pixel 8 · Android 17 · 1080×2340", t)
        assertFalse(t.contains("What IV Lens read"))
        // The hint about the catch place is in the form now, not in the text sent.
        assertFalse(t.contains("caught on"))
    }

    @Test
    fun fromTheCardItAddsTheRead() {
        val t = bugReportText("Wrong CP", device, listOf("Pokémon: Torchic", "CP 10 · HP 84 · Stardust 2500"))
        assertEquals("CP 10 · HP 84 · Stardust 2500", t.lines().last())
        assertTrue(t.contains("What IV Lens read:\nPokémon: Torchic"))
    }
}
