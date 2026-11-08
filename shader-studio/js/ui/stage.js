// Centre stage: pan / zoom / pinch around the canvas. Zoom 1 === "fit to window".
import { clamp } from '../dom.js';

const PAD = { x: 40, top: 28, bottom: 92 }; // keep the image clear of the dock
const MIN_ZOOM = 0.25;
const MAX_ZOOM = 40;

export function createStage({ stage, wrap, canvas, onZoom, onPointer }) {
  let cw = 1, ch = 1, zoom = 1, panX = 0, panY = 0;

  const centre = () => {
    const r = stage.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 + (PAD.top - PAD.bottom) / 2, r };
  };

  function fitScale() {
    const r = stage.getBoundingClientRect();
    const aw = Math.max(60, r.width - PAD.x * 2);
    const ah = Math.max(60, r.height - PAD.top - PAD.bottom);
    return Math.min(aw / cw, ah / ch);
  }

  function apply() {
    const s = fitScale() * zoom;
    wrap.style.transform = `translate(${panX}px, ${panY + (PAD.top - PAD.bottom) / 2}px) scale(${s})`;
    onZoom?.(zoom);
  }

  function zoomAt(next, clientX, clientY) {
    next = clamp(next, MIN_ZOOM, MAX_ZOOM);
    const c = centre();
    const px = (clientX ?? c.x) - c.x;
    const py = (clientY ?? c.y) - c.y;
    const f = fitScale();
    const qx = (px - panX) / (f * zoom);
    const qy = (py - panY) / (f * zoom);
    zoom = next;
    panX = px - qx * f * zoom;
    panY = py - qy * f * zoom;
    if (Math.abs(zoom - 1) < 0.02) fit();
    else apply();
  }

  function fit() {
    zoom = 1; panX = 0; panY = 0;
    apply();
  }

  // wheel / trackpad pinch
  stage.addEventListener(
    'wheel',
    (e) => {
      e.preventDefault();
      const k = Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0016));
      zoomAt(zoom * k, e.clientX, e.clientY);
    },
    { passive: false },
  );

  // drag to pan, two fingers to pinch
  const ptrs = new Map();
  let pinchDist = 0;
  stage.addEventListener('pointerdown', (e) => {
    if (e.target.closest('.dock, .badge, .popover, button, a, input, select')) return;
    stage.setPointerCapture(e.pointerId);
    ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
    stage.classList.add('grabbing');
    if (ptrs.size === 2) {
      const [a, b] = [...ptrs.values()];
      pinchDist = Math.hypot(a.x - b.x, a.y - b.y);
    }
  });
  stage.addEventListener('pointermove', (e) => {
    const r = canvas.getBoundingClientRect();
    onPointer?.([clamp((e.clientX - r.left) / r.width, 0, 1), clamp(1 - (e.clientY - r.top) / r.height, 0, 1)]);
    const p = ptrs.get(e.pointerId);
    if (!p) return;
    const dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    if (ptrs.size === 1) {
      panX += dx; panY += dy;
      apply();
    } else if (ptrs.size === 2) {
      const [a, b] = [...ptrs.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      if (pinchDist) zoomAt(zoom * (d / pinchDist), (a.x + b.x) / 2, (a.y + b.y) / 2);
      pinchDist = d;
    }
  });
  const end = (e) => {
    ptrs.delete(e.pointerId);
    pinchDist = 0;
    if (!ptrs.size) stage.classList.remove('grabbing');
  };
  stage.addEventListener('pointerup', end);
  stage.addEventListener('pointercancel', end);
  stage.addEventListener('dblclick', (e) => {
    if (!e.target.closest('.dock, .badge, .popover')) fit();
  });

  new ResizeObserver(apply).observe(stage);

  return {
    setSize(w, h) {
      cw = w; ch = h;
      wrap.style.width = `${w}px`;
      wrap.style.height = `${h}px`;
      wrap.style.marginLeft = `${-w / 2}px`;
      wrap.style.marginTop = `${-h / 2}px`;
      fit();
    },
    fit,
    zoomBy: (k) => zoomAt(zoom * k),
    get zoom() { return zoom; },
  };
}
