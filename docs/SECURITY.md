# Security checklist

- APK may contain only the Supabase URL and publishable/anon key.
- Never include service-role keys, database passwords, OAuth client secrets, or signing keys in Git.
- Enforce RLS for every user-owned table and test owner/friend/unrelated-user access.
- Validate listening-event durations server-side; use client-generated UUIDs for idempotency.
- Implement account deletion and data export before public release.
- This starter migration is not production-reviewed. Test in a separate Supabase project first.
