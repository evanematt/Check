// "Generative" mode of the left panel: describe a style, an AI model restyles the photo.
// Results become versions of the image, so the realtime shader styles still apply on top.
import { h } from '../dom.js';
import { icon } from './icons.js';
import { toast } from './components.js';
import { load, save } from '../storage.js';
import { toCanvas } from '../images.js';
import { PROVIDERS, PRESETS, FIDELITY, getProvider, buildPrompt, prepareInput } from '../ai/providers.js';

export function createGenerativePanel({ actions }) {
  const cfg = load('ai', { provider: PROVIDERS[0].id, fidelity: 1, prompt: '', providers: {} });
  const settingsFor = (p) => (cfg.providers[p.id] ||= { model: p.defaultModel, base: p.defaultBase, key: '' });
  const persist = () => save('ai', cfg);

  let versions = []; // { id, name, canvas }
  let activeId = null;
  let controller = null;
  let timer = null;

  // --- prompt -----------------------------------------------------------------
  const promptEl = h('textarea', {
    id: 'genPrompt', rows: '4', maxlength: '600', 'aria-label': 'Style description',
    placeholder: 'Describe the style, e.g. “low-poly 3D model, PS1-era game asset, flat-shaded facets, white background”',
    onInput: () => { cfg.prompt = promptEl.value; persist(); },
  });
  promptEl.value = cfg.prompt || '';

  const chips = h('div', { class: 'chips' }, PRESETS.map(([label, text]) =>
    h('button', { class: 'chip-btn', type: 'button', title: text, onClick: () => { promptEl.value = text; cfg.prompt = text; persist(); promptEl.focus(); } }, label)));

  const fidelity = h('div', { class: 'seg-row' });
  const paintFidelity = () => fidelity.replaceChildren(...FIDELITY.map((f, i) =>
    h('button', { class: `seg-btn${i === cfg.fidelity ? ' active' : ''}`, type: 'button', onClick: () => { cfg.fidelity = i; persist(); paintFidelity(); } }, f.label)));
  paintFidelity();

  const chain = h('input', { type: 'checkbox', id: 'genChain' });
  const status = h('p', { class: 'gen-status', role: 'status', 'aria-live': 'polite' });
  const setStatus = (msg, error = false) => { status.textContent = msg || ''; status.classList.toggle('error', error); };

  const goBtn = h('button', { class: 'btn primary grow', type: 'button', onClick: () => generate() }, icon('sparkle', 14), 'Generate');
  const cancelBtn = h('button', { class: 'btn', type: 'button', hidden: true, onClick: () => controller?.abort() }, 'Cancel');

  // --- settings ---------------------------------------------------------------
  const providerSel = h('select', { id: 'genProvider', 'aria-label': 'Provider', onChange: () => { cfg.provider = providerSel.value; persist(); paintSettings(); } },
    PROVIDERS.map((p) => h('option', { value: p.id }, p.name)));
  const modelEl = h('input', { type: 'text', id: 'genModel', 'aria-label': 'Model', spellcheck: 'false', onInput: () => { settingsFor(getProvider(cfg.provider)).model = modelEl.value.trim(); persist(); } });
  const keyEl = h('input', { type: 'password', id: 'genKey', 'aria-label': 'API key', autocomplete: 'off', spellcheck: 'false', onInput: () => { settingsFor(getProvider(cfg.provider)).key = keyEl.value.trim(); persist(); } });
  const baseEl = h('input', { type: 'text', id: 'genBase', 'aria-label': 'Base URL', spellcheck: 'false', onInput: () => { settingsFor(getProvider(cfg.provider)).base = baseEl.value.trim(); persist(); } });
  const keyHint = h('small', {});
  const field = (label, el, extra) => h('label', { class: 'field' }, h('span', { class: 'ctl-label' }, label), el, extra);

  function paintSettings() {
    const p = getProvider(cfg.provider);
    const s = settingsFor(p);
    providerSel.value = p.id;
    modelEl.value = s.model;
    keyEl.value = s.key;
    baseEl.value = s.base;
    keyHint.textContent = p.keyHint;
  }
  paintSettings();

  const details = h('details', { class: 'gen-settings' },
    h('summary', {}, 'Provider & API key'),
    field('Provider', h('div', { class: 'select' }, providerSel, icon('chevronDown', 12))),
    field('Model', modelEl),
    field('API key', keyEl, keyHint),
    field('Base URL', baseEl),
    h('p', { class: 'note' }, 'The key stays in this browser (localStorage) and is sent only to the provider above. Don’t put a real key into a shared or public deployment: send requests through your own proxy and set its address as Base URL.'),
    h('button', { class: 'link-btn', type: 'button', onClick: () => { settingsFor(getProvider(cfg.provider)).key = ''; persist(); paintSettings(); toast('Key removed from this browser'); } }, 'Remove saved key'),
  );

  // --- versions -----------------------------------------------------------------
  const grid = h('div', { class: 'versions' });
  function paintVersions() {
    grid.replaceChildren(...versions.map((v) => {
      const c = document.createElement('canvas');
      c.width = 160; c.height = 160;
      const k = Math.max(160 / v.canvas.width, 160 / v.canvas.height);
      c.getContext('2d').drawImage(v.canvas, (160 - v.canvas.width * k) / 2, (160 - v.canvas.height * k) / 2, v.canvas.width * k, v.canvas.height * k);
      return h('button', {
        class: `ver${v.id === activeId ? ' active' : ''}`, type: 'button', title: v.name,
        onClick: () => { activeId = v.id; paintVersions(); actions.useImage(v.canvas, v.name, { version: true }); },
      }, c, h('span', {}, v.name));
    }));
  }

  actions.onImage((source, name, meta) => {
    if (meta.version) return;
    versions = [{ id: 'orig', name: 'Original', canvas: source }];
    activeId = 'orig';
    paintVersions();
  });

  // --- generate ---------------------------------------------------------------
  function setRunning(on) {
    goBtn.disabled = on;
    cancelBtn.hidden = !on;
    clearInterval(timer);
    if (!on) return actions.setBusy(null);
    const t0 = Date.now();
    const tick = () => actions.setBusy(`Generating… ${Math.round((Date.now() - t0) / 1000)}s`);
    tick();
    timer = setInterval(tick, 1000);
  }

  function friendly(err) {
    if (err.name === 'TypeError') {
      return 'Couldn’t reach the provider. Check your connection, key and Base URL. The hosted preview blocks outside requests, so run the app locally.';
    }
    return err.message || 'Generation failed.';
  }

  async function generate() {
    const style = promptEl.value.trim();
    const p = getProvider(cfg.provider);
    const s = settingsFor(p);
    if (!style) { setStatus('Describe the style first.', true); promptEl.focus(); return; }
    if (p.needsKey && !s.key && s.base === p.defaultBase) {
      details.open = true;
      keyEl.focus();
      setStatus(`Add your ${p.name} API key first.`, true);
      return;
    }
    const src = (chain.checked ? versions.find((v) => v.id === activeId) : versions[0]) || versions[0];
    if (!src) return;
    controller = new AbortController();
    setStatus('');
    setRunning(true);
    try {
      const image = await prepareInput(src.canvas);
      const blob = await p.edit({
        apiKey: s.key, model: s.model || p.defaultModel, baseUrl: s.base || p.defaultBase,
        prompt: buildPrompt(style, cfg.fidelity), image, signal: controller.signal,
      });
      const bmp = await createImageBitmap(blob);
      const canvas = toCanvas(bmp);
      bmp.close?.();
      const v = { id: `v${Date.now()}`, name: style.slice(0, 32), canvas };
      versions.push(v);
      activeId = v.id;
      paintVersions();
      actions.useImage(canvas, v.name, { version: true });
      setStatus('Done. Switch back to Realtime to put a shader style on top.');
    } catch (err) {
      if (err.name === 'AbortError') setStatus('Cancelled.');
      else setStatus(friendly(err), true);
    } finally {
      controller = null;
      setRunning(false);
    }
  }

  return h('div', { class: 'gen' },
    h('section', { class: 'section' },
      h('h3', { class: 'section-title' }, '/ STYLE PROMPT'),
      promptEl,
      chips,
    ),
    h('section', { class: 'section' },
      h('h3', { class: 'section-title' }, '/ FIDELITY TO PHOTO'),
      fidelity,
      h('label', { class: 'check', for: 'genChain' }, chain, 'Build on the current version'),
    ),
    h('div', { class: 'gen-actions' }, goBtn, cancelBtn),
    status,
    details,
    h('section', { class: 'section' }, h('h3', { class: 'section-title' }, '/ VERSIONS'), grid),
  );
}
