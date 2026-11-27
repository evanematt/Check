// Verification screenshots for the landing hero.
// Usage: node tools/screenshot.mjs [url] [outDir]
//   url     defaults to http://localhost:8000  (serve this folder first: python3 -m http.server 8000)
//   outDir  defaults to ./shots
// Needs Playwright with Chromium: npm i -D playwright && npx playwright install chromium
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const url = process.argv[2] || 'http://localhost:8000';
const out = process.argv[3] || 'shots';
mkdirSync(out, { recursive: true });

// software WebGL so the three.js scene renders in headless mode
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const errors = [];

for (const [name, viewport, touch] of [['desktop', { width: 1440, height: 900 }, false], ['phone', { width: 390, height: 844 }, true]]) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1, hasTouch: touch });
  page.on('pageerror', (e) => errors.push(`${name}: ${e.message}`));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(`${name}: ${m.text()}`); });
  await page.goto(url);
  await page.waitForTimeout(3500);   // model, textures, fade-ins

  await page.screenshot({ path: join(out, `${name}-1-rest.png`) });

  // the lynx: look left, right, down
  if (!touch) {
    for (const [i, [x, y]] of [[20, 120], [viewport.width - 20, 300], [300, viewport.height - 20]].entries()) {
      await page.mouse.move(x, y);
      await page.waitForTimeout(900);
      await page.screenshot({ path: join(out, `${name}-2-lynx-${i}.png`) });
    }
  }

  // the book: hover (desktop) or tap (phone) → hop + card
  const box = await page.locator('.book-hit').boundingBox();
  if (box) {
    const cx = box.x + box.width / 2, cy = box.y + box.height / 2;
    if (touch) await page.touchscreen.tap(cx, cy); else await page.mouse.move(cx, cy);
    await page.waitForTimeout(1200);
    await page.screenshot({ path: join(out, `${name}-3-card-open.png`) });
  } else {
    errors.push(`${name}: .book-hit not found`);
  }

  const sideScroll = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
  if (sideScroll) errors.push(`${name}: page scrolls horizontally`);
  await page.close();
}

await browser.close();
console.log(`Screenshots in ${out}/`);
if (errors.length) { console.log('Problems:\n' + errors.map((e) => '  - ' + e).join('\n')); process.exitCode = 1; }
else console.log('No console errors, no horizontal scroll.');
