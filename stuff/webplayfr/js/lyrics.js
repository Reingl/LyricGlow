// word-by-word synced lyrics: each syllable fills as it is sung; click a line to jump there
import { api } from "./api.js";
import { prefs } from "./store.js";
export function createLyrics(root, onSeek) {
  let lines = [], els = [], active = -1, token = 0;
  const msg = (t) => { root.replaceChildren(Object.assign(document.createElement("p"), { className: "ly-none", textContent: t })); lines = []; els = []; active = -1; };
  async function load(t) {
    const my = ++token; active = -1;
    if (!t) return msg("Nothing playing");
    msg("Finding lyrics…");
    try {
      const q = new URLSearchParams({ title: t.title || "", artist: t.artist || "", album: t.album || "" }); if (t.dur) q.set("dur", t.dur);
      const d = await api("/api/lyrics?" + q);
      if (my !== token) return;
      if (!d || !d.lines || !d.lines.length) return msg("No synced lyrics for this song.");
      lines = d.lines;
      els = lines.map((ln, i) => {
        const el = document.createElement("div"); el.className = "ly-line"; el.dataset.i = i;
        if (ln.syl.every((s) => s.bg)) el.classList.add("bg");
        ln.syl.forEach((s) => { const sp = document.createElement("span"); sp.className = "syl"; sp.textContent = s.t; el.append(sp); });
        el.addEventListener("click", () => onSeek(ln.b));
        return el;
      });
      root.replaceChildren(...els);
      root.classList.toggle("blur", !!prefs.blur);
    } catch (e) { if (my === token) msg(e.code === "offline" ? "Can't reach the server." : "Lyrics aren't available right now."); }
  }
  function tick(time) {
    if (!lines.length) return;
    const t = time + (prefs.lyricOffset || 0);
    let a = -1;
    for (let i = 0; i < lines.length; i++) { if (lines[i].b <= t) a = i; else break; }
    if (a !== active) {
      els.forEach((el, i) => { el.classList.toggle("active", i === a); el.classList.toggle("past", i < a); el.classList.toggle("near", Math.abs(i - a) === 1);
        if (i !== a) el.querySelectorAll(".syl").forEach((s) => s.style.removeProperty("--p")); });
      active = a;
      if (a >= 0 && els[a].scrollIntoView) els[a].scrollIntoView({ block: "center", behavior: prefs.reduceMotion ? "auto" : "smooth" });
    }
    if (a >= 0) {
      const sy = els[a].children, ln = lines[a].syl;
      for (let i = 0; i < ln.length; i++) { const s = ln[i], p = s.e > s.b ? Math.max(0, Math.min(1, (t - s.b) / (s.e - s.b))) : (t >= s.b ? 1 : 0); sy[i].style.setProperty("--p", p.toFixed(3)); }
    }
  }
  return { load, tick, setBlur: (b) => root.classList.toggle("blur", b) };
}
