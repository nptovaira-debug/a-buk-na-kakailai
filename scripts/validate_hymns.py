#!/usr/bin/env python3
"""Validate shared hymn data and its bundled Android copy."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
shared = json.loads((root / "shared-data/hymns.json").read_text(encoding="utf-8"))
sections = json.loads((root / "shared-data/sections.json").read_text(encoding="utf-8"))
android_copy = (root / "app/src/main/assets/hymns.json").read_text(encoding="utf-8")
shared_text = (root / "shared-data/hymns.json").read_text(encoding="utf-8")

hymns = shared["hymns"]
numbers = [h["number"] for h in hymns]
assert len(hymns) == 250, f"Expected 250 records, got {len(hymns)}"
assert numbers == list(range(1, 251)), "Hymn numbering must be continuous from 1 to 250"
assert len(sections["sections"]) == 27, "Expected 27 section headings"
section_ids = {section["id"] for section in sections["sections"]}
assert all(h["sectionId"] in section_ids for h in hymns), "A hymn references an unknown section"
assert android_copy == shared_text, "Android asset is out of sync with shared hymn data"
for hymn in hymns:
    assert isinstance(hymn["stanzas"], list), f"Invalid stanza list for hymn {hymn['number']}"
    for stanza in hymn["stanzas"]:
        assert isinstance(stanza["text"], str), f"Invalid stanza text for hymn {hymn['number']}"
empty = [h["number"] for h in hymns if not h["stanzas"]]
assert empty == [237, 238, 239], f"Unexpected empty-source records: {empty}"
print(f"PASS: {len(hymns)} hymns, {len(sections['sections'])} sections; Android data copy matches.")
print("PASS: Hymns 237–239 remain explicit empty records; no missing hymn numbers.")
