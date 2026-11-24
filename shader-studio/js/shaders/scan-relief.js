import { LUMA, BLUR_LUMA } from './lib.js';

// Depth-scanner look: horizontal lines pushed up by brightness, glowing, over a faint grid.
export default {
  id: 'scan-relief',
  name: 'Scan Relief',
  category: 'Glow & Signal',
  tags: ['scan', 'lines', 'relief', 'contour', 'kinect', 'depth', 'point cloud', 'joy division', 'glow'],
  params: [
    { id: 'pitch', label: 'Line Spacing', type: 'range', min: 3, max: 18, step: 0.5, default: 6, unit: 'px', section: 'Lines' },
    { id: 'relief', label: 'Relief Height', type: 'range', min: 0, max: 60, step: 1, default: 16, unit: 'px', section: 'Lines' },
    { id: 'weight', label: 'Line Weight', type: 'range', min: 0.5, max: 5, step: 0.1, default: 1.4, unit: 'px', section: 'Lines' },
    { id: 'glow', label: 'Glow', type: 'range', min: 0, max: 2.5, step: 0.05, default: 1.1, section: 'Light' },
    { id: 'grid', label: 'Grid Size', type: 'range', min: 20, max: 120, step: 1, default: 48, unit: 'px', section: 'Grid' },
    { id: 'gridStrength', label: 'Grid Strength', type: 'range', min: 0, max: 1, step: 0.01, default: 0.2, section: 'Grid' },
    {
      id: 'mode', label: 'Line Color', type: 'select', default: 0, section: 'Color',
      options: [{ value: 0, label: 'Tint' }, { value: 1, label: 'Source' }],
    },
    { id: 'tint', label: 'Tint', type: 'color', default: '#bfe3ff', section: 'Color' },
    { id: 'bg', label: 'Background', type: 'color', default: '#02040a', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_pitch, u_relief, u_weight, u_glow, u_grid, u_gridStrength;
uniform int u_mode;
uniform vec3 u_tint, u_bg;
${LUMA}${BLUR_LUMA}
void main() {
  float pitch = max(3.0, u_pitch * u_pxScale);
  float relief = u_relief * u_pxScale;
  vec2 px = v_uv * u_resolution;
  float row = floor(px.y / pitch);
  int K = int(min(ceil(relief / pitch) + 1.0, 10.0));
  float lod = max(0.0, log2(pitch * 0.5 * u_imageSize.x / u_resolution.x));

  vec3 col = vec3(0.0);
  for (int k = -10; k <= 10; k++) {
    if (k < -K || k > K) continue;
    float yc = (row + float(k) + 0.5) * pitch;
    if (yc < 0.0 || yc > u_resolution.y) continue;
    vec3 src = textureLod(u_image, vec2(v_uv.x, yc / u_resolution.y), lod).rgb;
    float l = luma(src);
    float w = u_weight * u_pxScale * (0.45 + 0.9 * l);
    float c = 1.0 - smoothstep(w * 0.5 - 0.7, w * 0.5 + 0.7, abs(px.y - (yc + relief * l)));
    vec3 lc = ((u_mode == 0) ? u_tint : clamp(src * 1.2, 0.0, 1.0)) * (0.3 + 0.7 * l);
    col = max(col, lc * c);
  }
  float g = blurLuma(v_uv, 12.0);
  col += u_tint * g * g * u_glow * 0.6;

  vec2 gp = px / u_pxScale / u_grid;
  vec2 gf = abs(fract(gp - 0.5) - 0.5) * u_grid;
  col = max(col, u_tint * (1.0 - smoothstep(0.0, 1.0, min(gf.x, gf.y))) * u_gridStrength * 0.5);
  outColor = vec4(clamp(u_bg + col, 0.0, 1.0), 1.0);
}`,
};
