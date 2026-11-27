# AGENTS.md: landing page "Намалюй мені рись"

Technical brief for an AI coding agent working on this project. The human-facing guide (Ukrainian) is `README.md`. Read this whole file before editing. Most "simple" visual changes touch two or three coupled places, and those couplings are listed below.

## 1. What this is

A charity colouring-book landing page: an interactive hero plus static content blocks (Roboneers × СЕНС). It is a static site with no build step and no package manager: plain HTML, CSS and JS. three.js 0.160.0 comes in through an import map from jsDelivr, and the fonts (Inter, Unbounded) from Google Fonts.

The scene is assembled from separate layers that match a reference composition (2000×1306). Back to front:

1. **Sky** (`assets/sky.webp`): night sky with an aurora, cover-fitted to the whole hero, plus canvas sparkles.
2. **Far dunes** (`assets/snow-dunes.webp`, transparent PNG-style WebP).
3. **Ground robot (НРК) with a lynx, left.** It stands *behind* the left drift.
   - Three DOM layers: `nrk-base.webp`, then the lynx sprite (9×7 frames, the gaze follows the cursor), then `nrk-front.webp`.
   - A garland is drawn as SVG and throws warm light onto the robot, the lynx, the dunes and the left drift.
   - **On hover (or tap) the robot hops up out of the drift** (CSS transform) and settles back when the pointer leaves.
4. **Left drift** (`assets/drift-left.webp`), in front of the robot.
5. **Colouring book (WebGL, three.js),** one transparent canvas covering the hero. The book stands *behind* the right drift.
   - On hover, tap or focus it springs up and fully out of the drift, with a snow puff.
   - An info card opens beside it.
6. **Right drift** (`assets/drift-right.webp`), in front of the book, with **the book's garland** (`svg#driftGarland`) laid along its crest so it never covers the cover art. A warm point light in `book.js` (`garlandLight`) stands for it on the book.
   - **Snowfall** (`canvas.snowfall`, in `main.js`): three depth layers with wind and sway, in front of everything in the scene.
   - **Warm garland light on the snow:** `.dunes-light`, `.drift-left-light` and `.drift-right-light`, **multiply** tints clipped to each snow image (see §7).
7. **Info card** (`.book-card`): a frosted square panel with a hand-lettered SVG title, placeholder copy, and the buttons «Спробувати» and «Купити».
8. **Header** (`.site-header`): a floating glass pill with the logo, three links and a «Купити» button.

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

**Always look at those shots after a visual change.** To check the robot's hop, hover the robot (§7). Headless Chromium needs `--use-gl=angle --use-angle=swiftshader` for WebGL, and the script already passes those flags.

There are no automated tests. "Done" means the screenshots look right at both sizes, the console has no errors, and the page doesn't scroll sideways.

## 3. File map

| File | Responsibility |
|---|---|
| `index.html` | DOM structure and layer order, import map, SVG filter defs (`#warp`, `#moonlit`), card markup, header |
| `styles.css` | All styling. The composition frame and drift/robot placement variables live on `.hero`. The portrait override is `@media (max-aspect-ratio: 4/5)`; the header collapses at 720 px |
| `book.js` (ES module) | three.js: book, lights, halo, snow puff, colour grading, placement against the right drift, hover/tap/keyboard interaction, card positioning, animation loop |
| `nrk.js` (classic script, deferred) | Lynx gaze, garland SVG construction, garland light maps (`paintLight`), `#warp` animation, robot hover/tap hop |
| `main.js` (classic script, deferred) | Canvas sparkles and snowfall |
| `boil.js` (classic script, deferred) | Hand-drawn "boiling" lines around the robot and the book: strokes in object space, redrawn every frame to follow the hops, re-jittered at 8 fps across 3 drawings |
| `tools/screenshot.mjs` | Verification screenshots (Playwright) |

## 4. Layer and stacking order

Everything visual is inside `.scene` (`position:absolute; inset:0; z-index:0; filter:url(#warp)`). Inside it, **stacking is by DOM order**, with no z-indexes:

```
.sky  →  .hero-sparkles  →  .ground [ img.dunes → .dunes-light ]
      →  .ground.ground-left [ .nrk-spill → .nrk → img.drift-left → .drift-left-light ]
      →  canvas.book-scene
      →  .ground [ img.drift-right → .drift-right-light → svg#driftGarland ]
      →  canvas.boil  →  canvas.snowfall
```

Outside `.scene`, so not distorted:
- `.book-hit` (z 2): an invisible focus target for keyboard users, repositioned over the book every frame;
- `.book-card` (z 3);
- `.site-header` (z 10), which is outside `.hero` entirely.

## 5. Coordinate systems (most bugs come from mixing these)

1. **Composition frame (`.ground`):** the reference composition is 2000×1306 (`--frame-ratio: 1.5314`).
   - Every `.ground` box is `width: var(--gw)`, with that aspect ratio, bottom-aligned and centred (`left: var(--gx)`).
   - On landscape screens `--gw = max(100vw, 100svh·ratio)`. The frame covers the screen and only sky gets cropped.
   - On portrait screens `--gw: 230vw`, and the drifts are pulled towards the centre so both the robot and the book are visible.
   - Everything inside `.ground` is placed in **% of the frame**. These values were measured by feature-matching the layers against the reference composition:
     - dunes: `left -10.47%, top 17.15%, width 131.5%`;
     - left drift: `--dl-x / --dl-y / --dl-w`;
     - right drift: `--dr-x / --dr-y / --dr-w`.
2. **Robot box:** `.nrk` is placed *relative to the left drift*: `left = --dl-x + --nrk-dx`, `top = --dl-y + --nrk-dy`, `width = --nrk-w`. Move the drift and the robot follows it.
3. **Robot space:** 1274×1517 px, the native size of the НРК images.
   - Garland strands (`STRANDS` in `nrk.js`) and the lamp list for the light maps are in this space.
   - The lynx box is `left 32.564%, top 9.965%, 32.547% × 50%` of the robot box.
   - The spill canvas spans −70 %…170 % × −45 %…145 % of the robot box.
4. **three.js world:**
   - The book's pivot is at the origin, which is the book's "snow line"; Y is up.
   - `layout()` in `book.js` reads the **right drift image's on-screen rect** and puts the origin at `PLACE.fx/fy` (fractions of that image).
   - It sizes the book with `camera.zoom`, so the visible height is about `PLACE.size × drift width`. It positions it with a **lens shift** (projection matrix elements `[8]` and `[9]`) and then updates `projectionMatrixInverse`, which raycasting needs.

## 6. Coupled values (change them together)

| If you change… | …also change |
|---|---|
| The reference composition or any layer image | Re-measure that layer's box in % of the 2000×1306 frame (feature-match it against the composite, e.g. OpenCV SIFT and `estimateAffinePartial2D`), then update the CSS variables |
| Left drift box (`--dl-*`) | The robot follows automatically. Check that its wheels are still hidden behind the drift's crest, using `--nrk-dy` |
| Right drift box or image | Book `PLACE.fx/fy/size` in `book.js`. The book's straight bottom edge (the clip at `SNOW_Y`) must stay hidden behind the drift's top edge, including where the drift slopes down |
| `BURY`, `HOP` (`book.js`) | `HOP` must lift the book's base above the drift's crest. Check that the book's top doesn't leave the screen when it is out |
| Robot hop height (`.hero.nrk-up` `translateY`) | The hit area in `nrk.js` (`overRobot`) ignores the part hidden by the drift. Keep it in sync if the robot's position changes a lot |
| `#moonlit` colour matrix (`index.html`) | `GRADE.book` in `book.js` (the same matrix in GLSL, mixed at 0.82 and brightened ×1.45). That keeps the robot, the lynx and the book in one palette |
| Lynx sprite sheet | `.nrk-lynx` box percentages, `COLS/ROWS` in `nrk.js`, `background-size`/`mask-size` `900% 700%` (both in `.nrk-lynx` and `.nrk-lynx::before`) |
| Card markup or size | `positionCard()` in `book.js` measures `offsetWidth/Height`. It places the card right, then left, then above the book |

## 7. Invariants and gotchas

- **`mix-blend-mode` only blends inside the nearest isolated group.** `filter`, `opacity < 1`, `transform`, `z-index` stacking contexts and `mask` all isolate.
  - `.ground` boxes deliberately have **no** z-index, filter or transform, so `.nrk-spill` and `.drift-left-light` (screen-blended) can blend with the dunes behind them.
  - Don't add `filter`, `opacity` or `transform` to `.ground`.
- **The robot hop is CSS.** `nrk.js` toggles `.hero.nrk-up`, which translates both `.nrk` and `.nrk-spill`. The robot has `pointer-events:none` because the WebGL canvas is above it, so hover is hit-tested in JS against `.nrk-body`'s rect, ignoring the bottom part hidden by the drift. On touch, a tap makes it hop for about 1.4 s.
- **The book hides behind an image, not 3D snow.** Book materials are clipped at `y > SNOW_Y`. The drift image in front must cover that cut. The puff starts below the crest, so it rises over it.
- **Additive blending does not show on the transparent WebGL canvas.** Use normal alpha blending for any glow drawn there.
- **The book's hit area and the card position use `bookHolder`** (the model only), never `pivot`.
- **Garlands are built by one function,** `buildGarland(id, strands, k)` in `nrk.js`. Strand points are in the SVG's own units: 1274×1517 for the robot, 1660×580 for the drift. `k` scales bulbs, glows and spacing so both garlands keep the right perspective: the drift is in the foreground, so it uses 1.4 (bulbs about 3.4× the robot's on screen). The drift strand follows the measured crest of `drift-right.webp` plus about 40 units. Re-measure it if that image changes.
- **Warm light on white snow must be a multiply tint**, not `screen`: screen over white stays white, and over the dark sky it paints an orange halo. The snow-light layers' gradient centres are computed in CSS from the layout variables (`--dl-*`, `--nrk-*`, `--dr-*`), so they follow the robot and the book on every breakpoint. Keep them in sync if you change how the book is placed (`PLACE.fx`).
- **Grading is injected** with `onBeforeCompile`, replacing `#include <dithering_fragment>`, which runs after the sRGB conversion. So the maths is in display sRGB, the same as CSS filters.
- **Garland light maps are additive canvases** with ~45 lamps. Per-lamp alphas look tiny (0.02–0.1) on purpose: higher values saturate to yellow-white or paint a large orange halo on the sky.
- **The lynx sprite** was repaired. The original cut-out had holes and ragged edges, so its mask was closed, filled, smoothed and feathered (~2 px inward), and the edge colours were inpainted from the interior to remove the old background fringe. Check any replacement sheet for holes, because they show as flicker when the head turns.
- **Accessibility:**
  - Respect `prefers-reduced-motion`. The CSS turns off animations, transitions and the warp; the JS skips the book's spring, idle nudges and the warp animation.
  - Keep the card reachable by keyboard: Tab to `.book-hit` opens it, Esc closes it.
- **Performance:**
  - `#warp` is a full-screen SVG displacement, the most expensive part.
  - The lynx sheet is 1.6 MB, the heaviest asset.

## 8. Tuning knobs (quick reference)

| What | Where |
|---|---|
| Layer boxes (dunes, drifts) | `styles.css` → `.dunes`, `.hero { --dl-*, --dr-* }` |
| Robot position and size (relative to the left drift) | `styles.css` → `.hero { --nrk-dx, --nrk-dy, --nrk-w }` (plus the portrait override) |
| Robot hop height and feel | `styles.css` → `.hero.nrk-up .nrk` `translateY(-16%)` and its `transition` curves |
| Book position and size (relative to the right drift) | `book.js` → `PLACE.wide` / `PLACE.narrow` (`fx`, `fy`, `size`) |
| Bury depth, hop height, lean | `book.js` → `BURY`, `HOP`, `LEAN` |
| Book hop feel | `book.js` → `tick()`, `const k = 140, c = 13` |
| Idle "nudge" of the book | `book.js` → `nudgeAt` (6–9 s) |
| Card open/close delays | `book.js` → `scheduleClose` (280 ms), hide after close (400 ms) |
| Book lights and halo | `book.js` → `key`, `rim`, `bounce`, hemisphere, `glow` (on hover), `backGlow` |
| Night tint of robot, lynx and book | `index.html` → `#moonlit`, plus `GRADE.book` (keep in sync) |
| Lynx gaze sensitivity | `nrk.js` → `reach`, the smoothing `k`, idle wander |
| Garland path | `nrk.js` → `STRANDS` (robot space) |
| Garland brightness and flicker | `styles.css` → `.nrk-garland .bulb/.halo/.bloom`, `@keyframes twinkle`, `garland-flicker` |
| Garland light on the robot | `nrk.js` → `paintLight()` |
| Garland light on the snow (both garlands) | `styles.css` → `.dunes-light`, `.drift-left-light`, `.drift-right-light` |
| Book's garland on the drift (path, size) | `nrk.js` → `buildGarland('driftGarland', …, 1.4)` points and size factor; its light on the book: `book.js` → `garlandLight` |
| Boiling lines (colour, speed, jitter, stroke layout) | `boil.js` → `COLOR`, `FPS`, `DRAWINGS`, `JITTER`, `strokesFor()`; which objects and their visible box: `TARGETS` |
| Snowfall (density, speeds, sizes, wind) | `main.js` → the snowfall block: `LAYERS`, the density cap in `seed()`, `wind` in `loop()` |
| Haze strength and speed | `index.html` → `#warp feDisplacementMap scale`; `nrk.js` → the `baseFrequency` animation |

## 9. Content still to fill in

- Card description (`.book-card-text`) is placeholder text.
- Card buttons, header links and the header «Купити» all have `href="#"`.
- The card title is an outlined SVG from the design (`assets/book-card-title.svg`). To change the wording, re-export it from the design file.
- The logo is a raster PNG, recoloured white from the supplied file. A vector logo would be better.
- Content blocks below the hero (static HTML/CSS, no JS): `#about`, `#book`, `#help`, `#buy`, team, FAQ (`<details>`), footer. The header is `position:fixed` and its links anchor to these blocks. Unknown facts are marked: `.tbd` (dashed underline on a value) and `.tbd-text` (italic placeholder sentence). Search for both before launch. Facts used in the copy come from the book's back cover (`assets/book-cover-back.webp`).

## 10. Conventions

- **No build step, no frameworks.** Keep it that way unless the human asks.
- **Pinned CDN versions** (`three@0.160.0`). Don't use floating versions.
- **CSS:** placement variables on `.hero`, section banners (`/* ───────── … ───────── */`), overrides in media queries next to the section they change.
- **JS:** small comment banners per subsystem. Explain *why* in comments, not what.
- **Visual changes:** verify with `tools/screenshot.mjs` at both sizes before handing back. Describe what you checked.
