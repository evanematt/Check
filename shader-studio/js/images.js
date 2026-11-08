// Image sources: generated sample images + loading user files.
// Everything is normalised to a <canvas> (WebGL's flip-Y upload doesn't apply to ImageBitmap).

export const MAX_SOURCE = 4096; // longest side kept in memory
export const PREVIEW_MAX = 1600; // longest side of the on-screen render

function mulberry32(seed) {
  return () => {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function makeCanvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

// --- generated samples -----------------------------------------------------
function dusk() {
  const W = 1600, H = 1200, rnd = mulberry32(7);
  const c = makeCanvas(W, H), g = c.getContext('2d');
  const sky = g.createLinearGradient(0, 0, 0, H * 0.72);
  sky.addColorStop(0, '#0b1030'); sky.addColorStop(0.45, '#5b2a86'); sky.addColorStop(0.75, '#e0507a'); sky.addColorStop(1, '#ffb066');
  g.fillStyle = sky; g.fillRect(0, 0, W, H);
  for (let i = 0; i < 220; i++) {
    g.globalAlpha = rnd() * 0.8; g.fillStyle = '#fff';
    g.beginPath(); g.arc(rnd() * W, rnd() * H * 0.4, rnd() * 1.6 + 0.3, 0, 7); g.fill();
  }
  g.globalAlpha = 1;
  const sun = g.createRadialGradient(W * 0.62, H * 0.66, 0, W * 0.62, H * 0.66, 380);
  sun.addColorStop(0, 'rgba(255,240,200,1)'); sun.addColorStop(0.15, 'rgba(255,200,120,.9)'); sun.addColorStop(1, 'rgba(255,120,80,0)');
  g.fillStyle = sun; g.fillRect(0, 0, W, H);
  const layers = ['#7a2f6e', '#4d2162', '#2a1646', '#120b26'];
  layers.forEach((col, i) => {
    const base = H * (0.6 + i * 0.1), amp = 150 - i * 25, f = 0.004 + i * 0.002, ph = rnd() * 10;
    g.fillStyle = col; g.beginPath(); g.moveTo(0, H);
    for (let x = 0; x <= W; x += 8) {
      g.lineTo(x, base - amp * (0.5 + 0.5 * Math.sin(x * f + ph) * Math.cos(x * f * 2.3 + ph * 2)) - Math.sin(x * 0.05 + i) * 6);
    }
    g.lineTo(W, H); g.closePath(); g.fill();
  });
  return c;
}

function orbs() {
  const W = 1600, H = 1200, rnd = mulberry32(21);
  const c = makeCanvas(W, H), g = c.getContext('2d');
  g.fillStyle = '#07070d'; g.fillRect(0, 0, W, H);
  g.globalCompositeOperation = 'lighter';
  const hues = [190, 280, 330, 40, 160];
  for (let i = 0; i < 46; i++) {
    const x = rnd() * W, y = rnd() * H, r = 60 + rnd() * 260, hue = hues[i % hues.length] + rnd() * 30;
    const gr = g.createRadialGradient(x, y, 0, x, y, r);
    gr.addColorStop(0, `hsla(${hue},90%,62%,.75)`); gr.addColorStop(0.6, `hsla(${hue},90%,45%,.22)`); gr.addColorStop(1, `hsla(${hue},90%,40%,0)`);
    g.fillStyle = gr; g.fillRect(x - r, y - r, r * 2, r * 2);
  }
  g.globalCompositeOperation = 'source-over';
  g.lineWidth = 2;
  for (let i = 0; i < 14; i++) {
    g.strokeStyle = `rgba(255,255,255,${0.1 + rnd() * 0.25})`;
    g.beginPath(); g.arc(W * 0.5, H * 0.5, 80 + i * 46, 0, 7); g.stroke();
  }
  return c;
}

function shapes() {
  const W = 1600, H = 1200, rnd = mulberry32(99);
  const c = makeCanvas(W, H), g = c.getContext('2d');
  g.fillStyle = '#efe9dc'; g.fillRect(0, 0, W, H);
  const pal = ['#e63946', '#1d3557', '#f4a261', '#2a9d8f', '#111', '#457b9d'];
  for (let i = 0; i < 26; i++) {
    g.fillStyle = pal[Math.floor(rnd() * pal.length)];
    const x = rnd() * W, y = rnd() * H, s = 80 + rnd() * 320, k = rnd();
    g.beginPath();
    if (k < 0.34) g.arc(x, y, s / 2, 0, 7);
    else if (k < 0.67) g.rect(x - s / 2, y - s / 2, s, s * (0.5 + rnd()));
    else { g.moveTo(x, y - s / 2); g.lineTo(x + s / 2, y + s / 2); g.lineTo(x - s / 2, y + s / 2); g.closePath(); }
    g.fill();
  }
  g.fillStyle = '#111';
  for (let i = 0; i < 40; i++) g.fillRect(0, i * 30 + 6, W, 4 + (i % 3) * 2);
  g.globalCompositeOperation = 'destination-over';
  g.fillStyle = '#efe9dc'; g.fillRect(0, 0, W, H);
  return c;
}

const SAMPLE_DEFS = [
  { id: 'dusk', name: 'Dusk', make: dusk },
  { id: 'orbs', name: 'Orbs', make: orbs },
  { id: 'shapes', name: 'Shapes', make: shapes },
];
const cache = new Map();

export const SAMPLES = SAMPLE_DEFS.map(({ id, name, make }) => ({
  id,
  name,
  get canvas() {
    if (!cache.has(id)) cache.set(id, make());
    return cache.get(id);
  },
}));

// --- user files ------------------------------------------------------------
function fit(w, h, max) {
  const k = Math.min(1, max / Math.max(w, h));
  return [Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k))];
}

/** Draw any image-like into a canvas no larger than `max` on the longest side. */
export function toCanvas(src, max = MAX_SOURCE) {
  const sw = src.naturalWidth || src.videoWidth || src.width;
  const sh = src.naturalHeight || src.videoHeight || src.height;
  const [w, h] = fit(sw, sh, max);
  const c = makeCanvas(w, h);
  const g = c.getContext('2d');
  g.imageSmoothingQuality = 'high';
  g.drawImage(src, 0, 0, w, h);
  return c;
}

export async function fileToCanvas(file) {
  if (!file.type.startsWith('image/')) throw new Error('That file is not an image.');
  try {
    const bmp = await createImageBitmap(file);
    const c = toCanvas(bmp);
    bmp.close?.();
    return c;
  } catch {
    const url = URL.createObjectURL(file);
    try {
      const img = new Image();
      img.src = url;
      await img.decode();
      return toCanvas(img);
    } finally {
      URL.revokeObjectURL(url);
    }
  }
}

/** Working size of the on-screen render for a given source. */
export function previewSize(w, h) {
  const [pw, ph] = fit(w, h, PREVIEW_MAX);
  return { w: pw, h: ph };
}
