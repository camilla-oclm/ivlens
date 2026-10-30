#!/usr/bin/env python3
"""Checks the repo for private data before it goes public: every file in every commit on every
branch, plus the commit messages, since a public repo shows its whole history.

Usage: python3 tools/privacy_scan.py [another repo]
Prints each finding and exits 1, or prints "clean" and exits 0.

What it looks for: email addresses, IP addresses, home-folder paths, keys, tokens, passwords and
webhooks, catch-place lines from the game that aren't placeholders, and files that must never be
committed (the signing key, local settings, the handoff, test screenshots).

Personal words (names, machine names, devices) go in `.privacy-terms` next to this repo's README,
one regular expression per line (# starts a comment). That file is git-ignored, so the words it
guards never land in the repo themselves.
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else HERE

CHECKS = {
    "email": re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
    "ip address": re.compile(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])"),
    "home path": re.compile(r"/home/\w+|/Users/\w+|[A-Z]:\\Users\\|~/[A-Za-z][\w./-]*"),
    "key or token": re.compile(
        r"AIza[0-9A-Za-z_-]{35}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_\w{20,}|sk-[A-Za-z0-9_-]{20,}"
        r"|xox[abprs]-[\w-]+|AKIA[0-9A-Z]{16}|BEGIN [A-Z ]*PRIVATE KEY"),
    # A written-in value: quoted, or the rest of a properties-style line. Code that reads one isn't.
    "password": re.compile(r"(?im)(store|key)?passw(or)?d\s*[=:]\s*([\"'][^\"'\s]{3,}[\"']|[^\s\"'({]{3,}$)"),
    "webhook": re.compile(r"(?i)discord(app)?\.com/api/webhooks/\S+|hooks\.slack\.com/\S+"),
    "catch place": re.compile(r"(?i)\baround\s+(?!Hometown|<place>|…|\.\.\.)[A-Z][\w' -]+,\s*[A-Z]"),
    # Assistant co-author and session lines in commit messages (the owner wants none in public).
    # The brackets keep this line from matching itself.
    "ai session line": re.compile(r"(?i)Co-Authored-By:\s*Cl[a]ude|Claude-Sess[i]on:|claude\.ai/code/sess[i]on_"),
}

# Text that may hold these on purpose: the licence notices name their authors (so their emails and
# names aren't checked), git's own noreply addresses sign the commits, and the studio's address is
# the public contact (privacy policy, Play listing).
ALLOW = {
    "email": re.compile(r"users\.noreply\.github\.com|^info@plumcometlabs\.com$"),
    "ip address": re.compile(r"^(127\.0\.0\.1|0\.0\.0\.0)$"),
}
LICENCE_FILES = re.compile(r"(^|/)assets/licenses/|(^|/)LICENSE$")

NEVER_COMMIT = re.compile(
    r"(\.jks|\.keystore|\.p12|\.pem)$|(^|/)(keystore|local)\.properties$|(^|/)dropoff\.md$"
    r"|(^|/)\.privacy-terms$|(^|/)\.env$|test/resources/screens/")


def git(*args, inp=None):
    return subprocess.run(["git", "-C", str(ROOT), *args], input=inp, capture_output=True, check=True).stdout


def personal_terms():
    f = HERE / ".privacy-terms"
    if not f.exists():
        print("note: no .privacy-terms file, so personal words aren't checked")
        return []
    lines = [ln.strip() for ln in f.read_text().splitlines()]
    return [re.compile(ln, re.IGNORECASE) for ln in lines if ln and not ln.startswith("#")]


def history_blobs():
    """Each distinct file version reachable from any branch or tag, with one of its paths."""
    seen, pairs = set(), []
    for line in git("rev-list", "--all", "--objects").decode().splitlines():
        sha, _, path = line.partition(" ")
        if path and sha not in seen:
            seen.add(sha)
            pairs.append((sha, path))
    if not pairs:
        return
    out = git("cat-file", "--batch", inp="".join(f"{s}\n" for s, _ in pairs).encode())
    pos = 0
    for _, path in pairs:
        end = out.index(b"\n", pos)
        _, kind, size = out[pos:end].split()
        data = out[end + 1:end + 1 + int(size)]
        pos = end + 1 + int(size) + 1
        if kind == b"blob":
            yield path, data


def scan_text(where, text, terms, findings, is_licence=False):
    for name, rx in CHECKS.items():
        if name == "email" and is_licence:
            continue
        for m in rx.finditer(text):
            hit = m.group(0)
            if name in ALLOW and ALLOW[name].search(hit):
                continue
            findings.add((name, where, hit[:80]))
    for rx in [] if is_licence else terms:
        for m in rx.finditer(text):
            findings.add(("personal word", where, m.group(0)[:80]))


def main():
    terms = personal_terms()
    findings = set()

    tracked = git("ls-files").decode().splitlines()
    files = [(p, (ROOT / p).read_bytes()) for p in tracked if (ROOT / p).is_file()]
    sources = [("now", files), ("history", list(history_blobs()))]
    for label, items in sources:
        for path, data in items:
            where = f"{label}: {path}"
            if NEVER_COMMIT.search(path):
                findings.add(("must not be committed", where, path))
            if data[:2] == b"\x1f\x8b" and data[3] & 8:
                name = data[10:data.index(b"\0", 10)].decode("latin-1")
                if "/" in name or "\\" in name:
                    findings.add(("path in gzip header", where, name))
            if b"\0" in data[:8000]:
                continue
            scan_text(where, data.decode("utf-8", "replace"), terms, findings, bool(LICENCE_FILES.search(path)))

    has_commits = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "-q", "--verify", "HEAD"],
                                 capture_output=True).returncode == 0
    log = git("log", "--all", "--format=%H%x00%an <%ae>%x00%cn <%ce>%x00%B%x01").decode() if has_commits else ""
    for entry in filter(str.strip, log.split("\x01")):
        sha, author, committer, body = entry.strip().split("\x00", 3)
        scan_text(f"commit {sha[:9]}", "\n".join([author, committer, body]), terms, findings)

    for name, where, hit in sorted(findings):
        print(f"{name:22} {where}: {hit}")
    if findings:
        print(f"\n{len(findings)} finding(s). Fix them before the repo goes public.")
        return 1
    print("clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
