import { LUMA, H11, BSP, MASK, maskParams } from './lib.js';

// Blocky smear that grows out of the subject's silhouette.
export default {
  id: 'silhouette-glitch',
  name: 'Silhouette Glitch',
  category: 'Silhouette',
  tags: ['glitch', 'smear', 'silhouette', 'cutout', 'datamosh', 'outline', 'blocks'],
  params: [
    ...maskParams('Subject'),
    { id: 'spread', label: 'Smear Distance', type: 'range', min: 0, max: 400, step: 1, default: 100, unit: 'px', section: 'Smear' },
    { id: 'hshare', label: 'Horizontal Share', type: 'range', min: 0, max: 1, step: 0.01, default: 0.55, section: 'Smear' },
    { id: 'seed', label: 'Seed', type: 'range', min: 1, max: 99, step: 1, default: 11, section: 'Blocks' },
    { id: 'depth', label: 'Subdivisions', type: 'range', min: 2, max: 10, step: 1, default: 9, section: 'Blocks' },
    { id: 'stop', label: 'Size Variety', type: 'range', min: 0, max: 0.8, step: 0.01, default: 0.2, section: 'Blocks' },
    { id: 'smear', label: 'Smear Color', type: 'color', default: '#2b3cff', section: 'Color' },
    { id: 'grain', label: 'Grain', type: 'range', min: 0, max: 1, step: 0.01, default: 0.25, section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_spread, u_hshare, u_seed, u_depth, u_stop, u_grain;
uniform vec3 u_smear;
${LUMA}${H11}${BSP}${MASK}
float hash2(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }

void main() {
  vec2 uv = v_uv;
  float id;
  vec4 r = bspRect(uv, u_seed, u_depth, u_stop, u_resolution.x / u_resolution.y, id);
  float m0 = subjectMask(uv);

  bool horiz = h11(id * 3.3 + 1.7) < u_hshare;
  float live = step(0.25, h11(id * 8.1 + 6.0));
  float reach = u_spread * u_pxScale * h11(id * 5.9 + 2.2) * live;
  float side = h11(id * 1.9 + 9.0); // <.33 left only, <.66 right only, else both
  vec2 dir = horiz ? vec2(1.0, 0.0) : vec2(0.0, 1.0);

  float dil = m0;
  for (int i = -8; i <= 8; i++) {
    float k = float(i) / 8.0;
    if ((side < 0.33 && k > 0.0) || (side >= 0.33 && side < 0.66 && k < 0.0)) continue;
    dil = max(dil, subjectMask(uv + dir * k * reach / u_resolution));
  }
  float smear = clamp(dil - m0, 0.0, 1.0);

  vec3 col = texture(u_image, uv).rgb;
  col = mix(col, u_smear, smear);
  col += (hash2(gl_FragCoord.xy / u_pxScale) - 0.5) * u_grain * 0.22;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
