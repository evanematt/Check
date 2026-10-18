import * as THREE from 'three';
import { clamp, lerp } from '../core/math.js';

// The "4th dimension": time of day and weather. Both change lighting,
// visibility, road grip and which rules apply (e.g. dipped headlights).

export const WEATHER = {
  clear: { label: 'Ясно', fog: 0.0012, mu: 1.0, wet: 0, needLights: false, tempC: 12 },
  cloudy: { label: 'Хмарно', fog: 0.0022, mu: 1.0, wet: 0, needLights: false, tempC: 10 },
  rain: { label: 'Дощ', fog: 0.006, mu: 0.7, wet: 1, needLights: true, tempC: 9 },
  fog: { label: 'Туман', fog: 0.028, mu: 0.9, wet: 0.3, needLights: true, tempC: 7 },
  snow: { label: 'Сніг', fog: 0.012, mu: 0.4, wet: 0.6, needLights: true, tempC: -3 },
};

// Lviv, mid-October: sunrise ≈ 07:35, sunset ≈ 18:15 (local time).
const SUNRISE = 7.6, SUNSET = 18.25;
const SKY = {
  day: new THREE.Color(0x9cc6ec), dusk: new THREE.Color(0xe59a6a), night: new THREE.Color(0x0a0f1d),
  overcast: new THREE.Color(0xa7adb3), fog: new THREE.Color(0xb9bcbf),
};

export class Environment {
  constructor(scene, opts = {}) {
    this.scene = scene;
    this.hour = opts.hour ?? 11;
    this.timeScale = 0; // game minutes per real second (0 = frozen)
    this.weather = opts.weather ?? 'clear';
    this.date = new Date(2026, 9, 18);
    scene.fog = new THREE.FogExp2(0xffffff, 0.002);
    this.hemi = new THREE.HemisphereLight(0xdbe9ff, 0x4d4a42, 0.9);
    this.sun = new THREE.DirectionalLight(0xfff1dc, 2.2);
    this.sun.castShadow = opts.shadows !== false;
    this.sun.shadow.mapSize.set(2048, 2048);
    const sc = this.sun.shadow.camera;
    sc.left = -70; sc.right = 70; sc.top = 70; sc.bottom = -70; sc.near = 1; sc.far = 400;
    this.sun.shadow.bias = -0.0006;
    scene.add(this.hemi, this.sun, this.sun.target);
    this._initPrecip();
    this.apply();
  }

  _initPrecip() {
    const N = 4000;
    const pos = new Float32Array(N * 6);
    for (let i = 0; i < N; i++) {
      const x = (Math.random() - 0.5) * 70, y = Math.random() * 30, z = (Math.random() - 0.5) * 70;
      pos.set([x, y, z, x, y - 0.6, z], i * 6);
    }
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    this.rain = new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: 0xaab8c8, transparent: true, opacity: 0.5 }));
    this.rain.frustumCulled = false;
    const sp = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) sp.set([(Math.random() - 0.5) * 70, Math.random() * 30, (Math.random() - 0.5) * 70], i * 3);
    const sg = new THREE.BufferGeometry();
    sg.setAttribute('position', new THREE.BufferAttribute(sp, 3));
    this.snow = new THREE.Points(sg, new THREE.PointsMaterial({ color: 0xffffff, size: 0.09 }));
    this.snow.frustumCulled = false;
    this.scene.add(this.rain, this.snow);
  }

  get w() { return WEATHER[this.weather]; }

  // 0 at full day, 1 at full night.
  get night() {
    const h = this.hour;
    const fromRise = h - SUNRISE, toSet = SUNSET - h;
    const d = Math.min(fromRise, toSet);
    return clamp(1 - (d + 0.6) / 1.2, 0, 1);
  }

  get dusk() {
    const h = this.hour;
    return clamp(1 - Math.min(Math.abs(h - SUNRISE), Math.abs(h - SUNSET)) / 1.0, 0, 1);
  }

  get mu() { return this.w.mu; }
  get wet() { return this.w.wet; }

  get needLights() {
    return this.night > 0.35 || this.w.needLights;
  }

  get needLightsReason() {
    if (this.night > 0.35) return 'Темна пора доби';
    if (this.w.needLights) return `Недостатня видимість: ${this.w.label.toLowerCase()}`;
    return '';
  }

  get clock() {
    const h = Math.floor(this.hour) % 24, m = Math.floor((this.hour % 1) * 60);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
  }

  get tempC() { return Math.round(this.w.tempC - this.night * 4); }
  get dateLabel() { return '18.10.2026'; }

  set(hour, weather) {
    if (hour !== undefined && hour !== null) this.hour = ((hour % 24) + 24) % 24;
    if (weather) this.weather = weather;
    this.apply();
  }

  apply() {
    const n = this.night, dusk = this.dusk, w = this.w;
    const sky = SKY.day.clone();
    if (this.weather !== 'clear') sky.lerp(this.weather === 'fog' ? SKY.fog : SKY.overcast, 0.75);
    sky.lerp(SKY.dusk, dusk * 0.55 * (this.weather === 'clear' ? 1 : 0.4));
    sky.lerp(SKY.night, n);
    this.scene.background = sky;
    this.scene.fog.color.copy(sky);
    this.scene.fog.density = w.fog * (1 + n * 0.6);
    // Sun path: east -> south -> west.
    const k = clamp((this.hour - SUNRISE) / (SUNSET - SUNRISE), 0, 1);
    const elev = Math.sin(k * Math.PI) * 0.62;
    const az = lerp(-Math.PI / 2, Math.PI / 2, k);
    this.sunDir = new THREE.Vector3(Math.sin(az) * Math.cos(elev), Math.max(0.05, Math.sin(elev)), Math.cos(az) * Math.cos(elev));
    const overcast = this.weather === 'clear' ? 1 : this.weather === 'cloudy' ? 0.55 : 0.35;
    this.sun.intensity = (1 - n) * 2.4 * overcast;
    this.sun.color.setHSL(0.09, 0.6, lerp(0.92, 0.7, dusk));
    this.hemi.intensity = lerp(1.0, 0.12, n) * (0.7 + overcast * 0.3);
    this.rain.visible = this.weather === 'rain';
    this.snow.visible = this.weather === 'snow';
  }

  update(dt, focus) {
    if (this.timeScale) {
      this.hour = (this.hour + (dt * this.timeScale) / 60) % 24;
      this.apply();
    }
    this.sun.position.set(focus.x + this.sunDir.x * 150, this.sunDir.y * 150, focus.z + this.sunDir.z * 150);
    this.sun.target.position.set(focus.x, 0, focus.z);
    for (const p of [this.rain, this.snow]) {
      if (!p.visible) continue;
      const arr = p.geometry.attributes.position.array;
      const isRain = p === this.rain;
      const fall = (isRain ? 18 : 1.6) * dt;
      const stride = isRain ? 6 : 3;
      for (let i = 0; i < arr.length; i += stride) {
        arr[i + 1] -= fall;
        if (isRain) arr[i + 4] -= fall;
        else arr[i] += Math.sin((arr[i + 1] + i) * 0.7) * dt * 0.4;
        if (arr[i + 1] < 0) {
          arr[i + 1] += 30;
          if (isRain) arr[i + 4] += 30;
        }
      }
      p.geometry.attributes.position.needsUpdate = true;
      p.position.set(focus.x, 0, focus.z);
    }
  }
}
