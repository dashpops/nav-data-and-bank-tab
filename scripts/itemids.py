#!/usr/bin/env python3
"""Resolve OSRS item names to item IDs from the wiki.

    scripts/itemids.py "Jug of wine" "Flax" "Law rune"

Reads the `id`/`id1` field from each item page's infobox. Item pages often list
several ids (charged variants, members/free versions); the first is the base
item, which is what a bank withdrawal means.

Use this rather than recalling ids: a wrong id silently marks a step as never
satisfiable, which looks like the plugin being broken.
"""
import json
import re
import sys
import urllib.parse
import urllib.request

API = "https://oldschool.runescape.wiki/api.php"
UA = {"User-Agent": "olly-boaty-items/1.0 (personal plugin project)"}


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


def item_ids(text):
    """Every id on the page, in order; the first is normally the base item."""
    ids = []
    for m in re.finditer(r"^\|\s*id\d*\s*=\s*(\d+)", text, re.M):
        value = int(m.group(1))
        if value not in ids:
            ids.append(value)
    return ids


def search(term):
    data = api({"action": "query", "list": "search", "srsearch": term, "srlimit": 3})
    return [r["title"] for r in data.get("query", {}).get("search", [])]


def main():
    names = sys.argv[1:]
    if not names:
        print(__doc__)
        return 1
    pages = fetch(names)
    for name in names:
        text = pages.get(name)
        if not text:
            hits = search(name)
            print(f"  {name:28} NOT FOUND    search: {hits}")
            continue
        ids = item_ids(text)
        note = "" if ids else "   (no id on page - may be a category or disambiguation)"
        print(f"  {name:28} {ids[:4]}{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
