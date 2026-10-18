import * as THREE from 'three';
import { GeoBuilder } from '../world/geo.js';
import { makeCarMesh } from '../world/vehicles.js';
import { headingOf, fwd } from '../core/math.js';
import { obbCorners } from '../world/geo.js';

// Autodrome ("автодром") east of the city with five practical drills.
export const AUTODROME = { x: 950, z: 0 };
const O = AUTODROME;

const H_NORTH = 0; // heading for forward = -z
const H_WEST = headingOf({ x: -1, z: 0 });

export const DRILLS = [
  { id: 'slalom', title: 'Змійка', desc: 'Проїдьте між 6 конусами по черзі (перший конус — праворуч від вас), не збиваючи їх. Еталонний час — 25 с.', start: { x: O.x - 80, z: 125, h: H_NORTH } },
  { id: 'braking', title: 'Екстрене гальмування', desc: 'Розженіться до 50 км/год. Після команди «СТОП!» зупиніться якнайшвидше. Оцінюється гальмівний шлях від команди.', start: { x: O.x - 30, z: 140, h: H_NORTH } },
  { id: 'parallel', title: 'Паралельне паркування', desc: 'Заїдьте заднім ходом у місце між двома автомобілями праворуч. Наприкінці — стоянкове гальмо.', start: { x: O.x + 20, z: 62, h: H_NORTH } },
  { id: 'garage', title: 'Заїзд у «бокс» заднім ходом', desc: 'Поставте автомобіль у бокс праворуч заднім ходом, передньою частиною до виїзду. Наприкінці — стоянкове гальмо.', start: { x: O.x + 70, z: 70, h: H_NORTH } },
  { id: 'stopline', title: 'Зупинка біля стоп-лінії', desc: 'Розженіться понад 30 км/год і зупиніться так, щоб передній бампер був не далі 1 м перед стоп-лінією.', start: { x: O.x + 120, z: 140, h: H_NORTH } },
];

const SLALOM_Z = [85, 73, 61, 49, 37, 25];
const PARK = { x: O.x + 20 + 3.0, z: 25.4, hx: 1.2, hz: 3.25 }; // bay (axis along z)
const GARAGE = { x: O.x + 78.75, z: 20, hx: 2.75, hz: 1.45 };
const STOP_Z = 40;
const BRAKE_CUE = [100, 75];

export class Autodrome {
  constructor(scene, T) {
    this.scene = scene;
    this.group = new THREE.Group();
    this.colliders = [];
    this.cones = [];
    scene.add(this.group);
    const asphalt = new THREE.MeshStandardMaterial({ map: T.asphalt, roughness: 0.9 });
    const paint = new THREE.MeshStandardMaterial({ color: 0xf2f2ee, roughness: 0.7, polygonOffset: true, polygonOffsetFactor: -2 });
    const yellow = new THREE.MeshStandardMaterial({ color: 0xf5c400, roughness: 0.7, polygonOffset: true, polygonOffsetFactor: -2 });
    const wall = new THREE.MeshStandardMaterial({ color: 0xc8c2b6, roughness: 0.9 });
    const g = new GeoBuilder();
    g.flat(O.x, 20, { x: 1, z: 0 }, 260, 300, 0.02, 4);
    this.group.add(new THREE.Mesh(g.build(), asphalt));
    const m = new GeoBuilder(), y = new GeoBuilder();
    // Lane edges for each drill corridor.
    for (const lx of [O.x - 80, O.x - 30, O.x + 120]) {
      for (const s of [-1, 1]) m.flat(lx + s * 3, 60, { x: 0, z: 1 }, 200, 0.15, 0.04);
    }
    // Braking: cue zone markers, finish lines.
    m.flat(O.x - 80, 8, { x: 1, z: 0 }, 6, 0.5, 0.04); // slalom finish
    y.flat(O.x + 120, STOP_Z, { x: 1, z: 0 }, 6, 0.45, 0.045); // stop line
    // Parallel parking bay and kerb.
    for (const dz of [-PARK.hz, PARK.hz]) y.flat(PARK.x, PARK.z + dz, { x: 1, z: 0 }, 2.4, 0.12, 0.045);
    y.flat(PARK.x - PARK.hx, PARK.z, { x: 0, z: 1 }, PARK.hz * 2, 0.12, 0.045);
    const kerb = new GeoBuilder();
    kerb.box(O.x + 20 + 4.55, 0.08, 20, 0.3, 0.16, 120, 0, 1);
    this.colliders.push({ type: 'obb', cx: O.x + 20 + 4.55, cz: 20, hx: 0.15, hz: 60, rot: 0 });
    // Parked cars around the bay.
    for (const [cz, col] of [[PARK.z - PARK.hz - 2.25, 0x2b5aa8], [PARK.z + PARK.hz + 2.25, 0xd8c690]]) {
      const car = makeCarMesh(col, 'car');
      car.position.set(PARK.x, 0, cz);
      this.group.add(car);
      this.colliders.push({ type: 'obb', cx: PARK.x, cz, hx: 0.9, hz: 2.15, rot: 0, tag: 'parked' });
    }
    // Garage box (three walls).
    const gw = [
      { cx: GARAGE.x, cz: GARAGE.z - GARAGE.hz - 0.1, hx: GARAGE.hx, hz: 0.1 },
      { cx: GARAGE.x, cz: GARAGE.z + GARAGE.hz + 0.1, hx: GARAGE.hx, hz: 0.1 },
      { cx: GARAGE.x + GARAGE.hx + 0.1, cz: GARAGE.z, hx: 0.1, hz: GARAGE.hz + 0.2 },
    ];
    for (const w of gw) {
      kerb.box(w.cx, 1.0, w.cz, w.hx * 2, 2.0, w.hz * 2, 0, 1);
      this.colliders.push({ type: 'obb', ...w, rot: 0, tag: 'wall' });
    }
    y.flat(GARAGE.x - GARAGE.hx, GARAGE.z, { x: 0, z: 1 }, GARAGE.hz * 2, 0.15, 0.045);
    this.group.add(new THREE.Mesh(kerb.build(), wall));
    this.group.add(new THREE.Mesh(m.build(), paint));
    this.group.add(new THREE.Mesh(y.build(), yellow));
    // Slalom cones.
    const coneG = new THREE.ConeGeometry(0.22, 0.7, 12);
    const coneM = new THREE.MeshStandardMaterial({ color: 0xff6a00, roughness: 0.6 });
    for (const cz of SLALOM_Z) {
      const c = new THREE.Mesh(coneG, coneM);
      c.position.set(O.x - 80, 0.35, cz);
      c.castShadow = true;
      this.group.add(c);
      this.cones.push({ mesh: c, x: O.x - 80, z: cz, home: [O.x - 80, cz], knocked: false, vx: 0, vz: 0, spin: 0 });
    }
    // Sign board.
    const board = new THREE.Mesh(new THREE.BoxGeometry(12, 3, 0.3), new THREE.MeshStandardMaterial({ color: 0x1f5fbf }));
    board.position.set(O.x, 5, 175);
    this.group.add(board);
  }

  resetCones() {
    for (const c of this.cones) {
      c.knocked = false; c.vx = c.vz = c.spin = 0;
      [c.x, c.z] = c.home;
      c.mesh.position.set(c.x, 0.35, c.z);
      c.mesh.rotation.set(0, 0, 0);
    }
  }

  // Returns number of newly knocked cones this frame.
  updateCones(dt, car) {
    let hits = 0;
    const f = fwd(car.h);
    for (const c of this.cones) {
      if (!c.knocked) {
        for (const off of [1.35, 0, -1.35]) {
          const px = car.x + f.x * off, pz = car.z + f.z * off;
          if (Math.hypot(px - c.x, pz - c.z) < 0.92 + 0.22) {
            c.knocked = true; hits++;
            c.vx = f.x * car.v * 0.8 + (c.x - px) * 2; c.vz = f.z * car.v * 0.8 + (c.z - pz) * 2; c.spin = 6;
            break;
          }
        }
      } else if (c.vx || c.vz) {
        c.x += c.vx * dt; c.z += c.vz * dt;
        c.vx *= 0.95; c.vz *= 0.95;
        c.mesh.rotation.x = Math.min(Math.PI / 2, c.mesh.rotation.x + c.spin * dt);
        c.mesh.position.set(c.x, 0.25, c.z);
        if (Math.hypot(c.vx, c.vz) < 0.1) c.vx = c.vz = 0;
      }
    }
    return hits;
  }
}

function carCorners(car) {
  const rot = car.h; // car local x = right, local z = backward
  return obbCorners({ cx: car.x, cz: car.z, hx: 0.9, hz: 2.25, rot });
}

function inside(p, box, tol = 0.2) {
  return Math.abs(p.x - box.x) <= box.hx + tol && Math.abs(p.z - box.z) <= box.hz + tol;
}

function angleDiff(a, b) {
  let d = Math.abs(a - b) % (Math.PI * 2);
  if (d > Math.PI) d = Math.PI * 2 - d;
  return d;
}

// Drill runtime: one instance per attempt.
export class DrillRun {
  constructor(def, autodrome) {
    this.def = def;
    this.ad = autodrome;
    this.t = 0;
    this.done = false;
    this.result = null;
    this.collisions = 0;
    this.cones = 0;
    this.phase = 'go';
    this.info = '';
    this.gateSide = [];
    this.prevZ = def.start.z;
    this.maxKmh = 0;
    this.stillT = 0;
    this.cueZ = BRAKE_CUE[0] - Math.random() * (BRAKE_CUE[0] - BRAKE_CUE[1]);
    autodrome.resetCones();
  }

  onCollision() { this.collisions++; }

  finish(score, passed, lines) {
    this.done = true;
    score = Math.max(0, Math.min(100, Math.round(score)));
    this.result = { score, passed: passed && score >= 70, lines };
  }

  update(dt, car) {
    if (this.done) return;
    this.t += dt;
    const kmh = car.speedKmh;
    this.maxKmh = Math.max(this.maxKmh, kmh);
    this.stillT = kmh < 0.5 ? this.stillT + dt : 0;
    const id = this.def.id;
    if (id === 'slalom') {
      this.cones += this.ad.updateCones(dt, car);
      for (let i = 0; i < SLALOM_Z.length; i++) {
        const cz = SLALOM_Z[i];
        if (this.prevZ > cz && car.z <= cz && this.gateSide[i] === undefined) this.gateSide[i] = car.x > O.x - 80 ? 'right' : 'left';
      }
      this.prevZ = car.z;
      this.info = `Час ${this.t.toFixed(1)} с · збито конусів: ${this.cones}`;
      if (car.z < 8) {
        let missed = 0;
        SLALOM_Z.forEach((_, i) => {
          const want = i % 2 === 0 ? 'left' : 'right'; // first cone stays to the driver's right
          if (this.gateSide[i] !== want) missed++;
        });
        const score = 100 - this.cones * 15 - missed * 20 - Math.max(0, this.t - 25) - this.collisions * 15;
        this.finish(score, missed === 0, [`Час: ${this.t.toFixed(1)} с (еталон 25 с)`, `Збито конусів: ${this.cones}`, `Пропущено воріт: ${missed}`]);
      } else if (this.t > 90) this.finish(0, false, ['Час вичерпано']);
    } else if (id === 'braking') {
      if (this.phase === 'go') {
        this.info = `Розженіться до 50 км/год · ${Math.round(kmh)} км/год`;
        if (car.z <= this.cueZ) {
          if (kmh < 45) { this.finish(0, false, [`Швидкість на момент команди ${Math.round(kmh)} км/год — потрібно не менше 45`]); return; }
          this.phase = 'brake';
          this.cueSpeed = kmh;
          this.cueAt = { x: car.x, z: car.z };
          this.cue = true;
        }
      } else if (this.phase === 'brake') {
        this.info = 'СТОП!';
        if (kmh < 0.3) {
          const d = Math.hypot(car.x - this.cueAt.x, car.z - this.cueAt.z);
          const score = d <= 20 ? 100 : 100 - (d - 20) * 4;
          this.finish(score, d <= 27, [`Швидкість на момент команди: ${Math.round(this.cueSpeed)} км/год`, `Зупинний шлях (з реакцією): ${d.toFixed(1)} м`, 'Норматив гри: до 27 м з 50 км/год']);
        }
      }
    } else if (id === 'parallel' || id === 'garage') {
      const box = id === 'parallel' ? PARK : GARAGE;
      const corners = carCorners(car);
      const inCount = corners.filter((p) => inside(p, box)).length;
      this.info = `Час ${this.t.toFixed(0)} с · у межах місця: ${inCount}/4 кути · зіткнень: ${this.collisions}`;
      if (this.stillT > 1 && car.handbrake && this.t > 3) {
        const wantH = id === 'parallel' ? H_NORTH : H_WEST;
        let ang = angleDiff(car.h, wantH);
        if (id === 'parallel') ang = Math.min(ang, angleDiff(car.h, wantH + Math.PI));
        const deg = (ang * 180) / Math.PI;
        const score = 100 - (4 - inCount) * 18 - Math.max(0, deg - 3) * 3 - this.collisions * 20 - Math.max(0, this.t - 90) * 0.5;
        this.finish(inCount === 4 ? score : Math.min(score, 40), inCount === 4 && deg < 10 && this.collisions === 0,
          [`Кути в межах місця: ${inCount}/4`, `Відхилення від осі: ${deg.toFixed(1)}°`, `Зіткнень: ${this.collisions}`, `Час: ${this.t.toFixed(0)} с`]);
      } else if (this.t > 240) this.finish(0, false, ['Час вичерпано']);
    } else if (id === 'stopline') {
      this.info = `Макс. швидкість ${Math.round(this.maxKmh)} км/год (потрібно ≥ 30)`;
      if (this.stillT > 1 && this.maxKmh >= 30) {
        const frontZ = car.z + fwd(car.h).z * 2.25;
        const d = frontZ - STOP_Z; // >0 = before the line
        const score = d < 0 ? 70 - Math.abs(d) * 35 : d <= 1 ? 100 : 100 - (d - 1) * 12;
        this.finish(score, d >= 0 && d <= 1.5, [`Відстань від бампера до лінії: ${d >= 0 ? d.toFixed(2) + ' м перед лінією' : Math.abs(d).toFixed(2) + ' м за лінією'}`]);
      } else if (this.t > 60) this.finish(0, false, ['Час вичерпано']);
    }
  }

  get liveInfo() { return this.info; }
}
