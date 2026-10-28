#!/usr/bin/env python3
"""Makes the app's icons from icon B, docs/brand/ivlens-arc.svg (the owner's pick, 2026-10-27).

    python3 tools/make_icons.py            # from the repo root; needs fontTools, and rsvg-convert for the PNGs

Writes:
  app/src/main/res/drawable/ic_launcher_foreground.xml   colour layer (the background is @color/icon_bg)
  app/src/main/res/drawable/ic_launcher_monochrome.xml   themed icons (Android 13+ uses only its alpha)
  app/src/main/res/drawable/ic_bubble.xml                the floating bubble (tinted black on yellow)
  app/src/main/res/drawable/ic_stat.xml                  the notification icon (white; a bolder drawing)
  docs/brand/ivlens-arc-final.svg, docs/play/icon-512.png   Play's 512×512 icon (full square; Play masks it)
  docs/brand/feature-graphic.svg, docs/play/feature-graphic.png   Play's 1024×500 feature graphic

Vector drawables can't hold text, so "IV" becomes a path from Archivo at weight 900. The one-colour
versions can't draw the dot's dark ring, so their arcs stop short of the dot instead.
"""
import math
import pathlib
import shutil
import subprocess

from fontTools.pens.basePen import BasePen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res/drawable"

# Geometry of ivlens-arc.svg, on the adaptive icon's 108×108 canvas.
CX, CY, R = 54.0, 64.0, 26.0            # the arc's circle; it runs from 180° (left) over the top to 360°
DOT = (67.0, 41.5)                      # on the arc; radius 6, ringed by a 2-wide stroke in the background colour
DOT_R, RING = 6.0, 2.0
TRACK_W, FILL_W = 4.0, 4.5
BG, YELLOW, TRACK_ALPHA = "#111111", "#FFD400", 0.32


def num(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def point(deg):
    a = math.radians(deg)
    return CX + R * math.cos(a), CY + R * math.sin(a)


def arc(a0, a1):
    """SVG path of the arc from angle a0 to a1 (degrees, y down, clockwise on screen)."""
    (x0, y0), (x1, y1) = point(a0), point(a1)
    large = 1 if a1 - a0 > 180 else 0
    return f"M{num(x0)},{num(y0)} A{num(R)},{num(R)} 0 {large},1 {num(x1)},{num(y1)}"


def circle(cx, cy, r):
    return f"M{num(cx - r)},{num(cy)} a{num(r)},{num(r)} 0 1,0 {num(2 * r)},0 a{num(r)},{num(r)} 0 1,0 {num(-2 * r)},0"


class PathPen(BasePen):
    """Glyph outlines as path data with every command spelt out (no implied line after a move)."""

    def __init__(self, glyphs):
        super().__init__(glyphs)
        self.d = []

    def _moveTo(self, p):
        self.d.append(f"M{num(p[0])},{num(p[1])}")

    def _lineTo(self, p):
        self.d.append(f"L{num(p[0])},{num(p[1])}")

    def _qCurveToOne(self, p1, p2):
        self.d.append(f"Q{num(p1[0])},{num(p1[1])} {num(p2[0])},{num(p2[1])}")

    def _curveToOne(self, p1, p2, p3):
        self.d.append(f"C{num(p1[0])},{num(p1[1])} {num(p2[0])},{num(p2[1])} {num(p3[0])},{num(p3[1])}")

    def _closePath(self):
        self.d.append("Z")


_fonts = {}


def text(s, size, x, baseline, weight=900, anchor="middle"):
    """[s] in Archivo at [weight] as path data (no kerning), and its width. [anchor]: start or middle."""
    if weight not in _fonts:
        _fonts[weight] = instantiateVariableFont(TTFont(ROOT / "app/src/main/res/font/archivo.ttf"), {"wght": weight, "wdth": 100})
    font = _fonts[weight]
    glyphs, cmap, hmtx = font.getGlyphSet(), font.getBestCmap(), font["hmtx"]
    scale = size / font["head"].unitsPerEm
    names = [cmap[ord(c)] for c in s]
    width = sum(hmtx[n][0] for n in names) * scale
    if anchor == "middle":
        x -= width / 2
    pen = PathPen(glyphs)
    for n in names:
        glyphs[n].draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
        x += hmtx[n][0] * scale
    return "".join(pen.d), width


DOT_DEG = math.degrees(math.atan2(DOT[1] - CY, DOT[0] - CX)) % 360


def trim(width, outer=DOT_R + RING / 2):
    """Degrees to stop an arc of [width] short of the dot, so its round cap clears the ring ([outer])."""
    clear = outer + width / 2
    return math.degrees(2 * math.asin(clear / (2 * R)))


IV = text("IV", 20, CX, 70)[0]  # as in the SVG: size 20, centred, baseline at y 70
COLOUR = [  # (path, fill, stroke, stroke width)
    (arc(180, 360), None, TRACK_ALPHA, TRACK_W),
    (arc(180, DOT_DEG), None, "#FFFFFF", FILL_W),
    (circle(*DOT, DOT_R), YELLOW, BG, RING),
    (IV, "#FFFFFF", None, 0),
]
ONE_COLOUR = [  # the visible yellow of the dot is DOT_R - RING/2 wide
    (arc(DOT_DEG + trim(TRACK_W), 360), None, TRACK_ALPHA, TRACK_W),
    (arc(180, DOT_DEG - trim(FILL_W)), None, 1.0, FILL_W),
    (circle(*DOT, DOT_R - RING / 2), 1.0, None, 0),
    (IV, 1.0, None, 0),
]
# The notification icon is drawn at 24 dp, where those strokes come out under 1.5 dp and the faint
# track vanishes (the owner, 1.24: "barely visible"). So it's bolder: a thicker arc, a bigger dot,
# a larger "IV" and no track.
STAT_W, STAT_DOT = 7.0, 6.5
STAT = [
    (arc(180, DOT_DEG - trim(STAT_W, STAT_DOT + RING)), None, 1.0, STAT_W),
    (circle(*DOT, STAT_DOT), 1.0, None, 0),
    (text("IV", 25, CX, 72)[0], 1.0, None, 0),
]


def android_colour(c):
    if isinstance(c, float):  # white at that alpha
        return f"#{round(c * 255):02X}FFFFFF"
    return c


def vector(shapes, size_dp, box=(0, 0, 108, 108)):
    x0, y0, w, h = box
    out = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Made by tools/make_icons.py from docs/brand/ivlens-arc.svg; edit that, not this. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{size_dp}dp" android:height="{size_dp}dp"',
        f'    android:viewportWidth="{num(w)}" android:viewportHeight="{num(h)}">',
        f'    <group android:translateX="{num(-x0)}" android:translateY="{num(-y0)}">',
    ]
    for d, fill, stroke, sw in shapes:
        attrs = [f'android:pathData="{d}"']
        attrs.append(f'android:fillColor="{android_colour(fill)}"' if fill is not None else 'android:fillColor="#00000000"')
        if stroke is not None:
            attrs += [f'android:strokeColor="{android_colour(stroke)}"', f'android:strokeWidth="{num(sw)}"', 'android:strokeLineCap="round"']
        out.append("        <path " + "\n            ".join(attrs) + " />")
    out += ["    </group>", "</vector>", ""]
    return "\n".join(out)


def svg_paths(shapes):
    def colour(c):
        return f'rgba(255,255,255,{c})' if isinstance(c, float) else c

    out = []
    for d, fill, stroke, sw in shapes:
        s = f' stroke="{colour(stroke)}" stroke-width="{num(sw)}" stroke-linecap="round"' if stroke is not None else ""
        out.append(f'<path d="{d}" fill="{colour(fill) if fill is not None else "none"}"{s}/>')
    return "\n".join(out)


def svg(shapes, bg):
    return "\n".join(['<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">',
                      f'<rect x="0" y="0" width="108" height="108" fill="{bg}"/>', svg_paths(shapes), "</svg>", ""])


INK = "#111111"
VERDICTS = [("HUNDO", YELLOW, INK), ("PVP", "#3D5AFE", "#FFFFFF"), ("RAIDS", "#FF7A00", INK), ("KEEP", "#00C2A8", INK), ("TRANSFER", "#FF4D4D", INK)]


def block(x, y, w, h, fill, rx, outline=4, shadow=6):
    """A Bold Block shape: hard black shadow down and right, flat fill, black outline."""
    return (f'<rect x="{num(x + shadow)}" y="{num(y + shadow)}" width="{num(w)}" height="{num(h)}" rx="{rx}" fill="{INK}"/>'
            f'<rect x="{num(x)}" y="{num(y)}" width="{num(w)}" height="{num(h)}" rx="{rx}" fill="{fill}" stroke="{INK}" stroke-width="{outline}"/>')


def feature_graphic():
    """Play's 1024×500 feature graphic: the icon, the name, one line and the verdict words. No game art."""
    w, h = 1024, 500
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">',
           f'<rect width="{w}" height="{h}" fill="{YELLOW}"/>']
    tx, ty, ts, rx = 64, 112, 264, 58
    out.append(block(tx, ty, ts, ts, BG, rx, 6, 12))
    out.append(f'<g transform="translate({tx},{ty}) scale({num(ts / 72)}) translate(-18,-18)">{svg_paths(COLOUR)}</g>')
    x0 = tx + ts + 40
    for s, size, weight, baseline in [("IV Lens", 112, 900, 200), ("IVs, PvP ranks and raid value", 32, 700, 254), ("in one tap.", 32, 700, 294)]:
        out.append(f'<path d="{text(s, size, x0, baseline, weight, "start")[0]}" fill="{INK}"/>')
    words = [(t, fill, ink, text(t, 23, 0, 0)[1]) for t, fill, ink in VERDICTS]
    pad, gap = 13, 11
    x = x0
    for t, fill, ink, tw in words:
        bw = tw + 2 * pad
        out.append(block(x, 336, bw, 50, fill, 8))
        out.append(f'<path d="{text(t, 23, x + bw / 2, 369)[0]}" fill="{ink}"/>')
        x += bw + gap
    assert x - gap + 6 < w - 24, f"verdict row too wide: ends at {x - gap + 6}"
    return "\n".join(out + ["</svg>", ""])


# The bubble and notification icons show only the drawing, cropped to a square around it: the
# bubble's spans x 25.75–82.25, y 35.75–70; the notification one x 24.5–73.5, y 34.5–72, with
# 1 dp to spare at 24 dp.
CROP = (24.0, 22.5, 60.0, 60.0)
CROP_STAT = (22.25, 26.5, 53.5, 53.5)

if __name__ == "__main__":
    (RES / "ic_launcher_foreground.xml").write_text(vector(COLOUR, 108))
    (RES / "ic_launcher_monochrome.xml").write_text(vector(ONE_COLOUR, 108))
    (RES / "ic_bubble.xml").write_text(vector(ONE_COLOUR, 34, CROP))
    (RES / "ic_stat.xml").write_text(vector(STAT, 24, CROP_STAT))
    final = ROOT / "docs/brand/ivlens-arc-final.svg"
    final.write_text(svg(COLOUR, BG))
    graphic = ROOT / "docs/brand/feature-graphic.svg"
    graphic.write_text(feature_graphic())
    if shutil.which("rsvg-convert"):
        play = ROOT / "docs/play"
        play.mkdir(exist_ok=True)
        subprocess.run(["rsvg-convert", "-w", "512", "-h", "512", "-b", BG, str(final), "-o", str(play / "icon-512.png")], check=True)
        # Play wants the feature graphic without an alpha channel.
        subprocess.run(["rsvg-convert", "-b", YELLOW, str(graphic), "-o", str(play / "feature-graphic.png")], check=True)
        if shutil.which("magick"):
            subprocess.run(["magick", str(play / "feature-graphic.png"), "-alpha", "off", "PNG24:" + str(play / "feature-graphic.png")], check=True)
    print(f"dot at {DOT_DEG:.2f}°, trims {trim(FILL_W):.2f}° / {trim(TRACK_W):.2f}°")
