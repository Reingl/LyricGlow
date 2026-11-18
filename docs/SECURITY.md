# Security and privacy notes

- The Android app must only contain the Supabase project URL and publishable/anon key.
- Never embed service-role keys, database passwords, Discord client secrets or signing keys.
- Enable Row Level Security on every user-owned table and test owner, accepted-friend and unrelated-user access.
- Treat stream URLs and provider tokens as sensitive; do not log credentials.
- Ask permission before publishing listening activity. Default profile visibility should remain friends-only with separate toggles for now-playing, history and statistics.
- Provide account deletion and data export paths before public release.
