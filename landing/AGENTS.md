# AGENTS.md: landing page "Намалюй мені рись"

Technical brief for an AI coding agent working on this project. The human-facing guide (Ukrainian) is `README.md`. Read this whole file before editing. Most "simple" visual changes touch two or three coupled places, and those couplings are listed below.

## 1. What this is

A single-screen hero for a charity colouring-book landing page (Roboneers × СЕНС). It is a static site with no build step and no package manager: plain HTML, CSS and JS. three.js 0.160.0 comes in through an import map from jsDelivr, and the fonts (Inter, Unbounded) from Google Fonts.

Scene, back to front:

1. **Painting.** A night sky with a star, an aurora and snow dunes (`assets/hero-bg.webp`, 2000×1333, ratio 1.5), cover-fitted. CSS adds overlays: a breathing star glow, an aurora shimmer and canvas sparkles.
2. **Ground robot (НРК) with a lynx, left.** Three DOM layers: `nrk-base.webp`, then the lynx sprite, then `nrk-front.webp`.
   - The lynx is a 9×7 sprite sheet. The frame changes so its gaze follows the cursor.
   - A Christmas garland is drawn as SVG on top and throws warm light: onto the robot, onto the lynx, onto a snow pool around it, and onto the drift in front of the wheels.
3. **Colouring book, right.** WebGL (three.js) in one transparent canvas that covers the hero.
   - The book is half buried in a procedural snow drift textured with crops of the painting.
   - On hover, tap or focus it springs fully out of the drift. A slot-shaped hole fades in where it stood, snow puffs up, and an info card opens beside it.
4. **Info card** (`.book-card`): a frosted square panel with a hand-lettered SVG title, placeholder copy, and the buttons «Спробувати» and «Купити».
5. **Header** (`.site-header`): a floating glass pill with the logo, three links and a «Купити» button.

A slow animated SVG displacement ("heat haze", `#warp`) is applied to the whole `.scene` wrapper, but not to the card or the header.

The UI language is Ukrainian. Keep all user-facing copy in Ukrainian.

## 2. Run and verify

```bash
python3 -m http.server 8000        # from this folder; file:// will NOT work (ES modules + GLB)
node tools/screenshot.mjs http://localhost:8000 ./shots   # needs `playwright` + chromium
```

`tools/screenshot.mjs` writes desktop (1440×900) and phone (390×844) shots:
- the resting state;
- the book hovered or tapped, with the card open;
- several cursor positions so the lynx's gaze can be checked.

**Always look at those shots after a visual change.** Headless Chromium needs `--use-gl=angle --use-angle=swiftshader` for WebGL, and the script already passes those flags.

There are no automated tests. "Done" means the screenshots look right at both sizes, the console has no errors, and the page doesn't scroll sideways.

## 3. File map

| File | Responsibility |
|---|---|
| `index.html` | DOM structure, import map, SVG filter defs (`#warp`, `#moonlit`), card markup, header |
| `styles.css` | All styling. Tokens and `--bg-grade` live in `:root`. Responsive rules at 720 px (header) and 768 px (scene) |
| `book.js` (ES module) | three.js scene: drift, slot, puff, halo, book, lights, colour grading, layout/lens-shift, hover/tap/keyboard interaction, card positioning, animation loop |
| `nrk.js` (classic script, deferred) | Lynx gaze, garland SVG construction, garland light maps (`paintLight`), painted drift by the wheels (`paint`), `#warp` animation |
| `main.js` (classic script, deferred) | Canvas sparkles over the painting |
| `tools/screenshot.mjs` | Verification screenshots (Playwright) |

## 4. Layer and stacking order

Everything visual lives in `.scene`, which is `position:absolute; inset:0; z-index:0; filter:url(#warp)`:

| z-index | Element | Notes |
|---|---|---|
| -3 | `.hero-bg` (`<picture>`) | `filter: var(--bg-grade)` dims the painting |
| -2 | `.hero-star`, `.hero-aurora`, `.hero-sparkles` | Pinned to the painting (see §5). `mix-blend-mode: screen` |
| -1 | `.hero-shade` | Bottom gradient |
| 0 | `.nrk-spill` | Warm light map, `mix-blend-mode: screen`. **Must stay a sibling *before* `.nrk`, not inside it** (see §7) |
| 0 | `.nrk` | Contains `.nrk-shadow`, `.nrk-body` (base img, back garland SVG, lynx, front img, `.nrk-light` canvas, front garland SVG) and `.nrk-snow` canvas |
| 1 | `.book-scene` canvas | WebGL. Covers the hero and receives pointer events for picking |

Outside `.scene`, so not distorted:
- `.book-hit` (z 2): an invisible focus target for keyboard users, repositioned over the book every frame;
- `.book-card` (z 3);
- `.site-header` (z 10), which is outside `.hero` entirely.

## 5. Coordinate systems (most bugs come from mixing these)

1. **Painting space:** fractions (0–1) of the 1344×896 / 2000×1333 image.
   - The painting is cover-fitted inside a "stage" that is exactly `max(100vw, 100svh·1.5) × max(100svh, 100vw/1.5)`.
   - The stage is offset horizontally by `--focus-x`: 0.5 on desktop, 0.22 on phones, so the star stays in frame.
   - The star and aurora overlays are positioned in stage percentages. The star is at `26.4% 20.2%`.
   - **The book is anchored to a painting point** (`PLACE.wide/narrow.fx/fy` in `book.js`), so it stays on the same snow ridge at any aspect ratio. `layout()` reproduces the cover maths in JS and must stay in sync with the CSS stage (`IMG_RATIO`, `--focus-x`).
2. **three.js world:**
   - The book's base pivot is at the origin; Y is up; the drift is a heightfield `driftHeight(x, z)`.
   - The camera never moves to place the book. `layout()` uses `camera.zoom` for size and a **lens shift** (projection matrix elements `[8]` and `[9]`) to put the world origin on the anchor pixel.
   - After editing the projection matrix, `projectionMatrixInverse` must be updated, which `layout()` does, or raycasting breaks.
3. **Robot space:** 1274×1517 px, the native size of the НРК images.
   - Garland strands (`STRANDS` in `nrk.js`) and the lamp list used for the light maps are in this space.
   - The lynx box is `left 32.564%, top 9.965%, 32.547% × 50%` of the robot box.
   - The spill canvas spans −70 %…170 % × −45 %…145 % of the robot box, and `paintLight()` maps lamps into that.
4. **Drift canvas by the wheels:** `.nrk-snow` is `left:-14%; width:128%; bottom:-2%; height:30%` of the robot box. `paint()` maps lamp x into it with `((x/1274)+0.14)/1.28`.

## 6. Coupled values (change them together)

| If you change… | …also change |
|---|---|
| `--bg-grade` (CSS) | `GRADE.backdrop` in `book.js` (the same brightness · saturate · contrast, written in GLSL). Otherwise the book's drift no longer matches the painting. `.nrk-snow` already uses `--bg-grade` |
| `#moonlit` colour matrix (`index.html`) | `GRADE.book` in `book.js` (the same matrix in GLSL, mixed at 0.82 and brightened ×1.45 because the book texture is more saturated than the painted robot) |
| Background image composition | Star overlay position (`.hero-star::before/::after` `left/top`), aurora gradient positions, `PLACE.fx/fy`, and the snow crops `snow-body.webp` / `snow-crest.webp` (re-cut them from the new painting) |
| `BURY`, `LEAN`, `HOP` (`book.js`) | `SLOT_Z` is derived from `LEAN`. `HOP` must exceed `BURY` plus the drift height (~0.3) or the book won't fully clear the snow. Check that the book's top doesn't leave the screen when it is out (`PLACE.height`) |
| `.nrk` box (`left/bottom/height`) | It is shared with `.nrk-spill` through the same selector. Keep them in one rule |
| Lynx sprite sheet | `.nrk-lynx` box percentages, `COLS/ROWS` in `nrk.js`, `background-size`/`mask-size` `900% 700%` (both in `.nrk-lynx` and `.nrk-lynx::before`) |
| Card markup or size | `positionCard()` in `book.js` measures `offsetWidth/Height`. It places the card right, then left, then above the book |

## 7. Invariants and gotchas

- **`mix-blend-mode` only blends inside the nearest isolated group.** Any element with `filter`, `opacity < 1`, a `z-index` stacking context, `mask` and so on isolates its children.
  - That is why `.nrk-spill` is a sibling of `.nrk` and not a child.
  - That is why the lynx and robot light layers are inside `.nrk-body`, so they blend only with the robot.
  - Don't add `filter` or `opacity` to wrappers without checking blending still works.
- **Additive blending does not show on the transparent WebGL canvas.** It composites against nothing. The book halo uses normal alpha blending for that reason.
- **The book's hit area and the card position use `bookHolder`** (the model only), never `pivot`, because `pivot` also contains the large halo plane.
- **Clipping:** book materials use `clippingPlanes = [y > SNOW_Y]`, so the buried part is invisible even where the drift is semi-transparent. Picking ignores hits below `y = 0.18`.
- **Drift materials are `MeshBasicMaterial`.** They take no scene lights; shading is baked into vertex colours, and the crest is a second, additive-alpha mesh over the same geometry. They also receive no shadows, so `renderer.shadowMap` is off, and the "cast shadow" is baked too.
- **Grading is injected** with `onBeforeCompile`, replacing `#include <dithering_fragment>`, which runs after the sRGB conversion. So the maths is in display sRGB, the same as CSS filters. Keep `customProgramCacheKey` unique per grade kind.
- **Garland light maps are additive canvases** (`globalCompositeOperation='lighter'`) with ~45 lamps. Per-lamp alpha values look tiny (0.04–0.1) on purpose: higher values saturate to yellow-white.
- **The lynx sprite** was repaired. The original cut-out had holes, so its mask was closed and filled, and the holes inpainted. If you replace the sheet, check frames for holes, because they show as flicker when the head turns.
- **Accessibility:**
  - Respect `prefers-reduced-motion`. The CSS turns off animations and the warp, and the JS skips the hop spring, idle nudges and the warp animation.
  - Keep the card reachable by keyboard: Tab to `.book-hit` opens it, Esc closes it.
  - Keep `aria-*` attributes and the `alt` on the title image.
- **Performance:**
  - `#warp` is a full-screen SVG displacement, the most expensive part.
  - The lynx sheet is 1.6 MB, the heaviest asset.
  - Lower DPR or disable the warp on small screens if needed.

## 8. Tuning knobs (quick reference)

| What | Where |
|---|---|
| Book position and size | `book.js` → `PLACE.wide` / `PLACE.narrow` (`fx`, `fy` painting fractions, `height` = book size vs viewport height) |
| Bury depth, hop height, lean | `book.js` → `BURY`, `HOP`, `LEAN` |
| Hop feel (springiness) | `book.js` → `tick()`, `const k = 140, c = 13` |
| Idle "nudge" interval | `book.js` → `nudgeAt` (6–9 s) |
| Card open/close delays | `book.js` → `scheduleClose` (280 ms), hide after close (400 ms) |
| Book lights | `book.js` → `key` (spot from the star side), `rim`, `bounce`, hemisphere, `glow` (on hover), `backGlow` halo |
| Drift shape | `book.js` → `driftHeight()`; footprint fade in `buildDrift()` |
| Drift light and crest | `book.js` → `buildDrift()`: `L` light direction, `shade`, the ridge/crest weights |
| Background dimming | `styles.css` → `--bg-grade`, plus `GRADE.backdrop` (keep in sync) |
| Night tint of robot, lynx and book | `index.html` → `#moonlit`, plus `GRADE.book` (keep in sync) |
| Lynx gaze sensitivity | `nrk.js` → `reach`, the smoothing `k`, idle wander |
| Garland path | `nrk.js` → `STRANDS` (robot space) |
| Garland brightness and flicker | `styles.css` → `.nrk-garland .bulb/.halo/.bloom`, `@keyframes twinkle`, `garland-flicker` |
| Garland light on robot and snow | `nrk.js` → `paintLight()` alphas and radii, plus the drift's warm pass in `paint()` |
| Haze strength and speed | `index.html` → `#warp feDisplacementMap scale`; `nrk.js` → the `baseFrequency` animation |
| Robot position and size | `styles.css` → `.nrk, .nrk-spill` (plus the 768 px media query) |

## 9. Content still to fill in

- Card description (`.book-card-text`) is placeholder text.
- Card buttons, header links and the header «Купити» all have `href="#"`.
- The card title is an outlined SVG from the design (`assets/book-card-title.svg`). To change the wording, re-export it from the design file.
- The logo is a raster PNG, recoloured white from the supplied file. A vector logo would be better.
- Only the hero exists. When more sections are added, consider a sticky header: it is currently `position:absolute`.

## 10. Conventions

- **No build step, no frameworks.** Keep it that way unless the human asks.
- **Pinned CDN versions** (`three@0.160.0`). Don't use floating versions.
- **CSS:** tokens in `:root`, section banners (`/* ───────── … ───────── */`), mobile overrides in media queries at the end of the relevant section or the end of the file.
- **JS:** small comment banners per subsystem. Explain *why* in comments, not what.
- **Visual changes:** verify with `tools/screenshot.mjs` at both sizes before handing back. Describe what you checked.
