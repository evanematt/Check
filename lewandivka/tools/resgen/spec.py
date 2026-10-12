"""Loads the JSON catalogs exported by `gradle -p core exportSpec` (Java is the single source of truth)."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC_DIR = ROOT / "tools" / "data" / "spec"
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "lewandivka"
DATA = RES / "data" / "lewandivka"
MOD_ID = "lewandivka"


@lru_cache(maxsize=None)
def load(name: str):
    return json.loads((SPEC_DIR / f"{name}.json").read_text(encoding="utf-8"))


def blocks():
    return load("blocks")


def items():
    return load("items")


def entities():
    return load("entities")


def sounds():
    return load("sounds")


def advancements():
    return load("advancements")


def story():
    return load("story")


def write_json(path: Path, obj, indent: int = 2) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=indent) + "\n", encoding="utf-8")


def prop(block: dict, name: str):
    for p in block["props"]:
        if p["name"] == name:
            return p
    return None


def combos(block: dict, names: list | None = None) -> list:
    """All combinations of the given (default: visual) properties as dicts name -> value string."""
    names = block["visual"] if names is None else names
    result = [{}]
    for n in names:
        p = prop(block, n)
        result = [dict(c, **{n: v}) for c in result for v in p["values"]]
    return result


def combo_name(block: dict, combo: dict) -> str:
    """Texture/model name of a visual combination: bool props add their name when true, others add their value."""
    parts = []
    for n in block["visual"]:
        p = prop(block, n)
        v = combo[n]
        if p["type"] == "BOOL":
            if v == "true":
                parts.append(n)
        else:
            parts.append(str(v))
    return block["id"] + ("_" + "_".join(parts) if parts else "")


def state_value(block: dict, name: str, value: str) -> str:
    """Value of a property as Minecraft stores it: ENUM properties are integer indices in the game."""
    p = prop(block, name)
    if p["type"] == "ENUM":
        return str(p["values"].index(value))
    return value
