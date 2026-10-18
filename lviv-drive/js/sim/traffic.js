import { makeCarMesh } from '../world/vehicles.js';
import { rng, headingOf, clamp, fwd } from '../core/math.js';

// AI traffic. Cars follow lane centrelines, turn on Bézier curves through
// junctions, obey signals, STOP / give-way signs and the right-hand rule,
// stop for pedestrians and keep distance with an IDM car-following model.

const COLORS = [0xb0b4b8, 0x1d2a44, 0xffffff, 0x8a1c1c, 0x2e5e3a, 0x444a50, 0xd8c690, 0x2b5aa8, 0x101010, 0x7a7f86];
const S0 = 2.5, T_HEAD = 1.3, A_MAX = 1.8, B_COMF = 3.0;

export class Traffic {
  constructor(net, scene, count = 20) {
    this.net = net;
    this.scene = scene;
    this.cars = [];
    this.r = rng(99);
    this.time = 0;
    this.enabled = true;
    for (let i = 0; i < count; i++) this.spawn(i < 2 ? 'bus' : i % 7 === 3 ? 'van' : 'car');
  }

  setVisible(v) {
    this.enabled = v;
    for (const c of this.cars) c.mesh.visible = v;
  }

  spawn(type, avoid = null) {
    const net = this.net, r = this.r;
    const edges = [...net.edges.values()].filter((e) => e.zone !== 'pedestrian' && (type !== 'bus' || e.zone === 'city'));
    for (let tries = 0; tries < 40; tries++) {
      const e = r.pick(edges);
      const dir = r() < 0.5 ? 1 : -1;
      if (!net.allowed(e, dir)) continue;
      const na = net.startNode(e, dir), nb = net.endNode(e, dir);
      const lo = na.R + 10, hi = e.L - nb.R - 14;
      if (hi <= lo) continue;
      const d = r.range(lo, hi);
      const s = dir > 0 ? d : e.L - d;
      const lane = r.int(0, e.lanes - 1);
      const p = net.lanePoint(e, dir, lane, s);
      if (avoid && Math.hypot(p.x - avoid.x, p.z - avoid.z) < 60) continue;
      if (this.cars.some((c) => Math.hypot(c.x - p.x, c.z - p.z) < 14)) continue;
      const mesh = makeCarMesh(type === 'bus' ? 0xc8102e : r.pick(COLORS), type);
      this.scene.add(mesh);
      const car = {
        id: this.cars.length, type, mesh, edge: e, dir, lane, s, x: p.x, z: p.z,
        h: headingOf(net.travelDir(e, dir)), v: 0, mode: 'lane', next: null, curve: null,
        len: mesh.userData.L, wid: mesh.userData.W, vFactor: r.range(0.82, 1.0),
        stopWait: 0, waitT: 0, blocked: 0, brake: false, intent: 'straight', crashT: 0,
      };
      this.cars.push(car);
      return car;
    }
    return null;
  }

  // Remove a car and respawn it away from `avoid` (e.g. after a crash).
  respawn(car, avoid) {
    this.scene.remove(car.mesh);
    this.cars.splice(this.cars.indexOf(car), 1);
    this.spawn(car.type, avoid);
  }

  // ---- route helpers -------------------------------------------------------
  pickNext(car) {
    const node = this.net.endNode(car.edge, car.dir);
    const exits = this.net.exits(node.id, car.edge.id).filter((x) => car.type !== 'bus' || x.edge.zone === 'city');
    const pool = exits.length ? exits : this.net.exits(node.id, null);
    if (!pool.length) return null;
    const inDir = this.net.travelDir(car.edge, car.dir);
    const weighted = pool.map((x) => {
      const kind = this.net.turnKind(inDir, this.net.travelDir(x.edge, x.dir));
      return { ...x, kind, w: kind === 'straight' ? 2.2 : 1 };
    });
    let t = this.r() * weighted.reduce((a, x) => a + x.w, 0);
    for (const x of weighted) { t -= x.w; if (t <= 0) return x; }
    return weighted[0];
  }

  buildCurve(car) {
    const net = this.net;
    const node = net.endNode(car.edge, car.dir);
    const nx = car.next;
    const lane = Math.min(car.lane, nx.edge.lanes - 1);
    const laneOut = nx.kind === 'right' ? 0 : nx.kind === 'left' ? nx.edge.lanes - 1 : lane;
    const s0 = net.sFromEnd(car.edge, car.dir, node.R);
    const sStart = nx.dir > 0 ? node.R : nx.edge.L - node.R;
    const p0 = net.lanePoint(car.edge, car.dir, car.lane, s0);
    const p2 = net.lanePoint(nx.edge, nx.dir, laneOut, sStart);
    const d0 = net.travelDir(car.edge, car.dir), d2 = net.travelDir(nx.edge, nx.dir);
    // Control point: intersection of the entry and exit tangents.
    const den = d0.x * d2.z - d0.z * d2.x;
    let c;
    if (Math.abs(den) < 0.15) c = { x: (p0.x + p2.x) / 2, z: (p0.z + p2.z) / 2 };
    else {
      const t = ((p2.x - p0.x) * d2.z - (p2.z - p0.z) * d2.x) / den;
      c = { x: p0.x + d0.x * t, z: p0.z + d0.z * t };
    }
    const pts = [];
    let length = 0;
    for (let i = 0; i <= 16; i++) {
      const u = i / 16, a = (1 - u) * (1 - u), b = 2 * u * (1 - u), cc = u * u;
      const p = { x: a * p0.x + b * c.x + cc * p2.x, z: a * p0.z + b * c.z + cc * p2.z };
      if (i) length += Math.hypot(p.x - pts[i - 1].x, p.z - pts[i - 1].z);
      p.acc = length;
      pts.push(p);
    }
    car.curve = { pts, length, u: 0, node: node.id, fromEdge: car.edge, fromDir: car.dir, laneOut, sStart };
  }

  // Vehicles (AI and player) that will reach `node` from (edge, dir) soon.
  arrivals(nodeId, edge, dir, player, ignore) {
    const out = [];
    const net = this.net;
    const consider = (v) => {
      if (v === ignore) return;
      if (v.mode === 'turn' && v.curve && v.curve.node === nodeId && v.curve.fromEdge === edge && v.curve.fromDir === dir) {
        out.push({ eta: 0, v });
      } else if (v.mode === 'lane' && v.edge === edge && v.dir === dir) {
        const node = net.endNode(edge, dir);
        if (node.id !== nodeId) return;
        const d = net.distToEnd(edge, v.s, dir) - node.R;
        if (d > 55) return;
        if (v.v < 0.5 && d > 3) return; // queued / waiting, not a threat yet
        out.push({ eta: Math.max(0, d) / Math.max(v.v, 1.5), v });
      }
    };
    for (const c of this.cars) consider(c);
    if (player) consider(player);
    return out;
  }

  // ---- per-frame update ----------------------------------------------------
  update(dt, t, ctx) {
    this.time = t;
    if (!this.enabled) return;
    const { player, peds } = ctx;
    for (const car of this.cars) this.step(car, dt, t, player, peds);
    for (const car of this.cars) this.render(car, t);
  }

  step(car, dt, t, player, peds) {
    const net = this.net;
    if (car.crashT > 0) { car.crashT -= dt; car.v = 0; return; }
    const edge = car.mode === 'lane' ? car.edge : car.next.edge;
    let v0 = (Math.max(edge.limit, 15) / 3.6) * car.vFactor;
    if (car.type === 'bus') v0 = Math.min(v0, 11);
    // Obstacles: list of distances ahead (front bumper) where speed must be 0.
    let gapStop = Infinity;
    const front = car.len / 2;

    if (car.mode === 'lane') {
      const toEnd = net.distToEnd(car.edge, car.s, car.dir);
      const node = net.endNode(car.edge, car.dir);
      if (!car.next && toEnd < 70) {
        car.next = this.pickNext(car);
        car.intent = car.next ? car.next.kind : 'straight';
      }
      // Slow for the turn.
      if (car.next && car.next.kind !== 'straight') {
        const dTurn = toEnd - node.R;
        v0 = Math.min(v0, Math.sqrt(36 + 2 * 2.2 * Math.max(0, dTurn)));
      }
      // Signals and signs.
      const ns = net.nextStop(car.edge, car.dir, car.s);
      if (ns) {
        const dist = ns.ahead - front;
        const st = ns.stop;
        let mustStop = false;
        if (st.control === 'light') {
          const sig = net.signalState(st.ctrl, st.phase, t).state;
          if (sig === 'red' || sig === 'redyellow') mustStop = true;
          else if (sig === 'yellow') mustStop = dist > (car.v * car.v) / (2 * 3.5) + 1;
          if (!mustStop && st.node && car.intent === 'left' && dist < 25) mustStop = this.blocked(car, st, player, dt);
        } else if (st.node) {
          if (st.control === 'stop') {
            if (dist < 3 && car.v < 0.3) car.stopWait += dt;
            if (car.stopWait < 1.2) mustStop = true;
          }
          if (!mustStop && st.control !== 'main' && dist < 25) mustStop = this.blocked(car, st, player, dt);
          else if (!mustStop && st.control === 'main' && car.intent === 'left' && dist < 25) mustStop = this.blocked(car, st, player, dt);
        }
        if (mustStop && dist > -1) gapStop = Math.min(gapStop, Math.max(0.1, dist));
      }
      // Pedestrians on crosswalks ahead (this edge, and the first one on the next edge).
      for (const cw of car.edge.crosswalks) {
        const d = (cw.s - car.s) * car.dir - cw.half - 1.2 - front;
        if (d > -cw.half && d < 35 && cw.peds > 0) gapStop = Math.min(gapStop, Math.max(0.1, d));
      }
      if (car.next && toEnd < 30) {
        const cwN = car.next.edge.crosswalks.find((c) => c.node === node.id);
        if (cwN && cwN.peds > 0) gapStop = Math.min(gapStop, Math.max(0.1, toEnd - node.R - front - 1));
      }
      // Don't enter a junction the player is still crossing.
      if (player && player.mode === 'turn' && player.curve && player.curve.node === node.id && toEnd < 25 && node.degree >= 3) {
        gapStop = Math.min(gapStop, Math.max(0.1, toEnd - node.R - front - 1));
      }
    } else {
      v0 = Math.min(v0, car.next.kind === 'straight' ? 9 : 5.5);
      const c = car.curve;
      const cwN = car.next.edge.crosswalks.find((x) => x.node === c.node);
      if (cwN && cwN.peds > 0) {
        const d = c.length - c.u + Math.abs(cwN.s - c.sStart) - cwN.half - 1 - front;
        if (d > -1) gapStop = Math.min(gapStop, Math.max(0.1, d));
      }
      // Keep the swept path of the turn clear of the player.
      if (player) {
        for (const p of c.pts) {
          if (p.acc < c.u || p.acc > c.u + front + 6) continue;
          if (Math.hypot(p.x - player.x, p.z - player.z) < car.wid / 2 + 2.2) { gapStop = Math.min(gapStop, Math.max(0.1, p.acc - c.u - front)); break; }
        }
      }
    }

    // Leader: nearest vehicle in a narrow cone ahead (other AI cars + player).
    const f = fwd(car.h);
    let lead = null, leadGap = Infinity;
    const check = (o, len, ov) => {
      const rx = o.x - car.x, rz = o.z - car.z;
      const along = rx * f.x + rz * f.z;
      if (along <= 0 || along > 45) return;
      const lat = Math.abs(rx * -f.z + rz * f.x);
      if (lat > (car.mode === 'turn' ? 2.4 : 1.9)) return;
      const gap = along - front - len / 2;
      if (gap < leadGap) { leadGap = gap; lead = ov; }
    };
    for (const o of this.cars) if (o !== car) check(o, o.len, o.v);
    if (player) check(player, 4.5, Math.abs(player.v));
    if (peds) for (const p of peds.list) if (p.state === 'cross') check(p, 0.6, 0);

    // IDM acceleration with the closest of leader and virtual stop.
    const freeAcc = A_MAX * (1 - (car.v / Math.max(v0, 0.1)) ** 4);
    let acc = freeAcc;
    const idm = (gap, dv) => {
      const sStar = S0 + car.v * T_HEAD + (car.v * dv) / (2 * Math.sqrt(A_MAX * B_COMF));
      return A_MAX * (1 - (car.v / Math.max(v0, 0.1)) ** 4 - (sStar / Math.max(gap, 0.1)) ** 2);
    };
    if (lead !== null) acc = Math.min(acc, idm(leadGap, car.v - lead));
    if (gapStop < Infinity) acc = Math.min(acc, idm(gapStop + S0 - 0.8, car.v));
    acc = clamp(acc, -8, A_MAX);
    car.brake = acc < -0.4;
    car.v = Math.max(0, car.v + acc * dt);
    if (car.v < 0.1 && (lead !== null && leadGap < 6)) car.blocked += dt; else car.blocked = 0;

    // Advance along lane or curve.
    const ds = car.v * dt;
    if (car.mode === 'lane') {
      car.s += ds * car.dir;
      const node = net.endNode(car.edge, car.dir);
      if (net.distToEnd(car.edge, car.s, car.dir) <= node.R) {
        if (!car.next) car.next = this.pickNext(car);
        if (!car.next) { car.v = 0; return; }
        this.buildCurve(car);
        car.mode = 'turn';
        car.stopWait = 0;
        car.waitT = 0;
      } else {
        const p = net.lanePoint(car.edge, car.dir, car.lane, car.s);
        car.x = p.x; car.z = p.z;
        car.h = headingOf(net.travelDir(car.edge, car.dir));
      }
    }
    if (car.mode === 'turn') {
      const c = car.curve;
      c.u += ds;
      if (c.u >= c.length) {
        car.edge = car.next.edge;
        car.dir = car.next.dir;
        car.lane = c.laneOut;
        car.s = c.sStart + (c.u - c.length) * car.dir;
        car.mode = 'lane';
        car.next = null;
        car.intent = 'straight';
        car.curve = null;
        const p = net.lanePoint(car.edge, car.dir, car.lane, car.s);
        car.x = p.x; car.z = p.z;
        car.h = headingOf(net.travelDir(car.edge, car.dir));
      } else {
        let i = 1;
        while (i < c.pts.length - 1 && c.pts[i].acc < c.u) i++;
        const a = c.pts[i - 1], b = c.pts[i];
        const k = (c.u - a.acc) / Math.max(1e-6, b.acc - a.acc);
        car.x = a.x + (b.x - a.x) * k;
        car.z = a.z + (b.z - a.z) * k;
        car.h = headingOf({ x: b.x - a.x, z: b.z - a.z });
      }
    }
  }

  // Must this car wait at stop `st` because someone with priority is coming?
  blocked(car, st, player, dt) {
    const arms = this.net.priorityArms(st.node, car.edge, car.intent);
    if (!arms.length) return false;
    car.waitT += dt;
    if (car.waitT > 7) return false; // patience: avoid gridlock on equal junctions
    for (const arm of arms) {
      for (const a of this.arrivals(st.node, arm.edge, arm.dir, player, car)) if (a.eta < 3.4) return true;
    }
    return false;
  }

  render(car, t) {
    const m = car.mesh;
    m.position.set(car.x, 0, car.z);
    m.rotation.y = car.h;
    const ud = m.userData;
    ud.tail.emissiveIntensity = car.brake || car.v < 0.1 ? 1.6 : 0.3;
    const blink = Math.floor(t * 2.6) % 2 === 0;
    const signalling = car.next && car.next.kind !== 'straight' && (car.mode === 'turn' || this.net.distToEnd(car.edge, car.s, car.dir) < 45);
    ud.indL.emissiveIntensity = signalling && car.next.kind === 'left' && blink ? 2.5 : 0;
    ud.indR.emissiveIntensity = signalling && car.next.kind === 'right' && blink ? 2.5 : 0;
  }
}
