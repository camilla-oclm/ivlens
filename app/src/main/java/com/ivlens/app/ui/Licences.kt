package com.ivlens.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The notices the game data, font and libraries ask to ship with the app (assets/licenses). */
internal val LICENCES = listOf(
    "PvPoke game master and rankings (MIT)" to "licenses/pvpoke-MIT.txt",
    "Archivo font (SIL Open Font License 1.1)" to "licenses/archivo-OFL.txt",
    "AndroidX, Jetpack Compose, Kotlin and the other libraries (Apache License 2.0)" to "licenses/apache-2.0.txt",
)

/** The open-source parts inside ML Kit and Google Play services, collected by tools/licences.py. */
internal const val THIRD_PARTY = "licenses/third-party.json"

@Serializable
internal data class ThirdParty(val libraries: List<Part>, val texts: List<String>)

/** [text]: its index in [ThirdParty.texts], which holds each text once. */
@Serializable
internal data class Part(val name: String, val text: Int)

internal fun parseThirdParty(json: String): ThirdParty = Json.decodeFromString(ThirdParty.serializer(), json)

private val SEPARATOR = Regex("[-=_*#/ ]{3,}")

private fun isHeading(line: String) = line.length <= 60 && line.any { it.isLetter() } && line.none { it.isLowerCase() }

/**
 * The files are hard-wrapped for a wide screen: each paragraph becomes one line. A heading (a short
 * line in capitals before ordinary text, or of one or two words, like the OFL's PREAMBLE and
 * DISCLAIMER) keeps its own line, and lines of dashes go.
 */
internal fun reflow(text: String): String =
    text.trim().split(Regex("\\n\\s*\\n")).mapNotNull { p ->
        val lines = p.lines().map { it.trim().replace(Regex("\\s{2,}"), " ") }.filter { it.isNotEmpty() && !it.matches(SEPARATOR) }
        when {
            lines.isEmpty() -> null
            lines.size > 1 && isHeading(lines[0]) && (lines[1].any { it.isLowerCase() } || lines[0].split(' ').size <= 2) ->
                lines[0] + "\n" + lines.drop(1).joinToString(" ")
            else -> lines.joinToString(" ")
        }
    }.joinToString("\n\n")

private fun readAsset(context: Context, path: String): String =
    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")

/** Settings › About › Licences (1.24): a list of names; a tap opens that licence. */
@Composable
internal fun LicencesDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val own = remember { LICENCES.map { (title, path) -> title to readAsset(context, path) } }
    // About 1.4 MB, so it's read off the main thread.
    val parts by produceState<ThirdParty?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { parseThirdParty(readAsset(context, THIRD_PARTY)) }.getOrNull() }
    }
    var open by remember { mutableStateOf<Pair<String, String>?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Block.Paper,
        title = { Text("Licences", style = blockText(20.sp, 900)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Text recognition is Google's ML Kit, running on the phone. ML Kit and Google Play services come under " +
                        "Google's ML Kit Terms of Service and the Android SDK License. Raid move stats come from the PokeMiners " +
                        "game master. Tap a name to read its licence.",
                    style = blockText(13.sp, 500),
                )
                for ((title, text) in own) LicenceRow(title) { open = title to text }
                Text(
                    "INSIDE ML KIT AND GOOGLE PLAY SERVICES",
                    style = blockText(11.sp, 700, Block.Muted),
                    modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
                )
                val p = parts
                if (p == null) Small("Loading")
                else for (part in p.libraries) LicenceRow(part.name) { open = part.name to p.texts[part.text] }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("CLOSE", style = blockText(14.sp, 900)) } },
    )
    open?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = Block.Paper,
            title = { Text(title, style = blockText(18.sp, 900)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(remember(text) { reflow(text) }, style = blockText(11.sp, 500, Block.Muted))
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text("BACK", style = blockText(14.sp, 900)) } },
        )
    }
}

@Composable
private fun LicenceRow(title: String, onOpen: () -> Unit) {
    Text(title, style = blockText(14.sp, 700), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp))
}
