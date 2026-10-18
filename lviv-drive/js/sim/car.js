import { clamp, fwd, KMH } from '../core/math.js';

// Player vehicle: a bicycle-model chassis with an engine, a manual or
// automatic gearbox, clutch, brakes, parking brake and the full set of
// controls a learner needs (lights, indicators, hazards, wipers, horn,
// seatbelt, cruise control and speed limiter).

const MASS = 1350;
const WHEELBASE = 2.62;
const WHEEL_R = 0.31;
const FINAL = 3.9;
const RATIOS = [0, 3.6, 2.1, 1.4, 1.05, 0.82];
const REVERSE = 3.4;
const IDLE = 800;
const REDLINE = 6500;
const G = 9.81;

export const CAR_DIM = { length: 4.5, width: 1.8 };

function torque(rpm) {
  const k = (rpm - 4000) / 4500;
  return Math.max(55, 150 * (1 - k * k));
}

export class Car {
  constructor() {
    this.listeners = [];
    this.reset(0, 0, 0);
    this.transmission = 'manual';
  }

  on(fn) { this.listeners.push(fn); }
  emit(type, data) { for (const fn of this.listeners) fn(type, data); }

  reset(x, z, h) {
    this.x = x; this.z = z; this.h = h;
    this.v = 0;
    this.yawRate = 0;
    this.steer = 0;
    this.wheelAngle = 0;
    this.engineOn = false;
    this.cranking = 0;
    this.rpm = 0;
    this.gear = 0; // manual: -1 R, 0 N, 1..5 ; auto uses `selector`
    this.selector = 'P';
    this.autoGear = 1;
    this.clutch = 0;
    this.throttle = 0;
    this.brake = 0;
    this.handbrake = true;
    this.lights = 0; // 0 off, 1 side lights, 2 dipped, 3 main beam
    this.fogFront = false;
    this.fogRear = false;
    this.indicator = null;
    this.indicatorSince = 0;
    this.hazard = false;
    this.wipers = 0;
    this.belt = false;
    this.horn = false;
    this.limiter = null;
    this.cruise = null;
    this.fuel = 0.64;
    this.temp = 18;
    this.odometer = 48213.4;
    this.trip = 0;
    this._steerPeak = 0;
    this.time = 0;
    this.faults = {};
  }

  get speedKmh() { return Math.abs(this.v) * KMH; }
  get forward() { return fwd(this.h); }
  get reversing() {
    return this.transmission === 'auto' ? this.selector === 'R' : this.gear === -1;
  }
  get inGear() {
    if (this.transmission === 'auto') return this.selector === 'D' || this.selector === 'R';
    return this.gear !== 0;
  }
  get gearLabel() {
    if (this.transmission === 'auto') return this.selector === 'D' ? `D${this.autoGear}` : this.selector;
    return this.gear === -1 ? 'R' : this.gear === 0 ? 'N' : String(this.gear);
  }
  get blinkOn() { return Math.floor(this.time * 2.6) % 2 === 0; }

  // ---- discrete controls ----------------------------------------------
  toggleEngine() {
    if (this.engineOn) {
      this.engineOn = false;
      this.cranking = 0;
      this.emit('engine', false);
      return;
    }
    const ok = this.transmission === 'auto'
      ? this.selector === 'P' || this.selector === 'N'
      : this.gear === 0 || this.clutch > 0.5;
    if (!ok) {
      this.emit('hint', this.transmission === 'auto'
        ? 'Для запуску переведіть селектор у P або N'
        : 'Для запуску вичавіть зчеплення (Shift) або увімкніть нейтраль');
      return;
    }
    if (this.fuel <= 0) { this.emit('hint', 'Закінчилося пальне'); return; }
    this.cranking = 0.9;
    this.emit('crank');
  }

  shift(delta) {
    if (this.transmission === 'auto') {
      const order = ['P', 'R', 'N', 'D'];
      const i = clamp(order.indexOf(this.selector) + delta, 0, 3);
      this.setSelector(order[i]);
      return;
    }
    if (this.clutch < 0.5) {
      this.emit('hint', 'Вичавіть зчеплення (Shift), щоб перемкнути передачу');
      this.emit('grind');
      return;
    }
    const next = clamp(this.gear + delta, -1, 5);
    if (next === -1 && this.v > 1) { this.emit('hint', 'Задню передачу вмикайте лише після повної зупинки'); return; }
    this.gear = next;
    this.emit('shift', this.gearLabel);
  }

  setSelector(s) {
    if (this.transmission !== 'auto') return;
    if ((s === 'P' || s === 'R') && Math.abs(this.v) > 1) {
      this.emit('hint', 'Зупиніться, перш ніж вмикати P або R');
      return;
    }
    if (s !== 'P' && this.selector === 'P' && this.brake < 0.2 && this.engineOn) {
      this.emit('hint', 'Натисніть гальмо (S), щоб вийти з режиму P');
      return;
    }
    this.selector = s;
    this.autoGear = 1;
    this.emit('shift', this.gearLabel);
  }

  setTransmission(mode) {
    this.transmission = mode;
    this.gear = 0;
    this.selector = 'P';
    this.autoGear = 1;
  }

  toggleIndicator(side) {
    if (this.indicator === side) this.indicator = null;
    else { this.indicator = side; this.indicatorSince = this.time; this._steerPeak = 0; }
    this.emit('indicator', this.indicator);
  }
  toggleHazard() { this.hazard = !this.hazard; }
  cycleLights() { this.lights = this.lights >= 2 ? 0 : this.lights + 1; }
  toggleHighBeam() { this.lights = this.lights === 3 ? 2 : 3; }
  cycleWipers() { this.wipers = (this.wipers + 1) % 4; }
  toggleBelt() { this.belt = !this.belt; this.emit('belt', this.belt); }
  toggleHandbrake() { this.handbrake = !this.handbrake; this.emit('handbrake', this.handbrake); }
  toggleFogRear() { this.fogRear = !this.fogRear; }
  toggleFogFront() { this.fogFront = !this.fogFront; }
  toggleLimiter(limitKmh) {
    this.cruise = null;
    this.limiter = this.limiter ? null : limitKmh;
    this.emit('hint', this.limiter ? `Обмежувач швидкості: ${this.limiter} км/год` : 'Обмежувач вимкнено');
  }
  toggleCruise() {
    this.limiter = null;
    if (this.cruise || this.speedKmh < 30) {
      this.cruise = null;
      this.emit('hint', this.speedKmh < 30 && !this.cruise ? 'Круїз-контроль вмикається від 30 км/год' : 'Круїз-контроль вимкнено');
    } else {
      this.cruise = Math.round(this.speedKmh);
      this.emit('hint', `Круїз-контроль: ${this.cruise} км/год`);
    }
  }

  // ---- simulation -------------------------------------------------------
  update(dt, input, env) {
    this.time += dt;
    const mu = env.mu ?? 1;

    // Pedals and steering smoothing.
    this.clutch += ((input.clutch ? 1 : 0) - this.clutch) * Math.min(1, dt * 9);
    let throttle = clamp(input.throttle, 0, 1);
    this.brake = clamp(input.brake, 0, 1);
    if (this.brake > 0.1 && this.cruise) { this.cruise = null; this.emit('hint', 'Круїз-контроль вимкнено гальмом'); }
    if (this.cruise) throttle = Math.max(throttle, clamp((this.cruise - this.speedKmh) * 0.12 + 0.25, 0, 0.8));
    if (this.limiter && this.speedKmh >= this.limiter - 0.5) throttle = 0;
    this.throttle = throttle;

    const target = clamp(input.steer, -1, 1);
    const rate = Math.abs(target) > Math.abs(this.steer) ? 2.6 : 4.2;
    this.steer += (target - this.steer) * Math.min(1, dt * rate);
    const vAbs = Math.abs(this.v);
    const maxWheel = 0.62 / (1 + (vAbs / 13) ** 2 * 1.2);
    this.wheelAngle = this.steer * maxWheel;

    // Indicator self-cancel after the wheel unwinds from a turn.
    if (this.indicator) {
      const dirSign = this.indicator === 'right' ? 1 : -1;
      this._steerPeak = Math.max(this._steerPeak, this.steer * dirSign);
      if (this._steerPeak > 0.45 && this.steer * dirSign < 0.08) {
        this.indicator = null;
        this.emit('indicator', null);
      }
    }

    // Engine start / stall / temperature / fuel.
    if (this.cranking > 0) {
      this.cranking -= dt;
      this.rpm = 250;
      if (this.cranking <= 0) { this.engineOn = true; this.rpm = IDLE; this.emit('engine', true); }
    }
    if (this.engineOn) {
      this.temp += (90 - this.temp) * dt * 0.012;
      this.fuel = Math.max(0, this.fuel - dt * (0.000012 + throttle * (this.rpm / REDLINE) * 0.00011));
      if (this.fuel <= 0) { this.engineOn = false; this.emit('hint', 'Закінчилося пальне'); }
    } else {
      this.temp += (18 - this.temp) * dt * 0.002;
      if (this.cranking <= 0) this.rpm = Math.max(0, this.rpm - dt * 3000);
    }

    // Drivetrain.
    let driveForce = 0;
    let engineBrake = 0;
    const reverse = this.reversing;
    let ratio = 0;
    let engaged = false;
    if (this.transmission === 'manual') {
      ratio = this.gear === -1 ? REVERSE : RATIOS[Math.max(0, this.gear)];
      engaged = this.gear !== 0 && this.clutch < 0.5;
    } else {
      if (this.selector === 'D') ratio = RATIOS[this.autoGear];
      if (this.selector === 'R') ratio = REVERSE;
      engaged = ratio > 0;
    }
    const wheelRpm = (vAbs / WHEEL_R) * ratio * FINAL * 60 / (2 * Math.PI);
    if (this.engineOn) {
      if (!engaged) {
        const free = IDLE + throttle * (REDLINE - IDLE);
        this.rpm += (free - this.rpm) * Math.min(1, dt * 5);
      } else if (this.transmission === 'manual') {
        if (wheelRpm >= IDLE * 0.95) {
          this.rpm = Math.min(wheelRpm, REDLINE + 200);
          const t = this.rpm > REDLINE ? 0 : torque(this.rpm) * throttle;
          driveForce = (t * ratio * FINAL * 0.88) / WHEEL_R;
          if (throttle < 0.05) engineBrake = ((this.rpm - IDLE) / 5000) * 55 * ratio * FINAL / WHEEL_R;
        } else if (this.gear <= 2 && this.gear !== 0 && throttle > 0.12) {
          // Clutch slip while pulling away.
          this.rpm += (IDLE + 300 + throttle * 1800 - this.rpm) * Math.min(1, dt * 6);
          driveForce = (torque(this.rpm) * throttle * 0.85 * ratio * FINAL * 0.88) / WHEEL_R;
        } else if (this.gear <= 2 && this.gear !== 0 && wheelRpm > 350) {
          this.rpm = IDLE; // idle creep in first / second / reverse
          driveForce = (45 * ratio * FINAL) / WHEEL_R;
        } else {
          this.engineOn = false;
          this.rpm = 0;
          this.emit('stall');
        }
      } else {
        // Automatic with torque converter.
        const conv = Math.max(wheelRpm, IDLE + throttle * 2200);
        this.rpm += (Math.min(conv, REDLINE) - this.rpm) * Math.min(1, dt * 6);
        const mult = 1 + Math.max(0, 1 - wheelRpm / 2200) * 0.9;
        const t = this.selector === 'D' || this.selector === 'R' ? Math.max(torque(this.rpm) * throttle, throttle < 0.05 ? 32 : 0) : 0;
        driveForce = (t * mult * ratio * FINAL * 0.86) / WHEEL_R;
        if (throttle < 0.05 && wheelRpm > IDLE * 1.5) engineBrake = ((wheelRpm - IDLE) / 6000) * 35 * ratio * FINAL / WHEEL_R;
        if (this.selector === 'D') {
          const up = 2300 + throttle * 2900;
          if (this.rpm > up && this.autoGear < 5) { this.autoGear++; this.emit('shift', this.gearLabel); }
          else if (this.rpm < 1250 && this.autoGear > 1) { this.autoGear--; }
        }
      }
    }
    if (this.faults.brakes) this.brake *= 0.45;

    // Longitudinal dynamics.
    const dirSign = reverse ? -1 : 1;
    const rolling = 0.012 * MASS * G;
    const drag = 0.5 * 1.2 * 0.66 * this.v * this.v;
    const brakeForce = Math.min(this.brake * 11000, mu * MASS * G * 0.95);
    const hand = this.handbrake ? Math.min(4200, mu * MASS * G * 0.5) : 0;
    const parked = this.transmission === 'auto' && this.selector === 'P';
    let a = (driveForce * dirSign) / MASS;
    const resist = (brakeForce + hand + rolling + engineBrake) / MASS + drag / MASS;
    if (parked) this.v = 0;
    else if (Math.abs(this.v) < 0.05 && Math.abs(a) * MASS <= brakeForce + hand + rolling) {
      this.v = 0; // static friction holds the car
    } else {
      const sign = Math.abs(this.v) > 0.05 ? Math.sign(this.v) : Math.sign(a);
      const next = this.v + (a - sign * resist) * dt;
      this.v = Math.sign(next) !== sign && Math.abs(this.v) > 0 && Math.abs(a) < resist ? 0 : next;
    }
    this.v = clamp(this.v, -9, 60);

    // Lateral: kinematic bicycle model with a grip limit (understeer).
    let yaw = (-this.v / WHEELBASE) * Math.tan(this.wheelAngle);
    const maxYaw = (mu * G * 0.92) / Math.max(1, vAbs);
    if (Math.abs(yaw) > maxYaw) { yaw = Math.sign(yaw) * maxYaw; this.sliding = true; } else this.sliding = false;
    if (this.handbrake && vAbs > 6) yaw *= 1.35;
    this.yawRate = yaw;
    this.h += yaw * dt;
    const f = fwd(this.h);
    this.x += f.x * this.v * dt;
    this.z += f.z * this.v * dt;
    const dist = Math.abs(this.v) * dt;
    this.odometer += dist / 1000;
    this.trip += dist;
  }

  // Collision response from the world: push out and kill speed.
  bump(nx, nz, depth, keep = 0.25) {
    this.x += nx * depth;
    this.z += nz * depth;
    const f = fwd(this.h);
    const into = -(f.x * nx + f.z * nz) * Math.sign(this.v || 1);
    if (into > 0.2) this.v *= keep;
  }
}
