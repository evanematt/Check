import { LUMA, SOBEL } from './lib.js';

export default {
  id: 'chroma',
  name: 'Chroma',
  category: 'Glow & Signal',
  tags: ['chroma', 'rainbow', 'edges', 'iridescent', 'dark', 'dispersion'],
  params: [
    { id: 'edge', label: 'Edge Strength', type: 'range', min: 0.5, max: 6, step: 0.1, default: 2.5, section: 'Rainbow' },
    { id: 'width', label: 'Edge Width', type: 'range', min: 0.5, max: 5, step: 0.1, default: 1.6, unit: 'px', section: 'Rainbow' },
    { id: 'spread', label: 'Hue Spread', type: 'range', min: 0, max: 3, step: 0.05, default: 1, section: 'Rainbow' },
    { id: 'shift', label: 'Hue Shift', type: 'range', min: 0, max: 360, step: 1, default: 0, unit: '°', section: 'Rainbow' },
    { id: 'base', label: 'Base Brightness', type: 'range', min: 0, max: 1, step: 0.01, default: 0.18, section: 'Light' },
    { id: 'split', label: 'RGB Split', type: 'range', min: 0, max: 8, step: 0.1, default: 2.5, unit: 'px', section: 'Light' },
  ],
  fragment: /* glsl */ `
uniform float u_edge, u_width, u_spread, u_shift, u_base, u_split;
${LUMA}${SOBEL}
void main() {
  vec2 px = u_pxScale / u_resolution;
  vec2 g = sobel(v_uv, px * u_width);
  float amt = smoothstep(0.05, 0.6, length(g) * u_edge);
  float l = luma(texture(u_image, v_uv).rgb);
  float hue = atan(g.y, g.x) / 6.2831853 * u_spread + u_shift / 360.0 + l * 0.3;
  vec3 rainbow = 0.5 + 0.5 * cos(6.2831853 * (hue + vec3(0.0, 0.33, 0.67)));

  vec2 o = vec2(px.x * u_split, 0.0);
  vec3 split = vec3(texture(u_image, v_uv + o).r, texture(u_image, v_uv).g, texture(u_image, v_uv - o).b);
  vec3 col = split * u_base;
  col += rainbow * amt;
  col += rainbow * smoothstep(0.75, 1.0, l) * 0.5;
  outColor = vec4(col, 1.0);
}`,
};
