package com.ivlens.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

/**
 * Copies [name] (from [com.ivlens.app.core.Nickname]) to paste when renaming the Pokémon in the
 * game (1.23). Android 13 and later show their own "copied" note, so the toast is only for older ones.
 */
fun copyNickname(context: Context, name: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("IV Lens name", name))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied $name. Paste it when you rename it.", Toast.LENGTH_SHORT).show()
}
