# LyricGlow Android

An ad-free, highly customisable Android music client built around YouTube Music's public catalogue. Version **0.5.0** introduces the first real provider and playback backend.

## Included in 0.5.0
- YouTube Music-compatible public catalogue search for songs and albums, powered by NewPipeExtractor.
- Real song search results with title, artist, duration and artwork metadata.
- Album search and album playback queueing.
- Audio stream resolution on demand; expiring signed stream URLs are never persisted.
- Media3 ExoPlayer background playback, system media session, notification/lock-screen/Bluetooth controls and queue support.
- Gapless-capable Media3 playlist playback.
- 512 MiB LRU streaming cache for recently played audio segments, plus a seven-day SQLite metadata cache.
- Local saved-track library.
- Optional LyricGlow account sign-up/sign-in through Supabase Auth and a saved-track cloud backup action.
- Supabase migration for saved tracks, user playlists, playlist tracks and playback preferences with row-level security.
- Mono Green, Carbon and Paper themes, selectable accents, home layouts and experimental forced blur.

## Important provider/account notes
LyricGlow is an unofficial third-party client. YouTube Music does not offer a supported public API for this full client workflow; NewPipeExtractor parses public YouTube endpoints and can break when YouTube changes them. Search and stream extraction may be rate-limited, unavailable for some tracks, or temporarily broken. This integration does not bypass age gates, account restrictions, Premium entitlements or other access controls. It does not use or store Google passwords/cookies.

The LyricGlow account is a separate Supabase account used for LyricGlow cloud features. It is **not** a YouTube/Google login. The current sync action backs up saved tracks; full two-way sync for playlists, history and player preferences is a later milestone.

NewPipeExtractor is GPL-3.0-or-later. Review `LICENSE` and `THIRD_PARTY_NOTICES.md` before redistributing builds. Its use may affect the licensing obligations of distributed LyricGlow binaries.

## Build
1. Extract the ZIP and open `LyricGlow-main` in Android Studio.
2. Install Android SDK Platform 36 and compatible Build Tools.
3. Sync Gradle and build the `app` configuration.
4. Run on Android 8.0 (API 26) or newer.

The archive excludes machine-specific `local.properties`, signing keys and the Gradle wrapper JAR. GitHub Actions installs Gradle 8.11.1 and Android SDK 36.

## Supabase setup
1. In Supabase, enable Email authentication and configure email confirmation as desired.
2. Run `backend/supabase/migrations/001_initial_schema.sql`, then `002_music_backend.sql` in the SQL editor.
3. Copy `local.properties.example` to `local.properties` and set `SUPABASE_PUBLISHABLE_KEY=your_publishable_or_anon_key`.
4. For GitHub Actions, add the same value as repository secret `SUPABASE_PUBLISHABLE_KEY`.

Never put a service-role key, database password or OAuth client secret in the Android app or public repository. The publishable/anon key is intended to be exposed in client apps; database access is constrained by RLS.

## Cache behaviour
- Search metadata cache: SQLite, seven-day expiry.
- Audio stream cache: Media3 `SimpleCache`, 512 MiB LRU under the app cache directory. Android may clear this directory under storage pressure. This is streaming cache, not a permanent offline-download library.
- Signed playback URLs are resolved when needed and are not cached because they expire.

## Build verification
The source archive has not been compiled in this environment because Gradle/Android SDK are unavailable here. Run GitHub Actions or Android Studio before treating this release as build-verified. See `BUILD_STATUS.md`.
