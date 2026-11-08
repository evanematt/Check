export default {
  id: 'mosaic',
  name: 'Mosaic',
  category: 'Pixel & Blocks',
  tags: ['pixel', 'blocks', 'tiles', 'retro'],
  params: [
    { id: 'cell', label: 'Cell Size', type: 'range', min: 4, max: 80, step: 1, default: 18, unit: 'px', section: 'Pattern' },
    { id: 'gap', label: 'Gap', type: 'range', min: 0, max: 0.5, step: 0.01, default: 0.08, section: 'Pattern' },
    { id: 'levels', label: 'Color Levels', type: 'range', min: 2, max: 32, step: 1, default: 10, section: 'Intensity' },
  ],
  fragment: /* glsl */ `
uniform float u_cell;
uniform float u_gap;
uniform float u_levels;

void main() {
  float cell = max(2.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 center = (floor(px / cell) + 0.5) * cell;
  // sample a mip level that matches the cell so we average instead of alias
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 col = textureLod(u_image, center / u_resolution, lod).rgb;
  col = floor(col * u_levels + 0.5) / u_levels;
  vec2 f = fract(px / cell) - 0.5;
  float m = step(max(abs(f.x), abs(f.y)), 0.5 - u_gap * 0.5);
  outColor = vec4(col * m, 1.0);
}`,
};
