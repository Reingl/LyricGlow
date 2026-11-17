-- Starter schema only. Review and test before applying to production.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text,
  created_at timestamptz not null default now()
);

create table if not exists public.user_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  history_sync text not null default 'cloud' check (history_sync in ('cloud','local')),
  profile_visibility text not null default 'friends' check (profile_visibility in ('friends','private')),
  share_now_playing boolean not null default false,
  share_recent_history boolean not null default false,
  share_stats boolean not null default false
);

create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  addressee_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','blocked')),
  created_at timestamptz not null default now(),
  unique(requester_id, addressee_id),
  check (requester_id <> addressee_id)
);

create table if not exists public.listening_events (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text,
  track_id text,
  title text,
  artist text,
  duration_seconds integer not null default 0 check (duration_seconds >= 0),
  played_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.user_settings enable row level security;
alter table public.friendships enable row level security;
alter table public.listening_events enable row level security;

create policy "profile owner read" on public.profiles for select using (auth.uid() = id);
create policy "profile owner insert" on public.profiles for insert with check (auth.uid() = id);
create policy "profile owner update" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
create policy "settings owner all" on public.user_settings for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "friendship participants read" on public.friendships for select using (auth.uid() = requester_id or auth.uid() = addressee_id);
create policy "requester creates request" on public.friendships for insert with check (auth.uid() = requester_id and status = 'pending');
create policy "participants update friendship" on public.friendships for update using (auth.uid() = requester_id or auth.uid() = addressee_id) with check (auth.uid() = requester_id or auth.uid() = addressee_id);
create policy "event owner read" on public.listening_events for select using (auth.uid() = user_id);
create policy "event owner insert" on public.listening_events for insert with check (auth.uid() = user_id);
create policy "event owner delete" on public.listening_events for delete using (auth.uid() = user_id);
