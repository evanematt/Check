import { $, h } from './dom.js';
import { Renderer } from './renderer.js';
import { createStore, clone, DEFAULT_ADJUST, DEFAULT_MOTION } from './state.js';
import { SHADERS, getShader, resolveValues, randomValues } from './shaders/index.js';
import { SAMPLES, fileToCanvas, previewSize, makeDemoVideo, toCanvas } from './images.js';
import { createThumbs } from './thumbs.js';
import { createStage } from './ui/stage.js';
import { createTopbar } from './ui/topbar.js';
import { createStylesPanel } from './ui/styles-panel.js';
import { createControlsPanel } from './ui/controls-panel.js';
import { createDock } from './ui/dock.js';
import { createVideoBar } from './ui/video-bar.js';
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
let image = null; // { name }
let video = null; // HTMLVideoElement when the working source is a video
let disposeVideo = null;
let videoDirty = false; // a new frame (seek, first frame) must be uploaded while paused
let base = { w: 1, h: 1 }; // preview render size
let dirty = true;
let time = 0;
let last = performance.now();
let comparing = false;
let mouse = [0.5, 0.5];
let recorder = null;
const reportedErrors = new Set();
const uploads = []; // this session's uploads, for the Library
const imageListeners = new Set(); // notified whenever the working image changes
const RECORD_LIVE_SECONDS = 8; // length of a recording of a stream without a duration

const markDirty = () => { dirty = true; };
const isFiniteVideo = () => video && Number.isFinite(video.duration);

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
  const style = getShader(s.styleId);
  if (video) {
    if (video.readyState >= 2 && (!video.paused || videoDirty)) {
      renderer.updateImage(video);
      videoDirty = false;
      dirty = true;
    }
    // effects follow the video clock (a stream without a duration keeps its own clock)
    if (isFiniteVideo()) time = video.currentTime * s.motion.speed;
    else if (!video.paused) time += dt * s.motion.speed;
    videoBar.update();
  } else if (style.animated && s.motion.playing && !comparing) {
    time += dt * s.motion.speed;
    dirty = true;
  }
  if (dirty && image) {
    dirty = false;
    draw();
  }
  requestAnimationFrame(loop);
}

/** Common tail of loading any source (canvas or video element). */
function applySource(source, w, h, name, meta) {
  image = { name };
  renderer.setImage(source);
  base = previewSize(w, h);
  stage.setSize(base.w, base.h);
  badge.textContent = `${w} × ${h}`;
  thumbs.generate(source, base.w);
  markDirty();
  const still = video ? toCanvas(source, 1024) : source;
  imageListeners.forEach((fn) => fn(still, name, meta));
}

function releaseVideo() {
  if (recorder) recorder.stop();
  if (!video) return;
  video.pause();
  disposeVideo?.();
  video = null;
  disposeVideo = null;
  videoBar.hide();
}

function useImage(source, name, meta = {}) {
  releaseVideo();
  applySource(source, source.width, source.height, name, meta);
}

async function useVideo(el, name, dispose) {
  releaseVideo();
  const ready = new Promise((resolve, reject) => {
    if (el.readyState >= 2) return resolve();
    el.addEventListener('loadeddata', resolve, { once: true });
    el.addEventListener('error', () => reject(new Error('This browser can’t play that video. Try MP4 (H.264) or WebM.')), { once: true });
  });
  await ready;
  video = el;
  disposeVideo = dispose;
  el.addEventListener('seeked', () => { videoDirty = true; });
  time = 0;
  await el.play().catch(() => {});
  applySource(el, el.videoWidth, el.videoHeight, name, { video: true });
  videoBar.show(el);
}

function loadVideoFile(file) {
  const url = URL.createObjectURL(file);
  const el = document.createElement('video');
  el.muted = true;
  el.loop = true;
  el.playsInline = true;
  el.preload = 'auto';
  el.src = url;
  return useVideo(el, file.name.replace(/\.[^.]+$/, '') || 'video', () => {
    URL.revokeObjectURL(url);
    el.removeAttribute('src');
    el.load();
  });
}

function loadDemoVideo() {
  const demo = makeDemoVideo();
  return useVideo(demo.video, 'Animated demo', () => demo.dispose());
}

const isVideoFile = (f) => f.type.startsWith('video/') || /\.(mp4|webm|mov|m4v|ogv)$/i.test(f.name);

async function loadFile(file) {
  try {
    if (isVideoFile(file)) return await loadVideoFile(file);
    const c = await fileToCanvas(file);
    const name = file.name.replace(/\.[^.]+$/, '') || 'image';
    uploads.unshift({ name, canvas: c });
    uploads.length = Math.min(uploads.length, 12);
    useImage(c, name);
  } catch (err) {
    toast(err.message || 'Could not load that file', { error: true });
  }
}

// --- video controls ------------------------------------------------------------------
function toggleVideo() {
  if (!video) return;
  if (video.paused || video.ended) video.play().catch(() => {});
  else video.pause();
}

const pickRecorderType = () =>
  typeof MediaRecorder === 'undefined'
    ? null
    : ['video/mp4;codecs=avc1.42E01E', 'video/webm;codecs=vp9', 'video/webm;codecs=vp8', 'video/webm'].find((t) => MediaRecorder.isTypeSupported(t)) || null;

/** Record the rendered canvas in real time while the video plays once from the start. */
async function toggleRecord() {
  if (recorder) { recorder.stop(); return; }
  if (!video) { toast('Load a video first (Upload or Library)'); return; }
  const type = pickRecorderType();
  if (!type) { toast('This browser can’t record video', { error: true }); return; }

  const rec = new MediaRecorder(canvas.captureStream(30), { mimeType: type, videoBitsPerSecond: 10_000_000 });
  const chunks = [];
  rec.ondataavailable = (e) => { if (e.data.size) chunks.push(e.data); };
  const stopped = new Promise((res) => { rec.onstop = res; });
  const wasLoop = video.loop;
  const finite = isFiniteVideo();
  video.loop = false;
  if (finite && video.currentTime > 0) {
    video.currentTime = 0;
    await new Promise((res) => video.addEventListener('seeked', res, { once: true }));
  }
  recorder = rec;
  videoBar.setRecording(true);
  const t0 = performance.now();
  const tick = setInterval(() => actions.setBusy(`● Recording ${((performance.now() - t0) / 1000).toFixed(1)}s · click Stop to finish`), 200);
  const stop = () => { if (rec.state !== 'inactive') rec.stop(); };
  let timer = 0;
  if (finite) video.addEventListener('ended', stop, { once: true });
  else timer = setTimeout(stop, RECORD_LIVE_SECONDS * 1000);
  await video.play().catch(() => {});
  rec.start(200);
  await stopped;
  clearInterval(tick);
  clearTimeout(timer);
  video.removeEventListener('ended', stop);
  actions.setBusy(null);
  recorder = null;
  videoBar.setRecording(false);
  video.loop = wasLoop;
  if (finite) { video.currentTime = 0; video.play().catch(() => {}); }

  const blob = new Blob(chunks, { type: type.split(';')[0] });
  const ext = type.startsWith('video/mp4') ? 'mp4' : 'webm';
  const a = h('a', { href: URL.createObjectURL(blob), download: `${actions.fileName()}.${ext}` });
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  toast(`Saved ${ext.toUpperCase()} (${(blob.size / 1e6).toFixed(1)} MB)`);
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
  library: () => openLibrary({ samples: SAMPLES, uploads, onPick: useImage, onUpload: actions.upload, onDemo: loadDemoVideo }),
  hasVideo: () => !!video,
  toggleVideo,
  help: openHelp,
  fit: () => stage.fit(),
  zoomBy: (k) => stage.zoomBy(k),
  undo: () => store.undo(),
  redo: () => store.redo(),
  restartTime() {
    time = 0;
    if (isFiniteVideo()) video.currentTime = 0;
    markDirty();
  },
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
const videoBar = createVideoBar({
  root: $('#videoBar'),
  onToggle: toggleVideo,
  onSeek: (t) => { if (isFiniteVideo()) video.currentTime = t; },
  onLoop: (on) => { if (video) video.loop = on; },
  onRecord: toggleRecord,
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
  if (k === ' ' && video) { e.preventDefault(); toggleVideo(); }
  else if (k === 'c' && !e.repeat) actions.setCompare(true);
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
  const f = [...(e.dataTransfer?.files || [])].find((x) => x.type.startsWith('image/') || isVideoFile(x));
  if (f) loadFile(f);
});
addEventListener('paste', (e) => {
  const f = [...(e.clipboardData?.files || [])].find((x) => x.type.startsWith('image/'));
  if (f) loadFile(f);
});

// --- go ----------------------------------------------------------------------------
useImage(SAMPLES[0].canvas, SAMPLES[0].name);
requestAnimationFrame(loop);
