// Blocky portrait pixelation in graphite on paper.
export default {
  id: 'pixel-sketch',
  name: 'Pixel Sketch',
  category: 'Pixel & Blocks',
  tags: ['pixel', 'pencil', 'graphite', 'portrait', 'blocks', 'paper'],
  params: [
    { id: 'block', label: 'Block Size', type: 'range', min: 6, max: 120, step: 1, default: 28, unit: 'px', section: 'Pattern' },
    { id: 'tones', label: 'Tones', type: 'range', min: 3, max: 16, step: 1, default: 8, section: 'Pattern' },
    { id: 'grid', label: 'Grid Lines', type: 'range', min: 0, max: 1, step: 0.01, default: 0.2, section: 'Pattern' },
    { id: 'texture', label: 'Pencil Texture', type: 'range', min: 0, max: 1, step: 0.01, default: 0.55, section: 'Texture' },
    { id: 'paper', label: 'Paper', type: 'color', default: '#f2eee3', section: 'Color' },
    { id: 'ink', label: 'Graphite', type: 'color', default: '#1b1a19', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_block, u_tones, u_grid, u_texture;
uniform vec3 u_paper, u_ink;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

void main() {
  float cell = max(3.0, u_block * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 c = (floor(px / cell) + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = dot(src, vec3(0.2126, 0.7152, 0.0722));
  float q = floor(l * (u_tones - 1.0) + 0.5) / (u_tones - 1.0);

  vec2 pp = px / u_pxScale;
  float strokes = 0.5 + 0.5 * sin((pp.x + pp.y) * 1.1);
  float grain = hash(floor(pp * 0.8));
  float pencil = (1.0 - q) + ((strokes - 0.5) * 0.30 + (grain - 0.5) * 0.35) * u_texture;
  vec3 col = mix(u_paper, u_ink, clamp(pencil, 0.0, 1.0));

  vec2 f = fract(px / cell);
  float nearEdge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
  float gline = 1.0 - smoothstep(0.0, 1.5 / cell, nearEdge);
  col = mix(col, u_paper * 0.82, gline * u_grid);
  outColor = vec4(col, 1.0);
}`,
};
