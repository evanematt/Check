export default {
  id: 'scanlines',
  name: 'CRT Signal',
  category: 'Glow & Signal',
  tags: ['crt', 'scanlines', 'vhs', 'glitch', 'animated'],
  animated: true,
  params: [
    { id: 'lines', label: 'Lines', type: 'range', min: 60, max: 600, step: 10, default: 240, section: 'Scanlines' },
    { id: 'strength', label: 'Strength', type: 'range', min: 0, max: 1, step: 0.01, default: 0.45, section: 'Scanlines' },
    { id: 'wobble', label: 'Wobble', type: 'range', min: 0, max: 10, step: 0.1, default: 2, section: 'Signal' },
    { id: 'split', label: 'RGB Split', type: 'range', min: 0, max: 3, step: 0.05, default: 0.6, unit: '%', section: 'Signal' },
    { id: 'flicker', label: 'Flicker', type: 'range', min: 0, max: 1, step: 0.01, default: 0.3, section: 'Signal' },
  ],
  fragment: /* glsl */ `
uniform float u_lines;
uniform float u_strength;
uniform float u_wobble;
uniform float u_split;
uniform float u_flicker;

void main() {
  vec2 uv = v_uv;
  float band = 0.5 + 0.5 * sin(u_time * 0.7 + uv.y * 6.0);
  uv.x += sin(uv.y * 40.0 + u_time * 3.0) * u_wobble * 0.001 * band;
  float s = u_split * 0.01;
  vec3 col = vec3(
    texture(u_image, uv + vec2(s, 0.0)).r,
    texture(u_image, uv).g,
    texture(u_image, uv - vec2(s, 0.0)).b
  );
  float line = 0.5 + 0.5 * sin(v_uv.y * u_lines * 6.2831853);
  col *= mix(1.0, line, u_strength);
  col *= 1.0 - u_flicker * 0.08 * sin(u_time * 60.0);
  outColor = vec4(col, 1.0);
}`,
};
