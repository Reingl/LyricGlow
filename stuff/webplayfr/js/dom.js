// small DOM helpers + icons + toasts
export function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k === "style" && typeof v === "object") Object.assign(el.style, v);
    else if (k.startsWith("on")) el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === "html") el.innerHTML = v;
    else el.setAttribute(k, v === true ? "" : v);
  }
  for (const c of kids.flat()) if (c != null && c !== false) el.append(c.nodeType ? c : document.createTextNode(c));
  return el;
}
const I = {
  play: ["fill", '<path d="M8 5.2v13.6a1 1 0 0 0 1.5.86l11-6.8a1 1 0 0 0 0-1.72l-11-6.8A1 1 0 0 0 8 5.2z"/>'],
  pause: ["fill", '<rect x="6.5" y="5" width="4" height="14" rx="1"/><rect x="13.5" y="5" width="4" height="14" rx="1"/>'],
  next: ["fill", '<path d="M6 5.5v13l9-6.5z"/><rect x="16.5" y="5.5" width="2.5" height="13" rx="1"/>'],
  prev: ["fill", '<path d="M18 5.5v13L9 12z"/><rect x="5" y="5.5" width="2.5" height="13" rx="1"/>'],
  shuffle: ["", '<path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/>'],
  repeat: ["", '<path d="m17 2 4 4-4 4"/><path d="M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4"/><path d="M21 13v1a4 4 0 0 1-4 4H3"/>'],
  heart: ["", '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"/>'],
  more: ["fill", '<circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/>'],
  search: ["", '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>'],
  home: ["", '<path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10"/>'],
  lib: ["", '<path d="M4 4v16M9 4v16"/><path d="m14 5 6 14"/>'],
  queue: ["", '<path d="M4 6h12M4 12h12M4 18h7"/><path d="m17 15 5 3-5 3z"/>'],
  gear: ["", '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>'],
  x: ["", '<path d="M18 6 6 18M6 6l12 12"/>'],
  down: ["", '<path d="m6 9 6 6 6-6"/>'],
  vol: ["", '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13"/>'],
  mute: ["", '<path d="M11 5 6 9H3v6h3l5 4z"/><path d="m22 9-6 6M16 9l6 6"/>'],
  mic: ["", '<path d="M4 8h8M4 12h12M4 16h6"/><path d="M18 5v9.5a2.5 2.5 0 1 1-1.5-2.3"/>'],
  plus: ["", '<path d="M12 5v14M5 12h14"/>'],
  trash: ["", '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"/>'],
  up: ["", '<path d="m6 15 6-6 6 6"/>'],
  radio: ["", '<circle cx="12" cy="12" r="2"/><path d="M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4M19 5a10 10 0 0 1 0 14M5 19A10 10 0 0 1 5 5"/>'],
  note: ["", '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>'],
  bars: ["fill", '<rect x="5" y="9" width="3" height="10" rx="1"/><rect x="10.5" y="5" width="3" height="14" rx="1"/><rect x="16" y="11" width="3" height="8" rx="1"/>'],
};
export function ic(name, extra = "") {
  const [fill, body] = I[name] || ["", ""];
  const s = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  s.setAttribute("viewBox", "0 0 24 24"); s.setAttribute("class", `ic ${fill} ${extra}`.trim()); s.setAttribute("aria-hidden", "true"); s.innerHTML = body; return s;
}
export function toast(msg, ms = 2600) {
  const box = document.getElementById("toasts"); const t = h("div", { class: "toast" }, msg); box.append(t);
  setTimeout(() => t.remove(), ms);
}
