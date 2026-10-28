package com.ivlens.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.ivlens.app.graph
import com.ivlens.app.store.ThemeMode

// Material's colours, taken from the Bold Block palette so its own parts (fields, switches,
// dialogs, the tab bar) match. The app and the overlay over the game follow the Theme setting.
private fun schemeFor(p: BlockPalette): ColorScheme {
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.ink,
        onPrimary = p.onInk,
        primaryContainer = Block.Yellow,
        onPrimaryContainer = Block.OnColor,
        secondary = Block.Blue,
        onSecondary = Color.White,
        secondaryContainer = Block.Yellow,
        onSecondaryContainer = Block.OnColor,
        tertiary = Block.Blue,
        tertiaryContainer = if (p.dark) Color(0xFF2B3160) else Color(0xFFDDE2FF),
        background = p.page,
        onBackground = p.ink,
        surface = p.page,
        onSurface = p.ink,
        surfaceVariant = p.paper,
        onSurfaceVariant = p.muted,
        surfaceContainerLowest = p.paper,
        surfaceContainerLow = p.paper,
        surfaceContainer = p.paper,
        surfaceContainerHigh = p.paper,
        surfaceContainerHighest = p.paper,
        outline = p.ink,
        outlineVariant = p.ink,
        error = if (p.dark) Color(0xFFFF8A80) else Color(0xFFB3261E),
        errorContainer = Block.Red,
        onErrorContainer = Block.OnColor,
    )
}

private val AppTypography = Typography().let { t ->
    fun TextStyle.a(w: Int? = null) = copy(fontFamily = Archivo, fontWeight = w?.let { FontWeight(it) } ?: fontWeight)
    t.copy(
        displayLarge = t.displayLarge.a(900), displayMedium = t.displayMedium.a(900), displaySmall = t.displaySmall.a(900),
        headlineLarge = t.headlineLarge.a(900), headlineMedium = t.headlineMedium.a(900), headlineSmall = t.headlineSmall.a(900),
        titleLarge = t.titleLarge.a(900), titleMedium = t.titleMedium.a(700), titleSmall = t.titleSmall.a(700),
        bodyLarge = t.bodyLarge.a(500), bodyMedium = t.bodyMedium.a(500), bodySmall = t.bodySmall.a(500),
        labelLarge = t.labelLarge.a(700), labelMedium = t.labelMedium.a(700), labelSmall = t.labelSmall.a(700),
    )
}

@Composable
fun IvLensTheme(content: @Composable () -> Unit) {
    val settings by LocalContext.current.graph.settings.state.collectAsState()
    val dark = when (settings.theme) {
        ThemeMode.PHONE -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkBlock else LightBlock
    CompositionLocalProvider(LocalBlockPalette provides palette, LocalInk provides palette.ink) {
        MaterialTheme(colorScheme = schemeFor(palette), typography = AppTypography, content = content)
    }
}

val Numeric = TextStyle(fontFeatureSettings = "tnum")
