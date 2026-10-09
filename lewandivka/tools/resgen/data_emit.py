"""Data pack parts: recipes, advancements, tags, dimension types, dimensions, biomes, entity loot tables."""
from __future__ import annotations

from .spec import ASSETS, DATA, MOD_ID, RES, advancements, blocks, entities, items, write_json

M = MOD_ID


def text(key: str) -> dict:
    return {"translate": key}


def emit_recipes() -> int:
    write_json(DATA / "recipes" / "abandoned_kiosk.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PPP", "PSP", "IPI"],
        "key": {"P": {"tag": "minecraft:planks"}, "S": {"tag": "minecraft:signs"}, "I": {"item": "minecraft:iron_ingot"}},
        "result": {"item": f"{M}:abandoned_kiosk", "count": 1},
    })
    # the recipe is revealed in the recipe book when the quest asks for it (the server grants this criterion)
    write_json(DATA / "advancements" / "recipes" / "kiosk.json", {
        "criteria": {"unlock": {"trigger": "minecraft:impossible"}},
        "requirements": [["unlock"]],
        "rewards": {"recipes": [f"{M}:abandoned_kiosk"]},
    })
    return 2


def emit_advancements() -> int:
    n = 0
    for a in advancements():
        display = {
            "icon": {"item": a["icon"]},
            "title": text(f"advancements.{M}.{a['id']}.title"),
            "description": text(f"advancements.{M}.{a['id']}.description"),
            "frame": a["frame"],
            "show_toast": True,
            "announce_to_chat": a["frame"] != "task",
            "hidden": a["hidden"],
        }
        obj = {"display": display, "criteria": {"done": {"trigger": "minecraft:impossible"}}, "requirements": [["done"]]}
        if "parent" in a:
            obj["parent"] = f"{M}:{a['parent']}"
        else:
            display["background"] = "minecraft:textures/gui/advancements/backgrounds/stone.png"
            display["show_toast"] = False
            display["announce_to_chat"] = False
        write_json(DATA / "advancements" / f"{a['id']}.json", obj)
        n += 1
    return n


def emit_tags() -> int:
    quest = [f"{M}:{i['id']}" for i in items() if i["quest"]]
    write_json(DATA / "tags" / "items" / "quest_items.json", {"replace": False, "values": quest})
    gates = [f"{M}:{b['id']}" for b in blocks() if b["behaviour"] in ("GATE", "DASH_GATE")]
    write_json(DATA / "tags" / "blocks" / "gates.json", {"replace": False, "values": gates})
    stations = [f"{M}:{b['id']}" for b in blocks() if b["behaviour"] in ("STATION", "PEDESTAL", "KIOSK", "STASH", "NOTE", "CHECKPOINT")]
    write_json(DATA / "tags" / "blocks" / "stations.json", {"replace": False, "values": stations})
    write_json(DATA / "tags" / "items" / "seeds.json", {"replace": False, "values": [f"{M}:semky"]})
    write_json(DATA / "tags" / "entity_types" / "district_hostiles.json", {
        "replace": False, "values": [f"{M}:gopnik", f"{M}:seed_thrower", f"{M}:senior_yard_gopnik", f"{M}:fare_dodger"]})
    return 5


def dimension_type(min_y: int, height: int, ambient: float, fixed_time: int | None, raids: bool = False) -> dict:
    d = {
        "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
        "ambient_light": ambient, "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": raids,
        "logical_height": height, "min_y": min_y, "height": height, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": "minecraft:overworld", "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "value": {"min_inclusive": 0, "max_inclusive": 7}},
    }
    if fixed_time is not None:
        d["fixed_time"] = fixed_time
    return d


def emit_dimensions() -> int:
    write_json(DATA / "dimension_type" / "district.json", dimension_type(-64, 384, 0.05, None, raids=True))
    write_json(DATA / "dimension_type" / "chromandivka.json", dimension_type(0, 320, 0.25, 6000))
    for name in ("district", "chromandivka"):
        write_json(DATA / "dimension" / f"{name}.json", {
            "type": f"{M}:{name}",
            "generator": {"type": f"{M}:plan", "plan": name, "biome_source": {"type": f"{M}:plan", "plan": name}},
        })
    return 4


SPAWNERS = {k: [] for k in ("monster", "creature", "ambient", "axolotls", "underground_water_creature", "water_creature", "water_ambient", "misc")}


def biome(sky, fog, water, water_fog, grass=None, foliage=None, particle=None, ambient=None, mood=None, additions=None, music=None,
          rain=False) -> dict:
    eff = {"sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog}
    if grass is not None:
        eff["grass_color"] = grass
    if foliage is not None:
        eff["foliage_color"] = foliage
    if particle:
        eff["particle"] = {"probability": particle[1], "options": {"type": particle[0]}}
    if ambient:
        eff["ambient_sound"] = ambient
    if mood:
        eff["mood_sound"] = {"sound": mood, "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0}
    if additions:
        eff["additions_sound"] = {"sound": additions, "tick_chance": 0.0111}
    if music:
        eff["music"] = {"sound": music, "min_delay": 6000, "max_delay": 18000, "replace_current_music": False}
    return {"has_precipitation": rain, "temperature": 0.6, "downfall": 0.2, "effects": eff, "spawners": SPAWNERS,
            "spawn_costs": {}, "carvers": {}, "features": []}


def rgb(h: str) -> int:
    return int(h.lstrip("#"), 16)


def emit_biomes() -> int:
    b = {
        "district": biome(rgb("#8a7aa8"), rgb("#c09a8a"), rgb("#4a6a68"), rgb("#2a3a3a"), rgb("#6e8a4e"), rgb("#5a7a3a"),
                          ambient=f"{M}:ambient.district.loop", mood=f"{M}:ambient.district.mood", music=f"{M}:music.district",
                          rain=True),   # the city lies in the open country: it rains there like everywhere else
        "chromatic_meadow": biome(rgb("#ff9ad0"), rgb("#ffc8e6"), rgb("#2fd6c0"), rgb("#1a8a80"), rgb("#2fe0c0"), rgb("#1fc0a0"),
                                  particle=("minecraft:cherry_leaves", 0.003), ambient=f"{M}:ambient.chroma.loop",
                                  additions=f"{M}:ambient.chroma.additions", music=f"{M}:music.chroma"),
        "glowshroom_basin": biome(rgb("#e07ad0"), rgb("#c070c0"), rgb("#7a50d0"), rgb("#3a2a7a"), rgb("#3fd0b0"), rgb("#9a5ae0"),
                                  particle=("minecraft:glow", 0.006), ambient=f"{M}:ambient.chroma.loop",
                                  additions=f"{M}:ambient.chroma.additions", music=f"{M}:music.chroma"),
        "orange_riverlands": biome(rgb("#ffa0c0"), rgb("#ffb890"), rgb("#ff8a1f"), rgb("#c05a10"), rgb("#5fe0a0"), rgb("#3fc080"),
                                   particle=("minecraft:end_rod", 0.0015), ambient=f"{M}:ambient.chroma.loop",
                                   additions=f"{M}:ambient.chroma.additions", music=f"{M}:music.chroma"),
        "crystal_shelf": biome(rgb("#d0a0ff"), rgb("#e0c0ff"), rgb("#5ad0f0"), rgb("#2a7a9a"), rgb("#6fe0e0"), rgb("#4fc0d0"),
                               particle=("minecraft:end_rod", 0.003), ambient=f"{M}:ambient.chroma.loop",
                               additions=f"{M}:ambient.chroma.additions", music=f"{M}:music.chroma"),
        "floating_garden": biome(rgb("#ff90c8"), rgb("#ffd0e8"), rgb("#50e0e0"), rgb("#208a8a"), rgb("#7ae070"), rgb("#4fc050"),
                                 particle=("minecraft:cherry_leaves", 0.004), ambient=f"{M}:ambient.chroma.loop",
                                 additions=f"{M}:ambient.chroma.additions", music=f"{M}:music.chroma"),
        "grey_scar": biome(rgb("#9a9aa2"), rgb("#a8a8b0"), rgb("#8a9098"), rgb("#4a5058"), rgb("#8a8f8a"), rgb("#7a807a"),
                           ambient=f"{M}:ambient.tower.loop", music=f"{M}:music.tower"),
        "dry_lake": biome(rgb("#ffa070"), rgb("#ffc090"), rgb("#ff8a1f"), rgb("#c05a10"), rgb("#b8c070"), rgb("#98a050"),
                          ambient=f"{M}:ambient.aquapark", music=f"{M}:music.chroma"),
        "tower": biome(rgb("#888890"), rgb("#909098"), rgb("#7a8088"), rgb("#4a5058"), rgb("#808480"), rgb("#707470"),
                       ambient=f"{M}:ambient.tower.loop", music=f"{M}:music.tower"),
    }
    for name, obj in b.items():
        write_json(DATA / "worldgen" / "biome" / f"{name}.json", obj)
    return len(b)


def loot(entries: list, conditions=None) -> dict:
    return {"type": "minecraft:entity", "pools": entries}


def item_pool(item: str, lo: int, hi: int, chance: float | None = None, looting: bool = True) -> dict:
    fn = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        fn.append({"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    pool = {"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item, "functions": fn}]}
    if chance is not None:
        pool["conditions"] = [{"condition": "minecraft:random_chance_with_looting", "chance": chance, "looting_multiplier": 0.05}]
    return pool


def emit_loot() -> int:
    seeds, token = f"{M}:semky", f"{M}:district_token"
    write_json(DATA / "loot_tables" / "entities" / "gopnik.json", loot([item_pool(seeds, 0, 2), item_pool(token, 1, 1, 0.22)]))
    write_json(DATA / "loot_tables" / "entities" / "seed_thrower.json", loot([item_pool(seeds, 1, 3), item_pool(token, 1, 1, 0.16)]))
    write_json(DATA / "loot_tables" / "entities" / "senior_yard_gopnik.json", loot([item_pool(seeds, 2, 4), item_pool(token, 1, 1, None, looting=False)]))
    write_json(DATA / "loot_tables" / "entities" / "fare_dodger.json", loot([item_pool("minecraft:paper", 0, 2)]))
    return 4


def emit_pack_meta() -> int:
    write_json(RES / "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Левандівка: по той бік району"}})
    return 1


def generate() -> int:
    return (emit_recipes() + emit_advancements() + emit_tags() + emit_dimensions() + emit_biomes() + emit_loot() + emit_pack_meta())
