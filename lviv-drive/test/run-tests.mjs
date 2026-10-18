// Headless checks for the simulation core: network, routes, rules, AI.
import assert from 'node:assert/strict';
import { buildSchematicNetwork } from '../js/data/network.js';
import { RoadNetwork } from '../js/sim/network.js';
import { EXAM_ROUTES } from '../js/data/exams.js';
import { QUESTIONS, INSPECTION } from '../js/data/questions.js';
import { QUESTS } from '../js/data/quests.js';
import { VIOLATIONS, SEVERITY, gradePractical, to12 } from '../js/data/rules.js';

let passed = 0;
const test = async (name, fn) => {
  try { await fn(); passed++; console.log('ok  ', name); }
  catch (e) { console.error('FAIL', name, '\n', e); process.exitCode = 1; }
};

const net = new RoadNetwork(buildSchematicNetwork());

await test('network builds with junction radii and stops', () => {
  assert.equal(net.nodes.size, 20);
  assert.ok(net.edges.size >= 30);
  for (const n of net.nodes.values()) assert.ok(n.R > 3, n.id);
  const lights = [...net.edges.values()].flatMap((e) => e.stops).filter((s) => s.control === 'light');
  assert.ok(lights.length >= 12);
});

await test('exam routes are drivable (one-way, no U-turns)', () => {
  for (const r of EXAM_ROUTES) assert.equal(net.validatePath(r.path), null, r.id);
});

await test('signal phases never show green to crossing arms at once', () => {
  for (const ctrl of net.controllers.filter((c) => c.type === 'node')) {
    for (let t = 0; t < 88; t += 0.25) {
      const a = net.signalState(ctrl, 0, t).state, b = net.signalState(ctrl, 1, t).state;
      const go = (s) => s === 'green' || s === 'flash' || s === 'yellow';
      assert.ok(!(go(a) && go(b)), `${ctrl.id} t=${t} ${a}/${b}`);
    }
  }
});

await test('pedestrian green only while vehicles see red', () => {
  for (const cw of net.crosswalks.filter((c) => c.ctrl)) {
    for (let t = 0; t < 90; t += 0.5) {
      if (net.pedestrianGo(cw, t).go) assert.equal(net.signalState(cw.ctrl, cw.phase, t).state, 'red');
    }
  }
});

await test('priority: minor road yields to main road, right-hand rule on equal junction', () => {
  const minor = net.edges.get('n01-n11'); // STOP towards n01
  const arms = net.priorityArms('n01', minor, 'straight');
  assert.deepEqual(arms.map((a) => a.edge.id).sort(), ['n00-n01', 'n01-n02']);
  // n21: coming from north (n20-n21, southbound), the eastbound one-way from the west is on our right.
  const fromNorth = net.edges.get('n20-n21');
  assert.deepEqual(net.priorityArms('n21', fromNorth, 'left').map((a) => a.edge.id), ['n11-n21']);
  // The eastbound car has nobody on its right (north arm is to its left).
  assert.deepEqual(net.priorityArms('n21', net.edges.get('n11-n21'), 'straight').map((a) => a.edge.id), []);
});

await test('locate: lane side, sidewalk and junction detection', () => {
  const e = net.edges.get('n11-n12'); // Svobody, southbound a->b
  const p = net.lanePoint(e, 1, 0, e.L / 2);
  const loc = net.locate(p.x, p.z);
  assert.equal(loc.edge.id, e.id);
  assert.ok(loc.onRoad);
  assert.ok(loc.lat * 1 > 0, 'right of travel');
  const side = net.point(e, e.L / 2, e.hwR + 2);
  assert.equal(net.locate(side.x, side.z).surface, 'sidewalk');
  const n = net.nodes.get('n11');
  assert.equal(net.locate(n.x, n.z).node.id, 'n11');
});

await test('shortest path respects one-way streets', () => {
  const p = net.shortestPath('n31', 'n21');
  assert.ok(p && p[0] === 'n31' && p.at(-1) === 'n21');
  assert.equal(net.validatePath(p), null);
  assert.notDeepEqual(p, ['n31', 'n21']); // Вірменська is one-way eastbound
});

await test('data: questions, quests, violations, grading', () => {
  assert.ok(QUESTIONS.length >= 40);
  for (const q of QUESTIONS) assert.ok(q.answer >= 0 && q.answer < q.options.length && q.ref, q.q);
  for (const v of Object.values(VIOLATIONS)) assert.ok(SEVERITY[v.severity] && v.ref && v.tip);
  assert.ok(QUESTS.length >= 6 && INSPECTION.length >= 10);
  assert.equal(gradePractical([]).passed, true);
  assert.equal(gradePractical([{ severity: 'critical' }]).passed, false);
  assert.equal(gradePractical(Array(3).fill({ severity: 'major' })).passed, false);
  assert.equal(to12(100), 12);
  assert.equal(to12(0), 1);
});

const { runTrafficSoak } = await import('./traffic-sim.mjs');
await test('AI traffic soak: flows, obeys signals, never hits pedestrians', () => {
  const r = runTrafficSoak({ seconds: 240 });
  assert.ok(r.avgDist > 400 && r.minDist > 60, JSON.stringify(r));
  assert.equal(r.redRuns, 0);
  assert.equal(r.pedHits, 0);
  assert.ok(r.maxOverlapT < 3);
});

const { driveRoute } = await import('./autopilot.mjs');
for (const r of EXAM_ROUTES) {
  await test(`model driver completes exam route ${r.id} with zero violations`, () => {
    const res = driveRoute(r);
    assert.ok(res.finished, 'finished');
    assert.deepEqual(res.violations.map((v) => v.code), []);
    assert.equal(res.contacts, 0);
    assert.equal(res.pedContacts, 0);
  });
}

console.log(`\n${passed} passed${process.exitCode ? ', some FAILED' : ''}`);

