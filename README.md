# LyricGlow Android

An ad-free, customisable Android music app foundation. Version **0.4.0** refreshes the interface around a monochrome visual system, adds persistent accent customisation, and records real local listening history.

## What is included
- Kotlin + Jetpack Compose app shell.
- Media3 ExoPlayer playback service and Android MediaSession.
- Notification, lock-screen, Bluetooth and headset media controls.
- Direct audio/stream URL playback, queue add/remove, seek controls and full-screen player.
- Standard and Immersive home layouts.
- Mono Green (default), Carbon and Paper themes.
- Five selectable accent colours, saved across launches.
- Local Recently Played history: a track is recorded only after it has actually played for at least 10 seconds; up to 30 entries are retained.
- Optional translucent player surface and experimental forced blur toggle with an in-app performance warning.
- Four-tab navigation: Home, Search, Library and Profile.
- Supabase URL and publishable-key configuration placeholder; initial database migration retained.

## Honest feature boundaries
This is not yet a YouTube Music replacement. Playback currently supports direct audio URLs the app can access. Search/provider integration, account sign-in, synced lyrics, real album art, recommendation feeds, cloud history sync and a full-screen effect renderer are not implemented. The home screen intentionally does not invent songs, albums, mixes or recommendations. The Library remains an honest empty state until library persistence is implemented.

Forced blur uses Compose's blur modifier on the mini-player layer. Android versions, GPU drivers and device performance can still limit the effect; the setting cannot override platform-level rendering restrictions.

## Build
1. Extract the ZIP and open the `LyricGlow-main` folder in Android Studio.
2. Install Android SDK Platform 36 and compatible Build Tools.
3. Sync Gradle and build the `app` configuration.
4. Run on Android 8.0 (API 26) or newer.

The source archive excludes machine-specific `local.properties`, signing keys and the Gradle wrapper JAR. The GitHub Actions workflow builds with JDK 17, Android SDK 36 and Gradle 8.11.1.

## Supabase configuration
The project URL is configured in `app/build.gradle.kts`. Copy `local.properties.example` to `local.properties` and set `SUPABASE_PUBLISHABLE_KEY` if/when a Supabase client is enabled. Never put a service-role key, database password or OAuth client secret in the Android app or public repository.

## Build verification
The source has been edited and statically inspected in this environment, but no Android SDK/Gradle installation was available to run `assembleDebug`. Treat the first GitHub Actions or Android Studio build as the compile verification step. See `BUILD_STATUS.md` for details.
