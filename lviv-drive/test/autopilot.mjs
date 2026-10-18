// A careful "model driver" drives every exam route through live AI traffic
// and pedestrians, using the real car physics and rules engine. A correct
// driver must finish every route with zero violations — this guards against
// false positives in the rules engine and deadlocks in the traffic model.
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { buildSchematicNetwork } from '../js/data/network.js';
import { RoadNetwork } from '../js/sim/network.js';
import { RulesEngine } from '../js/sim/rules.js';
import { Car } from '../js/sim/car.js';
import { Traffic } from '../js/sim/traffic.js';
import { Pedestrians } from '../js/sim/peds.js';
import { carVsVehicle, carVsPoint } from '../js/sim/collide.js';
import { EXAM_ROUTES } from '../js/data/exams.js';
import { headingOf, fwd, clamp, wrapAngle } from '../js/core/math.js';

const net = new RoadNetwork(buildSchematicNetwork());

function buildPath(path, finishFrac) {
  const pts = [];
  for (let i = 0; i + 1 < path.length; i++) {
    const e = net.edgeBetween(path[i], path[i + 1]);
    const dir = e.a === path[i] ? 1 : -1;
    const n0 = net.nodes.get(path[i]), n1 = net.nodes.get(path[i + 1]);
    const last = i + 2 === path.length;
    const from = n0.R, to = last ? e.L * finishFrac : e.L - n1.R;
    for (let d = from; d <= to; d += 2) {
      const s = dir > 0 ? d : e.L - d;
      pts.push({ ...net.lanePoint(e, dir, 0, s), edge: e, dir, node: null, turnAt: null });
    }
    if (last) break;
    // Curve through the junction.
    const e2 = net.edgeBetween(path[i + 1], path[i + 2]);
    const dir2 = e2.a === path[i + 1] ? 1 : -1;
    const p0 = net.lanePoint(e, dir, 0, dir > 0 ? e.L - n1.R : n1.R);
    const p2 = net.lanePoint(e2, dir2, 0, dir2 > 0 ? n1.R : e2.L - n1.R);
    const d0 = net.travelDir(e, dir), d2 = net.travelDir(e2, dir2);
    const den = d0.x * d2.z - d0.z * d2.x;
    let c = { x: (p0.x + p2.x) / 2, z: (p0.z + p2.z) / 2 };
    if (Math.abs(den) > 0.15) {
      const t = ((p2.x - p0.x) * d2.z - (p2.z - p0.z) * d2.x) / den;
      c = { x: p0.x + d0.x * t, z: p0.z + d0.z * t };
    }
    const kind = net.turnKind(d0, d2);
    for (let k = 1; k < 10; k++) {
      const u = k / 10, a = (1 - u) ** 2, b = 2 * u * (1 - u), cc = u * u;
      pts.push({ x: a * p0.x + b * c.x + cc * p2.x, z: a * p0.z + b * c.z + cc * p2.z, node: n1, turn: kind });
    }
  }
  return pts;
}

export function driveRoute(route, { seed = 1, traffic: nCars = 18, log = false } = {}) {
  const scene = new THREE.Scene();
  const traffic = new Traffic(net, scene, nCars);
  const peds = new Pedestrians(net, scene, 16);
  const violations = [], events = [];
  const rules = new RulesEngine(net, { onViolation: (v) => violations.push(v), onEvent: (n) => events.push(n) });
  const car = new Car();
  car.setTransmission('auto');
  const pts = buildPath(route.path, route.finish);
  const start = pts.find((p, i) => i > 3 && p.edge) || pts[0];
  car.reset(start.x, start.z, headingOf(net.travelDir(start.edge, start.dir)));
  car.setTransmission('auto');
  const env = { needLights: route.weather !== 'clear', needLightsReason: '', night: 0 };
  // Pre-drive routine.
  car.belt = true;
  car.lights = 2;
  for (const c of [...traffic.cars]) if (Math.hypot(c.x - car.x, c.z - car.z) < 40) traffic.respawn(c, car);
  let t = 0, idx = 0, stopT = 0, contacts = 0, pedContacts = 0;
  const dt = 1 / 30;
  const input = { throttle: 0, brake: 1, steer: 0, clutch: false };
  car.toggleEngine();
  for (let k = 0; k < 40; k++) { car.update(dt, input, { mu: 1 }); }
  car.setSelector('D');
  car.toggleHandbrake();
  let finished = false;
  let yellowSeen = null, yellowStop = false;
  let waitStop = null;
  while (t < 900 && !finished) {
    t += dt;
    const f = fwd(car.h);
    // Advance along the path.
    while (idx + 1 < pts.length && Math.hypot(pts[idx + 1].x - car.x, pts[idx + 1].z - car.z) < Math.hypot(pts[idx].x - car.x, pts[idx].z - car.z)) idx++;
    const v = Math.abs(car.v);
    const look = 5 + v * 0.5;
    let ti = idx;
    while (ti + 1 < pts.length && Math.hypot(pts[ti].x - car.x, pts[ti].z - car.z) < look) ti++;
    const tp = pts[ti];
    const alpha = wrapAngle(headingOf({ x: tp.x - car.x, z: tp.z - car.z }) - car.h);
    const maxWheel = 0.62 / (1 + (v / 13) ** 2 * 1.2);
    const delta = Math.atan2(2 * 2.62 * Math.sin(-alpha), Math.max(look, 1));
    input.steer = clamp(delta / maxWheel, -1, 1);

    // Target speed.
    const here = pts[idx];
    const edge = here.edge || pts.slice(idx).find((p) => p.edge)?.edge;
    let vT = ((edge ? edge.limit : 20) - 6) / 3.6;
    // Slow down before a lower limit ahead (e.g. entering the residential zone).
    for (let j = idx, d = 0; j < pts.length && d < 60; j++, d += 2) {
      const e2 = pts[j].edge;
      if (e2 && e2.limit < (edge ? edge.limit : 50)) vT = Math.min(vT, Math.sqrt(((e2.limit - 4) / 3.6) ** 2 + 2 * 2 * d));
    }
    let stopDist = Infinity, sigStop = Infinity;
    // Upcoming turn / indicator.
    let dTurn = 0;
    // Cancel the indicator once the turn is done (gentle steering may not self-cancel it).
    if (car.indicator && !here.node && !pts.slice(idx, idx + 30).some((p) => p.node && p.turn === car.indicator)) car.toggleIndicator(car.indicator);
    for (let j = idx; j < pts.length && dTurn < 60; j++) {
      if (pts[j].node && pts[j].turn !== 'straight' && pts[j].node.degree >= 3) {
        if (car.indicator !== pts[j].turn) car.toggleIndicator(pts[j].turn);
        break;
      }
      if (pts[j].node && pts[j].turn !== 'straight') break;
      dTurn += 2;
    }
    for (let j = idx, d = 0; j < pts.length && d < 40; j++, d += 2) if (pts[j].node && pts[j].turn !== 'straight') vT = Math.min(vT, Math.sqrt(16 + 2 * 1.5 * d) * 0.95);
    // Stop lines.
    const loc = net.locate(car.x, car.z);
    if (loc.edge && !loc.node && here.edge) {
      const e = here.edge, dir = here.dir;
      const sF = (car.x + f.x * 2.2 - e.ax) * e.d.x + (car.z + f.z * 2.2 - e.az) * e.d.z;
      const ns = net.nextStop(e, dir, sF);
      if (ns && ns.ahead < 70) {
        const st = ns.stop;
        let must = false;
        if (st.control === 'light') {
          const s = net.signalState(st.ctrl, st.phase, t).state;
          // Decide once when the light turns yellow, like a real driver.
          if (s === 'yellow' && yellowSeen !== st.key) { yellowSeen = st.key; yellowStop = ns.ahead > (v * v) / 8 + 0.5; }
          if (s !== 'yellow' && yellowSeen === st.key) yellowSeen = null;
          must = s === 'red' || s === 'redyellow' || (s === 'yellow' && yellowStop) || (s === 'flash' && ns.ahead > (v * v) / 8 + 3 && ns.ahead < 30);
        } else if (st.control === 'stop') {
          if (waitStop !== st.key) must = true;
          if (ns.ahead < 2.5 && v < 0.05) { stopT += dt; if (stopT > 1.5) { waitStop = st.key; stopT = 0; } }
        }
        if (!must && st.node && ns.ahead < 30) {
          const nextTurn = pts.slice(idx).find((p) => p.node && p.node.id === st.node);
          const intent = nextTurn ? nextTurn.turn : 'straight';
          for (const arm of net.priorityArms(st.node, e, intent)) {
            if (traffic.arrivals(st.node, arm.edge, arm.dir, null, null).some((a) => a.eta < 4.5)) must = true;
          }
        }
        if (must) sigStop = Math.min(sigStop, ns.ahead - 0.8);
      }
    }
    // Keep checking priority up to the junction entry (e.g. waiting to turn left).
    if (here.edge && !loc.node) {
      let dN = 0, turnPt = null;
      for (let j = idx; j < pts.length && dN < 30; j++, dN += 2) if (pts[j].node) { turnPt = pts[j]; break; }
      if (turnPt && turnPt.node.degree >= 3) {
        for (const arm of net.priorityArms(turnPt.node.id, here.edge, turnPt.turn)) {
          if (traffic.arrivals(turnPt.node.id, arm.edge, arm.dir, null, null).some((a) => a.eta < 4.5)) { stopDist = Math.min(stopDist, dN - 2.7); break; }
        }
      }
    }
    // Pedestrians on crosswalks along the path ahead (including a turn's exit arm).
    for (const cw of net.crosswalks) {
      if (cw.peds <= 0) continue;
      const ce = net.edges.get(cw.edge), cc = net.point(ce, cw.s, 0);
      for (let j = idx, d = 0; j < pts.length && d < 45; j++, d += 2) {
        if (Math.hypot(pts[j].x - cc.x, pts[j].z - cc.z) < Math.max(ce.hwL, ce.hwR) + 1) { stopDist = Math.min(stopDist, d - cw.half - 3.2); break; }
      }
    }
    // Leader.
    for (const o of traffic.cars) {
      const rx = o.x - car.x, rz = o.z - car.z;
      const along = rx * f.x + rz * f.z, lat = Math.abs(rx * -f.z + rz * f.x);
      if (along > 0 && along < 35 && lat < 2.2) stopDist = Math.min(stopDist, along - o.len / 2 - 2.25 - 3);
    }
    if (stopDist < Infinity) vT = Math.min(vT, Math.sqrt(Math.max(0, 2 * 2.8 * Math.max(0, stopDist - 0.5))));
    // Signals and signs: a firm but non-emergency stop (~4 m/s²).
    if (sigStop < Infinity) vT = Math.min(vT, Math.sqrt(2 * 4.2 * Math.max(0, sigStop - 0.5)));
    if (sigStop < 0.6) vT = 0;
    if (stopDist < 0.6) vT = 0;
    // Finish.
    const end = pts[pts.length - 1];
    const dEnd = Math.hypot(end.x - car.x, end.z - car.z);
    if (idx > pts.length - 15) vT = Math.min(vT, Math.sqrt(2 * 1.5 * Math.max(0, dEnd - 1)));
    const err = vT - v;
    input.throttle = clamp(err * 0.35, 0, 0.7);
    input.brake = vT < 0.2 ? 0.8 : clamp(-err * 0.45, 0, 1);
    car.update(dt, input, { mu: 1 });
    if (dEnd < 6 && Math.abs(car.v) < 0.05) { car.toggleHandbrake(); finished = true; }

    const proxy = rulesProxy(car, rules);
    traffic.update(dt, t, { player: proxy, peds });
    peds.update(dt, t, { player: proxy, traffic });
    rules.update({ car, t, dt, env, traffic, peds, inCity: true, parking: [] });
    for (const o of traffic.cars) {
      if (Math.abs(o.x - car.x) < 8 && Math.abs(o.z - car.z) < 8 && carVsVehicle(car, o)) { contacts++; break; }
    }
    for (const p of peds.list) if (p.state !== 'down' && Math.abs(car.v) > 0.4 && carVsPoint(car, p.x, p.z, 0.3)) { pedContacts++; peds.knockDown(p); }
  }
  if (log) console.log(route.id, { t: t.toFixed(0), finished, violations: violations.map((v) => `${v.code}@${v.t.toFixed(0)}`), events: [...new Set(events)].join(','), contacts, pedContacts });
  return { finished, t, violations, events, contacts, pedContacts };
}

function rulesProxy(car, rules) {
  const loc = rules.loc;
  const f = fwd(car.h);
  const p = { x: car.x, z: car.z, h: car.h, v: Math.abs(car.v), len: 4.5, wid: 1.8, mode: 'lane', edge: null, dir: 1, s: 0 };
  if (loc && loc.node) { p.mode = 'turn'; p.curve = { node: loc.node.id, fromEdge: rules.lastEdge, fromDir: rules.lastDir }; }
  else if (loc && loc.edge) { p.edge = loc.edge; p.dir = f.x * loc.edge.d.x + f.z * loc.edge.d.z >= 0 ? 1 : -1; p.s = loc.s; }
  return p;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  let bad = 0;
  for (const r of EXAM_ROUTES) {
    const res = driveRoute(r, { log: true });
    try {
      assert.ok(res.finished, `${r.id} finished`);
      assert.deepEqual(res.violations.map((v) => v.code), [], `${r.id} clean drive`);
      assert.equal(res.contacts, 0, `${r.id} no contact with AI cars`);
      assert.equal(res.pedContacts, 0, `${r.id} no contact with pedestrians`);
    } catch (e) { bad++; console.error('FAIL', e.message); }
  }
  if (bad) process.exitCode = 1; else console.log('autopilot: all routes clean');
}
