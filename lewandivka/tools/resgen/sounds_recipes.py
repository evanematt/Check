"""Recipes: one function per sound event id (see core ModSounds). Each returns a mono float array at SR."""
from __future__ import annotations

import math

import numpy as np

from .sounds_synth import *  # noqa: F401,F403
from .sounds_synth import RECIPES, SR, snd, rng, tarr


def _g(sound_id, v):
    return rng(sound_id, v)


# ------------------------------------------------------------------------------------------------ tram
@snd("tram.bell")
def tram_bell(v=0):
    f = (740, 880)[v % 2]
    x = bell(f, 1.8)
    out = np.zeros(int(2.6 * SR))
    place(out, x, 0.0)
    place(out, bell(f * 1.0, 1.6), 0.28, 0.8)
    return reverb(out, 0.25)


@snd("tram.arrive")
def tram_arrive(v=0):
    g = _g("tram.arrive", v)
    dur = 4.5
    n = int(dur * SR)
    rumble = lp(noise(dur, g), 260) * np.linspace(0.1, 1.0, n) ** 1.5
    clicks = np.zeros(n)
    for i in range(int(dur * 7)):
        place(clicks, click(g, 1800, 0.05), 0.1 + i * 0.58 * (1 - i / (dur * 20)), 0.15 + 0.5 * i / (dur * 7))
    whine = sine(180, dur) * 0.05 * np.linspace(0, 1, n)
    return fade(mixdown(rumble, clicks, whine), 0.05, 0.4)


@snd("tram.horn")
def tram_horn(v=0):
    dur = 1.1
    return fade(mixdown(sine(311, dur), sine(392, dur), sine(311.7, dur) * 0.5, gains=[0.4, 0.4, 0.2]) * adsr(int(dur * SR), 0.05, 0.1, 0.9, 0.2), 0.02, 0.15)


@snd("tram.rumble")
def tram_rumble(v=0):
    g = _g("tram.rumble", v)
    dur = 6.0
    n = int(dur * SR)
    x = lp(noise(dur, g), 180) * 0.8 + sine(55, dur) * 0.15
    for i in range(10):
        place(x, click(g, 2000, 0.04), i * 0.6 + 0.05, 0.2)
    return loopable(x, 0.5)


@snd("tram.validate")
def tram_validate(v=0):
    dur = 0.5
    n = int(dur * SR)
    return (sine(1320 * (1 + 0.06 * v), dur) * 0.6 + sine(1760 * (1 + 0.06 * v), dur) * 0.4) * decay(n, 0.12)


@snd("tram.compost")
def tram_compost(v=0):
    g = _g("tram.compost", v)
    out = np.zeros(int(0.6 * SR))
    place(out, click(g, 900, 0.05), 0.0, 1.0)
    place(out, thud(160 - 30 * v, 0.2, g), 0.05, 0.8)
    place(out, bell(2400, 0.3), 0.08, 0.15)
    return out


# ------------------------------------------------------------------------------------------------ district life
@snd("kiosk.place")
def kiosk_place(v=0):
    g = _g("kiosk.place", v)
    out = np.zeros(int(0.9 * SR))
    place(out, thud(90, 0.4, g), 0.0)
    place(out, bp(noise(0.3, g), 600, 3) * decay(int(0.3 * SR), 0.08), 0.02, 0.6)
    place(out, bell(660, 0.5), 0.2, 0.15)
    return out


@snd("kiosk.open")
def kiosk_open(v=0):
    g = _g("kiosk.open", v)
    x = whoosh(300, 1500, 0.8, g, 0.5)
    out = np.zeros(int(1.1 * SR))
    place(out, x, 0.0)
    place(out, click(g, 1200, 0.06), 0.0, 0.8)
    place(out, bell(990, 0.6), 0.55, 0.2)
    return out


@snd("npc.mutter")
def npc_mutter(v=0):
    g = _g("npc.mutter", v)
    out = np.zeros(int(1.2 * SR))
    t = 0.0
    for i in range(4 + v):
        d = g.uniform(0.12, 0.22)
        place(out, voice(g.uniform(90, 120), d, ((500, 3), (900, 4)), g, (1.0, g.uniform(0.85, 1.1))), t, 0.7)
        t += d + g.uniform(0.02, 0.09)
        if t > 1.0:
            break
    return out


@snd("npc.blip")
def npc_blip(v=0):
    f = (330, 392, 294)[v % 3]
    dur = 0.07
    return sine(f, dur) * adsr(int(dur * SR), 0.005, 0.02, 0.6, 0.03) * 0.8


@snd("gopnik.ambient")
def gopnik_ambient(v=0):
    g = _g("gopnik.ambient", v)
    out = np.zeros(int(0.9 * SR))
    place(out, voice(105 + 8 * v, 0.28, ((420, 3), (800, 4)), g, (1.0, 0.8), 0.15), 0.0, 0.8)
    place(out, voice(95 + 6 * v, 0.3, ((380, 3), (700, 4)), g, (1.0, 0.7), 0.18), 0.35, 0.7)
    return out


@snd("gopnik.hurt")
def gopnik_hurt(v=0):
    g = _g("gopnik.hurt", v)
    return voice(160 + 25 * v, 0.28, ((600, 3), (1300, 4)), g, (1.3, 0.8), 0.25)


@snd("gopnik.death")
def gopnik_death(v=0):
    g = _g("gopnik.death", v)
    out = np.zeros(int(0.9 * SR))
    place(out, voice(150, 0.5, ((500, 3), (1000, 4)), g, (1.2, 0.5), 0.25), 0.0)
    place(out, thud(70, 0.3, g), 0.5, 0.8)
    return out


@snd("gopnik.throw")
def gopnik_throw(v=0):
    g = _g("gopnik.throw", v)
    return whoosh(900, 3200, 0.22, g, 0.5)


@snd("seed.crunch")
def seed_crunch(v=0):
    g = _g("seed.crunch", v)
    out = np.zeros(int(0.5 * SR))
    for i in range(5):
        place(out, hp(noise(0.03, g), 1500) * decay(int(0.03 * SR), 0.008), 0.02 + i * 0.07 + g.uniform(0, 0.02), g.uniform(0.4, 0.9))
    return out


@snd("stash.open")
def stash_open(v=0):
    g = _g("stash.open", v)
    out = np.zeros(int(0.9 * SR))
    for i in range(7):
        place(out, hp(lp(noise(0.08, g), 5000), 800) * decay(int(0.08 * SR), 0.03), i * 0.07 + g.uniform(0, 0.03), g.uniform(0.2, 0.6))
    place(out, bell(520 + 40 * v, 0.2) * 0.3, 0.1, 0.4)
    return out


@snd("note.read")
def note_read(v=0):
    g = _g("note.read", v)
    return fade(bp(noise(0.35, g), 3500, 0.8) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * tarr(0.35))) * 0.5, 0.02, 0.1)


@snd("box.rustle")
def box_rustle(v=0):
    g = _g("box.rustle", v)
    out = np.zeros(int(0.7 * SR))
    for i in range(6):
        place(out, lp(noise(0.1, g), 2500) * decay(int(0.1 * SR), 0.04), g.uniform(0, 0.55), g.uniform(0.2, 0.6))
    return out


@snd("box.open")
def box_open(v=0):
    g = _g("box.open", v)
    out = np.zeros(int(0.5 * SR))
    place(out, hp(noise(0.2, g), 1000) * decay(int(0.2 * SR), 0.06), 0.0, 0.5)
    place(out, thud(130, 0.2, g), 0.05, 0.4)
    return out


# ------------------------------------------------------------------------------------------------ package and kettle
@snd("package.growl")
def package_growl(v=0):
    g = _g("package.growl", v)
    return growl((62, 74, 88)[v % 3], 1.0 + 0.25 * v, g) * 0.9


@snd("package.place")
def package_place(v=0):
    return thud(80, 0.3, _g("package.place", v)) * 0.9


@snd("package.pickup")
def package_pickup(v=0):
    g = _g("package.pickup", v)
    return lp(noise(0.3, g), 1800) * np.sin(np.linspace(0, np.pi, int(0.3 * SR))) * 0.5


@snd("kettle.pour")
def kettle_pour(v=0):
    return fade(water(1.6, _g("kettle.pour", v)), 0.1, 0.4) * 0.8


@snd("kettle.pink")
def kettle_pink(v=0):
    g = _g("kettle.pink", v)
    dur = 2.4
    n = int(dur * SR)
    x = sweep(220, 1760, dur, "lin") * 0.3 * np.sin(np.pi * np.arange(n) / n)
    x += sweep(1760, 330, dur, "lin") * 0.2 * np.sin(np.pi * np.arange(n) / n)
    x += (bell(990, 1.2) * 0.4)[::-1].tolist().__len__() * 0 if False else 0
    rev = bell(1245, 1.5)[::-1] * 0.35
    out = np.zeros(n)
    out += x
    place(out, rev, 0.4, 1.0)
    place(out, water(1.0, g, 3000) * 0.3, 0.2)
    for i in range(6):
        place(out, glass(660 * (1.5 ** (i % 3)), 0.6) * 0.18, 0.2 + i * 0.28)
    return fade(out, 0.05, 0.6)


@snd("kettle.place")
def kettle_place(v=0):
    g = _g("kettle.place", v)
    out = np.zeros(int(0.7 * SR))
    place(out, thud(150, 0.2, g), 0.0, 0.6)
    place(out, bell(1100, 0.5), 0.0, 0.3)
    return out


# ------------------------------------------------------------------------------------------------ chroma and portal
@snd("chroma.consume")
def chroma_consume(v=0):
    g = _g("chroma.consume", v)
    out = np.zeros(int(1.4 * SR))
    place(out, bp(noise(0.25, g), 400, 2) * decay(int(0.25 * SR), 0.1), 0.0, 0.8)
    place(out, thud(110, 0.2, g), 0.28, 0.5)
    for i, f in enumerate((660, 880, 1320)):
        place(out, glass(f, 0.9) * 0.25, 0.4 + i * 0.1)
    return out


@snd("chroma.tick")
def chroma_tick(v=0):
    dur = 0.12
    return sine(1500, dur) * decay(int(dur * SR), 0.03) * 0.5


@snd("chroma.fail")
def chroma_fail(v=0):
    dur = 1.8
    n = int(dur * SR)
    return fade(sweep(520, 90, dur) * 0.5 * decay(n, 0.7) + sweep(530, 88, dur) * 0.4 * decay(n, 0.7), 0.01, 0.4)


@snd("chroma.transition")
def chroma_transition(v=0):
    g = _g("chroma.transition", v)
    dur = 8.0
    n = int(dur * SR)
    t = np.arange(n) / n
    wob = 1 + 0.03 * np.sin(2 * np.pi * (0.5 + 4 * t) * tarr(dur))
    base = np.zeros(n)
    f = 200 * (2.0 ** (np.where(t < 0.7, t / 0.7 * 1.6, 1.6 - (t - 0.7) / 0.3 * 2.8)))
    phase = 2 * np.pi * np.cumsum(f * wob) / SR
    base += np.sin(phase) * 0.35 + np.sin(phase * 1.5) * 0.15 + np.sin(phase * 2.01) * 0.12
    base *= np.sin(np.pi * np.clip(t * 1.05, 0, 1)) ** 0.7
    air = lp(noise(dur, g), 3500) * (t ** 2) * 0.25 * np.where(t < 0.85, 1, (1 - t) / 0.15)
    out = base + air
    for i, ff in enumerate((523, 659, 784, 1047)):
        place(out, glass(ff, 2.0) * 0.25, 5.4 + i * 0.35)
    return fade(reverb(out, 0.3, 1.5)[:n + int(0.5 * SR)], 0.1, 0.8)


@snd("chroma.arrive")
def chroma_arrive(v=0):
    out = np.zeros(int(3.0 * SR))
    for i, f in enumerate((523, 659, 784, 988, 1175, 1568)):
        place(out, glass(f, 1.8) * 0.4, i * 0.18)
    return reverb(out, 0.3)


@snd("crystal.chime")
def crystal_chime(v=0):
    f = (523, 587, 659, 784)[v % 4] * 1.0
    return reverb(glass(f * 2, 1.6) * 0.6, 0.25)


@snd("crystal.hum")
def crystal_hum(v=0):
    dur = 4.0
    x = sum(sine(f * (1 + d), dur) * a for f, d, a in ((220, 0.0, 0.4), (220, 0.004, 0.3), (330, 0.002, 0.2), (440, -0.003, 0.15)))
    x = tremolo(x, 0.5, 0.3)
    return loopable(x * 0.5, 0.4)


@snd("portal.ambient")
def portal_ambient(v=0):
    g = _g("portal.ambient", v)
    dur = 6.0
    x = padsound([196, 247, 294, 392], dur, 0.006, 1800) * 0.7
    x += lp(noise(dur, g), 1800) * 0.04 * (0.5 + 0.5 * np.sin(2 * np.pi * 1.3 * tarr(dur)))
    return loopable(x, 0.8)


@snd("portal.activate")
def portal_activate(v=0):
    g = _g("portal.activate", v)
    dur = 2.2
    n = int(dur * SR)
    out = sweep(150, 1200, dur) * 0.4 * np.linspace(0, 1, n) ** 2
    out += whoosh(500, 4000, dur, g, 0.4)
    for i, f in enumerate((392, 523, 659, 784)):
        place(out, glass(f, 1.5) * 0.35, 1.0 + i * 0.12)
    return fade(out, 0.02, 0.5)


@snd("portal.enter")
def portal_enter(v=0):
    g = _g("portal.enter", v)
    dur = 1.6
    return fade(whoosh(3000, 300, dur, g, 0.5) + sweep(900, 110, dur) * 0.25 * decay(int(dur * SR), 0.7), 0.01, 0.3)


# ------------------------------------------------------------------------------------------------ garage and mechanisms
@snd("garage.power_on")
def garage_power_on(v=0):
    g = _g("garage.power_on", v)
    out = np.zeros(int(2.0 * SR))
    place(out, thud(60, 0.4, g), 0.0, 0.9)
    place(out, click(g, 1400, 0.05), 0.02)
    place(out, motor(60, 190, 1.4, g) * 0.35 * np.linspace(0.3, 1, int(1.4 * SR)), 0.1)
    place(out, bell(880, 0.6), 1.2, 0.2)
    return fade(out, 0.01, 0.3)


@snd("garage.power_off")
def garage_power_off(v=0):
    g = _g("garage.power_off", v)
    out = np.zeros(int(1.6 * SR))
    place(out, motor(190, 40, 1.2, g) * 0.35 * np.linspace(1, 0.1, int(1.2 * SR)), 0.0)
    place(out, thud(55, 0.5, g), 1.1, 0.7)
    return fade(out, 0.01, 0.2)


@snd("garage.clack")
def garage_clack(v=0):
    g = _g("garage.clack", v)
    out = np.zeros(int(0.35 * SR))
    place(out, click(g, 1200 + 400 * v, 0.06), 0.0, 1.0)
    place(out, thud(180 - 25 * v, 0.12, g), 0.0, 0.5)
    place(out, bell(2100 + 200 * v, 0.2) * 0.2, 0.02)
    return out


@snd("garage.door")
def garage_door(v=0):
    g = _g("garage.door", v)
    dur = 1.8
    n = int(dur * SR)
    x = lp(noise(dur, g), 700) * (0.5 + 0.5 * np.sin(2 * np.pi * (4 + 2 * v) * tarr(dur))) * 0.7
    x += motor(70, 90, dur, g) * 0.25
    out = x * np.sin(np.pi * np.arange(n) / n) ** 0.5
    place(out, thud(60, 0.4, g), dur - 0.45, 0.9)
    return out


@snd("garage.press")
def garage_press(v=0):
    g = _g("garage.press", v)
    out = np.zeros(int(1.1 * SR))
    place(out, thud(48 + 8 * v, 0.6, g), 0.0, 1.2)
    place(out, hp(noise(0.5, g), 3000) * decay(int(0.5 * SR), 0.12), 0.08, 0.35)
    return out


@snd("garage.lift")
def garage_lift(v=0):
    g = _g("garage.lift", v)
    return fade(motor(90, 130, 2.4, g) * 0.5, 0.2, 0.5)


@snd("lever.pull")
def lever_pull(v=0):
    g = _g("lever.pull", v)
    out = np.zeros(int(0.6 * SR))
    place(out, thud(110 - 20 * v, 0.2, g), 0.0, 0.7)
    place(out, click(g, 900, 0.08), 0.12, 0.9)
    place(out, lp(noise(0.2, g), 1500) * decay(int(0.2 * SR), 0.08), 0.0, 0.4)
    return out


@snd("switch.click")
def switch_click(v=0):
    g = _g("switch.click", v)
    out = np.zeros(int(0.4 * SR))
    place(out, click(g, 1800, 0.04), 0.0)
    place(out, click(g, 1200, 0.05), 0.12, 0.8)
    place(out, bell(1500 + 300 * v, 0.18) * 0.12, 0.0)
    return out


@snd("tap.pour")
def tap_pour(v=0):
    return fade(water(1.2, _g("tap.pour", v), 1400 + 400 * v) * 0.7, 0.1, 0.3)


@snd("pump.start")
def pump_start(v=0):
    g = _g("pump.start", v)
    out = np.zeros(int(2.4 * SR))
    place(out, thud(70, 0.4, g), 0.0, 0.8)
    place(out, motor(40, 160, 1.8, g) * 0.45, 0.2)
    place(out, water(1.0, g) * 0.3, 1.4)
    return fade(out, 0.01, 0.4)


@snd("pump.fill")
def pump_fill(v=0):
    return fade(water(3.0, _g("pump.fill", v), 1800) * 0.8, 0.2, 0.6)


@snd("drain.open")
def drain_open(v=0):
    g = _g("drain.open", v)
    out = np.zeros(int(1.6 * SR))
    place(out, click(g, 800, 0.08))
    place(out, water(1.3, g, 900) * np.linspace(0.3, 1, int(1.3 * SR)) * 0.7, 0.1)
    return fade(out, 0.01, 0.3)


@snd("water.whoosh")
def water_whoosh(v=0):
    g = _g("water.whoosh", v)
    return mixdown(whoosh(300, 2500, 1.0 + 0.3 * v, g, 0.6), water(1.0, g) * 0.15)


# ------------------------------------------------------------------------------------------------ bosses and mechanics
@snd("boss.telegraph")
def boss_telegraph(v=0):
    dur = 1.0
    n = int(dur * SR)
    f = 200 * (2 ** (np.linspace(0, 1.5 + 0.3 * v, n)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return fade(tremolo((np.sin(ph) + 0.4 * np.sin(2 * ph)) * 0.5, 12 + 6 * v, 0.5) * np.linspace(0.2, 1, n), 0.02, 0.05)


@snd("boss.slam")
def boss_slam(v=0):
    g = _g("boss.slam", v)
    out = np.zeros(int(1.6 * SR))
    place(out, thud(42 + 6 * v, 0.9, g), 0.0, 1.3)
    place(out, lp(noise(1.2, g), 1200) * decay(int(1.2 * SR), 0.3), 0.02, 0.5)
    place(out, hp(noise(0.3, g), 2500) * decay(int(0.3 * SR), 0.05), 0.0, 0.4)
    return out


@snd("boss.phase")
def boss_phase(v=0):
    g = _g("boss.phase", v)
    dur = 2.4
    n = int(dur * SR)
    return fade(sweep(80, 40, dur) * 0.6 + whoosh(200, 1800, dur, g, 0.4) + sine(55, dur) * 0.3 * decay(n, 1.0), 0.02, 0.5)


@snd("boss.roar")
def boss_roar(v=0):
    g = _g("boss.roar", v)
    return mixdown(growl((54, 70)[v % 2], 1.8, g) * 1.2, voice(110, 1.6, ((300, 2), (700, 3)), g, (1.0, 0.6), 0.3) * 0.4)


@snd("boss.shield_on")
def boss_shield_on(v=0):
    g = _g("boss.shield_on", v)
    dur = 1.0
    return fade(mixdown(sweep(300, 1400, dur) * 0.3, whoosh(800, 4000, dur, g, 0.3), glass(880, 0.8) * 0.3), 0.01, 0.2)


@snd("boss.shield_off")
def boss_shield_off(v=0):
    g = _g("boss.shield_off", v)
    dur = 1.0
    return fade(mixdown(sweep(1400, 200, dur) * 0.3, hp(noise(0.3, g), 2000) * decay(int(0.3 * SR), 0.05) * 0.5, silence(dur)), 0.01, 0.2)


@snd("boss.defeat")
def boss_defeat(v=0):
    g = _g("boss.defeat", v)
    out = np.zeros(int(4.0 * SR))
    place(out, thud(40, 1.4, g), 0.0, 1.2)
    place(out, sweep(400, 50, 2.5) * 0.4 * decay(int(2.5 * SR), 1.0), 0.0)
    place(out, lp(noise(2.0, g), 900) * decay(int(2.0 * SR), 0.5), 0.1, 0.6)
    for i, f in enumerate((392, 523, 659, 784)):
        place(out, glass(f, 1.8) * 0.3, 2.0 + i * 0.2)
    return reverb(out, 0.25)


@snd("boss.spawn")
def boss_spawn(v=0):
    g = _g("boss.spawn", v)
    dur = 3.0
    n = int(dur * SR)
    swell = (sweep(60, 220, dur) * 0.4 + whoosh(100, 1500, dur, g, 0.3)) * np.linspace(0.1, 1, n) ** 2
    out = swell.copy()
    place(out, thud(45, 0.9, g), dur - 0.8, 1.1)
    return fade(out, 0.02, 0.5)


@snd("boss.bell")
def boss_bell(v=0):
    out = np.zeros(int(2.4 * SR))
    for i in range(4):
        place(out, bell(660, 1.0), i * 0.3, 0.8)
    return out


@snd("tower.ticket")
def tower_ticket(v=0):
    g = _g("tower.ticket", v)
    out = np.zeros(int(1.6 * SR))
    place(out, sine(1800, 0.12) * decay(int(0.12 * SR), 0.05) * 0.5, 0.0)
    for i in range(14):
        place(out, click(g, 2400, 0.02), 0.2 + i * 0.05, 0.4)
    place(out, sine(1200, 0.2) * decay(int(0.2 * SR), 0.08) * 0.5, 1.1)
    return out


@snd("tower.bell")
def tower_bell(v=0):
    return bell(1568, 1.2) * 0.8


@snd("tower.door")
def tower_door(v=0):
    g = _g("tower.door", v)
    dur = 1.4
    n = int(dur * SR)
    return fade(bp(noise(dur, g), 500, 4) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * tarr(dur))) * np.sin(np.pi * np.arange(n) / n) * 0.9, 0.02, 0.2)


# ------------------------------------------------------------------------------------------------ abilities and ui
@snd("ability.dash")
def ability_dash(v=0):
    g = _g("ability.dash", v)
    return whoosh(1200, 6000, 0.35, g, 0.8)


@snd("ability.spring")
def ability_spring(v=0):
    dur = 0.5
    n = int(dur * SR)
    f = 180 * (2 ** (np.linspace(0, 2.2, n)))
    ph = 2 * np.pi * np.cumsum(f * (1 + 0.1 * np.sin(2 * np.pi * 20 * tarr(dur)))) / SR
    return np.sin(ph) * decay(n, 0.2) * 0.7 * (1 + 0.1 * v)


@snd("ability.glide_start")
def ability_glide_start(v=0):
    g = _g("ability.glide_start", v)
    return whoosh(500, 2800, 0.7, g, 0.7)


@snd("ability.glide_end")
def ability_glide_end(v=0):
    g = _g("ability.glide_end", v)
    return whoosh(2000, 300, 0.4, g, 0.5)


@snd("ability.unlock")
def ability_unlock(v=0):
    out = np.zeros(int(2.4 * SR))
    for i, f in enumerate((392, 494, 587, 784, 988)):
        place(out, glass(f, 1.4) * 0.45, i * 0.14)
    return reverb(out, 0.3)


@snd("ability.ready")
def ability_ready(v=0):
    return glass(1568, 0.5) * 0.35


@snd("ui.notebook_open")
def ui_notebook_open(v=0):
    g = _g("ui.notebook_open", v)
    out = np.zeros(int(0.5 * SR))
    place(out, bp(noise(0.25, g), 2800, 0.9) * np.sin(np.pi * tarr(0.25) / 0.25) * 0.5, 0.0)
    place(out, thud(200, 0.1, g), 0.2, 0.3)
    return out


@snd("ui.quest_update")
def ui_quest_update(v=0):
    out = np.zeros(int(1.2 * SR))
    place(out, glass(784, 0.9) * 0.5, 0.0)
    place(out, glass(1175, 0.9) * 0.5, 0.18)
    return out


@snd("ui.checkpoint")
def ui_checkpoint(v=0):
    return glass(988, 0.9) * 0.45


@snd("ui.warning")
def ui_warning(v=0):
    out = np.zeros(int(0.6 * SR))
    for i in range(2):
        place(out, square(880, 0.15) * adsr(int(0.15 * SR), 0.005, 0.02, 0.8, 0.03) * 0.3, i * 0.25)
    return out


# ------------------------------------------------------------------------------------------------ cats
@snd("cat.purr")
def cat_purr(v=0):
    g = _g("cat.purr", v)
    dur = 3.0
    base = bp(noise(dur, g), 180, 1.5) * 1.5 + sine(25, dur) * 0.1
    pulse = 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * 24 * tarr(dur))) * np.abs(np.sin(2 * np.pi * 24 * tarr(dur))) ** 0.5
    x = base * pulse * (0.8 + 0.2 * np.sin(2 * np.pi * 0.33 * tarr(dur)))
    return loopable(lp(x, 900), 0.3)


@snd("cat.meow_low")
def cat_meow_low(v=0):
    g = _g("cat.meow_low", v)
    return cat_meow(420 + 25 * v, 0.7 + 0.1 * v, g, 1.2)


@snd("cat.meow_high")
def cat_meow_high(v=0):
    g = _g("cat.meow_high", v)
    return cat_meow(640 + 40 * v, 0.5 + 0.08 * v, g, 1.3)


@snd("cat.hiss")
def cat_hiss(v=0):
    g = _g("cat.hiss", v)
    n = int(0.9 * SR)
    return fade(hp(noise(0.9, g), 3500) * np.sin(np.pi * np.arange(n) / n) ** 0.5 * 0.7, 0.02, 0.1)


@snd("cat.scratch")
def cat_scratch(v=0):
    g = _g("cat.scratch", v)
    out = np.zeros(int(0.9 * SR))
    for i in range(5):
        d = 0.12
        place(out, hp(noise(d, g), 1800) * np.sin(np.pi * tarr(d) / d) * 0.5, i * 0.15 + g.uniform(0, 0.03))
    return out


@snd("cat.eat")
def cat_eat(v=0):
    g = _g("cat.eat", v)
    out = np.zeros(int(1.0 * SR))
    for i in range(5):
        place(out, bp(noise(0.06, g), 900, 1.5) * decay(int(0.06 * SR), 0.02), 0.05 + i * 0.17, 0.7)
    return out


@snd("cat.sniff")
def cat_sniff(v=0):
    g = _g("cat.sniff", v)
    out = np.zeros(int(0.8 * SR))
    for i in range(3):
        place(out, bp(noise(0.12, g), 2400, 1.2) * np.sin(np.pi * tarr(0.12) / 0.12), 0.05 + i * 0.22, 0.6)
    return out


# ------------------------------------------------------------------------------------------------ tower mechanics
@snd("relay.active")
def relay_active(v=0):
    g = _g("relay.active", v)
    out = np.zeros(int(1.0 * SR))
    place(out, sweep(300, 2400, 0.25) * decay(int(0.25 * SR), 0.1) * 0.5)
    place(out, glass(1568, 0.8) * 0.4, 0.12)
    place(out, click(g, 3000, 0.04), 0.0, 0.6)
    return out


@snd("relay.pass")
def relay_pass(v=0):
    g = _g("relay.pass", v)
    out = whoosh(800, 5000, 0.5, g, 0.6)
    out = np.concatenate([out, np.zeros(int(0.4 * SR))])
    place(out, glass(1318, 0.7) * 0.4, 0.3)
    return out


@snd("charge.overload")
def charge_overload(v=0):
    dur = 1.0
    n = int(dur * SR)
    f = 600 * (2 ** (np.linspace(0, 1.0, n)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return tremolo(np.sign(np.sin(ph)) * 0.25, 14, 0.7) * np.linspace(0.5, 1, n)


@snd("platform.crumble")
def platform_crumble(v=0):
    g = _g("platform.crumble", v)
    out = np.zeros(int(0.8 * SR))
    for i in range(7):
        place(out, hp(lp(noise(0.08, g), 4000), 500) * decay(int(0.08 * SR), 0.02), g.uniform(0, 0.6), g.uniform(0.3, 0.8))
    place(out, thud(90 + 20 * v, 0.2, g), 0.0, 0.4)
    return out


@snd("platform.collapse")
def platform_collapse(v=0):
    g = _g("platform.collapse", v)
    out = np.zeros(int(2.0 * SR))
    place(out, lp(noise(1.8, g), 1400) * decay(int(1.8 * SR), 0.5), 0.0, 0.8)
    place(out, thud(50, 0.6, g), 0.1, 0.9)
    for i in range(10):
        place(out, hp(noise(0.05, g), 1500) * decay(int(0.05 * SR), 0.015), g.uniform(0.1, 1.6), g.uniform(0.2, 0.6))
    return out


@snd("spring.boing")
def spring_boing(v=0):
    dur = 0.6
    n = int(dur * SR)
    f = 140 * (2 ** (np.linspace(0, 2.0, n)))
    vib = 1 + 0.08 * np.sin(2 * np.pi * (14 + 4 * v) * tarr(dur))
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    return (np.sin(ph) + 0.3 * np.sin(2 * ph)) * decay(n, 0.28) * 0.6


@snd("checkpoint.set")
def checkpoint_set(v=0):
    out = np.zeros(int(1.6 * SR))
    place(out, glass(659, 1.0) * 0.4, 0.0)
    place(out, glass(988, 1.2) * 0.4, 0.12)
    return reverb(out, 0.2)


@snd("lift.move")
def lift_move(v=0):
    g = _g("lift.move", v)
    return fade(motor(70, 100, 1.8, g) * 0.45, 0.15, 0.3)


@snd("validator.ok")
def validator_ok(v=0):
    out = np.zeros(int(0.7 * SR))
    place(out, glass(1318, 0.6) * 0.5, 0.0)
    place(out, glass(1760, 0.6) * 0.5, 0.1)
    return out


@snd("validator.fail")
def validator_fail(v=0):
    dur = 0.5
    return fade(square(120, dur) * 0.3 * adsr(int(dur * SR), 0.01, 0.05, 0.8, 0.1) + square(113, dur) * 0.2, 0.005, 0.1)


# ------------------------------------------------------------------------------------------------ ambience (looping)
@snd("ambient.district.loop")
def ambient_district(v=0):
    g = _g("ambient.district.loop", v)
    dur = 24.0
    n = int(dur * SR)
    hum = sine(100, dur) * 0.06 + sine(200.4, dur) * 0.03 + sine(50, dur) * 0.05
    hum *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.17 * tarr(dur))
    air = lp(noise(dur, g), 600) * 0.06 * (0.6 + 0.4 * np.sin(2 * np.pi * 0.09 * tarr(dur)))
    out = hum + air
    for i in range(5):
        at = g.uniform(1, dur - 3)
        place(out, lp(noise(0.6, g), 900) * np.sin(np.pi * tarr(0.6) / 0.6) * 0.04, at)
        place(out, voice(g.uniform(110, 160), 0.12, ((500, 3), (1000, 4)), g) * 0.05, at + 0.3)
    return loopable(out, 1.0)


@snd("ambient.district.mood")
def ambient_district_mood(v=0):
    x = bell((660, 740)[v % 2], 2.2) * 0.25
    out = np.zeros(int(4.0 * SR))
    place(out, x, 0.0)
    place(out, x, 0.9, 0.5)
    return reverb(lp(out, 2500), 0.5, 1.5)


@snd("ambient.chroma.loop")
def ambient_chroma(v=0):
    g = _g("ambient.chroma.loop", v)
    dur = 28.0
    chords = [(262, 330, 392, 494), (220, 262, 330, 440), (196, 247, 294, 392), (233, 294, 349, 466)]
    out = np.zeros(int(dur * SR))
    seg = dur / len(chords)
    for i, c in enumerate(chords):
        place(out, padsound(c, seg + 3.0, 0.005, 2200), i * seg, 0.5)
    out += lp(noise(dur, g), 5000) * 0.015 * (0.5 + 0.5 * np.sin(2 * np.pi * 0.2 * tarr(dur)))
    return loopable(out, 3.0) * 0.8


@snd("ambient.chroma.additions")
def ambient_chroma_additions(v=0):
    g = _g("ambient.chroma.additions", v)
    out = np.zeros(int(3.0 * SR))
    for i in range(3 + v):
        place(out, glass(g.choice([1047, 1175, 1319, 1568, 1760]), 1.6) * 0.3, i * g.uniform(0.2, 0.5))
    return reverb(out, 0.4, 1.4)


@snd("ambient.garage.loop")
def ambient_garage(v=0):
    g = _g("ambient.garage.loop", v)
    dur = 16.0
    out = sine(50, dur) * 0.08 + sine(100.5, dur) * 0.05 + lp(noise(dur, g), 400) * 0.06
    for i in range(6):
        at = g.uniform(0.5, dur - 2)
        place(out, bell(g.uniform(300, 500), 0.4) * 0.05, at)
        place(out, thud(70, 0.3, g) * 0.15, at + 0.2)
    return loopable(out, 1.0)


@snd("ambient.tower.loop")
def ambient_tower(v=0):
    g = _g("ambient.tower.loop", v)
    dur = 16.0
    out = sine(120, dur) * 0.05 + sine(240.3, dur) * 0.03 + sine(60, dur) * 0.03 + lp(noise(dur, g), 1500) * 0.015
    for i in range(30):
        place(out, click(g, 3200, 0.02) * 0.2, g.uniform(0, dur - 0.1))
    return loopable(out, 1.0)


@snd("ambient.aquapark")
def ambient_aquapark(v=0):
    g = _g("ambient.aquapark", v)
    dur = 14.0
    out = sine(70, dur) * 0.03 + lp(noise(dur, g), 500) * 0.03
    for i in range(18):
        f = g.uniform(500, 1500)
        place(out, sweep(f, f * 0.6, 0.12) * decay(int(0.12 * SR), 0.03) * 0.15, g.uniform(0, dur - 0.2))
    return reverb(loopable(out, 1.0), 0.3, 1.2)[: int(dur * SR) - int(1.0 * SR)]
