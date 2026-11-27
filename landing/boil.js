// "Boiling" line art around the robot and the book, like old hand-drawn animation:
// short pale strokes (rising lines above, contour dashes on the side, speed dashes, little swooshes)
// that are redrawn with a slight jitter at ~8 fps, cycling through a few "drawings".
(() => {
  const canvas = document.querySelector('.boil');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  const COLOR = 'rgba(255, 240, 160, 0.85)';
  const FPS = 8;            // the "on twos/threes" feel of hand-drawn boil
  const DRAWINGS = 3;       // how many slightly different drawings it cycles through
  const JITTER = 0.012;     // jitter per point, as a share of the object's height

  // Where the visible object is inside each element's box (fractions of that box).
  const TARGETS = [
    { el: () => document.querySelector('.nrk-body'), box: { x0: 0.05, y0: 0.06, x1: 0.97, y1: 0.86 }, seed: 11 },
    { el: () => document.querySelector('.book-hit'), box: { x0: 0, y0: 0, x1: 1, y1: 1 }, seed: 29 },
  ];

  // small deterministic RNG so each object always gets the same layout of strokes
  function rng(seed) {
    let s = seed >>> 0;
    return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
  }

  // Strokes in object space: u across (0 = left edge, 1 = right edge), v down (0 = top, 1 = bottom).
  function strokesFor(seed) {
    const r = rng(seed);
    const out = [];
    const line = (pts) => out.push(pts);
    // rising lines above the top
    for (let i = 0; i < 7; i++) {
      const u = 0.12 + r() * 0.76, v0 = -0.04 - r() * 0.06, len = 0.08 + r() * 0.12;
      line([[u, v0], [u + (r() - 0.5) * 0.02, v0 - len / 2], [u + (r() - 0.5) * 0.02, v0 - len]]);
    }
    // contour dashes down the left side, a little outside the edge
    for (let i = 0; i < 5; i++) {
      const v0 = 0.08 + i * 0.17 + r() * 0.05, len = 0.07 + r() * 0.07, u = -0.035 - r() * 0.035;
      line([[u, v0], [u - 0.012, v0 + len / 2], [u - 0.004, v0 + len]]);
    }
    // a couple of dashes along the top-left corner
    line([[0.02, 0.02], [0.08, -0.03], [0.16, -0.05]]);
    line([[0.22, -0.02], [0.3, -0.035]]);
    // speed dashes on the right
    for (let i = 0; i < 4; i++) {
      const v = 0.32 + i * 0.07 + r() * 0.03, u0 = 0.9 + r() * 0.08, len = 0.1 + r() * 0.12;
      line([[u0, v], [u0 + len, v + (r() - 0.5) * 0.015]]);
    }
    // little swooshes at the top-right
    line([[0.6, -0.02], [0.68, -0.07], [0.78, -0.04], [0.86, -0.06]]);
    line([[0.9, 0.04], [0.93, 0.0], [0.97, 0.03]]);
    // short ground dashes under the right side
    line([[0.6, 1.03], [0.7, 1.02]]);
    line([[0.64, 1.06], [0.73, 1.055]]);
    return out;
  }
  for (const t of TARGETS) t.strokes = strokesFor(t.seed);

  // per-drawing jitter, fixed for each of the DRAWINGS so the boil cycles rather than shakes randomly
  const jitter = Array.from({ length: DRAWINGS }, (_, d) => {
    const r = rng(1000 + d * 77);
    return Array.from({ length: 400 }, () => [r() - 0.5, r() - 0.5]);
  });

  let w = 0, h = 0, dpr = 1;
  function resize() {
    dpr = Math.min(devicePixelRatio || 1, 2);
    w = canvas.clientWidth; h = canvas.clientHeight;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
  }
  new ResizeObserver(resize).observe(canvas);
  resize();

  function draw(drawing) {
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const cr = canvas.getBoundingClientRect();
    ctx.strokeStyle = COLOR;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    for (const t of TARGETS) {
      const el = t.el();
      if (!el) continue;
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height) continue;
      const x0 = r.left - cr.left + t.box.x0 * r.width, y0 = r.top - cr.top + t.box.y0 * r.height;
      const bw = (t.box.x1 - t.box.x0) * r.width, bh = (t.box.y1 - t.box.y0) * r.height;
      const j = jitter[drawing];
      ctx.lineWidth = Math.max(1.5, bh * 0.006);
      let k = 0;
      for (const pts of t.strokes) {
        ctx.beginPath();
        pts.forEach(([u, v], i) => {
          const [jx, jy] = j[k++ % j.length];
          const x = x0 + u * bw + jx * JITTER * bh * 2, y = y0 + v * bh + jy * JITTER * bh * 2;
          if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
        });
        ctx.stroke();
      }
    }
  }

  if (reduce) {
    setTimeout(() => draw(0), 1500);
    return;
  }
  // redraw every frame so the strokes follow the hops; the drawing itself only changes at FPS
  function loop(now) {
    draw(Math.floor(now / (1000 / FPS)) % DRAWINGS);
    requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);
})();
