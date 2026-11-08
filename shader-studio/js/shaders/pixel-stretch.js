import { H11, BSP, FONT3X5 } from './lib.js';


export default {
  id: 'pixel-stretch',
  name: 'Pixel Stretch',
  category: 'Glitch & Stretch',
  tags: ['stretch', 'smear', 'boxes', 'glitch', 'labels', 'datamosh', 'streak', 'annotation'],
  params: [
    { id: 'seed', label: 'Seed', type: 'range', min: 1, max: 99, step: 1, default: 7, section: 'Layout' },
    { id: 'depth', label: 'Subdivisions', type: 'range', min: 2, max: 10, step: 1, default: 7, section: 'Layout' },
    { id: 'stop', label: 'Size Variety', type: 'range', min: 0, max: 0.8, step: 0.01, default: 0.3, section: 'Layout' },
    { id: 'amount', label: 'Stretch Amount', type: 'range', min: 0, max: 1, step: 0.01, default: 0.6, section: 'Stretch' },
    { id: 'hshare', label: 'Horizontal Share', type: 'range', min: 0, max: 1, step: 0.01, default: 0.45, section: 'Stretch' },
    { id: 'boxes', label: 'Extra Boxes', type: 'range', min: 0, max: 1, step: 0.01, default: 0.3, section: 'Annotations' },
    { id: 'outline', label: 'Outlines', type: 'toggle', default: true, section: 'Annotations' },
    { id: 'labels', label: 'Labels', type: 'toggle', default: true, section: 'Annotations' },
    { id: 'labelSize', label: 'Label Size', type: 'range', min: 1, max: 4, step: 0.5, default: 2, section: 'Annotations' },
    { id: 'line', label: 'Outline Color', type: 'color', default: '#a9a9a9', section: 'Annotations' },
    { id: 'ink', label: 'Label Ink', type: 'color', default: '#000000', section: 'Annotations' },
  ],
  fragment: /* glsl */ `
uniform float u_seed, u_depth, u_stop, u_amount, u_hshare, u_boxes, u_labelSize;
uniform bool u_outline, u_labels;
uniform vec3 u_line, u_ink;
${H11}${BSP}
${FONT3X5}

void main() {
  vec2 uv = v_uv;
  float id;
  vec4 r = bspRect(uv, u_seed, u_depth, u_stop, u_resolution.x / u_resolution.y, id);
  bool stretched = h11(id * 9.1 + 0.3) < u_amount;
  bool horiz = h11(id * 3.3 + 1.7) < u_hshare;
  bool shown = stretched || h11(id * 2.9 + 8.0) < u_boxes;

  vec2 suv = uv;
  float a = h11(id * 7.7 + 4.1) * 0.9;
  if (stretched) {
    if (horiz) suv.x = mix(r.x, r.z, a);
    else       suv.y = mix(r.y, r.w, a);
  }
  vec3 col = texture(u_image, suv).rgb;

  vec2 sizePx = (r.zw - r.xy) * u_resolution;
  vec2 toEdge = vec2(min(uv.x - r.x, r.z - uv.x), min(uv.y - r.y, r.w - uv.y)) * u_resolution;
  if (u_outline && shown && min(toEdge.x, toEdge.y) < max(1.0, u_pxScale)) col = mix(col, u_line, 0.9);

  if (u_labels && shown && sizePx.x > 60.0 * u_pxScale && sizePx.y > 24.0 * u_pxScale) {
    float px = u_labelSize * u_pxScale;
    vec2 lp = vec2(uv.x - r.x, r.w - uv.y) * u_resolution / px - vec2(3.0, 3.0);
    int n = 1 + int(id * 60.0);
    int digits = n >= 10 ? 2 : 1;
    int count = digits + 1;
    // backing pad behind the label
    if (lp.x > -2.0 && lp.x < float(count * 4 - 1) + 1.0 && lp.y > -2.0 && lp.y < 6.0) col = mix(col, vec3(1.0), 0.8);
    int gx = int(floor(lp.x));
    int gy = int(floor(lp.y));
    if (lp.x >= 0.0 && lp.y >= 0.0 && gy < 5 && gx < count * 4 - 1) {
      int slot = gx / 4, cx = gx - slot * 4;
      if (cx < 3) {
        int g = (slot == count - 1) ? (horiz ? 11 : 10) : (digits == 2 && slot == 0 ? n / 10 : n - (n / 10) * 10);
        if (glyphAt(g, cx, gy) == 1) col = u_ink;
      }
    }
  }
  outColor = vec4(col, 1.0);
}`,
};
