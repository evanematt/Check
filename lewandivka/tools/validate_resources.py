#!/usr/bin/env python3
"""Static validation of the mod's resources and of the ids used by its source code.

Checks: JSON syntax, model/blockstate/texture references, texture sizes, GeckoLib geometry vs texture and animations,
language completeness (uk_ua and en_us identical, every key used by the code present), sounds.json vs files,
data pack references, and that every vanilla id used in code/resources exists in Minecraft 1.20.1.
Exit code 1 when anything is wrong. Run from the repository root:  python3 tools/validate_resources.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "lewandivka"
DATA = RES / "data" / "lewandivka"
SPEC = ROOT / "tools" / "data" / "spec"
REG = json.loads((ROOT / "tools" / "data" / "mc1201-registry.json").read_text(encoding="utf-8"))
problems = []


def bad(msg: str) -> None:
    problems.append(msg)


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        bad(f"{path.relative_to(ROOT)}: invalid JSON ({e})")
        return None


def spec(name: str):
    return json.loads((SPEC / f"{name}.json").read_text(encoding="utf-8"))


def png_size(path: Path):
    from PIL import Image
    with Image.open(path) as im:
        return im.size, im.convert("RGBA").getextrema()


VANILLA_MODELS = {"minecraft:block/cube_all", "minecraft:block/cube_column", "minecraft:block/orientable", "minecraft:block/cross",
                  "minecraft:block/carpet", "minecraft:block/block", "minecraft:item/generated", "minecraft:item/template_spawn_egg"}
VANILLA_ITEMS = set(REG["items"])
VANILLA_ALL = VANILLA_ITEMS | set(REG["blocks"]) | set(REG["entities"]) | set(REG["particles"]) | set(REG["biomes"]) | set(REG["effects"]) | set(REG["enchantments"])


def texture_path(ref: str) -> Path:
    ns, _, path = ref.partition(":")
    return RES / "assets" / ns / "textures" / f"{path}.png"


def check_json_syntax() -> None:
    for p in RES.rglob("*.json"):
        load(p)
    for p in RES.rglob("*.mcmeta"):
        load(p)


def check_models() -> None:
    blocks = spec("blocks")
    items = spec("items")
    entities = spec("entities")
    ids = {b["id"] for b in blocks}
    for b in blocks:
        if not (ASSETS / "blockstates" / f"{b['id']}.json").exists():
            bad(f"missing blockstate for {b['id']}")
        if not (ASSETS / "models" / "item" / f"{b['id']}.json").exists():
            bad(f"missing item model for block {b['id']}")
        if not (DATA / "loot_tables" / "blocks" / f"{b['id']}.json").exists():
            bad(f"missing loot table for {b['id']}")
    for i in items:
        if not (ASSETS / "models" / "item" / f"{i['id']}.json").exists():
            bad(f"missing item model for {i['id']}")
        if i["id"] not in ids and not (ASSETS / "textures" / "item" / f"{i['id']}.png").exists() and i["id"] != "chromatic_compass":
            bad(f"missing item texture for {i['id']}")
    for e in entities:
        if e["role"] not in ("PROJECTILE", "VEHICLE", "MARKER") and not (ASSETS / "models" / "item" / f"{e['id']}_spawn_egg.json").exists():
            bad(f"missing spawn egg model for {e['id']}")
    for p in (ASSETS / "models").rglob("*.json"):
        m = load(p)
        if not isinstance(m, dict):
            continue
        parent = m.get("parent")
        if parent and parent.startswith("lewandivka:"):
            if not (ASSETS / "models" / f"{parent.split(':')[1]}.json").exists():
                bad(f"{p.relative_to(ROOT)}: missing parent {parent}")
        elif parent and parent not in VANILLA_MODELS:
            bad(f"{p.relative_to(ROOT)}: unknown vanilla parent {parent}")
        for key, ref in (m.get("textures") or {}).items():
            if ref.startswith("#"):
                continue
            if not texture_path(ref if ":" in ref else f"minecraft:{ref}").exists() and ref.startswith("lewandivka:"):
                bad(f"{p.relative_to(ROOT)}: missing texture {ref}")
        for ov in m.get("overrides", []):
            if not (ASSETS / "models" / f"{ov['model'].split(':')[1]}.json").exists():
                bad(f"{p.relative_to(ROOT)}: missing override model {ov['model']}")
    for p in (ASSETS / "blockstates").glob("*.json"):
        st = load(p)
        refs = []
        if "variants" in st:
            for v in st["variants"].values():
                refs += [v] if isinstance(v, dict) else v
        for part in st.get("multipart", []):
            a = part["apply"]
            refs += [a] if isinstance(a, dict) else a
        for r in refs:
            if r["model"].startswith("lewandivka:") and not (ASSETS / "models" / f"{r['model'].split(':')[1]}.json").exists():
                bad(f"{p.relative_to(ROOT)}: missing model {r['model']}")


def check_textures() -> None:
    for p in (ASSETS / "textures").rglob("*.png"):
        (w, h), ext = png_size(p)
        rel = p.relative_to(ASSETS / "textures")
        if ext[3][1] == 0:
            bad(f"{rel}: fully transparent")
        if rel.parts[0] in ("block", "item"):
            if w != 16 or h % 16 != 0:
                bad(f"{rel}: block/item textures must be 16 wide and a multiple of 16 high (got {w}x{h})")
            if h > 16 and not (p.with_name(p.name + ".mcmeta")).exists():
                bad(f"{rel}: animated strip without .mcmeta")


def check_entities() -> None:
    entities = spec("entities")
    for e in entities:
        model = e["model"]
        if model is None:
            continue
        geo_path = ASSETS / "geo" / "entity" / f"{model}.geo.json"
        anim_path = ASSETS / "animations" / "entity" / f"{model}.animation.json"
        tex_path = ASSETS / "textures" / "entity" / f"{e['texture']}.png"
        for p in (geo_path, anim_path, tex_path):
            if not p.exists():
                bad(f"entity {e['id']}: missing {p.relative_to(ROOT)}")
        if not (geo_path.exists() and anim_path.exists() and tex_path.exists()):
            continue
        geo = load(geo_path)
        desc = geo["minecraft:geometry"][0]["description"]
        (w, h), _ = png_size(tex_path)
        if (w, h) != (desc["texture_width"], desc["texture_height"]):
            bad(f"entity {e['id']}: texture {w}x{h} does not match model {desc['texture_width']}x{desc['texture_height']}")
        for bone in geo["minecraft:geometry"][0]["bones"]:
            for c in bone.get("cubes", []):
                u, v = c["uv"]
                sx, sy, sz = c["size"]
                if u < 0 or v < 0 or u + 2 * (sx + sz) > w or v + sy + sz > h:
                    bad(f"entity {e['id']}: cube of bone {bone['name']} has UV outside the texture")
        anims = load(anim_path)["animations"]
        for a in e["animations"]:
            if not any(k.endswith("." + a) and k.startswith("animation.") for k in anims):
                bad(f"entity {e['id']}: animation '{a}' missing in {anim_path.name}")
        bones = {b["name"] for b in geo["minecraft:geometry"][0]["bones"]}
        for name, anim in anims.items():
            for bone in anim.get("bones", {}):
                if bone not in bones:
                    bad(f"{anim_path.name}: {name} animates unknown bone {bone}")


def lang_files():
    uk = load(ASSETS / "lang" / "uk_ua.json") or {}
    en = load(ASSETS / "lang" / "en_us.json") or {}
    return uk, en


def check_lang() -> None:
    uk, en = lang_files()
    if set(uk) != set(en):
        bad(f"uk_ua and en_us differ: {sorted(set(uk) ^ set(en))[:10]}")
    allowed_empty = {k for k in uk if k.startswith("quest.") and k.endswith(".hint")} | {"speaker.lewandivka."}
    for k, v in uk.items():
        if not v.strip() and k not in allowed_empty:
            bad(f"empty Ukrainian text for {k}")
        if k in en and not en[k].strip() and k not in allowed_empty:
            bad(f"empty English text for {k}")
    # keys used by data files
    for p in DATA.rglob("*.json"):
        text = p.read_text(encoding="utf-8")
        for key in re.findall(r'"translate":\s*"([^"]+)"', text):
            if key not in uk:
                bad(f"{p.relative_to(ROOT)}: missing language key {key}")
    # keys used by the Java sources
    key_re = re.compile(r'"((?:[a-z_]+)\.lewandivka\.[a-z0-9_.%]*[a-z0-9_])"')
    for src in list((ROOT / "src").rglob("*.java")) + list((ROOT / "core" / "src" / "main").rglob("*.java")):
        for key in key_re.findall(src.read_text(encoding="utf-8")):
            if key.endswith("."):
                continue
            if key not in uk:
                bad(f"{src.relative_to(ROOT)}: language key {key} is not defined")


def check_sounds() -> None:
    sounds = spec("sounds")
    sj = load(ASSETS / "sounds.json") or {}
    files = set()
    for s in sounds:
        entry = sj.get(s["id"])
        if not entry:
            bad(f"sounds.json: missing {s['id']}")
            continue
        if len(entry["sounds"]) != s["variants"]:
            bad(f"sounds.json: {s['id']} has {len(entry['sounds'])} variants, expected {s['variants']}")
        for f in entry["sounds"]:
            path = ASSETS / "sounds" / f"{f['name'].split(':')[1]}.ogg"
            files.add(path)
            if not path.exists():
                bad(f"missing sound file {path.relative_to(ROOT)}")
    for p in (ASSETS / "sounds").rglob("*.ogg"):
        if p not in files:
            bad(f"orphan sound file {p.relative_to(ROOT)}")


def check_data() -> None:
    items = {i["id"] for i in spec("items")} | {b["id"] for b in spec("blocks")}
    advs = {p.stem for p in (DATA / "advancements").glob("*.json")}
    for p in (DATA / "advancements").glob("*.json"):
        a = load(p)
        if "parent" in a and a["parent"].split(":")[1] not in advs:
            bad(f"{p.name}: parent {a['parent']} missing")
        icon = a.get("display", {}).get("icon", {}).get("item")
        if icon:
            ns, _, name = icon.partition(":")
            if (ns == "lewandivka" and name not in items) or (ns == "minecraft" and name not in VANILLA_ITEMS):
                bad(f"{p.name}: unknown icon item {icon}")
    for p in DATA.rglob("*.json"):
        text = p.read_text(encoding="utf-8")
        for ns, name in re.findall(r'"(minecraft|lewandivka):([a-z0-9_/]+)"', text):
            if ns == "minecraft" and name not in VANILLA_ALL and not any(name.startswith(x) for x in ("planks", "signs", "infiniburn", "uniform", "overworld", "random", "set_", "looting", "survives", "impossible", "block", "entity", "item", "crafting", "textures")):
                if "/" in name or name in ("stone",):
                    continue
                bad(f"{p.relative_to(ROOT)}: unknown vanilla id minecraft:{name}")
    for p in (DATA / "worldgen" / "biome").glob("*.json"):
        b = load(p)
        for key in ("ambient_sound",):
            v = b["effects"].get(key)
            if v and v.startswith("lewandivka:") and v.split(":")[1] not in {s["id"] for s in spec("sounds")}:
                bad(f"{p.name}: unknown sound {v}")
        part = b["effects"].get("particle")
        if part and part["options"]["type"].split(":")[1] not in REG["particles"]:
            bad(f"{p.name}: unknown particle {part['options']['type']}")


def check_source_ids() -> None:
    id_re = re.compile(r'"minecraft:([a-z0-9_/.]+)(?:\[[^\]"]*\])?"')
    for src in list((ROOT / "src").rglob("*.java")) + list((ROOT / "core" / "src" / "main").rglob("*.java")):
        for name in id_re.findall(src.read_text(encoding="utf-8")):
            if "." in name or "/" in name:
                continue                                    # sound events, loot tables, tags
            if name not in VANILLA_ALL:
                bad(f"{src.relative_to(ROOT)}: 'minecraft:{name}' does not exist in Minecraft 1.20.1")


def main() -> int:
    check_json_syntax()
    check_models()
    check_textures()
    check_entities()
    check_lang()
    check_sounds()
    check_data()
    check_source_ids()
    if problems:
        print(f"{len(problems)} problem(s):")
        for p in problems[:200]:
            print("  -", p)
        return 1
    print("resources OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
