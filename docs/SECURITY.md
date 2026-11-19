# Security notes

- The Supabase publishable/anon key is a client key, not a secret. Never embed a service-role key or database password in the app.
- Supabase tables must retain row-level security. Apply both SQL migrations and verify policies in the Supabase dashboard before production use.
- LyricGlow account passwords are sent only to the configured Supabase Auth endpoint over HTTPS and are not persisted by the app.
- Access and refresh tokens are encrypted using an AES-GCM key stored in Android Keystore.
- YouTube/Google credentials are not collected or stored. LyricGlow sign-in is a separate account.
- Signed media URLs expire and are not written to the metadata database. The stream cache is private to the app sandbox.
- The app currently stores saved metadata and cached stream bytes locally. Clear app data to remove them.
