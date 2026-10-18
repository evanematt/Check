import { h, clear, fill } from './dom.js';
import { signDataURL, SIGN_NAMES } from '../world/signs.js';
import { QUESTS } from '../data/quests.js';
import { EXAM_ROUTES, EXAM_RULES } from '../data/exams.js';
import { DRILLS } from '../game/drills.js';
import { VIOLATIONS, SEVERITY, RULES_VERSION, PASS_RULES } from '../data/rules.js';
import { CONTROLS_HELP } from '../core/input.js';
import { readiness } from './profile.js';
import { TestsUI } from './tests.js';
import { WEATHER } from '../world/environment.js';

const ARROWS = { left: '↰', right: '↱', straight: '↑', stop: '⏹' };

export class UI {
  constructor(game) {
    this.game = game;
    this.root = document.getElementById('ui');
    this.mapZoom = 0.9;
    this.signCache = new Map();
    this.buildHud();
    this.buildMenu();
    this.tests = new TestsUI(game, this);
    this.showMenu();
  }

  sign(kind) {
    if (!this.signCache.has(kind)) this.signCache.set(kind, signDataURL(kind));
    return this.signCache.get(kind);
  }

  // ---------------------------------------------------------------------------
  // HUD
  buildHud() {
    const g = this.game;
    this.el = {};
    const E = this.el;
    E.quest = h('section.panel.quest', { 'aria-label': 'Завдання' });
    E.toasts = h('div.toasts', { 'aria-live': 'polite' });
    E.status = h('section.panel.status');
    E.chips = h('div.chips');
    E.instruction = h('div.instruction.hidden', h('span.arrow'), h('div', h('b.ins-main'), h('small.ins-sub')));
    E.speed = h('div.speedbox', h('div.speed-num', '0'), h('div.speed-unit', 'км/год'), h('img.limit', { alt: 'Обмеження швидкості' }));
    E.map = h('canvas.minimap', { width: 220, height: 220, 'aria-label': 'Мінікарта' });
    E.hint = h('div.hint');
    E.flash = h('div.flash');
    E.hud = h('div#hud.hidden',
      h('div.hud-tl', E.quest),
      h('div.hud-tc', E.toasts),
      h('div.hud-tr', E.status),
      h('div.hud-bl', E.chips),
      h('div.hud-bc', E.instruction, E.speed),
      h('div.hud-br', E.map),
      E.hint, E.flash,
    );
    this.root.append(E.hud);
    const chip = (key, action, label) => {
      const b = h('button.chip', { onclick: () => { g.action(action); this.updateHud(); }, title: label }, label);
      b.dataset.key = key;
      return b;
    };
    this.chipDefs = [
      ['engine', 'engine', 'Двигун'], ['belt', 'belt', 'Ремінь'], ['handbrake', 'handbrake', 'Ручник'],
      ['gear', 'gearUp', 'N'], ['trans', 'transmission', 'МКПП'],
      ['indL', 'indLeft', '◀'], ['hazard', 'hazard', '⚠'], ['indR', 'indRight', '▶'],
      ['lights', 'lights', 'Світло'], ['high', 'highbeam', 'Дальнє'], ['fog', 'fog', 'Пт'],
      ['wipers', 'wipers', 'Двірники'], ['limiter', 'limiter', 'Обмеж.'], ['cruise', 'cruise', 'Круїз'],
    ];
    this.chips = {};
    for (const [key, action, label] of this.chipDefs) {
      this.chips[key] = chip(key, action, label);
      E.chips.append(this.chips[key]);
    }
  }

  updateHud() {
    const g = this.game, c = g.car, E = this.el;
    if (!g.mode) return;
    const kmh = Math.round(c.speedKmh);
    E.speed.querySelector('.speed-num').textContent = kmh;
    const limit = g.rules.limit;
    const zone = g.rules.loc?.edge?.zone;
    const kind = !g.inCity ? null : zone === 'residential' ? 'residential' : zone === 'pedestrian' ? 'pedzone' : `speed${limit}`;
    const img = E.speed.querySelector('img');
    if (kind) { img.src = this.sign(kind); img.hidden = false; img.title = kind.startsWith('speed') ? `Обмеження ${limit} км/год` : SIGN_NAMES[kind]; } else img.hidden = true;
    E.speed.classList.toggle('over', g.inCity && kmh > limit + 2);

    const set = (key, on, text, warn) => {
      const b = this.chips[key];
      b.classList.toggle('on', !!on);
      b.classList.toggle('warn', !!warn);
      if (text !== undefined) b.textContent = text;
    };
    const moving = kmh > 3;
    set('engine', c.engineOn, c.engineOn ? 'Двигун ●' : 'Двигун ○');
    set('belt', c.belt, c.belt ? 'Ремінь ✓' : 'Ремінь ✗', !c.belt);
    set('handbrake', c.handbrake, c.handbrake ? 'Ручник ●' : 'Ручник ○', c.handbrake && moving);
    set('gear', true, `Передача ${c.gearLabel}`);
    set('trans', false, c.transmission === 'manual' ? 'МКПП' : 'АКПП');
    const blink = c.blinkOn;
    set('indL', (c.indicator === 'left' || c.hazard) && blink);
    set('indR', (c.indicator === 'right' || c.hazard) && blink);
    set('hazard', c.hazard);
    set('lights', c.lights >= 1, ['Світло: вимк', 'Габарити', 'Ближнє', 'Дальнє'][c.lights], g.env.needLights && c.lights < 2);
    set('high', c.lights === 3);
    set('fog', c.fogRear);
    set('wipers', c.wipers > 0, ['Двірники 0', 'Двірники I', 'Двірники II', 'Двірники III'][c.wipers], g.env.weather === 'rain' && c.wipers === 0);
    set('limiter', !!c.limiter, c.limiter ? `Обмеж. ${c.limiter}` : 'Обмеж.');
    set('cruise', !!c.cruise, c.cruise ? `Круїз ${c.cruise}` : 'Круїз');

    const m = g.mode.hud();
    const minor = g.log.filter((v) => v.severity !== 'critical').length;
    fill(E.status, 
      h('div.mode', m.mode), h('div.sub', m.sub || ''),
      m.score !== undefined ? h('div.score', `Бали: ${m.score}/100`) : h('div.score', `Помилок: ${minor}`),
      m.timer ? h('div.timer', `⏱ ${m.timer}`) : null,
      h('div.env', `${g.env.clock} · ${g.env.w.label} · ${g.env.tempC}°C`),
      g.env.needLights ? h('div.need', `Потрібне ближнє світло: ${g.env.needLightsReason}`) : null,
    );
  }

  setInstruction(main, sub, kind) {
    const E = this.el;
    E.instruction.classList.remove('hidden');
    E.instruction.querySelector('.arrow').textContent = ARROWS[kind] || 'ℹ';
    E.instruction.querySelector('.ins-main').textContent = main;
    E.instruction.querySelector('.ins-sub').textContent = sub || '';
  }

  hint(text) {
    const el = this.el.hint;
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(this._hintT);
    this._hintT = setTimeout(() => el.classList.remove('show'), 3200);
  }

  flash(text) {
    const el = this.el.flash;
    el.textContent = text;
    el.classList.remove('show');
    void el.offsetWidth;
    el.classList.add('show');
  }

  toastViolation(v) {
    const sev = SEVERITY[v.severity];
    const t = h(`div.toast.sev-${v.severity}`, { style: { borderColor: sev.color } },
      h('div.toast-head', h('span.badge', { style: { background: sev.color } }, `−${sev.points}`), h('b', v.title)),
      v.detail ? h('div.detail', v.detail) : null,
      h('div.ref', v.ref),
      h('div.tip', v.tip),
    );
    this.pushToast(t, v.severity === 'critical' ? 9000 : 6500);
  }

  toastGood(text) {
    this.pushToast(h('div.toast.good', h('div.toast-head', h('span.badge.ok', '✓'), h('b', text))), 3500);
  }

  pushToast(t, ms) {
    const box = this.el.toasts;
    box.prepend(t);
    while (box.children.length > 4) box.lastChild.remove();
    setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 400); }, ms);
  }

  renderQuest(st) {
    const E = this.el;
    fill(E.quest, 
      h('div.q-head', h('span.q-num', `Завдання ${st.index + 1}/${st.total}`), h('b', st.title)),
      h('ol.q-steps', st.steps.map((s) => h(`li.${s.state}`,
        h('span.q-dot', s.state === 'done' ? '✓' : s.state === 'active' ? '▸' : '•'),
        h('span', s.text, s.key ? h('kbd', s.key) : null, s.progress ? h('em', ` ${s.progress}`) : null)))),
      st.done ? h('div.q-done', 'Виконано! ', h('button.btn.small', { onclick: () => this.game.mode.startQuest(st.index + 1) }, st.index + 1 < st.total ? 'Наступне завдання →' : 'Усі завдання пройдено')) : null,
      h('div.q-nav',
        h('button.btn.small.ghost', { onclick: () => this.game.mode.startQuest(st.index - 1), disabled: st.index === 0 }, '←'),
        h('button.btn.small.ghost', { onclick: () => this.game.mode.startQuest(st.index + 1), disabled: st.index + 1 >= st.total }, '→'),
      ),
    );
  }

  questIntro(q) {
    this.setInstruction(q.title, q.intro, null);
  }

  toggleQuestLog() { this.el.quest.classList.toggle('collapsed'); }
  cycleMapZoom() { this.mapZoom = this.mapZoom > 1.5 ? 0.45 : this.mapZoom < 0.6 ? 0.9 : 1.8; }

  drawMinimap() {
    const g = this.game, cv = this.el.map, c = cv.getContext('2d');
    const W = cv.width, H = cv.height, car = g.car, net = g.net;
    c.clearRect(0, 0, W, H);
    c.fillStyle = 'rgba(12,16,22,0.82)';
    c.beginPath(); c.arc(W / 2, H / 2, W / 2 - 2, 0, Math.PI * 2); c.fill();
    c.save();
    c.beginPath(); c.arc(W / 2, H / 2, W / 2 - 4, 0, Math.PI * 2); c.clip();
    c.translate(W / 2, H / 2);
    // Heading-up map.
    c.rotate(car.h);
    c.scale(this.mapZoom, this.mapZoom);
    c.translate(-car.x, -car.z);
    c.lineCap = 'round';
    for (const e of net.edges.values()) {
      c.strokeStyle = e.zone === 'pedestrian' ? '#8f7c5c' : e.zone === 'residential' ? '#8a7558' : '#59626f';
      c.lineWidth = e.hwL + e.hwR;
      c.setLineDash(e.zone === 'pedestrian' ? [4, 4] : []);
      c.beginPath(); c.moveTo(e.ax, e.az); c.lineTo(e.bx, e.bz); c.stroke();
      if (e.oneway) {
        const dir = e.oneway === 'ab' ? 1 : -1;
        const m = net.point(e, e.L / 2, 0), d = net.travelDir(e, dir);
        c.fillStyle = '#cfd6df';
        c.beginPath();
        c.moveTo(m.x + d.x * 5, m.z + d.z * 5);
        c.lineTo(m.x - d.x * 3 + d.z * 3, m.z - d.z * 3 - d.x * 3);
        c.lineTo(m.x - d.x * 3 - d.z * 3, m.z - d.z * 3 + d.x * 3);
        c.fill();
      }
    }
    c.setLineDash([]);
    for (const n of net.nodes.values()) {
      if (n.degree < 3) continue;
      c.fillStyle = '#59626f';
      c.beginPath(); c.arc(n.x, n.z, n.R, 0, Math.PI * 2); c.fill();
    }
    // Route (exam) and waypoint.
    const mode = g.mode;
    if (mode && mode.kind === 'exam' && mode.path) {
      c.strokeStyle = 'rgba(53,194,255,0.85)'; c.lineWidth = 3 / this.mapZoom + 1.5;
      c.beginPath();
      c.moveTo(car.x, car.z);
      for (let i = mode.idx; i < mode.path.length - 1; i++) { const n = net.nodes.get(mode.path[i]); c.lineTo(n.x, n.z); }
      const f = mode.finishPoint; c.lineTo(f.x, f.z);
      c.stroke();
    }
    const wp = g.waypointPos;
    if (wp) {
      c.fillStyle = wp.finish ? '#3ddc84' : '#35c2ff';
      c.beginPath(); c.arc(wp.x, wp.z, 6 / this.mapZoom + 2, 0, Math.PI * 2); c.fill();
    }
    // Signals near the player.
    for (const e of net.edges.values()) for (const st of e.stops) {
      if (st.control !== 'light') continue;
      const p = net.lanePoint(e, st.dir, 0, st.s);
      if (Math.abs(p.x - car.x) > 200 || Math.abs(p.z - car.z) > 200) continue;
      const s = net.signalState(st.ctrl, st.phase, g.t).state;
      c.fillStyle = s === 'green' || s === 'flash' ? '#2bd96b' : s === 'yellow' || s === 'redyellow' ? '#ffb300' : '#ff3b30';
      c.beginPath(); c.arc(p.x, p.z, 2.4 / this.mapZoom + 1, 0, Math.PI * 2); c.fill();
    }
    // Traffic and pedestrians.
    if (g.traffic.enabled) {
      c.fillStyle = '#e8ecf1';
      for (const o of g.traffic.cars) {
        c.save(); c.translate(o.x, o.z); c.rotate(-o.h); c.fillRect(-o.wid / 2, -o.len / 2, o.wid, o.len); c.restore();
      }
      c.fillStyle = '#ffd166';
      for (const p of g.peds.list) { c.beginPath(); c.arc(p.x, p.z, 1.1, 0, Math.PI * 2); c.fill(); }
    }
    c.restore();
    // Player arrow (always pointing up).
    c.fillStyle = '#ff5a1f';
    c.beginPath(); c.moveTo(W / 2, H / 2 - 9); c.lineTo(W / 2 + 6, H / 2 + 7); c.lineTo(W / 2, H / 2 + 3); c.lineTo(W / 2 - 6, H / 2 + 7); c.fill();
    // North marker.
    c.fillStyle = '#cfd6df'; c.font = 'bold 12px system-ui'; c.textAlign = 'center'; c.textBaseline = 'middle';
    c.fillText('Пн', W / 2 + Math.sin(car.h) * (W / 2 - 14), H / 2 - Math.cos(car.h) * (H / 2 - 14));
  }

  onModeStart(mode) {
    this.el.hud.classList.remove('hidden');
    this.el.quest.classList.toggle('hidden', mode.kind !== 'free');
    this.el.instruction.classList.add('hidden');
    clear(this.el.toasts);
    if (mode.kind === 'free') this.renderQuest(mode.questState());
    this.updateHud();
  }

  // ---------------------------------------------------------------------------
  // Menus
  buildMenu() {
    this.menu = h('div#menu.screen');
    this.pause = h('div#pause.screen.hidden');
    this.results = h('div#results.screen.hidden');
    this.help = h('div#help.screen.hidden');
    this.root.append(this.menu, this.pause, this.results, this.help);
  }

  card(title, desc, onclick, extra) {
    return h('button.card', { onclick }, h('b', title), h('span', desc), extra || null);
  }

  showMenu(page = 'home') {
    const g = this.game, p = g.profile;
    this.el.hud.classList.add('hidden');
    this.pause.classList.add('hidden');
    this.results.classList.add('hidden');
    this.menu.classList.remove('hidden');
    const ready = readiness(p, { drills: DRILLS.length, exams: EXAM_ROUTES.length, quests: QUESTS.length });
    const back = h('button.btn.ghost.back', { onclick: () => this.showMenu() }, '← Назад');
    const src = g.net.meta.source === 'schematic' ? 'Схематична карта центру Львова (назви вулиць реальні, розміщення знаків спрощене)' : `Дані: ${g.net.meta.title || 'OpenStreetMap'}`;
    let body;
    if (page === 'home') {
      body = h('div.menu-home',
        h('header.brand', h('div.logo', 'ЛД'), h('div', h('h1', 'Lviv Drive'), h('p', 'Тренажер водія: ПДР України · Львів · 2026'))),
        h('div.ready', h('div.ready-label', `Готовність до іспиту в ТСЦ: ${ready}%`), h('div.bar', h('i', { style: { width: `${ready}%` } }))),
        h('div.cards',
          this.card('Навчання', 'Квести по Львову: світлофори, пішоходи, житлова зона, STOP, паркування, ніч', () => this.showMenu('free')),
          this.card('Практичний іспит', '3 екзаменаційні маршрути з голосовими вказівками та оцінюванням', () => this.showMenu('exam')),
          this.card('Практичні вправи', 'Автодром: змійка, гальмування, паркування, бокс, стоп-лінія', () => this.showMenu('drill')),
          this.card('Теоретичний іспит', `${PASS_RULES.theory.label}`, () => this.tests.startTheory()),
          this.card('Технічний тест', 'Огляд авто перед виїздом і контрольні лампи', () => this.showMenu('tech')),
          this.card('Довідник', 'Порушення, бали, посилання на ПДР, знаки', () => this.showMenu('ref')),
        ),
        h('div.menu-row',
          h('button.btn.ghost', { onclick: () => this.showMenu('settings') }, 'Налаштування'),
          h('button.btn.ghost', { onclick: () => this.toggleHelp(true) }, 'Керування'),
        ),
        h('footer.note', `${src}. ${RULES_VERSION.label}`),
      );
    } else if (page === 'free') {
      const weather = h('select#sel-weather', Object.entries(WEATHER).map(([k, w]) => h('option', { value: k }, w.label)));
      const hour = h('select#sel-hour', [[8, 'Ранок 08:00'], [11, 'День 11:00'], [17.9, 'Захід сонця 17:55'], [21, 'Вечір 21:00'], [23.5, 'Ніч 23:30']].map(([v, l]) => h('option', { value: v, selected: v === 11 }, l)));
      const cycle = h('input#sel-cycle', { type: 'checkbox' });
      body = h('div', back, h('h2', 'Навчання — квести'),
        h('div.form-row', h('label', 'Погода ', weather), h('label', 'Час ', hour), h('label.check', cycle, ' 4D: плин часу (1 с = 6 хв)')),
        h('div.cards.list', QUESTS.map((q, i) => this.card(`${p.quests[q.id] ? '✓ ' : ''}${i + 1}. ${q.title}`, q.intro, () => g.startMode('free', { quest: i, weather: weather.value, hour: Number(hour.value), cycle: cycle.checked })))),
      );
    } else if (page === 'exam') {
      body = h('div', back, h('h2', 'Практичний іспит'),
        h('ul.rules', EXAM_RULES.map((r) => h('li', r))),
        h('div.cards', EXAM_ROUTES.map((r) => {
          const best = p.exams[r.id];
          return this.card(r.title, r.desc, () => g.startMode('exam', { route: r.id }),
            best ? h('em', `Найкращий результат: ${best.score}/100 ${best.passed ? '✓ складено' : '✗'}`) : null);
        })));
    } else if (page === 'drill') {
      body = h('div', back, h('h2', 'Практичні вправи (автодром)'), h('p.muted', PASS_RULES.drill.label),
        h('div.cards', DRILLS.map((d) => {
          const best = p.drills[d.id];
          return this.card(d.title, d.desc, () => g.startMode('drill', { drill: d.id }), best ? h('em', `Найкраще: ${best.score}/100 ${best.passed ? '✓' : ''}`) : null);
        })));
    } else if (page === 'tech') {
      body = h('div', back, h('h2', 'Технічний тест'),
        h('div.cards',
          this.card('Огляд автомобіля перед виїздом', 'Знайдіть несправності та визначте, чи можна експлуатувати авто (ПДР, розділ 31)', () => this.tests.startInspection(), p.inspection ? h('em', `Найкраще: ${p.inspection.score}/100`) : null),
          this.card('Контрольні лампи панелі приладів', 'Що означає індикатор і як діяти', () => this.tests.startLamps(), p.lamps ? h('em', `Найкраще: ${p.lamps.score}/100`) : null),
        ));
    } else if (page === 'ref') {
      body = h('div', back, h('h2', 'Довідник'),
        h('p.muted', `${RULES_VERSION.label}. Джерело: `, h('a', { href: RULES_VERSION.source, target: '_blank', rel: 'noopener' }, 'zakon.rada.gov.ua'), '. Штрафи не наведено: суми змінюються, законопроєкт №15348 щодо порогів швидкості ще не ухвалено.'),
        h('h3', 'Система оцінювання'),
        h('ul.rules', Object.values(SEVERITY).map((s) => h('li', h('span.badge', { style: { background: s.color } }, s.label), ` −${s.points} балів`)),
          h('li', `Практичний іспит: ${PASS_RULES.practical.label}. Додатково — оцінка за 12-бальною шкалою.`),
          h('li', `Теорія: ${PASS_RULES.theory.label}.`)),
        h('h3', 'Порушення, які відстежує гра'),
        h('div.ref-list', Object.values(VIOLATIONS).map((v) => h('div.ref-item', h('span.badge', { style: { background: SEVERITY[v.severity].color } }, SEVERITY[v.severity].label), h('b', v.title), h('small', v.ref), h('p', v.tip)))),
        h('h3', 'Знаки на карті'),
        h('div.sign-grid', Object.entries(SIGN_NAMES).map(([k, name]) => h('figure', h('img', { src: this.sign(k), alt: name }), h('figcaption', name)))),
      );
    } else if (page === 'settings') {
      const s = g.settings;
      const sel = (key, opts) => h('select', { onchange: (e) => { s[key] = e.target.value; g.saveSettings(); this.applySettings(key); } }, opts.map(([v, l]) => h('option', { value: v, selected: s[key] === v }, l)));
      const chk = (key, label) => h('label.check', h('input', { type: 'checkbox', checked: s[key], onchange: (e) => { s[key] = e.target.checked; g.saveSettings(); this.applySettings(key); } }), ` ${label}`);
      body = h('div', back, h('h2', 'Налаштування'),
        h('div.form-col',
          h('label', 'Графіка ', sel('quality', [['high', 'Висока (тіні, згладжування)'], ['low', 'Низька (слабкі ПК)']]), h('small.muted', ' — потребує перезавантаження')),
          h('label', 'Коробка передач ', sel('transmission', [['manual', 'Механічна (зчеплення)'], ['auto', 'Автоматична']])),
          chk('sound', 'Звук'), chk('voice', 'Голос екзаменатора (Web Speech, uk-UA)'), chk('mirrors', 'Дзеркала заднього виду (живе зображення)'),
        ),
        h('p.muted', `Карта: ${src}. Щоб використати реальні дані OpenStreetMap, запустіть «npm run import-osm» (див. README).`),
      );
    }
    fill(this.menu, h('div.menu-inner', body));
  }

  applySettings(key) {
    const g = this.game, s = g.settings;
    if (key === 'sound') g.audio.setMuted(!s.sound);
    if (key === 'voice') g.audio.voice = s.voice;
    if (key === 'mirrors') g.cockpit.mirrors = s.mirrors;
    if (key === 'transmission') g.car.setTransmission(s.transmission);
  }

  hideMenu() {
    this.menu.classList.add('hidden');
    this.pause.classList.add('hidden');
    this.tests.hide();
  }

  isPauseOpen() { return !this.pause.classList.contains('hidden'); }

  showPause() {
    const g = this.game;
    fill(this.pause, h('div.menu-inner.narrow',
      h('h2', 'Пауза'),
      h('button.btn', { onclick: () => g.resume() }, 'Продовжити'),
      h('button.btn.ghost', { onclick: () => this.restartMode() }, 'Почати спочатку'),
      h('button.btn.ghost', { onclick: () => this.toggleHelp(true) }, 'Керування'),
      h('button.btn.ghost', { onclick: () => g.quitToMenu() }, 'Головне меню'),
    ));
    this.pause.classList.remove('hidden');
  }

  restartMode() {
    const m = this.game.mode;
    if (!m) return;
    if (m.kind === 'free') this.game.startMode('free', { ...m.opts, quest: m.questIndex });
    else if (m.kind === 'exam') this.game.startMode('exam', { route: m.route.id });
    else this.game.startMode('drill', { drill: m.def.id });
  }

  hideResults() { this.results.classList.add('hidden'); }

  showResults(r) {
    const g = this.game;
    this.el.hud.classList.add('hidden');
    fill(this.results, h('div.menu-inner',
      h('div.result-head',
        h(`div.verdict.${r.passed ? 'pass' : 'fail'}`, r.passed ? '✓' : '✗'),
        h('div', h('h2', r.summary), h('p.muted', r.title), r.reason ? h('p', r.reason) : null),
        h('div.big-score', h('b', `${r.score}`), h('small', '/100'), r.grade12 ? h('div.g12', `${r.grade12} з 12`) : null),
      ),
      r.stats?.length ? h('ul.stats', r.stats.map((s) => h('li', s))) : null,
      r.log?.length ? h('div.ref-list', h('h3', 'Помилки та як їх уникнути'), r.log.map((v) => h('div.ref-item',
        h('span.badge', { style: { background: SEVERITY[v.severity].color } }, `−${SEVERITY[v.severity].points}`),
        h('b', v.title), h('small', v.ref), v.detail ? h('p', v.detail) : null, h('p.tip', v.tip)))) : (r.kind === 'exam' ? h('p', 'Жодної помилки — чудово!') : null),
      r.review || null,
      h('div.menu-row',
        r.retry ? h('button.btn', { onclick: () => r.retry() }, 'Ще раз') : null,
        h('button.btn.ghost', { onclick: () => g.quitToMenu() }, 'Головне меню'),
      ),
    ));
    this.results.classList.remove('hidden');
  }

  toggleHelp(force) {
    const show = force ?? this.help.classList.contains('hidden');
    if (!show) { this.help.classList.add('hidden'); return; }
    fill(this.help, h('div.menu-inner',
      h('h2', 'Керування автомобілем'),
      h('table.keys', CONTROLS_HELP.map(([k, d]) => h('tr', h('td', h('kbd', k)), h('td', d)))),
      h('p.muted', 'Підтримується геймпад: лівий стік — кермо, RT/LT — газ/гальмо, LB — зчеплення, X/Y — передачі.'),
      h('button.btn', { onclick: () => this.toggleHelp(false) }, 'Закрити'),
    ));
    this.help.classList.remove('hidden');
  }
}
