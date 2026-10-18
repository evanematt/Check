import * as THREE from 'three';

// Accumulates quads/triangles into one BufferGeometry so the whole city
// renders in a handful of draw calls. Winding is fixed up automatically from
// the requested normal, so callers can pass corners in any rotational order.
export class GeoBuilder {
  constructor() {
    this.pos = [];
    this.nor = [];
    this.uv = [];
    this.col = null;
    this.idx = [];
  }

  get vertexCount() { return this.pos.length / 3; }

  _orient(p, n) {
    const ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
    const bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
    const cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
    return cx * n[0] + cy * n[1] + cz * n[2] >= 0;
  }

  // p: 4 corners [x,y,z] in order around the quad; uv: 4 [u,v]; n: [nx,ny,nz]
  quad(p, uv, n, color) {
    const base = this.vertexCount;
    for (let i = 0; i < 4; i++) {
      this.pos.push(p[i][0], p[i][1], p[i][2]);
      this.nor.push(n[0], n[1], n[2]);
      this.uv.push(uv[i][0], uv[i][1]);
      if (color) (this.col ||= []).push(color[0], color[1], color[2]);
    }
    if (this._orient(p, n)) this.idx.push(base, base + 1, base + 2, base, base + 2, base + 3);
    else this.idx.push(base, base + 2, base + 1, base, base + 3, base + 2);
  }

  tri(p, uv, n, color) {
    const base = this.vertexCount;
    for (let i = 0; i < 3; i++) {
      this.pos.push(p[i][0], p[i][1], p[i][2]);
      this.nor.push(n[0], n[1], n[2]);
      this.uv.push(uv[i][0], uv[i][1]);
      if (color) (this.col ||= []).push(color[0], color[1], color[2]);
    }
    if (this._orient(p, n)) this.idx.push(base, base + 1, base + 2);
    else this.idx.push(base, base + 2, base + 1);
  }

  // Flat quad lying on the ground, centred at (cx, cz), long axis along dir.
  flat(cx, cz, dir, length, width, y, uvScale = 1) {
    const hx = dir.x * length / 2, hz = dir.z * length / 2;
    const wx = -dir.z * width / 2, wz = dir.x * width / 2;
    this.quad(
      [[cx - hx - wx, y, cz - hz - wz], [cx + hx - wx, y, cz + hz - wz], [cx + hx + wx, y, cz + hz + wz], [cx - hx + wx, y, cz - hz + wz]],
      [[0, 0], [length / uvScale, 0], [length / uvScale, width / uvScale], [0, width / uvScale]],
      [0, 1, 0],
    );
  }

  // Flat disc / polygon on the ground.
  disc(cx, cz, r, y, seg = 24, uvScale = 4) {
    for (let i = 0; i < seg; i++) {
      const a0 = (i / seg) * Math.PI * 2, a1 = ((i + 1) / seg) * Math.PI * 2;
      const p1 = [cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r];
      const p2 = [cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r];
      this.tri([[cx, y, cz], p1, p2], [[cx / uvScale, cz / uvScale], [p1[0] / uvScale, p1[2] / uvScale], [p2[0] / uvScale, p2[2] / uvScale]], [0, 1, 0]);
    }
  }

  // Axis-aligned-in-local-space box rotated by `rot` around y.
  box(cx, cy, cz, w, h, d, rot = 0, uvScale = 1, color, skipBottom = true) {
    const c = Math.cos(rot), s = Math.sin(rot);
    const P = (lx, ly, lz) => [cx + lx * c + lz * s, cy + ly, cz - lx * s + lz * c];
    const N = (lx, lz) => [lx * c + lz * s, 0, -lx * s + lz * c];
    const x = w / 2, y = h / 2, z = d / 2;
    const k = uvScale;
    // walls: +z, -z, +x, -x
    this.quad([P(-x, -y, z), P(x, -y, z), P(x, y, z), P(-x, y, z)], [[0, 0], [w / k, 0], [w / k, h / k], [0, h / k]], N(0, 1), color);
    this.quad([P(x, -y, -z), P(-x, -y, -z), P(-x, y, -z), P(x, y, -z)], [[0, 0], [w / k, 0], [w / k, h / k], [0, h / k]], N(0, -1), color);
    this.quad([P(x, -y, z), P(x, -y, -z), P(x, y, -z), P(x, y, z)], [[0, 0], [d / k, 0], [d / k, h / k], [0, h / k]], N(1, 0), color);
    this.quad([P(-x, -y, -z), P(-x, -y, z), P(-x, y, z), P(-x, y, -z)], [[0, 0], [d / k, 0], [d / k, h / k], [0, h / k]], N(-1, 0), color);
    this.quad([P(-x, y, z), P(x, y, z), P(x, y, -z), P(-x, y, -z)], [[0, 0], [0.01, 0], [0.01, 0.01], [0, 0.01]], [0, 1, 0], color);
    if (!skipBottom) this.quad([P(-x, -y, z), P(x, -y, z), P(x, -y, -z), P(-x, -y, -z)], [[0, 0], [1, 0], [1, 1], [0, 1]], [0, -1, 0], color);
  }

  build() {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(this.pos, 3));
    g.setAttribute('normal', new THREE.Float32BufferAttribute(this.nor, 3));
    g.setAttribute('uv', new THREE.Float32BufferAttribute(this.uv, 2));
    if (this.col) g.setAttribute('color', new THREE.Float32BufferAttribute(this.col, 3));
    g.setIndex(this.idx);
    g.computeBoundingSphere();
    return g;
  }
}

// Oriented box overlap test (separating axis theorem) on the ground plane.
export function obbOverlap(a, b) {
  const axes = [
    [Math.cos(a.rot), -Math.sin(a.rot)], [Math.sin(a.rot), Math.cos(a.rot)],
    [Math.cos(b.rot), -Math.sin(b.rot)], [Math.sin(b.rot), Math.cos(b.rot)],
  ];
  const corners = (o) => {
    const c = Math.cos(o.rot), s = Math.sin(o.rot);
    return [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([u, v]) => [o.cx + u * o.hx * c + v * o.hz * s, o.cz - u * o.hx * s + v * o.hz * c]);
  };
  const ca = corners(a), cb = corners(b);
  for (const [ax, az] of axes) {
    let amin = Infinity, amax = -Infinity, bmin = Infinity, bmax = -Infinity;
    for (const [x, z] of ca) { const p = x * ax + z * az; amin = Math.min(amin, p); amax = Math.max(amax, p); }
    for (const [x, z] of cb) { const p = x * ax + z * az; bmin = Math.min(bmin, p); bmax = Math.max(bmax, p); }
    if (amax < bmin || bmax < amin) return false;
  }
  return true;
}

export function obbCorners(o) {
  const c = Math.cos(o.rot), s = Math.sin(o.rot);
  return [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([u, v]) => ({ x: o.cx + u * o.hx * c + v * o.hz * s, z: o.cz - u * o.hx * s + v * o.hz * c }));
}
