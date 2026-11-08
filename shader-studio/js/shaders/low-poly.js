import { H22, HASH } from './lib.js';

// Flat-shaded facets (jittered Voronoi), PS1-ish.
export default {
  id: 'low-poly',
  name: 'Low Poly',
  category: 'Pixel & Blocks',
  tags: ['low poly', 'facets', 'voronoi', 'crystal', 'ps1', 'flat', 'polygon'],
  params: [
    { id: 'cell', label: 'Facet Size', type: 'range', min: 12, max: 160, step: 1, default: 44, unit: 'px', section: 'Facets' },
    { id: 'jitter', label: 'Irregularity', type: 'range', min: 0, max: 1, step: 0.01, default: 0.9, section: 'Facets' },
    { id: 'facet', label: 'Facet Shading', type: 'range', min: 0, max: 0.5, step: 0.01, default: 0.12, section: 'Shading' },
    { id: 'edges', label: 'Edge Lines', type: 'range', min: 0, max: 1, step: 0.01, default: 0, section: 'Shading' },
    { id: 'levels', label: 'Color Levels', type: 'range', min: 3, max: 32, step: 1, default: 32, section: 'Shading' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_jitter, u_facet, u_edges, u_levels;
${H22}${HASH}
void main() {
  float cell = max(8.0, u_cell * u_pxScale);
  vec2 g = v_uv * u_resolution / cell;
  vec2 ig = floor(g), fg = fract(g);
  float d1 = 9.0, d2 = 9.0;
  vec2 best = vec2(0.0), bestId = vec2(0.0);
  for (int j = -1; j <= 1; j++) {
    for (int i = -1; i <= 1; i++) {
      vec2 o = vec2(float(i), float(j));
      vec2 pt = o + 0.5 + (h22(ig + o) - 0.5) * u_jitter;
      float d = length(pt - fg);
      if (d < d1) { d2 = d1; d1 = d; best = pt + ig; bestId = ig + o; }
      else if (d < d2) { d2 = d; }
    }
  }
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 col = textureLod(u_image, clamp(best * cell / u_resolution, 0.0, 1.0), lod).rgb;
  col *= 1.0 + (hash(bestId) - 0.5) * u_facet * 2.0;
  col = floor(col * u_levels + 0.5) / u_levels;
  col = mix(col, col * 0.5, (1.0 - smoothstep(0.0, 0.05, d2 - d1)) * u_edges);
  outColor = vec4(col, 1.0);
}`,
};
