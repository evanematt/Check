import { HASH } from './lib.js';

// The frame breaks into pixel blocks that peel away along a direction, snapping to the grid.
// Timing follows u_time, which is the video clock for videos (so the effect syncs to playback).
export default {
  id: 'pixel-dissolve',
  name: 'Pixel Dissolve',
  category: 'Glitch & Stretch',
  tags: ['dissolve', 'disintegrate', 'pixels', 'particles', 'thanos', 'video', 'transition', 'blocks', 'animated'],
  animated: true,
  params: [
    {
      id: 'mode', label: 'Mode', type: 'select', default: 0, section: 'Timing', randomize: false,
      options: [{ value: 0, label: 'Disintegrate' }, { value: 1, label: 'Assemble' }, { value: 2, label: 'Ping-pong' }, { value: 3, label: 'Manual' }],
    },
    { id: 'start', label: 'Start Time', type: 'range', min: 0, max: 30, step: 0.1, default: 0.5, unit: 's', section: 'Timing', randomize: false },
    { id: 'duration', label: 'Duration', type: 'range', min: 0.5, max: 20, step: 0.1, default: 3, unit: 's', section: 'Timing', randomize: false },
    { id: 'progress', label: 'Manual Progress', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Timing', randomize: false },
    { id: 'cell', label: 'Block Size', type: 'range', min: 6, max: 48, step: 1, default: 16, unit: 'px', section: 'Blocks' },
    { id: 'pixelate', label: 'Pixelate Blocks', type: 'range', min: 0, max: 1, step: 0.01, default: 1, section: 'Blocks' },
    { id: 'shrink', label: 'Shrink', type: 'range', min: 0, max: 1, step: 0.01, default: 0.4, section: 'Blocks' },
    { id: 'fade', label: 'Fade Out', type: 'range', min: 0, max: 1, step: 0.01, default: 1, section: 'Blocks' },
    {
      id: 'dir', label: 'Direction', type: 'select', default: 1, section: 'Motion',
      options: [
        { value: 0, label: 'Up' }, { value: 1, label: 'Up-right' }, { value: 2, label: 'Right' }, { value: 3, label: 'Down-right' },
        { value: 4, label: 'Down' }, { value: 5, label: 'Down-left' }, { value: 6, label: 'Left' }, { value: 7, label: 'Up-left' },
      ],
    },
    { id: 'dist', label: 'Travel', type: 'range', min: 1, max: 30, step: 1, default: 14, unit: ' cells', section: 'Motion' },
    { id: 'wobble', label: 'Sideways Wobble', type: 'range', min: 0, max: 1, step: 0.01, default: 0.5, section: 'Motion' },
    {
      id: 'sweep', label: 'Starts From', type: 'select', default: 0, section: 'Motion',
      options: [
        { value: 0, label: 'Left' }, { value: 1, label: 'Right' }, { value: 2, label: 'Top' },
        { value: 3, label: 'Bottom' }, { value: 4, label: 'Center' }, { value: 5, label: 'Random' },
      ],
    },
    { id: 'chaos', label: 'Randomness', type: 'range', min: 0, max: 1, step: 0.01, default: 0.35, section: 'Motion' },
    { id: 'span', label: 'Spread', type: 'range', min: 0.1, max: 0.9, step: 0.01, default: 0.5, section: 'Motion' },
    { id: 'bg', label: 'Background', type: 'color', default: '#000000', section: 'Color' },
  ],
  fragment: /* glsl */ `
uniform float u_cell, u_progress, u_start, u_duration, u_dist, u_chaos, u_span, u_shrink, u_fade, u_wobble, u_pixelate;
uniform int u_mode, u_sweep, u_dir;
uniform vec3 u_bg;
${HASH}

float progress() {
  if (u_mode == 3) return u_progress;
  float t = max((u_time - u_start) / max(u_duration, 0.05), 0.0);
  if (u_mode == 0) return clamp(t, 0.0, 1.0);
  if (u_mode == 1) return 1.0 - clamp(t, 0.0, 1.0);
  return 1.0 - abs(fract(t * 0.5) * 2.0 - 1.0);
}

vec2 dirVec(int d) {
  if (d == 0) return vec2(0.0, 1.0);
  if (d == 1) return vec2(1.0, 1.0);
  if (d == 2) return vec2(1.0, 0.0);
  if (d == 3) return vec2(1.0, -1.0);
  if (d == 4) return vec2(0.0, -1.0);
  if (d == 5) return vec2(-1.0, -1.0);
  if (d == 6) return vec2(-1.0, 0.0);
  return vec2(-1.0, 1.0);
}

struct Blk { float e; float n; float j; float scale; float alpha; };

Blk blockOf(vec2 id, vec2 grid, float p) {
  vec2 uvc = (id + 0.5) / grid;
  float w;
  if (u_sweep == 0) w = uvc.x;
  else if (u_sweep == 1) w = 1.0 - uvc.x;
  else if (u_sweep == 2) w = 1.0 - uvc.y;
  else if (u_sweep == 3) w = uvc.y;
  else if (u_sweep == 4) w = clamp(distance(uvc, vec2(0.5)) * 1.4142, 0.0, 1.0);
  else w = hash(id + 11.0);
  float delay = mix(w, hash(id + 7.3), u_chaos) * (1.0 - u_span);
  Blk b;
  b.e = clamp((p - delay) / max(u_span, 0.001), 0.0, 1.0);
  float speed = 0.35 + 0.65 * hash(id + 19.1);
  b.n = floor(b.e * b.e * u_dist * speed + 0.5);
  float rj = hash(id + 3.9);
  b.j = 0.0;
  if (b.e > 0.0) {
    if (rj < 0.33 * u_wobble) b.j = -1.0;
    else if (rj > 1.0 - 0.33 * u_wobble) b.j = 1.0;
  }
  b.scale = 1.0 - b.e * u_shrink;
  b.alpha = 1.0 - smoothstep(0.55, 1.0, b.e) * u_fade;
  return b;
}

void main() {
  float p = progress();
  if (p <= 0.0) { outColor = texture(u_image, v_uv); return; }

  float cell = max(4.0, u_cell * u_pxScale);
  vec2 res = u_resolution;
  vec2 px = v_uv * res;
  vec2 grid = res / cell;
  vec2 gmax = ceil(grid);
  vec2 base = floor(px / cell);
  vec2 f = fract(px / cell);
  vec2 d = dirVec(u_dir);
  vec2 pr = vec2(-d.y, d.x);
  float lodFlat = log2(max(1.0, cell * u_imageSize.x / res.x));
  float lodFull = log2(max(1.0, u_imageSize.x / res.x));

  vec3 col = u_bg;
  float bestE = -1.0;
  int K = int(min(u_dist + 1.0, 32.0));
  for (int k = 0; k <= 32; k++) {
    if (k > K) break;
    for (int j = -1; j <= 1; j++) {
      vec2 id = base - float(k) * d - float(j) * pr;
      if (id.x < 0.0 || id.y < 0.0 || id.x >= gmax.x || id.y >= gmax.y) continue;
      Blk b = blockOf(id, grid, p);
      if (b.n != float(k) || b.j != float(j) || b.alpha <= 0.001 || b.e <= bestE) continue;
      vec2 q = (f - 0.5) / max(b.scale, 0.001);
      if (max(abs(q.x), abs(q.y)) > 0.5) continue;
      bestE = b.e;
      vec3 tex = textureLod(u_image, (id + q + 0.5) * cell / res, lodFull).rgb;
      vec3 flatC = textureLod(u_image, (id + 0.5) * cell / res, lodFlat).rgb;
      vec3 c = mix(tex, flatC, smoothstep(0.0, 0.25, b.e) * u_pixelate);
      col = mix(u_bg, c, b.alpha);
    }
  }
  outColor = vec4(col, 1.0);
}`,
};
