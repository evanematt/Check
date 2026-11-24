import { LUMA, H11, BSP, BLUR_LUMA } from './lib.js';

// Vertical-bar pixel display with glow, plus "detection" boxes on the bright regions.
export default {
  id: 'column-display',
  name: 'Column Display',
  category: 'Glow & Signal',
  tags: ['display', 'columns', 'vertical lines', 'cyan', 'led', 'detection', 'boxes', 'bounding box', 'glow'],
  params: [
    { id: 'cell', label: 'Pixel Pitch', type: 'range', min: 4, max: 28, step: 1, default: 8, unit: 'px', section: 'Display' },
    { id: 'gain', label: 'Brightness', type: 'range', min: 0.5, max: 3.5, step: 0.05, default: 1.6, section: 'Display' },
    { id: 'cutoff', label: 'Cutoff', type: 'range', min: 0, max: 0.7, step: 0.01, default: 0.1, section: 'Display' },
    { id: 'glow', label: 'Glow', type: 'range', min: 0, max: 2.5, step: 0.05, default: 1.2, section: 'Display' },
    { id: 'boxOn', label: 'Detection Boxes', type: 'toggle', default: true, section: 'Boxes' },
    { id: 'boxes', label: 'Box Density', type: 'range', min: 0, max: 1, step: 0.01, default: 0.55, section: 'Boxes' },
    { id: 'seed', label: 'Seed', type: 'range', min: 1, max: 99, step: 1, default: 9, section: 'Boxes' },
    {
      id: 'mode', label: 'Bar Color', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Tint' }, { value: 1, label: 'Source' }],
    },
    { id: 'tint', label: 'Tint', type: 'color', default: '#35e0ff', section: 'Color' },
    { id: 'boxCol', label: 'Box Color', type: 'color', default: '#d7f4ff', section: 'Color' },
    { id: 'bg', label: 'Background', type: 'color', default: '#000308', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_gain, u_cutoff, u_glow, u_boxes, u_seed;
uniform bool u_boxOn;
uniform int u_mode;
uniform vec3 u_tint, u_boxCol, u_bg;
${LUMA}${H11}${BSP}${BLUR_LUMA}
void main() {
  float cell = max(3.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 id = floor(px / cell), f = fract(px / cell);
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, (id + 0.5) * cell / u_resolution, lod).rgb;
  float l = clamp(luma(src) * u_gain, 0.0, 1.0);
  float it = clamp((l - u_cutoff) / max(1.0 - u_cutoff, 0.001), 0.0, 1.0);
  float half_ = 0.5 * mix(0.14, 0.92, it);
  float aa = 1.0 / cell;
  float bar = (1.0 - smoothstep(half_ - aa, half_ + aa, abs(f.x - 0.5))) * step(0.06, f.y) * step(f.y, 0.94) * step(0.03, it);
  vec3 lc = (u_mode == 0) ? u_tint : clamp(src / max(max(src.r, max(src.g, src.b)), 0.001), 0.0, 1.0);
  vec3 col = u_bg + lc * bar * (0.35 + 0.9 * it);
  col += lc * blurLuma(v_uv, cell * 2.5) * u_glow * 0.55;

  if (u_boxOn) {
    float rid;
    vec4 r = bspRect(v_uv, u_seed, 7.0, 0.25, u_resolution.x / u_resolution.y, rid);
    vec2 ctr = (r.xy + r.zw) * 0.5, hs = (r.zw - r.xy) * 0.25;
    float m = (luma(textureLod(u_image, ctr, 4.0).rgb) + luma(textureLod(u_image, ctr + vec2(hs.x, 0.0), 4.0).rgb)
             + luma(textureLod(u_image, ctr - vec2(hs.x, 0.0), 4.0).rgb) + luma(textureLod(u_image, ctr + vec2(0.0, hs.y), 4.0).rgb)
             + luma(textureLod(u_image, ctr - vec2(0.0, hs.y), 4.0).rgb)) * 0.2 * u_gain;
    if (m > 0.22 && h11(rid * 5.3 + 1.0) < u_boxes) {
      vec2 toEdge = vec2(min(v_uv.x - r.x, r.z - v_uv.x), min(v_uv.y - r.y, r.w - v_uv.y)) * u_resolution;
      if (min(toEdge.x, toEdge.y) < max(1.0, u_pxScale)) col = mix(col, u_boxCol, 0.9);
      vec2 sizePx = (r.zw - r.xy) * u_resolution / u_pxScale;
      if (sizePx.x > 44.0) {
        vec2 q = vec2(v_uv.x - r.x, r.w - v_uv.y) * u_resolution / u_pxScale;
        if (q.y > 4.0 && q.y < 6.0 && q.x > 4.0 && q.x < 4.0 + 10.0 + 22.0 * h11(rid * 9.1)) col = u_boxCol;
        if (q.y > 9.0 && q.y < 11.0 && q.x > 4.0 && q.x < 4.0 + 8.0 + 12.0 * h11(rid * 4.4)) col = u_boxCol * 0.8;
      }
    }
  }
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
