import { $, h } from './dom.js';
import { Renderer } from './renderer.js';
import { createStore, clone, DEFAULT_ADJUST, DEFAULT_MOTION } from './state.js';
import { SHADERS, getShader, resolveValues, randomValues } from './shaders/index.js';
import { SAMPLES, fileToCanvas, previewSize } from './images.js';
import { createThumbs } from './thumbs.js';
import { createStage } from './ui/stage.js';
import { createTopbar } from './ui/topbar.js';
import { createStylesPanel } from './ui/styles-panel.js';
import { createControlsPanel } from './ui/controls-panel.js';
import { createDock } from './ui/dock.js';
import { createGenerativePanel } from './ui/generative-panel.js';
import { openLibrary, openHelp } from './ui/modals.js';
import { toast } from './ui/components.js';

const app = $('#app');
const canvas = $('#canvas');

// --- boot ----------------------------------------------------------------------
let renderer;
let thumbs;
try {
  renderer = new Renderer(canvas);
  thumbs = createThumbs(SHADERS);
} catch (err) {
  app.replaceChildren(h('div', { class: 'fatal' }, h('h1', {}, 'Shader Studio can’t start'), h('p', {}, err.message)));
  throw err;
}

const store = createStore({
  styleId: 'dots',
  params: {},
  adjust: clone(DEFAULT_ADJUST),
  motion: clone(DEFAULT_MOTION),
});

// --- engine ----------------------------------------------------------------------
let image = null; // { canvas, name }
let base = { w: 1, h: 1 }; // preview render size
let dirty = true;
let time = 0;
let last = performance.now();
let comparing = false;
let mouse = [0.5, 0.5];
const reportedErrors = new Set();
const uploads = []; // this session's uploads, for the Library
const imageListeners = new Set(); // notified whenever the working image changes

const markDirty = () => { dirty = true; };

function frameArgs(width, height, pxScale) {
  const s = store.state;
  const style = comparing ? null : getShader(s.styleId);
  return {
    style: style && style.id !== 'original' ? style : null,
    values: style ? resolveValues(style, s) : {},
    adjust: comparing ? null : s.adjust,
    time,
    mouse,
    width,
    height,
    pxScale,
  };
}

function draw() {
  const args = frameArgs(base.w, base.h, 1);
  const err = renderer.render(args);
  if (err && !reportedErrors.has(args.style.id)) {
    reportedErrors.add(args.style.id);
    toast(`“${args.style.name}” failed to compile - see the console`, { error: true });
  }
}

function loop(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  const s = store.state;
  if (getShader(s.styleId).animated && s.motion.playing && !comparing) {
    time += dt * s.motion.speed;
    dirty = true;
  }
  if (dirty && image) {
    dirty = false;
    draw();
  }
  requestAnimationFrame(loop);
}

function useImage(source, name, meta = {}) {
  image = { canvas: source, name };
  renderer.setImage(source);
  base = previewSize(source.width, source.height);
  stage.setSize(base.w, base.h);
  badge.textContent = `${source.width} × ${source.height}`;
  thumbs.generate(source, base.w);
  markDirty();
  imageListeners.forEach((fn) => fn(source, name, meta));
}

async function loadFile(file) {
  try {
    const c = await fileToCanvas(file);
    const name = file.name.replace(/\.[^.]+$/, '') || 'image';
    uploads.unshift({ name, canvas: c });
    uploads.length = Math.min(uploads.length, 12);
    useImage(c, name);
  } catch (err) {
    toast(err.message || 'Could not load that image', { error: true });
  }
}

// --- actions -------------------------------------------------------------------
const fileInput = $('#fileInput');
fileInput.addEventListener('change', () => {
  const f = fileInput.files?.[0];
  if (f) loadFile(f);
  fileInput.value = '';
});

const mobile = matchMedia('(max-width: 900px)');
const busy = $('#busy');
const actions = {
  upload: () => fileInput.click(),
  useImage,
  onImage: (fn) => imageListeners.add(fn),
  setBusy(text) {
    busy.hidden = !text;
    busy.querySelector('span').textContent = text || '';
  },
  library: () => openLibrary({ samples: SAMPLES, uploads, onPick: useImage, onUpload: actions.upload }),
  help: openHelp,
  fit: () => stage.fit(),
  zoomBy: (k) => stage.zoomBy(k),
  undo: () => store.undo(),
  redo: () => store.redo(),
  restartTime: () => { time = 0; markDirty(); },
  setCompare(on) {
    if (comparing === on) return;
    comparing = on;
    $('#compareBadge').hidden = !on;
    markDirty();
  },
  resetAll() {
    store.update((s) => {
      delete s.params[s.styleId];
      s.adjust = clone(DEFAULT_ADJUST);
    }, { structure: true });
    store.commit();
  },
  restyle() {
    const others = SHADERS.filter((s) => s.id !== store.state.styleId && s.id !== 'original');
    if (!others.length) return;
    const pick = others[Math.floor(Math.random() * others.length)];
    store.update((s) => { s.styleId = pick.id; }, { structure: true });
    store.commit();
  },
  shuffle() {
    const style = getShader(store.state.styleId);
    if (!style.params.length) return toast('This style has nothing to shuffle');
    store.update((s) => { s.params[style.id] = { ...(s.params[style.id] || {}), ...randomValues(style) }; }, { structure: true });
    store.commit();
  },
  togglePanel(side) {
    const cls = mobile.matches ? `${side}-open` : `${side}-collapsed`;
    if (mobile.matches) app.classList.remove(side === 'left' ? 'right-open' : 'left-open');
    app.classList.toggle(cls);
    requestAnimationFrame(() => stage.fit());
  },
  toggleWide: () => app.classList.toggle('left-wide'),

  // export
  maxExportSize: renderer.maxSize,
  getBaseSize: () => ({ ...base }),
  fileName: () => `shader-studio-${store.state.styleId}`,
  async exportImage({ mime, scale, quality }) {
    const w = base.w * scale, h2 = base.h * scale;
    const args = frameArgs(w, h2, scale);
    const err = renderer.render(args);
    if (err) throw new Error('This style failed to compile, nothing to export.');
    const blob = await new Promise((res) => canvas.toBlob(res, mime, quality));
    markDirty(); // canvas was resized for export; redraw at preview size
    return blob;
  },
};

// --- UI --------------------------------------------------------------------------
const badge = $('#stageBadge');
const stage = createStage({
  stage: $('#stage'),
  wrap: $('#canvasWrap'),
  canvas,
  onZoom: (z) => topbar.setZoom(z),
  onPointer: (m) => { mouse = m; if (getShader(store.state.styleId).animated) markDirty(); },
});
const topbar = createTopbar({ root: $('#topbar'), actions });
createStylesPanel({ root: $('#stylesPanel'), store, shaders: SHADERS, thumbs, actions, generative: createGenerativePanel({ actions }) });
createControlsPanel({ root: $('#controlsPanel'), store, actions });
createDock({ root: $('#dock'), actions });

store.on('render', markDirty);
store.on('history', () => topbar.setHistory({ canUndo: store.canUndo(), canRedo: store.canRedo() }));
topbar.setHistory({ canUndo: false, canRedo: false });

// --- keyboard ----------------------------------------------------------------
addEventListener('keydown', (e) => {
  if (e.target.closest?.('input[type=text], input[type=search], textarea, select, [contenteditable]')) return;
  if (document.querySelector('.modal-overlay')) return;
  const mod = e.ctrlKey || e.metaKey;
  const k = e.key.toLowerCase();
  if (mod && k === 'z') { e.preventDefault(); e.shiftKey ? store.redo() : store.undo(); return; }
  if (mod && k === 'y') { e.preventDefault(); store.redo(); return; }
  if (mod || e.altKey) return;
  if (k === 'c' && !e.repeat) actions.setCompare(true);
  else if (k === 'r') actions.restyle();
  else if (k === 's') actions.shuffle();
  else if (k === '0') stage.fit();
  else if (k === '+' || k === '=') stage.zoomBy(1.25);
  else if (k === '-' || k === '_') stage.zoomBy(1 / 1.25);
  else if (k === '?') openHelp();
});
addEventListener('keyup', (e) => { if (e.key.toLowerCase() === 'c') actions.setCompare(false); });
addEventListener('blur', () => actions.setCompare(false));

// --- drop & paste ------------------------------------------------------------------
const dropOverlay = $('#dropOverlay');
let dragDepth = 0;
addEventListener('dragenter', (e) => { if (e.dataTransfer?.types.includes('Files')) { dragDepth++; dropOverlay.hidden = false; } });
addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; dropOverlay.hidden = true; } });
addEventListener('dragover', (e) => e.preventDefault());
addEventListener('drop', (e) => {
  e.preventDefault();
  dragDepth = 0;
  dropOverlay.hidden = true;
  const f = [...(e.dataTransfer?.files || [])].find((x) => x.type.startsWith('image/'));
  if (f) loadFile(f);
});
addEventListener('paste', (e) => {
  const f = [...(e.clipboardData?.files || [])].find((x) => x.type.startsWith('image/'));
  if (f) loadFile(f);
});

// --- go ----------------------------------------------------------------------------
useImage(SAMPLES[0].canvas, SAMPLES[0].name);
requestAnimationFrame(loop);
