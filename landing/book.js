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
  wide:   { fx: 0.585, fy: 0.735, height: 0.34 },  // height = visible book height / viewport height
  narrow: { fx: 0.34,  fy: 0.80,  height: 0.30 },
};
const IMG_RATIO = 1344 / 896;

const BOOK_SCALE = 4;        // model is ~0.31 m tall → ~1.23 units
const BURY = 0.26;           // how far the base sits below the snow line
const HOP = 0.2;             // how high it jumps on hover
const SNOW_Y = 0.02;         // anything of the book below this is clipped (it's "in the snow")

/* ───────── Renderer / scene ───────── */
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.localClippingEnabled = true;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(28, 1, 0.1, 50);
camera.position.set(0, 2.1, 6.7);
camera.lookAt(0, 0, 0);
const CAM_DIST = camera.position.length();

// Night light: cool sky fill, a soft key from the viewer's side, a rim from the star (upper left, behind).
scene.add(new THREE.HemisphereLight(0xb4bfff, 0x1d2a9a, 1.15));

const key = new THREE.DirectionalLight(0xdfe3ff, 1.35);
key.position.set(-1.6, 2.6, 4);
key.castShadow = true;
key.shadow.mapSize.set(1024, 1024);
key.shadow.camera.left = -1.6;
key.shadow.camera.right = 1.6;
key.shadow.camera.top = 1.6;
key.shadow.camera.bottom = -1.6;
key.shadow.radius = 6;
key.shadow.bias = -0.0015;
scene.add(key);

const rim = new THREE.DirectionalLight(0x9fb0ff, 2.2);
rim.position.set(-3, 3, -3);
scene.add(rim);

const glow = new THREE.PointLight(0x7f8cff, 0, 3, 1.5);   // brightens while hovered
glow.position.set(0, 0.6, 0.9);
scene.add(glow);

/* ───────── Snow drift ───────── */
function strokeTexture() {
  const s = 1024;
  const c = document.createElement('canvas');
  c.width = c.height = s;
  const g = c.getContext('2d');
  g.fillStyle = '#c3cbff';
  g.fillRect(0, 0, s, s);
  const tones = ['#a6b1ff', '#d9deff', '#8c99f5', '#eef0ff', '#b4bdff'];
  g.lineCap = 'round';
  for (let i = 0; i < 2600; i++) {
    const x = Math.random() * s, y = Math.random() * s;
    const len = 20 + Math.random() * 70;
    const a = -0.25 + Math.random() * 0.35;          // mostly horizontal, like the painting
    g.strokeStyle = tones[(Math.random() * tones.length) | 0];
    g.globalAlpha = 0.25 + Math.random() * 0.35;
    g.lineWidth = 2 + Math.random() * 6;
    g.beginPath();
    g.moveTo(x, y);
    g.quadraticCurveTo(x + len * 0.5, y + Math.sin(a) * len * 0.5 - 6, x + Math.cos(a) * len, y + Math.sin(a) * len);
    g.stroke();
  }
  g.globalAlpha = 1;
  for (let i = 0; i < 1400; i++) {                    // glitter specks
    g.fillStyle = Math.random() < 0.7 ? '#ffffff' : '#dfe4ff';
    const r = Math.random() * 1.8 + 0.4;
    g.beginPath();
    g.arc(Math.random() * s, Math.random() * s, r, 0, Math.PI * 2);
    g.fill();
  }
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

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
const snowTex = strokeTexture();

function buildDrift() {
  const geo = new THREE.PlaneGeometry(3.6, 2.6, 180, 130);
  geo.rotateX(-Math.PI / 2);
  const pos = geo.attributes.position;
  const colors = new Float32Array(pos.count * 4);
  const lo = new THREE.Color('#2443c4'), hi = new THREE.Color('#b9c3ff'), tmp = new THREE.Color();
  const cs = Math.cos(BOOK_YAW), sn = Math.sin(BOOK_YAW);

  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), z = pos.getZ(i);
    // distance to the book's footprint (rotated with the book)
    const u = x * cs - z * sn, v = x * sn + z * cs;
    const du = Math.max(Math.abs(u) - 0.4, 0);
    const dv = Math.max(Math.abs(v) - 0.04, 0) * (v > 0 ? 0.8 : 1.2);    // snow piled more in front
    const d2 = du * du + dv * dv;
    const lump = 0.75 + 0.5 * noise(x * 2.6 + 3, z * 2.6);
    let h = 0.17 * lump * Math.exp(-d2 / 0.045);                          // drift hugging the book
    h += 0.06 * Math.exp(-(x * x) / 0.9 - (z * z) / 0.45);                 // gentle wide mound
    h += (noise(x * 5 + 7, z * 5) - 0.5) * 0.04 * Math.exp(-d2 / 0.3);     // lumps
    h += (noise(x * 14, z * 14) - 0.5) * 0.01;
    h += (noise(u * 7 + 11, 1.3) - 0.35) * 0.07 * Math.exp(-d2 / 0.012); // clumps right against the cover
    pos.setY(i, h);

    // organic, noisy footprint that melts into the painted snow
    const ang = Math.atan2(z, x);
    const edge = 0.8 + 0.35 * noise(Math.cos(ang) * 2 + 5, Math.sin(ang) * 2 + 5);
    const r = Math.hypot(x / 1.5, z / 0.95) / edge;
    const alpha = 1 - smooth(0.5, 0.98, r);
    tmp.copy(lo).lerp(hi, Math.min(1, 0.12 + h * 3.2));
    colors.set([tmp.r, tmp.g, tmp.b, alpha], i * 4);
  }
  geo.setAttribute('color', new THREE.BufferAttribute(colors, 4));
  geo.computeVertexNormals();

  const mat = new THREE.MeshStandardMaterial({
    map: snowTex,
    vertexColors: true,
    transparent: true,
    roughness: 1,
    metalness: 0,
    depthWrite: true,
  });
  const mesh = new THREE.Mesh(geo, mat);
  mesh.receiveShadow = true;
  mesh.renderOrder = 2;
  return mesh;
}
scene.add(buildDrift());

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
puff.renderOrder = 3;
scene.add(puff);

function burst(strength = 1) {
  const cs = Math.cos(BOOK_YAW), sn = Math.sin(BOOK_YAW);
  for (let i = 0; i < PUFF; i++) {
    if (Math.random() > strength) continue;
    const u = (Math.random() - 0.5) * 1.1, v = 0.08 + Math.random() * 0.12;
    puffPos[i * 3] = u * cs + v * sn;
    puffPos[i * 3 + 1] = 0.2 + Math.random() * 0.1;
    puffPos[i * 3 + 2] = -u * sn + v * cs;
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

/* ───────── The book ───────── */
const pivot = new THREE.Group();          // animated: hop + wobble
const base = { y: -BURY, rx: -0.3, ry: BOOK_YAW, rz: -0.07 };
pivot.position.y = base.y;
pivot.rotation.set(base.rx, base.ry, base.rz);
scene.add(pivot);

const clip = [new THREE.Plane(new THREE.Vector3(0, 1, 0), -SNOW_Y)];
const bookMeshes = [];

new GLTFLoader().load('assets/book.glb', (gltf) => {
  const model = gltf.scene;
  const box = new THREE.Box3().setFromObject(model);
  const c = box.getCenter(new THREE.Vector3());
  model.position.set(-c.x, -box.min.y, -c.z);       // centred, base on y = 0
  const holder = new THREE.Group();
  holder.scale.setScalar(BOOK_SCALE);
  holder.add(model);
  pivot.add(holder);

  model.traverse((o) => {
    if (!o.isMesh) return;
    if (!o.geometry.attributes.normal) o.geometry.computeVertexNormals();
    const m = o.material;
    if (m.map) m.map.anisotropy = renderer.capabilities.getMaxAnisotropy();
    m.side = THREE.DoubleSide;
    m.clippingPlanes = clip;
    m.clipShadows = true;
    m.roughness = 0.7;
    o.castShadow = true;
    bookMeshes.push(o);
  });

  pivot.add(snowCap(BOOK_SCALE * (box.max.x - box.min.x), BOOK_SCALE * (box.max.y - box.min.y)));

  layout();
  canvas.classList.add('is-ready');
});

// a lumpy line of snow resting on the book's top edge
function snowCap(width, height) {
  const geo = new THREE.SphereGeometry(1, 64, 20);
  const p = geo.attributes.position;
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), y = p.getY(i), z = p.getZ(i);
    const t = Math.min(1, Math.abs(x));
    const profile = Math.pow(1 - t * t, 0.35) * (0.45 + 0.9 * noise(x * 5 + 2, 0.5));
    const bump = 1 + 0.25 * (noise(x * 18, y * 4 + z * 4) - 0.5);
    p.setXYZ(i,
      x * width * 0.48,
      (y > 0 ? y * 0.085 * profile * bump : y * 0.015),
      z * (0.035 + 0.035 * profile) * bump);
  }
  geo.computeVertexNormals();
  const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: 0xc4ccff, map: snowTex, roughness: 1 }));
  mesh.position.y = height + 0.004;
  mesh.castShadow = true;
  return mesh;
}

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
  const targetPx = Math.min(p.height * vh, vw * 0.62);
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
    hop.vel += reduceMotion ? 0 : 2.6;
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
  tmpBox.setFromObject(pivot);
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
  pivot.position.y = base.y + hop.y;
  pivot.rotation.x = base.rx + lift * 0.12;         // straightens up towards the viewer
  pivot.rotation.z = base.rz + Math.sin(now / 160) * hop.vel * 0.012;
  pivot.rotation.y = base.ry - lift * 0.08;
  glow.intensity += ((open ? 2.2 : 0) - glow.intensity) * Math.min(1, dt * 6);

  stepPuff(dt);
  renderer.render(scene, camera);

  if (bookMeshes.length) {
    positionHit();
    if (open) positionCard();
  }
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
