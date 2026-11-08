import { LUMA, H11, H22, MASK, maskParams } from './lib.js';

// Fills the subject with a swarm of mouse-pointer arrows.
export default {
  id: 'cursor-fill',
  name: 'Cursor Fill',
  category: 'Silhouette',
  tags: ['cursor', 'pointer', 'arrows', 'windows', 'silhouette', 'icons', 'pattern', 'fill'],
  params: [
    ...maskParams('Subject'),
    { id: 'invert', label: 'Invert Mask', type: 'toggle', default: false, section: 'Subject' },
    { id: 'cell', label: 'Spacing', type: 'range', min: 8, max: 44, step: 1, default: 18, unit: 'px', section: 'Arrows' },
    { id: 'size', label: 'Arrow Size', type: 'range', min: 0.6, max: 1.8, step: 0.05, default: 1.3, unit: ' cells', section: 'Arrows' },
    { id: 'jitter', label: 'Position Jitter', type: 'range', min: 0, max: 1, step: 0.01, default: 0.6, section: 'Arrows' },
    { id: 'rot', label: 'Rotation Jitter', type: 'range', min: 0, max: 1, step: 0.01, default: 0.15, section: 'Arrows' },
    { id: 'outline', label: 'Outline', type: 'range', min: 0, max: 3, step: 0.1, default: 1.2, unit: 'px', section: 'Arrows' },
    {
      id: 'bgMode', label: 'Background', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Image' }, { value: 1, label: 'Solid' }],
    },
    { id: 'dim', label: 'Dim Image', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Color' },
    { id: 'bg', label: 'Solid Color', type: 'color', default: '#050509', section: 'Color' },
    { id: 'fill', label: 'Arrow Fill', type: 'color', default: '#ffffff', section: 'Color' },
    { id: 'edge', label: 'Arrow Outline', type: 'color', default: '#000000', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform bool u_invert;
uniform int u_bgMode;
uniform float u_cell, u_size, u_jitter, u_rot, u_outline, u_dim;
uniform vec3 u_bg, u_fill, u_edge;
${LUMA}${H11}${H22}${MASK}

// classic pointer: tip at the origin, body pointing down (y up), 1 unit tall
const vec2 P[7] = vec2[7](
  vec2(0.0, 0.0), vec2(0.0, -16.0 / 19.0), vec2(4.0 / 19.0, -12.0 / 19.0), vec2(7.0 / 19.0, -1.0),
  vec2(9.0 / 19.0, -18.0 / 19.0), vec2(6.0 / 19.0, -11.0 / 19.0), vec2(11.0 / 19.0, -11.0 / 19.0));

float sdPoly(vec2 p) {
  float d = dot(p - P[0], p - P[0]);
  float s = 1.0;
  for (int i = 0; i < 7; i++) {
    int j = (i == 0) ? 6 : i - 1;
    vec2 e = P[j] - P[i], w = p - P[i];
    vec2 b = w - e * clamp(dot(w, e) / dot(e, e), 0.0, 1.0);
    d = min(d, dot(b, b));
    bool c1 = p.y >= P[i].y, c2 = p.y < P[j].y, c3 = e.x * w.y > e.y * w.x;
    if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) s = -s;
  }
  return s * sqrt(d);
}

void main() {
  vec3 base = texture(u_image, v_uv).rgb * (1.0 - u_dim);
  vec3 col = (u_bgMode == 1) ? u_bg : base;
  float cell = max(6.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 ig = floor(px / cell);
  float unit = u_size * cell;
  float outPx = u_outline * u_pxScale;

  for (int j = -1; j <= 2; j++) {
    for (int i = -2; i <= 1; i++) {
      vec2 id = ig + vec2(float(i), float(j));
      vec2 anchor = (id + 0.5 + (h22(id) - 0.5) * u_jitter) * cell;
      float m = subjectMask(clamp(anchor / u_resolution, 0.0, 1.0));
      if (u_invert) m = 1.0 - m;
      if (m < 0.5) continue;
      float ang = (h11(dot(id, vec2(7.0, 13.0)) + 1.0) - 0.5) * u_rot * 3.14159;
      vec2 d = px - anchor;
      float cs = cos(ang), sn = sin(ang);
      vec2 p = vec2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y) / unit;
      float dpx = sdPoly(p) * unit;
      col = mix(col, u_edge, 1.0 - smoothstep(outPx - 0.7, outPx + 0.7, dpx));
      col = mix(col, u_fill, 1.0 - smoothstep(-0.7, 0.7, dpx));
    }
  }
  outColor = vec4(col, 1.0);
}`,
};
