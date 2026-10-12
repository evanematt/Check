"""Small pixel-art toolkit: every texture of the mod is painted by code so the whole art set is reproducible
and original (no third-party assets are used or copied)."""
from __future__ import annotations

import math
import random
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image

Color = tuple  # (r, g, b, a)


def C(value) -> Color:
    """'#rrggbb', 'rrggbb', (r, g, b) or (r, g, b, a) -> (r, g, b, a)."""
    if isinstance(value, str):
        v = value.lstrip("#")
        if len(v) == 6:
            return (int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16), 255)
        if len(v) == 8:
            return (int(v[0:2], 16), int(v[2:4], 16), int(v[4:6], 16), int(v[6:8], 16))
        raise ValueError(value)
    if len(value) == 3:
        return (int(value[0]), int(value[1]), int(value[2]), 255)
    return (int(value[0]), int(value[1]), int(value[2]), int(value[3]))


def clamp(v: float, lo: int = 0, hi: int = 255) -> int:
    return max(lo, min(hi, int(round(v))))


def shade(c, k: float) -> Color:
    """Multiplies the brightness (k < 1 darker, k > 1 lighter); lightening mixes towards white a little."""
    r, g, b, a = C(c)
    if k >= 1:
        t = min(1.0, k - 1.0)
        return (clamp(r + (255 - r) * t * 0.6), clamp(g + (255 - g) * t * 0.6), clamp(b + (255 - b) * t * 0.6), a)
    return (clamp(r * k), clamp(g * k), clamp(b * k), a)


def mix(a, b, t: float) -> Color:
    a, b = C(a), C(b)
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(4))  # type: ignore[return-value]


def ramp(base, n: int = 5) -> list:
    """Dark-to-light ramp around a base colour (index n // 2 is the base)."""
    out = []
    for i in range(n):
        k = 0.55 + (i / (n - 1)) * (1.55 - 0.55)
        out.append(shade(base, k))
    return out


def with_alpha(c, a: int) -> Color:
    r, g, b, _ = C(c)
    return (r, g, b, a)


TRANSPARENT = (0, 0, 0, 0)

# ---------------------------------------------------------------------------------------------- fonts
_FONT_3X5 = {
    "0": ("111", "101", "101", "101", "111"), "1": ("010", "110", "010", "010", "111"),
    "2": ("111", "001", "111", "100", "111"), "3": ("111", "001", "111", "001", "111"),
    "4": ("101", "101", "111", "001", "001"), "5": ("111", "100", "111", "001", "111"),
    "6": ("111", "100", "111", "101", "111"), "7": ("111", "001", "010", "010", "010"),
    "8": ("111", "101", "111", "101", "111"), "9": ("111", "101", "111", "001", "111"),
    "#": ("101", "111", "101", "111", "101"), "?": ("111", "001", "011", "000", "010"),
    "!": ("010", "010", "010", "000", "010"), "-": ("000", "000", "111", "000", "000"),
    "A": ("010", "101", "111", "101", "101"), "B": ("110", "101", "110", "101", "110"),
    "P": ("110", "101", "110", "100", "100"), "T": ("111", "010", "010", "010", "010"),
    "X": ("101", "101", "010", "101", "101"), "N": ("110", "101", "101", "101", "101"),
    " ": ("000", "000", "000", "000", "000"), "Z": ("111", "001", "010", "100", "111"),
}

# 7-segment digits for the queue display (5 x 7)
_SEG = {
    "0": "abcdef", "1": "bc", "2": "abged", "3": "abgcd", "4": "fgbc", "5": "afgcd",
    "6": "afgedc", "7": "abc", "8": "abcdefg", "9": "abcdfg",
}


class Tex:
    """RGBA image with pixel-art drawing helpers. Coordinates are inclusive pixels."""

    def __init__(self, w: int = 16, h: int = 16, fill=TRANSPARENT):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w, h), C(fill))
        self.p = self.im.load()

    # ------------------------------------------------------------------ basics
    def set(self, x: int, y: int, c) -> "Tex":
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = C(c)
        return self

    def get(self, x: int, y: int) -> Color:
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[x, y]
        return TRANSPARENT

    def fill(self, c) -> "Tex":
        return self.rect(0, 0, self.w - 1, self.h - 1, c)

    def rect(self, x0: int, y0: int, x1: int, y1: int, c) -> "Tex":
        c = C(c)
        for y in range(max(0, y0), min(self.h - 1, y1) + 1):
            for x in range(max(0, x0), min(self.w - 1, x1) + 1):
                self.p[x, y] = c
        return self

    def frame(self, x0: int, y0: int, x1: int, y1: int, c) -> "Tex":
        self.hline(x0, x1, y0, c).hline(x0, x1, y1, c).vline(x0, y0, y1, c).vline(x1, y0, y1, c)
        return self

    def hline(self, x0: int, x1: int, y: int, c) -> "Tex":
        for x in range(min(x0, x1), max(x0, x1) + 1):
            self.set(x, y, c)
        return self

    def vline(self, x: int, y0: int, y1: int, c) -> "Tex":
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.set(x, y, c)
        return self

    def line(self, x0: int, y0: int, x1: int, y1: int, c) -> "Tex":
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.set(x0, y0, c)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy
        return self

    def circle(self, cx: float, cy: float, r: float, c, filled: bool = True) -> "Tex":
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x - cx, y - cy)
                if (filled and d <= r) or (not filled and r - 0.7 <= d <= r + 0.3):
                    self.p[x, y] = C(c)
        return self

    def ellipse(self, cx: float, cy: float, rx: float, ry: float, c) -> "Tex":
        for y in range(self.h):
            for x in range(self.w):
                if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0:
                    self.p[x, y] = C(c)
        return self

    def blit(self, other: "Tex", x: int, y: int, skip_transparent: bool = True) -> "Tex":
        for yy in range(other.h):
            for xx in range(other.w):
                c = other.p[xx, yy]
                if skip_transparent and c[3] == 0:
                    continue
                self.set(x + xx, y + yy, c)
        return self

    def copy(self) -> "Tex":
        t = Tex(self.w, self.h)
        t.im = self.im.copy()
        t.p = t.im.load()
        return t

    # ------------------------------------------------------------------ surfaces
    def noise(self, base, variance: float = 0.12, seed: int = 1, area=None, chunk: int = 1) -> "Tex":
        """Fills the area with the base colour plus brightness noise (the Minecraft look: slight, readable)."""
        rnd = random.Random(seed)
        x0, y0, x1, y1 = area if area else (0, 0, self.w - 1, self.h - 1)
        cells = {}
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                key = (x // chunk, y // chunk)
                if key not in cells:
                    cells[key] = 1.0 + rnd.uniform(-variance, variance)
                self.set(x, y, shade(base, cells[key]))
        return self

    def dither(self, c, density: float, seed: int = 1, area=None) -> "Tex":
        rnd = random.Random(seed)
        x0, y0, x1, y1 = area if area else (0, 0, self.w - 1, self.h - 1)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if rnd.random() < density:
                    self.set(x, y, c)
        return self

    def bevel(self, light: float = 1.18, dark: float = 0.78, area=None, depth: int = 1) -> "Tex":
        """Lights the top/left edges and darkens the bottom/right edges of the area."""
        x0, y0, x1, y1 = area if area else (0, 0, self.w - 1, self.h - 1)
        for d in range(depth):
            for x in range(x0 + d, x1 - d + 1):
                self.set(x, y0 + d, shade(self.get(x, y0 + d), light))
                self.set(x, y1 - d, shade(self.get(x, y1 - d), dark))
            for y in range(y0 + d + 1, y1 - d):
                self.set(x0 + d, y, shade(self.get(x0 + d, y), light))
                self.set(x1 - d, y, shade(self.get(x1 - d, y), dark))
        return self

    def outline(self, c, only_outer: bool = True) -> "Tex":
        """Draws an outline around the non-transparent pixels (sprite look)."""
        src = self.copy()
        for y in range(self.h):
            for x in range(self.w):
                if src.p[x, y][3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if src.get(x + dx, y + dy)[3] != 0:
                        self.p[x, y] = C(c)
                        break
        return self

    def tint(self, c, t: float) -> "Tex":
        for y in range(self.h):
            for x in range(self.w):
                px = self.p[x, y]
                if px[3]:
                    m = mix(px, c, t)
                    self.p[x, y] = (m[0], m[1], m[2], px[3])
        return self

    def replace(self, old, new) -> "Tex":
        old, new = C(old), C(new)
        for y in range(self.h):
            for x in range(self.w):
                if self.p[x, y] == old:
                    self.p[x, y] = new
        return self

    # ------------------------------------------------------------------ text and symbols
    def text(self, s: str, x: int, y: int, c, spacing: int = 1) -> "Tex":
        for ch in s:
            glyph = _FONT_3X5.get(ch.upper())
            if glyph is None:
                x += 4
                continue
            for gy, row in enumerate(glyph):
                for gx, bit in enumerate(row):
                    if bit == "1":
                        self.set(x + gx, y + gy, c)
            x += 3 + spacing
        return self

    def text_width(self, s: str, spacing: int = 1) -> int:
        return max(0, len(s) * (3 + spacing) - spacing)

    def text_center(self, s: str, cx: int, y: int, c) -> "Tex":
        return self.text(s, cx - self.text_width(s) // 2, y, c)

    def seven_seg(self, digit: str, x: int, y: int, c, w: int = 5, h: int = 9) -> "Tex":
        """One 7-segment digit of size w x h with its top-left corner at (x, y)."""
        segs = _SEG.get(digit, "")
        mid = y + h // 2
        if "a" in segs:
            self.hline(x + 1, x + w - 2, y, c)
        if "d" in segs:
            self.hline(x + 1, x + w - 2, y + h - 1, c)
        if "g" in segs:
            self.hline(x + 1, x + w - 2, mid, c)
        if "f" in segs:
            self.vline(x, y + 1, mid - 1, c)
        if "b" in segs:
            self.vline(x + w - 1, y + 1, mid - 1, c)
        if "e" in segs:
            self.vline(x, mid + 1, y + h - 2, c)
        if "c" in segs:
            self.vline(x + w - 1, mid + 1, y + h - 2, c)
        return self

    def symbol(self, name: str, cx: int, cy: int, r: int, c, outline=None) -> "Tex":
        """Cross / circle / triangle / square: the colour-independent channel of puzzles."""
        if outline is not None:
            self.symbol(name, cx + 1, cy, r, outline).symbol(name, cx - 1, cy, r, outline)
            self.symbol(name, cx, cy + 1, r, outline).symbol(name, cx, cy - 1, r, outline)
        if name == "cross":
            self.line(cx - r, cy - r, cx + r, cy + r, c).line(cx - r, cy + r, cx + r, cy - r, c)
            self.line(cx - r + 1, cy - r, cx + r, cy + r - 1, c).line(cx - r + 1, cy + r, cx + r, cy - r + 1, c)
        elif name == "circle":
            self.circle(cx, cy, r, c, filled=False)
            self.circle(cx, cy, r - 1, c, filled=False)
        elif name == "triangle":
            for i in range(r * 2 + 1):
                t = i / (r * 2)
                self.hline(int(round(cx - r * t)), int(round(cx + r * t)), cy - r + i, c) if i in (r * 2,) else None
            self.line(cx, cy - r, cx - r, cy + r, c).line(cx, cy - r, cx + r, cy + r, c).hline(cx - r, cx + r, cy + r, c)
            self.line(cx + 1, cy - r + 1, cx - r + 1, cy + r - 1, c).line(cx - 1, cy - r + 1, cx + r - 1, cy + r - 1, c)
        elif name == "square":
            self.frame(cx - r, cy - r, cx + r, cy + r, c).frame(cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, c)
        return self

    def arrow(self, direction: str, cx: int, cy: int, size: int, c) -> "Tex":
        """Chunky arrow pointing up/down/left/right."""
        for i in range(size + 1):
            for j in range(-i, i + 1):
                x, y = {"up": (cx + j, cy - size // 2 + i), "down": (cx + j, cy + size // 2 - i),
                        "left": (cx - size // 2 + i, cy + j), "right": (cx + size // 2 - i, cy + j)}[direction]
                self.set(x, y, c)
        stem = max(1, size // 3)
        for k in range(size):
            for w in range(-stem // 2, stem // 2 + 1):
                x, y = {"up": (cx + w, cy + k // 2), "down": (cx + w, cy - k // 2), "left": (cx + k // 2, cy + w), "right": (cx - k // 2, cy + w)}[direction]
                self.set(x, y, c)
        return self

    def glow(self, cx: float, cy: float, r: float, c, strength: float = 0.8) -> "Tex":
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x - cx, y - cy)
                if d <= r:
                    t = (1 - d / r) * strength
                    cur = self.p[x, y]
                    m = mix(cur if cur[3] else c, c, t)
                    self.p[x, y] = (m[0], m[1], m[2], max(cur[3], clamp(255 * t)))
        return self

    def stripes(self, c1, c2, width: int = 3, area=None, slope: int = 1) -> "Tex":
        """Diagonal hazard stripes."""
        x0, y0, x1, y1 = area if area else (0, 0, self.w - 1, self.h - 1)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c1 if ((x + slope * y) // width) % 2 == 0 else c2)
        return self

    def rivets(self, points: Iterable, c, hi=None) -> "Tex":
        for x, y in points:
            self.set(x, y, c)
            if hi is not None:
                self.set(x - 1, y - 1, hi)
        return self

    # ------------------------------------------------------------------ output
    def save(self, path) -> Path:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        self.im.save(path, optimize=True)
        return path


def sheet(textures: Sequence, cols: int = 8, scale: int = 4, pad: int = 2, bg=(60, 60, 70, 255)) -> Image.Image:
    """Contact sheet of several textures for visual review."""
    if not textures:
        return Image.new("RGBA", (1, 1))
    tw = max(t.w for t in textures)
    th = max(t.h for t in textures)
    rows = (len(textures) + cols - 1) // cols
    img = Image.new("RGBA", (cols * (tw * scale + pad) + pad, rows * (th * scale + pad) + pad), bg)
    for i, t in enumerate(textures):
        big = t.im.resize((t.w * scale, t.h * scale), Image.NEAREST)
        img.alpha_composite(big, (pad + (i % cols) * (tw * scale + pad), pad + (i // cols) * (th * scale + pad)))
    return img
