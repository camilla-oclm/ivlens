package com.ivlens.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "IV Lens 1.24 · Google Pixel 8 · Android 17 · 1080×2340": what a bug report says about the phone. */
fun deviceLine(version: String, phone: String, android: String, screen: String): String =
    "IV Lens $version · $phone · Android $android · $screen"

private fun deviceLine(context: Context): String {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    val dm = context.resources.displayMetrics
    return deviceLine(version, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.RELEASE, "${dm.widthPixels}×${dm.heightPixels}")
}

/** The report: what the user wrote, the [device] line and, from the card, what IV Lens [read]. */
fun bugReportText(what: String, device: String, read: List<String>): String = buildString {
    appendLine("IV Lens bug report")
    appendLine()
    appendLine("What went wrong:")
    appendLine(what.trim())
    appendLine()
    appendLine(device)
    if (read.isNotEmpty()) {
        appendLine()
        appendLine("What IV Lens read:")
        read.forEach { appendLine(it) }
    }
}.trimEnd()

private fun copyBugReport(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("IV Lens bug report", text))
    // Android 13 and later show their own "copied" note.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied. Paste it in a new post.", Toast.LENGTH_SHORT).show()
}

private fun shareBugReport(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "IV Lens bug report")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "Report a bug")) }
}

/**
 * "Report a bug" (1.24; a Share menu straight away before): a form to write what went wrong, which
 * shows what's added to it (app version, phone, Android version, screen size and, from the card,
 * what IV Lens [read]), then copies it and opens Discord's #bug-reports, or shares it. Text only:
 * the game prints where each Pokémon was caught, so screenshots are left to the user, with a hint.
 */
@Composable
fun BugReportDialog(read: List<String>, onClose: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val device = remember { deviceLine(context) }
    var what by rememberSaveable { mutableStateOf("") }
    val ready = what.isNotBlank()
    val text = bugReportText(what, device, read)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Block.Paper,
        title = { Text("Report a bug", style = blockText(20.sp, 900)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    what, { what = it }, label = { Text("What went wrong?") }, minLines = 4,
                    colors = blockFieldColors(), modifier = Modifier.fillMaxWidth(),
                )
                Small("A screenshot helps. Crop or cover its \"caught on … around …\" line first: it shows where you were.")
                Text("ADDED TO YOUR REPORT", style = blockText(11.sp, 700, Block.Muted), modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                Text(device, style = blockText(13.sp, 700))
                if (read.isNotEmpty()) {
                    Text("What IV Lens read:", style = blockText(13.sp, 700), modifier = Modifier.padding(top = 6.dp))
                    read.forEach { Text(it, style = blockText(13.sp, 500)) }
                }
                Small("Your phone model and Android version help us find the bug. Nothing leaves your phone until you paste or share it.")
                Text(
                    "COPY & OPEN DISCORD copies it and opens #bug-reports in our Discord: start a new post there and paste it.",
                    style = blockText(13.sp, 500),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyBugReport(context, text)
                    runCatching { uri.openUri(StudioLinks.BUG_REPORTS) }
                    onClose()
                },
                enabled = ready,
            ) { Text("COPY & OPEN DISCORD", style = blockText(14.sp, 900, if (ready) Block.Ink else Block.Muted)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClose) { Text("CANCEL", style = blockText(14.sp, 900)) }
                TextButton(onClick = { shareBugReport(context, text); onClose() }, enabled = ready) {
                    Text("SHARE", style = blockText(14.sp, 900, if (ready) Block.Ink else Block.Muted))
                }
            }
        },
    )
}
