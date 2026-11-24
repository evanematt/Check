---
name: 3d-video
description: "Create 3D animated videos (MP4) with Claude: explainer videos, product spins, 3D data/stat reveals, logo stings, social Reels/TikTok/Shorts in 16:9, 9:16 or 1:1. Builds a Three.js scene in HTML whose every frame is a pure function of time, then renders it frame-by-frame in headless Chromium and encodes with ffmpeg. Use when the user asks for a 3D video, 3D animation, animated explainer, motion graphic, product render video, animated chart video, or a reel/short with 3D visuals."
---

# 3D Video

Turn a brief ("a 15s vertical explainer about how our app syncs files") into a finished MP4.

**Pipeline:** brief → storyboard (beats) → `scene.html` (Three.js + HTML overlay text) → still checks → `render.mjs` → MP4.

Everything lives in this skill folder:

| Path | What it is |
|---|---|
| `scripts/render.mjs` | Renderer: serves the scene, seeks every frame, screenshots, pipes into ffmpeg (H.264, yuv420p, faststart). Also `--stills` and `--serve` |
| `lib/director.js` | Toolkit: `postFX` (bloom), `filmGrain`, easing, `prog`/`tween`/`track`/`envelope`, seeded `rng`, `createStage`, `studioLights`, `floor`, materials, `roundedBox`, `barChart`, `particles`, `connector`, camera `orbit`/`handheld`, overlay `el`/`reveal`/`wordReveal`/`pinTo`, `mountScene` |
| `templates/explainer.html` | Complete working 14s explainer (title → exploded layers with pinned labels → 3D bar chart + counter → CTA). Responsive to vertical. **Start from this.** |
| `templates/cinematic-desk.html` | 29s cinematic "storytelling reel": a lamp-lit room with a retro computer, sunset light through blinds, bloom, film grain, hard cuts between 8 camera shots, an animated canvas UI on the CRT screen, word-by-word captions, chat bubbles popping from the screen, floating chrome words and a "Comment SKILL" end card. Start from this for moody, cinematic, social-first videos. |
| `references/recipes.md` | Shot recipes, camera moves, pacing, styles, palettes, GLTF models, audio, troubleshooting |

Requirements: Node 18+, Playwright with Chromium, ffmpeg. (Check: `node -v; ffmpeg -version | head -1; node -e "require('playwright')"`.)

## Workflow

### 1. Pin down the brief (ask only what's missing)
- Topic / message and the **one takeaway** for the viewer
- Format: `1920x1080` (YouTube/web), `1080x1920` (Reels/TikTok/Shorts), `1080x1080` (feed)
- Length (default 10–20s; social hooks within the first 1.5s)
- Brand: colours, font, logo, CTA text. If none, choose a palette from `references/recipes.md`.

### 2. Write a storyboard as beats
A short table before coding: time range · what the 3D does · what text appears · camera move. Rules of thumb:
- 2.5–4s per beat; one idea per beat; ≤ 7 words of headline on screen at once.
- Every beat has motion in **both** the object and the camera (slow orbit/dolly + handheld drift).
- Transitions overlap by ~0.3–0.5s (next starts before previous fully ends).
- End on a held frame ≥ 1.5s (logo/CTA).

### 3. Build the scene
Copy the template next to where the output should live (not inside the skill folder) and edit it:

```bash
cp .claude/skills/3d-video/templates/explainer.html ./my-video/scene.html
```

Edit the `COPY` / `PALETTE` blocks, then reshape objects and the `update(t)` timeline. Keep the import map and `import * as D from '/__skill/lib/director.js'` (the renderer serves the skill at `/__skill/`).

**The one hard rule — determinism.** `update(t)` must set *all* visible state from `t` alone. No `requestAnimationFrame`, `Date.now()`, `performance.now()`, `Math.random()`, CSS transitions/animations, or physics stepping. Use `D.prog`, `D.track`, `D.envelope`, `D.rng(seed)`. The renderer seeks frames out of order (stills) and must get identical results.

Other rules:
- Text goes in the HTML overlay (`D.el`, `D.reveal`, `D.wordReveal`) — crisp, any web font. Use `D.pinTo` to attach labels to 3D points.
- Lay out for both orientations with `D.isVertical(size)`; in vertical, stack text above/below the object and pull the camera back.
- Keep text inside a safe area: ≥ 6% from edges; for 9:16 keep the bottom ~18% and right ~12% clear (platform UI).
- Load assets (GLTF, textures, fonts) as promises and pass them to `mountScene({ assets })` so rendering waits.

**Cinematic look** (see `cinematic-desk.html`): one strong warm key light through a patterned blocker (window blinds) for striped shadows, a dim cool or warm fill, practical lights (lamp bulb, glowing screen) as `MeshBasicMaterial` with `toneMapped:false` so bloom picks them up, `D.postFX` bloom, `D.filmGrain`, a CSS vignette, fog, and a shot list with hard cuts (brief blur pop on each cut). Screens and UIs are a 2D canvas redrawn from `t` and used as a `CanvasTexture`.

### 4. Check stills before rendering (fast, ~3s)
```bash
node .claude/skills/3d-video/scripts/render.mjs ./my-video/scene.html --stills 1,4,8,12
```
Open the PNGs and look: framing, text overlap, labels in place, nothing off-screen, contrast. For a vertical cut add `--size 1080x1920`. Optionally stitch a contact sheet:
`ffmpeg -i a.png -i b.png -i c.png -i d.png -filter_complex "[0][1][2][3]xstack=inputs=4:layout=0_0|w0_0|0_h0|w0_h0,scale=1920:-1" sheet.png`

Fix and repeat until every beat reads well. This is where quality comes from.

### 5. Render
```bash
node .claude/skills/3d-video/scripts/render.mjs ./my-video/scene.html --out ./my-video/video-16x9.mp4
node .claude/skills/3d-video/scripts/render.mjs ./my-video/scene.html --size 1080x1920 --out ./my-video/video-9x16.mp4
```
Options: `--fps 60`, `--from 3 --to 7` (render a slice), `--audio music.mp3` (trimmed + faded), `--crf 16` (higher quality). Software WebGL renders ~4–5 fps at 1080p, so 15s ≈ 1.5–2 min.

Live preview in a browser: `--serve`, then open the URL with `?play=1` (loop) or `?t=4.2` (freeze).

### 6. Deliver
Give the MP4 path(s), the storyboard, and how to change copy/colours (the `COPY`/`PALETTE` blocks). Offer variants: other aspect ratios, a 6s cut-down, alternate CTA.

## Quality bar
- Soft studio lighting + shadows + subtle fog/particles; avoid flat unlit colours.
- Ease everything (`outCubic`/`outBack` for entrances, `inOutCubic` for camera). Nothing linear except counters.
- Staggered reveals (bars, words, labels) at 0.08–0.25s offsets.
- One accent colour for emphasis, high contrast text (≥ 4.5:1), max two typefaces.
- No idle frames: something drifts, rotates or breathes at all times.

See `references/recipes.md` for shot recipes (product turntable, exploded view, process flow, globe/network, device mockup, logo reveal, kinetic type), more palettes, GLTF loading and troubleshooting.
