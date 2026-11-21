# LyricGlow fork changelog

## 0.1.0 — Initial LyricGlow foundation

- Forked the supplied Mochy Music source snapshot as the functional foundation.
- Changed launcher/display application name to LyricGlow.
- Changed Android application ID to `com.reingl.lyricglow`; retained the original internal Kotlin namespace to avoid destabilising upstream component and module references.
- Set the project root name to LyricGlow.
- Reworked the default Material accent from neutral grey to vivid green while retaining monochrome black/white surfaces.
- Updated the app icon palette's default background and custom green swatch.
- Replaced upstream-facing README with LyricGlow product direction, build notes, limitations, and derivative attribution.
- Added `UPSTREAM.md` and this changelog.
- Retained Mochy's existing search, catalogue, playback, album/playlist queues, local library, database, caching, lyrics, download, account/sync, and customisation implementations rather than rebuilding those systems.

## 0.1.1 — CI resource-linking hotfix

- Added `Theme.mochy` as a compatibility alias inheriting from `Theme.LyricGlow`, because existing manifest activities still reference the upstream style name.
- Documented the separate Kotlin compiler failure as unresolved until its actual compiler diagnostic is supplied.
