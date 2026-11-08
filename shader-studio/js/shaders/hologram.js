import { LUMA, SOBEL } from './lib.js';

export default {
  id: 'hologram',
  name: 'Hologram',
  category: 'Glow & Signal',
  tags: ['hologram', 'scanlines', 'glow', 'cyan', 'sci-fi', 'glitch', 'animated'],
  animated: true,
  params: [
    { id: 'lines', label: 'Scanlines', type: 'range', min: 40, max: 500, step: 10, default: 200, section: 'Signal' },
    { id: 'glitch', label: 'Glitch Bands', type: 'range', min: 0, max: 1, step: 0.01, default: 0.35, section: 'Signal' },
    { id: 'flicker', label: 'Flicker', type: 'range', min: 0, max: 1, step: 0.01, default: 0.35, section: 'Signal' },
    { id: 'glow', label: 'Edge Glow', type: 'range', min: 0, max: 3, step: 0.05, default: 1.3, section: 'Light' },
    { id: 'amount', label: 'Tint Amount', type: 'range', min: 0, max: 1, step: 0.01, default: 0.9, section: 'Light' },
    { id: 'tint', label: 'Tint', type: 'color', default: '#52e6ff', section: 'Light' },
  ],
  fragment: /* glsl */ `
uniform float u_lines, u_glitch, u_flicker, u_glow, u_amount;
uniform vec3 u_tint;
${LUMA}${SOBEL}
float hash1(float n) { return fract(sin(n * 127.1) * 43758.5453); }

void main() {
  vec2 uv = v_uv;
  float band = floor(uv.y * 28.0);
  float rr = hash1(band * 13.7 + floor(u_time * 7.0));
  uv.x += (rr > 0.94 ? (rr - 0.97) * 0.25 : 0.0) * u_glitch;

  vec2 px = u_pxScale / u_resolution;
  vec3 src = vec3(texture(u_image, uv + vec2(px.x * 2.5, 0.0)).r,
                  texture(u_image, uv).g,
                  texture(u_image, uv - vec2(px.x * 2.5, 0.0)).b);
  float l = luma(src);
  float e = smoothstep(0.1, 0.6, length(sobel(uv, px * 1.5)) * 2.0);

  float scan = 0.62 + 0.38 * sin((v_uv.y * u_lines + u_time * 1.5) * 6.2831853);
  vec3 holo = u_tint * pow(l, 1.5) * 0.6 * scan;
  holo += mix(u_tint, vec3(1.0), 0.6) * e * u_glow;
  vec3 col = mix(src * scan * 0.6, holo, u_amount);
  col *= 1.0 - u_flicker * 0.18 * hash1(floor(u_time * 24.0));
  outColor = vec4(col, 1.0);
}`,
};
