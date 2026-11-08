// Library + Help dialogs.
import { h } from '../dom.js';
import { icon } from './icons.js';
import { openModal } from './components.js';

function preview(canvas) {
  const c = document.createElement('canvas');
  c.width = 240;
  c.height = 180;
  const k = Math.max(240 / canvas.width, 180 / canvas.height);
  c.getContext('2d').drawImage(canvas, (240 - canvas.width * k) / 2, (180 - canvas.height * k) / 2, canvas.width * k, canvas.height * k);
  return c;
}

export function openLibrary({ samples, uploads, onPick, onUpload }) {
  let modal;
  const item = (name, canvas) =>
    h('button', { class: 'lib-item', type: 'button', onClick: () => { onPick(canvas, name); modal.close(); } },
      preview(canvas), h('span', {}, name));
  const body = h('div', {},
    h('div', { class: 'lib-grid' },
      h('button', { class: 'lib-item add', type: 'button', onClick: () => { modal.close(); onUpload(); } }, icon('upload', 22), h('span', {}, 'Upload image')),
    ),
    h('h4', { class: 'lib-h' }, 'Samples'),
    h('div', { class: 'lib-grid' }, samples.map((s) => item(s.name, s.canvas))),
    uploads.length ? [h('h4', { class: 'lib-h' }, 'This session'), h('div', { class: 'lib-grid' }, uploads.map((u) => item(u.name, u.canvas)))] : null,
  );
  modal = openModal({ title: 'Image library', body, width: 640 });
}

export function openHelp() {
  const rows = [
    ['Ctrl / ⌘ + Z', 'Undo'],
    ['Ctrl / ⌘ + Shift + Z', 'Redo'],
    ['Hold C', 'Compare with original'],
    ['R', 'Restyle (random style)'],
    ['S', 'Shuffle settings'],
    ['0', 'Fit to window'],
    ['+ / −', 'Zoom in / out'],
    ['Scroll / pinch', 'Zoom at cursor'],
    ['Drag', 'Pan'],
    ['Double-click slider', 'Reset that slider'],
    ['Drop / paste an image', 'Load it'],
  ];
  openModal({
    title: 'Help & shortcuts',
    width: 460,
    body: h('table', { class: 'keys' }, h('tbody', {}, rows.map(([k, v]) => h('tr', {}, h('th', {}, h('kbd', {}, k)), h('td', {}, v))))),
  });
}
