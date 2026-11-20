# A Buk na Kakailai — Development Roadmap

## Product direction

Build both a historical digital edition and a practical modern hymn app. Keep the current web/PWA edition available while developing a native Android app. Both clients should consume the same structured hymn dataset.

## Phase 1 — Preserve the source (started)

- [x] Export the existing 250 hymn records into structured JSON without intentionally editing the transcription.
- [x] Record all 27 section headings and hymn-number membership.
- [x] Document the 1960 publication details and known missing scan pages / hymns.
- [ ] Compare every hymn against the original scan and record verified corrections.
- [ ] Add scan page references only when confirmed from the source.
- [x] Establish a stable edition catalog with the 1960 collection as the default edition.
- [ ] Add future editions as separate datasets with independent numbering, sections and source references.

## Phase 2 — Improve the web/PWA edition

- [ ] Load the structured JSON as the canonical data source instead of duplicating hymn text in HTML.
- [ ] Preserve search, section filtering, favourites, notes, history, collections, service planning and presentation tools.
- [ ] Verify install prompt, cache refresh, offline reading and data backup/restore on Android Chrome.
- [ ] Add accessible navigation and clear empty-state messages.

## Phase 3 — Native Android foundation (scaffold started)

- [x] Create a Kotlin + Jetpack Compose Android project structure.
- [x] Bundle the shared hymn JSON as an offline asset.
- [x] Add a basic searchable hymn list and hymn reading screen.
- [ ] Add Room persistence for favourites, notes, history and playlists.
- [ ] Add DataStore reading preferences.
- [ ] Add audio only when authorized/usable recordings are available.
- [ ] Consider optional sync only after local/offline workflows are stable.

## Phase 4 — Worship and learning tools

- [ ] Full-screen congregational presentation.
- [ ] Adjustable text size and display controls.
- [ ] Automatic lyric scrolling.
- [ ] Musical notation / tonic sol-fa only where source material is available and rights/permissions are clear.
- [ ] Audio accompaniment only where recordings and distribution rights are clear.

## Phase 5 — Community and release

- [ ] Optional correction submission process with moderation and a public change log.
- [ ] Privacy-conscious backup/sync design.
- [ ] Test on real Android phones, including low-connectivity and offline scenarios.
- [ ] Prepare privacy policy, store listing, screenshots and release signing.
- [ ] Complete applicable Google Play testing requirements before production release.

## Architecture principles

- Offline-first; core hymn reading must not require an account or internet connection.
- Historical source text and editorial corrections remain distinguishable.
- One shared dataset serves web and Android clients.
- Every hymn collection has a stable edition ID; hymn numbers are unique only within an edition.
- Favourites, playlists, notes, recordings and history must use the pair (edition ID, hymn number), never hymn number alone.
- Preserve the 1960 transcription and its existing numbering; add new editions alongside it rather than merging or renumbering records.
- Keep the current reader interface unchanged until multi-edition browsing is explicitly introduced.
- No fabricated lyrics, musical notation, recordings, source page references or historical corrections.
