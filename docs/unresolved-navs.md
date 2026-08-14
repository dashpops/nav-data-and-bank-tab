# Unresolved navigation targets

Steps that deliberately have **no** `nav` in `step-metadata.json`, and the reason.
A step missing from this list and from the metadata is simply one nobody has
looked at yet — this file only covers steps that were investigated and left out.

Rules of thumb used when deciding:

- **Never invent a tile.** If the wiki has no pin and Quest Helper has no
  `WorldPoint`, the step stays empty and gets listed here.
- **Roaming or scattered targets** (wandering NPCs, ore rocks, trees) don't get a
  nav — the guide's own text is better guidance than a wrong tile.
- **Steps you're already standing at** don't repeat the previous step's nav.
- **Instanced areas** have no overworld tile. Note that a *separate region* is not
  the same thing: the Blast Furnace looked instanced but is reachable via stairs
  Shortest Path knows about, so it now has targets.

Verify a claim here before trusting it — several entries were wrong when first
written (see "Resolved" at the bottom).

## Needs a coordinate (would be filled if someone supplies one)

| Bank | Step | What | Why unresolved |
|---|---|---|---|
| 39 | 9 | Kill a Jogre | Monster page has no map template; roams the jungle |
| 41B | 4 | Buy 100 Soda Ash/Sand | No pin for the vendor; unclear which port |
| 42 | 3 | Collect 4x Super Antipoison | Observatory dungeon interior, no pin |
| 42 | 4 | Loot south chests near the stairs | Observatory dungeon interior, no pin |
| 46 | 2 | Buy a raw shark | Vendor not identified in the guide text |
| 46 | 4 | Buy an Ice Cooler | Vendor not identified |
| 49 | 11 | Talk to High Priest (Entrana) | No pin found |
| 50 | 3 | Talk to Frizzy Skernip | No pin found |
| 58 | 2 | Sword Merchant identifies sword | No pin found |
| 65 | 15 | Bank at small island north of Fossil Island | No pin for that bank |
| 80 | 3 | Talk to Romeo | Not looked up yet |
| 84 | 5 | Kill 20 Blue Dragons | Taverley Dungeon interior (~2881,9826); entrance is on #3 |
| 87 | 7 | Travel Dwarven Ferryman | No pin for the ferryman |
| 90 | 2 | Start Shadow of the Storm | Start point not pinned; #3 navs to Uzer |
| 94 | 3 | Guardians of Armadyl | No pin on the wiki or in Quest Helper |
| 97 | 6, 7, 8 | Scorpions / mine carts / clay for book pages | Inside the Dwarven Mine, scattered; #4 navs to Rolad |
| 98 | 5 | Animal Magnetism at "the Farm" | Which farm is not stated in the guide text |
| 99 | 8 | Repair the ship | No pin for the wrecked ship |
| 99 | 13 | Use Star on Experiment Entrance | No pin for the entrance |
| 99 | 19 | Buy 2 undead chickens at the farm | Same unnamed farm as 98 #5 |
| 100 | 16 | Experiment Lair | No pin |
| 103 | 3 | Estate agent (move house) | Page has no map template |
| 103 | 5 | Enter Kalphite Lair | Page has no map template |
| 104 | 6 | Pyramid Plunder in Sophanem | No pin; #5 navs to Sophanem |
| 108B | 10 | Kill Wormbrain (Port Sarim) | No pin; roams the jail area |
| 109 | 10 | Kill Elvarg | Boss has no map pin; #10 navs to Crandor instead |
| 112 | 10, 11 | White Tree on Ice Mountain | No pin for the tree |
| 113 | 14 | Buy Asgarnian Ale in Burthorpe bar | Bar has no pin of its own; Harold upstairs is #15 |
| 114 | 6 | Kill Chronozon | Boss has no map pin; #5 navs to Edgeville |
| 115 | 22 | Travel Dwarven Ferryman | Still no pin, as at bank 87 |
| 116 | 3 | Talk to Dwarven Engineer | No pin; #1 navs to Keldagrim bank |

### Banks 121-130

| Bank | Step(s) | Why |
|---|---|---|
| 121 | 59-63 | Chambers of Xeric scouting — inside a raid, no fixed tile |
| 121 | 16, 23, 33, 56 | "Games necklace -> Wintertodt" — a teleport, not a walk. #24 navs to Ignisia there |
| 121 | 17, 21, 26, 31, 35, 50 | Minecart hops — Shortest Path treats these as transports; the destination is the following step |
| 121 | 45 | Boulder leap shortcut in the dense essence mine — no pin |
| 122 | 5 | Catch chinchompas in Kourend Woodland — a hunting area, not a spot |
| 124 | 17-25 | Taverley Dungeon interior (jail, dragons, Cerberus tunnel); #10 navs to the entrance |
| 128, 129 | all | Miscellania and Etceteria kingdom management — the work is in an interface, not at a tile |
| 130 | 21, 30 | Placing toads in Feldip Hills — scattered; #23 navs to Rantz |

Istoria (121 #28) resolved to `1552,10224`, which is not Arceuus and looks like a
different NPC of the same name, so it was left out rather than guessed.

## Deliberately empty (a nav would be wrong or useless)

| Bank | Step(s) | Reason |
|---|---|---|
| 36 | 5 | "Continue The Feud" sits *before* the travel-to-Pollnivneach step; can't tell if Al Kharid or Pollnivneach is meant. Looks like a guide sequencing quirk |
| 37 | 16 | MTA point rooms are instanced; #14 already navs to the arena |
| 41B | 6 | Pickpocket a Falador guard — guards roam |
| 45 | 7 | "Mine a limestone as you pass" — incidental, on-route |
| 46 | 10, 11 | Swamp tar / Tree Grotto logs — scattered gathering |
| 47 | 2, 3 | Cook's Assistant — the kitchen is inside Lumbridge Castle, already navved by #1 |
| 49 | 3 | Cut willow logs — many trees |
| 55 | 5, 7 | Powermine iron / mine coal — rock clusters |
| 58 | 17 | Mine clay at Varrock west mine — rock cluster |
| 59 | 5 | Mine coal — rock cluster |
| 62 | 7, 8 | Hosidius minecart and salt petre — no pins |
| 71 | 4 | Charter ship purchase — several charter docks, depends where you are |
| 72, 76, 82 | all | Activity banks (fish to 46, farming contracts/patches, woodcut to 45) — no fixed destination |
| 75 | 9 | Fairy ring hop — the ring is the mechanism, the destination is the next step |
| 78 | 15 | Charter ship purchase (as above) |

## Low confidence — set, but worth checking in game

| Bank | Step | Target | Concern |
|---|---|---|---|
| 36 | 9 | Asp & Snake bar `3361,2956` | **Inferred, not looked up.** The Bandit Champion has no pin; the guide says "over a chair", and chairs are in the bar |
| 39 | 8 | Snake weed `2764,3047` + Ardrigal `2870,3115` | Multi-nav; routes to whichever is closer. Ardrigal is 108 tiles from snake weed |
| 55 | 10 | Seers' Village bank `2725,3492` | Guide says "Camelot Bank"; this is the Camelot-area bank |
| 61 | 1 | Falador furnace `2976,3369` | A "Withdraw" step pointed at a furnace, because the step is "Make Blurite Bar at Falador furnace" |
| 62 | 3 | *(empty)* | Garden of Death returned four unlabelled polygon points; none clearly the entrance |
| 75 | 5 | Cooks' Guild `3143,3448` | Estimated from a building outline, not a pin |
| 62 | 9 | Woodcutting Guild `1565,3499` | Outline vertex rather than the entrance |

## Ambiguous "Withdraw" steps

These follow a "bank anywhere"-style step, so there is no defensible bank to pick:
**63 #1**, **64 #1**, **66 #1**, **72 #3**.

## Resolved (kept as a record of what was wrong)

- **Blast Furnace** (66 #9–12, 68 #5, 69 all) — was listed here as "instanced,
  unreachable". Wrong: it is a separate static region linked to Keldagrim by
  stairs in Shortest Path's `transports.tsv`. The Blast Furnace *page* has no pin
  but the NPCs inside it do (Ordan `1936,4966`, Foreman `1944,4958`).
- **Camel pen / Ugthanki dung / Tough Guy** (36 #7, #8) — resolved to Ali the
  Camel `3343,2964` once the parser was fixed.
- **Snake weed, Ardrigal, specimen tray, panning point, anvil, information
  clerk** — all were wrongly recorded as "no wiki data"; they were missed by a
  broken parser (`{{NPC map}}` and `x:N,y:N` pin lists were not being matched)
  and found once it was fixed. **Re-check with a working parser before declaring
  anything absent.**
- **Saro, Mary, Willow, Temple of Ikov, red vine worms, Vestri** — no wiki pin,
  but Quest Helper's source had verified `WorldPoint`s.
- **Hemenster** (79 #2) — was `2596,3473` from a polygon vertex, ~50 tiles off the
  real gate at `2642,3441`.
- **Tithe Farm** (74 #5, #10) — outline corner corrected to the entrance.
- **Complete Merlin's Crystal** (41B #19) — was `2758,3507`, inside Camelot Castle.
  Wrong: the finale is the ritual at the star symbol north-east of the castle,
  just inside the garden fence, `2780,3515` (Quest Helper's `goStandInStar`). The
  wiki confirms the magical symbol is outdoors on the garden's NE side. Smashing
  the crystal afterwards is trivial and #22 already navs to Merlin upstairs.

---

# Unresolved withdraw items

Item IDs come from the wiki via `scripts/itemids.py`, not from memory — a wrong
id silently makes a step unsatisfiable, which reads as the plugin being broken.

Where the guide states no amount ("Coins", "Food"), quantity is recorded as `0`
meaning *unstated*: the overlay then shows only a tick or cross, rather than
inventing a "/1" the guide never asked for.

## Judgement calls made

| Bank | Step | Call |
|---|---|---|
| 35 | 1 | "Desert Robes/boots" is one entry accepting robe `1835`, boots `1837` or shirt `1833`. The guide's slash makes it unclear whether it means the set or either piece |
| 41B | 1 | "Lit Black Candle" accepts lit `32` and unlit `38`. Only the lit one is asked for, but the unlit is what you would spot in the bank |
| 38, 39 | 1 | "Wines" taken as Jug of wine `1993`, the wine the guide uses for Blackjacking |
| 39 | 1 | "3x Bones" taken as plain Bones `526`, not big/bat bones |

## Deliberately not recorded

| Bank | Step | Why |
|---|---|---|
| 41B, 42 | 1 | "Teleport Runes" names no specific rune, and which ones vary by destination. Marking a guess would show a cross against runes you do not need |
| 43 | 1 | "Withdraw: Nothing" — nothing to record |
| 36 | 1 | "Barcrawl" recorded as Barcrawl card `455`; the shorthand is unambiguous in context |

Steps whose text begins "Withdraw" but which are instructions rather than lists
(e.g. bank 35 #9, "Withdraw a knife and cut a cactus") carry only the item named.

## Bulk pass, banks 33-215

`scripts/parse_withdraws.py` reads the withdraw lists, pulls out stated amounts
and resolves names against the wiki. It filled 169 steps with 1024 entries. It
never overwrites a list already in the file, so the hand-checked banks 33-44
stayed as they were.

**"Teleport Runes" now means Staff of air + Rune pouch**, per instruction. Both
appear in the bank filter, either satisfies the entry.

Guesses in the alias table worth a second look:

| Guide says | Taken as | Note |
|---|---|---|
| "Pickaxe" | Bronze pickaxe | Any pickaxe would do; the lowest is a placeholder, so a better one in the bank shows a cross |
| "Axe" | Bronze axe | Same |
| "Varrock Teleport Runes" | Law rune | Only the law rune is specific; the elemental runes are not named |
| "Milk" | Bucket of milk | The only bankable milk |
| "Grimy Herb" | Grimy guam leaf | The guide does not say which herb |
| "Antipoison" / "Super antipoison" | 4-dose | Dose is never stated |

Around 144 steps still have at least one unresolved word. Nearly all are
categories rather than items — "Combat Gear", "Food", "Potions", "Range Gear",
"Best Air Spell" — which have no id to point at and are left out on purpose.
The rest are genuinely ambiguous ("Biohazard Items", "Thralls, etc") or later
content nobody has checked yet.
