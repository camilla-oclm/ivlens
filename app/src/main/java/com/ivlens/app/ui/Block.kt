package com.ivlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivlens.app.R
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictTag

/** The neutral colours of one theme (1.22). The colour blocks (yellow, blue...) are the same in both. */
@Immutable
data class BlockPalette(
    /** Text, outlines and hard shadows. */
    val ink: Color,
    /** Text on an ink-filled cell (a picked toggle, the active field). */
    val onInk: Color,
    /** Panels, cards and the chip. */
    val paper: Color,
    /** The screen behind them. */
    val page: Color,
    val muted: Color,
    /** Disabled and neutral fills. */
    val grey: Color,
    /** Orange text on paper ("Raids after TM"). */
    val orangeInk: Color,
    val dark: Boolean,
)

val LightBlock = BlockPalette(
    ink = Color(0xFF111111), onInk = Color.White, paper = Color.White, page = Color(0xFFF3F2EC),
    muted = Color(0xFF4A4A4A), grey = Color(0xFFD9D9D9), orangeInk = Color(0xFFB35300), dark = false,
)

/** Bold Block turned around: light outlines and shadows on near-black. */
val DarkBlock = BlockPalette(
    ink = Color(0xFFF1F0EA), onInk = Color(0xFF141317), paper = Color(0xFF232228), page = Color(0xFF141317),
    muted = Color(0xFFADAAB4), grey = Color(0xFF3B3A42), orangeInk = Color(0xFFFF9A3D), dark = true,
)

val LocalBlockPalette = staticCompositionLocalOf { LightBlock }

/** Ink for text on the fill it sits on; [BlockPanel] sets it for its fill. */
val LocalInk = compositionLocalOf { LightBlock.ink }

/** The "Bold Block" look: black outlines, hard shadows, flat colour blocks. */
object Block {
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.ink
    val OnInk: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.onInk
    val Paper: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.paper
    val Page: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.page
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.muted
    val Grey: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.grey
    val OrangeInk: Color @Composable @ReadOnlyComposable get() = LocalBlockPalette.current.orangeInk
    val Yellow = Color(0xFFFFD400)
    val Blue = Color(0xFF3D5AFE)
    val Orange = Color(0xFFFF7A00)
    val Teal = Color(0xFF00C2A8)
    val Red = Color(0xFFFF4D4D)
    /** Text on a colour block (yellow, orange, teal, red): dark in both themes. */
    val OnColor = Color(0xFF111111)
    val Line = 2.dp
}

/** Ink for text on [fill]: dark on the colour blocks, white on blue, the theme's own on neutral fills. */
@Composable
@ReadOnlyComposable
fun inkOn(fill: Color): Color = when (fill) {
    Block.Yellow, Block.Orange, Block.Teal, Block.Red -> Block.OnColor
    Block.Blue -> Color.White
    Block.Ink -> Block.OnInk
    else -> Block.Ink
}

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    R.font.archivo,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Archivo = FontFamily(archivo(500), archivo(600), archivo(700), archivo(900))

@Composable
@ReadOnlyComposable
fun blockText(size: TextUnit, weight: Int = 700, color: Color = LocalInk.current) =
    TextStyle(fontFamily = Archivo, fontWeight = FontWeight(weight), fontSize = size, color = color, fontFeatureSettings = "tnum")

/** Outline plus a hard offset shadow. Leave room right and below for the shadow. */
@Composable
fun Modifier.block(radius: Dp = 10.dp, shadow: Dp = 4.dp, fill: Color = Block.Paper): Modifier {
    val shape = RoundedCornerShape(radius)
    val ink = Block.Ink
    return this
        .drawBehind {
            if (shadow > 0.dp) {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(shadow.toPx(), shadow.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(radius.toPx()),
                )
            }
        }
        .clip(shape)
        .background(fill)
        .border(Block.Line, ink, shape)
}

/** Colour block and text colour for a verdict. */
@Composable
@ReadOnlyComposable
fun verdictFill(kind: VerdictKind?): Pair<Color, Color> {
    val fill = when (kind) {
        VerdictKind.HUNDO -> Block.Yellow
        VerdictKind.PVP -> Block.Blue
        VerdictKind.RAIDS -> Block.Orange
        VerdictKind.KEEP -> Block.Teal
        VerdictKind.TRANSFER -> Block.Red
        null -> Block.Grey
    }
    return fill to inkOn(fill)
}

/** Fill for an IV percentage cell. */
@Composable
@ReadOnlyComposable
fun ivFill(percent: Int?): Color = when {
    percent == null -> Block.Paper
    percent >= 96 -> Block.Yellow
    percent >= 80 -> Block.Teal
    else -> Block.Paper
}

/** Big word in the colour block: HUNDO, PVP, RAIDS, KEEP, TRANSFER. */
val VerdictTag.blockWord: String get() = kind.name

/** Small line under it: GREAT #1, DRAGON #21, AFTER TM, 84% IV... */
val VerdictTag.blockSub: String
    get() = when {
        kind == VerdictKind.HUNDO -> "15/15/15"
        title.startsWith("Raids after TM") -> "AFTER TM"
        title.contains(" · ") -> title.substringAfter(" · ").uppercase()
        kind == VerdictKind.TRANSFER -> detail.substringBefore(" · ").uppercase()
        else -> detail.uppercase()
    }

@Composable
fun BlockButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Block.Yellow,
    textColor: Color = inkOn(fill),
    enabled: Boolean = true,
    shadow: Dp = 3.dp,
) {
    Box(
        modifier
            .padding(end = shadow, bottom = shadow)
            .block(radius = 10.dp, shadow = if (enabled) shadow else 0.dp, fill = if (enabled) fill else Block.Grey)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 46.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = blockText(14.sp, 900, if (enabled) textColor else Block.Muted))
    }
}

/** On/off pill: inked when on. */
@Composable
fun BlockToggle(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Block.Ink else Block.Paper)
            .border(Block.Line, Block.Ink, RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .height(36.dp)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = blockText(12.sp, 700, if (selected) Block.OnInk else Block.Ink))
    }
}

@Composable
fun BlockPanel(modifier: Modifier = Modifier, fill: Color = Block.Paper, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalInk provides inkOn(fill)) {
        Column(
            modifier
                .fillMaxWidth()
                .padding(end = 4.dp, bottom = 4.dp)
                .block(radius = 14.dp, shadow = 4.dp, fill = fill)
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun BlockLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = blockText(11.sp, 700), modifier = modifier)
}

/** One line that shrinks to fit its width instead of clipping (verdict words, big numbers). */
@Composable
fun FitText(text: String, maxSize: TextUnit, weight: Int, color: Color = LocalInk.current, modifier: Modifier = Modifier, minSize: TextUnit = 8.sp) {
    BasicText(
        text,
        modifier,
        style = blockText(maxSize, weight, color),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}
