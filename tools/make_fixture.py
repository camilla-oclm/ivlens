"""Turn a phone screenshot into test fixtures.

    python3 tools/make_fixture.py screenshot.png name [--full]

Writes app/src/test/resources/screens/<name>.rgb.gz (gzipped raw RGB; header is three big-endian
ints: width, height, rows stored; top half by default, the whole screen with --full) and, when
Tesseract is installed, app/src/test/resources/ocr/<name>.tess.json (OCR lines with boxes).
Tesseract is rougher than ML Kit: add any missing lines by hand in the test, with a comment.
Needs Pillow (pip install pillow).
"""
import csv, gzip, io, json, shutil, struct, subprocess, sys
from pathlib import Path
from PIL import Image

src, name = sys.argv[1], sys.argv[2]
full = "--full" in sys.argv
root = Path(__file__).resolve().parent.parent / "app/src/test/resources"
im = Image.open(src).convert("RGB")
w, h = im.size
rows = h if full else h // 2
with gzip.open(root / "screens" / f"{name}.rgb.gz", "wb") as f:
    f.write(struct.pack(">iii", w, h, rows))
    f.write(im.crop((0, 0, w, rows)).tobytes())
print(f"screens/{name}.rgb.gz: {w}x{h}, {rows} rows")

if shutil.which("tesseract") is None:
    print("tesseract not found; skipped OCR fixture")
    sys.exit(0)
tsv = subprocess.run(["tesseract", src, "-", "--psm", "11", "tsv"], capture_output=True, text=True).stdout
groups = {}
for r in csv.DictReader(io.StringIO(tsv), delimiter="\t", quoting=csv.QUOTE_NONE):
    t = (r.get("text") or "").strip()
    if not t or float(r["conf"]) < 0:
        continue
    groups.setdefault((r["block_num"], r["par_num"], r["line_num"]), []).append(r)
lines = []
for ws in groups.values():
    lines.append({
        "text": " ".join(x["text"] for x in ws),
        "l": min(int(x["left"]) for x in ws),
        "t": min(int(x["top"]) for x in ws),
        "r": max(int(x["left"]) + int(x["width"]) for x in ws),
        "b": max(int(x["top"]) + int(x["height"]) for x in ws),
    })
lines.sort(key=lambda x: (x["t"], x["l"]))
(root / "ocr" / f"{name}.tess.json").write_text(json.dumps({"w": w, "h": h, "lines": lines}, indent=0))
print(f"ocr/{name}.tess.json: {len(lines)} lines")
