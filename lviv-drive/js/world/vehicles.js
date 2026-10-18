import * as THREE from 'three';
import { mergeGeometries } from '../vendor/BufferGeometryUtils.js';

// Low-poly vehicle and pedestrian meshes. Local frame: forward = -z.

const shared = {};
function geo() {
  if (shared.wheel) return shared;
  shared.wheel = new THREE.CylinderGeometry(0.33, 0.33, 0.24, 14).rotateZ(Math.PI / 2);
  shared.lamp = new THREE.BoxGeometry(0.32, 0.14, 0.05);
  shared.glass = new THREE.MeshStandardMaterial({ color: 0x1b2633, roughness: 0.15, metalness: 0.6 });
  shared.tyre = new THREE.MeshStandardMaterial({ color: 0x151515, roughness: 0.9 });
  shared.head = new THREE.MeshStandardMaterial({ color: 0xeeeeee, emissive: 0xfff4d6, emissiveIntensity: 0.3 });
  shared.dark = new THREE.MeshStandardMaterial({ color: 0x222222, roughness: 0.8 });
  return shared;
}

export function makeCarMesh(color = 0x3366aa, type = 'car') {
  const s = geo();
  const g = new THREE.Group();
  const body = new THREE.MeshStandardMaterial({ color, roughness: 0.45, metalness: 0.35 });
  const tail = new THREE.MeshStandardMaterial({ color: 0x550000, emissive: 0xff1a00, emissiveIntensity: 0.25 });
  const indL = new THREE.MeshStandardMaterial({ color: 0x553300, emissive: 0xffa000, emissiveIntensity: 0 });
  const indR = indL.clone();
  let L = 4.3, W = 1.8, H = 0.75, cabinH = 0.6;
  if (type === 'bus') { L = 12; W = 2.5; H = 2.6; cabinH = 0.4; }
  if (type === 'van') { L = 5.2; W = 2.0; H = 1.6; cabinH = 0.35; }
  const lower = new THREE.Mesh(new THREE.BoxGeometry(W, H, L), body);
  lower.position.y = 0.35 + H / 2;
  lower.castShadow = true;
  g.add(lower);
  if (type === 'car') {
    const cabin = new THREE.Mesh(new THREE.BoxGeometry(W * 0.86, cabinH, L * 0.5), s.glass);
    cabin.position.set(0, 0.35 + H + cabinH / 2, 0.15);
    g.add(cabin);
  } else {
    const band = new THREE.Mesh(new THREE.BoxGeometry(W + 0.02, type === 'bus' ? 1.0 : 0.6, L * 0.92), s.glass);
    band.position.set(0, 0.35 + H * (type === 'bus' ? 0.62 : 0.7), 0.1);
    g.add(band);
    if (type === 'bus') {
      // Trolleybus poles, a familiar sight in Lviv.
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 5.5, 5), s.dark);
      pole.rotation.x = -1.2;
      pole.position.set(-0.4, 0.35 + H + 1.2, 2.5);
      const pole2 = pole.clone(); pole2.position.x = 0.4;
      g.add(pole, pole2);
    }
  }
  // Wheels, lamps and indicators are merged per material to keep draw calls low.
  const wz = L / 2 - 0.85;
  const at = (geo, x, y, z) => geo.clone().translate(x, y, z);
  const wheels = [[-W / 2 + 0.1, -wz], [W / 2 - 0.1, -wz], [-W / 2 + 0.1, wz], [W / 2 - 0.1, wz]].map(([x, z]) => at(s.wheel, x, 0.33, z));
  g.add(new THREE.Mesh(mergeGeometries(wheels), s.tyre));
  const front = -L / 2 - 0.01, back = L / 2 + 0.01, ly = 0.35 + H * 0.55;
  const xs = [-W / 2 + 0.3, W / 2 - 0.3];
  g.add(new THREE.Mesh(mergeGeometries(xs.map((x) => at(s.lamp, x, ly, front))), s.head));
  g.add(new THREE.Mesh(mergeGeometries(xs.map((x) => at(s.lamp, x, ly, back))), tail));
  const indG = new THREE.BoxGeometry(0.04, 0.1, 0.18);
  for (const [x, m] of [[-W / 2 - 0.01, indL], [W / 2 + 0.01, indR]]) {
    g.add(new THREE.Mesh(mergeGeometries([at(indG, x, ly, front + 0.25), at(indG, x, ly, back - 0.25)]), m));
  }
  g.userData = { tail, indL, indR, L, W };
  return g;
}

export function makePedMesh(seed) {
  const colors = [0x8a3b3b, 0x2f4f7f, 0x3f6f3f, 0x6b4f8f, 0xc28f2c, 0x444444, 0xb85c38, 0x1e6f78];
  const skin = [0xf1c27d, 0xe0ac69, 0xc68642, 0x8d5524][seed % 4];
  const g = new THREE.Group();
  const torsoM = new THREE.MeshStandardMaterial({ color: colors[seed % colors.length], roughness: 0.9 });
  const legM = new THREE.MeshStandardMaterial({ color: [0x222244, 0x333333, 0x4a3b2a][seed % 3], roughness: 0.9 });
  const skinM = new THREE.MeshStandardMaterial({ color: skin, roughness: 0.8 });
  const torso = new THREE.Mesh(new THREE.BoxGeometry(0.44, 0.62, 0.26), torsoM);
  torso.position.y = 1.18;
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.13, 10, 8), skinM);
  head.position.y = 1.62;
  const legL = new THREE.Mesh(new THREE.BoxGeometry(0.16, 0.86, 0.18), legM);
  legL.geometry.translate(0, -0.43, 0);
  legL.position.set(-0.11, 0.88, 0);
  const legR = legL.clone();
  legR.position.x = 0.11;
  g.add(torso, head, legL, legR);
  g.userData = { legL, legR };
  g.traverse((m) => { if (m.isMesh) m.castShadow = true; });
  return g;
}
