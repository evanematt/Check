// Small math helpers shared by the simulation. Ground plane is (x, z):
// x = east, z = south, y = up. Heading h: forward = (-sin h, -cos h).

export const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
export const lerp = (a, b, t) => a + (b - a) * t;
export const KMH = 3.6;

export function fwd(h) {
  return { x: -Math.sin(h), z: -Math.cos(h) };
}

// Right-hand perpendicular of a ground direction.
export function rightOf(d) {
  return { x: -d.z, z: d.x };
}

export function dot(a, b) {
  return a.x * b.x + a.z * b.z;
}

export function cross(a, b) {
  return a.x * b.z - a.z * b.x;
}

export function len(x, z) {
  return Math.hypot(x, z);
}

export function wrapAngle(a) {
  while (a > Math.PI) a -= Math.PI * 2;
  while (a < -Math.PI) a += Math.PI * 2;
  return a;
}

// Heading that makes `fwd(h)` point along direction d.
export function headingOf(d) {
  return Math.atan2(-d.x, -d.z);
}

// Signed turn angle from direction a to direction b (positive = right turn).
export function turnAngle(a, b) {
  return Math.atan2(cross(a, b), dot(a, b));
}

export function segProject(px, pz, ax, az, bx, bz) {
  const dx = bx - ax, dz = bz - az;
  const L2 = dx * dx + dz * dz || 1;
  const t = clamp(((px - ax) * dx + (pz - az) * dz) / L2, 0, 1);
  const qx = ax + dx * t, qz = az + dz * t;
  return { t, x: qx, z: qz, d: Math.hypot(px - qx, pz - qz) };
}

// Deterministic PRNG so the city looks the same on every load.
export function rng(seed = 1) {
  let a = seed >>> 0;
  const f = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  f.range = (lo, hi) => lo + f() * (hi - lo);
  f.int = (lo, hi) => Math.floor(lo + f() * (hi - lo + 1));
  f.pick = (arr) => arr[Math.floor(f() * arr.length)];
  return f;
}

export function fmtTime(sec) {
  sec = Math.max(0, Math.floor(sec));
  const m = Math.floor(sec / 60), s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}
