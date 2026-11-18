# LyricGlow Android

An ad-free, customisable Android music app foundation. Version 0.3.0 adds selectable home layouts, persistent appearance settings, skeletal theme previews, and retains the Media3 direct-stream playback foundation.

## Included in this milestone
- Kotlin + Jetpack Compose app shell
- Media3 ExoPlayer playback service and Android MediaSession
- Notification, lock-screen, Bluetooth and headset media controls
- Direct audio/stream URL playback and queue add/remove
- Playback seek controls and full-screen player
- Two home layouts: Top Picks and Immersive
- Three visual themes: Crystal, Nocturne and Atelier
- Settings > Appearance with persistent layout/theme/section preferences
- Lightweight skeletal previews that do not load album art
- Adaptive translucent-surface toggle (native real-time blur renderer is not yet implemented)
- Supabase URL and publishable-key configuration placeholder
- Initial database migration retained as a future backend foundation

## Important scope
This is a working source-project milestone, not a finished YouTube Music replacement. It currently plays direct audio URLs that the app is allowed to access. Provider integrations, account sign-in, synced lyrics, real album-art recommendations, native real-time Liquid Glass blur, and production cloud sync remain future milestones.

## Open and build
1. Download and extract this ZIP.
2. Open the extracted `LyricGlow` folder in Android Studio.
3. Install Android SDK Platform 36 and compatible Build Tools when prompted.
4. Let Gradle sync and build the `app` configuration.
5. Run on Android 8.0 (API 26) or newer.

The ZIP intentionally excludes machine-specific `local.properties`, signing keys, and the Gradle wrapper JAR. Android Studio can configure the SDK path. The GitHub Actions workflow uses JDK 17, Android SDK 36 and Gradle 8.11.1 to build a debug APK.

## Supabase configuration
The Supabase project URL is configured in `app/build.gradle.kts`. Copy `local.properties.example` to `local.properties` and set `SUPABASE_PUBLISHABLE_KEY` if/when the Supabase client is enabled. Never put a service-role key, database password, or OAuth client secret in the Android app or public repository.

## Build verification
This source ZIP was assembled from the repository source and updated for the appearance milestone. It has not been compiled against an Android SDK in this environment. Run Gradle sync/build before treating it as verified.
