// Shared GLSL snippets. Interpolate into a shader's `fragment` template string.
// They rely on the prelude uniforms (u_image, u_resolution, u_pxScale).

export const LUMA = /* glsl */ `
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
`;

export const HASH = /* glsl */ `
float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}
`;

/** Smooth luminance blur, radius in preview px. Needs LUMA. */
export const BLUR_LUMA = /* glsl */ `
float blurLuma(vec2 uv, float radiusPx) {
  vec2 r = radiusPx * u_pxScale / u_resolution;
  float acc = luma(texture(u_image, uv).rgb);
  for (int i = 0; i < 16; i++) {
    float a = float(i) * 2.399963;
    float k = sqrt((float(i) + 0.5) / 16.0);
    acc += luma(texture(u_image, uv + vec2(cos(a), sin(a)) * k * r).rgb);
  }
  return acc / 17.0;
}
`;

/** Sobel gradient of luminance; d = sample offset in uv. Needs LUMA. */
export const SOBEL = /* glsl */ `
vec2 sobel(vec2 uv, vec2 d) {
  float tl = luma(texture(u_image, uv + vec2(-d.x,  d.y)).rgb);
  float t  = luma(texture(u_image, uv + vec2( 0.0,  d.y)).rgb);
  float tr = luma(texture(u_image, uv + vec2( d.x,  d.y)).rgb);
  float l  = luma(texture(u_image, uv + vec2(-d.x,  0.0)).rgb);
  float r  = luma(texture(u_image, uv + vec2( d.x,  0.0)).rgb);
  float bl = luma(texture(u_image, uv + vec2(-d.x, -d.y)).rgb);
  float b  = luma(texture(u_image, uv + vec2( 0.0, -d.y)).rgb);
  float br = luma(texture(u_image, uv + vec2( d.x, -d.y)).rgb);
  return vec2(-tl - 2.0 * l - bl + tr + 2.0 * r + br,
              -bl - 2.0 * b - br + tl + 2.0 * t + tr);
}
`;
