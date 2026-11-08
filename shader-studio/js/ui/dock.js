// Floating action bar at the bottom of the stage + the Export popover.
import { h } from '../dom.js';
import { icon } from './icons.js';
import { slider, toast } from './components.js';

const FORMATS = [
  { id: 'png', mime: 'image/png', ext: 'png', label: 'PNG' },
  { id: 'jpeg', mime: 'image/jpeg', ext: 'jpg', label: 'JPG' },
  { id: 'webp', mime: 'image/webp', ext: 'webp', label: 'WebP' },
];

export function createDock({ root, actions }) {
  const opts = { format: 'png', scale: 1, quality: 0.92 };

  const btn = (name, label, title, onClick, cls = '') =>
    h('button', { class: `dock-btn ${cls}`, type: 'button', title, onClick }, icon(name, 16), h('span', {}, label));

  const exportBtn = btn('download', 'Export', 'Export image', () => togglePopover(), 'primary');
  let popover = null;

  root.append(
    btn('upload', 'Upload', 'Upload an image (or drop / paste one)', actions.upload),
    btn('refresh', 'Restyle', 'Pick a random style (R)', actions.restyle),
    btn('dice', 'Shuffle', 'Randomise this style’s settings (S)', actions.shuffle),
    btn('grid', 'Library', 'Sample images & recent uploads', actions.library),
    h('span', { class: 'dock-sep' }),
    exportBtn,
  );

  function closePopover() {
    popover?.remove();
    popover = null;
    exportBtn.classList.remove('open');
    document.removeEventListener('pointerdown', outside, true);
    document.removeEventListener('keydown', onKey, true);
  }
  const outside = (e) => { if (popover && !popover.contains(e.target) && !exportBtn.contains(e.target)) closePopover(); };
  const onKey = (e) => { if (e.key === 'Escape') closePopover(); };

  function togglePopover() {
    if (popover) return closePopover();
    exportBtn.classList.add('open');
    popover = buildPopover();
    root.append(popover);
    document.addEventListener('pointerdown', outside, true);
    document.addEventListener('keydown', onKey, true);
  }

  function buildPopover() {
    const base = actions.getBaseSize();
    const max = actions.maxExportSize;
    const fmt = () => FORMATS.find((f) => f.id === opts.format);
    const dims = h('output', { class: 'dims' });
    const qualityBox = h('div', { class: 'quality' });
    const fmtRow = h('div', { class: 'seg-row' });
    const scaleRow = h('div', { class: 'seg-row' });

    const paint = () => {
      fmtRow.replaceChildren(...FORMATS.map((f) =>
        h('button', { class: `seg-btn${f.id === opts.format ? ' active' : ''}`, type: 'button', onClick: () => { opts.format = f.id; paint(); } }, f.label)));
      scaleRow.replaceChildren(...[1, 2, 3, 4].map((k) => {
        const tooBig = Math.max(base.w, base.h) * k > max;
        return h('button', { class: `seg-btn${k === opts.scale ? ' active' : ''}`, type: 'button', disabled: tooBig, title: tooBig ? 'Too large for this GPU' : null,
          onClick: () => { opts.scale = k; paint(); } }, `${k}×`);
      }));
      dims.textContent = `${base.w * opts.scale} × ${base.h * opts.scale} px`;
      qualityBox.replaceChildren();
      qualityBox.hidden = opts.format === 'png';
      if (opts.format !== 'png') {
        qualityBox.append(slider({ label: 'Quality', min: 0.5, max: 1, step: 0.01, value: opts.quality, def: 0.92, onInput: (v) => { opts.quality = v; } }));
      }
    };

    const run = async (copy) => {
      try {
        const f = copy ? FORMATS[0] : fmt();
        const blob = await actions.exportImage({ mime: f.mime, scale: opts.scale, quality: opts.quality });
        if (!blob) throw new Error('The browser could not encode this image.');
        if (copy) {
          await navigator.clipboard.write([new ClipboardItem({ [f.mime]: blob })]);
          toast('Copied to clipboard');
        } else {
          const a = h('a', { href: URL.createObjectURL(blob), download: `${actions.fileName()}.${f.ext}` });
          document.body.append(a);
          a.click();
          a.remove();
          setTimeout(() => URL.revokeObjectURL(a.href), 4000);
          toast('Exported');
          closePopover();
        }
      } catch (err) {
        toast(err.message || 'Export failed', { error: true });
      }
    };

    const el = h(
      'div',
      { class: 'popover', role: 'dialog', 'aria-label': 'Export' },
      h('h3', { class: 'section-title' }, '/ EXPORT'),
      h('div', { class: 'ctl' }, h('div', { class: 'ctl-row' }, h('span', { class: 'ctl-label' }, 'Format')), fmtRow),
      h('div', { class: 'ctl' }, h('div', { class: 'ctl-row' }, h('span', { class: 'ctl-label' }, 'Size'), dims), scaleRow),
      qualityBox,
      h('div', { class: 'popover-actions' },
        h('button', { class: 'btn primary grow', type: 'button', onClick: () => run(false) }, icon('download', 14), 'Download'),
        h('button', { class: 'btn', type: 'button', title: 'Copy as PNG', 'aria-label': 'Copy as PNG', onClick: () => run(true) }, icon('copy', 14)),
      ),
    );
    paint();
    return el;
  }

  return { closePopover, togglePopover };
}
