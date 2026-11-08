import { LUMA, HASH } from './lib.js';

// Squares sized by tone, some hollow. Turn on Dual View (Adjust tab) for the
// normal + inverted pair: the inverted twin draws white squares over the blurred photo.
export default {
  id: 'pixel-squares',
  name: 'Pixel Squares',
  category: 'Pixel & Blocks',
  tags: ['squares', 'pixel', 'halftone', 'hollow', 'poster', 'grid', 'dual', 'inverted'],
  params: [
    { id: 'cell', label: 'Cell Size', type: 'range', min: 6, max: 44, step: 1, default: 14, unit: 'px', section: 'Grid' },
    { id: 'scale', label: 'Square Size', type: 'range', min: 0.5, max: 1.4, step: 0.01, default: 1.05, section: 'Grid' },
    { id: 'hollow', label: 'Hollow Squares', type: 'range', min: 0, max: 1, step: 0.01, default: 0.45, section: 'Grid' },
    { id: 'invert', label: 'Invert Mapping', type: 'toggle', default: false, section: 'Intensity' },
    { id: 'color', label: 'Source Color', type: 'range', min: 0, max: 1, step: 0.01, default: 0.7, section: 'Color' },
    { id: 'invBg', label: 'Photo Behind Inverted', type: 'toggle', default: true, section: 'Color' },
    { id: 'grain', label: 'Grain', type: 'range', min: 0, max: 1, step: 0.01, default: 0.45, section: 'Color' },
    { id: 'ink', label: 'Ink', type: 'color', default: '#0e0f0c', section: 'Color' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#f6f6f3', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_scale, u_hollow, u_color, u_grain;
uniform bool u_invert, u_invBg;
uniform vec3 u_ink, u_paper;
${LUMA}${HASH}
float boxd(vec2 p, float h) { vec2 q = abs(p) - h; return max(q.x, q.y); }

void main() {
  float cell = max(5.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 id = floor(px / cell);
  vec2 p = px / cell - id - 0.5;
  vec2 c = (id + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = luma(src);
  float mass = u_invert ? l : 1.0 - l;
  float h = 0.5 * sqrt(clamp(mass, 0.0, 1.0)) * u_scale;
  float aa = 1.0 / cell;
  float sq = 1.0 - smoothstep(-aa, aa, boxd(p, h));
  if (hash(id) < u_hollow && mass > 0.18 && mass < 0.88) {
    sq *= smoothstep(-aa, aa, boxd(p, h * 0.45));
  }
  vec3 fillc;
  vec3 bg;
  if (u_invert) {
    fillc = u_paper;
    bg = u_invBg ? textureLod(u_image, v_uv, 5.0).rgb * 0.8 : u_ink;
  } else {
    float tint = u_color * smoothstep(0.25, 0.7, l);
    fillc = mix(u_ink, clamp(src * 0.95, 0.0, 1.0), tint);
    bg = u_paper;
  }
  vec3 col = mix(bg, fillc, sq);
  col += (hash(gl_FragCoord.xy / u_pxScale) - 0.5) * u_grain * 0.12;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
