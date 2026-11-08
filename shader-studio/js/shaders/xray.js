import { LUMA, HASH, BLUR_LUMA } from './lib.js';

export default {
  id: 'xray',
  name: 'X-Ray',
  category: 'Color & Tone',
  tags: ['xray', 'x-ray', 'radiology', 'skeleton', 'invert', 'blue', 'medical'],
  params: [
    { id: 'invert', label: 'Invert', type: 'toggle', default: true, section: 'Density' },
    { id: 'contrast', label: 'Contrast', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.6, section: 'Density' },
    { id: 'detail', label: 'Bone Detail', type: 'range', min: 0, max: 3, step: 0.05, default: 1.2, section: 'Density' },
    { id: 'glow', label: 'Glow', type: 'range', min: 0, max: 1.5, step: 0.01, default: 0.5, section: 'Light' },
    { id: 'noise', label: 'Film Noise', type: 'range', min: 0, max: 1, step: 0.01, default: 0.25, section: 'Light' },
    { id: 'tint', label: 'Tint', type: 'color', default: '#9bb0ff', section: 'Color' },
    { id: 'shadow', label: 'Shadow', type: 'color', default: '#02030a', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform bool u_invert;
uniform float u_contrast, u_detail, u_glow, u_noise;
uniform vec3 u_tint, u_shadow;
${LUMA}${HASH}${BLUR_LUMA}
float dens(float l) { return u_invert ? 1.0 - l : l; }
void main() {
  float l = dens(luma(texture(u_image, v_uv).rgb));
  float local = dens(blurLuma(v_uv, 6.0));
  float x = clamp((l + (l - local) * u_detail * 2.5 - 0.5) * u_contrast + 0.5, 0.0, 1.0);
  vec3 col = mix(u_shadow, u_tint, pow(x, 1.3));
  col += vec3(smoothstep(0.75, 1.0, x) * 0.35);
  col += u_tint * dens(blurLuma(v_uv, 16.0)) * u_glow * 0.35;
  col += (hash(gl_FragCoord.xy / u_pxScale) - 0.5) * u_noise * 0.12;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
