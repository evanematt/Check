#!/usr/bin/env node
// Builds data/lviv-osm.json (the game's road-network schema) from
// OpenStreetMap: real street geometry, names, lanes, one-way streets, speed
// limits, traffic signals, STOP / give-way signs and pedestrian crossings.
//
//   npm run import-osm                       # fetch Lviv centre from Overpass
//   npm run import-osm -- --bbox 49.835,24.015,49.848,24.040
//   npm run import-osm -- --in export.json   # use a saved Overpass JSON
//
// Map data © OpenStreetMap contributors, ODbL 1.0 (https://www.openstreetmap.org/copyright).
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const DRIVABLE = ['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'unclassified', 'residential', 'living_street',
  'motorway_link', 'trunk_link', 'primary_link', 'secondary_link', 'tertiary_link', 'pedestrian'];
const RANK = { motorway: 9, trunk: 8, primary: 7, secondary: 6, tertiary: 5, unclassified: 3, residential: 2, living_street: 1, pedestrian: 0 };
const COBBLE = ['sett', 'cobblestone', 'unhewn_cobblestone', 'paving_stones'];
const DEFAULT_BBOX = [49.8355, 24.017, 49.848, 24.0405]; // s, w, n, e — Lviv old town and around

export function overpassQuery([s, w, n, e]) {
  const bb = `${s},${w},${n},${e}`;
  return `[out:json][timeout:90];
(
  way["highway"~"^(${DRIVABLE.join('|')})$"](${bb});
);
out body;
>;
out body qt;`;
}

function rank(hw) { return RANK[(hw || '').replace('_link', '')] ?? 2; }

function parseLimit(tags) {
  const m = tags.maxspeed;
  if (tags.highway === 'living_street' || m === 'UA:living_street') return 20;
  if (!m || m === 'UA:urban') return 50;
  if (m === 'UA:rural') return 90;
  if (m === 'UA:motorway') return 130;
  const n = parseInt(m, 10);
  return Number.isFinite(n) ? n : 50;
}

// Douglas–Peucker on projected points; returns kept indices.
function simplify(pts, tol) {
  const keep = new Set([0, pts.length - 1]);
  const rec = (i, j) => {
    let best = -1, dmax = 0;
    const [ax, az] = pts[i], [bx, bz] = pts[j];
    const L = Math.hypot(bx - ax, bz - az) || 1;
    for (let k = i + 1; k < j; k++) {
      const d = Math.abs((bx - ax) * (az - pts[k][1]) - (ax - pts[k][0]) * (bz - az)) / L;
      if (d > dmax) { dmax = d; best = k; }
    }
    if (best > 0 && dmax > tol) { keep.add(best); rec(i, best); rec(best, j); }
  };
  if (pts.length > 2) rec(0, pts.length - 1);
  return [...keep].sort((a, b) => a - b);
}

export function convertOverpass(json, { bbox = null, title = 'OpenStreetMap — Львів' } = {}) {
  const osmNodes = new Map();
  const ways = [];
  for (const el of json.elements || []) {
    if (el.type === 'node') osmNodes.set(el.id, el);
    else if (el.type === 'way' && el.tags && DRIVABLE.includes(el.tags.highway)) ways.push(el);
  }
  // Local projection centred on the data.
  let lat0 = 0, lon0 = 0, n0 = 0;
  for (const w of ways) for (const id of w.nodes) { const n = osmNodes.get(id); if (n) { lat0 += n.lat; lon0 += n.lon; n0++; } }
  if (!n0) throw new Error('No drivable ways in the input');
  lat0 /= n0; lon0 /= n0;
  const kx = 111320 * Math.cos((lat0 * Math.PI) / 180), kz = 110540;
  const xy = (n) => [(n.lon - lon0) * kx, -(n.lat - lat0) * kz];

  // Junctions: nodes shared by ways, plus way ends.
  const uses = new Map();
  for (const w of ways) {
    w.nodes.forEach((id, i) => uses.set(id, (uses.get(id) || 0) + (i === 0 || i === w.nodes.length - 1 ? 2 : 1)));
  }
  const isJunction = (id) => (uses.get(id) || 0) >= 2;

  const nodes = new Map(); // id -> {id, x, z, osm}
  const edges = new Map();
  const nodeWays = new Map(); // node id -> [{way, edgeId}]
  const origToEdge = new Map(); // osm node id (non-vertex) -> {edgeId, t}
  const nid = (id) => `o${id}`;
  const addNode = (id) => {
    if (!nodes.has(nid(id))) { const [x, z] = xy(osmNodes.get(id)); nodes.set(nid(id), { id: nid(id), x: +x.toFixed(2), z: +z.toFixed(2), osm: id }); }
    return nid(id);
  };

  for (const w of ways) {
    const t = w.tags;
    const ids = w.nodes.filter((id) => osmNodes.has(id));
    if (ids.length < 2) continue;
    const oneway = t.oneway === 'yes' || t.oneway === '1' || t.junction === 'roundabout' || t.highway === 'motorway' ? 'ab' : t.oneway === '-1' ? 'ba' : null;
    const lanesTotal = parseInt(t.lanes, 10);
    const lanes = Number.isFinite(lanesTotal) ? Math.max(1, oneway ? lanesTotal : Math.round(lanesTotal / 2)) : rank(t.highway) >= 6 ? 2 : 1;
    const attrs = {
      name: t['name:uk'] || t.name || `(${t.highway})`,
      lanes: Math.min(lanes, 3),
      oneway,
      limit: t.highway === 'pedestrian' ? 0 : parseLimit(t),
      zone: t.highway === 'pedestrian' ? 'pedestrian' : t.highway === 'living_street' ? 'residential' : 'city',
      surface: COBBLE.includes(t.surface) ? 'cobble' : 'asphalt',
      parking: null,
      highway: t.highway,
    };
    // Split at junctions, simplify each piece, emit straight edges.
    let start = 0;
    for (let i = 1; i < ids.length; i++) {
      if (!isJunction(ids[i]) && i !== ids.length - 1) continue;
      const seg = ids.slice(start, i + 1);
      const pts = seg.map((id) => xy(osmNodes.get(id)));
      const keep = simplify(pts, 2.5);
      for (let k = 0; k + 1 < keep.length; k++) {
        const a = addNode(seg[keep[k]]), b = addNode(seg[keep[k + 1]]);
        if (a === b) continue;
        const id = `${a}-${b}`, rid = `${b}-${a}`;
        if (edges.has(id) || edges.has(rid)) continue;
        const na = nodes.get(a), nb = nodes.get(b);
        const L = Math.hypot(nb.x - na.x, nb.z - na.z);
        if (L < 1) continue;
        edges.set(id, { id, a, b, ...attrs });
        for (const n of [a, b]) { if (!nodeWays.has(n)) nodeWays.set(n, []); nodeWays.get(n).push({ rank: rank(t.highway), name: attrs.name, edgeId: id }); }
        // Original nodes between kept vertices map onto this edge.
        for (let m = keep[k] + 1; m < keep[k + 1]; m++) {
          const p = pts[m];
          const tt = ((p[0] - na.x) * (nb.x - na.x) + (p[1] - na.z) * (nb.z - na.z)) / (L * L);
          origToEdge.set(seg[m], { edgeId: id, t: Math.min(0.95, Math.max(0.05, tt)) });
        }
      }
      start = i;
    }
  }

  // Signals / signs near junctions (OSM often tags them on the approach node).
  const nearestJunction = (osmId) => {
    const own = nodes.get(nid(osmId));
    if (own && (nodeWays.get(own.id) || []).length >= 3) return own;
    const p = xy(osmNodes.get(osmId));
    let best = null;
    for (const n of nodes.values()) {
      if ((nodeWays.get(n.id) || []).length < 3) continue;
      const d = Math.hypot(n.x - p[0], n.z - p[1]);
      if (d < 25 && (!best || d < best.d)) best = { n, d };
    }
    return best ? best.n : null;
  };
  const signalJ = new Set(), stopJ = new Map();
  const crosswalks = [];
  for (const n of osmNodes.values()) {
    const tg = n.tags || {};
    if (!uses.has(n.id)) continue;
    if (tg.highway === 'traffic_signals') {
      const j = nearestJunction(n.id);
      if (j) signalJ.add(j.id);
      else if (origToEdge.has(n.id)) crosswalks.push({ edge: origToEdge.get(n.id).edgeId, t: +origToEdge.get(n.id).t.toFixed(3), signal: true });
    } else if (tg.highway === 'stop' || tg.highway === 'give_way') {
      const j = nearestJunction(n.id);
      if (j) {
        const kind = tg.highway === 'stop' ? 'stop' : 'giveway';
        if (stopJ.get(j.id) !== 'stop') stopJ.set(j.id, kind);
      }
    }
    if (tg.highway === 'crossing' && origToEdge.has(n.id)) {
      const signal = tg.crossing === 'traffic_signals' || tg['crossing:signals'] === 'yes';
      const m = origToEdge.get(n.id);
      crosswalks.push({ edge: m.edgeId, t: +m.t.toFixed(3), signal });
    }
  }

  // Junction control.
  for (const n of nodes.values()) {
    const arms = nodeWays.get(n.id) || [];
    if (arms.length < 3) { n.control = { kind: 'bend' }; continue; }
    if (signalJ.has(n.id)) { n.control = { kind: 'light' }; continue; }
    const top = arms.reduce((a, b) => (b.rank > a.rank ? b : a));
    const ranksDiffer = arms.some((a) => a.rank < top.rank);
    if (stopJ.has(n.id) || ranksDiffer) n.control = { kind: 'priority', main: top.name, minor: stopJ.get(n.id) || 'giveway' };
    else n.control = { kind: 'uncontrolled' };
  }

  // Keep the largest connected component so AI traffic can't get stranded.
  const adj = new Map();
  for (const e of edges.values()) {
    if (!adj.has(e.a)) adj.set(e.a, []);
    if (!adj.has(e.b)) adj.set(e.b, []);
    adj.get(e.a).push(e.b); adj.get(e.b).push(e.a);
  }
  let bestComp = new Set();
  const seen = new Set();
  for (const start of adj.keys()) {
    if (seen.has(start)) continue;
    const comp = new Set([start]);
    const stack = [start];
    seen.add(start);
    while (stack.length) for (const v of adj.get(stack.pop())) if (!seen.has(v)) { seen.add(v); comp.add(v); stack.push(v); }
    if (comp.size > bestComp.size) bestComp = comp;
  }
  const outNodes = [...nodes.values()].filter((n) => bestComp.has(n.id)).map(({ osm, ...n }) => n);
  const outEdges = [...edges.values()].filter((e) => bestComp.has(e.a)).map(({ highway, ...e }) => e);
  const edgeIds = new Set(outEdges.map((e) => e.id));

  return {
    meta: {
      source: 'osm',
      title,
      attribution: '© OpenStreetMap contributors, ODbL 1.0',
      bbox,
      origin: { lat: +lat0.toFixed(6), lon: +lon0.toFixed(6) },
      generated: new Date().toISOString(),
      stats: { nodes: outNodes.length, edges: outEdges.length, signals: signalJ.size, crosswalks: crosswalks.length },
    },
    nodes: outNodes,
    edges: outEdges,
    crosswalks: crosswalks.filter((c) => edgeIds.has(c.edge)),
    landmarks: [],
  };
}

async function main() {
  const args = process.argv.slice(2);
  const opt = (name) => { const i = args.indexOf(`--${name}`); return i >= 0 ? args[i + 1] : null; };
  const bbox = opt('bbox') ? opt('bbox').split(',').map(Number) : DEFAULT_BBOX;
  const out = resolve(opt('out') || new URL('../data/lviv-osm.json', import.meta.url).pathname);
  let json;
  if (opt('in')) {
    json = JSON.parse(await readFile(opt('in'), 'utf8'));
  } else {
    const endpoint = opt('endpoint') || 'https://overpass-api.de/api/interpreter';
    console.log(`Fetching ${bbox.join(',')} from ${endpoint} …`);
    const res = await fetch(endpoint, { method: 'POST', body: new URLSearchParams({ data: overpassQuery(bbox) }) });
    if (!res.ok) throw new Error(`Overpass HTTP ${res.status}`);
    json = await res.json();
  }
  const data = convertOverpass(json, { bbox });
  await mkdir(dirname(out), { recursive: true });
  await writeFile(out, JSON.stringify(data));
  console.log(`Wrote ${out}`, data.meta.stats);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((e) => { console.error(e.message); process.exit(1); });
}
