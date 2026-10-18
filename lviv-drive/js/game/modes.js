import { QUESTS } from '../data/quests.js';
import { EXAM_ROUTES } from '../data/exams.js';
import { gradePractical, VIOLATIONS } from '../data/rules.js';
import { DRILLS, DrillRun } from './drills.js';
import { recordResult } from '../ui/profile.js';
import { fmtTime } from '../core/math.js';

const TURN_WORDS = { left: 'поверніть ліворуч', right: 'поверніть праворуч', straight: 'їдьте прямо' };

// ---------------------------------------------------------------------------
// Training: free drive with quests.
export class FreeMode {
  constructor(game, opts = {}) {
    this.game = game;
    this.kind = 'free';
    this.title = 'Навчання';
    this.questIndex = opts.quest ?? 0;
    this.opts = opts;
    this.allowRecover = true;
  }

  start() {
    const g = this.game;
    g.env.set(this.opts.hour ?? 11, this.opts.weather ?? 'clear');
    g.env.timeScale = this.opts.cycle ? 6 : 0;
    g.setTraffic(true);
    g.spawnParked();
    this.startQuest(this.questIndex);
  }

  startQuest(i) {
    this.questIndex = Math.max(0, Math.min(QUESTS.length - 1, i));
    this.quest = QUESTS[this.questIndex];
    this.step = 0;
    this.stepEntered = -1;
    this.wpPos = null;
    this.counters = {};
    this.done = false;
    this.quest.onStart?.(this.api);
    this.game.ui.questIntro(this.quest);
    this.game.ui.renderQuest(this.questState());
  }

  get api() { return this.game.questApi; }

  questState() {
    const q = this.quest;
    return {
      title: q.title, intro: q.intro, index: this.questIndex, total: QUESTS.length,
      steps: q.steps.map((s, i) => ({
        text: s.text, key: s.key, state: i < this.step ? 'done' : i === this.step ? 'active' : 'todo',
        progress: s.event && i === this.step ? `${this.counters[s.event] || 0}/${s.count}` : '',
      })),
      done: this.done,
    };
  }

  get waypoint() {
    if (this.done) return null;
    return this.wpPos || null;
  }

  // Resolve a step's waypoint: the preferred spot on the schematic map, or the
  // nearest matching feature on an imported map.
  resolveWaypoint(wp) {
    const g = this.game, net = g.net, car = g.car;
    if (!wp) return null;
    const pick = (cands) => {
      let best = null;
      for (const c of cands) {
        const d = Math.hypot(c.x - car.x, c.z - car.z);
        if (!best || d < best.d) best = { ...c, d };
      }
      return best;
    };
    const mid = (e) => net.point(e, e.L / 2, 0);
    const cwPos = (cw) => net.point(net.edges.get(cw.edge), cw.s, 0);
    if (wp.prefer) {
      const n = net.nodes.get(wp.prefer), e = net.edges.get(wp.prefer);
      if (n) return { x: n.x, z: n.z };
      if (e) {
        if (wp.feature === 'crosswalk') { const cw = e.crosswalks.find((c) => !c.node && !c.ctrl); if (cw) return cwPos(cw); }
        if (wp.feature === 'parking') { const z = g.city.parking.find((p) => p.edge === e.id); if (z) return net.point(e, (z.s0 + z.s1) / 2, 0); }
        return mid(e);
      }
    }
    const nodes = [...net.nodes.values()].filter((n) => n.degree >= 3);
    const edges = [...net.edges.values()];
    switch (wp.feature) {
      case 'light': return pick(nodes.filter((n) => n.control.kind === 'light'));
      case 'equal': return pick(nodes.filter((n) => n.control.kind === 'uncontrolled'));
      case 'crosswalk': return pick(net.crosswalks.filter((c) => !c.ctrl).map(cwPos));
      case 'residential': return pick(edges.filter((e) => e.zone === 'residential').map(mid));
      case 'stop': return pick(edges.flatMap((e) => e.stops.filter((st) => st.control === 'stop').map((st) => net.lanePoint(e, st.dir, 0, st.s))));
      case 'parking': return pick(g.city.parking.map((z) => net.point(net.edges.get(z.edge), (z.s0 + z.s1) / 2, 0)));
      default: return null;
    }
  }

  enterStep() {
    const st = this.quest.steps[this.step];
    this.wpPos = st ? this.resolveWaypoint(st.waypoint) : null;
    if (st && st.waypoint && !this.wpPos) {
      this.game.ui.hint('На цій карті немає потрібного об\'єкта — крок зараховано');
      this.skipStep = true;
    }
  }

  update() {
    if (this.done) return;
    if (this.stepEntered !== this.step) { this.stepEntered = this.step; this.enterStep(); }
    const st = this.quest.steps[this.step];
    let ok = !!this.skipStep;
    this.skipStep = false;
    if (st.event) ok = ok || (this.counters[st.event] || 0) >= st.count;
    if (!ok && st.check) ok = st.check(this.api);
    if (ok) {
      this.step++;
      this.counters = {};
      this.game.audio.success();
      if (this.step >= this.quest.steps.length) {
        this.done = true;
        recordResult(this.game.profile, 'quest', this.quest.id, { score: 100, passed: true });
        this.game.ui.toastGood(`Завдання «${this.quest.title}» виконано!`);
      }
      this.game.ui.renderQuest(this.questState());
    }
  }

  onEvent(name) {
    this.counters[name] = (this.counters[name] || 0) + 1;
    const st = this.quest.steps[this.step];
    if (st && st.event === name) this.game.ui.renderQuest(this.questState());
  }

  onViolation() {}

  hud() {
    return { mode: this.title, sub: `${this.questIndex + 1}/${QUESTS.length} · ${this.quest.title}` };
  }
}

// ---------------------------------------------------------------------------
// Practical exam on a fixed route with spoken instructions.
// For imported maps the fixed routes don't exist: build a 1-2 km route with
// several junctions between random nodes.
export function generateRoute(net, base) {
  const nodes = [...net.nodes.values()].filter((n) => n.degree >= 3);
  for (let tries = 0; tries < 60 && nodes.length > 1; tries++) {
    const a = nodes[Math.floor(Math.random() * nodes.length)];
    const b = nodes[Math.floor(Math.random() * nodes.length)];
    if (a === b) continue;
    const path = net.shortestPath(a.id, b.id);
    if (!path || path.length < 5 || net.validatePath(path)) continue;
    let len = 0;
    for (let i = 0; i + 1 < path.length; i++) len += net.edgeBetween(path[i], path[i + 1]).L;
    if (len < 700 || len > 2200) continue;
    return { ...base, path, title: `${base.title} (згенеровано за картою)`, startS: 15, finish: 0.5 };
  }
  return null;
}

export class ExamMode {
  constructor(game, routeId) {
    this.game = game;
    this.kind = 'exam';
    const net = game.net;
    const fixed = EXAM_ROUTES.find((r) => r.id === routeId) || EXAM_ROUTES[0];
    const valid = fixed.path.every((id) => net.nodes.has(id)) && !net.validatePath(fixed.path);
    this.route = valid ? fixed : generateRoute(net, fixed) || fixed;
    this.title = 'Практичний іспит';
    this.allowRecover = false;
    this.log = [];
    this.t = 0;
    this.limit = 20 * 60;
  }

  start() {
    const g = this.game, r = this.route;
    g.env.set(r.time, r.weather);
    g.env.timeScale = 0;
    g.setTraffic(true);
    this.path = [...r.path];
    this.idx = 1;
    this.announced = -1;
    this.ended = false;
    g.spawnOnRoute(r);
    this.say(`Доброго дня. ${r.title}. Підготуйтеся до руху: пристебніться, заведіть двигун, за потреби увімкніть ближнє світло. Рушайте, коли будете готові.`);
    g.ui.setInstruction('Підготуйтеся до руху', 'Ремінь · двигун · світло · дзеркала · покажчик');
  }

  say(text) {
    this.game.audio.say(text);
    this.lastSaid = text;
  }

  get finalEdge() {
    const n = this.path.length;
    return this.game.net.edgeBetween(this.path[n - 2], this.path[n - 1]);
  }

  get finishPoint() {
    const net = this.game.net, n = this.path.length;
    const e = this.finalEdge;
    const dir = e.a === this.path[n - 2] ? 1 : -1;
    const s = dir > 0 ? e.L * this.route.finish : e.L * (1 - this.route.finish);
    return { ...net.lanePoint(e, dir, 0, s), e, dir };
  }

  get waypoint() {
    if (this.idx >= this.path.length - 1) {
      const f = this.finishPoint;
      return { x: f.x, z: f.z, finish: true };
    }
    return { node: this.path[this.idx] };
  }

  instructionFor(i) {
    const net = this.game.net;
    const prev = this.path[i - 1], node = this.path[i], next = this.path[i + 1];
    const e1 = net.edgeBetween(prev, node), e2 = net.edgeBetween(node, next);
    const d1 = net.travelDir(e1, e1.a === prev ? 1 : -1), d2 = net.travelDir(e2, e2.a === node ? 1 : -1);
    const kind = net.turnKind(d1, d2);
    const n = net.nodes.get(node);
    const street = e2.name;
    if (n.degree < 3) {
      const bend = kind === 'straight' ? 'Прямо по дорозі' : `Дорога повертає ${kind === 'left' ? 'ліворуч' : 'праворуч'}`;
      return { kind, text: `Рухайтеся вулицею ${street}`, short: bend };
    }
    return { kind, text: `На перехресті ${TURN_WORDS[kind]}${kind === 'straight' ? '' : `, на ${street}`}.`, short: `${TURN_WORDS[kind]} → ${street}` };
  }

  update(dt) {
    if (this.ended) return;
    const g = this.game;
    this.t += dt;
    if (this.t > this.limit) { this.end(false, 'Час іспиту вичерпано'); return; }
    if (this.idx < this.path.length - 1) {
      const n = g.net.nodes.get(this.path[this.idx]);
      const d = Math.hypot(g.car.x - n.x, g.car.z - n.z);
      const ins = this.instructionFor(this.idx);
      if (this.announced !== this.idx && d < 140) {
        this.announced = this.idx;
        if (n.degree >= 3) this.say(ins.text);
      }
      g.ui.setInstruction(ins.short[0].toUpperCase() + ins.short.slice(1), `до перехрестя ${Math.max(0, Math.round(d - n.R))} м`, ins.kind);
    } else {
      const f = this.finishPoint;
      const d = Math.hypot(g.car.x - f.x, g.car.z - f.z);
      if (this.announced !== 'finish') {
        this.announced = 'finish';
        this.say('Зупиніться біля правого краю проїзної частини в позначеному місці та затягніть стоянкове гальмо.');
      }
      g.ui.setInstruction('Зупиніться біля правого краю', `місце зупинки через ${Math.round(d)} м`, 'stop');
      if (d < 14 && g.car.speedKmh < 0.5 && g.car.handbrake) this.end(true, 'Маршрут завершено');
    }
  }

  onEvent(name, data) {
    if (name !== 'node_exit' || this.ended) return;
    const net = this.game.net;
    const expectedNode = this.path[this.idx];
    if (data.node === expectedNode && this.idx < this.path.length - 1) {
      const nextNode = this.path[this.idx + 1];
      const expEdge = net.edgeBetween(expectedNode, nextNode);
      if (data.edge === expEdge) { this.idx++; return; }
    } else if (data.node !== expectedNode && this.idx >= this.path.length - 1) {
      return; // driving past nodes after the last instruction is handled by finish distance
    }
    // Deviation: penalise and re-plan from the node we are heading to.
    this.game.rules.report('ROUTE_MISSED');
    const ahead = net.endNode(data.edge, data.dir).id;
    const goal = this.path[this.path.length - 1];
    const tail = net.shortestPath(ahead, goal, data.edge.id) || [ahead];
    this.path = [data.node, ...tail];
    if (this.path.length < 2) this.path.push(goal);
    this.idx = 1;
    this.announced = -1;
    this.say('Маршрут змінено. Слухайте наступну вказівку.');
  }

  onViolation(v) {
    this.log.push(v);
    if (v.severity === 'critical') this.end(false, v.title);
  }

  end(completed, reason) {
    if (this.ended) return;
    this.ended = true;
    const g = this.game;
    const grade = gradePractical(this.log);
    const passed = completed && grade.passed;
    const summary = passed ? 'Іспит складено' : 'Іспит не складено';
    this.say(passed ? 'Вітаю, іспит складено.' : 'На жаль, іспит не складено.');
    recordResult(g.profile, 'exam', this.route.id, { score: grade.score, passed });
    g.finishMode({
      kind: 'exam', title: `${this.route.title}`, passed, summary, reason,
      score: grade.score, grade12: grade.grade12,
      stats: [`Час: ${fmtTime(this.t)}`, `Помилок: ${this.log.length} (грубих ${grade.major}, критичних ${grade.critical})`, `Пробіг: ${(g.stats.distance / 1000).toFixed(2)} км`],
      log: this.log,
      retry: () => g.startMode('exam', { route: this.route.id }),
    });
  }

  hud() {
    const grade = gradePractical(this.log);
    return { mode: this.title, sub: this.route.title, score: grade.score, timer: fmtTime(this.limit - this.t) };
  }
}

// ---------------------------------------------------------------------------
// Practical drills on the autodrome.
export class DrillMode {
  constructor(game, drillId) {
    this.game = game;
    this.kind = 'drill';
    this.def = DRILLS.find((d) => d.id === drillId) || DRILLS[0];
    this.title = 'Практична вправа';
    this.allowRecover = false;
  }

  start() {
    const g = this.game;
    g.env.set(13, 'clear');
    g.env.timeScale = 0;
    g.setTraffic(false);
    const s = this.def.start;
    g.placeCar(s.x, s.z, s.h, { engine: true, belt: true, handbrake: false });
    this.run = new DrillRun(this.def, g.autodrome);
    g.ui.setInstruction(this.def.title, this.def.desc);
    g.audio.say(this.def.desc);
  }

  update(dt) {
    const g = this.game;
    this.run.update(dt, g.car);
    if (this.run.cue && !this.cueShown) { this.cueShown = true; g.ui.flash('СТОП!'); g.audio.critical(); }
    g.ui.setInstruction(this.def.title, this.run.liveInfo);
    if (this.run.done && !this.reported) {
      this.reported = true;
      const r = this.run.result;
      recordResult(g.profile, 'drill', this.def.id, r);
      g.finishMode({
        kind: 'drill', title: this.def.title, passed: r.passed, summary: r.passed ? 'Вправу виконано' : 'Вправу не виконано',
        score: r.score, grade12: null, stats: r.lines, log: [],
        retry: () => g.startMode('drill', { drill: this.def.id }),
      });
    }
  }

  onCollision() { this.run.onCollision(); }
  onEvent() {}
  onViolation() {}
  hud() { return { mode: this.title, sub: this.def.title, timer: fmtTime(this.run.t) }; }
}

export { QUESTS, EXAM_ROUTES, DRILLS, VIOLATIONS };
