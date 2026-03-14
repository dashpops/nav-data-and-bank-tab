#!/usr/bin/env python3
import argparse
import datetime as dt
import html
import json
import re
import sys
import urllib.request
from pathlib import Path
from typing import Optional

SOURCE_URL = "https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3?action=raw"
PAGE_URL = "https://oldschool.runescape.wiki/w/Guide:B0aty_HCIM_Guide_V3"


def fetch_wikitext() -> str:
    with urllib.request.urlopen(SOURCE_URL) as response:
        return response.read().decode("utf-8")


def clean_text(text: str) -> str:
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = text.replace("'''", "").replace("''", "")
    text = re.sub(r"\[\[([^|\]]+)\|([^\]]+)\]\]", r"\2", text)
    text = re.sub(r"\[\[([^\]]+)\]\]", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = html.unescape(text)
    text = text.replace("\u00a0", " ")
    text = re.sub(r"\s+", " ", text)
    return text.strip(" |")


def slugify(text: str) -> str:
    text = clean_text(text).lower()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def parse_checklist_title(raw_title: str, bank_number: int) -> str:
    value = raw_title.replace("{{#expr:{{#var:bankNumber}}+1}}", str(bank_number + 1))
    return clean_text(value)


def split_title_and_body(block: str) -> tuple[str, str]:
    prefix = "{{Checklist|title="
    if not block.startswith(prefix):
        raise ValueError("Checklist block missing expected prefix")

    content = block[len(prefix):]
    depth = 0
    index = 0
    while index < len(content):
        char = content[index]
        two_chars = content[index:index + 2]
        if two_chars == "{{":
            depth += 1
            index += 2
            continue
        if two_chars == "}}" and depth > 0:
            depth -= 1
            index += 2
            continue
        if char == "|" and depth == 0:
            title = content[:index]
            body = content[index + 1:]
            if body.endswith("}}"):
                body = body[:-2]
            return title.strip(), body
        index += 1

    raise ValueError("Could not split checklist title/body")


def extract_checklist_blocks(wikitext: str) -> list[tuple[int, str]]:
    blocks = []
    position = 0
    while True:
        start = wikitext.find("{{Checklist|title=", position)
        if start == -1:
            return blocks

        index = start
        depth = 0
        while index < len(wikitext) - 1:
            token = wikitext[index:index + 2]
            if token == "{{":
                depth += 1
                index += 2
                continue
            if token == "}}":
                depth -= 1
                index += 2
                if depth == 0:
                    blocks.append((start, wikitext[start:index]))
                    position = index
                    break
                continue
            index += 1
        else:
            raise ValueError("Unclosed checklist template")


def build_context_maps(wikitext: str) -> tuple[list[tuple[int, str]], list[tuple[int, str]], list[tuple[int, int]]]:
    episodes = []
    videos = []
    bank_resets = []

    offset = 0
    for line in wikitext.splitlines(keepends=True):
        episode_match = re.match(r"^===\s*(.+?)\s*===\s*$", line.strip())
        if episode_match:
            title = clean_text(episode_match.group(1))
            if title and title != "Guide specific terminology":
                episodes.append((offset, title))

        video_match = re.search(r"\{\{Youtube\|([^}|]+)", line)
        if video_match:
            videos.append((offset, video_match.group(1).strip()))

        if re.search(r"\{\{Var\|\s*bankNumber\s*\|\s*0\s*\}\}", line):
            bank_resets.append((offset, 0))

        offset += len(line)

    return episodes, videos, bank_resets


def latest_value(context: list[tuple[int, str]], position: int) -> Optional[str]:
    result = None
    for item_position, value in context:
        if item_position > position:
            break
        result = value
    return result


def latest_bank_reset(context: list[tuple[int, int]], position: int) -> bool:
    return any(item_position <= position for item_position, _ in context)


def parse_steps(body: str, section_slug: str) -> list[dict]:
    steps = []
    for line in body.splitlines():
        match = re.match(r"^(\*+)\s*(.+?)\s*$", line)
        if not match:
            continue

        level = len(match.group(1))
        text = clean_text(match.group(2))
        if not text:
            continue

        steps.append(
            {
                "id": f"{section_slug}-step-{len(steps) + 1:03d}",
                "index": len(steps) + 1,
                "level": level,
                "text": text,
            }
        )
    return steps


def make_unique_slug(base_slug: str, seen_slugs: dict[str, int]) -> str:
    count = seen_slugs.get(base_slug, 0)
    seen_slugs[base_slug] = count + 1
    if count == 0:
        return base_slug
    return f"{base_slug}-{count + 1}"


def shorten_title(text: str, limit: int = 72) -> str:
    if len(text) <= limit:
        return text
    return text[: limit - 3].rstrip() + "..."


def build_guide(wikitext: str) -> dict:
    checklist_blocks = extract_checklist_blocks(wikitext)
    episodes, videos, bank_resets = build_context_maps(wikitext)
    sections = []
    bank_number = 0
    saw_reset = False
    seen_slugs: dict[str, int] = {}

    for position, block in checklist_blocks:
        if latest_bank_reset(bank_resets, position) and not saw_reset:
            bank_number = 0
            saw_reset = True

        raw_title, body = split_title_and_body(block)
        title = parse_checklist_title(raw_title, bank_number)
        episode = latest_value(episodes, position) or "Guide"
        youtube_id = latest_value(videos, position)
        if not title:
            preview_steps = parse_steps(body, "preview")
            if not preview_steps:
                continue
            title = shorten_title(preview_steps[0]["text"])

        section_slug = make_unique_slug(slugify(f"{episode}-{title}"), seen_slugs)
        steps = parse_steps(body, section_slug)

        if title.startswith("Bank "):
            bank_number += 1

        if not steps:
            continue

        sections.append(
            {
                "id": section_slug,
                "episodeTitle": episode,
                "title": title,
                "youtubeId": youtube_id,
                "steps": steps,
            }
        )

    return {
        "title": "B0aty HCIM Guide V3",
        "sourceUrl": PAGE_URL,
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "sections": sections,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Import the B0aty HCIM Guide V3 from the OSRS Wiki.")
    parser.add_argument("--input-file", type=Path, help="Path to a local raw wikitext file")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("src/main/resources/guide.json"),
        help="Where to write the generated guide JSON",
    )
    args = parser.parse_args()

    if args.input_file:
        wikitext = args.input_file.read_text(encoding="utf-8")
    else:
        wikitext = fetch_wikitext()

    guide = build_guide(wikitext)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(guide, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")
    print(f"Wrote {len(guide['sections'])} sections to {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
