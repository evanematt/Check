"""Генерує JSON-ресурси мода: blockstates, моделі, мову, лут, теги, вимір і біоми, досягнення."""
import json
import os

BASE = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
A = os.path.join(BASE, "assets", "lewandivka")
D = os.path.join(BASE, "data", "lewandivka")
M = "lewandivka"


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


# ------------------------------------------------------------------ блоки
SIMPLE_CUBES = ["crystal_rose", "crystal_aqua", "crystal_citrine", "glowshroom_cap", "glowshroom_stem", "chroma_portal",
                "boss_altar", "cardboard_box", "chroma_bars", "box_switch", "collector_door", "shelter_door", "grey_void", "tram_stop"]

for b in SIMPLE_CUBES:
    w(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": f"{M}:block/{b}"}}})
    w(f"{A}/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/{b}"}})

w(f"{A}/blockstates/rainbow_leaves.json", {"variants": {"": {"model": f"{M}:block/rainbow_leaves"}}})
w(f"{A}/models/block/rainbow_leaves.json", {"parent": "minecraft:block/leaves", "textures": {"all": f"{M}:block/rainbow_leaves"}})

for b in ["lift_switch", "ticket_validator", "door_lever"]:
    w(f"{A}/blockstates/{b}.json", {"variants": {"active=false": {"model": f"{M}:block/{b}_off"},
                                                 "active=true": {"model": f"{M}:block/{b}_on"}}})
    for s in ("off", "on"):
        w(f"{A}/models/block/{b}_{s}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/{b}_{s}"}})

w(f"{A}/blockstates/guard_door.json", {"variants": {"open=false": {"model": f"{M}:block/guard_door"},
                                                    "open=true": {"model": f"{M}:block/guard_door_open"}}})
w(f"{A}/models/block/guard_door.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/guard_door"}})
w(f"{A}/models/block/guard_door_open.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{M}:block/guard_door_open"}})

w(f"{A}/blockstates/kiosk.json", {"variants": {"": {"model": f"{M}:block/kiosk"}}})
w(f"{A}/models/block/kiosk.json", {"parent": "minecraft:block/cube_bottom_top",
                                    "textures": {"top": f"{M}:block/kiosk_top", "bottom": "minecraft:block/oak_planks", "side": f"{M}:block/kiosk_side"}})


def box_model(name, frm, to, tex_top, tex_side, tex_bottom=None):
    faces = {}
    for f in ("north", "south", "east", "west"):
        faces[f] = {"texture": "#side"}
    faces["up"] = {"texture": "#top"}
    faces["down"] = {"texture": "#bottom"}
    w(f"{A}/models/block/{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": tex_side, "top": tex_top, "side": tex_side, "bottom": tex_bottom or tex_side},
        "elements": [{"from": frm, "to": to, "faces": faces}]})
    w(f"{A}/blockstates/{name}.json", {"variants": {"": {"model": f"{M}:block/{name}"}}})


box_model("launch_pad", [0, 0, 0], [16, 4, 16], f"{M}:block/launch_pad_top", f"{M}:block/launch_pad_side")
box_model("litter_box", [1, 0, 1], [15, 5, 15], f"{M}:block/litter_box_top", f"{M}:block/litter_box_side")
box_model("surprise", [5, 0, 5], [11, 4, 11], f"{M}:block/surprise", f"{M}:block/surprise")
box_model("tiny_box", [4, 0, 4], [12, 6, 12], f"{M}:block/tiny_box", f"{M}:block/tiny_box")
box_model("door_mat", [0, 0, 0], [16, 1, 16], f"{M}:block/door_mat", f"{M}:block/door_mat")

ALL_BLOCKS = SIMPLE_CUBES + ["rainbow_leaves", "lift_switch", "ticket_validator", "door_lever", "guard_door", "kiosk",
                             "launch_pad", "litter_box", "surprise", "tiny_box", "door_mat"]
for b in ALL_BLOCKS:
    parent = f"{M}:block/{b}_off" if b in ("lift_switch", "ticket_validator", "door_lever") else f"{M}:block/{b}"
    w(f"{A}/models/item/{b}.json", {"parent": parent})

# ------------------------------------------------------------------ предмети
ITEMS = ["chroma_pill", "district_token", "magic_kettle", "garage_parcel", "tram_compostor", "collar_chinazik", "collar_metadonna",
         "color_charge", "sunflower_seeds", "dash_sneakers", "spring_insoles", "glider_ticket"]
for i in ITEMS:
    w(f"{A}/models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/{i}"}})
for n in ("note_borzhnyk", "note_garage", "note_tram"):
    w(f"{A}/models/item/{n}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/quest_note"}})
for e in ("gopnik", "shade", "colorless", "borzhnyk"):
    w(f"{A}/models/item/{e}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

# ------------------------------------------------------------------ мова
names = {
    "item.lewandivka.chroma_pill": "Таблетка «Хрома»",
    "item.lewandivka.district_token": "Жетон району",
    "item.lewandivka.magic_kettle": "Магічний чайник",
    "item.lewandivka.garage_parcel": "Посилка з Гаража № 13",
    "item.lewandivka.tram_compostor": "Компостер останнього трамваю",
    "item.lewandivka.note_borzhnyk": "Записка: Боржник",
    "item.lewandivka.note_garage": "Записка: Гараж № 13",
    "item.lewandivka.note_tram": "Записка: Останній трамвай",
    "item.lewandivka.collar_chinazik": "Нашийник Чіназіка",
    "item.lewandivka.collar_metadonna": "Нашийник Метадонни",
    "item.lewandivka.color_charge": "Кольоровий заряд",
    "item.lewandivka.sunflower_seeds": "Семки",
    "item.lewandivka.dash_sneakers": "Кросівки-ривок",
    "item.lewandivka.spring_insoles": "Пружні устілки",
    "item.lewandivka.glider_ticket": "Квиток-планер",
    "item.lewandivka.gopnik_spawn_egg": "Яйце виклику гопника",
    "item.lewandivka.shade_spawn_egg": "Яйце виклику безквиткового",
    "item.lewandivka.colorless_spawn_egg": "Яйце виклику безбарвника",
    "item.lewandivka.borzhnyk_spawn_egg": "Яйце виклику Боржника",
    "block.lewandivka.crystal_rose": "Рожевий кристал",
    "block.lewandivka.crystal_aqua": "Бірюзовий кристал",
    "block.lewandivka.crystal_citrine": "Цитриновий кристал",
    "block.lewandivka.glowshroom_cap": "Капелюх світлогриба",
    "block.lewandivka.glowshroom_stem": "Ніжка світлогриба",
    "block.lewandivka.rainbow_leaves": "Веселкове листя",
    "block.lewandivka.chroma_portal": "Кольоровий портал",
    "block.lewandivka.launch_pad": "Пружний люк",
    "block.lewandivka.tram_stop": "Трамвайна зупинка",
    "block.lewandivka.lift_switch": "Підйомник",
    "block.lewandivka.ticket_validator": "Компостер",
    "block.lewandivka.door_lever": "Важіль дверей",
    "block.lewandivka.boss_altar": "Вівтар виклику",
    "block.lewandivka.cardboard_box": "Картонна коробка",
    "block.lewandivka.tiny_box": "Крихітна коробочка",
    "block.lewandivka.chroma_bars": "Кольорові ґрати",
    "block.lewandivka.box_switch": "Кнопка в коробці",
    "block.lewandivka.guard_door": "Охоронні двері",
    "block.lewandivka.collector_door": "Двері Колекціонера",
    "block.lewandivka.litter_box": "Котячий лоток",
    "block.lewandivka.surprise": "Сюрприз",
    "block.lewandivka.kiosk": "Закинутий кіоск",
    "block.lewandivka.door_mat": "Килимок біля дверей",
    "block.lewandivka.shelter_door": "Двері Притулку",
    "block.lewandivka.grey_void": "Сіра порожнеча",
    "entity.lewandivka.chinazik": "Чіназік",
    "entity.lewandivka.metadonna": "Метадонна",
    "entity.lewandivka.gopnik": "Гопник",
    "entity.lewandivka.shade": "Тіньовий безквитковий",
    "entity.lewandivka.colorless": "Безбарвник",
    "entity.lewandivka.borzhnyk": "Боржник",
    "entity.lewandivka.garage_king": "Гаражний Король",
    "entity.lewandivka.lady_whirl": "Пані Вирва",
    "entity.lewandivka.conductor": "Кондуктор Останнього Рейсу",
    "entity.lewandivka.collector": "Колекціонер Нашийників",
    "entity.lewandivka.colorless_head": "Безбарвний Голова району",
    "entity.minecraft.villager.shlagbaum": "Шлагбаум",
    "entity.minecraft.villager.lewandivka.shlagbaum": "Шлагбаум",
    "biome.lewandivka.rainbow_valley": "Веселкова долина",
    "biome.lewandivka.glowshroom_forest": "Світлогрибний ліс",
    "biome.lewandivka.candy_meadows": "Цукеркові луки",
    "biome.lewandivka.grey_district": "Сірий район",
    "advancements.lewandivka.root.title": "Левандівка",
    "advancements.lewandivka.root.description": "Ковтнути Хрому. Одному не працює.",
    "advancements.lewandivka.chromandivka.title": "По той бік району",
    "advancements.lewandivka.chromandivka.description": "Потрапити в Хромандівку",
    "advancements.lewandivka.cats.title": "Двоє, які знають дорогу",
    "advancements.lewandivka.cats.description": "Повернути імена Чіназіку і Метадонні",
    "advancements.lewandivka.almost_hit.title": "Майже влучив",
    "advancements.lewandivka.almost_hit.description": "Прибрати сюрприз Чіназіка совком",
    "advancements.lewandivka.victory.title": "Ну шо, котів вигуляли?",
    "advancements.lewandivka.victory.description": "Перемогти Безбарвного Голову району",
}
w(f"{A}/lang/uk_ua.json", names)
w(f"{A}/lang/en_us.json", names)

# ------------------------------------------------------------------ лут
def self_drop(b):
    w(f"{D}/loot_tables/blocks/{b}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{M}:{b}"}],
                                                                                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


for b in ["crystal_rose", "crystal_aqua", "crystal_citrine", "glowshroom_cap", "glowshroom_stem", "rainbow_leaves", "chroma_portal",
          "launch_pad", "litter_box", "kiosk", "door_mat"]:
    self_drop(b)


def ent_loot(name, pools):
    w(f"{D}/loot_tables/entities/{name}.json", {"type": "minecraft:entity", "pools": pools})


def pool(item, lo, hi, looting=True):
    funcs = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    if looting:
        funcs.append({"function": "minecraft:looting_enchant", "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    return {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": funcs}]}


ent_loot("gopnik", [pool(f"{M}:district_token", 1, 2), pool(f"{M}:sunflower_seeds", 0, 3, False)])
ent_loot("shade", [pool(f"{M}:district_token", 0, 2)])
ent_loot("colorless", [pool(f"{M}:district_token", 0, 1), pool("minecraft:gray_dye", 0, 2, False)])
ent_loot("garage_king", [pool("minecraft:iron_ingot", 6, 12), pool(f"{M}:crystal_citrine", 2, 4, False)])
ent_loot("lady_whirl", [pool("minecraft:prismarine_shard", 6, 12), pool(f"{M}:crystal_aqua", 2, 4, False)])
ent_loot("conductor", [pool("minecraft:emerald", 4, 8), pool(f"{M}:crystal_rose", 2, 4, False)])
ent_loot("collector", [pool(f"{M}:collar_chinazik", 1, 1, False), pool(f"{M}:collar_metadonna", 1, 1, False)])
ent_loot("colorless_head", [pool("minecraft:diamond", 4, 8), pool("minecraft:nether_star", 1, 1, False)])

# ------------------------------------------------------------------ теги
mc = os.path.join(BASE, "data", "minecraft", "tags", "blocks")
w(f"{mc}/mineable/pickaxe.json", {"replace": False, "values": [f"{M}:crystal_rose", f"{M}:crystal_aqua", f"{M}:crystal_citrine",
                                                                 f"{M}:launch_pad", f"{M}:chroma_portal", f"{M}:litter_box"]})
w(f"{mc}/mineable/axe.json", {"replace": False, "values": [f"{M}:kiosk", f"{M}:glowshroom_cap", f"{M}:glowshroom_stem", f"{M}:cardboard_box"]})
w(f"{mc}/mineable/hoe.json", {"replace": False, "values": [f"{M}:rainbow_leaves"]})
w(f"{mc}/mineable/shovel.json", {"replace": False, "values": [f"{M}:surprise"]})
w(f"{mc}/leaves.json", {"replace": False, "values": [f"{M}:rainbow_leaves"]})

# ------------------------------------------------------------------ вимір
w(f"{D}/dimension_type/chromandivka.json", {
    "ultrawarm": False, "natural": True, "piglin_safe": False, "respawn_anchor_works": False, "bed_works": True,
    "has_raids": False, "has_skylight": True, "has_ceiling": False, "coordinate_scale": 1.0, "ambient_light": 0.05,
    "logical_height": 384, "effects": "minecraft:overworld", "infiniburn": "#minecraft:infiniburn_overworld",
    "min_y": -64, "height": 384,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "value": {"min_inclusive": 0, "max_inclusive": 7}},
    "monster_spawn_block_light_limit": 0})

BIOMES = {
    # name: (temperature range, humidity range, effects, monsters, extra vegetal features)
    "rainbow_valley": ([-1.0, 0.1], [-1.0, 0.2],
                       dict(sky_color=0xFF6FD8, fog_color=0xFFC8A0, water_color=0x3DF5E0, water_fog_color=0x1FB8B0,
                            grass_color=0x33F0C8, foliage_color=0xFF7AD9),
                       [("shade", 60)], ["rainbow_tree", "crystal_spike", "floating_island", "giant_glowshroom_sparse"]),
    "glowshroom_forest": ([-1.0, 0.1], [0.2, 1.0],
                          dict(sky_color=0xC070FF, fog_color=0xE0A8FF, water_color=0xFF9A3C, water_fog_color=0xD06010,
                               grass_color=0xA060FF, foliage_color=0x6AF0FF),
                          [("shade", 60)], ["giant_glowshroom", "crystal_spike", "floating_island"]),
    "candy_meadows": ([0.1, 1.0], [-1.0, 0.55],
                      dict(sky_color=0xFF8FB0, fog_color=0xFFE0A0, water_color=0xFF6A3D, water_fog_color=0xC04020,
                           grass_color=0xFFE14A, foliage_color=0xFF4FA0),
                      [("shade", 50), ("colorless", 10)], ["rainbow_tree_sparse", "floating_island", "crystal_spike"]),
    "grey_district": ([0.1, 1.0], [0.55, 1.0],
                      dict(sky_color=0x9A9A9A, fog_color=0xB4B4B4, water_color=0x7A7A7A, water_fog_color=0x505050,
                           grass_color=0x8C8C8C, foliage_color=0x777777),
                      [("colorless", 80), ("shade", 20)], ["crystal_spike_rare"]),
}

# Єдиний порядок фіч для всіх біомів (щоб не було «feature order cycle»).
STEPS = [
    [], [], ["minecraft:amethyst_geode"], ["minecraft:monster_room"], [], [],
    ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
     "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_redstone", "minecraft:ore_diamond",
     "minecraft:ore_lapis", "minecraft:ore_copper"],
    ["minecraft:glow_lichen"], ["minecraft:spring_water"],
    None,  # рослинність — за біомом
    ["minecraft:freeze_top_layer"],
]
VEG_ORDER = ["minecraft:patch_grass_plain", "minecraft:flower_default", "minecraft:flower_meadow", "minecraft:patch_dead_bush",
             f"{M}:rainbow_tree", f"{M}:rainbow_tree_sparse", f"{M}:giant_glowshroom", f"{M}:giant_glowshroom_sparse",
             f"{M}:crystal_spike", f"{M}:crystal_spike_rare", f"{M}:floating_island"]
VEG_BASE = {
    "rainbow_valley": ["minecraft:patch_grass_plain", "minecraft:flower_default", "minecraft:flower_meadow"],
    "glowshroom_forest": ["minecraft:patch_grass_plain"],
    "candy_meadows": ["minecraft:patch_grass_plain", "minecraft:flower_default", "minecraft:flower_meadow"],
    "grey_district": ["minecraft:patch_dead_bush"],
}

for name, (temp, hum, fx, monsters, extra) in BIOMES.items():
    veg = set(VEG_BASE[name]) | {f"{M}:{e}" for e in extra}
    features = []
    for step in STEPS:
        features.append([f for f in VEG_ORDER if f in veg] if step is None else step)
    effects = dict(fx)
    if name != "grey_district":
        effects["particle"] = {"options": {"type": "minecraft:end_rod"}, "probability": 0.0015}
    else:
        effects["particle"] = {"options": {"type": "minecraft:white_ash"}, "probability": 0.02}
    w(f"{D}/worldgen/biome/{name}.json", {
        "has_precipitation": name != "grey_district",
        "temperature": 0.7, "downfall": 0.6,
        "effects": effects,
        "spawners": {
            "monster": [{"type": f"{M}:{m}", "weight": wgt, "minCount": 1, "maxCount": 2} for m, wgt in monsters],
            "creature": [] if name == "grey_district" else [{"type": "minecraft:sheep", "weight": 8, "minCount": 2, "maxCount": 3},
                                                            {"type": "minecraft:rabbit", "weight": 4, "minCount": 2, "maxCount": 3}],
            "ambient": [], "axolotls": [], "underground_water_creature": [], "water_creature": [], "water_ambient": [], "misc": []},
        "spawn_costs": {},
        "carvers": {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]},
        "features": features})

w(f"{D}/dimension/chromandivka.json", {
    "type": f"{M}:chromandivka",
    "generator": {"type": "minecraft:noise", "settings": "minecraft:overworld",
                  "biome_source": {"type": "minecraft:multi_noise", "biomes": [
                      {"biome": f"{M}:{n}", "parameters": {"temperature": t, "humidity": h, "continentalness": [-1.2, 1.0],
                                                          "erosion": [-1.0, 1.0], "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}}
                      for n, (t, h, *_rest) in BIOMES.items()]}}})

# Конфігуровані та розміщені фічі.
CF = f"{D}/worldgen/configured_feature"
PF = f"{D}/worldgen/placed_feature"
w(f"{CF}/rainbow_tree.json", {"type": "minecraft:tree", "config": {
    "ignore_vines": True, "force_dirt": False,
    "minimum_size": {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1},
    "dirt_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dirt"}},
    "trunk_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:birch_log", "Properties": {"axis": "y"}}},
    "foliage_provider": {"type": "minecraft:simple_state_provider",
                         "state": {"Name": f"{M}:rainbow_leaves", "Properties": {"distance": "7", "persistent": "false", "waterlogged": "false"}}},
    "trunk_placer": {"type": "minecraft:straight_trunk_placer", "base_height": 5, "height_rand_a": 3, "height_rand_b": 1},
    "foliage_placer": {"type": "minecraft:blob_foliage_placer", "radius": 3, "offset": 0, "height": 3},
    "decorators": []}})
for f in ("crystal_spike", "giant_glowshroom", "floating_island"):
    w(f"{CF}/{f}.json", {"type": f"{M}:{f}", "config": {}})


def placed(name, feature, mods, tree=False):
    p = list(mods) + [{"type": "minecraft:in_square"}]
    if tree:
        p += [{"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
              {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
              {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive",
                                                                         "state": {"Name": "minecraft:birch_sapling", "Properties": {"stage": "0"}}}}]
    else:
        p += [{"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}]
    p += [{"type": "minecraft:biome"}]
    w(f"{PF}/{name}.json", {"feature": f"{M}:{feature}", "placement": p})


placed("rainbow_tree", "rainbow_tree", [{"type": "minecraft:count", "count": 2}], tree=True)
placed("rainbow_tree_sparse", "rainbow_tree", [{"type": "minecraft:rarity_filter", "chance": 3}], tree=True)
placed("giant_glowshroom", "giant_glowshroom", [{"type": "minecraft:count", "count": 2}])
placed("giant_glowshroom_sparse", "giant_glowshroom", [{"type": "minecraft:rarity_filter", "chance": 5}])
placed("crystal_spike", "crystal_spike", [{"type": "minecraft:rarity_filter", "chance": 2}])
placed("crystal_spike_rare", "crystal_spike", [{"type": "minecraft:rarity_filter", "chance": 8}])
placed("floating_island", "floating_island", [{"type": "minecraft:rarity_filter", "chance": 7}])

# ------------------------------------------------------------------ досягнення
def adv(name, icon, frame, parent=None, background=None):
    display = {"icon": {"item": icon}, "title": {"translate": f"advancements.lewandivka.{name}.title"},
               "description": {"translate": f"advancements.lewandivka.{name}.description"},
               "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False}
    if background:
        display["background"] = background
    obj = {"display": display, "criteria": {"trigger": {"trigger": "minecraft:impossible"}}}
    if parent:
        obj["parent"] = f"{M}:{parent}"
    w(f"{D}/advancements/{name}.json", obj)


adv("root", f"{M}:chroma_pill", "task", background="minecraft:textures/block/purple_concrete.png")
adv("chromandivka", f"{M}:chroma_portal", "goal", "root")
adv("cats", f"{M}:collar_chinazik", "challenge", "chromandivka")
adv("almost_hit", "minecraft:wooden_shovel", "task", "cats")
adv("victory", f"{M}:color_charge", "challenge", "cats")
print("ok")

# ------------------------------------------------------------------ рецепти
w(f"{D}/recipes/kiosk.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                              "pattern": ["ISI", "PPP", "PPP"],
                              "key": {"I": {"item": "minecraft:iron_ingot"}, "S": {"tag": "minecraft:signs"}, "P": {"tag": "minecraft:planks"}},
                              "result": {"item": f"{M}:kiosk"}})
w(f"{D}/recipes/cardboard_box.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                      "pattern": ["PP", "PP"], "key": {"P": {"item": "minecraft:paper"}},
                                      "result": {"item": f"{M}:cardboard_box"}})
w(f"{D}/recipes/litter_box.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                   "pattern": ["B B", "BSB"], "key": {"B": {"item": "minecraft:blue_dye"}, "S": {"item": "minecraft:sand"}},
                                   "result": {"item": f"{M}:litter_box"}})
print("recipes ok")
