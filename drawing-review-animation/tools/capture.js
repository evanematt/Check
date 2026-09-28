// Deterministic frame capture: node capture.js <outDir> <fromFrame> <toFrame|end> [fps]
const { chromium } = require(require('child_process').execSync('npm root -g').toString().trim() + '/playwright');
const fs = require('fs');
const [outDir, fromS, toS, fpsS] = process.argv.slice(2);
const FPS = +(fpsS || 30), DT = 1000 / FPS, from = +fromS, to = toS === 'end' ? Infinity : +toS;
const init = () => {
  window.__CAPTURE__ = true; window.__vnow = 0;
  let seed = 20260928;
  Math.random = () => { seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0; return seed / 4294967296; };
  const timers = new Map(); let tid = 1;
  window.setTimeout = (fn, ms = 0, ...a) => { const id = tid++; timers.set(id, { at: window.__vnow + ms, fn: () => fn(...a) }); return id; };
  window.clearTimeout = (id) => timers.delete(id);
  const runTimers = () => { for (const [id, t] of [...timers]) if (t.at <= window.__vnow) { timers.delete(id); t.fn(); } };
  const raf = () => new Promise((r) => requestAnimationFrame(r));
  window.__step = async (dt) => {
    window.__vnow += dt;
    runTimers();
    await raf(); await raf();
    runTimers();
    for (const a of document.getAnimations()) {
      if (a.playState === 'finished') continue;
      if (!a.__v) { a.__v = true; a.pause(); a.currentTime = 0; continue; }
      if (a.playState !== 'paused') a.pause();
      a.currentTime += dt;
      const end = a.effect && a.effect.getComputedTiming().endTime;
      if (end !== Infinity && a.currentTime >= end) a.finish();
    }
  };
  document.addEventListener('DOMContentLoaded', () => document.documentElement.classList.add('capture'));
};
(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 2, ignoreHTTPSErrors: true });
  const p = await ctx.newPage();
  p.on('pageerror', (e) => console.log('PAGEERR', e.message));
  // serve Google Fonts from a local cache so every worker renders identical type
  const cacheDir = `${__dirname}/.font-cache`; fs.mkdirSync(cacheDir, { recursive: true });
  await p.route(/fonts\.(googleapis|gstatic)\.com/, async (route) => {
    const key = `${cacheDir}/${Buffer.from(route.request().url()).toString('base64url').slice(-120)}`;
    if (!fs.existsSync(key)) {
      let res;
      for (let i = 0; i < 5 && !res; i++) { try { res = await route.fetch(); } catch (e) { await new Promise((r) => setTimeout(r, 1000)); } }
      if (!res) return route.abort();
      fs.writeFileSync(key + '.type', res.headers()['content-type'] || '');
      fs.writeFileSync(key + '.tmp', await res.body()); fs.renameSync(key + '.tmp', key);
    }
    route.fulfill({ body: fs.readFileSync(key), contentType: fs.readFileSync(key + '.type', 'utf8'), headers: { 'access-control-allow-origin': '*' } });
  });
  await p.addInitScript(init);
  await p.goto('http://localhost:8765/', { waitUntil: 'networkidle' });
  // load every face up front: the browser fetches a weight only when first used,
  // which would swap fonts mid-video and differ between parallel workers
  await p.evaluate(async () => {
    const faces = ['400 14px Inter', '500 14px Inter', '600 14px Inter', '400 14px "Geist Mono"', '500 14px "Geist Mono"'];
    const loaded = await Promise.all(faces.map((f) => document.fonts.load(f)));
    await document.fonts.ready;
    if (loaded.some((l) => !l.length)) throw new Error('font failed to load');
  });
  let f = 0, endAt = Infinity; const t0 = Date.now();
  for (; f < to; f++) {
    await p.evaluate((dt) => window.__step(dt), DT);
    if (endAt === Infinity && await p.evaluate(() => !!window.__ended)) endAt = f + Math.round(FPS * 1.0);
    if (f >= endAt) break;
    if (f >= from) await p.screenshot({ path: `${outDir}/${String(f).padStart(6, '0')}.jpg`, type: 'jpeg', quality: 95 });
    if (f % 300 === 0) console.log('frame', f, Math.round((Date.now() - t0) / 1000) + 's');
  }
  console.log('done', f);
  await b.close();
})();
