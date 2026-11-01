"""Lady Vortex (the other four bosses are made in Blockbench, see entities_blockbench), the minion, the leash anchor, trams and projectiles:
GeckoLib models, textures and animations, and the animations every boss has."""
from __future__ import annotations

from .geo import Anim, Model, animations_json
from .paint import Tex, shade


# ------------------------------------------------------------------------------------------------ shared animation set
def boss_animations(name: str, extra: list | None = None, legs: bool = True) -> dict:
    A = []
    a = Anim("idle", 4.0)
    a.rot("body", {0: (0, 0, 0), 2: (1.5, 0, 0), 4: (0, 0, 0)})
    a.rot("head", {0: (0, 0, 0), 1.5: (-2, 6, 0), 3: (-1, -6, 0), 4: (0, 0, 0)})
    a.rot("arm_left", {0: (0, 0, -3), 2: (3, 0, -6), 4: (0, 0, -3)})
    a.rot("arm_right", {0: (0, 0, 3), 2: (3, 0, 6), 4: (0, 0, 3)})
    A.append(a)
    w = Anim("walk", 1.2)
    w.rot("arm_left", {0: (22, 0, 0), 0.6: (-22, 0, 0), 1.2: (22, 0, 0)})
    w.rot("arm_right", {0: (-22, 0, 0), 0.6: (22, 0, 0), 1.2: (-22, 0, 0)})
    if legs:
        w.rot("leg_left", {0: (-24, 0, 0), 0.6: (24, 0, 0), 1.2: (-24, 0, 0)})
        w.rot("leg_right", {0: (24, 0, 0), 0.6: (-24, 0, 0), 1.2: (24, 0, 0)})
    w.pos("body", {0: (0, 0.5, 0), 0.3: (0, 0, 0), 0.6: (0, 0.5, 0), 0.9: (0, 0, 0), 1.2: (0, 0.5, 0)})
    A.append(w)
    t = Anim("telegraph", 1.0, loop=False, hold_on_end=True)
    t.rot("arm_left", {0: (0, 0, 0), 0.5: (-150, 0, -20), 1.0: (-160, 0, -24)})
    t.rot("arm_right", {0: (0, 0, 0), 0.5: (-150, 0, 20), 1.0: (-160, 0, 24)})
    t.rot("body", {0: (0, 0, 0), 1.0: (-12, 0, 0)})
    t.rot("head", {0: (0, 0, 0), 1.0: (-18, 0, 0)})
    A.append(t)
    at = Anim("attack", 0.7, loop=False)
    at.rot("arm_right", {0: (-30, 0, 10), 0.2: (-150, 0, 14), 0.4: (30, 0, 0), 0.7: (0, 0, 0)})
    at.rot("body", {0: (0, 0, 0), 0.2: (-8, -10, 0), 0.4: (12, 14, 0), 0.7: (0, 0, 0)})
    A.append(at)
    sl = Anim("slam", 1.0, loop=False)
    sl.rot("arm_left", {0: (0, 0, 0), 0.3: (-170, 0, -10), 0.5: (20, 0, 0), 1.0: (0, 0, 0)})
    sl.rot("arm_right", {0: (0, 0, 0), 0.3: (-170, 0, 10), 0.5: (20, 0, 0), 1.0: (0, 0, 0)})
    sl.rot("body", {0: (0, 0, 0), 0.3: (-14, 0, 0), 0.5: (22, 0, 0), 1.0: (0, 0, 0)})
    sl.pos("body", {0: (0, 0, 0), 0.3: (0, 1.5, 0), 0.5: (0, -1.5, 0), 1.0: (0, 0, 0)})
    A.append(sl)
    sh = Anim("shield", 2.0)
    sh.rot("arm_left", {0: (-70, 0, -30), 2: (-70, 0, -30)})
    sh.rot("arm_right", {0: (-70, 0, 30), 2: (-70, 0, 30)})
    sh.rot("head", {0: (8, 0, 0), 2: (8, 0, 0)})
    sh.scale("body", {0: (1, 1, 1), 1: (1.03, 1.03, 1.03), 2: (1, 1, 1)})
    A.append(sh)
    st = Anim("stagger", 2.0)
    st.rot("body", {0: (-14, 0, 0), 1: (-18, 0, 4), 2: (-14, 0, 0)})
    st.rot("head", {0: (24, 0, 0), 1: (30, 8, 0), 2: (24, 0, 0)})
    st.rot("arm_left", {0: (10, 0, -16), 2: (10, 0, -16)})
    st.rot("arm_right", {0: (10, 0, 16), 2: (10, 0, 16)})
    A.append(st)
    de = Anim("death", 2.0, loop=False, hold_on_end=True)
    de.rot("body", {0: (0, 0, 0), 1.2: (-30, 0, 0), 2.0: (-84, 0, 0)})
    de.pos("body", {0: (0, 0, 0), 2.0: (0, -10, 6)})
    de.rot("head", {0: (0, 0, 0), 2.0: (30, 0, 0)})
    de.rot("arm_left", {0: (0, 0, 0), 2.0: (20, 0, -60)})
    de.rot("arm_right", {0: (0, 0, 0), 2.0: (20, 0, 60)})
    A.append(de)
    ph = Anim("phase", 1.6, loop=False)
    ph.rot("head", {0: (0, 0, 0), 0.4: (-30, 0, 0), 1.2: (-30, 0, 0), 1.6: (0, 0, 0)})
    ph.rot("arm_left", {0: (0, 0, 0), 0.4: (-20, 0, -80), 1.2: (-20, 0, -80), 1.6: (0, 0, 0)})
    ph.rot("arm_right", {0: (0, 0, 0), 0.4: (-20, 0, 80), 1.2: (-20, 0, 80), 1.6: (0, 0, 0)})
    ph.scale("body", {0: (1, 1, 1), 0.4: (1.08, 1.08, 1.08), 1.2: (1.08, 1.08, 1.08), 1.6: (1, 1, 1)})
    A.append(ph)
    for a in extra or []:
        A.append(a)
    return animations_json(name, A)


def _paint_parts(m: Model, colors: dict, default: str, variance: float = 0.06) -> Tex:
    t = m.new_texture()
    for i, (n, p) in enumerate(m.parts.items()):
        p.fill(t, colors.get(n, default), variance, 100 + i)
    return t


# ================================================================================================ Lady Vortex
def lady_vortex_model() -> Model:
    m = Model("lady_vortex", 128, 128, bounds=(3.0, 4.0, (0, 1.6, 0)))
    m.bone("root")
    for i in range(4):
        m.bone(f"spiral_{i + 1}", "root", pivot=(0, 5 * i + 2, 0))
        s = 16 - i * 2
        m.cube(f"spiral_{i + 1}", f"spiral_{i + 1}", (-s / 2, 5 * i, -s / 2), (s, 5, s))
    m.bone("body", "root", pivot=(0, 20, 0))
    m.cube("body", "torso", (-4, 20, -3), (8, 12, 6))
    m.cube("body", "ring", (-9, 16, -9), (18, 4, 18))
    m.bone("head", "body", pivot=(0, 32, 0))
    m.cube("head", "head", (-4, 32, -4), (8, 8, 8))
    m.cube("head", "cap", (-4.5, 36, -4.5), (9, 4, 9))
    m.cube("head", "goggles", (-4.5, 34, -5), (9, 2, 1))
    m.bone("arm_left", "body", pivot=(5, 30, 0))
    m.cube("arm_left", "arm_l", (4, 18, -2), (3, 12, 4))
    m.bone("arm_right", "body", pivot=(-5, 30, 0))
    m.cube("arm_right", "arm_r", (-7, 18, -2), (3, 12, 4))
    return m


def paint_lady_vortex() -> Tex:
    m = lady_vortex_model()
    aqua = ["#46c8e8", "#2fa8d8", "#7ae0f0", "#38b8e0"]
    colors = {f"spiral_{i + 1}": aqua[i] for i in range(4)}
    colors.update({"torso": "#e0e0e8", "ring": "#e04a4a", "head": "#e0b898", "cap": "#e8f0f8", "goggles": "#16202a", "arm_l": "#e0b898", "arm_r": "#e0b898"})
    t = _paint_parts(m, colors, "#46c8e8")
    for i in range(4):
        p = m.parts[f"spiral_{i + 1}"]
        for f in ("north", "south", "east", "west"):
            x0, y0, x1, y1 = p.rect(f)
            for x in range(x0, x1 + 1, 3):
                t.vline(x, y0, y1, shade(aqua[i], 1.3))
            t.hline(x0, x1, y1, shade(aqua[i], 0.7))
    ring = m.parts["ring"]
    for f in ("north", "south", "east", "west"):
        x0, y0, x1, y1 = ring.rect(f)
        for x in range(x0, x1 + 1, 4):
            t.rect(x, y0, min(x + 1, x1), y1, "#f4f4f4")
    head = m.parts["head"]
    x0, y0, x1, y1 = head.rect("north")
    t.rect(x0 + 1, y0 + 3, x0 + 2, y0 + 4, "#1a1a1a")
    t.rect(x1 - 2, y0 + 3, x1 - 1, y0 + 4, "#1a1a1a")
    t.hline(x0 + 2, x1 - 2, y0 + 6, "#a04a4a")
    g = m.parts["goggles"]
    x0, y0, x1, y1 = g.rect("north")
    t.rect(x0 + 1, y0, x0 + 3, y1, "#7ad8f8")
    t.rect(x1 - 3, y0, x1 - 1, y1, "#7ad8f8")
    return t


def lady_vortex_animations() -> dict:
    extra = []
    sp = Anim("spin", 3.0)
    for i in range(4):
        sp.rot(f"spiral_{i + 1}", {0: (0, 0, 0), 1.5: (0, 180 * (1 if i % 2 == 0 else -1), 0), 3.0: (0, 360 * (1 if i % 2 == 0 else -1), 0)})
    extra.append(sp)
    return boss_animations("lady_vortex", extra, legs=False)


# ================================================================================================ helpers
def minion_model() -> Model:
    m = Model("minion", 64, 64, bounds=(1.5, 1.5, (0, 0.5, 0)))
    m.bone("root")
    m.bone("body", "root", pivot=(0, 3, 0))
    m.cube("body", "torso", (-4, 3, -3), (8, 6, 6))
    m.bone("head", "body", pivot=(0, 9, 0))
    m.cube("head", "head", (-3, 9, -3), (6, 5, 6))
    m.cube("head", "antenna", (-0.5, 14, -0.5), (1, 3, 1))
    m.bone("arm_left", "body", pivot=(4, 8, 0))
    m.cube("arm_left", "arm_l", (4, 4, -1), (2, 5, 2))
    m.bone("arm_right", "body", pivot=(-4, 8, 0))
    m.cube("arm_right", "arm_r", (-6, 4, -1), (2, 5, 2))
    m.bone("wheel_left", "root", pivot=(3, 2.5, 0))
    m.cube("wheel_left", "wheel_l", (2, 0, -2.5), (2, 5, 5))
    m.bone("wheel_right", "root", pivot=(-3, 2.5, 0))
    m.cube("wheel_right", "wheel_r", (-4, 0, -2.5), (2, 5, 5))
    return m


def paint_minion() -> Tex:
    m = minion_model()
    colors = {"torso": "#b05a20", "head": "#8d98a3", "antenna": "#e6b422", "arm_l": "#6a7480", "arm_r": "#6a7480", "wheel_l": "#222428", "wheel_r": "#222428"}
    t = _paint_parts(m, colors, "#6a7480")
    head = m.parts["head"]
    x0, y0, x1, y1 = head.rect("north")
    t.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 3, "#16202a")
    t.rect(x0 + 2, y0 + 2, x0 + 3, y0 + 2, "#ff4a2a")
    t.rect(x1 - 3, y0 + 2, x1 - 2, y0 + 2, "#ff4a2a")
    torso = m.parts["torso"]
    x0, y0, x1, y1 = torso.rect("north")
    t.stripes("#e6b422", "#1b1e22", 2, area=(x0, y1 - 1, x1, y1))
    return t


def minion_animations() -> dict:
    A = []
    a = Anim("idle", 2.0)
    a.rot("head", {0: (0, 0, 0), 1: (0, 12, 0), 2: (0, 0, 0)})
    a.rot("arm_left", {0: (0, 0, -4), 1: (4, 0, -8), 2: (0, 0, -4)})
    a.rot("arm_right", {0: (0, 0, 4), 1: (4, 0, 8), 2: (0, 0, 4)})
    A.append(a)
    w = Anim("walk", 0.5)
    w.rot("wheel_left", {0: (0, 0, 0), 0.5: (360, 0, 0)})
    w.rot("wheel_right", {0: (0, 0, 0), 0.5: (360, 0, 0)})
    w.pos("body", {0: (0, 0.2, 0), 0.25: (0, 0, 0), 0.5: (0, 0.2, 0)})
    A.append(w)
    at = Anim("attack", 0.5, loop=False)
    at.rot("arm_right", {0: (0, 0, 0), 0.15: (-120, 0, 0), 0.3: (20, 0, 0), 0.5: (0, 0, 0)})
    at.rot("body", {0: (0, 0, 0), 0.15: (-10, 0, 0), 0.3: (14, 0, 0), 0.5: (0, 0, 0)})
    A.append(at)
    return animations_json("minion", A)


def anchor_model() -> Model:
    m = Model("leash_anchor", 64, 64, bounds=(1.5, 2.0, (0, 0.8, 0)))
    m.bone("root")
    m.bone("stake", "root", pivot=(0, 0, 0))
    m.cube("stake", "stake", (-2, 0, -2), (4, 20, 4))
    m.bone("ring", "stake", pivot=(0, 14, 0))
    m.cube("ring", "ring", (-6, 12, -6), (12, 3, 12))
    m.cube("ring", "gem", (-2, 20, -2), (4, 4, 4))
    return m


def paint_anchor() -> Tex:
    m = anchor_model()
    t = _paint_parts(m, {"stake": "#5a3a6a", "ring": "#c9a24a", "gem": "#ff72b8"}, "#5a3a6a")
    g = m.parts["gem"]
    for f in ("north", "south", "east", "west", "up", "down"):
        x0, y0, x1, y1 = g.rect(f)
        t.noise("#ff72b8", 0.15, 5, area=(x0, y0, x1, y1))
    return t


def anchor_animations() -> dict:
    a = Anim("idle", 2.0)
    a.pos("ring", {0: (0, 0, 0), 1: (0, 1.2, 0), 2: (0, 0, 0)})
    a.rot("ring", {0: (0, 0, 0), 2: (0, 360, 0)})
    b = Anim("break", 0.8, loop=False, hold_on_end=True)
    b.scale("stake", {0: (1, 1, 1), 0.8: (0.01, 0.01, 0.01)})
    b.rot("stake", {0: (0, 0, 0), 0.8: (90, 0, 40)})
    return animations_json("leash_anchor", [a, b])


def tram_model(name: str) -> Model:
    m = Model(name, 256, 256, bounds=(8.0, 5.0, (0, 1.5, 0)))
    m.bone("root")
    m.bone("body", "root", pivot=(0, 0, 0))
    m.cube("body", "hull", (-14, 5, -30), (28, 22, 60))
    m.cube("body", "roof", (-13, 27, -29), (26, 3, 58))
    m.cube("body", "skirt", (-15, 3, -30), (30, 3, 60))
    m.cube("body", "lamp_l", (6, 12, -31), (5, 4, 1))
    m.cube("body", "lamp_r", (-11, 12, -31), (5, 4, 1))
    m.cube("body", "bell", (-2, 4, -32), (4, 3, 2))
    m.bone("pantograph", "body", pivot=(0, 30, 0))
    m.cube("pantograph", "arm", (-1, 30, -2), (2, 9, 2))
    m.cube("pantograph", "bar", (-8, 39, -5), (16, 1, 10))
    m.bone("wheels", "root", pivot=(0, 3, 0))
    for i, z in enumerate((-22, -8, 8, 22)):
        m.cube("wheels", f"wheel_{i}", (-15, 0, z - 4), (30, 5, 8))
    return m


def paint_tram(name: str) -> Tex:
    m = tram_model(name)
    base, trim = ("#c93a3a", "#f0e6d0") if name == "sky_tram" else ("#2f6a8a", "#e6d6a0")
    colors = {"hull": base, "roof": "#d8d8d8", "skirt": "#2a2c30", "lamp_l": "#fff0a8", "lamp_r": "#fff0a8", "bell": "#e6c46a", "arm": "#4a4f55", "bar": "#2a2c30"}
    for i in range(4):
        colors[f"wheel_{i}"] = "#222428"
    t = _paint_parts(m, colors, base)
    hull = m.parts["hull"]
    for f in ("east", "west"):
        x0, y0, x1, y1 = hull.rect(f)
        t.hline(x0, x1, y0 + 14, trim)
        for x in range(x0 + 3, x1 - 3, 9):
            t.rect(x, y0 + 4, x + 5, y0 + 10, "#12181e")
            t.rect(x + 1, y0 + 5, x + 4, y0 + 8, "#ffd98a" if (x // 9) % 2 == 0 else "#7a9ab0")
            t.frame(x, y0 + 4, x + 5, y0 + 10, trim)
    for f in ("north", "south"):
        x0, y0, x1, y1 = hull.rect(f)
        t.rect(x0 + 3, y0 + 3, x1 - 3, y0 + 10, "#12181e")
        t.rect(x0 + 4, y0 + 4, x1 - 4, y0 + 8, "#7a9ab0")
        t.vline((x0 + x1) // 2, y0 + 3, y0 + 10, trim)
        t.rect(x0 + 2, y1 - 4, x1 - 2, y1 - 3, trim)
    return t


def tram_animations(name: str) -> dict:
    a = Anim("idle", 3.0)
    a.pos("body", {0: (0, 0, 0), 1.5: (0, 0.12, 0), 3.0: (0, 0, 0)})
    mv = Anim("move", 1.0)
    mv.rot("body", {0: (0, 0, 0.4), 0.5: (0, 0, -0.4), 1.0: (0, 0, 0.4)})
    mv.pos("body", {0: (0, 0, 0), 0.25: (0, 0.2, 0), 0.5: (0, 0, 0), 0.75: (0, 0.2, 0), 1.0: (0, 0, 0)})
    mv.rot("pantograph", {0: (0, 0, 0), 0.5: (1.5, 0, 0), 1.0: (0, 0, 0)})
    return animations_json("tram", [a, mv])


def projectile_model(name: str, size: int, depth: int) -> Model:
    m = Model(name, 32, 32, bounds=(1.0, 1.0, (0, 0.2, 0)))
    m.bone("root")
    m.bone("spin", "root", pivot=(0, 0, 0))
    m.cube("spin", "core", (-size / 2, -size / 2, -depth / 2), (size, size, depth))
    return m


def paint_seed_projectile() -> Tex:
    m = projectile_model("projectile", 2, 2)
    t = _paint_parts(m, {"core": "#20232a"}, "#20232a")
    x0, y0, x1, y1 = m.parts["core"].rect("north")
    t.set(x0, y0, "#f0f0e8")
    return t


def paint_wheel_projectile() -> Tex:
    m = projectile_model("wheel", 10, 4)
    t = _paint_parts(m, {"core": "#222428"}, "#222428")
    for f in ("north", "south"):
        x0, y0, x1, y1 = m.parts["core"].rect(f)
        t.circle((x0 + x1) / 2, (y0 + y1) / 2, 3.6, "#8d98a3", filled=False)
        t.circle((x0 + x1) / 2, (y0 + y1) / 2, 1.2, "#8d98a3")
    return t


def spin_animations(name: str) -> dict:
    a = Anim("idle", 0.5)
    a.rot("spin", {0: (0, 0, 0), 0.5: (0, 0, 360)})
    return animations_json(name, [a])
