import { LUMA, BLUR_LUMA } from './lib.js';

export default {
  id: 'hope',
  name: 'Hope',
  category: 'Print & Ink',
  tags: ['poster', 'posterize', 'tones', 'screenprint', 'duotone', 'flat'],
  params: [
    {
      id: 'tones', label: 'Tones', type: 'select', default: 4, section: 'Tones',
      options: [{ value: 4, label: '4 tones' }, { value: 3, label: '3 tones' }],
    },
    { id: 'smooth', label: 'Smoothing', type: 'range', min: 0, max: 14, step: 0.5, default: 3, unit: 'px', section: 'Tones' },
    { id: 'contrast', label: 'Contrast', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.4, section: 'Tones' },
    { id: 'bias', label: 'Bias', type: 'range', min: -0.3, max: 0.3, step: 0.01, default: 0, section: 'Tones' },
    { id: 'c1', label: 'Shadow', type: 'color', default: '#00334e', section: 'Palette' },
    { id: 'c2', label: 'Midtone', type: 'color', default: '#d6413b', section: 'Palette' },
    { id: 'c3', label: 'Light mid (4 tones)', type: 'color', default: '#78a5b8', section: 'Palette' },
    { id: 'c4', label: 'Highlight', type: 'color', default: '#fbe9b7', section: 'Palette' },
  ],
  fragment: /* glsl */ `
uniform int u_tones;
uniform float u_smooth, u_contrast, u_bias;
uniform vec3 u_c1, u_c2, u_c3, u_c4;
${LUMA}${BLUR_LUMA}
void main() {
  float l = blurLuma(v_uv, u_smooth);
  l = clamp((l - 0.5) * u_contrast + 0.5 + u_bias, 0.0, 1.0);
  float w = fwidth(l) * 0.75 + 0.0005;
  vec3 col;
  if (u_tones == 3) {
    col = mix(u_c1, u_c2, smoothstep(0.34 - w, 0.34 + w, l));
    col = mix(col, u_c4, smoothstep(0.67 - w, 0.67 + w, l));
  } else {
    col = mix(u_c1, u_c2, smoothstep(0.25 - w, 0.25 + w, l));
    col = mix(col, u_c3, smoothstep(0.50 - w, 0.50 + w, l));
    col = mix(col, u_c4, smoothstep(0.75 - w, 0.75 + w, l));
  }
  outColor = vec4(col, 1.0);
}`,
};
