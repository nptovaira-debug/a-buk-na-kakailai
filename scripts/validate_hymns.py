#!/usr/bin/env python3
"""Validate shared hymn data and web/Android loading setup."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
hymns_text = (root / "shared-data/hymns.json").read_text(encoding="utf-8")
data = json.loads(hymns_text)
edition_catalog = json.loads((root / "shared-data/editions.json").read_text(encoding="utf-8"))
sections = json.loads((root / "shared-data/sections.json").read_text(encoding="utf-8"))["sections"]
android = (root / "app/src/main/assets/hymns.json").read_text(encoding="utf-8")
web = (root / "index.html").read_text(encoding="utf-8")
sw = (root / "service-worker.js").read_text(encoding="utf-8")
hymns = data["hymns"]

# The current collection remains the unchanged 1960 edition. The catalog
# establishes a stable edition identity before additional editions are added.
assert edition_catalog["schemaVersion"] == 1
assert edition_catalog["defaultEditionId"] == "kuanua-1960"
assert len(edition_catalog["editions"]) == 1
current_edition = edition_catalog["editions"][0]
assert current_edition["id"] == "kuanua-1960"
assert current_edition["hymnDataFile"] == "hymns.json"
assert current_edition["sectionDataFile"] == "sections.json"
assert current_edition["publicationYear"] == data["publicationYear"]
assert current_edition["sourcePublication"] == data["sourcePublication"]

assert len(hymns) == 250
assert [h["number"] for h in hymns] == list(range(1, 251))
assert len(sections) == 27
section_ids = {s["id"] for s in sections}
assert all(h["sectionId"] in section_ids for h in hymns)
assert android == hymns_text
assert [h["number"] for h in hymns if not h["stanzas"]] == [237, 238, 239]
assert '<div id="hymnDataContainer"' in web
assert "fetch('./shared-data/hymns.json')" in web
assert "fetch('./shared-data/sections.json')" in web
assert 'id="hymn-1" data-section=' not in web
assert "chunks.push(" in web
assert "./shared-data/hymns.json" in sw and "./shared-data/sections.json" in sw
print(f"PASS: {len(hymns)} hymns, {len(sections)} sections; Android data matches.")
print("PASS: Web app loads shared JSON; static duplicate hymn markup removed.")
print("PASS: Offline cache includes shared data; missing hymns remain unfilled.")
