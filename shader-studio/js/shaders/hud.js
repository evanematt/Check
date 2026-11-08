import { LUMA, H11, HASH, FONT3X5 } from './lib.js';

// Interface overlay: heat-grid patches, target box, corner brackets, ruler and number readouts.
export default {
  id: 'hud',
  name: 'HUD Overlay',
  category: 'Technical',
  tags: ['hud', 'ui', 'overlay', 'grid', 'target', 'crosshair', 'readout', 'sci-fi', 'poster'],
  params: [
    { id: 'seed', label: 'Seed', type: 'range', min: 1, max: 99, step: 1, default: 5, section: 'Layout' },
    { id: 'patches', label: 'Heat Patches', type: 'range', min: 0, max: 3, step: 1, default: 2, section: 'Layout' },
    { id: 'cell', label: 'Grid Cell', type: 'range', min: 8, max: 48, step: 1, default: 18, unit: 'px', section: 'Layout' },
    { id: 'box', label: 'Target Box', type: 'toggle', default: true, section: 'Elements' },
    { id: 'brackets', label: 'Corner Brackets', type: 'toggle', default: true, section: 'Elements' },
    { id: 'ruler', label: 'Ruler', type: 'toggle', default: true, section: 'Elements' },
    { id: 'readout', label: 'Number Readout', type: 'toggle', default: true, section: 'Elements' },
    { id: 'shift', label: 'Heat Hue', type: 'range', min: 0, max: 1, step: 0.01, default: 0.1, section: 'Color' },
    { id: 'desat', label: 'Desaturate Image', type: 'range', min: 0, max: 1, step: 0.01, default: 0.7, section: 'Color' },
    { id: 'dim', label: 'Dim Image', type: 'range', min: 0, max: 0.8, step: 0.01, default: 0.15, section: 'Color' },
    { id: 'ui', label: 'UI Color', type: 'color', default: '#f4f4ee', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_seed, u_patches, u_cell, u_shift, u_desat, u_dim;
uniform bool u_box, u_brackets, u_ruler, u_readout;
uniform vec3 u_ui;
${LUMA}${H11}${HASH}${FONT3X5}

void main() {
  vec3 col = texture(u_image, v_uv).rgb;
  col = mix(col, vec3(luma(col)), u_desat) * (1.0 - u_dim);

  vec2 res = u_resolution / u_pxScale;
  vec2 px = v_uv * res;          // preview px, y up
  float m = 0.03 * res.x;
  float ink = 0.0;

  // heat-grid patches
  for (int k = 0; k < 3; k++) {
    if (float(k) >= u_patches) break;
    float s = u_seed * 7.0 + float(k) * 13.0;
    float w = res.x * (0.14 + 0.16 * h11(s + 1.0));
    float h = w * (0.7 + 0.6 * h11(s + 9.0));
    vec2 o = vec2(h11(s + 3.0) * max(res.x - w, 1.0), h11(s + 4.0) * max(res.y - h, 1.0));
    vec2 q = px - o;
    if (q.x >= 0.0 && q.y >= 0.0 && q.x < w && q.y < h) {
      vec2 cid = floor(q / u_cell);
      if (hash(cid + s) < 0.78) {
        vec2 cc = (cid + 0.5) * u_cell + o;
        float l = luma(textureLod(u_image, cc * u_pxScale / u_resolution, 3.0).rgb);
        vec3 heat = 0.5 + 0.5 * cos(6.2831853 * (l * 0.9 + u_shift + vec3(0.0, 0.33, 0.67)));
        vec2 f = fract(q / u_cell);
        float line = step(min(min(f.x, f.y), min(1.0 - f.x, 1.0 - f.y)), 1.0 / u_cell);
        col = mix(col, heat, 0.88);
        col = mix(col, u_ui, line * 0.45);
      }
    }
  }

  // target box: dashed square with a small X
  if (u_box) {
    vec2 bc = res * vec2(0.55 + 0.3 * h11(u_seed + 21.0), 0.35 + 0.3 * h11(u_seed + 22.0));
    float hs = 0.035 * res.x;
    vec2 d = px - bc, ad = abs(d);
    float mx = max(ad.x, ad.y);
    float t = (ad.x > ad.y) ? d.y : d.x;
    if (mx < hs + 0.8 && mx > hs - 0.8 && fract(t / 8.0) < 0.55) ink = 1.0;
    if (mx < hs * 0.35 && abs(ad.x - ad.y) < 0.8) ink = 1.0;
  }

  // corner brackets
  if (u_brackets) {
    float bx = min(px.x, res.x - px.x) - m;
    float by = min(px.y, res.y - px.y) - m;
    float L = 0.04 * res.x;
    if ((abs(bx) < 1.0 && by >= 0.0 && by < L) || (abs(by) < 1.0 && bx >= 0.0 && bx < L)) ink = 1.0;
  }

  // ruler along the top
  if (u_ruler) {
    float x0 = res.x * 0.55, x1 = res.x * 0.95, ry = res.y - m * 2.4;
    if (px.x > x0 && px.x < x1) {
      float u = (px.x - x0) / 6.0;
      bool tick = fract(u) < 0.17;
      bool major = tick && mod(floor(u), 5.0) == 0.0;
      if (abs(px.y - ry) < 0.7) ink = 1.0;
      if (tick && px.y > ry && px.y < ry + (major ? 9.0 : 4.0)) ink = 1.0;
    }
    float mk = x0 + (x1 - x0) * (0.2 + 0.6 * h11(u_seed + 31.0));
    float dy = ry + 12.0 - px.y;
    if (dy > 0.0 && dy < 6.0 && abs(px.x - mk) < dy * 0.6) ink = 1.0;
  }

  // number readout, bottom left
  if (u_readout) {
    float gp = 2.0;
    vec2 o = vec2(m * 1.6, m * 1.6);
    vec2 q = vec2(px.x - o.x, o.y + 6.0 * 6.0 * gp - px.y);
    if (q.x >= 0.0 && q.y >= 0.0) {
      int cx = int(floor(q.x / gp)), cy = int(floor(q.y / gp));
      int line = cy / 6, row = cy - line * 6, slot = cx / 4, sub = cx - slot * 4;
      if (line < 6 && slot < 9 && sub < 3 && row < 5) {
        int dgt = int(h11(u_seed * 3.0 + float(line) * 5.1 + float(slot) * 1.7) * 10.0);
        if (glyphAt(dgt, sub, row) == 1) ink = 1.0;
      }
    }
  }

  col = mix(col, u_ui, ink);
  outColor = vec4(col, 1.0);
}`,
};
