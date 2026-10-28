package com.ivlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.League
import com.ivlens.app.core.Levels
import com.ivlens.app.core.Pokedex
import com.ivlens.app.core.RankRow
import com.ivlens.app.core.Rankings
import com.ivlens.app.core.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The Ranks tab (1.23): the best raid attackers per type and PvPoke's top per league, from the
 * data the verdicts use. SAVED marks species in the Saved list; a tap opens one in the Calculator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RanksScreen(vm: MainViewModel) {
    val graph = vm.graph
    val saved by graph.collection.items.collectAsState()
    val owned = remember(saved) { saved.mapNotNull { m -> m.input.speciesId?.let { it to m.input.shadow } }.toSet() }
    var pvp by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf("dragon") }
    var league by rememberSaveable { mutableStateOf(League.GREAT) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // The raid boards take a moment the first time, so they're worked out off the main thread.
    val rows by produceState<List<RankRow>?>(null, pvp, type, league) {
        value = null
        value = withContext(Dispatchers.Default) {
            if (pvp) Rankings.pvpRows(graph.gameData, league) else Rankings.raidRows(graph.verdicts.raids, type)
        }
    }
    val date = graph.gameData.dateText
    // Handed to the browser, like the Discord link: IV Lens itself needs no internet for it.
    val uri = LocalUriHandler.current
    val pokedex: (RankRow) -> Unit = { r -> runCatching { uri.openUri(Pokedex.url(r.species)) } }
    LazyColumn(
        Modifier.fillMaxSize().background(Block.Page),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTitle("Ranks")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlockToggle("RAIDS", !pvp, { pvp = false })
                BlockToggle("PVP", pvp, { pvp = true })
            }
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pvp) {
                    for (lg in League.entries) BlockToggle(lg.label.substringBefore(' ').uppercase(), league == lg, { league = lg })
                } else {
                    for (t in Rankings.TYPES) TypeToggle(t, type == t) { type = t }
                }
            }
            Text(
                if (pvp) "PvPoke's top 50 for ${league.label}. A Shadow is ranked on its own."
                else "Best attackers with a ${type.replaceFirstChar { it.uppercase() }} charged move, at Lv 40 with perfect IVs and their best moves. " +
                    "Shadows in, Megas out. % is against #1; * needs an Elite TM.",
                style = blockText(12.sp, 500, Block.Muted),
                modifier = Modifier.padding(top = 10.dp),
            )
            Small("Game data of $date; it updates with the app. SAVED: you have one in Saved. Tap one for more.")
        }
        val shown = rows
        if (shown == null) {
            item { Hint("Ranking…") }
        } else {
            items(shown, key = { "${it.species.id}|${it.shadow}" }) { r ->
                val key = "${r.species.id}|${r.shadow}"
                RankLine(r, (r.species.id to r.shadow) in owned, open == key, { open = if (open == key) null else key }, { pokedex(r) }) {
                    if (pvp) PvpDetails(r, league, graph) else RaidDetails(r, graph.gameData)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockButton(
                            "Check mine", {
                                vm.load(CalcInput(speciesId = r.species.id, shadow = r.shadow, fast = r.fast?.id, charged = listOfNotNull(r.charged?.id)), null)
                            },
                            fill = Block.Paper, shadow = 2.dp,
                        )
                        BlockButton("More info", { pokedex(r) }, fill = Block.Paper, shadow = 2.dp)
                    }
                    Small("Check mine opens it in the Calculator, to compare yours. More info (or its name) opens its Pokémon GO page on pokemon.gameinfo.io in the browser.")
                }
            }
        }
    }
}

/** A Ranks line; a tap opens it in place (1.23) to show [details]. */
@Composable
private fun RankLine(
    r: RankRow, owned: Boolean, expanded: Boolean, onToggle: () -> Unit, onName: () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp).block(radius = 10.dp, shadow = 3.dp)) {
        RankHead(r, owned, onToggle, onName.takeIf { expanded })
        if (expanded) {
            HLine()
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), content = details)
        }
    }
}

@Composable
private fun PvpDetails(r: RankRow, league: League, graph: com.ivlens.app.AppGraph) {
    val maxLevel = graph.settings.state.collectAsState().value.pvpMaxIndex
    val best by produceState<Rankings.BestIvs?>(null, r, league, maxLevel) {
        value = withContext(Dispatchers.Default) { Rankings.bestIvs(graph.ranker, r.species, league, maxLevel) }
    }
    best?.let { b ->
        val xl = if (b.level > Levels.L40) " (needs XL)" else ""
        Detail("Best IVs") { Text(b.ivText, style = blockText(16.sp, 900)) }
        Detail("Reaches") { Text("CP ${"%,d".format(Locale.US, b.cp)} at Lv ${Levels.label(b.level)}$xl", style = blockText(12.sp, 700)) }
    }
}

@Composable
private fun RaidDetails(r: RankRow, gd: com.ivlens.app.core.GameData) {
    val sp = r.species
    if (r.fast != null && r.charged != null) {
        for ((label, m) in listOf("Fast" to r.fast, "Charged" to r.charged)) Detail(label) {
            Text(m.name, style = blockText(12.sp, 700))
            TypePill(m.type)
            if (m.id in sp.el) Text("Elite TM", style = blockText(12.sp, 700, Block.Muted))
        }
    }
    Detail("Lv 40") { Text("CP ${"%,d".format(Locale.US, Stats.cp(sp, gd.cpm[Levels.L40], 15, 15, 15))} with perfect IVs", style = blockText(12.sp, 700)) }
    if (r.shadow) Detail("Shadow") { Text("20% more attack, but it takes 20% more damage", style = blockText(12.sp, 700)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detail(label: String, value: @Composable () -> Unit) {
    FlowRow(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(label, style = blockText(12.sp, 700, Block.Muted), modifier = Modifier.width(64.dp))
        value()
    }
}

/** The usual type colours (1.23): plain colours, no artwork. Dark ones take white text. */
private object TypeColors {
    private val fills = mapOf(
        "normal" to 0xFFA8A77A, "fire" to 0xFFEE8130, "water" to 0xFF6390F0, "grass" to 0xFF7AC74C, "electric" to 0xFFF7D02C,
        "ice" to 0xFF96D9D6, "fighting" to 0xFFC22E28, "poison" to 0xFFA33EA1, "ground" to 0xFFE2BF65, "flying" to 0xFFA98FF3,
        "psychic" to 0xFFF95587, "bug" to 0xFFA6B91A, "rock" to 0xFFB6A136, "ghost" to 0xFF735797, "dragon" to 0xFF6F35FC,
        "dark" to 0xFF705746, "steel" to 0xFFB7B7CE, "fairy" to 0xFFD685AD,
    )
    private val whiteText = setOf("fighting", "poison", "ghost", "dragon", "dark")
    fun of(type: String) = Color(fills[type] ?: 0xFFA8A77A)
    fun inkOn(type: String) = if (type in whiteText) Color.White else Block.OnColor
}

/** Gold, silver and bronze for the top three. */
private val MEDALS = listOf(Color(0xFFFFD400), Color(0xFFCDD1D8), Color(0xFFE0A06A))
private val SHADOW_PURPLE = Color(0xFF4B2C6F)

@Composable
private fun Pill(text: String, fill: Color, ink: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.clip(shape).background(fill).border(1.5.dp, Block.Ink, shape).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(text, style = blockText(9.sp, 900, ink).copy(letterSpacing = 0.4.sp))
    }
}

@Composable
private fun TypePill(type: String) = Pill(type.uppercase(), TypeColors.of(type), TypeColors.inkOn(type))

/** A type in the picker, with its colour dot; inked when picked. */
@Composable
private fun TypeToggle(type: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val ink = if (selected) Block.OnInk else Block.Ink
    Row(
        Modifier.clip(shape).background(if (selected) Block.Ink else Block.Paper).border(Block.Line, Block.Ink, shape)
            .clickable(role = Role.RadioButton, onClick = onClick).height(36.dp).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(TypeColors.of(type)).border(1.5.dp, ink, CircleShape))
        Text(type.uppercase(), style = blockText(12.sp, 700, ink))
    }
}

/** A Ranks line (the owner picked the medals-and-pills look): rank square, name and pills, types, raid moves and % bar. */
@Composable
private fun RankHead(r: RankRow, owned: Boolean, onToggle: () -> Unit, onName: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val medal = MEDALS.getOrNull(r.rank - 1)
        val square = RoundedCornerShape(8.dp)
        Box(
            Modifier.size(38.dp).clip(square).background(medal ?: Block.Paper).border(Block.Line, Block.Ink, square),
            contentAlignment = Alignment.Center,
        ) {
            Text("#${r.rank}", style = blockText(if (r.rank >= 10) 13.sp else 15.sp, 900, if (medal != null) Block.OnColor else Block.Ink))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Open lines link the name to its Pokédex page; a closed line opens with any tap.
                Text(
                    r.species.name.uppercase(),
                    style = blockText(14.sp, 900).let { if (onName != null) it.copy(textDecoration = TextDecoration.Underline) else it },
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).let { m -> onName?.let { m.clickable(role = Role.Button, onClick = it) } ?: m },
                )
                if (r.shadow) Pill("SHADOW", SHADOW_PURPLE, Color.White)
                if (owned) Pill("SAVED", Block.Yellow, Block.OnColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { r.species.types.forEach { TypePill(it) } }
            if (r.fast != null && r.charged != null) {
                val sp = r.species
                fun name(id: String, n: String) = if (id in sp.el) "$n*" else n
                Text(
                    name(r.fast.id, r.fast.name) + " + " + name(r.charged.id, r.charged.name),
                    style = blockText(12.sp, 500, Block.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (r.percent != null && r.charged != null) {
                val bar = RoundedCornerShape(4.dp)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(bar).background(Block.Grey).border(1.5.dp, Block.Ink, bar)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(r.percent / 100f).background(TypeColors.of(r.charged.type)))
                }
            }
        }
        r.percent?.let { Text("$it%", style = blockText(14.sp, 900), modifier = Modifier.padding(top = 2.dp)) }
    }
}
