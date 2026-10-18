import { h, fill, shuffle } from './dom.js';
import { QUESTIONS, LAMPS, INSPECTION } from '../data/questions.js';
import { PASS_RULES, to12 } from '../data/rules.js';
import { recordResult } from './profile.js';
import { fmtTime } from '../core/math.js';

// Dashboard warning lamp pictograms (simplified ISO 2575 shapes).
const LAMP_SVG = {
  oil: '<path d="M8 30h30l10-8 8 2-12 10H8z" fill="currentColor"/><path d="M14 30v-8h14l4 4" stroke="currentColor" stroke-width="3" fill="none"/><circle cx="54" cy="36" r="2.5" fill="currentColor"/>',
  battery: '<rect x="8" y="18" width="48" height="28" rx="3" stroke="currentColor" stroke-width="4" fill="none"/><rect x="14" y="12" width="8" height="6" fill="currentColor"/><rect x="42" y="12" width="8" height="6" fill="currentColor"/><path d="M14 32h8M43 32h8M47 28v8" stroke="currentColor" stroke-width="3"/>',
  temp: '<path d="M32 8v28" stroke="currentColor" stroke-width="5"/><circle cx="32" cy="42" r="7" fill="currentColor"/><path d="M8 52c6-4 10 4 16 0s10 4 16 0 10 4 16 0" stroke="currentColor" stroke-width="3" fill="none"/><path d="M36 16h8M36 24h8" stroke="currentColor" stroke-width="3"/>',
  brake: '<circle cx="32" cy="32" r="16" stroke="currentColor" stroke-width="4" fill="none"/><path d="M12 16a24 24 0 0 0 0 32M52 16a24 24 0 0 1 0 32" stroke="currentColor" stroke-width="4" fill="none"/><path d="M32 22v12" stroke="currentColor" stroke-width="5"/><circle cx="32" cy="40" r="2.5" fill="currentColor"/>',
  abs: '<circle cx="32" cy="32" r="16" stroke="currentColor" stroke-width="4" fill="none"/><path d="M12 16a24 24 0 0 0 0 32M52 16a24 24 0 0 1 0 32" stroke="currentColor" stroke-width="4" fill="none"/><text x="32" y="37" text-anchor="middle" font-size="13" font-weight="bold" fill="currentColor" font-family="Arial">ABS</text>',
  belt: '<circle cx="30" cy="12" r="6" fill="currentColor"/><path d="M22 22h16l4 18H22z" fill="currentColor"/><path d="M20 20l22 26" stroke="#111" stroke-width="4"/><path d="M22 40l-4 16M38 40l4 16" stroke="currentColor" stroke-width="5"/>',
  engine: '<path d="M10 26h8v-6h18v6h6l6-6h8v24h-8l-6-6h-6v8H18v-8h-8z" fill="none" stroke="currentColor" stroke-width="4"/>',
  fuel: '<rect x="12" y="12" width="22" height="40" rx="2" stroke="currentColor" stroke-width="4" fill="none"/><rect x="16" y="16" width="14" height="10" fill="currentColor"/><path d="M34 22h8l6 6v18a3 3 0 0 1-6 0V34h-8" stroke="currentColor" stroke-width="3" fill="none"/>',
  highbeam: '<path d="M30 14c14 0 20 8 20 18s-6 18-20 18z" fill="currentColor"/><path d="M8 18h16M8 26h16M8 34h16M8 42h16" stroke="currentColor" stroke-width="4"/>',
  fog: '<path d="M34 14c-14 0-20 8-20 18s6 18 20 18z" fill="currentColor"/><path d="M40 20h16M40 32h16M40 44h16" stroke="currentColor" stroke-width="4"/><path d="M48 12c-3 6 3 10 0 16s3 10 0 16" stroke="currentColor" stroke-width="2" fill="none"/>',
};
const LAMP_COLOR = { oil: '#ff3b30', battery: '#ff3b30', temp: '#ff3b30', brake: '#ff3b30', abs: '#ffb300', belt: '#ff3b30', engine: '#ffb300', fuel: '#ffb300', highbeam: '#3b82ff', fog: '#ffb300' };

function lampIcon(id) {
  const el = h('div.lamp', { style: { color: LAMP_COLOR[id] } });
  el.innerHTML = `<svg viewBox="0 0 64 64" width="64" height="64" aria-hidden="true">${LAMP_SVG[id]}</svg>`;
  return el;
}

export class TestsUI {
  constructor(game, ui) {
    this.game = game;
    this.ui = ui;
    this.view = h('div#testview.screen.hidden');
    ui.root.append(this.view);
  }

  hide() { this.view.classList.add('hidden'); clearInterval(this.timer); }

  open() {
    this.ui.menu.classList.add('hidden');
    this.view.classList.remove('hidden');
  }

  close() {
    this.hide();
    this.ui.showMenu();
  }

  // ---- Theory ticket ---------------------------------------------------------
  startTheory() {
    const R = PASS_RULES.theory;
    // Balanced ticket: shuffle, but keep every topic represented.
    const byTopic = new Map();
    for (const q of shuffle(QUESTIONS)) {
      if (!byTopic.has(q.topic)) byTopic.set(q.topic, []);
      byTopic.get(q.topic).push(q);
    }
    const picked = [];
    while (picked.length < R.questions) {
      let added = false;
      for (const list of byTopic.values()) {
        if (list.length && picked.length < R.questions) { picked.push(list.shift()); added = true; }
      }
      if (!added) break;
    }
    this.theory = { qs: shuffle(picked).map((q) => ({ ...q, order: shuffle(q.options.map((_, i) => i)) })), i: 0, answers: [], left: R.minutes * 60 };
    this.open();
    clearInterval(this.timer);
    this.timer = setInterval(() => {
      this.theory.left--;
      const t = this.view.querySelector('.t-timer');
      if (t) t.textContent = `⏱ ${fmtTime(this.theory.left)}`;
      if (this.theory.left <= 0) this.finishTheory();
    }, 1000);
    this.renderTheory();
  }

  renderTheory() {
    const T = this.theory, q = T.qs[T.i];
    const nav = h('div.qnav', T.qs.map((_, i) => h(`button.qn${i === T.i ? '.cur' : ''}${T.answers[i] !== undefined ? '.ans' : ''}`, { onclick: () => { T.i = i; this.renderTheory(); } }, i + 1)));
    fill(this.view, h('div.menu-inner.test',
      h('div.t-head', h('b', 'Теоретичний іспит ПДР'), h('span.t-timer', `⏱ ${fmtTime(T.left)}`), h('button.btn.small.ghost', { onclick: () => this.close() }, 'Вийти')),
      nav,
      h('div.qcard',
        h('div.q-topic', `${q.topic} · питання ${T.i + 1} з ${T.qs.length}`),
        q.sign ? h('img.q-sign', { src: this.ui.sign(q.sign), alt: 'Дорожній знак' }) : null,
        h('h3', q.q),
        h('div.options', q.order.map((oi) => h(`button.opt${T.answers[T.i] === oi ? '.sel' : ''}`, {
          onclick: () => {
            T.answers[T.i] = oi;
            const next = T.qs.findIndex((_, k) => k > T.i && T.answers[k] === undefined);
            const any = T.qs.findIndex((_, k) => T.answers[k] === undefined);
            if (next >= 0) T.i = next; else if (any >= 0) T.i = any;
            if (any < 0) { this.renderTheory(); return; }
            this.renderTheory();
          },
        }, q.options[oi]))),
      ),
      h('div.menu-row', h('button.btn', { onclick: () => this.finishTheory() }, `Завершити (${T.answers.filter((a) => a !== undefined).length}/${T.qs.length})`)),
    ));
  }

  finishTheory() {
    clearInterval(this.timer);
    const T = this.theory, R = PASS_RULES.theory;
    const wrong = T.qs.map((q, i) => ({ q, a: T.answers[i] })).filter((x) => x.a !== x.q.answer);
    const correct = T.qs.length - wrong.length;
    const score = Math.round((correct / T.qs.length) * 100);
    const passed = wrong.length <= R.maxMistakes && T.left > 0;
    recordResult(this.game.profile, 'theory', 'ticket', { score, passed });
    this.hide();
    this.ui.showResults({
      kind: 'test', title: 'Теоретичний іспит ПДР', passed, summary: passed ? 'Теорію складено' : 'Теорію не складено',
      score, grade12: to12(score),
      stats: [`Правильних відповідей: ${correct} з ${T.qs.length}`, `Помилок: ${wrong.length} (допускається ${R.maxMistakes})`, T.left <= 0 ? 'Час вичерпано' : `Залишок часу: ${fmtTime(T.left)}`],
      review: wrong.length ? h('div.ref-list', h('h3', 'Розбір помилок'), wrong.map(({ q, a }) => h('div.ref-item',
        q.sign ? h('img.q-sign.small', { src: this.ui.sign(q.sign), alt: '' }) : null,
        h('b', q.q), h('p', 'Ваша відповідь: ', a === undefined ? '—' : q.options[a]),
        h('p.tip', 'Правильно: ', q.options[q.answer]), h('small', q.ref)))) : null,
      retry: () => this.startTheory(),
    });
  }

  // ---- Vehicle inspection -------------------------------------------------------
  startInspection() {
    const items = INSPECTION.map((it) => ({ ...it, fault: null, checked: false, verdict: null }));
    const pool = shuffle(items);
    const nFaults = 2 + Math.floor(Math.random() * 3);
    let forbiddenPlaced = Math.random() < 0.8 ? 0 : 1; // usually at least one forbidden fault
    for (let k = 0; k < nFaults; k++) {
      const it = pool[k];
      const options = it.faults.filter((f) => (forbiddenPlaced === 0 ? f.forbidden : true));
      const f = (options.length ? options : it.faults)[Math.floor(Math.random() * (options.length || it.faults.length))];
      it.fault = f;
      if (f.forbidden) forbiddenPlaced++;
    }
    this.insp = { items, decision: null };
    this.open();
    this.renderInspection();
  }

  renderInspection() {
    const I = this.insp;
    const scroll = this.view.scrollTop;
    const verdicts = [['ok', 'Справно'], ['minor', 'Несправність — можна їхати'], ['forbidden', 'Експлуатацію заборонено']];
    fill(this.view, h('div.menu-inner.test',
      h('div.t-head', h('b', 'Огляд автомобіля перед виїздом'), h('button.btn.small.ghost', { onclick: () => this.close() }, 'Вийти')),
      h('p.muted', 'Перевірте кожен вузол, оцініть стан і вирішіть, чи можна виїжджати. Підстава — ПДР, розділ 31 «Технічний стан транспортних засобів».'),
      h('div.insp', I.items.map((it) => h('div.insp-item',
        h('div.insp-title', h('b', it.title),
          it.checked ? h(`span.reading${it.fault ? '.bad' : ''}`, it.fault ? it.fault.text : it.ok)
            : h('button.btn.small', { onclick: () => { it.checked = true; this.renderInspection(); } }, 'Перевірити')),
        it.checked ? h('div.verdicts', verdicts.map(([v, l]) => h(`button.opt.small${it.verdict === v ? '.sel' : ''}`, { onclick: () => { it.verdict = v; this.renderInspection(); } }, l))) : null,
      ))),
      h('div.qcard', h('h3', 'Чи можна експлуатувати автомобіль?'),
        h('div.options', [['yes', 'Так, можна виїжджати'], ['no', 'Ні, рух заборонено до усунення несправностей']].map(([v, l]) => h(`button.opt${I.decision === v ? '.sel' : ''}`, { onclick: () => { I.decision = v; this.renderInspection(); } }, l)))),
      h('div.menu-row', h('button.btn', { onclick: () => this.finishInspection() }, 'Завершити огляд')),
    ));
    this.view.scrollTop = scroll;
  }

  finishInspection() {
    const I = this.insp;
    let pts = 0;
    const lines = [];
    for (const it of I.items) {
      const truth = !it.fault ? 'ok' : it.fault.forbidden ? 'forbidden' : 'minor';
      const ok = it.verdict === truth;
      if (ok) pts++;
      if (!ok) lines.push(h('div.ref-item', h('b', it.title), h('p', it.fault ? it.fault.text : it.ok),
        h('p.tip', 'Правильно: ', truth === 'ok' ? 'справно' : truth === 'minor' ? 'несправність, з якою можна їхати (усунути найближчим часом)' : 'експлуатацію заборонено (ПДР, розділ 31)'),
        !it.checked ? h('small', 'Вузол не перевірено') : null));
    }
    const mustStay = I.items.some((it) => it.fault && it.fault.forbidden);
    const decisionOk = (I.decision === 'no') === mustStay && I.decision !== null;
    const score = Math.round(((pts + (decisionOk ? 4 : 0)) / (I.items.length + 4)) * 100);
    const passed = decisionOk && score >= PASS_RULES.technical.minScore;
    recordResult(this.game.profile, 'inspection', 'pretrip', { score, passed });
    this.hide();
    this.ui.showResults({
      kind: 'test', title: 'Технічний огляд перед виїздом', passed, summary: passed ? 'Огляд виконано правильно' : 'Огляд виконано з помилками',
      score, grade12: to12(score),
      stats: [`Правильно оцінено вузлів: ${pts} з ${I.items.length}`, `Рішення про виїзд: ${decisionOk ? 'правильне' : 'неправильне'} (${mustStay ? 'експлуатацію заборонено' : 'можна виїжджати'})`],
      review: lines.length ? h('div.ref-list', h('h3', 'Розбір'), lines) : null,
      retry: () => this.startInspection(),
    });
  }

  // ---- Warning lamps ---------------------------------------------------------------
  startLamps() {
    const lamps = shuffle(LAMPS).slice(0, 8);
    this.lamps = { list: lamps.map((l) => ({ ...l, options: shuffle([l, ...shuffle(LAMPS.filter((x) => x.id !== l.id)).slice(0, 3)]) })), answers: {} };
    this.open();
    this.renderLamps();
  }

  renderLamps() {
    const Lm = this.lamps;
    const scroll = this.view.scrollTop;
    fill(this.view, h('div.menu-inner.test',
      h('div.t-head', h('b', 'Контрольні лампи панелі приладів'), h('button.btn.small.ghost', { onclick: () => this.close() }, 'Вийти')),
      h('div.lamp-grid', Lm.list.map((l) => h('div.lamp-q', lampIcon(l.id),
        h('div.options', l.options.map((o) => h(`button.opt.small${Lm.answers[l.id] === o.id ? '.sel' : ''}`, { onclick: () => { Lm.answers[l.id] = o.id; this.renderLamps(); } }, o.label)))))),
      h('div.menu-row', h('button.btn', { onclick: () => this.finishLamps() }, `Завершити (${Object.keys(Lm.answers).length}/${Lm.list.length})`)),
    ));
    this.view.scrollTop = scroll;
  }

  finishLamps() {
    const Lm = this.lamps;
    const wrong = Lm.list.filter((l) => Lm.answers[l.id] !== l.id);
    const score = Math.round(((Lm.list.length - wrong.length) / Lm.list.length) * 100);
    const passed = wrong.length <= 1;
    recordResult(this.game.profile, 'lamps', 'quiz', { score, passed });
    this.hide();
    this.ui.showResults({
      kind: 'test', title: 'Контрольні лампи', passed, summary: passed ? 'Тест складено' : 'Тест не складено', score, grade12: to12(score),
      stats: [`Правильно: ${Lm.list.length - wrong.length} з ${Lm.list.length} (допускається 1 помилка)`],
      review: h('div.ref-list', h('h3', 'Що означають лампи'), Lm.list.map((l) => h('div.ref-item.lamp-row', lampIcon(l.id), h('div', h('b', l.label), h('p.tip', l.action))))),
      retry: () => this.startLamps(),
    });
  }
}
