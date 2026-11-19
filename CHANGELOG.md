# Changelog

## 0.4.0 — UI and listening-history foundation

- Replaced the blue/purple appearance presets with **Mono Green** (default), **Carbon**, and **Paper** palettes.
- Reworked the home screen to remove fabricated songs, albums, recommendation cards, and mood shortcuts. Empty sections no longer imply that demo content is real.
- Added local Recently Played history. Tracks are recorded when playback actually starts, retained across app restarts, deduplicated, and capped at 30 entries.
- Added a best-effort experimental forced-blur toggle and a yellow warning about possible lag, battery use, and rendering glitches. Android/platform limitations still apply; this cannot force unsupported hardware or OS APIs to provide true backdrop blur.
- Removed fabricated discovery items from the Library placeholder.
- Preserved the existing Media3 player, service, queue, and direct audio URL playback architecture.

### Notes

- Provider search, catalogue browsing, synced lyrics, artwork extraction, and cloud listening sync remain future work; this update does not pretend those integrations exist.
- Listening history is stored locally in app preferences. It is not uploaded to Supabase.
