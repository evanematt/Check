import { LUMA, MASK, maskParams } from './lib.js';

// Flat or dotted colour fill inside the subject, over the photo or a solid background.
export default {
  id: 'silhouette-fill',
  name: 'Silhouette Fill',
  category: 'Silhouette',
  tags: ['silhouette', 'cutout', 'flat', 'dots', 'poster', 'stencil', 'fill'],
  params: [
    ...maskParams('Subject'),
    {
      id: 'style', label: 'Fill', type: 'select', default: 0, section: 'Fill',
      options: [{ value: 0, label: 'Flat' }, { value: 1, label: 'Dots' }],
    },
    {
      id: 'bgMode', label: 'Background', type: 'select', default: 0, section: 'Fill',
      options: [{ value: 0, label: 'Image' }, { value: 1, label: 'Solid' }],
    },
    { id: 'dot', label: 'Dot Spacing', type: 'range', min: 6, max: 60, step: 1, default: 18, unit: 'px', section: 'Fill' },
    { id: 'desat', label: 'Desaturate Image', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Fill' },
    { id: 'fill', label: 'Fill Color', type: 'color', default: '#d9432a', section: 'Color' },
    { id: 'dotColor', label: 'Dot Color', type: 'color', default: '#ffffff', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform int u_style, u_bgMode;
uniform float u_dot, u_desat;
uniform vec3 u_fill, u_dotColor;
${LUMA}${MASK}
void main() {
  vec3 img = texture(u_image, v_uv).rgb;
  img = mix(img, vec3(luma(img)), u_desat);
  vec3 base = (u_bgMode == 1) ? u_fill : img;
  vec3 col = base;
  if (u_style == 0) {
    col = mix(base, u_fill, subjectMask(v_uv));
  } else {
    float cell = max(4.0, u_dot * u_pxScale);
    vec2 px = v_uv * u_resolution;
    vec2 c = (floor(px / cell) + 0.5) * cell;
    vec2 o = vec2(0.35 * cell) / u_resolution;
    vec2 cu = c / u_resolution;
    float m = (subjectMask(cu) + subjectMask(cu + vec2(o.x, 0.0)) + subjectMask(cu - vec2(o.x, 0.0))
             + subjectMask(cu + vec2(0.0, o.y)) + subjectMask(cu - vec2(0.0, o.y))) * 0.2;
    float rad = 0.4 * smoothstep(0.3, 0.7, m);
    float d = length(px - c) / cell;
    float aa = 1.0 / cell;
    col = mix(base, u_dotColor, 1.0 - smoothstep(rad - aa, rad + aa, d));
  }
  outColor = vec4(col, 1.0);
}`,
};
