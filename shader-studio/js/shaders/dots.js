// PLACEHOLDER style - replace with your own shaders.
export default {
  id: 'dots',
  name: 'Halftone Dots',
  category: 'Pixel & Blocks',
  tags: ['halftone', 'dots', 'print', 'led'],
  params: [
    { id: 'cell', label: 'Dot Spacing', type: 'range', min: 4, max: 48, step: 1, default: 12, unit: 'px', section: 'Pattern' },
    { id: 'softness', label: 'Softness', type: 'range', min: 0, max: 1, step: 0.01, default: 0.15, section: 'Pattern' },
    {
      id: 'mode', label: 'Dot Color', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Source' }, { value: 1, label: 'Ink' }],
    },
    { id: 'ink', label: 'Ink', type: 'color', default: '#f2efe6', section: 'Color' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#0b0b0d', section: 'Color' },
    { id: 'invert', label: 'Invert Mapping', type: 'toggle', default: false, section: 'Intensity' },
  ],
  fragment: /* glsl */ `
uniform float u_cell;
uniform float u_softness;
uniform int   u_mode;
uniform vec3  u_ink;
uniform vec3  u_paper;
uniform bool  u_invert;

void main() {
  float cell = max(3.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 c = (floor(px / cell) + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = dot(src, vec3(0.2126, 0.7152, 0.0722));
  if (u_invert) l = 1.0 - l;
  float r = sqrt(l) * 0.55;
  float d = length(px - c) / cell;
  float aa = max(u_softness * 0.5, 1.0 / cell);
  float m = 1.0 - smoothstep(r - aa, r + aa, d);
  vec3 dotCol = (u_mode == 0) ? clamp(src * 1.15, 0.0, 1.0) : u_ink;
  outColor = vec4(mix(u_paper, dotCol, m), 1.0);
}`,
};
