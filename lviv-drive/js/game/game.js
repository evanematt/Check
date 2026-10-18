import * as THREE from 'three';
import { loadNetworkData } from '../data/network.js';
import { RoadNetwork, PARK_W } from '../sim/network.js';
import { makeTextures } from '../world/textures.js';
import { buildCity } from '../world/city.js';
import { Cockpit } from '../world/cockpit.js';
import { Environment } from '../world/environment.js';
import { Car } from '../sim/car.js';
import { Traffic } from '../sim/traffic.js';
import { Pedestrians } from '../sim/peds.js';
import { CollisionWorld, carVsVehicle, carVsPoint } from '../sim/collide.js';
import { RulesEngine } from '../sim/rules.js';
import { Input } from '../core/input.js';
import { Audio } from '../core/audio.js';
import { Autodrome, AUTODROME } from './drills.js';
import { FreeMode, ExamMode, DrillMode } from './modes.js';
import { UI } from '../ui/ui.js';
import { loadProfile } from '../ui/profile.js';
import { headingOf, fwd } from '../core/math.js';

const SETTINGS_KEY = 'lvivdrive.settings.v1';

function loadSettings() {
  const def = { quality: 'high', transmission: 'manual', sound: true, voice: true, mirrors: true, traffic: 20 };
  try { return { ...def, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') }; } catch { return def; }
}

export class Game {
  constructor(canvas) {
    this.canvas = canvas;
    this.settings = loadSettings();
    this.profile = loadProfile();
    this.paused = true;
    this.mode = null;
    this.t = 0;
    this.flags = {};
    this.stats = { distance: 0, nightClean: 0 };
  }

  saveSettings() {
    try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(this.settings)); } catch { /* ignore */ }
  }

  async init(onProgress = () => {}) {
    const high = this.settings.quality === 'high';
    const r = (this.renderer = new THREE.WebGLRenderer({ canvas: this.canvas, antialias: high, powerPreference: 'high-performance' }));
    r.setPixelRatio(Math.min(window.devicePixelRatio || 1, high ? 2 : 1));
    r.outputColorSpace = THREE.SRGBColorSpace;
    r.toneMapping = THREE.ACESFilmicToneMapping;
    r.toneMappingExposure = 1.05;
    r.shadowMap.enabled = high;
    r.shadowMap.type = THREE.PCFSoftShadowMap;
    this.scene = new THREE.Scene();

    onProgress('Завантаження карти Львова…');
    const data = await loadNetworkData();
    this.net = new RoadNetwork(data);
    onProgress('Будуємо місто…');
    await new Promise((res) => setTimeout(res, 0));
    this.textures = makeTextures();
    this.city = buildCity(this.net, this.textures, { quality: this.settings.quality });
    this.scene.add(this.city.group);
    this.autodrome = new Autodrome(this.scene, this.textures);
    this.collisions = new CollisionWorld([...this.city.colliders, ...this.autodrome.colliders]);

    onProgress('Час доби, погода, трафік…');
    this.env = new Environment(this.scene, { shadows: high });
    this.cockpit = new Cockpit(r, this.scene, { mirrors: this.settings.mirrors });
    this.car = new Car();
    this.car.setTransmission(this.settings.transmission);
    this.car.on((type, d) => this.onCarEvent(type, d));
    this.traffic = new Traffic(this.net, this.scene, this.settings.traffic);
    this.peds = new Pedestrians(this.net, this.scene, high ? 22 : 12);
    this.rules = new RulesEngine(this.net, {
      onViolation: (v) => this.onViolation(v),
      onEvent: (n, d) => this.onEvent(n, d),
    });
    this.audio = new Audio();
    this.audio.setMuted(!this.settings.sound);
    this.audio.voice = this.settings.voice;
    this.input = new Input(this.canvas, (a) => this.action(a));
    this.menuCam = new THREE.PerspectiveCamera(55, 1, 0.5, 3000);
    this.marker = this.makeMarker();
    this.ui = new UI(this);
    window.addEventListener('resize', () => this.resize());
    this.resize();
    this.spawnParked();
    this.last = performance.now();
    this.frameCount = 0;
    this.renderer.setAnimationLoop(() => this.frame());
  }

  // Shared API for quests (data/quests.js).
  get questApi() {
    const g = this;
    return {
      car: g.car, flags: g.flags, stats: g.stats, get loc() { return g.rules.loc; },
      near: (id, r) => { const n = g.net.nodes.get(id); return n && Math.hypot(g.car.x - n.x, g.car.z - n.z) < r; },
      nearEdge: (id, t, r) => {
        const e = g.net.edges.get(id);
        if (!e) return false;
        const p = g.net.point(e, e.L * t, 0);
        return Math.hypot(g.car.x - p.x, g.car.z - p.z) < r;
      },
      setTime: (h) => g.env.set(h),
      atWaypoint: (r) => { const w = g.mode?.wpPos; return !!w && Math.hypot(g.car.x - w.x, g.car.z - w.z) < r; },
    };
  }

  makeMarker() {
    const grp = new THREE.Group();
    const mat = new THREE.MeshBasicMaterial({ color: 0x35c2ff, transparent: true, opacity: 0.35, depthWrite: false });
    const col = new THREE.Mesh(new THREE.CylinderGeometry(1.6, 1.6, 30, 24, 1, true), mat);
    col.position.y = 15;
    const ring = new THREE.Mesh(new THREE.TorusGeometry(2.2, 0.15, 8, 32), new THREE.MeshBasicMaterial({ color: 0x35c2ff }));
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 0.2;
    grp.add(col, ring);
    grp.userData = { mat, ringMat: ring.material };
    grp.visible = false;
    this.scene.add(grp);
    return grp;
  }

  resize() {
    const w = window.innerWidth, h = window.innerHeight;
    this.renderer.setSize(w, h, false);
    this.cockpit.setAspect(w / h);
    this.menuCam.aspect = w / h;
    this.menuCam.updateProjectionMatrix();
  }

  // ---- placement ------------------------------------------------------------
  placeCar(x, z, h, o = {}) {
    const car = this.car;
    const tr = car.transmission;
    car.reset(x, z, h);
    car.setTransmission(tr);
    car.engineOn = !!o.engine;
    if (car.engineOn) car.rpm = 800;
    car.belt = !!o.belt;
    car.handbrake = o.handbrake !== false;
    this.rules.reset();
    this.flags = {};
    this.stats = { distance: 0, nightClean: 0 };
    this.cockpit.look.targetYaw = 0;
    this.t = this.t || 0;
  }

  spawnParked() {
    const zone = this.city.parking[0];
    if (zone) {
      const e = this.net.edges.get(zone.edge);
      const s = zone.s0 + 9;
      const lat = zone.sign * (e.carriage + PARK_W / 2);
      const p = this.net.point(e, s, lat);
      this.placeCar(p.x, p.z, headingOf(this.net.travelDir(e, zone.sign)));
      return;
    }
    const e = [...this.net.edges.values()].find((x) => this.net.allowed(x, 1));
    const p = this.net.lanePoint(e, 1, 0, Math.min(40, e.L / 2));
    this.placeCar(p.x, p.z, headingOf(this.net.travelDir(e, 1)));
  }

  spawnOnRoute(route) {
    const [a, b] = route.path;
    const e = this.net.edgeBetween(a, b);
    const dir = e.a === a ? 1 : -1;
    const n = this.net.nodes.get(a);
    const d = Math.max(route.startS, n.R + 8);
    const s = dir > 0 ? d : e.L - d;
    const p = this.net.lanePoint(e, dir, 0, s);
    this.placeCar(p.x, p.z, headingOf(this.net.travelDir(e, dir)));
    // Clear AI cars right next to the start.
    for (const c of [...this.traffic.cars]) if (Math.hypot(c.x - p.x, c.z - p.z) < 30) this.traffic.respawn(c, p);
  }

  recover() {
    if (this.mode && !this.mode.allowRecover) { this.ui.hint('На іспиті та у вправах відновлення недоступне'); return; }
    let best = null;
    for (const e of this.net.edges.values()) {
      for (const dir of [1, -1]) {
        if (!this.net.allowed(e, dir)) continue;
        const n0 = this.net.startNode(e, dir), n1 = this.net.endNode(e, dir);
        for (let s = n0.R + 6; s < e.L - n1.R - 6; s += 10) {
          const ss = dir > 0 ? s : e.L - s;
          const p = this.net.lanePoint(e, dir, 0, ss);
          const d = Math.hypot(p.x - this.car.x, p.z - this.car.z);
          if (!best || d < best.d) best = { d, p, e, dir };
        }
      }
    }
    if (best) {
      const c = this.car;
      Object.assign(c, { x: best.p.x, z: best.p.z, h: headingOf(this.net.travelDir(best.e, best.dir)), v: 0 });
      this.ui.hint('Автомобіль повернуто на дорогу');
    }
  }

  setTraffic(on) {
    this.traffic.setVisible(on);
    this.peds.setVisible(on);
  }

  // ---- modes ------------------------------------------------------------------
  startMode(kind, opts = {}) {
    this.ui.hideResults();
    if (kind === 'free') this.mode = new FreeMode(this, opts);
    else if (kind === 'exam') this.mode = new ExamMode(this, opts.route);
    else if (kind === 'drill') this.mode = new DrillMode(this, opts.drill);
    this.log = [];
    this.mode.start();
    this.ui.onModeStart(this.mode);
    this.resume();
  }

  finishMode(result) {
    this.paused = true;
    this.ui.showResults(result);
  }

  quitToMenu() {
    this.mode = null;
    this.paused = true;
    this.ui.showMenu();
  }

  resume() {
    this.audio.init();
    this.audio.resume();
    this.paused = false;
    this.ui.hideMenu();
  }

  // ---- events ---------------------------------------------------------------------
  onViolation(v) {
    if (!this.mode || this.paused) return;
    this.log.push(v);
    if (v.code === 'NO_LIGHTS' || v.code === 'HIGH_BEAM_CITY') this.stats.nightClean = 0;
    this.ui.toastViolation(v);
    if (v.severity === 'critical') this.audio.critical(); else this.audio.error();
    this.mode.onViolation(v);
  }

  onEvent(name, data) {
    if (!this.mode || this.paused) return;
    const nice = { stopped_red: 'Правильно: зупинка на червоний', yield_ped: 'Правильно: пропустили пішохода', stop_ok: 'Правильно: повна зупинка перед STOP', signal_ok: 'Правильно: поворот із сигналом', zone_ok: 'Житлова зона: швидкість дотримано', parked_ok: 'Правильно припарковано', equal_ok: 'Рівнозначне перехрестя проїхано правильно', give_way_ok: 'Дорогу надано правильно' };
    if (nice[name] && this.mode.kind === 'free') this.ui.toastGood(nice[name]);
    this.mode.onEvent(name, data);
  }

  onCarEvent(type, d) {
    if (type === 'hint') this.ui.hint(d);
    else if (type === 'stall') { this.rules.report('STALL'); this.ui.hint('Двигун заглух. Вичавіть зчеплення та запустіть (I)'); }
    else if (type === 'crank') this.ui.hint('Запуск двигуна…');
    else if (type === 'grind') this.audio.tone(120, 0.2, 'sawtooth', 0.08);
    else if (type === 'shift') this.audio.click(700, 0.03);
  }

  action(a) {
    if (a === 'pause') {
      if (!this.mode) return;
      if (this.paused) { if (this.ui.isPauseOpen()) this.resume(); } else { this.paused = true; this.ui.showPause(); }
      return;
    }
    if (a === 'help') { this.ui.toggleHelp(); return; }
    if (this.paused) return;
    const c = this.car;
    switch (a) {
      case 'engine': c.toggleEngine(); break;
      case 'handbrake': c.toggleHandbrake(); break;
      case 'gearUp': c.shift(1); break;
      case 'gearDown': c.shift(-1); break;
      case 'transmission':
        if (c.speedKmh > 1) { this.ui.hint('Змінюйте тип коробки лише на стоянці'); break; }
        this.settings.transmission = c.transmission === 'manual' ? 'auto' : 'manual';
        c.setTransmission(this.settings.transmission);
        this.saveSettings();
        this.ui.hint(c.transmission === 'manual' ? 'Механічна коробка (Shift — зчеплення, Q/E — передачі)' : 'Автоматична коробка (Q/E — селектор P-R-N-D, у P/R — лише на місці)');
        break;
      case 'lights': c.cycleLights(); break;
      case 'highbeam': c.toggleHighBeam(); break;
      case 'fog': c.toggleFogRear(); break;
      case 'indLeft': c.toggleIndicator('left'); break;
      case 'indRight': c.toggleIndicator('right'); break;
      case 'hazard': c.toggleHazard(); break;
      case 'wipers': c.cycleWipers(); break;
      case 'belt': c.toggleBelt(); this.audio.click(900, 0.05); break;
      case 'limiter': c.toggleLimiter(this.rules.limit); break;
      case 'cruise': c.toggleCruise(); break;
      case 'recover': this.recover(); break;
      case 'questlog': this.ui.toggleQuestLog(); break;
      case 'map': this.ui.cycleMapZoom(); break;
      case 'lookCenter': this.cockpit.look.targetYaw = 0; break;
      default: break;
    }
  }

  // ---- main loop ---------------------------------------------------------------------
  frame() {
    const now = performance.now();
    const dt = Math.min(0.05, (now - this.last) / 1000);
    this.last = now;
    this.frameCount++;
    if (this.paused || !this.mode) this.renderMenuBackground(dt);
    else this.step(dt);
  }

  renderMenuBackground(dt) {
    this.menuAngle = (this.menuAngle || 0) + dt * 0.04;
    const a = this.menuAngle;
    if (this.mode) {
      // Paused in game: keep the driver's view.
      this.renderer.render(this.scene, this.cockpit.camera);
      return;
    }
    this.menuCam.position.set(Math.cos(a) * 260, 95, Math.sin(a) * 220);
    this.menuCam.lookAt(0, 20, 0);
    this.env.update(dt, { x: 0, z: 0 });
    this.t += dt;
    this.traffic.update(dt, this.t, { player: null, peds: this.peds });
    this.peds.update(dt, this.t, { player: { x: this.menuCam.position.x, z: this.menuCam.position.z, h: 0, v: 0, mode: 'turn' }, traffic: this.traffic });
    this.city.update(this.t, { night: this.env.night, wet: this.env.wet });
    this.renderer.render(this.scene, this.menuCam);
  }

  playerProxy() {
    const c = this.car, loc = this.rules.loc;
    const f = fwd(c.h);
    const p = { x: c.x, z: c.z, h: c.h, v: Math.abs(c.v), len: 4.5, wid: 1.8, mode: 'lane', edge: null, dir: 1, s: 0, curve: null, next: null };
    if (loc && loc.node) {
      p.mode = 'turn';
      p.curve = { node: loc.node.id, fromEdge: this.rules.lastEdge, fromDir: this.rules.lastDir };
    } else if (loc && loc.edge) {
      p.edge = loc.edge;
      p.dir = f.x * loc.edge.d.x + f.z * loc.edge.d.z >= 0 ? 1 : -1;
      p.s = loc.s;
    }
    return p;
  }

  // render=false lets tests advance the simulation deterministically.
  step(dt, render = true) {
    const c = this.car, env = this.env;
    this.t += dt;
    const inp = this.input.poll(dt);
    c.horn = inp.horn;
    // Head look: hold 1/3/C or drag with the mouse.
    const L = this.cockpit.look;
    if (inp.look === 'left') L.targetYaw = 1.05;
    else if (inp.look === 'right') L.targetYaw = -1.0;
    else if (inp.look === 'back') L.targetYaw = 2.6;
    else if (inp.mouse.down) {
      L.targetYaw = Math.max(-2.6, Math.min(2.6, L.targetYaw - inp.mouse.dx * 0.004));
      L.targetPitch = Math.max(-0.8, Math.min(0.5, L.targetPitch - inp.mouse.dy * 0.004));
    } else {
      L.targetYaw *= 0.9;
      L.targetPitch += (-0.06 - L.targetPitch) * 0.1;
    }
    if (L.yaw > 0.5) this.flags.lookedLeft = true;
    if (L.yaw < -0.5) this.flags.lookedRight = true;

    const before = Math.abs(c.v);
    c.update(dt, inp, { mu: env.mu });
    this.stats.distance += Math.abs(c.v) * dt;
    if (env.night > 0.35 && c.lights === 2 && Math.abs(c.v) > 1) this.stats.nightClean += Math.abs(c.v) * dt;

    // Collisions: static world, AI vehicles, pedestrians.
    const hit = this.collisions.resolveCar(c);
    if (hit && before * 3.6 > 4 && !this._hitCool) {
      this._hitCool = 1;
      this.audio.crash();
      if (this.mode.onCollision) this.mode.onCollision(hit);
      else if (this.inCity) this.rules.report('COLLISION_MINOR', { detail: `Швидкість удару ${Math.round(before * 3.6)} км/год` });
    }
    if (this.traffic.enabled) {
      for (const o of this.traffic.cars) {
        if (Math.abs(o.x - c.x) > 9 || Math.abs(o.z - c.z) > 9) continue;
        const h = carVsVehicle(c, o);
        if (h) {
          c.bump(h.nx, h.nz, h.depth, 0.1);
          if (!this._hitCool && (before > 0.8 || o.v > 0.8)) {
            this._hitCool = 1.5;
            o.crashT = 4;
            this.audio.crash();
            this.rules.report('COLLISION_CAR');
          }
        }
      }
      for (const p of this.peds.list) {
        if (p.state === 'down') continue;
        if (carVsPoint(c, p.x, p.z, 0.3) && Math.abs(c.v) > 0.4) {
          this.peds.knockDown(p);
          this.audio.crash();
          this.rules.report('PED_HIT');
        }
      }
    }
    if (this._hitCool) this._hitCool = Math.max(0, this._hitCool - dt);

    const b = this.net.bounds();
    this.inCity = c.x > b.minX - 80 && c.x < b.maxX + 80 && c.z > b.minZ - 80 && c.z < b.maxZ + 80;
    this.rules.enabled = this.inCity;
    const proxy = this.playerProxy();
    this.traffic.update(dt, this.t, { player: proxy, peds: this.peds });
    this.peds.update(dt, this.t, { player: proxy, traffic: this.traffic });
    this.rules.update({ car: c, t: this.t, dt, env, traffic: this.traffic, peds: this.peds, inCity: this.inCity, parking: this.city.parking });
    this.mode.update(dt);

    env.update(dt, c);
    this.city.update(this.t, { night: env.night, wet: env.wet });
    const cobble = this.rules.loc?.edge?.surface === 'cobble';
    this.cockpit.update(dt, c, env, { clock: env.clock, tempC: env.tempC, dateLabel: env.dateLabel, weatherLabel: env.w.label, cobble });
    this.audio.update(c, env, cobble);
    this.updateMarker();
    if (!render) return;
    this.cockpit.renderMirrors();
    this.renderer.render(this.scene, this.cockpit.camera);
    if (this.frameCount % 6 === 0) this.ui.updateHud();
    if (this.frameCount % 4 === 0) this.ui.drawMinimap();
  }

  get waypointPos() {
    const w = this.mode?.waypoint;
    if (!w) return null;
    if (w.node) { const n = this.net.nodes.get(w.node); return n ? { x: n.x, z: n.z } : null; }
    if (w.edge) { const e = this.net.edges.get(w.edge); return e ? this.net.point(e, e.L * w.t, 0) : null; }
    return { x: w.x, z: w.z, finish: w.finish };
  }

  updateMarker() {
    const p = this.waypointPos;
    this.marker.visible = !!p;
    if (!p) return;
    this.marker.position.set(p.x, 0, p.z);
    const col = p.finish ? 0x3ddc84 : 0x35c2ff;
    this.marker.userData.mat.color.setHex(col);
    this.marker.userData.ringMat.color.setHex(col);
    this.marker.userData.mat.opacity = 0.25 + Math.sin(this.t * 3) * 0.08;
  }

  get autodromeCenter() { return AUTODROME; }
}
