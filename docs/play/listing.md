# Google Play: listing and app content (2026-10-27, updated 2026-10-30)

What to paste into Play Console for IV Lens 1.27 (`com.plumcometlabs.ivlens`); all of it was filled
in on 2026-10-29. The files next to this one are the graphics: `icon-512.png` and
`feature-graphic.png`, both made by `tools/make_icons.py`.

## Store listing

- **App name** (30 max): `IV Lens: IV Calculator & PvP` (28)
- **Short description** (80 max):
  `IVs, PvP ranks and raid value in one tap. Free: no ads, no account, no tracking.` (80)
- **Category:** Tools. **Price:** Free. **Countries:** all.
- **Contact email:** `info@plumcometlabs.com`, the studio's address (also in the privacy policy).
  **Website:** `https://plumcometlabs.com`.
- **Graphics:** `icon-512.png` (512×512), `feature-graphic.png` (1024×500, no alpha).
- **Phone screenshots:** 2 to 8. Each side must be 320–3,840 px, and the long side at most twice
  the short one. The test phones (1080×2340 and 1200×2670) are both taller than 2:1, so crop
  to 1080×2160 (drop the status and navigation bars) or frame them. Prefer the app's own screens:
  Ranks, a Calc result, Saved, Scan's "What the chip says", the card. Keep game artwork small, and
  paint out any "caught … around <place>" line.
  **Made 2026-10-28:** `screenshots/1-chip.png` to `6-saved.png` (chip, card, appraise, ranks, calc,
  saved), 720×1405, status and navigation bars cropped, the appraise shot's catch place painted out.
  The same set is the screenshot gallery on plumcometlabs.com/ivlens.
- **AI-generated content** (asked with the graphics): "Don't label". The screenshots are real, and
  the icon and feature graphic are drawn by a script.

### Full description (4,000 max)

```
IV Lens reads the Pokémon on your screen and tells you straight away what it's good for: HUNDO, PVP, RAIDS, KEEP or TRANSFER.

It's an IV checker for Pokémon GO trainers that runs entirely on your phone. Free, with no ads, no purchases, no account and no tracking. IV Lens has no internet access at all, so nothing it sees can leave your phone.

HOW IT WORKS
• Start the bubble, open a Pokémon in the game, and a small chip gives the verdict by itself.
• When several IVs fit, open the game's Appraise screen: IV Lens reads the three bars and pins the exact IVs.
• Tap the chip for the full card: IVs and level, PvP ranks in Great, Ultra and Master League for the Pokémon and its evolutions, and what each power-up costs in stardust and candy.

WHAT THE CHIP SAYS
• HUNDO: a perfect 15/15/15.
• PVP: good IVs for a league where the species, or what it evolves into, is actually used.
• RAIDS: a top attacker of its type, Shadows included.
• KEEP: good IVs, worth keeping.
• TRANSFER: nothing special.
The card adds plain steps, such as "Evolve to Gardevoir" or "Power up to Lv 40 for raids", and the moves it needs.

ALSO
• A calculator for any Pokémon, by hand.
• A saved list, with an optional Auto save, filters and export.
• Ranks: the best raid attackers per type and the top PvP picks per league, with their best IVs.
• Copy name: a short name with the IVs, to paste when renaming a Pokémon in the game.
• Light and dark themes.

PRIVACY
IV Lens captures the screen only while the bubble is on. It reads the image on the phone and never stores or sends it. Your saved Pokémon stay on your phone.

GOOD TO KNOW
• For now the game must be set to English.
• IV Lens only reads the screen. It never taps or plays for you.
• Stats come from PvPoke's game master (MIT licence).

IV Lens is a fan-made tool. It isn't affiliated with, endorsed by or connected to Niantic, Nintendo, The Pokémon Company or Game Freak. Pokémon and Pokémon GO are trademarks of their respective owners.
```

## App content (Policy › App content)

- **Privacy policy:** `https://plumcometlabs.com/ivlens/privacy` (live since 2026-10-28). It's
  word for word `privacy-policy.md`: change both together. Play wants it linked inside the app
  too: since 1.27, Settings › About › Privacy policy.
- **App access:** all functionality is available without special access. Note for the
  reviewers: "The bubble reads Pokémon GO's Pokémon screens. Without the game, the Calc, Saved and
  Ranks tabs show what the app does."
- **Ads:** no ads.
- **Content rating (IARC):** category Utility / Productivity / Other. No violence, no user
  interaction or sharing between users, no location sharing, no purchases. Expect Everyone / PEGI 3.
- **Target audience:** 13 and over. It isn't designed for children, which keeps it out of the
  Families policy. "Could it unintentionally appeal to children?" No: it's a stats tool.
- **Data safety:** no data collected, no data shared. Since 1.24 the app has no internet access
  (ML Kit's usage stats are removed with it), so nothing can be sent. The bug report is a form the
  user fills in and then copies to Discord or shares, so it isn't collection by the app.
- **Government, financial, health, news:** none.
- **Foreground service (FOREGROUND_SERVICE_MEDIA_PROJECTION):**
  - Feature: "Screen reading for IV calculation while the user plays."
  - Description: "The user starts the bubble in IV Lens and accepts Android's screen-capture
    prompt. While the bubble is on, IV Lens captures the screen to read the Pokémon's CP, HP,
    stardust cost and appraisal bars with on-device text recognition, and shows a verdict over the
    game. The capture has to keep running while the user plays in another app, so it runs as a
    foreground service with an ongoing notification. Images are processed in memory and never
    stored or sent."
  - If it's interrupted: "Nothing is lost. The verdict chip disappears, and the user starts the
    bubble again from the app or the notification."
  - Use case picked in the form: "Other".
  - Video (2026-10-29): a short screen recording: open IV Lens, Start bubble, accept Android's
    prompt, open a Pokémon in the game, the chip shows a verdict. It's unlisted on the studio's
    YouTube channel, with the catch place painted out and checked in every frame.
- **Display over other apps** (no form, but be ready to explain): the bubble and the verdict chip
  sit over the game; they're the app's core feature.

## Testing and release

- **Play App Signing:** let Google make the app-signing key; `ivlens-release.jks` becomes the
  upload key. Builds signed on the laptop then can't update a copy installed from Play (and the
  other way round): uninstall first, after an export.
- **Internal test first** (no review wait, up to 100 testers): 1.26 (versionCode 27), then 1.27,
  went up on 2026-10-29 and installed from Play on a test phone, signed with Google's key. Release
  notes (`<en-US>`): "First test build. Open a Pokémon in the game with the bubble on, and IV Lens
  gives its IVs and a verdict. Tell us what it gets wrong in #bug-reports on Discord."
  - A tester account Play doesn't recognise yet gets "Item not found" in the Play Store even after
    accepting the invite; another account that was on the list from the start worked at once.
- **1.27** (versionCode 28) adds the in-app privacy policy link: the build for the closed test.
  Release name: `1.27`. Release notes (`<en-US>`): "Settings › About now links to the privacy
  policy."
- **Closed test "Alpha"** (approved 2026-10-29): 1.27, all countries.
  - Testers: the Google Group `groups.google.com/g/ivlens-testers` (anyone can join; members are
    hidden). Opt-in link: `https://play.google.com/apps/testing/com.plumcometlabs.ivlens`.
    Feedback: Discord's #bug-reports.
  - Production needs at least **12 testers opted in for 14 days in a row** (Google's help page,
    checked 2026-10-27); someone who leaves and rejoins starts their 14 days again, so recruit
    15–20. The README's "Get it" section has the three steps; also put the opt-in link and the
    14 days in the group's welcome message and the pinned message of Discord's `#testers`
    (`docs/discord.md`). Ask a community's mods before posting there.
- **Then** apply for production on the Dashboard. It asks about the test (how testers were found,
  what they used, their feedback), the app (audience, value, first-year installs) and what changed
  because of the test, so keep notes during the 14 days.
