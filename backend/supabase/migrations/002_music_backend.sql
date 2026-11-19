-- LyricGlow 0.5.0 cloud library schema. Apply after 001_initial_schema.sql.
create table if not exists public.saved_tracks (
    user_id uuid not null references auth.users(id) on delete cascade,
    provider text not null default 'youtube_music',
    provider_id text not null,
    title text not null,
    artist text not null default 'Unknown artist',
    artwork_url text,
    page_url text not null,
    saved_at timestamptz not null default now(),
    primary key (user_id, provider, provider_id)
);

create table if not exists public.user_playlists (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    name text not null check (length(trim(name)) between 1 and 80),
    description text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.user_playlist_tracks (
    playlist_id uuid not null references public.user_playlists(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    position integer not null check (position >= 0),
    provider text not null default 'youtube_music',
    provider_id text not null,
    title text not null,
    artist text not null default 'Unknown artist',
    artwork_url text,
    page_url text not null,
    added_at timestamptz not null default now(),
    primary key (playlist_id, position),
    unique (playlist_id, provider, provider_id)
);

create table if not exists public.user_player_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    audio_quality text not null default 'auto' check (audio_quality in ('auto','low','normal','high')),
    crossfade_ms integer not null default 0 check (crossfade_ms between 0 and 12000),
    gapless_playback boolean not null default true,
    normalize_volume boolean not null default false,
    updated_at timestamptz not null default now()
);

alter table public.saved_tracks enable row level security;
alter table public.user_playlists enable row level security;
alter table public.user_playlist_tracks enable row level security;
alter table public.user_player_preferences enable row level security;

create policy "saved tracks own all" on public.saved_tracks
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "playlists own all" on public.user_playlists
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "playlist tracks own all" on public.user_playlist_tracks
    for all using (auth.uid() = user_id) with check (
        auth.uid() = user_id and exists (
            select 1 from public.user_playlists p where p.id = playlist_id and p.user_id = auth.uid()
        )
    );
create policy "player preferences own all" on public.user_player_preferences
    for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create index if not exists user_playlists_user_updated_idx on public.user_playlists(user_id, updated_at desc);
create index if not exists playlist_tracks_playlist_position_idx on public.user_playlist_tracks(playlist_id, position);
