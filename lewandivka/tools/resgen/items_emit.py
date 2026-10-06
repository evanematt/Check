"""Writes item textures and models."""
from __future__ import annotations

from .items_paint import paint_item, compass_frame, COMPASS_FRAMES
from .spec import ASSETS, MOD_ID, items, blocks, entities, write_json

M = MOD_ID


def generate() -> int:
    count = 0
    for it in items():
        iid = it["id"]
        if iid == "chromatic_compass":
            for f in range(COMPASS_FRAMES):
                compass_frame(f * 360.0 / COMPASS_FRAMES).save(ASSETS / "textures" / "item" / f"chromatic_compass_{f:02d}.png")
                write_json(ASSETS / "models" / "item" / f"chromatic_compass_{f:02d}.json",
                           {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/chromatic_compass_{f:02d}"}})
            overrides = []
            for f in range(COMPASS_FRAMES):
                overrides.append({"predicate": {"angle": f / COMPASS_FRAMES + 0.5 / COMPASS_FRAMES * (0 if f == 0 else 1) if f else 0.0},
                                  "model": f"{M}:item/chromatic_compass_{f:02d}"})
            # thresholds: frame f is used for angle in [f/16 - 1/32, f/16 + 1/32); the first frame also wraps
            overrides = [{"predicate": {"angle": 0.0}, "model": f"{M}:item/chromatic_compass_00"}]
            for f in range(1, COMPASS_FRAMES):
                overrides.append({"predicate": {"angle": (f - 0.5) / COMPASS_FRAMES}, "model": f"{M}:item/chromatic_compass_{f:02d}"})
            overrides.append({"predicate": {"angle": (COMPASS_FRAMES - 0.5) / COMPASS_FRAMES}, "model": f"{M}:item/chromatic_compass_00"})
            write_json(ASSETS / "models" / "item" / "chromatic_compass.json",
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/chromatic_compass_00"}, "overrides": overrides})
            count += 1
            continue
        paint_item(it).save(ASSETS / "textures" / "item" / f"{iid}.png")
        write_json(ASSETS / "models" / "item" / f"{iid}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{M}:item/{iid}"}})
        count += 1
    # spawn eggs use the vanilla tinted template
    for e in entities():
        if e["role"] in ("PROJECTILE", "VEHICLE", "MARKER"):
            continue
        write_json(ASSETS / "models" / "item" / f"{e['id']}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})
        count += 1
    return count
