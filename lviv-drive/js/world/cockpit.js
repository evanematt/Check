import * as THREE from 'three';
import { clamp } from '../core/math.js';

// First-person interior: dashboard with live instrument cluster, steering
// wheel with the driver's hands, pedal-box era red body (as in the reference
// screenshot), working rear-view and wing mirrors, gear lever, handbrake and
// wipers. Everything is parented to `root`, which follows the car.

export const EYE = new THREE.Vector3(-0.37, 1.2, 0.08);
const COCKPIT_LAYER = 1;

function beam(p1, p2, t, mat) {
  const a = new THREE.Vector3(...p1), b = new THREE.Vector3(...p2);
  const m = new THREE.Mesh(new THREE.BoxGeometry(t, t, a.distanceTo(b)), mat);
  m.position.copy(a).add(b).multiplyScalar(0.5);
  m.lookAt(b);
  return m;
}

function flipU(geo) {
  const uv = geo.attributes.uv;
  for (let i = 0; i < uv.count; i++) uv.setX(i, 1 - uv.getX(i));
  return geo;
}

export class Cockpit {
  constructor(renderer, scene, opts = {}) {
    this.renderer = renderer;
    this.scene = scene;
    this.root = new THREE.Group();
    this.camera = new THREE.PerspectiveCamera(72, 1, 0.05, 2500);
    this.camera.position.copy(EYE);
    this.camera.layers.enable(COCKPIT_LAYER);
    this.root.add(this.camera);
    this.look = { yaw: 0, pitch: -0.06, targetYaw: 0, targetPitch: -0.06 };
    this.mirrors = opts.mirrors !== false;
    this.frame = 0;
    this._build(opts.bodyColor ?? 0xb3261e);
    scene.add(this.root);
  }

  _build(bodyColor) {
    const R = this.root;
    const body = new THREE.MeshStandardMaterial({ color: bodyColor, roughness: 0.35, metalness: 0.3 });
    const dash = new THREE.MeshStandardMaterial({ color: 0x242526, roughness: 0.85 });
    const trim = new THREE.MeshStandardMaterial({ color: 0x3a3b3d, roughness: 0.8 });
    const head = new THREE.MeshStandardMaterial({ color: 0x8a8580, roughness: 0.95 });
    const skin = new THREE.MeshStandardMaterial({ color: 0xc58c5f, roughness: 0.7 });
    const sleeve = new THREE.MeshStandardMaterial({ color: 0x2b2f36, roughness: 0.9 });
    const chrome = new THREE.MeshStandardMaterial({ color: 0xcfd3d6, roughness: 0.2, metalness: 0.9 });
    const add = (m) => { m.traverse((o) => o.layers.set(COCKPIT_LAYER)); R.add(m); return m; };

    // Bonnet (visible through the windscreen) and body shell edges.
    const hood = new THREE.Mesh(new THREE.BoxGeometry(1.72, 0.08, 1.5), body);
    hood.position.set(0, 0.93, -1.72);
    hood.rotation.x = 0.06;
    add(hood);
    const scuttle = new THREE.Mesh(new THREE.BoxGeometry(1.7, 0.06, 0.25), trim);
    scuttle.position.set(0, 0.98, -0.98);
    add(scuttle);

    // Dashboard: low top surface, cluster in a hooded binnacle, lower fascia.
    const dashTop = new THREE.Mesh(new THREE.BoxGeometry(1.62, 0.1, 0.4), dash);
    dashTop.position.set(0, 0.93, -0.8);
    add(dashTop);
    const dashFace = new THREE.Mesh(new THREE.BoxGeometry(1.62, 0.3, 0.08), dash);
    dashFace.position.set(0, 0.74, -0.5);
    add(dashFace);
    const hoodBin = new THREE.Mesh(new THREE.BoxGeometry(0.54, 0.05, 0.2), dash);
    hoodBin.position.set(-0.37, 1.13, -0.63);
    add(hoodBin);
    const backing = new THREE.Mesh(new THREE.BoxGeometry(0.54, 0.24, 0.06), dash);
    backing.position.set(-0.37, 1.0, -0.67);
    add(backing);
    const console = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.4, 0.75), trim);
    console.position.set(0.05, 0.45, -0.3);
    add(console);
    // Vents.
    for (const x of [-0.72, 0.68, -0.06, 0.16]) {
      const v = new THREE.Mesh(new THREE.BoxGeometry(0.14, 0.06, 0.02), trim);
      v.position.set(x, 0.86, -0.455);
      add(v);
    }

    // Instrument cluster (canvas, unlit so it reads at night).
    this.cluster = document.createElement('canvas');
    this.cluster.width = 640; this.cluster.height = 240;
    this.clusterTex = new THREE.CanvasTexture(this.cluster);
    this.clusterTex.colorSpace = THREE.SRGBColorSpace;
    const cl = new THREE.Mesh(new THREE.PlaneGeometry(0.46, 0.172), new THREE.MeshBasicMaterial({ map: this.clusterTex }));
    cl.position.set(-0.37, 1.02, -0.63);
    cl.rotation.x = -0.22;
    add(cl);
    // Centre display (clock, weather, cruise).
    this.center = document.createElement('canvas');
    this.center.width = 256; this.center.height = 128;
    this.centerTex = new THREE.CanvasTexture(this.center);
    this.centerTex.colorSpace = THREE.SRGBColorSpace;
    const cd = new THREE.Mesh(new THREE.PlaneGeometry(0.16, 0.08), new THREE.MeshBasicMaterial({ map: this.centerTex }));
    cd.position.set(0.06, 0.8, -0.455);
    add(cd);

    // Steering wheel with hands.
    const wheel = new THREE.Group();
    wheel.position.set(-0.37, 0.97, -0.4);
    wheel.rotation.x = -0.38;
    const column = new THREE.Mesh(new THREE.CylinderGeometry(0.035, 0.045, 0.3, 10), trim);
    column.rotation.x = Math.PI / 2;
    column.position.z = -0.15;
    wheel.add(column);
    const rotor = new THREE.Group();
    wheel.add(rotor);
    rotor.add(new THREE.Mesh(new THREE.TorusGeometry(0.19, 0.022, 10, 36), dash));
    const hub = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 0.04, 20), dash);
    hub.rotation.x = Math.PI / 2;
    rotor.add(hub);
    const badge = new THREE.Mesh(new THREE.CircleGeometry(0.03, 20), chrome);
    badge.position.z = 0.022;
    rotor.add(badge);
    for (const a of [Math.PI / 2 + 0.05, -Math.PI / 2 - 0.05, -Math.PI / 2 + 1.6]) {
      const spoke = new THREE.Mesh(new THREE.BoxGeometry(0.15, 0.03, 0.015), dash);
      spoke.position.set(Math.cos(a + Math.PI / 2) * 0.1, Math.sin(a + Math.PI / 2) * 0.1, 0);
      spoke.rotation.z = a + Math.PI / 2;
      rotor.add(spoke);
    }
    for (const side of [-1, 1]) {
      const ang = side < 0 ? Math.PI * 0.92 : Math.PI * 0.08;
      const hand = new THREE.Group();
      hand.position.set(Math.cos(ang) * 0.19, Math.sin(ang) * 0.19, 0.01);
      const palm = new THREE.Mesh(new THREE.SphereGeometry(0.045, 12, 10), skin);
      palm.scale.set(1, 1.35, 0.8);
      const thumb = new THREE.Mesh(new THREE.CapsuleGeometry(0.013, 0.04, 4, 8), skin);
      thumb.position.set(-side * 0.03, 0.035, 0.03);
      thumb.rotation.z = side * 0.9;
      const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.05, 0.42, 10), skin);
      arm.position.set(side * 0.06, -0.12, 0.17);
      arm.rotation.set(-1.0, 0, side * 0.35);
      const cuff = new THREE.Mesh(new THREE.CylinderGeometry(0.055, 0.06, 0.2, 10), sleeve);
      cuff.position.set(side * 0.11, -0.22, 0.33);
      cuff.rotation.set(-1.0, 0, side * 0.35);
      hand.add(palm, thumb, arm, cuff);
      rotor.add(hand);
    }
    add(wheel);
    this.rotor = rotor;

    // Pillars, roof, doors.
    for (const s of [-1, 1]) {
      add(beam([s * 0.8, 0.98, -0.98], [s * 0.7, 1.47, -0.3], 0.09, body));
      add(beam([s * 0.82, 1.47, -0.3], [s * 0.82, 1.47, 0.8], 0.07, body));
      add(beam([s * 0.82, 1.0, 0.7], [s * 0.82, 1.47, 0.75], 0.1, body));
      const door = new THREE.Mesh(new THREE.BoxGeometry(0.08, 0.5, 1.75), trim);
      door.position.set(s * 0.86, 0.73, -0.1);
      add(door);
      const sill = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.06, 1.75), body);
      sill.position.set(s * 0.86, 1.0, -0.1);
      add(sill);
    }
    const roof = new THREE.Mesh(new THREE.BoxGeometry(1.55, 0.04, 1.2), head);
    roof.position.set(0, 1.5, 0.25);
    add(roof);
    const header = new THREE.Mesh(new THREE.BoxGeometry(1.45, 0.06, 0.08), body);
    header.position.set(0, 1.47, -0.3);
    add(header);

    // Gear lever and handbrake.
    this.gearKnob = new THREE.Group();
    const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.014, 0.2, 8), chrome);
    stick.position.y = 0.1;
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.035, 12, 10), dash);
    knob.position.y = 0.21;
    this.gearKnob.add(stick, knob);
    this.gearKnob.position.set(0.05, 0.7, -0.28);
    add(this.gearKnob);
    this.handbrake = new THREE.Group();
    const lever = new THREE.Mesh(new THREE.BoxGeometry(0.04, 0.04, 0.3), dash);
    lever.position.z = -0.15;
    this.handbrake.add(lever);
    this.handbrake.position.set(0.05, 0.72, 0.15);
    add(this.handbrake);

    // Wipers.
    this.wipers = [];
    for (const x of [-0.55, 0.05]) {
      const w = new THREE.Group();
      const blade = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.012, 0.012), dash);
      blade.position.x = 0.27;
      w.add(blade);
      w.position.set(x, 1.0, -0.98);
      w.rotation.x = -0.9;
      add(w);
      this.wipers.push(w);
    }
    this.wipePhase = 0;

    // Mirrors with live render targets (cockpit layer is hidden from them).
    const mkTarget = (w, h) => new THREE.WebGLRenderTarget(w, h, { samples: 0 });
    this.rearRT = mkTarget(384, 104);
    this.leftRT = mkTarget(200, 130);
    this.rightRT = mkTarget(200, 130);
    const glass = (rt) => new THREE.MeshBasicMaterial({ map: rt.texture });
    const rearMirror = new THREE.Mesh(flipU(new THREE.PlaneGeometry(0.26, 0.07)), glass(this.rearRT));
    rearMirror.position.set(-0.02, 1.37, -0.6);
    rearMirror.rotation.y = -0.28;
    rearMirror.rotation.x = -0.05;
    const frame = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.085, 0.02), dash);
    frame.position.set(-0.02, 1.37, -0.612);
    frame.rotation.y = -0.28;
    add(frame); add(rearMirror);
    for (const s of [-1, 1]) {
      const rt = s < 0 ? this.leftRT : this.rightRT;
      const housing = new THREE.Mesh(new THREE.BoxGeometry(0.24, 0.15, 0.1), body);
      housing.position.set(s * 1.02, 1.07, -0.66);
      const m = new THREE.Mesh(flipU(new THREE.PlaneGeometry(0.2, 0.125)), glass(rt));
      m.position.set(s * 1.02, 1.07, -0.605);
      m.rotation.y = s * 0.3;
      const arm = beam([s * 0.85, 1.02, -0.72], [s * 0.95, 1.05, -0.68], 0.04, body);
      add(housing); add(m); add(arm);
    }
    const mkCam = (fov, aspect, pos, yaw) => {
      const c = new THREE.PerspectiveCamera(fov, aspect, 0.3, 400);
      c.position.set(...pos);
      c.rotation.y = yaw;
      R.add(c);
      return c;
    };
    this.rearCam = mkCam(22, 384 / 104, [0, 1.35, 2.4], Math.PI);
    this.leftCam = mkCam(38, 200 / 130, [-1.05, 1.07, -0.5], Math.PI - 0.22);
    this.rightCam = mkCam(38, 200 / 130, [1.05, 1.07, -0.5], Math.PI + 0.22);

    // Dim cabin light so hands and controls stay readable at night.
    this.cabinLight = new THREE.PointLight(0xffe2c4, 0, 2.4, 2);
    this.cabinLight.position.set(-0.2, 1.35, -0.1);
    R.add(this.cabinLight);

    // Headlights.
    this.headlight = new THREE.SpotLight(0xfff3dd, 0, 80, 0.5, 0.55, 1.1);
    this.headlight.position.set(0, 0.75, -2.2);
    this.headlight.target.position.set(0, 0, -30);
    R.add(this.headlight, this.headlight.target);
  }

  setAspect(a) {
    this.camera.aspect = a;
    this.camera.updateProjectionMatrix();
  }

  update(dt, car, env, info) {
    this.root.position.set(car.x, 0, car.z);
    this.root.rotation.y = car.h;
    // Head look (mouse drag / keys 1,2,3), with a little road buzz on cobbles.
    const L = this.look;
    L.yaw += (L.targetYaw - L.yaw) * Math.min(1, dt * 8);
    L.pitch += (L.targetPitch - L.pitch) * Math.min(1, dt * 8);
    const buzz = info.cobble ? Math.min(0.006, car.speedKmh * 0.00015) : car.speedKmh * 0.00002;
    this.camera.rotation.set(L.pitch + (Math.random() - 0.5) * buzz, L.yaw, 0, 'YXZ');
    this.camera.position.set(EYE.x + Math.sin(L.yaw) * -0.08, EYE.y + (Math.random() - 0.5) * buzz * 0.4, EYE.z);

    this.rotor.rotation.z = -car.steer * 2.4;
    // Gear lever: H-pattern for manual, PRND gate for automatic.
    if (car.transmission === 'manual') {
      const pos = { '-1': [0.04, 0.05], 0: [0, 0], 1: [-0.04, -0.05], 2: [-0.04, 0.05], 3: [0, -0.05], 4: [0, 0.05], 5: [0.04, -0.05] }[car.gear];
      this.gearKnob.rotation.set(pos[1] * 4, 0, -pos[0] * 4);
    } else {
      const k = { P: -0.25, R: -0.1, N: 0, D: 0.18 }[car.selector];
      this.gearKnob.rotation.set(k, 0, 0);
    }
    this.handbrake.rotation.x = car.handbrake ? 0.45 : 0;
    // Wipers.
    const speed = [0, 0.55, 1.1, 1.8][car.wipers];
    if (speed > 0 || this.wipePhase % (Math.PI * 2) > 0.05) this.wipePhase += dt * Math.max(speed, 1.1) * 3;
    const a = Math.max(0, Math.sin(this.wipePhase)) * 1.6;
    for (const w of this.wipers) w.rotation.z = a;
    this.cabinLight.intensity = env.night * 0.9;
    // Headlights.
    const hl = car.lights >= 2 ? (car.lights === 3 ? 2.6 : 1.4) : 0;
    this.headlight.intensity = hl * (0.4 + env.night * 2.2) * 40;
    this.headlight.distance = car.lights === 3 ? 140 : 70;
    this.headlight.angle = car.lights === 3 ? 0.42 : 0.55;

    this.frame++;
    if (this.frame % 4 === 0) this.drawCluster(car, info);
    if (this.frame % 30 === 0) this.drawCenter(info);
  }

  renderMirrors() {
    if (!this.mirrors) return;
    const r = this.renderer;
    const which = this.frame % 3;
    const [cam, rt] = which === 0 ? [this.rearCam, this.rearRT] : which === 1 ? [this.leftCam, this.leftRT] : [this.rightCam, this.rightRT];
    const prev = r.getRenderTarget();
    r.setRenderTarget(rt);
    r.render(this.scene, cam);
    r.setRenderTarget(prev);
  }

  drawCluster(car, info) {
    const c = this.cluster.getContext('2d');
    const W = 640, H = 240;
    c.fillStyle = '#0b0c0e'; c.fillRect(0, 0, W, H);
    const gauge = (cx, cy, r, max, value, step, label, red) => {
      c.save();
      c.translate(cx, cy);
      c.strokeStyle = '#2a2c30'; c.lineWidth = 3;
      c.beginPath(); c.arc(0, 0, r, 0, Math.PI * 2); c.stroke();
      const a0 = Math.PI * 0.75, a1 = Math.PI * 2.25;
      const ang = (v) => a0 + (a1 - a0) * clamp(v / max, 0, 1);
      if (red) { c.strokeStyle = '#c0261d'; c.lineWidth = 8; c.beginPath(); c.arc(0, 0, r - 8, ang(red), a1); c.stroke(); }
      c.fillStyle = '#e9edf2'; c.strokeStyle = '#e9edf2';
      c.font = 'bold 15px Arial'; c.textAlign = 'center'; c.textBaseline = 'middle';
      for (let v = 0; v <= max + 1e-6; v += step / 2) {
        const a = ang(v), major = Math.abs(v / step - Math.round(v / step)) < 1e-6;
        c.lineWidth = major ? 3 : 1.5;
        c.beginPath();
        c.moveTo(Math.cos(a) * (r - (major ? 16 : 10)), Math.sin(a) * (r - (major ? 16 : 10)));
        c.lineTo(Math.cos(a) * (r - 3), Math.sin(a) * (r - 3));
        c.stroke();
        if (major) c.fillText(String(Math.round(v * (max > 20 ? 1 : 1))), Math.cos(a) * (r - 30), Math.sin(a) * (r - 30));
      }
      c.font = '12px Arial'; c.fillStyle = '#9aa3ad'; c.fillText(label, 0, r * 0.45);
      const a = ang(value);
      c.strokeStyle = '#ff5a1f'; c.lineWidth = 4;
      c.beginPath(); c.moveTo(-Math.cos(a) * 12, -Math.sin(a) * 12); c.lineTo(Math.cos(a) * (r - 12), Math.sin(a) * (r - 12)); c.stroke();
      c.fillStyle = '#333'; c.beginPath(); c.arc(0, 0, 9, 0, Math.PI * 2); c.fill();
      c.restore();
    };
    gauge(128, 122, 108, 220, car.speedKmh, 20, 'км/год');
    gauge(512, 122, 108, 8, car.rpm / 1000, 1, 'об/хв ×1000', 6.5);

    // Centre LCD.
    c.fillStyle = '#10161c'; c.fillRect(250, 40, 140, 160);
    c.fillStyle = '#7fe0ff'; c.textAlign = 'center';
    c.font = 'bold 40px Arial'; c.fillText(car.gearLabel, 320, 92);
    c.font = '13px Arial';
    c.fillText(`${car.odometer.toFixed(1)} км`, 320, 128);
    c.fillText(`Trip ${(car.trip / 1000).toFixed(2)}`, 320, 146);
    c.fillText(`${info.clock}  ${info.tempC}°C`, 320, 164);
    if (car.cruise) c.fillText(`CRUISE ${car.cruise}`, 320, 184);
    else if (car.limiter) c.fillText(`LIMIT ${car.limiter}`, 320, 184);
    // Fuel & coolant bars.
    const bar = (x, frac, col, label) => {
      c.fillStyle = '#222'; c.fillRect(x, 210, 90, 10);
      c.fillStyle = col; c.fillRect(x, 210, 90 * clamp(frac, 0, 1), 10);
      c.fillStyle = '#9aa3ad'; c.font = '11px Arial'; c.textAlign = 'left'; c.fillText(label, x, 232);
    };
    bar(255, car.fuel, car.fuel < 0.12 ? '#ffb300' : '#7fe0ff', 'Пальне');
    bar(30, (car.temp - 40) / 80, car.temp > 110 ? '#ff3b30' : '#7fe0ff', `${Math.round(car.temp)}°`);

    // Warning and indicator lamps.
    const lamps = [];
    const blink = car.blinkOn;
    const ind = car.hazard ? 'both' : car.indicator;
    if ((ind === 'left' || ind === 'both') && blink) lamps.push(['◀', '#2bd96b', 214, 22]);
    if ((ind === 'right' || ind === 'both') && blink) lamps.push(['▶', '#2bd96b', 426, 22]);
    if (car.lights === 3) lamps.push(['HI', '#3b82ff', 250, 22]);
    else if (car.lights === 2) lamps.push(['LO', '#2bd96b', 250, 22]);
    else if (car.lights === 1) lamps.push(['≡', '#2bd96b', 250, 22]);
    if (car.fogRear) lamps.push(['FOG', '#ffb300', 290, 22]);
    if (car.handbrake) lamps.push(['(P)', '#ff3b30', 350, 22]);
    if (!car.belt) lamps.push(['BELT', '#ff3b30', 392, 22]);
    if (!car.engineOn) { lamps.push(['OIL', '#ff3b30', 290, 220 - 200]); lamps.push(['BAT', '#ff3b30', 330, 220 - 200]); }
    if (car.fuel < 0.12) lamps.push(['FUEL', '#ffb300', 560, 222]);
    if (car.faults.brakes) lamps.push(['(!)', '#ff3b30', 600, 22]);
    c.font = 'bold 15px Arial'; c.textAlign = 'center';
    for (const [t, col, x, y] of lamps) { c.fillStyle = col; c.fillText(t, x, y); }
    this.clusterTex.needsUpdate = true;
  }

  drawCenter(info) {
    const c = this.center.getContext('2d');
    c.fillStyle = '#06090c'; c.fillRect(0, 0, 256, 128);
    c.fillStyle = '#d6f3ff'; c.textAlign = 'center';
    c.font = 'bold 40px Arial'; c.fillText(info.clock, 128, 52);
    c.font = '18px Arial'; c.fillText(`${info.dateLabel}  ${info.tempC}°C`, 128, 84);
    c.fillStyle = '#7fe0ff'; c.fillText(info.weatherLabel, 128, 110);
    this.centerTex.needsUpdate = true;
  }
}
