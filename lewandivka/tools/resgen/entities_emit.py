"""Writes the GeckoLib geometry, animation and texture files of every entity."""
from __future__ import annotations

from . import entities_bosses as B
from . import entities_cats as K
from . import entities_humanoid as H
from .spec import ASSETS, entities, write_json

MODELS = {
    "humanoid": (H.humanoid_model, H.humanoid_animations),
    "cat": (K.cat_model, K.cat_animations),
    "garage_king": (B.garage_king_model, B.garage_king_animations),
    "collar_collector": (B.collar_collector_model, lambda: B.boss_animations("collar_collector", legs=False)),
    "lady_vortex": (B.lady_vortex_model, B.lady_vortex_animations),
    "conductor": (B.conductor_model, lambda: B.boss_animations("conductor")),
    "colorless_head": (B.colorless_head_model, lambda: B.boss_animations("colorless_head", legs=False)),
    "minion": (B.minion_model, B.minion_animations),
    "leash_anchor": (B.anchor_model, B.anchor_animations),
    "tram": (lambda: B.tram_model("sky_tram"), lambda: B.tram_animations("tram")),
    "projectile": (lambda: B.projectile_model("projectile", 2, 2), lambda: B.spin_animations("projectile")),
    "wheel": (lambda: B.projectile_model("wheel", 10, 4), lambda: B.spin_animations("wheel")),
}

TEXTURES = {
    "gopnik": lambda: H.paint("gopnik"),
    "seed_thrower": lambda: H.paint("seed_thrower"),
    "senior_yard_gopnik": lambda: H.paint("senior_yard_gopnik"),
    "debtor": lambda: H.paint("debtor"),
    "pan_shlahbaum": lambda: H.paint("pan_shlahbaum"),
    "fare_dodger": lambda: H.paint("fare_dodger"),
    "fare_dodger_leader": lambda: H.paint("fare_dodger_leader"),
    "chinazik": lambda: K.paint("chinazik"),
    "metadonna": lambda: K.paint("metadonna"),
    "garage_king": B.paint_garage_king,
    "collar_collector": B.paint_collar_collector,
    "lady_vortex": B.paint_lady_vortex,
    "conductor": B.paint_conductor,
    "colorless_head": B.paint_colorless_head,
    "mechanic_minion": B.paint_minion,
    "leash_anchor": B.paint_anchor,
    "arena_tram": lambda: B.paint_tram("arena_tram"),
    "sky_tram": lambda: B.paint_tram("sky_tram"),
    "seed_projectile": B.paint_seed_projectile,
    "wheel_projectile": B.paint_wheel_projectile,
}


def generate() -> int:
    done_models = set()
    count = 0
    for e in entities():
        model = e["model"]
        if model and model not in done_models:
            build, anims = MODELS[model]
            write_json(ASSETS / "geo" / "entity" / f"{model}.geo.json", build().to_json(), indent=1)
            write_json(ASSETS / "animations" / "entity" / f"{model}.animation.json", anims(), indent=1)
            done_models.add(model)
            count += 1
        TEXTURES[e["texture"]]().save(ASSETS / "textures" / "entity" / f"{e['texture']}.png")
        count += 1
    return count
