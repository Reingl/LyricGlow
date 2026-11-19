# Build status — 0.5.0

Implemented source changes: YouTube Music public catalogue search, song and album result mapping, audio stream resolution, album queue creation, local metadata cache, saved-track library, Media3 streaming cache, Supabase email auth and saved-track backup, and the 002 cloud schema migration.

Not verified: `assembleDebug` was not run in this environment because no Gradle executable or Android SDK is installed. The first CI run is required to verify dependency resolution and Kotlin/Compose compilation.

Known limitations: YouTube endpoints and stream extraction are unofficial and may change or rate-limit clients. Some videos/tracks will not resolve. No Google/YouTube account login or Premium entitlement is implemented. Cloud sync currently backs up saved tracks only; playlist/history/preferences sync, true offline downloads, synced lyrics, recommendations and advanced playback effects remain future work.
