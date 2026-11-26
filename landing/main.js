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
