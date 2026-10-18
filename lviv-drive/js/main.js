import { Game } from './game/game.js';

const text = document.getElementById('loading-text');
const loading = document.getElementById('loading');

function webglAvailable() {
  try {
    const c = document.createElement('canvas');
    return !!(c.getContext('webgl2') || c.getContext('webgl'));
  } catch {
    return false;
  }
}

async function boot() {
  if (!webglAvailable()) {
    text.textContent = 'Ваш браузер не підтримує WebGL. Спробуйте Chrome, Firefox або Edge з апаратним прискоренням.';
    return;
  }
  const game = new Game(document.getElementById('view'));
  window.__game = game; // handy for debugging in the console
  try {
    await game.init((msg) => { text.textContent = msg; });
    loading.remove();
  } catch (err) {
    console.error(err);
    text.textContent = `Помилка запуску: ${err.message}`;
  }
}

boot();
