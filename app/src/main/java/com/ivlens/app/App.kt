package com.ivlens.app

import android.app.Application
import android.content.Context
import com.ivlens.app.core.Analysis
import com.ivlens.app.core.Analyzer
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.CalcResult
import com.ivlens.app.core.GameData
import com.ivlens.app.core.IvCalculator
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.League
import com.ivlens.app.core.PvpRanker
import com.ivlens.app.core.Verdict
import com.ivlens.app.core.VerdictEngine
import com.ivlens.app.scan.ScanResolver
import com.ivlens.app.scan.SpeciesMatcher
import com.ivlens.app.store.AppSettings
import com.ivlens.app.store.CollectionStore
import com.ivlens.app.store.SavedMon
import com.ivlens.app.store.SettingsStore

class IvLensApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

val Context.graph: AppGraph get() = (applicationContext as IvLensApp).graph

/** Shared singletons: game data, calculators and stores. */
class AppGraph(context: Context) {
    val gameData: GameData = GameData.parse(context.assets.open("pokedata.json").bufferedReader().use { it.readText() })
    val calc = IvCalculator(gameData)
    val ranker = PvpRanker(gameData)
    val analyzer = Analyzer(gameData, ranker)
    val verdicts = VerdictEngine(gameData, ranker)
    val matcher = SpeciesMatcher(gameData)
    val moveMatcher = com.ivlens.app.scan.MoveMatcher(gameData)
    val resolver = ScanResolver(gameData, calc, matcher)
    val settings = SettingsStore(context)
    val collection = CollectionStore(context)

    fun calculate(input: CalcInput, s: AppSettings = settings.state.value): CalcResult? =
        calc.calculate(input, s.powerMaxIndex)

    fun analyze(result: CalcResult, selected: IvCombo?, s: AppSettings = settings.state.value): Analysis =
        analyzer.analyze(result, selected, s.pvpMaxIndex, s.powerMaxIndex)

    fun judge(result: CalcResult, selected: IvCombo?, s: AppSettings = settings.state.value): Verdict? =
        verdicts.judge(result, selected, s.rules, s.pvpMaxIndex)

    /**
     * Saves the Pokémon, replacing its earlier entry. An [auto] save (the "Auto save" setting)
     * never swaps exact IVs for a range. True if the saved list changed.
     */
    fun save(result: CalcResult, selected: IvCombo?, analysis: Analysis, auto: Boolean = false): Boolean {
        val verdict = judge(result, selected)
        val own = analysis.forms.firstOrNull()
        fun rank(lg: League) = own?.leagues?.firstOrNull { it.league == lg }?.takeIf { !it.tooHigh }?.best?.rank
        val combos = selected?.let { listOf(it) } ?: result.combos
        val now = System.currentTimeMillis()
        return collection.put(
            SavedMon(
                id = now,
                savedAt = now,
                name = result.species.name,
                input = result.input,
                selected = selected,
                ivMin = combos.minOfOrNull { it.percent },
                ivMax = combos.maxOfOrNull { it.percent },
                levelText = if (selected != null) "Lv ${com.ivlens.app.core.Levels.label(selected.level)}" else result.levelRangeText(),
                greatRank = rank(League.GREAT),
                ultraRank = rank(League.ULTRA),
                verdict = verdict?.primary?.title,
                verdictKind = verdict?.primary?.kind?.name,
            ),
            keepExact = auto,
        )
    }
}
