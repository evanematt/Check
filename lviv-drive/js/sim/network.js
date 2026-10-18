import { segProject, rightOf, dot, cross, turnAngle } from '../core/math.js';

export const LANE_W = 3.3;
export const PARK_W = 2.4;
export const SIDEWALK_W = 4;
const CW_HALF = 1.6; // crosswalk half-width along the road
const STOP_GAP = 5.2; // stop line distance beyond the node radius

const EDGE_DEFAULTS = { lanes: 1, oneway: null, limit: 50, zone: 'city', surface: 'asphalt', parking: null };

// Traffic-light timeline (seconds). Phase 1 is phase 0 shifted by half a cycle,
// so there is a 2 s all-red overlap. Order follows Ukrainian practice:
// green -> flashing green -> yellow -> red -> red+yellow -> green.
const NODE_CYCLE = 44;
const NODE_TABLE = [[14, 'green'], [17, 'flash'], [20, 'yellow'], [42, 'red'], [44, 'redyellow']];
const PED_CYCLE = 40;
const PED_TABLE = [[22, 'green'], [25, 'flash'], [28, 'yellow'], [38, 'red'], [40, 'redyellow']];

function lookup(table, tt) {
  let start = 0;
  for (const [end, state] of table) {
    if (tt < end) return { state, remaining: end - tt, elapsed: tt - start };
    start = end;
  }
  return { state: 'red', remaining: 0, elapsed: 0 };
}

export class RoadNetwork {
  constructor(data) {
    this.meta = data.meta || {};
    this.landmarks = data.landmarks || [];
    this.nodes = new Map();
    this.edges = new Map();
    this.controllers = [];
    this.crosswalks = [];

    for (const n of data.nodes) {
      this.nodes.set(n.id, { ...n, control: n.control || { kind: 'uncontrolled' }, edges: [], R: 8 });
    }
    for (const raw of data.edges) {
      const a = this.nodes.get(raw.a), b = this.nodes.get(raw.b);
      if (!a || !b) continue;
      const dx = b.x - a.x, dz = b.z - a.z, L = Math.hypot(dx, dz);
      if (L < 1) continue;
      const e = { ...EDGE_DEFAULTS, ...raw, ax: a.x, az: a.z, bx: b.x, bz: b.z, L, d: { x: dx / L, z: dz / L } };
      e.r = rightOf(e.d);
      e.carriage = e.oneway ? (e.lanes * LANE_W) / 2 : e.lanes * LANE_W;
      const pk = e.parking;
      e.hwR = e.carriage + (pk === 'right' || pk === 'both' ? PARK_W : 0);
      e.hwL = e.carriage + (pk === 'left' || pk === 'both' ? PARK_W : 0);
      e.stops = [];
      e.crosswalks = [];
      this.edges.set(e.id, e);
      a.edges.push(e.id);
      b.edges.push(e.id);
    }
    for (const n of this.nodes.values()) {
      let hw = 0;
      for (const id of n.edges) {
        const e = this.edges.get(id);
        hw = Math.max(hw, e.hwL, e.hwR);
      }
      n.degree = n.edges.length;
      n.R = n.degree <= 2 ? hw + 1 : hw + 4;
    }
    this._buildApproaches();
    this._buildMidblock(data.crosswalks || []);
  }

  // ---- basic edge helpers -------------------------------------------------
  endNode(e, dir) { return this.nodes.get(dir > 0 ? e.b : e.a); }
  startNode(e, dir) { return this.nodes.get(dir > 0 ? e.a : e.b); }
  distToEnd(e, s, dir) { return dir > 0 ? e.L - s : s; }
  sFromEnd(e, dir, d) { return dir > 0 ? e.L - d : d; }
  travelDir(e, dir) { return { x: e.d.x * dir, z: e.d.z * dir }; }
  allowed(e, dir) {
    if (e.zone === 'pedestrian') return false;
    if (e.oneway === 'ab') return dir > 0;
    if (e.oneway === 'ba') return dir < 0;
    return true;
  }
  laneCount(e) { return e.lanes; }
  // Offset of lane i (0 = rightmost, kerb side) measured to the right of travel.
  laneOffset(e, dir, i = 0) {
    return e.oneway ? e.carriage - LANE_W * (i + 0.5) : LANE_W * (e.lanes - i - 0.5);
  }
  point(e, s, lat) {
    return { x: e.ax + e.d.x * s + e.r.x * lat, z: e.az + e.d.z * s + e.r.z * lat };
  }
  lanePoint(e, dir, i, s) {
    return this.point(e, s, this.laneOffset(e, dir, i) * dir);
  }
  // Unit vector pointing from node n out along edge e.
  armDir(e, nodeId) {
    return e.a === nodeId ? { x: e.d.x, z: e.d.z } : { x: -e.d.x, z: -e.d.z };
  }
  dirTowards(e, nodeId) { return e.b === nodeId ? 1 : -1; }

  // ---- approaches, stop lines, signals, crosswalks ------------------------
  _buildApproaches() {
    let ctrlSeed = 0;
    for (const n of this.nodes.values()) {
      if (n.degree < 3) continue;
      const kind = n.control.kind;
      let ctrl = null;
      if (kind === 'light') {
        ctrl = { id: `L_${n.id}`, type: 'node', node: n.id, offset: (ctrlSeed++ * 13.7) % NODE_CYCLE };
        this.controllers.push(ctrl);
      }
      const arms = n.edges.map((id) => this.edges.get(id));
      const ref = this.armDir(arms[0], n.id);
      for (const e of arms) {
        const dir = this.dirTowards(e, n.id);
        const arm = this.armDir(e, n.id);
        const phase = Math.abs(cross(ref, arm)) < 0.71 ? 0 : 1;
        let control = 'equal';
        if (kind === 'light') control = 'light';
        else if (kind === 'priority') control = e.name === n.control.main ? 'main' : n.control.minor || 'giveway';
        // Crosswalk across this arm near the junction.
        if (e.zone !== 'pedestrian') {
          const cw = this._addCrosswalk(e, this.sFromEnd(e, dir, n.R + CW_HALF + 0.4), n.id);
          if (ctrl) { cw.ctrl = ctrl; cw.phase = phase; }
        }
        if (!this.allowed(e, dir)) continue;
        e.stops.push({
          dir, s: this.sFromEnd(e, dir, n.R + STOP_GAP), node: n.id, control,
          ctrl, phase, key: `${e.id}|${dir}`,
        });
      }
    }
  }

  _buildMidblock(list) {
    let i = 0;
    for (const m of list) {
      const e = this.edges.get(m.edge);
      if (!e) continue;
      const s = (m.t ?? 0.5) * e.L;
      const cw = this._addCrosswalk(e, s, null);
      if (m.signal) {
        const ctrl = { id: `P_${e.id}_${i++}`, type: 'ped', offset: (i * 7.3) % PED_CYCLE };
        this.controllers.push(ctrl);
        cw.ctrl = ctrl;
        cw.phase = 0;
        for (const dir of [1, -1]) {
          if (!this.allowed(e, dir)) continue;
          e.stops.push({ dir, s: s - dir * (CW_HALF + 3), node: null, control: 'light', ctrl, phase: 0, key: `${e.id}|${dir}|m${i}` });
        }
      }
    }
  }

  _addCrosswalk(e, s, nodeId) {
    const cw = {
      id: `cw${this.crosswalks.length}`, edge: e.id, s, half: CW_HALF, node: nodeId,
      latMin: -e.hwL - 1.2, latMax: e.hwR + 1.2, ctrl: null, phase: 0, peds: 0,
    };
    this.crosswalks.push(cw);
    e.crosswalks.push(cw);
    return cw;
  }

  signalState(ctrl, phase, t) {
    if (ctrl.type === 'ped') return lookup(PED_TABLE, (((t + ctrl.offset) % PED_CYCLE) + PED_CYCLE) % PED_CYCLE);
    const tt = (((t + ctrl.offset + phase * (NODE_CYCLE / 2)) % NODE_CYCLE) + NODE_CYCLE) % NODE_CYCLE;
    return lookup(NODE_TABLE, tt);
  }

  // Pedestrian signal for a crosswalk: green while vehicles on that arm see red.
  pedestrianGo(cw, t) {
    if (!cw.ctrl) return { go: true, remaining: Infinity };
    const st = this.signalState(cw.ctrl, cw.phase, t);
    return { go: st.state === 'red' && st.elapsed > 1, remaining: st.state === 'red' ? st.remaining : 0 };
  }

  // Next stop point ahead of position s when travelling dir on edge e.
  nextStop(e, dir, s) {
    let best = null;
    for (const st of e.stops) {
      if (st.dir !== dir) continue;
      const ahead = (st.s - s) * dir;
      if (ahead >= -0.5 && (!best || ahead < best.ahead)) best = { stop: st, ahead };
    }
    return best;
  }

  // ---- spatial queries ----------------------------------------------------
  locate(x, z) {
    let best = null;
    for (const e of this.edges.values()) {
      const p = segProject(x, z, e.ax, e.az, e.bx, e.bz);
      const reach = Math.max(e.hwL, e.hwR) + SIDEWALK_W + 3;
      if (p.d > reach) continue;
      if (!best || p.d < best.dist) {
        const lat = (x - e.ax) * e.r.x + (z - e.az) * e.r.z;
        best = { edge: e, s: p.t * e.L, lat, dist: p.d };
      }
    }
    let inNode = null;
    for (const n of this.nodes.values()) {
      const d = Math.hypot(x - n.x, z - n.z);
      if (d < n.R && (!inNode || d < inNode.d)) inNode = { node: n, d };
    }
    const res = { edge: best?.edge || null, s: best?.s || 0, lat: best?.lat || 0, node: inNode?.node || null };
    if (inNode) {
      res.onRoad = true;
      res.surface = 'road';
    } else if (best) {
      const e = best.edge;
      const along = best.s > 0.01 && best.s < e.L - 0.01;
      if (along && best.lat >= -e.hwL && best.lat <= e.hwR) res.surface = 'road';
      else if (best.dist <= Math.max(e.hwL, e.hwR) + SIDEWALK_W) res.surface = 'sidewalk';
      else res.surface = 'off';
      res.onRoad = res.surface === 'road';
    } else {
      res.surface = 'off';
      res.onRoad = false;
    }
    return res;
  }

  // Legal ways out of a node, excluding the arm we arrived on.
  exits(nodeId, fromEdgeId) {
    const n = this.nodes.get(nodeId);
    const out = [];
    for (const id of n.edges) {
      if (id === fromEdgeId && n.degree > 1) continue;
      const e = this.edges.get(id);
      const dir = e.a === nodeId ? 1 : -1;
      if (this.allowed(e, dir)) out.push({ edge: e, dir });
    }
    return out;
  }

  turnKind(inVec, outVec) {
    const a = turnAngle(inVec, outVec);
    if (a > 0.6) return 'right';
    if (a < -0.6) return 'left';
    return 'straight';
  }

  // Arms whose traffic has priority over a vehicle entering `node` from edge e.
  priorityArms(nodeId, e, intent = 'straight') {
    const n = this.nodes.get(nodeId);
    if (!n || n.degree < 3) return [];
    const myStop = e.stops.find((s) => s.node === nodeId);
    const control = myStop ? myStop.control : 'equal';
    const travel = this.travelDir(e, this.dirTowards(e, nodeId));
    const res = [];
    for (const id of n.edges) {
      if (id === e.id) continue;
      const o = this.edges.get(id);
      const odir = this.dirTowards(o, nodeId);
      if (!this.allowed(o, odir)) continue;
      const arm = this.armDir(o, nodeId);
      const oStop = o.stops.find((s) => s.node === nodeId);
      const oControl = oStop ? oStop.control : 'equal';
      const oncoming = dot(arm, travel) > 0.7;
      const fromRight = cross(travel, arm) > 0.3;
      let has = false;
      if (control === 'giveway' || control === 'stop') has = oControl === 'main' || (oncoming && intent === 'left');
      else if (control === 'main') has = oControl === 'main' && oncoming && intent === 'left';
      else if (control === 'equal') has = fromRight || (oncoming && intent === 'left');
      else if (control === 'light') has = oncoming && intent === 'left' && oStop && oStop.phase === myStop.phase;
      if (has) res.push({ edge: o, dir: odir });
    }
    return res;
  }

  // Dijkstra over junctions. Returns a node id list from `from` to `to`.
  shortestPath(from, to, avoidFirst = null) {
    const dist = new Map([[from, 0]]);
    const prev = new Map();
    const open = new Set([from]);
    while (open.size) {
      let u = null;
      for (const k of open) if (u === null || dist.get(k) < dist.get(u)) u = k;
      open.delete(u);
      if (u === to) break;
      for (const id of this.nodes.get(u).edges) {
        const e = this.edges.get(id);
        const dir = e.a === u ? 1 : -1;
        if (!this.allowed(e, dir)) continue;
        if (u === from && avoidFirst && id === avoidFirst) continue;
        const v = dir > 0 ? e.b : e.a;
        const nd = dist.get(u) + e.L;
        if (!dist.has(v) || nd < dist.get(v)) {
          dist.set(v, nd);
          prev.set(v, u);
          open.add(v);
        }
      }
    }
    if (!dist.has(to)) return null;
    const path = [to];
    while (path[0] !== from) path.unshift(prev.get(path[0]));
    return path;
  }

  edgeBetween(u, v) {
    for (const id of this.nodes.get(u).edges) {
      const e = this.edges.get(id);
      if ((e.a === u && e.b === v) || (e.b === u && e.a === v)) return e;
    }
    return null;
  }

  // Checks that a node path is drivable (used for exam routes and tests).
  validatePath(path) {
    for (let i = 0; i + 1 < path.length; i++) {
      const e = this.edgeBetween(path[i], path[i + 1]);
      if (!e) return `no edge ${path[i]}-${path[i + 1]}`;
      if (!this.allowed(e, e.a === path[i] ? 1 : -1)) return `edge ${e.id} not allowed ${path[i]}->${path[i + 1]}`;
      if (i > 0 && path[i + 1] === path[i - 1]) return `u-turn at ${path[i]}`;
    }
    return null;
  }

  bounds() {
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    for (const n of this.nodes.values()) {
      minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
      minZ = Math.min(minZ, n.z); maxZ = Math.max(maxZ, n.z);
    }
    return { minX, maxX, minZ, maxZ };
  }
}
