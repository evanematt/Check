// Synthesised sounds (no audio assets): engine, indicator relay, horn,
// tyre squeal, rain, plus UI cues for errors and successes. The examiner
// speaks through the Web Speech API when a Ukrainian voice is available.

export class Audio {
  constructor() {
    this.ctx = null;
    this.muted = false;
    this.voice = true;
    this._lastTick = false;
  }

  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    const ctx = (this.ctx = new AC());
    this.master = ctx.createGain();
    this.master.gain.value = 0.7;
    this.master.connect(ctx.destination);

    // Engine: two detuned oscillators through a low-pass filter.
    this.engFilter = ctx.createBiquadFilter();
    this.engFilter.type = 'lowpass';
    this.engFilter.frequency.value = 600;
    this.engGain = ctx.createGain();
    this.engGain.gain.value = 0;
    this.engFilter.connect(this.engGain).connect(this.master);
    this.osc1 = ctx.createOscillator(); this.osc1.type = 'sawtooth';
    this.osc2 = ctx.createOscillator(); this.osc2.type = 'square';
    const g2 = ctx.createGain(); g2.gain.value = 0.35;
    this.osc1.connect(this.engFilter);
    this.osc2.connect(g2).connect(this.engFilter);
    this.osc1.start(); this.osc2.start();

    // Noise source shared by rain, tyres and cobbles.
    const buf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    const mkNoise = (type, freq, q) => {
      const src = ctx.createBufferSource();
      src.buffer = buf; src.loop = true;
      const f = ctx.createBiquadFilter(); f.type = type; f.frequency.value = freq; f.Q.value = q;
      const g = ctx.createGain(); g.gain.value = 0;
      src.connect(f).connect(g).connect(this.master);
      src.start();
      return g;
    };
    this.rainGain = mkNoise('highpass', 1800, 0.5);
    this.squealGain = mkNoise('bandpass', 2400, 8);
    this.roadGain = mkNoise('lowpass', 300, 1);

    // Horn.
    this.hornGain = ctx.createGain(); this.hornGain.gain.value = 0;
    this.hornGain.connect(this.master);
    for (const f of [415, 494]) {
      const o = ctx.createOscillator(); o.type = 'square'; o.frequency.value = f;
      const g = ctx.createGain(); g.gain.value = 0.08;
      o.connect(g).connect(this.hornGain); o.start();
    }
  }

  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }

  setMuted(m) {
    this.muted = m;
    if (this.master) this.master.gain.value = m ? 0 : 0.7;
  }

  update(car, env, onCobble) {
    if (!this.ctx) return;
    const t = this.ctx.currentTime;
    const rpm = car.engineOn ? car.rpm : car.cranking > 0 ? 260 : 0;
    const f = Math.max(20, rpm / 30);
    this.osc1.frequency.setTargetAtTime(f, t, 0.03);
    this.osc2.frequency.setTargetAtTime(f * 0.5 + 1.5, t, 0.03);
    this.engFilter.frequency.setTargetAtTime(300 + car.throttle * 1400 + rpm * 0.15, t, 0.05);
    const vol = car.engineOn || car.cranking > 0 ? 0.045 + car.throttle * 0.07 : 0;
    this.engGain.gain.setTargetAtTime(vol, t, 0.08);
    this.hornGain.gain.setTargetAtTime(car.horn ? 1 : 0, t, 0.01);
    this.rainGain.gain.setTargetAtTime(env.weather === 'rain' ? 0.06 : 0, t, 0.5);
    this.squealGain.gain.setTargetAtTime(car.sliding && car.speedKmh > 15 ? 0.05 : 0, t, 0.05);
    const road = Math.min(1, car.speedKmh / 60) * (onCobble ? 0.16 : 0.05);
    this.roadGain.gain.setTargetAtTime(road, t, 0.2);
    // Indicator relay click on each blink edge.
    const blinking = car.indicator || car.hazard;
    const on = blinking && car.blinkOn;
    if (blinking && on !== this._lastTick) this.click(on ? 1800 : 1200, 0.025);
    this._lastTick = on;
  }

  click(freq = 1500, len = 0.02) { this.tone(freq, len, 'square', 0.05); }

  tone(freq, len, type = 'sine', vol = 0.12, when = 0) {
    if (!this.ctx) return;
    const t = this.ctx.currentTime + when;
    const o = this.ctx.createOscillator();
    const g = this.ctx.createGain();
    o.type = type; o.frequency.value = freq;
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + len);
    o.connect(g).connect(this.master);
    o.start(t); o.stop(t + len + 0.02);
  }

  error() { this.tone(660, 0.18, 'square', 0.08); this.tone(440, 0.3, 'square', 0.08, 0.16); }
  critical() { this.tone(330, 0.6, 'sawtooth', 0.1); this.tone(220, 0.8, 'sawtooth', 0.1, 0.25); }
  success() { this.tone(660, 0.15, 'sine', 0.1); this.tone(880, 0.25, 'sine', 0.1, 0.12); }
  crash() {
    if (!this.ctx) return;
    this.tone(90, 0.5, 'sawtooth', 0.2);
    this.tone(60, 0.7, 'square', 0.15, 0.05);
  }

  say(text) {
    if (!this.voice || this.muted || !window.speechSynthesis) return;
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'uk-UA';
    const v = speechSynthesis.getVoices().find((x) => x.lang && x.lang.startsWith('uk'));
    if (v) u.voice = v;
    u.rate = 1.02;
    speechSynthesis.cancel();
    speechSynthesis.speak(u);
  }
}
