"""Original sound synthesis (numpy only): every sound and music track of the mod is generated here."""
from __future__ import annotations

import math
import zlib

import numpy as np

SR = 22050
RECIPES: dict = {}


def snd(sound_id: str):
    def deco(fn):
        RECIPES[sound_id] = fn
        return fn
    return deco


# ------------------------------------------------------------------------------------------------ primitives
def rng(sound_id: str, variant: int = 0) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32(f"{sound_id}/{variant}".encode()))


def tarr(dur: float) -> np.ndarray:
    return np.arange(int(dur * SR)) / SR


def sine(f, dur: float, phase: float = 0.0) -> np.ndarray:
    t = tarr(dur)
    return np.sin(2 * np.pi * f * t + phase)


def sweep(f0: float, f1: float, dur: float, curve: str = "exp") -> np.ndarray:
    t = tarr(dur)
    if curve == "exp":
        k = (f1 / f0) ** (1 / max(dur, 1e-6))
        phase = 2 * np.pi * f0 * (k ** t - 1) / math.log(k) if abs(k - 1) > 1e-6 else 2 * np.pi * f0 * t
    else:
        phase = 2 * np.pi * (f0 * t + (f1 - f0) * t ** 2 / (2 * dur))
    return np.sin(phase)


def saw(f, dur: float) -> np.ndarray:
    t = tarr(dur)
    return 2 * ((t * f) % 1.0) - 1


def square(f, dur: float, duty: float = 0.5) -> np.ndarray:
    t = tarr(dur)
    return np.where(((t * f) % 1.0) < duty, 1.0, -1.0)


def tri(f, dur: float) -> np.ndarray:
    return 2 * np.abs(saw(f, dur)) - 1


def noise(dur: float, g: np.random.Generator) -> np.ndarray:
    return g.uniform(-1, 1, int(dur * SR))


def _fft_filter(x: np.ndarray, mask_fn) -> np.ndarray:
    n = len(x)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    return np.fft.irfft(spec * mask_fn(f), n)


def lp(x: np.ndarray, fc: float, order: int = 2) -> np.ndarray:
    return _fft_filter(x, lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order)))


def hp(x: np.ndarray, fc: float, order: int = 2) -> np.ndarray:
    return _fft_filter(x, lambda f: 1.0 - 1.0 / np.sqrt(1.0 + (f / max(fc, 1e-3)) ** (2 * order)))


def bp(x: np.ndarray, f0: float, q: float = 4.0) -> np.ndarray:
    return _fft_filter(x, lambda f: 1.0 / (1.0 + (q * (f / f0 - f0 / np.maximum(f, 1e-3))) ** 2))


def decay(n: int, tau: float) -> np.ndarray:
    return np.exp(-np.arange(n) / (tau * SR))


def adsr(n: int, a: float, d: float, s: float, r: float) -> np.ndarray:
    e = np.ones(n) * s
    na, nd, nr = int(a * SR), int(d * SR), int(r * SR)
    if na > 0:
        e[:na] = np.linspace(0, 1, na)
    if nd > 0 and na + nd < n:
        e[na:na + nd] = np.linspace(1, s, nd)
    if nr > 0 and nr < n:
        e[n - nr:] = np.linspace(e[n - nr - 1] if n - nr - 1 >= 0 else s, 0, nr)
    return e


def fade(x: np.ndarray, fin: float = 0.005, fout: float = 0.02) -> np.ndarray:
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def pad_to(x: np.ndarray, n: int) -> np.ndarray:
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[:n] if len(x) < n else x[:n]


def mixdown(*parts, gains=None) -> np.ndarray:
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for i, p in enumerate(parts):
        out += pad_to(p, n) * (gains[i] if gains else 1.0)
    return out


def place(base: np.ndarray, x: np.ndarray, at: float = 0.0, gain: float = 1.0) -> np.ndarray:
    i = int(at * SR)
    if i >= len(base):
        return base
    end = min(len(base), i + len(x))
    base[i:end] += x[: end - i] * gain
    return base


def silence(dur: float) -> np.ndarray:
    return np.zeros(int(dur * SR))


def tremolo(x: np.ndarray, rate: float, depth: float) -> np.ndarray:
    t = np.arange(len(x)) / SR
    return x * (1 - depth + depth * (0.5 + 0.5 * np.sin(2 * np.pi * rate * t)))


def reverb(x: np.ndarray, wet: float = 0.3, size: float = 1.0) -> np.ndarray:
    out = x.copy()
    taps = [(0.031, 0.6), (0.047, 0.5), (0.071, 0.42), (0.113, 0.34), (0.173, 0.26), (0.251, 0.2), (0.347, 0.14)]
    pad = np.concatenate([x, np.zeros(int(0.5 * SR * size))])
    out = np.concatenate([out, np.zeros(len(pad) - len(out))])
    for d, g in taps:
        k = int(d * size * SR)
        out[k:] += pad[: len(out) - k] * g * wet
    return out


def normalize(x: np.ndarray, peak: float = 0.9) -> np.ndarray:
    m = np.max(np.abs(x)) if len(x) else 0
    return x * (peak / m) if m > 1e-9 else x


def loopable(x: np.ndarray, xfade: float = 0.5) -> np.ndarray:
    """Cross-fades the tail into the head so the sound loops without a click."""
    k = int(xfade * SR)
    head, tail = x[:k].copy(), x[-k:].copy()
    body = x[:-k].copy()
    w = np.linspace(0, 1, k)
    body[:k] = head * w + tail * (1 - w)
    return body


def bell(f: float, dur: float, brightness: float = 1.0) -> np.ndarray:
    ratios = [1.0, 2.76, 5.4, 8.93, 13.34]
    gains = [1.0, 0.55, 0.32, 0.18, 0.09]
    taus = [dur * 0.45, dur * 0.3, dur * 0.2, dur * 0.12, dur * 0.07]
    out = np.zeros(int(dur * SR))
    for r, g, tau in zip(ratios, gains, taus):
        if f * r > 9000:
            continue
        out += sine(f * r, dur) * decay(len(out), tau) * g * (brightness if r > 1 else 1)
    return out


def glass(f: float, dur: float) -> np.ndarray:
    out = np.zeros(int(dur * SR))
    for r, g in ((1, 1.0), (2.0, 0.4), (3.01, 0.25), (4.2, 0.15)):
        out += sine(f * r, dur) * decay(len(out), dur * 0.35 / r ** 0.5) * g
    return out * np.minimum(1, np.arange(len(out)) / (0.004 * SR))


def pluck(f: float, dur: float, bright: float = 1.0) -> np.ndarray:
    out = np.zeros(int(dur * SR))
    for h in range(1, 9):
        if f * h > 7000:
            break
        out += sine(f * h, dur) * decay(len(out), dur * 0.5 / (h ** 0.9)) / h ** (0.8 / bright)
    return out * np.minimum(1, np.arange(len(out)) / (0.003 * SR))


def padsound(freqs, dur: float, detune: float = 0.004, bright: float = 2500.0) -> np.ndarray:
    out = np.zeros(int(dur * SR))
    for f in freqs:
        for d in (-detune, 0.0, detune):
            out += saw(f * (1 + d), dur) * 0.3
    out = lp(out, bright)
    n = len(out)
    return out * adsr(n, 0.8, 0.3, 0.8, min(1.2, dur * 0.4)) / max(len(freqs), 1)


def thud(f: float = 70, dur: float = 0.35, g=None) -> np.ndarray:
    g = g or np.random.default_rng(1)
    n = int(dur * SR)
    body = sweep(f * 2.2, f * 0.7, dur) * decay(n, dur * 0.28)
    click = lp(noise(dur, g), 900) * decay(n, 0.018) * 0.7
    return body + click


def whoosh(lo: float, hi: float, dur: float, g, peak: float = 0.5) -> np.ndarray:
    """Noise whose band-pass centre sweeps lo -> peak -> hi over the sound (overlap-add of filtered frames)."""
    n = int(dur * SR)
    x = noise(dur, g)
    frame, hop = 1024, 512
    out = np.zeros(n + frame)
    win = np.hanning(frame)
    for start in range(0, max(1, n - frame // 2), hop):
        seg = np.zeros(frame)
        chunk = x[start:start + frame]
        seg[: len(chunk)] = chunk
        pos = min(1.0, start / max(n, 1))
        fc = lo + (hi - lo) * math.sin(math.pi * pos)
        filt = bp(seg * win, max(fc, 60.0), 2.0)
        out[start:start + frame] += filt
    env = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 1.5
    return out[:n] * env * peak * 3.0


def click(g, f: float = 3000, dur: float = 0.03) -> np.ndarray:
    n = int(dur * SR)
    return hp(noise(dur, g), f * 0.3) * decay(n, dur * 0.2)


def growl(f: float, dur: float, g) -> np.ndarray:
    n = int(dur * SR)
    wob = 1 + 0.06 * np.sin(2 * np.pi * 7 * tarr(dur))
    t = tarr(dur)
    phase = 2 * np.pi * np.cumsum(f * wob) / SR
    raw = np.sign(np.sin(phase)) * 0.5 + np.sin(phase) * 0.5
    raw = lp(raw, 900) + lp(noise(dur, g), 500) * 0.35
    return tremolo(raw, 22, 0.5) * adsr(n, 0.05, 0.1, 0.8, 0.2)


def voice(f0: float, dur: float, formants=((700, 3.0), (1200, 4.0)), g=None, glide=(1.0, 1.0), breath=0.1) -> np.ndarray:
    n = int(dur * SR)
    f = np.linspace(f0 * glide[0], f0 * glide[1], n)
    phase = 2 * np.pi * np.cumsum(f) / SR
    src = 2 * ((phase / (2 * np.pi)) % 1) - 1
    if g is not None:
        src = src + noise(dur, g) * breath
    out = np.zeros(n)
    for fc, q in formants:
        out += bp(src, fc, q)
    return out * adsr(n, 0.03, 0.05, 0.85, min(0.1, dur * 0.4))


def motor(f0: float, f1: float, dur: float, g) -> np.ndarray:
    n = int(dur * SR)
    f = np.linspace(f0, f1, n)
    phase = 2 * np.pi * np.cumsum(f) / SR
    x = np.sign(np.sin(phase)) * 0.3 + np.sin(phase) * 0.5 + np.sin(phase * 2) * 0.25
    return lp(x, 1800) + lp(noise(dur, g), 700) * 0.15


def water(dur: float, g, bright: float = 2200) -> np.ndarray:
    n = int(dur * SR)
    x = bp(noise(dur, g), bright, 1.2) * 0.6
    bubbles = np.zeros(n)
    for _ in range(int(dur * 14)):
        at = g.uniform(0, dur - 0.1)
        f = g.uniform(300, 1200)
        b = sweep(f, f * 2.2, 0.08) * decay(int(0.08 * SR), 0.03)
        place(bubbles, b, at, g.uniform(0.2, 0.6))
    return x * (0.7 + 0.3 * np.sin(2 * np.pi * 3 * tarr(dur))) + bubbles


def cat_meow(f0: float, dur: float, g, up: float = 1.25) -> np.ndarray:
    n = int(dur * SR)
    t = np.arange(n) / n
    contour = np.where(t < 0.35, 1 + (up - 1) * (t / 0.35), up - (up - 0.78) * ((t - 0.35) / 0.65))
    f = f0 * contour
    phase = 2 * np.pi * np.cumsum(f) / SR
    src = 2 * ((phase / (2 * np.pi)) % 1) - 1
    open_ = 0.5 + 0.5 * np.sin(np.pi * np.clip(t * 1.2, 0, 1))
    out = bp(src, 900, 2.5) * (0.6 + 0.4 * open_) + bp(src, 2300, 3.0) * 0.4 * open_
    out += lp(noise(dur, g), 3000) * 0.04
    return out * adsr(n, 0.04, 0.1, 0.85, 0.12)


def to_pcm(x: np.ndarray) -> np.ndarray:
    return np.clip(x, -1, 1)
