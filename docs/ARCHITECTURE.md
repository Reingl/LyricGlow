# Architecture plan

- Kotlin + Jetpack Compose: native Android interface.
- AndroidX Media3: playback, media session, audio focus, and notifications.
- Room: offline library/cache and queued listening events.
- Supabase Auth + PostgreSQL + Row Level Security: accounts and synced data.
- Supabase Edge Functions: privileged integrations and Discord OAuth callbacks.
- AGSL/Compose Canvas: lyric and artwork effects.
- Provider adapters: isolate each music source behind a common interface.

Do not treat third-party provider access as permission to redistribute or stream copyrighted content. Follow each provider's terms and applicable law.
