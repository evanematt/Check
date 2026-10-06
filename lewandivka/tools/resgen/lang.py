"""Builds uk_ua.json and en_us.json from the Java catalogs and lang_ui."""
from __future__ import annotations

from .lang_ui import UI
from .spec import ASSETS, MOD_ID, advancements, blocks, entities, items, sounds, story, write_json


def build() -> tuple:
    uk: dict = {}
    en: dict = {}

    def put(key: str, a: str, b: str) -> None:
        if key in uk:
            raise KeyError(f"duplicate language key {key}")
        uk[key] = a
        en[key] = b

    for k, (a, b) in UI.items():
        put(k, a, b)
    for b in blocks():
        put(f"block.{MOD_ID}.{b['id']}", b["nameUk"], b["nameEn"])
    for i in items():
        put(f"item.{MOD_ID}.{i['id']}", i["nameUk"], i["nameEn"])
        if i["loreUk"]:
            put(f"item.{MOD_ID}.{i['id']}.lore", i["loreUk"], i["loreEn"])
    for e in entities():
        put(f"entity.{MOD_ID}.{e['id']}", e["nameUk"], e["nameEn"])
        if e["role"] not in ("PROJECTILE", "VEHICLE", "MARKER"):
            put(f"item.{MOD_ID}.{e['id']}_spawn_egg", f"Яйце виклику: {e['nameUk']}", f"{e['nameEn']} Spawn Egg")
    for s in sounds():
        put(f"subtitles.{MOD_ID}.{s['id']}", s["subtitleUk"], s["subtitleEn"])
    for a in advancements():
        put(f"advancements.{MOD_ID}.{a['id']}.title", a["titleUk"], a["titleEn"])
        put(f"advancements.{MOD_ID}.{a['id']}.description", a["descUk"], a["descEn"])
    put(f"advancements.{MOD_ID}.root.background", "", "") if False else None
    st = story()
    for key, v in st["steps"].items():
        put(f"quest.{key}.title", v["titleUk"], v["titleEn"])
        put(f"quest.{key}.objective", v["objectiveUk"], v["objectiveEn"])
        put(f"quest.{key}.hint", v["hintUk"], v["hintEn"])
    for key, v in st["stages"].items():
        put(f"stage.{MOD_ID}.{key}", v["uk"], v["en"])
    for key, v in st["text"].items():
        put(key, v["uk"], v["en"])
    for n, v in st["notes"].items():
        put(f"note.{MOD_ID}.{n}", v["uk"], v["en"])
    return uk, en


def generate() -> int:
    uk, en = build()
    write_json(ASSETS / "lang" / "uk_ua.json", dict(sorted(uk.items())), indent=2)
    write_json(ASSETS / "lang" / "en_us.json", dict(sorted(en.items())), indent=2)
    return len(uk)
