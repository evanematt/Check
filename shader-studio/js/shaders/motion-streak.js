import { LUMA, HASH } from './lib.js';

// Directional / radial smear that lets bright highlights drag further, with colour fringes.
export default {
  id: 'motion-streak',
  name: 'Motion Streak',
  category: 'Glitch & Stretch',
  tags: ['motion blur', 'streak', 'smear', 'radial', 'zoom', 'highlights', 'chromatic', 'overexposed'],
  params: [
    {
      id: 'type', label: 'Direction', type: 'select', default: 0, section: 'Streak',
      options: [{ value: 0, label: 'Linear' }, { value: 1, label: 'Radial' }],
    },
    { id: 'angle', label: 'Angle', type: 'range', min: 0, max: 180, step: 1, default: 90, unit: '°', section: 'Streak' },
    { id: 'length', label: 'Length', type: 'range', min: 0, max: 300, step: 1, default: 60, unit: 'px', section: 'Streak' },
    { id: 'cx', label: 'Center X', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Streak' },
    { id: 'cy', label: 'Center Y', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Streak' },
    { id: 'boost', label: 'Highlight Drag', type: 'range', min: 0, max: 3, step: 0.05, default: 1.4, section: 'Light' },
    { id: 'exposure', label: 'Exposure', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.35, section: 'Light' },
    { id: 'chroma', label: 'Color Fringe', type: 'range', min: 0, max: 3, step: 0.05, default: 1, section: 'Light' },
    { id: 'grain', label: 'Grain', type: 'range', min: 0, max: 1, step: 0.01, default: 0.4, section: 'Light' },
  ],
  fragment: /* glsl */ `
uniform int u_type;
uniform float u_angle, u_length, u_cx, u_cy, u_boost, u_exposure, u_chroma, u_grain;
${LUMA}${HASH}
void main() {
  float len = u_length * u_pxScale;
  vec2 dir = vec2(cos(radians(u_angle)), sin(radians(u_angle)));
  vec2 c = vec2(u_cx, u_cy);
  float ch = u_chroma * 0.2;
  float jit = hash(gl_FragCoord.xy);
  vec3 acc = vec3(0.0);
  float wsum = 0.0;
  for (int i = 0; i < 24; i++) {
    float t = (float(i) + jit) / 24.0 - 0.5;
    vec2 off = (u_type == 0) ? dir * t * len / u_resolution : (v_uv - c) * t * (len / (0.5 * u_resolution.x));
    vec3 s = vec3(texture(u_image, v_uv + off * (1.0 + ch)).r,
                  texture(u_image, v_uv + off).g,
                  texture(u_image, v_uv + off * (1.0 - ch)).b);
    float w = 1.0 + u_boost * 8.0 * smoothstep(0.55, 1.0, luma(s));
    acc += s * w;
    wsum += w;
  }
  vec3 col = acc / wsum * u_exposure;
  col += (hash(gl_FragCoord.xy / u_pxScale + 3.0) - 0.5) * u_grain * 0.2;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
