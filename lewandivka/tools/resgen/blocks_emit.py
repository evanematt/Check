"""Writes block textures, models, blockstates, item models and loot tables from the block catalog."""
from __future__ import annotations

from .blocks_paint import paint_block
from .paint import Tex
from .spec import ASSETS, DATA, MOD_ID, blocks, combos, combo_name, prop, state_value, write_json

M = MOD_ID
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def tex(name: str) -> str:
    return f"{M}:block/{name}"


def faces_all(texture: str, uv=(0, 0, 16, 16), **extra) -> dict:
    return {d: dict({"uv": list(uv), "texture": texture}, **extra) for d in ("down", "up", "north", "south", "west", "east")}


def box(frm, to, texture="#all", uv=None) -> dict:
    uv = uv or (0, 0, 16, 16)
    return {"from": list(frm), "to": list(to), "faces": faces_all(texture, uv)}


def write_model(name: str, model: dict) -> None:
    write_json(ASSETS / "models" / "block" / f"{name}.json", model)


def write_state(block_id: str, state: dict) -> None:
    write_json(ASSETS / "blockstates" / f"{block_id}.json", state)


def when_for(b: dict, combo: dict, extra: dict | None = None) -> dict:
    cond = {n: state_value(b, n, v) for n, v in combo.items()}
    if extra:
        cond.update(extra)
    return cond


def multipart(parts: list) -> dict:
    return {"multipart": parts}


def emit_shared_models() -> None:
    write_model("plate_base", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#tex"},
        "elements": [{"from": [0, 0, 15], "to": [16, 16, 16], "faces": {
            "north": {"uv": [0, 0, 16, 16], "texture": "#tex"},
            "south": {"uv": [0, 0, 16, 16], "texture": "#tex"},
            "up": {"uv": [0, 0, 16, 1], "texture": "#tex"}, "down": {"uv": [0, 15, 16, 16], "texture": "#tex"},
            "west": {"uv": [15, 0, 16, 16], "texture": "#tex"}, "east": {"uv": [0, 0, 1, 16], "texture": "#tex"}}}],
    })
    write_model("decal_base", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#tex"},
        "elements": [{"from": [0, 0.05, 0], "to": [16, 0.05, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#tex"}, "down": {"uv": [0, 0, 16, 16], "texture": "#tex"}}}],
    })
    write_model("pad_base", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "#side"},
        "elements": [{"from": [0, 0, 0], "to": [16, 2, 16], "faces": {
            "up": {"uv": [0, 0, 16, 16], "texture": "#top"}, "down": {"uv": [0, 0, 16, 16], "texture": "#side"},
            "north": {"uv": [0, 14, 16, 16], "texture": "#side"}, "south": {"uv": [0, 14, 16, 16], "texture": "#side"},
            "west": {"uv": [0, 14, 16, 16], "texture": "#side"}, "east": {"uv": [0, 14, 16, 16], "texture": "#side"}}}],
    })


def item_model(b: dict, kind: str, first: str, extra_tex: str | None = None) -> None:
    path = ASSETS / "models" / "item" / f"{b['id']}.json"
    if kind == "generated":
        write_json(path, {"parent": "minecraft:item/generated", "textures": {"layer0": tex(extra_tex or first)}})
    else:
        write_json(path, {"parent": f"{M}:block/{first}"})


def emit_block(b: dict) -> None:
    bid = b["id"]
    textures = paint_block(b)
    for name, t in textures.items():
        t.save(ASSETS / "textures" / "block" / f"{name}.png")
        if t.h > t.w:   # animated strip
            frames = t.h // t.w
            write_json(ASSETS / "textures" / "block" / f"{name}.png.mcmeta", {"animation": {"frametime": 2, "interpolate": True}})
    model = b["model"]
    cs = combos(b)
    first = combo_name(b, cs[0])
    facing = prop(b, "facing")

    if model == "CUBE":
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(n)}})
            parts.append({"when": when_for(b, c), "apply": {"model": f"{M}:block/{n}"}} if c else {"apply": {"model": f"{M}:block/{n}"}})
        write_state(bid, {"variants": {"": {"model": f"{M}:block/{first}"}}} if len(cs) == 1 else multipart(parts))
        item_model(b, "block", first)
    elif model == "COLUMN":
        write_model(bid, {"parent": "minecraft:block/cube_column", "textures": {"end": tex(f"{bid}_top"), "side": tex(f"{bid}_side")}})
        write_state(bid, {"variants": {"": {"model": f"{M}:block/{bid}"}}})
        item_model(b, "block", bid)
    elif model == "ORIENTABLE":
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": "minecraft:block/orientable", "textures": {
                "front": tex(f"{n}_front") if f"{n}_front" in textures else tex(f"{bid}_front"),
                "side": tex(f"{bid}_side"), "top": tex(f"{bid}_top")}})
            for d, rot in ROT.items():
                parts.append({"when": when_for(b, c, {"facing": d}), "apply": {"model": f"{M}:block/{n}", "y": rot}})
        write_state(bid, multipart(parts))
        item_model(b, "block", first)
    elif model == "PLATE":
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": f"{M}:block/plate_base", "textures": {"tex": tex(n)}})
            for d, rot in ROT.items():
                part = {"when": when_for(b, c, {"facing": d}), "apply": {"model": f"{M}:block/{n}", "y": rot}}
                parts.append(part)
        write_state(bid, multipart(parts))
        item_model(b, "generated", first)
    elif model == "PAD":
        write_model("spring_pad_up", {"parent": f"{M}:block/pad_base", "textures": {"top": tex("spring_pad_up"), "side": tex("spring_pad_side")}})
        write_model("spring_pad_dir", {"parent": f"{M}:block/pad_base", "textures": {"top": tex("spring_pad_arrow"), "side": tex("spring_pad_side")}})
        parts = [{"when": {"facing": "up"}, "apply": {"model": f"{M}:block/spring_pad_up"}}]
        for d, rot in ROT.items():
            parts.append({"when": {"facing": d}, "apply": {"model": f"{M}:block/spring_pad_dir", "y": rot}})
        write_state(bid, multipart(parts))
        item_model(b, "block", "spring_pad_up")
    elif model == "CARPET":
        write_model(bid, {"parent": "minecraft:block/carpet", "textures": {"wool": tex(bid)}})
        write_state(bid, {"variants": {"": {"model": f"{M}:block/{bid}"}}})
        item_model(b, "block", bid)
    elif model == "CLUSTER":
        rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": "minecraft:block/cross", "textures": {"cross": tex(n)}})
            for d, r in rot.items():
                parts.append({"when": when_for(b, c, {"facing": d}), "apply": dict({"model": f"{M}:block/{n}"}, **r)})
        write_state(bid, multipart(parts))
        item_model(b, "generated", first)
    elif model == "ANIMATED":
        write_model(bid, {"parent": "minecraft:block/cube_all", "textures": {"all": tex(bid)}})
        write_state(bid, {"variants": {"": {"model": f"{M}:block/{bid}"}}})
        item_model(b, "generated", bid)
    elif model == "PORTAL":
        write_model(bid, {"parent": "minecraft:block/block", "textures": {"particle": tex(bid), "tex": tex(bid)},
                          "elements": [{"from": [0, 0, 7], "to": [16, 16, 9], "faces": {
                              "north": {"uv": [0, 0, 16, 16], "texture": "#tex"}, "south": {"uv": [0, 0, 16, 16], "texture": "#tex"}}}]})
        write_state(bid, {"variants": {"axis=0": {"model": f"{M}:block/{bid}"}, "axis=1": {"model": f"{M}:block/{bid}", "y": 90}}})
        item_model(b, "generated", bid)
    elif model == "PEDESTAL":
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": "minecraft:block/block", "textures": {"particle": tex(f"{bid}_side"), "side": tex(f"{bid}_side"), "top": tex(f"{n}_top")},
                            "elements": [
                                {"from": [2, 0, 2], "to": [14, 3, 14], "faces": faces_all("#side")},
                                {"from": [4, 3, 4], "to": [12, 10, 12], "faces": faces_all("#side")},
                                {"from": [2, 10, 2], "to": [14, 13, 14], "faces": dict(faces_all("#side"), up={"uv": [0, 0, 16, 16], "texture": "#top"})},
                            ]})
            parts.append({"when": when_for(b, c), "apply": {"model": f"{M}:block/{n}"}})
        write_state(bid, multipart(parts))
        item_model(b, "block", first)
    elif model == "LAMP":
        parts = []
        for c in cs:
            n = combo_name(b, c)
            write_model(n, {"parent": "minecraft:block/block", "textures": {"particle": tex(f"{bid}_pole"), "pole": tex(f"{bid}_pole"), "head": tex(n)},
                            "elements": [
                                {"from": [7, 0, 7], "to": [9, 9, 9], "faces": faces_all("#pole")},
                                {"from": [5, 9, 5], "to": [11, 15, 11], "faces": faces_all("#head")},
                                {"from": [4, 15, 4], "to": [12, 16, 12], "faces": faces_all("#pole")}]})
            parts.append({"when": when_for(b, c), "apply": {"model": f"{M}:block/{n}"}})
        write_state(bid, multipart(parts))
        item_model(b, "block", first)
    elif model == "SMALL":
        emit_small(b, cs, textures)
    else:
        raise ValueError(f"unknown model template {model} for {bid}")

    write_json(DATA / "loot_tables" / "blocks" / f"{bid}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{M}:{bid}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def small_elements(bid: str, c: dict) -> list:
    if bid == "supply_stash":
        return [box((1, 0, 1), (15, 12, 15), "#all")]
    if bid == "cardboard_box":
        if c["small"] == "true":
            return [box((4, 0, 4), (12, 7, 12), "#all")]
        return [box((1, 0, 1), (15, 12, 15), "#all")]
    if bid == "package_block":
        return [box((3, 0, 3), (13, 7, 13), "#all")]
    if bid == "kettle_block":
        return [box((4, 0, 4), (12, 7, 12), "#all"), box((12, 3, 7), (14, 5, 9), "#all"), box((2, 3, 7), (4, 6, 9), "#all"), box((6, 7, 6), (10, 8, 10), "#all")]
    if bid == "seed_bowl":
        return [box((2, 0, 2), (14, 3, 14), "#all")]
    raise KeyError(bid)


def emit_small(b: dict, cs: list, textures: dict) -> None:
    bid = b["id"]
    first = combo_name(b, cs[0])
    parts = []
    for c in cs:
        n = combo_name(b, c)
        if bid == "clue_prop":
            write_model(n, {"parent": f"{M}:block/decal_base", "textures": {"tex": tex(n)}})
        else:
            write_model(n, {"parent": "minecraft:block/block", "textures": {"particle": tex(n), "all": tex(n)}, "elements": small_elements(bid, c)})
        parts.append({"when": when_for(b, c), "apply": {"model": f"{M}:block/{n}"}} if c else {"apply": {"model": f"{M}:block/{n}"}})
    write_state(bid, {"variants": {"": {"model": f"{M}:block/{first}"}}} if len(cs) == 1 else multipart(parts))
    item_model(b, "generated" if bid == "clue_prop" else "block", first)


def generate() -> int:
    emit_shared_models()
    n = 0
    for b in blocks():
        emit_block(b)
        n += 1
    return n
