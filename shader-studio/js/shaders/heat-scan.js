import { LUMA, HASH, BLUR_LUMA } from './lib.js';

// Thermal-camera look: a vertical colour gradient (cool top -> hot bottom) on a dark ground,
// with optional defocus, speckle dissolve and film grain.
export default {
  id: 'heat-scan',
  name: 'Heat Scan',
  category: 'Color & Tone',
  tags: ['thermal', 'heat', 'infrared', 'gradient', 'blur', 'speckle', 'grain', 'scan', 'orange', 'blue'],
  params: [
    { id: 'cut', label: 'Cutoff', type: 'range', min: 0, max: 0.8, step: 0.01, default: 0.18, section: 'Subject' },
    { id: 'gain', label: 'Gain', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.4, section: 'Subject' },
    { id: 'blur', label: 'Defocus', type: 'range', min: 0, max: 40, step: 0.5, default: 0, unit: 'px', section: 'Subject' },
    { id: 'speckle', label: 'Speckle Dissolve', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Subject' },
    { id: 'pos', label: 'Gradient Center', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Gradient' },
    { id: 'soft', label: 'Gradient Softness', type: 'range', min: 0.05, max: 1.2, step: 0.01, default: 0.4, section: 'Gradient' },
    { id: 'glow', label: 'Halo', type: 'range', min: 0, max: 1.5, step: 0.01, default: 0.5, section: 'Light' },
    { id: 'grain', label: 'Grain', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Light' },
    { id: 'top', label: 'Top Color', type: 'color', default: '#8fb8ff', section: 'Color' },
    { id: 'bot', label: 'Bottom Color', type: 'color', default: '#ff5a1f', section: 'Color' },
    { id: 'bg', label: 'Background', type: 'color', default: '#1a0909', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cut, u_gain, u_blur, u_speckle, u_pos, u_soft, u_glow, u_grain;
uniform vec3 u_top, u_bot, u_bg;
${LUMA}${HASH}${BLUR_LUMA}
void main() {
  float l = (u_blur > 0.5) ? blurLuma(v_uv, u_blur) : luma(texture(u_image, v_uv).rgb);
  l = clamp(l * u_gain, 0.0, 1.0);
  float m = smoothstep(u_cut, u_cut + 0.25, l);
  m = mix(m, step(hash(floor(v_uv * u_resolution / u_pxScale)), m), u_speckle);
  vec3 grad = mix(u_top, u_bot, smoothstep(u_pos - u_soft * 0.5, u_pos + u_soft * 0.5, 1.0 - v_uv.y));
  vec3 col = mix(u_bg, grad * (0.3 + 0.95 * l), m);
  col += grad * blurLuma(v_uv, 28.0) * u_glow * 0.35 * (1.0 - m * 0.5);
  col += (hash(gl_FragCoord.xy / u_pxScale + 7.0) - 0.5) * u_grain * 0.22;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
