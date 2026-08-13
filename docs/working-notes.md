# Boaty — working notes

How to pick this up again after a reboot, a week away, or a fresh session.

**Boaty** is a fork of conde's [HCIM Guide plugin](https://github.com/nandobrazil/hcim-guide-plugin),
which renders the OSRS Wiki *B0aty HCIM Guide V3* as a RuneLite sidebar. Upstream
ships navigation coordinates for banks 1–4 only; this fork fills in the rest, so
each step can hand a destination to the **Shortest Path** plugin.

Work lives on the **`boaty`** branch. `master` is untouched upstream.

---

## Progress

| | |
|---|---|
| Nav entries | **481** |
| Bank sections covered | **89 / 215** — banks 33 → 120, contiguous |
| Guide data | 236 sections / 2911 steps, regenerated from the wiki 2026-08-12 |
| Olly is playing | around **bank 33**, so coverage runs ~77 banks ahead |

Coverage starts at 33 deliberately — that is where Olly was when this started,
and banks 1–32 are behind him.

```bash
# recompute the above at any time
python3 - <<'PY'
import json,re
g=json.load(open('src/main/resources/guide.json'))
m=json.load(open('src/main/resources/step-metadata.json'))
banks=[s for s in g["sections"] if re.match(r'^Bank ',s["title"])]
done=[s for s in banks if any(st["id"] in m for st in s["steps"])]
print(len(m),"entries |",len(done),"/",len(banks),"banks | furthest",done[-1]["title"])
PY
```

Steps left deliberately without a target, and why, are in
[unresolved-navs.md](unresolved-navs.md). Read it before concluding something is
missing — a lot of it is "a nav here would be wrong", not "nobody got to it".

---

## Launching from the desktop: Iron and GIM

Two Mint applications (Mint menu → Games, or search "Iron" / "GIM"):

| App | Character |
|---|---|
| **Iron** | ProperDash |
| **GIM** | Minion Olly |

Both run `~/.local/bin/osrs-client <Character> <Label>`, which handles the
session problem below by itself:

1. If a Bolt client is already running that character, its session is captured.
2. Otherwise a cached session under 5 hours old is reused.
3. Otherwise **Bolt is opened for you** with a prompt to launch that character;
   the session is picked up automatically and the prompt closes itself.

So after a reboot you still need Bolt once, but you no longer have to remember
the sequence — click the app and follow the prompt. Sessions are cached per
character (`~/.cache/rl-run/session-<character>.env`, mode 600), so Iron and
GIM do not fight over one cache.

Note the game logs a character out if it appears twice, so launching GIM while
you are playing Minion Olly in Bolt will take that session over — which is the
point, but worth knowing.

```bash
osrs-client "ProperDash" Iron --refresh    # force a new session
osrs-client "ProperDash" Iron --dry-run    # report what it would do
```

Desktop entries live in `~/.local/share/applications/osrs-{iron,gim}.desktop`
with icons in `~/.local/share/icons/`. They are plain files, so they survive
reboots and upgrades; edit the `Exec=` line to point at a different character.

## Running the dev client

The Bolt/official client **cannot** load this plugin. It hard-disables developer
mode, so it never scans `sideloaded-plugins` — passing `--developer-mode` through
Bolt's `clientArguments` is accepted and logged but does nothing. The only way to
run Boaty is the from-source client.

```bash
boaty-deploy --run     # validate metadata, build, sideload, launch
boaty-deploy           # build + sideload only (safe while a client is running)
rl-run                 # launch using the cached Jagex session
```

### After a reboot, the session cache is stale

`rl-run` borrows a Jagex session from a running Bolt client, because a
from-source client on its own only offers the dead legacy login. The session is
cached in `~/.cache/rl-run/session.env`, but the ID is minted per Bolt launch and
expires after a few hours — **it will not survive a reboot**.

So the first run of the day is:

1. Open **Bolt** and launch the character you want (reaching the login screen is enough).
2. `rl-run <CharacterName>` — captures and caches that character's session.
3. **Close that Bolt client** so the character is not logged in twice.
4. Log in on the dev client.

After that, plain `rl-run` reuses the cache until it expires. `rl-run --refresh <Character>`
forces a re-capture.

To work on an alt without disturbing your main: launch the alt in Bolt and
`rl-run <alt>`. The main keeps playing on its own Bolt client — separate
character, separate session. Just never log the *same* character into both.

### Iterating

Edit metadata or Java, then `boaty-deploy --run`. There is no plugin hot-reload;
a code or data change means a client restart (~20s).

Don't run `boaty-deploy` and then expect a *running* client to pick it up — it
loaded its jar at startup. Deploy, then relaunch.

---

## What survives a reboot

| Survives | Notes |
|---|---|
| The repo and all commits | `/home/olly/callemshite/boaty/hcim-guide-plugin`, branch `boaty` |
| `~/.local/bin` helpers | `boaty-deploy`, `rl-run`, plus the OSRS bits below. `~/.profile` puts this on PATH |
| Deployed jar | `~/.runelite/sideloaded-plugins/boaty.jar` |
| JDK 11 | `/usr/lib/jvm/java-11-openjdk-amd64` — RuneLite needs 11, not 17/21 |
| Gradle caches | `~/.gradle` — keeps rebuilds fast |
| Ctrl+Space wiki search | `osrs-hotkey` daemon, autostarted via `~/.config/autostart/osrs-hotkey.desktop` |

| Does **not** survive | What to do |
|---|---|
| Jagex session cache | Re-capture: `rl-run <Character>` with that character open in Bolt |
| `osrs-click` number-key remap | `xmodmap` remaps reset on logout. Run `osrs-click` to re-enable (its state file lives in `/tmp`, which also clears) |
| Any running dev client | `boaty-deploy --run` |

---

## Getting coordinates right

The rule that matters: **never estimate a tile.** Every coordinate should come
from a source, and the sources are, in order:

1. **The wiki's map templates.** Fetch raw wikitext and read the pin:
   `https://oldschool.runescape.wiki/w/PAGE?action=raw`. The scratchpad tool
   `wikicoords.py` does this over the MediaWiki API in batches.
2. **Quest Helper's source** — `github.com/Zoinkwiz/quest-helper`, grep for the
   NPC or quest name near `new WorldPoint(x, y, z)`. Hand-verified, and often
   better than the wiki when the wiki only has a building outline.
3. **Shortest Path's transport data** — `github.com/Skretzo/shortest-path`,
   `src/main/resources/transports/*.tsv`. This is the authority on what is
   actually *reachable*.

Traps that have caused real errors here:

- Pins live in **several templates**: `{{Map}}`, `{{NPC map}}`, `{{Scene map}}` …
  Match any template whose name contains "map", or you will silently miss pages.
- Coordinate forms vary: `x=N|y=N`, positional `N:N`, and pin lists
  `x:2870,y:3115`. Missing a form looks exactly like "the wiki has no data".
- `r=` is **radius**; `height=`/`width=` are map display size. Neither is the plane.
- **Check the plane.** Lumbridge Castle bank is plane 2, Thormac plane 3, and
  Merlin / Juliet / Sanfew / Dr Fenkenstrain and others are upstairs. A wrong
  plane paths to the wrong floor and looks like a broken plugin.
- **A separate region is not an instance.** The Blast Furnace looked instanced
  (`1940,4958`) but Shortest Path links it to Keldagrim by stairs, so it routes
  fine. Check the transport data before declaring something unreachable.
- Use the **shopkeeper's tile**, not the shop building's outline polygon. An
  outline vertex put the Fishing Contest ~50 tiles off.
- Disambiguate multiple spawns by proximity to the neighbouring steps' locations.

---

## Step IDs are positional — don't skip the fingerprint dance

`scripts/import_b0aty_guide.py` regenerates `guide.json` from the wiki and numbers
steps by position (`<section-slug>-step-NNN`). Bank titles come from a running
counter too. So a single step inserted on the wiki shifts every later step ID
**and** can shift section slugs — silently re-pointing our navigation data at the
wrong steps. Regenerating once already moved `bank-135` to `bank-132-2`.

`scripts/reanchor_metadata.py` defends against this. Every entry carries a
`_text` / `_section` fingerprint (Gson ignores unknown fields, so they are inert
at runtime).

```bash
python3 scripts/reanchor_metadata.py verify        # does metadata still line up?
# regenerating the guide:
python3 scripts/reanchor_metadata.py fingerprint   # 1. BEFORE regenerating
python3 scripts/import_b0aty_guide.py              # 2. pull the current wiki
python3 scripts/reanchor_metadata.py reanchor      # 3. remap onto the new IDs
```

`reanchor` refuses to write if it cannot place an entry, rather than guessing.
`boaty-deploy` runs `verify` on every build, so drift cannot land quietly.

This is also why a step with two destinations must **not** be split in
`guide.json`: the split would be undone by the next regeneration and would show
up as permanently unresolved. Use the `navs` list instead — see below.

---

## Data format

`src/main/resources/step-metadata.json`, keyed by step ID:

```json
"episode-2-banks-24-through-75-bank-33-step-004": {
  "nav": { "label": "Seers' Village church organ", "x": 2691, "y": 3461, "z": 0 },
  "diary": "Kandarin Easy",
  "_text": "Play the Organ after the first completion [Kandarin Easy Diary]",
  "_section": "Bank 33"
}
```

For a step with more than one destination, use `navs` — the plugin routes to
whichever is closest and re-paths as you move:

```json
"…bank-39-step-008": {
  "navs": [
    { "label": "Snake weed", "x": 2764, "y": 3047, "z": 0 },
    { "label": "Ardrigal",   "x": 2870, "y": 3115, "z": 0 }
  ]
}
```

`quest` and `diary` are free-text tags. `_text` / `_section` are written by the
fingerprint step — don't hand-edit them.

---

## Other OSRS bits on this machine

Unrelated to the plugin, but they live in `~/.local/bin` and get forgotten:

- **`osrs-click`** — toggles OS Mouse Keys and maps the top-row number keys `1`–`0`
  to a left click, for one-press-one-click obstacle spam (Brimhaven spikes).
  While on, the number row does not type numbers. Resets on logout.
- **`osrs-search`** / **`osrs-hotkey`** — Ctrl+Space pops a box that searches the
  OSRS Wiki. The daemon grabs the key itself because Cinnamon fails to bind
  hot-added custom shortcuts; it autostarts on login.
