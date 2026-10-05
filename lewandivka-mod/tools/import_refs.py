"""Переносить текстури з референсів користувача (tools/refs) у мод.

Запуск після gen_textures.py: python3 tools/import_refs.py
"""
import os
from collections import deque

from PIL import Image, ImageFilter

HERE = os.path.dirname(__file__)
REFS = os.path.join(HERE, "refs")
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "lewandivka", "textures")


def load(name):
    return Image.open(os.path.join(REFS, name)).convert("RGBA")


def save(img, path):
    full = os.path.join(TEX, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def cut_sprite(sheet, box, tol=58):
    """Вирізає іконку, прибирає темне тло заливкою від країв, вписує в квадрат."""
    im = sheet.crop(box).copy()
    W, H = im.size
    px = im.load()
    border = [px[x, 0] for x in range(W)] + [px[x, H - 1] for x in range(W)] + [px[0, y] for y in range(H)] + [px[W - 1, y] for y in range(H)]
    bg = tuple(sorted(c[i] for c in border)[len(border) // 2] for i in range(3))

    def close(c):
        return sum((c[i] - bg[i]) ** 2 for i in range(3)) < tol * tol

    seen = [[False] * H for _ in range(W)]
    q = deque()
    for x in range(W):
        q.append((x, 0))
        q.append((x, H - 1))
    for y in range(H):
        q.append((0, y))
        q.append((W - 1, y))
    while q:
        x, y = q.popleft()
        if x < 0 or y < 0 or x >= W or y >= H or seen[x][y]:
            continue
        seen[x][y] = True
        if not close(px[x, y]):
            continue
        px[x, y] = (0, 0, 0, 0)
        q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    bb = im.getbbox()
    if bb:
        im = im.crop(bb)
    s = max(im.size)
    sq = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    sq.paste(im, ((s - im.size[0]) // 2, (s - im.size[1]) // 2))
    return sq


def to_icon(img, size=32):
    small = img.resize((size, size), Image.LANCZOS)
    px = small.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a > 110 else (0, 0, 0, 0)
    return small


def tile(sheet, box, size=16):
    return sheet.crop(box).convert("RGB").resize((size, size), Image.LANCZOS).convert("RGBA")


# ------------------------------------------------------------------ предмети
items = load("items.png")
ITEM_BOXES = {
    "chroma_pill": (15, 42, 168, 182),
    "magic_kettle": (372, 42, 532, 182),
    "garage_parcel": (555, 42, 714, 182),
    "tram_compostor": (733, 42, 918, 182),
    "note_borzhnyk": (943, 42, 1102, 182),
    "note_garage": (1290, 42, 1452, 182),
    "note_tram": (1462, 42, 1632, 182),
    "collar_chinazik": (12, 360, 152, 497),
    "collar_metadonna": (163, 360, 303, 497),
    "sunflower_seeds": (316, 357, 460, 497),
    "dash_sneakers": (476, 357, 658, 497),
    "spring_insoles": (673, 357, 832, 497),
    "glider_ticket": (846, 357, 1028, 497),
    "color_charge": (1043, 357, 1243, 480),
}
for name, box in ITEM_BOXES.items():
    save(to_icon(cut_sprite(items, box)), f"item/{name}.png")
save(to_icon(cut_sprite(load("token.png"), (560, 215, 705, 350))), "item/district_token.png")
save(to_icon(cut_sprite(load("pill.png"), (755, 375, 945, 525))), "item/chroma_pill.png")

# ------------------------------------------------------------------ блоки
blocks = load("blocks.png")
for name, box in {"crystal_rose": (65, 60, 220, 200), "crystal_aqua": (338, 60, 475, 200), "crystal_citrine": (590, 60, 730, 200)}.items():
    save(to_icon(cut_sprite(blocks, box), 32), f"block/{name}.png")
TILES = {
    "glowshroom_cap": (875, 75, 985, 115),
    "glowshroom_stem": (1185, 120, 1212, 185),
    "rainbow_leaves": (1462, 80, 1580, 185),
    "chroma_portal": (322, 285, 378, 425),
    "launch_pad_top": (1330, 320, 1440, 390),
    "lift_switch_off": (60, 545, 150, 650),
    "ticket_validator_off": (210, 560, 315, 650),
    "door_lever_off": (378, 572, 462, 652),
    "boss_altar": (530, 585, 660, 640),
    "guard_door": (730, 525, 805, 660),
    "chroma_bars": (880, 535, 1008, 650),
    "box_switch": (1025, 590, 1093, 650),
    "collector_door": (1195, 525, 1282, 660),
    "shelter_door": (1375, 525, 1462, 660),
    "grey_void": (1535, 545, 1638, 650),
    "cardboard_box": (130, 790, 250, 862),
    "tiny_box": (480, 795, 570, 858),
    "litter_box_top": (790, 785, 890, 832),
    "surprise": (1140, 790, 1225, 850),
    "door_mat": (1420, 785, 1590, 845),
    "kiosk_side": (330, 975, 515, 1085),
    "kiosk_top": (330, 955, 515, 985),
}
for name, box in TILES.items():
    save(tile(blocks, box), f"block/{name}.png")
# Увімкнені стани перемикачів: ті самі, але яскравіші.
for n in ("lift_switch", "ticket_validator", "door_lever"):
    off = Image.open(os.path.join(TEX, f"block/{n}_off.png")).convert("RGBA")
    on = off.point(lambda v: min(255, int(v * 1.45 + 20)))
    save(on, f"block/{n}_on.png")
# Кольорові ґрати — прозорі проміжки між прутами.
bars = Image.open(os.path.join(TEX, "block/chroma_bars.png")).convert("RGBA")
bp = bars.load()
for y in range(16):
    for x in range(16):
        r, g, b, a = bp[x, y]
        if max(r, g, b) < 70:
            bp[x, y] = (0, 0, 0, 0)
save(bars, "block/chroma_bars.png")


# ------------------------------------------------------------------ коти
# Області на UV-аркушах (координати однакові для обох аркушів 1536x1024).
SHEET = {
    "head_top": (930, 50, 1190, 140), "head_left": (922, 160, 988, 268), "head_front": (1000, 152, 1122, 272),
    "head_right": (1136, 160, 1198, 268), "head_back": (1233, 168, 1343, 268), "ear": (1362, 42, 1424, 126),
    "snout": (1437, 170, 1522, 246), "body_left": (922, 318, 1010, 518), "body_top": (1030, 318, 1155, 360),
    "body_front": (1040, 425, 1120, 518), "body_right": (1162, 318, 1266, 518), "body_back": (1285, 318, 1370, 518),
    "tail": (1400, 318, 1468, 568), "leg": (922, 562, 996, 726), "leg2": (1104, 562, 1168, 726), "paw": (1392, 615, 1445, 690),
}

FACES = {  # частина моделі -> грань -> область аркуша (+ поворот)
    "body": {"top": ("body_top", 90), "bottom": ("body_front", 90), "right": ("body_left", 90), "left": ("body_right", 90),
             "front": ("body_front", 0), "back": ("body_back", 0)},
    "chest": {"*": ("body_front", 0)},
    "head": {"front": ("head_front", 0), "left": ("head_left", 0), "right": ("head_right", 0), "back": ("head_back", 0),
             "top": ("head_top", 0), "bottom": ("snout", 0)},
    "muzzle": {"*": ("snout", 0)},
    "ear_l": {"front": ("ear", 0), "*": ("head_top", 0)},
    "ear_r": {"front": ("ear", 0), "*": ("head_top", 0)},
    "cheek_l": {"*": ("head_left", 0)},
    "cheek_r": {"*": ("head_right", 0)},
    "tail": {"*": ("tail", 90)},
    "tail2": {"*": ("tail", 90)},
    "leg_fl": {"bottom": ("paw", 0), "*": ("leg", 0)},
    "leg_fr": {"bottom": ("paw", 0), "*": ("leg", 0)},
    "leg_bl": {"bottom": ("paw", 0), "*": ("leg2", 0)},
    "leg_br": {"bottom": ("paw", 0), "*": ("leg2", 0)},
}


def cat_from_sheet(sheet_name, out_name):
    import importlib.util
    spec = importlib.util.spec_from_file_location("gt", os.path.join(HERE, "gen_textures.py"))
    gt = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gt)
    sheet = load(sheet_name)
    S = 4
    img = Image.new("RGBA", (64 * S, 64 * S), (0, 0, 0, 0))
    for name, parent, pivot, cubes in gt.CAT_PARTS:
        for (u, v), _, (w, h, d) in cubes:
            for face, (fx, fy, fw, fh) in gt.box_faces(u, v, w, h, d).items():
                spec_ = FACES[name].get(face) or FACES[name]["*"]
                region, rot = spec_
                src = sheet.crop(SHEET[region]).rotate(rot, expand=True)
                if name == "muzzle" and face == "front":
                    sw, sh = src.size
                    src = src.crop((sw // 6, sh // 3, sw * 5 // 6, sh))
                W, H = max(1, int(fw * S)), max(1, int(fh * S))
                img.paste(src.resize((W, H), Image.LANCZOS).convert("RGBA"), (int(fx * S), int(fy * S)))
    save(img, f"entity/{out_name}.png")
    # Сплячий варіант: замальовуємо очі шерстю з рядка над ними.
    sleep = img.copy()
    hx, hy, hw, hh = gt.box_faces(0, 32, 6, 6, 5)["front"]
    x0, y0, W, H = hx * S, hy * S, hw * S, hh * S
    px = sleep.load()
    for y in range(y0 + int(H * 0.30), y0 + int(H * 0.62)):
        for x in range(x0, x0 + W):
            px[x, y] = px[x, y0 + int(H * 0.22)]
    for x in range(x0 + int(W * 0.15), x0 + int(W * 0.85)):
        if not (x0 + int(W * 0.42) < x < x0 + int(W * 0.58)):
            px[x, y0 + int(H * 0.5)] = (30, 26, 26, 255)
    save(sleep, f"entity/{out_name}_sleep.png")


cat_from_sheet("chinazik.png", "chinazik")
cat_from_sheet("metadonna.png", "metadonna")
print("refs imported")
