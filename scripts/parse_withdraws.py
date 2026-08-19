#!/usr/bin/env python3
"""Turn the guide's "Withdraw:" steps into withdraw metadata.

    scripts/parse_withdraws.py            # report only
    scripts/parse_withdraws.py --write    # update step-metadata.json

Splits each list into entries, pulls out a stated amount where there is one, and
resolves names to item ids from the wiki. Anything it cannot resolve confidently
is left out and printed, because a wrong id makes a step permanently
unsatisfiable and reads as a broken plugin rather than as bad data.
"""
import json
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
GUIDE = REPO / "src/main/resources/guide.json"
META = REPO / "src/main/resources/step-metadata.json"
API = "https://oldschool.runescape.wiki/api.php"
UA = {"User-Agent": "olly-boaty-items/1.0 (personal plugin project)"}

# Guide shorthand -> wiki page name. Anything not here is looked up as written.
ALIASES = {
    "air staff": "Staff of air", "ardy cloak": "Ardougne cloak 1",
    "ardougne cloak": "Ardougne cloak 1", "ardougne cloak 1": "Ardougne cloak 1",
    "ardy cloak 1": "Ardougne cloak 1", "pestle & mortar": "Pestle and mortar", "pestle\u0001mortar": "Pestle and mortar",
    "pestle and mortar": "Pestle and mortar", "duelling ring": "Ring of dueling",
    "dueling ring": "Ring of dueling", "ring of duelling": "Ring of dueling",
    "games necklace": "Games necklace(8)", "wine": "Jug of wine", "wines": "Jug of wine",
    "empty bucket": "Bucket", "empty buckets": "Bucket", "bucket": "Bucket",
    "empty pot": "Empty pot", "empty pots": "Empty pot", "empty sack": "Empty sack",
    "swamp tar": "Swamp tar", "tar": "Swamp tar", "guam": "Guam leaf",
    "seceteaurs": "Secateurs", "magic secateurs": "Magic secateurs",
    "chroncle": "Chronicle", "chronicle": "Chronicle",
    "ghost speak amulet": "Ghostspeak amulet", "ghostspeak amulet": "Ghostspeak amulet",
    "dramen staff": "Dramen staff", "steel axe": "Steel axe", "adamant axe": "Adamant axe",
    "rune axe": "Rune axe", "mithril axe": "Mithril axe",
    "adamant scimitar": "Adamant scimitar", "rune sword": "Rune sword",
    "silver sickle": "Silver sickle", "lit candle": "Lit candle",
    "black wizard hat": "Wizard hat (black)", "chefs hat": "Chef's hat",
    "graceful legs": "Graceful legs", "boots of lightness": "Boots of lightness",
    "ring of recoil": "Ring of recoil", "recoil rings": "Ring of recoil",
    "ring of charos": "Ring of charos", "scrying orb": "Scrying orb",
    "digsite pendant": "Digsite pendant (5)", "ectophial": "Ectophial",
    "rune pouch": "Rune pouch", "seed dibber": "Seed dibber", "watering can": "Watering can",
    "watering cans": "Watering can", "spade": "Spade", "rake": "Rake", "saw": "Saw",
    "hammer": "Hammer", "chisel": "Chisel", "needle": "Needle", "thread": "Thread",
    "knife": "Knife", "rope": "Rope", "ropes": "Rope", "tinderbox": "Tinderbox",
    "feathers": "Feather", "coins": "Coins", "papyrus": "Papyrus", "vial": "Vial",
    "vial of water": "Vial of water", "silk": "Silk", "wool": "Wool",
    "soft clay": "Soft clay", "clay": "Clay", "molten glass": "Molten glass",
    "iron bar": "Iron bar", "iron bars": "Iron bar", "steel bar": "Steel bar",
    "steel bars": "Steel bar", "bronze bar": "Bronze bar", "bronze bars": "Bronze bar",
    "gold bar": "Gold bar", "silver bar": "Silver bar", "planks": "Plank",
    "plank": "Plank", "oak plank": "Oak plank", "oak logs": "Oak logs",
    "willow log": "Willow logs", "logs": "Logs", "nails": "Steel nails",
    "steel nails": "Steel nails", "coal": "Coal", "pure essence": "Pure essence",
    "marks of grace": "Mark of grace", "small fishing net": "Small fishing net",
    "big fishing net": "Big fishing net", "fishing rod": "Fishing rod",
    "fly fishing rod": "Fly fishing rod", "fishing bait": "Fishing bait",
    "harpoon": "Harpoon", "lobster pot": "Lobster pot", "cabbage": "Cabbage",
    "garlic": "Garlic", "bread": "Bread", "milk": "Bucket of milk", "egg": "Egg",
    "flour": "Pot of flour", "ashes": "Ashes", "bowl": "Bowl",
    "bowl of water": "Bowl of water", "bucket of water": "Bucket of water",
    "bucket of slime": "Bucket of slime", "buckets of slime": "Bucket of slime",
    "dragon bones": "Dragon bones", "bones": "Bones", "bat bones": "Bat bones",
    "karambwans": "Cooked karambwan", "karambwan": "Cooked karambwan",
    "karambwanji": "Karambwanji", "raw shark": "Raw shark", "raw tuna": "Raw tuna",
    "shrimps": "Shrimps", "swordfish": "Swordfish", "tuna": "Tuna", "salmon": "Salmon",
    "bass": "Bass", "antipoison": "Antipoison(4)", "super antipoison": "Super antipoison(4)",
    "law rune": "Law rune", "law runes": "Law rune", "law": "Law rune",
    "water rune": "Water rune", "water runes": "Water rune",
    "air rune": "Air rune", "air runes": "Air rune", "air": "Air rune",
    "earth rune": "Earth rune", "fire rune": "Fire rune",
    "death rune": "Death rune", "death runes": "Death rune",
    "chaos rune": "Chaos rune", "chaos runes": "Chaos rune",
    "steel pickaxe": "Steel pickaxe", "adamant pickaxe": "Adamant pickaxe",
    "pickaxe": "Bronze pickaxe", "barcrawl card": "Barcrawl card", "barcrawl": "Barcrawl card", "varrock teleport runes": "Law rune",
    "flax": "Flax", "axe": "Bronze axe", "beer": "Beer", "stew": "Stew",
    "onion": "Onion", "onions": "Onion", "cheese": "Cheese", "jug of water": "Jug of water",
    "desert robes": "Desert robe", "shantay pass": "Shantay pass",
    "waterskin": "Waterskin(4)", "waterskin (4)": "Waterskin(4)",
    "glassblowing pipe": "Glassblowing pipe", "seaweed": "Seaweed",
    "limpwurt roots": "Limpwurt root", "marigold seed": "Marigold seed",
    "onion seeds": "Onion seed", "cabbage seeds": "Cabbage seed",
    "jute seeds": "Jute seed", "compost": "Compost", "garden pie": "Garden pie",
    "seedbox": "Seed box", "excalibur": "Excalibur", "silverlight": "Silverlight",
    "cat": "Pet cat", "kitten": "Pet kitten", "spice": "Spice", "gnome spice": "Gnome spice",
    "charcoal": "Charcoal", "yellow dye": "Yellow dye", "orange dye": "Orange dye",
    "blue dye": "Blue dye", "purple dye": "Purple dye", "red dye": "Red dye",
    "pink skirt": "Pink skirt", "wig": "Wig", "paste": "Paste",
    "wizard mind bomb": "Wizard's mind bomb", "wizard mind bomb;": "Wizard's mind bomb",
    "greenmans ale": "Greenman's ale", "fruit blast": "Fruit blast",
    "eye of newt": "Eye of newt", "rotten tomato": "Rotten tomato",
    "sunbeam ale": "Sunbeam ale", "vodka": "Vodka", "marrentil": "Marrentill",
    "beers": "Beer", "nettles": "Nettles", "nettle tea": "Nettle tea",
    "papyrus,": "Papyrus", "iron chainbody": "Iron chainbody",
    "bronze med helm": "Bronze med helm", "unfired bowl": "Unfired bowl",
    "sapphire amulet": "Sapphire amulet", "oak longbow": "Oak longbow",
    "bird snare": "Bird snare", "enchanted gem": "Enchanted gem",
    "dwarven cake": "Dwarven rock cake", "ammo mould": "Ammo mould",
    "cannonball mould": "Ammo mould", "scorpion cage": "Scorpion cage",
    "antidragon shield": "Anti-dragon shield", "antifire shield": "Anti-dragon shield",
    "rune spear": "Rune spear", "shortbow": "Shortbow", "bronze knives": "Bronze knife",
    "steel warhammer": "Steel warhammer", "steel longsword": "Steel longsword",
    "steel mace": "Steel mace", "steel dagger": "Steel dagger", "steel swords": "Steel sword",
    "bag of salt": "Bag of salt", "bucket of sap": "Bucket of sap",
    "willow blackjack": "Willow blackjack", "swamp paste": "Swamp paste",
    "grimy herb": "Grimy guam leaf", "treasure map": "Treasure map",
    "pickled brain": "Pickled brain", "decapitated head": "Decapitated head",
    "conductor mould": "Lightning conductor", "sickle mould": "Sickle mould",
    "holy sickle (b)": "Silver sickle (b)", "plant cure": "Plant cure",
    "filled plant pot": "Plant pot", "trolley": "Trolley", "schematic": "Schematics",
    "dwarven lore": "Dwarven lore", "earth talisman": "Earth talisman",
    "fishing pass": "Fishing pass", "red vine worm": "Red vine worm",
    "ship ticket": "Ship ticket", "blunt axe": "Blunt axe", "rusty sword": "Rusty sword",
    "maze key": "Maze key", "scroll": "Scroll", "message": "Message",
    "nulodion notes": "Nulodion's notes", "elvargs head": "Elvarg's head",
    "unstamped letter": "Unstamped letter", "sealed letter": "Sealed letter",
    "cup of tea": "Cup of tea", "leather gloves": "Leather gloves",
    "cowhide": "Cowhide", "bear fur": "Bear fur", "wolfbane": "Wolfbane",
    "thin snail": "Thin snail", "orange slices": "Orange slices",
    "spicy maggots": "Spicy maggots", "blurite ore": "Blurite ore",
    "copper ore": "Copper ore", "iron ore": "Iron ore", "silver ore": "Silver ore",
    "sawmill proposal": "Sawmill proposal", "antique lamp": "Antique lamp",
    "elemental bars": "Elemental metal", "pendant of lucien": "Pendant of Lucien",
    "silverlight keys": "Silverlight key", "silverlight key": "Silverlight key",
    "touch paper": "Touch paper", "plague sample": "Plague sample",
    "pineapples": "Pineapple", "soggy bread": "Soggy bread", "dyed orange": "Dyed orange",
    "battered key": "Battered key", "lit black candle": "Black candle",
    "black candle": "Black candle", "stake": "Stake", "chef's hat": "Chef's hat",
}

# Second wave, from re-sweeping banks 65+: plurals the first pass missed, common
# quest items, and a few typos in the guide. Kept separate only for readability.
ALIASES.update({
    # logs / planks / wood
    "logs": "Logs", "willow logs": "Willow logs", "maple logs": "Maple logs",
    "maple log": "Maple logs", "yew logs": "Yew logs", "mahogany logs": "Mahogany logs",
    "teak logs": "Teak logs", "teak log": "Teak logs", "oak planks": "Oak plank",
    "willow branch": "Willow branch", "willow branches": "Willow branch",
    # jewellery / amulets
    "catspeak amulet": "Amulet of catspeak", "amulet of catspeak": "Amulet of catspeak",
    "cat speak amulet": "Amulet of catspeak", "necklace of passage": "Necklace of passage(5)",
    # boots / armour / symbols
    "climbing boots": "Climbing boots", "spiked boots": "Spiked boots",
    "steel gauntlets": "Steel gauntlets", "gold helmet": "Gold helmet",
    "holy symbol": "Holy symbol", "unblessed symbol": "Unblessed symbol",
    # weapons / ammo
    "bronze arrows": "Bronze arrow", "bronze arrow": "Bronze arrow",
    "headless arrows": "Headless arrow", "ogre bow": "Ogre bow",
    "ogre arrows": "Ogre arrow", "ogre bellows": "Ogre bellows",
    "rune thrownaxe": "Rune thrownaxe", "mithril spear": "Mithril spear",
    "adamant sword": "Adamant sword", "steel sword": "Steel sword",
    # tools
    "lockpick": "Lockpick", "shears": "Shears", "gardening trowel": "Gardening trowel",
    "iron spit": "Iron spit", "teasing stick": "Teasing stick",
    "butterfly net": "Butterfly net", "bullseye lantern": "Bullseye lantern",
    # moulds
    "necklace mould": "Necklace mould", "ring mould": "Ring mould",
    "tiara mould": "Tiara mould",
    # farming
    "supercompost": "Supercompost", "ugthanki dung": "Ugthanki dung",
    "potato seeds": "Potato seed", "mithril seeds": "Mithril seed",
    "limpwurt root": "Limpwurt root", "tarromin": "Tarromin", "harralander": "Harralander",
    "toadflax": "Toadflax",
    # food / cooking
    "cake": "Cake", "chocolate cake": "Chocolate cake", "fish pie": "Fish pie",
    "cooked trout": "Trout", "cooked meat": "Cooked meat", "steak sandwich": "Steak sandwich",
    "cooked jubbly": "Cooked jubbly", "lava eel": "Lava eel", "raw chicken": "Raw chicken",
    "raw swordfish": "Raw swordfish", "watermelon": "Watermelon",
    "jangerberries": "Jangerberry", "jangerberry": "Jangerberry",
    # potions
    "prayer potion": "Prayer potion(4)", "prayer potions": "Prayer potion(4)",
    "restore potion": "Restore potion(4)", "restores potions": "Restore potion(4)",
    "antipoisons": "Antipoison(4)",
    # runes
    "mud runes": "Mud rune", "mud rune": "Mud rune",
    # containers / misc quest items
    "clockwork mechanism": "Clockwork", "clockwork": "Clockwork", "barley": "Barley",
    "candle": "Candle", "unlit candle": "Candle", "bronze wire": "Bronze wire",
    "bronze wires": "Bronze wire", "fishbowl": "Fishbowl", "iron oxide": "Iron oxide",
    "red spider eggs": "Red spiders' eggs", "red spiders eggs": "Red spiders' eggs",
    "pigeon cages": "Pigeon cage", "pigeon cage": "Pigeon cage", "pot lid": "Pot lid",
    "cadava berries": "Cadava berries", "cadavaberry": "Cadava berries",
    "unicorn horn": "Unicorn horn", "tinerbox": "Tinderbox", "rat poison": "Rat poison",
    "box traps": "Box trap", "box trap": "Box trap", "commorb": "Commorb",
    "damaged soul bearer": "Damaged soul bearer", "masterthief armband": "Masterthief armband",
    "fire feather": "Fire feather", "jogre bones": "Jogre bones",
    "karambwan vessel": "Karambwan vessel", "karambwan vessels": "Karambwan vessel",
    "karambawns": "Cooked karambwan", "ball of wool": "Ball of wool",
    "balls of wool": "Ball of wool", "enchanted key": "Enchanted key",
    "gilded cross": "Gilded cross", "strange fruit": "Strange fruit",
    "strange fruits": "Strange fruit", "camulet": "Camulet", "blessed pot": "Blessed pot",
    "rotten apple": "Rotten apple", "earmuffs": "Earmuffs", "silver tiara": "Silver tiara",
    "pure essenece": "Pure essence", "bucket of milk": "Bucket of milk",
    "bowstring": "Bow string", "sack of potatoes": "Sack of potatoes",
    "rada's blessing 1": "Rada's blessing 1", "kharedst memoirs": "Kharedst's memoirs",
    "kharedst's memoirs": "Kharedst's memoirs",
})

# Third wave, from the early banks 1-10.
ALIASES.update({
    "mind rune": "Mind rune", "mind runes": "Mind rune", "air talisman": "Air talisman",
    "research package": "Research package", "eclipse red": "Eclipse red",
    "red eclipse": "Eclipse red", "arrow shaft": "Arrow shaft",
    "arrowshaft": "Arrow shaft", "arrowshafts": "Arrow shaft", "feather": "Feather",
    "bronze axe": "Bronze axe", "treasure scroll": "Treasure scroll",
})

# Fourth wave, from banks 11-20.
ALIASES.update({
    "raw sardine": "Raw sardine", "doogle leaves": "Doogle leaves",
    "snape grass": "Snape grass", "pie dish": "Pie dish", "boots": "Leather boots",
    "leather boots": "Leather boots", "poisoned sheep feed": "Poisoned sheep feed",
    "house teleport tablet": "Teleport to house (tablet)",
    "house teleport": "Teleport to house (tablet)",
    "falador teleport tablet": "Falador teleport (tablet)",
    "falador teleport": "Falador teleport (tablet)",
})

# Fifth wave, from banks 21-32.
ALIASES.update({
    "bark sample": "Bark sample", "translation book": "Translation book",
    "soda ash": "Soda ash", "bucket of sand": "Bucket of sand",
    "glarials pebble": "Glarial's pebble", "glarial's pebble": "Glarial's pebble",
    "wizard hat": "Wizard hat", "ghosts skull": "Ghost's skull",
    "ghost's skull": "Ghost's skull", "pot of flour": "Pot of flour",
    "woad leaf": "Woad leaf", "woad leaves": "Woad leaf", "redberries": "Redberries",
    "enchanted scroll": "Enchanted scroll", "enchanted quill": "Enchanted quill",
})

# Named in the guide but not a single bankable item, so deliberately skipped.
SKIP = {
    "teleport runes", "combat runes", "combat gear", "range gear", "blast spells",
    "best air spell", "food", "few food", "8 food", "2x food", "full inventory of best food",
    "all pies", "tick manipulation", "nothing", "invent of planks", "remainder of cash",
    "rogue outfit", "studded body", "chaps", "coif", "studded body & chaps & coif",
    "studded body+ chaps", "bone crossbow + bolts", "all ectotokens", "ectotokens",
    "remaining dragon bones", "pots", "food for elvarg", "a knife, log or axe",
    "harpoon or barb-tailed harpoon if you grabbed one in previous step",
    "2x red/yellow/blue dyes", "1500 empty buckets", "2100 headless arrows",
}

# "Teleport Runes" means these, per the guide's usual loadout.
TELEPORT_RUNES = ("Teleport runes", ["Staff of air", "Rune pouch"])


def api(params):
    params = dict(params, format="json", formatversion="2")
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers=UA)
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def fetch_ids(titles):
    """wiki page title -> [item ids]"""
    out = {}
    titles = sorted(set(titles))
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
                continue
            try:
                text = page["revisions"][0]["slots"]["main"]["content"]
            except (KeyError, IndexError):
                continue
            ids = []
            for m in re.finditer(r"^\|\s*id\d*\s*=\s*(\d+)", text, re.M):
                v = int(m.group(1))
                if v not in ids:
                    ids.append(v)
            by_title[page["title"]] = ids
        for t in chunk:
            resolved, seen = t, set()
            while resolved in alias and resolved not in seen:
                seen.add(resolved)
                resolved = alias[resolved]
            out[t] = by_title.get(resolved, [])
    return out


# Item names containing "&", protected from the split above.
AMPERSAND_NAMES = {
    r"pestle\s*&\s*mortar": "Pestle\u0001Mortar",
    r"studded body\s*&\s*chaps\s*&\s*coif": "Studded set",
}

QTY = re.compile(r"^\s*(\d+)\s*x?\s+(.*)$", re.I)
QTY_SUFFIX = re.compile(r"^(.*?)\s+x\s*(\d+)\s*$", re.I)


def split_entries(text):
    """The item list after "Withdraw:", split on commas and ampersands."""
    body = re.sub(r"^\s*Withdraw(?:\s+at[^:]*)?:?", "", text, flags=re.I)
    body = re.sub(r"\(\d+\s*Inventory[^)]*\)", "", body, flags=re.I)   # slot counts
    body = re.sub(r"\([^)]*\)", "", body)                              # other asides
    # Names that contain "&" must survive the split on it.
    for joined, placeholder in AMPERSAND_NAMES.items():
        body = re.sub(joined, placeholder, body, flags=re.I)
    # "+" and ";" join items too - "Cannonball Mould + 5 Food", "Bone crossbow+bolts",
    # "Scrying Orb ...; Wizard Mind Bomb" - and no item name contains either.
    parts = re.split(r",|\s*;\s*|\s*\+\s*|\s+&\s+|\s+and\s+", body)
    parts = [p.replace("\u0001", " & ") for p in parts]
    return [p.strip(" .;") for p in parts if p.strip(" .;")]


# Words the guide puts in front of an item that are not part of its name, so
# "4x regular Logs" and "a knife" resolve to Logs and Knife.
QUALIFIERS = {"regular", "normal", "noted", "your", "a", "an", "the", "all"}


def parse_entry(part):
    """(display name, lookup name, quantity)"""
    qty = 0
    m = QTY.match(part)
    if m:
        qty, part = int(m.group(1)), m.group(2)
    else:
        m = QTY_SUFFIX.match(part)
        if m:
            part, qty = m.group(1), int(m.group(2))
    name = part.strip(" .;")
    words = name.split()
    while len(words) > 1 and words[0].lower() in QUALIFIERS:
        words = words[1:]
    name = " ".join(words)
    key = name.lower().strip()
    return name, key, qty


def main():
    write = "--write" in sys.argv
    # --force-from=N re-parses banks N and up, replacing lists already there. Below
    # N is left alone, which protects the hand-curated banks 33-44 and anything
    # else already settled. Without it, an existing list is never overwritten.
    force_from = None
    min_bank, max_bank = 33, 10 ** 9
    for a in sys.argv:
        if a.startswith("--force-from"):
            force_from = int(a.split("=", 1)[1] if "=" in a else "0")
        elif a.startswith("--min-bank"):
            min_bank = int(a.split("=", 1)[1])
        elif a.startswith("--max-bank"):
            max_bank = int(a.split("=", 1)[1])
    guide = json.loads(GUIDE.read_text())
    meta = json.loads(META.read_text())
    steps = {s["id"]: (sec["title"], s) for sec in guide["sections"] for s in sec["steps"]}

    # Collect what needs looking up
    wanted_pages = set(ALIASES.values()) | set(TELEPORT_RUNES[1])
    ids = fetch_ids(wanted_pages)

    plans, unresolved = {}, {}
    for step_id, (section, step) in steps.items():
        if not re.match(r"^\s*Withdraw", step["text"], re.I):
            continue
        bank = re.match(r"^Bank (\d+)", section)
        if not bank or not (min_bank <= int(bank.group(1)) <= max_bank):
            continue
        entries, missed = [], []
        for part in split_entries(step["text"]):
            name, key, qty = parse_entry(part)
            if not key or key in SKIP:
                if key == "teleport runes":
                    page_ids = [i for p in TELEPORT_RUNES[1] for i in ids.get(p, [])[:1]]
                    if page_ids:
                        entries.append({"name": TELEPORT_RUNES[0],
                                        "itemIds": page_ids, "quantity": 0})
                        continue
                missed.append(name)
                continue
            page = ALIASES.get(key)
            if not page:
                missed.append(name)
                continue
            got = ids.get(page, [])
            if not got:
                missed.append(name)
                continue
            entries.append({"name": name, "itemIds": got[:1], "quantity": qty})
        if entries:
            plans[step_id] = entries
        if missed:
            unresolved[f"{section} #{step['index']}"] = missed

    resolved_count = sum(len(v) for v in plans.values())
    print(f"steps with items: {len(plans)}   entries resolved: {resolved_count}")
    print(f"steps with something unresolved: {len(unresolved)}")

    if write:
        written = skipped = 0
        for step_id, entries in plans.items():
            entry = meta.get(step_id, {})
            if entry.get("withdraw"):
                section = steps[step_id][0]
                bank = re.match(r"^Bank (\d+)", section)
                forced = force_from is not None and bank and int(bank.group(1)) >= force_from
                if not forced:
                    skipped += 1      # already settled; leave it alone
                    continue
            entry["withdraw"] = entries
            meta[step_id] = entry
            written += 1
        print(f"wrote {written} steps, left {skipped} existing lists untouched")
        META.write_text(json.dumps(meta, indent=2) + "\n")
        print("wrote", META)

    print("\nunresolved by step:")
    for k in sorted(unresolved, key=lambda s: (len(s), s)):
        print(f"  {k:14} {unresolved[k]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
