# HCIM Guide

External RuneLite plugin that turns the OSRS Wiki `Guide:B0aty_HCIM_Guide_V3` into a per-step sidebar helper.

## Features

- Sidebar guide with persistent progress
- Section picker grouped by bank and guide segment
- Current-step card with compact action buttons
- Timeline view with wrapped text and no horizontal scrolling
- Manual completion with automatic advance on `Done`
- Safe auto-progress for quests, skill thresholds, and curated key items
- Quick links to the official wiki page and current episode video

## Project layout

- `src/main/java/com/conde/hcimguide`: plugin source
- `src/main/resources/guide.json`: generated guide data bundled with the plugin
- `scripts/import_b0aty_guide.py`: importer for the official wiki page
- `src/test/java/com/conde/hcimguide/HcimGuidePluginTest.java`: development client launcher

## Development

Run the plugin in a RuneLite development client:

```bash
./gradlew run
```

Build the project:

```bash
./gradlew build
```

Build the distributable fat jar:

```bash
./gradlew shadowJar
```

## Guide data generation

```bash
python3 scripts/import_b0aty_guide.py
```

Optional arguments:

```bash
python3 scripts/import_b0aty_guide.py \
  --input-file /path/to/Guide_B0aty_HCIM_Guide_V3.wiki \
  --output src/main/resources/guide.json
```

## Publishing

Before opening the Plugin Hub PR:

1. Create a public GitHub repository for this project.
2. Push the plugin code to that repository.
3. Set `support=` in `runelite-plugin.properties` to your GitHub issues URL.
4. Follow the exact submission checklist in `PUBLISHING.md`.

## Licensing

The code is licensed under BSD 2-Clause.

The imported guide data is derived from the OSRS Wiki and keeps separate attribution and licensing requirements. See [NOTICE.md](/Users/conde/projects/hcim-guide-plugin/NOTICE.md).
