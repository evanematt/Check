// End-to-end browser test (optional): boots the real game in headless
// Chromium and plays it — a training quest, a full exam route driven by an
// autopilot, a parking drill and the theory test — failing on any page error.
//
//   npm i -D playwright && npx playwright install chromium   (once)
//   node test/e2e.mjs [--shots dir]
// Set PLAYWRIGHT_MODULE to a playwright index.mjs path to use a preinstalled copy.
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';

const pw = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const shots = process.argv.includes('--shots') ? process.argv[process.argv.indexOf('--shots') + 1] : null;
const port = 8000 + Math.floor(Math.random() * 900);
const server = spawn(process.execPath, [new URL('../tools/serve.mjs', import.meta.url).pathname], { env: { ...process.env, PORT: String(port) }, stdio: 'ignore' });
await new Promise((r) => setTimeout(r, 600));

const browser = await pw.chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1100, height: 620 } });
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !m.text().includes('404')) errors.push(m.text()); });
const shot = async (name) => { if (shots) await page.screenshot({ path: `${shots}/${name}.png` }); };
let failed = 0;
const step = async (name, fn) => {
  try { await fn(); console.log('ok  ', name); } catch (e) { failed++; console.error('FAIL', name, e.message); }
};

try {
  await page.goto(`http://localhost:${port}/`);
  await page.waitForFunction(() => !document.getElementById('loading'), null, { timeout: 180000 });
  await shot('menu');

  await step('training quest 1 (pre-drive routine, manual gearbox)', async () => {
    await page.click('text=Навчання');
    await page.click('text=1. Підготовка до руху');
    const r = await page.evaluate(() => {
      const g = window.__game;
      const sim = (sec, keys = []) => { for (const k of keys) g.input.keys.add(k); for (let i = 0; i < sec * 30; i++) g.step(1 / 30, false); for (const k of keys) g.input.keys.delete(k); };
      g.action('belt'); g.action('engine'); sim(1.5);
      g.action('lights'); g.action('lights');
      sim(0.7, ['Digit1']); sim(0.7, ['Digit3']); sim(0.5);
      g.action('indLeft'); sim(1.5);
      g.action('handbrake');
      g.input.keys.add('ShiftLeft'); sim(0.3); g.action('gearUp'); g.input.keys.delete('ShiftLeft');
      sim(6, ['KeyW']); sim(3, ['KeyS', 'ShiftLeft']); // clutch down while stopping, or the engine stalls
      return { done: g.mode.done, step: g.mode.step, engine: g.car.engineOn };
    });
    assert.ok(r.done, JSON.stringify(r));
    await page.evaluate(() => window.__game.step(1 / 30, true));
    await shot('quest');
  });

  await step('exam route A driven by autopilot passes', async () => {
    const res = await page.evaluate(async (routeId) => {
  const g = window.__game, net = g.net;
  g.settings.transmission = 'auto'; g.car.setTransmission('auto');
  g.startMode('exam', { route: routeId });
  const ctl = { throttle: 0, brake: 1, steer: 0, clutch: false, horn: false, look: null, mouse: { dx: 0, dy: 0, down: false } };
  g.input.poll = () => ctl;
  const c = g.car;
  const hits = [];
  const orig = g.collisions.resolveCar.bind(g.collisions);
  g.collisions.resolveCar = (car) => { const h = orig(car); if (h && hits.length < 12 && Math.abs(car.v) > 1) { const loc = net.locate(car.x, car.z); hits.push({ type: h.collider.type, r: h.collider.r, hx: h.collider.hx, cx: +(h.collider.x ?? h.collider.cx).toFixed(1), cz: +(h.collider.z ?? h.collider.cz).toFixed(1), car: [+car.x.toFixed(1), +car.z.toFixed(1)], edge: loc.edge?.id, node: loc.node?.id, lat: +loc.lat.toFixed(2), s: +loc.s.toFixed(1) }); } return h; };
  const ctx = [];
  const origRep = g.rules.report.bind(g.rules);
  g.rules.report = (code, extra) => {
    if (code === 'PED_HIT' || code === 'COLLISION_CAR') {
      const f = { x: -Math.sin(c.h), z: -Math.cos(c.h) };
      const near = (x, z) => { const rx = x - c.x, rz = z - c.z; return { along: +(rx * f.x + rz * f.z).toFixed(2), lat: +(rx * -f.z + rz * f.x).toFixed(2) }; };
      const loc = net.locate(c.x, c.z);
      ctx.push({ code, t: +g.t.toFixed(1), kmh: +c.speedKmh.toFixed(1), edge: loc.edge?.id, node: loc.node?.id,
        peds: g.peds.list.filter((p) => Math.hypot(p.x - c.x, p.z - c.z) < 5).map((p) => ({ state: p.state, cw: p.cw.id, cwNode: p.cw.node, ctrl: !!p.cw.ctrl, lat: +p.lat.toFixed(2), latKerb: +p.latKerb.toFixed(2), ...near(p.x, p.z) })),
        cars: g.traffic.cars.filter((o) => Math.hypot(o.x - c.x, o.z - c.z) < 7).map((o) => ({ mode: o.mode, edge: o.edge.id, dir: o.dir, intent: o.intent, v: +o.v.toFixed(1), ...near(o.x, o.z), rel: +(((o.h - c.h) * 180) / Math.PI).toFixed(0) })) });
    }
    return origRep(code, extra);
  };
  g.action('belt'); g.action('lights'); g.action('lights');
  g.action('engine');
  for (let i = 0; i < 40; i++) g.step(1 / 30, false);
  g.action('gearUp'); g.action('gearUp'); g.action('gearUp'); // P->R->N->D
  g.action('handbrake');
  const wrap = (a) => { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; };
  const heading = (d) => Math.atan2(-d.x, -d.z);
  const BUILD = 'function buildPath(path, finishFrac) {\n  const pts = [];\n  for (let i = 0; i + 1 < path.length; i++) {\n    const e = net.edgeBetween(path[i], path[i + 1]);\n    const dir = e.a === path[i] ? 1 : -1;\n    const n0 = net.nodes.get(path[i]), n1 = net.nodes.get(path[i + 1]);\n    const last = i + 2 === path.length;\n    const from = n0.R, to = last ? e.L * finishFrac : e.L - n1.R;\n    for (let d = from; d <= to; d += 2) {\n      const s = dir > 0 ? d : e.L - d;\n      pts.push({ ...net.lanePoint(e, dir, 0, s), edge: e, dir, node: null, turnAt: null });\n    }\n    if (last) break;\n    // Curve through the junction.\n    const e2 = net.edgeBetween(path[i + 1], path[i + 2]);\n    const dir2 = e2.a === path[i + 1] ? 1 : -1;\n    const p0 = net.lanePoint(e, dir, 0, dir > 0 ? e.L - n1.R : n1.R);\n    const p2 = net.lanePoint(e2, dir2, 0, dir2 > 0 ? n1.R : e2.L - n1.R);\n    const d0 = net.travelDir(e, dir), d2 = net.travelDir(e2, dir2);\n    const den = d0.x * d2.z - d0.z * d2.x;\n    let c = { x: (p0.x + p2.x) / 2, z: (p0.z + p2.z) / 2 };\n    if (Math.abs(den) > 0.15) {\n      const t = ((p2.x - p0.x) * d2.z - (p2.z - p0.z) * d2.x) / den;\n      c = { x: p0.x + d0.x * t, z: p0.z + d0.z * t };\n    }\n    const kind = net.turnKind(d0, d2);\n    for (let k = 1; k < 10; k++) {\n      const u = k / 10, a = (1 - u) ** 2, b = 2 * u * (1 - u), cc = u * u;\n      pts.push({ x: a * p0.x + b * c.x + cc * p2.x, z: a * p0.z + b * c.z + cc * p2.z, node: n1, turn: kind });\n    }\n  }\n  return pts;\n}\n\n';
  const buildPath = new Function('net', 'headingOf', BUILD + '; return buildPath;')(net, heading);
  const pts = buildPath(g.mode.path, g.mode.route.finish);
  let t = 0, stopKey = null, stopT = 0, idx = 0;
  let yellowSeen = null, yellowStop = false;
  const notes = [];
  while (t < 900 && !g.paused) {
    t += 1 / 30;
    const m = g.mode;
    const f = { x: -Math.sin(c.h), z: -Math.cos(c.h) };
    const v = Math.abs(c.v);
    while (idx + 1 < pts.length && Math.hypot(pts[idx + 1].x - c.x, pts[idx + 1].z - c.z) < Math.hypot(pts[idx].x - c.x, pts[idx].z - c.z)) idx++;
    const look = 5 + v * 0.5;
    let ti = idx;
    while (ti + 1 < pts.length && Math.hypot(pts[ti].x - c.x, pts[ti].z - c.z) < look) ti++;
    const tp = pts[ti];
    const alpha = wrap(heading({ x: tp.x - c.x, z: tp.z - c.z }) - c.h);
    const maxWheel = 0.62 / (1 + (v / 13) ** 2 * 1.2);
    ctl.steer = Math.max(-1, Math.min(1, Math.atan2(2 * 2.62 * Math.sin(-alpha), look) / maxWheel));
    const here = pts[idx];
    const edge = here.edge || pts.slice(idx).find((p) => p.edge)?.edge;
    let vT = ((edge ? edge.limit : 20) - 6) / 3.6;
    for (let j = idx, d = 0; j < pts.length && d < 60; j++, d += 2) {
      const e2 = pts[j].edge;
      if (e2 && e2.limit < (edge ? edge.limit : 50)) vT = Math.min(vT, Math.sqrt(((e2.limit - 4) / 3.6) ** 2 + 4 * d));
      if (pts[j].node && pts[j].turn !== 'straight') vT = Math.min(vT, Math.sqrt(16 + 3 * d) * 0.95);
      if (pts[j].node && pts[j].turn !== 'straight' && pts[j].node.degree >= 3 && d < 60 && c.indicator !== pts[j].turn) c.toggleIndicator(pts[j].turn);
      if (pts[j].node && pts[j].turn !== 'straight') break;
    }
    let stopDist = Infinity, sigStop = Infinity;
    const loc = net.locate(c.x, c.z);
    if (c.indicator && !here.node && !pts.slice(idx, idx + 30).some((p) => p.node && p.turn === c.indicator)) c.toggleIndicator(c.indicator);
    if (here.edge && !loc.node) {
      let dN = 0, turnPt = null;
      for (let j = idx; j < pts.length && dN < 30; j++, dN += 2) if (pts[j].node) { turnPt = pts[j]; break; }
      if (turnPt && turnPt.node.degree >= 3) {
        for (const arm of net.priorityArms(turnPt.node.id, here.edge, turnPt.turn)) if (g.traffic.arrivals(turnPt.node.id, arm.edge, arm.dir, null, null).some((a) => a.eta < 4.5)) { stopDist = Math.min(stopDist, dN - 2.7); break; }
      }
    }
    if (loc.edge && !loc.node && here.edge) {
      const e = here.edge, dir = here.dir;
      const sF = (c.x + f.x * 2.2 - e.ax) * e.d.x + (c.z + f.z * 2.2 - e.az) * e.d.z;
      const ns = net.nextStop(e, dir, sF);
      if (ns && ns.ahead < 70) {
        const st = ns.stop;
        let must = false;
        if (st.control === 'light') {
          const s = net.signalState(st.ctrl, st.phase, g.t).state;
          // Decide once when the light turns yellow, like a real driver.
          if (s === 'yellow' && yellowSeen !== st.key) { yellowSeen = st.key; yellowStop = ns.ahead > (v * v) / 8 + 0.5; }
          if (s !== 'yellow' && yellowSeen === st.key) yellowSeen = null;
          must = s === 'red' || s === 'redyellow' || (s === 'yellow' && yellowStop) || (s === 'flash' && ns.ahead > (v * v) / 8 + 3 && ns.ahead < 30);
        } else if (st.control === 'stop') {
          if (stopKey !== st.key) must = true;
          if (ns.ahead < 2.5 && v < 0.05) { stopT += 1 / 30; if (stopT > 1.5) { stopKey = st.key; stopT = 0; } }
        }
        if (!must && st.node && ns.ahead < 30) {
          const nt = pts.slice(idx).find((p) => p.node && p.node.id === st.node);
          for (const arm of net.priorityArms(st.node, e, nt ? nt.turn : 'straight')) if (g.traffic.arrivals(st.node, arm.edge, arm.dir, null, null).some((a) => a.eta < 4.5)) must = true;
        }
        if (must) sigStop = Math.min(sigStop, ns.ahead - 0.8);
      }
    }
    for (const cw of net.crosswalks) {
      if (cw.peds <= 0) continue;
      const ce = net.edges.get(cw.edge), cc = net.point(ce, cw.s, 0);
      for (let j = idx, d = 0; j < pts.length && d < 45; j++, d += 2) {
        if (Math.hypot(pts[j].x - cc.x, pts[j].z - cc.z) < Math.max(ce.hwL, ce.hwR) + 1) { stopDist = Math.min(stopDist, d - cw.half - 3.2); break; }
      }
    }
    for (const o of g.traffic.cars) {
      const rx = o.x - c.x, rz = o.z - c.z, along = rx * f.x + rz * f.z, lat = Math.abs(rx * -f.z + rz * f.x);
      if (along > 0 && along < 35 && lat < 2.2) stopDist = Math.min(stopDist, along - o.len / 2 - 5.2);
    }
    if (stopDist < Infinity) vT = Math.min(vT, Math.sqrt(2 * 2.8 * Math.max(0, stopDist - 0.5)));
    // Signals and signs: a firm but non-emergency stop (~4 m/s²).
    if (sigStop < Infinity) vT = Math.min(vT, Math.sqrt(2 * 4.2 * Math.max(0, sigStop - 0.5)));
    if (sigStop < 0.6) vT = 0;
    if (stopDist < 0.6) vT = 0;
    const end = pts[pts.length - 1], dEnd = Math.hypot(end.x - c.x, end.z - c.z);
    if (idx > pts.length - 15) vT = Math.min(vT, Math.sqrt(2 * 1.5 * Math.max(0, dEnd - 1)));
    if (dEnd < 8 && v < 0.05 && !c.handbrake) { g.action('handbrake'); notes.push(`handbrake at ${t.toFixed(0)}`); }
    const err = vT - v;
    ctl.throttle = Math.max(0, Math.min(0.7, err * 0.35));
    ctl.brake = vT < 0.2 ? 0.8 : Math.max(0, Math.min(1, -err * 0.45));
    g.step(1 / 30, false);
  }
  return { ctx, t: Math.round(t), paused: g.paused, idx: g.mode.idx, path: g.mode.path.length, log: g.log.map((v) => v.code), notes,
    result: document.querySelector('#results .result-head')?.innerText.replace(/\s+/g, ' ') };
}, 'A');
    assert.deepEqual(res.log, []);
    assert.match(res.result || '', /Іспит складено/);
    await shot('exam-result');
  });

  await step('parallel parking drill scores a car placed in the bay', async () => {
    const r = await page.evaluate(() => {
      const g = window.__game;
      g.startMode('drill', { drill: 'parallel' });
      for (let i = 0; i < 120; i++) g.step(1 / 30, false);
      const c = g.car;
      Object.assign(c, { x: 950 + 20 + 3.0, z: 25.4, h: 0, v: 0 });
      if (!c.handbrake) g.action('handbrake');
      for (let i = 0; i < 60; i++) g.step(1 / 30, false);
      return g.mode.run.result;
    });
    assert.ok(r && r.passed && r.score >= 90, JSON.stringify(r));
  });

  await step('night cockpit renders with instruments', async () => {
    await page.evaluate(() => { const g = window.__game; g.startMode('free', { quest: 7, hour: 22, weather: 'rain' }); g.action('lights'); g.action('lights'); for (let i = 0; i < 10; i++) g.step(1 / 30, true); });
    await shot('night');
  });

  await step('theory test runs and grades', async () => {
    await page.evaluate(() => window.__game.quitToMenu());
    await page.click('text=Теоретичний іспит');
    for (let i = 0; i < 20; i++) await page.click('.opt >> nth=0');
    await page.click('text=Завершити');
    const txt = await page.textContent('#results');
    assert.match(txt, /Правильних відповідей: \d+ з 20/);
  });

  assert.deepEqual(errors, [], 'no page errors');
} catch (e) {
  failed++;
  console.error('FAIL', e.message);
} finally {
  await browser.close();
  server.kill();
}
if (failed) process.exit(1);
console.log('e2e ok');
