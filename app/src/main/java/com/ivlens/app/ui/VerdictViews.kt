package com.ivlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivlens.app.core.Verdict
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.core.VerdictTag

/** Left-hand colour block shared by the chip and the card: big word, small line under it. */
@Composable
fun VerdictBlock(word: String, sub: String, kind: VerdictKind?, outline: Boolean, modifier: Modifier = Modifier, big: Boolean = false) {
    val (fill, ink) = if (outline) Block.Paper to Block.OrangeInk else verdictFill(kind)
    Column(
        modifier.background(fill).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        FitText(word, if (big) 22.sp else 19.sp, 900, ink)
        if (sub.isNotEmpty()) FitText(sub, 11.sp, 700, if (outline) Block.Ink else ink)
    }
}

/** What the small auto-scan chip shows. */
data class MiniState(
    val kind: VerdictKind?,
    val word: String,
    val sub: String,
    val line1: String,
    val line2: String? = null,
    /** "Raids after TM" is drawn as an outlined block. */
    val outline: Boolean = false,
    val busy: Boolean = false,
)

const val APPRAISE_HINT = "Appraise for exact IVs"

/**
 * The chip for [v]. With [appraise] (several IVs still fit), its first small line says the game's
 * Appraise screen gives the exact ones (1.22). The moves come first: a chip asking for them doesn't.
 */
fun miniFromVerdict(v: Verdict, line1: String, appraise: Boolean = false): MiniState {
    val p = v.primary
    if (v.pendingMoves) {
        return MiniState(null, "MOVES?", "SCROLL DOWN", line1, "Raids if ${p.detail.removePrefix("with ")}")
    }
    val also = v.also.filter { it.kind != VerdictKind.KEEP || p.kind == VerdictKind.HUNDO }.map { it.alsoText() }
    val line2 = if (also.isNotEmpty()) "Also " + also.joinToString(" · ") else p.detail.takeIf { it.isNotEmpty() && p.kind != VerdictKind.KEEP }
    return MiniState(
        kind = p.kind,
        word = p.blockWord,
        sub = p.blockSub,
        line1 = line1,
        line2 = if (appraise) listOfNotNull(APPRAISE_HINT, line2).joinToString("\n") else line2,
        outline = p.title.startsWith("Raids after TM"),
    )
}

fun VerdictTag.alsoText(): String = if (needsMoves) "Raids? (${title.substringAfter(" · ")})" else title

val CHIP_MAX_WIDTH = 244.dp

/** The small chip that appears by itself when auto-scan reads a Pokémon. */
@Composable
fun VerdictChip(
    state: MiniState,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    /** Fixed so the service knows exactly where the chip sits. */
    width: Dp = CHIP_MAX_WIDTH,
) {
    Box(Modifier.padding(end = 4.dp, bottom = 4.dp).alpha(if (state.busy) 0.6f else 1f)) {
        Row(
            Modifier
                .width(width)
                .height(IntrinsicSize.Min)
                .block(radius = 10.dp, shadow = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = onDragEnd) { change, amount ->
                        change.consume()
                        onDrag(amount.x, amount.y)
                    }
                }
                .clickable(role = Role.Button, onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerdictBlock(state.word, state.sub, state.kind, state.outline, Modifier.width(86.dp).fillMaxHeight())
            Box(Modifier.width(Block.Line).fillMaxHeight().background(Block.Ink))
            Column(Modifier.weight(1f).padding(start = 9.dp, top = 6.dp, bottom = 6.dp)) {
                FitText(state.line1, 13.sp, 700, minSize = 10.sp)
                state.line2?.let { Text(it, style = blockText(11.sp, 500), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            Box(
                Modifier.width(30.dp).height(40.dp).clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Hide", tint = Block.Ink, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Verdict row at the top of the result card and the calculator. */
@Composable
fun VerdictBanner(v: Verdict, moveNames: List<String>, modifier: Modifier = Modifier, inGame: Boolean = true) {
    val p = v.primary
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        if (v.pendingMoves) {
            VerdictBlock("MOVES?", if (inGame) "SCROLL DOWN" else "PICK ABOVE", null, false, Modifier.width(104.dp).fillMaxHeight(), big = true)
        } else {
            VerdictBlock(p.blockWord, p.blockSub, p.kind, p.title.startsWith("Raids after TM"), Modifier.width(104.dp).fillMaxHeight(), big = true)
        }
        Box(Modifier.width(Block.Line).fillMaxHeight().background(Block.Ink))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp)) {
            when {
                v.pendingMoves -> {
                    Text(if (inGame) "Scroll down in the game so its moves can be read." else "Pick its moves above to settle this.", style = blockText(13.sp, 700))
                    Text("With ${p.detail.removePrefix("with ")} it's a top raider (${p.title.substringAfter(" · ")}).", style = blockText(12.sp, 500))
                }
                p.steps.isNotEmpty() -> p.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", style = blockText(14.sp, 700), modifier = Modifier.padding(bottom = 2.dp))
                }
                p.detail.isNotEmpty() -> Text(p.detail, style = blockText(14.sp, 700))
            }
            if (!v.pendingMoves) p.note?.let { Text(it, style = blockText(12.sp, 500)) }
            val also = v.also.map { it.alsoText() }
            if (also.isNotEmpty()) Text("Also " + also.joinToString(" · "), style = blockText(12.sp, 500))
            if (moveNames.isNotEmpty() && !p.detail.contains(moveNames.first())) {
                Text("Now: " + moveNames.joinToString(" + "), style = blockText(12.sp, 500, Block.Muted))
            }
            if (!v.confident) {
                val how = if (inGame) "Appraise it in the game to be sure." else "Tap an IV combo to be sure."
                Text("Likely. $how", style = blockText(12.sp, 500, Block.Muted))
            }
        }
    }
}

@Composable
fun HLine() = Box(Modifier.fillMaxWidth().height(Block.Line).background(Block.Ink))

@Composable
fun VLine() = Box(Modifier.width(Block.Line).fillMaxHeight().background(Block.Ink))

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

val InkWhite = Color.White
