"""Генерує всі текстури мода (піксель-арт) і геометрію котячої моделі.

Запуск: python3 tools/gen_textures.py  (з папки lewandivka-mod). Потрібен Pillow.
Кольори котів зняті з фото: Чіназік — чорний довгошерстий з рудуватим підшерстям і жовто-зеленими очима;
Метадонна — сіра смугаста з білими грудьми, мордочкою і лапками, рожевий ніс.
"""
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "lewandivka")
JAVA = os.path.join(os.path.dirname(__file__), "..", "src", "main", "java", "ua", "lewandivka", "client")
TEX = os.path.join(ROOT, "textures")


def outline(img):
    px = img.load()
    W, H = img.size
    src = img.copy().load()
    for y in range(H):
        for x in range(W):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and src[nx, ny][3] > 0:
                        c = src[nx, ny]
                        px[x, y] = (c[0] // 4, c[1] // 4, c[2] // 4, 255)
                        break
    return img


def out(path, img):
    if path.startswith("item/"):
        img = outline(img)
    full = os.path.join(TEX, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def jitter(c, amt, rnd):
    return tuple(max(0, min(255, int(v + rnd.uniform(-amt, amt)))) for v in c[:3]) + ((c[3],) if len(c) > 3 else (255,))


def mixc(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


# --------------------------------------------------------------------------- box UV

def box_faces(u, v, w, h, d):
    """Повертає області граней (x, y, ширина, висота) у текстурних пікселях моделі."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + 2 * d + w, v + d, w, h),
    }


def paint_box(img, scale, u, v, w, h, d, fn):
    for face, (fx, fy, fw, fh) in box_faces(u, v, w, h, d).items():
        W, H = int(round(fw * scale)), int(round(fh * scale))
        for y in range(H):
            for x in range(W):
                c = fn(face, x, y, W, H)
                if c is not None:
                    img.putpixel((int(fx * scale) + x, int(fy * scale) + y), c)


# --------------------------------------------------------------------------- коти

CAT_PARTS = [
    # name, parent, pivot, [(uv, origin, size)]
    ("body", None, (0, 15, 0), [((0, 0), (-3.5, -3.5, -7), (7, 7, 14))]),
    ("chest", None, (0, 15, -5), [((0, 22), (-3, -0.5, -3), (6, 6, 3))]),
    ("head", None, (0, 13, -7), [((0, 32), (-3, -3, -5), (6, 6, 5))]),
    ("muzzle", "head", (0, 0, 0), [((24, 32), (-1.5, 0.5, -6), (3, 2, 1))]),
    ("ear_l", "head", (0, 0, 0), [((24, 36), (0.8, -5, -2.5), (2, 2, 1)), ((36, 32), (1.3, -6, -2.5), (1, 1, 1))]),
    ("ear_r", "head", (0, 0, 0), [((30, 36), (-2.8, -5, -2.5), (2, 2, 1)), ((40, 32), (-2.3, -6, -2.5), (1, 1, 1))]),
    ("cheek_l", "head", (0, 0, 0), [((44, 0), (3, -0.5, -3.5), (1, 3, 3))]),
    ("cheek_r", "head", (0, 0, 0), [((44, 6), (-4, -0.5, -3.5), (1, 3, 3))]),
    ("tail", None, (0, 13, 6.5), [((0, 46), (-1.5, -1.5, 0), (3, 3, 6))]),
    ("tail2", "tail", (0, 0, 6), [((20, 46), (-1.5, -1.5, 0), (3, 3, 6))]),
    ("leg_fl", None, (1.8, 18, -5), [((44, 12), (-1, 0, -1), (2, 6, 2))]),
    ("leg_fr", None, (-1.8, 18, -5), [((52, 12), (-1, 0, -1), (2, 6, 2))]),
    ("leg_bl", None, (1.8, 18, 5), [((44, 20), (-1, 0, -1), (2, 6, 2))]),
    ("leg_br", None, (-1.8, 18, 5), [((52, 20), (-1, 0, -1), (2, 6, 2))]),
]


def fmt(f):
    s = repr(float(f))
    return s + "f"


def write_cat_model_java():
    lines = [
        "package ua.lewandivka.client;",
        "",
        "import net.minecraft.client.model.ModelData;",
        "import net.minecraft.client.model.ModelPartBuilder;",
        "import net.minecraft.client.model.ModelPartData;",
        "import net.minecraft.client.model.ModelTransform;",
        "import net.minecraft.client.model.TexturedModelData;",
        "",
        "/** Згенеровано tools/gen_textures.py — геометрія збігається з текстурами котів. */",
        "public final class CatModelData {",
        "    public static TexturedModelData create() {",
        "        ModelData data = new ModelData();",
        "        ModelPartData root = data.getRoot();",
    ]
    for name, parent, pivot, cubes in CAT_PARTS:
        b = "ModelPartBuilder.create()"
        for (u, v), (x, y, z), (w, h, d) in cubes:
            b += f".uv({u}, {v}).cuboid({fmt(x)}, {fmt(y)}, {fmt(z)}, {fmt(w)}, {fmt(h)}, {fmt(d)})"
        par = parent if parent else "root"
        lines.append(f"        ModelPartData {name} = {par}.addChild(\"{name}\", {b}, ModelTransform.pivot({fmt(pivot[0])}, {fmt(pivot[1])}, {fmt(pivot[2])}));")
    lines += [
        "        return TexturedModelData.of(data, 64, 64);",
        "    }",
        "",
        "    private CatModelData() {",
        "    }",
        "}",
        "",
    ]
    with open(os.path.join(JAVA, "CatModelData.java"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def cat_texture(kind, sleep):
    S = 2
    img = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    rnd = random.Random(7 if kind == "chinazik" else 11)

    if kind == "chinazik":
        base, dark, light, rust = (34, 28, 26), (20, 17, 16), (54, 44, 38), (78, 56, 42)
        eye, eye_dark, pupil = (196, 208, 92), (140, 150, 60), (12, 10, 10)
        nose, inner_ear = (28, 22, 22), (78, 52, 50)
    else:
        base, dark, light, rust = (141, 138, 134), (84, 80, 76), (170, 167, 162), (120, 116, 110)
        eye, eye_dark, pupil = (182, 196, 92), (128, 140, 60), (14, 14, 14)
        nose, inner_ear = (214, 150, 142), (204, 150, 146)
    white = (240, 236, 230)

    def fur(x, y, streak=True):
        r = rnd.random()
        if r < 0.18:
            c = dark
        elif r < 0.32:
            c = light
        else:
            c = base
        if streak and kind == "chinazik" and (x * 7 + y * 3) % 11 == 0:
            c = light
        return jitter(c, 6, rnd)

    def tabby(stripe_coord, period=5):
        # Смуги «скумбрія» у Метадонни.
        return (stripe_coord % period) in (0, 1)

    def body_fn(face, x, y, W, H):
        if kind == "chinazik":
            c = fur(x, y)
            if face == "bottom" and rnd.random() < 0.4:
                c = jitter(rust, 8, rnd)
            if face in ("right", "left") and y > H - 3 and rnd.random() < 0.35:
                c = jitter(rust, 8, rnd)
            return c
        if face == "bottom" or face == "front":
            return jitter(white, 5, rnd)
        if face in ("right", "left") and y >= H - 3:
            return jitter(white, 5, rnd)
        if face == "top":
            return jitter(dark, 6, rnd) if tabby(y // 1, 6) or x in (W // 2 - 1, W // 2) else fur(x, y, False)
        if face in ("right", "left"):
            return jitter(dark, 6, rnd) if tabby(x + y // 4, 6) else fur(x, y, False)
        return fur(x, y, False)

    def chest_fn(face, x, y, W, H):
        if kind == "chinazik":
            return jitter(rust, 10, rnd) if rnd.random() < 0.45 else fur(x, y)
        return jitter(white, 5, rnd)

    def head_fn(face, x, y, W, H):
        if face == "front":
            # Очі: 3x3 на 2x-текстурі, вертикальна зіниця.
            for ex in (2, 7):
                if ex <= x <= ex + 2 and 4 <= y <= 6:
                    if sleep:
                        return jitter(dark, 4, rnd) if y == 5 else fur(x, y, False)
                    if x == ex + 1:
                        return pupil
                    if x == ex and y == 4:
                        return (238, 244, 200, 255)
                    return eye if y != 6 else eye_dark
            if kind == "metadonna":
                if y >= 7:
                    return jitter(white, 5, rnd)
                if y <= 3 and x in (2, 4, 7, 9) and (x + y) % 2 == 0:
                    return jitter(dark, 6, rnd)
                if y <= 2 and x in (5, 6):
                    return jitter(dark, 6, rnd)
            if kind == "chinazik" and y >= 8 and rnd.random() < 0.3:
                return jitter(rust, 8, rnd)
            return fur(x, y, False)
        if kind == "metadonna":
            if face == "top":
                return jitter(dark, 6, rnd) if x in (3, 5, 6, 8) and y < H - 2 else fur(x, y, False)
            if face == "bottom":
                return jitter(white, 5, rnd)
            if face in ("right", "left") and y >= H - 4:
                return jitter(white, 5, rnd)
            if face in ("right", "left") and y in (3, 4) and x % 3 == 0:
                return jitter(dark, 6, rnd)
        return fur(x, y)

    def muzzle_fn(face, x, y, W, H):
        if face == "front":
            if 2 <= x <= 3 and y <= 1:
                return nose
            if y == 3 and x in (1, 2, 3, 4):
                return (60, 40, 40, 255) if kind == "chinazik" else (188, 150, 146, 255)
        if kind == "chinazik":
            return jitter((44, 36, 32), 5, rnd)
        return jitter(white, 4, rnd)

    def ear_fn(face, x, y, W, H):
        if face == "front" and W >= 4 and 1 <= x <= W - 2 and y >= 1:
            return jitter(inner_ear, 6, rnd)
        return fur(x, y, False)

    def cheek_fn(face, x, y, W, H):
        if kind == "chinazik":
            return jitter(light if (x + y) % 3 == 0 else rust if rnd.random() < 0.3 else base, 7, rnd)
        if y >= H // 2:
            return jitter(white, 5, rnd)
        return fur(x, y, False)

    def tail_fn(face, x, y, W, H):
        if kind == "chinazik":
            c = fur(x, y)
            if rnd.random() < 0.15:
                c = jitter(rust, 8, rnd)
            return c
        coord = x if face in ("right", "left", "top", "bottom") else y
        if face in ("front", "back"):
            return fur(x, y, False)
        return jitter(dark, 6, rnd) if (coord % 4) in (0, 1) else fur(x, y, False)

    def leg_fn(face, x, y, W, H):
        if kind == "metadonna" and y >= H - 4:
            return jitter(white, 4, rnd)
        if face == "bottom":
            return jitter((60, 48, 44) if kind == "chinazik" else (196, 150, 146), 5, rnd)
        if kind == "metadonna" and face in ("right", "left", "front", "back") and y % 4 == 0:
            return jitter(dark, 6, rnd)
        return fur(x, y)

    fns = {"body": body_fn, "chest": chest_fn, "head": head_fn, "muzzle": muzzle_fn, "ear_l": ear_fn, "ear_r": ear_fn,
           "cheek_l": cheek_fn, "cheek_r": cheek_fn, "tail": tail_fn, "tail2": tail_fn,
           "leg_fl": leg_fn, "leg_fr": leg_fn, "leg_bl": leg_fn, "leg_br": leg_fn}
    def shaded(fn):
        def g(face, x, y, W, H):
            c = fn(face, x, y, W, H)
            if c is None:
                return None
            k = {"top": 1.12, "bottom": 0.72}.get(face, 1.06 - 0.28 * y / max(1, H - 1))
            if face == "front" and fn is head_fn:
                k = 1.0
            return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)
        return g

    for name, parent, pivot, cubes in CAT_PARTS:
        for (u, v), _, (w, h, d) in cubes:
            paint_box(img, S, u, v, w, h, d, shaded(fns[name]))
    out(f"entity/{kind}{'_sleep' if sleep else ''}.png", img)


# --------------------------------------------------------------------------- гуманоїди

HUMAN = {
    "head": (0, 0, 8, 8, 8),
    "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4),
    "arm": (40, 16, 4, 12, 4),
    "leg": (0, 16, 4, 12, 4),
}


def human_skin(name, fn, seed):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rnd = random.Random(seed)
    for part, (u, v, w, h, d) in HUMAN.items():
        paint_box(img, 1, u, v, w, h, d, lambda face, x, y, W, H, part=part: fn(part, face, x, y, W, H, rnd))
    out(f"entity/{name}.png", img)


def tracksuit(main, stripe, skin, eye, cap, shoes, translucent=None):
    def fn(part, face, x, y, W, H, rnd):
        def t(c, a=5):
            c = jitter(c, a, rnd)
            if translucent is not None:
                c = c[:3] + (translucent,)
            return c

        if part == "head":
            if face == "front":
                if y == 4 and x in (1, 2, 5, 6):
                    return eye + (255,) if len(eye) == 3 else eye
                if y == 3 and 1 <= x <= 6:
                    return t((40, 40, 40))
                if y == 6 and 2 <= x <= 5:
                    return t((60, 40, 40))
                if y >= 6 and rnd.random() < 0.25:
                    return t(mixc(skin, (40, 40, 40), 0.4))
            if face == "top" or (face != "bottom" and y <= 1):
                return t(cap)
            return t(skin, 8)
        if part == "hat":
            if face == "top":
                return t(cap)
            if face != "bottom" and y <= 1:
                return t(cap)
            if face == "front" and y == 2:
                return t(cap)
            return None
        if part == "body":
            if face == "front" and x in (3, 4):
                return t(stripe if y > 0 else main)
            if face == "front" and y == 0:
                return t(stripe)
            return t(main, 6)
        if part == "arm":
            if y >= H - 3:
                return t(skin, 8)
            if face in ("right", "left") and x in (1, 3):
                return t(stripe)
            return t(main, 6)
        if part == "leg":
            if y >= H - 2:
                return t(shoes)
            if face in ("right", "left") and x in (1, 3):
                return t(stripe)
            return t(main, 6)
        return None

    return fn


def simple_skin(skin, hair, shirt, pants, shoes, eye=(30, 30, 30), extra=None, hat=None):
    def fn(part, face, x, y, W, H, rnd):
        if extra:
            c = extra(part, face, x, y, W, H, rnd)
            if c is not None:
                return c if c != "none" else None
        if part == "head":
            if face == "front":
                if y == 4 and x in (1, 2, 5, 6):
                    return (255, 255, 255, 255) if x in (1, 5) else eye + (255,)
                if y == 6 and 3 <= x <= 4:
                    return jitter(mixc(skin, (90, 40, 40), 0.5), 3, rnd)
                if y <= 1:
                    return jitter(hair, 8, rnd)
            if face == "top" or (face in ("right", "left", "back") and y <= 3) or (face == "back"):
                return jitter(hair, 8, rnd)
            return jitter(skin, 6, rnd)
        if part == "hat":
            if hat:
                return hat(face, x, y, W, H, rnd)
            return None
        if part == "body":
            return jitter(shirt, 6, rnd)
        if part == "arm":
            if y >= H - 3:
                return jitter(skin, 6, rnd)
            return jitter(shirt, 6, rnd)
        if part == "leg":
            if y >= H - 2:
                return jitter(shoes, 5, rnd)
            return jitter(pants, 6, rnd)
        return None

    return fn


def suit_skin(name, skin, suit, stripe, hair, cap, eye, shoes=(238, 238, 238), zombie=False, alpha=255, glow_eyes=False, seed=1):
    """Спортивка як на референсах: чорний костюм, три білі смуги, кепка, білі кросівки. Текстура 2x."""
    S = 2
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    rnd = random.Random(seed)
    sd = mixc(skin, (0, 0, 0), 0.25)

    def A(c, a=None):
        c = c[:3]
        return c + ((a if a is not None else alpha),)

    def shade(c, y, H):
        return mixc(c, (0, 0, 0), 0.18 * y / max(1, H - 1))

    def head(face, x, y, W, H):
        top_c = cap if cap else hair
        if face == "top":
            return A(jitter(top_c, 5, rnd))
        if face == "bottom":
            return A(sd)
        if face == "front":
            if y <= 3:
                return A(jitter(top_c, 5, rnd))
            if y == 6 and (2 <= x <= 6 or 9 <= x <= 13):
                return A(mixc(hair, (0, 0, 0), 0.3))
            if y in (7, 8) and (3 <= x <= 6 or 9 <= x <= 12):
                if zombie or glow_eyes:
                    return eye[:3] + (255,)
                if x in (5, 6, 9, 10):
                    return A(eye) if y == 8 or x in (6, 9) else A((20, 20, 20))
                return A((240, 240, 240))
            if 7 <= x <= 8 and 8 <= y <= 10:
                return A(sd)
            if y == 12 and 5 <= x <= 10:
                return A((96, 48, 48) if not zombie else (40, 20, 20))
            if y >= 11 and rnd.random() < 0.18 and not zombie:
                return A(mixc(skin, (40, 30, 20), 0.35))
            return A(jitter(skin, 4, rnd))
        if y <= 5 or face == "back" and y <= 9:
            return A(jitter(top_c if y <= 3 or not cap else hair, 5, rnd))
        if face in ("right", "left") and 7 <= y <= 9 and 6 <= x <= 8:
            return A(sd)
        return A(jitter(skin, 4, rnd))

    def hat(face, x, y, W, H):
        if not cap:
            return None
        if face == "top":
            if 6 <= x <= 9 and 6 <= y <= 9:
                return A((250, 250, 250))
            return A(jitter(cap, 4, rnd))
        if face == "bottom":
            return None
        if y <= 5:
            if face == "front" and 6 <= x <= 9 and 2 <= y <= 3:
                return A((250, 250, 250))
            return A(jitter(cap, 4, rnd))
        if face == "front" and y == 6:
            return A(mixc(cap, (0, 0, 0), 0.5))
        return None

    def body(face, x, y, W, H):
        if face == "front":
            if 7 <= x <= 8:
                return A((150, 150, 156))
            if y <= 1:
                return A(stripe)
            if 2 <= x <= 4 and 4 <= y <= 5:
                return A(stripe)
        if face in ("top",) and 7 <= x <= 8:
            return A(stripe)
        return A(shade(jitter(suit, 4, rnd), y, H))

    def limb(is_leg):
        def f(face, x, y, W, H):
            if face in ("right", "left") and x in (1, 3, 5) and y < (19 if is_leg else 17):
                return A(stripe)
            if is_leg:
                if y >= 20 or face == "bottom":
                    return A((150, 150, 150) if y == 23 or face == "bottom" else shoes)
            else:
                if y >= 18 or face == "bottom":
                    return A(jitter(skin, 4, rnd))
            return A(shade(jitter(suit, 4, rnd), y, H))
        return f

    for (u, v, w, h, d), fn in (((0, 0, 8, 8, 8), head), ((32, 0, 8, 8, 8), hat), ((16, 16, 8, 12, 4), body),
                                ((40, 16, 4, 12, 4), limb(False)), ((0, 16, 4, 12, 4), limb(True))):
        paint_box(img, S, u, v, w, h, d, fn)
    out(f"entity/{name}.png", img)


def make_humanoids():
    black, white = (24, 24, 28), (240, 240, 240)
    suit_skin("gopnik", (198, 150, 112), black, white, (40, 30, 24), None, (90, 60, 30), seed=1)
    suit_skin("gopnik_cap", (186, 136, 98), black, white, (30, 24, 20), (20, 20, 22), (60, 90, 120), seed=2)
    suit_skin("gopnik_zombie", (96, 150, 80), black, white, (40, 70, 34), (20, 20, 22), (20, 30, 18), zombie=True, seed=3)
    suit_skin("shade", (70, 40, 100), (46, 20, 70), (170, 90, 240), (30, 12, 50), (26, 10, 40), (235, 150, 255),
              shoes=(120, 70, 180), alpha=180, glow_eyes=True, seed=4)
    suit_skin("colorless", (156, 156, 156), (104, 104, 108), (205, 205, 205), (80, 80, 80), (70, 70, 72), (250, 250, 250),
              shoes=(190, 190, 190), glow_eyes=True, seed=5)

    def borz_hat(face, x, y, W, H, rnd):
        if face == "top" or (face in ("right", "left", "back") and y <= 4):
            return jitter((105, 105, 112), 6, rnd)
        return None

    human_skin("borzhnyk", simple_skin((222, 180, 150), (90, 62, 40), (108, 108, 115), (58, 78, 128), (60, 40, 30), eye=(60, 90, 40), hat=borz_hat), 4)

    def king_extra(part, face, x, y, W, H, rnd):
        steel, rust, orange = (120, 120, 126), (130, 72, 44), (196, 104, 40)
        if part == "head" and face == "front":
            if y in (3, 4) and x in (1, 2, 5, 6):
                return (255, 240, 150, 255)
            if y in (6, 7) and 1 <= x <= 6:
                return (60, 60, 64, 255) if x % 2 == 0 else (170, 170, 176, 255)
        if part == "hat":
            if face == "top" or (face != "bottom" and y <= 1 and x % 2 == 0):
                return jitter((232, 192, 40), 10, rnd)
            return "none"
        r = rnd.random()
        if (x // 2 + y // 3) % 3 == 0:
            return jitter(steel, 10, rnd)
        return jitter(orange if r < 0.3 else rust, 12, rnd)

    human_skin("garage_king", simple_skin((0, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0), (0, 0, 0), extra=king_extra), 5)

    def cond_extra(part, face, x, y, W, H, rnd):
        if part == "body" and face == "front":
            if x in (3, 4) and y in (2, 5, 8):
                return (222, 182, 60, 255)
            if x == y // 1 and y < 8:
                return (110, 70, 40, 255)
        if part == "hat":
            if face == "top" or (face != "bottom" and y <= 2):
                if face == "front" and y == 1 and x in (3, 4):
                    return (222, 182, 60, 255)
                return jitter((26, 36, 80), 5, rnd)
            return "none"
        return None

    human_skin("conductor", simple_skin((226, 186, 156), (70, 50, 40), (40, 60, 130), (30, 40, 90), (20, 20, 24), extra=cond_extra), 6)

    def coll_extra(part, face, x, y, W, H, rnd):
        rings = {2: (220, 40, 60), 5: (255, 120, 200), 8: (60, 120, 240), 11: (80, 200, 90)}
        if part in ("body", "leg") and y in rings and face in ("front", "back"):
            return rings[y] + (255,)
        if part == "leg" and y < H - 2:
            return jitter((80, 34, 108), 6, rnd)
        if part == "hat":
            if face == "top" or (face != "bottom" and y <= 3):
                return (120, 50, 160, 255) if y == 3 else jitter((24, 20, 28), 4, rnd)
            return "none"
        if part == "head" and face == "front" and y == 5 and 2 <= x <= 5:
            return (40, 30, 30, 255)
        return None

    human_skin("collector", simple_skin((214, 200, 220), (40, 30, 50), (90, 40, 120), (60, 26, 84), (20, 20, 24), eye=(150, 40, 200), extra=coll_extra), 7)

    def head_extra(part, face, x, y, W, H, rnd):
        if part == "body" and face == "front":
            if x in (3, 4):
                return (40, 40, 44, 255) if y > 0 else (230, 230, 230, 255)
            if x in (2, 5) and y < 6:
                return (225, 225, 225, 255)
            if (x + y) % 9 == 0:
                return (200, 200, 200, 255)
        if part == "head" and face == "front" and y == 4 and x in (2, 5):
            return (255, 255, 255, 255)
        if part == "head" and face == "front" and y == 4 and x in (1, 6):
            return (150, 150, 150, 255)
        return None

    human_skin("colorless_head", simple_skin((160, 160, 160), (70, 70, 70), (88, 88, 94), (70, 70, 74), (40, 40, 40), extra=head_extra), 8)


def villager_overlay():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rnd = random.Random(9)
    red, white, black, stripe = (210, 30, 40, 255), (240, 240, 240, 255), (28, 28, 34, 255), (235, 235, 235, 255)

    def cap(face, x, y, W, H):
        if face == "top":
            return red if ((x + y) // 2) % 2 == 0 else white
        if face != "bottom" and y <= 2:
            return red if ((x + y) // 2) % 2 == 0 else white
        return None

    paint_box(img, 1, 32, 0, 8, 10, 8, cap)

    def suit(face, x, y, W, H):
        if y >= 12:
            return None
        if face == "front" and x in (3, 4):
            return stripe
        if face in ("right", "left") and x in (1, 4):
            return stripe
        return jitter(black, 4, rnd)

    paint_box(img, 1, 0, 38, 8, 20, 6, suit)
    paint_box(img, 1, 16, 20, 8, 12, 6, lambda f, x, y, W, H: stripe if (f == "front" and x in (3, 4)) else jitter(black, 4, rnd))
    paint_box(img, 1, 44, 22, 4, 8, 4, lambda f, x, y, W, H: stripe if x == 1 else jitter(black, 4, rnd))
    paint_box(img, 1, 40, 38, 8, 4, 4, lambda f, x, y, W, H: stripe if y == 1 else jitter(black, 4, rnd))
    out("entity/villager/profession/shlagbaum.png", img)
    out("entity/zombie_villager/profession/shlagbaum.png", img)


def lady_whirl():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    rnd = random.Random(12)

    def shell(face, x, y, W, H):
        wave = math.sin((x + y * 0.7) * 0.9)
        if wave > 0.85:
            return (230, 255, 255, 170)
        return (80 + rnd.randint(0, 20), 210 + rnd.randint(0, 25), 255, 105)

    def pool(face, x, y, W, H):
        if face == "front" and 2 <= x <= 3 and y in (3, 4):
            return (255, 140, 40, 255)
        if (x + 2 * y) % 5 == 0:
            return (120, 200, 255, 255)
        return jitter((40, 120, 230), 12, rnd)

    paint_box(img, 1, 0, 0, 8, 8, 8, shell)
    paint_box(img, 1, 0, 16, 6, 6, 6, pool)
    eye = lambda f, x, y, W, H: (255, 255, 255, 255) if (x == 0 and y == 0) else (16, 24, 60, 255)
    paint_box(img, 1, 32, 0, 2, 2, 2, eye)
    paint_box(img, 1, 32, 4, 2, 2, 2, eye)
    paint_box(img, 1, 32, 8, 1, 1, 1, lambda f, x, y, W, H: (220, 90, 140, 255))
    out("entity/lady_whirl.png", img)


# --------------------------------------------------------------------------- блоки

def tile(fn, seed=0):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            c = fn(x, y, rnd)
            if c is not None:
                img.putpixel((x, y), c if len(c) == 4 else c + (255,))
    return img


def crystal(c1, c2, seed):
    def fn(x, y, rnd):
        facet = ((x + y) // 4 + (x - y) // 5) % 3
        base = [c1, mixc(c1, c2, 0.5), c2][facet][:3]
        if (x + 2 * y) % 13 == 0:
            return (255, 255, 255)
        return jitter(base, 10, rnd)[:3]

    return tile(fn, seed)


def make_blocks():
    out("block/crystal_rose.png", crystal((255, 110, 200), (200, 60, 170), 1))
    out("block/crystal_aqua.png", crystal((90, 250, 240), (40, 170, 220), 2))
    out("block/crystal_citrine.png", crystal((255, 230, 90), (240, 160, 40), 3))

    def cap(x, y, rnd):
        if (x - 4) ** 2 + (y - 5) ** 2 < 4 or (x - 11) ** 2 + (y - 10) ** 2 < 5 or (x - 12) ** 2 + (y - 3) ** 2 < 2:
            return jitter((250, 210, 255), 8, rnd)[:3]
        return jitter((150, 60, 220), 14, rnd)[:3]

    out("block/glowshroom_cap.png", tile(cap, 4))
    out("block/glowshroom_stem.png", tile(lambda x, y, r: jitter((226, 214, 240) if x % 5 else (200, 186, 222), 6, r)[:3], 5))

    def leaves(x, y, rnd):
        if rnd.random() < 0.18:
            return None
        hue = ((x + y) / 30.0) % 1.0
        import colorsys
        r, g, b = colorsys.hsv_to_rgb(hue, 0.55, 1.0)
        c = (int(r * 255), int(g * 255), int(b * 255))
        if rnd.random() < 0.05:
            return (255, 255, 230)
        return jitter(c, 14, rnd)[:3]

    out("block/rainbow_leaves.png", tile(leaves, 6))

    def portal(x, y, rnd):
        import colorsys
        dx, dy = x - 7.5, y - 7.5
        a = math.atan2(dy, dx)
        rr = math.hypot(dx, dy)
        hue = (a / (2 * math.pi) + rr / 10.0) % 1.0
        r, g, b = colorsys.hsv_to_rgb(hue, 0.7, 1.0)
        if x in (0, 15) or y in (0, 15):
            return (60, 20, 90)
        return (int(r * 255), int(g * 255), int(b * 255))

    out("block/chroma_portal.png", tile(portal, 7))
    out("block/launch_pad_top.png", tile(lambda x, y, r: (255, 255, 255) if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 9 else
                                    ((90, 230, 90) if (x // 2 + y // 2) % 2 else (60, 190, 70)), 8))
    out("block/launch_pad_side.png", tile(lambda x, y, r: (40, 40, 44) if y > 11 else (90, 230, 90), 9))

    def node(on, base, lamp_on, lamp_off, hazard=False):
        def fn(x, y, rnd):
            if 5 <= x <= 10 and 2 <= y <= 6:
                return lamp_on if on else lamp_off
            if hazard and y >= 11:
                return (240, 200, 30) if (x + y) % 4 < 2 else (30, 30, 30)
            if x in (0, 15) or y in (0, 15):
                return (40, 40, 44)
            return jitter(base, 8, rnd)[:3]

        return fn

    out("block/lift_switch_off.png", tile(node(False, (120, 120, 128), (255, 220, 60), (90, 40, 40), True), 10))
    out("block/lift_switch_on.png", tile(node(True, (120, 120, 128), (120, 255, 120), (90, 40, 40), True), 10))
    out("block/ticket_validator_off.png", tile(node(False, (210, 60, 50), (120, 255, 120), (60, 30, 30)), 11))
    out("block/ticket_validator_on.png", tile(node(True, (210, 60, 50), (120, 255, 120), (60, 30, 30)), 11))
    out("block/door_lever_off.png", tile(node(False, (150, 110, 60), (255, 200, 255), (70, 40, 70)), 12))
    out("block/door_lever_on.png", tile(node(True, (150, 110, 60), (255, 200, 255), (70, 40, 70)), 12))

    def altar(x, y, rnd):
        if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 12 and (x + y) % 3 == 0:
            return (255, 120, 240)
        if x in (0, 15) or y in (0, 15):
            return (120, 40, 160)
        return jitter((30, 20, 40), 8, rnd)[:3]

    out("block/boss_altar.png", tile(altar, 13))

    def box(x, y, rnd):
        if 7 <= x <= 8:
            return (210, 190, 140)
        if x in (0, 15) or y in (0, 15):
            return (140, 100, 60)
        return jitter((186, 140, 92), 8, rnd)[:3]

    out("block/cardboard_box.png", tile(box, 14))
    out("block/tiny_box.png", tile(box, 15))

    def bars(x, y, rnd):
        import colorsys
        if x % 4 in (1, 2) or y in (0, 15):
            r, g, b = colorsys.hsv_to_rgb((x / 16 + y / 40) % 1.0, 0.6, 1.0)
            return (int(r * 255), int(g * 255), int(b * 255))
        return None

    out("block/chroma_bars.png", tile(bars, 16))

    def bswitch(x, y, rnd):
        if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 8:
            return (230, 40, 50)
        return box(x, y, rnd)

    out("block/box_switch.png", tile(bswitch, 17))

    def gdoor(open_):
        def fn(x, y, rnd):
            if x in (0, 15) or y in (0, 15):
                return (70, 70, 76)
            if open_:
                return None
            if 11 <= x <= 12 and 7 <= y <= 8:
                return (230, 190, 60)
            if y in (4, 11):
                return (90, 90, 96)
            return jitter((150, 150, 158), 6, rnd)[:3]

        return fn

    out("block/guard_door.png", tile(gdoor(False), 18))
    out("block/guard_door_open.png", tile(gdoor(True), 18))

    def cdoor(x, y, rnd):
        rings = {3: (220, 40, 60), 7: (255, 120, 200), 11: (60, 120, 240)}
        if y in rings and 3 <= x <= 12:
            return rings[y]
        if x in (0, 15) or y in (0, 15):
            return (60, 20, 80)
        return jitter((110, 50, 140), 8, rnd)[:3]

    out("block/collector_door.png", tile(cdoor, 19))
    out("block/litter_box_top.png", tile(lambda x, y, r: (60, 120, 220) if x in (0, 1, 14, 15) or y in (0, 1, 14, 15) else jitter((220, 210, 180), 12, r)[:3], 20))
    out("block/litter_box_side.png", tile(lambda x, y, r: jitter((60, 120, 220), 6, r)[:3], 21))
    out("block/surprise.png", tile(lambda x, y, r: jitter((98, 66, 40), 14, r)[:3], 22))

    def kiosk_side(x, y, rnd):
        if y <= 3:
            return (210, 30, 40) if (x // 2) % 2 == 0 else (240, 240, 240)
        if 5 <= y <= 10 and 2 <= x <= 13:
            return (150, 200, 230) if (x + y) % 7 else (220, 240, 255)
        return jitter((200, 170, 120), 8, rnd)[:3]

    out("block/kiosk_side.png", tile(kiosk_side, 23))
    out("block/kiosk_top.png", tile(lambda x, y, r: (210, 30, 40) if ((x + y) // 3) % 2 == 0 else (240, 240, 240), 24))
    out("block/door_mat.png", tile(lambda x, y, r: (240, 200, 60) if (2 <= x <= 13 and 2 <= y <= 13 and (x + y) % 4 == 0) else jitter((200, 110, 50), 8, r)[:3], 25))

    def sdoor(x, y, rnd):
        if (x - 7.5) ** 2 + (y - 6) ** 2 < 10:
            return (255, 220, 120)
        if x in (0, 15) or y in (0, 15):
            return (60, 20, 80)
        return jitter((180, 90, 200), 10, rnd)[:3]

    out("block/shelter_door.png", tile(sdoor, 26))
    out("block/grey_void.png", tile(lambda x, y, r: jitter((128, 128, 128), 20, r)[:3], 27))

    def tram(x, y, rnd):
        if 3 <= x <= 12 and 3 <= y <= 9:
            return (240, 240, 240) if not (5 <= x <= 10 and 5 <= y <= 7) else (40, 120, 60)
        if x in (0, 15) or y in (0, 15):
            return (60, 60, 60)
        return jitter((240, 200, 40), 8, rnd)[:3]

    out("block/tram_stop.png", tile(tram, 28))


# --------------------------------------------------------------------------- предмети

def item(draw_fn, seed=0):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(img, d, random.Random(seed))
    return img


def make_items():
    def pill(img, d, r):
        # Глянцева капсула по діагоналі: рожево-фіолетова половина і світла половина з бліком.
        ax, ay, bx, by, rad = 4.0, 11.5, 11.5, 4.0, 3.2
        for i in range(16):
            for j in range(16):
                px, py = i + 0.5, j + 0.5
                vx, vy = bx - ax, by - ay
                t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
                cx, cy = ax + vx * t, ay + vy * t
                dist = math.hypot(px - cx, py - cy)
                if dist > rad:
                    continue
                side = (px - cx) * (-vy) + (py - cy) * vx  # знак: вгорі-ліворуч чи внизу-праворуч від осі
                if t < 0.5:
                    base = (232, 70, 190) if side > 0 else (150, 40, 170)
                else:
                    base = (250, 240, 255) if side > 0 else (196, 170, 220)
                if abs(t - 0.5) < 0.04:
                    base = (90, 30, 110)
                light = 1.0 - dist / rad * 0.35
                c = tuple(min(255, int(v * light)) for v in base) + (255,)
                img.putpixel((i, j), c)
        d.point([(5, 9), (6, 8), (7, 7)], fill=(255, 220, 250, 255))
        d.point([(10, 4)], fill=(255, 255, 255, 255))

    out("item/chroma_pill.png", item(pill))

    def notebook(img, d, r):
        d.rectangle([3, 1, 12, 14], fill=(120, 84, 48, 255), outline=(60, 40, 24, 255))
        d.rectangle([5, 3, 10, 6], fill=(233, 219, 176, 255))
        d.line([(5, 8), (10, 8)], fill=(233, 219, 176, 255))
        d.line([(5, 10), (9, 10)], fill=(233, 219, 176, 255))
        d.line([(3, 1), (3, 14)], fill=(40, 28, 16, 255))

    out("item/district_notebook.png", item(notebook))

    def token(img, d, r):
        d.ellipse([2, 2, 13, 13], fill=(200, 160, 50, 255), outline=(120, 90, 20, 255))
        d.ellipse([4, 4, 11, 11], outline=(240, 210, 100, 255))
        d.line([(6, 10), (8, 5), (10, 10)], fill=(110, 80, 20, 255))

    out("item/district_token.png", item(token))

    def kettle(img, d, r):
        d.ellipse([3, 5, 12, 14], fill=(220, 60, 70, 255), outline=(140, 30, 40, 255))
        d.rectangle([6, 3, 9, 5], fill=(80, 80, 80, 255))
        d.line([(12, 8), (15, 6)], fill=(140, 30, 40, 255), width=2)
        d.arc([1, 6, 5, 11], 90, 270, fill=(80, 80, 80, 255))
        d.point([(6, 8), (5, 9)], fill=(255, 220, 220, 255))
        d.point([(14, 4), (13, 2), (15, 1)], fill=(200, 255, 255, 255))

    out("item/magic_kettle.png", item(kettle))

    def parcel(img, d, r):
        d.rectangle([2, 4, 13, 13], fill=(186, 140, 92, 255), outline=(120, 80, 40, 255))
        d.line([(7, 4), (7, 13)], fill=(240, 230, 200, 255))
        d.line([(2, 8), (13, 8)], fill=(240, 230, 200, 255))
        d.point([(10, 6), (11, 6)], fill=(20, 20, 20, 255))
        d.line([(9, 11), (12, 11)], fill=(20, 20, 20, 255))

    out("item/garage_parcel.png", item(parcel))

    def compostor(img, d, r):
        d.rectangle([3, 2, 12, 13], fill=(210, 60, 50, 255), outline=(110, 30, 30, 255))
        d.rectangle([5, 4, 10, 7], fill=(230, 230, 230, 255))
        d.rectangle([6, 9, 9, 11], fill=(40, 40, 40, 255))
        d.point([(7, 5), (8, 5)], fill=(40, 160, 60, 255))

    out("item/tram_compostor.png", item(compostor))

    def note(img, d, r):
        d.polygon([(3, 2), (12, 2), (13, 13), (4, 14)], fill=(240, 236, 220, 255), outline=(170, 160, 130, 255))
        for yy in (5, 7, 9, 11):
            d.line([(5, yy), (11, yy)], fill=(80, 80, 120, 255))
        d.rectangle([9, 1, 13, 3], fill=(210, 30, 40, 255))

    out("item/quest_note.png", item(note))

    def collar(color, bell):
        def fn(img, d, r):
            d.ellipse([2, 3, 13, 12], outline=color + (255,), width=2)
            d.ellipse([6, 10, 9, 14], fill=bell + (255,), outline=(120, 100, 20, 255))
        return fn

    out("item/collar_chinazik.png", item(collar((210, 30, 40), (240, 200, 60))))
    out("item/collar_metadonna.png", item(collar((255, 130, 200), (230, 230, 240))))

    def charge(img, d, r):
        import colorsys
        for i in range(16):
            for j in range(16):
                rr = math.hypot(i - 7.5, j - 7.5)
                if rr < 6.5:
                    hue = (math.atan2(j - 7.5, i - 7.5) / (2 * math.pi)) % 1.0
                    a, b, c = colorsys.hsv_to_rgb(hue, max(0.0, rr / 6.5) * 0.8, 1.0)
                    img.putpixel((i, j), (int(a * 255), int(b * 255), int(c * 255), 255))

    out("item/color_charge.png", item(charge))

    def seeds(img, d, r):
        d.polygon([(4, 4), (11, 4), (13, 14), (2, 14)], fill=(240, 200, 40, 255), outline=(150, 110, 20, 255))
        for _ in range(6):
            x, y = r.randint(4, 10), r.randint(7, 12)
            d.point([(x, y)], fill=(30, 30, 30, 255))
        d.line([(5, 3), (10, 3)], fill=(200, 60, 40, 255))

    out("item/sunflower_seeds.png", item(seeds, 3))

    def sneakers(img, d, r):
        d.polygon([(1, 9), (7, 5), (10, 6), (14, 10), (14, 13), (1, 13)], fill=(240, 240, 240, 255), outline=(60, 60, 60, 255))
        d.line([(4, 12), (13, 12)], fill=(60, 60, 60, 255))
        d.line([(5, 8), (9, 11)], fill=(255, 80, 200, 255))
        d.line([(7, 7), (11, 10)], fill=(80, 220, 255, 255))

    out("item/dash_sneakers.png", item(sneakers))

    def insoles(img, d, r):
        d.ellipse([3, 1, 9, 14], fill=(90, 230, 120, 255), outline=(40, 120, 60, 255))
        for yy in range(3, 14, 3):
            d.line([(10, yy), (14, yy + 1)], fill=(180, 180, 190, 255))

    out("item/spring_insoles.png", item(insoles))

    def glider(img, d, r):
        d.rectangle([2, 4, 13, 11], fill=(250, 240, 200, 255), outline=(150, 120, 60, 255))
        d.line([(4, 6), (11, 6)], fill=(80, 80, 120, 255))
        d.point([(5, 9), (7, 9), (9, 9)], fill=(40, 40, 40, 255))
        d.line([(1, 3), (4, 1)], fill=(120, 220, 255, 255))
        d.line([(12, 1), (15, 3)], fill=(120, 220, 255, 255))

    out("item/glider_ticket.png", item(glider))


def make_icon():
    img = Image.new("RGBA", (128, 128))
    for y in range(128):
        for x in range(128):
            t = y / 127
            img.putpixel((x, y), mixc((255, 95, 200), (255, 210, 138), t))
    d = ImageDraw.Draw(img)
    d.ellipse([84, 10, 116, 42], fill=(255, 244, 160, 255))
    d.ellipse([12, 20, 30, 38], fill=(127, 246, 255, 255))
    # Два котячі силуети: великий чорний і маленький сірий.
    d.ellipse([14, 70, 70, 116], fill=(30, 25, 24, 255))
    d.ellipse([20, 48, 52, 80], fill=(30, 25, 24, 255))
    d.polygon([(22, 56), (24, 38), (36, 52)], fill=(30, 25, 24, 255))
    d.polygon([(50, 56), (48, 38), (38, 52)], fill=(30, 25, 24, 255))
    d.ellipse([28, 60, 34, 66], fill=(196, 208, 92, 255))
    d.ellipse([39, 60, 45, 66], fill=(196, 208, 92, 255))
    d.ellipse([76, 92, 110, 118], fill=(141, 138, 134, 255))
    d.ellipse([84, 78, 104, 98], fill=(141, 138, 134, 255))
    d.polygon([(85, 84), (86, 72), (93, 81)], fill=(141, 138, 134, 255))
    d.polygon([(103, 84), (102, 72), (95, 81)], fill=(141, 138, 134, 255))
    d.ellipse([88, 90, 100, 98], fill=(240, 236, 230, 255))
    d.point([(90, 86), (98, 86)], fill=(182, 196, 92, 255))
    out("../icon.png", img)


if __name__ == "__main__":
    write_cat_model_java()
    for k in ("chinazik", "metadonna"):
        cat_texture(k, False)
        cat_texture(k, True)
    make_humanoids()
    villager_overlay()
    lady_whirl()
    make_blocks()
    make_items()
    make_icon()
    print("ok")
