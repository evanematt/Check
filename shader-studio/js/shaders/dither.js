import { HASH } from './lib.js';

export default {
  id: 'dither',
  name: 'Dither',
  category: 'Print & Ink',
  tags: ['dither', 'bayer', 'halftone', '1-bit', 'retro', 'mac'],
  params: [
    { id: 'pixel', label: 'Pixel Size', type: 'range', min: 1, max: 12, step: 1, default: 3, unit: 'px', section: 'Pattern' },
    {
      id: 'pattern', label: 'Pattern', type: 'select', default: 3, section: 'Pattern',
      options: [{ value: 3, label: 'Bayer 8×8' }, { value: 2, label: 'Bayer 4×4' }, { value: 1, label: 'Bayer 2×2' }, { value: 0, label: 'Noise' }],
    },
    { id: 'levels', label: 'Levels', type: 'range', min: 2, max: 8, step: 1, default: 2, section: 'Pattern' },
    { id: 'invert', label: 'Invert Mapping', type: 'toggle', default: false, section: 'Color' },
    {
      id: 'mode', label: 'Color Mode', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Two-tone' }, { value: 1, label: 'Per channel' }],
    },
    { id: 'dark', label: 'Dark', type: 'color', default: '#0a0a0c', section: 'Color' },
    { id: 'light', label: 'Light', type: 'color', default: '#f4f4f0', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_pixel, u_levels;
uniform int u_pattern, u_mode;
uniform bool u_invert;
uniform vec3 u_dark, u_light;
${HASH}
float bayer(ivec2 p, int levels) {
  int v = 0;
  for (int i = 0; i < 3; i++) {
    if (i >= levels) break;
    int xb = (p.x >> i) & 1;
    int yb = (p.y >> i) & 1;
    v = (v << 2) | (((xb ^ yb) << 1) | yb);
  }
  return (float(v) + 0.5) / float(1 << (2 * levels));
}

void main() {
  float cell = max(1.0, floor(u_pixel * u_pxScale + 0.5));
  vec2 id = floor(floor(v_uv * u_resolution) / cell);
  vec2 c = (id + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  if (u_invert) src = 1.0 - src;
  float thr = (u_pattern == 0) ? hash(id) : bayer(ivec2(id), u_pattern);
  float n = u_levels - 1.0;
  vec3 q;
  if (u_mode == 0) {
    float l = dot(src, vec3(0.2126, 0.7152, 0.0722));
    q = vec3(clamp(floor(l * n + thr), 0.0, n) / n);
  } else {
    q = clamp(floor(src * n + vec3(thr)), 0.0, n) / n;
  }
  outColor = vec4(mix(u_dark, u_light, q), 1.0);
}`,
};
