// Reusable controls: slider, select, toggle, color, section heading, toast, modal.
import { h } from '../dom.js';
import { icon } from './icons.js';

function decimals(step) {
  return (String(step).split('.')[1] || '').length;
}
export const formatValue = (v, step, unit = '') => `${Number(v).toFixed(decimals(step))}${unit}`;

export function slider({ label, min, max, step = 1, value, unit = '', def, onInput, onChange }) {
  const input = h('input', { type: 'range', min, max, step, 'aria-label': label, value });
  const val = h('span', { class: 'val' });
  const paint = () => {
    const v = +input.value;
    val.textContent = formatValue(v, step, unit);
    input.style.setProperty('--p', `${((v - min) / (max - min)) * 100}%`);
  };
  input.addEventListener('input', () => {
    paint();
    onInput?.(+input.value);
  });
  input.addEventListener('change', () => onChange?.(+input.value));
  paint();
  const reset = () => {
    if (def == null) return;
    input.value = def;
    paint();
    onInput?.(def);
    onChange?.(def);
  };
  return h(
    'div',
    { class: 'ctl', title: def != null ? 'Double-click to reset' : null, onDblclick: reset },
    h('div', { class: 'ctl-row' }, h('span', { class: 'ctl-label' }, label), val),
    input,
  );
}

export function select({ label, options, value, onChange }) {
  const el = h(
    'select',
    { 'aria-label': label, onChange: () => onChange?.(options[el.selectedIndex].value) },
    options.map((o) => h('option', { value: String(o.value), selected: o.value === value }, o.label)),
  );
  return h('div', { class: 'ctl ctl-inline' }, h('span', { class: 'ctl-label' }, label), h('div', { class: 'select' }, el, icon('chevronDown', 12)));
}

export function toggle({ label, value, onChange }) {
  const btn = h('button', { class: 'switch', role: 'switch', 'aria-checked': String(!!value), 'aria-label': label, type: 'button' }, h('i'));
  btn.addEventListener('click', () => {
    const next = btn.getAttribute('aria-checked') !== 'true';
    btn.setAttribute('aria-checked', String(next));
    onChange?.(next);
  });
  return h('div', { class: 'ctl ctl-inline' }, h('span', { class: 'ctl-label' }, label), btn);
}

export function color({ label, value, onInput, onChange }) {
  const input = h('input', { type: 'color', 'aria-label': label, value });
  const hex = h('span', { class: 'val' }, value);
  input.addEventListener('input', () => {
    hex.textContent = input.value;
    onInput?.(input.value);
  });
  input.addEventListener('change', () => onChange?.(input.value));
  return h('div', { class: 'ctl ctl-inline' }, h('span', { class: 'ctl-label' }, label), h('div', { class: 'color' }, hex, input));
}

export const sectionTitle = (text) => h('h3', { class: 'section-title' }, `/ ${text}`);

// --- toast -------------------------------------------------------------------
let toastTimer;
export function toast(message, { error = false } = {}) {
  let el = document.getElementById('toast');
  if (!el) {
    el = h('div', { id: 'toast', role: 'status', 'aria-live': 'polite' });
    document.body.append(el);
  }
  el.textContent = message;
  el.className = `show${error ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (el.className = ''), error ? 6000 : 2600);
}

// --- modal -------------------------------------------------------------------
export function openModal({ title, body, width = 560 }) {
  const prevFocus = document.activeElement;
  const close = () => {
    overlay.remove();
    document.removeEventListener('keydown', onKey, true);
    prevFocus?.focus?.();
  };
  const onKey = (e) => {
    if (e.key === 'Escape') {
      e.stopPropagation();
      close();
    }
  };
  const overlay = h(
    'div',
    { class: 'modal-overlay', onPointerdown: (e) => e.target === overlay && close() },
    h(
      'div',
      { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title, style: { maxWidth: `${width}px` } },
      h('header', {}, h('h2', {}, title), h('button', { class: 'icon-btn', 'aria-label': 'Close', onClick: close }, icon('x'))),
      h('div', { class: 'modal-body' }, body),
    ),
  );
  document.body.append(overlay);
  document.addEventListener('keydown', onKey, true);
  overlay.querySelector('.modal header button')?.focus();
  return { close };
}
