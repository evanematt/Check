// Twinkling snow sparkles + faint star motes over the hero painting.
(() => {
  const canvas = document.querySelector('.hero-sparkles');
  if (!canvas) return;
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const ctx = canvas.getContext('2d');
  let w = 0, h = 0, dpr = 1, motes = [], raf = 0, visible = true;

  function seed() {
    const count = Math.round(Math.min(140, (w * h) / 9000));
    motes = Array.from({ length: count }, () => {
      const inSnow = Math.random() < 0.7;
      return {
        x: Math.random() * w,
        // the snowfield occupies roughly the lower 55% of the painting
        y: inSnow ? h * (0.45 + Math.random() * 0.55) : Math.random() * h * 0.45,
        r: (inSnow ? 0.6 : 0.4) + Math.random() * 1.1,
        phase: Math.random() * Math.PI * 2,
        speed: 0.6 + Math.random() * 1.6,
        hue: inSnow ? 230 : 210 + Math.random() * 40,
      };
    });
  }

  function resize() {
    const rect = canvas.getBoundingClientRect();
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = rect.width; h = rect.height;
    canvas.width = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    seed();
    if (reduce) draw(0);
  }

  function draw(t) {
    ctx.clearRect(0, 0, w, h);
    const s = t / 1000;
    for (const m of motes) {
      const a = Math.max(0, Math.sin(s * m.speed + m.phase));
      const alpha = a * a * 0.9;
      if (alpha < 0.02) continue;
      const g = ctx.createRadialGradient(m.x, m.y, 0, m.x, m.y, m.r * 4);
      g.addColorStop(0, `hsla(${m.hue}, 100%, 96%, ${alpha})`);
      g.addColorStop(1, `hsla(${m.hue}, 100%, 80%, 0)`);
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(m.x, m.y, m.r * 4, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function loop(t) {
    draw(t);
    raf = visible ? requestAnimationFrame(loop) : 0;
  }

  new ResizeObserver(resize).observe(canvas);
  resize();
  if (reduce) return;

  new IntersectionObserver(([e]) => {
    visible = e.isIntersecting;
    if (visible && !raf) raf = requestAnimationFrame(loop);
  }).observe(canvas);
})();

// Constant snowfall: three depth layers (far = small and slow, near = bigger, faster, softer), drifting in a light wind.
(() => {
  const canvas = document.querySelector('.snowfall');
  if (!canvas) return;
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const ctx = canvas.getContext('2d');
  const LAYERS = [
    { share: 0.55, r: [0.6, 1.2], v: [18, 32], a: [0.35, 0.6] },   // far
    { share: 0.33, r: [1.3, 2.2], v: [34, 58], a: [0.55, 0.8] },   // mid
    { share: 0.12, r: [2.4, 3.8], v: [60, 95], a: [0.6, 0.9] },    // near
  ];
  let w = 0, h = 0, flakes = [];
  const rnd = (a, b) => a + Math.random() * (b - a);

  function seed() {
    const total = Math.round(Math.min(420, (w * h) / 5200));
    flakes = [];
    for (const L of LAYERS) {
      for (let i = 0; i < Math.round(total * L.share); i++) {
        flakes.push({
          x: Math.random() * w, y: Math.random() * h,
          r: rnd(...L.r), v: rnd(...L.v), a: rnd(...L.a),
          sway: rnd(8, 26), freq: rnd(0.3, 0.9), phase: Math.random() * Math.PI * 2,
        });
      }
    }
  }

  function resize() {
    const dpr = Math.min(devicePixelRatio || 1, 2);
    w = canvas.clientWidth; h = canvas.clientHeight;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    seed();
    if (reduce) draw(0);
  }

  function draw(t) {
    ctx.clearRect(0, 0, w, h);
    for (const f of flakes) {
      const x = f.x + Math.sin(t * f.freq + f.phase) * f.sway;
      if (f.r > 2.2) {   // near flakes are soft, out-of-focus
        const g = ctx.createRadialGradient(x, f.y, 0, x, f.y, f.r * 1.8);
        g.addColorStop(0, `rgba(240, 244, 255, ${f.a})`);
        g.addColorStop(1, 'rgba(240, 244, 255, 0)');
        ctx.fillStyle = g;
        ctx.beginPath(); ctx.arc(x, f.y, f.r * 1.8, 0, Math.PI * 2); ctx.fill();
      } else {
        ctx.fillStyle = `rgba(240, 244, 255, ${f.a})`;
        ctx.beginPath(); ctx.arc(x, f.y, f.r, 0, Math.PI * 2); ctx.fill();
      }
    }
  }

  let last = performance.now();
  function loop(now) {
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now;
    const wind = 10 + 8 * Math.sin(now / 5000);
    for (const f of flakes) {
      f.y += f.v * dt;
      f.x += wind * dt * (f.r / 2);
      if (f.y - f.r * 2 > h) { f.y = -f.r * 2; f.x = Math.random() * w; }
      if (f.x > w + 30) f.x = -30;
    }
    draw(now / 1000);
    requestAnimationFrame(loop);
  }

  new ResizeObserver(resize).observe(canvas);
  if (!reduce) requestAnimationFrame(loop);
})();
