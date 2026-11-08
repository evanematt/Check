import { LUMA } from './lib.js';

// Round LEDs on a grid with bloom, like a dot-matrix signal.
export default {
  id: 'led-matrix',
  name: 'LED Matrix',
  category: 'Glow & Signal',
  tags: ['led', 'dots', 'matrix', 'traffic light', 'signal', 'glow', 'bloom', 'display'],
  params: [
    { id: 'cell', label: 'Pitch', type: 'range', min: 5, max: 36, step: 1, default: 11, unit: 'px', section: 'Grid' },
    { id: 'size', label: 'LED Size', type: 'range', min: 0.15, max: 0.5, step: 0.01, default: 0.36, section: 'Grid' },
    { id: 'gain', label: 'Brightness', type: 'range', min: 0.5, max: 4, step: 0.05, default: 1.8, section: 'Intensity' },
    { id: 'cutoff', label: 'Cutoff', type: 'range', min: 0, max: 0.7, step: 0.01, default: 0.08, section: 'Intensity' },
    { id: 'off', label: 'Unlit LEDs', type: 'range', min: 0, max: 0.5, step: 0.01, default: 0.07, section: 'Intensity' },
    { id: 'glow', label: 'Glow', type: 'range', min: 0, max: 2, step: 0.05, default: 0.9, section: 'Light' },
    { id: 'radius', label: 'Glow Radius', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.4, unit: ' cells', section: 'Light' },
    {
      id: 'mode', label: 'LED Color', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Tint' }, { value: 1, label: 'Source' }],
    },
    { id: 'tint', label: 'Tint', type: 'color', default: '#3dff9a', section: 'Color' },
    { id: 'bg', label: 'Housing', type: 'color', default: '#020806', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_size, u_gain, u_cutoff, u_off, u_glow, u_radius;
uniform int u_mode;
uniform vec3 u_tint, u_bg;
${LUMA}
void main() {
  float cell = max(4.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 ig = floor(px / cell);
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  float aa = 1.0 / cell;
  vec3 col = u_bg;
  for (int j = -2; j <= 2; j++) {
    for (int i = -2; i <= 2; i++) {
      vec2 cc = (ig + vec2(float(i), float(j)) + 0.5) * cell;
      vec3 src = textureLod(u_image, clamp(cc / u_resolution, 0.0, 1.0), lod).rgb;
      float it = clamp((luma(src) * u_gain - u_cutoff) / max(1.0 - u_cutoff, 0.001), 0.0, 1.0);
      vec3 lc = (u_mode == 0) ? u_tint : clamp(src / max(max(src.r, max(src.g, src.b)), 0.001), 0.0, 1.0);
      float d = length(px - cc) / cell;
      if (i == 0 && j == 0) {
        float core = 1.0 - smoothstep(u_size - aa, u_size + aa, d);
        col += lc * core * (u_off * (1.0 - it) + it);
        col += vec3(core * it * it * 0.35);
      }
      col += lc * it * exp(-(d * d) / (u_radius * u_radius)) * u_glow * 0.18;
    }
  }
  outColor = vec4(col, 1.0);
}`,
};
