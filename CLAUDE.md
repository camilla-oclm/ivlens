# IV Lens: notes for Claude Code

A light Pokémon GO companion for Android. A floating bubble reads the Pokémon on screen and gives
its IVs, PvP ranks, power-up costs and a straight verdict: **HUNDO / PVP / RAIDS / KEEP / TRANSFER**.
Everything runs on the phone.

The owner tests each build in the game on their phones and reports back with screenshots.
`docs/ROADMAP.md` has the next steps and the Google Play checklist. Read it before starting work.
Icon and name candidates are in `docs/brand/`; the Play listing drafts and graphics in `docs/play/`.

**This repo is public** (since 2026-10-30). Where things stand, the owner's laptop and phone setup and the
private notes live in `dropoff.md`, which is git-ignored and exists only on the owner's laptop: read
it first when it's there. Never put handoff notes, machine names, home paths, the owner's devices or
anything personal in a tracked file or a commit message. `tools/privacy_scan.py` checks for that.

## The owner's ground rules

- **Free forever.** No ads, no in-app purchases, no accounts, no tracking. The owner pays the Play
  developer fee.
- **No Pokémon IP.** No Pokémon artwork, no Poké Ball, no official logos or lettering, and no
  "Pokémon" or "Poké" in the app name or icon. Keep the fan-made, not-affiliated line in the app
  and the store listing.
- **On-device only.** Screenshots never leave the phone.
- **Plain language.** User-facing text is short, direct and jargon-free. Verdict words are fixed:
  HUNDO, PVP, RAIDS, KEEP, TRANSFER, plus the states MOVES? and CP?.
- **Bold Block design.** The owner picked it from four options: black 2 dp outlines, hard offset
  shadows, flat colour blocks and the Archivo font. Tokens live in `ui/Block.kt`.

## Build and run

- Needs JDK 17+ and the Android SDK (platform 36, build tools 36).
- Uses Gradle 8.14.3 through `./gradlew` (the distribution's checksum is pinned) and AGP 8.13.2,
  with Kotlin 2.2.21 and a Compose BOM.
- Android Studio isn't needed: `local.properties` (git-ignored) points Gradle at the SDK. The
  harmless "SDK XML version 4" warning means the SDK tools are newer than AGP.
- **Phones:** two test phones of different screen sizes (1080×2340 and 1200×2670), so the
  screen-relative readers (arc, Appraise bars, white page) are checked on both. They connect over
  USB with **USB debugging** on; `adb -d` picks the cable, and with both plugged in,
  `adb -s <serial>` picks one. A fresh install starts with the defaults (Debug log and Auto save off)
  and needs the overlay and screen-capture permissions granted. The owner's own setup is in
  `dropoff.md`.

```
./gradlew :app:testReleaseUnitTest      # all logic tests (screenshot ones only where screens/ exists)
./gradlew :app:assembleRelease          # signed APK -> app/build/outputs/apk/release/
./gradlew :app:bundleRelease            # .aab for Google Play
./gradlew :app:assembleDebug            # "IV Lens dev" -> app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -s IvLens                    # dev build only, with "Debug log" on in its Settings
```

- **Signing:** `ivlens-release.jks` plus `keystore.properties`. Both are git-ignored. Back them up
  outside the repo: updates must be signed with this key to install over the owner's copy. On Play,
  this key becomes the *upload* key, and Play App Signing holds the app-signing key. So a build
  signed here can't update a copy installed from Play (or the other way round) until it's
  uninstalled.
- **App ID** (1.24): `com.plumcometlabs.ivlens`, the Play ID, which can never change once uploaded.
  The code's package (`namespace`) stays `com.ivlens.app`. Builds up to 1.23 used `com.ivlens.app`
  as the ID too: `./gradlew -PlegacyId :app:assembleRelease` builds that ID, to install over those
  copies and export their saved list. Drop the switch once the owner's phones have moved.
- **Versions:** bump `versionCode` / `versionName` in `app/build.gradle.kts` for every build handed
  to the owner. The current version is **28 / 1.27**. Settings shows the installed version at the
  bottom.
- **Release build:** R8 is on (minify + shrinkResources) with `-dontobfuscate`. The build is arm64
  only (`abiFilters`).
- **Dev build** (1.25): the debug build is its own app, "IV Lens dev" (ID + `.dev`, version
  name + `-dev`, signed with the SDK's debug key), so it installs next to the copy from Play, which a
  laptop-signed build can't update. It has its own saved list and permissions. It's the only build
  with the Debug log (`BuildConfig.DEBUG`). It runs a little slower and skips R8, so a release is
  still checked last as a release build or through Play's internal test.

## How it works

```
OverlayService (foreground service, type mediaProjection)
 ├─ bubble (plain View)          tap = manual scan, long-press = stop
 ├─ chip   (ComposeView)         auto verdict, draggable, tap = full card
 ├─ card   (ComposeView)         full result, number pad, toggles, save
 └─ tick() every 400 ms
     ScreenCapturer.sample()  -> FrameSample (cheap fingerprint of the panel area)
     settled / changed?       -> autoScan()
        ScreenReader.read()   -> ML Kit OCR + ArcReader + AppraisalReader
        ScanResolver          -> species, CP/HP/dust, variant, moves, IV combos
        buildState()          -> Analyzer (PvP, power-ups) + VerdictEngine
        miniFor()             -> chip
```

### Packages

- `core/`: pure Kotlin, unit-tested.
  - `GameData`, CP/HP maths (`IvCalc`), stardust and candy costs (`Costs`). Shadow and Purified
    costs use a float32 multiply and ceil (800 × 1.2f shows as 961).
  - PvP stat-product ranks (`Pvp`).
  - Raid ranking (`Raids`): score = dps · (hp·def)^0.25 at L40, ranked per type with Shadows
    included and Megas excluded.
  - Verdict rules (`Verdict`). Defaults: PvP top 100 (species also in PvPoke's top 100 there), raids top 30,
    keep ≥ 82%.
- `scan/`: pure Kotlin, unit-tested on real screenshots.
  - `ScreenParser`: reads OCR lines into CP, HP, dust, name, candy, moves, weight and
    shadow/lucky flags.
  - `SpeciesMatcher` / `MoveMatcher`: fuzzy matching against OCR noise.
  - `ArcReader`: finds the level dot on the white arc.
  - `ScanResolver`: turns a reading into IV combos.
  - `ScrollMerge`: decides when a scrolled view is the same Pokémon.
  - `AppraisalReader` / `AppraisalFit`: read the Appraise screen's bars and match them to CP/HP.
- `service/`: Android glue: capture, ML Kit and the overlay service.
- `ui/`: Compose. `Block.kt` (tokens and components), `VerdictViews.kt` (chip, banner),
  `Results.kt`, `OverlayCard.kt`, `Screens.kt` (in-app Scan / Calc / Saved / Settings tabs),
  `Ranks.kt` (Ranks tab), `Share.kt` (Copy name), `BugReport.kt` (Report a bug), `SavedFile.kt` (export/import),
  `Licences.kt`.
- `store/`: settings (SharedPreferences) and the saved collection (`collection.json`, and its export file).
- `assets/pokedata.json`: generated by `tools/build_data.py` (see "Game data").

### Behaviours that were tuned on the owner's screenshots

Each of these fixes a real failure. Keep them unless a test shows they're wrong.

- **"Is this a Pokémon page?"** A page counts when ≥ 55% of the panel area (rows 0.395h–0.83h) is
  pure white.
  - A read happens once the fingerprint settles (diff ≤ 6). If the page keeps animating, it reads
    after 2.5 s anyway.
  - A big change (diff > 250, from a scroll or swipe) resets the back-off.
- **Scrolling:** CP goes off the top first, but HP stays. `ScrollMerge` treats that as the same
  Pokémon: it keeps CP and result and only adds the moves. Before 1.4, the read turned into
  "CP? HIDDEN" and the moves were lost.
- **Moves:** when the raid verdict depends on unknown moves, the chip shows **MOVES? · SCROLL DOWN**
  until they're read. Pre-evolutions don't wait, because evolving re-rolls moves.
  - Moves read once are remembered per Pokémon for the session (`MoveMemory`, 1.18), and filled in
    on any later read, tap or appraisal of it. The log says "moves from earlier".
  - Before 1.18 they were kept only while the chip stayed on that Pokémon. The owner's Kyogre
    (CP 2459, HP 133; 2026-10-24), swiped away from to a Dialga and back, asked for them again.
  - 1.19, from the 1.18 review:
    - The key is species|CP|HP|**weight**: two raid catches with the same IVs share CP and HP but
      not moves. With the CP or the weight unread, nothing is remembered or filled in.
    - The top of the page often shows the fast move and only the first charged move (Dialga,
      Mewtwo). Such a part of the known set keeps the full set, and gets it filled in. Other moves
      (a TM) replace it.
    - Moves from a scroll are remembered only when the scrolled view shows it's the same Pokémon
      (`ScrollMerge.confirms`: its weight, else its HP). A quick swipe to another Gyarados and a
      scroll could otherwise file its moves under the first one for the whole session.
  - The appraised IVs had the same flaw (1.15 to 1.18): one appraised with the CP hidden was kept
    under species|null|HP, so another of the kind with the same HP and stardust, CP hidden too,
    showed its IVs as exact. Since 1.19 such a key needs the weight too
    (`AppraisalFit.memoryKey`; `HiddenCpTest`, two Dragonite); with the CP read it's unchanged.
- **Appraise screen** (reworked in 1.6): the game tints the page in the team colour and shows an
  animated leader, so the page never reads as white (whiteness 0.29) and never settles.
  - **The bars are found by their look alone, with no text reading** (`AppraisalReader.find`):
    three bars with the same ends, evenly spaced, each three equal segments, in the left part of
    the lower half (`region()`: x < 0.7w, 0.55h–0.97h).
    - Up to 1.5, text recognition had to find the "Attack / Defense / HP" labels first. On the
      owner's Zoroark it showed nothing at all.
    - Anything after a bar's third segment is ignored, so a leader standing close can't stretch it.
    - Our own chip and bubble are blanked out first (`blankOut`), so a covered bar is never guessed.
  - Off the white page, `checkBars()` runs on **every tick**, reading only that region straight
    from the frame buffer (`ScreenCapturer.region`).
    - Bars count once two frames in a row agree, since the fill animates in.
    - New bars get up to three quick second looks, 150 ms apart (1.7). Before that, the second
      look waited for the next 400 ms tick, and a quick tap could close the screen first. That
      happened once in 18 tries in 1.6's live test (a Minun).
    - Then one full read of the page behind tells whose bars they are (`matchBars` →
      `showAppraisal` → `appraisedState`).
    - Bar checks go on after the 15 s "page behind" timer gives up. That timer used to stop all
      appraisal reading, so a slow walk through the leader's lines was a dead end.
  - Bars are measured by colour: red (209,131,131) at 15, orange (231,169,92) below 15, grey track
    (226,226,224). Each of the three segments holds five points, and a value counts only on a whole
    point.
  - An appraisal is accepted only if a combo fits CP and HP (`AppraisalFit`). It's remembered per
    Pokémon (species|CP|HP) for the session.
  - Bars that don't fit are kept as `OverlayState.bars`. The chip then says **CHECK · BARS**, and
    the card opens with the BARS editor filled in. The bars apply as soon as CP or HP is fixed.
  - **BARS editor** on the card: three rows of 15 cells in fives, like the game's bars; tap where
    each bar ends. It's the manual way when reading fails.
  - Verified on real screens: Cloyster 15/7/15, and Zoroark 10/12/7 at L27 (CP 1879, HP 112;
    15 combos from CP/HP alone).
  - Verified live in the game on 2026-10-17 (1.6): 16 Pokémon appraised, each read and matched to
    its CP/HP. Half-drawn bars seen while the bars animate in (1/1/1, 7/4/7) failed the fit and
    were ignored.
  - The chip hides when Appraise is tapped (the leader's lines cover the page behind), then comes
    back with the exact IVs once the bars show. The owner is fine with this.
- **Forms that share a name** (Qwilfish and Hisuian Qwilfish, Raichu and Alolan Raichu; 1.8):
  - The parser reads the type labels under the weight and height ("DARK / POISON", in capitals;
    `ScreenReading.types`). `ScanResolver.candidates` keeps the forms with those types.
  - On the Appraise screen the leader can cover the labels ("DARK / POIS" reads as just DARK). So
    when the bars don't fit the chosen form, `AppraisalFit.fitForms` tries its other forms, and
    the state switches to the one that fits.
  - Before 1.8, the owner's Hisuian Qwilfish was read as a plain one, whose CP and HP fit too, and
    its bars showed CHECK · BARS.
- **Gender** (1.9): `GenderReader` finds the ♂ / ♀ icon by its look, in a band at the right of the
  page from the name line down (`ScreenReading.nameLine`), and sets `CalcInput.gender`.
  - The icon is pale blue-grey. ♀ is symmetric (as many pixels left as right at the top); ♂ is
    about three times heavier at the top right. Genderless Pokémon have no icon.
  - On the Appraise screen the tint hides the bottom of the ♀, but the top still tells.
  - `GameData.evolutionChain(sp, gender)` drops evolutions open only to the other gender
    (`GENDER_ONLY`: Gallade, Froslass, Vespiquen, Salazzle, Mothim, Wormadam and others). The
    verdict and the card's league rows both use it. Before 1.9, a female Kirlia showed Gallade.
  - Data gaps: PvPoke has no male Meowstic, and Lechonk only evolves to the male Oinkologne.
- **Steps on the card** (1.9): each verdict carries plain steps (`VerdictTag.steps`), and the card
  lists them in place of the short detail line. Examples: "Evolve to Gardevoir", "It needs Charm +
  Dazzling Gleam: use TMs if evolving doesn't give them", "Power up to Lv 40 for raids".
  - A power-up step is left out when the Pokémon is already at that level.
  - A HUNDO's steps are "Keep it" plus the steps of its best use (PvP or raids).
  - The chip is unchanged.
- **"What the chip says"** (1.9): a panel in the Scan tab explains each verdict word, MOVES?,
  league ranks and "Over cap".
- **PvP only where it can compete** (1.10): `League.canCompete(maxCp)`. Fully powered, with its own
  IVs, the Pokémon must get within 3% of the cap (2,425 Ultra, 1,455 Great), or reach 3,700 CP
  for Master League.
  - The verdict, the Master League tag, the card's power-up targets and the league rows all use it.
    A row it fails shows **Too small** and the max CP, never a blue rank.
  - Before 1.10, the owner's maxed-out Sneasel (2,310 CP) said "PVP · Ultra #8". The #8 only meant
    good IVs for a Sneasel. A 98% Venusaur (3,075 CP) said "PVP MASTER".
- **PvP needs a species that's used** (1.16): PVP needs the species (or what it evolves into) in
  PvPoke's top 100 for that league (`Species.leagueRank`, the Shadow rank for a Shadow;
  `VerdictRules.pvpSpeciesCutoff`, Settings "PvP species": 50 / 100 / 200), *and* a top-100 IV
  rank. The Master League tag needs the species there too. The owner asked "if the stats are bad,
  what's the point?" after a 33% Flabébé said PVP.
  - That Flabébé (0/11/4) stays PVP: Florges is Ultra #8 and Great #10 in PvPoke. The 76%
    Electrike (Ultra #85 as Manectric) lost it: Manectric is Ultra #672.
- **Why a KEEP or TRANSFER isn't PvP** (1.11, 1.16): `VerdictTag.note`, shown on the card under the
  steps once the IVs are exact.
  - In a league where the species is used but the IVs miss: "Not for PvP: Great #161 as Florges
    (top 100 needed)", plus "Too much Attack" only when a lower Attack (same Defense and HP) would
    make the cut.
  - Otherwise, good IVs on a species nobody uses there: "Good Ultra League IVs (#85 as Manectric),
    but Manectric isn't used there (not in PvPoke's top 100)."
  - The owner asked why a 76% Electrike was PVP and a 96% one only KEEP.
- **Shadow / Lucky / Purified behind the Appraise screen** (1.11, 1.12): there the stardust cost
  and PURIFY are hidden, and a number read there can be anything (the year in "caught on …/2026").
  - `seenVariant` keeps the kind from reads that had the cost. `behindAppraise()` drops the
    stardust and takes the kind from `seenVariant`, else PURIFY / LUCKY on screen, else plain.
  - A read with the cost replaces a same-key state of another kind, even when it's no sharper.
  - Before 1.11, a Shadow Rhyhorn (13/2/2) swiped to on the Appraise screen said TRANSFER, and the
    full page read after it (cost 961, RAIDS) was dropped as "the same Pokémon, not sharper".
  - Before 1.12, a Nymble there came out Purified · Lucky from a stray number.
- **Overlay boxes where the overlays really are** (1.12): `overlayRects()` shifts the bubble's and
  chip's boxes by how far Android moved each window from its params (`shiftOf`, measured with
  `getLocationOnScreen`; overlays can sit below the status bar). The log says the chip's shift.
  - Before 1.12 the chip's box sat too high and its own LUCKY label (KEEP · LUCKY) was read at
    51,548. Every Pokémon after a Lucky one then came out Lucky, on the Appraise screen too.
- **CP hidden by the Pokémon:** some Pokémon (Dragonite, Rhydon, Kingdra, an XXL Munna) move over
  the CP, so it isn't read (`NO_CP` in the log). That's normal, and other IV apps have it too. The
  number pad is the fallback.
  - Since 1.15 the Appraise bars stand in for it: with exact IVs, the HP and stardust (and the arc,
    if read) pin the level, and the CP follows (`IvCalculator.cpFor`, used by `AppraisalFit.fit`).
    If two levels still fit (Magikarp, HP 49 at 1,600 stardust), nothing is guessed.
  - The owner's XXL Munna (HP 103, 1,900 stardust; 2026-10-23) covered its CP fully; even the
    owner couldn't read it.
  - A CP covered in part is misread instead (1.24): the owner's Drifblim wears a hat over its
    CP 223, read as "23", "29", "290", "292" and "293", where no IVs fit at all, so its bars
    "didn't fit". A CP that fits no IVs is now treated as hidden, and the bars work it out
    (`AppraisalFit.misreadCp`; `MisreadCpTest`). A CP that fits some IVs is never overruled, and
    the level arc, if read, must agree. Rules on the digits read (one, then two digits off) were
    tried first; each new misread broke them. An Electrike's 410 read as 10 was the same.
  - 1.26: the arc only has to agree to within a half level. Another Drifblim with a hat (CP 345
    read as 45, HP 101, 600 stardust, bars 4/3/4; 2026-10-29) is Lv 6, and its arc read as 5.5
    (see "Level arc"), so its bars were refused. An arc a whole level off still refuses them.
  - Swiped away and back, the CP came out garbled a new way, so the appraisal kept under the CP
    read before wasn't found and the CP had to be typed. Appraisals are now also kept under the
    hidden-CP key (species|null|HP|weight; `AppraisalFit.memoryKeys`), and a read whose CP fits no
    IVs looks there too (`recallKeys`). Without the weight read, nothing new is found.
- **Auto save** (Settings, off by default; 1.7): `autoSave()` saves each state the chip shows,
  after a read, the moves or an appraisal.
  - The saved list keeps one entry per Pokémon (species|CP|HP; `putSaved`). A new save replaces
    the old entry and keeps its id. Card and calculator saves work the same way.
  - An auto save never swaps saved exact IVs for a range, and a read that changes nothing isn't
    written.
  - Reads with a problem (hidden CP, bars that don't fit) aren't saved.
- **Deleting several saved entries** (1.14): SELECT by the Saved title, or a long press on an entry,
  starts picking. Taps then tick entries. A bar above the list (it stays in view) has ALL (every
  entry shown, so a search narrows it), NONE, DONE and DELETE, which asks first
  (`CollectionStore.removeAll`). Back leaves picking.
- **Debug log** (Settings, off by default; since 1.25 only in the dev build, so the Play build
  writes nothing to the phone's log): `log()` writes to `adb logcat -s IvLens`. It covers each
  auto-read (species, CP, HP, dust, the arc's level since 1.26, combo count, [Shadow] / [Lucky] and
  the text that said LUCKY), the bars seen and whether they fit, and why the chip was hidden.
- **Level arc:** candidate dots are scored by "solid line to the left, translucent to the right",
  so sparkles on the faint part are ignored.
  - If the arc disagrees with CP/HP/dust, `recheckArc()` re-reads it from one or two fresh frames.
  - Geometry defaults to r = 0.4096w, cy = 0.638w. The last confident fit is remembered per screen
    size (prefs "arc").
  - **Calibration** (`ANGLE_L1`, `SPAN`; the dot's angle is linear in the CP multiplier): fitted on
    Pokémon at Lv 17–32.5, all on the right half of the arc, where it's within 0.3°. The first one
    on the left half, the hatted Drifblim at Lv 6 (`drifblim_hat`, 2026-10-29), sits 2° off (125.1°
    read, 123.1° expected), which is half a level there. A refit with it (179.4 / 179.6) keeps every
    point within 0.4°, but moves the untested Lv 35–50 end by about half a degree, so it waits for
    screenshots at low and high levels. Nothing above Lv 32.5 has been checked yet.
- **Chip placement:** the default spot is centred inside the arc over the Pokémon. Prefs "mini"
  hold the keys `x2`/`y2`; the owner has dragged it lower, above the name.
  - The chip's area is excluded from the fingerprint and the arc. OCR lines are dropped only if
    their **centre** is under the chip.
- **Hidden CP:** retries every 2 s up to 4 times. After that, the card opens with the number pad
  on CP.
- **Number pad cursor** (1.20): a tap on the number moves the cursor between the nearest digits
  (`getOffsetForPosition` on the number text), and digits and ⌫ work there (`NumberInput`).
  Before, only the last digit could be deleted: the owner had to retype a whole CP to fix one digit.
  - 1.22: the cursor is a bar drawn between the digits (`drawWithContent` + `getCursorRect`), not a
    "▏" character: Archivo lacks it, so a wider stand-in glyph opened a gap and shifted the digits
    each time the cursor moved, and taps landed off (the owner's Torchic, CP 610 read as 10). The
    number is 30 sp and spaced; a last row has ← → (one digit) and Cancel. Check new symbols are in
    Archivo before using them (fontTools on `res/font/archivo.ttf`): ← → ‹ › — are; ▏ ◀ ▶ ⌫ ✓ aren't.
- **Studio links** (1.21): Settings ends with "Made by Plum Comet Labs" and Discord / Website
  buttons (`StudioLinks`), opened with `LocalUriHandler`, so no internet permission is needed. The
  Discord invite must never expire: check it with
  `curl -s "https://discord.com/api/v10/invites/<code>?with_expiration=true"` (`expires_at` null).
  - **Privacy policy** (1.27): a button above Licences opens `StudioLinks.PRIVACY`, the website's
    copy of `docs/play/privacy-policy.md`. Play wants the policy linked in the app as well as in
    Play Console (its privacy policy page says so); the app had no link until then.
- **Settings tab** (1.22): Settings is its own tab next to Saved, in panels Look, Scanning, Verdicts
  and About. Before, it was a panel at the end of Scan.
- **Appraise hint** (1.22): while several IVs still fit, the chip's first small line is "Appraise for
  exact IVs" (`APPRAISE_HINT`), and the card's IV line and "Likely…" line point to the Appraise
  screen. A chip asking for the moves (MOVES?) doesn't add it: moves first. Before, only the Scan
  tab's "How it works" mentioned Appraise; the owner noticed the chip never did.
- **Dark theme** (1.22): Settings › Look › Theme: PHONE (the default, follows the phone's dark mode),
  LIGHT or DARK. The chip and card over the game follow it too.
  - The neutral colours come from `BlockPalette` (`LightBlock`, `DarkBlock`) through
    `LocalBlockPalette`; `Block.Ink`, `Paper`, `Page`, `Muted`, `Grey`, `OrangeInk` and `OnInk`
    read it. Dark is Bold Block turned around: light outlines and hard shadows on near-black.
  - The colour blocks (yellow, blue, orange, teal, red) are the same in both themes, with dark text
    (`Block.OnColor`; white on blue). `inkOn(fill)` picks the text colour for a fill.
  - `blockText` defaults to `LocalInk`, the ink for the fill being drawn on. `BlockPanel` sets it for
    its fill; anything else drawn on a colour block passes `inkOn(fill)` or sets `LocalInk` itself.
  - The status bar icons follow the app's theme (`MainActivity`), since it can differ from the phone's.
- **Copy name** (1.23): with exact IVs, COPY NAME on the card (and in the Calculator) copies a name
  to paste when renaming the Pokémon in the game, so its IVs show in the Pokémon list (`Nickname`).
  A nickname replaces the species name there, so the name leads (the owner's first try copied
  "71 7/14/11"). IVs are circled numbers ⓪–⑮, one character each, to fit the game's 12:
  "Torchic71⑦⑭⑪", "Medich U8⓪⑮⑭" for a PvP pick (league letter and rank), "100" for a hundo; a
  long name is cut, a form in brackets dropped. Android 13+ shows its own "copied" note.
- **Saved filter** (1.23): a SHOW row of verdict toggles with counts (only verdicts present) above
  SORT; it works with search and SELECT › ALL.
- **Report a bug** (1.23; a form since 1.24, the owner's idea): `BugReportDialog` has a box for
  what went wrong, and shows what's added to it (app version, phone, Android version, screen size;
  from the card, "Wrong read? Report it", also what was read), so people know their phone model
  goes with it. COPY & OPEN DISCORD copies the report and opens `StudioLinks.BUG_REPORTS`; SHARE
  opens Android's Share menu. The card opens the form in the app (`MainActivity.EXTRA_BUG_READ`),
  since typing needs a keyboard. Text only, deliberately: the game prints where each Pokémon was
  caught, so no screenshot is attached; the form asks people to crop that line from any they add.
  - `BUG_REPORTS` is a never-expiring invite made from #bug-reports (a forum channel), so it lands
    there for members and newcomers; a channel link would only open for members. Check it with the
    Discord invite API like the main one (`expires_at` null, channel bug-reports).
- **Ranks tab** (1.23, the owner's idea): RAIDS per type, the top 20 of `RaidRanker.board` (the
  boards the RAIDS verdict ranks against: Lv 40, perfect IVs, best moves, Shadows in, Megas out),
  with moves (* Elite TM) and % of #1; PVP per league, PvPoke's top 50 from `pvp` / `pvps`
  (`Rankings`). SAVED marks a species in Saved (same Shadow-ness). A tap opens the line in place:
  types; PvP the #1 IVs for the league with level and CP (`Rankings.bestIvs`); raids the moves
  with types and Elite TM, CP at Lv 40. "Check mine" opens it in the Calculator (a tap did that at
  first; the owner found the jump confusing). Calculator's tab label is Calc so five fit.
  - Look (the owner picked "B" of four mockups): gold / silver / bronze rank squares for the top 3,
    type pills in the usual type colours (`TypeColors`; white text on the dark ones), SHADOW and
    SAVED pills, a raid % bar in the charged move's type colour, a colour dot per type button.
    No Pokémon pictures, deliberately: artwork is ruled out (Play takedowns), and they'd need
    10–30 MB or internet access.
  - Instead, an open line links to the species' Pokémon GO page on pokemon.gameinfo.io (and since
    1.24 so does the name on the Calculator's result, the owner's request)
    (`Pokedex.url`: /en/pokemon/<dex>-<slug>, the owner's pick over pokemon.com's main-games
    Pokédex): the underlined name and a More info button, opened in the browser (no internet
    permission). The site goes by the number (a misspelt slug still lands right), so the dex must be
    right; a form uses its species' page.
- **Game data date** (1.23): `GameData.date` from the `source` line, shown in Settings › About and
  on the Ranks tab. The ranks only change when a release ships new data, so refresh it for releases.
- **No internet access** (1.24, the owner's pick for Play): ML Kit adds `INTERNET` to send usage
  stats to Google; the manifest removes it, and the stats uploader's backend (the
  `CctBackendFactory` meta-data), so the stats library drops each event at once ("Transport backend
  'cct' is not registered"). Without the backend removal, a missing INTERNET could crash it: its
  sender catches network errors but not the `SecurityException`. `ACCESS_NETWORK_STATE` stays for
  its job scheduler (Android 14+ wants it for network conditions); it sends nothing. Links (Discord,
  website, gameinfo.io) open in other apps, so they still work.
- **Saved list export/import** (1.24): Settings › Saved list. Export writes
  `ivlens-saved-<date>.json` (`SavedFile`: app, format 1, time, entries) where the user picks;
  Import reads that or a bare `collection.json`. `mergeSaved` adds Pokémon not saved yet
  (species|CP|HP, as `putSaved`), fills in exact IVs where only a range was saved, keeps the rest,
  and gives a clashing id a free one. Made for the move to the Play app ID.
- **Licences** (1.24, `ui/Licences.kt`): Settings › About › Licences lists PvPoke's MIT notice,
  Archivo's OFL and Apache 2.0 (AndroidX, Compose, Kotlin and the rest), then the 203 open-source
  parts inside ML Kit and Play services; a tap opens the text, each paragraph unwrapped (`reflow`).
  - `tools/licences.py` collects those parts from the libraries' `third_party_licenses` files into
    `assets/licenses/third-party.json` (one copy of each text). Rerun it after changing a
    dependency: it also stops on a library whose declared licence the screen doesn't cover.
  - The Apache text's appendix ("Copyright [yyyy] [name of copyright owner]") is cut everywhere:
    it's a fill-in template, and the owner took it for a placeholder.
- **Icons** (1.24): icon B, the level arc and dot (`docs/brand/ivlens-arc.svg`). Run
  `tools/make_icons.py` to remake the launcher layers, the monochrome layer, `ic_bubble` (34 dp on
  the yellow bubble) and `ic_stat`, plus Play's icon and feature graphic in `docs/play/`. The
  one-colour versions stop the arcs short of the dot, since they can't draw its dark ring. "IV" is
  a path from Archivo at weight 900.
  - The notification icon has its own bolder drawing (no faint track, a thicker arc, a larger IV):
    at 24 dp the first one was barely visible (the owner, on a phone). The notification is tinted
    Bold Block blue (`setColor`) for the shade.
- **README banner** (2026-10-29, the owner's pick over Play's yellow feature graphic):
  `docs/readme/banner.png` is the top of `plumcometlabs.com/ivlens`, captured with headless Chromium
  (`--force-prefers-reduced-motion --force-device-scale-factor=1.5 --window-size=1280,640`; with
  motion the title is caught mid fade-in) and cropped to rows 88–287 at 1×. Remake it if the site's
  header changes. Below the intro, the six Play screenshots sit in a 3×2 table with the website's
  captions.
- **Game language:** the reader expects the game in English (POWER UP, PURIFY, LUCKY, English names).
  Other languages would need their own words and names; until then the Play listing should say so.

## Tests and fixtures

- `app/src/test/resources/screens/*.rgb.gz` are real screenshots stored as gzipped raw RGB.
  **They're not in the repo** (the folder is git-ignored): they show game artwork, so the owner
  deleted them from it on 2026-10-28. `TestData.screenshot()` skips a test whose screenshot is
  missing, so the pixel tests (arc, Appraise bars, gender, forms) run only where the files exist.
  - Header: three big-endian ints (width, height, rows stored), then RGB bytes. Usually only the
    top half is stored; `cloyster_appraise`, `zoroark_appraise` and `qwilfish_hisuian_appraise`
    store the whole screen.
  - The easiest way to get a new screen is `adb exec-out screencap -p > x.png` while the owner holds
    it on the phone. Turn it into a fixture with its catch place painted out (see below).
  - A new screenshot can still back a test: keep its fixture in the ignored `screens/` folder, so
    the test runs on the laptop and is skipped elsewhere. Where possible, also test the rule on
    numbers or OCR lines, which can be committed.
- `app/src/test/resources/ocr/*.tess.json` are Tesseract OCR of the same screens. These are rougher
  than ML Kit: Tesseract misses some text, which the tests add by hand with a comment.
- To add a fixture from a new screenshot, use `tools/make_fixture.py` (see its header).
- **No personal data in fixtures.** The game prints where and when a Pokémon was caught ("caught on
  … around <place>"). `make_fixture.py` stores only the top half of the image by default, but it
  runs OCR on the whole screen.
  - Replace those lines in the `.tess.json` with placeholders ("Hometown, Region, Country",
    1/1/2020) before committing.
  - With `--full`, paint over them in the pixels too.
  - The 2026-10-17 scrub covered `cloyster_appraise` (OCR text and pixels), `fomantis_moves`,
    `gyarados_moves`, `mewtwo_moves` and `mewtwo_pvp_tab`.
  - On the Appraise screen, the place is in the leader's speech bubble. Painting rows 2150–2285,
    x 60–1010 (on 1080×2340) with the bubble's own colour from x = 45 clears it. `zoroark_appraise`
    and `qwilfish_hisuian_appraise` were made that way.
- Notable truths:
  - Gyarados: 14/9/15 at L31.5.
  - Venusaur: 15/14/15 at L24.5.
  - Metagross (CP 2132): L20, 12/15/13 or 13/12/14.
  - Espeon (CP 1495): L17, 13/12/10 or 13/11/11.
  - Cloyster appraisal: 15/7/15 at L19.
  - Hisuian Qwilfish appraisal (CP 889, HP 90): 14/11/11 at L15. No IVs fit plain Qwilfish.
  - Genders on the fixtures: ♂ Dragonite, Kubfu, Snorlax, Venusaur. ♀ Alakazam, Fomantis, Gyarados,
    Cloyster, Zoroark, Qwilfish. No icon: Dialga, Metagross, Mewtwo, Rayquaza.
  - The owner's Kirlia (♀, CP 791, HP 92): 15/14/13 at L29, RAIDS as Gardevoir (Fairy #12).
- UI checks were done with Paparazzi snapshots in a scratch copy of the project, which is not
  included. Adding Compose `@Preview`s or Paparazzi here would help.

## Game data

`tools/build_data.py gm.json Pokemon.js out.json pm.json rankings_dir` rebuilds `assets/pokedata.json` from:

- **PvPoke `gamemaster.json` (MIT):** species, base stats, families, evolutions, movepools, elite
  moves.
- **PvPoke `src/js/pokemon/Pokemon.js`:** the CP multiplier table, levels 1–51.
- **PokeMiners `latest.json` game master:** raid (PvE) move power, duration and energy.
- **PvPoke league rankings** (1.16): `rankings-1500.json`, `-2500` and `-10000` in one folder give
  each species its rank per league (`pvp`, and `pvps` for its Shadow). All ranked ids matched.
  Without the folder, no species counts as used and nothing is PVP, so always pass it.

Refresh it after big game updates (new Pokémon or move rebalances), then rerun the tests. The
league rankings shift with the meta, so refresh them now and then too.

- **Downloads:** the files come from the raw GitHub files of `pvpoke/pvpoke` (`master`:
  `src/data/gamemaster.json`, `src/js/pokemon/Pokemon.js`,
  `src/data/rankings/all/overall/rankings-{1500,2500,10000}.json`) and `PokeMiners/game_masters`
  (`master`: `latest/latest.json`). Keep them out of the repo.
- **1.16 build:** PvPoke's game master of 2026-10-21 03:50:13 changed nothing the app uses since
  2026-10-17 (only the date); the rankings were fetched on 2026-10-23.
- **Merging:** costume and event copies (same name, stats and types) are merged into one entry.
  - Since 1.10 the types are part of that test. Before, 36 regional forms with the same stats were
    merged into the regular one, with their evolutions and moves.
  - So regular Sneasel "evolved" to Sneasler, Vulpix to Alolan Ninetales, and a plain Darmanitan
    was filed as the Galarian (Ice) one.
  - Rebuilding from the same game master (2026-10-17 03:10:24) without the fix gave a
    byte-identical file. With the fix: 1,052 species (36 added, the 30 entries they were merged
    into corrected, nothing removed).
- **Doubtful in PvPoke's data:** Cubone lists Alolan Marowak as an evolution. In Pokémon GO a
  Cubone only becomes a plain Marowak, as far as we know. It's left as PvPoke has it.
- **Unreleased in PvPoke, but in the game** (1.13): `SEEN_IN_GAME` in `build_data.py` keeps such
  species anyway. So far `minior_core` and `ditto` (1.17, below). The owner's Minior (CP 296, HP 46,
  600 stardust) showed NO_SPECIES. It fits the Core form's stats, not the Meteor form's. Add others
  the same way when the owner meets them, with a test (`MiniorTest`, `DittoTest`).
  - Rebuilt from the same three files (game master 2026-10-17 03:10:24), the only change was
    Minior (Core) added: 1,053 species.
  - **Ditto** (1.17): PvPoke leaves it out because it can't battle normally (Transform, Struggle).
    The owner's Ditto (CP 668, HP 105, 5,000 stardust, bars 11/13/10 at L30) showed NO_SPECIES
    and CHECK · BARS (`DittoTest`). Transform gains no energy, so it was filed as a charged move
    until 1.19: `build_data.py` now counts a move that is only ever a fast move as fast. That also
    gave it raid stats (power 0), so Ditto ranks at the bottom for Normal and the chip never waits
    for its moves. Only Transform changed. Rebuilt from the 1.16 files plus rankings fetched 2026-10-24:
    only Ditto added, 1,054 species.
  - PvPoke also marks Spewpa, Cosmog, Cosmoem and Pyukumuku unreleased; they're likely in the game
    (not checked), so they're candidates when the owner meets one. Shedinja is in the game too, but
    its HP is always 1, which the maths would need to handle before it goes in.

## Working style that suited this project

- Fix real failures with a test built from the owner's screenshot first, then change the code.
- Keep detection logic pure (in `scan/` or `core/`) and keep the service thin.
- Build, bump the version and hand over an APK; the owner tests in the game and reports back with
  screenshots.
