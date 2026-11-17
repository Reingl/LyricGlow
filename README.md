# LyricGlow Android Starter

An initial native Android project shell for LyricGlow, an ad-free music app.

## Current status

This is a starter scaffold, not a finished music player. Playback, provider integrations, Google sign-in, Discord integration, and production security review still need implementation.

## Open in Android Studio or build remotely

- Android package: `com.lyricglow.app`
- Minimum Android: API 26
- Compile SDK: 36
- Supabase URL is configured in `app/build.gradle.kts`.
- Put your Supabase publishable key in `local.properties` as `SUPABASE_PUBLISHABLE_KEY=...`.

Never commit service-role keys, database passwords, OAuth client secrets, or signing keys.
