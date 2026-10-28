# IV Lens

A light companion for Pokémon GO on Android. A floating bubble reads the Pokémon on your screen and
gives its IVs, PvP ranks, power-up costs and a straight verdict: **HUNDO, PVP, RAIDS, KEEP or
TRANSFER**. Everything runs on the phone.

IV Lens is free, with no ads, no purchases, no account and no tracking. It has no internet access at
all, so nothing it sees can leave your phone. Made by [Plum Comet Labs](https://plumcometlabs.com).

## What it does

- **Auto-scan chip.** Open a Pokémon and a small chip gives the verdict by itself: HUNDO, PVP (with
  league), RAIDS (with type rank), KEEP or TRANSFER. It waits for the moves (MOVES? · SCROLL DOWN)
  when the answer depends on them.
- **Appraise screen.** When several IVs fit (the chip says "Appraise for exact IVs"), opening the
  game's Appraise screen pins the exact IVs from its three bars.
- **Bubble scan.** Uses Android screen capture. On-device text recognition (ML Kit, bundled model)
  reads CP, HP, name, candy family and the POWER UP stardust cost.
- **Level arc.** Reads the white dot on the arc above the Pokémon to pin the level, usually to one
  half-level. It's only used when it agrees with CP, HP and stardust.
- **Result card.** IV range or exact IVs, PvP ranks (Great, Ultra, Master) for the Pokémon and each
  evolution, and power-up targets with stardust, candy and XL costs.
  - A built-in number pad for a CP hidden behind the Pokémon.
  - Shadow, Purified and Lucky toggles.
  - Tap a possible IV combo to see its exact ranks.
  - **Copy name** puts a short name with the exact IVs ("Torchic71⑦⑭⑪") on the clipboard, to paste
    when renaming it in the game.
  - "Wrong read? Report it" opens a text bug report.
- **Calculator and saved list** inside the app. With **Auto save** on (Settings), each Pokémon the
  chip reads is saved by itself, once per Pokémon. The saved list filters by verdict, and exports to
  a file.
- **Ranks.** The best raid attackers per type and PvPoke's top 50 per league, with the best PvP IVs.
- **Light or dark,** following the phone or set in Settings.

## Privacy

- The screen is captured only while the bubble is on, after Android asks you to allow it. Each image
  is read on the phone and never saved or sent.
- The app has no internet permission. ML Kit's usage reporting is removed from the build.
- Saved Pokémon and settings stay in the app's storage on your phone.
- The full policy: [plumcometlabs.com/ivlens/privacy](https://plumcometlabs.com/ivlens/privacy).

## Good to know

- The game must be set to English for now: the reader looks for English words and names.
- IV Lens only reads the screen. It never taps or plays for you.
- Some Pokémon cover their own CP on the page. Then the chip says CP?: the Appraise bars or the
  number pad fill it in.

## Building

Requirements: JDK 17+ and the Android SDK with platform 36 and build tools 36. Gradle 8.14.3 comes
through `./gradlew` (its checksum is pinned), with AGP 8.13 and Kotlin 2.2.

```
./gradlew :app:testReleaseUnitTest   # logic tests
./gradlew :app:assembleRelease       # release APK (signed if keystore.properties is present)
./gradlew :app:bundleRelease         # .aab for Google Play
```

- Without `keystore.properties` the release build is unsigned; sign it with your own key to install it.
- Some tests read real screenshots of the game. Those aren't in the repo, since they show game
  artwork; the tests that need them are skipped.
- `CLAUDE.md` explains how the app works and why each detection rule is the way it is.

## Layout

- `core/`: game data, IV maths, PvP ranks, raid ranking, power-up costs and the verdict rules. Pure
  Kotlin, unit-tested.
- `scan/`: the screen text parser, species and move matching, the level-arc and Appraise-bar readers,
  and the scan resolver. Pure Kotlin, unit-tested.
- `service/`: screen capture, ML Kit and the overlay service (bubble, chip and card).
- `ui/`: Compose screens and the overlay card, in the "Bold Block" style (`ui/Block.kt`).
- `store/`: settings and the saved list.
- `tools/`: `build_data.py` (game data), `licences.py` (the Licences screen), `make_icons.py` (icons
  and Play graphics), `make_fixture.py` (test fixtures from a screenshot) and `privacy_scan.py`.

## Licence and credits

- IV Lens's code is under the [MIT licence](LICENSE).
- Stats, movesets and league rankings come from [PvPoke](https://github.com/pvpoke/pvpoke) (MIT); raid
  move stats from the PokeMiners game master. `app/src/main/assets/pokedata.json` is built from them
  by `tools/build_data.py`.
- The Archivo font is under the SIL Open Font License. The app's Settings › About › Licences lists
  every notice, including the open-source parts inside ML Kit and Google Play services.

## Feedback

Bugs and ideas are welcome on the [Plum Comet Labs Discord](https://discord.gg/VvvbhuxmfF): post in
#bug-reports or #ideas.

IV Lens is a fan-made tool. It isn't affiliated with, endorsed by or connected to Niantic, Nintendo,
The Pokémon Company or Game Freak. Pokémon and Pokémon GO are trademarks of their respective owners.
