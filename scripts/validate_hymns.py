#!/usr/bin/env python3
"""Validate that shared and Android hymn data match the current web transcription."""
import html
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
shared_text = (root / "shared-data/hymns.json").read_text(encoding="utf-8")
shared = json.loads(shared_text)
sections = json.loads((root / "shared-data/sections.json").read_text(encoding="utf-8"))
android_copy = (root / "app/src/main/assets/hymns.json").read_text(encoding="utf-8")
web_html = (root / "index.html").read_text(encoding="utf-8")

hymns = shared["hymns"]
numbers = [h["number"] for h in hymns]
assert len(hymns) == 250, f"Expected 250 records, got {len(hymns)}"
assert numbers == list(range(1, 251)), "Hymn numbering must be continuous from 1 to 250"
assert len(sections["sections"]) == 27, "Expected 27 section headings"
section_ids = {section["id"] for section in sections["sections"]}
assert all(h["sectionId"] in section_ids for h in hymns), "A hymn references an unknown section"
assert android_copy == shared_text, "Android asset is out of sync with shared hymn data"

def clean(fragment, strip=True):
    value = html.unescape(re.sub(r"<[^>]*>", "", fragment))
    return value.strip() if strip else value

web_records = []
for match in re.finditer(r'<article class="hymn" id="hymn-(\d+)" data-section="([^"]+)">([\s\S]*?)</article>', web_html):
    number, section_id, body = match.groups()
    title_match = re.search(r"<h2>([\s\S]*?)</h2>", body)
    source_match = re.search(r'<p class="ref">([\s\S]*?)</p>', body)
    stanzas = []
    stanza_pattern = r'<div class="stanza"><div class="stanza-no">([\s\S]*?)</div><div class="lyrics">([\s\S]*?)</div></div>'
    for stanza in re.finditer(stanza_pattern, body):
        stanza_number, stanza_text = stanza.groups()
        stanza_text = re.sub(r"<br\s*/?>", "\n", stanza_text)
        stanzas.append({"number": clean(stanza_number), "text": clean(stanza_text, strip=False)})
    web_records.append({
        "number": int(number),
        "sectionId": section_id,
        "title": clean(title_match.group(1)) if title_match else "",
        "sourceReference": clean(source_match.group(1)) if source_match else "",
        "stanzas": stanzas,
    })
assert web_records == hymns, "Shared JSON differs from the existing web hymn transcription"

empty = [h["number"] for h in hymns if not h["stanzas"]]
assert empty == [237, 238, 239], f"Unexpected empty-source records: {empty}"
print(f"PASS: {len(hymns)} hymns, {len(sections['sections'])} sections; JSON matches web source.")
print("PASS: Android data copy matches; hymns 237–239 remain without invented lyrics.")
