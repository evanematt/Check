#!/usr/bin/env node
// Render a 3D scene (HTML + Three.js) to MP4, frame by frame, deterministically.
//
//   node render.mjs <scene.html> [options]
//
// Options
//   --out <file.mp4>        Output path (default: <scene>.mp4 next to the scene)
//   --size <WxH>            Frame size, e.g. 1920x1080, 1080x1920, 1080x1080 (default: scene's own)
//   --fps <n>               Frames per second (default: scene's own, else 30)
//   --duration <sec>        Override duration
//   --from <sec> --to <sec> Render only part of the timeline
//   --stills <t1,t2,...>    Write PNG stills at these times (seconds) instead of a video
//   --audio <file>          Mux an audio track (trimmed to video length, faded out)
//   --crf <n>               x264 quality (default 18, lower = better)
//   --query <a=1&b=2>       Extra URL params for the scene (debug cameras, variants)
//   --serve                 Just serve the scene and print a URL for live preview
//
// Scene contract (see lib/director.js -> mountScene):
//   window.SCENE = { width, height, fps, duration, ready: Promise, seek(t) }
//   seek(t) must draw the frame for time t (seconds) synchronously or return a Promise.
//
// The scene directory is served at "/", the skill directory at "/__skill/".
// Remote requests (CDN scripts, Google Fonts) are fetched with curl (proxy-aware)
// and cached in ~/.cache/claude-3d-video so re-renders are offline and fast. If a jsDelivr/unpkg
// URL is blocked, the same package version is pulled from the npm registry instead.

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { spawn, execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const SKILL_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const CACHE_DIR = path.join(os.homedir(), '.cache', 'claude-3d-video');

// ---------- args ----------
const argv = process.argv.slice(2);
if (!argv.length || argv.includes('-h') || argv.includes('--help')) {
  console.log(fs.readFileSync(fileURLToPath(import.meta.url), 'utf8').split('\n').slice(1, 22).map(l => l.replace(/^\/\/ ?/, '')).join('\n'));
  process.exit(0);
}
const opts = { _: [] };
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  if (a.startsWith('--')) {
    const k = a.slice(2);
    if (k === 'serve') opts.serve = true;
    else opts[k] = argv[++i];
  } else opts._.push(a);
}
const scenePath = path.resolve(opts._[0]);
if (!fs.existsSync(scenePath)) { console.error(`Scene not found: ${scenePath}`); process.exit(1); }
const sceneDir = path.dirname(scenePath);
const sceneFile = path.basename(scenePath);

// ---------- static server ----------
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.json': 'application/json',
  '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp',
  '.svg': 'image/svg+xml', '.glb': 'model/gltf-binary', '.gltf': 'model/gltf+json', '.hdr': 'application/octet-stream',
  '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.mp3': 'audio/mpeg', '.mp4': 'video/mp4' };

function serveFile(res, file) {
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('not found'); return; }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(data);
  });
}
const server = http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  let root = sceneDir, rel = url;
  if (url.startsWith('/__skill/')) { root = SKILL_DIR; rel = url.slice('/__skill'.length); }
  const file = path.normalize(path.join(root, rel === '/' ? sceneFile : rel));
  if (!file.startsWith(root)) { res.writeHead(403); res.end(); return; }
  serveFile(res, file);
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const port = server.address().port;
const qs = new URLSearchParams();
if (opts.size) { const [w, h] = opts.size.split('x').map(Number); qs.set('w', w); qs.set('h', h); }
if (opts.query) for (const [k, v] of new URLSearchParams(opts.query)) qs.set(k, v);
const pageUrl = `http://127.0.0.1:${port}/${sceneFile}${qs.toString() ? '?' + qs : ''}`;

if (opts.serve) {
  console.log(`Serving ${scenePath}\n  ${pageUrl}\n  (add ?t=3.5 to freeze at a time, ?play=1 to loop in real time)\nCtrl+C to stop.`);
  await new Promise(() => {});
}

// ---------- remote asset cache ----------
fs.mkdirSync(CACHE_DIR, { recursive: true });
function fetchCached(url) {
  const key = crypto.createHash('sha1').update(url).digest('hex');
  const body = path.join(CACHE_DIR, key), meta = body + '.type';
  if (!fs.existsSync(body)) {
    const hdr = body + '.hdr';
    execFileSync('curl', ['-sSL', '--fail', '--compressed', '-A', 'Mozilla/5.0 (X11; Linux x86_64) Chrome/124 Safari/537.36',
      '-D', hdr, '-o', body + '.tmp', url], { stdio: ['ignore', 'ignore', 'pipe'] });
    const types = fs.readFileSync(hdr, 'utf8').match(/^content-type:\s*(.+)$/gim) || [];
    fs.writeFileSync(meta, (types.pop() || 'content-type: application/octet-stream').split(':').slice(1).join(':').trim());
    fs.renameSync(body + '.tmp', body); fs.rmSync(hdr, { force: true });
  }
  return { body: fs.readFileSync(body), contentType: fs.readFileSync(meta, 'utf8') };
}

// Fallback for npm CDNs (jsDelivr / unpkg) that a sandbox or proxy blocks: fetch the same
// package version from the npm registry with `npm pack` and serve the file from the tarball.
function fetchFromNpm(url) {
  const m = url.split('?')[0].match(/^https?:\/\/(?:cdn\.jsdelivr\.net\/npm|unpkg\.com)\/((?:@[^/]+\/)?[^/@]+)@([^/]+)\/(.+)$/);
  if (!m) return null;
  const [, name, ver, file] = m;
  const dir = path.join(CACHE_DIR, 'npm', `${name.replace('/', '__')}@${ver}`);
  if (!fs.existsSync(path.join(dir, 'package.json'))) {
    fs.mkdirSync(dir, { recursive: true });
    const tgz = execFileSync('npm', ['pack', `${name}@${ver}`, '--silent', '--pack-destination', dir], { stdio: ['ignore', 'pipe', 'pipe'] })
      .toString().trim().split('\n').pop();
    execFileSync('tar', ['-xzf', path.join(dir, tgz), '-C', dir, '--strip-components=1']);
    fs.rmSync(path.join(dir, tgz), { force: true });
  }
  const f = path.normalize(path.join(dir, file));
  if (!f.startsWith(dir) || !fs.existsSync(f)) return null;
  return { body: fs.readFileSync(f), contentType: MIME[path.extname(f).toLowerCase()] || 'application/octet-stream' };
}

// ---------- browser ----------
let chromium;
try { ({ chromium } = require('playwright')); }
catch { try { ({ chromium } = require(path.join(execFileSync('npm', ['root', '-g']).toString().trim(), 'playwright'))); }
  catch { console.error('Playwright not found. Install with: npm i -g playwright && npx playwright install chromium'); process.exit(1); } }

const launchOpts = { args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--enable-webgl', '--hide-scrollbars'] };
if (process.env.PW_EXECUTABLE) launchOpts.executablePath = process.env.PW_EXECUTABLE;
const browser = await chromium.launch(launchOpts);
const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
await ctx.route(/^https?:\/\/(?!127\.0\.0\.1)/, async route => {
  const url = route.request().url();
  let res = null, err;
  try { res = fetchCached(url); } catch (e) { err = e; }
  if (!res) { try { res = fetchFromNpm(url); } catch (e) { err = e; } }
  if (res) await route.fulfill({ status: 200, body: res.body, headers: { 'content-type': res.contentType, 'access-control-allow-origin': '*' } });
  else { console.warn(`! failed to fetch ${url}: ${(err ? err.message : 'not found').split('\n')[0]}`); await route.abort(); }
});
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', e => errors.push(e.message));
page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });

await page.goto(pageUrl);
try {
  await page.waitForFunction(() => window.SCENE && window.SCENE.ready, null, { timeout: 60000 });
  await page.evaluate(() => window.SCENE.ready);
} catch (e) {
  console.error('Scene failed to initialise (window.SCENE missing).', errors.join('\n'));
  await browser.close(); server.close(); process.exit(1);
}
const info = await page.evaluate(() => ({ w: SCENE.width, h: SCENE.height, fps: SCENE.fps, d: SCENE.duration }));
await page.setViewportSize({ width: info.w, height: info.h });
await page.evaluate(() => window.SCENE.resize && window.SCENE.resize());
const fps = Number(opts.fps || info.fps || 30);
const duration = Number(opts.duration || info.d);
const t0 = Number(opts.from || 0), t1 = Math.min(Number(opts.to || duration), duration);

async function frameAt(t, type = 'png') {
  await page.evaluate(t => window.SCENE.seek(t), t);
  return page.screenshot({ type, ...(type === 'jpeg' ? { quality: 95 } : {}), animations: 'disabled', caret: 'hide' });
}

// ---------- stills ----------
if (opts.stills) {
  const base = opts.out ? opts.out.replace(/\.(mp4|png)$/i, '') : scenePath.replace(/\.html?$/i, '');
  for (const s of opts.stills.split(',').map(Number)) {
    const f = `${base}-t${s.toFixed(2)}.png`;
    fs.writeFileSync(f, await frameAt(s));
    console.log(`still ${s}s -> ${f}`);
  }
  if (errors.length) console.warn('Page errors:\n  ' + errors.join('\n  '));
  await browser.close(); server.close(); process.exit(0);
}

// ---------- video ----------
const out = path.resolve(opts.out || scenePath.replace(/\.html?$/i, '.mp4'));
const total = Math.max(1, Math.round((t1 - t0) * fps));
const ffArgs = ['-y', '-hide_banner', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-c:v', 'mjpeg', '-i', '-'];
if (opts.audio) ffArgs.push('-i', path.resolve(opts.audio));
ffArgs.push('-c:v', 'libx264', '-preset', 'medium', '-crf', String(opts.crf || 18), '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
  '-vf', `scale=trunc(iw/2)*2:trunc(ih/2)*2`);
if (opts.audio) {
  const len = total / fps;
  ffArgs.push('-c:a', 'aac', '-b:a', '192k', '-af', `afade=t=out:st=${Math.max(0, len - 1.5)}:d=1.5`, '-t', String(len), '-shortest');
}
ffArgs.push(out);
const ff = spawn('ffmpeg', ffArgs, { stdio: ['pipe', 'inherit', 'inherit'] });
const ffDone = new Promise((res, rej) => ff.on('close', c => c === 0 ? res() : rej(new Error('ffmpeg exited ' + c))));

const started = Date.now();
for (let i = 0; i < total; i++) {
  const buf = await frameAt(t0 + i / fps, 'jpeg');
  if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
  if (i % fps === 0 || i === total - 1) {
    const el = (Date.now() - started) / 1000, eta = el / (i + 1) * (total - i - 1);
    process.stdout.write(`\rframe ${i + 1}/${total}  ${(100 * (i + 1) / total).toFixed(0)}%  eta ${eta.toFixed(0)}s   `);
  }
}
ff.stdin.end();
await ffDone;
await browser.close(); server.close();
console.log(`\n✔ ${out}  (${info.w}x${info.h}, ${fps}fps, ${(total / fps).toFixed(2)}s, rendered in ${((Date.now() - started) / 1000).toFixed(0)}s)`);
if (errors.length) console.warn('Page errors:\n  ' + errors.join('\n  '));
