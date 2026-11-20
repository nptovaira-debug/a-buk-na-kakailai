# Multi-edition hymn data model

## Purpose

Prepare the app to add hymn books from other editions without changing the current 1960 collection, its numbering, transcription or reader interface.

## Current catalog

`shared-data/editions.json` is the edition registry. The current default edition is:

- ID: `kuanua-1960`
- Title: `A Buk na Kakailai (1960 Edition)`
- Language: Kuanua
- Dialect: Blanche Bay
- Publication year: 1960
- Data files: `hymns.json` and `sections.json`

The existing hymn records remain in their current schema and files. No lyrics, numbers, references or section assignments have been changed for this preparation.

## Identity rules for future editions

1. Give every edition a permanent, unique, lowercase ID (for example, `edition-shortname-year`).
2. Treat a hymn's identity as `(editionId, hymnNumber)`. A number such as 25 may exist in several editions and must never be treated as a global identifier.
3. Keep each edition's original hymn numbering, section structure, titles and source references independent.
4. Keep section IDs unique within an edition or namespace them with the edition ID.
5. Do not merge hymns from different editions into one numbered sequence.
6. Preserve the original transcription separately from any later verified correction or editorial note.

## User data compatibility

When persistent user data is migrated or introduced, key these records by both edition ID and hymn number:

- favourites
- service playlists and collections
- personal notes
- reading history
- recording links and moderation references

Existing 1960-only records can be migrated by assigning `kuanua-1960` as their edition ID. Do not run that migration until the multi-edition feature is implemented and tested.

## Future dataset layout

The current file layout remains in place. When another edition is added, register it in `editions.json` and give it its own hymn and section data files. The app should load the selected edition's data while keeping the current 1960 edition as the default unless the user chooses otherwise.

## Scope of this preparation

- Added the edition catalog and a validation check for the existing 1960 metadata.
- Did not change the current app screens, hymn JSON records, search behavior, favourites, or Firebase data.
- Did not add a second edition or claim multi-edition browsing is already available.
