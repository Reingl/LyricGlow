// queue + playback. Two <audio> elements: the next song is preloaded on the idle one and swapped in when the current one ends (near-gapless).
import { api, streamUrl, imgUrl } from "./api.js";
import { prefs, emit } from "./store.js";
const KEY = "lg.queue.v1";
export const st = { queue: [], index: -1, playing: false, time: 0, dur: 0, shuffle: false, repeat: "off", errors: 0 };
const els = [new Audio(), new Audio()];
let ai = 0;
const act = () => els[ai];
export const cur = () => st.queue[st.index] || null;
export const pos = () => act().currentTime || 0;
function load(el, tr) { el.dataset.id = tr.id; el.src = streamUrl(tr.id, prefs); el.volume = prefs.volume; el.preload = "auto"; }
function save() { try { localStorage.setItem(KEY, JSON.stringify({ q: st.queue.slice(0, 500), i: st.index, s: st.shuffle, r: st.repeat })); } catch { /* full */ } }
els.forEach((a, i) => {
  a.addEventListener("timeupdate", () => { if (i === ai) { st.time = a.currentTime; emit("time"); } });
  a.addEventListener("durationchange", () => { if (i === ai && isFinite(a.duration)) { st.dur = a.duration; emit("time"); } });
  a.addEventListener("play", () => { if (i === ai) { st.playing = true; st.errors = 0; emit("play"); } });
  a.addEventListener("pause", () => { if (i === ai && !a.ended) { st.playing = false; emit("play"); } });
  a.addEventListener("ended", () => { if (i === ai) advance(); });
  a.addEventListener("error", () => { if (i === ai && a.getAttribute("src")) onError(); });
});
function onError() {
  const t = cur(); st.playing = false; emit("play"); emit("error", t);
  if (++st.errors < 3) setTimeout(() => next(), 700);
}
function nextIndex() {
  if (!st.queue.length) return -1;
  if (st.repeat === "one") return st.index;
  const n = st.index + 1;
  return n < st.queue.length ? n : st.repeat === "all" ? 0 : -1;
}
function preload() {
  const n = nextIndex(); if (n < 0 || n === st.index) return;
  const o = els[1 - ai], t = st.queue[n];
  if (o.dataset.id !== t.id) load(o, t);
  if (prefs.stream === "server" || prefs.stream === "direct") api("/api/prefetch", { method: "POST", body: { ids: [t.id], mode: "direct" } }).catch(() => {});
}
function media() {
  if (!("mediaSession" in navigator)) return;
  const t = cur(); if (!t) return;
  navigator.mediaSession.metadata = new MediaMetadata({ title: t.title, artist: t.artist || "", album: t.album || "", artwork: t.art ? [{ src: imgUrl(t.art), sizes: "512x512" }] : [] });
  const set = (a, f) => { try { navigator.mediaSession.setActionHandler(a, f); } catch { /* unsupported */ } };
  set("play", () => toggle(true)); set("pause", () => toggle(false)); set("previoustrack", prev); set("nexttrack", next);
  set("seekto", (d) => seek(d.seekTime)); set("seekbackward", () => seek(pos() - 10)); set("seekforward", () => seek(pos() + 10));
}
export async function playIndex(i) {
  if (i < 0 || i >= st.queue.length) return;
  const t = st.queue[i], was = act(), o = els[1 - ai];
  st.index = i; st.time = 0; st.dur = t.dur || 0; was.pause();
  if (o.dataset.id === t.id && o.getAttribute("src") && o.readyState >= 2) { ai = 1 - ai; act().currentTime = 0; } else { load(act(), t); }
  if (was !== act()) { was.removeAttribute("src"); was.load(); delete was.dataset.id; }
  emit("track"); emit("queue"); media(); save();
  try { await act().play(); } catch { st.playing = false; emit("play"); }
  preload();
}
export function play(items, start = 0) { st.queue = items.slice(); st.shuffle = false; return playIndex(start); }
export function enqueue(items, asNext = false) {
  const have = new Set(st.queue.map((t) => t.id)), add = items.filter((t) => !have.has(t.id));
  if (!add.length) return 0;
  const at = asNext && st.index >= 0 ? st.index + 1 : st.queue.length;
  st.queue.splice(at, 0, ...add);
  if (st.index < 0) { playIndex(at); } else { emit("queue"); save(); preload(); }
  return add.length;
}
export function removeAt(i) {
  if (i < 0 || i >= st.queue.length) return;
  st.queue.splice(i, 1);
  if (i < st.index) st.index--; else if (i === st.index) { if (st.queue.length) playIndex(Math.min(i, st.queue.length - 1)); else { act().pause(); st.index = -1; emit("track"); } }
  emit("queue"); save();
}
export function move(i, d) {
  const j = i + d; if (i < 0 || j < 0 || i >= st.queue.length || j >= st.queue.length) return i;
  [st.queue[i], st.queue[j]] = [st.queue[j], st.queue[i]];
  if (st.index === i) st.index = j; else if (st.index === j) st.index = i;
  emit("queue"); save(); preload(); return j;
}
export function clearUpcoming() { const n = st.queue.length - st.index - 1; if (n > 0) st.queue.length = st.index + 1; emit("queue"); save(); return Math.max(n, 0); }
export function toggle(force) {
  const a = act(); if (!a.getAttribute("src") && cur()) load(a, cur());
  const want = force === undefined ? a.paused : force;
  if (want) a.play().catch(() => {}); else a.pause();
}
export function seek(t) { const a = act(); if (isFinite(t)) { a.currentTime = Math.max(0, Math.min(t, a.duration || t)); st.time = a.currentTime; emit("time"); } }
export function setVolume(v) { prefs.volume = Math.max(0, Math.min(1, v)); els.forEach((a) => (a.volume = prefs.volume)); localStorage.setItem("lg.prefs.v1", JSON.stringify(prefs)); emit("volume"); }
export function toggleShuffle() {
  st.shuffle = !st.shuffle;
  if (st.shuffle && st.index >= 0) { const head = st.queue.slice(0, st.index + 1), tail = st.queue.slice(st.index + 1); for (let i = tail.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [tail[i], tail[j]] = [tail[j], tail[i]]; } st.queue = head.concat(tail); preload(); }
  emit("queue"); emit("mode"); save();
}
export function cycleRepeat() { st.repeat = { off: "all", all: "one", one: "off" }[st.repeat]; emit("mode"); save(); preload(); }
export function next() { const n = st.index + 1; if (n < st.queue.length) playIndex(n); else if (st.repeat === "all" && st.queue.length) playIndex(0); else advance(true); }
export function prev() { if (pos() > 3 || st.index <= 0) seek(0); else playIndex(st.index - 1); }
async function advance(manual = false) {
  const n = nextIndex();
  if (n >= 0 && !(manual && n === st.index)) return playIndex(n === st.index && manual ? n : n);
  if (prefs.radio && cur()) {                                   // end of the queue: keep going with songs like this one
    try {
      const r = await api(`/api/radio?id=${encodeURIComponent(cur().id)}`), have = new Set(st.queue.map((t) => t.id)), add = (r.tracks || []).filter((t) => !have.has(t.id));
      if (add.length) { st.queue.push(...add); emit("queue"); return playIndex(st.index + 1); }
    } catch { /* no radio available */ }
  }
  st.playing = false; emit("play");
}
export function restore() {
  try {
    const d = JSON.parse(localStorage.getItem(KEY) || "{}");
    if (Array.isArray(d.q) && d.q.length) { st.queue = d.q; st.index = Math.min(d.i ?? -1, d.q.length - 1); st.shuffle = !!d.s; st.repeat = d.r || "off"; if (cur()) { st.dur = cur().dur || 0; load(act(), cur()); } }
  } catch { /* corrupt */ }
  emit("queue"); emit("track");
}
