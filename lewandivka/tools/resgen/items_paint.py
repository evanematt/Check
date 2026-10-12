"""Painters of the item sprites (16x16, outlined, readable at hotbar size)."""
from __future__ import annotations

import math

from .paint import Tex, C, shade, mix, with_alpha
from . import palette as P

OUT = "#1b1d22"
PAINTERS = {}


def painter(item_id):
    def deco(fn):
        PAINTERS[item_id] = fn
        return fn
    return deco


def sprite() -> Tex:
    return Tex(16, 16)


def paper(t: Tex, x0, y0, x1, y1, base=P.PAPER):
    t.rect(x0, y0, x1, y1, base).frame(x0, y0, x1, y1, shade(base, 0.75))
    t.noise(base, 0.03, 7, area=(x0 + 1, y0 + 1, x1 - 1, y1 - 1))
    return t


@painter("district_notebook")
def district_notebook(i):
    t = sprite()
    t.rect(3, 2, 12, 14, "#3f6a4a").frame(3, 2, 12, 14, "#1f3a28")
    t.rect(4, 3, 4, 13, "#2a4a34")                          # spine
    t.rect(6, 4, 11, 7, "#e6dcc0").frame(6, 4, 11, 7, "#8a8068")   # label
    t.hline(7, 10, 5, "#4d4a42").hline(7, 9, 6, "#4d4a42")
    t.rect(11, 8, 12, 14, "#c9a24a")                        # elastic band
    t.rect(13, 3, 13, 13, "#e6dcc0").vline(14, 4, 12, "#bfb391")   # page edge
    t.outline(OUT)
    return t


@painter("district_token")
def district_token(i):
    t = sprite()
    t.circle(7.5, 7.5, 6, "#c9a24a").circle(7.5, 7.5, 4.6, "#e6c46a").circle(7.5, 7.5, 6, "#8a6a2a", filled=False)
    t.rect(5, 6, 10, 9, "#8a6a2a").rect(6, 5, 9, 5, "#8a6a2a")              # tram silhouette
    t.set(6, 7, "#ffe9a8").set(8, 7, "#ffe9a8").hline(5, 10, 10, "#8a6a2a")
    t.set(5, 4, "#fff3c8").set(4, 5, "#fff3c8")
    t.outline(OUT)
    return t


@painter("semky")
def semky(i):
    t = sprite()
    for y in range(3, 14):                                   # a paper cone
        half = (y - 2) * 0.55
        for x in range(int(8 - half), int(8 + half) + 1):
            t.set(x, y, "#c9c2ae" if (x + y) % 5 else "#b3ab95")
    t.line(8, 14, 8, 15, "#9a927c")
    t.hline(4, 11, 3, "#8a8068")
    for x, y in ((5, 2), (7, 1), (9, 2), (11, 2), (6, 3), (8, 3), (10, 3)):
        t.set(x, y, "#202225").set(x, y - 1 if y > 1 else y, "#e8e8e8")
    t.text("", 0, 0, "#000000")
    t.outline(OUT)
    return t


def note_base(mark) -> Tex:
    t = sprite()
    paper(t, 3, 2, 12, 13)
    t.set(12, 2, "#00000000").set(3, 13, "#00000000")
    mark(t)
    t.hline(5, 10, 10, "#7a7466").hline(5, 9, 11, "#7a7466")
    t.outline(OUT)
    return t


@painter("debtor_note")
def debtor_note(i):
    def mark(t):
        t.text("?", 6, 4, "#a8473f")
        t.set(11, 3, "#a8473f")
    return note_base(mark)


@painter("garage13_note")
def garage13_note(i):
    def mark(t):
        t.text("13", 5, 4, "#2f5a8a")
        t.line(4, 8, 11, 3, "#a8473f")
    return note_base(mark)


@painter("last_tram_note")
def last_tram_note(i):
    def mark(t):
        t.rect(5, 4, 10, 7, "#cdb86a").frame(5, 4, 10, 7, "#8a7a3a")
        t.set(6, 5, "#2a2e33").set(9, 5, "#2a2e33").hline(5, 10, 8, "#4d4a42")
    return note_base(mark)


@painter("magic_kettle")
def magic_kettle(i):
    t = sprite()
    t.ellipse(8, 10, 5, 4, "#3a9a8c").ellipse(8, 10, 3, 2, "#5ac8b8")
    t.rect(4, 12, 11, 13, "#2a6a60")
    t.rect(7, 5, 9, 6, "#2a6a60").set(8, 4, "#c9a24a")                     # lid and knob
    t.line(12, 9, 14, 6, "#3a9a8c").line(13, 9, 15, 6, "#2a6a60")          # spout
    t.line(3, 7, 2, 10, "#2a6a60").line(3, 6, 4, 6, "#2a6a60")             # handle
    t.set(5, 8, "#d8fff6").set(6, 8, "#d8fff6")
    t.set(14, 4, P.PINK).set(15, 2, P.PINK).set(13, 2, "#ffc0e0")          # steam
    t.outline(OUT)
    return t


@painter("package")
def package(i):
    t = sprite()
    t.rect(2, 5, 13, 13, "#b0804a").frame(2, 5, 13, 13, "#6e4a2a")
    t.noise("#b0804a", 0.05, 4, area=(3, 6, 12, 12))
    t.rect(2, 5, 13, 6, "#c9965a")
    t.vline(7, 5, 13, "#6e4a2a").vline(8, 5, 13, "#8a5e34").hline(2, 13, 9, "#6e4a2a")
    t.rect(10, 10, 12, 12, "#e6dcc0").set(11, 11, "#a8473f")
    t.set(7, 4, "#6e4a2a").set(8, 3, "#6e4a2a").set(6, 3, "#6e4a2a")        # string bow
    t.outline(OUT)
    return t


@painter("ticket_composter")
def ticket_composter(i):
    t = sprite()
    t.rect(3, 8, 12, 13, "#8d98a3").frame(3, 8, 12, 13, "#4d555e")
    t.rect(4, 9, 11, 10, "#bcc6cf")
    t.rect(5, 3, 10, 7, "#e6b422").frame(5, 3, 10, 7, "#8a6a14")            # yellow handle
    t.rect(7, 7, 8, 9, "#4d555e")
    t.rect(6, 11, 9, 12, "#14161a").set(7, 11, "#fffbe8")                  # punch hole
    t.outline(OUT)
    return t


@painter("chroma_tablet")
def chroma_tablet(i):
    t = sprite()
    # capsule drawn diagonally like the reference pill sprites
    for k in range(-5, 6):
        x, y = 8 + k, 8 - k
        for d in (-2, -1, 0, 1, 2):
            c = P.PINK if k < 0 else P.CYAN
            t.set(x + d, y + d, shade(c, 1.0 if abs(d) < 2 else 0.7))
            t.set(x + d + 1, y + d, shade(c, 1.15))
    t.line(6, 5, 9, 2, "#ffffff").line(7, 6, 10, 3, shade("#ffffff", 0.9))
    t.set(8, 8, "#fffbe8")
    t.outline(OUT)
    return t


@painter("lift_battery")
def lift_battery(i):
    t = sprite()
    t.rect(4, 4, 11, 14, "#3a7a3a").frame(4, 4, 11, 14, "#1f4a1f")
    t.rect(5, 5, 6, 13, "#5fd65f").rect(9, 5, 10, 13, "#2a5a2a")
    t.rect(6, 2, 9, 3, "#c8c8c8").frame(6, 2, 9, 3, "#6a6a6a")
    t.hline(5, 10, 8, "#1f4a1f").rect(7, 9, 8, 12, "#f6ee5a")
    t.text("", 0, 0, "#000000")
    t.outline(OUT)
    return t


@painter("cat_fish")
def cat_fish(i):
    t = sprite()
    t.ellipse(7, 8, 5.5, 3, "#8ab4d0").ellipse(7, 9, 4, 1.5, "#d8ecf6")
    t.polygon = None
    t.line(12, 8, 15, 5, "#6a94b0").line(12, 8, 15, 11, "#6a94b0").vline(15, 5, 11, "#6a94b0")
    t.set(3, 7, "#101820").set(3, 6, "#ffffff")
    t.line(6, 6, 9, 5, "#5a84a0")
    t.vline(5, 7, 9, "#5a84a0")
    t.outline(OUT)
    return t


def collar(base, tag, bell=False) -> Tex:
    t = sprite()
    t.ellipse(8, 8, 6, 4.5, base).ellipse(8, 8, 4, 2.5, "#00000000")
    for y in range(16):
        for x in range(16):
            if math.hypot((x - 8) / 4.0, (y - 8) / 2.5) < 1.0:
                t.set(x, y, "#00000000")
    t.set(8, 12, tag).set(7, 13, tag).set(9, 13, tag).set(8, 14, tag)
    if bell:
        t.circle(8, 13, 1.6, "#e6c46a")
    t.set(4, 6, shade(base, 1.4)).set(5, 5, shade(base, 1.4))
    t.outline(OUT)
    return t


@painter("collar_chinazik")
def collar_chinazik(i):
    return collar("#26282e", "#c8d0d8")


@painter("collar_metadonna")
def collar_metadonna(i):
    return collar("#8a8e94", P.PINK)


@painter("collar_decoy")
def collar_decoy(i):
    return collar("#b83a3a", "#e6c46a", bell=True)


@painter("water_core")
def water_core(i):
    t = sprite()
    t.circle(7.5, 7.5, 6, "#2a78c8").circle(7.5, 7.5, 5, "#46a8f0").circle(7.5, 7.5, 3, "#8ad8ff")
    t.set(5, 5, "#ffffff").set(6, 4, "#d8f6ff").set(4, 6, "#d8f6ff")
    t.line(9, 10, 11, 8, "#2a78c8").line(8, 11, 11, 10, "#2a78c8")
    t.outline(OUT)
    return t


def ticket(symbol, color) -> Tex:
    t = sprite()
    t.rect(2, 4, 13, 12, "#efe6c8").frame(2, 4, 13, 12, "#8a8068")
    t.vline(10, 5, 11, "#b8ad8c")
    for y in (5, 7, 9, 11):
        t.set(10, y, "#efe6c8")                                    # perforation
    t.symbol(symbol, 6, 8, 2, color)
    t.hline(11, 12, 6, "#7a7466").hline(11, 12, 8, "#7a7466")
    t.outline(OUT)
    return t


@painter("ticket_cross")
def ticket_cross(i):
    return ticket("cross", "#d8403a")


@painter("ticket_circle")
def ticket_circle(i):
    return ticket("circle", "#3a6ad8")


@painter("ticket_triangle")
def ticket_triangle(i):
    return ticket("triangle", "#d8b020")


@painter("ticket_square")
def ticket_square(i):
    return ticket("square", "#38b048")


def shard(c1, c2, c3) -> Tex:
    t = sprite()
    pts = [(8, 1), (12, 6), (10, 14), (6, 14), (3, 7)]
    for y in range(16):
        for x in range(16):
            # inside the polygon?
            inside = False
            j = len(pts) - 1
            for k in range(len(pts)):
                xi, yi = pts[k]
                xj, yj = pts[j]
                if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
                    inside = not inside
                j = k
            if inside:
                t.set(x, y, c1 if x < 7 else (c2 if x < 10 else c3))
    t.line(8, 2, 6, 12, "#ffffff").line(9, 3, 8, 8, shade("#ffffff", 0.85))
    t.outline(OUT)
    return t


@painter("ring_fragment_garage")
def ring_fragment_garage(i):
    return shard("#e0502a", "#f08a2a", "#f6ee5a")


@painter("ring_fragment_collector")
def ring_fragment_collector(i):
    return shard("#f6ee5a", "#5fd65f", "#3fe0c4")


@painter("ring_fragment_vortex")
def ring_fragment_vortex(i):
    return shard("#3fe0c4", "#3ad8f2", "#4a7cf0")


@painter("ring_fragment_conductor")
def ring_fragment_conductor(i):
    return shard("#4a7cf0", "#9a5cf2", "#ff72b8")


@painter("chromatic_charge")
def chromatic_charge(i):
    t = sprite()
    cols = [P.PINK, P.ORANGE, P.LEMON, P.GREEN, P.CYAN, P.VIOLET]
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6:
                a = math.atan2(y - 7.5, x - 7.5)
                c = cols[int(((a + math.pi) / (2 * math.pi)) * len(cols)) % len(cols)]
                t.set(x, y, shade(c, 1.2 - d / 12))
    t.circle(7.5, 7.5, 2.2, "#ffffff").circle(7.5, 7.5, 3.4, "#ffffff", filled=False)
    t.outline(OUT)
    return t


@painter("mundane_sock")
def mundane_sock(i):
    t = sprite()
    t.rect(5, 2, 10, 7, "#b8bcc2")
    t.rect(5, 2, 10, 3, "#e8e8e8")
    t.line(5, 7, 5, 11, "#b8bcc2").line(6, 8, 6, 12, "#b8bcc2")
    t.rect(5, 8, 11, 12, "#b8bcc2").rect(8, 12, 13, 13, "#b8bcc2")
    t.rect(11, 9, 13, 12, "#9aa0a8")
    t.hline(5, 10, 5, "#9aa0a8").hline(5, 10, 7, "#9aa0a8")
    t.set(7, 10, "#8a9098").set(9, 9, "#8a9098")
    t.outline(OUT)
    return t


def compass_frame(angle_deg: float) -> Tex:
    t = sprite()
    t.circle(7.5, 7.5, 7, "#c9a24a").circle(7.5, 7.5, 5.8, "#2a1f3a").circle(7.5, 7.5, 7, "#7a5a1a", filled=False)
    for k in range(4):
        a = math.radians(k * 90)
        t.set(int(round(7.5 + math.sin(a) * 5)), int(round(7.5 - math.cos(a) * 5)), "#8a7ab0")
    a = math.radians(angle_deg)
    nx, ny = 7.5 + math.sin(a) * 5, 7.5 - math.cos(a) * 5
    t.line(7, 7, int(round(nx)), int(round(ny)), "#ff72b8").line(8, 8, int(round(nx)), int(round(ny)), "#ff3a9a")
    sx, sy = 7.5 - math.sin(a) * 4, 7.5 + math.cos(a) * 4
    t.line(7, 7, int(round(sx)), int(round(sy)), "#3ad8f2")
    t.set(7, 7, "#ffffff").set(8, 8, "#ffffff").set(5, 3, "#8a7ab0")
    t.outline(OUT)
    return t


COMPASS_FRAMES = 16


@painter("chromatic_compass")
def chromatic_compass(i):
    return compass_frame(0)


def paint_item(item: dict) -> Tex:
    fn = PAINTERS.get(item["id"])
    if fn is None:
        raise KeyError(f"no painter for item {item['id']}")
    return fn(item)
