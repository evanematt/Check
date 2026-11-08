// Style thumbnails: every style rendered on the current image by a second, tiny
// WebGL context, then copied (cover-cropped) into 2D canvases cached per style.

import { Renderer } from './renderer.js';
import { defaultValues } from './shaders/index.js';
import { toCanvas } from './images.js';

export const THUMB = 192; // px, square

export function createThumbs(shaders) {
  const glCanvas = document.createElement('canvas');
  const renderer = new Renderer(glCanvas);
  const cache = new Map(); // id -> canvas | null (null = failed)
  const listeners = new Set();
  let job = 0;

  function copyCover(src, w, h) {
    const out = document.createElement('canvas');
    out.width = out.height = THUMB;
    const k = Math.max(THUMB / w, THUMB / h);
    const sw = THUMB / k, sh = THUMB / k;
    out.getContext('2d').drawImage(src, (w - sw) / 2, (h - sh) / 2, sw, sh, 0, 0, THUMB, THUMB);
    return out;
  }

  return {
    onUpdate(fn) {
      listeners.add(fn);
    },
    failed: (id) => cache.get(id) === null,
    paint(canvas, id) {
      const src = cache.get(id);
      const g = canvas.getContext('2d');
      g.clearRect(0, 0, canvas.width, canvas.height);
      if (src) g.drawImage(src, 0, 0, canvas.width, canvas.height);
    },
    /** Re-render all thumbnails for a new source image, a few per frame. */
    generate(source, previewWidth) {
      const token = ++job;
      const small = toCanvas(source, 256);
      renderer.setImage(small);
      const queue = [...shaders];
      const step = () => {
        if (token !== job) return;
        for (let i = 0; i < 3 && queue.length; i++) {
          const sh = queue.shift();
          const err = renderer.render({
            style: sh.id === 'original' ? null : sh,
            values: defaultValues(sh),
            time: 0,
            width: small.width,
            height: small.height,
            pxScale: small.width / previewWidth,
          });
          cache.set(sh.id, err ? null : copyCover(glCanvas, small.width, small.height));
          listeners.forEach((fn) => fn(sh.id));
        }
        if (queue.length) requestAnimationFrame(step);
      };
      step();
    },
  };
}
