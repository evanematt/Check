// Shared GLSL snippets. Interpolate into a shader's `fragment` template string.
// They rely on the prelude uniforms (u_image, u_resolution, u_pxScale).

export const LUMA = /* glsl */ `
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
`;

export const HASH = /* glsl */ `
float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
`;

/** Smooth luminance blur, radius in preview px. Needs LUMA. */
export const BLUR_LUMA = /* glsl */ `
float blurLuma(vec2 uv, float radiusPx) {
  vec2 r = radiusPx * u_pxScale / u_resolution;
  float acc = luma(texture(u_image, uv).rgb);
  for (int i = 0; i < 16; i++) {
    float a = float(i) * 2.399963;
    float k = sqrt((float(i) + 0.5) / 16.0);
    acc += luma(texture(u_image, uv + vec2(cos(a), sin(a)) * k * r).rgb);
  }
  return acc / 17.0;
}
`;

/** Sobel gradient of luminance; d = sample offset in uv. Needs LUMA. */
export const SOBEL = /* glsl */ `
vec2 sobel(vec2 uv, vec2 d) {
  float tl = luma(texture(u_image, uv + vec2(-d.x,  d.y)).rgb);
  float t  = luma(texture(u_image, uv + vec2( 0.0,  d.y)).rgb);
  float tr = luma(texture(u_image, uv + vec2( d.x,  d.y)).rgb);
  float l  = luma(texture(u_image, uv + vec2(-d.x,  0.0)).rgb);
  float r  = luma(texture(u_image, uv + vec2( d.x,  0.0)).rgb);
  float bl = luma(texture(u_image, uv + vec2(-d.x, -d.y)).rgb);
  float b  = luma(texture(u_image, uv + vec2( 0.0, -d.y)).rgb);
  float br = luma(texture(u_image, uv + vec2( d.x, -d.y)).rgb);
  return vec2(-tl - 2.0 * l - bl + tr + 2.0 * r + br,
              -bl - 2.0 * b - br + tl + 2.0 * t + tr);
}
`;

export const H11 = /* glsl */ `
float h11(float p) {
  p = fract(p * 0.1031);
  p *= p + 33.33;
  p *= p + p;
  return fract(p);
}
`;

export const H22 = /* glsl */ `
vec2 h22(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.xx + p3.yz) * p3.zy);
}
`;

/**
 * Random rectangles by binary space partition of the unit square. Needs H11.
 *   seed   any number            depth  max subdivisions
 *   stop   0..1 chance a branch stops early (bigger, more varied boxes)
 *   aspect output width/height so splits prefer the long side
 * Returns the rect (x0, y0, x1, y1) in uv containing `uv`; `id` is a stable 0..1 hash for it.
 */
export const BSP = /* glsl */ `
vec4 bspRect(vec2 uv, float seed, float depth, float stop, float aspect, out float id) {
  vec4 r = vec4(0.0, 0.0, 1.0, 1.0);
  float s = h11(seed + 0.5);
  for (int i = 0; i < 10; i++) {
    if (float(i) >= depth) break;
    if (i >= 2 && h11(s * 3.7 + 11.0) < stop) break;
    vec2 size = (r.zw - r.xy) * vec2(aspect, 1.0);
    bool splitX = size.x > size.y ? h11(s + 1.3) < 0.8 : h11(s + 1.3) < 0.2;
    float t = mix(0.25, 0.75, h11(s * 5.1 + 2.7));
    if (splitX) {
      float m = mix(r.x, r.z, t);
      if (uv.x < m) { r.z = m; s = h11(s + 17.0); } else { r.x = m; s = h11(s + 43.0); }
    } else {
      float m = mix(r.y, r.w, t);
      if (uv.y < m) { r.w = m; s = h11(s + 17.0); } else { r.y = m; s = h11(s + 43.0); }
    }
  }
  id = s;
  return r;
}
`;

/**
 * Subject mask (1 = subject). Needs LUMA. Declare the matching params with maskParams()
 * and spread them into a shader's `params`.
 */
export const MASK = /* glsl */ `
uniform int u_maskMode;
uniform vec3 u_key;
uniform float u_tol, u_thr;
float subjectMask(vec2 uv) {
  vec4 t = texture(u_image, uv);
  if (u_maskMode == 2) return t.a;
  float l = luma(t.rgb);
  if (u_maskMode == 3) return 1.0 - smoothstep(u_thr - 0.04, u_thr + 0.04, l);
  if (u_maskMode == 4) return smoothstep(u_thr - 0.04, u_thr + 0.04, l);
  float dist = distance(t.rgb, u_key);
  if (u_maskMode == 0) {
    // distance to the nearest corner colour, so one contaminated corner doesn't break the mask
    dist = min(min(distance(t.rgb, textureLod(u_image, vec2(0.01, 0.99), 3.0).rgb),
                   distance(t.rgb, textureLod(u_image, vec2(0.99, 0.99), 3.0).rgb)),
               min(distance(t.rgb, textureLod(u_image, vec2(0.01, 0.01), 3.0).rgb),
                   distance(t.rgb, textureLod(u_image, vec2(0.99, 0.01), 3.0).rgb)));
  }
  return smoothstep(u_tol, u_tol + 0.06, dist);
}
`;

export const maskParams = (section = 'Subject') => [
  {
    id: 'maskMode', label: 'Subject Mask', type: 'select', default: 0, section, randomize: false,
    options: [
      { value: 0, label: 'Auto (corner color)' },
      { value: 1, label: 'Key color' },
      { value: 2, label: 'Alpha channel' },
      { value: 3, label: 'Dark subject' },
      { value: 4, label: 'Light subject' },
    ],
  },
  { id: 'key', label: 'Key Color', type: 'color', default: '#ececec', section, randomize: false },
  { id: 'tol', label: 'Color Tolerance', type: 'range', min: 0.02, max: 0.6, step: 0.01, default: 0.14, section, randomize: false },
  { id: 'thr', label: 'Brightness Threshold', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section, randomize: false },
];

// 3x5 pixel font: digits 0-9, then arrow down (10) and arrow right (11).
const FONT_ART = [
  ['###', '#.#', '#.#', '#.#', '###'], ['.#.', '##.', '.#.', '.#.', '###'], ['###', '..#', '###', '#..', '###'],
  ['###', '..#', '###', '..#', '###'], ['#.#', '#.#', '###', '..#', '..#'], ['###', '#..', '###', '..#', '###'],
  ['###', '#..', '###', '#.#', '###'], ['###', '..#', '..#', '..#', '..#'], ['###', '#.#', '###', '#.#', '###'],
  ['###', '#.#', '###', '..#', '###'], ['.#.', '.#.', '.#.', '#.#', '.#.'], ['.#.', '..#', '###', '..#', '.#.'],
];
const FONT_BITS = FONT_ART.map((g) => parseInt(g.join('').replace(/#/g, '1').replace(/\./g, '0'), 2));
/** glyphAt(index, col 0-2, row 0-4) -> 0/1 */
export const FONT3X5 = `const int FONT[12] = int[12](${FONT_BITS.join(', ')});
int glyphAt(int idx, int cx, int cy) { return (FONT[idx] >> (14 - (cy * 3 + cx))) & 1; }
`;
