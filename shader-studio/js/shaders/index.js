// Shader registry.
//
// To add a style:
//   1. copy js/shaders/_template.js to js/shaders/<name>.js and edit it
//   2. import it below and add it to RAW_SHADERS
// The left panel, thumbnails, search and right-hand controls are all generated from it.

import original from './original.js';
import ascii from './ascii.js';
import glyphTiles from './glyph-tiles.js';
import pixelSketch from './pixel-sketch.js';
import mosaic from './mosaic.js';
import dots from './dots.js';
import dither from './dither.js';
import scribbles from './scribbles.js';
import hope from './hope.js';
import blueprint from './blueprint.js';
import hologram from './hologram.js';
import chroma from './chroma.js';
import scanlines from './scanlines.js';

// Order here = order of categories and tiles in the left panel.
const RAW_SHADERS = [
  original,
  ascii,
  glyphTiles, pixelSketch, mosaic, dots,
  dither, scribbles, hope,
  blueprint,
  hologram, chroma, scanlines,
];

// ---------------------------------------------------------------------------
function normalizeParam(p) {
  if (!p.id || !p.type) throw new Error('Shader param needs `id` and `type`');
  const out = { section: 'Settings', uniform: `u_${p.id}`, label: p.id, ...p };
  switch (p.type) {
    case 'range':
      out.step ??= 1;
      out.min ??= 0;
      out.max ??= 1;
      out.default ??= out.min;
      break;
    case 'select':
      out.options ??= [];
      out.default ??= out.options[0]?.value ?? 0;
      break;
    case 'toggle':
      out.default ??= false;
      break;
    case 'color':
      out.default ??= '#ffffff';
      break;
    default:
      throw new Error(`Unknown param type "${p.type}" (range | select | toggle | color)`);
  }
  return out;
}

function normalizeShader(raw) {
  if (!raw.id || !raw.name || !raw.fragment) throw new Error('Shader needs `id`, `name` and `fragment`');
  return {
    category: 'Other',
    tags: [],
    animated: false,
    ...raw,
    params: (raw.params || []).map(normalizeParam),
  };
}

export const SHADERS = RAW_SHADERS.map(normalizeShader);
export const getShader = (id) => SHADERS.find((s) => s.id === id) || SHADERS[0];

export function defaultValues(shader) {
  return Object.fromEntries(shader.params.map((p) => [p.id, p.default]));
}

/** Defaults + the user's overrides for this style. */
export function resolveValues(shader, state) {
  return { ...defaultValues(shader), ...(state.params[shader.id] || {}) };
}

// ---------------------------------------------------------------------------
// Randomisation (Shuffle button)
// ---------------------------------------------------------------------------
function hslToHex(h, s, l) {
  const a = s * Math.min(l, 1 - l);
  const f = (n) => {
    const k = (n + h / 30) % 12;
    const c = l - a * Math.max(-1, Math.min(k - 3, 9 - k, 1));
    return Math.round(c * 255).toString(16).padStart(2, '0');
  };
  return `#${f(0)}${f(8)}${f(4)}`;
}

export function randomValues(shader) {
  const out = {};
  for (const p of shader.params) {
    if (p.randomize === false) continue;
    if (p.type === 'range') {
      const steps = Math.max(1, Math.round((p.max - p.min) / p.step));
      const decimals = (String(p.step).split('.')[1] || '').length;
      out[p.id] = +(p.min + Math.floor(Math.random() * (steps + 1)) * p.step).toFixed(decimals);
    } else if (p.type === 'select') {
      out[p.id] = p.options[Math.floor(Math.random() * p.options.length)].value;
    } else if (p.type === 'toggle') {
      out[p.id] = Math.random() > 0.5;
    } else if (p.type === 'color') {
      out[p.id] = hslToHex(Math.random() * 360, 0.5 + Math.random() * 0.5, 0.35 + Math.random() * 0.35);
    }
  }
  return out;
}
