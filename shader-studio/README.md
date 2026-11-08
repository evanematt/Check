# Shader Studio

A browser studio for transforming images with GLSL shaders. Vanilla JS + WebGL2, no build step, no dependencies.

## Run

ES modules need a web server (opening `index.html` from disk won't work):

```bash
cd shader-studio
python3 -m http.server 8000   # then open http://localhost:8000
```

## Layout

```
index.html              page shell (top bar · styles panel · stage · controls panel)
css/styles.css          all styling (design tokens at the top)
js/main.js              wiring: engine loop, actions, shortcuts, drag/drop/paste
js/renderer.js          WebGL2 pipeline: style shader -> FBO -> adjust pass -> canvas
js/state.js             store + undo/redo history
js/thumbs.js            renders every style on the current image for the left panel
js/images.js            sample images, file loading, preview sizing
js/shaders/             <- your shaders live here
js/ui/                  topbar, styles-panel, controls-panel, stage, dock, modals, components
```

## Adding a shader

1. Copy `js/shaders/_template.js` to `js/shaders/<name>.js` and edit it.
2. Import it in `js/shaders/index.js` and add it to `RAW_SHADERS`.

That's it. The tile + thumbnail, search, category grouping, Shuffle/Restyle, favourites, recipes and the
right-hand controls are all generated from the shader's metadata.

A shader is `{ id, name, category, tags, animated, params, fragment }`.

**Uniforms provided to every shader**

| Uniform | Meaning |
| --- | --- |
| `sampler2D u_image` | source image (mipmapped, y-up) |
| `vec2 u_resolution` | output size in px |
| `vec2 u_imageSize` | source image size in px |
| `float u_time` | seconds, scaled by the Motion speed |
| `float u_pxScale` | output px / preview px - multiply px values by it so 2x–4x exports match the preview |
| `vec2 u_mouse` | pointer over the canvas, 0..1, y-up |
| `in vec2 v_uv`, `out vec4 outColor` | 0..1 coordinates, y-up / the result |

**Accepted `fragment` formats** (auto-detected): a bare `void main()`, a Shadertoy-style
`mainImage(out vec4, in vec2)` (with `iResolution`, `iTime`, `iFrame`, `iMouse`, `iChannel0`), or a full
`#version 300 es` source used verbatim.

**`params`** become right-panel controls *and* uniforms (default `u_<id>`):

| `type` | GLSL | extra fields |
| --- | --- | --- |
| `range` | `float` | `min`, `max`, `step`, `default`, `unit` |
| `select` | `int` | `options: [{ value, label }]` |
| `toggle` | `bool` | |
| `color` | `vec3` | `default: '#rrggbb'` |

`section` groups controls under a `/ SECTION` heading; `randomize: false` excludes a param from Shuffle.
Compile errors are shown as a toast and logged to the console with the offending shader id.

Shared GLSL helpers (`luma`, `hash`, `blurLuma`, `sobel`) live in `js/shaders/lib.js`; interpolate them into a
fragment string with `${LUMA}${SOBEL}`.

**Styles included**

| Category | Styles |
| --- | --- |
| ASCII & Text | ASCII (procedural 5×7 glyphs) |
| Pixel & Blocks | Glyph Tiles, Pixel Sketch, Low Poly, Mosaic, Halftone Dots |
| Print & Ink | Dither (Bayer 2/4/8 + noise), Scribbles (hatching), Hope (3/4-tone poster) |
| Technical | Blueprint, HUD Overlay (heat-grid patches, target box, brackets, ruler, number readout) |
| Glitch & Stretch | Pixel Stretch (random boxes with stretched pixels, outlines, numbered labels) |
| Silhouette | Silhouette Glitch (blocky smear growing out of the subject), Silhouette Fill (flat or dotted fill) |
| Glow & Signal | LED Matrix (round LEDs with bloom), Dream Glow (soft focus + haze), Hologram (animated), Chroma, CRT Signal (animated) |
| Color & Tone | X-Ray, Thermal Map (5-stop gradient map with banding, solarize, edge heat) |

`original` is the passthrough used by "compare".

**Silhouette styles** need a subject mask. Pick one in *Subject Mask*: auto (nearest of the four corner colours),
a key color, the image's alpha channel (upload a transparent PNG cut-out), or a dark/light brightness threshold.
Person cut-outs from busy photos need real segmentation, which isn't included.

`lib.js` also has a 3×5 digit font (`FONT3X5`), `bspRect` (random rectangles), `subjectMask` + `maskParams()` and `h11`/`h22` hashes.

## AI restyle (Generative tab)

Left panel -> **Generative**. Describe a style (or click a preset such as *Low-poly 3D*), choose how closely to follow the photo
(Loose / Balanced / Strict) and press Generate. The model restyles the photo; the result is added to **Versions** and
becomes the working image, so you can switch back to **Realtime** and put any shader style on top. "Build on the current
version" feeds the last result back in for iterative edits.

Providers live in `js/ai/providers.js` (`gemini`, `openai`). Each is one `edit({ apiKey, model, baseUrl, prompt, image, signal })`
function that returns an image Blob, so adding another is a few lines. Model names are editable because they change often.

- Calls go **straight from the browser** with your own key, which is stored in `localStorage`. That is fine locally.
  For a shared or public deployment, put the calls behind your own proxy and set its address as *Base URL*.
- The photo is downscaled to 1024 px on its long side before sending.
- The hosted preview blocks outside requests, so the AI tab only works when you run the app yourself.
- Tested against a local mock of the Gemini API (request shape, errors, cancel, text-only replies), not against the live APIs.

## Shortcuts

`Ctrl/⌘+Z` undo · `Ctrl/⌘+Shift+Z` redo · hold `C` compare · `R` restyle · `S` shuffle · `0` fit · `+`/`-` zoom ·
scroll/pinch to zoom · drag to pan · double-click a slider to reset it · drop or paste an image to load it.
