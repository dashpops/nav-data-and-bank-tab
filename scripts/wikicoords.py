#!/usr/bin/env python3
"""Look up map-pin coordinates for OSRS wiki pages, in batches.

    scripts/wikicoords.py "Ali Morrisane" "Nardah bank"
    scripts/wikicoords.py --near=2852,2955 "Jungle forester"   # sort by distance

Prints every coordinate candidate per page so a human picks the right one, and
says which template each came from.

Why it is fussier than it looks - each of these silently produced a wrong
"this page has no data" answer at some point:
  * Pins live in several templates: {{Map}}, {{NPC map}}, {{Scene map}} ...
    so match any template whose name contains "map".
  * Coordinates come as x=N|y=N, positional N:N or N,N, and pin lists
    x:2870,y:3115 - handle all three.
  * `r=` is a radius and `height=`/`width=` are display size. Neither is a plane.
  * Nested templates inside a field (e.g. {{FloorNumber}} on upstairs entries)
    break naive parsing, which is why templates are brace-matched.
  * On a title miss, fall back to the search API rather than giving up
    (Haig Halen is "Curator Haig Halen").

Check the plane on anything indoors. Quest Helper's source is a good second
source when the wiki has no pin at all.
"""
import json
import re
import sys
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = {"User-Agent": "hcim-guide-coords/1.0 (personal plugin project)"}


def api(params):
    params = dict(params, format="json", formatversion="2")
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers=UA)
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def fetch(titles):
    out = {}
    for i in range(0, len(titles), 20):
        chunk = titles[i:i + 20]
        data = api({"action": "query", "prop": "revisions", "rvprop": "content",
                    "rvslots": "main", "titles": "|".join(chunk), "redirects": "1"})
        q = data.get("query", {})
        alias = {}
        for kind in ("normalized", "redirects"):
            for r in q.get(kind, []):
                alias[r["from"]] = r["to"]
        by_title = {}
        for page in q.get("pages", []):
            if page.get("missing"):
                by_title[page["title"]] = None
                continue
            try:
                by_title[page["title"]] = page["revisions"][0]["slots"]["main"]["content"]
            except (KeyError, IndexError):
                by_title[page["title"]] = None
        for t in chunk:
            resolved, seen = t, set()
            while resolved in alias and resolved not in seen:
                seen.add(resolved)
                resolved = alias[resolved]
            out[t] = by_title.get(resolved)
    return out


def search(term, limit=4):
    data = api({"action": "query", "list": "search", "srsearch": term, "srlimit": limit})
    return [r["title"] for r in data.get("query", {}).get("search", [])]


def templates(text):
    """(name, body) for every {{template}}, brace-matched."""
    i = 0
    while True:
        i = text.find("{{", i)
        if i < 0:
            return
        depth, j = 0, i
        while j < len(text):
            if text.startswith("{{", j):
                depth += 1
                j += 2
                continue
            if text.startswith("}}", j):
                depth -= 1
                j += 2
                if depth == 0:
                    break
                continue
            j += 1
        head, _, body = text[i + 2:j - 2].partition("|")
        yield head.strip(), body
        i += 2


def coords(text):
    found = []
    for tname, body in templates(text):
        if "map" not in tname.lower():
            continue
        body = re.sub(r"\{\{.*?\}\}", "", body, flags=re.S)
        label = re.search(r"name\s*=\s*([^|\n}]+)", body)
        label = label.group(1).strip() if label else ""
        plane = re.search(r"(?:plane|z)\s*=\s*(\d+)", body)
        z = int(plane.group(1)) if plane else 0
        xm = re.search(r"\bx\s*=\s*(\d{3,5})", body)
        ym = re.search(r"\by\s*=\s*(\d{3,5})", body)
        if xm and ym:
            found.append((int(xm.group(1)), int(ym.group(1)), z, f"{{{{{tname}}}}} {label}".strip()))
        for pm in re.finditer(r"x\s*:\s*(\d{3,5})\s*,\s*y\s*:\s*(\d{3,5})", body):
            found.append((int(pm.group(1)), int(pm.group(2)), z, f"{{{{{tname}}}}} pin"))
        for pm in re.finditer(r"(?<![\d=:])(\d{4})\s*[:,]\s*(\d{4})(?![\d])", body):
            found.append((int(pm.group(1)), int(pm.group(2)), z, f"{{{{{tname}}}}} positional"))
    seen, uniq = set(), []
    for c in found:
        if c[:3] not in seen:
            seen.add(c[:3])
            uniq.append(c)
    return uniq


def report(title, text, near=None):
    cs = coords(text) if text else []
    print(f"\n### {title}")
    if not cs:
        print("   (no coords found)")
        return
    if near:
        cs.sort(key=lambda c: (c[0] - near[0]) ** 2 + (c[1] - near[1]) ** 2)
    for x, y, z, src in cs[:6]:
        dist = f"   [{int(((x - near[0]) ** 2 + (y - near[1]) ** 2) ** 0.5)} tiles from ref]" if near else ""
        print(f"   {x}, {y}, {z}   {src}{dist}")


def main():
    args = sys.argv[1:]
    near = None
    if args and args[0].startswith("--near="):
        near = tuple(int(v) for v in args[0].split("=", 1)[1].split(","))
        args = args[1:]
    if not args:
        print(__doc__)
        return 1
    pages = fetch(args)
    for t in args:
        text = pages.get(t)
        if not text:
            hits = search(t)
            if hits:
                print(f"\n### {t}  -> no direct page; search suggests: {hits}")
                alt = fetch(hits[:1]).get(hits[0])
                if alt:
                    report(f"{t}  (via '{hits[0]}')", alt, near)
                continue
        report(t, text, near)
    return 0


if __name__ == "__main__":
    sys.exit(main())
