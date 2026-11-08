import { LUMA, HASH } from './lib.js';

// 5x7 capital letters, A-Z (7 rows of 5 columns each).
const ART = {
  A: '.###. #...# #...# ##### #...# #...# #...#',
  B: '####. #...# #...# ####. #...# #...# ####.',
  C: '.###. #...# #.... #.... #.... #...# .###.',
  D: '####. #...# #...# #...# #...# #...# ####.',
  E: '##### #.... #.... ####. #.... #.... #####',
  F: '##### #.... #.... ####. #.... #.... #....',
  G: '.###. #...# #.... #.### #...# #...# .####',
  H: '#...# #...# #...# ##### #...# #...# #...#',
  I: '.###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.',
  J: '..### ...#. ...#. ...#. ...#. #..#. .##..',
  K: '#...# #..#. #.#.. ##... #.#.. #..#. #...#',
  L: '#.... #.... #.... #.... #.... #.... #####',
  M: '#...# ##.## #.#.# #.#.# #...# #...# #...#',
  N: '#...# #...# ##..# #.#.# #..## #...# #...#',
  O: '.###. #...# #...# #...# #...# #...# .###.',
  P: '####. #...# #...# ####. #.... #.... #....',
  Q: '.###. #...# #...# #...# #.#.# #..#. .##.#',
  R: '####. #...# #...# ####. #.#.. #..#. #...#',
  S: '.#### #.... #.... .###. ....# ....# ####.',
  T: '##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..',
  U: '#...# #...# #...# #...# #...# #...# .###.',
  V: '#...# #...# #...# #...# #...# .#.#. ..#..',
  W: '#...# #...# #...# #.#.# #.#.# ##.## #...#',
  X: '#...# #...# .#.#. ..#.. .#.#. #...# #...#',
  Y: '#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..',
  Z: '##### ....# ...#. ..#.. .#... #.... #####',
};
const rows = [];
for (const k of 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') {
  const r = ART[k].split(' ');
  if (r.length !== 7 || r.some((x) => x.length !== 5)) throw new Error(`letter ${k} must be 5x7`);
  for (const line of r) rows.push(parseInt(line.replace(/#/g, '1').replace(/\./g, '0'), 2));
}
const TABLE = `const int LETTER[${rows.length}] = int[${rows.length}](${rows.join(', ')});`;

export default {
  id: 'letter-field',
  name: 'Letter Field',
  category: 'ASCII & Text',
  tags: ['letters', 'text', 'typography', 'alphabet', 'ascii', 'type', 'pixel', 'dither'],
  params: [
    { id: 'cell', label: 'Cell Size', type: 'range', min: 7, max: 36, step: 1, default: 12, unit: 'px', section: 'Letters' },
    { id: 'density', label: 'Letter Density', type: 'range', min: 0.2, max: 2.5, step: 0.05, default: 1, section: 'Letters' },
    { id: 'solid', label: 'Solid Threshold', type: 'range', min: 0.3, max: 1, step: 0.01, default: 0.68, section: 'Letters' },
    { id: 'gain', label: 'Gain', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.1, section: 'Intensity' },
    { id: 'invert', label: 'Invert Mapping', type: 'toggle', default: false, section: 'Intensity' },
    {
      id: 'mode', label: 'Letter Color', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Ink' }, { value: 1, label: 'Source' }],
    },
    { id: 'ink', label: 'Ink', type: 'color', default: '#1d1d1f', section: 'Color' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#f3ead9', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_density, u_solid, u_gain;
uniform bool u_invert;
uniform int u_mode;
uniform vec3 u_ink, u_paper;
${LUMA}${HASH}
${TABLE}
float bayer4(ivec2 p) {
  int x = p.x & 3, y = p.y & 3;
  int v = 0;
  for (int i = 0; i < 2; i++) {
    int xb = (x >> i) & 1, yb = (y >> i) & 1;
    v = (v << 2) | (((xb ^ yb) << 1) | yb);
  }
  return (float(v) + 0.5) / 16.0;
}

void main() {
  float cell = max(6.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 id = floor(px / cell);
  vec2 c = (id + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = clamp(luma(src) * u_gain, 0.0, 1.0);
  if (u_invert) l = 1.0 - l;
  float dk = 1.0 - l;
  vec3 ink = (u_mode == 1) ? clamp(src * 0.85, 0.0, 1.0) : u_ink;
  vec3 col = u_paper;
  vec2 lc = fract(px / cell);

  if (dk > u_solid) {
    // darkest cells: fine pixel texture instead of letters
    ivec2 sub = ivec2(floor(lc * 4.0)) + ivec2(id) * 4;
    float t = (dk - u_solid) / max(1.0 - u_solid, 0.001);
    col = mix(u_paper, ink, step(bayer4(sub), 0.35 + t * 0.65));
  } else {
    float prob = clamp((dk - 0.12) * 2.4 * u_density, 0.0, 1.0);
    if (hash(id + 3.7) < prob) {
      int g = int(floor(hash(id) * 26.0));
      vec2 q = (lc - 0.12) / 0.76;
      if (q.x >= 0.0 && q.x < 1.0 && q.y >= 0.0 && q.y < 1.0) {
        int gx = int(q.x * 5.0);
        int gy = int((1.0 - q.y) * 7.0);
        if (((LETTER[g * 7 + gy] >> (4 - gx)) & 1) == 1) col = ink;
      }
    }
  }
  outColor = vec4(col, 1.0);
}`,
};
