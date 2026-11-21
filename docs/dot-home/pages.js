// The pages of the Dot Home design preview.
const TOTAL = 18;
let pageNo = 0;
function page(content, opts = {}) {
  pageNo++;
  const folio = opts.folio === false ? '' : `<div class="folio"><span>Dot Home · design preview</span><span>${opts.section || ''}</span><span>${String(pageNo).padStart(2, '0')} / ${TOTAL}</span></div>`;
  return `<section class="page ${opts.cls || ''}">${content}${folio}</section>`;
}
function pins(list) { return list.map(([n, x, y]) => `<span class="pin" style="left:${x}px;top:${y}px">${n}</span>`).join(''); }

// ---------- mini frames for storyboards
function drawerFrame(p) {
  const top = Math.round((1 - p) * 588);
  const rows = [0, 1, 2, 3].map(i => Math.max(0, Math.min(1, (p - 0.35 - i * 0.12) / 0.3)));
  return phone(`<div style="position:relative;flex:1">
      <div style="position:absolute;inset:0;display:flex;flex-direction:column;transform:scale(${1 - 0.06 * p});opacity:${1 - 0.7 * p}" class="home wall">${clockBlock()}<div class="flex1"></div>
        <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1])).join('')}</div>${dock()}</div>
      <div style="position:absolute;left:0;right:0;top:${top}px;bottom:-40px;background:#050505;border-radius:${Math.round(30 * (1 - p))}px ${Math.round(30 * (1 - p))}px 0 0;border-top:1px solid #222">
        <div class="search-pill" style="opacity:${Math.min(1, p * 1.6)}">${icon('search', 15, '#888')}<span>Search apps, contacts, web</span></div>
        ${[0, 1, 2, 3].map(i => `<div class="drecent" style="margin-top:14px;opacity:${rows[i]};transform:translateY(${(1 - rows[i]) * 16}px)">${[['messages', 'Messages'], ['music', 'Music'], ['maps', 'Maps'], ['camera', 'Camera']].map(a => app(a[0], '', { size: 40, labels: false })).join('')}</div>`).join('')}
      </div>
      ${p > 0 && p < 1 ? `<div class="finger" style="right:auto;left:115px;top:${top + 40}px"></div>` : ''}
    </div>`, { zoom: 0.38 });
}

function appOpenFrame(p) {
  // Maps icon sits in the pinned grid; window grows from it
  const from = { x: 87, y: 455, w: 46, h: 46, r: 23 }, to = { x: 0, y: -30, w: 270, h: 618, r: 37 };
  const e = 1 - Math.pow(1 - p, 3);
  const L = v => from[v] + (to[v] - from[v]) * e;
  const win = p === 0 ? '' : `<div style="position:absolute;left:${L('x')}px;top:${L('y')}px;width:${L('w')}px;height:${L('h')}px;border-radius:${L('r')}px;background:#e8e8e6;overflow:hidden;z-index:5">
      <div style="position:absolute;inset:0;opacity:${Math.max(0, (p - 0.4) / 0.6)}">${mapsMock()}</div>
      <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;opacity:${Math.max(0, 1 - p * 2.2)}">${icon('maps', 22 + 30 * e, '#000')}</div></div>`;
  return phone(`<div style="position:relative;flex:1">
      <div style="position:absolute;inset:0;display:flex;flex-direction:column;transform:scale(${1 + 0.08 * e});opacity:${1 - 0.6 * e}" class="home wall">${clockBlock()}<div class="flex1"></div>
        <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1], a[0] === 'maps' && p > 0 ? { style: 'opacity:0' } : {})).join('')}</div>${dock()}</div>
      ${win}
    </div>`, { zoom: 0.38, light: p < 0.6 });
}

function mapsMock() {
  return `<svg width="270" height="618" viewBox="0 0 270 618"><rect width="270" height="618" fill="#e8e8e6"/>
    <path d="M-10 200 C 80 180, 120 260, 280 230 M40 -10 C 60 200, 20 400, 90 640 M-10 420 C 100 380, 180 470, 280 430 M180 -10 C 170 150, 230 300, 200 640" stroke="#fff" stroke-width="14" fill="none"/>
    <path d="M90 300 C 120 280, 150 330, 190 310" stroke="${RED}" stroke-width="4" fill="none" stroke-dasharray="1 7" stroke-linecap="round"/>
    <circle cx="190" cy="310" r="9" fill="${RED}"/><circle cx="90" cy="300" r="6" fill="#000"/>
    <rect x="16" y="520" width="238" height="70" rx="22" fill="#fff"/><text x="34" y="552" font-family="Inter" font-weight="600" font-size="13">Dentist · 8 min</text>
    <text x="34" y="572" font-family="Mono" font-size="9" fill="#777">0.6 MI · ARRIVE 13:52</text></svg>`;
}

function pressCell(scale, ring, label) {
  return `<div class="frame"><div class="cell">
    ${ring ? `<div style="position:absolute;width:${46 * scale + 18}px;height:${46 * scale + 18}px;border-radius:50%;border:1.5px dotted rgba(255,255,255,${ring})"></div>` : ''}
    <div style="transform:scale(${scale})">${app('music', '', { size: 46, labels: false })}</div></div><div class="t">${label}</div></div>`;
}

function holdCell(p, label) {
  const n = 20, R = 36;
  let d = '';
  for (let i = 0; i < n; i++) {
    const a = -Math.PI / 2 + (i / n) * Math.PI * 2, on = i / n < p;
    d += `<circle cx="${60 + Math.cos(a) * R}" cy="${60 + Math.sin(a) * R}" r="2" fill="${on ? (p === 1 ? RED : '#fff') : '#262626'}"/>`;
  }
  const lift = p === 1;
  const ring = lift ? 'box-shadow:0 0 0 2px #fff, 0 10px 20px rgba(0,0,0,.8);border-radius:50%' : '';
  return `<div class="frame"><div class="cell"><svg width="120" height="120" style="position:absolute;left:0;top:0">${d}</svg>
    <div style="transform:scale(${lift ? 1.12 : 1 - 0.04 * p});${ring}">${app('messages', '', { size: 46, labels: false, style: 'width:auto' })}</div></div><div class="t">${label}</div></div>`;
}

function rippleCell(p, label) {
  const R = 10 + p * 52, n = 18, op = 1 - p;
  let d = '';
  for (let ring = 0; ring < 2; ring++) {
    const rr = Math.max(0, R - ring * 14), o = Math.max(0, op - ring * 0.25);
    for (let i = 0; i < n; i++) {
      const a = (i / n) * Math.PI * 2;
      d += `<circle cx="${60 + Math.cos(a) * rr}" cy="${60 + Math.sin(a) * rr}" r="${2 - ring * 0.6}" fill="#fff" opacity="${o.toFixed(2)}"/>`;
    }
  }
  return `<div class="frame"><div class="cell"><svg width="120" height="120" style="position:absolute;left:0;top:0">${d}
    <circle cx="60" cy="60" r="${p < 0.15 ? 9 : 0}" fill="rgba(255,255,255,.25)"/></svg></div><div class="t">${label}</div></div>`;
}

function clockCell(t, label) {
  return `<div class="frame"><div class="cell" style="width:156px">${dots('09:41', { dot: 4.4, off: t === 1 ? '#151515' : null, t, scatter: 60, seed: 11 })}</div><div class="t">${label}</div></div>`;
}

function bubbleCell(letter, s, label, prev) {
  const col = 'JKLMN'.split('').map(l => `<span style="font:700 8px Mono;color:${l === letter ? '#fff' : '#555'};${l === letter ? 'transform:scale(1.6)' : ''}">${l}</span>`).join('');
  return `<div class="frame"><div class="cell">
    <div style="position:absolute;right:10px;top:22px;bottom:22px;display:flex;flex-direction:column;justify-content:space-between;align-items:center">${col}</div>
    <div style="position:absolute;right:34px;top:${18 + 'JKLMN'.indexOf(letter) * 17}px;width:52px;height:52px;border-radius:50% 50% 6px 50%;background:#fff;transform:rotate(-45deg) scale(${s});display:flex;align-items:center;justify-content:center">
      <span style="transform:rotate(45deg);font:900 26px Doto;color:#000">${letter}</span></div>
    ${prev ? `<div style="position:absolute;left:12px;bottom:10px;font:400 8px Mono;color:${RED}">TICK</div>` : ''}
    </div><div class="t">${label}</div></div>`;
}

function stackCell(p, label) {
  const size = 46 + p * 64, r = 23 + (24 - 23) * p;
  const ic = ['messages', 'photos', 'video', 'contacts', 'mail', 'news'];
  const inner = p < 0.5
    ? `<div style="display:grid;grid-template-columns:1fr 1fr;gap:${2 + p * 6}px">${ic.slice(0, 4).map(n => icon(n, 12 + p * 6)).join('')}</div>`
    : `<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;opacity:${(p - 0.5) * 2}">${ic.map(n => `<span style="width:22px;height:22px;border-radius:50%;background:#222;display:flex;align-items:center;justify-content:center">${icon(n, 12)}</span>`).join('')}</div>`;
  return `<div class="frame"><div class="cell"><div style="width:${size}px;height:${size}px;border-radius:${p < 0.2 ? '50%' : r + 'px'};background:#1a1a1a;box-shadow:inset 0 0 0 1px #2a2a2a;display:flex;align-items:center;justify-content:center">${inner}</div></div><div class="t">${label}</div></div>`;
}

// ---------- widgets (gallery)
function widget(kind) {
  switch (kind) {
    case 'weather': return `<div class="w w2 wweather" style="width:150px">${homeWeatherInner()}</div>`;
    case 'battery': return `<div class="w w2 wbatt" style="width:150px">${batteryRing(0.82, 58)}<div class="wb-rows"><span>${icon('phone', 11, '#999')} 82%</span><span>${icon('earbuds', 11, '#999')} 64%</span></div></div>`;
    case 'clock': {
      let d = '';
      for (let i = 0; i < 60; i++) {
        const a = -Math.PI / 2 + (i / 60) * Math.PI * 2, big = i % 5 === 0;
        d += `<circle cx="${59 + Math.cos(a) * 46}" cy="${59 + Math.sin(a) * 46}" r="${big ? 2.2 : 1}" fill="${i === 34 ? RED : big ? '#fff' : '#444'}"/>`;
      }
      const h = -Math.PI / 2 + ((9 + 41 / 60) / 12) * Math.PI * 2, m = -Math.PI / 2 + (41 / 60) * Math.PI * 2;
      for (let k = 1; k <= 5; k++) d += `<circle cx="${59 + Math.cos(h) * k * 5.5}" cy="${59 + Math.sin(h) * k * 5.5}" r="2.2" fill="#fff"/>`;
      for (let k = 1; k <= 7; k++) d += `<circle cx="${59 + Math.cos(m) * k * 5.5}" cy="${59 + Math.sin(m) * k * 5.5}" r="1.8" fill="#bbb"/>`;
      return `<div class="w w2" style="width:150px;display:flex;align-items:center;justify-content:center"><svg width="118" height="118">${d}</svg></div>`;
    }
    case 'next': return `<div class="w" style="width:310px;height:78px;display:flex;align-items:center;gap:14px">
      <div style="width:50px;height:50px;border-radius:14px;background:#1c1c1c;display:flex;flex-direction:column;align-items:center;justify-content:center"><span style="font:400 7.5px Mono;color:${RED};letter-spacing:.1em">NOV</span><span style="font:900 20px Doto">21</span></div>
      <div style="flex:1"><div style="font:400 8.5px Mono;letter-spacing:.16em;color:#888">NEXT · IN 4 H</div><div style="font:600 14px Inter;margin-top:2px">Dentist</div><div style="font-size:9.5px;color:#999">14:00 – 14:30 · High St</div></div>
      <div style="font:600 10px Inter;background:#fff;color:#000;border-radius:14px;padding:6px 11px">Directions</div></div>`;
    case 'music': return `<div class="w w4 wmusic" style="width:310px">${document.querySelector('#musicTpl') ? '' : ''}
      <div class="wm-art">${artDots(5, 8)}</div>
      <div class="wm-meta"><div class="wm-title">Midnight Drive</div><div class="wm-artist">Analog Sun</div><div class="wm-bar">${Array.from({ length: 26 }, (_, i) => `<i class="${i < 9 ? 'on' : ''}"></i>`).join('')}</div></div>
      <div style="display:flex;gap:10px;align-items:center">${icon('prev', 14, '#999')}<div class="wm-ctrl">${icon('pause', 16)}</div>${icon('next', 14, '#999')}</div></div>`;
    case 'steps': return `<div class="w" style="width:150px;height:78px"><div class="w-label">STEPS</div><div style="margin-top:8px">${dots('6248', { dot: 3.6, off: null })}</div>
      <div style="font-size:9px;color:#999;margin-top:7px">62% of today's goal</div></div>`;
    case 'torch': return `<div class="w" style="width:78px;height:78px;display:flex;align-items:center;justify-content:center;background:#fff">${icon('torch', 26, '#000')}</div>`;
    case 'focus': return `<div class="w" style="width:78px;height:78px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px">${icon('bell', 22, RED)}<span style="font:400 7.5px Mono;color:#aaa;letter-spacing:.1em">QUIET</span></div>`;
  }
}
function homeWeatherInner() {
  return `<div class="w-label">LONDON</div><div class="w-big">${dots('8°', { dot: 6.4, off: null })}</div><div class="w-sub">Cloudy · H 10° L 4°</div>
    <div class="hourly">${[5, 6, 8, 9, 8, 7, 6, 5].map((h, i) => `<i style="height:${h * 3}px;${i === 2 ? 'background:' + RED : ''}"></i>`).join('')}</div>`;
}

// ---------- glyph back
function glyphBack(mode) {
  const lit = (part) => {
    if (mode === 'charge') return part.startsWith('ring') ? (parseInt(part.slice(4)) < 11 ? '#fff' : '#2a2a2a') : '#2a2a2a';
    if (mode === 'notify') return part === 'line' || part === 'dot' ? '#fff' : '#2a2a2a';
    if (mode === 'timer') return part.startsWith('ring') ? (parseInt(part.slice(4)) < 6 ? '#fff' : '#2a2a2a') : '#2a2a2a';
    return '#2a2a2a';
  };
  let ring = '';
  const segs = 16, cx = 80, cy = 150, R = 34;
  for (let i = 0; i < segs; i++) {
    const a0 = -Math.PI / 2 + 0.25 + (i / segs) * (Math.PI * 2 - 0.9), a1 = a0 + (Math.PI * 2 - 0.9) / segs - 0.12;
    ring += `<path d="M${cx + Math.cos(a0) * R} ${cy + Math.sin(a0) * R} A ${R} ${R} 0 0 1 ${cx + Math.cos(a1) * R} ${cy + Math.sin(a1) * R}" stroke="${lit('ring' + i)}" stroke-width="5" stroke-linecap="round" fill="none" ${lit('ring' + i) === '#fff' ? `filter="url(#glow${mode})"` : ''}/>`;
  }
  const glow = c => c === '#fff' ? `filter="url(#glow${mode})"` : '';
  return `<svg width="200" height="375" viewBox="0 0 160 300">
    <defs><filter id="glow${mode}" filterUnits="userSpaceOnUse" x="0" y="0" width="160" height="300"><feGaussianBlur stdDeviation="2.4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>
    <rect x="2" y="2" width="156" height="296" rx="26" fill="#0b0b0b" stroke="#333"/>
    <rect x="16" y="16" width="52" height="74" rx="20" fill="#111" stroke="#2a2a2a"/>
    <circle cx="42" cy="38" r="11" fill="#050505" stroke="#333"/><circle cx="42" cy="68" r="11" fill="#050505" stroke="#333"/>
    <path d="M14 30 A 26 26 0 0 1 40 12 L 52 12" stroke="${lit('cam')}" stroke-width="5" fill="none" stroke-linecap="round"/>
    <path d="M128 22 L 100 58" stroke="${lit('diag')}" stroke-width="5" stroke-linecap="round"/>
    ${ring}
    <path d="M80 214 L 80 262" stroke="${lit('line')}" stroke-width="5" stroke-linecap="round" ${glow(lit('line'))}/>
    <circle cx="80" cy="278" r="3.5" fill="${lit('dot')}" ${glow(lit('dot'))}/>
  </svg>`;
}

// ---------- reach zones
function reachPhone() {
  return phone(`<div class="home wall" style="position:relative">
      <div style="position:absolute;inset:0;background:linear-gradient(180deg, rgba(215,25,33,.22) 0%, rgba(215,25,33,.08) 32%, rgba(255,255,255,0) 34%, rgba(255,255,255,0) 58%, rgba(255,255,255,.07) 60%, rgba(255,255,255,.07) 100%)"></div>
      <div style="position:absolute;left:16px;top:20px;font:400 9px Mono;color:${RED};letter-spacing:.14em">HARD TO REACH · LOOK, DON'T TOUCH</div>
      <div style="position:absolute;left:16px;top:225px;font:400 9px Mono;color:#888;letter-spacing:.14em">STRETCH</div>
      <div style="position:absolute;left:16px;top:350px;font:400 9px Mono;color:#fff;letter-spacing:.14em">EASY · EVERYTHING YOU TAP LIVES HERE</div>
      <svg style="position:absolute;left:0;top:0" width="270" height="590"><path d="M300 600 A 250 250 0 0 0 40 330" stroke="#fff" stroke-dasharray="3 5" fill="none"/></svg>
      <div style="position:absolute;bottom:18px;left:0;right:0">
        <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1])).join('')}</div>${dock()}</div>
    </div>`, { zoom: 0.9 });
}

function gesturePhone() {
  const arrow = (x1, y1, x2, y2, label, lx, ly) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#fff" stroke-width="2" marker-end="url(#ah)"/>
    <text x="${lx}" y="${ly}" fill="#fff" font-family="Mono" font-size="9" letter-spacing="1">${label}</text>`;
  return phone(`<div class="home wall" style="position:relative">
      <div style="opacity:.1;flex:1;display:flex;flex-direction:column">${clockBlock()}<div class="flex1"></div>
      <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1])).join('')}</div>${dock()}</div>
      <svg style="position:absolute;left:0;top:0" width="270" height="590">
        <defs><marker id="ah" markerWidth="8" markerHeight="8" refX="5" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8 z" fill="#fff"/></marker></defs>
        ${arrow(48, 120, 48, 230, 'SWIPE DOWN', 60, 168)}
        <text x="60" y="181" fill="#888" font-family="Mono" font-size="8" letter-spacing="1">NOTIFICATIONS</text>
        ${arrow(48, 520, 48, 400, 'SWIPE UP', 60, 466)}
        <text x="60" y="479" fill="#888" font-family="Mono" font-size="8" letter-spacing="1">APPS + SEARCH</text>
        ${arrow(120, 300, 210, 300, 'SWIPE RIGHT', 122, 288)}
        <text x="122" y="318" fill="#888" font-family="Mono" font-size="8" letter-spacing="1">TODAY</text>
        <circle cx="205" cy="170" r="14" fill="none" stroke="${RED}" stroke-width="2"/><circle cx="205" cy="170" r="22" fill="none" stroke="${RED}" stroke-width="1" stroke-dasharray="2 3"/>
        <text x="160" y="214" fill="${RED}" font-family="Mono" font-size="9" letter-spacing="1">DOUBLE-TAP</text>
        <text x="160" y="227" fill="#888" font-family="Mono" font-size="8" letter-spacing="1">LOCK</text>
        <path d="M168 392 l -12 -12 M168 392 l 0 -10 M168 392 l -10 0 M212 436 l 12 12 M212 436 l 0 10 M212 436 l 10 0" stroke="#bbb" stroke-width="2" stroke-linecap="round"/>
        <text x="152" y="470" fill="#bbb" font-family="Mono" font-size="9" letter-spacing="1">PINCH</text>
        <text x="152" y="483" fill="#888" font-family="Mono" font-size="8" letter-spacing="1">EDIT HOME</text>
      </svg>
    </div>`, { zoom: 0.9 });
}

// ================================================================== PAGES
function buildPages() {
  const out = [];

  // 1 · cover
  out.push(page(`
    <div class="cols">
      <div class="col-text" style="width:420px;justify-content:center">
        <div class="kicker">Design preview · Nothing Phone (2)</div>
        <div style="display:flex;flex-direction:column;gap:22px">${dots('DOT', { dot: 15, off: '#141414' })}${dots('HOME', { dot: 15, off: '#141414' })}</div>
        <p class="lede" style="margin-top:26px">A home screen for your Phone (2) that is calm to look at and alive to touch. Every tap answers with motion and a click you can feel, and nothing is on screen that doesn't earn its place.</p>
        <div style="margin-top:22px"><span class="tag red">Intuitive</span><span class="tag">Smooth</span><span class="tag">Useful</span><span class="tag">Minimal</span></div>
        <p class="small" style="margin-top:28px;max-width:52ch">Every screen in this document is a mock-up of what I would build. Motion is shown as storyboards with real timings and spring curves, because a PDF can't move.</p>
      </div>
      <div class="stage" style="justify-content:flex-start;gap:0;position:relative;margin-left:-60px">
        <div style="transform:translateX(130px) scale(.86);opacity:.8">${phone(drawerScreen())}</div>
        <div style="z-index:2">${phone(homeScreen())}</div>
      </div>
    </div>`, { folio: false }));

  // 2 · principles
  const princ = [
    ['01', 'Intuitive', 'Nothing to learn. Swipe up for apps, type to find anything, hold for more. Every gesture matches what Android already taught you, and the first launch shows each one once, then gets out of the way.'],
    ['02', 'Smooth', 'Built for the Phone (2)\'s 120 Hz screen. Everything follows your finger 1:1 and settles with real spring physics, so motion can be interrupted at any point and never feels canned.'],
    ['03', 'Useful', 'Search does the heavy lifting: apps, contacts, settings, sums, units and timers from one box. Glance information (next event, weather, battery) sits under the clock, not behind a tap.'],
    ['04', 'Minimal', 'Black, white and one red. One accent per screen, used for what matters right now. No badges with numbers, no clutter, and empty space you can leave empty.'],
  ];
  out.push(page(`
    <div class="kicker">Principles</div>
    <h2>Four rules, one character</h2>
    <div class="grid4" style="margin-top:22px">${princ.map(p => `<div class="card" style="height:262px;display:flex;flex-direction:column">
      ${dots(p[0], { dot: 5, off: '#1c1c1c' })}<h3 style="font-size:18px;margin-top:22px">${p[1]}</h3><p style="font-size:12.5px;margin-top:6px">${p[2]}</p></div>`).join('')}</div>
    <div class="card" style="margin-top:18px;display:flex;gap:40px;align-items:center">
      <div style="width:250px;flex:none"><div class="kicker" style="margin:0">Keeping the Nothing character</div></div>
      <div class="grid4" style="flex:1;gap:24px">
        <div><b>Dot matrix</b><p class="small">Clock, numbers and headings in dots, like the Glyph and Nothing's own type.</p></div>
        <div><b>Monochrome</b><p class="small">Every icon drawn in the same white line style, from each app's own monochrome icon.</p></div>
        <div><b>Mechanical</b><p class="small">Crisp, short haptics and springy motion, like pressing a well-made button.</p></div>
        <div><b>Honest</b><p class="small">No fake glass or 3D. Flat shapes, real shadows only where something is lifted.</p></div>
      </div>
    </div>
    <div style="display:flex;justify-content:space-between;margin-top:24px;padding:0 4px">
      ${[['03', 'Design language'], ['04', 'Home'], ['06', 'Widgets'], ['07', 'Drawer'], ['08', 'Search'], ['09', 'Hold & stacks'], ['10', 'Gestures'], ['11', 'Customise'], ['12', 'Motion'], ['16', 'Glyph'], ['18', 'Plan']]
        .map(c => `<div><div class="mono" style="font-size:9px;color:${RED}">${c[0]}</div><div style="font-size:11px;color:#bbb">${c[1]}</div></div>`).join('')}
    </div>`, { section: 'Principles' }));

  // 3 · design language
  const sw = [['#000000', 'Black', 'Background'], ['#111111', 'Surface', 'Cards, widgets'], ['#222222', 'Line', 'Borders, dividers'], ['#8A8A8A', 'Mute', 'Secondary text'], ['#FFFFFF', 'White', 'Text, icons'], ['#D71921', 'Signal', 'One thing that matters']];
  out.push(page(`
    <div class="kicker">Design language</div>
    <h2>Black, white, one red, and dots</h2>
    <div style="display:flex;gap:28px;margin-top:20px">
      <div style="flex:1.1">
        <h3>Colour</h3>
        <div style="display:flex;gap:10px;margin-top:8px">${sw.map(s => `<div style="flex:1"><div style="height:74px;border-radius:16px;background:${s[0]};border:1px solid #2a2a2a"></div>
          <div style="font:600 11px Inter;margin-top:7px">${s[1]}</div><div class="mono" style="font-size:9px;color:#777">${s[0]}</div><div class="small" style="font-size:9.5px">${s[2]}</div></div>`).join('')}</div>
        <p class="small" style="margin-top:10px;max-width:70ch">Red appears at most once per screen: a notification dot, the active letter, the pending autocorrection, the current hour. If everything is red, nothing is.</p>
        <h3 style="margin-top:22px">Type</h3>
        <div class="card" style="display:flex;gap:26px;align-items:flex-end;margin-top:8px">
          <div><div style="font:900 40px Doto;line-height:1;white-space:nowrap">Aa 123</div><div class="small mono" style="margin-top:6px">DOTO · DISPLAY, NUMBERS</div></div>
          <div><div style="font:600 30px Inter;line-height:1">Aa 123</div><div class="small mono" style="margin-top:6px">INTER · TEXT, LABELS</div></div>
          <div><div style="font:400 22px Mono;line-height:1">AA 123</div><div class="small mono" style="margin-top:6px">SPACE MONO · SMALL CAPS</div></div>
        </div>
        <p class="small" style="margin-top:8px;max-width:70ch">Nothing's own Ndot font can't be redistributed, so Dot Home uses Doto (free, open licence) for display text and a hand-built 5 × 7 dot matrix for the clock.</p>
      </div>
      <div style="flex:1">
        <h3>The clock: a real dot matrix</h3>
        <div class="card" style="margin-top:8px;display:flex;flex-direction:column;align-items:center;padding:26px">${dots('09:41', { dot: 11, off: '#1a1a1a' })}
          <div class="small mono" style="margin-top:12px">5 × 7 GRID · UNLIT DOTS STAY FAINTLY VISIBLE</div></div>
        <h3 style="margin-top:20px">Icons</h3>
        <div class="card" style="margin-top:8px;display:flex;flex-wrap:wrap;gap:10px;justify-content:space-between">
          ${['phone', 'messages', 'camera', 'photos', 'maps', 'music', 'mail', 'browser', 'calendar', 'notes', 'clock', 'settings', 'wallet', 'weather', 'files', 'video'].map(n => app(n, '', { size: 38, labels: false, style: 'width:auto' })).join('')}
        </div>
        <p class="small" style="margin-top:8px">Android 13+ apps include a one-colour version of their icon. Dot Home draws every one in white on a dark disc. Apps without one get a matching greyscale version.</p>
        <h3 style="margin-top:16px">Shape</h3>
        <div style="display:flex;gap:14px;align-items:center;margin-top:8px">
          <div style="width:46px;height:46px;border-radius:50%;background:#1a1a1a;border:1px solid #2a2a2a"></div><span class="small" style="white-space:nowrap">Circles: apps</span>
          <div style="width:70px;height:46px;border-radius:18px;background:#111;border:1px solid #2a2a2a"></div><span class="small" style="white-space:nowrap">Cards</span>
          <div style="width:70px;height:26px;border-radius:13px;background:#151515;border:1px solid #2a2a2a"></div><span class="small" style="white-space:nowrap">Pills: search, chips</span>
        </div>
      </div>
    </div>`, { section: 'Design language' }));

  // 4 · home hero
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Home</div>
        <h2>Everything you need, at a glance</h2>
        <p>The top half is for looking, the bottom half for touching. The clock and today's essentials sit where your eyes land; everything you tap sits where your thumb already is.</p>
        <ol class="notes">
          <li><b>Dot-matrix clock.</b> Tap it to open Clock. When the screen wakes, its dots fly together into place.</li>
          <li><b>Glance chips.</b> Weather, your next event and battery. Each one is a shortcut: tap the event to open it, or the battery to see what's using it.</li>
          <li><b>Pinned apps.</b> Up to 4 × 3, your choice. Labels can be turned off for an even quieter look.</li>
          <li><b>Notification dot.</b> One small red dot, never a number. Hold the icon to see what's waiting.</li>
          <li><b>Dock.</b> Four apps you use all day, on a barely-there tray.</li>
          <li><b>Swipe hint.</b> Three dots. Swipe up anywhere for every app and search.</li>
        </ol>
      </div>
      <div class="stage">
        <div style="position:relative">${phone(homeScreen())}${pins([[1, 236, 74], [2, 296, 170], [3, -24, 380], [4, -24, 450], [5, -24, 560], [6, 172, 590]])}</div>
        <div style="width:250px">
          <div class="card">
            <div class="kicker" style="margin-bottom:6px">Why it works</div>
            <p style="font-size:12px">Glance info means fewer unlock-and-open trips: you see your next meeting and the weather without touching anything.</p>
            <p style="font-size:12px;margin-top:8px">Pinned apps and the dock sit in the easy-reach zone, so the phone works one-handed.</p>
            <p style="font-size:12px;margin-top:8px">The wallpaper is pure black with a faint dot grid, so OLED pixels stay off and the battery lasts longer.</p>
          </div>
        </div>
      </div>
    </div>`, { section: 'Home' }));

  // 5 · home variants
  out.push(page(`
    <div class="kicker">Home · three ways</div>
    <h2>Make it as quiet or as rich as you like</h2>
    <div class="stage" style="margin-top:10px;align-items:flex-start">
      <div>${phone(homeScreen('widgets'), { zoom: 0.86 })}<div class="cap"><b>Widgets</b> · Nothing-style weather, battery, music</div></div>
      <div>${phone(homeScreen('minimal'), { zoom: 0.86 })}<div class="cap"><b>Text only</b> · names instead of icons</div></div>
      <div>${phone(glanceScreen(), { zoom: 0.86 })}<div class="cap"><b>Today</b> · swipe right from home</div></div>
    </div>`, { section: 'Home' }));

  // 6 · widgets
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Widgets</div>
        <h2>Small, dotted, actually useful</h2>
        <p>A built-in set that matches the look perfectly, drawn in the same dots and type as the clock. Any normal Android widget works too, but these are made to fit.</p>
        <ol class="notes" style="margin-top:14px">
          <li><b>Weather.</b> Temperature in dots, today's hourly trend as bars, now marked in red.</li>
          <li><b>Battery.</b> A ring of 24 dots for the phone, with earbuds below when connected.</li>
          <li><b>Dot clock.</b> Sixty dots around the edge. Hands made of dots, seconds in red.</li>
          <li><b>Next up.</b> Your next event with a one-tap action (directions, join call).</li>
          <li><b>Now playing.</b> Track, a dot progress bar, play and pause.</li>
          <li><b>Steps, torch, quiet mode.</b> Single-purpose tiles: one tap, done.</li>
        </ol>
      </div>
      <div class="stage" style="flex-direction:column;align-items:flex-start;justify-content:center;gap:16px;zoom:1.22">
        <div style="display:flex;gap:14px">${widget('weather')}${widget('battery')}${widget('clock')}</div>
        <div style="display:flex;gap:14px">${widget('next')}${widget('steps')}</div>
        <div style="display:flex;gap:14px">${widget('music')}${widget('torch')}${widget('focus')}</div>
      </div>
    </div>`, { section: 'Widgets' }));

  // 7 · drawer
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">App drawer</div>
        <h2>Every app, two ways to find it</h2>
        <p>Swipe up from anywhere on home. The drawer follows your finger, and the search box is ready so you can start typing straight away.</p>
        <ol class="notes">
          <li><b>Suggested.</b> Four apps you're likely to want now, based on time of day and habit, learned on your phone.</li>
          <li><b>A–Z, grouped.</b> Big dotted letters make scanning easy. Hidden apps never show here.</li>
          <li><b>Dot scrubber.</b> Slide down the right edge: a bubble shows the letter under your thumb, the list jumps there, and each letter gives a tiny tick you can feel.</li>
          <li><b>Pull down to close.</b> Or swipe back. The drawer always remembers where you were for 30 seconds.</li>
        </ol>
      </div>
      <div class="stage">
        <div>${phone(drawerScreen())}<div class="cap"><b>Open</b> · suggested + A–Z</div></div>
        <div>${phone(drawerScreen({ from: 'M', active: 'M', groups: 5, bubbleTop: 290 }))}<div class="cap"><b>Scrubbing</b> · thumb on “M”</div></div>
      </div>
    </div>`, { section: 'App drawer' }));

  // 8 · search
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Search</div>
        <h2>Type two letters, press go</h2>
        <p>Search is the fastest way to do almost anything. It's on the same screen as the drawer, opens with your keyboard ready, and works with Swipe Keys or any keyboard.</p>
        <ol class="notes">
          <li><b>Top hit.</b> The best match is highlighted. Press go on the keyboard to open it, without looking for it.</li>
          <li><b>Everything in one list.</b> Apps, contacts (with call and message buttons), settings pages, then the web.</li>
          <li><b>Instant answers.</b> Sums, unit conversions and time zones, worked out as you type, with one-tap copy.</li>
          <li><b>Quick actions.</b> “timer 10”, “alarm 7” or “note buy milk” do the thing directly.</li>
        </ol>
        <p class="small" style="margin-top:10px">Contacts only appear if you allow it. Everything is searched on the phone; nothing is sent anywhere until you choose “search the web”.</p>
      </div>
      <div class="stage">
        <div>${phone(searchScreen('apps'))}<div class="cap"><b>“ca”</b> · apps, contact, settings</div></div>
        <div>${phone(searchScreen('answers'))}<div class="cap"><b>“12*4+3”</b> · instant answer</div></div>
      </div>
    </div>`, { section: 'Search' }));

  // 9 · hold + stacks
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Touch & hold · Stacks</div>
        <h2>Hold for more, group without folders</h2>
        <h3>Touch & hold an app</h3>
        <p>The icon lifts towards you, everything else dims and blurs, and a card shows the app's own shortcuts. The actions underneath work on any app: info, hide, change icon, remove.</p>
        <h3>Stacks</h3>
        <p>A stack is a folder that doesn't feel like one: up to nine apps behind one icon, showing four of them in miniature. Tap and it expands from the icon itself into a card. Swipe down to drop it back.</p>
        <div style="display:flex;gap:18px;align-items:center;margin-top:18px" class="card">${stackIcon()}<p class="small" style="max-width:none">Drag one app onto another to make a stack. Its name is suggested from the apps inside (Social, Travel, Money) and is easy to change.</p></div>
      </div>
      <div class="stage">
        <div>${phone(longPressScreen())}<div class="cap"><b>Hold</b> · lifted icon + shortcuts</div></div>
        <div>${phone(stackScreen())}<div class="cap"><b>Stack</b> · expanded</div></div>
      </div>
    </div>`, { section: 'Interactions' }));

  // 10 · gestures & reach
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Gestures & reach</div>
        <h2>One-handed by design</h2>
        <p>The Phone (2) is tall. Everything you touch lives in the bottom half, the top half is for reading, and every gesture works anywhere on the screen.</p>
        <table class="spec" style="margin-top:16px">
          <tr><th>Gesture</th><th>Does</th></tr>
          <tr><td>Swipe up</td><td>App drawer and search</td></tr>
          <tr><td>Swipe down</td><td>Notification shade, from anywhere, not just the top edge</td></tr>
          <tr><td>Swipe right</td><td>Today: next event, weather, screen time, a note</td></tr>
          <tr><td>Double-tap</td><td>Lock the screen</td></tr>
          <tr><td>Pinch</td><td>Edit home</td></tr>
          <tr><td>Hold empty space</td><td>Edit home</td></tr>
        </table>
        <p class="small" style="margin-top:10px">Every gesture can be changed or switched off in settings (for example, swipe left to open the camera or the torch).</p>
      </div>
      <div class="stage">
        <div>${gesturePhone()}<div class="cap"><b>Gestures</b></div></div>
        <div>${reachPhone()}<div class="cap"><b>Reach zones</b> · right thumb</div></div>
      </div>
    </div>`, { section: 'Gestures' }));

  // 11 · edit + settings
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Edit & settings</div>
        <h2>Change things where they are</h2>
        <h3>Edit home</h3>
        <p>Pinch in and home shrinks into a card with its grid showing as faint dashed cells. Drag an app and the cell it will land in lights up red, with a tick as it snaps. Widgets, wallpaper and layout are one tap away underneath.</p>
        <h3>Settings</h3>
        <p>One short page, no menus inside menus. Every option shows what it does immediately in the background.</p>
        <ol class="notes" style="margin-top:12px">
          <li><b>Animations: Full, Calm, Off.</b> Calm swaps springs for short fades. Off follows Android's “remove animations”.</li>
          <li><b>Haptics: Off, Soft, Crisp.</b> Crisp is the default, short and mechanical.</li>
          <li><b>Glyph echo.</b> Optional: the back lights answer some actions (see Glyph).</li>
        </ol>
      </div>
      <div class="stage">
        <div>${phone(editScreen())}<div class="cap"><b>Edit home</b> · dragging Wallet</div></div>
        <div>${phone(settingsScreen())}<div class="cap"><b>Settings</b></div></div>
      </div>
    </div>`, { section: 'Customise' }));

  // 12 · motion: touch
  out.push(page(`
    <div class="kicker">Motion · every touch</div>
    <h2>Press, ripple, release</h2>
    <p style="max-width:90ch">Every tap gets three answers at once: the thing you touched moves, dots ripple out from your finger, and you feel a short click. The timings below are the real values I'd build with.</p>
    <div style="margin-top:14px">
      <div class="motion-row">
        <div class="motion-meta"><h3>Press & release</h3><p class="small">The icon squashes on touch and springs back slightly past full size when you let go.</p>
          <div class="spec-line">PRESS <b>scale 0.90 · 70 ms</b><br>RELEASE <b>spring k 600 · ζ 0.55</b><br>HAPTIC <b>crisp tick on release</b></div></div>
        <div class="board">${pressCell(1, 0, '<b>0</b> MS · TOUCH')}<span class="arrow">→</span>${pressCell(0.9, 0.5, '<b>70</b> MS · PRESSED')}<span class="arrow">→</span>${pressCell(1.06, 0, '<b>150</b> MS · OVERSHOOT')}<span class="arrow">→</span>${pressCell(1, 0, '<b>320</b> MS · AT REST')}</div>
        <div><div class="small mono" style="margin-bottom:4px;font-size:9px">SCALE · 0.6 S</div>${springCurve(600, 0.55, { from: 0.9, to: 1, lo: 0.86, hi: 1.1, w: 120, h: 96 })}</div>
      </div>
      <div class="motion-row">
        <div class="motion-meta"><h3>Dot ripple</h3><p class="small">Two rings of dots spread from exactly where your finger landed and fade, like the Glyph waking up.</p>
          <div class="spec-line">DURATION <b>420 ms · ease out</b><br>RADIUS <b>0 → 56 dp</b><br>WHERE <b>icons, cards, empty space</b></div></div>
        <div class="board">${rippleCell(0.02, '<b>0</b> MS')}<span class="arrow">→</span>${rippleCell(0.3, '<b>120</b> MS')}<span class="arrow">→</span>${rippleCell(0.6, '<b>250</b> MS')}<span class="arrow">→</span>${rippleCell(0.88, '<b>380</b> MS')}</div>
      </div>
      <div class="motion-row">
        <div class="motion-meta"><h3>Touch & hold build-up</h3><p class="small">While you hold, a ring of dots fills around the icon, so you can see the long press coming and let go early to cancel.</p>
          <div class="spec-line">FILL <b>dots light one by one, 400 ms</b><br>THEN <b>icon lifts · firm press</b><br>CANCEL <b>lift finger or move 8 dp</b></div></div>
        <div class="board">${holdCell(0, '<b>0</b> MS')}<span class="arrow">→</span>${holdCell(0.4, '<b>160</b> MS')}<span class="arrow">→</span>${holdCell(0.8, '<b>320</b> MS')}<span class="arrow">→</span>${holdCell(1, '<b>400</b> MS · LIFT')}</div>
      </div>
    </div>`, { section: 'Motion' }));

  // 13 · motion: navigation
  out.push(page(`
    <div class="kicker">Motion · moving around</div>
    <h2>Follows your finger, settles on its own</h2>
    <div class="motion-row" style="padding-top:8px">
      <div class="motion-meta"><h3>Opening the drawer</h3><p class="small">The sheet tracks your thumb 1:1. Home sinks back and dims. Rows of apps rise in one after another, 15 ms apart. Let go and a spring finishes the move: flick fast and it opens, drag slowly back and it closes.</p>
        <div class="spec-line">DRAG <b>1:1 with finger</b><br>RELEASE <b>spring k 400 · ζ 0.85</b><br>STAGGER <b>15 ms per row</b><br>HAPTIC <b>soft detent when fully open</b></div>
        <div style="margin-top:12px">${springCurve(400, 0.85, { w: 190, h: 70 })}</div></div>
      <div class="board">${[0, 0.3, 0.6, 0.85, 1].map((p, i) => `<div class="frame">${drawerFrame(p)}<div class="t"><b>${[0, 60, 130, 200, 300][i]}</b> MS</div></div>`).join('')}</div>
    </div>
    <div class="motion-row">
      <div class="motion-meta"><h3>Opening an app</h3><p class="small">The app grows out of its own icon, so you always know where it came from. Home pushes back slightly behind it. Closing reverses into the icon.</p>
        <div class="spec-line">DURATION <b>300 ms · decelerate</b><br>CORNERS <b>23 → 37 px (screen corners)</b><br>CLOSE <b>shrinks back into the icon</b></div></div>
      <div class="board">${[0, 0.25, 0.5, 0.75, 1].map((p, i) => `<div class="frame">${appOpenFrame(p)}<div class="t"><b>${[0, 75, 150, 225, 300][i]}</b> MS</div></div>`).join('')}</div>
    </div>`, { section: 'Motion' }));

  // 14 · motion: signature
  out.push(page(`
    <div class="kicker">Motion · signature moments</div>
    <h2>The details people notice</h2>
    <div class="motion-row" style="padding-top:8px">
      <div class="motion-meta"><h3>Clock wakes up</h3><p class="small">When you unlock, the clock's dots fly in from scattered points and click into the time. It plays once per unlock, takes under half a second, and never blocks a tap.</p>
        <div class="spec-line">DURATION <b>480 ms · ease out cubic</b><br>STAGGER <b>3 ms per dot</b><br>SKIPPED <b>if Animations = Calm/Off</b></div></div>
      <div class="board">${clockCell(0, '<b>0</b> MS')}<span class="arrow">→</span>${clockCell(0.18, '<b>90</b> MS')}<span class="arrow">→</span>${clockCell(0.4, '<b>190</b> MS')}<span class="arrow">→</span>${clockCell(1, '<b>480</b> MS')}</div>
    </div>
    <div class="motion-row">
      <div class="motion-meta"><h3>Scrubbing A–Z</h3><p class="small">The bubble pops out of the scrubber and follows your thumb. Each new letter gives a tiny tick, so you can feel your way down the alphabet.</p>
        <div class="spec-line">BUBBLE <b>spring k 800 · ζ 0.7</b><br>HAPTIC <b>tick per letter</b><br>LIST <b>jumps, then eases 120 ms</b></div></div>
      <div class="board">${bubbleCell('K', 0.3, '<b>0</b> MS · TOUCH')}<span class="arrow">→</span>${bubbleCell('K', 1.08, '<b>90</b> MS')}<span class="arrow">→</span>${bubbleCell('L', 1, 'NEXT LETTER', true)}<span class="arrow">→</span>${bubbleCell('M', 1, 'NEXT LETTER', true)}</div>
    </div>
    <div class="motion-row">
      <div class="motion-meta"><h3>Stack opens</h3><p class="small">The stack icon itself grows into the card, and the mini icons inside become full icons. It's one continuous shape, not a pop-up.</p>
        <div class="spec-line">EXPAND <b>spring k 450 · ζ 0.8</b><br>ICONS <b>fade in from 50%</b><br>CLOSE <b>swipe down, follows finger</b></div></div>
      <div class="board">${stackCell(0, '<b>0</b> MS')}<span class="arrow">→</span>${stackCell(0.35, '<b>90</b> MS')}<span class="arrow">→</span>${stackCell(0.7, '<b>180</b> MS')}<span class="arrow">→</span>${stackCell(1, '<b>280</b> MS')}</div>
    </div>`, { section: 'Motion' }));

  // 15 · spec
  out.push(page(`
    <div class="cols" style="gap:36px">
      <div style="flex:1.45">
        <div class="kicker">Motion & haptics spec</div>
        <h2>The numbers</h2>
        <table class="spec" style="margin-top:12px">
          <tr><th>Moment</th><th>Motion</th><th>Timing</th><th>Haptic</th></tr>
          <tr><td>Tap an app</td><td>Squash, spring back with slight overshoot, dot ripple</td><td class="m">70 ms · k600 ζ.55</td><td>Crisp tick</td></tr>
          <tr><td>Open app</td><td>Window grows from the icon</td><td class="m">300 ms decel</td><td>None</td></tr>
          <tr><td>Drawer</td><td>Tracks finger, rows rise in one by one</td><td class="m">k400 ζ.85 · 15 ms</td><td>Soft detent at the top</td></tr>
          <tr><td>Scrub A–Z</td><td>Bubble follows thumb</td><td class="m">k800 ζ.7</td><td>Tick per letter</td></tr>
          <tr><td>Touch & hold</td><td>Icon lifts 12%, background dims and blurs</td><td class="m">400 ms hold · 180 ms</td><td>Firm press</td></tr>
          <tr><td>Stack</td><td>Icon grows into card</td><td class="m">k450 ζ.8</td><td>Light tick</td></tr>
          <tr><td>Edit home</td><td>Scales with your pinch, cells light up</td><td class="m">k500 ζ.9</td><td>Tick on each snap</td></tr>
          <tr><td>Clock wake</td><td>Dots assemble into the time</td><td class="m">480 ms · 3 ms/dot</td><td>None</td></tr>
          <tr><td>Search result</td><td>Results slide in under the box</td><td class="m">160 ms, 12 ms stagger</td><td>None</td></tr>
          <tr><td>Calm / Off</td><td>Every spring becomes a 150 ms fade, or instant</td><td class="m">150 ms / 0</td><td>Unchanged</td></tr>
        </table>
        <p class="small" style="margin-top:10px;max-width:none">k = spring stiffness, ζ = damping (1 = no bounce). Lower ζ means more bounce. Every animation can be interrupted mid-way and picks up from where it is.</p>
      </div>
      <div style="flex:1">
        <div class="kicker" style="margin-top:4px">Smoothness budget</div>
        <h2 style="font-size:30px">Never drop a frame</h2>
        <div class="card" style="margin-top:12px">
          <div style="display:flex;align-items:baseline;gap:10px">${dots('8.3', { dot: 4.5, off: null })}<span class="small mono">MS PER FRAME AT 120 HZ</span></div>
          <ul style="margin:14px 0 0 16px;color:#c8c8c8;font-size:12px;line-height:1.6">
            <li>Everything that moves is drawn directly on the GPU, with no layout work while animating.</li>
            <li>Icons are drawn once and cached as images, so scrolling the drawer only moves pictures.</li>
            <li>Springs are calculated from real physics each frame, so they keep the speed of your flick.</li>
            <li>The app list is loaded at boot and kept in memory, so the drawer opens instantly.</li>
            <li>Target: open drawer in one frame, under 60 MB of memory, no work at all when the screen is idle.</li>
          </ul>
        </div>
        <div class="card" style="margin-top:14px">
          <div class="kicker" style="margin-bottom:6px">Haptic palette</div>
          <div style="display:flex;gap:10px;align-items:flex-end;height:54px">
            ${[['Tick', 14], ['Crisp', 24], ['Detent', 32], ['Firm', 44]].map(h => `<div style="flex:1;text-align:center"><div style="height:${h[1]}px;background:#fff;border-radius:4px;margin:0 auto;width:10px"></div><div class="small mono" style="margin-top:6px;font-size:8.5px">${h[0].toUpperCase()}</div></div>`).join('')}
          </div>
          <p class="small" style="margin-top:8px;max-width:none">Short, sharp pulses using Android's built-in keyboard and click effects, which the Phone (2)'s motor plays very cleanly.</p>
        </div>
      </div>
    </div>`, { section: 'Motion' }));

  // 16 · glyph
  out.push(page(`
    <div class="cols">
      <div class="col-text">
        <div class="kicker">Glyph · optional</div>
        <h2>The back joins in</h2>
        <p>The Phone (2)'s Glyph lights can echo what happens on the front. It's subtle, off by default, and only used where light says something useful.</p>
        <ol class="notes">
          <li><b>Charging.</b> The ring fills as the battery charges, visible face-down on the desk.</li>
          <li><b>People you pin.</b> Messages from up to five chosen contacts pulse the bottom strip. Everyone else stays dark.</li>
          <li><b>Timers.</b> Start a timer from search and the ring counts down in segments.</li>
        </ol>
        <div class="card" style="margin-top:14px"><p class="small" style="max-width:none"><b style="color:#fff">What it needs.</b> Apps can only control the Glyph through Nothing's Glyph Developer Kit. It's free, but you register with Nothing to get a key. I'd build everything else first and add this at the end.</p></div>
      </div>
      <div class="stage">
        <div class="frame">${glyphBack('charge')}<div class="t"><b>CHARGING</b> · 68%</div></div>
        <div class="frame">${glyphBack('notify')}<div class="t"><b>PINNED CONTACT</b></div></div>
        <div class="frame">${glyphBack('timer')}<div class="t"><b>TIMER</b> · 4 MIN LEFT</div></div>
      </div>
    </div>`, { section: 'Glyph' }));

  // 17 · limits
  out.push(page(`
    <div class="kicker">Honest limits</div>
    <h2>What stays, what asks first</h2>
    <div class="grid3" style="margin-top:20px">
      <div class="card">
        <h3 style="margin-top:0">Stays exactly as it is</h3>
        <p class="small" style="max-width:none">No app can restyle these. They already fit the look.</p>
        <ul style="margin:10px 0 0 16px;color:#c8c8c8;font-size:12px;line-height:1.7">
          <li>Notification shade and quick settings</li><li>Lock screen and always-on display</li><li>Status bar</li><li>Recent apps screen</li>
        </ul>
      </div>
      <div class="card">
        <h3 style="margin-top:0">One thing changes slightly</h3>
        <p style="font-size:12px;max-width:none">With gesture navigation, swiping up to go home from an app is drawn by Android's own launcher. When a different home app is chosen, Android plays a simpler animation for that one gesture: a quick fade instead of the app shrinking into its icon.</p>
        <p class="small" style="margin-top:8px;max-width:none">Opening apps from Dot Home still uses its own grow-from-icon animation.</p>
      </div>
      <div class="card">
        <h3 style="margin-top:0">Asks first, all optional</h3>
        <table class="spec" style="margin-top:6px">
          <tr><td>Double-tap to lock</td><td>An accessibility switch (that's the only way Android allows it)</td></tr>
          <tr><td>Contacts in search</td><td>Contacts permission</td></tr>
          <tr><td>Notification dots</td><td>Notification access</td></tr>
          <tr><td>Weather</td><td>Rough location, or type a city</td></tr>
        </table>
      </div>
    </div>
    <div class="card" style="margin-top:16px;display:flex;gap:30px;align-items:center">
      <div class="kicker" style="margin:0;width:150px;flex:none">Privacy</div>
      <p style="max-width:none;font-size:12.5px">Like Swipe Keys, everything stays on the phone: suggested apps, search history and settings never leave it. The only time it goes online is to fetch weather, if you turn weather on. Uninstalling removes everything.</p>
    </div>
    <div class="grid3" style="margin-top:16px">
      ${[['1', 'Install', 'Download the APK from your GitHub releases page, like Swipe Keys.'], ['2', 'Try it', 'Press the home button, choose Dot Home, then "Always". Your Nothing home screen stays as it is underneath.'], ['3', 'Switch back any time', 'Settings → Apps → Default apps → Home app → Nothing Launcher. One tap, nothing lost.']]
        .map(x => `<div class="card" style="display:flex;gap:14px;align-items:flex-start">${dots(x[0], { dot: 5, off: '#1c1c1c' })}<div><b>${x[1]}</b><p class="small" style="margin-top:3px">${x[2]}</p></div></div>`).join('')}
    </div>`, { section: 'Limits' }));

  // 18 · plan
  const phases = [
    ['1', 'The core', 'What you touch hundreds of times a day.', ['Home: clock, glance chips, pinned apps, dock', 'App drawer with suggested apps and A–Z scrubber', 'Search: apps, settings, sums, units, top hit on go', 'Monochrome icons for every app', 'Press, ripple, drawer and app-open motion', 'Settings basics']],
    ['2', 'Make it yours', 'Shaping it around how you use your phone.', ['Touch & hold shortcuts, hide and remove', 'Stacks', 'Edit mode with pinch and snap', 'Built-in widgets, plus any Android widget', 'Gestures and the Today page', 'Contacts and quick actions in search']],
    ['3', 'Polish', 'The details that make it feel finished.', ['Clock wake-up animation', 'Glyph echo (needs Nothing\'s key)', 'Backup and restore of your layout', 'Calm and Off motion modes tuned', 'Tuning on your phone from your feedback']],
  ];
  out.push(page(`
    <div class="kicker">Build plan</div>
    <h2>How I'd build it</h2>
    <div class="grid3" style="margin-top:18px">${phases.map(p => `<div class="card" style="height:330px">
      <div style="display:flex;align-items:center;gap:12px">${dots(p[0], { dot: 6, off: '#1c1c1c' })}<div><h3 style="margin:0;font-size:17px">${p[1]}</h3><p class="small">${p[2]}</p></div></div>
      <ul style="margin:16px 0 0 16px;color:#c8c8c8;font-size:12.5px;line-height:1.75">${p[3].map(x => `<li>${x}</li>`).join('')}</ul></div>`).join('')}</div>
    <div class="card" style="margin-top:16px">
      <div class="kicker" style="margin-bottom:8px">Before I start, tell me</div>
      <div class="grid4" style="gap:22px">
        <p class="small" style="max-width:none"><b style="color:#fff">Your dock.</b> Which four apps do you open most?</p>
        <p class="small" style="max-width:none"><b style="color:#fff">Home style.</b> Icons, widgets or text only (page 5)?</p>
        <p class="small" style="max-width:none"><b style="color:#fff">Labels.</b> App names under icons, or icons only?</p>
        <p class="small" style="max-width:none"><b style="color:#fff">Anything to change.</b> Anything in here you'd remove, or something you wish your launcher did?</p>
      </div>
    </div>`, { section: 'Next' }));

  return out.join('');
}

document.addEventListener('DOMContentLoaded', () => {
  document.body.innerHTML = buildPages();
  document.fonts.ready.then(() => { document.body.dataset.ready = '1'; });
});
