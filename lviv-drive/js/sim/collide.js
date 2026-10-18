import { fwd } from '../core/math.js';

// Static collision world (buildings, poles, trees, bollards) in a spatial
// hash, plus helpers for car-vs-car and car-vs-pedestrian tests. The car is
// approximated by three circles along its length.

const CELL = 20;
const CAR_R = 0.92;
const CAR_OFFSETS = [1.35, 0, -1.35];

export class CollisionWorld {
  constructor(colliders) {
    this.grid = new Map();
    for (const c of colliders) this.insert(c);
  }

  insert(c) {
    const ext = c.type === 'circle' ? c.r : Math.hypot(c.hx, c.hz);
    const x = c.type === 'circle' ? c.x : c.cx, z = c.type === 'circle' ? c.z : c.cz;
    for (let gx = Math.floor((x - ext) / CELL); gx <= Math.floor((x + ext) / CELL); gx++) {
      for (let gz = Math.floor((z - ext) / CELL); gz <= Math.floor((z + ext) / CELL); gz++) {
        const k = `${gx},${gz}`;
        if (!this.grid.has(k)) this.grid.set(k, []);
        this.grid.get(k).push(c);
      }
    }
  }

  near(x, z) {
    const out = new Set();
    const gx = Math.floor(x / CELL), gz = Math.floor(z / CELL);
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) {
      const a = this.grid.get(`${gx + i},${gz + j}`);
      if (a) for (const c of a) out.add(c);
    }
    return out;
  }

  // Resolves penetration for the car; returns the strongest contact or null.
  resolveCar(car) {
    const f = fwd(car.h);
    let worst = null;
    for (const off of CAR_OFFSETS) {
      const px = car.x + f.x * off, pz = car.z + f.z * off;
      for (const c of this.near(px, pz)) {
        const hit = circleVs(px, pz, CAR_R, c);
        if (hit && (!worst || hit.depth > worst.depth)) worst = { ...hit, collider: c };
      }
    }
    if (worst) car.bump(worst.nx, worst.nz, worst.depth + 0.01);
    return worst;
  }
}

function circleVs(px, pz, r, c) {
  if (c.type === 'circle') {
    const dx = px - c.x, dz = pz - c.z;
    const d = Math.hypot(dx, dz);
    const pen = r + c.r - d;
    if (pen <= 0) return null;
    return { nx: dx / (d || 1), nz: dz / (d || 1), depth: pen };
  }
  // OBB: transform into local space (local x = (cos, -sin), local z = (sin, cos)).
  const co = Math.cos(c.rot), si = Math.sin(c.rot);
  const rx = px - c.cx, rz = pz - c.cz;
  const lx = rx * co - rz * si, lz = rx * si + rz * co;
  const qx = Math.max(-c.hx, Math.min(c.hx, lx)), qz = Math.max(-c.hz, Math.min(c.hz, lz));
  let dx = lx - qx, dz = lz - qz;
  let d = Math.hypot(dx, dz);
  let pen;
  if (d > 1e-6) {
    pen = r - d;
    if (pen <= 0) return null;
    dx /= d; dz /= d;
  } else {
    // Centre inside the box: push out through the nearest face.
    const ex = c.hx - Math.abs(lx), ez = c.hz - Math.abs(lz);
    if (ex < ez) { dx = Math.sign(lx) || 1; dz = 0; pen = ex + r; } else { dx = 0; dz = Math.sign(lz) || 1; pen = ez + r; }
  }
  // Back to world space.
  return { nx: dx * co + dz * si, nz: -dx * si + dz * co, depth: pen };
}

// Car (3 circles) vs another vehicle approximated by circles along its length.
export function carVsVehicle(car, o) {
  const f = fwd(car.h), g = fwd(o.h);
  const n = Math.max(2, Math.round(o.len / 1.6));
  const or = Math.min(1.3, o.wid / 2 + 0.05);
  for (const off of CAR_OFFSETS) {
    const px = car.x + f.x * off, pz = car.z + f.z * off;
    for (let i = 0; i < n; i++) {
      const t = -o.len / 2 + or * 0.7 + (i / (n - 1)) * (o.len - or * 1.4);
      const qx = o.x + g.x * t, qz = o.z + g.z * t;
      const dx = px - qx, dz = pz - qz, d = Math.hypot(dx, dz);
      const pen = CAR_R + or - d;
      if (pen > 0) return { nx: dx / (d || 1), nz: dz / (d || 1), depth: pen };
    }
  }
  return null;
}

export function carVsPoint(car, x, z, r) {
  const f = fwd(car.h);
  for (const off of CAR_OFFSETS) {
    const px = car.x + f.x * off, pz = car.z + f.z * off;
    if (Math.hypot(px - x, pz - z) < CAR_R + r) return true;
  }
  return false;
}
