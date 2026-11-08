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

`mosaic`, `dots` and `scanlines` are placeholders that exercise every control type and an animated style.
Delete them (and their imports) once you add your own. `original` is the passthrough used by "compare".

## Shortcuts

`Ctrl/⌘+Z` undo · `Ctrl/⌘+Shift+Z` redo · hold `C` compare · `R` restyle · `S` shuffle · `0` fit · `+`/`-` zoom ·
scroll/pinch to zoom · drag to pan · double-click a slider to reset it · drop or paste an image to load it.
