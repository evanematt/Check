# 3D Video Recipes

All snippets assume `import * as D from '/__skill/lib/director.js'; const { THREE } = D;` and run inside `update(t)` unless marked *setup*.

## Shot recipes

### Product turntable (hero)
```js
// setup
const hero = D.roundedBox(2, 3.6, 0.3, 0.3, D.mat.metal('#d9dbe3')); scene.add(hero);
// update
hero.rotation.y = t * 0.6;                                   // constant spin
hero.position.y = Math.sin(t * 1.3) * 0.08;                  // float
D.orbit(camera, { radius: D.tween(t, 0, 4, 9, 7), angle: 0.3, elevation: 0.2 }); // slow push-in
```

### Exploded view (layers / architecture / "what's inside")
Stack N parts, then separate along one axis with `D.prog(...)` and pin a label to each part with `D.pinTo`. See `templates/explainer.html`.

### Process flow (A → B → C)
```js
// setup: nodes in a row + connectors
const xs = [-4, 0, 4];
const nodes = xs.map((x, i) => { const n = D.roundedBox(1.6, 1.6, 1.6, 0.25, D.mat.plastic(colors[i])); n.position.x = x; scene.add(n); return n; });
const links = [[xs[0], xs[1]], [xs[1], xs[2]]].map(([a, b]) => { const c = D.connector([a + 0.9, 0, 0], [b - 0.9, 0, 0], { lift: 0.8 }); scene.add(c.mesh); return c; });
// update: pop each node, then draw the link to the next one
nodes.forEach((n, i) => n.scale.setScalar(Math.max(0.001, D.prog(t, 0.5 + i * 1.2, 0.6, D.ease.outBack))));
links.forEach((l, i) => l.update(D.prog(t, 1.0 + i * 1.2, 0.7)));
// camera tracks the active node
D.orbit(camera, { target: [D.track([[0, -4], [1.2, -4], [2.4, 0], [3.6, 4]])(t), 0, 0], radius: 9, angle: 0.35, elevation: 0.3 });
```

### Network / globe
```js
// setup
const globe = new THREE.Mesh(new THREE.SphereGeometry(2, 64, 64), D.mat.matte('#1b2140', { wireframe: true, transparent: true, opacity: 0.35 }));
const r = D.rng(3); const dots = new THREE.Group();
for (let i = 0; i < 120; i++) { const v = new THREE.Vector3(r() - .5, r() - .5, r() - .5).normalize().multiplyScalar(2.02);
  const d = new THREE.Mesh(new THREE.SphereGeometry(0.03), D.mat.glow('#7ad7ff')); d.position.copy(v); dots.add(d); }
globe.add(dots); scene.add(globe);
// update
globe.rotation.y = t * 0.25;
```

### Device mockup with screen content
Use a canvas texture for the screen; redraw it from `t` each frame.
```js
// setup
const cv = document.createElement('canvas'); cv.width = 540; cv.height = 1170; const g = cv.getContext('2d');
const tex = new THREE.CanvasTexture(cv); tex.colorSpace = THREE.SRGBColorSpace;
const phone = D.roundedBox(2.2, 4.6, 0.25, 0.35, D.mat.metal('#222'));
const screen = new THREE.Mesh(new THREE.PlaneGeometry(2.0, 4.33), new THREE.MeshBasicMaterial({ map: tex }));
screen.position.z = 0.13; phone.add(screen); scene.add(phone);
// update
g.fillStyle = '#0f1226'; g.fillRect(0, 0, 540, 1170);
g.fillStyle = '#7aa2ff'; g.fillRect(40, 200, 460 * D.prog(t, 1, 2), 40);  // animated progress bar
tex.needsUpdate = true;
```

### Logo sting
Extrude an SVG logo: `SVGLoader` from `three/addons/loaders/SVGLoader.js` → `ExtrudeGeometry` per path. Scale in with `outBack`, spin 1 turn with `inOutExpo`, light sweep by animating `rim.position.x` from -8 to 8.

### Kinetic typography
Overlay text with `D.wordReveal(el, text)`; drive with `D.prog(t, start, 0.8–1.4, D.ease.linear)`. Pair with a slow camera dolly on an abstract 3D backdrop (particles + floating rounded boxes).

### Big number / stat
`.stat` gradient text + counter: `el.textContent = Math.round(target * D.prog(t, s, 1.8, D.ease.outCubic))`. Pair with `D.barChart`.

## Camera moves (use one per beat)
| Move | How |
|---|---|
| Push-in (focus) | `radius` tween down 15–25% over the beat |
| Pull-out (reveal context) | `radius` tween up, elevation up slightly |
| Orbit (show 3D-ness) | `angle` tween 0.4–1.0 rad per beat |
| Crane | `elevation` tween 0.1 → 0.6 |
| Whip transition | `angle` jump ~1.5 rad in 0.35s with `inOutExpo` + motion blur via overlay `filter: blur()` |
| Always | `D.handheld(camera, t, 0.6–1)` after positioning |

Use `D.track([[t, value], ...])` for multi-beat camera paths so each beat flows into the next.

## Pacing
- 6s ad: hook (0–1.5) · payoff (1.5–4.5) · logo/CTA (4.5–6)
- 15s reel: hook · 2–3 beats · CTA (hold ≥1.5s)
- 30–60s explainer: problem · how it works (3–5 beats) · proof (stat) · CTA
- Text must stay on screen ≥ (words × 0.3s) + 0.8s.

## Palettes (bg / accent / accent2 / objects)
| Name | bg | accent | accent2 | objects |
|---|---|---|---|---|
| Midnight (default) | `#0b0d17` | `#7aa2ff` | `#ff8a5b` | `#5b7cff #7ad7ff #ff8a5b #ffd166 #9b7bff` |
| Clean studio (light) | `#eef0f5` | `#2f5bff` | `#ff5c39` | `#ffffff #c9d3ff #2f5bff #1b1f33` — use `D.mat.matte`, floor `grid:false`, darker text `--ink:#12162b` |
| Neon | `#07060d` | `#00f0ff` | `#ff2bd6` | `D.mat.glow` everywhere, add `particles` with `color:'#ff2bd6'` |
| Earth | `#14120f` | `#e9c46a` | `#e76f51` | `#2a9d8f #e9c46a #f4a261 #e76f51` |
| Mono premium | `#0a0a0a` | `#ffffff` | `#bdbdbd` | `D.mat.metal('#d0d0d0')`, `D.mat.glass()` |

For a light theme, also set `D.floor(scene, { color: bg })` so fog matches, and lower `studioLights` intensity to ~0.8.

## Loading models and textures
```js
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
const model = new GLTFLoader().loadAsync('./model.glb').then(g => { g.scene.traverse(o => o.castShadow = o.receiveShadow = true); scene.add(g.scene); return g; });
D.mountScene({ stage, duration, update, assets: [model] });
```
Put files next to `scene.html` (served at `/`). Free CC0 models: Poly Haven, Kenney, Quaternius. Animated GLTF: create an `AnimationMixer` and call `mixer.setTime(t)` in `update` (deterministic).

Environment lighting (nicer reflections): `RoomEnvironment` from `three/addons/environments/RoomEnvironment.js` + `PMREMGenerator` → `scene.environment`.

## Audio
`--audio track.mp3` muxes and fades the last 1.5s. Time beats to the music: note downbeats (seconds) and put beat starts on them. Royalty-free sources: Pixabay Music, YouTube Audio Library. For voice-over, render the VO first and set beat times from its timestamps.

## Troubleshooting
| Symptom | Fix |
|---|---|
| `window.SCENE missing` | JS error in scene; run `--serve` and open the console, or read the "Page errors" the renderer prints |
| Black frames | Missing lights, camera inside object, or `update` threw. Render one still to check |
| Labels lag / wrong place | Call `D.pinTo` after setting camera and object transforms; `stack.updateMatrixWorld()` before reading `matrixWorld` |
| Frames differ between runs / jitter | Something non-deterministic (random, time, CSS animation). Replace with `D.rng`, `t` |
| Stripes on surfaces | Shadow acne: raise `light.shadow.normalBias` |
| Fonts fall back | Add `&display=block` to the Google Fonts URL; the renderer waits on `document.fonts.ready` |
| Slow render | Lower `--fps 24`, reduce shadow map to 1024, fewer particles, render slices in parallel with `--from/--to` then `ffmpeg -f concat` |
| Remote asset fails | The renderer fetches CDN/fonts with curl and caches in `~/.cache/claude-3d-video`; if jsDelivr/unpkg is blocked it pulls the same package version from the npm registry (`npm pack`). Otherwise check network or vendor the file next to the scene |
| Overlay text collides in 9:16 | 3D-pinned overlays (`D.pinTo`) keep their 16:9 world offsets and fall off the sides of a vertical frame. Give pinned items separate `V ? [...] : [...]` offsets that hug the centre line, and check a still per shot in both sizes |
| Caption unreadable over a bright screen | Put captions that sit over UI/screens on a dark pill (`.cap.scrim` in `cinematic-desk.html`) and don't time on-screen pop-ups (banners, bubbles) to overlap the caption's area |
