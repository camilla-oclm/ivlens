package com.ivlens.app.ui

import android.content.Context
import android.net.Uri
import com.ivlens.app.store.CollectionStore
import com.ivlens.app.store.SavedMerge
import com.ivlens.app.store.SavedMon
import com.ivlens.app.store.decodeSavedFile
import com.ivlens.app.store.encodeSavedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Import offers to open. Some file apps call a .json file plain text or just bytes. */
val SAVED_FILE_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** A saved list is about 500 bytes a Pokémon; anything bigger than this is the wrong file. */
private const val MAX_SAVED_FILE = 20 shl 20

/** The name Export suggests: ivlens-saved-2026-10-27.json. */
fun savedFileName(now: Date = Date()): String = "ivlens-saved-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)}.json"

/**
 * Settings › Saved list › Export (1.24): writes [items] to [uri], a new file the user picked with
 * Android's file picker, so no storage permission is needed. Returns the note to show.
 */
suspend fun exportSaved(context: Context, uri: Uri, items: List<SavedMon>): String = withContext(Dispatchers.IO) {
    runCatching {
        // "wt": some file apps don't truncate on "w", so an export over an older, longer file kept
        // the old file's tail after the new JSON, and no longer imported (1.28).
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(encodeSavedFile(items, System.currentTimeMillis()).toByteArray()) }
    }.fold({ "Exported ${items.size} Pokémon." }, { "Couldn't write the file." })
}

/** Settings › Saved list › Import (1.24): adds the Pokémon in the file at [uri]. Returns the note to show. */
suspend fun importSaved(context: Context, uri: Uri, store: CollectionStore): String = withContext(Dispatchers.IO) {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { s ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 shl 10)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= MAX_SAVED_FILE)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull() ?: return@withContext "Couldn't read that file."
    val items = decodeSavedFile(text) ?: return@withContext "That isn't an IV Lens saved list."
    importNote(items.size, store.merge(items))
}

fun importNote(total: Int, r: SavedMerge): String {
    if (total == 0) return "That file has no saved Pokémon."
    val same = total - r.added - r.updated
    return listOfNotNull(
        "Added ${r.added} Pokémon.".takeIf { r.added > 0 },
        "Exact IVs filled in for ${r.updated}.".takeIf { r.updated > 0 },
        (if (r.added + r.updated == 0) "Nothing new: you had them all." else "$same already saved.").takeIf { same > 0 },
    ).joinToString(" ")
}
