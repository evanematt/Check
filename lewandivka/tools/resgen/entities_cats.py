"""Chinazik (black) and Metadonna (grey tabby): blocky GeckoLib cats with the animations the story needs."""
from __future__ import annotations

import random

from .geo import Anim, Model, animations_json
from .paint import Tex, shade, mix

NAME = "cat"


def cat_model() -> Model:
    m = Model(NAME, 64, 64, bounds=(2.0, 2.0, (0, 0.6, 0)))
    m.bone("root", pivot=(0, 0, 0))
    m.bone("body", "root", pivot=(0, 6, 3))
    m.cube("body", "body", (-3, 4, -5), (6, 5, 9))
    m.cube("body", "chest", (-2, 4, -6), (4, 4, 1))
    m.bone("head", "body", pivot=(0, 9, -5))
    m.cube("head", "head", (-3.5, 8, -10), (7, 6, 5))
    m.cube("head", "snout", (-1.5, 8.5, -12), (3, 2, 2))
    m.bone("ear_l", "head", pivot=(2.5, 14, -6))
    m.cube("ear_l", "ear_l", (1.5, 14, -6.5), (2, 2, 1))
    m.bone("ear_r", "head", pivot=(-2.5, 14, -6))
    m.cube("ear_r", "ear_r", (-3.5, 14, -6.5), (2, 2, 1), mirror=True)
    m.bone("tail1", "body", pivot=(0, 8, 4))
    m.cube("tail1", "tail1", (-1, 7, 4), (2, 2, 4))
    m.bone("tail2", "tail1", pivot=(0, 8, 8))
    m.cube("tail2", "tail2", (-1, 7, 8), (2, 2, 4))
    m.bone("tail3", "tail2", pivot=(0, 8, 12))
    m.cube("tail3", "tail3", (-1, 7, 12), (2, 2, 3))
    m.bone("leg_fl", "body", pivot=(1.8, 5, -4))
    m.cube("leg_fl", "leg_fl", (0.8, 0, -5), (2, 5, 2))
    m.bone("leg_fr", "body", pivot=(-1.8, 5, -4))
    m.cube("leg_fr", "leg_fr", (-2.8, 0, -5), (2, 5, 2), mirror=True)
    m.bone("leg_bl", "body", pivot=(1.8, 5, 3))
    m.cube("leg_bl", "leg_bl", (0.8, 0, 2), (2, 5, 3))
    m.bone("leg_br", "body", pivot=(-1.8, 5, 3))
    m.cube("leg_br", "leg_br", (-2.8, 0, 2), (2, 5, 3), mirror=True)
    return m


# ------------------------------------------------------------------------------------------------ animations
def _sit_pose() -> tuple:
    rot = {"body": (-48, 0, 0), "head": (46, 0, 0), "leg_fl": (48, 0, 0), "leg_fr": (48, 0, 0),
           "leg_bl": (-82, 0, 0), "leg_br": (-82, 0, 0), "tail1": (62, 0, 0), "tail2": (12, 0, 0), "tail3": (8, 0, 0)}
    pos = {"body": (0, -1.6, 1.0), "leg_bl": (0, -0.6, 0.6), "leg_br": (0, -0.6, 0.6)}
    return rot, pos


def cat_animations() -> dict:
    A = []
    # --- idle: standing, tail sways, an ear flicks
    a = Anim("idle", 4.0)
    a.rot("tail1", {0: (0, 0, 0), 1: (0, 0, 6), 2: (0, 0, 0), 3: (0, 0, -6), 4: (0, 0, 0)})
    a.rot("tail2", {0: (0, 0, 0), 1: (0, 0, 8), 2: (0, 0, 0), 3: (0, 0, -8), 4: (0, 0, 0)})
    a.rot("tail3", {0: (0, 0, 4), 1: (0, 0, 12), 2: (0, 0, 4), 3: (0, 0, -8), 4: (0, 0, 4)})
    a.rot("head", {0: (0, 0, 0), 1.2: (0, 7, 0), 2.4: (0, 0, 0), 3.2: (0, -6, 0), 4: (0, 0, 0)})
    a.rot("ear_l", {0: (0, 0, 0), 3.3: (0, 0, 0), 3.4: (0, 0, -16), 3.55: (0, 0, 0), 4: (0, 0, 0)})
    a.rot("body", {0: (0, 0, 0), 2: (-1.2, 0, 0), 4: (0, 0, 0)})
    A.append(a)
    # --- sit
    rot, pos = _sit_pose()
    s = Anim("sit", 5.0)
    for bone, v in rot.items():
        if bone == "tail3":
            s.rot(bone, {0: (v[0], 0, 0), 2.5: (v[0], 0, 10), 5: (v[0], 0, 0)})
        elif bone == "head":
            s.rot(bone, {0: (v[0], 0, 0), 2.5: (v[0] - 2, 4, 0), 5: (v[0], 0, 0)})
        else:
            s.rot(bone, {0: v, 5: v})
    for bone, v in pos.items():
        s.pos(bone, {0: v, 5: v})
    A.append(s)
    # --- walk and run
    for name, length, amp, bob in (("walk", 0.9, 30, 0.35), ("run", 0.5, 50, 0.7)):
        w = Anim(name, length)
        h = length / 2
        w.rot("leg_fl", {0: (amp, 0, 0), h: (-amp, 0, 0), length: (amp, 0, 0)})
        w.rot("leg_br", {0: (amp, 0, 0), h: (-amp, 0, 0), length: (amp, 0, 0)})
        w.rot("leg_fr", {0: (-amp, 0, 0), h: (amp, 0, 0), length: (-amp, 0, 0)})
        w.rot("leg_bl", {0: (-amp, 0, 0), h: (amp, 0, 0), length: (-amp, 0, 0)})
        w.pos("body", {0: (0, bob, 0), h / 2: (0, 0, 0), h: (0, bob, 0), h * 1.5: (0, 0, 0), length: (0, bob, 0)})
        w.rot("tail1", {0: (-14, 0, 0), h: (-14, 0, 8), length: (-14, 0, 0)} if name == "walk" else {0: (-6, 0, 0), length: (-6, 0, 0)})
        w.rot("tail2", {0: (0, 0, 0), h: (0, 0, -8), length: (0, 0, 0)})
        w.rot("head", {0: (-4, 0, 0), h: (-2, 0, 0), length: (-4, 0, 0)})
        if name == "run":
            w.rot("body", {0: (-8, 0, 0), h: (8, 0, 0), length: (-8, 0, 0)})
        A.append(w)
    # --- look: sitting, the head turns left and right
    rot, pos = _sit_pose()
    l = Anim("look", 4.0)
    for bone, v in rot.items():
        if bone != "head":
            l.rot(bone, {0: v, 4: v})
    l.rot("head", {0: (46, 0, 0), 0.8: (46, -38, 0), 1.6: (46, -38, 0), 2.4: (46, 38, 0), 3.2: (46, 38, 0), 4: (46, 0, 0)})
    l.rot("ear_l", {0: (0, 0, 0), 1: (0, 0, 8), 2: (0, 0, 0), 3: (0, 0, 8), 4: (0, 0, 0)})
    for bone, v in pos.items():
        l.pos(bone, {0: v, 4: v})
    A.append(l)
    # --- sniff: standing, head low, nose twitching
    sn = Anim("sniff", 2.4)
    sn.rot("head", {0: (34, 0, 0), 0.3: (28, 0, 0), 0.6: (34, 0, 0), 0.9: (28, 6, 0), 1.2: (34, 0, 0), 1.5: (28, -6, 0), 1.8: (34, 0, 0), 2.1: (28, 0, 0), 2.4: (34, 0, 0)})
    sn.rot("body", {0: (6, 0, 0), 2.4: (6, 0, 0)})
    sn.rot("tail1", {0: (-10, 0, 0), 1.2: (-10, 0, 10), 2.4: (-10, 0, 0)})
    sn.rot("leg_fl", {0: (-8, 0, 0), 2.4: (-8, 0, 0)})
    sn.rot("leg_fr", {0: (-8, 0, 0), 2.4: (-8, 0, 0)})
    A.append(sn)
    # --- eat: head down and bobbing
    e = Anim("eat", 2.0)
    e.rot("head", {0: (52, 0, 0), 0.25: (42, 0, 0), 0.5: (54, 0, 0), 0.75: (42, 0, 0), 1: (54, 0, 0), 1.25: (42, 0, 0), 1.5: (54, 0, 0), 1.75: (42, 0, 0), 2: (52, 0, 0)})
    e.rot("body", {0: (10, 0, 0), 2: (10, 0, 0)})
    e.rot("leg_fl", {0: (-12, 0, 0), 2: (-12, 0, 0)})
    e.rot("leg_fr", {0: (-12, 0, 0), 2: (-12, 0, 0)})
    e.rot("tail1", {0: (-6, 0, 0), 1: (-6, 0, 6), 2: (-6, 0, 0)})
    A.append(e)
    # --- scratch: sitting, a hind leg scratches the neck
    rot, pos = _sit_pose()
    sc = Anim("scratch", 1.2)
    for bone, v in rot.items():
        if bone not in ("leg_bl", "head"):
            sc.rot(bone, {0: v, 1.2: v})
    sc.rot("leg_bl", {0: (-120, 0, -8), 0.15: (-150, 0, -8), 0.3: (-120, 0, -8), 0.45: (-150, 0, -8), 0.6: (-120, 0, -8), 0.75: (-150, 0, -8), 0.9: (-120, 0, -8), 1.2: (-120, 0, -8)})
    sc.rot("head", {0: (50, 0, 12), 0.3: (54, 0, 16), 0.6: (50, 0, 12), 0.9: (54, 0, 16), 1.2: (50, 0, 12)})
    for bone, v in pos.items():
        sc.pos(bone, {0: v, 1.2: v})
    A.append(sc)
    # --- sleep: curled up low, breathing
    sl = Anim("sleep", 4.0)
    sl.pos("body", {0: (0, -3.4, 0), 2: (0, -3.1, 0), 4: (0, -3.4, 0)})
    sl.rot("body", {0: (0, 0, 0), 4: (0, 0, 0)})
    sl.rot("head", {0: (38, 25, 8), 2: (36, 25, 8), 4: (38, 25, 8)})
    sl.rot("leg_fl", {0: (-84, 0, 0), 4: (-84, 0, 0)})
    sl.rot("leg_fr", {0: (-84, 0, 0), 4: (-84, 0, 0)})
    sl.rot("leg_bl", {0: (-86, 0, 0), 4: (-86, 0, 0)})
    sl.rot("leg_br", {0: (-86, 0, 0), 4: (-86, 0, 0)})
    sl.pos("leg_fl", {0: (0, -0.8, 0), 4: (0, -0.8, 0)})
    sl.pos("leg_fr", {0: (0, -0.8, 0), 4: (0, -0.8, 0)})
    sl.rot("tail1", {0: (0, 70, 0), 4: (0, 70, 0)})
    sl.rot("tail2", {0: (0, 60, 0), 4: (0, 60, 0)})
    sl.rot("tail3", {0: (0, 50, 0), 2: (0, 54, 0), 4: (0, 50, 0)})
    sl.rot("ear_l", {0: (0, 0, 14), 4: (0, 0, 14)})
    sl.rot("ear_r", {0: (0, 0, -14), 4: (0, 0, -14)})
    A.append(sl)
    # --- alert: stands tall, ears up, tail raised
    al = Anim("alert", 2.0)
    al.rot("body", {0: (-8, 0, 0), 2: (-8, 0, 0)})
    al.rot("head", {0: (-4, 0, 0), 1: (-6, 6, 0), 2: (-4, 0, 0)})
    al.rot("leg_fl", {0: (10, 0, 0), 2: (10, 0, 0)})
    al.rot("leg_fr", {0: (10, 0, 0), 2: (10, 0, 0)})
    al.rot("ear_l", {0: (0, 0, 12), 2: (0, 0, 12)})
    al.rot("ear_r", {0: (0, 0, -12), 2: (0, 0, -12)})
    al.rot("tail1", {0: (-58, 0, 0), 1: (-58, 0, 8), 2: (-58, 0, 0)})
    al.rot("tail2", {0: (-20, 0, 0), 2: (-20, 0, 0)})
    A.append(al)
    # --- point: sits, turns its head and raises one front paw towards the target
    rot, pos = _sit_pose()
    po = Anim("point", 3.0)
    for bone, v in rot.items():
        if bone not in ("head", "leg_fl"):
            po.rot(bone, {0: v, 3: v})
    po.rot("head", {0: (44, -24, 0), 1.5: (42, -26, 0), 3: (44, -24, 0)})
    po.rot("leg_fl", {0: (-30, 0, 0), 0.4: (-70, -10, 0), 1.5: (-72, -10, 0), 3: (-70, -10, 0)})
    po.rot("ear_l", {0: (0, 0, 10), 3: (0, 0, 10)})
    po.rot("ear_r", {0: (0, 0, -10), 3: (0, 0, -10)})
    for bone, v in pos.items():
        po.pos(bone, {0: v, 3: v})
    A.append(po)
    return animations_json(NAME, A)


# ------------------------------------------------------------------------------------------------ textures
def _bands(t: Tex, part, axis_rows, dark, light=None, every=2):
    for face in ("up", "down", "east", "west", "north", "south"):
        x0, y0, x1, y1 = part.rect(face)
        for y in range(y0, y1 + 1):
            if ((y - y0) // every) % 2 == 1 and face != "north":
                t.hline(x0, x1, y, dark)


def paint_chinazik() -> Tex:
    m = cat_model()
    t = m.new_texture()
    base, patch = "#1d1e22", "#4d5057"
    for name, p in m.parts.items():
        p.fill(t, base, 0.07, hash(name) % 100)
    rnd = random.Random(7)
    for p in m.parts.values():                                   # fluffy fur: lighter specks
        for _ in range(10):
            f = rnd.choice(("up", "east", "west", "north", "south"))
            x0, y0, x1, y1 = p.rect(f)
            t.set(rnd.randint(x0, x1), rnd.randint(y0, y1), "#2b2d33")
    # chest patch
    chest = m.parts["chest"]
    for f in ("north", "up", "down", "east", "west"):
        x0, y0, x1, y1 = chest.rect(f)
        t.noise(patch, 0.1, 9, area=(x0, y0, x1, y1))
    body = m.parts["body"]
    x0, y0, x1, y1 = body.rect("down")
    t.noise("#34363c", 0.08, 11, area=(x0, y0, x1, y1))
    # tail rings
    for nm in ("tail1", "tail2", "tail3"):
        p = m.parts[nm]
        for f in ("east", "west", "up", "down", "north", "south"):
            x0, y0, x1, y1 = p.rect(f)
            for y in range(y0, y1 + 1):
                if (y - y0) % 4 >= 2:
                    t.hline(x0, x1, y, "#33353b")
    # face: dark mask, suspicious narrow eyes with a faint glint
    head = m.parts["head"]
    head.face(t, "north", "#17181b")
    t.noise("#17181b", 0.05, 13, area=head.rect("north"))
    for ex in (1, 5):
        head.box(t, "north", ex, 2, ex + 1, 2, "#0a0a0c")
        head.px(t, "north", ex, 2, "#2c3a2c")
        head.px(t, "north", ex + 1, 2, "#8a9a50")
    head.box(t, "north", 3, 3, 3, 3, "#0a0a0c")
    head.box(t, "north", 2, 4, 4, 4, "#0a0a0c")
    head.px(t, "north", 3, 4, "#222226")
    snout = m.parts["snout"]
    snout.face(t, "north", "#101012")
    snout.px(t, "north", 1, 0, "#0a0a0c")
    for p in (m.parts["ear_l"], m.parts["ear_r"]):
        x0, y0, x1, y1 = p.rect("north")
        t.rect(x0, y0, x1, y1, "#2e2f35")
        t.set(x0 + 1, y0 + 1, "#3a2a30")
    for nm in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):          # paws a little lighter
        p = m.parts[nm]
        for f in ("north", "south", "east", "west"):
            x0, y0, x1, y1 = p.rect(f)
            t.rect(x0, y1, x1, y1, "#33353b")
        x0, y0, x1, y1 = p.rect("down")
        t.rect(x0, y0, x1, y1, "#26272b")
    # whiskers: light pixels on the sides of the head near the snout
    for f in ("east", "west"):
        x0, y0, x1, y1 = head.rect(f)
        t.set(x0, y0 + 3, "#7a7d84")
        t.set(x0, y0 + 4, "#6a6d74")
    return t


def paint_metadonna() -> Tex:
    m = cat_model()
    t = m.new_texture()
    base, stripe, white = "#8e8b85", "#4b4843", "#e9e3d6"
    for name, p in m.parts.items():
        p.fill(t, base, 0.06, 3 + hash(name) % 90)
    # tabby stripes across the back and sides
    body = m.parts["body"]
    for f in ("up", "east", "west"):
        x0, y0, x1, y1 = body.rect(f)
        for y in range(y0, y1 + 1):
            if (y - y0) % 3 == 0:
                for x in range(x0, x1 + 1):
                    if (x + y) % 5 != 0:
                        t.set(x, y, stripe)
        for x in range(x0, x1 + 1):
            if (x - x0) % 4 == 1:
                t.vline(x, y0, y1, shade(stripe, 1.1))
    x0, y0, x1, y1 = body.rect("down")
    t.noise(white, 0.04, 21, area=(x0, y0, x1, y1))
    x0, y0, x1, y1 = body.rect("north")
    t.noise(white, 0.04, 22, area=(x0, y0, x1, y1))
    chest = m.parts["chest"]
    for f in ("north", "up", "down", "east", "west"):
        x0, y0, x1, y1 = chest.rect(f)
        t.noise(white, 0.04, 23, area=(x0, y0, x1, y1))
    for nm in ("tail1", "tail2", "tail3"):
        p = m.parts[nm]
        for f in ("east", "west", "up", "down", "north", "south"):
            x0, y0, x1, y1 = p.rect(f)
            for y in range(y0, y1 + 1):
                if (y - y0) % 4 >= 2:
                    t.hline(x0, x1, y, stripe)
    for nm in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        p = m.parts[nm]
        for f in ("north", "south", "east", "west"):
            x0, y0, x1, y1 = p.rect(f)
            t.rect(x0, y1 - 1, x1, y1, white)
            t.hline(x0, x1, y0 + 1, stripe)
            t.hline(x0, x1, y0 + 3, stripe)
        x0, y0, x1, y1 = p.rect("down")
        t.rect(x0, y0, x1, y1, white)
        t.set(x0, y0, "#d9a79f")
    # head: forehead "M" stripes, big dark eyes with a highlight, pink nose, white muzzle
    head = m.parts["head"]
    x0, y0, x1, y1 = head.rect("up")
    for x in range(x0 + 1, x1, 2):
        t.vline(x, y0, y0 + 3, stripe)
    head.face(t, "north", base)
    t.noise(base, 0.05, 25, area=head.rect("north"))
    head.box(t, "north", 1, 0, 1, 1, stripe)
    head.box(t, "north", 3, 0, 3, 1, stripe)
    head.box(t, "north", 5, 0, 5, 1, stripe)
    for ex in (0, 4):
        head.box(t, "north", ex, 2, ex + 2, 3, "#2a2018")
        head.px(t, "north", ex + 1, 2, "#ffffff")
        head.px(t, "north", ex + 2, 3, "#5a4a30")
    head.box(t, "north", 2, 4, 4, 5, white)
    head.px(t, "north", 3, 4, "#c98b86")
    head.px(t, "north", 3, 5, "#6a5a58")
    snout = m.parts["snout"]
    snout.face(t, "north", white)
    snout.px(t, "north", 1, 0, "#c98b86")
    for p, inner in ((m.parts["ear_l"], "#d9a79f"), (m.parts["ear_r"], "#d9a79f")):
        x0, y0, x1, y1 = p.rect("north")
        t.rect(x0, y0, x1, y1, inner)
        x0, y0, x1, y1 = p.rect("south")
        t.rect(x0, y0, x1, y1, base)
    for f in ("east", "west"):
        x0, y0, x1, y1 = head.rect(f)
        t.set(x0, y0 + 3, "#f0f0f0")
        t.set(x0, y0 + 4, "#dcdcdc")
        t.hline(x0 + 1, x0 + 3, y0 + 1, stripe)
    return t


def paint(name: str) -> Tex:
    return {"chinazik": paint_chinazik, "metadonna": paint_metadonna}[name]()
