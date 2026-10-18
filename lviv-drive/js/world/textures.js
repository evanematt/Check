import * as THREE from 'three';
import { rng } from '../core/math.js';

function canvas(w, h = w) {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  return c;
}

function tex(cv, repeat = true) {
  const t = new THREE.CanvasTexture(cv);
  t.colorSpace = THREE.SRGBColorSpace;
  if (repeat) t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.anisotropy = 8;
  return t;
}

function noise(c, w, h, base, amp, n, r) {
  c.fillStyle = base;
  c.fillRect(0, 0, w, h);
  for (let i = 0; i < n; i++) {
    const v = Math.floor(r() * amp);
    c.fillStyle = `rgba(${v},${v},${v},0.08)`;
    c.fillRect(r() * w, r() * h, 1 + r() * 3, 1 + r() * 3);
  }
}

export function makeTextures() {
  const r = rng(42);
  const T = {};

  // Asphalt: 1 texture tile = 4 m.
  {
    const cv = canvas(256); const c = cv.getContext('2d');
    noise(c, 256, 256, '#3b3d40', 255, 9000, r);
    T.asphalt = tex(cv);
  }
  // Lviv cobblestone (бруківка): 1 tile = 2 m.
  {
    const cv = canvas(256); const c = cv.getContext('2d');
    c.fillStyle = '#4a4440'; c.fillRect(0, 0, 256, 256);
    for (let y = 0; y < 256; y += 21) {
      const off = (y / 21) % 2 ? 12 : 0;
      for (let x = -24; x < 256; x += 24) {
        const g = 95 + Math.floor(r() * 50);
        c.fillStyle = `rgb(${g},${g - 8},${g - 16})`;
        c.beginPath();
        c.ellipse(x + off + 12, y + 10, 10.5, 9, 0, 0, Math.PI * 2);
        c.fill();
      }
    }
    T.cobble = tex(cv);
  }
  // Sidewalk paving slabs: 1 tile = 3 m.
  {
    const cv = canvas(256); const c = cv.getContext('2d');
    noise(c, 256, 256, '#a7a39b', 120, 4000, r);
    c.strokeStyle = 'rgba(60,55,50,0.35)'; c.lineWidth = 2;
    for (let i = 0; i <= 256; i += 64) {
      c.beginPath(); c.moveTo(i, 0); c.lineTo(i, 256); c.stroke();
      c.beginPath(); c.moveTo(0, i); c.lineTo(256, i); c.stroke();
    }
    T.paving = tex(cv);
  }
  // Grass / park.
  {
    const cv = canvas(256); const c = cv.getContext('2d');
    c.fillStyle = '#4f6b3a'; c.fillRect(0, 0, 256, 256);
    for (let i = 0; i < 6000; i++) {
      const g = 70 + Math.floor(r() * 60);
      c.fillStyle = `rgba(${g - 20},${g + 30},${g - 30},0.5)`;
      c.fillRect(r() * 256, r() * 256, 1, 2);
    }
    T.grass = tex(cv);
  }

  // Facades: one tile = one window bay (3.6 m) x one floor (3.4 m).
  // Colours inspired by Lviv's Austro-Hungarian townhouses.
  const palettes = ['#e8c98f', '#d9a38a', '#b9c9a6', '#e4dcc8', '#9fb7c7', '#d8b4a0', '#c9a96e', '#efe2c4'];
  T.facades = palettes.map((col, i) => {
    const cv = canvas(128); const c = cv.getContext('2d');
    c.fillStyle = col; c.fillRect(0, 0, 128, 128);
    c.fillStyle = 'rgba(0,0,0,0.10)'; c.fillRect(0, 0, 128, 7); // cornice shadow
    c.fillStyle = 'rgba(255,255,255,0.25)'; c.fillRect(0, 7, 128, 3);
    c.fillStyle = '#f4efe6'; c.fillRect(34, 26, 60, 84); // window frame
    c.fillStyle = i % 2 ? '#2d3a46' : '#33404a'; c.fillRect(40, 32, 48, 72);
    c.fillStyle = '#f4efe6'; c.fillRect(62, 32, 4, 72); c.fillRect(40, 58, 48, 4);
    c.fillStyle = 'rgba(0,0,0,0.15)'; c.fillRect(30, 110, 68, 6); // sill
    if (i % 3 === 0) { c.fillStyle = 'rgba(255,255,255,0.35)'; c.fillRect(30, 18, 68, 6); }
    return tex(cv);
  });
  // Night: lit windows (emissive map). Random per tile isn't possible in a
  // repeating texture, so use a 4x4 bay atlas with a random lit pattern.
  {
    const cv = canvas(256); const c = cv.getContext('2d');
    c.fillStyle = '#000'; c.fillRect(0, 0, 256, 256);
    for (let by = 0; by < 4; by++) for (let bx = 0; bx < 4; bx++) {
      if (r() < 0.45) {
        c.fillStyle = r() < 0.5 ? '#ffcf7a' : '#ffe2a8';
        c.fillRect(bx * 64 + 20, by * 64 + 16, 24, 36);
      }
    }
    T.windowsLit = tex(cv);
    T.windowsLit.repeat.set(0.25, 0.25);
  }
  return T;
}
