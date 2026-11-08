import { LUMA, HASH, BLUR_LUMA } from './lib.js';

export default {
  id: 'scribbles',
  name: 'Scribbles',
  category: 'Print & Ink',
  tags: ['hatching', 'pencil', 'sketch', 'ink', 'lines', 'notepad'],
  params: [
    { id: 'spacing', label: 'Line Spacing', type: 'range', min: 3, max: 24, step: 0.5, default: 7, unit: 'px', section: 'Strokes' },
    { id: 'weight', label: 'Line Weight', type: 'range', min: 0.5, max: 4, step: 0.1, default: 1.3, unit: 'px', section: 'Strokes' },
    { id: 'layers', label: 'Layers', type: 'range', min: 1, max: 4, step: 1, default: 3, section: 'Strokes' },
    { id: 'wobble', label: 'Wobble', type: 'range', min: 0, max: 1, step: 0.01, default: 0.45, section: 'Strokes' },
    { id: 'blur', label: 'Smoothing', type: 'range', min: 0, max: 14, step: 0.5, default: 3, unit: 'px', section: 'Intensity' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#f6f5f1', section: 'Color' },
    { id: 'ink', label: 'Ink', type: 'color', default: '#16171c', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_spacing, u_weight, u_layers, u_wobble, u_blur;
uniform vec3 u_paper, u_ink;
${LUMA}${HASH}${BLUR_LUMA}
float vnoise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float hatch(vec2 pp, float ang, float spacing, float seed) {
  vec2 dir = vec2(cos(ang), sin(ang));
  float s = dot(pp, dir) / spacing + (vnoise(pp / 37.0 + seed) - 0.5) * u_wobble * 3.0;
  float dist = abs(fract(s) - 0.5) * spacing;
  float aa = 0.7 / u_pxScale;
  return 1.0 - smoothstep(u_weight * 0.5 - aa, u_weight * 0.5 + aa, dist);
}

void main() {
  vec2 pp = v_uv * u_resolution / u_pxScale;
  float dk = 1.0 - clamp((blurLuma(v_uv, u_blur) - 0.04) * 1.15, 0.0, 1.0);
  float ink = 0.0;
  for (int i = 0; i < 4; i++) {
    float fi = float(i);
    if (fi >= u_layers) break;
    float t = 0.15 + fi * (0.6 / max(u_layers - 1.0, 1.0));
    float h = hatch(pp, 0.8 + fi * 1.3, u_spacing * (1.0 - 0.08 * fi), fi * 11.0);
    ink = max(ink, h * smoothstep(t - 0.04, t + 0.08, dk));
  }
  outColor = vec4(mix(u_paper, u_ink, ink), 1.0);
}`,
};
