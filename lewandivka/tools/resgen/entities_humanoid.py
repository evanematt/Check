"""The shared humanoid model (player-skin UV layout) with seven NPC/monster skins and its animations."""
from __future__ import annotations

import math
import random

from .geo import Anim, Model, Part, animations_json
from .paint import Tex, shade, mix, C

NAME = "humanoid"


def humanoid_model() -> Model:
    m = Model(NAME, 64, 64, bounds=(2.0, 3.0, (0, 1.0, 0)))
    m.bone("root", pivot=(0, 0, 0))
    m.bone("body", "root", pivot=(0, 12, 0))
    m.cube("body", "body", (-4, 12, -2), (8, 12, 4), (16, 16))
    m.cube("body", "jacket", (-4, 12, -2), (8, 12, 4), (16, 32), inflate=0.25)
    m.bone("head", "body", pivot=(0, 24, 0))
    m.cube("head", "head", (-4, 24, -4), (8, 8, 8), (0, 0))
    m.cube("head", "hat", (-4, 24, -4), (8, 8, 8), (32, 0), inflate=0.5)
    m.bone("right_arm", "body", pivot=(-5, 22, 0))
    m.cube("right_arm", "right_arm", (-8, 12, -2), (4, 12, 4), (40, 16))
    m.cube("right_arm", "right_sleeve", (-8, 12, -2), (4, 12, 4), (40, 32), inflate=0.25)
    m.bone("left_arm", "body", pivot=(5, 22, 0))
    m.cube("left_arm", "left_arm", (4, 12, -2), (4, 12, 4), (32, 48))
    m.cube("left_arm", "left_sleeve", (4, 12, -2), (4, 12, 4), (48, 48), inflate=0.25)
    m.bone("right_leg", "root", pivot=(-2, 12, 0))
    m.cube("right_leg", "right_leg", (-4, 0, -2), (4, 12, 4), (0, 16))
    m.cube("right_leg", "right_pants", (-4, 0, -2), (4, 12, 4), (0, 32), inflate=0.25)
    m.bone("left_leg", "root", pivot=(2, 12, 0))
    m.cube("left_leg", "left_leg", (0, 0, -2), (4, 12, 4), (16, 48))
    m.cube("left_leg", "left_pants", (0, 0, -2), (4, 12, 4), (0, 48), inflate=0.25)
    return m


def humanoid_animations() -> dict:
    anims = []
    # idle: slow breathing and a little arm sway
    a = Anim("idle", 3.0)
    a.rot("body", {0: (0, 0, 0), 1.5: (1.5, 0, 0), 3.0: (0, 0, 0)})
    a.rot("right_arm", {0: (0, 0, 2), 1.5: (2, 0, 4), 3.0: (0, 0, 2)})
    a.rot("left_arm", {0: (0, 0, -2), 1.5: (2, 0, -4), 3.0: (0, 0, -2)})
    a.rot("head", {0: (0, 0, 0), 1.5: (-1.5, 3, 0), 3.0: (0, 0, 0)})
    anims.append(a)
    for name, length, amp, arm in (("walk", 1.0, 32, 30), ("run", 0.6, 48, 55)):
        w = Anim(name, length)
        w.rot("right_leg", {0: (amp, 0, 0), length / 2: (-amp, 0, 0), length: (amp, 0, 0)})
        w.rot("left_leg", {0: (-amp, 0, 0), length / 2: (amp, 0, 0), length: (-amp, 0, 0)})
        w.rot("right_arm", {0: (-arm, 0, 4), length / 2: (arm, 0, 4), length: (-arm, 0, 4)})
        w.rot("left_arm", {0: (arm, 0, -4), length / 2: (-arm, 0, -4), length: (arm, 0, -4)})
        if name == "run":
            w.rot("body", {0: (14, 0, 0), length: (14, 0, 0)})
            w.pos("body", {0: (0, 0.5, 0), length / 2: (0, -0.5, 0), length: (0, 0.5, 0)})
        else:
            w.pos("body", {0: (0, 0.3, 0), length / 4: (0, 0, 0), length / 2: (0, 0.3, 0), length * 3 / 4: (0, 0, 0), length: (0, 0.3, 0)})
        anims.append(w)
    at = Anim("attack", 0.6, loop=False)
    at.rot("right_arm", {0: (0, 0, 0), 0.15: (-110, 0, 0), 0.3: (-15, 0, 0), 0.6: (0, 0, 0)})
    at.rot("body", {0: (0, 0, 0), 0.15: (-8, 8, 0), 0.3: (10, -12, 0), 0.6: (0, 0, 0)})
    anims.append(at)
    hu = Anim("hurt", 0.4, loop=False)
    hu.rot("head", {0: (0, 0, 0), 0.1: (-22, 0, 0), 0.4: (0, 0, 0)})
    hu.rot("body", {0: (0, 0, 0), 0.1: (-10, 0, 0), 0.4: (0, 0, 0)})
    anims.append(hu)
    ta = Anim("talk", 2.0)
    ta.rot("head", {0: (0, 0, 0), 0.5: (4, 0, 0), 1.0: (0, 0, 0), 1.5: (4, 0, 0), 2.0: (0, 0, 0)})
    ta.rot("right_arm", {0: (0, 0, 0), 0.5: (-40, 0, 10), 1.0: (-10, 0, 4), 1.5: (-40, 0, 10), 2.0: (0, 0, 0)})
    anims.append(ta)
    th = Anim("throw", 0.7, loop=False)
    th.rot("right_arm", {0: (0, 0, 0), 0.2: (-150, 0, 0), 0.35: (-60, 0, 0), 0.7: (0, 0, 0)})
    th.rot("body", {0: (0, 0, 0), 0.2: (-12, 0, 0), 0.35: (8, 0, 0), 0.7: (0, 0, 0)})
    anims.append(th)
    return animations_json(NAME, anims)


# ------------------------------------------------------------------------------------------------ skins
SKIN_TONES = {"fair": "#e0b090", "tan": "#c89066", "ruddy": "#d49a78", "pale": "#e8c8b0"}


class Cfg:
    def __init__(self, **kw):
        self.__dict__.update(kw)


def part(m: Model, name: str) -> Part:
    return m.parts[name]


def face_basics(t: Tex, head: Part, cfg) -> None:
    """Face on the front of the head: brows, eyes, mouth, plus cfg-specific extras."""
    tone = cfg.skin
    head.face(t, "north", tone)
    t.noise(tone, 0.03, 5, area=head.rect("north"))
    # eyes (row 3-4), brows (row 2), mouth (row 6)
    ew = "#f4f4f0"
    pupil = cfg.eye
    for ex in (1, 5):
        head.box(t, "north", ex, 3, ex + 1, 3, ew)
        head.px(t, "north", ex + (1 if cfg.look_right else 0), 3, pupil)
        head.px(t, "north", ex, 4, shade(tone, 0.85))
        head.px(t, "north", ex + 1, 4, shade(tone, 0.85))
    brow = cfg.brow
    if cfg.unibrow:
        head.box(t, "north", 1, 2, 6, 2, brow)
    else:
        head.box(t, "north", 1, 2, 2, 2, brow)
        head.box(t, "north", 5, 2, 6, 2, brow)
    if cfg.worried:
        head.px(t, "north", 1, 2, tone)
        head.px(t, "north", 6, 2, tone)
        head.px(t, "north", 2, 1, brow)
        head.px(t, "north", 5, 1, brow)
    head.px(t, "north", 3, 4, shade(tone, 0.88))
    head.px(t, "north", 4, 4, shade(tone, 0.88))
    mouth = shade(tone, 0.5)
    if cfg.mouth == "scowl":
        head.box(t, "north", 2, 6, 5, 6, mouth)
        head.px(t, "north", 2, 7, mouth)
        head.px(t, "north", 5, 7, mouth)
    elif cfg.mouth == "grin":
        head.box(t, "north", 2, 6, 5, 6, "#f4f4f0")
        head.box(t, "north", 2, 7, 5, 7, mouth)
    elif cfg.mouth == "flat":
        head.box(t, "north", 2, 6, 5, 6, mouth)
    else:
        head.box(t, "north", 3, 6, 4, 6, mouth)
    if cfg.stubble:
        rnd = random.Random(9)
        for _ in range(14):
            head.px(t, "north", rnd.randrange(1, 7), rnd.randrange(5, 8), shade(tone, 0.72))
    if cfg.moustache:
        head.box(t, "north", 1, 5, 6, 5, cfg.hair)
        head.px(t, "north", 1, 6, cfg.hair)
        head.px(t, "north", 6, 6, cfg.hair)
    if cfg.glasses:
        gc = "#2a2a2e"
        head.box(t, "north", 0, 3, 3, 3, gc)
        head.box(t, "north", 4, 3, 7, 3, gc)
        head.px(t, "north", 0, 4, gc)
        head.px(t, "north", 3, 4, gc)
        head.px(t, "north", 4, 4, gc)
        head.px(t, "north", 7, 4, gc)
        head.box(t, "north", 0, 5, 3, 5, gc) if False else None
    if cfg.scar:
        for dy in range(3):
            head.px(t, "north", 6 - dy // 2, 3 + dy * 1, "#b04a4a")
    # the other faces of the head are skin; hair covers top/back/sides
    for f in ("up", "down", "east", "west", "south"):
        head.face(t, f, tone)
    hair = cfg.hair
    for f, rows in (("up", None), ("south", None), ("east", 4), ("west", 4)):
        x0, y0, x1, y1 = head.rect(f)
        if rows:
            y1 = y0 + rows - 1
        t.noise(hair, 0.08, 7 + len(f), area=(x0, y0, x1, y1))
    x0, y0, x1, y1 = head.rect("north")
    t.noise(hair, 0.08, 17, area=(x0, y0, x1, y0 + 1))                       # fringe
    if cfg.hairline:
        head.box(t, "north", 0, 2, 0, 2, hair)
        head.box(t, "north", 7, 2, 7, 2, hair)


def cap_overlay(t: Tex, hat: Part, color, logo=None, stripe=None) -> None:
    for f in ("up", "north", "south", "east", "west"):
        x0, y0, x1, y1 = hat.rect(f)
        if f == "up":
            t.noise(color, 0.04, 21, area=(x0, y0, x1, y1))
        else:
            t.noise(color, 0.04, 22, area=(x0, y0, x1, y0 + 2 if f != "north" else y0 + 1))
    # brim
    hat.box(t, "north", 0, 2, 7, 2, shade(color, 0.75))
    if logo:
        hat.px(t, "north", 3, 0, logo)
        hat.px(t, "north", 4, 0, logo)
    if stripe:
        x0, y0, x1, y1 = hat.rect("up")
        t.vline((x0 + x1) // 2, y0, y1, stripe)
        x0, y0, x1, y1 = hat.rect("north")
        t.hline(x0, x1, y0, stripe)


def hood_overlay(t: Tex, hat: Part, color) -> None:
    for f in ("up", "south", "east", "west"):
        x0, y0, x1, y1 = hat.rect(f)
        t.noise(color, 0.05, 31, area=(x0, y0, x1, y1))
    x0, y0, x1, y1 = hat.rect("north")
    t.noise(color, 0.05, 32, area=(x0, y0, x1, y0))
    t.noise(color, 0.05, 33, area=(x0, y0, x0, y1))
    t.noise(color, 0.05, 34, area=(x1, y0, x1, y1))


def paint_skin(cfg) -> Tex:
    m = humanoid_model()
    t = m.new_texture()
    body, jacket, head, hat = part(m, "body"), part(m, "jacket"), part(m, "head"), part(m, "hat")
    ra, rs, la, ls = part(m, "right_arm"), part(m, "right_sleeve"), part(m, "left_arm"), part(m, "left_sleeve")
    rl, rp, ll, lp = part(m, "right_leg"), part(m, "right_pants"), part(m, "left_leg"), part(m, "left_pants")
    # base layers: skin under clothes
    for p in (body, ra, la, rl, ll):
        p.fill(t, cfg.under, 0.04, 40)
    face_basics(t, head, cfg)
    # clothes as the first layer (the overlay layers hold details)
    for p, col in ((jacket, cfg.top), (rs, cfg.top), (ls, cfg.top)):
        p.fill(t, col, 0.04, 50)
    for p in (rp, lp):
        p.fill(t, cfg.pants, 0.04, 60)
    # inner layers equal the clothes too, so nothing shows through
    for p in (body, ra, la):
        p.fill(t, cfg.top, 0.04, 70)
    for p in (rl, ll):
        p.fill(t, cfg.pants, 0.04, 71)
    # hands and shoes (on the base layer and on the overlay layer, otherwise the clothes cover them)
    for arm in (ra, la, rs, ls):
        for f in ("north", "south", "east", "west"):
            arm.box(t, f, 0, 10, 3, 11, cfg.skin)
        arm.box(t, "down", 0, 0, 3, 3, cfg.skin)
    for leg in (rl, ll, rp, lp):
        for f in ("north", "south", "east", "west"):
            leg.box(t, f, 0, 10, 3, 11, cfg.shoes)
        leg.face(t, "down", shade(cfg.shoes, 0.6))
    # stripes along sleeves and legs
    if cfg.stripe:
        for sl, side in ((rs, "east"), (ls, "west")):
            for f in ("north", "south"):
                x0, y0, x1, y1 = sl.rect(f)
                t.vline(x0 if side == "east" else x1, y0, y1 - 2, cfg.stripe)
            x0, y0, x1, y1 = sl.rect(side if False else "west" if side == "east" else "east")
            t.vline((x0 + x1) // 2, y0, y1 - 2, cfg.stripe)
        for pl in (rp, lp):
            for f in ("east", "west"):
                x0, y0, x1, y1 = pl.rect(f)
                t.vline((x0 + x1) // 2, y0, y1 - 2, cfg.stripe)
                if cfg.stripe2:
                    t.vline((x0 + x1) // 2 + 1, y0, y1 - 2, cfg.stripe)
        for sl in (rs, ls):
            for f in ("east", "west"):
                x0, y0, x1, y1 = sl.rect(f)
                t.vline((x0 + x1) // 2, y0, y1 - 2, cfg.stripe)
    # jacket details
    zip_c = cfg.zip
    x0, y0, x1, y1 = jacket.rect("north")
    t.vline((x0 + x1) // 2, y0, y1, zip_c)
    t.rect(x0, y0, x1, y0, shade(cfg.top, 0.8))                  # collar
    cfg.extras(t, m) if cfg.extras else None
    # headwear
    if cfg.headwear == "cap":
        cap_overlay(t, hat, cfg.cap, cfg.logo, cfg.cap_stripe)
    elif cfg.headwear == "hood":
        hood_overlay(t, hat, cfg.top)
    return t


def make_cfg(**kw) -> Cfg:
    base = dict(skin=SKIN_TONES["fair"], hair="#2a2018", eye="#2a2a30", brow="#2a2018", unibrow=False, worried=False,
                mouth="flat", stubble=False, moustache=False, glasses=False, scar=False, hairline=False, look_right=False,
                under="#2a2a2e", top="#202024", pants="#202024", shoes="#d8d8d8", stripe=None, stripe2=False, zip="#9a9a9a",
                headwear=None, cap="#16171b", logo=None, cap_stripe=None, extras=None)
    base.update(kw)
    return Cfg(**base)


def extras_semky(t: Tex, m: Model) -> None:
    body = part(m, "body")
    jacket = part(m, "jacket")
    jacket.box(t, "north", 5, 7, 7, 10, "#c9c2ae")            # a paper cone of seeds in the pocket
    jacket.px(t, "north", 6, 7, "#202225")
    ra = part(m, "right_sleeve")
    ra.box(t, "north", 0, 9, 3, 10, "#c9c2ae")


def extras_leader(t: Tex, m: Model) -> None:
    jacket = part(m, "jacket")
    for i in range(5):                                       # garland of tickets across the chest
        jacket.box(t, "north", 1 + i, 3 + (i % 2), 1 + i, 4 + (i % 2), "#e6c46a")
    jacket.box(t, "north", 1, 8, 6, 8, "#a8473f")            # sash


def extras_shlahbaum(t: Tex, m: Model) -> None:
    jacket = part(m, "jacket")
    # orange safety vest with two reflective bands
    for f in ("north", "south"):
        x0, y0, x1, y1 = jacket.rect(f)
        t.rect(x0, y0, x1, y1, "#e8702a")
        t.hline(x0, x1, y0 + 6, "#f0f0e8")
        t.hline(x0, x1, y0 + 9, "#f0f0e8")
        if f == "north":
            t.vline((x0 + x1) // 2, y0, y1, "#8a8a8a")
    for f in ("east", "west"):
        x0, y0, x1, y1 = jacket.rect(f)
        t.rect(x0, y0, x1, y1, "#e8702a")
        t.hline(x0, x1, y0 + 6, "#f0f0e8")
    for s in ("right_sleeve", "left_sleeve"):
        sl = part(m, s)
        for f in ("north", "south", "east", "west"):
            x0, y0, x1, y1 = sl.rect(f)
            for k in range(0, y1 - y0 - 1, 4):
                t.hline(x0, x1, y0 + k, "#f0f0e8" if (k // 4) % 2 == 0 else "#d03a3a")   # barrier stripes


SKINS = {
    "gopnik": lambda: make_cfg(skin=SKIN_TONES["tan"], hair="#241a14", brow="#1a1410", unibrow=True, mouth="scowl", stubble=True,
                               under="#2a2a2e", top="#17181c", pants="#17181c", shoes="#e0e0e0", stripe="#f2f2f2",
                               headwear="cap", cap="#101114", logo="#e0e0e0", zip="#8a8a90"),
    "seed_thrower": lambda: make_cfg(skin=SKIN_TONES["fair"], hair="#3a2a1a", brow="#2a2018", mouth="grin", look_right=True,
                                     top="#26324a", pants="#1c2230", shoes="#b8b8b8", stripe="#cdb86a", headwear="hood", zip="#9aa0aa",
                                     extras=extras_semky),
    "senior_yard_gopnik": lambda: make_cfg(skin=SKIN_TONES["ruddy"], hair="#14100c", brow="#120e0a", unibrow=True, mouth="scowl", stubble=True,
                                           scar=True, top="#0e0e10", pants="#a52a2a", shoes="#e8e8e8", stripe="#e6c46a", stripe2=True,
                                           headwear="cap", cap="#0e0e10", logo="#c9a24a", zip="#c9a24a"),
    "debtor": lambda: make_cfg(skin=SKIN_TONES["pale"], hair="#5a3a22", brow="#4a3018", worried=True, mouth="flat", glasses=True,
                               top="#8a7a5a", pants="#5a5a60", shoes="#3a2a20", zip="#4a4030", under="#5a5a60"),
    "pan_shlahbaum": lambda: make_cfg(skin=SKIN_TONES["ruddy"], hair="#6a6a70", brow="#5a5a60", moustache=True, mouth="flat",
                                      top="#5f705f", pants="#3f4a40", shoes="#26262a", headwear="cap", cap="#5f705f", cap_stripe="#d03a3a",
                                      logo="#f0f0e8", zip="#c9c9c9", extras=extras_shlahbaum),
    "fare_dodger": lambda: make_cfg(skin=SKIN_TONES["fair"], hair="#2a2a22", brow="#2a2a22", mouth="flat", look_right=True,
                                    top="#4a6a3a", pants="#3a4a6a", shoes="#9a9a9a", headwear="hood", zip="#b0b0b0"),
    "fare_dodger_leader": lambda: make_cfg(skin=SKIN_TONES["tan"], hair="#1a1210", brow="#1a1210", unibrow=True, mouth="grin", scar=False,
                                           stubble=True, top="#3a1a1a", pants="#2a1818", shoes="#26181a", headwear="cap", cap="#26181a",
                                           cap_stripe="#e6c46a", logo="#e6c46a", zip="#e6c46a", extras=extras_leader),
}


def paint(name: str) -> Tex:
    return paint_skin(SKINS[name]())
