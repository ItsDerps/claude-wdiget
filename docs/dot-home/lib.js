// Building blocks for the Dot Home design document: icons, dot-matrix type, phones, keys.

const RED = '#D71921';

// ---------------------------------------------------------------- icons (24px, line style)
const ICONS = {
  phone: '<path d="M6.5 3.5h3l1.5 4-2 1.3a11 11 0 0 0 6.2 6.2l1.3-2 4 1.5v3a2 2 0 0 1-2 2A17 17 0 0 1 4.5 5.5a2 2 0 0 1 2-2z"/>',
  messages: '<path d="M4 5.5h16v10H9l-4.5 3.5V5.5z"/>',
  camera: '<rect x="3" y="7" width="18" height="12" rx="3"/><circle cx="12" cy="13" r="3.5"/><path d="M8.5 7l1.5-2.5h4L15.5 7"/>',
  photos: '<rect x="3.5" y="4.5" width="17" height="15" rx="3"/><path d="M3.5 16l5-5 4 4 2.5-2.5 5.5 5"/><circle cx="15.5" cy="9" r="1.5"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3.5 2"/>',
  maps: '<path d="M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.3"/>',
  music: '<path d="M9 18V6l10-2v12"/><circle cx="6.5" cy="18" r="2.5"/><circle cx="16.5" cy="16" r="2.5"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M5.5 18.5l1.8-1.8M16.7 7.3l1.8-1.8"/>',
  mail: '<rect x="3" y="5.5" width="18" height="13" rx="2.5"/><path d="M3.5 7l8.5 6 8.5-6"/>',
  browser: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 3.5 5.3 3.5 8.5s-1 5.9-3.5 8.5c-2.5-2.6-3.5-5.3-3.5-8.5s1-5.9 3.5-8.5z"/>',
  calendar: '<rect x="3.5" y="5" width="17" height="15" rx="3"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
  notes: '<rect x="5" y="3.5" width="14" height="17" rx="2.5"/><path d="M8.5 8.5h7M8.5 12h7M8.5 15.5h4"/>',
  weather: '<path d="M7 18.5h10a4 4 0 0 0 .5-8 5.5 5.5 0 0 0-10.6 1.2A3.4 3.4 0 0 0 7 18.5z"/>',
  calculator: '<rect x="5" y="3" width="14" height="18" rx="3"/><path d="M8.5 7.5h7M8.5 12h1M12 12h0M14.5 12h1M8.5 16h1M12 16h0M14.5 16h1" stroke-width="2"/>',
  files: '<path d="M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2V7z"/>',
  video: '<rect x="3" y="5.5" width="18" height="13" rx="3.5"/><path d="M10.5 9.5v5l4-2.5z"/>',
  wallet: '<rect x="3" y="6" width="18" height="13" rx="3"/><path d="M3 10h18M15.5 14.5h2"/>',
  health: '<path d="M12 19.5s-7.5-4.6-7.5-10A4.2 4.2 0 0 1 12 7a4.2 4.2 0 0 1 7.5 2.5c0 5.4-7.5 10-7.5 10z"/>',
  store: '<path d="M5 8h14l-1 12H6L5 8z"/><path d="M9 8V6.5a3 3 0 0 1 6 0V8"/>',
  recorder: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0 0 12 0M12 17v3.5"/>',
  contacts: '<circle cx="12" cy="9" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>',
  keyboard: '<rect x="2.5" y="6" width="19" height="12" rx="2.5"/><path d="M6 10h0M9 10h0M12 10h0M15 10h0M18 10h0M8 14h8" stroke-width="2"/>',
  translate: '<path d="M4 6h8M8 4v2M5.5 6c.8 3 2.8 5.5 5.5 7M10.5 6c-.8 3-2.8 5.5-5.5 7M13 20l3.5-8 3.5 8M14.2 17.5h4.6"/>',
  card: '<rect x="3" y="6" width="18" height="12" rx="2.5"/><path d="M3 10h18"/>',
  fitness: '<path d="M3 12h4l2-5 4 10 2-5h6"/>',
  books: '<path d="M4.5 5.5a2 2 0 0 1 2-2h12v15h-12a2 2 0 0 0-2 2v-15z"/><path d="M4.5 18.5a2 2 0 0 1 2-2h12"/>',
  news: '<rect x="3.5" y="4.5" width="17" height="15" rx="2.5"/><path d="M7 9h10M7 12.5h10M7 16h6"/>',
  torch: '<path d="M8 3h8v4l-2 3v10h-4V10L8 7V3z"/><path d="M12 13v2"/>',
  timer: '<circle cx="12" cy="13" r="7.5"/><path d="M12 13V9M10 2.5h4"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M16 16l4.5 4.5"/>',
  mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0 0 12 0M12 17v3.5"/>',
  info: '<circle cx="12" cy="12" r="8.5"/><path d="M12 11v5M12 8h0" stroke-width="2"/>',
  hide: '<path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z"/><circle cx="12" cy="12" r="2.5"/><path d="M4 20L20 4"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16v4z"/>',
  remove: '<path d="M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  widgets: '<rect x="4" y="4" width="7" height="7" rx="2"/><rect x="13" y="4" width="7" height="7" rx="2"/><rect x="4" y="13" width="7" height="7" rx="2"/><rect x="13" y="13" width="7" height="7" rx="2"/>',
  wallpaper: '<rect x="4" y="3.5" width="16" height="17" rx="3"/><path d="M4 15l4.5-4 4 3.5 2.5-2 5 4"/>',
  layout: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M4 10h16M10 10v10"/>',
  play: '<path d="M8 5.5v13l10-6.5z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  next: '<path d="M6 6l9 6-9 6V6zM18 6v12"/>',
  prev: '<path d="M18 6l-9 6 9 6V6zM6 6v12"/>',
  chevron: '<path d="M9 5l7 7-7 7"/>',
  arrowup: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  copy: '<rect x="8" y="8" width="12" height="12" rx="2.5"/><path d="M16 8V5.5a1.5 1.5 0 0 0-1.5-1.5h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8"/>',
  bell: '<path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16zM10 20.5h4"/>',
  lock: '<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>',
  bolt: '<path d="M13 3L6 13h5l-1 8 7-10h-5l1-8z"/>',
  earbuds: '<path d="M7 5a3 3 0 0 1 3 3v3a2 2 0 0 1-2 2H7v6M17 5a3 3 0 0 0-3 3v3a2 2 0 0 0 2 2h1v6"/>',
  step: '<path d="M8 3c2 0 3 2 3 5s-1 5-3 5-3-2-3-5 1-5 3-5zM6 15h4l-.5 4h-3L6 15zM16 7c2 0 3 2 3 5s-1 5-3 5-3-2-3-5 1-5 3-5zM14 19h4"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"/>',
  car: '<path d="M4 15l1.5-5a2 2 0 0 1 2-1.5h9a2 2 0 0 1 2 1.5L20 15v3h-16v-3z"/><path d="M4 15h16"/><circle cx="7.5" cy="15.5" r=".5"/><circle cx="16.5" cy="15.5" r=".5"/>',
  cast: '<path d="M3 8V6.5A1.5 1.5 0 0 1 4.5 5h15A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5H14M3 12a7 7 0 0 1 7 7M3 15.5a3.5 3.5 0 0 1 3.5 3.5"/>',
  battery: '<rect x="3" y="7.5" width="16" height="9" rx="2"/><path d="M21 10.5v3"/>',
};

function icon(name, size = 22, color = '#fff', sw = 1.6) {
  return `<svg width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${color}" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round">${ICONS[name] || ''}</svg>`;
}

// Monochrome app icon: glyph on a dark circle
function app(name, label, opts = {}) {
  const s = opts.size || 46;
  const cls = ['app', opts.cls || ''].join(' ');
  const style = opts.style || '';
  const lab = label && opts.labels !== false ? `<div class="app-label">${label}</div>` : '';
  const dot = opts.badge ? '<span class="badge"></span>' : '';
  return `<div class="${cls}" style="${style}"><div class="app-circle" style="width:${s}px;height:${s}px">${icon(name, Math.round(s * 0.47))}${dot}</div>${lab}</div>`;
}

// ---------------------------------------------------------------- 5x7 dot-matrix digits
const GLYPHS = {
  '0': ['01110', '10001', '10011', '10101', '11001', '10001', '01110'],
  '1': ['00100', '01100', '00100', '00100', '00100', '00100', '01110'],
  '2': ['01110', '10001', '00001', '00010', '00100', '01000', '11111'],
  '3': ['11111', '00010', '00100', '00010', '00001', '10001', '01110'],
  '4': ['00010', '00110', '01010', '10010', '11111', '00010', '00010'],
  '5': ['11111', '10000', '11110', '00001', '00001', '10001', '01110'],
  '6': ['00110', '01000', '10000', '11110', '10001', '10001', '01110'],
  '7': ['11111', '00001', '00010', '00100', '01000', '01000', '01000'],
  '8': ['01110', '10001', '10001', '01110', '10001', '10001', '01110'],
  '9': ['01110', '10001', '10001', '01111', '00001', '00010', '01100'],
  ':': ['0', '0', '1', '0', '1', '0', '0'],
  '°': ['111', '101', '111', '000', '000', '000', '000'],
  '%': ['11001', '11010', '00010', '00100', '01000', '01011', '10011'],
  '.': ['0', '0', '0', '0', '0', '0', '1'],
  D: ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
  O: ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
  T: ['11111', '00100', '00100', '00100', '00100', '00100', '00100'],
  H: ['10001', '10001', '10001', '11111', '10001', '10001', '10001'],
  M: ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
  E: ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
};

/** Album-art placeholder: a small dot pattern. */
function artDots(n = 5, pitch = 8) {
  let d = '';
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) {
    const v = Math.abs(Math.sin(r * 1.7 + c * 2.3));
    d += `<circle cx="${c * pitch + pitch / 2}" cy="${r * pitch + pitch / 2}" r="${(1 + v * 2).toFixed(1)}" fill="${r === 2 && c === 3 ? RED : '#666'}"/>`;
  }
  return `<svg width="${n * pitch}" height="${n * pitch}">${d}</svg>`;
}

// Seeded random so scattered frames are the same on every render
function rng(seed) {
  let s = seed >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

/**
 * Dot-matrix text as SVG. t = assembly progress (1 = formed, 0 = scattered).
 * opts: dot (px pitch), on, off (unlit dot colour or null), scatter (px), seed
 */
function dots(text, opts = {}) {
  const p = opts.dot || 8, r = p * 0.36;
  const on = opts.on || '#fff', off = opts.off === undefined ? '#1c1c1c' : opts.off;
  const t = opts.t === undefined ? 1 : opts.t;
  const scatter = opts.scatter || 0;
  const rand = rng(opts.seed || 7);
  let x = 0, out = '';
  const ease = 1 - Math.pow(1 - t, 3);
  for (const ch of text) {
    if (ch === ' ') { x += 3 * p; continue; }
    const g = GLYPHS[ch];
    if (!g) continue;
    const w = g[0].length;
    for (let row = 0; row < 7; row++) {
      for (let col = 0; col < w; col++) {
        const cx = x + col * p + p / 2, cy = row * p + p / 2;
        const lit = g[row][col] === '1';
        if (lit) {
          const ang = rand() * Math.PI * 2, dist = scatter * (0.4 + rand() * 0.8);
          const dx = Math.cos(ang) * dist * (1 - ease), dy = Math.sin(ang) * dist * (1 - ease);
          const op = 0.25 + 0.75 * ease;
          out += `<circle cx="${(cx + dx).toFixed(1)}" cy="${(cy + dy).toFixed(1)}" r="${r.toFixed(2)}" fill="${on}" opacity="${op.toFixed(2)}"/>`;
        } else if (off) {
          out += `<circle cx="${cx}" cy="${cy}" r="${r.toFixed(2)}" fill="${off}"/>`;
        }
      }
    }
    x += (w + 1) * p;
  }
  const W = x - p, H = 7 * p;
  return `<svg width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" style="overflow:visible">${out}</svg>`;
}

// ---------------------------------------------------------------- phone frame
function statusBar(time = '09:41', light = true) {
  const c = light ? '#fff' : '#000';
  return `<div class="status" style="color:${c}">
    <span class="punch"></span><span class="st-time">${time}</span>
    <span class="st-right">
      <svg width="14" height="10" viewBox="0 0 14 10"><rect x="0" y="7" width="2.2" height="3" rx=".6" fill="${c}"/><rect x="3.6" y="5" width="2.2" height="5" rx=".6" fill="${c}"/><rect x="7.2" y="2.6" width="2.2" height="7.4" rx=".6" fill="${c}"/><rect x="10.8" y="0" width="2.2" height="10" rx=".6" fill="${c}"/></svg>
      <svg width="13" height="10" viewBox="0 0 24 18"><path d="M12 17l3.5-4a5 5 0 0 0-7 0L12 17zM5 9.5a10 10 0 0 1 14 0l2.3-2.6a13.5 13.5 0 0 0-18.6 0L5 9.5z" fill="${c}"/></svg>
      <span class="st-batt"><span style="background:${c};width:70%"></span></span>
    </span></div>`;
}

function phone(inner, opts = {}) {
  const cls = ['phone', opts.cls || ''].join(' ');
  const z = opts.zoom ? `zoom:${opts.zoom};` : '';
  const status = opts.status === false ? '' : statusBar(opts.time, opts.light !== false);
  const bar = opts.navbar === false ? '' : `<div class="navbar" style="background:${opts.light === false ? '#000' : '#fff'}"></div>`;
  return `<div class="${cls}" style="${z}${opts.style || ''}"><div class="screen ${opts.screen || ''}">${status}${inner}${bar}</div></div>`;
}

// ---------------------------------------------------------------- mini Swipe Keys keyboard
function keyboard(sugg = ['car', 'camera', 'can']) {
  const rows = [['q1', 'w2', 'e3', 'r4', 't5', 'y6', 'u7', 'i8', 'o9', 'p0'], ['a@', 's#', 'd£', 'f&', 'g*', 'h(', 'j)', "k'", 'l"']];
  const r3 = ['z%', 'x-', 'c+', 'v=', 'b/', 'n;', 'm:'];
  const key = k => `<div class="key"><i>${k[1]}</i>${k[0]}</div>`;
  return `<div class="kb">
    <div class="kb-sugg"><span>${sugg[1]}</span><b>${sugg[0]}</b><span>${sugg[2]}</span></div>
    <div class="kb-row">${rows[0].map(key).join('')}</div>
    <div class="kb-row r2">${rows[1].map(key).join('')}</div>
    <div class="kb-row"><div class="key sp w15">${icon('arrowup', 13)}</div>${r3.map(key).join('')}<div class="key sp w15">⌫</div></div>
    <div class="kb-row"><div class="key sp w125">123</div><div class="key sp w125">☺</div><div class="key">,</div><div class="key space">space</div><div class="key">.</div><div class="key sp w15 go">go</div></div>
  </div>`;
}

// ---------------------------------------------------------------- spring curve
/** SVG plot of a damped spring from 0 to 1. */
function springCurve(stiffness, damping, opts = {}) {
  const W = opts.w || 220, H = opts.h || 90, T = opts.T || 0.6;
  const w0 = Math.sqrt(stiffness), z = damping;
  const pts = [];
  for (let i = 0; i <= 120; i++) {
    const t = (i / 120) * T;
    let x;
    if (z < 1) {
      const wd = w0 * Math.sqrt(1 - z * z);
      x = 1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + (z * w0 / wd) * Math.sin(wd * t));
    } else {
      x = 1 - Math.exp(-w0 * t) * (1 + w0 * t);
    }
    const from = opts.from === undefined ? 0 : opts.from, to = opts.to === undefined ? 1 : opts.to;
    const v = from + (to - from) * x;
    const lo = opts.lo === undefined ? -0.05 : opts.lo, hi = opts.hi === undefined ? 1.25 : opts.hi;
    pts.push([(i / 120) * W, H - ((v - lo) / (hi - lo)) * H]);
  }
  const d = pts.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ');
  const lo = opts.lo === undefined ? -0.05 : opts.lo, hi = opts.hi === undefined ? 1.25 : opts.hi;
  const yT = H - (((opts.to === undefined ? 1 : opts.to) - lo) / (hi - lo)) * H;
  return `<svg width="${W}" height="${H}" class="curve"><line x1="0" y1="${yT}" x2="${W}" y2="${yT}" stroke="#333" stroke-dasharray="2 4"/><path d="${d}" fill="none" stroke="${RED}" stroke-width="2"/></svg>`;
}
