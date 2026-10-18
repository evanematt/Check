// Soak test for AI traffic + pedestrians: runs 6 simulated minutes headless.
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { buildSchematicNetwork } from '../js/data/network.js';
import { RoadNetwork } from '../js/sim/network.js';
import { Traffic } from '../js/sim/traffic.js';
import { Pedestrians } from '../js/sim/peds.js';

export function runTrafficSoak({ seconds = 360, cars = 22 } = {}) {
  const net = new RoadNetwork(buildSchematicNetwork());
  const scene = new THREE.Scene();
  const traffic = new Traffic(net, scene, cars);
  const peds = new Pedestrians(net, scene, 20);
  const player = { x: -165, z: -160, h: Math.PI, v: 0, mode: 'lane', edge: net.edges.get('n10-n11'), dir: 1, s: 80 };
  const dt = 1 / 30;
  const progress = new Map(traffic.cars.map((c) => [c, 0]));
  let overlapT = 0, maxOverlapT = 0, pedCrossings = 0, prevCross = new Set();
  let redRuns = 0, pedHits = 0, lineCrossings = 0;
  const prevS = new Map();
  for (let t = 0; t < seconds; t += dt) {
    // Move the "player" camera around so pedestrians spawn everywhere.
    const ang = t * 0.05;
    player.x = Math.cos(ang) * 200; player.z = Math.sin(ang) * 150; player.h = -ang;
    peds.update(dt, t, { player: { ...player, mode: 'turn', v: 0 }, traffic });
    traffic.update(dt, t, { player: null, peds });
    for (const c of traffic.cars) {
      const key = c.mode === 'lane' ? `${c.edge.id}|${c.dir}` : null;
      const before = prevS.get(c);
      if (key && before && before.key === key) {
        for (const st of c.edge.stops) {
          if (st.dir !== c.dir || st.control !== 'light') continue;
          const f0 = before.s + c.dir * c.len / 2, f1 = c.s + c.dir * c.len / 2;
          if ((f0 - st.s) * c.dir < 0 && (f1 - st.s) * c.dir >= 0) {
            lineCrossings++;
            const sig = net.signalState(st.ctrl, st.phase, t).state;
            if (sig === 'red' || sig === 'redyellow') redRuns++;
          }
        }
      }
      prevS.set(c, key ? { key, s: c.s } : null);
      for (const p of peds.list) {
        if (p.state !== 'cross' || c.v < 0.2) continue;
        const fx = -Math.sin(c.h), fz = -Math.cos(c.h), rx = p.x - c.x, rz = p.z - c.z;
        const along = rx * fx + rz * fz, lat = rx * -fz + rz * fx;
        if (Math.abs(along) < c.len / 2 + 0.3 && Math.abs(lat) < c.wid / 2 + 0.3) pedHits++;
      }
      assert.ok(Number.isFinite(c.x) && Number.isFinite(c.z) && Number.isFinite(c.v), 'NaN in car state');
      progress.set(c, (progress.get(c) || 0) + c.v * dt);
    }
    let overlapping = false;
    for (let i = 0; i < traffic.cars.length; i++) for (let j = i + 1; j < traffic.cars.length; j++) {
      const a = traffic.cars[i], b = traffic.cars[j];
      if (a.mode === 'lane' && b.mode === 'lane' && Math.hypot(a.x - b.x, a.z - b.z) < 2.0) overlapping = true;
    }
    overlapT = overlapping ? overlapT + dt : 0;
    maxOverlapT = Math.max(maxOverlapT, overlapT);
    for (const p of peds.list) if (p.state === 'cross' && !prevCross.has(p)) { prevCross.add(p); pedCrossings++; }
  }
  const dists = [...progress.values()];
  return { redRuns, lineCrossings, pedHits, minDist: Math.min(...dists), avgDist: dists.reduce((a, b) => a + b, 0) / dists.length, maxOverlapT, pedCrossings };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const r = runTrafficSoak();
  console.log(r);
  assert.ok(r.avgDist > 600, 'traffic flows');
  assert.ok(r.minDist > 100, 'no car stuck for the whole run');
  assert.ok(r.maxOverlapT < 3, 'no persistent overlaps on lanes');
  assert.ok(r.pedCrossings > 10, 'pedestrians cross');
  assert.ok(r.lineCrossings > 50, 'cars pass signals');
  assert.equal(r.redRuns, 0, 'AI never runs a red light');
  assert.equal(r.pedHits, 0, 'AI never drives through crossing pedestrians');
  console.log('traffic soak ok');
}
