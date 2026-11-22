// talks to the lyricglow web backend (same origin by default; or any URL + token when the site is hosted elsewhere)
const LS = window.localStorage;
export const cfg = {
  get base() { return (LS.getItem("lg.base") || "").replace(/\/+$/, ""); }, set base(v) { LS.setItem("lg.base", String(v).trim()); },
  get token() { return LS.getItem("lg.token") || ""; }, set token(v) { LS.setItem("lg.token", String(v).trim()); },
};
const tq = (u) => (cfg.token ? u + (u.includes("?") ? "&" : "?") + "t=" + encodeURIComponent(cfg.token) : u);
export async function api(path, opt = {}) {
  const h = {};
  if (cfg.token) h["X-LG-Token"] = cfg.token;
  if (opt.body) h["Content-Type"] = "application/json";
  let r;
  try {
    r = await fetch(cfg.base + path, { method: opt.method || "GET", headers: h, body: opt.body ? JSON.stringify(opt.body) : undefined,
      credentials: cfg.base ? "include" : "same-origin", signal: opt.signal });
  } catch (e) {
    if (e.name === "AbortError") throw e;
    const err = new Error("Can't reach the LyricGlow server"); err.code = "offline"; throw err;
  }
  if (!r.ok) {
    let j = {}; try { j = await r.json(); } catch { /* not json */ }
    const err = new Error(j.message || j.error || r.statusText); err.status = r.status; err.code = j.error || "http"; throw err;
  }
  return r.json();
}
export const imgUrl = (u) => (!u ? "" : u.startsWith("data:") ? u : tq(`${cfg.base}/api/img?u=${encodeURIComponent(u)}`));
export function streamUrl(id, p) {
  const q = [];
  if (p.stream && p.stream !== "server") { q.push("mode=" + p.stream, "br=" + p.bitrate); if (p.normalize) q.push("norm=1"); }
  return tq(`${cfg.base}/stream/${id}` + (q.length ? "?" + q.join("&") : ""));
}
export const fmt = (s) => { s = Math.max(0, Math.floor(s || 0)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };
