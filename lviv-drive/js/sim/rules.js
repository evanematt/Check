import { VIOLATIONS } from '../data/rules.js';
import { fwd, dot } from '../core/math.js';
import { PARK_W } from './network.js';

// Watches the player's driving and reports violations (with ПДР references)
// and positive events used by quests ("stopped_red", "yield_ped", ...).

const COOLDOWN = {
  SPEEDING_10: 10, SPEEDING_20: 10, SPEEDING_40: 10, SPEEDING_CRIT: 10,
  NO_SEATBELT: 30, NO_LIGHTS: 25, HIGH_BEAM_CITY: 25, HORN_CITY: 20, HANDBRAKE_DRIVE: 20,
  OPPOSITE_LANE: 12, SIDEWALK: 8, WRONG_WAY: 15, PEDESTRIAN_ZONE: 15, COLLISION_MINOR: 3, COLLISION_CAR: 4,
};
const FRONT = 2.2;

export class RulesEngine {
  constructor(net, { onViolation, onEvent }) {
    this.net = net;
    this.onViolation = onViolation;
    this.onEvent = onEvent;
    this.enabled = true;
    this.reset();
  }

  reset() {
    this.cool = new Map();
    this.timers = { speed: 0, opp: 0, belt: 0, lights: 0, high: 0, hb: 0, walk: 0, wrong: 0, horn: 0, still: 0 };
    this.prevFront = null;
    this.track = new Map(); // stop key -> {minKmh, stoppable, lastState, stoppedRed}
    this.entry = null;
    this.lastEdge = null;
    this.lastDir = 1;
    this.inNode = null;
    this.zoneDist = 0;
    this.parked = false;
    this.yielded = new WeakSet();
    this.insideCw = new Set();
    this.limit = 50;
    this.prevLimit = 50;
    this.limitEdge = null;
    this.limitDist = 0;
    this.now = 0;
  }

  report(code, extra = {}) {
    const def = VIOLATIONS[code];
    if (!def) return;
    const until = this.cool.get(code) || 0;
    if (this.now < until) return;
    this.cool.set(code, this.now + (COOLDOWN[code] ?? 6));
    this.onViolation({ code, ...def, t: this.now, ...extra });
  }

  event(name, data) { this.onEvent(name, data); }

  // g: { car, t, dt, env, traffic, peds, parking }
  update(g) {
    const { car, dt, env } = g;
    const net = this.net;
    this.now = g.t;
    if (!this.enabled) return;
    const f = fwd(car.h);
    const kmh = car.speedKmh;
    const loc = net.locate(car.x, car.z);
    const fx = car.x + f.x * FRONT, fz = car.z + f.z * FRONT;
    this.loc = loc;

    // Travel direction along the current edge (by heading; reversing ignored).
    let dir = this.lastDir;
    if (loc.edge) dir = dot(f, loc.edge.d) >= 0 ? 1 : -1;
    const moving = car.v > 0.3;

    // ---- speed ---------------------------------------------------------------
    const edgeForLimit = loc.node ? this.lastEdge : loc.edge;
    let limit = edgeForLimit ? (edgeForLimit.zone === 'pedestrian' ? 20 : edgeForLimit.limit || 50) : 50;
    // Inside a junction the zone has ended at its exit sign: use the highest
    // limit of the streets that meet there.
    if (loc.node) {
      for (const id of loc.node.edges) {
        const e = net.edges.get(id);
        if (e.zone !== 'pedestrian') limit = Math.max(limit, e.limit || 50);
      }
    }
    // A lower limit applies from its sign (a few metres into the street), so
    // give 15 m to finish braking after turning in.
    if (edgeForLimit && edgeForLimit !== this.limitEdge) {
      this.limitEdge = edgeForLimit;
      this.limitDist = 0;
      this.prevLimit = this.limit;
    }
    this.limitDist += Math.abs(car.v) * dt;
    if (limit < this.prevLimit && this.limitDist < 15) limit = this.prevLimit;
    this.limit = limit;
    const over = kmh - this.limit;
    this.timers.speed = over > 2 ? this.timers.speed + dt : 0;
    if (this.timers.speed > 1.5) {
      this.report(over > 40 ? 'SPEEDING_CRIT' : over > 20 ? 'SPEEDING_40' : over > 10 ? 'SPEEDING_20' : 'SPEEDING_10', { detail: `${Math.round(kmh)} км/год при обмеженні ${this.limit}` });
    }
    if (loc.edge && loc.edge.zone === 'residential' && !loc.node && moving) {
      if (over > 1) this.zoneDist = 0;
      else {
        this.zoneDist += car.v * dt;
        if (this.zoneDist > 150) { this.zoneDist = 0; this.event('zone_ok'); }
      }
    }

    // ---- stop lines, signals, crosswalks (front bumper) ----------------------
    if (loc.edge && !loc.node) {
      const e = loc.edge;
      const sF = (fx - e.ax) * e.d.x + (fz - e.az) * e.d.z;
      const key = `${e.id}|${dir}`;
      const prev = this.prevFront && this.prevFront.key === key ? this.prevFront.s : null;
      const ns = net.nextStop(e, dir, sF);
      if (ns && ns.ahead < 60) this.trackApproach(ns, car, g.t);
      if (prev !== null && moving) {
        for (const st of e.stops) {
          if (st.dir !== dir) continue;
          if ((prev - st.s) * dir < 0 && (sF - st.s) * dir >= 0) this.crossLine(st, car, g.t);
        }
      }
      this.prevFront = { key, s: sF };
    } else if (loc.node) {
      this.prevFront = null;
    }

    // ---- crosswalks (front bumper, works on edges and inside junctions) ----
    for (const cw of net.crosswalks) {
      const e = net.edges.get(cw.edge);
      if (cw.cx === undefined) { const c = net.point(e, cw.s, 0); cw.cx = c.x; cw.cz = c.z; }
      const dx = fx - cw.cx, dz = fz - cw.cz;
      if (dx * dx + dz * dz > 400) { this.insideCw.delete(cw); continue; }
      const s = (fx - e.ax) * e.d.x + (fz - e.az) * e.d.z;
      const lat = (fx - e.ax) * e.r.x + (fz - e.az) * e.r.z;
      const inside = Math.abs(s - cw.s) <= cw.half && lat >= cw.latMin && lat <= cw.latMax;
      if (inside && !this.insideCw.has(cw) && moving) this.crossWalk(cw, g.peds);
      if (inside) this.insideCw.add(cw); else this.insideCw.delete(cw);
      if (kmh < 2 && g.peds && cw.peds > 0) {
        const ahead = (cw.cx - car.x) * f.x + (cw.cz - car.z) * f.z;
        const side = Math.abs((cw.cx - car.x) * -f.z + (cw.cz - car.z) * f.x);
        if (ahead > 0 && ahead < 16 && side < 9) {
          for (const p of g.peds.list) {
            if (p.cw === cw && p.state === 'cross' && !this.yielded.has(p)) {
              this.yielded.add(p);
              this.event('yield_ped');
            }
          }
        }
      }
    }

    // ---- junction entry / exit -----------------------------------------------
    if (loc.node && loc.node.degree >= 2 && this.inNode !== loc.node) {
      this.inNode = loc.node;
      this.enterNode(loc.node, car, g);
    } else if (!loc.node && this.inNode) {
      const n = this.inNode;
      this.inNode = null;
      if (loc.edge) this.exitNode(n, loc.edge, dir, car);
    }
    if (loc.edge && !loc.node) { this.lastEdge = loc.edge; this.lastDir = dir; }

    // ---- lane discipline ------------------------------------------------------
    if (loc.edge && !loc.node && loc.onRoad && moving) {
      const e = loc.edge;
      if (!net.allowed(e, dir)) {
        this.timers.wrong += dt;
        if (this.timers.wrong > 0.8) this.report(e.zone === 'pedestrian' ? 'PEDESTRIAN_ZONE' : 'WRONG_WAY');
      } else this.timers.wrong = 0;
      const rightOfTravel = loc.lat * dir;
      if (!e.oneway && rightOfTravel < -0.4 && kmh > 3) {
        this.timers.opp += dt;
        if (this.timers.opp > 1.5) this.report('OPPOSITE_LANE');
      } else this.timers.opp = 0;
    } else { this.timers.opp = 0; this.timers.wrong = 0; }
    if (!loc.onRoad && kmh > 3 && g.inCity) {
      this.timers.walk += dt;
      if (this.timers.walk > 0.6) this.report('SIDEWALK');
    } else this.timers.walk = 0;

    // ---- driver & equipment -----------------------------------------------------
    this.timers.belt = kmh > 5 && !car.belt ? this.timers.belt + dt : 0;
    if (this.timers.belt > 3) this.report('NO_SEATBELT');
    this.timers.lights = kmh > 5 && env.needLights && car.lights < 2 ? this.timers.lights + dt : 0;
    if (this.timers.lights > 4) this.report('NO_LIGHTS', { detail: env.needLightsReason });
    this.timers.high = kmh > 5 && car.lights === 3 && g.inCity ? this.timers.high + dt : 0;
    if (this.timers.high > 3) this.report('HIGH_BEAM_CITY');
    this.timers.hb = kmh > 8 && car.handbrake ? this.timers.hb + dt : 0;
    if (this.timers.hb > 1.5) this.report('HANDBRAKE_DRIVE');
    this.timers.horn = car.horn ? this.timers.horn + dt : 0;
    if (this.timers.horn > 0.4 && g.inCity && !this.hazardAhead(car, g)) this.report('HORN_CITY');

    // ---- parking -----------------------------------------------------------------
    if (kmh < 1) this.timers.still += dt; else { this.timers.still = 0; if (kmh > 3) this.parked = false; }
    const secured = car.handbrake && (!car.engineOn || car.gearLabel === 'N' || car.gearLabel === 'P');
    if (!this.parked && secured && this.timers.still > 2) {
      this.parked = true;
      this.evaluateParking(car, loc, g);
    }
  }

  trackApproach(ns, car, t) {
    const st = ns.stop;
    let tr = this.track.get(st.key);
    if (!tr) { tr = { minKmh: Infinity, stoppable: false, lastState: null, stoppedRed: false }; this.track.set(st.key, tr); }
    if (ns.ahead < 12) tr.minKmh = Math.min(tr.minKmh, car.speedKmh);
    if (ns.ahead > 40) { tr.minKmh = Infinity; }
    if (st.control === 'light') {
      const state = this.net.signalState(st.ctrl, st.phase, t).state;
      if (state === 'yellow' && tr.lastState !== 'yellow') {
        tr.stoppable = (car.v * car.v) / (2 * 4) + 1 < ns.ahead;
      }
      if ((state === 'red' || state === 'redyellow') && car.speedKmh < 1.5 && ns.ahead < 10 && !tr.stoppedRed) {
        tr.stoppedRed = true;
        this.event('stopped_red');
      }
      tr.lastState = state;
    }
  }

  crossLine(st, car, t) {
    const tr = this.track.get(st.key) || { minKmh: car.speedKmh };
    if (st.control === 'light') {
      const state = this.net.signalState(st.ctrl, st.phase, t).state;
      if (state === 'red' || state === 'redyellow') this.report('RED_LIGHT');
      else if (state === 'yellow' && tr.stoppable) this.report('YELLOW_LIGHT');
      else if (tr.stoppedRed) this.event('green_go');
      tr.stoppedRed = false;
    } else if (st.control === 'stop') {
      if (tr.minKmh > 1.0) this.report('STOP_SIGN');
      else this.event('stop_ok');
    }
    tr.minKmh = Infinity;
  }

  crossWalk(cw, peds) {
    if (!peds || cw.peds <= 0) return;
    const crossing = peds.list.some((p) => p.cw === cw && p.state === 'cross');
    if (crossing) this.report('CROSSWALK');
  }

  enterNode(node, car, g) {
    const net = this.net;
    const from = this.lastEdge;
    const inVec = fwd(car.h);
    const ind = car.hazard ? null : car.indicator;
    this.entry = { node, from, inVec, ind, indAge: ind ? car.time - car.indicatorSince : 0, violated: false };
    if (!from || node.degree < 3 || !g.traffic || !g.traffic.enabled) return;
    if (net.endNode(from, this.lastDir).id !== node.id) return;
    // Conflicts that apply whatever the manoeuvre is are reported now; the
    // extra duty of a left turn (oncoming traffic) is judged at the exit,
    // from the turn actually made rather than from the indicator.
    const conflict = (arms) => arms.some((arm) => g.traffic.arrivals(node.id, arm.edge, arm.dir, null, null).some((a) => a.eta < 2.6));
    const base = net.priorityArms(node.id, from, 'straight');
    const leftOnly = net.priorityArms(node.id, from, 'left').filter((a) => !base.some((b) => b.edge === a.edge));
    if (car.v > 1 && conflict(base)) {
      this.report('NO_YIELD');
      this.entry.violated = true;
    } else if (car.v > 1 && conflict(leftOnly)) {
      this.entry.leftConflict = true;
    }
    const st = from.stops.find((s) => s.node === node.id && s.dir === this.lastDir);
    this.entry.control = st ? st.control : 'none';
  }

  exitNode(node, edge, dir, car) {
    const net = this.net;
    const en = this.entry;
    this.entry = null;
    if (!en || !en.from) return;
    const outVec = net.travelDir(edge, dir);
    const turn = net.turnKind(en.inVec, outVec);
    const turnInfo = { node: node.id, edge, dir, turn };
    if (!net.allowed(edge, dir)) {
      this.report(edge.zone === 'pedestrian' ? 'PEDESTRIAN_ZONE' : 'WRONG_WAY');
      en.violated = true;
    }
    if (turn === 'left' && en.leftConflict) { this.report('NO_YIELD_LEFT'); en.violated = true; }
    if (node.degree >= 3 && turn === 'straight' && en.ind && en.indAge > 3 && car.indicator === en.ind) {
      this.report('SIGNAL_NOT_CANCELLED');
    }
    if (node.degree >= 3 && turn !== 'straight') {
      if (!en.ind) { this.report('NO_SIGNAL'); en.violated = true; }
      else if (en.ind !== turn) { this.report('WRONG_SIGNAL'); en.violated = true; }
      else if (en.indAge >= 1.2) this.event('signal_ok');
      else { this.report('NO_SIGNAL', { detail: 'Сигнал увімкнено надто пізно' }); en.violated = true; }
    }
    if (en.control === 'equal' && !en.violated) this.event('equal_ok');
    if (en.control === 'giveway' && !en.violated) this.event('give_way_ok');
    this.event('node_exit', turnInfo);
  }

  hazardAhead(car, g) {
    const f = fwd(car.h);
    const close = (x, z) => {
      const rx = x - car.x, rz = z - car.z;
      const along = rx * f.x + rz * f.z;
      return along > 0 && along < 15 && Math.abs(rx * -f.z + rz * f.x) < 3;
    };
    if (g.traffic && g.traffic.enabled && g.traffic.cars.some((c) => close(c.x, c.z))) return true;
    if (g.peds && g.peds.list.some((p) => close(p.x, p.z))) return true;
    return false;
  }

  // Classifies where the car is parked; emits parked_ok or ILLEGAL_PARKING.
  inParkingBay(car, loc, parking) {
    if (!loc.edge || loc.node) return false;
    const e = loc.edge;
    const f = fwd(car.h);
    if (Math.abs(dot(f, e.d)) < 0.96) return false;
    for (const z of parking || []) {
      if (z.edge !== e.id) continue;
      if (loc.s < z.s0 - 0.5 || loc.s > z.s1 + 0.5) continue;
      const lo = z.sign > 0 ? e.carriage - 0.35 : -(e.carriage + PARK_W) - 0.35;
      const hi = z.sign > 0 ? e.carriage + PARK_W + 0.35 : -e.carriage + 0.35;
      if (loc.lat >= lo && loc.lat <= hi) return true;
    }
    return false;
  }

  evaluateParking(car, loc, g) {
    if (!g.inCity) return;
    if (this.inParkingBay(car, loc, g.parking)) { this.event('parked_ok'); return; }
    const net = this.net;
    if (loc.node) { this.report('ILLEGAL_PARKING', { detail: 'На перехресті' }); return; }
    if (!loc.edge) return;
    const e = loc.edge;
    for (const cw of e.crosswalks) {
      if (Math.abs(loc.s - cw.s) < 10 + cw.half) { this.report('ILLEGAL_PARKING', { detail: 'Ближче 10 м від пішохідного переходу' }); return; }
    }
    for (const id of [e.a, e.b]) {
      const n = net.nodes.get(id);
      if (n.degree >= 3 && Math.hypot(car.x - n.x, car.z - n.z) < n.R + 10) {
        this.report('ILLEGAL_PARKING', { detail: 'Ближче 10 м від перехрестя' });
        return;
      }
    }
    this.event('parked_roadside', { loc });
  }
}
