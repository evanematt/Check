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

  // textures cut from the background painting: brushy snow body + glittering crest
  const load = (src) => new Promise((res) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => res(null); i.src = src; });
  let tex = null;

  function paint() {
    const w = snow.clientWidth, h = snow.clientHeight;
    if (!w || !h || !tex) return;
    const [body, crest] = tex;
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
    const outline = () => {
      ctx.beginPath();
      ctx.moveTo(0, h);
      for (let x = 0; x <= w; x += 2) ctx.lineTo(x, top(x));
      ctx.lineTo(w, h);
      ctx.closePath();
    };

    // body: the painting's own dark brushwork
    ctx.save();
    outline();
    ctx.clip();
    if (body) ctx.drawImage(body, 0, 0, body.width, body.height, -w * 0.05, h * 0.05, w * 1.1, h * 1.1);
    // the side away from the star sinks into shadow
    const shade = ctx.createLinearGradient(0, 0, w, 0);
    shade.addColorStop(0, 'rgba(10, 20, 80, 0)');
    shade.addColorStop(1, 'rgba(10, 20, 80, 0.35)');
    ctx.fillStyle = shade;
    ctx.fillRect(0, 0, w, h);

    // crest: glittering lavender snow along the top edge, soft towards the body, like the painted ridges
    if (crest) {
      const band = document.createElement('canvas');
      band.width = snow.width; band.height = snow.height;
      const b = band.getContext('2d');
      b.setTransform(dpr, 0, 0, dpr, 0, 0);
      b.fillStyle = b.createPattern(crest, 'repeat');
      b.beginPath();
      b.moveTo(0, h);
      for (let x = 0; x <= w; x += 2) b.lineTo(x, top(x));
      b.lineTo(w, h);
      b.closePath();
      b.fill();
      // carve away everything below a lumpy line under the top edge, with a blurred edge
      b.globalCompositeOperation = 'destination-out';
      b.filter = `blur(${Math.max(3, h * 0.05)}px)`;
      b.fillStyle = '#000';
      b.beginPath();
      b.moveTo(-20, h + 40);
      for (let x = -20; x <= w + 20; x += 3) b.lineTo(x, top(Math.min(w, Math.max(0, x))) + h * (0.1 + 0.16 * noise(x / w * 7 + 3)));
      b.lineTo(w + 20, h + 40);
      b.closePath();
      b.fill();
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.globalAlpha = 0.85;
      ctx.drawImage(band, 0, 0);
      ctx.globalAlpha = 1;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    ctx.restore();

    // fade the drift's ends and bottom into the painted snow
    ctx.globalCompositeOperation = 'destination-in';
    const fadeX = ctx.createLinearGradient(0, 0, w, 0);
    fadeX.addColorStop(0, 'rgba(0,0,0,0)');
    fadeX.addColorStop(0.16, 'rgba(0,0,0,1)');
    fadeX.addColorStop(0.84, 'rgba(0,0,0,1)');
    fadeX.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = fadeX;
    ctx.fillRect(0, 0, w, h);
    const fadeY = ctx.createLinearGradient(0, 0, 0, h);
    fadeY.addColorStop(0, 'rgba(0,0,0,1)');
    fadeY.addColorStop(0.75, 'rgba(0,0,0,1)');
    fadeY.addColorStop(1, 'rgba(0,0,0,0.3)');
    ctx.fillStyle = fadeY;
    ctx.fillRect(0, 0, w, h);
    ctx.globalCompositeOperation = 'source-over';
  }

  Promise.all([load('assets/snow-body.webp'), load('assets/snow-crest.webp')]).then((t) => { tex = t; paint(); });

  let raf = 0;
  new ResizeObserver(() => { cancelAnimationFrame(raf); raf = requestAnimationFrame(paint); }).observe(snow);
})();
