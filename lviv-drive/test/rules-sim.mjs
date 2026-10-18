// Scripted drives through the schematic map to verify the rules engine.
import assert from 'node:assert/strict';
import { buildSchematicNetwork } from '../js/data/network.js';
import { RoadNetwork } from '../js/sim/network.js';
import { RulesEngine } from '../js/sim/rules.js';
import { Car } from '../js/sim/car.js';
import { headingOf } from '../js/core/math.js';

const net = new RoadNetwork(buildSchematicNetwork());

function session() {
  const log = [], events = [];
  const rules = new RulesEngine(net, { onViolation: (v) => log.push(v.code), onEvent: (e) => events.push(e) });
  const car = new Car();
  car.belt = true; car.engineOn = true; car.handbrake = false; car.lights = 2;
  const env = { needLights: false, night: 0 };
  let t = 0;
  const step = (x, z, h, v, dt = 1 / 30) => {
    car.x = x; car.z = z; car.h = h; car.v = v; car.time += dt; t += dt;
    rules.update({ car, t, dt, env, traffic: null, peds: null, inCity: true, parking: [] });
  };
  // Drive a polyline at constant speed (km/h). opts.at(t) lets a test act mid-drive.
  const drive = (pts, kmh, opts = {}) => {
    const v = kmh / 3.6;
    for (let i = 0; i + 1 < pts.length; i++) {
      const a = pts[i], b = pts[i + 1];
      const L = Math.hypot(b.x - a.x, b.z - a.z);
      const h = headingOf({ x: (b.x - a.x) / L, z: (b.z - a.z) / L });
      for (let d = 0; d < L; d += v / 30) {
        opts.each?.(t, d, i);
        step(a.x + (b.x - a.x) * d / L, a.z + (b.z - a.z) * d / L, h, v);
      }
    }
  };
  const stopFor = (sec) => { for (let k = 0; k < sec * 30; k++) step(car.x, car.z, car.h, 0); };
  return { log, events, rules, car, env, drive, stopFor, get t() { return t; }, set t(x) { t = x; } };
}

// Lane polyline from (edge, dir, sFrom) through the end node into (edge2, dir2).
function route(e1, dir1, sFrom, e2, dir2, sTo) {
  const a = net.edges.get(e1), b = e2 ? net.edges.get(e2) : null;
  const pts = [net.lanePoint(a, dir1, 0, sFrom)];
  const node = net.endNode(a, dir1);
  pts.push(net.lanePoint(a, dir1, 0, net.sFromEnd(a, dir1, node.R)));
  if (b) {
    pts.push({ x: node.x, z: node.z });
    pts.push(net.lanePoint(b, dir2, 0, dir2 > 0 ? node.R : b.L - node.R));
    pts.push(net.lanePoint(b, dir2, 0, sTo));
  }
  return pts;
}

function findTime(stopKeyPred, wanted) {
  for (const e of net.edges.values()) for (const st of e.stops) {
    if (!stopKeyPred(e, st)) continue;
    for (let t = 0; t < 100; t += 0.1) if (net.signalState(st.ctrl, st.phase, t).state === wanted) return t;
  }
  throw new Error('no time');
}

let n = 0;
const test = (name, fn) => { try { fn(); n++; console.log('ok  ', name); } catch (e) { console.error('FAIL', name, e.message); process.exitCode = 1; } };

// Svobody southbound towards the n11 light.
const svobody = (e, st) => e.id === 'n10-n11' && st.dir === 1 && st.node === 'n11';
const approachTime = (s0, kmh, wantStateAtLine) => {
  // start so that we reach the stop line exactly at a time when it shows `wantStateAtLine`.
  const e = net.edges.get('n10-n11');
  const st = e.stops.find((x) => svobody(e, x));
  const travel = (st.s - s0 - 2.2) / (kmh / 3.6);
  const tLine = findTime(svobody, wantStateAtLine) + 1.0;
  return tLine - travel;
};

test('running a red light is critical', () => {
  const S = session();
  S.t = approachTime(40, 40, 'red');
  S.drive(route('n10-n11', 1, 40, 'n11-n12', 1, 40), 40);
  assert.ok(S.log.includes('RED_LIGHT'), S.log.join());
});

test('stopping at red then going on green is rewarded', () => {
  const S = session();
  const e = net.edges.get('n10-n11');
  const st = e.stops.find((x) => svobody(e, x));
  S.t = findTime(svobody, 'red') + 0.5;
  S.drive([net.lanePoint(e, 1, 0, st.s - 60), net.lanePoint(e, 1, 0, st.s - 4)], 30);
  // wait until green
  while (net.signalState(st.ctrl, st.phase, S.t).state !== 'green') S.stopFor(0.5);
  S.drive(route('n10-n11', 1, st.s - 4, 'n11-n12', 1, 40), 25);
  assert.ok(S.events.includes('stopped_red'), S.events.join());
  assert.ok(S.events.includes('green_go'), S.events.join());
  assert.ok(!S.log.includes('RED_LIGHT'), S.log.join());
});

test('turning without indicator is reported; with indicator is rewarded', () => {
  const t0 = findTime(svobody, 'green');
  const S = session();
  S.t = t0;
  S.drive(route('n10-n11', 1, 120, 'n11-n21', 1, 40), 20); // left turn into Вірменська
  assert.ok(S.log.includes('NO_SIGNAL'), S.log.join());
  const S2 = session();
  S2.t = t0;
  S2.car.toggleIndicator('left');
  S2.drive(route('n10-n11', 1, 120, 'n11-n21', 1, 40), 20);
  assert.ok(S2.events.includes('signal_ok'), `${S2.log} / ${S2.events}`);
  assert.ok(!S2.log.includes('NO_SIGNAL'));
});

test('indicator left on while going straight through a junction', () => {
  const S = session();
  S.t = findTime(svobody, 'green');
  S.car.toggleIndicator('left');
  S.drive(route('n10-n11', 1, 60, 'n11-n12', 1, 40), 30);
  assert.ok(S.log.includes('SIGNAL_NOT_CANCELLED'), S.log.join());
  assert.ok(!S.log.includes('NO_SIGNAL'));
});

test('entering a one-way street against the flow', () => {
  const S = session();
  S.drive(route('n20-n21', 1, 20, 'n11-n21', -1, 60), 15);
  assert.ok(S.log.includes('WRONG_WAY'), S.log.join());
});

test('STOP sign: rolling through vs full stop', () => {
  const e = net.edges.get('n01-n11');
  const st = e.stops.find((x) => x.node === 'n01');
  assert.equal(st.control, 'stop');
  const S = session();
  S.drive(route('n01-n11', -1, 120, 'n01-n02', 1, 40), 20);
  assert.ok(S.log.includes('STOP_SIGN'), S.log.join());
  const S2 = session();
  S2.car.toggleIndicator('left');
  S2.drive([net.lanePoint(e, -1, 0, 120), net.lanePoint(e, -1, 0, st.s + 2.5)], 20);
  S2.stopFor(2);
  S2.drive(route('n01-n11', -1, st.s + 2.5, 'n01-n02', 1, 40), 15);
  assert.ok(S2.events.includes('stop_ok'), `${S2.log} / ${S2.events}`);
  assert.ok(!S2.log.includes('STOP_SIGN'));
});

test('speeding, seatbelt and lights', () => {
  const S = session();
  S.car.belt = false;
  S.env.needLights = true;
  S.car.lights = 0;
  const e = net.edges.get('n03-n13');
  S.drive([net.lanePoint(e, 1, 0, 20), net.lanePoint(e, 1, 0, 150)], 75);
  assert.ok(S.log.includes('SPEEDING_40'), S.log.join());
  assert.ok(S.log.includes('NO_SEATBELT'));
  assert.ok(S.log.includes('NO_LIGHTS'));
});

test('residential zone 20 km/h and opposite lane', () => {
  const S = session();
  const e = net.edges.get('n20-n21');
  S.drive([net.lanePoint(e, 1, 0, 15), net.lanePoint(e, 1, 0, e.L - 15)], 35);
  assert.ok(S.log.includes('SPEEDING_20'), S.log.join());
  const S2 = session();
  const h = net.edges.get('n03-n13');
  S2.drive([net.point(h, 20, -1.6), net.point(h, 120, -1.6)], 40); // left of centre line
  assert.ok(S2.log.includes('OPPOSITE_LANE'), S2.log.join());
});

test('clean drive along a main road produces no violations', () => {
  const S = session();
  const e = net.edges.get('n03-n13');
  S.drive([net.lanePoint(e, 1, 0, 20), net.lanePoint(e, 1, 0, 140)], 45);
  assert.deepEqual(S.log, []);
});

console.log(`\n${n} rule scenarios passed${process.exitCode ? ', some FAILED' : ''}`);
