# LyricGlow Android

An ad-free, customisable Android music app foundation. This milestone adds native background playback using AndroidX Media3.

## Included
- Kotlin + Jetpack Compose app shell
- Media3 ExoPlayer playback service and Android MediaSession
- Notification, lock-screen, Bluetooth and headset media controls
- Direct audio/stream URL playback
- Queue support (add, next, previous, remove/clear)
- Playback state, seek bar and elapsed/total time
- Supabase URL and publishable-key configuration placeholder
- Initial database migration retained as a future backend foundation

## Important scope
This is a working **source project foundation**, not a finished YouTube Music replacement. It currently plays direct audio URLs that the app is allowed to access. Provider integrations, account sign-in, synced lyrics, animated artwork, add-ons and production cloud sync are later milestones. Do not bypass provider restrictions or stream content without the required rights.

## Open and build
1. Download and extract this ZIP.
2. Open the extracted `LyricGlow` folder in Android Studio.
3. Install Android SDK Platform 36 and a compatible Android SDK Build Tools version when prompted.
4. Let Gradle sync.
5. Run the `app` configuration on Android 8.0 (API 26) or newer.

The ZIP intentionally does not include machine-specific `local.properties`, signing keys, or a Gradle wrapper JAR. Android Studio can configure the local SDK path. If you use a terminal, run Gradle from an installed Gradle 8.11+ distribution with JDK 17.

## Supabase configuration
The Supabase project URL is configured in `app/build.gradle.kts`. Copy `local.properties.example` to `local.properties` and set `SUPABASE_PUBLISHABLE_KEY` if/when the Supabase client is enabled. Never put a service-role key, database password, or OAuth client secret in the Android app or public repository.

## Project layout
- `app/src/main/java/com/lyricglow/app/` - Android app and playback implementation
- `backend/supabase/migrations/` - initial schema foundation
- `docs/` - architecture, security and roadmap notes

## Build status
Source assembled for Android Studio. It has not been built on a real Android SDK in this environment; verify Gradle sync/build on your machine before treating it as release-ready.


## Build an APK without Android Studio (GitHub Actions)
This repository includes `.github/workflows/build-apk.yml`.
1. Upload/extract the project files into the root of your GitHub repository, including the hidden `.github` folder.
2. Open the repository on GitHub and select **Actions**.
3. Select **Build LyricGlow APK**. If it has not run automatically after your upload, choose **Run workflow**.
4. Wait for the workflow to finish, open the successful run, and download the `LyricGlow-debug-apk` artifact.
5. Extract the artifact ZIP to get `app-debug.apk`, then install it on your Android phone. You may need to allow installs from your browser/file manager.

GitHub Actions performs the build on a hosted Linux runner; Android Studio is not required on your phone. This workflow creates a debug APK for testing, not a signed Play Store release.
