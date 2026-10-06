"""GUI, HUD, sky and icon textures."""
from __future__ import annotations

import math
import random

from .paint import Tex, shade, mix, with_alpha
from . import palette as P
from .spec import ASSETS


def notebook() -> Tex:
    """256x256: the open notebook page (192x160) plus small icons below it."""
    t = Tex(256, 256)
    # cover edge and paper
    t.rect(0, 0, 191, 159, "#5a4a35")
    t.rect(3, 3, 188, 156, "#e6dcc0")
    t.noise("#e6dcc0", 0.025, 3, area=(3, 3, 188, 156))
    # ruled lines and the red margin
    for y in range(24, 152, 10):
        t.hline(8, 184, y, "#b8c4d4")
    t.vline(26, 4, 155, "#d8a0a0")
    t.vline(27, 4, 155, "#d8a0a0")
    # binding holes and rings
    for y in (24, 80, 136):
        t.circle(10, y, 3, "#5a4a35")
        t.circle(10, y, 2, "#2a2018")
    # taped corner, coffee ring and a few worn spots
    t.rect(168, 3, 188, 9, with_alpha("#d8c880", 255)).hline(168, 188, 3, "#b8a860")
    t.circle(150, 120, 11, "#c8b48a", filled=False)
    t.circle(150, 120, 10, "#d6c39a", filled=False)
    rnd = random.Random(7)
    for _ in range(60):
        t.set(rnd.randint(4, 187), rnd.randint(4, 155), "#d0c4a4")
    # page shading at the right/bottom
    for i in range(4):
        t.vline(188 - i, 4, 155, shade("#e6dcc0", 0.95 - i * 0.03))
        t.hline(4, 188, 156 - i, shade("#e6dcc0", 0.95 - i * 0.03))
    t.frame(0, 0, 191, 159, "#2a2018")
    # icons (16x16) from y = 176
    def icon(x, draw):
        i = Tex(16, 16)
        draw(i)
        t.blit(i, x, 176)
    icon(0, lambda i: (i.line(3, 8, 6, 12, "#2a8a3a"), i.line(6, 12, 13, 3, "#2a8a3a"), i.line(3, 9, 6, 13, "#38b048"), i.line(6, 13, 13, 4, "#38b048")))
    icon(16, lambda i: (i.circle(7.5, 7.5, 4, "#e6b422"), i.circle(7.5, 7.5, 2, "#fff0a8")))
    icon(32, lambda i: (i.symbol("cross", 8, 8, 4, "#a8473f")))
    icon(48, lambda i: (i.circle(6.5, 6.5, 4.5, "#4a6a8a", filled=False), i.line(10, 10, 14, 14, "#4a6a8a"), i.circle(6.5, 6.5, 3, "#a8c8e8")))
    icon(64, lambda i: (i.rect(4, 7, 11, 13, "#c9a24a"), i.frame(4, 7, 11, 13, "#7a5a1a"), i.circle(7.5, 6, 3, "#7a5a1a", filled=False), i.set(7, 10, "#2a2018")))
    icon(80, lambda i: (i.circle(7.5, 7.5, 5, "#d8d8d8", filled=False), i.circle(7.5, 7.5, 1.5, "#d8d8d8"), i.line(7, 7, 7, 3, "#d8d8d8")))
    icon(96, lambda i: (i.text("?", 6, 4, "#4a6a8a"), i.frame(2, 2, 12, 12, "#4a6a8a")))
    return t


def abilities() -> Tex:
    """64x32: dash, spring, glider (colour) in the first row, the same greyed out in the second."""
    t = Tex(64, 32)

    def dash(c) -> Tex:
        i = Tex(16, 16)
        i.arrow("right", 9, 8, 9, c)
        for y in (3, 8, 13):
            i.hline(1, 4, y, shade(c, 0.8))
        i.outline("#1b1d22")
        return i

    def spring(c) -> Tex:
        i = Tex(16, 16)
        for k, y in enumerate(range(3, 14, 2)):
            i.hline(4 if k % 2 == 0 else 5, 11 if k % 2 == 0 else 10, y, c)
        i.arrow("up", 8, 3, 5, c)
        i.outline("#1b1d22")
        return i

    def glider(c) -> Tex:
        i = Tex(16, 16)
        i.line(1, 7, 8, 2, c).line(8, 2, 14, 7, c)
        i.line(1, 7, 4, 9, c).line(4, 9, 8, 7, c).line(8, 7, 12, 9, c).line(12, 9, 14, 7, c)
        i.line(8, 7, 8, 13, shade(c, 0.8))
        i.rect(7, 13, 9, 14, shade(c, 0.7))
        i.outline("#1b1d22")
        return i

    for k, (fn, col) in enumerate(((dash, "#4ac8f2"), (spring, "#5fd65f"), (glider, "#f6c84a"))):
        t.blit(fn(col), k * 16, 0)
        t.blit(fn("#7a7d84"), k * 16, 16)
    return t


def hud() -> Tex:
    """128x32: bar frame (0,0..63,7), bar fill (0,8..63,15), small icons for sync/charge/checkpoint below."""
    t = Tex(128, 32)
    t.rect(0, 0, 63, 7, "#1b1d22").frame(0, 0, 63, 7, "#8d9096").frame(1, 1, 62, 6, "#2a2e33")
    for x in range(2, 62):
        t.vline(x, 8, 15, mix(P.PINK, P.CYAN, (x - 2) / 60))
    t.hline(2, 61, 8, "#ffffff")
    ch = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.5:
                a = math.atan2(y - 7.5, x - 7.5)
                cols = [P.PINK, P.ORANGE, P.LEMON, P.GREEN, P.CYAN, P.VIOLET]
                ch.set(x, y, shade(cols[int(((a + math.pi) / (2 * math.pi)) * 6) % 6], 1.15 - d / 14))
    ch.circle(7.5, 7.5, 2.2, "#ffffff")
    ch.outline("#1b1d22")
    t.blit(ch, 0, 16)
    sync = Tex(16, 16)
    sync.circle(7.5, 7.5, 6, "#f4f4f4", filled=False)
    sync.line(7, 7, 7, 3, "#f4f4f4").line(7, 7, 10, 9, "#f4f4f4")
    sync.outline("#1b1d22")
    t.blit(sync, 16, 16)
    cp = Tex(16, 16)
    cp.rect(7, 2, 8, 12, "#c8c8c8").rect(5, 12, 10, 13, "#6a6a6a").rect(9, 2, 13, 5, P.PINK)
    cp.outline("#1b1d22")
    t.blit(cp, 32, 16)
    return t


def sun_glow() -> Tex:
    """64x64 white radial glow (tinted by the sky renderer)."""
    t = Tex(64, 64)
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5) / 31.5
            if d <= 1:
                a = max(0.0, 1 - d) ** 1.6
                core = 1.0 if d < 0.28 else 0.0
                t.set(x, y, (255, 255, 255, int(255 * min(1.0, a + core))))
    return t


def star() -> Tex:
    t = Tex(8, 8)
    t.set(3, 3, "#ffffff").set(4, 3, "#ffffff").set(3, 4, "#ffffff").set(4, 4, "#ffffff")
    t.set(3, 1, "#ffffff80").set(4, 1, "#ffffff80").set(3, 6, "#ffffff80").set(4, 6, "#ffffff80")
    t.set(1, 3, "#ffffff80").set(1, 4, "#ffffff80").set(6, 3, "#ffffff80").set(6, 4, "#ffffff80")
    return t


def icon() -> Tex:
    """128x128 mod icon: a dusk tram stop with a rainbow ring above it."""
    t = Tex(128, 128)
    for y in range(128):
        c = mix("#3a2a5a", "#ff8a6a", y / 128)
        t.hline(0, 127, y, c)
    # rainbow ring
    cols = ["#e04a4a", "#f09a3a", "#f6ee5a", "#5fd65f", "#3ad8f2", "#4a7cf0", "#9a5cf2"]
    for k, c in enumerate(cols):
        for deg in range(200, 341):
            a = math.radians(deg)
            r = 52 - k * 3
            t.set(int(64 + math.cos(a) * r), int(70 + math.sin(a) * r), c)
            t.set(int(64 + math.cos(a) * r), int(71 + math.sin(a) * r), c)
    # suns
    for x, y, r in ((20, 22, 6), (46, 12, 4), (100, 24, 5)):
        t.circle(x, y, r, "#fff3a8")
        t.circle(x, y, r + 2, "#ffe27a", filled=False)
    # ground and tram stop
    t.rect(0, 100, 127, 127, "#3c3e42")
    t.rect(0, 100, 127, 103, "#5e6266")
    t.rect(30, 70, 98, 74, "#5e6266")
    for x in (34, 62, 92):
        t.rect(x, 74, x + 2, 100, "#2a2e33")
    t.rect(34, 74, 94, 78, "#a8473f")
    t.rect(46, 84, 76, 100, "#c93a3a").frame(46, 84, 76, 100, "#7a1f1f")
    for x in (50, 58, 66):
        t.rect(x, 88, x + 4, 94, "#ffd98a")
    t.rect(20, 98, 107, 99, "#8d9096")
    # a small black cat on the platform
    t.rect(100, 90, 108, 99, "#14161a")
    t.rect(106, 85, 110, 90, "#14161a")
    t.set(106, 84, "#14161a").set(110, 84, "#14161a").set(109, 87, "#d8c84a")
    return t


def generate() -> int:
    notebook().save(ASSETS / "textures" / "gui" / "notebook.png")
    abilities().save(ASSETS / "textures" / "gui" / "abilities.png")
    hud().save(ASSETS / "textures" / "gui" / "hud.png")
    sun_glow().save(ASSETS / "textures" / "environment" / "sun_glow.png")
    star().save(ASSETS / "textures" / "environment" / "star.png")
    icon().save(ASSETS / "icon.png")
    return 6
