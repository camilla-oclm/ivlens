package com.ivlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivlens.app.core.Analysis
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.CalcResult
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Species
import com.ivlens.app.core.Verdict
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.ScanOutcome

enum class EditField(val label: String) { CP("CP"), HP("Max HP"), DUST("Stardust to power up") }

data class OverlayState(
    val candidates: List<Species>,
    val input: CalcInput,
    val result: CalcResult?,
    val analysis: Analysis?,
    val selected: IvCombo?,
    val problem: ScanOutcome.Problem?,
    val verdict: Verdict? = null,
    /** Names of the moves in [input], for display. */
    val moveNames: List<String> = emptyList(),
    /** [selected] came from the game's Appraise screen. */
    val appraised: Boolean = false,
    /** Bars from the Appraise screen (or set on the card) that don't fit CP and HP. */
    val bars: Appraisal? = null,
    /** The card's bar editor: one value per stat, null where not set yet. Null when it's closed. */
    val barInput: List<Int?>? = null,
    val editing: EditField? = null,
    val buffer: String = "",
    /** Where the number pad's digits go in [buffer]. */
    val cursor: Int = 0,
    val showSpecies: Boolean = false,
    val saved: Boolean = false,
    val busy: Boolean = false,
    /** Weight read on its page; tells apart two of a kind with the same CP and HP. */
    val weightKg: Double? = null,
)

interface OverlayActions {
    fun close()
    fun rescan()
    fun toggleSpeciesList()
    fun pickSpecies(id: String)
    fun toggleShadow()
    fun togglePurified()
    fun toggleLucky()
    fun edit(field: EditField)
    fun key(c: Char)
    /** Puts the number pad's cursor before digit [at]. */
    fun moveCursor(at: Int)
    fun select(combo: IvCombo?)
    fun toggleBars()
    /** [stat]: 0 Attack, 1 Defense, 2 HP. */
    fun setBar(stat: Int, value: Int)
    fun save()
    /** Puts a short name with the exact IVs on the clipboard, to paste when renaming it ([com.ivlens.app.core.Nickname]). */
    fun copyName()
    /** Opens the bug report form in the app, with what was read ([BugReportDialog]). */
    fun report()
    fun openInApp()
}

@Composable
private fun ValueCell(label: String, value: String, active: Boolean, warn: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val fill = when {
        active -> Block.Ink
        warn -> Block.Red
        else -> Block.Paper
    }
    val ink = inkOn(fill)
    Column(
        modifier.fillMaxHeight().background(fill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(label.uppercase(), style = blockText(11.sp, 700, ink))
        Text(value, style = blockText(if (value.length > 6) 14.sp else 19.sp, 900, ink), maxLines = 1)
    }
}

@Composable
private fun Keypad(field: EditField, buffer: String, cursor: Int, onKey: (Char) -> Unit, onCursor: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${field.label.uppercase()}: ", style = blockText(13.sp, 700))
            // The cursor is a bar drawn between the digits, not a character, so the digits stay put
            // when it moves (1.22). Before, a "▏" the font doesn't have pushed them aside, and a
            // second tap landed off. A tap puts it in the nearest gap; ← → move it one digit.
            val at = cursor.coerceIn(0, buffer.length)
            val ink = LocalInk.current
            var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                buffer.ifEmpty { "—" },
                style = blockText(30.sp, 900, if (buffer.isEmpty()) Block.Muted else ink).copy(letterSpacing = 6.sp),
                onTextLayout = { layout = it },
                modifier = Modifier.weight(1f)
                    .pointerInput(buffer) {
                        detectTapGestures { p -> layout?.let { onCursor(it.getOffsetForPosition(p)) } }
                    }
                    .padding(vertical = 4.dp)
                    .drawWithContent {
                        drawContent()
                        val r = layout?.getCursorRect(at) ?: return@drawWithContent
                        val w = 3.dp.toPx()
                        drawRect(ink, topLeft = Offset((r.left + r.right) / 2 - w / 2, r.top), size = Size(w, r.height))
                    },
            )
        }
        for (r in listOf("123", "456", "789", "<0✓", "←→x")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in r) {
                    val label = when (c) {
                        '<' -> "⌫"
                        '✓' -> "OK"
                        'x' -> "Cancel"
                        else -> c.toString()
                    }
                    BlockButton(label, { onKey(c) }, Modifier.weight(1f), fill = if (c == '✓') Block.Yellow else Block.Paper, shadow = 2.dp)
                }
            }
        }
    }
}

/**
 * The Appraise screen's three bars, to set by hand when they weren't read: 15 cells per stat in
 * groups of five like the game's, filled orange (red at 15). Tap where each bar ends.
 */
@Composable
private fun BarsEditor(values: List<Int?>, onSet: (Int, Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tap where each bar ends on the Appraise screen.", style = blockText(12.sp, 500, Block.Muted))
        listOf("ATK", "DEF", "HP").forEachIndexed { i, label ->
            val v = values.getOrNull(i)
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = blockText(12.sp, 900), modifier = Modifier.width(32.dp))
                Box(
                    Modifier.width(22.dp).fillMaxHeight().background(if (v == 0) Block.Ink else Block.Paper)
                        .clickable(role = Role.Button) { onSet(i, 0) },
                    contentAlignment = Alignment.Center,
                ) { Text("0", style = blockText(11.sp, 900, if (v == 0) Block.OnInk else Block.Muted)) }
                for (g in 0 until 3) {
                    Row(
                        Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp).background(Block.Ink).padding(Block.Line),
                        horizontalArrangement = Arrangement.spacedBy(Block.Line),
                    ) {
                        for (k in 1..5) {
                            val n = g * 5 + k
                            val fill = when {
                                v == null || n > v -> Block.Paper
                                v == 15 -> Block.Red
                                else -> Block.Orange
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().background(fill).clickable(role = Role.Button) { onSet(i, n) })
                        }
                    }
                }
                Text(v?.toString() ?: "–", style = blockText(15.sp, 900), modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
        }
    }
}

/** The result card shown over the game after tapping the bubble or the chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverlayCard(state: OverlayState, actions: OverlayActions) {
    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.46f).dp
    val species = state.result?.species ?: state.candidates.firstOrNull { it.id == state.input.speciesId }
    Box(Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, bottom = 4.dp)) {
        Column(Modifier.fillMaxWidth().block(radius = 14.dp, shadow = 5.dp)) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clickable(enabled = state.candidates.size > 1, role = Role.Button) { actions.toggleSpeciesList() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text((species?.name ?: "Unknown Pokémon").uppercase(), style = blockText(21.sp, 900), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.candidates.size > 1) Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change Pokémon", tint = Block.Ink)
                }
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = actions::rescan), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Scan again", tint = Block.Ink)
                }
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = actions::close), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Block.Ink)
                }
            }
            HLine()
            if (state.showSpecies) {
                FlowRow(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in state.candidates) BlockToggle(c.name, c.id == state.input.speciesId, { actions.pickSpecies(c.id) })
                }
                HLine()
            }

            // Numbers read from the screen; tap one to fix it.
            val noCp = state.problem == ScanOutcome.Problem.NO_CP && state.input.cp == null
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                ValueCell("CP", state.input.cp?.toString() ?: "Tap", state.editing == EditField.CP, noCp, { actions.edit(EditField.CP) }, Modifier.weight(1f))
                VLine()
                ValueCell("HP", state.input.hp?.toString() ?: "—", state.editing == EditField.HP, false, { actions.edit(EditField.HP) }, Modifier.weight(1f))
                VLine()
                ValueCell("Dust", state.input.dust?.grouped() ?: "—", state.editing == EditField.DUST, false, { actions.edit(EditField.DUST) }, Modifier.weight(1.15f))
            }
            HLine()
            state.editing?.let {
                Keypad(it, state.buffer, state.cursor, actions::key, actions::moveCursor)
                HLine()
            }
            if (state.editing == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BlockToggle("SHADOW", state.input.shadow, actions::toggleShadow)
                    BlockToggle("PURIFIED", state.input.purified, actions::togglePurified)
                    BlockToggle("LUCKY", state.input.lucky, actions::toggleLucky)
                    BlockToggle("BARS", state.barInput != null, actions::toggleBars)
                }
                HLine()
                state.barInput?.let {
                    BarsEditor(it, actions::setBar)
                    HLine()
                }
                val message = when {
                    state.busy -> "Reading the screen…"
                    state.bars != null && !state.appraised ->
                        "The Appraise bars read ${state.bars.ivText}, which doesn't fit CP ${state.input.cp ?: "?"} and HP ${state.input.hp ?: "?"}. Check those numbers, or fix the bars."
                    state.problem == ScanOutcome.Problem.NO_SPECIES && species == null -> "Couldn't tell which Pokémon this is. Open it in the app to pick it."
                    noCp -> "The CP is hidden. Tap CP and type it in."
                    else -> null
                }
                if (message != null) {
                    Text(message, style = blockText(13.sp, 700), modifier = Modifier.fillMaxWidth().background(Block.Grey).padding(12.dp))
                    HLine()
                }
                val result = state.result
                if (result != null) {
                    Column(Modifier.fillMaxWidth().heightIn(max = maxBody).verticalScroll(rememberScrollState())) {
                        ResultDetails(result, state.analysis, state.selected, actions::select, compact = true, verdict = state.verdict, moveNames = state.moveNames, appraised = state.appraised)
                    }
                    HLine()
                }
                Text(
                    "Wrong read? Report it",
                    style = blockText(12.sp, 700, Block.Muted).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = actions::report).padding(horizontal = 14.dp, vertical = 9.dp),
                )
                HLine()
                Row(Modifier.fillMaxWidth().height(50.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Button, onClick = actions::openInApp),
                        contentAlignment = Alignment.Center,
                    ) { Text("OPEN IN APP", style = blockText(14.sp, 900)) }
                    VLine()
                    // Only with exact IVs: the name is for renaming it in the game (1.23).
                    val canCopy = (state.selected ?: state.result?.exact) != null
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(if (canCopy) Block.Paper else Block.Grey)
                            .clickable(enabled = canCopy, role = Role.Button, onClick = actions::copyName),
                        contentAlignment = Alignment.Center,
                    ) { Text("COPY NAME", style = blockText(14.sp, 900, if (canCopy) Block.Ink else Block.Muted)) }
                    VLine()
                    val canSave = !state.saved && (state.result?.combos?.isNotEmpty() == true)
                    val saveFill = if (canSave) Block.Yellow else Block.Grey
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(saveFill)
                            .clickable(enabled = canSave, role = Role.Button, onClick = actions::save),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (state.saved) "SAVED" else "SAVE", style = blockText(14.sp, 900, inkOn(saveFill))) }
                }
            }
        }
    }
}
