import { LUMA, BLUR_LUMA, SOBEL } from './lib.js';

// Luminance -> 5-stop colour ramp, with optional banding, solarising and edge heat.
export default {
  id: 'thermal',
  name: 'Thermal Map',
  category: 'Color & Tone',
  tags: ['gradient map', 'thermal', 'heat', 'solarize', 'false color', 'palette', 'infrared'],
  params: [
    { id: 'smooth', label: 'Smoothing', type: 'range', min: 0, max: 14, step: 0.5, default: 1.5, unit: 'px', section: 'Map' },
    { id: 'contrast', label: 'Contrast', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.2, section: 'Map' },
    { id: 'fold', label: 'Solarize', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Map' },
    { id: 'shift', label: 'Palette Shift', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Map' },
    { id: 'bands', label: 'Banding (0 = smooth)', type: 'range', min: 0, max: 16, step: 1, default: 0, section: 'Map' },
    { id: 'edge', label: 'Edge Heat', type: 'range', min: 0, max: 3, step: 0.05, default: 1, section: 'Map' },
    { id: 'c1', label: 'Stop 1 (dark)', type: 'color', default: '#05070d', section: 'Palette' },
    { id: 'c2', label: 'Stop 2', type: 'color', default: '#1f55d6', section: 'Palette' },
    { id: 'c3', label: 'Stop 3', type: 'color', default: '#9be822', section: 'Palette' },
    { id: 'c4', label: 'Stop 4', type: 'color', default: '#ff6a1a', section: 'Palette' },
    { id: 'c5', label: 'Stop 5 (light)', type: 'color', default: '#f4f6ee', section: 'Palette' },
  ],
  fragment: /* glsl */ `
uniform float u_smooth, u_contrast, u_fold, u_shift, u_bands, u_edge;
uniform vec3 u_c1, u_c2, u_c3, u_c4, u_c5;
${LUMA}${BLUR_LUMA}${SOBEL}
vec3 ramp(float t) {
  t = clamp(t, 0.0, 1.0) * 4.0;
  if (t < 1.0) return mix(u_c1, u_c2, t);
  if (t < 2.0) return mix(u_c2, u_c3, t - 1.0);
  if (t < 3.0) return mix(u_c3, u_c4, t - 2.0);
  return mix(u_c4, u_c5, t - 3.0);
}
void main() {
  float l = blurLuma(v_uv, u_smooth);
  l = clamp((l - 0.5) * u_contrast + 0.5, 0.0, 1.0);
  l = mix(l, 1.0 - abs(2.0 * l - 1.0), u_fold);
  if (u_shift > 0.0) l = fract(l + u_shift);
  if (u_bands > 0.5) l = floor(l * u_bands + 0.5) / u_bands;
  vec3 col = ramp(l);
  float e = length(sobel(v_uv, 1.2 * u_pxScale / u_resolution));
  col = mix(col, u_c4, smoothstep(0.1, 0.7, e * u_edge) * 0.85);
  outColor = vec4(col, 1.0);
}`,
};
