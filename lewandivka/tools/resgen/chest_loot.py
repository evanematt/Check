"""The loot tables of the chests and barrels of the buildings: what a flat, a house, the kindergarten, the school, a garage, a shed and
the old shop hold. Random, but made of what a first days of survival need: food, torches, tools, arrows, and now and then an emerald
(the money of the traders of the market). Nothing in them is a quest item.

A table is a list of pools: (rolls min, max, chance that the pool is rolled at all, [(item, weight, count min, count max), ...]).
"""
from __future__ import annotations

from .spec import DATA, MOD_ID, write_json

Pool = tuple  # (min rolls, max rolls, chance, entries)


def item(name: str, weight: int, lo: int = 1, hi: int | None = None) -> tuple:
    return (f"minecraft:{name}", weight, lo, hi if hi is not None else lo)


FOOD = (1, 3, 1.0, [item("bread", 10, 1, 3), item("apple", 8, 1, 3), item("carrot", 8, 1, 4), item("baked_potato", 8, 1, 3),
                    item("cooked_chicken", 5, 1, 2), item("cooked_porkchop", 4, 1, 2), item("cooked_cod", 3, 1, 2),
                    item("cookie", 6, 2, 6), item("pumpkin_pie", 2), item("milk_bucket", 1)])

TABLES: dict[str, list[Pool]] = {
    # a lived-in flat: food in the kitchen, the small things of a household
    "flat": [
        FOOD,
        (1, 2, 1.0, [item("torch", 10, 3, 8), item("coal", 6, 2, 5), item("stick", 5, 2, 6), item("paper", 4, 1, 4), item("string", 4, 1, 3),
                     item("leather", 3, 1, 2), item("flint", 3, 1, 2), item("glass_bottle", 3, 1, 2), item("bone_meal", 3, 1, 3)]),
        (1, 1, 0.35, [item("emerald", 5, 1, 2), item("iron_ingot", 5, 1, 3), item("arrow", 4, 3, 8), item("golden_apple", 1),
                      item("experience_bottle", 2, 1, 2)]),
    ],
    # a house: the same and what a man keeps in a shed of his own
    "house": [
        FOOD,
        (1, 2, 1.0, [item("torch", 10, 4, 10), item("coal", 6, 2, 6), item("oak_planks", 5, 4, 10), item("stick", 5, 3, 8),
                     item("string", 3, 1, 3), item("leather", 3, 1, 3), item("bone_meal", 3, 1, 3)]),
        (1, 1, 0.45, [item("stone_pickaxe", 2), item("stone_axe", 2), item("stone_shovel", 2), item("stone_sword", 2), item("shield", 1),
                      item("bow", 1), item("fishing_rod", 2), item("shears", 1), item("bucket", 2), item("iron_pickaxe", 1), item("iron_axe", 1)]),
        (1, 1, 0.3, [item("emerald", 5, 1, 2), item("iron_ingot", 5, 1, 3), item("arrow", 4, 4, 10), item("golden_apple", 1)]),
    ],
    # the group rooms: snacks, wool, crayons
    "kindergarten": [
        (2, 3, 1.0, [item("cookie", 10, 2, 8), item("apple", 6, 1, 3), item("bread", 4, 1, 2), item("carrot", 4, 1, 3), item("sweet_berries", 4, 2, 6),
                     item("honey_bottle", 2), item("cake", 1), item("golden_carrot", 1), item("milk_bucket", 1)]),
        (1, 2, 1.0, [item("white_wool", 3, 1, 4), item("red_wool", 3, 1, 4), item("blue_wool", 3, 1, 4), item("yellow_wool", 3, 1, 4),
                     item("lime_wool", 3, 1, 4), item("orange_wool", 3, 1, 4), item("pink_wool", 3, 1, 4), item("paper", 4, 1, 4),
                     item("red_dye", 3, 1, 3), item("blue_dye", 3, 1, 3), item("yellow_dye", 3, 1, 3), item("book", 2)]),
        (1, 1, 0.25, [item("emerald", 3), item("experience_bottle", 2), item("golden_apple", 1)]),
    ],
    # the classrooms and the library: books, paper, ink
    "school": [
        (1, 3, 1.0, [item("book", 10, 1, 3), item("paper", 10, 2, 8), item("ink_sac", 5, 1, 3), item("feather", 5, 1, 3),
                     item("writable_book", 3), item("map", 2), item("bone_meal", 3, 1, 3), item("stick", 3, 1, 4)]),
        (1, 2, 1.0, [item("apple", 4, 1, 2), item("bread", 4, 1, 2), item("cooked_chicken", 2), item("cookie", 4, 2, 5), item("carrot", 3, 1, 2)]),
        (1, 1, 0.35, [item("bookshelf", 1), item("clock", 1), item("spyglass", 1), item("name_tag", 1), item("experience_bottle", 3, 1, 2),
                      item("emerald", 3)]),
    ],
    # a garage: tools, fuel, odds and ends of metal
    "garage": [
        (1, 3, 1.0, [item("coal", 8, 3, 8), item("charcoal", 4, 2, 5), item("iron_ingot", 6, 1, 4), item("iron_nugget", 5, 3, 9),
                     item("flint", 4, 1, 3), item("torch", 8, 4, 12), item("arrow", 5, 4, 12), item("redstone", 4, 2, 6), item("rail", 3, 3, 8),
                     item("chain", 3, 1, 4), item("lantern", 3, 1, 2), item("gunpowder", 2, 1, 2)]),
        (1, 1, 0.5, [item("flint_and_steel", 2), item("bucket", 2), item("shears", 1), item("iron_pickaxe", 2), item("iron_axe", 2),
                     item("iron_shovel", 2), item("iron_sword", 1), item("iron_hoe", 1)]),
        (1, 1, 0.3, [item("emerald", 2, 1, 2), item("golden_apple", 1), item("experience_bottle", 2)]),
    ],
    # an allotment shed: seeds and gardening
    "shed": [
        (2, 3, 1.0, [item("wheat_seeds", 10, 2, 8), item("beetroot_seeds", 6, 2, 6), item("melon_seeds", 4, 1, 3), item("pumpkin_seeds", 4, 1, 3),
                     item("carrot", 6, 2, 5), item("potato", 6, 2, 5), item("bone_meal", 6, 2, 6), item("oak_sapling", 3, 1, 3),
                     item("birch_sapling", 3, 1, 3), item("apple", 4, 1, 3), item("sweet_berries", 4, 2, 5), item("stick", 4, 2, 5)]),
        (1, 1, 0.5, [item("stone_hoe", 2), item("wooden_hoe", 3), item("bucket", 2), item("water_bucket", 1), item("shears", 2),
                     item("iron_hoe", 1), item("lead", 1)]),
        (1, 1, 0.2, [item("emerald", 3), item("golden_carrot", 1)]),
    ],
    # the shop: what is sold, and the money of the till
    "shop": [
        (2, 4, 1.0, [item("bread", 8, 1, 3), item("cookie", 6, 2, 6), item("cake", 1), item("pumpkin_pie", 3), item("sweet_berries", 4, 2, 5),
                     item("honey_bottle", 3), item("milk_bucket", 2), item("cooked_chicken", 3), item("apple", 5, 1, 3), item("carrot", 4, 1, 3)]),
        (1, 2, 1.0, [item("paper", 5, 1, 4), item("string", 4, 1, 3), item("ink_sac", 3), item("book", 3), item("glass_bottle", 6, 1, 3),
                     item("bucket", 1), item("lead", 2), item("lantern", 3), item("torch", 6, 3, 8)]),
        (1, 1, 0.6, [item("emerald", 8, 1, 3), item("name_tag", 2), item("clock", 1), item("experience_bottle", 2)]),
    ],
}


def pool_json(pool: Pool) -> dict:
    lo, hi, chance, entries = pool
    obj: dict = {"rolls": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)} if lo != hi else float(lo)}
    if chance < 1.0:
        obj["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    obj["entries"] = []
    for name, weight, c_lo, c_hi in entries:
        entry: dict = {"type": "minecraft:item", "name": name, "weight": weight}
        if c_hi > 1:
            entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": float(c_lo), "max": float(c_hi)}}]
        obj["entries"].append(entry)
    return obj


def emit() -> int:
    n = 0
    for kind, pools in TABLES.items():
        write_json(DATA / "loot_tables" / "chests" / f"{kind}.json", {"type": "minecraft:chest", "pools": [pool_json(p) for p in pools]})
        n += 1
    return n
