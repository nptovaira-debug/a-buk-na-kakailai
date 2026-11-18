# A Buk na Kakailai

A mobile-friendly Kuanua hymn reader based on the 1960 *A Buk na Tinir, ma A Buk na Kakailai ure ra Lotu, ma A Lotu na Pupunang* (Blanche Bay Dialect).

## Project editions

- **Web/PWA:** the existing browser-based hymn reader remains the current public edition.
- **Android:** an initial Kotlin + Jetpack Compose app is being developed in `app/`.
- **Shared content:** `shared-data/` contains the structured hymn and section data intended as the canonical source for both clients.

## Existing web features

- Search and browse the digitised hymn collection
- Hymn sections and individual hymn reading
- Favourites, personal notes, history and service planning
- Reading preferences and dark reader mode
- Share/copy and presentation tools
- Installable PWA and service-worker caching

## Historical source and accuracy

The transcription is OCR-assisted and must be checked against the original scan. Preserve original wording and spelling. The supplied scan has missing printed pages 188–189; hymns 237–239 have headings in the transcription but no reproduced lyrics. No missing text has been reconstructed.

See [content verification policy](documentation/content-verification.md) and [development roadmap](documentation/development-roadmap.md).

## Shared data

- `shared-data/hymns.json` — numbered hymn records, source reference strings and stanza text.
- `shared-data/sections.json` — section titles and hymn-number membership.
- `shared-data/sources.json` — publication metadata, transcription status and known gaps.

Run the structural check with `python3 scripts/validate_hymns.py`.

## Android build

Open the repository in Android Studio and select the `app` configuration. The project uses Kotlin, Jetpack Compose, Java 17 and Android SDK 35. GitHub Actions builds a debug APK when Android project files change.

The Android app currently reads bundled JSON assets and works offline for the core hymn list and reading screens. Favourites, notes, playlists, audio and sync remain future milestones.

## Deploy the web edition

1. In GitHub, open **Settings → Pages**.
2. Under **Build and deployment**, choose **GitHub Actions**.
3. The included Pages workflow deploys the web edition when changes are pushed to `main`.

For offline and install features, open the deployed HTTPS site in Chrome on Android.
