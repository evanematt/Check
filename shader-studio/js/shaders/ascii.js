// Procedural 5x7 bitmap glyphs, ordered by ink density. Built from art so they're easy to edit.
const GLYPHS = [
  ['.....', '.....', '.....', '.....', '.....', '.....', '.....'], // ' '
  ['.....', '.....', '.....', '.....', '.....', '..#..', '.....'], // .
  ['.....', '.....', '..#..', '.....', '..#..', '.....', '.....'], // :
  ['.....', '.....', '.....', '.###.', '.....', '.....', '.....'], // -
  ['.....', '..#..', '..#..', '#####', '..#..', '..#..', '.....'], // +
  ['.....', '#.#.#', '.###.', '..#..', '.###.', '#.#.#', '.....'], // *
  ['.#.#.', '.#.#.', '#####', '.#.#.', '#####', '.#.#.', '.#.#.'], // #
  ['.###.', '#####', '##.##', '#####', '#####', '#####', '.###.'], // @
];
const rows = GLYPHS.flatMap((g) => g.map((r) => parseInt(r.replace(/#/g, '1').replace(/\./g, '0'), 2)));
const TABLE = `const int GLYPH[${rows.length}] = int[${rows.length}](${rows.join(', ')});`;

export default {
  id: 'ascii',
  name: 'ASCII',
  category: 'ASCII & Text',
  tags: ['ascii', 'text', 'characters', 'terminal', 'glyph'],
  params: [
    { id: 'cell', label: 'Cell Size', type: 'range', min: 6, max: 40, step: 1, default: 12, unit: 'px', section: 'Characters' },
    { id: 'gain', label: 'Gain', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.3, section: 'Intensity' },
    { id: 'invert', label: 'Invert Mapping', type: 'toggle', default: false, section: 'Intensity' },
    {
      id: 'mode', label: 'Glyph Color', type: 'select', default: 1, section: 'Color',
      options: [{ value: 0, label: 'Source' }, { value: 1, label: 'Ink' }],
    },
    { id: 'tint', label: 'Backdrop Tint', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Color' },
    { id: 'ink', label: 'Ink', type: 'color', default: '#f4f4f0', section: 'Color' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#0a0a0c', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_gain, u_tint;
uniform int u_mode;
uniform bool u_invert;
uniform vec3 u_ink, u_paper;
${TABLE}

void main() {
  vec2 cs = vec2(u_cell, u_cell * 1.6) * u_pxScale;
  vec2 px = v_uv * u_resolution;
  vec2 c = (floor(px / cs) + 0.5) * cs;
  float lod = log2(max(1.0, cs.x * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = clamp(dot(src, vec3(0.2126, 0.7152, 0.0722)) * u_gain, 0.0, 1.0);
  if (u_invert) l = 1.0 - l;
  int g = int(min(floor(l * 8.0), 7.0));

  vec2 q = (fract(px / cs) - 0.12) / 0.76;
  float on = 0.0;
  if (q.x >= 0.0 && q.x < 1.0 && q.y >= 0.0 && q.y < 1.0) {
    int gx = int(q.x * 5.0);
    int gy = int((1.0 - q.y) * 7.0);
    on = float((GLYPH[g * 7 + gy] >> (4 - gx)) & 1);
  }
  vec3 fg = (u_mode == 0) ? clamp(src * 1.25, 0.0, 1.0) : u_ink;
  vec3 bg = mix(u_paper, src * 0.3, u_tint);
  outColor = vec4(mix(bg, fg, on), 1.0);
}`,
};
