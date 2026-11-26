// Ground robot with the lynx: the lynx turns its head towards the cursor,
// and the wheels sit in a painted snow drift.
(() => {
  const lynx = document.getElementById('nrkLynx');
  const snow = document.querySelector('.nrk-snow');
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
    lynx.style.backgroundPosition = `${(col * 100) / (COLS - 1)}% ${(row * 100) / (ROWS - 1)}%`;
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
  for (const id in STRANDS) {
    const svg = document.getElementById(id);
    if (!svg) continue;
    const f = el('filter', { id: id + 'Glow', x: '-200%', y: '-200%', width: '500%', height: '500%' }, el('defs', {}, svg));
    el('feGaussianBlur', { stdDeviation: 7 }, f);
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
        el('circle', { cx: p.x, cy: p.y + 6, r: 19, class: 'halo', style: `animation-delay:${delay}` }, halos);
        el('rect', { x: p.x - 3, y: p.y - 1.5, width: 6, height: 5, rx: 1, class: 'cap' }, bulbs);
        el('ellipse', { cx: p.x, cy: p.y + 8, rx: 5.2, ry: 7.2, class: 'bulb', style: `animation-delay:${delay}` }, bulbs);
        n++;
      }
    }
  }

  /* ───────── Snow drift in front of the wheels ───────── */
  if (!snow) return;
  // value noise for a lumpy top edge
  const hash = (x) => { const n = Math.sin(x * 127.1) * 43758.5453; return n - Math.floor(n); };
  const noise = (x) => { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return hash(i) + (hash(i + 1) - hash(i)) * u; };
  const rnd = (a, b) => a + Math.random() * (b - a);

  function paint() {
    const w = snow.clientWidth, h = snow.clientHeight;
    if (!w || !h) return;
    const dpr = Math.min(devicePixelRatio || 1, 2);
    snow.width = Math.round(w * dpr);
    snow.height = Math.round(h * dpr);
    const ctx = snow.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);

    // drift silhouette: a long low mound with lumps where it piles against the wheels
    const top = (x) => {
      const u = x / w;
      const mound = Math.sin(Math.PI * u) ** 0.7;
      const wheels = Math.exp(-(((u - 0.24) / 0.09) ** 2)) * 0.35 + Math.exp(-(((u - 0.76) / 0.1) ** 2)) * 0.25;
      return h * (1 - (0.55 * mound + wheels * mound + (noise(u * 9) - 0.5) * 0.14 * mound));
    };
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(0, h);
    for (let x = 0; x <= w; x += 2) ctx.lineTo(x, top(x));
    ctx.lineTo(w, h);
    ctx.closePath();
    const fill = ctx.createLinearGradient(0, h * 0.2, 0, h);
    fill.addColorStop(0, '#6074e6');
    fill.addColorStop(0.45, '#2e48c6');
    fill.addColorStop(1, '#152a8c');
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.clip();

    // brush strokes, mostly horizontal, like the painting
    const tones = ['#8796f5', '#4d66e0', '#aeb8ff', '#2c45c4', '#6c80ee'];
    ctx.lineCap = 'round';
    for (let i = 0; i < 520; i++) {
      const x = rnd(-20, w), y = rnd(0, h);
      const len = rnd(14, 60), a = rnd(-0.18, 0.12);
      ctx.strokeStyle = tones[(Math.random() * tones.length) | 0];
      ctx.globalAlpha = rnd(0.15, 0.45) * (1 - y / h * 0.6);
      ctx.lineWidth = rnd(1.5, 4.5);
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.quadraticCurveTo(x + len / 2, y - 3, x + Math.cos(a) * len, y + Math.sin(a) * len);
      ctx.stroke();
    }
    // bright crest along the top edge
    ctx.globalAlpha = 0.45;
    ctx.strokeStyle = '#c9d0ff';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    for (let x = 0; x <= w; x += 3) ctx[x ? 'lineTo' : 'moveTo'](x, top(x) + 1.5);
    ctx.stroke();
    // glitter
    for (let i = 0; i < 260; i++) {
      ctx.globalAlpha = rnd(0.4, 1);
      ctx.fillStyle = '#ffffff';
      ctx.beginPath();
      ctx.arc(rnd(0, w), rnd(0, h), rnd(0.4, 1.4), 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    // fade the drift's ends and bottom into the painted snow
    ctx.globalCompositeOperation = 'destination-in';
    const fadeX = ctx.createLinearGradient(0, 0, w, 0);
    fadeX.addColorStop(0, 'rgba(0,0,0,0)');
    fadeX.addColorStop(0.14, 'rgba(0,0,0,1)');
    fadeX.addColorStop(0.86, 'rgba(0,0,0,1)');
    fadeX.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.globalAlpha = 1;
    ctx.fillStyle = fadeX;
    ctx.fillRect(0, 0, w, h);
    const fadeY = ctx.createLinearGradient(0, 0, 0, h);
    fadeY.addColorStop(0, 'rgba(0,0,0,1)');
    fadeY.addColorStop(0.75, 'rgba(0,0,0,1)');
    fadeY.addColorStop(1, 'rgba(0,0,0,0.2)');
    ctx.fillStyle = fadeY;
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
  }

  let raf = 0;
  new ResizeObserver(() => { cancelAnimationFrame(raf); raf = requestAnimationFrame(paint); }).observe(snow);
})();
