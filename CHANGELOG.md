# Changelog

## 0.4.0 — 2026-11-19

### Changed
- Replaced Crystal/Nocturne/Atelier palettes with Mono Green (default), Carbon and Paper.
- Reworked the home screen to show current playback state instead of fabricated promotional content.
- Renamed the first home layout to Standard in the appearance selector.
- Updated application version to 0.4.0 (versionCode 4).

### Added
- Five persistent accent colour choices.
- Local Recently Played history, recorded after 10 seconds of active playback and limited to 30 entries.
- Experimental forced blur preference and an explicit yellow performance warning.
- Empty-state messaging for unimplemented Library content.

### Removed
- Hardcoded fake recent tracks, sample albums, promotional mixes, new-release cards and mood shortcuts.
- Fake discovery category rows from the Library placeholder.

### Notes
- Provider search, streaming catalogues, synced lyrics, album artwork and cloud history are not implemented in this milestone.
- Android build not executed in this environment; see BUILD_STATUS.md.
