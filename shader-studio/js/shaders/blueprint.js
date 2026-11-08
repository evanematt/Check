import { LUMA, SOBEL } from './lib.js';

export default {
  id: 'blueprint',
  name: 'Blueprint',
  category: 'Technical',
  tags: ['blueprint', 'technical', 'drawing', 'edges', 'grid', 'cyanotype'],
  params: [
    { id: 'edge', label: 'Edge Strength', type: 'range', min: 0.2, max: 5, step: 0.1, default: 1.8, section: 'Lines' },
    { id: 'width', label: 'Line Width', type: 'range', min: 0.5, max: 4, step: 0.1, default: 1.2, unit: 'px', section: 'Lines' },
    { id: 'shade', label: 'Hatch Shading', type: 'range', min: 0, max: 1, step: 0.01, default: 0.55, section: 'Lines' },
    { id: 'grid', label: 'Grid Size', type: 'range', min: 10, max: 120, step: 1, default: 40, unit: 'px', section: 'Grid' },
    { id: 'gridStrength', label: 'Grid Strength', type: 'range', min: 0, max: 1, step: 0.01, default: 0.4, section: 'Grid' },
    { id: 'bg', label: 'Paper', type: 'color', default: '#1f4fa3', section: 'Color' },
    { id: 'line', label: 'Ink', type: 'color', default: '#e8f0ff', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_edge, u_width, u_shade, u_grid, u_gridStrength;
uniform vec3 u_bg, u_line;
${LUMA}${SOBEL}
float stripe(float x, float w) {
  return 1.0 - smoothstep(0.0, w, abs(fract(x) - 0.5) * 2.0);
}

void main() {
  vec2 d = u_width * u_pxScale / u_resolution;
  float e = smoothstep(0.12, 0.5, length(sobel(v_uv, d)) * u_edge);

  vec2 pp = v_uv * u_resolution / u_pxScale;
  float dark = 1.0 - luma(textureLod(u_image, v_uv, 2.0).rgb);
  float h1 = stripe((pp.x + pp.y) / 5.0, 0.4) * smoothstep(0.35, 0.55, dark);
  float h2 = stripe((pp.x - pp.y) / 5.0, 0.4) * smoothstep(0.65, 0.85, dark);
  float shade = max(h1, h2) * u_shade;

  vec2 gp = pp / u_grid;
  vec2 gf = abs(fract(gp - 0.5) - 0.5);
  float minor = 1.0 - smoothstep(0.0, 1.0 / u_grid, min(gf.x, gf.y));
  vec2 mf = abs(fract(gp / 5.0 - 0.5) - 0.5);
  float major = 1.0 - smoothstep(0.0, 2.0 / (u_grid * 5.0), min(mf.x, mf.y));
  float grid = max(minor * 0.35, major * 0.8) * u_gridStrength;

  vec2 vv = v_uv - 0.5;
  vec3 col = u_bg * (1.0 - 0.45 * dot(vv, vv) * 2.0);
  col = mix(col, u_line, grid);
  col = mix(col, u_line, shade * 0.7);
  col = mix(col, u_line, e);
  outColor = vec4(col, 1.0);
}`,
};
