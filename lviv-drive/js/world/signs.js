import * as THREE from 'three';

// Procedural road signs drawn on a canvas (Ukrainian ДСТУ 4100 style,
// simplified). Used for 3D sign plates and for the theory-test pictures.

const RED = '#d61f26';
const BLUE = '#1f5fbf';
const YELLOW = '#f6c41a';
const S = 256;

function circle(c, r, fill, stroke, lw) {
  c.beginPath();
  c.arc(S / 2, S / 2, r, 0, Math.PI * 2);
  if (fill) { c.fillStyle = fill; c.fill(); }
  if (stroke) { c.lineWidth = lw; c.strokeStyle = stroke; c.stroke(); }
}

function pedestrian(c, x, y, k, color) {
  c.save();
  c.translate(x, y);
  c.scale(k, k);
  c.strokeStyle = color;
  c.fillStyle = color;
  c.lineCap = 'round';
  c.lineWidth = 9;
  c.beginPath(); c.arc(4, -48, 11, 0, Math.PI * 2); c.fill();
  c.beginPath();
  c.moveTo(2, -32); c.lineTo(-4, 8); // body
  c.moveTo(-4, 8); c.lineTo(-22, 46); // back leg
  c.moveTo(-4, 8); c.lineTo(16, 24); c.lineTo(20, 48); // front leg
  c.moveTo(0, -24); c.lineTo(-20, -2); // arm
  c.moveTo(2, -24); c.lineTo(22, -8);
  c.stroke();
  c.restore();
}

function blueSquare(c) {
  c.fillStyle = '#fff';
  roundRect(c, 8, 8, S - 16, S - 16, 18); c.fill();
  c.fillStyle = BLUE;
  roundRect(c, 18, 18, S - 36, S - 36, 12); c.fill();
}

function roundRect(c, x, y, w, h, r) {
  c.beginPath();
  c.moveTo(x + r, y);
  c.arcTo(x + w, y, x + w, y + h, r);
  c.arcTo(x + w, y + h, x, y + h, r);
  c.arcTo(x, y + h, x, y, r);
  c.arcTo(x, y, x + w, y, r);
  c.closePath();
}

function triangle(c, up, fill, stroke) {
  c.beginPath();
  if (up) { c.moveTo(S / 2, 14); c.lineTo(S - 10, S - 30); c.lineTo(10, S - 30); }
  else { c.moveTo(10, 30); c.lineTo(S - 10, 30); c.lineTo(S / 2, S - 14); }
  c.closePath();
  c.lineJoin = 'round';
  c.fillStyle = fill; c.fill();
  c.lineWidth = 22; c.strokeStyle = stroke; c.stroke();
}

const DRAW = {
  giveway: (c) => triangle(c, false, '#fff', RED),
  stop: (c) => {
    c.beginPath();
    for (let i = 0; i < 8; i++) {
      const a = Math.PI / 8 + (i * Math.PI) / 4;
      c.lineTo(S / 2 + Math.cos(a) * 120, S / 2 + Math.sin(a) * 120);
    }
    c.closePath();
    c.fillStyle = RED; c.fill();
    c.lineWidth = 8; c.strokeStyle = '#fff'; c.stroke();
    c.fillStyle = '#fff';
    c.font = 'bold 74px Arial, sans-serif';
    c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText('STOP', S / 2, S / 2 + 4);
  },
  priority: (c, end) => {
    c.save();
    c.translate(S / 2, S / 2); c.rotate(Math.PI / 4);
    c.fillStyle = '#fff'; c.fillRect(-86, -86, 172, 172);
    c.lineWidth = 3; c.strokeStyle = '#333'; c.strokeRect(-86, -86, 172, 172);
    c.fillStyle = YELLOW; c.fillRect(-60, -60, 120, 120);
    c.restore();
    if (end) {
      c.strokeStyle = '#222'; c.lineWidth = 7;
      for (let i = -2; i <= 2; i++) { c.beginPath(); c.moveTo(60 + i * 9, 190 + i * 9); c.lineTo(190 + i * 9, 60 + i * 9); c.stroke(); }
    }
  },
  noentry: (c) => {
    circle(c, 118, RED);
    c.fillStyle = '#fff'; c.fillRect(38, 108, 180, 40);
  },
  notraffic: (c) => circle(c, 106, '#fff', RED, 26),
  nostop: (c) => {
    circle(c, 106, BLUE, RED, 26);
    c.strokeStyle = RED; c.lineWidth = 22;
    c.beginPath(); c.moveTo(58, 58); c.lineTo(198, 198); c.moveTo(198, 58); c.lineTo(58, 198); c.stroke();
  },
  nopark: (c) => {
    circle(c, 106, BLUE, RED, 26);
    c.strokeStyle = RED; c.lineWidth = 22;
    c.beginPath(); c.moveTo(58, 58); c.lineTo(198, 198); c.stroke();
  },
  speed: (c, n) => {
    circle(c, 106, '#fff', RED, 26);
    c.fillStyle = '#111';
    c.font = `bold ${n >= 100 ? 82 : 104}px Arial, sans-serif`;
    c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText(String(n), S / 2, S / 2 + 6);
  },
  crossing: (c) => {
    blueSquare(c);
    c.fillStyle = '#fff';
    c.beginPath(); c.moveTo(S / 2, 34); c.lineTo(S - 34, S - 44); c.lineTo(34, S - 44); c.closePath(); c.fill();
    c.fillStyle = '#111';
    for (let i = 0; i < 4; i++) c.fillRect(70 + i * 32, S - 70, 18, 14);
    pedestrian(c, S / 2, 150, 0.95, '#111');
  },
  crossingwarn: (c) => {
    triangle(c, true, '#fff', RED);
    pedestrian(c, S / 2, 150, 0.9, '#111');
  },
  residential: (c) => {
    blueSquare(c);
    c.fillStyle = '#fff';
    c.beginPath(); c.moveTo(70, 120); c.lineTo(128, 64); c.lineTo(186, 120); c.closePath(); c.fill();
    c.fillRect(82, 118, 92, 70);
    c.fillStyle = BLUE; c.fillRect(116, 150, 24, 38);
    pedestrian(c, 205, 196, 0.45, '#fff');
    pedestrian(c, 52, 196, 0.38, '#fff');
  },
  endresidential: (c) => {
    DRAW.residential(c);
    c.strokeStyle = RED; c.lineWidth = 14;
    c.beginPath(); c.moveTo(30, 226); c.lineTo(226, 30); c.stroke();
  },
  pedzone: (c) => {
    blueSquare(c);
    pedestrian(c, 112, 140, 1.25, '#fff');
    pedestrian(c, 170, 160, 0.9, '#fff');
  },
  oneway: (c) => {
    blueSquare(c);
    c.fillStyle = '#fff';
    c.beginPath(); c.moveTo(128, 40); c.lineTo(180, 108); c.lineTo(146, 108); c.lineTo(146, 216);
    c.lineTo(110, 216); c.lineTo(110, 108); c.lineTo(76, 108); c.closePath(); c.fill();
  },
  lightwarn: (c) => {
    triangle(c, true, '#fff', RED);
    c.fillStyle = '#222'; roundRect(c, 106, 92, 44, 108, 10); c.fill();
    [[RED, 112], [YELLOW, 145], ['#22b14c', 178]].forEach(([col, y]) => {
      c.fillStyle = col; c.beginPath(); c.arc(128, y, 12, 0, Math.PI * 2); c.fill();
    });
  },
  parking: (c) => {
    blueSquare(c);
    c.fillStyle = '#fff';
    c.font = 'bold 170px Arial, sans-serif';
    c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText('P', S / 2, S / 2 + 10);
  },
};

export const SIGN_NAMES = {
  giveway: '2.1 «Дати дорогу»',
  stop: '2.2 «Проїзд без зупинки заборонено»',
  priority: '2.3 «Головна дорога»',
  endpriority: '2.4 «Кінець головної дороги»',
  notraffic: '3.1 «Рух заборонено»',
  noentry: '3.21 «В\'їзд заборонено»',
  nostop: '3.34 «Зупинку заборонено»',
  nopark: '3.35 «Стоянку заборонено»',
  residential: '5.31 «Житлова зона»',
  endresidential: '«Кінець житлової зони»',
  pedzone: '5.33 «Пішохідна зона»',
  crossing: '«Пішохідний перехід»',
  crossingwarn: 'Попереджувальний «Пішохідний перехід»',
  oneway: '«Дорога з одностороннім рухом»',
  lightwarn: '«Світлофорне регулювання»',
  parking: '«Місце для стоянки»',
};

export function drawSign(canvas, kind) {
  const c = canvas.getContext('2d');
  c.clearRect(0, 0, S, S);
  const m = /^speed(\d+)$/.exec(kind);
  if (m) DRAW.speed(c, Number(m[1]));
  else if (kind === 'endpriority') DRAW.priority(c, true);
  else (DRAW[kind] || DRAW.notraffic)(c);
}

const cache = new Map();
export function signTexture(kind) {
  if (cache.has(kind)) return cache.get(kind);
  const cv = document.createElement('canvas');
  cv.width = cv.height = S;
  drawSign(cv, kind);
  const tex = new THREE.CanvasTexture(cv);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  cache.set(kind, tex);
  return tex;
}

export function signDataURL(kind) {
  const cv = document.createElement('canvas');
  cv.width = cv.height = S;
  drawSign(cv, kind);
  return cv.toDataURL();
}
