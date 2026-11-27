// The colouring book, standing behind the right snow drift (an image in the DOM, in front of this canvas).
// Hover (or tap / keyboard focus) → it hops out of the snow and an info card opens beside it.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

const hero = document.querySelector('.hero');
const canvas = document.querySelector('.book-scene');
const card = document.getElementById('bookCard');
const hit = document.querySelector('.book-hit');
const reduceMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

/* ───────── Placement ─────────
   The book is anchored to a point inside the right drift image (.drift-right), as fractions of that image,
   so it always stands behind the same part of the drift whatever the screen shape.
   fx/fy = where the book's snow line sits; size = visible book height relative to the drift's width. */
const PLACE = {
  wide:   { fx: 0.66, fy: 0.42, size: 0.27 },
  narrow: { fx: 0.66, fy: 0.42, size: 0.22 },
};
const drift = document.querySelector('.drift-right');

const BOOK_SCALE = 4;        // model is ~0.31 m tall → ~1.23 units
const BURY = 0.2;            // how far the base sits below the snow line
const HOP = 0.64;            // on hover it flies all the way out of the drift
const LEAN = -0.22;          // leaning back
const SNOW_Y = 0.02;         // anything of the book below this is clipped — it's hidden behind the drift image anyway

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

const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const BOOK_YAW = 0.22;

/* ───────── Colour grading, applied to the final (sRGB) pixel ─────────
   The same moonlit colour matrix the robot and the lynx get (#moonlit in index.html),
   plus a fine grain, so the book reads as part of the same night scene. */
const GRADE = {
  book: `
    vec3 c = gl_FragColor.rgb;
    vec3 moon = vec3(dot(c, vec3(0.40, 0.05, 0.03)), dot(c, vec3(0.03, 0.48, 0.08)), dot(c, vec3(0.07, 0.12, 0.80)) + 0.035);
    c = mix(c, moon * 1.45, 0.82);
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    c *= 0.965 + 0.07 * n;
    gl_FragColor.rgb = c;`,

};
function grade(material, kind) {
  material.onBeforeCompile = (sh) => {
    sh.fragmentShader = sh.fragmentShader.replace('#include <dithering_fragment>', `#include <dithering_fragment>\n{${GRADE[kind]}\n}`);
  };
  material.customProgramCacheKey = () => 'grade-' + kind;
  return material;
}

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
    // snow kicked up from where the book stood (they start hidden behind the drift and rise over its crest)
    const u = (Math.random() - 0.5) * 1.0, v = (Math.random() - 0.3) * 0.12;
    puffPos[i * 3] = u * cs + v * sn;
    puffPos[i * 3 + 1] = 0.05 + Math.random() * 0.1;
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

// everything of the book below the snow line is clipped — it is behind the drift image anyway
const clip = [new THREE.Plane(new THREE.Vector3(0, 1, 0), -SNOW_Y)];

// warm light from the garland lying on the drift in front of the book
const garlandLight = new THREE.PointLight(0xffa040, 0.9, 2.4, 1.2);
garlandLight.position.set(0, 0.15, 0.7);
scene.add(garlandLight);

/* ───────── The book ───────── */
const pivot = new THREE.Group();          // animated: hop + wobble
const base = { y: -BURY, rx: LEAN, ry: BOOK_YAW, rz: -0.07 };
pivot.position.y = base.y;
pivot.rotation.set(base.rx, base.ry, base.rz);
scene.add(pivot);

const bookMeshes = [];
let bookHolder = null;   // the book model itself — used to place the card
const bookSize = new THREE.Vector3();

new GLTFLoader().load('assets/book.glb', (gltf) => {
  const model = gltf.scene;
  const box = new THREE.Box3().setFromObject(model);
  box.getSize(bookSize);
  const c = box.getCenter(new THREE.Vector3());
  model.position.set(-c.x, -box.min.y, -c.z);       // centred, base on y = 0
  const holder = new THREE.Group();
  holder.scale.setScalar(BOOK_SCALE);
  holder.add(model);
  pivot.add(holder);
  bookHolder = holder;

  model.traverse((o) => {
    if (!o.isMesh) return;
    if (!o.geometry.attributes.normal) o.geometry.computeVertexNormals();
    const m = o.material;
    if (m.map) m.map.anisotropy = renderer.capabilities.getMaxAnisotropy();
    m.side = THREE.DoubleSide;
    m.clippingPlanes = clip;
    m.clipShadows = true;
    m.roughness = 0.9;          // matte, like painted paper — no plastic sheen
    grade(m, 'book');
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
  const d = drift.getBoundingClientRect();
  anchor.x = d.left - r.left + p.fx * d.width;
  anchor.y = d.top - r.top + p.fy * d.height;

  // visible book ≈ 0.95 units tall
  const targetPx = p.size * d.width;
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

// The cover's outline on screen (client px), for the hand-drawn lines in boil.js:
// [bottom-left, top-left, top-right, bottom-right] of the front face.
function publishOutline() {
  if (!bookHolder) return;
  const r = canvas.getBoundingClientRect();
  const hx = bookSize.x / 2, hz = bookSize.z / 2;
  // holder space: the model is centred, base on y = 0, cover facing +z
  window.__bookOutline = [[-hx, 0], [-hx, bookSize.y], [hx, bookSize.y], [hx, 0]].map(([x, y]) => {
    const v = bookHolder.localToWorld(new THREE.Vector3(x, y, hz)).project(camera);
    return [r.left + (v.x + 1) / 2 * r.width, r.top + (1 - v.y) / 2 * r.height];
  });
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
  pivot.rotation.x = base.rx + lift * 0.18;         // straightens up towards the viewer
  pivot.rotation.z = base.rz + Math.sin(now / 160) * hop.vel * 0.012;
  pivot.rotation.y = base.ry - lift * 0.08;
  glow.intensity += ((open ? 2.2 : 0) - glow.intensity) * Math.min(1, dt * 6);
  garlandLight.intensity = 0.85 + 0.2 * Math.sin(now / 230) * Math.sin(now / 410);

  stepPuff(dt);
  renderer.render(scene, camera);

  if (bookMeshes.length) {
    positionHit();
    publishOutline();
    if (open) positionCard();
  }
  requestAnimationFrame(tick);
}
requestAnimationFrame(tick);
