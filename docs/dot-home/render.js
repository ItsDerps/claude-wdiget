const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
(async () => {
  const dir = __dirname;
  fs.mkdirSync(path.join(dir, 'shots'), { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1123, height: 794 }, deviceScaleFactor: 1 });
  const errors = [];
  p.on('pageerror', e => errors.push(e.message));
  p.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  await p.goto('file://' + path.join(dir, 'dothome.html'));
  await p.waitForSelector('body[data-ready="1"]', { timeout: 15000 });
  await p.waitForTimeout(300);
  const only = process.argv[2] ? process.argv[2].split(',').map(Number) : null;
  const pages = await p.$$('section.page');
  for (let i = 0; i < pages.length; i++) {
    if (only && !only.includes(i + 1)) continue;
    await pages[i].screenshot({ path: path.join(dir, 'shots', `p${String(i + 1).padStart(2, '0')}.png`) });
  }
  if (!only) {
    await p.pdf({ path: path.join(dir, 'Dot-Home-design-preview.pdf'), width: '297mm', height: '210mm', printBackground: true, preferCSSPageSize: true });
  }
  console.log('pages', pages.length, 'errors', JSON.stringify(errors));
  await b.close();
})();
