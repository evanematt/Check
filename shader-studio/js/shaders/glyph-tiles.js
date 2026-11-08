// Rounded tiles whose style (dot / ring / solid / filled circle) steps with brightness.
export default {
  id: 'glyph-tiles',
  name: 'Glyph Tiles',
  category: 'Pixel & Blocks',
  tags: ['tiles', 'grid', 'icons', 'led', 'pixel', 'rounded'],
  params: [
    { id: 'cell', label: 'Cell Size', type: 'range', min: 8, max: 56, step: 1, default: 20, unit: 'px', section: 'Grid' },
    { id: 'radius', label: 'Corner Radius', type: 'range', min: 0, max: 0.5, step: 0.01, default: 0.25, section: 'Grid' },
    { id: 'outline', label: 'Outline', type: 'range', min: 0, max: 0.15, step: 0.005, default: 0.05, section: 'Grid' },
    { id: 'cutoff', label: 'Cutoff', type: 'range', min: 0, max: 0.8, step: 0.01, default: 0.16, section: 'Intensity' },
    { id: 'gain', label: 'Gain', type: 'range', min: 0.5, max: 3, step: 0.05, default: 1.5, section: 'Intensity' },
    { id: 'bg', label: 'Background', type: 'color', default: '#000000', section: 'Palette' },
    { id: 'c1', label: 'Tone 1', type: 'color', default: '#5b2a86', section: 'Palette' },
    { id: 'c2', label: 'Tone 2', type: 'color', default: '#cdb4f0', section: 'Palette' },
    { id: 'c3', label: 'Tone 3', type: 'color', default: '#ff8c5a', section: 'Palette' },
    { id: 'c4', label: 'Tone 4', type: 'color', default: '#ff6b8f', section: 'Palette' },
    { id: 'c5', label: 'Tone 5', type: 'color', default: '#ff2d6f', section: 'Palette' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_radius, u_outline, u_cutoff, u_gain;
uniform vec3 u_bg, u_c1, u_c2, u_c3, u_c4, u_c5;

float box(vec2 p, float hs, float r) {
  vec2 q = abs(p) - vec2(hs - r);
  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
float cov(float d, float aa) { return 1.0 - smoothstep(-aa, aa, d); }

void main() {
  float cell = max(6.0, u_cell * u_pxScale);
  vec2 px = v_uv * u_resolution;
  vec2 id = floor(px / cell);
  vec2 p = px / cell - id - 0.5;
  vec2 c = (id + 0.5) * cell;
  float lod = log2(max(1.0, cell * u_imageSize.x / u_resolution.x));
  vec3 src = textureLod(u_image, c / u_resolution, lod).rgb;
  float l = clamp(dot(src, vec3(0.2126, 0.7152, 0.0722)) * u_gain, 0.0, 1.0);
  float aa = 1.0 / cell;
  vec3 col = u_bg;

  if (l > u_cutoff) {
    float t = (l - u_cutoff) / max(1.0 - u_cutoff, 0.001);
    int lv = int(min(floor(t * 5.0), 4.0));
    float d = box(p, 0.42, u_radius * 0.84);
    float tile = cov(d, aa);
    float edge = tile * (1.0 - cov(d + u_outline, aa));
    float len = length(p);

    vec3 fill = u_c1, line = u_c2, mark = u_c2;
    float useEdge = 1.0, ringR = 0.0, dotR = 0.0;
    vec3 light = mix(u_c3, vec3(1.0), 0.8);
    if (lv == 0)      { fill = u_c1; useEdge = 0.0; dotR = 0.07; }
    else if (lv == 1) { fill = u_c1; line = u_c2; ringR = 0.2; }
    else if (lv == 2) { fill = u_c3; line = light; }
    else if (lv == 3) { fill = u_c3; line = light; dotR = 0.3; mark = u_c4; }
    else              { fill = u_c5; line = light; dotR = 0.09; mark = u_c3; }

    col = mix(col, fill, tile);
    col = mix(col, line, edge * useEdge);
    if (ringR > 0.0) col = mix(col, mark, tile * cov(abs(len - ringR) - 0.03, aa));
    if (dotR > 0.0)  col = mix(col, mark, tile * cov(len - dotR, aa));
  }
  outColor = vec4(col, 1.0);
}`,
};
