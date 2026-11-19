# LyricGlow architecture (0.5.0)

## UI
Jetpack Compose owns navigation, appearance settings, Search, Library, Account and the player surfaces.

## Catalogue/provider
`data/YoutubeMusicRepository` isolates the unofficial public YouTube Music catalogue integration. NewPipeExtractor performs song/album search, playlist loading and on-demand audio stream resolution. The UI stores provider page URLs and stable provider IDs, never expiring signed media URLs.

## Playback
`PlaybackService` owns the Media3 ExoPlayer and MediaSession. Search selections resolve off the main thread and are handed to the session controller. Album selections resolve the album's track list before passing the ordered MediaItems to ExoPlayer. Media3 SimpleCache wraps the HTTP data source with a 512 MiB LRU cache.

## Local data
`MusicMetadataCache` stores search metadata and saved tracks in SQLite. Search metadata expires after seven days. The audio cache lives in the app cache directory and Android may evict it. It is not a permanent download store.

## Cloud
`SupabaseAuthRepository` implements LyricGlow email/password auth. Session tokens are encrypted with an AES-GCM key held in Android Keystore. `SupabaseLibrarySync` backs up saved tracks to Supabase. SQL migrations define the RLS-protected cloud profile, settings, listening, saved-track and playlist tables. Only saved-track upload is wired in this milestone; other sync paths remain future work.

## Provider caveats
YouTube's endpoints are unofficial and may change or rate-limit requests. Stream URLs are short-lived and are resolved immediately before playback. Restricted content and Google/YouTube account login are not implemented.
