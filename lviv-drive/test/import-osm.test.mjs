// Importer test on a synthetic Overpass export (no network needed).
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { convertOverpass } from '../tools/import-osm.mjs';
import { RoadNetwork } from '../js/sim/network.js';
import { Traffic } from '../js/sim/traffic.js';
import { generateRoute } from '../js/game/modes.js';

export function syntheticOverpass() {
  const els = [];
  let nextId = 1;
  const node = (lat, lon, tags) => { const id = nextId++; els.push({ type: 'node', id, lat, lon, ...(tags ? { tags } : {}) }); return id; };
  const LAT = 49.84, LON = 24.03, DLAT = 0.0027, DLON = 0.0042; // ~300 m grid
  const J = [];
  for (let r = 0; r < 3; r++) { J.push([]); for (let c = 0; c < 3; c++) J[r].push(node(LAT - r * DLAT, LON + c * DLON, r === 0 && c === 0 ? { highway: 'traffic_signals' } : undefined)); }
  const way = (nodes, tags) => els.push({ type: 'way', id: nextId++, nodes, tags });
  const mid = (a, b, f, tags) => { const A = els.find((e) => e.id === a), B = els.find((e) => e.id === b); return node(A.lat + (B.lat - A.lat) * f, A.lon + (B.lon - A.lon) * f, tags); };
  // Row 0: primary with a zebra crossing and a bend vertex.
  const zebra = mid(J[0][0], J[0][1], 0.5, { highway: 'crossing', crossing: 'uncontrolled' });
  const bend = mid(J[0][1], J[0][2], 0.5);
  els.find((e) => e.id === bend).lat += 0.0002;
  const west = node(LAT, LON - DLON);
  way([west, J[0][0], zebra, J[0][1], bend, J[0][2]], { highway: 'primary', name: 'вул. Городоцька', lanes: '4', maxspeed: '50' });
  way([J[1][0], J[1][1], J[1][2]], { highway: 'residential', name: 'вул. Тестова', oneway: 'yes' });
  way([J[2][0], J[2][1], J[2][2]], { highway: 'living_street', name: 'вул. Житлова', surface: 'sett' });
  const sig = mid(J[0][0], J[1][0], 0.5, { highway: 'crossing', crossing: 'traffic_signals' });
  way([J[0][0], sig, J[1][0], J[2][0]], { highway: 'secondary', name: 'вул. Шевченка' });
  const stop = mid(J[1][1], J[0][1], 0.97, { highway: 'stop' }); // ~8 m before the primary road
  way([J[2][1], J[1][1], stop, J[0][1]], { highway: 'residential', name: 'вул. Руська' });
  way([J[0][2], J[1][2]], { highway: 'residential', name: 'вул. Друга' });
  way([J[1][2], J[2][2]], { highway: 'pedestrian', name: 'пл. Ринок' });
  return { elements: els, J };
}

const { elements, J } = syntheticOverpass();
const data = convertOverpass({ elements });
const net = new RoadNetwork(data);
const n = (r, c) => net.nodes.get(`o${J[r][c]}`);

assert.equal(n(0, 0).control.kind, 'light', 'signals at junction');
assert.deepEqual(n(0, 1).control, { kind: 'priority', main: 'вул. Городоцька', minor: 'stop' }, 'STOP on approach');
assert.deepEqual(n(1, 0).control, { kind: 'priority', main: 'вул. Шевченка', minor: 'giveway' }, 'secondary/residential ranks differ -> priority');
assert.equal(n(1, 1).control.kind, 'uncontrolled', 'equal residential roads');
assert.equal(data.crosswalks.length, 2);
assert.ok(data.crosswalks.some((c) => c.signal) && data.crosswalks.some((c) => !c.signal));
const oneway = [...net.edges.values()].find((e) => e.name === 'вул. Тестова');
assert.equal(oneway.oneway, 'ab');
const living = [...net.edges.values()].find((e) => e.name === 'вул. Житлова');
assert.equal(living.zone, 'residential'); assert.equal(living.limit, 20); assert.equal(living.surface, 'cobble');
assert.ok([...net.edges.values()].some((e) => e.zone === 'pedestrian'));
assert.equal([...net.edges.values()].find((e) => e.name === 'вул. Городоцька').lanes, 2);
// Simulation runs on the imported network.
const traffic = new Traffic(net, new THREE.Scene(), 8);
for (let t = 0; t < 60; t += 1 / 30) traffic.update(1 / 30, t, { player: null, peds: null });
assert.ok(traffic.cars.every((c) => Number.isFinite(c.x)));
const route = generateRoute(net, { id: 'X', title: 'Test', weather: 'clear', time: 12 });
assert.ok(route && !net.validatePath(route.path), 'route generated on imported map');
console.log('ok   OSM importer: signals, STOP, crossings, one-way, living street, pedestrian zone, traffic, routes');
