import json, re, sys
gm_path, pokemon_js, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
pm_path = sys.argv[4] if len(sys.argv) > 4 else None
# Folder with PvPoke's rankings-1500/2500/10000.json (src/data/rankings/all/overall/).
rank_dir = sys.argv[5] if len(sys.argv) > 5 else None
g = json.load(open(gm_path))
js = open(pokemon_js).read()
cpms = [float(x) for x in re.search(r"var cpms = \[([^\]]+)\]", js).group(1).split(",")]
assert abs(cpms[98] - 0.8403) < 1e-3, "level 50 cpm check"
cpms = cpms[:101]  # levels 1..51 in half steps

def base_name(n):
    return re.sub(r"\s*\(.*\)$", "", n).strip()

# PvPoke still marks these unreleased, but the owner has one in the game. Minior (CP 296, HP 46,
# 600 stardust; 2026-10-20) fits the Core form's stats and not the Meteor form's. Ditto (CP 668,
# HP 105, 5,000 stardust; 2026-10-24) can't battle normally (Transform), so PvPoke leaves it out.
SEEN_IN_GAME = {"minior_core", "ditto"}

kept = []
by_key = {}
alias = {}
extra_evo = {}
extra_moves = {}
for s in g["pokemon"]:
    tags = s.get("tags", [])
    if "shadow" in tags or "mega" in tags or "supermega" in tags:
        continue
    if s.get("released") is False and s["speciesId"] not in SEEN_IN_GAME:
        continue
    sid = s["speciesId"]
    if sid.endswith("_shadow") or "_mega" in sid:
        continue
    b = s["baseStats"]
    # Costume and event copies share name, stats and types, and are merged into one entry.
    # Regional forms can share name and stats but never types (Sneasel and Hisuian Sneasel),
    # so the types are part of the key: merging those gave Sneasel Sneasler's evolution.
    types = tuple(sorted(t for t in s.get("types", []) if t != "none"))
    key = (base_name(s["speciesName"]), b["atk"], b["def"], b["hp"], types)
    if key in by_key:
        alias[sid] = by_key[key]
        extra_evo.setdefault(by_key[key], []).extend((s.get("family", {}).get("evolutions") or []))
        extra_moves.setdefault(by_key[key], []).append(s)
        continue
    by_key[key] = sid
    kept.append(s)

ids = {s["speciesId"] for s in kept}
def resolve(i):
    i = alias.get(i, i)
    return i if i in ids else None

def merged(sp, key):
    seen = []
    for src in [sp] + extra_moves.get(sp["speciesId"], []):
        for m in src.get(key) or []:
            if m not in seen:
                seen.append(m)
    return seen

out = []
for s in kept:
    fam = s.get("family", {})
    evo = []
    for e in (fam.get("evolutions", []) or []) + extra_evo.get(s["speciesId"], []):
        r = resolve(e)
        if r and r not in evo and r != s["speciesId"]:
            evo.append(r)
    b = s["baseStats"]
    out.append({
        "id": s["speciesId"], "name": s["speciesName"], "base": base_name(s["speciesName"]),
        "dex": s["dex"], "a": b["atk"], "d": b["def"], "s": b["hp"],
        "fam": fam.get("id", "FAMILY_" + s["speciesId"].upper()), "evo": evo,
        "types": [t for t in s.get("types", []) if t != "none"],
        **({"rare": True} if set(s.get("tags", [])) & {"legendary", "mythical", "ultrabeast"} else {}),
        **({"sh": True} if "shadoweligible" in s.get("tags", []) else {}),
        "fm": merged(s, "fastMoves"),
        "cm": merged(s, "chargedMoves"),
        **({"el": merged(s, "eliteMoves")} if merged(s, "eliteMoves") else {}),
    })
out.sort(key=lambda x: (x["dex"], x["id"]))

# Where PvPoke ranks each species in each league, best first (1.16): PVP needs the species to be
# used there, not only good IVs. "pvp" is the plain form, "pvps" its Shadow.
if rank_dir:
    by_id = {x["id"]: x for x in out}
    unmatched = []
    for short, cap in (("GL", 1500), ("UL", 2500), ("ML", 10000)):
        for rank, e in enumerate(json.load(open(f"{rank_dir}/rankings-{cap}.json")), 1):
            rid = e["speciesId"]
            shadow = rid.endswith("_shadow")
            sid = resolve(rid[: -len("_shadow")] if shadow else rid)
            if sid is None:
                unmatched.append(rid)
                continue
            ranks = by_id[sid].setdefault("pvps" if shadow else "pvp", {})
            ranks[short] = min(ranks.get(short, rank), rank)
    print("league ranks added;", len(unmatched), "ranked ids not in the data:", unmatched[:20])
pve = {}
if pm_path:
    for e in json.load(open(pm_path)):
        ms = e.get("data", {}).get("moveSettings")
        if ms:
            pve[str(ms.get("movementId"))] = ms
pve_norm = {k.replace("_", ""): v for k, v in pve.items()}
moves = []
missing = []
# A fast move gains energy, except one that is only ever a fast move: Ditto's Transform gains none,
# and was filed as charged (1.19).
fast_only = {m for s in g["pokemon"] for m in s.get("fastMoves") or []} - \
    {m for s in g["pokemon"] for m in s.get("chargedMoves") or []}
for m in g["moves"]:
    fast = (m.get("energyGain") or 0) > 0 or m["moveId"] in fast_only
    entry = {"id": m["moveId"], "name": m["name"], "type": m["type"], "fast": fast}
    mid = m["moveId"]
    keys = [mid + ("_FAST" if fast else ""), mid, mid.replace("_", "") + ("_FAST" if fast else "")]
    if mid.startswith("HIDDEN_POWER"):
        keys.insert(0, "HIDDEN_POWER_FAST")
    ms = next((pve_norm[k.replace("_", "")] for k in keys if k.replace("_", "") in pve_norm), None)
    if ms:
        entry.update({"p": ms.get("power", 0), "ms": ms.get("durationMs", 1000), "e": ms.get("energyDelta", 0)})
    else:
        missing.append(mid)
    moves.append(entry)
print(len(moves), "moves;", len(missing), "without raid stats:", missing)
json.dump({"source": "PvPoke gamemaster " + str(g.get("timestamp", "")), "cpm": cpms, "species": out, "moves": moves},
          open(out_path, "w"), separators=(",", ":"), ensure_ascii=False)
print(len(out), "species;", len(alias), "duplicate forms collapsed; max dex", max(x["dex"] for x in out))
