package com.ivlens.app.core

/** What to do with a Pokémon, most important first. */
enum class VerdictKind { HUNDO, PVP, RAIDS, KEEP, TRANSFER }

data class VerdictTag(
    val kind: VerdictKind,
    val title: String,
    val detail: String,
    /** A raid guess made without the Pokémon's moves; settles once they're read. */
    val needsMoves: Boolean = false,
    /** What to do, in order, in plain words: "Evolve to Gardevoir", "Power up to Lv 40 for raids". */
    val steps: List<String> = emptyList(),
    /**
     * Why a KEEP or TRANSFER isn't a PvP pick: its IVs miss in a league where the species is used
     * ("Not for PvP: Ultra #230 as Florges (top 100 needed)"), or the species isn't used there
     * ("Good Ultra League IVs (#85 as Manectric), but Manectric isn't used there").
     */
    val note: String? = null,
)

data class Verdict(
    val primary: VerdictTag,
    val also: List<VerdictTag>,
    /** False when the possible IV combos don't all lead to the same verdict. */
    val confident: Boolean,
) {
    /** The main answer hangs on moves that haven't been read yet. */
    val pendingMoves: Boolean get() = primary.needsMoves
}

data class VerdictRules(
    /** A league rank at or better than this counts as a PvP keeper. */
    val pvpRankCutoff: Int = 100,
    /** ...and only for a species PvPoke ranks this high in that league (1.16). */
    val pvpSpeciesCutoff: Int = 100,
    /** IV % at or above this is worth keeping in general (82% is three stars). */
    val keepIv: Int = 82,
    val raidIv: Int = 80,
    val raidAtkIv: Int = 12,
    /** A raid rank at or better than this, within the moves' type, counts as a raid keeper. */
    val raidRankCutoff: Int = 30,
)

class VerdictEngine(
    private val gd: GameData,
    private val ranker: PvpRanker,
    val raids: RaidRanker = RaidRanker(gd),
) {

    fun judge(result: CalcResult, selected: IvCombo?, rules: VerdictRules, pvpMaxLevel: Int = Levels.L50): Verdict? {
        val combos = selected?.let { listOf(it) } ?: result.combos
        if (combos.isEmpty()) return null
        val sp = result.species
        val chain = listOf(sp) + gd.evolutionChain(sp, result.input.gender).map { it.first }
        val perCombo = combos.map { tagsFor(it, sp, chain, result.input, rules, pvpMaxLevel) }
        val kinds = perCombo.map { it.first().kind }
        val counts = kinds.groupingBy { it }.eachCount()
        val majority = counts.entries.maxWith(compareBy<Map.Entry<VerdictKind, Int>> { it.value }.thenByDescending { it.key.ordinal }).key
        val rep = perCombo.first { it.first().kind == majority }
        // The note gives one combo's rank, so it's only shown once the IVs are exact.
        val primary = if (combos.size == 1) rep.first() else rep.first().copy(note = null)
        return Verdict(primary, rep.drop(1).distinctBy { it.kind }, confident = counts.size == 1)
    }

    /**
     * Raids: ranked against every attacker of the same type. With its own moves when they
     * were read; otherwise (or if they're poor) with the best regular-TM moves, saying so.
     */
    private fun raidTag(c: IvCombo, sp: Species, chain: List<Species>, input: CalcInput, rules: VerdictRules): VerdictTag? {
        val shadow = input.variant.shadow
        // Legendaries and Shadows are rare enough to use with modest IVs; common ones need good IVs.
        val ivOk = if (shadow || chain.any { it.rare }) c.atk >= 10 else c.atk >= rules.raidAtkIv && c.percent >= rules.raidIv
        if (!ivOk) return null
        val cut = rules.raidRankCutoff
        val power = powerUp(c, Levels.L40, "for raids")
        val own = raids.rankOf(sp, shadow, c, input.fast, input.charged)
        if (own != null && own.rank <= cut) {
            return VerdictTag(
                VerdictKind.RAIDS, "Raids · ${own.typeLabel} #${own.rank}", "${own.fast.name} + ${own.charged.name}",
                steps = listOfNotNull("Keep its moves: ${own.fast.name} + ${own.charged.name}", power),
            )
        }
        val tm = chain.mapNotNull { raids.bestWithTm(it, shadow, c) }.filter { it.rank <= cut }.minByOrNull { it.rank } ?: return null
        val moves = "${tm.fast.name} + ${tm.charged.name}"
        return when {
            // Evolving re-rolls the moves, so the current ones don't matter.
            tm.form.id != sp.id -> VerdictTag(
                VerdictKind.RAIDS, "Raids · ${tm.typeLabel} #${tm.rank}", "as ${tm.form.name} with $moves",
                steps = listOfNotNull("Evolve to ${tm.form.name}", "It needs $moves: use TMs if evolving doesn't give them", power),
            )
            own != null -> VerdictTag(
                VerdictKind.RAIDS, "Raids after TM", "TM to $moves · ${tm.typeLabel} #${tm.rank}",
                steps = listOfNotNull(
                    "Fast TM to ${tm.fast.name}".takeIf { tm.fast.id != input.fast },
                    "Charged TM to ${tm.charged.name}".takeIf { tm.charged.id !in input.charged },
                    power,
                ),
            )
            else -> VerdictTag(VerdictKind.RAIDS, "Raids · ${tm.typeLabel} #${tm.rank}", "with $moves", needsMoves = true)
        }
    }

    /** "Power up to Lv 40 for raids"; null when it's already that high. */
    private fun powerUp(c: IvCombo, to: Int, what: String): String? =
        if (c.level >= to) null else "Power up to Lv ${Levels.label(to)} $what" + (if (to > Levels.L40) " (needs XL Candy)" else "")

    private fun tagsFor(
        c: IvCombo,
        sp: Species,
        chain: List<Species>,
        input: CalcInput,
        rules: VerdictRules,
        pvpMaxLevel: Int,
    ): List<VerdictTag> {
        val variant = input.variant
        val tags = mutableListOf<VerdictTag>()
        val pct = c.percent
        fun asForm(f: Species) = if (f.id != sp.id) "as ${f.name}" else null
        fun evolve(f: Species) = if (f.id != sp.id) "Evolve to ${f.name}" else null

        if (c.sum == 45) tags += VerdictTag(VerdictKind.HUNDO, "Hundo", "15/15/15")

        // PvP: best Great or Ultra League rank over the Pokémon and its evolutions, only where it
        // still fits under the cap at its current level, and (1.16) only for a species PvPoke
        // ranks among the league's best: good IVs on a species nobody uses there make no pick.
        data class Pvp(val league: League, val form: Species, val rank: PvpRank)
        var best: Pvp? = null // used there, good IVs
        var missed: Pvp? = null // used there, IVs short of the cut: explains a KEEP or TRANSFER
        var unused: Pvp? = null // good IVs, but the species isn't used there
        fun Pvp?.worseThan(p: Pvp) = this == null || p.rank.rank < rank.rank
        for (f in chain) for (lg in listOf(League.GREAT, League.ULTRA)) {
            val cap = lg.cap ?: continue
            if (Stats.cp(f, gd.cpm[c.level], c.atk, c.def, c.sta) > cap) continue
            // Skip leagues it can't get near even at full power (a Sneasel isn't an Ultra League pick).
            if (!lg.canCompete(Stats.cp(f, gd.cpm[pvpMaxLevel.coerceAtMost(gd.maxIndex)], c.atk, c.def, c.sta))) continue
            val r = ranker.table(f, lg, pvpMaxLevel)[c.atk, c.def, c.sta] ?: continue
            val p = Pvp(lg, f, r)
            val used = (f.leagueRank(lg, variant.shadow) ?: Int.MAX_VALUE) <= rules.pvpSpeciesCutoff
            when {
                used && r.rank <= rules.pvpRankCutoff -> if (best.worseThan(p)) best = p
                used -> if (missed.worseThan(p)) missed = p
                r.rank <= rules.pvpRankCutoff -> if (unused.worseThan(p)) unused = p
            }
        }
        best?.let { b ->
            val xl = if (b.rank.level > Levels.L40) " (needs XL)" else ""
            tags += VerdictTag(
                VerdictKind.PVP,
                "PvP · ${b.league.label.substringBefore(' ')} #${b.rank.rank}",
                listOfNotNull(asForm(b.form), "Lv ${Levels.label(b.rank.level)}$xl").joinToString(" · "),
                steps = listOfNotNull(
                    evolve(b.form),
                    powerUp(c, b.rank.level, "for ${b.league.label}") ?: "Already at its best level for ${b.league.label}",
                ),
            )
        }
        // Master League wants near-perfect IVs on something big.
        val biggest = chain.maxBy { Stats.cp(it, gd.cpm[Levels.L50], 15, 15, 15) }
        val usedInMaster = (biggest.leagueRank(League.MASTER, variant.shadow) ?: Int.MAX_VALUE) <= rules.pvpSpeciesCutoff
        if (c.sum in 43..44 && c.atk == 15 && usedInMaster && League.MASTER.canCompete(Stats.cp(biggest, gd.cpm[Levels.L50], 15, 15, 15))) {
            tags += VerdictTag(
                VerdictKind.PVP, "PvP · Master", listOfNotNull(asForm(biggest), "$pct% IV").joinToString(" · "),
                steps = listOfNotNull(evolve(biggest), powerUp(c, Levels.L50, "for Master League")),
            )
        }

        raidTag(c, sp, chain, input, rules)?.let { tags += it }

        val keepReason = when {
            c.sum == 0 -> "Nundo"
            pct >= rules.keepIv -> "$pct% IV"
            chain.any { it.rare } -> "Legendary"
            variant.lucky -> "Lucky"
            else -> null
        }
        // Why a KEEP or TRANSFER isn't PvP: in a league where the species is used, its IVs miss;
        // else, good IVs on a species nobody uses there (the owner's Electrike as Manectric).
        fun leagueWord(p: Pvp) = p.league.label.substringBefore(' ')
        val pvpNote = missed?.let { m ->
            val cut = rules.pvpRankCutoff
            // Blame Attack only when a lower one, same Defense and HP, would make the cut.
            val table = ranker.table(m.form, m.league, pvpMaxLevel)
            val atkWhy = (0 until c.atk).any { a -> (table[a, c.def, c.sta]?.rank ?: Int.MAX_VALUE) <= cut }
            val where = listOfNotNull("${leagueWord(m)} #${m.rank.rank}", asForm(m.form)).joinToString(" ")
            "Not for PvP: $where (top $cut needed)." + if (atkWhy) " Too much Attack: it hits the CP limit early." else ""
        } ?: unused?.let { u ->
            val ivs = listOfNotNull("#${u.rank.rank}", asForm(u.form)).joinToString(" ")
            "Good ${leagueWord(u)} League IVs ($ivs), but ${u.form.name} isn't used there (not in PvPoke's top ${rules.pvpSpeciesCutoff})."
        }

        if (keepReason != null) {
            tags += VerdictTag(VerdictKind.KEEP, "Keep", keepReason, steps = listOf("Keep it ($keepReason), though it has no PvP or raid use"), note = pvpNote)
        }

        if (tags.isEmpty()) {
            tags += VerdictTag(
                VerdictKind.TRANSFER, "Transfer", "$pct% IV · no PvP or raid use", steps = listOf("Transfer it for candy"), note = pvpNote,
            )
        }
        val sorted = tags.sortedBy { it.kind.ordinal }
        // A hundo is kept anyway; then the same steps as its best use.
        if (sorted.first().kind == VerdictKind.HUNDO) {
            val use = sorted.drop(1).firstOrNull { it.kind == VerdictKind.PVP || it.kind == VerdictKind.RAIDS }?.steps ?: emptyList()
            return listOf(sorted.first().copy(steps = listOf("Keep it: a perfect 15/15/15") + use)) + sorted.drop(1)
        }
        return sorted
    }
}
