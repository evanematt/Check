"""Painters of every block texture. Each painter returns {texture name: Tex}."""
from __future__ import annotations

from .paint import Tex, C, shade, mix, ramp, with_alpha, TRANSPARENT
from .palette import *  # noqa: F401,F403
from . import palette as P
from .spec import combos, combo_name, prop

# ------------------------------------------------------------------------------------------------ materials


def concrete(base=P.CONCRETE, seed=1, w=16, h=16) -> Tex:
    t = Tex(w, h).noise(base, 0.06, seed, chunk=1)
    t.dither(shade(base, 0.82), 0.07, seed + 1).dither(shade(base, 1.12), 0.05, seed + 2)
    return t


def metal(base=P.STEEL, seed=1, w=16, h=16) -> Tex:
    t = Tex(w, h).noise(base, 0.035, seed)
    for y in range(0, h, 2):
        t.hline(0, w - 1, y, shade(t.get(0, y), 1.06))
    return t


def planks(base=P.WOOD, seed=1) -> Tex:
    t = Tex(16, 16).noise(base, 0.05, seed)
    for y in (3, 7, 11, 15):
        t.hline(0, 15, y, shade(base, 0.7))
    for y, x in ((0, 5), (4, 11), (8, 3), (12, 9)):
        t.vline(x, y, y + 2, shade(base, 0.78))
    return t


def bolt_corners(t: Tex, inset=2, c=None, hi=None):
    c = c or shade(P.STEEL_DARK, 0.9)
    hi = hi or shade(P.STEEL_LIGHT, 1.1)
    pts = [(inset, inset), (t.w - 1 - inset, inset), (inset, t.h - 1 - inset), (t.w - 1 - inset, t.h - 1 - inset)]
    return t.rivets(pts, c, hi)


def cardboard(seed=3, base=P.CARDBOARD) -> Tex:
    t = Tex(16, 16).noise(base, 0.05, seed)
    for y in (5, 10):
        t.hline(0, 15, y, shade(base, 0.82))
    t.rect(6, 0, 9, 15, shade(base, 1.18))   # packing tape
    t.vline(6, 0, 15, shade(base, 1.3)).vline(9, 0, 15, shade(base, 0.9))
    return t


# ------------------------------------------------------------------------------------------------ painters
PAINTERS = {}


def painter(block_id):
    def deco(fn):
        PAINTERS[block_id] = fn
        return fn
    return deco


def combo_iter(block):
    return combos(block)


@painter("abandoned_kiosk")
def abandoned_kiosk(b):
    side = Tex(16, 16).noise("#6f7f76", 0.05, 11)
    for y in range(0, 16, 4):
        side.hline(0, 15, y, shade("#6f7f76", 0.8))
    top = concrete("#6e7378", 12)
    top.frame(0, 0, 15, 15, shade("#6e7378", 0.7))
    front = Tex(16, 16).noise("#6f7f76", 0.05, 13)
    front.rect(0, 0, 15, 3, "#a8473f")                           # awning
    for x in range(0, 16, 4):
        front.rect(x, 0, x + 1, 3, "#e6dcc0")
    front.hline(0, 15, 4, shade("#a8473f", 0.6))
    front.rect(2, 6, 13, 12, "#16202a")                         # service window
    front.rect(3, 7, 12, 11, "#ffd98a")
    front.frame(2, 6, 13, 12, "#3b2f23")
    front.rect(3, 12, 12, 12, "#b58b55")                        # counter shelf
    front.text("24", 5, 14, "#e6dcc0") if False else None
    front.hline(2, 13, 13, "#3b2f23")
    front.bevel(depth=1)
    return {"abandoned_kiosk_front": front, "abandoned_kiosk_side": side, "abandoned_kiosk_top": top}


@painter("kiosk_foundation")
def kiosk_foundation(b):
    t = concrete("#9c9d96", 21)
    t.frame(0, 0, 15, 15, "#c7b86a").frame(1, 1, 14, 14, shade("#c7b86a", 0.8))
    for i in range(3, 14, 4):
        t.set(i, 2, "#c7b86a").set(i, 13, "#c7b86a").set(2, i, "#c7b86a").set(13, i, "#c7b86a")
    t.text("K", 6, 5, shade("#c7b86a", 0.9)) if False else None
    t.rect(5, 5, 10, 10, shade("#9c9d96", 0.88)).frame(5, 5, 10, 10, "#c7b86a")
    return {"kiosk_foundation": t}


@painter("supply_stash")
def supply_stash(b):
    out = {}
    for combo in combo_iter(b):
        name = combo_name(b, combo)
        looted = combo["looted"] == "true"
        t = Tex(16, 16).noise(P.WOOD_DARK, 0.05, 31)
        t.rect(1, 3, 14, 15, planks(P.WOOD, 32).im.getpixel((3, 3)))
        t.noise(P.WOOD, 0.06, 33, area=(1, 3, 14, 15))
        for y in (6, 10, 14):
            t.hline(1, 14, y, shade(P.WOOD, 0.62))
        t.rect(0, 2, 15, 3, shade(P.STEEL, 0.8))                       # metal rim
        t.rivets([(2, 2), (13, 2)], shade(P.STEEL_DARK, 1.0))
        if looted:
            t.rect(3, 4, 12, 8, "#1c1712")                              # empty, dark inside
            t.hline(3, 12, 4, "#33281d")
        else:
            t.rect(3, 4, 12, 8, "#2b3a2a")                              # junk heap
            t.dither("#c9b36a", 0.35, 7, area=(3, 4, 12, 8)).dither("#a8473f", 0.2, 8, area=(3, 4, 12, 8))
            t.rect(5, 3, 7, 4, "#e6dcc0")                               # a sticking-out paper
            t.set(10, 3, "#ff72b8")
        t.bevel(1.12, 0.8, area=(0, 2, 15, 15))
        out[name] = t
    return out


@painter("clue_prop")
def clue_prop(b):
    out = {}
    for combo in combo_iter(b):
        k = int(combo["kind"])
        t = Tex(16, 16)
        if k == 0:    # empty tea package
            t.rect(5, 4, 10, 12, "#6a8f4a").frame(5, 4, 10, 12, "#35502a")
            t.rect(6, 7, 9, 9, "#e6dcc0").set(7, 8, "#a8473f")
            t.hline(5, 10, 5, "#c9b36a")
        elif k == 1:  # tea glass in a holder
            t.rect(6, 5, 9, 11, "#d3a15a").rect(7, 6, 8, 10, "#b9772e")
            t.frame(5, 4, 10, 12, "#8d9aa3").hline(5, 10, 12, "#5e6266")
            t.set(12, 6, "#8d9aa3").set(12, 9, "#8d9aa3").vline(13, 7, 8, "#8d9aa3")
        elif k == 2:  # child's mitten and toy
            t.ellipse(7, 9, 4, 3, "#cf5c4e").rect(10, 8, 12, 9, "#cf5c4e")
            t.ellipse(7, 9, 2, 1, "#e6dcc0")
            t.rect(3, 4, 5, 6, "#4a7cf0").set(4, 3, "#f6ee5a")
        elif k == 3:  # trash bag
            t.ellipse(8, 9, 5, 4, "#2a2e33").ellipse(7, 8, 3, 2, "#3d434a")
            t.set(8, 4, "#2a2e33").set(7, 5, "#2a2e33").set(9, 5, "#2a2e33")
            t.set(11, 10, "#c9b36a").set(5, 11, "#a8473f")
        else:         # garage rooftop: feather and crushed pack
            t.rect(4, 8, 9, 11, "#d9d3c0").frame(4, 8, 9, 11, "#8c866f")
            t.line(10, 4, 13, 9, "#e6e6e6").line(11, 4, 13, 8, "#bdbdbd")
            t.set(6, 9, "#a8473f")
        t.outline("#20242a")
        out[combo_name(b, combo)] = t
    return out


@painter("lore_note")
def lore_note(b):
    t = Tex(16, 16)
    t.rect(2, 1, 13, 14, P.PAPER).frame(2, 1, 13, 14, P.PAPER_DARK)
    t.noise(P.PAPER, 0.04, 41, area=(3, 2, 12, 13))
    for y in (4, 6, 8, 10):
        t.hline(4, 11 if y != 10 else 8, y, "#4d4a42")
    t.set(8, 0, "#a8473f").set(7, 0, "#a8473f").rect(7, 0, 8, 1, "#a8473f")   # pin
    t.rect(2, 14, 13, 14, shade(P.PAPER_DARK, 0.8))
    return {"lore_note": t}


@painter("garage_plate")
def garage_plate(b):
    out = {}
    for combo in combo_iter(b):
        n = int(combo["plate"])
        t = Tex(16, 16)
        base = "#2f5a8a" if n not in (32, 33, 34, 35) else "#c9b36a"
        if n == 36:
            base = "#a8473f"
        t.rect(1, 3, 14, 12, base).frame(1, 3, 14, 12, shade(base, 0.6))
        t.bevel(1.2, 0.8, area=(1, 3, 14, 12))
        t.rivets([(2, 4), (13, 4), (2, 11), (13, 11)], shade(base, 0.55))
        ink = "#f2f2e8" if base != "#c9b36a" else "#2a2a2a"
        if n in (32, 33, 34, 35):
            d = {32: "right", 33: "left", 34: "up", 35: "down"}[n]
            t.arrow(d, 8, 7, 7, ink)
        elif n == 36:
            t.text("404", 3, 6, ink)
        elif n == 37:
            t.symbol("cross", 8, 7, 3, ink)
        elif n == 38:
            t.text("P", 6, 5, ink)
        else:
            s = str(n)
            if n >= 10:
                t.text(s, 8 - t.text_width(s) // 2, 6, ink)
            else:
                t.text(s, 7, 5, ink)
        out[combo_name(b, combo)] = t
    return out


def panel_front(kind: str, lit: bool, seed=50) -> Tex:
    t = metal("#6c747c", seed)
    t.frame(0, 0, 15, 15, "#2f343a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
    bolt_corners(t, 2)
    glow = "#ffd24a" if lit else "#4a4030"
    if kind == "breaker":
        for i in range(4):
            x = 2 + i * 3
            t.rect(x, 4, x + 1, 11, "#22262b")
            t.rect(x, 4 if lit else 8, x + 1, 7 if lit else 11, "#d8d8d0" if lit else "#a8473f")
        t.rect(2, 12, 13, 13, glow)
    elif kind == "console":
        t.rect(2, 2, 13, 7, "#10161a").frame(2, 2, 13, 7, "#2f343a")
        t.rect(3, 3, 12, 6, "#1d5a3a" if lit else "#16221c")
        if lit:
            t.hline(4, 9, 4, "#6dffa0").hline(4, 7, 5, "#6dffa0")
        for i in range(3):
            t.rect(3 + i * 4, 9, 5 + i * 4, 12, "#c9b36a" if i != 1 else "#a8473f")
        t.rect(2, 13, 13, 13, glow)
    else:  # switchbox
        t.rect(3, 3, 12, 12, "#4a4f55").frame(3, 3, 12, 12, "#22262b")
        t.rect(7, 4, 8, 11, "#22262b")
        t.rect(6, 4 if lit else 8, 9, 7 if lit else 11, "#d8d8d0" if lit else "#a8473f")
        t.set(4, 4, glow).set(11, 4, glow)
    return t


@painter("garage_power_panel")
def garage_power_panel(b):
    out = {}
    for combo in combo_iter(b):
        out[combo_name(b, combo) + "_front"] = panel_front(combo["kind"], combo["lit"] == "true", 50)
    out["garage_power_panel_side"] = metal("#59616a", 51).frame(0, 0, 15, 15, "#2f343a")
    out["garage_power_panel_top"] = metal("#59616a", 52).frame(0, 0, 15, 15, "#2f343a")
    return out


@painter("heavy_lever")
def heavy_lever(b):
    out = {}
    for combo in combo_iter(b):
        up = combo["powered"] == "true"
        t = metal("#59616a", 60)
        t.frame(0, 0, 15, 15, "#2f343a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(5, 5, 10, 11, "#22262b")
        t.rect(7, 2 if up else 8, 8, 8 if up else 14, "#b9c0c7")        # shaft
        t.rect(6, 1 if up else 13, 9, 3 if up else 15, "#a8473f")       # knob
        t.rect(2, 13 if up else 2, 3, 14 if up else 3, "#6dffa0" if up else "#6a4a3a")
        out[combo_name(b, combo) + "_front"] = t
    out["heavy_lever_side"] = metal("#59616a", 61).frame(0, 0, 15, 15, "#2f343a")
    out["heavy_lever_top"] = metal("#59616a", 62).frame(0, 0, 15, 15, "#2f343a")
    return out


@painter("ticket_validator")
def ticket_validator(b):
    out = {}
    for combo in combo_iter(b):
        sym = combo["symbol"]
        active = combo["active"] == "true"
        t = metal("#6a5d4a", 70)                       # old brass-grey casing
        t.frame(0, 0, 15, 15, "#2b241a").bevel(1.18, 0.78, area=(1, 1, 14, 14))
        t.rect(3, 2, 12, 9, "#14161a").frame(3, 2, 12, 9, "#2b241a")
        face = "#2fd37a" if active else "#3a3d44"
        t.rect(4, 3, 11, 8, face if sym == "none" else "#101216")
        if sym != "none":
            t.symbol(sym, 7, 5, 2, "#7dffb0" if active else "#8d9096")
        t.rect(5, 11, 10, 12, "#0c0d10")               # ticket slot
        t.hline(5, 10, 13, "#c9b36a")
        t.set(2, 11, "#6dffa0" if active else "#7a2a24").set(13, 11, "#6dffa0" if active else "#7a2a24")
        out[combo_name(b, combo) + "_front"] = t
    out["ticket_validator_side"] = metal("#5a4f3e", 71).frame(0, 0, 15, 15, "#2b241a")
    out["ticket_validator_top"] = metal("#5a4f3e", 72).frame(0, 0, 15, 15, "#2b241a")
    return out


@painter("artifact_pedestal")
def artifact_pedestal(b):
    out = {}
    stone = concrete("#7a7f86", 80)
    stone.bevel(1.15, 0.78)
    out["artifact_pedestal_side"] = stone
    for combo in combo_iter(b):
        kind, filled = combo["kind"], combo["filled"] == "true"
        t = concrete("#6b7078", 81).frame(0, 0, 15, 15, "#454a52")
        t.rect(2, 2, 13, 13, "#2b2f36").frame(2, 2, 13, 13, "#8d939c")
        if filled:
            t.glow(8, 8, 7, "#ffe27a", 0.7)
        c = "#e6dcc0" if not filled else "#fff6c8"
        if kind == "kettle":
            t.ellipse(8, 9, 4, 3, c).rect(11, 7, 13, 8, c).vline(5, 5, 8, c).hline(5, 11, 5, c)
        elif kind == "package":
            t.rect(4, 5, 11, 11, c).frame(4, 5, 11, 11, shade(c, 0.7)).vline(7, 5, 11, shade(c, 0.7)).hline(4, 11, 8, shade(c, 0.7))
        elif kind == "composter":
            t.rect(4, 5, 11, 10, c).rect(6, 10, 9, 12, c).set(7, 7, "#2b2f36").set(8, 8, "#2b2f36")
        else:
            t.circle(8, 8, 4, c).circle(8, 8, 2, shade(c, 0.7)).set(8, 8, c)
        out[combo_name(b, combo) + "_top"] = t
    return out


@painter("garage_lift")
def garage_lift(b):
    t = Tex(16, 16).noise("#5e6670", 0.04, 90)
    t.rect(0, 0, 15, 2, "#1b1e22").rect(0, 13, 15, 15, "#1b1e22")
    t.stripes("#e6b422", "#1b1e22", 3, area=(0, 0, 15, 2)).stripes("#e6b422", "#1b1e22", 3, area=(0, 13, 15, 15))
    t.rect(2, 4, 13, 11, "#4a525b").frame(2, 4, 13, 11, "#2c3138")
    for x in (4, 7, 10):
        t.vline(x, 5, 10, "#343a42")
    t.bevel(1.1, 0.8)
    return {"garage_lift": t}


@painter("colored_portal")
def colored_portal(b):
    frames = []
    for f in range(16):
        t = Tex(16, 16)
        for y in range(16):
            for x in range(16):
                import math
                v = (math.sin((x + f) * 0.5) + math.cos((y - f) * 0.45) + math.sin((x + y + f * 2) * 0.3)) / 3
                hue = (v * 0.5 + 0.5)
                cols = [P.PINK, P.MAGENTA, P.VIOLET, P.CYAN, P.TURQUOISE, P.LEMON]
                c = cols[int(hue * (len(cols) - 0.01))]
                t.set(x, y, with_alpha(c, 170))
        frames.append(t)
    strip = Tex(16, 16 * len(frames))
    for i, f in enumerate(frames):
        strip.blit(f, 0, i * 16, skip_transparent=False)
    return {"colored_portal": strip}


@painter("portal_frame")
def portal_frame(b):
    t = concrete("#2a2640", 100)
    t.noise("#2a2640", 0.1, 101)
    t.frame(0, 0, 15, 15, "#14102a")
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        t.set(x, y, P.PINK)
    t.hline(5, 10, 8, P.CYAN).vline(8, 5, 10, P.CYAN)
    t.set(8, 8, P.LEMON)
    return {"portal_frame": t}


def crystal_face(color, seed):
    base = P.HUE[color]
    t = Tex(16, 16).noise(shade(base, 0.8), 0.1, seed)
    # facets
    t.rect(0, 0, 15, 15, shade(base, 0.8)).noise(shade(base, 0.85), 0.08, seed)
    t.line(0, 15, 15, 0, shade(base, 1.35)).line(0, 10, 10, 0, shade(base, 1.2)).line(5, 15, 15, 5, shade(base, 0.6))
    t.rect(3, 3, 5, 5, shade(base, 1.5)).set(11, 11, shade(base, 1.6))
    t.frame(0, 0, 15, 15, shade(base, 0.55))
    return t


@painter("chromatic_crystal")
def chromatic_crystal(b):
    out = {}
    for combo in combo_iter(b):
        t = crystal_face(combo["color"], 110 + hash(combo["color"]) % 50)
        for y in range(16):
            for x in range(16):
                r, g, bb, a = t.get(x, y)
                t.set(x, y, (r, g, bb, 215))
        out[combo_name(b, combo)] = t
    return out


@painter("crystal_cluster")
def crystal_cluster(b):
    out = {}
    for combo in combo_iter(b):
        base = P.HUE[combo["color"]]
        t = Tex(16, 16)
        spikes = [(3, 15, 5, 4), (7, 15, 8, 1), (11, 15, 10, 5), (5, 15, 3, 8), (9, 15, 12, 7)]
        for x0, y0, x1, y1 in spikes:
            t.line(x0, y0, x1, y1, base).line(x0 + 1, y0, x1, y1, shade(base, 1.25)).line(x0 - 1, y0, x1, y1, shade(base, 0.75))
        t.noise(base, 0.1, 120, area=(0, 12, 15, 15)) if False else None
        t.rect(2, 13, 13, 15, shade(base, 0.7))
        t.set(8, 2, shade(base, 1.7)).set(5, 5, shade(base, 1.6))
        t.outline(shade(base, 0.4))
        out[combo_name(b, combo)] = t
    return out


@painter("glowshroom_cap")
def glowshroom_cap(b):
    out = {}
    base = {"cyan": "#2fb0c8", "orange": "#e8802a", "pink": "#e0509e", "violet": "#7a3fc0"}
    for combo in combo_iter(b):
        c = base[combo["color"]]
        t = Tex(16, 16).noise(c, 0.07, 130 + len(combo["color"]))
        import random
        rnd = random.Random(131)
        for _ in range(9):
            x, y = rnd.randrange(1, 15), rnd.randrange(1, 15)
            t.set(x, y, "#fffbe8").set(x + 1, y, shade("#fffbe8", 0.8)).set(x, y + 1, shade(c, 1.4))
        t.bevel(1.12, 0.82)
        out[combo_name(b, combo)] = t
    return out


@painter("glowshroom_stem")
def glowshroom_stem(b):
    side = Tex(16, 16).noise("#e9dcc0", 0.05, 140)
    for x in (2, 6, 11, 14):
        side.vline(x, 0, 15, shade("#e9dcc0", 0.82))
    side.dither("#d6c39a", 0.1, 141)
    top = Tex(16, 16).noise("#e9dcc0", 0.04, 142).frame(0, 0, 15, 15, "#bfae88")
    top.circle(8, 8, 5, "#d6c39a", filled=False)
    return {"glowshroom_stem_side": side, "glowshroom_stem_top": top}


@painter("rainbow_leaves")
def rainbow_leaves(b):
    import random
    rnd = random.Random(150)
    t = Tex(16, 16)
    cols = [P.TURQUOISE, P.TURQUOISE_DARK, P.PINK, P.LEMON, P.CYAN, "#2bb8a0"]
    for y in range(16):
        for x in range(16):
            if rnd.random() < 0.82:
                c = rnd.choice(cols[:2] * 3 + cols[2:])
                t.set(x, y, shade(c, 0.85 + rnd.random() * 0.3))
    return {"rainbow_leaves": t}


@painter("colored_grate")
def colored_grate(b):
    out = {}
    for combo in combo_iter(b):
        col = combo["color"]
        c = {"orange": P.ORANGE, "green": P.GREEN, "purple": "#a050e0"}[col]
        sym = {"orange": "triangle", "green": "square", "purple": "circle"}[col]
        t = Tex(16, 16)
        t.frame(0, 0, 15, 15, shade(c, 0.6)).frame(1, 1, 14, 14, c)
        for x in range(3, 14, 3):
            t.vline(x, 1, 14, shade(c, 0.85))
        for y in range(3, 14, 4):
            t.hline(1, 14, y, shade(c, 0.85))
        t.rect(5, 5, 10, 10, shade(c, 0.45)).symbol(sym, 7, 7, 2, "#f4f4f4")
        out[combo_name(b, combo)] = t
    return out


def door(base, seed, label=None, stripe=None, window=False):
    t = metal(base, seed)
    t.frame(0, 0, 15, 15, shade(base, 0.45)).frame(1, 1, 14, 14, shade(base, 0.75)).bevel(1.15, 0.82, area=(2, 2, 13, 13))
    t.rivets([(3, 3), (12, 3), (3, 12), (12, 12)], shade(base, 0.5), shade(base, 1.3))
    if stripe:
        t.stripes(stripe[0], stripe[1], 3, area=(2, 7, 13, 8))
    if window:
        t.rect(5, 4, 10, 8, "#10161a").frame(5, 4, 10, 8, shade(base, 0.5))
    return t


@painter("guard_door")
def guard_door(b):
    t = door("#46604a", 160, window=True)
    t.hline(2, 13, 11, "#2a3a2d").rect(11, 10, 12, 12, "#c9b36a")
    return {"guard_door": t}


@painter("collector_door")
def collector_door(b):
    t = door("#4a2f5a", 161)
    for y in (5, 8, 11):
        t.hline(3, 12, y, "#c9a24a")
    t.circle(8, 8, 3, "#c9a24a", filled=False)
    return {"collector_door": t}


@painter("shelter_door")
def shelter_door(b):
    t = door("#7a7d80", 162, stripe=("#e6b422", "#1b1e22"))
    t.circle(8, 11, 2, "#2a2e33", filled=False).hline(6, 10, 11, "#2a2e33")
    return {"shelter_door": t}


@painter("dash_door")
def dash_door(b):
    t = door("#56708a", 163)
    for i in range(3):
        x = 3 + i * 4
        t.line(x, 4, x + 2, 7, "#f6ee5a").line(x + 2, 7, x, 10, "#f6ee5a")
    return {"dash_door": t}


@painter("office_door")
def office_door(b):
    t = Tex(16, 16).noise("#b9a27a", 0.04, 164)
    t.frame(0, 0, 15, 15, "#6f5c3c").frame(1, 1, 14, 14, "#8d7650")
    t.rect(4, 3, 11, 8, "#cfd8d4").frame(4, 3, 11, 8, "#6f5c3c")
    t.noise("#cfd8d4", 0.05, 165, area=(5, 4, 10, 7))
    t.rect(4, 10, 11, 12, "#8d7650").text("40", 5, 10, "#2a2a2a") if False else None
    t.rect(12, 9, 13, 10, "#c9b36a")
    return {"office_door": t}


@painter("grey_void")
def grey_void(b):
    t = Tex(16, 16).noise("#6e6e72", 0.03, 170)
    t.dither("#8a8a8e", 0.05, 171)
    return {"grey_void": t}


@painter("crumbling_platform")
def crumbling_platform(b):
    out = {}
    for combo in combo_iter(b):
        s = int(combo["stage"])
        if s == 0:
            base = "#4fd6c8"
            t = Tex(16, 16).noise(base, 0.06, 180)
            for i in range(0, 16, 4):
                t.hline(0, 15, i, shade(base, 0.8)).vline(i, 0, 15, shade(base, 0.8))
            t.dither(P.PINK, 0.06, 181)
        elif s == 1:
            base = "#8a8e92"
            t = Tex(16, 16).noise(base, 0.05, 182)
            for i in range(0, 16, 4):
                t.hline(0, 15, i, shade(base, 0.8)).vline(i, 0, 15, shade(base, 0.8))
        elif s == 2:
            base = "#7a7d80"
            t = Tex(16, 16).noise(base, 0.06, 183)
            for i in range(0, 16, 4):
                t.hline(0, 15, i, shade(base, 0.8)).vline(i, 0, 15, shade(base, 0.8))
            t.line(2, 0, 7, 8, "#2a2c30").line(7, 8, 5, 15, "#2a2c30").line(7, 8, 13, 11, "#2a2c30").line(13, 11, 15, 15, "#2a2c30")
        else:
            base = "#55585b"
            t = Tex(16, 16).noise(base, 0.08, 184)
            t.line(0, 4, 15, 9, "#1e2023").line(3, 0, 9, 15, "#1e2023").line(0, 12, 15, 3, "#1e2023")
            t.dither("#1e2023", 0.25, 185)
        t.frame(0, 0, 15, 15, shade(base, 0.6))
        out[combo_name(b, combo)] = t
    return out


def relay_front(active):
    t = metal("#5a6068", 190)
    t.frame(0, 0, 15, 15, "#2a2e33").bevel(1.15, 0.8, area=(1, 1, 14, 14))
    t.circle(8, 8, 5, "#1c2024").circle(8, 8, 4, "#3a4048")
    c = P.CYAN if active else "#2a4850"
    t.circle(8, 8, 3, c)
    if active:
        t.glow(8, 8, 7, P.CYAN, 0.5).set(8, 8, "#ffffff").set(7, 7, "#d6ffff")
    t.set(2, 2, c).set(13, 2, c).set(2, 13, c).set(13, 13, c)
    return t


@painter("chromatic_relay")
def chromatic_relay(b):
    out = {}
    for combo in combo_iter(b):
        out[combo_name(b, combo) + "_front"] = relay_front(combo["active"] == "true")
    out["chromatic_relay_side"] = metal("#4a5058", 191).frame(0, 0, 15, 15, "#2a2e33")
    out["chromatic_relay_top"] = metal("#4a5058", 192).frame(0, 0, 15, 15, "#2a2e33")
    return out


@painter("water_drain")
def water_drain(b):
    out = {}
    for combo in combo_iter(b):
        opened = combo["open"] == "true"
        t = metal("#5a6a78", 200)
        t.frame(0, 0, 15, 15, "#2a323a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(3, 3, 12, 12, "#10181e" if opened else "#46525e").frame(3, 3, 12, 12, "#2a323a")
        for x in range(4, 12, 2):
            t.vline(x, 4, 11, "#0a0e12" if opened else "#2a323a")
        if opened:
            t.dither(P.ORANGE, 0.25, 201, area=(4, 4, 11, 11))
        t.rivets([(1, 1), (14, 1), (1, 14), (14, 14)], "#2a323a")
        out[combo_name(b, combo)] = t
    return out


@painter("pump_control")
def pump_control(b):
    out = {}
    for combo in combo_iter(b):
        on = combo["on"] == "true"
        t = metal("#5f7078", 210)
        t.frame(0, 0, 15, 15, "#2a323a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.circle(8, 6, 3, "#1c2428").circle(8, 6, 2, "#2fd37a" if on else "#7a2a24")
        t.rect(3, 11, 12, 13, "#14181c")
        t.rect(4 if on else 9, 11, 7 if on else 11, 13, "#e6dcc0")
        t.set(13, 3, "#c9b36a")
        out[combo_name(b, combo) + "_front"] = t
    out["pump_control_side"] = metal("#4a5a62", 211).frame(0, 0, 15, 15, "#2a323a")
    out["pump_control_top"] = metal("#4a5a62", 212).frame(0, 0, 15, 15, "#2a323a")
    return out


@painter("tram_switch")
def tram_switch(b):
    out = {}
    for combo in combo_iter(b):
        st = combo["state"]
        t = metal("#6a5a46", 220)
        t.frame(0, 0, 15, 15, "#2b241a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(3, 2, 12, 5, "#14161a")
        t.text("A", 4, 2, "#f6ee5a" if st == "a" else "#4a4630") if False else None
        t.rect(4, 3, 6, 4, "#f6ee5a" if st == "a" else "#4a4630").rect(9, 3, 11, 4, "#f6ee5a" if st == "b" else "#4a4630")
        # a miniature rail fork
        t.line(8, 14, 8, 10, "#d0d0d0")
        if st == "a":
            t.line(8, 10, 4, 6, "#d0d0d0")
        else:
            t.line(8, 10, 12, 6, "#d0d0d0")
        t.hline(5, 11, 14, "#3a3a3a")
        out[combo_name(b, combo) + "_front"] = t
    out["tram_switch_side"] = metal("#5a4a38", 221).frame(0, 0, 15, 15, "#2b241a")
    out["tram_switch_top"] = metal("#5a4a38", 222).frame(0, 0, 15, 15, "#2b241a")
    return out


@painter("battery_socket")
def battery_socket(b):
    out = {}
    for combo in combo_iter(b):
        filled = combo["filled"] == "true"
        t = metal("#60666e", 230)
        t.frame(0, 0, 15, 15, "#2a2e33").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(4, 3, 11, 12, "#15181c").frame(4, 3, 11, 12, "#3a4048")
        if filled:
            t.rect(5, 4, 10, 11, "#3a7a3a").rect(5, 4, 10, 6, "#6dffa0").rect(6, 2, 9, 3, "#c9c9c9")
            t.hline(5, 10, 8, "#235a23")
        else:
            t.hline(5, 10, 7, "#2a2e33").hline(5, 10, 9, "#2a2e33")
        t.rect(2, 13, 13, 13, "#6dffa0" if filled else "#7a2a24")
        out[combo_name(b, combo) + "_front"] = t
    out["battery_socket_side"] = metal("#50565e", 231).frame(0, 0, 15, 15, "#2a2e33")
    out["battery_socket_top"] = metal("#50565e", 232).frame(0, 0, 15, 15, "#2a2e33")
    return out


@painter("collar_stand")
def collar_stand(b):
    out = {}
    for combo in combo_iter(b):
        filled = combo["filled"] == "true"
        t = Tex(16, 16).noise("#5a3a5e", 0.05, 240)
        t.frame(0, 0, 15, 15, "#2a1a2e").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(6, 2, 9, 13, "#3a2440")
        t.ellipse(7.5, 8, 5, 3, "#c9a24a" if not filled else "#6dffa0").ellipse(7.5, 8, 3, 1.5, "#2a1a2e")
        t.set(7, 11, "#f6ee5a").set(8, 11, "#f6ee5a")
        out[combo_name(b, combo) + "_front"] = t
    out["collar_stand_side"] = Tex(16, 16).noise("#4a3050", 0.05, 241).frame(0, 0, 15, 15, "#2a1a2e")
    out["collar_stand_top"] = Tex(16, 16).noise("#4a3050", 0.05, 242).frame(0, 0, 15, 15, "#2a1a2e")
    return out


@painter("paint_tap")
def paint_tap(b):
    out = {}
    for combo in combo_iter(b):
        col = combo["color"]
        lit = combo["lit"] == "true"
        c = P.HUE[col]
        t = metal("#5a6068", 250)
        t.frame(0, 0, 15, 15, "#2a2e33").bevel(1.15, 0.8, area=(1, 1, 14, 14))
        t.rect(5, 2, 10, 6, "#8d98a3").frame(5, 2, 10, 6, "#2a2e33")
        t.rect(7, 6, 8, 9, "#8d98a3")
        t.rect(6, 9, 9, 10, "#2a2e33")
        t.rect(6, 11, 9, 13, c if lit else shade(c, 0.5))
        if col == "release":
            t.rect(5, 11, 10, 12, "#f4f4f4" if lit else "#9a9a9a").set(7, 13, "#2a2e33")
        else:
            sym = P.SYMBOL_OF.get(col, "circle")
            t.symbol(sym, 7, 12, 1, "#10161a") if False else None
        t.set(12, 3, c).set(3, 3, c)
        t.rect(2, 14, 13, 14, c if lit else shade(c, 0.45))
        out[combo_name(b, combo) + "_front"] = t
    out["paint_tap_side"] = metal("#4a5058", 251).frame(0, 0, 15, 15, "#2a2e33")
    out["paint_tap_top"] = metal("#4a5058", 252).frame(0, 0, 15, 15, "#2a2e33")
    return out


@painter("press_head")
def press_head(b):
    t = Tex(16, 16).noise("#6a7078", 0.04, 260)
    t.stripes("#e6b422", "#1b1e22", 3, area=(0, 12, 15, 15))
    t.frame(0, 0, 15, 15, "#22262b").bevel(1.15, 0.8, area=(1, 1, 14, 11))
    for x in range(3, 13, 3):
        t.vline(x, 2, 10, "#4a5058")
    t.rivets([(2, 2), (13, 2)], "#2a2e33", "#aab2ba")
    return {"press_head": t}


@painter("spring_pad")
def spring_pad(b):
    arrow = Tex(16, 16).noise("#2fae6a", 0.05, 270)
    arrow.frame(0, 0, 15, 15, "#14603a").frame(1, 1, 14, 14, "#6dffa0")
    arrow.arrow("up", 8, 8, 8, "#f4fff0")
    up = Tex(16, 16).noise("#2fae6a", 0.05, 271)
    up.frame(0, 0, 15, 15, "#14603a").frame(1, 1, 14, 14, "#6dffa0")
    for i, y in enumerate(range(3, 14, 2)):          # a coil spring seen from above
        up.hline(4 if i % 2 == 0 else 5, 11 if i % 2 == 0 else 10, y, "#f4fff0")
    side = Tex(16, 16).noise("#1d7a4a", 0.05, 272).frame(0, 0, 15, 15, "#14603a")
    return {"spring_pad_arrow": arrow, "spring_pad_up": up, "spring_pad_side": side}


@painter("spring_hatch")
def spring_hatch(b):
    t = door("#46708a", 280)
    t.arrow("up", 8, 8, 9, "#f6ee5a")
    return {"spring_hatch": t}


@painter("checkpoint_lamp")
def checkpoint_lamp(b):
    out = {}
    for combo in combo_iter(b):
        lit = combo["lit"] == "true"
        t = Tex(16, 16)
        t.rect(5, 0, 10, 15, "#d9d3c0" if lit else "#8a8f94").frame(5, 0, 10, 15, "#4a5058")
        if lit:
            t.glow(7.5, 7, 7, "#ffe27a", 0.8)
            t.rect(6, 2, 9, 13, "#fff3b0")
        else:
            t.rect(6, 2, 9, 13, "#7a828a")
        t.rect(5, 0, 10, 1, "#3a4048").rect(5, 14, 10, 15, "#3a4048")
        out[combo_name(b, combo)] = t
    out["checkpoint_lamp_pole"] = metal("#3a4048", 290)
    return out


@painter("cardboard_box")
def cardboard_box(b):
    out = {}
    for combo in combo_iter(b):
        small = combo["small"] == "true"
        opened = combo["opened"] == "true"
        t = cardboard(300 + (1 if small else 0))
        if opened:
            t.rect(1, 1, 14, 14, "#6e4f2a").frame(1, 1, 14, 14, shade(P.CARDBOARD, 0.8))
            t.rect(0, 0, 15, 1, P.CARDBOARD).rect(0, 14, 15, 15, P.CARDBOARD)
        if small:
            t.dither("#7a4a2a", 0.05, 301)
            t.set(4, 4, "#2a2a2a").set(5, 3, "#2a2a2a")   # scratch marks
        out[combo_name(b, combo)] = t
    return out


@painter("package_block")
def package_block(b):
    t = Tex(16, 16).noise("#a8794a", 0.05, 310)
    t.rect(0, 7, 15, 8, "#6e4a2a").rect(7, 0, 8, 15, "#6e4a2a")           # string
    t.rect(10, 10, 14, 14, "#e6dcc0").frame(10, 10, 14, 14, "#6a5a3a")
    t.rect(2, 2, 5, 4, "#a8473f").set(3, 3, "#e6dcc0")                    # stamp
    t.dither("#6e4a2a", 0.05, 311)
    t.frame(0, 0, 15, 15, "#6e4a2a")
    return {"package_block": t}


@painter("kettle_block")
def kettle_block(b):
    t = Tex(16, 16).noise("#3a9a8c", 0.05, 320)
    t.rect(0, 0, 15, 2, "#2a6a60").rect(0, 13, 15, 15, "#2a6a60")
    t.dither(P.PINK, 0.06, 321)
    t.frame(0, 0, 15, 15, "#1f5a50")
    t.glow(8, 8, 5, "#ffd0ec", 0.4)
    t.rect(6, 5, 9, 9, "#e8f6f2")
    return {"kettle_block": t}


@painter("old_rug")
def old_rug(b):
    t = Tex(16, 16).noise("#8a4a4a", 0.05, 330)
    t.frame(0, 0, 15, 15, "#e6dcc0").frame(1, 1, 14, 14, "#5a2a2a")
    t.rect(4, 4, 11, 11, "#a85a4a").frame(4, 4, 11, 11, "#e6dcc0")
    t.set(7, 7, "#e6dcc0").set(8, 8, "#e6dcc0").set(8, 7, "#c9a24a").set(7, 8, "#c9a24a")
    return {"old_rug": t}


@painter("waterfall")
def waterfall(b):
    return {"waterfall": flow(False)}


@painter("waterfall_up")
def waterfall_up(b):
    return {"waterfall_up": flow(True)}


def flow(up: bool) -> Tex:
    import random
    frames = 16
    t = Tex(16, 16 * frames)
    rnd = random.Random(340 + (1 if up else 0))
    streaks = [(rnd.randrange(16), rnd.randrange(16), rnd.randrange(3, 8)) for _ in range(9)]
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                t.set(x, f * 16 + y, with_alpha("#58b8f0" if not up else "#78d8f8", 150))
        for x0, y0, ln in streaks:
            for k in range(ln):
                yy = (y0 + (-f if up else f) + k) % 16
                t.set(x0, f * 16 + yy, with_alpha("#ffffff", 210 - k * 18))
                t.set((x0 + 1) % 16, f * 16 + yy, with_alpha("#c8ecff", 120))
    return t


@painter("seed_bowl")
def seed_bowl(b):
    out = {}
    for combo in combo_iter(b):
        s = int(combo["stage"])
        t = Tex(16, 16).noise("#a8a29a", 0.05, 350)
        t.circle(8, 8, 7, "#7a766e").circle(8, 8, 6, "#3a3834")
        if s == 1:
            t.dither("#2a2a2a", 0.55, 351, area=(3, 3, 12, 12)).dither("#6a6a6a", 0.2, 352, area=(3, 3, 12, 12))
        elif s == 2:
            t.dither("#2a2a2a", 0.15, 353, area=(5, 5, 10, 10))
            t.set(7, 7, "#e6dcc0")
        t.circle(8, 8, 7, "#55524c", filled=False)
        out[combo_name(b, combo)] = t
    return out


@painter("queue_display")
def queue_display(b):
    out = {}
    for combo in combo_iter(b):
        d = combo["digit"]
        t = Tex(16, 16)
        t.rect(1, 2, 14, 13, "#101214").frame(1, 2, 14, 13, "#3a3e44")
        t.seven_seg(d, 5, 3, "#ff9a2a", 5, 9)
        t.rect(2, 12, 13, 12, "#2a2018")
        t.set(2, 3, "#ff9a2a").set(13, 3, "#ff9a2a")
        out[combo_name(b, combo)] = t
    return out


@painter("ticket_machine")
def ticket_machine(b):
    front = metal("#a8473f", 360)
    front.frame(0, 0, 15, 15, "#4a1f1a").bevel(1.15, 0.8, area=(1, 1, 14, 14))
    front.rect(3, 2, 12, 6, "#101214").frame(3, 2, 12, 6, "#4a1f1a")
    front.rect(4, 3, 11, 5, "#1d5a3a").hline(5, 10, 4, "#6dffa0")
    front.rect(5, 8, 10, 9, "#e6dcc0").rect(5, 8, 10, 8, "#fffbe8")                       # a ticket sticking out
    front.rect(4, 11, 11, 13, "#2a2e33").frame(4, 11, 11, 13, "#4a1f1a")
    front.rect(6, 12, 9, 12, "#c9b36a")
    side = metal("#8a3a34", 361).frame(0, 0, 15, 15, "#4a1f1a")
    top = metal("#8a3a34", 362).frame(0, 0, 15, 15, "#4a1f1a")
    return {"ticket_machine_front": front, "ticket_machine_side": side, "ticket_machine_top": top}


def paint_block(block: dict) -> dict:
    fn = PAINTERS.get(block["id"])
    if fn is None:
        raise KeyError(f"no painter for block {block['id']}")
    return fn(block)
