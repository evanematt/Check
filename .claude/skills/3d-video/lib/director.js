// director.js — tiny toolkit for deterministic, time-driven 3D videos with Three.js.
//
// Everything is a pure function of time t (seconds). Never use requestAnimationFrame,
// Date.now(), performance.now() or Math.random() for anything visible: the renderer
// seeks to arbitrary frames, so state must be recomputable from t alone.
//
// import * as D from '/__skill/lib/director.js';

import * as THREE from 'three';
export { THREE };

// ---------------------------------------------------------------- easing / math
export const ease = {
  linear: p => p,
  inQuad: p => p * p,
  outQuad: p => 1 - (1 - p) * (1 - p),
  inOutQuad: p => (p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2),
  inCubic: p => p * p * p,
  outCubic: p => 1 - Math.pow(1 - p, 3),
  inOutCubic: p => (p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2),
  outQuart: p => 1 - Math.pow(1 - p, 4),
  inOutQuart: p => (p < 0.5 ? 8 * p ** 4 : 1 - Math.pow(-2 * p + 2, 4) / 2),
  outExpo: p => (p === 1 ? 1 : 1 - Math.pow(2, -10 * p)),
  inOutExpo: p => p === 0 ? 0 : p === 1 ? 1 : p < 0.5 ? Math.pow(2, 20 * p - 10) / 2 : (2 - Math.pow(2, -20 * p + 10)) / 2,
  outBack: p => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2); },
  outElastic: p => p === 0 ? 0 : p === 1 ? 1 : Math.pow(2, -10 * p) * Math.sin((p * 10 - 0.75) * (2 * Math.PI) / 3) + 1,
};
export const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
export const lerp = (a, b, p) => a + (b - a) * p;
/** Eased 0→1 progress of t within [start, start+dur]. */
export const prog = (t, start, dur, e = ease.inOutCubic) => e(clamp((t - start) / dur));
/** Value going a→b over [start, start+dur]. */
export const tween = (t, start, dur, a, b, e) => lerp(a, b, prog(t, start, dur, e));
/** 0→1→0 envelope: fades in over `fin`, holds, fades out over `fout`, ending at start+dur. */
export const envelope = (t, start, dur, fin = 0.4, fout = 0.4) =>
  Math.min(prog(t, start, fin, ease.outCubic), 1 - prog(t, start + dur - fout, fout, ease.inCubic));
/** Seeded RNG (mulberry32) — use instead of Math.random. */
export function rng(seed = 1) {
  return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let x = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x; return ((x ^ (x >>> 14)) >>> 0) / 4294967296; };
}
/** Keyframe track: keys = [[time, value], ...]; value may be number or array. */
export function track(keys, e = ease.inOutCubic) {
  return t => {
    if (t <= keys[0][0]) return keys[0][1];
    for (let i = 0; i < keys.length - 1; i++) {
      const [ta, va] = keys[i], [tb, vb] = keys[i + 1];
      if (t <= tb) { const p = e((t - ta) / (tb - ta));
        return Array.isArray(va) ? va.map((v, j) => lerp(v, vb[j], p)) : lerp(va, vb, p); }
    }
    return keys[keys.length - 1][1];
  };
}

// ---------------------------------------------------------------- stage
/** Size from ?w=&h= (renderer passes --size), else defaults. */
export function frameSize(defW = 1920, defH = 1080) {
  const q = new URLSearchParams(location.search);
  return { width: Number(q.get('w')) || defW, height: Number(q.get('h')) || defH };
}
/** True when the frame is taller than wide — use it to re-layout for Reels/TikTok/Shorts. */
export const isVertical = ({ width, height }) => height > width;

export function createStage({ width, height, background = '#0b0d17', fov = 35, toneMapping = true, shadows = true } = {}) {
  const canvas = document.createElement('canvas');
  canvas.id = 'stage';
  document.body.prepend(canvas);
  Object.assign(document.body.style, { margin: 0, overflow: 'hidden', width: width + 'px', height: height + 'px', background });
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true, alpha: false });
  renderer.setPixelRatio(1);
  renderer.setSize(width, height);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  if (toneMapping) { renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.05; }
  if (shadows) { renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap; }
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(background);
  const camera = new THREE.PerspectiveCamera(fov, width / height, 0.1, 200);
  camera.position.set(0, 2, 10);
  return { renderer, scene, camera, canvas, width, height };
}

/** Soft studio lighting: hemisphere + key (shadowed) + rim + fill. Returns the lights. */
export function studioLights(scene, { key = '#ffffff', rim = '#7aa2ff', fill = '#ffb38a', intensity = 1 } = {}) {
  const hemi = new THREE.HemisphereLight('#dfe8ff', '#1a1c2b', 0.6 * intensity);
  const keyL = new THREE.DirectionalLight(key, 2.4 * intensity);
  keyL.position.set(5, 8, 6); keyL.castShadow = true;
  keyL.shadow.mapSize.set(2048, 2048); keyL.shadow.camera.left = keyL.shadow.camera.bottom = -10;
  keyL.shadow.camera.right = keyL.shadow.camera.top = 10; keyL.shadow.bias = -0.0002; keyL.shadow.normalBias = 0.03; keyL.shadow.radius = 6;
  const rimL = new THREE.DirectionalLight(rim, 2.0 * intensity); rimL.position.set(-6, 4, -6);
  const fillL = new THREE.PointLight(fill, 30 * intensity, 30); fillL.position.set(-5, 1, 5);
  scene.add(hemi, keyL, rimL, fillL);
  return { hemi, key: keyL, rim: rimL, fill: fillL };
}

/** Shadow-catcher floor that fades into the background, plus optional grid. */
export function floor(scene, { y = 0, color = '#0b0d17', grid = true, gridColor = '#2a3050', size = 60 } = {}) {
  const g = new THREE.Group();
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(size, size), new THREE.ShadowMaterial({ opacity: 0.35 }));
  shadow.rotation.x = -Math.PI / 2; shadow.position.y = y; shadow.receiveShadow = true; g.add(shadow);
  if (grid) { const gh = new THREE.GridHelper(size, size, gridColor, gridColor); gh.position.y = y + 0.001;
    gh.material.transparent = true; gh.material.opacity = 0.35; g.add(gh); }
  scene.add(g);
  scene.fog = new THREE.Fog(color, 14, 40);
  return g;
}

// ---------------------------------------------------------------- materials & objects
export const mat = {
  matte: (color = '#e8eaf2', o = {}) => new THREE.MeshStandardMaterial({ color, roughness: 0.75, metalness: 0, ...o }),
  plastic: (color = '#5b7cff', o = {}) => new THREE.MeshPhysicalMaterial({ color, roughness: 0.35, clearcoat: 0.6, clearcoatRoughness: 0.2, ...o }),
  metal: (color = '#c9ccd6', o = {}) => new THREE.MeshStandardMaterial({ color, roughness: 0.25, metalness: 1, ...o }),
  glass: (color = '#ffffff', o = {}) => new THREE.MeshPhysicalMaterial({ color, roughness: 0.08, transmission: 1, thickness: 0.6, ior: 1.4, transparent: true, ...o }),
  glow: (color = '#7aa2ff', o = {}) => new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 1.6, roughness: 0.4, ...o }),
};

/** Rounded box without addons (bevelled via ExtrudeGeometry). */
export function roundedBox(w = 1, h = 1, d = 1, r = 0.12, material = mat.plastic()) {
  const s = new THREE.Shape(), x = -w / 2, y = -h / 2;
  s.moveTo(x + r, y); s.lineTo(x + w - r, y); s.quadraticCurveTo(x + w, y, x + w, y + r);
  s.lineTo(x + w, y + h - r); s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  s.lineTo(x + r, y + h); s.quadraticCurveTo(x, y + h, x, y + h - r);
  s.lineTo(x, y + r); s.quadraticCurveTo(x, y, x + r, y);
  const bevel = Math.min(r, d / 2) * 0.6;
  const geo = new THREE.ExtrudeGeometry(s, { depth: d - 2 * bevel, bevelEnabled: true, bevelSize: bevel, bevelThickness: bevel, bevelSegments: 6, curveSegments: 10 });
  geo.translate(0, 0, -(d - 2 * bevel) / 2);
  geo.computeVertexNormals();
  const m = new THREE.Mesh(geo, material); m.castShadow = m.receiveShadow = true;
  return m;
}

/** 3D bar chart. Returns { group, bars, update(p) } — update(0..1) grows bars with stagger. */
export function barChart(values, { colors = ['#5b7cff', '#7ad7ff', '#ff8a5b', '#ffd166', '#9b7bff'], width = 0.7, gap = 0.45, maxH = 3.2, depth = 0.7 } = {}) {
  const group = new THREE.Group(), max = Math.max(...values);
  const bars = values.map((v, i) => {
    const b = roundedBox(width, 1, depth, 0.08, mat.plastic(colors[i % colors.length]));
    b.geometry.translate(0, 0.5, 0);
    b.position.x = (i - (values.length - 1) / 2) * (width + gap);
    b.userData.h = (v / max) * maxH; group.add(b); return b;
  });
  const update = p => bars.forEach((b, i) => {
    const q = ease.outBack(clamp(p * (1 + bars.length * 0.15) - i * 0.15));
    b.scale.y = Math.max(0.001, q * b.userData.h); b.visible = q > 0.001;
  });
  update(0);
  return { group, bars, update };
}

/** Floating dust/bokeh particles, deterministic. Call update(t). */
export function particles(scene, { count = 400, spread = 30, color = '#9fb4ff', size = 0.05, seed = 7 } = {}) {
  const r = rng(seed), pos = new Float32Array(count * 3), base = [];
  for (let i = 0; i < count; i++) base.push([(r() - 0.5) * spread, r() * spread * 0.4, (r() - 0.5) * spread, r() * 6.28]);
  const geo = new THREE.BufferGeometry(); geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const pts = new THREE.Points(geo, new THREE.PointsMaterial({ color, size, transparent: true, opacity: 0.7, depthWrite: false, blending: THREE.AdditiveBlending }));
  scene.add(pts);
  return { points: pts, update: t => { base.forEach(([x, y, z, ph], i) => {
    pos[i * 3] = x + Math.sin(t * 0.3 + ph) * 0.3; pos[i * 3 + 1] = y + Math.sin(t * 0.5 + ph) * 0.4; pos[i * 3 + 2] = z; });
    geo.attributes.position.needsUpdate = true; } };
}

/** Thin glowing curve between two points; update(p) draws it on. */
export function connector(a, b, { color = '#7aa2ff', lift = 1, radius = 0.025 } = {}) {
  const A = new THREE.Vector3(...a), B = new THREE.Vector3(...b);
  const mid = A.clone().add(B).multiplyScalar(0.5); mid.y += lift;
  const curve = new THREE.QuadraticBezierCurve3(A, mid, B);
  const geo = new THREE.TubeGeometry(curve, 64, radius, 8, false);
  const mesh = new THREE.Mesh(geo, mat.glow(color));
  const total = geo.index.count;
  return { mesh, update: p => { geo.setDrawRange(0, Math.floor(total * clamp(p) / 6) * 6); mesh.visible = p > 0; } };
}

// ---------------------------------------------------------------- camera
/** Place camera on an orbit around target. angle in radians, elevation in radians. */
export function orbit(camera, { target = [0, 0, 0], radius = 10, angle = 0, elevation = 0.25 } = {}) {
  const [tx, ty, tz] = target;
  camera.position.set(tx + radius * Math.sin(angle) * Math.cos(elevation), ty + radius * Math.sin(elevation), tz + radius * Math.cos(angle) * Math.cos(elevation));
  camera.lookAt(tx, ty, tz);
}
/** Subtle hand-held drift so static shots feel alive. Apply after positioning. */
export function handheld(camera, t, amount = 1) {
  camera.position.x += Math.sin(t * 0.7) * 0.04 * amount; camera.position.y += Math.sin(t * 1.1 + 1) * 0.03 * amount;
  camera.rotation.z += Math.sin(t * 0.5) * 0.002 * amount;
}

// ---------------------------------------------------------------- HTML overlay (typography, labels)
let overlayRoot;
export function overlay(css = '') {
  if (!overlayRoot) {
    overlayRoot = document.createElement('div');
    overlayRoot.id = 'overlay';
    Object.assign(overlayRoot.style, { position: 'fixed', inset: 0, pointerEvents: 'none' });
    document.body.appendChild(overlayRoot);
  }
  if (css) { const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s); }
  return overlayRoot;
}
/** Create an overlay element. */
export function el(html, className = '', style = {}) {
  const e = document.createElement('div'); e.className = className; e.innerHTML = html;
  e.style.position = 'absolute';
  for (const [k, v] of Object.entries(style)) k.startsWith('--') ? e.style.setProperty(k, v) : (e.style[k] = v);
  overlay().appendChild(e); return e;
}
/** Fade + slide + blur-in an element by progress p (0..1). */
export function reveal(e, p, { y = 40, blur = 12, scale = 0.96 } = {}) {
  e.style.opacity = p; e.style.filter = `blur(${(1 - p) * blur}px)`;
  e.style.transform = `translate(var(--tx, 0), calc(var(--ty, 0px) + ${(1 - p) * y}px)) scale(${lerp(scale, 1, p)})`;
  e.style.visibility = p <= 0.001 ? 'hidden' : 'visible';
}
/** Per-word staggered reveal. Call once with text to split; returns update(p). */
export function wordReveal(e, text, { stagger = 0.08, y = 30 } = {}) {
  e.innerHTML = text.split(' ').map(w => `<span style="display:inline-block;white-space:pre">${w} </span>`).join('');
  const spans = [...e.children], n = spans.length;
  return p => spans.forEach((s, i) => {
    const q = ease.outCubic(clamp(p * (1 + n * stagger) - i * stagger));
    s.style.opacity = q; s.style.transform = `translateY(${(1 - q) * y}px)`; s.style.filter = `blur(${(1 - q) * 8}px)`;
  });
}
/** Pin an overlay element to a 3D point (call every frame after camera moves). */
export function pinTo(e, vec3, camera, { width, height }, offset = [0, 0]) {
  camera.updateMatrixWorld();
  const v = new THREE.Vector3(...(Array.isArray(vec3) ? vec3 : vec3.toArray())).project(camera);
  e.style.left = ((v.x + 1) / 2 * width + offset[0]) + 'px';
  e.style.top = ((1 - v.y) / 2 * height + offset[1]) + 'px';
}

// ---------------------------------------------------------------- mount
/**
 * Register the scene with the renderer.
 *   mountScene({ stage, duration, fps, update(t), assets: [promises] })
 * update(t) sets all object state for time t; director renders afterwards.
 * Preview in a browser: ?t=2.5 freezes at 2.5 s, ?play=1 plays in real time.
 */
export function mountScene({ stage, duration, fps = 30, update, assets = [] }) {
  const { renderer, scene, camera, width, height } = stage;
  const seek = t => { update(t); renderer.render(scene, camera); };
  const ready = Promise.all([document.fonts ? document.fonts.ready : null, ...assets]).then(() => { seek(0); });
  window.SCENE = { width, height, fps, duration, ready, seek, resize() { renderer.setSize(width, height); } };
  const q = new URLSearchParams(location.search);
  ready.then(() => {
    if (q.has('t')) seek(Number(q.get('t')));
    else if (q.has('play')) { const t0 = performance.now(); const loop = () => { seek(((performance.now() - t0) / 1000) % duration); requestAnimationFrame(loop); }; loop(); }
  });
  return window.SCENE;
}
