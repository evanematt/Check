import { h } from '../dom.js';
import { icon } from './icons.js';

export function createTopbar({ root, actions }) {
  const btn = (name, label, onClick, extra = {}) =>
    h('button', { class: 'tb-btn', type: 'button', title: label, 'aria-label': label, onClick, ...extra }, icon(name));
  const sep = () => h('span', { class: 'tb-sep' });

  const zoomLabel = h('button', { class: 'tb-zoom', type: 'button', title: 'Fit to window (0)', onClick: actions.fit }, '100%');
  const undo = btn('undo', 'Undo (Ctrl+Z)', actions.undo);
  const redo = btn('redo', 'Redo (Ctrl+Shift+Z)', actions.redo);

  const compare = btn('compare', 'Hold to compare with original (C)', null, { class: 'tb-btn compare-btn' });
  const on = (e) => { e.preventDefault(); actions.setCompare(true); };
  const off = () => actions.setCompare(false);
  compare.addEventListener('pointerdown', on);
  compare.addEventListener('pointerup', off);
  compare.addEventListener('pointerleave', off);
  compare.addEventListener('pointercancel', off);

  root.append(
    h(
      'div',
      { class: 'tb-left' },
      btn('panelLeft', 'Toggle styles panel', () => actions.togglePanel('left')),
      h('div', { class: 'brand' }, icon('sparkle', 16), h('span', {}, 'Shader Studio')),
      h(
        'div',
        { class: 'seg', role: 'tablist' },
        h('button', { class: 'seg-btn active', role: 'tab', 'aria-selected': 'true' }, 'Studio'),
        h('button', { class: 'seg-btn', role: 'tab', disabled: true, title: 'Coming soon' }, 'Flow'),
      ),
    ),
    h(
      'div',
      { class: 'tb-center' },
      btn('zoomOut', 'Zoom out (-)', () => actions.zoomBy(1 / 1.25)),
      zoomLabel,
      btn('zoomIn', 'Zoom in (+)', () => actions.zoomBy(1.25)),
      btn('fit', 'Fit to window (0)', actions.fit),
      sep(),
      compare,
      sep(),
      undo,
      redo,
      btn('reset', 'Reset this style and adjustments', actions.resetAll),
    ),
    h(
      'div',
      { class: 'tb-right' },
      btn('help', 'Help & shortcuts (?)', actions.help),
      btn('panelRight', 'Toggle controls panel', () => actions.togglePanel('right')),
    ),
  );

  return {
    setZoom(z) { zoomLabel.textContent = `${Math.round(z * 100)}%`; },
    setHistory({ canUndo, canRedo }) { undo.disabled = !canUndo; redo.disabled = !canRedo; },
  };
}
