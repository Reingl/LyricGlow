-- Initial LyricGlow schema foundation. Review and test RLS before production use.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique,
    display_name text,
    avatar_url text,
    visibility text not null default 'friends'
        check (visibility in ('private', 'friends', 'public')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.user_settings (
    user_id uuid primary key references auth.users(id) on delete cascade,
    share_now_playing boolean not null default false,
    share_history boolean not null default false,
    share_stats boolean not null default false,
    updated_at timestamptz not null default now()
);

create table if not exists public.friendships (
    id uuid primary key default gen_random_uuid(),
    requester_id uuid not null references auth.users(id) on delete cascade,
    addressee_id uuid not null references auth.users(id) on delete cascade,
    status text not null default 'pending'
        check (status in ('pending', 'accepted', 'blocked')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (requester_id <> addressee_id),
    unique (requester_id, addressee_id)
);

create table if not exists public.listening_events (
    id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    track_provider text,
    provider_track_id text,
    track_title text,
    artist_name text,
    played_at timestamptz not null default now(),
    listened_ms bigint not null default 0 check (listened_ms >= 0)
);

create index if not exists listening_events_user_played_idx
    on public.listening_events(user_id, played_at desc);

alter table public.profiles enable row level security;
alter table public.user_settings enable row level security;
alter table public.friendships enable row level security;
alter table public.listening_events enable row level security;

create policy "profiles read own" on public.profiles
    for select using (auth.uid() = id);
create policy "profiles update own" on public.profiles
    for update using (auth.uid() = id) with check (auth.uid() = id);

create policy "settings read own" on public.user_settings
    for select using (auth.uid() = user_id);
create policy "settings insert own" on public.user_settings
    for insert with check (auth.uid() = user_id);
create policy "settings update own" on public.user_settings
    for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "friendships read participant" on public.friendships
    for select using (auth.uid() in (requester_id, addressee_id));
create policy "friendships request own" on public.friendships
    for insert with check (auth.uid() = requester_id and status = 'pending');

create policy "listening events read own" on public.listening_events
    for select using (auth.uid() = user_id);
create policy "listening events insert own" on public.listening_events
    for insert with check (auth.uid() = user_id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, display_name)
    values (new.id, coalesce(new.raw_user_meta_data ->> 'name', new.email));
    insert into public.user_settings (user_id) values (new.id);
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute procedure public.handle_new_user();
