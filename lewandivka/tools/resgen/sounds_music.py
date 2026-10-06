"""Procedural music: five original tracks composed from code (no samples, no third-party material)."""
from __future__ import annotations

import numpy as np

from .sounds_synth import (SR, RECIPES, adsr, bell, bp, click, decay, fade, glass, hp, loopable, lp, mixdown, noise, normalize,
                           padsound, place, pluck, reverb, rng, silence, sine, snd, thud, tarr)


def mtof(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def bass(f: float, dur: float) -> np.ndarray:
    n = int(dur * SR)
    t = tarr(dur)
    x = np.sin(2 * np.pi * f * t) + 0.35 * np.sin(4 * np.pi * f * t)
    return x * adsr(n, 0.01, 0.08, 0.7, min(0.12, dur * 0.4))


def stab(freqs, dur: float) -> np.ndarray:
    x = np.zeros(int(dur * SR))
    for f in freqs:
        t = tarr(dur)
        x += (2 * ((t * f) % 1.0) - 1) * 0.3
    x = lp(x, 2400)
    return x * decay(len(x), dur * 0.35)


def snare(g) -> np.ndarray:
    n = int(0.18 * SR)
    return (hp(noise(0.18, g), 1500) * decay(n, 0.05) + sine(190, 0.18) * decay(n, 0.04) * 0.5) * 0.7


def hat(g, open_: bool = False) -> np.ndarray:
    d = 0.2 if open_ else 0.05
    return hp(noise(d, g), 6000) * decay(int(d * SR), d * 0.3) * 0.35


def kick() -> np.ndarray:
    n = int(0.3 * SR)
    t = tarr(0.3)
    f = 120 * np.exp(-t * 18) + 45
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * decay(n, 0.09) * 0.9


def _finish(x: np.ndarray, rev: float = 0.2, peak: float = 0.8) -> np.ndarray:
    x = reverb(x, rev, 1.4)
    return fade(normalize(x, peak), 0.05, 1.5)


@snd("music.district")
def music_district(v=0):
    g = rng("music.district")
    bpm = 66
    beat = 60.0 / bpm
    bars = 16
    chords = [(57, 60, 64), (53, 57, 60), (48, 52, 55), (55, 59, 62)]     # Am F C G
    dur = bars * 4 * beat + 3
    out = np.zeros(int(dur * SR))
    for b in range(bars):
        c = chords[b % 4]
        t0 = b * 4 * beat
        place(out, padsound([mtof(m) for m in c], 4 * beat + 1.0, 0.004, 1400), t0, 0.28)
        place(out, bass(mtof(c[0] - 12), 2 * beat), t0, 0.45)
        place(out, bass(mtof(c[0] - 12), 2 * beat), t0 + 2 * beat, 0.35)
        pattern = [0, 1, 2, 1, 2, 1, 0, 2] if b % 2 == 0 else [0, 2, 1, 2, 0, 1, 2, 1]
        for i, p in enumerate(pattern):
            place(out, pluck(mtof(c[p % 3] + 12), 1.4), t0 + i * beat / 2, 0.22)
        if b % 4 == 3:
            place(out, bell(mtof(c[2] + 24), 3.0) * 0.5, t0 + 2 * beat, 0.18)
        if b >= 4 and b % 2 == 0:                                       # a wandering melody
            scale = [69, 71, 72, 74, 76, 79]
            for k in range(3):
                note = scale[(b + k * 2 + int(g.integers(0, 3))) % len(scale)]
                place(out, pluck(mtof(note + 12), 2.0, 1.4), t0 + (k * 1.5 + 0.5) * beat, 0.26)
    return _finish(out, 0.3, 0.75)


@snd("music.chroma")
def music_chroma(v=0):
    g = rng("music.chroma")
    bpm = 78
    beat = 60.0 / bpm
    bars = 20
    chords = [(62, 66, 69), (57, 61, 64), (59, 62, 66), (55, 59, 62)]    # D A Bm G
    penta = [74, 76, 78, 81, 83, 86]
    dur = bars * 4 * beat + 4
    out = np.zeros(int(dur * SR))
    for b in range(bars):
        c = chords[b % 4]
        t0 = b * 4 * beat
        place(out, padsound([mtof(m) for m in c], 4 * beat + 1.5, 0.006, 2400), t0, 0.3)
        place(out, bass(mtof(c[0] - 24), 4 * beat), t0, 0.3)
        for i in range(8):
            note = c[i % 3] + 24 + (12 if i % 4 == 3 else 0)
            place(out, glass(mtof(note), 1.6) * 0.28, t0 + i * beat / 2)
        if b >= 2:
            for k in range(2):
                n = penta[int(g.integers(0, len(penta)))]
                place(out, bell(mtof(n), 2.4) * 0.4, t0 + (k * 2 + 0.25) * beat, 0.16)
        if b % 4 == 0:
            place(out, glass(mtof(c[2] + 36), 3.0) * 0.4, t0 + 3 * beat, 0.15)
    return _finish(out, 0.4, 0.75)


@snd("music.boss")
def music_boss(v=0):
    g = rng("music.boss")
    bpm = 124
    beat = 60.0 / bpm
    bars = 32
    chords = [(52, 55, 59), (48, 52, 55), (50, 54, 57), (52, 55, 59)]    # Em C D Em
    dur = bars * 4 * beat + 2
    out = np.zeros(int(dur * SR))
    for b in range(bars):
        c = chords[b % 4]
        t0 = b * 4 * beat
        for i in range(8):
            place(out, bass(mtof(c[0] - 12), beat / 2 * 0.9), t0 + i * beat / 2, 0.5)
        for i in (0, 2):
            place(out, kick(), t0 + i * beat, 0.9)
        place(out, kick(), t0 + 2.5 * beat, 0.6)
        for i in (1, 3):
            place(out, snare(g), t0 + i * beat, 0.6)
        for i in range(16):
            place(out, hat(g, i % 4 == 3), t0 + i * beat / 4, 0.3)
        if b >= 4:
            for i, p in enumerate([0, 2, 1, 2, 0, 1, 2, 1]):
                place(out, pluck(mtof(c[p % 3] + 12), 0.5, 1.6), t0 + i * beat / 2, 0.18)
        if b % 4 == 3:
            place(out, stab([mtof(m + 12) for m in c], 0.9), t0 + 3 * beat, 0.45)
        if b >= 8 and b % 2 == 0:
            place(out, padsound([mtof(m + 12) for m in c], 4 * beat, 0.01, 1800), t0, 0.12)
    return _finish(out, 0.15, 0.85)


@snd("music.tower")
def music_tower(v=0):
    g = rng("music.tower")
    bpm = 100
    beat = 60.0 / bpm
    bars = 24
    dur = bars * 4 * beat + 3
    out = np.zeros(int(dur * SR))
    drone = padsound([mtof(m) for m in (36, 43, 51)], dur, 0.003, 700)
    out += drone[: len(out)] * 0.3
    osti = [60, 63, 66, 63, 62, 65, 68, 65]
    for b in range(bars):
        t0 = b * 4 * beat
        for i in range(8):
            if b % 6 == 5 and i > 4:
                continue
            note = osti[(i + (b // 4)) % 8] + (12 if b >= 12 and i % 2 else 0)
            place(out, pluck(mtof(note), 0.35, 0.8), t0 + i * beat / 2, 0.22)
        for i in range(16):
            if g.random() < 0.55:
                place(out, click(g, 3000, 0.02) * 0.5, t0 + i * beat / 4, 0.25)
        if b % 2 == 0:
            place(out, bell(mtof(84), 1.2) * 0.4, t0 + 3.5 * beat, 0.18)
        if b % 8 == 7:
            place(out, stab([mtof(m) for m in (48, 54, 58)], 1.4), t0 + 2 * beat, 0.3)
    return _finish(out, 0.25, 0.7)


@snd("music.credits")
def music_credits(v=0):
    g = rng("music.credits")
    bpm = 90
    beat = 60.0 / bpm
    bars = 28
    chords = [(60, 64, 67), (55, 59, 62), (57, 60, 64), (53, 57, 60)]    # C G Am F
    scale = [72, 74, 76, 79, 81, 84]
    dur = bars * 4 * beat + 5
    out = np.zeros(int(dur * SR))
    for b in range(bars):
        c = chords[b % 4]
        t0 = b * 4 * beat
        place(out, padsound([mtof(m) for m in c], 4 * beat + 1.5, 0.005, 2200), t0, 0.3)
        place(out, bass(mtof(c[0] - 12), 3.5 * beat), t0, 0.4)
        for i in range(8):
            place(out, pluck(mtof(c[i % 3] + 12 + (12 if i % 4 == 3 else 0)), 1.2, 1.2), t0 + i * beat / 2, 0.2)
        if b >= 4:
            for k in range(3):
                n = scale[(b * 2 + k * 3) % len(scale)]
                place(out, bell(mtof(n), 2.5) * 0.5, t0 + (k * 1.25 + 0.5) * beat, 0.22)
        if b >= 12 and b % 2 == 0:
            place(out, glass(mtof(c[2] + 36), 2.5) * 0.4, t0 + 2 * beat, 0.2)
        if b >= 16:
            for i in (0, 2):
                place(out, kick(), t0 + i * beat, 0.35)
            for i in (1, 3):
                place(out, hat(g, True), t0 + i * beat, 0.25)
    return _finish(out, 0.35, 0.8)
