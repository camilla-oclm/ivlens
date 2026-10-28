# Icon candidates

**Studio:** Plum Comet Labs (picked 2026-10-25; generic, for later apps too). Its icon idea in
the Bold Block style: a plum-purple comet with a flat tail and a black 2 dp outline. Not drawn
yet.

Five original icons drawn for the name options (see the "IV Lens icon & name options" design
canvas). None uses game artwork, the Poké Ball or official lettering.

| File | Name option | Idea |
|---|---|---|
| `ivlens-lens.svg` | IV Lens | Lens with three stars |
| `ivlens-arc.svg` | IV Lens (alt) | Level arc and dot |
| `statspark.svg` | StatSpark | Three stat bars and a sparkle |
| `hundohunt.svg` | Hundo Hunt | Award medal reading 100 |
| `ivy.svg` | IVy | Sprout whose leaves are stat bars |

`*-themed.svg` are one-colour versions for Android themed icons.

**Picked: `ivlens-arc.svg`** (icon B; the owner, 2026-10-27). Since 1.24 it's the app icon, made by
`tools/make_icons.py`, which does the steps below. `ivlens-arc-final.svg` is the result with "IV"
as a path (the source of Play's `docs/play/icon-512.png`), and `feature-graphic.svg` is the source
of Play's feature graphic.

## Turning the chosen one into the app icon

- The SVGs use Android's adaptive-icon grid: a 108×108 canvas, of which the middle 72×72 shows
  (`viewBox="18 18 72 72"`). The key shapes stay inside the 66-unit safe circle.
- **Background layer:** the first `<rect>`, a solid colour, goes to `@color/ic_launcher_background`.
- **Foreground layer:** everything else, converted to a vector drawable
  (`res/drawable/ic_launcher_foreground.xml`) with a 108×108 viewport.
- **Monochrome layer:** the themed version's outlines, as `ic_launcher_monochrome.xml`.
- **Text:** "IV" and "100" use the Archivo font. Convert them to paths first, since vector
  drawables can't hold text (Inkscape "Object to Path", or fontTools).
- **Shadows:** the hard shadows are plain shapes offset by 2.5–3 units, so they convert
  directly.
- **Play Store icon:** export at 512×512 PNG (full square; Play applies its own mask).
- Also update the bubble (`ic_bubble_lens`) and notification icon (`ic_stat_lens`) to match.
