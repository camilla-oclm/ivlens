package com.ivlens.app.ui

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivlens.app.core.Analysis
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.CalcResult
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Verdict
import com.ivlens.app.graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class Tab { SCAN, CALC, COLLECTION, RANKS, SETTINGS }

data class CalcUi(
    val query: String = "",
    val speciesId: String? = null,
    val cp: String = "",
    val hp: String = "",
    val dust: String = "",
    val shadow: Boolean = false,
    val purified: Boolean = false,
    val lucky: Boolean = false,
    val levelHint: List<Int>? = null,
    val result: CalcResult? = null,
    val analysis: Analysis? = null,
    val selected: IvCombo? = null,
    val saved: Boolean = false,
    val verdict: Verdict? = null,
    val fast: String? = null,
    val charged: String? = null,
) {
    fun toInput() = CalcInput(
        speciesId = speciesId,
        cp = cp.toIntOrNull(),
        hp = hp.toIntOrNull(),
        dust = dust.replace(",", "").toIntOrNull(),
        shadow = shadow,
        purified = purified,
        lucky = lucky,
        levelHint = levelHint,
        fast = fast,
        charged = listOfNotNull(charged),
    )
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val graph = app.graph
    private val _tab = MutableStateFlow(Tab.SCAN)
    val tab: StateFlow<Tab> = _tab.asStateFlow()
    private val _calc = MutableStateFlow(CalcUi())
    val calc: StateFlow<CalcUi> = _calc.asStateFlow()
    private var job: Job? = null

    init {
        // Settings such as trainer level change the results.
        viewModelScope.launch { graph.settings.state.drop(1).collect { recompute() } }
    }

    fun setTab(t: Tab) { _tab.value = t }

    private fun change(f: (CalcUi) -> CalcUi) {
        _calc.update { f(it).copy(saved = false) }
        recompute()
    }

    fun setQuery(q: String) = change {
        val sp = it.speciesId?.let { id -> graph.gameData.byId[id] }
        it.copy(query = q, speciesId = if (sp != null && sp.name == q) sp.id else null, selected = null, levelHint = null)
    }

    fun pickSpecies(id: String) = change {
        it.copy(speciesId = id, query = graph.gameData.byId[id]?.name ?: it.query, selected = null, fast = null, charged = null)
    }

    fun setFast(id: String?) = change { it.copy(fast = id) }
    fun setCharged(id: String?) = change { it.copy(charged = id) }

    fun setCp(v: String) = change { it.copy(cp = v.filter(Char::isDigit).take(5), selected = null) }
    fun setHp(v: String) = change { it.copy(hp = v.filter(Char::isDigit).take(4), selected = null) }
    fun setDust(v: String) = change { it.copy(dust = v.filter(Char::isDigit).take(6), selected = null) }
    fun toggleShadow() = change { it.copy(shadow = !it.shadow, purified = false, lucky = if (!it.shadow) false else it.lucky) }
    fun togglePurified() = change { it.copy(purified = !it.purified, shadow = false) }
    fun toggleLucky() = change { it.copy(lucky = !it.lucky, shadow = if (!it.lucky) false else it.shadow) }
    fun select(c: IvCombo?) = change { it.copy(selected = c) }
    fun clear() { _calc.value = CalcUi() }

    fun load(input: CalcInput, selected: IvCombo?) {
        val sp = input.speciesId?.let { graph.gameData.byId[it] }
        _calc.value = CalcUi(
            query = sp?.name ?: "",
            speciesId = sp?.id,
            cp = input.cp?.toString() ?: "",
            hp = input.hp?.toString() ?: "",
            dust = input.dust?.toString() ?: "",
            shadow = input.shadow,
            purified = input.purified,
            lucky = input.lucky,
            levelHint = input.levelHint,
            selected = selected,
            fast = input.fast,
            charged = input.charged.firstOrNull(),
        )
        _tab.value = Tab.CALC
        recompute()
    }

    fun save() {
        val s = _calc.value
        val r = s.result ?: return
        val a = s.analysis ?: return
        graph.save(r, s.selected, a)
        _calc.update { it.copy(saved = true) }
    }

    private fun recompute() {
        job?.cancel()
        val snapshot = _calc.value
        job = viewModelScope.launch {
            val next = withContext(Dispatchers.Default) {
                val r = graph.calculate(snapshot.toInput())
                val sel = snapshot.selected?.takeIf { r?.combos?.contains(it) == true }
                val ok = r?.takeIf { it.combos.isNotEmpty() }
                snapshot.copy(result = r, analysis = ok?.let { graph.analyze(it, sel) }, verdict = ok?.let { graph.judge(it, sel) }, selected = sel)
            }
            _calc.update {
                if (it.toInput() == snapshot.toInput() && it.selected == snapshot.selected) {
                    it.copy(result = next.result, analysis = next.analysis, verdict = next.verdict, selected = next.selected)
                } else it
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INPUT = "ivlens.input"
        const val EXTRA_SELECTED = "ivlens.selected"
        /** The card's "Wrong read? Report it" (1.24): what was read, for the bug report form. */
        const val EXTRA_BUG_READ = "ivlens.bugRead"
    }

    private val vm: MainViewModel by viewModels()

    /** Lines read by the card for an open bug report form; null when it's closed. */
    private val bugReport = mutableStateOf<List<String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            IvLensTheme {
                // The status bar icons follow the app's theme, which can differ from the phone's.
                val dark = LocalBlockPalette.current.dark
                LaunchedEffect(dark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                        navigationBarStyle = SystemBarStyle.auto(Color.argb(0xe6, 0xFF, 0xFF, 0xFF), Color.argb(0x80, 0x1b, 0x1b, 0x1b)) { dark },
                    )
                }
                AppRoot(vm)
                bugReport.value?.let { read -> BugReportDialog(read) { bugReport.value = null } }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringArrayListExtra(EXTRA_BUG_READ)?.let {
            bugReport.value = it
            intent.removeExtra(EXTRA_BUG_READ)
            return
        }
        val raw = intent?.getStringExtra(EXTRA_INPUT) ?: return
        val input = runCatching { Json.decodeFromString(CalcInput.serializer(), raw) }.getOrNull() ?: return
        val sel = intent.getStringExtra(EXTRA_SELECTED)?.let { runCatching { Json.decodeFromString(IvCombo.serializer(), it) }.getOrNull() }
        vm.load(input, sel)
        intent.removeExtra(EXTRA_INPUT)
    }
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val tab by vm.tab.collectAsState()
    Scaffold(
        bottomBar = {
            Column {
                HLine()
                NavigationBar(containerColor = Block.Paper, tonalElevation = 0.dp) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Block.OnColor,
                        selectedTextColor = Block.Ink,
                        indicatorColor = Block.Yellow,
                        unselectedIconColor = Block.Muted,
                        unselectedTextColor = Block.Muted,
                    )
                    @Composable
                    fun item(t: Tab, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) = NavigationBarItem(
                        selected = tab == t,
                        onClick = { vm.setTab(t) },
                        icon = { Icon(icon, null) },
                        label = { Text(label.uppercase(), style = blockText(12.sp, 900, if (tab == t) Block.Ink else Block.Muted)) },
                        colors = colors,
                    )
                    item(Tab.SCAN, Icons.Filled.Search, "Scan")
                    item(Tab.CALC, Icons.Filled.Edit, "Calc")
                    item(Tab.COLLECTION, Icons.Filled.Star, "Saved")
                    item(Tab.RANKS, Icons.AutoMirrored.Filled.List, "Ranks")
                    item(Tab.SETTINGS, Icons.Filled.Settings, "Settings")
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.SCAN -> ScanScreen()
                Tab.CALC -> CalcScreen(vm)
                Tab.COLLECTION -> CollectionScreen(vm)
                Tab.RANKS -> RanksScreen(vm)
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
