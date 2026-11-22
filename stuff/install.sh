#!/usr/bin/env sh
# lyricglow installer — Arch Linux and Termux (Android)
set -e
here="$(cd "$(dirname "$0")" && pwd)"

if [ -n "$PREFIX" ] && [ -d /data/data/com.termux ]; then
    echo "==> Termux detected  (cell renderer; kitty-only extras like the animated cover and Discord are desktop features)"
    pkg install -y python python-pillow termux-api android-tools mpv yt-dlp ffmpeg
    pip install ytmusicapi >/dev/null 2>&1 && echo "    ytmusicapi installed" || echo "    (ytmusicapi optional — search/play still work via yt-dlp; Quick picks & Albums need it)"
    dest="$PREFIX/bin/lyricglow"
    mkdir -p "$HOME/.shortcuts"                                   # Termux:Widget home-screen buttons
    printf '#!/data/data/com.termux/files/usr/bin/sh\ntermux-wake-lock\nexec lyricglow\n'    > "$HOME/.shortcuts/lyricglow"
    printf '#!/data/data/com.termux/files/usr/bin/sh\ntermux-wake-lock\nexec lyricglow ytm\n' > "$HOME/.shortcuts/lyricglow-music"
    chmod +x "$HOME/.shortcuts/lyricglow" "$HOME/.shortcuts/lyricglow-music"
else
    echo "==> Linux detected"
    if command -v pacman >/dev/null 2>&1; then
        sudo pacman -S --needed --noconfirm python playerctl python-pillow python-numpy fontconfig inter-font mpv yt-dlp ffmpeg
    else
        echo "!! not Arch: install python3, playerctl, python-pillow, python-numpy, mpv, yt-dlp, ffmpeg and a TTF font (Inter recommended)"
    fi
    python3 -m pip install --user --break-system-packages ytmusicapi >/dev/null 2>&1 \
        && echo "    ytmusicapi installed" \
        || echo "    !! ytmusicapi not installed — Quick picks, Albums, Radios, Playlists need it:  pipx install ytmusicapi  (or pip install --user --break-system-packages ytmusicapi)"
    mkdir -p "$HOME/.local/bin" "$HOME/.local/share/applications"
    dest="$HOME/.local/bin/lyricglow"
    [ -f "$here/lyricglow.desktop" ] && install -m 644 "$here/lyricglow.desktop" "$HOME/.local/share/applications/lyricglow.desktop"
fi

install -m 755 "$here/lyricglow.py" "$dest"
# the web app: backend + website (so `lyricglow web` works from anywhere)
share="${XDG_DATA_HOME:-$HOME/.local/share}/lyricglow"
[ -n "$PREFIX" ] && [ -d /data/data/com.termux ] && share="$PREFIX/share/lyricglow"
mkdir -p "$share"
install -m 644 "$here/lyricglow_web.py" "$share/lyricglow_web.py"
rm -rf "$share/webplayfr" && cp -r "$here/webplayfr" "$share/webplayfr"
[ -f "$here/lyricglow-web.service" ] && cp "$here/lyricglow-web.service" "$share/lyricglow-web.service"
cfgdir="${XDG_CONFIG_HOME:-$HOME/.config}/lyricglow"
mkdir -p "$cfgdir/canvas"
[ -f "$cfgdir/config.toml" ] || cp "$here/example.config.toml" "$cfgdir/config.toml"

echo "==> installed to $dest        config: $cfgdir/config.toml"
echo
echo "    lyricglow ytm           the YouTube Music app          (TAB = lyrics / cover / pick songs)"
echo "    lyricglow ytm login     sign in with your browser's YouTube login (or press  l  in the app)"
echo "    lyricglow web           the web app (open the printed link from your phone, e.g. over Tailscale)"
echo "    lyricglow               lyrics for whatever is already playing"
echo "    lyricglow --demo        preview, no player needed"
echo "    lyricglow doctor        if anything doesn't work"
case ":$PATH:" in *":$HOME/.local/bin:"*) ;; *) [ -z "$PREFIX" ] && echo "    note: add ~/.local/bin to your PATH" ;; esac
