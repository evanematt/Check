// Ground robot with the lynx: the lynx turns its head towards the cursor,
// and the wheels sit in a painted snow drift.
(() => {
  const lynx = document.getElementById('nrkLynx');
  if (!lynx) return;

  /* ───────── Gaze ─────────
     Sprite sheet is 9×7 frames: columns 0→8 look to the viewer's left → right, rows 0→6 look up → down. */
  const COLS = 9, ROWS = 7;
  const pointer = { x: 0, y: 0, has: false, last: 0 };
  const note = (e) => { pointer.x = e.clientX; pointer.y = e.clientY; pointer.has = true; pointer.last = performance.now(); };
  addEventListener('pointermove', note, { passive: true });
  addEventListener('pointerdown', note, { passive: true });
  document.addEventListener('pointerleave', () => { pointer.last = 0; });

  const g = { c: 4, r: 3, col: -1, row: -1 };
  function show(col, row) {
    if (col === g.col && row === g.row) return;
    g.col = col; g.row = row;
    const p = `${(col * 100) / (COLS - 1)}% ${(row * 100) / (ROWS - 1)}%`;
    lynx.style.backgroundPosition = p;
    lynx.style.setProperty('--frame', p);   // the light layers are masked by the same frame
  }

  let prev = performance.now();
  function tick(now) {
    const dt = Math.min((now - prev) / 1000, 0.05);
    prev = now;
    const t = now / 1000;
    const r = lynx.getBoundingClientRect();
    const hx = r.left + r.width * 0.55, hy = r.top + r.height * 0.16;   // the lynx's eyes
    const idle = !pointer.has || now - pointer.last > 4000;
    let tx, ty;
    if (idle) {                       // look around on its own
      tx = Math.sin(t * 0.45) * 0.9 + Math.sin(t * 1.3) * 0.2;
      ty = Math.sin(t * 0.31 + 1.2) * 0.6;
    } else {
      const reach = Math.max(160, Math.min(innerWidth, innerHeight) * 0.36);
      tx = Math.max(-1, Math.min(1, (pointer.x - hx) / reach));
      ty = Math.max(-1, Math.min(1, (pointer.y - hy) / (reach * 0.8)));
    }
    const k = 1 - Math.exp(-dt * (idle ? 3 : 10));
    g.c += ((tx + 1) * (COLS - 1) / 2 - g.c) * k;
    g.r += ((ty + 1) * (ROWS - 1) / 2 - g.r) * k;
    let col = g.col < 0 ? Math.round(g.c) : g.col, row = g.row < 0 ? Math.round(g.r) : g.row;
    if (Math.abs(g.c - col) > 0.62) col = Math.round(g.c);   // hysteresis: no flicker between frames
    if (Math.abs(g.r - row) > 0.62) row = Math.round(g.r);
    show(Math.max(0, Math.min(COLS - 1, col)), Math.max(0, Math.min(ROWS - 1, row)));
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);

  /* ───────── Garland: wire swagged over the robot with warm bulbs ─────────
     Coordinates are in the painting's 1274×1517 space. Back strands sit behind the lynx, front ones over it. */
  const NS = 'http://www.w3.org/2000/svg';
  const STRANDS = {
    nrkGarlandBack: [
      { pts: [[168, 352], [338, 372], [508, 366]], sag: 34 },               // under the sensor head
      { pts: [[160, 380], [162, 520], [166, 680]], sag: 10, side: true },   // left mast post
      { pts: [[514, 384], [518, 530], [522, 680]], sag: -10, side: true },  // right mast post
      { pts: [[158, 676], [368, 668], [574, 660]], sag: 16 },               // rail by the mast
      { pts: [[576, 656], [840, 704], [1100, 752], [1240, 806]], sag: 18 }, // far side rail
    ],
    nrkGarlandFront: [
      { pts: [[160, 680], [415, 778], [600, 796], [735, 902]], sag: 16 },   // near side rail
      { pts: [[760, 864], [1000, 838], [1244, 812]], sag: 26 },             // tailgate
    ],
  };
  const el = (tag, attrs, parent) => {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    parent.appendChild(e);
    return e;
  };
  let n = 0;
  const lamps = [];   // every bulb, in the painting's 1274×1517 space — the light maps below are drawn from these
  for (const id in STRANDS) {
    const svg = document.getElementById(id);
    if (!svg) continue;
    const f = el('filter', { id: id + 'Glow', x: '-200%', y: '-200%', width: '500%', height: '500%' }, el('defs', {}, svg));
    el('feGaussianBlur', { stdDeviation: 9 }, f);
    const bf = el('filter', { id: id + 'Bloom', x: '-300%', y: '-300%', width: '700%', height: '700%' }, svg.querySelector('defs'));
    el('feGaussianBlur', { stdDeviation: 22 }, bf);
    const blooms = el('g', { filter: `url(#${id}Bloom)` }, svg);
    const halos = el('g', { filter: `url(#${id}Glow)` }, svg);
    const wires = el('g', {}, svg);
    const bulbs = el('g', {}, svg);
    for (const st of STRANDS[id]) {
      let d = `M${st.pts[0][0]} ${st.pts[0][1]}`;
      for (let i = 1; i < st.pts.length; i++) {
        const [x0, y0] = st.pts[i - 1], [x1, y1] = st.pts[i];
        const cx = (x0 + x1) / 2 + (st.side ? st.sag : 0), cy = (y0 + y1) / 2 + (st.side ? 0 : st.sag * 2);
        d += ` Q${cx} ${cy} ${x1} ${y1}`;
      }
      const path = el('path', { d, class: 'wire' }, wires);
      const len = path.getTotalLength();
      for (let L = 17; L < len; L += 34) {
        const p = path.getPointAtLength(L);
        const delay = `${(-(n * 0.21) % 1.8).toFixed(2)}s`;
        el('circle', { cx: p.x, cy: p.y + 8, r: 30, class: 'bloom' }, blooms);
        el('circle', { cx: p.x, cy: p.y + 7, r: 26, class: 'halo', style: `animation-delay:${delay}` }, halos);
        lamps.push([p.x, p.y + 8]);
        el('rect', { x: p.x - 3, y: p.y - 1.5, width: 6, height: 5, rx: 1, class: 'cap' }, bulbs);
        el('ellipse', { cx: p.x, cy: p.y + 8, rx: 5.2, ry: 7.2, class: 'bulb', style: `animation-delay:${delay}` }, bulbs);
        n++;
      }
    }
  }

  /* ───────── Garland light maps ─────────
     The bulbs throw warm light: strongly onto the robot (canvas masked to the painted robot),
     and broadly onto the snow and painting around it (a large canvas screened over the scene). */
  const nrk = document.querySelector('.nrk');
  const bodyLight = document.querySelector('.nrk-light');
  const spill = document.querySelector('.nrk-spill canvas');
  const PW = 1274, PH = 1517;

  function sizeCanvas(c) {
    const r = c.getBoundingClientRect();
    const dpr = Math.min(devicePixelRatio || 1, 2);
    c.width = Math.max(1, Math.round(r.width * dpr));
    c.height = Math.max(1, Math.round(r.height * dpr));
    return c.getContext('2d');
  }
  function glowAt(ctx, x, y, r, rgba) {
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, rgba);
    g.addColorStop(1, 'rgba(255, 150, 40, 0)');
    ctx.fillStyle = g;
    ctx.fillRect(x - r, y - r, r * 2, r * 2);
  }

  function paintLight() {
    if (bodyLight) {
      const ctx = sizeCanvas(bodyLight);
      const sx = bodyLight.width / PW, sy = bodyLight.height / PH;
      ctx.globalCompositeOperation = 'lighter';
      for (const [x, y] of lamps) glowAt(ctx, x * sx, y * sy, 170 * sx, 'rgba(255, 160, 55, 0.075)');
      for (const [x, y] of lamps) glowAt(ctx, x * sx, y * sy, 60 * sx, 'rgba(255, 190, 100, 0.1)');
    }
    if (spill) {
      // spill canvas spans -70%…170% horizontally and -45%…145% vertically of the robot box
      const ctx = sizeCanvas(spill);
      const W = spill.width, H = spill.height;
      const map = (x, y) => [((x / PW) + 0.7) / 2.4 * W, ((y / PH) + 0.45) / 1.9 * H];
      ctx.globalCompositeOperation = 'lighter';
      const unit = W / 2.4 / PW;   // canvas px per painting px
      for (const [x, y] of lamps) {
        const [cx, cy] = map(x, y);
        glowAt(ctx, cx, cy, 460 * unit, 'rgba(255, 150, 50, 0.022)');
      }
      // a warm pool on the snow beneath the robot
      ctx.save();
      const [px, py] = map(PW * 0.5, PH * 0.98);
      ctx.translate(px, py);
      ctx.scale(1, 0.32);
      glowAt(ctx, 0, 0, 950 * unit, 'rgba(255, 150, 50, 0.55)');
      ctx.restore();
    }
  }
  if (nrk) new ResizeObserver(() => requestAnimationFrame(paintLight)).observe(nrk);

  /* ───────── Heat-haze distortion over the scene ───────── */
  const warp = document.getElementById('warpNoise');
  if (warp && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    let lastWarp = 0;
    const drift = (now) => {
      if (now - lastWarp > 50) {   // ~20 fps is plenty for a slow shimmer
        lastWarp = now;
        const t = now / 1000;
        warp.setAttribute('baseFrequency', `${(0.006 + 0.0014 * Math.sin(t * 0.37)).toFixed(5)} ${(0.018 + 0.004 * Math.sin(t * 0.29 + 1)).toFixed(5)}`);
      }
      requestAnimationFrame(drift);
    };
    requestAnimationFrame(drift);
  }

  /* ───────── Hop: the robot rises out of the drift on hover (tap on touch screens) ─────────
     The robot box has pointer-events: none (the book's canvas sits above it), so hit-testing is done here
     against the painted robot's bounds, ignoring the part hidden behind the drift. */
  const hero = document.querySelector('.hero');
  const driftLeft = document.querySelector('img.drift-left');
  let up = false, downTimer = 0;
  const overRobot = (x, y) => {
    const b = document.querySelector('.nrk-body').getBoundingClientRect();
    const d = driftLeft ? driftLeft.getBoundingClientRect() : null;
    const inX = x > b.left + b.width * 0.05 && x < b.right - b.width * 0.03;
    const inY = y > b.top + b.height * 0.18 && y < b.bottom - b.height * 0.1;
    // below the drift's crest line at the robot's position the robot is hidden; treat that as snow
    const crest = d ? d.top + d.height * 0.2 : Infinity;
    return inX && inY && y < Math.max(crest, b.top + b.height * 0.75);
  };
  const setUp = (v) => {
    if (v === up) return;
    up = v;
    hero.classList.toggle('nrk-up', v);
  };
  addEventListener('pointermove', (e) => {
    if (e.pointerType === 'touch') return;
    const on = overRobot(e.clientX, e.clientY);
    hero.classList.toggle('nrk-hover', on);
    clearTimeout(downTimer);
    if (on) setUp(true);
    else if (up) downTimer = setTimeout(() => setUp(false), 180);
  }, { passive: true });
  addEventListener('pointerdown', (e) => {
    if (e.pointerType === 'mouse' || !overRobot(e.clientX, e.clientY)) return;
    setUp(true);
    clearTimeout(downTimer);
    downTimer = setTimeout(() => setUp(false), 1400);
  }, { passive: true });
})();
