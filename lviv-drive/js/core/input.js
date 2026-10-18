// Keyboard (layout-independent via KeyboardEvent.code), mouse look and a
// standard gamepad. Continuous controls are smoothed so keyboard driving
// feels like pedals; discrete controls fire named actions.

export const KEYMAP = {
  KeyI: 'engine', Enter: 'engine',
  Space: 'handbrake',
  KeyE: 'gearUp', KeyQ: 'gearDown',
  KeyM: 'transmission',
  KeyL: 'lights', KeyB: 'highbeam', KeyF: 'fog',
  KeyZ: 'indLeft', KeyX: 'indRight', KeyV: 'hazard',
  KeyJ: 'wipers', KeyG: 'belt',
  KeyY: 'limiter', KeyU: 'cruise',
  KeyT: 'recover',
  Tab: 'questlog', KeyO: 'map',
  Escape: 'pause', F1: 'help', Slash: 'help',
  Digit2: 'lookCenter',
};

export const CONTROLS_HELP = [
  ['W / ↑', 'Газ'], ['S / ↓', 'Гальмо'], ['A D / ← →', 'Кермо'], ['Shift', 'Зчеплення (механіка)'],
  ['Q / E', 'Передача вниз / вгору (P-R-N-D для автомата)'], ['M', 'Механіка / автомат (на стоянці)'],
  ['I / Enter', 'Запуск / вимкнення двигуна'], ['Space', 'Стоянкове гальмо'], ['G', 'Ремінь безпеки'],
  ['Z / X', 'Лівий / правий покажчик повороту'], ['V', 'Аварійна сигналізація'],
  ['L', 'Світло: вимк → габарити → ближнє'], ['B', 'Дальнє світло'], ['F', 'Задній протитуманний'],
  ['J', 'Склоочисники (режими)'], ['H', 'Звуковий сигнал'], ['Y', 'Обмежувач швидкості'], ['U', 'Круїз-контроль'],
  ['1 / 3 (утримувати)', 'Огляд ліворуч / праворуч (дзеркала)'], ['C (утримувати)', 'Подивитися назад'],
  ['Миша (затиснути)', 'Огляд з салону'], ['Tab', 'Журнал завдань'], ['O', 'Масштаб мінікарти'],
  ['T', 'Повернути авто на дорогу (не на іспиті)'], ['Esc', 'Пауза / меню'],
];

export class Input {
  constructor(target, onAction) {
    this.keys = new Set();
    this.onAction = onAction;
    this.throttle = 0;
    this.brake = 0;
    this.steer = 0;
    this.mouse = { down: false, dx: 0, dy: 0 };
    this.enabled = true;
    window.addEventListener('keydown', (e) => {
      if (!this.enabled) return;
      if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) return;
      if (['Tab', 'Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'F1', 'Slash'].includes(e.code)) e.preventDefault();
      if (!this.keys.has(e.code) && KEYMAP[e.code]) this.onAction(KEYMAP[e.code]);
      this.keys.add(e.code);
    });
    window.addEventListener('keyup', (e) => this.keys.delete(e.code));
    window.addEventListener('blur', () => this.keys.clear());
    target.addEventListener('mousedown', () => { this.mouse.down = true; });
    window.addEventListener('mouseup', () => { this.mouse.down = false; });
    window.addEventListener('mousemove', (e) => {
      if (!this.mouse.down) return;
      this.mouse.dx += e.movementX;
      this.mouse.dy += e.movementY;
    });
    this._padPrev = [];
  }

  has(...codes) { return codes.some((c) => this.keys.has(c)); }

  poll(dt) {
    let thr = this.has('KeyW', 'ArrowUp') ? 1 : 0;
    let brk = this.has('KeyS', 'ArrowDown') ? 1 : 0;
    let st = (this.has('KeyD', 'ArrowRight') ? 1 : 0) - (this.has('KeyA', 'ArrowLeft') ? 1 : 0);
    let clutch = this.has('ShiftLeft', 'ShiftRight');
    let horn = this.has('KeyH');
    const pad = navigator.getGamepads ? [...navigator.getGamepads()].find((p) => p && p.connected) : null;
    let analog = false;
    if (pad) {
      const ax = pad.axes[0] || 0;
      if (Math.abs(ax) > 0.08) { st = ax; analog = true; }
      const rt = pad.buttons[7]?.value || 0, lt = pad.buttons[6]?.value || 0;
      if (rt > 0.02) { thr = rt; analog = true; }
      if (lt > 0.02) { brk = lt; analog = true; }
      if (pad.buttons[4]?.pressed) clutch = true;
      if (pad.buttons[10]?.pressed) horn = true;
      const map = { 0: 'engine', 1: 'handbrake', 3: 'gearUp', 2: 'gearDown', 14: 'indLeft', 15: 'indRight', 12: 'lights', 9: 'pause' };
      for (const [i, act] of Object.entries(map)) {
        const p = !!pad.buttons[i]?.pressed;
        if (p && !this._padPrev[i]) this.onAction(act);
        this._padPrev[i] = p;
      }
    }
    if (analog) {
      this.throttle = thr; this.brake = brk; this.steer = st;
    } else {
      // Keyboard pedals ramp up, release quickly.
      this.throttle = thr ? Math.min(1, this.throttle + dt * 2.2) : Math.max(0, this.throttle - dt * 6);
      this.brake = brk ? Math.min(1, this.brake + dt * 3.5) : Math.max(0, this.brake - dt * 8);
      this.steer = st;
    }
    const look = this.has('Digit1') ? 'left' : this.has('Digit3') ? 'right' : this.has('KeyC') ? 'back' : null;
    const m = { dx: this.mouse.dx, dy: this.mouse.dy, down: this.mouse.down };
    this.mouse.dx = 0; this.mouse.dy = 0;
    return { throttle: this.throttle, brake: this.brake, steer: this.steer, clutch, horn, look, mouse: m };
  }
}
