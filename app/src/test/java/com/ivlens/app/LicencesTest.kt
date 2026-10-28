package com.ivlens.app

import com.ivlens.app.ui.LICENCES
import com.ivlens.app.ui.THIRD_PARTY
import com.ivlens.app.ui.parseThirdParty
import com.ivlens.app.ui.reflow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Settings › About › Licences (1.24): MIT asks for PvPoke's notice to ship with the app. */
class LicencesTest {
    private fun asset(path: String) = File("src/main/assets/$path").readText()

    @Test
    fun everyListedFileShips() {
        for ((title, path) in LICENCES) assertTrue(title, asset(path).isNotBlank())
        assertTrue(asset("licenses/pvpoke-MIT.txt").contains("Copyright (c) 2019 pvpoke"))
    }

    @Test
    fun noFillInPlaceholders() {
        // The Apache text's appendix ("Copyright [yyyy] [name of copyright owner]") read as a placeholder.
        for ((_, path) in LICENCES) assertFalse(path, asset(path).contains("[yyyy]"))
        val parts = parseThirdParty(asset(THIRD_PARTY))
        assertTrue(parts.texts.none { it.contains("[yyyy]") })
    }

    @Test
    fun partsInsideGoogleLibrariesAreListed() {
        val parts = parseThirdParty(asset(THIRD_PARTY))
        val names = parts.libraries.map { it.name }
        assertTrue(names.containsAll(listOf("TensorFlow", "Protocol Buffers", "Abseil")))
        assertTrue(parts.libraries.all { it.text in parts.texts.indices })
    }

    @Test
    fun paragraphsBecomeOneLineEach() {
        assertEquals("MIT License\n\nPermission is hereby granted, free of charge,", reflow("MIT License\n\n   Permission is hereby\n   granted, free of charge,\n"))
    }

    @Test
    fun headingsKeepTheirLineAndDashesGo() {
        val ofl = "-----------\nSIL OPEN FONT LICENSE Version 1.1\n-----------\n\nPREAMBLE\nThe goals of the Open Font\nLicense (OFL) are\n\nDISCLAIMER\nTHE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY\nOF ANY KIND."
        assertEquals(
            "SIL OPEN FONT LICENSE Version 1.1\n\nPREAMBLE\nThe goals of the Open Font License (OFL) are\n\nDISCLAIMER\nTHE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
            reflow(ofl),
        )
    }
}
