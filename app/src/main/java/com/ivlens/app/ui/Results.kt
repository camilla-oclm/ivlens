package com.ivlens.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivlens.app.core.Analysis
import com.ivlens.app.core.CalcResult
import com.ivlens.app.core.Cost
import com.ivlens.app.core.FormInfo
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.LeagueInfo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.Verdict
import java.text.NumberFormat
import java.util.Locale

private val fmt: NumberFormat = NumberFormat.getIntegerInstance()
fun Int.grouped(): String = fmt.format(this)

fun costText(c: Cost): String = buildList {
    add("${c.stardust.grouped()} dust")
    if (c.candy > 0) add("${c.candy.grouped()} candy")
    if (c.candyXl > 0) add("${c.candyXl.grouped()} XL")
}.joinToString(" · ")

/** IV cell plus the combo / level line. */
@Composable
fun IvRow(result: CalcResult, selected: IvCombo?, appraised: Boolean = false, inGame: Boolean = false) {
    val shown = selected ?: result.exact
    val pct = shown?.percent ?: result.ivMax
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        val fill = ivFill(pct)
        CompositionLocalProvider(LocalInk provides inkOn(fill)) {
            Column(Modifier.width(104.dp).fillMaxHeight().background(fill).padding(horizontal = 10.dp, vertical = 7.dp)) {
                BlockLabel("IV")
                Text(if (shown != null) "${shown.percent}%" else result.ivRangeText(), style = blockText(if (shown != null) 24.sp else 19.sp, 900), maxLines = 1)
            }
        }
        VLine()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                if (shown != null) "${shown.ivText} · Lv ${Levels.label(shown.level)}"
                else "${result.combos.size} possible · ${result.levelRangeText()}",
                style = blockText(15.sp, 700),
            )
            Text(
                when {
                    selected != null && appraised -> "Exact, from the Appraise screen"
                    selected != null && result.combos.size > 1 -> "Picked from ${result.combos.size} possibilities"
                    inGame && shown == null -> "Appraise it in the game for the exact IVs"
                    result.usedArc -> "Level read from the arc"
                    result.input.dust == null -> "Add the stardust cost to narrow it down"
                    else -> "Attack / Defense / HP"
                },
                style = blockText(12.sp, 500, Block.Muted),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComboChips(result: CalcResult, selected: IvCombo?, onSelect: (IvCombo?) -> Unit, initiallyShown: Int = 6) {
    if (result.combos.size <= 1) return
    var expanded by remember(result) { mutableStateOf(false) }
    val list = if (expanded) result.combos else result.combos.take(initiallyShown)
    FlowRow(
        Modifier.fillMaxWidth().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (c in list) {
            BlockToggle("${c.ivText} · ${c.percent}% · Lv ${Levels.label(c.level)}", c == selected, { onSelect(if (c == selected) null else c) })
        }
        if (!expanded && result.combos.size > initiallyShown) {
            BlockToggle("+${result.combos.size - initiallyShown} more", false, { expanded = true })
        }
    }
}

@Composable
private fun LeagueCell(info: LeagueInfo, modifier: Modifier) {
    val out = info.tooHigh || info.ineligible || info.tooSmall
    val good = !out && (info.best?.rank ?: 9999) <= 100
    val fill = if (good) Block.Blue else Block.Paper
    val ink = if (good) Color.White else Block.Ink
    Column(modifier.fillMaxHeight().background(fill).padding(horizontal = 9.dp, vertical = 7.dp)) {
        Text(info.league.label.substringBefore(' ').uppercase(), style = blockText(11.sp, 700, ink))
        Text(
            info.rankText(),
            style = blockText(if (out) 14.sp else 17.sp, 900, if (out) Block.Muted else ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val b = info.best
        if (info.tooSmall && !info.tooHigh && !info.ineligible) {
            Text(info.detailText(), style = blockText(11.sp, 600, Block.Muted), maxLines = 2)
        } else if (b != null && !out) {
            val detail = if (info.single) String.format(Locale.US, "%.1f%% · Lv %s", b.percentOfBest, Levels.label(b.level))
            else String.format(Locale.US, "best %.1f%%", b.percentOfBest)
            Text(detail, style = blockText(11.sp, 600, ink), maxLines = 2)
        }
    }
}

@Composable
private fun FormRanks(f: FormInfo) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 7.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text((if (f.depth > 0) "→ " else "") + f.species.name.uppercase(), style = blockText(13.sp, 900), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (f.depth > 0) "${f.cpText()} after evolving" else f.cpText(), style = blockText(12.sp, 600, Block.Muted))
    }
    HLine()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        f.leagues.forEachIndexed { i, lg ->
            if (i > 0) VLine()
            LeagueCell(lg, Modifier.weight(1f))
        }
    }
}

@Composable
fun PvpSection(analysis: Analysis) {
    analysis.forms.forEachIndexed { i, f ->
        if (i > 0) HLine()
        FormRanks(f)
    }
}

@Composable
fun PowerUpSection(analysis: Analysis) {
    if (analysis.powerUps.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BlockLabel("Power up" + (analysis.fromLevel?.let { " from Lv ${Levels.label(it)}" } ?: ""))
        for (p in analysis.powerUps) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((if (p.label.startsWith("Level")) "To level ${Levels.label(p.level)}" else "${p.label} · Lv ${Levels.label(p.level)}").uppercase(), style = blockText(13.sp, 700))
                    Text(costText(p.cost), style = blockText(12.sp, 500, Block.Muted))
                }
                Text(p.cpText(), style = blockText(15.sp, 900))
            }
        }
    }
}

/**
 * Everything about the result, as stacked sections divided by black rules. The caller draws
 * the outer box (the floating card, or a panel in the app).
 */
@Composable
fun ResultDetails(
    result: CalcResult,
    analysis: Analysis?,
    selected: IvCombo?,
    onSelect: (IvCombo?) -> Unit,
    compact: Boolean = false,
    verdict: Verdict? = null,
    moveNames: List<String> = emptyList(),
    appraised: Boolean = false,
) {
    if (result.combos.isEmpty()) {
        Box(Modifier.fillMaxWidth().background(Block.Red).padding(12.dp)) {
            Text(
                "No IVs fit these numbers. Check CP, HP and the stardust cost, and whether it's Shadow, Purified or Lucky.",
                style = blockText(13.sp, 700, Block.OnColor),
            )
        }
        return
    }
    if (verdict != null) {
        VerdictBanner(verdict, moveNames, inGame = compact)
        HLine()
    }
    IvRow(result, selected, appraised, inGame = compact)
    if (result.combos.size > 1 && !appraised) {
        HLine()
        ComboChips(result, selected, onSelect, if (compact) 4 else 8)
    }
    if (analysis != null) {
        HLine()
        PvpSection(analysis)
        if (analysis.powerUps.isNotEmpty()) {
            HLine()
            PowerUpSection(analysis)
        }
    }
}
