#!/usr/bin/env python3
"""Collects the open-source notices inside the app's libraries for Settings › About › Licences (1.24).

    python3 tools/licences.py        # from the repo root, after a build (it reads Gradle's cache)

Writes app/src/main/assets/licenses/third-party.json: {"libraries": [{"name", "text"}], "texts": [...]}.

- Google's ML Kit and Play services libraries each carry third_party_licenses.json/.txt: the
  open-source parts built into them and their licence texts. They're merged here, one copy of each
  text. Rerun after changing a dependency.
- It also checks the licence each library declares (its POM) against KNOWN, the ones the Licences
  screen covers. A new one stops it: add it to KNOWN, and to the screen, first.
"""
import json
import pathlib
import re
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1"
OUT = ROOT / "app/src/main/assets/licenses/third-party.json"

# A library's declared licence -> where the Licences screen covers it.
KNOWN = {
    "The Apache Software License, Version 2.0": "apache-2.0.txt",
    "The Apache License, Version 2.0": "apache-2.0.txt",
    "Apache-2.0": "apache-2.0.txt",
    "ML Kit Terms of Service": "named in the intro",
    "Android Software Development Kit License": "named in the intro",
}
# Guava's empty "listenablefuture" stand-in has no licence in its POM; Guava is Apache 2.0.
NO_LICENCE_OK = {"com.google.guava:listenablefuture"}
# The Apache text ends with a how-to for applying it to your own code, with "[yyyy] [name of
# copyright owner]" to fill in. It isn't part of the terms, and reads as a placeholder, so it goes.
APACHE_APPENDIX = re.compile(r"APPENDIX: How to apply the Apache License to your work\..*?limitations under the License\.\s*", re.S)


def coordinates():
    tree = subprocess.run(
        [str(ROOT / "gradlew"), "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    found = set()
    for line in tree.splitlines():
        m = re.search(r"([\w.\-]+):([\w.\-]+):([\w.\-]+)(?: -> ([\w.\-]+))?", line)
        if m:
            g, a, v, resolved = m.groups()
            found.add((g, a, resolved or v))
    return sorted(found)


def main():
    parts = {}  # component name -> its licence text, first one found
    unknown = []
    for g, a, v in coordinates():
        folder = CACHE / g / a / v
        poms = sorted(folder.glob("*/*.pom"))
        if not poms:
            sys.exit(f"{g}:{a}:{v} isn't in Gradle's cache: build the app first")
        names = set(n.strip() for n in re.findall(r"<license>\s*<name>([^<]+)</name>", poms[0].read_text(errors="ignore")))
        if not names and f"{g}:{a}" not in NO_LICENCE_OK:
            unknown.append(f"{g}:{a}:{v}: no licence in its POM")
        unknown += [f"{g}:{a}:{v}: {n}" for n in names if n not in KNOWN]
        for f in sorted(folder.glob("*/*.aar")) + sorted(folder.glob("*/*.jar")):
            with zipfile.ZipFile(f) as z:
                if "third_party_licenses.json" not in z.namelist():
                    continue
                index = json.loads(z.read("third_party_licenses.json"))
                data = z.read("third_party_licenses.txt")
                for name, r in index.items():
                    parts.setdefault(name, data[r["start"]:r["start"] + r["length"]].decode("utf-8", errors="replace"))
    if unknown:
        sys.exit("Licences the Licences screen doesn't cover yet:\n  " + "\n  ".join(unknown))

    texts, by_text, libraries = [], {}, []
    for name in sorted(parts, key=str.lower):
        text = APACHE_APPENDIX.sub("", parts[name]).strip()
        key = re.sub(r"\s+", " ", text)
        if key not in by_text:
            by_text[key] = len(texts)
            texts.append(text)
        libraries.append({"name": name, "text": by_text[key]})
    OUT.write_text(json.dumps({"libraries": libraries, "texts": texts}, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(libraries)} parts, {len(texts)} texts, {OUT.stat().st_size // 1024} KB -> {OUT.relative_to(ROOT)}")


main()
