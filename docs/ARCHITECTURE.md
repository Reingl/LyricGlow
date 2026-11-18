# Architecture

## Playback
`PlaybackService` owns a Media3 ExoPlayer and MediaSession. The Activity connects with a MediaController. UI recreation does not own or destroy the player.

## Provider boundary
Provider adapters should resolve authorised catalog items into the app's `Track` model and a playable URI accepted by the provider's terms. The playback layer should not contain provider-specific scraping logic.

## Planned modules
- `catalog`: search, browse and provider adapters
- `lyrics`: timed lyrics model, TTML/LRC parsing and word-level sync
- `artwork`: animated image/video decoding with bounded memory and frame rate
- `library`: Room cache for playlists, favourites and queue
- `account`: Supabase Auth and profile
- `sync`: idempotent listening events and cloud sync
- `extensions`: signed/declarative ZIP add-ons; no arbitrary code execution
