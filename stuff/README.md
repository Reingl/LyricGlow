# lyricglow 4 — a YouTube Music app for your terminal

Search, queue, browse your library, and read **word-by-word synced lyrics** over an **animated album cover** — in kitty,
with real typography, depth blur, glow, 60/120 fps motion and Discord Rich Presence. Ad-free: audio comes straight from
`yt-dlp` through `mpv`.

```sh
sh install.sh
lyricglow ytm                 # the app (opens on Quick picks)
lyricglow ytm "daft punk"     # search and play right away
lyricglow --demo              # preview, no account or player needed
lyricglow doctor              # if anything doesn't work
```

It also still works as a lyrics widget for whatever you already play (Spotify, Firefox, mpv, Termux/Android…): just run `lyricglow`.

---

## The three views — press **TAB**

| view | what you see |
|---|---|
| **lyrics** | cover on the left, lyrics sweeping word by word, soft glow, distant lines blur away |
| **album cover** | just the cover — big, centred, animated — with title and artist |
| **pick songs** | the browser: **Search · Quick picks · Albums · Radios · Playlists · Queue** |

`TAB` goes forward, `Shift-TAB` backward. Switching never re-downloads or re-uploads the animated cover.

### Picking songs

Number keys **1–6** (or **← →**) switch tabs:

| tab | what it shows | needs sign-in? |
|---|---|---|
| **1 Search** | `/` to search. Prefix `album:` or `playlist:` to search those. Paste a YouTube / YouTube Music URL (playlists work). | no |
| **2 Quick picks** | your home-feed "Quick picks" (personalised once signed in) | no |
| **3 Albums** | your library albums — or home-feed albums when signed out | library: yes |
| **4 Radios** | "Radio from the current song" + the mixes on your home feed (My Supermix, …) | no |
| **5 Playlists** | **Liked Music**, **History**, and your playlists | **yes** |
| **6 Queue** | what's playing and what's next | no |

`Enter` plays a song (the rest of the list is queued behind it) or opens an album/playlist · `p` play all · `a` add to queue ·
`r` start a radio from the song · `Esc` back · `d` remove (queue). When the queue runs low, **radio autoplay** tops it up.

## Keys

| key | action | key | action |
|---|---|---|---|
| `TAB` / `Shift-TAB` | next / previous view | `/` | search (from any view) |
| `space` | play / pause | `Q` | queue |
| `n` / `p` | next / previous song (`p` = play all inside the picker) | `← →` | seek ∓5 s (switch tab inside the picker) |
| `+` / `-` | volume | `z` | shuffle what's coming |
| `R` | repeat: off → all → one | `l` | **sign in** with your browser's login |
| `s` | saturation style | `y` / `c` | glow style / glow colour |
| `g` / `G` | glow down / up | `v` | effects off / low / high |
| `[` `]` / `0` | lyric sync ∓0.1 s / reset | `L` | low-end mode on/off |
| `i` | toggle cover panel | `d` | debug overlay |
| `r` | refetch lyrics | `h` | key hints |
| `q` | quit | | |

---

## Signing in (easy)

Your library — **Playlists, Liked Music, History, library Albums** — needs your YouTube Music account. Everything else works without.

1. Make sure you're **logged in at music.youtube.com** in your normal browser (Firefox, LibreWolf, Zen, Floorp, Chrome, Chromium, Brave, Vivaldi, Edge, Opera).
2. In the app press **`l`** — or run `lyricglow ytm login`.

That's it. lyricglow reuses the login your browser already has — **nothing to copy, nothing to paste**. If it can't find one,
`lyricglow ytm login` offers to open the login page, waits for you, and tries again.

* Firefox-family browsers are read directly; Chrome-family browsers are decrypted with yt-dlp's cookie reader.
* Several browsers logged in? You're asked which. Force one with `lyricglow ytm login firefox` (or `chrome:Profile 1`).
* Second Google account in the browser? `[ytm] authuser = 1`.
* The result is saved in `~/.config/lyricglow/ytm-auth.json` (mode `0600`). It is a session — treat it like a password.
  `lyricglow ytm logout` deletes it, `lyricglow ytm status` shows who is signed in.
* Session expired (sessions last long, but logging out in the browser ends them)? Press `l` again.
* **Termux:** there is no desktop browser to read, so library sign-in isn't available there. Search, Quick picks, Albums, Radios and playback all work signed-out.
* Advanced / old way: `lyricglow ytm login --manual` (paste request headers from DevTools).

> Needs `ytmusicapi` (`pipx install ytmusicapi`, or `pip install --user --break-system-packages ytmusicapi`). Without it, search and playback still work (via yt-dlp) but Quick picks, Albums, Radios and Playlists don't.

## About "ad-free"

`lyricglow ytm` plays the **audio stream** of a track directly (yt-dlp → mpv), so there is no ad player involved. This isn't an
official client and isn't affiliated with YouTube/Google: it may go against YouTube's terms of service, and it can break whenever
YouTube changes something — when playback stops working, **update yt-dlp** first (`sudo pacman -Syu yt-dlp`). If you pay for
Premium, `[ytm] cookies_from_browser = "firefox"` lets yt-dlp play as you.

---

## Animated album cover — on by default (kitty)

The cover animates like Apple Music's / Spotify's motion artwork, and the backdrop picks up its colours and motion. Sources are tried in order
(`[canvas] sources`):

| source | what it is |
|---|---|
| `local` | your own loops in `~/.config/lyricglow/canvas/` — `Artist - Title.mp4`, `Album.webp`, `<spotify id>.mp4` (mp4/webm/mkv/gif/webp) |
| `endpoints` | URL templates **you** configure — e.g. a self-hosted Spotify-Canvas API. The response is searched for a video URL, so any JSON shape works. |
| `apple` | Apple Music motion artwork via a community REST wrapper (`artwork.m8tec.top`, `amaa.tromme.app`) |

**Privacy:** with `apple` enabled (the default), the **artist and album** of what you play are sent to those community servers.
Remove `"apple"` (or set `sources = []`) to keep everything local. Spotify has no official Canvas API; community ones need your own login cookie and may break — that's why it's an `endpoints` template, not built in.
I couldn't test the live servers from my build environment, so the response parsing is deliberately tolerant.

Needs `ffmpeg` for mp4/HLS loops (GIF/WebP work without). Loops are cached (`~/.cache/lyricglow/canvas`), uploaded to kitty **once** and then looped on the GPU.
Not available on Termux, in tmux, or in low-end mode. `lyricglow --demo` shows a synthetic loop.

## Discord Rich Presence (Linux only)

1. Create an application at <https://discord.com/developers/applications>. Its **name** is what friends see — *"Listening to YouTube Music"*.
2. Put its **Application ID** in the config and enable it:

```toml
[discord]
enabled   = true
client_id = "123456789012345678"
```

Everything else is configurable — presence type (listening/playing/watching), the two text lines (`{title} {artist} {album} {url}` templates),
cover art, progress bar vs elapsed time, pause behaviour, buttons, rate limit, and whether only `ytm` songs or any followed player are shown.
See `example.config.toml`. Works with Discord, Vesktop/arRPC, Flatpak and Snap installs. Discord limits updates, so changes go out at most every 15 s (configurable);
the presence is cleared when you quit. `lyricglow doctor` shows whether Discord's socket was found.

---

## Look & speed

**Glow** — `[glow] style`: `soft` · `neon` · `bloom` · `halo` (only the syllable being sung) · `pulse` · `aurora` (colour gradient) · `none`;
`color`: `album` · `white` · `accent` · `warm` · `cool` · `"#rrggbb"`; plus `strength`, `size`, `follow`. Live: `y` / `c` / `g` / `G`.

**Saturation** — `before` / `after` make not-yet-sung and already-sung words saturated (album colour) or desaturated; `s` cycles presets.

**60 / 120 fps** — `lyricglow --fps 120` (up to 240). Scrolling and cover float run at that rate; the expensive word-sweep re-render has its own cap
(`sweep_hz`, default 40) so 120 fps stays smooth on ordinary CPUs. For kitty itself: `repaint_delay 4`, `input_delay 0`, `sync_to_monitor yes` in `kitty.conf`.

**Low-end mode** (`L`, `--low-end on`, or automatic on ≤ 2 CPUs / when frames stay slow): flat backdrop, no blur/glow blur, no animated cover, 15 fps —
about 10× less CPU and 8× less output in the cell renderer.

**Fonts** — install `inter-font` (or set `font`) for the Apple look.

## Other terminals

lyricglow auto-selects the **kitty** pixel renderer inside kitty and a truecolor **cell** renderer everywhere else (and inside tmux, which swallows graphics).
Ghostty/WezTerm implement much of the kitty protocol: try `--render kitty` (unverified). Cell renderer limits: no animated cover, cover drawn in half-blocks.

---

## Android (Termux)

You get the full cell renderer: lyrics, the `ytm` player, low-end mode. (Animated cover, Discord and library sign-in are desktop features.)

**1. Install the right apps** — from **F-Droid** (or GitHub releases), *not* the Play Store (that Termux is outdated): **Termux** and **Termux:API**.
**2. Get the files into Termux**

```sh
termux-setup-storage                       # allow storage access once
cp ~/storage/downloads/lyricglow/* ~/ && cd ~ && sh install.sh
```

**3. Pick how lyricglow learns what's playing** (only needed for `lyricglow`; `lyricglow ytm` needs nothing — it plays inside Termux itself):

* **Exact sync (recommended)** — uses `dumpsys media_session`. Needs wireless-debugging adb to the phone itself (Android 11+, connected to Wi-Fi):
  1. Settings → About → tap **Build number** 7× → Settings → Developer options → **Wireless debugging** ON.
  2. Tap **Pair device with pairing code**. In Termux (split-screen helps): `adb pair localhost:<pairing port>` and enter the 6-digit code.
  3. `adb connect localhost:<port shown on the Wireless debugging screen>` (this port differs from the pairing port).
  4. `lyricglow doctor` should now say *dumpsys via adb …*. Re-run step 3 after a reboot or Wi-Fi change.
  Shizuku (`rish`) or root also work.
* **Fallback** — Termux:API reads the media notification: open **Termux:API**, grant **Notification access** (Settings → Apps → Special access). You get title/artist only; the clock starts when the song is first seen, so nudge with `[` / `]`.

**4. Run it**: `lyricglow`, or `lyricglow ytm`. Keep it alive with `termux-wake-lock` and exempt Termux from battery optimisation.
`install.sh` also creates **Termux:Widget** shortcuts (`lyricglow`, `lyricglow-music`) for your home screen.
Audio in `ytm` silent? Add `mpv_args = ["--ao=opensles"]` under `[ytm]` (or start `pulseaudio --start`). Slow phone? `--low-end on`.
Anything odd: **`lyricglow doctor`** prints exactly what it can and can't see.

Newer Android versions print the playback state as `state=PLAYING(3)`; older ones as `state=3`. Both are handled.

## Following other players

`lyricglow` follows MPRIS players (via `playerctl`), any **mpv** with an IPC socket (terminal YouTube clients that use mpv work — start mpv with `--input-ipc-server=/tmp/mpvsocket`),
or **anything** through `--cmd 'script'` that prints JSON `{"title","artist","album","position","duration","playing"}` or a tab-separated
`status⇥position⇥duration⇥artist⇥title⇥album` line.

## Config

`~/.config/lyricglow/config.toml` — **`example.config.toml` lists every setting** (56) with comments; unknown keys are reported so typos don't fail silently.
Flat keys (`glow_style = "neon"`) and tables (`[glow] style = "neon"`) both work. Flags: `lyricglow --help`.

## Troubleshooting

* **`lyricglow doctor`** first.
* *Nothing but text, no images* — your terminal isn't kitty, or you're in tmux: `--render cells`.
* *Playback fails / skips* — update yt-dlp.
* *Quick picks / Albums / Radios say "needs ytmusicapi"* — install it (see above).
* *"no YouTube login found"* — log in at music.youtube.com in the browser, then press `l` again (Firefox can take a minute to write cookies).
* *Animated cover missing* — `ffmpeg` installed? low-end mode off? kitty (not tmux)? `lyricglow doctor` → "animated cover".
* *Lyrics early/late* — `[` `]` (Bluetooth adds ~150–250 ms).
* *Laggy* — `d` shows frame cost; try `--fps 60`, `L`, or `shader = "low"`.
* Errors are logged to `~/.cache/lyricglow/log.txt`.

## How it works (short)

Lyrics are syllable-timed TTML from the Better Lyrics API; every character gets an interpolated start/end. The kitty renderer draws each lyric line as its own RGBA image through the
kitty graphics protocol (uploaded once per look, then only *moved* — scrolling costs almost nothing) and re-renders only the line being sung. The backdrop, cover (with a spring "pop" on pause),
progress bar and animated loop are images too. The player drives `mpv` over its JSON IPC socket; sign-in turns your browser's YouTube cookies into the headers `ytmusicapi` expects; Discord is spoken to directly over its local IPC socket.

Lyrics: [Better Lyrics](https://better-lyrics.boidu.dev). Music metadata and library: [ytmusicapi](https://github.com/sigma67/ytmusicapi). Playback: [yt-dlp](https://github.com/yt-dlp/yt-dlp) + [mpv](https://mpv.io).

## Lyric styles, backgrounds, beat and cyberpsychosis (cells renderer first)

Press **T** to cycle lyric styles, **b** for backgrounds, **B** beat, **X** cyberpsychosis, **F** the silhouettes. All are also config / flags.

| style | what it is |
|---|---|
| `classic` | the scrolling Apple-Music-style list |
| `kinetic` | one phrase at a time, huge spaced capitals, words pop in as sung, comic starbursts |
| `word` | the same, but **one word** at a time |
| `immersive` | words set off effects **as they are sung**: *burn* → flames and embers, *water/rain* → waves and drops, *drinks/wine* → two wine glasses clink and the wine spills, snow, hearts, gold sparks, lightning, magic, space, food, smoke, danger, glitch … **~950 words, 30 effect types**; add yours with `~/.config/lyricglow/wordfx.txt` (`fire: wildfire cinders`) or `[[word_effect]]` in an addon. Every effect is armed per word, so **rewind and it plays again**. *you* grows into a block-letter **YOU** scaled by the loudness in dB (`grow_db_floor`, `grow_db_label` in the config) |
| `slice` | glossy typography (stacked / tilted / arced / tiled). Used automatically for the line “got a shotgun in my pocket” together with the immersive background |
| `caption` | a caption box; position, colours and width come from `[caption]` in an addon; addon **buttons** work by tap (Termux), click or key |
| `vintage` | pastel collage: short lines scatter as pastel words on white, longer lines appear in a pixel speech bubble over a pastel sky |

**Backgrounds**: any image, GIF or video (`--bg FILE`, `[background] file`, or per-song rules in an addon). `--bg-mode immersive` gives a dark purple field with drifting glossy hearts, diamonds, cherries, pineapples and sparkles that pulse with the beat. Low-end mode uses smaller, fewer frames (≤32 at 320 px).

**Beat**: follows bass, kick, low synth and hi-hats and deliberately skips the vocal range; only a track with no beat sounds falls back to voices. Best sources: ffmpeg on the real audio (YouTube Music mode, local files), `parec` live capture on Linux; otherwise lyric timing. Termux: `pkg install ffmpeg`.

**Cyberpsychosis** (automatic on “I Really Want to Stay at Your House”, or `X` for any song): lyrics *duplicate and stutter* on the beat (no corrupted letters), the cover splits, the screen shakes, and a built-in Earth-over-the-moon scene appears (hide the two silhouettes with `F` / `--no-figures`). Effects are rate-limited (no strobing); `--cyber off` disables everything.

**Addons** (`*.add`, TOML — see `example.addon.add`): per-song backgrounds, lyric rules, caption placement, custom buttons and extra effect words. Shell actions are off unless `[addons] shell = true`.

**Status**: all of the above runs in the cells renderer (Termux and any terminal). The kitty renderer already has beat, cyberpsychosis, image/GIF/video backgrounds, kinetic and word; `immersive`, `slice`, `caption`, `vintage`, the candy background and addon buttons fall back to the classic list there for now.
