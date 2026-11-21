// Screens of Dot Home, each returns the inner HTML of a phone screen.

function clockBlock(opts = {}) {
  const time = opts.time || '09:41';
  return `<div class="clock">
    <div class="clock-digits">${dots(time, { dot: opts.dot || 8.6, off: opts.off === undefined ? '#161616' : opts.off })}</div>
    <div class="clock-date">SAT 21 NOV</div>
    ${opts.glance === false ? '' : `<div class="glance">
      <span>${icon('weather', 12, '#bbb')} 8°</span>
      <span>${icon('calendar', 12, '#bbb')} Dentist 14:00</span>
      <span class="g-batt">${icon('bolt', 12, RED)} 82%</span>
    </div>`}
  </div>`;
}

function dock(apps = ['phone', 'messages', 'browser', 'camera']) {
  return `<div class="dock">${apps.map(a => app(a, '', { size: 46, labels: false })).join('')}</div>
    <div class="swipe-hint"><span></span><span></span><span></span></div>`;
}

function homeScreen(variant = 'default', opts = {}) {
  if (variant === 'minimal') {
    return `<div class="home wall">
      ${clockBlock({ glance: false, dot: 9.4 })}
      <div class="flex1"></div>
      <div class="text-apps">
        <div>Messages</div><div>Music</div><div>Maps</div><div>Notes</div><div>Camera</div>
      </div>
      <div class="flex1"></div>
      <div class="swipe-hint"><span></span><span></span><span></span></div>
    </div>`;
  }
  if (variant === 'widgets') {
    return `<div class="home wall">
      <div class="wgrid">
        <div class="w w2 wweather">
          <div class="w-label">LONDON</div>
          <div class="w-big">${dots('8°', { dot: 6.4, off: null })}</div>
          <div class="w-sub">Cloudy · H 10° L 4°</div>
          <div class="hourly">${[5, 6, 8, 9, 8, 7, 6, 5].map((h, i) => `<i style="height:${h * 3}px;${i === 2 ? 'background:' + RED : ''}"></i>`).join('')}</div>
        </div>
        <div class="w w2 wbatt">
          ${batteryRing(0.82, 54)}
          <div class="wb-rows"><span>${icon('phone', 11, '#999')} 82%</span><span>${icon('earbuds', 11, '#999')} 64%</span></div>
        </div>
        <div class="w w4 wmusic">
          <div class="wm-art">${artDots(5, 8)}</div>
          <div class="wm-meta"><div class="wm-title">Midnight Drive</div><div class="wm-artist">Analog Sun</div>
            <div class="wm-bar">${Array.from({ length: 22 }, (_, i) => `<i class="${i < 9 ? 'on' : ''}"></i>`).join('')}</div></div>
          <div class="wm-ctrl">${icon('pause', 16)}</div>
        </div>
      </div>
      <div class="flex1"></div>
      <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1])).join('')}</div>
      ${dock()}
    </div>`;
  }
  return `<div class="home wall">
    ${clockBlock()}
    <div class="flex1"></div>
    <div class="pinned">
      ${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes'], ['mail', 'Mail', true], ['calendar', 'Calendar'], ['wallet', 'Wallet'], ['settings', 'Settings']]
        .map(a => app(a[0], a[1], { badge: a[2] })).join('')}
    </div>
    ${dock()}
  </div>`;
}

function batteryRing(level, size) {
  const n = 24, R = size / 2 - 4;
  let out = '';
  for (let i = 0; i < n; i++) {
    const a = -Math.PI / 2 + (i / n) * Math.PI * 2;
    const on = i / n < level;
    out += `<circle cx="${size / 2 + Math.cos(a) * R}" cy="${size / 2 + Math.sin(a) * R}" r="2.1" fill="${on ? (i === Math.floor(level * n) - 1 ? RED : '#fff') : '#2a2a2a'}"/>`;
  }
  return `<svg width="${size}" height="${size}">${out}<text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" fill="#fff" font-family="Doto" font-weight="900" font-size="${size * 0.3}">82</text></svg>`;
}

const ALL_APPS = {
  A: [['books', 'Audiobooks']],
  B: [['browser', 'Browser'], ['card', 'Bank']],
  C: [['calculator', 'Calculator'], ['calendar', 'Calendar'], ['camera', 'Camera'], ['clock', 'Clock'], ['contacts', 'Contacts']],
  F: [['files', 'Files'], ['fitness', 'Fitness']],
  H: [['health', 'Health']],
  M: [['mail', 'Mail'], ['maps', 'Maps'], ['messages', 'Messages'], ['music', 'Music']],
  N: [['news', 'News'], ['notes', 'Notes']],
  P: [['phone', 'Phone'], ['photos', 'Photos']],
  R: [['recorder', 'Recorder']],
  S: [['settings', 'Settings'], ['store', 'Store'], ['keyboard', 'Swipe Keys']],
  T: [['timer', 'Timer'], ['torch', 'Torch'], ['translate', 'Translate']],
  V: [['video', 'Video']],
  W: [['wallet', 'Wallet'], ['weather', 'Weather']],
};

function scrubber(active) {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
  return `<div class="scrub">${letters.map(l => {
    const has = !!ALL_APPS[l];
    const cls = l === active ? 'on' : has ? '' : 'dim';
    return `<span class="${cls}">${l}</span>`;
  }).join('')}</div>`;
}

function drawerScreen(opts = {}) {
  const startAt = opts.from || 'A';
  const letters = Object.keys(ALL_APPS).filter(l => l >= startAt);
  let groups = '';
  for (const l of letters.slice(0, opts.groups || 4)) {
    groups += `<div class="dgroup"><div class="dletter ${opts.active === l ? 'hot' : ''}">${l}</div>
      <div class="dgrid">${ALL_APPS[l].map(a => app(a[0], a[1], { size: 40 })).join('')}</div></div>`;
  }
  const bubble = opts.active ? `<div class="bubble" style="top:${opts.bubbleTop || 300}px"><span>${opts.active}</span></div>
    <div class="finger" style="top:${(opts.bubbleTop || 300) + 14}px"></div>` : '';
  return `<div class="drawer">
    <div class="search-pill">${icon('search', 15, '#888')}<span>Search apps, contacts, web</span>${icon('mic', 15, '#888')}</div>
    ${opts.active ? '' : `<div class="dsection">SUGGESTED</div>
    <div class="drecent">${[['messages', 'Messages'], ['music', 'Music'], ['maps', 'Maps'], ['camera', 'Camera']].map(a => app(a[0], a[1], { size: 40 })).join('')}</div>`}
    <div class="dlist">${groups}</div>
    ${scrubber(opts.active)}
    ${bubble}
  </div>`;
}

function searchScreen(kind = 'apps') {
  if (kind === 'answers') {
    return `<div class="drawer search">
      <div class="search-pill active">${icon('search', 15, '#fff')}<span class="q">12*4+3<i class="caret"></i></span><span class="x">✕</span></div>
      <div class="answer">
        <div class="ans-q">12 × 4 + 3 =</div>
        <div class="ans-a">${dots('51', { dot: 7, off: null })}</div>
        <div class="ans-act"><span>${icon('copy', 12)} Copy</span></div>
      </div>
      <div class="dsection">ALSO TRY</div>
      <div class="try"><span>timer 10</span><span>5 km in miles</span><span>8am in Tokyo</span></div>
      <div class="action-row">${icon('timer', 18)}<div><b>Start a 10 minute timer</b><small>Type "timer 10" and press go</small></div>${icon('chevron', 14, '#666')}</div>
      <div class="action-row">${icon('cast', 18)}<div><b>5 km = 3.11 miles</b><small>Units convert as you type</small></div>${icon('copy', 14, '#666')}</div>
      <div class="flex1"></div>
      ${keyboard(['51', '=', 'timer'])}
    </div>`;
  }
  return `<div class="drawer search">
    <div class="search-pill active">${icon('search', 15, '#fff')}<span class="q">ca<i class="caret"></i></span><span class="x">✕</span></div>
    <div class="top-hit">${app('calculator', '', { size: 44, labels: false })}<div><b>Calculator</b><small>Press go to open</small></div><span class="go-chip">GO</span></div>
    <div class="dsection">APPS</div>
    <div class="drecent">${[['calendar', 'Calendar'], ['camera', 'Camera'], ['card', 'Bank'], ['cast', 'Cast']].map(a => app(a[0], a[1], { size: 40 })).join('')}</div>
    <div class="dsection">CONTACTS</div>
    <div class="row-item"><span class="initials">CL</span><div><b>Cara Lee</b><small>Mobile · 07700 900123</small></div>${icon('phone', 15, '#888')}${icon('messages', 15, '#888')}</div>
    <div class="dsection">SETTINGS</div>
    <div class="row-item">${icon('settings', 16, '#aaa')}<div><b>Camera permissions</b><small>Privacy</small></div></div>
    <div class="row-item web">${icon('browser', 16, '#aaa')}<div><b>Search the web for “ca”</b></div>${icon('chevron', 14, '#666')}</div>
    <div class="flex1"></div>
    ${keyboard()}
  </div>`;
}

function longPressScreen() {
  return `<div class="home wall">
    <div class="dimmed">${clockBlock()}<div class="flex1"></div>
      <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes'], ['mail', 'Mail'], ['calendar', 'Calendar'], ['wallet', 'Wallet'], ['settings', 'Settings']]
        .map(a => app(a[0], a[1], { style: a[0] === 'messages' ? 'visibility:hidden' : '' })).join('')}</div>
      ${dock()}
    </div>
    <div class="popup" style="bottom:118px;left:16px">
      <div class="pop-row">${icon('edit', 16)}<span>New message</span></div>
      <div class="pop-row">${icon('contacts', 16)}<span>Cara Lee</span></div>
      <div class="pop-row">${icon('search', 16)}<span>Search chats</span></div>
      <div class="pop-actions">
        <span>${icon('info', 16)}<small>Info</small></span>
        <span>${icon('hide', 16)}<small>Hide</small></span>
        <span>${icon('edit', 16)}<small>Icon</small></span>
        <span>${icon('remove', 16, RED)}<small style="color:${RED}">Remove</small></span>
      </div>
    </div>
    <div class="lifted" style="left:74px;bottom:44px">${app('messages', '', { size: 52, labels: false })}</div>
  </div>`;
}

function stackScreen() {
  return `<div class="home wall">
    <div class="dimmed strong">${clockBlock()}<div class="flex1"></div>
      <div class="pinned">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1])).join('')}</div>
      ${dock()}
    </div>
    <div class="stack-open">
      <div class="stack-title">${'SOCIAL'}</div>
      <div class="stack-grid">${[['messages', 'Messages'], ['photos', 'Moments'], ['video', 'Clips'], ['contacts', 'Friends'], ['mail', 'Mail'], ['news', 'Forum']].map(a => app(a[0], a[1], { size: 44 })).join('')}</div>
      <div class="stack-hint">Swipe down or tap outside to close</div>
    </div>
  </div>`;
}

function stackIcon() {
  return `<div class="app"><div class="app-circle stack-ic" style="width:46px;height:46px">
    ${['messages', 'photos', 'video', 'contacts'].map(n => icon(n, 13)).join('')}</div><div class="app-label">Social</div></div>`;
}

function editScreen() {
  let cells = '';
  for (let i = 0; i < 24; i++) cells += '<i></i>';
  return `<div class="edit">
    <div class="edit-top"><span>Edit home</span><b>Done</b></div>
    <div class="edit-canvas wall">
      ${clockBlock({ dot: 6.6 })}
      <div class="edit-grid">${cells}</div>
      <div class="pinned small">${[['music', 'Music'], ['maps', 'Maps'], ['photos', 'Photos'], ['notes', 'Notes']].map(a => app(a[0], a[1], { size: 34 })).join('')}</div>
      <div class="dragging">${app('wallet', '', { size: 40, labels: false })}</div>
    </div>
    <div class="edit-tray">
      ${[['widgets', 'Widgets'], ['wallpaper', 'Wallpaper'], ['layout', 'Layout'], ['settings', 'Settings']].map(b => `<span>${icon(b[0], 18)}<small>${b[1]}</small></span>`).join('')}
    </div>
  </div>`;
}

function seg(options, on) {
  return `<span class="seg">${options.map((o, i) => `<i class="${i === on ? 'on' : ''}">${o}</i>`).join('')}</span>`;
}
function toggle(on) { return `<span class="tog ${on ? 'on' : ''}"><i></i></span>`; }

function settingsScreen() {
  return `<div class="settings">
    <div class="set-title">${'SETTINGS'}</div>
    <div class="set-group">HOME</div>
    <div class="set-row"><span>Grid</span><em>4 × 5 ${icon('chevron', 12, '#666')}</em></div>
    <div class="set-row"><span>Clock</span>${seg(['Dot', 'Thin', 'Off'], 0)}</div>
    <div class="set-row"><span>App labels</span>${toggle(true)}</div>
    <div class="set-row slider"><span>Icon size</span><span class="sl"><i style="width:55%"></i><b style="left:55%"></b></span></div>
    <div class="set-group">GESTURES</div>
    <div class="set-row"><span>Double-tap</span><em>Lock screen ${icon('chevron', 12, '#666')}</em></div>
    <div class="set-row"><span>Swipe down</span><em>Notifications ${icon('chevron', 12, '#666')}</em></div>
    <div class="set-row"><span>Swipe left / right</span><em>Off ${icon('chevron', 12, '#666')}</em></div>
    <div class="set-group">FEEL</div>
    <div class="set-row"><span>Animations</span>${seg(['Full', 'Calm', 'Off'], 0)}</div>
    <div class="set-row"><span>Haptics</span>${seg(['Off', 'Soft', 'Crisp'], 2)}</div>
    <div class="set-row"><span>Glyph echo</span>${toggle(false)}</div>
    <div class="set-group">APPS</div>
    <div class="set-row"><span>Hidden apps</span><em>2 ${icon('chevron', 12, '#666')}</em></div>
  </div>`;
}

function glanceScreen() {
  // Swipe right from home: today at a glance
  return `<div class="home wall">
    <div class="glance-page">
      <div class="gp-title">TODAY</div>
      <div class="gp-card"><div class="gp-k">NEXT</div><div class="gp-v">Dentist</div><div class="gp-s">14:00 · in 4 h 19 m</div></div>
      <div class="gp-card row"><div><div class="gp-k">WEATHER</div><div class="gp-v">${dots('8°', { dot: 4.6, off: null })}</div></div>
        <div class="hourly big">${[5, 6, 8, 9, 8, 7, 6, 5, 4].map((h, i) => `<i style="height:${h * 4}px;${i === 3 ? 'background:' + RED : ''}"></i>`).join('')}</div></div>
      <div class="gp-card"><div class="gp-k">SCREEN TIME</div><div class="gp-v">1 h 12 m</div>
        <div class="st-bars">${[30, 55, 20, 70, 45, 15, 60].map(h => `<i style="height:${h * 0.5}px"></i>`).join('')}</div></div>
      <div class="gp-card"><div class="gp-k">NOTE</div><div class="gp-s light">Pick up film at 5 · call mum</div></div>
    </div>
  </div>`;
}
