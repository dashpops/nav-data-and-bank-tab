#!/usr/bin/env python3
"""Reconcile every Withdraw step's TEXT against what actually resolved.

parse_withdraws only writes the items it can resolve; anything it can't is *left
out*. A dropped item therefore never shows up as an "unresolved entry" in the
written metadata -- the list is just shorter than the step text asked for. This
script closes that blind spot: it re-tokenises each Withdraw step's text with the
parser's own splitter and asks of every token, "is this covered?" -- resolvable by
an alias, a CHARGED variant-set, intentionally SKIPped, teleport-runes, or already
present by name in the current metadata. Whatever's left is a genuine gap.

    scripts/audit_withdraws.py            # list real gaps (+ prose / artifact counts)
    scripts/audit_withdraws.py --all      # also print the prose/artifact buckets

Run it after any guide.json or alias change; REAL gaps should stay ~flat (the tail
is wiki location/disambig pages, sub-bank-33 steps, and loadout prose).
"""
import json, re, sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "scripts"))
import parse_withdraws as P

GUIDE = REPO / "src/main/resources/guide.json"
META = REPO / "src/main/resources/step-metadata.json"

# Loadout descriptions the guide names that are not a single bankable item, plus a
# few late-game gear phrases -- correctly dropped, so not a bug, just not an item.
PROSE = re.compile(
    r'gear|combat|armou?r|potions?|\bfood\b|spells?|inventory of|to kill|optional|'
    r'weapon|thralls|best .*spell|robes|blessing|runes? to|full crystal|lantern lense|'
    r'random|bop|greegree|signet|memoirs|sceptre|blood moon|macahuitl|shards|vouchers|'
    r'naptha|explosive|of passage|etc', re.I)


def classify(tok):
    t = tok.strip()
    if re.fullmatch(r'\d+\w*', t) or t.lower() in {"bolts", "mortar", "pestle", "etc"}:
        return "ARTIFACT"
    if PROSE.search(t) or len(t.split()) > 3:
        return "PROSE"
    return "REAL"


def main():
    guide = json.loads(GUIDE.read_text())
    meta = json.loads(META.read_text())
    ids = P.fetch_ids(set(P.ALIASES.values()) | set(P.TELEPORT_RUNES[1]))

    buckets = {"REAL": [], "PROSE": [], "ARTIFACT": []}
    for sec in guide["sections"]:
        for s in sec["steps"]:
            if not re.match(r"^\s*Withdraw", s["text"], re.I):
                continue
            cur = meta.get(s["id"], {}).get("withdraw", [])
            for part in P.split_entries(s["text"]):
                if isinstance(part, tuple):                 # ALT ("any of these")
                    disp = part[2]
                    ok = any((lambda pg: pg and ids.get(pg))(P.resolve_page(P.parse_entry(a)[1]))
                             for a in part[1])
                    if ok or any(disp.lower() in e["name"].lower() for e in cur):
                        continue
                    tok = disp
                else:
                    name, key, qty = P.parse_entry(part)
                    if not re.search(r"[a-z]", key):        # bare numbers -> split noise
                        continue
                    if not key or key in P.DROP or key == "teleport runes" or key in P.CHARGED:
                        continue
                    pg = P.resolve_page(key)
                    if pg and ids.get(pg):
                        continue
                    if any(name.lower() == e["name"].lower() or name.lower() in e["name"].lower()
                           or e["name"].lower() in name.lower() for e in cur):
                        continue
                    tok = name
                buckets[classify(tok)].append((sec["title"], tok))

    real = buckets["REAL"]
    print(f"REAL single-item gaps: {len(real)}   "
          f"(PROSE {len(buckets['PROSE'])}, ARTIFACT {len(buckets['ARTIFACT'])})")
    from collections import defaultdict
    seen = defaultdict(list)
    for title, tok in real:
        seen[tok.lower()].append(title)
    for tok in sorted(seen):
        print(f"  {tok!r:30} x{len(seen[tok])}  e.g. {seen[tok][0]}")
    if "--all" in sys.argv:
        for name in ("PROSE", "ARTIFACT"):
            print(f"\n{name}:")
            for title, tok in buckets[name]:
                print(f"  {title}: {tok!r}")


if __name__ == "__main__":
    main()
