import { makePedMesh } from '../world/vehicles.js';
import { rng, headingOf } from '../core/math.js';

// Pedestrians that use crosswalks. They wait on the kerb and step out only
// when it is safe for approaching drivers to stop (unregulated crossings) or
// when their signal is green (regulated crossings). This makes every
// "didn't give way" error the driver's fault, as in a real exam.

const COMFORT_DECEL = 4.0;

export class Pedestrians {
  constructor(net, scene, max = 22) {
    this.net = net;
    this.scene = scene;
    this.max = max;
    this.list = [];
    this.r = rng(5);
    this.spawnT = 0;
    this.enabled = true;
    this.seed = 0;
  }

  setVisible(v) {
    this.enabled = v;
    if (!v) { for (const p of this.list) this.scene.remove(p.mesh); this.list = []; }
  }

  update(dt, t, ctx) {
    for (const cw of this.net.crosswalks) cw.peds = 0;
    if (!this.enabled) return;
    const { player, traffic } = ctx;
    this.spawnT -= dt;
    if (this.spawnT <= 0) {
      this.spawnT = 0.6;
      if (this.list.length < this.max) this.trySpawn(player);
    }
    for (const p of this.list) this.step(p, dt, t, player, traffic);
    this.list = this.list.filter((p) => {
      if (p.dead) this.scene.remove(p.mesh);
      return !p.dead;
    });
    for (const p of this.list) if (p.state === 'cross') p.cw.peds++;
  }

  trySpawn(player) {
    const net = this.net, r = this.r;
    const f = { x: -Math.sin(player.h), z: -Math.cos(player.h) };
    const cands = [];
    for (const cw of net.crosswalks) {
      const e = net.edges.get(cw.edge);
      const c = net.point(e, cw.s, 0);
      const dx = c.x - player.x, dz = c.z - player.z;
      const d = Math.hypot(dx, dz);
      if (d < 22 || d > 160) continue;
      const ahead = (dx * f.x + dz * f.z) / d;
      const waiting = this.list.filter((p) => p.cw === cw && p.state !== 'leave').length;
      if (waiting >= 3) continue;
      cands.push({ cw, e, w: ahead > 0.4 && d < 110 ? 6 : 1 });
    }
    if (!cands.length) return;
    let pick = r() * cands.reduce((a, c) => a + c.w, 0);
    let chosen = cands[0];
    for (const c of cands) { pick -= c.w; if (pick <= 0) { chosen = c; break; } }
    if (r() > 0.55) return;
    const { cw, e } = chosen;
    const side = r() < 0.5 ? 1 : -1;
    const kerb = (sd) => sd * ((sd > 0 ? e.hwR : e.hwL) + 0.9);
    const mesh = makePedMesh(this.seed++);
    this.scene.add(mesh);
    const along = r.range(-cw.half * 0.6, cw.half * 0.6);
    const p = {
      cw, e, side, along, lat: kerb(side) + side * 3, latKerb: kerb(side), latTarget: kerb(-side),
      state: 'approach', wait: r.range(0.8, 2.2), speed: r.range(1.15, 1.55), mesh, x: 0, z: 0, h: 0,
      walkPhase: r() * 6, leaveDir: this.awayFromJunction(cw, e, r), leaveLeft: 9, dead: false, downT: 0,
    };
    this.place(p);
    this.list.push(p);
  }

  // After crossing, walk along the pavement away from the junction corner.
  awayFromJunction(cw, e, r) {
    if (!cw.node) return r() < 0.5 ? 1 : -1;
    return cw.node === e.a ? 1 : -1;
  }

  place(p) {
    const q = this.net.point(p.e, p.cw.s + p.along, p.lat);
    p.x = q.x; p.z = q.z;
  }

  // Is it safe to step onto an unregulated crosswalk right now?
  safeToCross(cw, e, traffic, player) {
    const vehicles = traffic && traffic.enabled ? [...traffic.cars] : [];
    if (player) vehicles.push(player);
    for (const v of vehicles) {
      const speed = Math.abs(v.v);
      if (v.mode === 'turn') {
        const into = v.next ? v.next.edge === e && v.curve && cw.node === v.curve.node : true;
        const c = this.net.point(e, cw.s, 0);
        if (into && speed > 1.5 && Math.hypot(v.x - c.x, v.z - c.z) < 22) return false;
        continue;
      }
      if (v.edge !== e) {
        // About to turn into this street through the crosswalk's junction?
        if (!cw.node || !v.edge || speed < 1) continue;
        const end = this.net.endNode(v.edge, v.dir);
        if (end.id !== cw.node) continue;
        const turningIn = v.next ? v.next.edge === e : true;
        if (turningIn && this.net.distToEnd(v.edge, v.s, v.dir) < 18 + speed * 2.5) return false;
        continue;
      }
      const d = (cw.s - v.s) * v.dir - cw.half - 2.2;
      if (d < -cw.half * 2 - 4.5) continue; // already past
      if (d < 1.5 && speed > 0.3) return false; // on or right at the crossing
      if (d > 75) continue;
      if (speed < 0.4) continue; // stopped and waiting for us
      const need = (speed * speed) / (2 * COMFORT_DECEL) + speed * 0.7 + 4;
      if (d < need) return false;
    }
    return true;
  }

  step(p, dt, t, player, traffic) {
    const net = this.net;
    if (p.state === 'down') {
      p.downT += dt;
      p.mesh.rotation.z = Math.min(Math.PI / 2, p.downT * 4);
      if (p.downT > 5) p.dead = true;
      return;
    }
    let moving = false;
    if (p.state === 'approach') {
      const step = Math.min(Math.abs(p.latKerb - p.lat), p.speed * dt);
      p.lat += Math.sign(p.latKerb - p.lat) * step;
      moving = true;
      if (Math.abs(p.latKerb - p.lat) < 0.05) p.state = 'wait';
    } else if (p.state === 'wait') {
      p.wait -= dt;
      if (p.wait <= 0) {
        const width = Math.abs(p.latTarget - p.latKerb);
        let go;
        if (p.cw.ctrl) {
          const sig = net.pedestrianGo(p.cw, t);
          go = sig.go && sig.remaining > (width / p.speed) * 0.75;
        } else go = this.safeToCross(p.cw, p.e, traffic, player);
        if (go) p.state = 'cross';
      }
    } else if (p.state === 'cross') {
      const step = Math.min(Math.abs(p.latTarget - p.lat), p.speed * dt);
      p.lat += Math.sign(p.latTarget - p.lat) * step;
      moving = true;
      if (Math.abs(p.latTarget - p.lat) < 0.05) p.state = 'leave';
    } else if (p.state === 'leave') {
      p.along += p.leaveDir * p.speed * dt;
      p.leaveLeft -= p.speed * dt;
      moving = true;
      if (p.leaveLeft <= 0) p.dead = true;
    }
    const prevX = p.x, prevZ = p.z;
    this.place(p);
    if (moving) {
      const dx = p.x - prevX, dz = p.z - prevZ;
      if (dx * dx + dz * dz > 1e-6) p.h = headingOf({ x: dx, z: dz });
      p.walkPhase += dt * p.speed * 5.5;
    }
    const m = p.mesh;
    m.position.set(p.x, 0, p.z);
    m.rotation.y = p.h;
    const sw = moving ? Math.sin(p.walkPhase) * 0.45 : 0;
    m.userData.legL.rotation.x = sw;
    m.userData.legR.rotation.x = -sw;
  }

  knockDown(p) {
    if (p.state === 'down') return;
    p.state = 'down';
    p.downT = 0;
  }
}
