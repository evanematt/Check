import * as THREE from 'three';
import { GeoBuilder, obbOverlap } from './geo.js';
import { mergeGeometries } from '../vendor/BufferGeometryUtils.js';
import { signTexture } from './signs.js';
import { LANE_W, PARK_W, SIDEWALK_W } from '../sim/network.js';
import { rng, headingOf, rightOf } from '../core/math.js';

const Y = { ground: -0.02, paving: 0, plaza: 0.008, road: 0.02, node: 0.026, mark: 0.04, zebra: 0.045 };

function edgeRot(e) { return Math.atan2(-e.d.z, e.d.x); }

export function buildCity(net, T, opts = {}) {
  const R = rng(7);
  const group = new THREE.Group();
  const colliders = [];
  const parking = [];
  const signals = new Map(); // key ctrl|phase -> {ctrl, phase, red, yellow, green}
  const pedSignals = new Map();
  const lampHeads = [];
  const signPlates = new Map(); // kind -> GeoBuilder
  const signPoles = new GeoBuilder(), signBacks = new GeoBuilder();
  const signList = [];
  const quality = opts.quality || 'high';

  // ---- materials ---------------------------------------------------------
  const mat = {
    asphalt: new THREE.MeshStandardMaterial({ map: T.asphalt, roughness: 0.92, metalness: 0 }),
    cobble: new THREE.MeshStandardMaterial({ map: T.cobble, roughness: 0.85 }),
    paving: new THREE.MeshStandardMaterial({ map: T.paving, roughness: 0.95 }),
    grass: new THREE.MeshStandardMaterial({ map: T.grass, roughness: 1 }),
    paint: new THREE.MeshStandardMaterial({ color: 0xf2f2ee, roughness: 0.7, polygonOffset: true, polygonOffsetFactor: -2 }),
    curb: new THREE.MeshStandardMaterial({ color: 0x9a9893, roughness: 0.9 }),
    roof: new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.8, side: THREE.DoubleSide }),
    pole: new THREE.MeshStandardMaterial({ color: 0x55595e, roughness: 0.6, metalness: 0.4 }),
    signBack: new THREE.MeshStandardMaterial({ color: 0x8d9196, roughness: 0.6, metalness: 0.3 }),
    housing: new THREE.MeshStandardMaterial({ color: 0x1d1f22, roughness: 0.7 }),
    trunk: new THREE.MeshStandardMaterial({ color: 0x5a4330, roughness: 1 }),
    crown: new THREE.MeshStandardMaterial({ color: 0x3f6b32, roughness: 1, flatShading: true }),
    lampHead: new THREE.MeshStandardMaterial({ color: 0xfff1c9, emissive: 0xffd27a, emissiveIntensity: 0 }),
    stone: new THREE.MeshStandardMaterial({ color: 0xe9e2d0, roughness: 0.85 }),
    copper: new THREE.MeshStandardMaterial({ color: 0x5f9a83, roughness: 0.6, metalness: 0.2 }),
    gold: new THREE.MeshStandardMaterial({ color: 0xc9a04a, roughness: 0.4, metalness: 0.7 }),
  };
  for (const k of ['asphalt', 'cobble', 'paving', 'grass']) mat[k].map.repeat.set(1, 1);
  const facadeMats = T.facades.map((map) => new THREE.MeshStandardMaterial({
    map, roughness: 0.9, emissive: 0xffc070, emissiveMap: T.windowsLit, emissiveIntensity: 0,
  }));

  const add = (geo, m, shadow = false) => {
    const mesh = new THREE.Mesh(geo, m);
    mesh.receiveShadow = true;
    mesh.castShadow = shadow;
    group.add(mesh);
    return mesh;
  };

  // ---- ground ------------------------------------------------------------
  const b = net.bounds();
  const cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
  const W = b.maxX - b.minX + 260, D = b.maxZ - b.minZ + 260;
  {
    const g = new GeoBuilder();
    g.flat(cx, cz, { x: 1, z: 0 }, 4000, 4000, Y.ground, 8);
    add(g.build(), mat.grass);
    const p = new GeoBuilder();
    p.flat(cx, cz, { x: 1, z: 0 }, W, D, Y.paving, 3);
    add(p.build(), mat.paving);
  }

  // ---- roads, junctions, plazas -------------------------------------------
  const asphalt = new GeoBuilder(), cobble = new GeoBuilder(), marks = new GeoBuilder(), curbs = new GeoBuilder();
  const roadQuad = (gb, e, s0, s1, l0, l1, y, k) => {
    const p = (s, l) => { const q = net.point(e, s, l); return [q.x, y, q.z]; };
    gb.quad([p(s0, l0), p(s1, l0), p(s1, l1), p(s0, l1)], [[l0 / k, s0 / k], [l0 / k, s1 / k], [l1 / k, s1 / k], [l1 / k, s0 / k]], [0, 1, 0]);
  };

  for (const e of net.edges.values()) {
    const na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
    if (e.zone === 'pedestrian') {
      roadQuad(cobble, e, 0, e.L, -e.hwL - 2, e.hwR + 2, Y.plaza, 2);
      continue;
    }
    const isCobble = e.surface === 'cobble';
    roadQuad(isCobble ? cobble : asphalt, e, 0, e.L, -e.hwL, e.hwR, Y.road, isCobble ? 2 : 4);
    // Curbs (between junctions only).
    const s0 = na.R, s1 = e.L - nb.R;
    if (s1 > s0 + 1) {
      for (const side of [-1, 1]) {
        const lat = side > 0 ? e.hwR + 0.15 : -e.hwL - 0.15;
        const mid = net.point(e, (s0 + s1) / 2, lat);
        curbs.box(mid.x, 0.07, mid.z, s1 - s0, 0.14, 0.3, edgeRot(e), 1);
      }
    }
    paintEdge(e, na, nb);
  }
  for (const n of net.nodes.values()) {
    if (!n.edges.length) continue;
    const anyAsphalt = n.edges.some((id) => net.edges.get(id).surface !== 'cobble' && net.edges.get(id).zone !== 'pedestrian');
    const drivable = n.edges.some((id) => net.edges.get(id).zone !== 'pedestrian');
    if (!drivable) continue;
    (anyAsphalt ? asphalt : cobble).disc(n.x, n.z, n.R, Y.node, 28, anyAsphalt ? 4 : 2);
  }
  for (const lm of net.landmarks) {
    if (lm.type === 'plaza') {
      cobble.flat(lm.x, lm.z, { x: 1, z: 0 }, lm.w, lm.d, Y.plaza, 2);
    }
  }

  function paintEdge(e, na, nb) {
    const sA = na.degree >= 3 ? na.R + 5.6 : na.R + 0.5;
    const sB = e.L - (nb.degree >= 3 ? nb.R + 5.6 : nb.R + 0.5);
    if (sB <= sA) return;
    const dashed = (lat, w = 0.13, dash = 3, gap = 6, solidNearEnds = 0) => {
      for (let s = sA; s < sB; s += dash + gap) {
        const s2 = Math.min(s + dash, sB);
        const p = net.point(e, (s + s2) / 2, lat);
        marks.flat(p.x, p.z, e.d, s2 - s, w, Y.mark);
      }
      if (solidNearEnds > 0) {
        solid(lat, w, sA, Math.min(sA + solidNearEnds, sB));
        solid(lat, w, Math.max(sB - solidNearEnds, sA), sB);
      }
    };
    const solid = (lat, w = 0.13, from = sA, to = sB) => {
      if (to <= from) return;
      const p = net.point(e, (from + to) / 2, lat);
      marks.flat(p.x, p.z, e.d, to - from, w, Y.mark);
    };
    if (!e.oneway) {
      if (e.lanes >= 2) { solid(-0.14); solid(0.14); }
      else dashed(0, 0.13, 3, 6, 20);
      for (let i = 1; i < e.lanes; i++) { dashed(LANE_W * i); dashed(-LANE_W * i); }
    } else {
      for (let k = 1; k < e.lanes; k++) dashed(-e.carriage + LANE_W * k);
    }
    // Parking strips with bay separators (6 m bays).
    for (const side of ['right', 'left']) {
      if (!(e.parking === side || e.parking === 'both')) continue;
      const sg = side === 'right' ? 1 : -1;
      const inner = sg * e.carriage, outer = sg * (e.carriage + PARK_W);
      const p0 = sA + 8, p1 = sB - 8;
      solid(inner, 0.12, p0, p1);
      const bays = [];
      for (let s = p0; s + 6 <= p1; s += 6) {
        const q = net.point(e, s, (inner + outer) / 2);
        marks.flat(q.x, q.z, rightOf(e.d), PARK_W, 0.12, Y.mark);
        bays.push({ s0: s, s1: s + 6 });
      }
      const q = net.point(e, p1, (inner + outer) / 2);
      marks.flat(q.x, q.z, rightOf(e.d), PARK_W, 0.12, Y.mark);
      parking.push({ edge: e.id, side, sign: sg, s0: p0, s1: p1, bays });
      placeSign('parking', e, side === 'right' ? 1 : -1, p0 + 1);
    }
  }

  // Stop lines.
  for (const e of net.edges.values()) {
    for (const st of e.stops) {
      const lat0 = e.oneway ? -e.carriage : 0, lat1 = e.carriage;
      const latMid = st.dir * (lat0 + lat1) / 2;
      const p = net.point(e, st.s, e.oneway ? 0 : latMid);
      const width = lat1 - lat0;
      if (st.control === 'giveway') {
        for (let l = lat0 + 0.4; l < lat1 - 0.3; l += 1.2) {
          const q = net.point(e, st.s, st.dir * (l + 0.3));
          marks.flat(q.x, q.z, e.d, 0.45, 0.6, Y.mark);
        }
      } else if (st.control === 'stop' || st.control === 'light') {
        marks.flat(p.x, p.z, e.d, 0.45, width - 0.2, Y.mark);
      }
    }
  }
  // Zebra crossings.
  for (const cw of net.crosswalks) {
    const e = net.edges.get(cw.edge);
    for (let l = -e.hwL + 0.4; l < e.hwR - 0.3; l += 1.0) {
      const q = net.point(e, cw.s, l + 0.27);
      marks.flat(q.x, q.z, e.d, cw.half * 2, 0.55, Y.zebra);
    }
  }

  add(asphalt.build(), mat.asphalt);
  add(cobble.build(), mat.cobble);
  add(marks.build(), mat.paint);
  add(curbs.build(), mat.curb);

  // ---- blocked areas for building placement (spatial grid) ------------------
  const GRID = 50;
  const grid = new Map();
  const addBlock = (o) => {
    const r = Math.hypot(o.hx, o.hz);
    for (let gx = Math.floor((o.cx - r) / GRID); gx <= Math.floor((o.cx + r) / GRID); gx++) {
      for (let gz = Math.floor((o.cz - r) / GRID); gz <= Math.floor((o.cz + r) / GRID); gz++) {
        const k = `${gx},${gz}`;
        if (!grid.has(k)) grid.set(k, []);
        grid.get(k).push(o);
      }
    }
  };
  const blocked = (o) => {
    const r = Math.hypot(o.hx, o.hz);
    for (let gx = Math.floor((o.cx - r) / GRID); gx <= Math.floor((o.cx + r) / GRID); gx++) {
      for (let gz = Math.floor((o.cz - r) / GRID); gz <= Math.floor((o.cz + r) / GRID); gz++) {
        const cell = grid.get(`${gx},${gz}`);
        if (cell) for (const b of cell) if (obbOverlap(o, b)) return true;
      }
    }
    return false;
  };
  const blockers = { push: addBlock };
  for (const e of net.edges.values()) {
    const m = net.point(e, e.L / 2, 0);
    const hw = Math.max(e.hwL, e.hwR) + SIDEWALK_W + 0.5;
    blockers.push({ cx: m.x, cz: m.z, hx: e.L / 2 + 2, hz: hw, rot: edgeRot(e) });
  }
  for (const n of net.nodes.values()) {
    blockers.push({ cx: n.x, cz: n.z, hx: n.R + SIDEWALK_W, hz: n.R + SIDEWALK_W, rot: 0 });
  }

  // ---- landmarks -----------------------------------------------------------
  for (const lm of net.landmarks) buildLandmark(lm);

  function buildLandmark(lm) {
    const gb = new GeoBuilder();
    if (lm.type === 'plaza') {
      blockers.push({ cx: lm.x, cz: lm.z, hx: lm.w / 2, hz: lm.d / 2, rot: 0 });
      return;
    }
    if (lm.type === 'ratusha') {
      // Lviv City Hall: four-storey block with a 65 m tower.
      gb.box(lm.x, 8, lm.z, 34, 16, 28, lm.rot, 3.5);
      gb.box(lm.x, 16 + 18, lm.z, 10, 36, 10, lm.rot, 3.5);
      gb.box(lm.x, 52 + 2, lm.z, 12, 4, 12, lm.rot, 3.5);
      add(gb.build(), facadeMats[3], true);
      const crown = new THREE.Mesh(new THREE.CylinderGeometry(2.5, 5, 8, 8), mat.copper);
      crown.position.set(lm.x, 60, lm.z);
      const top = new THREE.Mesh(new THREE.SphereGeometry(1.2, 10, 8), mat.gold);
      top.position.set(lm.x, 65, lm.z);
      group.add(crown, top);
      const ob = { cx: lm.x, cz: lm.z, hx: 17, hz: 14, rot: lm.rot };
      colliders.push({ type: 'obb', ...ob });
      blockers.push({ ...ob, hx: 24, hz: 22 });
    } else if (lm.type === 'opera') {
      gb.box(lm.x, 11, lm.z, 64, 22, 42, lm.rot, 3.5);
      add(gb.build(), mat.stone, true);
      const dome = new THREE.Mesh(new THREE.SphereGeometry(9, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), mat.copper);
      dome.position.set(lm.x, 22, lm.z);
      const statue = new THREE.Mesh(new THREE.ConeGeometry(1.5, 6, 6), mat.gold);
      statue.position.set(lm.x, 33, lm.z);
      group.add(dome, statue);
      // Columns along the facade facing the avenue.
      const cg = new THREE.CylinderGeometry(0.6, 0.7, 12, 10);
      const cols = new THREE.InstancedMesh(cg, mat.stone, 8);
      const m4 = new THREE.Matrix4();
      const c = Math.cos(lm.rot), sn = Math.sin(lm.rot);
      for (let i = 0; i < 8; i++) {
        // Portico on the local -z face (towards the avenue).
        const lx = -14 + i * 4, lz = -23;
        m4.makeTranslation(lm.x + lx * c + lz * sn, 6, lm.z - lx * sn + lz * c);
        cols.setMatrixAt(i, m4);
      }
      group.add(cols);
      const ob = { cx: lm.x, cz: lm.z, hx: 32, hz: 21, rot: lm.rot };
      colliders.push({ type: 'obb', ...ob });
      blockers.push({ ...ob, hx: 38, hz: 25 });
    } else if (lm.type === 'church') {
      gb.box(lm.x, 12, lm.z, 26, 24, 44, lm.rot, 3.5);
      add(gb.build(), facadeMats[7], true);
      const dome = new THREE.Mesh(new THREE.SphereGeometry(8, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), mat.copper);
      dome.position.set(lm.x, 24, lm.z);
      const lantern = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.6, 6, 8), mat.copper);
      lantern.position.set(lm.x, 34, lm.z);
      const cross = new THREE.Mesh(new THREE.BoxGeometry(0.3, 3, 0.3), mat.gold);
      cross.position.set(lm.x, 38.5, lm.z);
      group.add(dome, lantern, cross);
      const ob = { cx: lm.x, cz: lm.z, hx: 13, hz: 22, rot: lm.rot };
      colliders.push({ type: 'obb', ...ob });
      blockers.push({ ...ob, hx: 18, hz: 27 });
    }
  }

  // ---- buildings -----------------------------------------------------------
  const facadeGB = facadeMats.map(() => new GeoBuilder());
  const roofGB = new GeoBuilder();

  const roofColors = [[0.58, 0.27, 0.18], [0.52, 0.24, 0.16], [0.36, 0.38, 0.40], [0.34, 0.52, 0.45]];

  const tryBuilding = (o, floors) => {
    if (blocked(o)) return false;
    addBlock({ ...o, hx: o.hx + 0.2, hz: o.hz + 0.2 });
    const h = floors * 3.4;
    const pi = Math.floor(R() * facadeGB.length);
    facadeGB[pi].box(o.cx, h / 2, o.cz, o.hx * 2, h, o.hz * 2, o.rot, 3.5);
    gable(o, h, R.pick(roofColors));
    colliders.push({ type: 'obb', cx: o.cx, cz: o.cz, hx: o.hx, hz: o.hz, rot: o.rot });
    return true;
  };

  function gable(o, h, color) {
    const c = Math.cos(o.rot), s = Math.sin(o.rot);
    const P = (lx, ly, lz) => [o.cx + lx * c + lz * s, ly, o.cz - lx * s + lz * c];
    const rh = Math.min(5, o.hz * 0.55);
    const x = o.hx, z = o.hz + 0.4;
    const n1 = [s * 0.5, 0.85, c * 0.5], n2 = [-s * 0.5, 0.85, -c * 0.5];
    roofGB.quad([P(-x, h, z), P(x, h, z), P(x, h + rh, 0), P(-x, h + rh, 0)], [[0, 0], [1, 0], [1, 1], [0, 1]], n1, color);
    roofGB.quad([P(x, h, -z), P(-x, h, -z), P(-x, h + rh, 0), P(x, h + rh, 0)], [[0, 0], [1, 0], [1, 1], [0, 1]], n2, color);
    const wall = [0.85, 0.78, 0.66];
    roofGB.tri([P(x, h, z), P(x, h, -z), P(x, h + rh, 0)], [[0, 0], [1, 0], [0.5, 1]], [c, 0, -s], wall);
    roofGB.tri([P(-x, h, -z), P(-x, h, z), P(-x, h + rh, 0)], [[0, 0], [1, 0], [0.5, 1]], [-c, 0, s], wall);
  }

  // Frontage along streets.
  for (const e of net.edges.values()) {
    if (e.zone === 'pedestrian') continue;
    const na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
    const old = e.zone === 'residential';
    for (const side of [-1, 1]) {
      let s = na.R + SIDEWALK_W + 1;
      const end = e.L - nb.R - SIDEWALK_W - 1;
      while (s < end - 6) {
        const w = Math.min(R.range(12, 24), end - s);
        const d = R.range(12, 18);
        const hw = side > 0 ? e.hwR : e.hwL;
        const lat = side * (hw + SIDEWALK_W + 0.6 + d / 2);
        const p = net.point(e, s + w / 2, lat);
        tryBuilding({ cx: p.x, cz: p.z, hx: w / 2, hz: d / 2, rot: edgeRot(e) }, old ? R.int(3, 4) : R.int(4, 6));
        s += w + (R() < 0.15 ? R.range(2, 6) : 0.3);
      }
    }
  }
  // Frame the market square.
  for (const lm of net.landmarks.filter((l) => l.type === 'plaza')) {
    const sides = [
      { x: lm.x, z: lm.z - lm.d / 2 - 8, len: lm.w, rot: 0 },
      { x: lm.x, z: lm.z + lm.d / 2 + 8, len: lm.w, rot: 0 },
      { x: lm.x - lm.w / 2 - 8, z: lm.z, len: lm.d, rot: Math.PI / 2 },
      { x: lm.x + lm.w / 2 + 8, z: lm.z, len: lm.d, rot: Math.PI / 2 },
    ];
    for (const sd of sides) {
      for (let t = -sd.len / 2 + 8; t < sd.len / 2 - 6; t += 13) {
        const ox = sd.rot ? 0 : t, oz = sd.rot ? t : 0;
        tryBuilding({ cx: sd.x + ox, cz: sd.z + oz, hx: 6.3, hz: 7, rot: sd.rot }, R.int(3, 4));
      }
    }
  }
  // Fill block interiors.
  for (let x = b.minX - 100; x < b.maxX + 100; x += 24) {
    for (let z = b.minZ - 100; z < b.maxZ + 100; z += 24) {
      if (R() < 0.35) continue;
      tryBuilding({ cx: x + R.range(-4, 4), cz: z + R.range(-4, 4), hx: R.range(6, 10), hz: R.range(6, 9), rot: 0 }, R.int(3, 6));
    }
  }
  facadeGB.forEach((gb, i) => { if (gb.vertexCount) add(gb.build(), facadeMats[i], quality === 'high'); });
  add(roofGB.build(), mat.roof, quality === 'high');

  // ---- trees & street lamps ------------------------------------------------
  const trees = [], lamps = [];
  for (const e of net.edges.values()) {
    if (e.zone === 'pedestrian') continue;
    const na = net.nodes.get(e.a), nb = net.nodes.get(e.b);
    const nearCrosswalk = (s) => e.crosswalks.some((c) => Math.abs(c.s - s) < 7);
    for (let s = na.R + 10; s < e.L - nb.R - 8; s += 17) {
      if (nearCrosswalk(s)) continue;
      if (e.zone === 'city' && R() < 0.75) {
        const side = R() < 0.5 ? -1 : 1;
        const lat = side * ((side > 0 ? e.hwR : e.hwL) + 1.4);
        trees.push(net.point(e, s, lat));
      }
    }
    let k = 0;
    for (let s = na.R + 14; s < e.L - nb.R - 6; s += 30, k++) {
      if (nearCrosswalk(s)) continue;
      const side = k % 2 ? -1 : 1;
      const lat = side * ((side > 0 ? e.hwR : e.hwL) + 0.6);
      lamps.push({ ...net.point(e, s, lat), toward: { x: -e.r.x * side, z: -e.r.z * side } });
    }
  }
  {
    const trunkG = new THREE.CylinderGeometry(0.18, 0.25, 3, 6);
    const crownG = new THREE.IcosahedronGeometry(2.3, 0);
    const ti = new THREE.InstancedMesh(trunkG, mat.trunk, trees.length);
    const ci = new THREE.InstancedMesh(crownG, mat.crown, trees.length);
    const m4 = new THREE.Matrix4();
    trees.forEach((p, i) => {
      m4.makeTranslation(p.x, 1.5, p.z); ti.setMatrixAt(i, m4);
      const sc = 0.8 + R() * 0.5;
      m4.makeScale(sc, sc * 1.2, sc).setPosition(p.x, 4.3, p.z); ci.setMatrixAt(i, m4);
      colliders.push({ type: 'circle', x: p.x, z: p.z, r: 0.35 });
    });
    ci.castShadow = quality === 'high';
    group.add(ti, ci);

    const poles = new GeoBuilder();
    const headG = new THREE.SphereGeometry(0.32, 10, 8);
    const hi = new THREE.InstancedMesh(headG, mat.lampHead, lamps.length);
    lamps.forEach((p, i) => {
      poles.box(p.x, 3.5, p.z, 0.16, 7, 0.16, 0, 1);
      const hx = p.x + p.toward.x * 1.1, hz = p.z + p.toward.z * 1.1;
      poles.box((p.x + hx) / 2, 7, (p.z + hz) / 2, 0.1, 0.1, 1.2, Math.atan2(p.toward.x, p.toward.z), 1);
      m4.makeTranslation(hx, 6.85, hz); hi.setMatrixAt(i, m4);
      lampHeads.push({ x: hx, z: hz });
      colliders.push({ type: 'circle', x: p.x, z: p.z, r: 0.15 });
    });
    add(poles.build(), mat.pole);
    group.add(hi);
  }

  // ---- signs ----------------------------------------------------------------
  function placeSign(kind, e, dir, s, stackIndex = 0, latExtra = 0) {
    const hw = dir > 0 ? e.hwR : e.hwL;
    const lat = dir * (hw + 1.2 + latExtra);
    const p = net.point(e, s, lat);
    const travel = net.travelDir(e, dir);
    const right = rightOf(travel);
    const y = 2.55 - stackIndex * 0.8;
    if (stackIndex === 0) {
      signPoles.box(p.x, 1.45, p.z, 0.08, 2.9, 0.08, 0, 1);
      colliders.push({ type: 'circle', x: p.x, z: p.z, r: 0.1 });
    }
    const w = 0.7, h = 0.7;
    const c = (u, v, off) => [p.x + right.x * u + travel.x * off, y + v, p.z + right.z * u + travel.z * off];
    if (!signPlates.has(kind)) signPlates.set(kind, new GeoBuilder());
    signPlates.get(kind).quad([c(-w / 2, -h / 2, -0.06), c(w / 2, -h / 2, -0.06), c(w / 2, h / 2, -0.06), c(-w / 2, h / 2, -0.06)], [[0, 0], [1, 0], [1, 1], [0, 1]], [-travel.x, 0, -travel.z]);
    signBacks.quad([c(-w / 2, -h / 2, -0.04), c(w / 2, -h / 2, -0.04), c(w / 2, h / 2, -0.04), c(-w / 2, h / 2, -0.04)], [[0, 0], [1, 0], [1, 1], [0, 1]], [travel.x, 0, travel.z]);
    signList.push({ kind, x: p.x, z: p.z, edge: e.id, dir, s });
  }

  for (const e of net.edges.values()) {
    if (e.zone === 'pedestrian') {
      // Pedestrian zone: sign + bollards at both ends.
      for (const [end, dir] of [['a', 1], ['b', -1]]) {
        const n = net.nodes.get(e[end]);
        const s = dir > 0 ? n.R + 1.5 : e.L - n.R - 1.5;
        placeSign('pedzone', e, dir, s);
        for (let l = -e.hwL; l <= e.hwR + 0.01; l += 1.5) {
          const q = net.point(e, s + dir * 1.5, l);
          signPoles.box(q.x, 0.45, q.z, 0.22, 0.9, 0.22, 0, 1);
          colliders.push({ type: 'circle', x: q.x, z: q.z, r: 0.15 });
        }
      }
      continue;
    }
    for (const st of e.stops) {
      if (!st.node) continue;
      if (st.control === 'giveway') placeSign('giveway', e, st.dir, st.s + st.dir * 0.5);
      else if (st.control === 'stop') placeSign('stop', e, st.dir, st.s + st.dir * 0.5);
      else if (st.control === 'main') placeSign('priority', e, st.dir, st.s - st.dir * 8);
    }
    // One-way streets: entry sign and "no entry" against the flow.
    if (e.oneway) {
      const okDir = e.oneway === 'ab' ? 1 : -1;
      const startN = net.startNode(e, okDir), endN = net.endNode(e, okDir);
      const sStart = okDir > 0 ? startN.R + 6 : e.L - startN.R - 6;
      placeSign('oneway', e, okDir, sStart, 0, 0);
      const sEnd = okDir > 0 ? e.L - endN.R - 1.5 : endN.R + 1.5;
      placeSign('noentry', e, -okDir, sEnd);
    }
    // Residential zone boundaries.
    for (const [end, dirIn] of [['a', 1], ['b', -1]]) {
      const n = net.nodes.get(e[end]);
      if (n.degree < 2) continue;
      const others = n.edges.map((id) => net.edges.get(id)).filter((o) => o.id !== e.id);
      const boundary = e.zone === 'residential' && others.some((o) => o.zone === 'city');
      if (!boundary) continue;
      const sIn = dirIn > 0 ? n.R + 7 : e.L - n.R - 7;
      if (net.allowed(e, dirIn)) placeSign('residential', e, dirIn, sIn, 0, 0.5);
      if (net.allowed(e, -dirIn)) placeSign('endresidential', e, -dirIn, sIn + dirIn * 4, 0, 0.5);
    }
    // Speed limits that differ from the 50 km/h default in town.
    if (e.zone === 'city' && e.limit && e.limit !== 50) {
      for (const dir of [1, -1]) {
        if (!net.allowed(e, dir)) continue;
        const n = net.startNode(e, dir);
        placeSign(`speed${e.limit}`, e, dir, dir > 0 ? n.R + 12 : e.L - n.R - 12, 0, 0.4);
      }
    }
  }
  // Crosswalk signs at the near edge of each crossing, on the right of the
  // approaching traffic — never on the junction corner where cars turn.
  for (const cw of net.crosswalks) {
    const e = net.edges.get(cw.edge);
    for (const dir of [1, -1]) {
      if (!net.allowed(e, dir) && e.oneway) continue;
      placeSign('crossing', e, dir, cw.s - dir * (cw.half + 0.4), 0, 0.3);
    }
  }
  for (const [kind, gb] of signPlates) {
    add(gb.build(), new THREE.MeshStandardMaterial({ map: signTexture(kind), transparent: true, alphaTest: 0.4, roughness: 0.5 }));
  }
  add(signBacks.build(), mat.signBack);
  add(signPoles.build(), mat.pole);

  // ---- traffic lights --------------------------------------------------------
  const lampGeo = new THREE.CircleGeometry(0.13, 16);
  const housingGeo = new THREE.BoxGeometry(0.38, 1.05, 0.28);
  const visorGeo = new THREE.BoxGeometry(0.36, 0.04, 0.18);
  const poleGeo = new THREE.CylinderGeometry(0.07, 0.09, 3.2, 8);
  // Housing with the three sun visors merged into one geometry.
  const headGeo = mergeGeometries([housingGeo, ...[0.33, 0, -0.33].map((y) => visorGeo.clone().translate(0, y + 0.15, 0.22))]);
  const lampMat = (color) => new THREE.MeshStandardMaterial({ color: 0x111111, emissive: color, emissiveIntensity: 0.05 });

  function signalKey(ctrl, phase) {
    const key = `${ctrl.id}|${phase}`;
    if (!signals.has(key)) {
      signals.set(key, { ctrl, phase, red: lampMat(0xff2a1a), yellow: lampMat(0xffb300), green: lampMat(0x22ff66) });
    }
    return signals.get(key);
  }

  for (const e of net.edges.values()) {
    for (const st of e.stops) {
      if (st.control !== 'light') continue;
      const sig = signalKey(st.ctrl, st.phase);
      const hw = st.dir > 0 ? e.hwR : e.hwL;
      const travel = net.travelDir(e, st.dir);
      const heading = headingOf(travel);
      // Right-hand pole, plus a second head on the median / left kerb of wide roads.
      const heads = [st.dir * (hw + 0.8)];
      if (e.oneway) heads.push(-st.dir * (e.carriage + 0.8));
      else if (e.lanes >= 2) heads.push(-st.dir * 0.3);
      for (const lat of heads) {
        const p = net.point(e, st.s + st.dir * 1.2, lat);
        const pole = new THREE.Mesh(poleGeo, mat.pole);
        pole.position.set(p.x, 1.6, p.z);
        group.add(pole);
        colliders.push({ type: 'circle', x: p.x, z: p.z, r: 0.12 });
        const g = new THREE.Group();
        g.position.set(p.x, 3.8, p.z);
        g.rotation.y = heading;
        g.add(new THREE.Mesh(headGeo, mat.housing));
        [['red', 0.33], ['yellow', 0], ['green', -0.33]].forEach(([c, y]) => {
          const l = new THREE.Mesh(lampGeo, sig[c]);
          l.position.set(0, y, 0.145);
          g.add(l);
        });
        group.add(g);
      }
    }
  }
  // Pedestrian signal heads at signalised crosswalks.
  for (const cw of net.crosswalks) {
    if (!cw.ctrl) continue;
    const e = net.edges.get(cw.edge);
    const key = `${cw.ctrl.id}|${cw.phase}`;
    if (!pedSignals.has(key)) pedSignals.set(key, { cw, red: lampMat(0xff2a1a), green: lampMat(0x22ff66) });
    const ps = pedSignals.get(key);
    for (const side of [-1, 1]) {
      const lat = side > 0 ? e.hwR + 0.9 : -e.hwL - 0.9;
      const p = net.point(e, cw.s + cw.half + 0.6, lat);
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 2.6, 8), mat.pole);
      pole.position.set(p.x, 1.3, p.z);
      group.add(pole);
      colliders.push({ type: 'circle', x: p.x, z: p.z, r: 0.1 });
      const g = new THREE.Group();
      g.position.set(p.x, 2.8, p.z);
      const across = { x: -e.r.x * side, z: -e.r.z * side };
      g.rotation.y = Math.atan2(across.x, across.z);
      g.add(new THREE.Mesh(new THREE.BoxGeometry(0.34, 0.72, 0.24), mat.housing));
      const r = new THREE.Mesh(lampGeo, ps.red); r.position.set(0, 0.17, 0.125);
      const gr = new THREE.Mesh(lampGeo, ps.green); gr.position.set(0, -0.17, 0.125);
      g.add(r, gr);
      group.add(g);
    }
  }

  // Distant hills (Високий замок and the Roztochia ridge) for the skyline.
  {
    const hillMat = new THREE.MeshStandardMaterial({ color: 0x5c7a4a, roughness: 1, flatShading: true });
    [[700, -900, 260, 120], [-900, -700, 320, 90], [200, -1300, 500, 150], [-300, 1200, 420, 80]].forEach(([x, z, r, h]) => {
      const m = new THREE.Mesh(new THREE.ConeGeometry(r, h, 9), hillMat);
      m.position.set(x, h / 2 - 5, z);
      group.add(m);
    });
  }

  function update(t, env) {
    const blink = Math.floor(t * 2) % 2 === 0;
    for (const s of signals.values()) {
      const st = net.signalState(s.ctrl, s.phase, t).state;
      const on = {
        red: st === 'red' || st === 'redyellow',
        yellow: st === 'yellow' || st === 'redyellow',
        green: st === 'green' || (st === 'flash' && blink),
      };
      for (const c of ['red', 'yellow', 'green']) s[c].emissiveIntensity = on[c] ? 3.2 : 0.04;
    }
    for (const p of pedSignals.values()) {
      const go = net.pedestrianGo(p.cw, t).go;
      p.red.emissiveIntensity = go ? 0.04 : 2.5;
      p.green.emissiveIntensity = go ? 2.5 : 0.04;
    }
    const night = env.night;
    mat.lampHead.emissiveIntensity = night > 0.3 ? 2.5 * night : 0;
    for (const m of facadeMats) m.emissiveIntensity = night * 0.9;
    const wet = env.wet;
    mat.asphalt.roughness = 0.92 - wet * 0.55;
    mat.asphalt.color.setScalar(1 - wet * 0.35);
    mat.cobble.roughness = 0.85 - wet * 0.45;
  }

  return { group, colliders, parking, signList, lampHeads, update };
}
