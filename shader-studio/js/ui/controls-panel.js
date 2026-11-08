// Right panel: icon rail + the controls for the active tab.
//   Style  - generated from the active shader's `params`
//   Adjust - global post adjustments
//   Motion - playback of animated styles
import { h } from '../dom.js';
import { icon } from './icons.js';
import { slider, select, toggle, color, sectionTitle } from './components.js';
import { DEFAULT_ADJUST } from '../state.js';
import { getShader, resolveValues, defaultValues } from '../shaders/index.js';

const TABS = [
  { id: 'style', label: 'Style', icon: 'sliders' },
  { id: 'adjust', label: 'Adjust', icon: 'sun' },
  { id: 'motion', label: 'Motion', icon: 'play' },
];

export function createControlsPanel({ root, store, actions }) {
  let tab = 'style';
  const rail = h('nav', { class: 'rail', role: 'tablist', 'aria-orientation': 'vertical' });
  const panel = h('div', { class: 'cp-body' });
  root.append(rail, panel);

  TABS.forEach((t) =>
    rail.append(
      h('button', {
        class: 'rail-btn', type: 'button', role: 'tab', title: t.label, 'aria-label': t.label, dataset: { tab: t.id },
        onClick: () => { tab = t.id; render(); },
      }, icon(t.icon, 18)),
    ),
  );

  const head = (title, onReset) =>
    h('header', { class: 'cp-head' }, h('h2', {}, `/ ${title}`),
      onReset ? h('button', { class: 'link-btn', type: 'button', onClick: onReset }, 'Reset') : null);

  // --- Style tab -------------------------------------------------------------
  function renderStyle() {
    const s = store.state;
    const shader = getShader(s.styleId);
    const values = resolveValues(shader, s);
    const defs = defaultValues(shader);
    const set = (id, v) => store.update((st) => { (st.params[shader.id] ||= {})[id] = v; });
    const commit = () => store.commit();

    panel.append(head(shader.name, shader.params.length ? () => {
      store.update((st) => { delete st.params[shader.id]; }, { structure: true });
      store.commit();
    } : null));
    panel.append(h('p', { class: 'meta' }, shader.category));

    if (!shader.params.length) {
      panel.append(h('p', { class: 'empty-note' }, 'This style has no adjustable parameters.'));
      return;
    }
    const sections = new Map();
    shader.params.forEach((p) => (sections.get(p.section) || sections.set(p.section, []).get(p.section)).push(p));
    for (const [name, params] of sections) {
      const sec = h('section', { class: 'section' }, sectionTitle(name));
      for (const p of params) {
        const v = values[p.id];
        if (p.type === 'range') sec.append(slider({ label: p.label, min: p.min, max: p.max, step: p.step, unit: p.unit, value: v, def: defs[p.id], onInput: (x) => set(p.id, x), onChange: commit }));
        else if (p.type === 'select') sec.append(select({ label: p.label, options: p.options, value: v, onChange: (x) => { set(p.id, x); commit(); } }));
        else if (p.type === 'toggle') sec.append(toggle({ label: p.label, value: v, onChange: (x) => { set(p.id, x); commit(); } }));
        else if (p.type === 'color') sec.append(color({ label: p.label, value: v, onInput: (x) => set(p.id, x), onChange: commit }));
      }
      panel.append(sec);
    }
  }

  // --- Adjust tab ------------------------------------------------------------
  function renderAdjust() {
    const a = store.state.adjust;
    const set = (k, v) => store.update((s) => { s.adjust[k] = v; });
    const commit = () => store.commit();
    const sl = (key, label, min, max, step = 1, unit = '') =>
      slider({ label, min, max, step, unit, value: a[key], def: DEFAULT_ADJUST[key], onInput: (v) => set(key, v), onChange: commit });

    panel.append(head('Adjust', () => {
      store.update((s) => { s.adjust = { ...DEFAULT_ADJUST }; }, { structure: true });
      store.commit();
    }));
    panel.append(
      h('section', { class: 'section' }, sectionTitle('Light'), sl('brightness', 'Brightness', -100, 100), sl('contrast', 'Contrast', -100, 100), sl('exposure', 'Exposure', -2, 2, 0.05)),
      h('section', { class: 'section' }, sectionTitle('Color'), sl('saturation', 'Saturation', 0, 200, 1, '%'), sl('hue', 'Hue', -180, 180, 1, '°'),
        toggle({ label: 'Invert', value: a.invert, onChange: (v) => { set('invert', v); commit(); } })),
      h('section', { class: 'section' }, sectionTitle('Finish'), sl('vignette', 'Vignette', 0, 100), sl('grain', 'Grain', 0, 100)),
    );
  }

  // --- Motion tab ------------------------------------------------------------
  function renderMotion() {
    const m = store.state.motion;
    const shader = getShader(store.state.styleId);
    const playBtn = h('button', { class: 'btn wide', type: 'button' });
    const paint = () => {
      playBtn.replaceChildren(icon(m.playing ? 'pause' : 'play', 14), m.playing ? 'Pause' : 'Play');
    };
    playBtn.addEventListener('click', () => {
      store.update((s) => { s.motion.playing = !s.motion.playing; });
      paint();
    });
    paint();
    panel.append(
      head('Motion'),
      h('p', { class: 'meta' }, shader.animated ? `${shader.name} is animated.` : `${shader.name} is a still style - motion has no effect.`),
      h('section', { class: 'section' }, sectionTitle('Playback'),
        playBtn,
        slider({ label: 'Speed', min: 0, max: 4, step: 0.05, unit: '×', value: m.speed, def: 1, onInput: (v) => store.update((s) => { s.motion.speed = v; }) }),
        h('button', { class: 'btn wide ghost', type: 'button', onClick: actions.restartTime }, icon('reset', 14), 'Restart'),
      ),
    );
  }

  function render() {
    rail.querySelectorAll('.rail-btn').forEach((b) => {
      const on = b.dataset.tab === tab;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', String(on));
    });
    panel.replaceChildren();
    ({ style: renderStyle, adjust: renderAdjust, motion: renderMotion })[tab]();
  }

  store.on('structure', render);
  render();
}
