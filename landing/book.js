// The colouring book, half buried in the snow drift.
// Hover (or tap / keyboard focus) → it hops out of the snow and an info card opens beside it.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

const hero = document.querySelector('.hero');
const canvas = document.querySelector('.book-scene');
const card = document.getElementById('bookCard');
const hit = document.querySelector('.book-hit');
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

/* ───────── Placement ─────────
   The book is anchored to a point *on the painting* (fractions of the 1344×896 image),
   so it stays on the same snow ridge whatever the screen shape. */
const PLACE = {
  wide:   { fx: 0.70, fy: 0.80, height: 0.38 },  // height = book size relative to viewport height
  narrow: { fx: 0.37, fy: 0.88,  height: 0.36 },
};
const IMG_RATIO = 1344 / 896;

const BOOK_SCALE = 4;        // model is ~0.31 m tall → ~1.23 units
const BURY = 0.2;            // how far the base sits below the snow line
const HOP = 0.64;            // on hover it flies all the way out of the drift
const LEAN = -0.22;          // leaning back
const SLOT_Z = Math.tan(LEAN) * 0.42;   // where the leaning book meets the drift surface
const SNOW_Y = 0.02;         // anything of the book below this is clipped (it's "in the snow")

/* ───────── Renderer / scene ───────── */
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.localClippingEnabled = true;
renderer.shadowMap.enabled = false;   // the drift is painted flat; volume comes from the lights and baked shading
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(28, 1, 0.1, 50);
camera.position.set(0, 2.7, 6.5);
camera.lookAt(0, 0, 0);
const CAM_DIST = camera.position.length();

// Night light, built for volume rather than flat fill:
//  - a low cool sky fill, so the shadow side really falls off
//  - a soft spot "key" from the star side (upper left, in front) that grades across the cover
//  - a strong cold rim from the star behind, catching the book's top and left edges
//  - a faint bounce from the snow below
scene.add(new THREE.HemisphereLight(0x9aa8ff, 0x24339e, 0.55));

const key = new THREE.SpotLight(0xe8ebff, 4.2, 0, 0.42, 1, 0);
key.position.set(-2.4, 3.4, 3.2);
key.target.position.set(0, 0.55, 0);
scene.add(key, key.target);

const rim = new THREE.DirectionalLight(0xb7c3ff, 3.6);
rim.position.set(-3.2, 2.6, -3);
scene.add(rim);

const bounce = new THREE.DirectionalLight(0x5f74ff, 0.6);
bounce.position.set(0.5, -1, 2);
scene.add(bounce);

const glow = new THREE.PointLight(0x9aa6ff, 0, 3, 1.5);   // brightens while hovered
glow.position.set(0, 0.6, 0.9);
scene.add(glow);

/* ───────── Snow drift ───────── */
// cheap value noise for lumpy snow
function hash(x, y) { const n = Math.sin(x * 127.1 + y * 311.7) * 43758.5453; return n - Math.floor(n); }
function noise(x, y) {
  const xi = Math.floor(x), yi = Math.floor(y), xf = x - xi, yf = y - yi;
  const u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
  const a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
  return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
}
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };

const BOOK_YAW = 0.22;

// height of the snow drift at (x, z) — shared by the drift and the slot left behind
function driftHeight(x, z) {
  const cs = Math.cos(BOOK_YAW), sn = Math.sin(BOOK_YAW);
  const zz = z - SLOT_Z;
  const u = x * cs - zz * sn, v = x * sn + zz * cs;
  const du = Math.max(Math.abs(u) - 0.42, 0);
  const dv = Math.max(Math.abs(v) - 0.03, 0) * (v > 0 ? 0.8 : 1.2);    // snow piled more in front
  const d2 = du * du + dv * dv;
  const lump = 0.75 + 0.5 * noise(x * 2.6 + 3, z * 2.6);
  let h = 0.17 * lump * Math.exp(-d2 / 0.05);                           // drift hugging the book
  h += 0.07 * Math.exp(-(x * x) / 1.1 - (z * z) / 0.5);                  // gentle wide mound
  h += (noise(x * 5 + 7, z * 5) - 0.5) * 0.04 * Math.exp(-d2 / 0.3);     // lumps
  h += (noise(x * 14, z * 14) - 0.5) * 0.01;
  h += (noise(u * 7 + 11, 1.3) - 0.35) * 0.06 * Math.exp(-d2 / 0.012);  // clumps along the slot
  return h;
}

// Textures cut from the background painting itself, so the drift shares its brushwork:
// a dark brushy snow body, and the glittering lavender crest the painted ridges have.
const texLoader = new THREE.TextureLoader();
function paintTexture(url) {
  const t = texLoader.load(url);
  t.colorSpace = THREE.SRGBColorSpace;
  t.wrapS = t.wrapT = THREE.MirroredRepeatWrapping;
  t.anisotropy = 4;
  return t;
}
const bodyTex = paintTexture('assets/snow-body.webp');
const crestTex = paintTexture('assets/snow-crest.webp');

function buildDrift() {
  const geo = new THREE.PlaneGeometry(3.8, 2.6, 190, 130);
  geo.rotateX(-Math.PI / 2);
  const pos = geo.attributes.position;
  const uv = geo.attributes.uv;
  const fade = new Float32Array(pos.count);

  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), z = pos.getZ(i);
    pos.setY(i, driftHeight(x, z));
    // strokes run mostly horizontally, as in the painting
    uv.setXY(i, x / 1.7 + 0.3, z / 0.65 + 0.2);
    // organic, noisy footprint that melts into the painted snow
    const ang = Math.atan2(z, x);
    const edge = 0.8 + 0.35 * noise(Math.cos(ang) * 2 + 5, Math.sin(ang) * 2 + 5);
    const r = Math.hypot(x / 1.5, z / 0.95) / edge;
    fade[i] = 1 - smooth(0.5, 0.98, r);
  }
  geo.computeVertexNormals();

  // flat, painterly light from the star (upper left): no specular, just light and shadow sides
  const L = new THREE.Vector3(-0.55, 0.75, 0.45).normalize();
  const COLS = 191, ROWS = 131;   // PlaneGeometry(…, 190, 130) vertices per row / column
  const nrm = geo.attributes.normal, n = new THREE.Vector3();
  const body = new Float32Array(pos.count * 4);
  const crest = new Float32Array(pos.count * 4);
  for (let i = 0; i < pos.count; i++) {
    n.fromBufferAttribute(nrm, i);
    const lit = Math.max(0, n.dot(L));
    const px = pos.getX(i), pz = pos.getZ(i) - SLOT_Z;
    const u = px * Math.cos(BOOK_YAW) - pz * Math.sin(BOOK_YAW), v = px * Math.sin(BOOK_YAW) + pz * Math.cos(BOOK_YAW);
    const du = Math.max(Math.abs(u) - 0.44, 0);
    const crease = Math.exp(-(du * du + v * v) / 0.012);          // snow packed against the book
    const cast = v < 0 ? Math.exp(-(du * du) / 0.02) * Math.exp(-(v * v) / 0.09) : 0;   // book's shadow falls back, away from the key
    const shade = (0.4 + 0.85 * lit) * (1 - 0.35 * crease) * (1 - 0.3 * cast);
    body.set([shade, shade, shade * 1.03, fade[i]], i * 4);
    const h = pos.getY(i);
    // ridges = where the surface bulges above its neighbours (positive curvature)
    const col = i % COLS, row = (i / COLS) | 0;
    let ridge = 0;
    if (col > 2 && col < COLS - 3 && row > 2 && row < ROWS - 3) {
      const avg = (pos.getY(i - 3) + pos.getY(i + 3) + pos.getY(i - 3 * COLS) + pos.getY(i + 3 * COLS)) / 4;
      ridge = smooth(0.002, 0.014, h - avg);
    }
    const c = Math.max(ridge * (0.4 + 0.6 * lit), smooth(0.8, 0.97, lit) * smooth(0.1, 0.2, h) * 0.6)
      * (0.65 + 0.5 * noise(pos.getX(i) * 7, pos.getZ(i) * 7));
    crest.set([1, 1, 1, Math.min(1, c) * fade[i]], i * 4);
  }
  geo.setAttribute('color', new THREE.BufferAttribute(body, 4));

  const group = new THREE.Group();
  const bodyMesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
    map: bodyTex, vertexColors: true, transparent: true, depthWrite: true,
  }));
  bodyMesh.renderOrder = 2;
  group.add(bodyMesh);

  const crestGeo = geo.clone();
  crestGeo.setAttribute('color', new THREE.BufferAttribute(crest, 4));
  const cuv = crestGeo.attributes.uv;
  for (let i = 0; i < cuv.count; i++) cuv.setXY(i, pos.getX(i) / 0.35, pos.getZ(i) / 0.2);
  const crestMesh = new THREE.Mesh(crestGeo, new THREE.MeshBasicMaterial({
    map: crestTex, vertexColors: true, transparent: true, depthWrite: false,
    polygonOffset: true, polygonOffsetFactor: -1,
  }));
  crestMesh.renderOrder = 2.5;
  group.add(crestMesh);
  return group;
}
scene.add(buildDrift());

/* ───────── The slot the book leaves in the drift ───────── */
function slotTexture() {
  const w = 512, h = 96;
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d');
  g.filter = 'blur(5px)';
  // soft trodden rim
  g.fillStyle = 'rgba(20, 34, 140, 0.6)';
  g.beginPath(); g.roundRect(10, 8, w - 20, h - 16, 36); g.fill();
  // the dark opening: lit back wall at the top, deep shadow below
  const grad = g.createLinearGradient(0, 24, 0, h - 24);
  grad.addColorStop(0, '#7d8ff5');
  grad.addColorStop(0.35, '#2a3fb8');
  grad.addColorStop(0.6, '#0a1352');
  grad.addColorStop(1, '#050a2e');
  g.fillStyle = grad;
  g.beginPath(); g.roundRect(26, 24, w - 52, h - 48, 22); g.fill();
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function buildSlot() {
  const W = 1.1, D = 0.34;
  const geo = new THREE.PlaneGeometry(W, D, 64, 8);
  geo.rotateX(-Math.PI / 2);
  const cs = Math.cos(BOOK_YAW), sn = Math.sin(BOOK_YAW);
  const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const u = p.getX(i), v = p.getZ(i);
    const x = u * cs + v * sn, z = -u * sn + v * cs + SLOT_Z;
    p.setXYZ(i, x, driftHeight(x, z) + 0.006, z);
  }
  const mat = new THREE.MeshBasicMaterial({
    map: slotTexture(), transparent: true, opacity: 0, depthWrite: false,
    polygonOffset: true, polygonOffsetFactor: -2,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.renderOrder = 3;
  return mesh;
}
const slot = buildSlot();
scene.add(slot);

/* ───────── Snow puff (on hop) ───────── */
const PUFF = 70;
const puffGeo = new THREE.BufferGeometry();
const puffPos = new Float32Array(PUFF * 3);
const puffVel = new Float32Array(PUFF * 3);
const puffLife = new Float32Array(PUFF).fill(0);
const puffAlpha = new Float32Array(PUFF);
puffGeo.setAttribute('position', new THREE.BufferAttribute(puffPos, 3));
puffGeo.setAttribute('alpha', new THREE.BufferAttribute(puffAlpha, 1));
const puffMat = new THREE.ShaderMaterial({
  transparent: true,
  depthWrite: false,
  blending: THREE.AdditiveBlending,
  uniforms: { uScale: { value: 1 } },
  vertexShader: `
    attribute float alpha; varying float vA; uniform float uScale;
    void main(){ vA = alpha; vec4 mv = modelViewMatrix * vec4(position,1.);
      gl_PointSize = uScale * (0.4 + alpha) * 5. / -mv.z * 7.; gl_Position = projectionMatrix * mv; }`,
  fragmentShader: `
    varying float vA;
    void main(){ float d = length(gl_PointCoord - .5); if (d > .5) discard;
      gl_FragColor = vec4(vec3(.82,.86,1.), vA * .75 * smoothstep(.5,.0,d)); }`,
});
const puff = new THREE.Points(puffGeo, puffMat);
puff.renderOrder = 4;
scene.add(puff);

function burst(strength = 1) {
  const cs = Math.cos(BOOK_YAW), sn = Math.sin(BOOK_YAW);
  for (let i = 0; i < PUFF; i++) {
    if (Math.random() > strength) continue;
    const u = (Math.random() - 0.5) * 1.0, v = (Math.random() - 0.3) * 0.12;
    const x = u * cs + v * sn, z = -u * sn + v * cs + SLOT_Z;
    puffPos[i * 3] = x;
    puffPos[i * 3 + 1] = driftHeight(x, z) + 0.02;
    puffPos[i * 3 + 2] = z;
    puffVel[i * 3] = (Math.random() - 0.5) * 0.9 + Math.sign(u) * 0.3;
    puffVel[i * 3 + 1] = 0.8 + Math.random() * 1.2;
    puffVel[i * 3 + 2] = 0.2 + Math.random() * 0.6;
    puffLife[i] = 0.6 + Math.random() * 0.5;
  }
}

function stepPuff(dt) {
  for (let i = 0; i < PUFF; i++) {
    if (puffLife[i] <= 0) { puffAlpha[i] = 0; continue; }
    puffLife[i] -= dt;
    puffVel[i * 3 + 1] -= 3.2 * dt;
    for (let k = 0; k < 3; k++) { puffVel[i * 3 + k] *= 1 - 1.8 * dt; puffPos[i * 3 + k] += puffVel[i * 3 + k] * dt; }
    puffAlpha[i] = Math.max(0, Math.min(1, puffLife[i] * 1.6)) * 0.8;
  }
  puffGeo.attributes.position.needsUpdate = true;
  puffGeo.attributes.alpha.needsUpdate = true;
}

/* ───────── Back glow: a soft lavender halo that lifts the book off the painting ───────── */
function haloTexture() {
  const c = document.createElement('canvas');
  c.width = c.height = 256;
  const g = c.getContext('2d');
  const grad = g.createRadialGradient(128, 128, 0, 128, 128, 128);
  grad.addColorStop(0, 'rgba(205, 212, 255, 0.75)');
  grad.addColorStop(0.55, 'rgba(180, 190, 255, 0.6)');
  grad.addColorStop(0.75, 'rgba(140, 155, 255, 0.22)');
  grad.addColorStop(1, 'rgba(120, 130, 255, 0)');
  g.fillStyle = grad;
  g.fillRect(0, 0, 256, 256);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const backGlow = new THREE.Mesh(
  new THREE.PlaneGeometry(1.55, 1.95),   // hugs the book's outline
  new THREE.MeshBasicMaterial({ map: haloTexture(), transparent: true, depthWrite: false }),
);
backGlow.position.set(-0.03, 0.66, -0.12);
backGlow.renderOrder = 1;

/* ───────── The book ───────── */
const pivot = new THREE.Group();          // animated: hop + wobble
const base = { y: -BURY, rx: LEAN, ry: BOOK_YAW, rz: -0.07 };
pivot.position.y = base.y;
pivot.rotation.set(base.rx, base.ry, base.rz);
scene.add(pivot);

const clip = [new THREE.Plane(new THREE.Vector3(0, 1, 0), -SNOW_Y)];
const bookMeshes = [];
let bookHolder = null;   // the book itself, without the halo — used to place the card

new GLTFLoader().load('assets/book.glb', (gltf) => {
  const model = gltf.scene;
  const box = new THREE.Box3().setFromObject(model);
  const c = box.getCenter(new THREE.Vector3());
  model.position.set(-c.x, -box.min.y, -c.z);       // centred, base on y = 0
  const holder = new THREE.Group();
  holder.scale.setScalar(BOOK_SCALE);
  holder.add(model);
  pivot.add(holder);
  pivot.add(backGlow);
  bookHolder = holder;

  model.traverse((o) => {
    if (!o.isMesh) return;
    if (!o.geometry.attributes.normal) o.geometry.computeVertexNormals();
    const m = o.material;
    if (m.map) m.map.anisotropy = renderer.capabilities.getMaxAnisotropy();
    m.side = THREE.DoubleSide;
    m.clippingPlanes = clip;
    m.clipShadows = true;
    m.roughness = 0.62;
    o.castShadow = true;
    bookMeshes.push(o);
  });

  layout();
  canvas.classList.add('is-ready');
});

/* ───────── Layout: size + lens-shift so the drift sits on the painted ridge ───────── */
let vw = 1, vh = 1;
const anchor = { x: 0, y: 0 };

function layout() {
  const r = hero.getBoundingClientRect();
  vw = r.width; vh = r.height;
  renderer.setSize(vw, vh, false);
  camera.aspect = vw / vh;

  const narrow = vw / vh < 0.9 || vw <= 768;
  const p = narrow ? PLACE.narrow : PLACE.wide;
  const focusX = parseFloat(getComputedStyle(hero).getPropertyValue('--focus-x')) || 0.5;
  const sw = Math.max(vw, vh * IMG_RATIO), sh = Math.max(vh, vw / IMG_RATIO);
  anchor.x = (vw - sw) * focusX + p.fx * sw;
  anchor.y = (vh - sh) / 2 + p.fy * sh;

  // visible book ≈ 0.95 units tall; cap by width on narrow screens
  const targetPx = Math.min(p.height * vh, vw * (narrow ? 0.46 : 0.55));
  const halfTan = Math.tan(THREE.MathUtils.degToRad(camera.fov / 2));
  camera.zoom = (2 * targetPx / vh) * CAM_DIST * halfTan / 0.95;
  camera.updateProjectionMatrix();

  const e = camera.projectionMatrix.elements;
  e[8] = -((anchor.x / vw) * 2 - 1);
  e[9] = -(1 - (anchor.y / vh) * 2);
  camera.projectionMatrixInverse.copy(camera.projectionMatrix).invert();

  puffMat.uniforms.uScale.value = camera.zoom * renderer.getPixelRatio();
}
new ResizeObserver(layout).observe(hero);

/* ───────── Interaction ───────── */
const ray = new THREE.Raycaster();
const ndc = new THREE.Vector2();
let hovered = false;       // pointer is on the book
let open = false;
let closeTimer = 0;
let pinned = false;        // opened by tap or keyboard

function pickBook(clientX, clientY) {
  if (!bookMeshes.length) return false;
  const r = canvas.getBoundingClientRect();
  ndc.set(((clientX - r.left) / r.width) * 2 - 1, -((clientY - r.top) / r.height) * 2 + 1);
  ray.setFromCamera(ndc, camera);
  const hits = ray.intersectObjects(bookMeshes, false);
  return hits.some((h) => h.point.y > 0.18);   // ignore the part hidden in the snow
}

function setOpen(v) {
  if (v === open) return;
  open = v;
  clearTimeout(closeTimer);
  if (v) {
    card.hidden = false;
    positionCard();
    requestAnimationFrame(() => card.classList.add('is-open'));
    hop.target = HOP;
    hop.vel += reduceMotion ? 0 : 1.2;
    if (!reduceMotion) burst(1);
  } else {
    card.classList.remove('is-open');
    hop.target = 0;
    pinned = false;
    closeTimer = setTimeout(() => { if (!open) card.hidden = true; }, 400);
  }
  hit.setAttribute('aria-expanded', String(v));
}

function scheduleClose() {
  clearTimeout(closeTimer);
  closeTimer = setTimeout(() => { if (!hovered && !card.matches(':hover') && !pinned) setOpen(false); }, 280);
}

window.addEventListener('pointermove', (e) => {
  if (e.pointerType === 'touch') return;
  const now = pickBook(e.clientX, e.clientY);
  if (now !== hovered) {
    hovered = now;
    canvas.classList.toggle('is-hover', now);
    if (now) setOpen(true);
    else if (open) scheduleClose();
  }
});

card.addEventListener('pointerenter', () => clearTimeout(closeTimer));
card.addEventListener('pointerleave', (e) => { if (e.pointerType !== 'touch') scheduleClose(); });

// touch: tap the book to toggle, tap elsewhere to close
canvas.addEventListener('pointerdown', (e) => {
  if (e.pointerType !== 'touch' && e.pointerType !== 'pen') return;
  if (pickBook(e.clientX, e.clientY)) {
    if (open) setOpen(false); else { setOpen(true); pinned = true; }
  } else if (open) setOpen(false);
});

// keyboard
hit.addEventListener('click', () => { if (open) setOpen(false); else { setOpen(true); pinned = true; } });
hit.addEventListener('focus', () => { setOpen(true); pinned = true; });
document.addEventListener('focusin', (e) => {
  if (open && pinned && e.target !== hit && !card.contains(e.target)) setOpen(false);
});
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && open) { setOpen(false); hit.focus({ preventScroll: true }); } });

/* ───────── Card + focus-target positioning ───────── */
const tmpBox = new THREE.Box3();
const corners = Array.from({ length: 8 }, () => new THREE.Vector3());
const rect = { l: 0, t: 0, r: 0, b: 0 };

function bookScreenRect() {
  tmpBox.setFromObject(bookHolder || pivot);
  tmpBox.min.y = Math.max(tmpBox.min.y, 0.18);
  const { min, max } = tmpBox;
  let i = 0;
  for (const x of [min.x, max.x]) for (const y of [min.y, max.y]) for (const z of [min.z, max.z]) corners[i++].set(x, y, z);
  rect.l = rect.t = Infinity; rect.r = rect.b = -Infinity;
  for (const v of corners) {
    v.project(camera);
    const sx = (v.x + 1) / 2 * vw, sy = (1 - v.y) / 2 * vh;
    rect.l = Math.min(rect.l, sx); rect.r = Math.max(rect.r, sx);
    rect.t = Math.min(rect.t, sy); rect.b = Math.max(rect.b, sy);
  }
  return rect;
}

function positionCard() {
  const b = bookScreenRect();
  const cw = card.offsetWidth, ch = card.offsetHeight, gap = 28, pad = 16;
  let x, y, from = '-10px', origin = 'left center';
  if (b.r + gap + cw + pad <= vw) {                       // right of the book
    x = b.r + gap; y = (b.t + b.b) / 2 - ch / 2;
  } else if (b.l - gap - cw >= pad) {                     // left of the book
    x = b.l - gap - cw; y = (b.t + b.b) / 2 - ch / 2; from = '10px'; origin = 'right center';
  } else {                                                // above (phones)
    x = (b.l + b.r) / 2 - cw / 2; y = b.t - gap - ch; from = '0px'; origin = 'center bottom';
  }
  x = Math.min(Math.max(x, pad), vw - cw - pad);
  y = Math.min(Math.max(y, pad), vh - ch - pad);
  card.style.left = `${x}px`;
  card.style.top = `${y}px`;
  card.style.setProperty('--from-x', from);
  card.style.setProperty('--origin', origin);
}

function positionHit() {
  const b = bookScreenRect();
  Object.assign(hit.style, { left: `${b.l}px`, top: `${b.t}px`, width: `${b.r - b.l}px`, height: `${b.b - b.t}px` });
}

/* ───────── Animation ───────── */
const hop = { y: 0, vel: 0, target: 0 };
let nudgeAt = performance.now() + 3500;
let last = performance.now();

function tick(now) {
  const dt = Math.min((now - last) / 1000, 1 / 30);
  last = now;

  // spring towards target height (slightly under-damped → a little bounce)
  if (reduceMotion) { hop.y = hop.target; hop.vel = 0; }
  else {
    const k = 140, c = 13;
    hop.vel += (-(hop.y - hop.target) * k - hop.vel * c) * dt;
    hop.y += hop.vel * dt;
  }

  // every few seconds, an idle twitch to invite a hover
  if (!open && !reduceMotion && now > nudgeAt) {
    hop.vel += 1.1;
    burst(0.25);
    nudgeAt = now + 6000 + Math.random() * 3000;
  }

  const lift = hop.y / HOP;                         // 0 … ~1
  pivot.position.y = base.y + hop.y + Math.sin(now / 650) * 0.025 * Math.min(1, Math.max(0, lift));
  slot.material.opacity = smooth(0.45, 0.85, lift);
  pivot.rotation.x = base.rx + lift * 0.18;         // straightens up towards the viewer
  pivot.rotation.z = base.rz + Math.sin(now / 160) * hop.vel * 0.012;
  pivot.rotation.y = base.ry - lift * 0.08;
  glow.intensity += ((open ? 2.2 : 0) - glow.intensity) * Math.min(1, dt * 6);
  backGlow.material.opacity = 0.75 + 0.25 * Math.min(1, Math.max(0, lift));

  stepPuff(dt);
  renderer.render(scene, camera);

  if (bookMeshes.length) {
    positionHit();
    if (open) positionCard();
  }
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
