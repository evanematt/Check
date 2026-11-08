import { LUMA, HASH } from './lib.js';

// Soft focus + bloom + tinted haze.
export default {
  id: 'dream-glow',
  name: 'Dream Glow',
  category: 'Glow & Signal',
  tags: ['glow', 'bloom', 'haze', 'soft', 'dreamy', 'neon', 'fog', 'blur'],
  params: [
    { id: 'radius', label: 'Glow Radius', type: 'range', min: 2, max: 80, step: 1, default: 26, unit: 'px', section: 'Glow' },
    { id: 'intensity', label: 'Intensity', type: 'range', min: 0, max: 2.5, step: 0.05, default: 1, section: 'Glow' },
    { id: 'threshold', label: 'Threshold', type: 'range', min: 0, max: 0.9, step: 0.01, default: 0.3, section: 'Glow' },
    { id: 'soft', label: 'Soft Focus', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Glow' },
    { id: 'sat', label: 'Saturation', type: 'range', min: 0.3, max: 2.2, step: 0.01, default: 1.25, section: 'Color' },
    { id: 'haze', label: 'Haze', type: 'range', min: 0, max: 1, step: 0.01, default: 0.35, section: 'Color' },
    { id: 'hazeColor', label: 'Haze Color', type: 'color', default: '#8fb9cc', section: 'Color' },
    { id: 'grain', label: 'Grain', type: 'range', min: 0, max: 1, step: 0.01, default: 0.3, section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_radius, u_intensity, u_threshold, u_soft, u_sat, u_haze, u_grain;
uniform vec3 u_hazeColor;
${LUMA}${HASH}
float lodFor(float radiusPx) {
  return clamp(log2(max(1.0, radiusPx * u_pxScale * u_imageSize.x / u_resolution.x / 5.0)), 0.0, 7.0);
}
vec3 blurRGB(vec2 uv, float radiusPx) {
  vec2 r = radiusPx * u_pxScale / u_resolution;
  float lod = lodFor(radiusPx);
  vec3 acc = textureLod(u_image, uv, lod).rgb;
  for (int i = 0; i < 24; i++) {
    float a = float(i) * 2.399963;
    float k = sqrt((float(i) + 0.5) / 24.0);
    acc += textureLod(u_image, uv + vec2(cos(a), sin(a)) * k * r, lod).rgb;
  }
  return acc / 25.0;
}
void main() {
  vec3 base = texture(u_image, v_uv).rgb;
  vec3 wide = blurRGB(v_uv, u_radius);
  vec3 tight = blurRGB(v_uv, max(1.0, u_radius * 0.3));
  vec3 bright = max((wide * 0.6 + tight * 0.4) - u_threshold, 0.0);
  vec3 col = mix(base, tight, u_soft);
  col = 1.0 - (1.0 - col) * (1.0 - clamp(bright * u_intensity, 0.0, 1.0));
  col = mix(vec3(luma(col)), col, u_sat);
  col = mix(col, u_hazeColor, u_haze * 0.4);
  col += (hash(gl_FragCoord.xy / u_pxScale) - 0.5) * u_grain * 0.14;
  outColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}`,
};
