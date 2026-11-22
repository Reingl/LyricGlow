// preferences (saved in this browser) + a tiny event bus
const KEY = "lg.prefs.v1";
const defaults = { theme: "dark", accent: "clay", surface: "soft", scale: 1, lyric: 1, blur: true, lyricOffset: 0, stream: "server", bitrate: 160,
  normalize: false, radio: true, volume: 0.8, reduceMotion: false, autoLyrics: false };
export const prefs = Object.assign({}, defaults, (() => { try { return JSON.parse(localStorage.getItem(KEY) || "{}"); } catch { return {}; } })());
const subs = new Set();
export const on = (f) => (subs.add(f), () => subs.delete(f));
export const emit = (k, v) => subs.forEach((f) => f(k, v));
export function applyPrefs() {
  const r = document.documentElement;
  r.dataset.theme = prefs.theme === "auto" ? (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark") : prefs.theme;
  r.dataset.accent = prefs.accent; r.dataset.surface = prefs.surface; r.dataset.motion = prefs.reduceMotion ? "reduced" : "full";
  r.style.setProperty("--scale", prefs.scale); r.style.setProperty("--lyric", prefs.lyric);
}
export function setPref(k, v) { prefs[k] = v; localStorage.setItem(KEY, JSON.stringify(prefs)); applyPrefs(); emit("pref", k); }
