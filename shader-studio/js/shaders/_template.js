// Copy this file to js/shaders/<your-name>.js, then add it to js/shaders/index.js.
//
// ─── Uniforms always available in `fragment` ─────────────────────────────
//   sampler2D u_image       source image (mipmapped, y-up)
//   vec2      u_resolution  output size in px
//   vec2      u_imageSize   source image size in px
//   float     u_time        seconds, scaled by the Motion speed slider
//   float     u_pxScale     output px / preview px. Multiply any value in px by it so
//                           exports at 2x/3x/4x look the same as the preview
//   vec2      u_mouse       pointer over the canvas, 0..1, y-up
//   in vec2   v_uv          0..1, y-up        out vec4 outColor
//
// Accepted fragment formats (auto-detected):
//   • bare   `void main() { ... outColor = ...; }`   (prelude above is prepended)
//   • Shadertoy `void mainImage(out vec4 fragColor, in vec2 fragCoord)`
//     (iResolution, iTime, iFrame, iMouse, iChannel0 are provided)
//   • full GLSL ES 3.00 source starting with `#version 300 es` (used verbatim,
//     but it must declare its own `in vec2 v_uv;` and uniforms)
//
// ─── Params → right-hand controls ────────────────────────────────────────
//   Each param becomes a control AND a uniform (default name `u_<id>`, or set `uniform`).
//   type 'range'  -> `uniform float u_x;`   { min, max, step, default, unit? }
//   type 'select' -> `uniform int   u_x;`   { options: [{ value: 0, label: 'A' }, ...] }
//   type 'toggle' -> `uniform bool  u_x;`
//   type 'color'  -> `uniform vec3  u_x;`   default is a '#rrggbb' string
//   `section` groups controls under a "/ SECTION" heading (in order of first use).
//   `randomize: false` keeps a param out of the Shuffle button.
export default {
  id: 'my-style', // unique, kebab-case
  name: 'My Style', // shown under the thumbnail
  category: 'Custom', // groups tiles in the left panel
  tags: ['example'], // searchable
  animated: false, // true -> re-renders every frame while Motion is playing
  params: [
    { id: 'amount', label: 'Amount', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Intensity' },
  ],
  fragment: /* glsl */ `
uniform float u_amount;

void main() {
  vec3 src = texture(u_image, v_uv).rgb;
  outColor = vec4(mix(src, 1.0 - src, u_amount), 1.0);
}`,
};
