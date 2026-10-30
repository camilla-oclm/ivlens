# IV Lens roadmap: from here to Google Play

Status (30 Oct 2026): 1.27 is on Google Play as a closed test (approved 29 Oct), after an internal
test of 1.26 and 1.27. 1.24 brought the Play app ID, icon B, no internet access, saved-list
export/import and a Licences screen; 1.25 kept the Debug log to the dev build; 1.26 fixed a CP
hidden under a hat; 1.27 links the privacy policy in Settings. The listing is in `docs/play/`, the
website with the privacy policy is live (plumcometlabs.com), and this repo is public since 30 Oct.
Next: 12 or more testers for 14 days in a row, then production.

- **Scanning:** auto-scan with a verdict chip, plus a full card.
- **Calculations:** PvP and raid verdicts, and waiting for moves.
- **Appraisal:** reads the Appraise screen. Verified live in the game with 1.6: 16 Pokémon, all
  read and matched.
- **App:** in-app calculator and saved list, with an optional Auto save (1.7).
- **Design:** Bold Block.

The app will be published **free**, with **no ads, no purchases and no data collection**.

---

## 1. Before publishing: things to check on the phone

- [ ] **Appraise flow live.**
  - [x] The chip shows "exact" once the bars appear (1.6, 2026-10-17: 16 Pokémon). It hides
    during the leader's lines and comes back with the result, which the owner is happy with.
  - [ ] 1.7: a quick tap through the bars is still caught (1.6 missed one in 18).
  - [ ] 1.8: regional forms are told apart (Hisuian Qwilfish, Alolan Raichu and the like). The
    Hisuian Qwilfish is confirmed.
  - [ ] 1.9: the card's steps read well, and the ♂ / ♀ icon is read right. The owner's first look
    was good.
  - [ ] 1.10: regional forms that share stats (Alolan Vulpix, Hisuian Sneasel, Paldean Wooper)
    read as their own form; "Too small" shows where a Pokémon can't compete.
  - It follows the game's arrows to the next Pokémon.
  - It hides on the map.
- [ ] **Scroll down and back up** on several Pokémon: CP kept, moves picked up, nothing lost.
- [ ] **Animated backgrounds** (special backgrounds, sparkles): the level arc is still read.
- [x] **Add debug logging** (1.6): the Settings switch "Debug log", off by default. It logs each read,
  the Appraise bars and why the chip changed. Read it with `adb logcat -s IvLens`.
  - [x] Only in the dev build (1.25): the debug build is its own app, "IV Lens dev", and the
    Play build has no switch and writes nothing to the phone's log.
- [ ] **Battery check:** an hour of play with the bubble on.
- [ ] **Small-screen phone** (about 360 dp wide): chip text, card height, number pad.

## 2. Product polish before release

- [x] **Final name: IV Lens** (the owner, 2026-10-25). Of the candidates (IV Lens, StatSpark,
      Hundo Hunt, IVy) it's the only one with "IV" as its own word, the word Play searches match
      (Calcy IV, Smart IV, "IV: 100%"). Play would read "IVy" as the plant. No app by that name was
      found. The Play title carries more keywords (see Store listing). The app label stays "IV Lens".
- [x] **Icon: B, the level arc and dot** (`ivlens-arc`; the owner, 2026-10-27). In 1.24
      `tools/make_icons.py` makes the adaptive icon's layers (background `#111111`), the monochrome
      layer, the bubble and notification icons, and Play's 512 px icon and feature graphic.
- [x] **`applicationId`: `com.plumcometlabs.ivlens`** (the owner, 2026-10-27; 1.24). It can never
  change after the first Play upload. The code's package stays `com.ivlens.app`.
  - The old ID installs as a separate app, so 1.24 adds a saved-list export/import, and
    `-PlegacyId` builds 1.24 under the old ID to install over the owner's copies and export from.
- [x] **Internet permission: removed** (option b; the owner, 2026-10-27; 1.24). ML Kit added
  `INTERNET` to send usage stats to Google. 1.24 removes it, and the stats uploader's backend too,
  so the stats are dropped before they're stored: the uploader's code catches network errors but
  not the `SecurityException` a missing permission can raise. `ACCESS_NETWORK_STATE` stays (the
  stats library's job scheduler needs it; it can't send anything).
  - [x] Check on a phone (2026-10-27): text recognition works, nothing crashes, the log shows
    "Transport backend 'cct' is not registered".
- [x] **Licences screen** (1.24): Settings › About › Licences shows PvPoke's MIT notice, Archivo's
  OFL and the Apache 2.0 text (AndroidX, Compose, Kotlin), names ML Kit's and Play services' own
  terms and the PokeMiners raid data, and lists the 203 open-source parts Google's libraries carry
  notices for (`tools/licences.py`, which also checks every library's declared licence is covered).
  The owner read it on the dev phone (2026-10-27); the Apache appendix's fill-in template is cut.
- [x] **Master League verdict** (1.10): it now needs a max CP of 3,700 (Togekiss and Snorlax in,
  Venusaur out). Great and Ultra League need the Pokémon to get within 3% of the cap
  (`League.canCompete`). A list of known Master League picks could still sharpen this.
- [ ] **Appraisal-only reads** can't see Shadow or Purified, so the raid verdict may assume
  non-shadow. Carry these flags over from the chip's Pokémon where they match.
- [x] **Export/import** of the saved list (1.24): Settings › Saved list. A JSON file the user picks;
  import adds the Pokémon not saved yet and fills in exact IVs where only a range was saved.
  - [ ] Check on a phone: export from the old-ID build (done 2026-10-27), import into the Play-ID one.
- [ ] **First-run tour:** permissions, then "open a Pokémon", then what the chip words mean. The
  words are explained in the Scan tab since 1.9 ("What the chip says"); the tour could reuse it.
- [ ] **Languages:** only an English game client is parsed today. Say so in the listing, or add
  other languages later.
- [x] **Open source** (2026-10-28): MIT licence. The repo went public on 2026-10-30, once
  `tools/privacy_scan.py` said "clean", and the IV Lens page on plumcometlabs.com links to it.

## 3. Google Play checklist (free app, no ads)

**Studio**
- [x] **Studio name: Plum Comet Labs** (2026-10-25), generic so it fits later apps too. It's the
      Play developer name, the Discord server's name and the website, `plumcometlabs.com`.
- [x] Discord server "Plum Comet Labs" (see `docs/discord.md`).
- [x] Play developer account approved (2026-10-27).
- [x] **Contact email** Play shows publicly: `info@plumcometlabs.com`, the studio's address
      (2026-10-28; also in the privacy policy).
- [x] **Tester list: a Google Group** (2026-10-29), so testers join themselves:
      `groups.google.com/g/ivlens-testers`. The closed test points at it.

**Testing gate (new personal accounts)**
- [ ] Run a **closed test with at least 12 testers opted in for 14 days in a row**, then apply for
      production access from the dashboard. Checked on Google's help page on 2026-10-27; a tester
      who opts out and back in starts their 14 days again, so recruit more than 12.
  - The closed test "Alpha" was approved on 2026-10-29: 1.27, all countries, feedback through
    Discord's #bug-reports.
- [x] Each tester needs a Google account, joins the group and opts in through the test link
      (`play.google.com/apps/testing/com.plumcometlabs.ivlens`). The README's "Get it" section has
      the three steps.
- [x] An internal test first (2026-10-29): 1.26, then 1.27, installed from Play and signed with
      Google's app-signing key.
- Details and the production-access questions: `docs/play/listing.md` › Testing and release.

**Build**
- [x] `./gradlew :app:bundleRelease` produces a signed `.aab` (1.26 and 1.27, 2026-10-29). Play
      App Signing is on: the current keystore is the upload key.
  - After that, a build signed by the owner can't update a copy installed from Play, and the other
    way round: uninstall first (after an export). Since 1.25 the dev build has its own ID
    (`.dev`, "IV Lens dev"), so it sits next to the Play copy.
- [x] Target the current required API level (36 today).

**App content (Play Console › Policy › App content)** — all filed on 2026-10-29, with the answers
in `docs/play/listing.md`.
- [x] Privacy policy URL: `https://plumcometlabs.com/ivlens/privacy` (the text is
      `docs/play/privacy-policy.md`). It says:
  - the screen is captured only while the bubble is on;
  - images are processed on the phone and never stored or sent;
  - saved Pokémon stay on the device;
  - the app has no internet access (1.24), so ML Kit sends nothing.
- [x] Ads: **No ads**.
- [x] Data safety: no data collected or shared (INTERNET is removed since 1.24).
- [x] Content rating questionnaire. It's a utility, so expect "Everyone".
- [x] Target audience: 13+ (not designed for children, which keeps it out of Families policy).
- [x] **Foreground service declaration** (needed because the app targets Android 14+):
  - type **mediaProjection**;
  - describe the feature: screen reading for IV calculation while the user plays;
  - say what happens if it's interrupted;
  - link a short video showing the user starting the bubble and accepting Android's
    screen-capture prompt.
  - Filed with the description in `docs/play/listing.md` and a demo video, unlisted on the
    studio's YouTube channel. The closed test's review passed with it.
- [ ] Be ready to explain "Display over other apps" (the bubble and chip).

**Store listing**
- [x] Title (≤ 30 characters) without "Pokémon" or "Poké": **IV Lens: IV Calculator & PvP** (28;
      agreed 2026-10-25). It covers the words players search: "IV", "IV calculator" and "PvP".
  - No "Pokémon" or "Poké" in the title or icon. It's a trademark, The Pokémon Company enforces it,
    and strikes could close the studio's account, which is meant for later apps too. Poke Genie uses
    "Poke", but that's its risk.
  - Searches for "pokemon go" are owned by the official apps and 10M+ apps: no title wins those.
    Installs and ratings (the closed-test testers) move the ranking.
- [x] Short description (≤ 80 characters) and full description, in Play since 2026-10-29 from
      `docs/play/listing.md`.
  - Draft short description (80): "IVs, PvP ranks and raid value in one tap. Free: no ads, no account,
    no tracking."
  - The full description may say what it works with ("an IV checker for Pokémon GO trainers") a
    few times, naturally, with the fan-made, not-affiliated line. No keyword stuffing.
  - Mention compatibility descriptively at most, e.g. "for Pokémon GO trainers", plus the
    not-affiliated line.
- [x] Icon: 512×512 PNG (`docs/play/icon-512.png`, 1.24).
- [x] Feature graphic: 1024×500, no alpha (`docs/play/feature-graphic.png`, 1.24): the icon, the
      name, one line and the verdict words in Bold Block, no game art.
- [x] At least 2 phone screenshots: six since 2026-10-28 (`docs/play/screenshots/`). Prefer the
      app's own screens (Scan tab, calculator, saved list, Ranks, the chip and card). Avoid game
      artwork filling the frame. Both test phones are taller than Play's 2:1 limit, so crop them
      (see `docs/play/listing.md`).
- [x] Category: Tools. Price: Free. Countries: all.
- [x] AI-generated content: "Don't label". The screenshots are real, and the icon and feature
      graphic are drawn by a script.

**Release**
- [x] Closed test, live since 2026-10-29.
- [ ] Production, once 12 testers have stayed opted in for 14 days. Watch pre-launch reports and
      Android vitals for crashes.

## 4. Risks to keep in mind (not legal advice)

- **Trademarks:** the name, icon and screenshots must not suggest an official product.
- **Game terms:** screen-reading IV tools (for example Calcy IV and Poke Genie) have existed for
  years, but Niantic's terms are theirs to enforce. The app only reads the screen; it never
  automates input.
- **Data licensing:** PvPoke is MIT. The PokeMiners data is extracted game data with no formal
  licence, which is common practice for companion apps but not risk-free.
- **Sideloading after 2026:** Android is phasing in developer verification. It covers certified
  devices in some countries from 30 Sep 2026 and goes global in 2027. Builds shared outside Play
  will need a registered developer. Play publishing covers this.

## 5. Ideas for later

- Raid boss counters for the current rotation (needs a data refresh mechanism; keep offline-first).
- Batch mode: swipe through storage while the chip records each Pokémon to the saved list.
- Compare: "is this better than the one I already saved?"
- Shiny / XXL / XXS badges (weight and height are already read).
