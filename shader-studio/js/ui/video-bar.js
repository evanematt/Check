// Playback bar shown above the dock while a video is loaded.
import { h } from '../dom.js';
import { icon } from './icons.js';

const fmt = (s) => {
  if (!Number.isFinite(s)) return '0:00';
  const m = Math.floor(s / 60);
  return `${m}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
};

export function createVideoBar({ root, onToggle, onSeek, onLoop, onRecord }) {
  let video = null;
  let scrubbing = false;
  const play = h('button', { class: 'vb-btn', type: 'button', title: 'Play / pause (Space)', 'aria-label': 'Play or pause', onClick: () => onToggle() });
  const seek = h('input', { type: 'range', min: '0', max: '1', step: '0.01', value: '0', 'aria-label': 'Seek', class: 'vb-seek' });
  const time = h('span', { class: 'vb-time' }, '0:00');
  const loop = h('button', { class: 'vb-btn', type: 'button', title: 'Loop', 'aria-label': 'Loop', 'aria-pressed': 'true', onClick: () => { const on = loop.getAttribute('aria-pressed') !== 'true'; loop.setAttribute('aria-pressed', String(on)); onLoop(on); } }, icon('reset', 15));
  const rec = h('button', { class: 'vb-btn rec', type: 'button', title: 'Record the result as a video', 'aria-label': 'Record video', onClick: () => onRecord() }, h('i'), h('span', {}, 'Record'));

  seek.addEventListener('pointerdown', () => { scrubbing = true; });
  seek.addEventListener('pointerup', () => { scrubbing = false; });
  seek.addEventListener('input', () => onSeek(+seek.value));
  root.append(play, seek, time, loop, rec);
  root.hidden = true;

  return {
    show(el) {
      video = el;
      const live = !Number.isFinite(el.duration);
      seek.hidden = live;
      loop.hidden = live;
      seek.max = live ? '1' : String(el.duration);
      loop.setAttribute('aria-pressed', String(!!el.loop));
      root.hidden = false;
      this.update();
    },
    hide() { video = null; root.hidden = true; },
    update() {
      if (!video) return;
      const playing = !video.paused && !video.ended;
      if (play.dataset.p !== String(playing)) { play.dataset.p = String(playing); play.replaceChildren(icon(playing ? 'pause' : 'play', 15)); }
      const live = !Number.isFinite(video.duration);
      if (!scrubbing && !live) seek.value = String(video.currentTime);
      time.textContent = live ? 'LIVE' : `${fmt(video.currentTime)} / ${fmt(video.duration)}`;
    },
    setRecording(on) {
      rec.classList.toggle('on', on);
      rec.querySelector('span').textContent = on ? 'Stop' : 'Record';
    },
  };
}
