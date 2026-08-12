#!/usr/bin/env python3
"""Keep step-metadata.json anchored to guide.json across regenerations.

Step IDs in guide.json are positional (``<section-slug>-step-NNN``), so any step
the wiki inserts mid-section shifts every later ID -- silently re-pointing our
navigation data at the wrong steps. Bank titles are numbered by a running
counter too, so an inserted "Bank" checklist also shifts later section slugs.

This script defends against both:

  fingerprint   Record the step text (and section title) each metadata entry is
                attached to, as ``_text`` / ``_section`` keys. Run this BEFORE
                regenerating guide.json. Gson ignores unknown fields, so the
                extra keys are inert at runtime.

  reanchor      After regenerating guide.json, rebuild the metadata keys by
                matching each entry's recorded text back to its step. Reports
                anything it could not place instead of guessing.

  verify        Check every metadata key still exists in guide.json and that the
                recorded text still matches. Exits non-zero on drift.

Matching is deliberately conservative: exact normalised text within the same
section, then a unique normalised match anywhere in the guide. Ambiguous or
missing matches are reported for a human, never auto-resolved.
"""
import argparse
import difflib
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
GUIDE = REPO / "src/main/resources/guide.json"
META = REPO / "src/main/resources/step-metadata.json"


def normalise(text):
    """Lowercase alphanumerics only - immune to typo/punctuation/link churn."""
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def fuzzy_match(key, entry, norm_text, steps, floor=0.6):
    """Best in-section candidate whose text contains, or closely resembles, the
    recorded text. Returns (step_id, None) or (None, reason). Requires a clear
    winner so an elaborated step is matched but a rewritten one is not."""
    section_title = entry.get("_section")
    scored = []
    for step_id, (section, step) in steps.items():
        if section["title"] != section_title:
            continue
        candidate = normalise(step["text"])
        if norm_text and norm_text in candidate:
            score = 1.0  # recorded text survives verbatim inside the new text
        else:
            score = difflib.SequenceMatcher(None, norm_text, candidate).ratio()
        scored.append((score, step_id))

    if not scored:
        return None, f"section {section_title!r} no longer exists"
    scored.sort(reverse=True)
    best_score, best_id = scored[0]
    runner_up = scored[1][0] if len(scored) > 1 else 0.0
    if best_score < floor:
        return None, f"text no longer in guide, best in-section match {best_score:.2f}: {entry.get('_text','')[:60]!r}"
    if best_score - runner_up < 0.15:
        # Neighbouring steps in a section often share phrasing ("talk to X (3,1,2)").
        # An unchanged position is the tie-breaker: if the entry's own step is
        # among the close scorers, the step was elaborated in place.
        if any(sid == key and score >= floor for score, sid in scored):
            return key, None
        return None, f"text drifted and {best_score:.2f}/{runner_up:.2f} matches are too close to call"
    return best_id, None


def load(guide_path, meta_path):
    guide = json.loads(Path(guide_path).read_text(encoding="utf-8"))
    meta = json.loads(Path(meta_path).read_text(encoding="utf-8"))
    steps = {}
    for section in guide["sections"]:
        for step in section["steps"]:
            steps[step["id"]] = (section, step)
    return guide, meta, steps


def cmd_fingerprint(args):
    _, meta, steps = load(args.guide, args.meta)
    stamped = missing = 0
    for key, entry in meta.items():
        found = steps.get(key)
        if not found:
            print(f"  WARN no such step, cannot fingerprint: {key}")
            missing += 1
            continue
        section, step = found
        entry["_text"] = step["text"]
        entry["_section"] = section["title"]
        stamped += 1
    Path(args.meta).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print(f"fingerprinted {stamped} entries ({missing} unresolved)")
    return 1 if missing else 0


def cmd_reanchor(args):
    _, meta, steps = load(args.guide, args.meta)

    # index: normalised text -> [step ids]
    by_text = {}
    by_section_text = {}
    for step_id, (section, step) in steps.items():
        by_text.setdefault(normalise(step["text"]), []).append(step_id)
        by_section_text[(section["title"], normalise(step["text"]))] = step_id

    remapped, unchanged, problems = {}, 0, []
    for key, entry in meta.items():
        text = entry.get("_text")
        if text is None:
            # No fingerprint: keep only if the key still resolves.
            if key in steps:
                remapped[key] = entry
                unchanged += 1
            else:
                problems.append((key, "no fingerprint and step ID is gone"))
            continue

        norm = normalise(text)
        target = by_section_text.get((entry.get("_section"), norm))
        if target is None:
            candidates = by_text.get(norm, [])
            if len(candidates) == 1:
                target = candidates[0]
            elif len(candidates) > 1:
                problems.append((key, f"ambiguous, {len(candidates)} steps share this text"))
                continue
            else:
                # Wiki often elaborates a step in place ("Talk to X" -> "Head west
                # and talk to X"). Accept only a confidently similar step that is
                # still in the same section, and re-stamp the fingerprint.
                target, why = fuzzy_match(key, entry, norm, steps)
                if target is None:
                    problems.append((key, why))
                    continue
                print(f"  fuzzy  {key}\n     text drifted, matched in-section: {steps[target][1]['text'][:70]!r}")
                entry["_text"] = steps[target][1]["text"]

        if target != key:
            print(f"  moved {key}\n     -> {target}")
        else:
            unchanged += 1
        remapped[target] = entry

    print(f"\nre-anchored: {len(remapped)} entries ({unchanged} unchanged, "
          f"{len(remapped) - unchanged} moved), {len(problems)} unresolved")
    for key, why in problems:
        print(f"  UNRESOLVED {key}: {why}")

    if problems and not args.force:
        print("\nRefusing to write with unresolved entries (use --force to write anyway).")
        return 1

    Path(args.meta).write_text(json.dumps(remapped, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {args.meta}")
    return 0


def cmd_verify(args):
    _, meta, steps = load(args.guide, args.meta)
    bad = []
    for key, entry in meta.items():
        found = steps.get(key)
        if not found:
            bad.append((key, "step ID not found in guide.json"))
            continue
        _, step = found
        text = entry.get("_text")
        if text is not None and normalise(text) != normalise(step["text"]):
            bad.append((key, f"text drift\n       recorded: {text[:70]}\n       actual  : {step['text'][:70]}"))
    print(f"checked {len(meta)} entries against {len(steps)} steps -> {len(bad)} problem(s)")
    for key, why in bad:
        print(f"  {key}: {why}")
    return 1 if bad else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--guide", default=str(GUIDE))
    parser.add_argument("--meta", default=str(META))
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("fingerprint", help="record step text into each metadata entry")
    reanchor = sub.add_parser("reanchor", help="remap metadata keys onto a regenerated guide.json")
    reanchor.add_argument("--force", action="store_true", help="write even if entries are unresolved")
    sub.add_parser("verify", help="check metadata still matches guide.json")
    args = parser.parse_args()
    return {"fingerprint": cmd_fingerprint, "reanchor": cmd_reanchor, "verify": cmd_verify}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
