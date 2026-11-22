// pages: home, search (All / Songs / Albums / Artists / Playlists), album / artist / playlist pages, library, queue
import { api, imgUrl, fmt } from "./api.js";
import { h, ic, toast } from "./dom.js";
import * as P from "./player.js";
import { on } from "./store.js";

export const lib = { liked: new Map(), playlists: [] };
export const app = { info: {} };
const view = () => document.getElementById("view");
const bgi = (u) => (u ? { backgroundImage: `url("${imgUrl(u)}")` } : {});
const go = (hash) => { location.hash = hash; };
const detailHash = (it) => `#/d/${it.kind || "album"}/${encodeURIComponent(it.id)}/${encodeURIComponent(it.title || "")}`;

export async function loadLib() {
  try { const d = await api("/api/my"); lib.liked = new Map((d.liked || []).map((t) => [t.id, t])); lib.playlists = d.playlists || []; } catch { /* offline */ }
}
async function toggleLike(t, btn) {
  const liked = !lib.liked.has(t.id);
  if (liked) lib.liked.set(t.id, t); else lib.liked.delete(t.id);
  document.querySelectorAll(`.like[data-id="${CSS.escape(t.id)}"]`).forEach((b) => b.classList.toggle("on", liked));
  try { await api("/api/like", { method: "POST", body: { track: t, liked } }); toast(liked ? "Added to Liked songs" : "Removed from Liked songs"); } catch { toast("Couldn't save that"); }
}
export const isLiked = (id) => lib.liked.has(id);
export { toggleLike };

// ---- popup menu + dialog helpers
export function menu(anchor, entries) {
  document.querySelectorAll(".menu").forEach((m) => m.remove());
  const r = anchor.getBoundingClientRect();
  const m = h("div", { class: "menu", role: "menu" }, entries.filter(Boolean).map((e) => h("button", { role: "menuitem", onclick: () => { m.remove(); e.fn(); } }, ic(e.icon || "note"), e.label)));
  document.body.append(m);
  const mw = m.offsetWidth, mh = m.offsetHeight;
  m.style.left = `${Math.max(8, Math.min(innerWidth - mw - 8, r.right - mw))}px`;
  m.style.top = `${r.bottom + mh + 8 > innerHeight ? Math.max(8, r.top - mh - 4) : r.bottom + 4}px`;
  setTimeout(() => document.addEventListener("click", () => m.remove(), { once: true }), 0);
}
export function dialog(node) { const d = document.getElementById("dlg"); d.replaceChildren(node); if (!d.open) d.showModal(); return d; }
export const closeDialog = () => document.getElementById("dlg").close();
export function pickPlaylist(tracks) {
  const list = Array.isArray(tracks) ? tracks : [tracks];
  const add = async (pl) => {
    try {
      if (pl) await api(`/api/my/playlists/${pl.id}/tracks`, { method: "POST", body: { op: "add", tracks: list } });
      else { const name = box.querySelector("input").value.trim() || "New playlist"; await api("/api/my/playlists", { method: "POST", body: { name, tracks: list } }); }
      await loadLib(); closeDialog(); toast("Saved to your playlist");
    } catch { toast("Couldn't save that"); }
  };
  const box = h("div", {}, h("h2", {}, "Add to playlist"),
    h("div", { class: "list" }, lib.playlists.map((pl) => h("button", { class: "row", style: { gridTemplateColumns: "1fr auto" }, onclick: () => add(pl) }, h("div", { class: "meta" }, h("div", { class: "t" }, pl.name), h("div", { class: "s" }, `${pl.tracks.length} songs`)), ic("plus")))),
    h("div", { class: "set full" }, h("label", {}, "New playlist"), h("input", { class: "in", type: "text", placeholder: "Name", "aria-label": "Playlist name" }),
      h("div", { class: "acts" }, h("button", { class: "btn pri", onclick: () => add(null) }, "Create & add"), h("button", { class: "btn", onclick: closeDialog }, "Cancel"))));
  dialog(box);
}

// ---- building blocks
export function row(t, list, i, o = {}) {
  const like = h("button", { class: "ibtn like" + (isLiked(t.id) ? " on" : ""), "data-id": t.id, title: "Like", "aria-label": "Like", onclick: (e) => { e.stopPropagation(); toggleLike(t); } }, ic("heart"));
  const more = h("button", { class: "ibtn", title: "More", "aria-label": "More options", onclick: (e) => { e.stopPropagation(); menu(more, [
    { label: "Play next", icon: "play", fn: () => toast(P.enqueue([t], true) ? "Plays next" : "Already in the queue") },
    { label: "Add to queue", icon: "queue", fn: () => toast(P.enqueue([t]) ? "Added to the queue" : "Already in the queue") },
    { label: "Add to playlist…", icon: "plus", fn: () => pickPlaylist(t) },
    { label: "Start radio", icon: "radio", fn: async () => { try { const r = await api(`/api/radio?id=${encodeURIComponent(t.id)}`); P.play([t, ...(r.tracks || [])], 0); } catch { toast("No radio for this one"); } } },
    t.album_id && { label: "Go to album", icon: "note", fn: () => go(`#/d/album/${encodeURIComponent(t.album_id)}/${encodeURIComponent(t.album || "")}`) },
    ...(o.extraMenu || []),
  ]); } }, ic("more"));
  const el = h("div", { class: "row", role: "button", tabindex: "0", "data-id": t.id,
    onclick: () => P.play(list, i), onkeydown: (e) => { if (e.key === "Enter") { e.preventDefault(); P.play(list, i); } } },
    h("div", { class: "art", style: bgi(t.art) }, h("span", { class: "eq" }, ic("bars"))),
    h("div", { class: "meta" }, h("div", { class: "t" }, t.title), h("div", { class: "s" }, [t.artist, o.hideAlbum ? "" : t.album].filter(Boolean).join(" · "))),
    h("div", { class: "end" }, t.dur ? fmt(t.dur) : "", like, more, ...(o.extra || [])));
  return el;
}
const card = (it) => h("a", { class: "card" + (it.kind === "artist" ? " round" : ""), href: detailHash(it), title: it.title },
  h("div", { class: "art", style: bgi(it.art) }), h("div", { class: "t" }, it.title), h("div", { class: "s" }, it.artist || ""));
const songsOf = (items) => items.filter((x) => (x.kind || "song") === "song");
const rows = (items, o) => h("div", { class: "list" }, items.map((t, i) => row(t, items, i, o)));
const msg = (...k) => h("div", { class: "msg" }, ...k);
const skeleton = () => h("div", {}, h("div", { class: "skel", style: { width: "40%", marginBottom: "1.25rem" } }), [0, 1, 2, 3, 4].map(() => h("div", { class: "skel", style: { marginBottom: ".5rem" } })));
function unavailable(e) {
  if (e.code === "offline") return msg(h("b", {}, "Can't reach the server. "), "Check that ", h("code", {}, "lyricglow web"), " is running, or open Settings → Connection.");
  if (e.status === 501 || e.code === "unavailable") return msg(h("b", {}, "This part needs ytmusicapi. "), "Run ", h("code", {}, "pip install ytmusicapi"), " and restart ", h("code", {}, "lyricglow web"), ". Search and playback work without it.");
  if (e.code === "signin_required") return msg(h("b", {}, "Sign in needed. "), "Run ", h("code", {}, "lyricglow ytm login"), " on the computer that runs the server.");
  return msg(h("b", {}, "Something went wrong: "), e.message || "unknown error");
}
export function markPlaying() {
  const c = P.cur();
  document.querySelectorAll(".row[data-id]").forEach((r) => { const is = !!c && r.dataset.id === c.id; r.classList.toggle("cur", is); r.classList.toggle("playing", is && P.st.playing); });
}
const playAll = (items, shuffle) => { const s = songsOf(items); if (!s.length) return; P.play(shuffle ? [...s].sort(() => Math.random() - 0.5) : s, 0); };

// ---- pages
async function home() {
  const root = h("div", {}, h("h1", {}, (() => { const hr = new Date().getHours(); return hr < 5 ? "Late night" : hr < 12 ? "Good morning" : hr < 18 ? "Good afternoon" : "Good evening"; })()));
  const wrap = h("div", { class: "sec" }, skeleton());
  root.append(wrap);
  if (P.st.queue.length) root.insertBefore(h("div", { class: "sec" }, h("h2", {}, "Pick up where you left off"), rows(P.st.queue.slice(Math.max(0, P.st.index), Math.max(0, P.st.index) + 4), { hideAlbum: true })), wrap);
  view().replaceChildren(root);
  try {
    const d = await api("/api/home"); wrap.remove();
    if (!d.shelves.length) root.append(msg("Nothing here yet — try searching for a song."));
    for (const s of d.shelves) {
      const songs = s.layout === "songs";
      root.append(h("section", { class: "sec" }, h("h2", {}, s.title || "For you"), songs ? rows(songsOf(s.items).slice(0, 8)) : h("div", { class: "grid" }, s.items.map(card))));
    }
  } catch (e) { wrap.replaceChildren(unavailable(e), h("div", { class: "acts" }, h("a", { class: "btn pri", href: "#/search" }, ic("search"), "Search for music"))); }
  markPlaying();
}
let sugT;
async function search(q = "", f = "all") {
  const input = h("input", { class: "field", type: "search", placeholder: "Songs, albums, artists…", value: q, list: "sugg", autocomplete: "off", "aria-label": "Search" });
  const sugg = h("datalist", { id: "sugg" });
  const run = (v, ff = f) => { if (v.trim()) go(`#/search/${encodeURIComponent(v.trim())}/${ff}`); };
  input.addEventListener("keydown", (e) => { if (e.key === "Enter") run(input.value); });
  input.addEventListener("input", () => { clearTimeout(sugT); sugT = setTimeout(async () => { if (input.value.length < 2) return; try { const r = await api("/api/suggest?q=" + encodeURIComponent(input.value)); sugg.replaceChildren(...(r.items || []).map((s) => h("option", { value: typeof s === "string" ? s : s.text || "" }))); } catch { /* ignore */ } }, 220); });
  const chips = h("div", { class: "chips", role: "group", "aria-label": "Filter results" }, [["all", "All"], ["songs", "Songs"], ["albums", "Albums"], ["artists", "Artists"], ["playlists", "Playlists"]]
    .map(([k, l]) => h("button", { class: "chip", "aria-pressed": String(f === k), onclick: () => (q ? run(q, k) : go(`#/search//${k}`)) }, l)));
  const out = h("div", {});
  view().replaceChildren(h("div", {}, h("div", { class: "sbox" }, ic("search"), input, sugg), chips, out));
  if (!q) { out.append(msg("Search any song, album or artist — no account needed.")); input.focus(); return; }
  out.append(skeleton());
  try {
    const d = await api(`/api/search?q=${encodeURIComponent(q)}&type=${f}`); out.replaceChildren();
    const sec = (title, node) => out.append(h("section", { class: "sec" }, h("h2", {}, title), node));
    if (d.songs && d.songs.length) sec("Songs", rows(d.songs));
    if (d.albums && d.albums.length) sec("Albums", h("div", { class: "grid" }, d.albums.map(card)));
    if (d.artists && d.artists.length) sec("Artists", h("div", { class: "grid" }, d.artists.map(card)));
    if (d.playlists && d.playlists.length) sec("Playlists", h("div", { class: "grid" }, d.playlists.map(card)));
    if (!out.children.length) out.append(msg(`No results for “${q}”.`));
    if (d.errors) out.append(msg(h("small", {}, "Some results are unavailable: install ytmusicapi for albums, artists and playlists.")));
  } catch (e) { out.replaceChildren(unavailable(e)); }
  markPlaying();
}
function hero(d, tracks, extra = []) {
  const s = songsOf(tracks);
  return h("div", { class: "hero" }, h("div", { class: "art", style: bgi(d.art) }),
    h("div", { class: "info" }, h("div", { class: "kick" }, d.kicker || d.kind || ""), h("h1", {}, d.title || "Untitled"),
      h("div", { class: "sub" }, [d.subtitle, d.year, s.length ? `${s.length} songs` : ""].filter(Boolean).join(" · ")),
      h("div", { class: "acts" },
        h("button", { class: "btn pri", onclick: () => playAll(tracks) }, ic("play"), "Play"),
        h("button", { class: "btn", onclick: () => playAll(tracks, true) }, ic("shuffle"), "Shuffle"),
        h("button", { class: "btn", onclick: () => toast(`${P.enqueue(s)} added to the queue`) }, ic("queue"), "Add to queue"), ...extra)));
}
async function detail(kind, id, title) {
  view().replaceChildren(skeleton());
  try {
    const d = await api(`/api/detail?kind=${encodeURIComponent(kind)}&id=${encodeURIComponent(id)}&title=${encodeURIComponent(title || "")}`);
    const tr = d.tracks || [];
    view().replaceChildren(h("div", {}, hero(d, tr, [h("button", { class: "btn", onclick: () => pickPlaylist(songsOf(tr)) }, ic("plus"), "Save to playlist")]), h("div", { class: "sec" }, rows(tr, { hideAlbum: kind === "album" }))));
  } catch (e) { view().replaceChildren(unavailable(e)); }
  markPlaying();
}
async function localPlaylist(id) {
  await loadLib();
  const pl = lib.playlists.find((p) => p.id === id);
  if (!pl) return view().replaceChildren(msg("That playlist doesn't exist any more."));
  const rm = (t) => ({ label: "Remove from this playlist", icon: "trash", fn: async () => { await api(`/api/my/playlists/${id}/tracks`, { method: "POST", body: { op: "remove", id: t.id } }); localPlaylist(id); } });
  view().replaceChildren(h("div", {}, hero({ title: pl.name, kicker: "Your playlist", art: pl.tracks[0] && pl.tracks[0].art, subtitle: "" }, pl.tracks, [
    h("button", { class: "btn", onclick: async () => { if (confirm(`Delete “${pl.name}”?`)) { await api(`/api/my/playlists/${id}`, { method: "DELETE" }); await loadLib(); go("#/library"); } } }, ic("trash"), "Delete")]),
    h("div", { class: "sec" }, pl.tracks.length ? h("div", { class: "list" }, pl.tracks.map((t, i) => row(t, pl.tracks, i, { extraMenu: [rm(t)] }))) : msg("Empty — add songs from the ⋯ menu on any track."))));
  markPlaying();
}
async function library() {
  await loadLib();
  const liked = [...lib.liked.values()];
  const root = h("div", {}, h("h1", {}, "Library"),
    h("section", { class: "sec" }, h("div", { class: "shelf-h" }, h("h2", {}, "Liked songs"), liked.length ? h("button", { class: "btn sm", onclick: () => playAll(liked) }, ic("play"), "Play all") : null),
      liked.length ? rows(liked) : msg("Songs you like show up here. Tap the heart on any track.")),
    h("section", { class: "sec" }, h("div", { class: "shelf-h" }, h("h2", {}, "Your playlists"), h("button", { class: "btn sm", onclick: () => pickPlaylist([]) }, ic("plus"), "New playlist")),
      lib.playlists.length ? h("div", { class: "grid" }, lib.playlists.map((pl) => h("a", { class: "card", href: `#/pl/${pl.id}` }, h("div", { class: "art", style: bgi(pl.tracks[0] && pl.tracks[0].art) }), h("div", { class: "t" }, pl.name), h("div", { class: "s" }, `${pl.tracks.length} songs`))))
        : msg("No playlists yet.")));
  const yt = h("section", { class: "sec" });
  if (app.info.account) {
    yt.append(h("h2", {}, "YouTube Music"), skeleton()); root.append(yt);
    api("/api/library?kind=playlists").then((d) => yt.replaceChildren(h("h2", {}, "YouTube Music playlists"), h("div", { class: "grid" }, (d.items || []).map(card)))).catch((e) => yt.replaceChildren(h("h2", {}, "YouTube Music"), unavailable(e)));
  }
  view().replaceChildren(root); markPlaying();
}
function queue() {
  const q = P.st.queue, i = P.st.index;
  const mk = (t, n) => row(t, q, n, { hideAlbum: true, extra: [
    h("button", { class: "ibtn", title: "Move up", "aria-label": "Move up", onclick: (e) => { e.stopPropagation(); P.move(n, -1); } }, ic("up")),
    h("button", { class: "ibtn", title: "Move down", "aria-label": "Move down", onclick: (e) => { e.stopPropagation(); P.move(n, 1); } }, ic("down")),
    h("button", { class: "ibtn", title: "Remove", "aria-label": "Remove from queue", onclick: (e) => { e.stopPropagation(); P.removeAt(n); } }, ic("x"))] });
  view().replaceChildren(h("div", {}, h("div", { class: "shelf-h" }, h("h1", {}, "Queue"),
      h("div", { class: "acts", style: { marginTop: 0 } }, h("button", { class: "btn sm", onclick: () => toast(`Cleared ${P.clearUpcoming()} upcoming songs`) }, ic("trash"), "Clear upcoming"),
        h("button", { class: "btn sm", onclick: () => pickPlaylist(q) }, ic("plus"), "Save as playlist"))),
    q.length ? h("div", {}, i >= 0 ? h("section", { class: "sec" }, h("h2", {}, "Now playing"), h("div", { class: "list" }, mk(q[i], i))) : null,
      h("section", { class: "sec" }, h("h2", {}, "Up next"), q.length - i - 1 > 0 ? h("div", { class: "list" }, q.slice(i + 1).map((t, k) => mk(t, i + 1 + k))) : msg("Nothing queued. Search for something or let radio continue.")))
      : msg("The queue is empty. Play something from Search or Home.")));
  markPlaying();
}
export async function route() {
  const parts = location.hash.replace(/^#\/?/, "").split("/").map((s) => { try { return decodeURIComponent(s); } catch { return s; } });
  const name = parts[0] || "home";
  window.scrollTo(0, 0);
  if (name === "home") await home(); else if (name === "search") await search(parts[1] || "", parts[2] || "all");
  else if (name === "library") await library(); else if (name === "queue") queue();
  else if (name === "d") await detail(parts[1], parts[2], parts[3]); else if (name === "pl") await localPlaylist(parts[1]);
  else await home();
  return name;
}
on((k) => { if (k === "queue" && location.hash.startsWith("#/queue")) queue(); if (k === "track" || k === "play") markPlaying(); });
