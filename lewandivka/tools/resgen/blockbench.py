"""Blockbench "Generic Model" files (.bbmodel, free format, a patch of the texture on every face) as GeckoLib geometry.

The boss models of the mod were made in Blockbench; tools/models/ holds them the way they were supplied. The geometry the game loads is made
from them here. The models are drawn the way the models of Minecraft's entities are: the right side of the character is at -x, the front is the
north face, the numbers of a cube are those of the geometry files of the game, which GeckoLib (software.bernie.geckolib.loading.object.
BakedModelFactory, 4.8.x) mirrors in x when it reads a cube and a bone. So:

* a group of the outliner is a bone, its parent is the group around it; the cubes directly inside it are its cubes;
* origins, sizes and pivots are copied as they are;
* GeckoLib turns a written rotation (rx, ry, rz) into (-rx, -ry, rz) of the mirrored model; the rotations of the files are those of the
  picture the model is drawn as, so a mirrored model needs (-rx, ry, -rz) written, to look the same as the picture;
* the east and west faces change places (the face at +x of the file is at -x of the game), the pictures on the faces keep their way up;
* the up and down faces are written from the opposite corner with a negative size, which makes GeckoLib show their picture the way up
  Blockbench does.

The result was compared with the pictures that came with the models (front and back, two-coloured suits, asymmetric faces, rotated ears,
horns and tails) by a software renderer that builds the quads the way GeckoLib does; the same views of the game are in the client test.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image

from .spec import ROOT

MODELS = ROOT / "tools" / "models"
FACES = ("north", "east", "south", "west", "up", "down")
#: the face of the file that is at +x is at -x of the game
SWAP = {"east": "west", "west": "east"}


def _num(x: float):
    r = round(float(x), 4)
    return int(r) if r == int(r) else r


def _vec(v) -> list:
    return [_num(x) for x in v]


def _rotation(rot) -> list:
    return _vec((-rot[0], rot[1], -rot[2]))


def read(source: str) -> dict:
    return json.loads((MODELS / f"{source}.bbmodel").read_text(encoding="utf-8"))


def _cube(e: dict) -> dict:
    frm, to = e["from"], e["to"]
    cube = {
        "origin": _vec(frm),
        "size": _vec((to[0] - frm[0], to[1] - frm[1], to[2] - frm[2])),
    }
    rot = e.get("rotation") or [0, 0, 0]
    if any(abs(r) > 1e-6 for r in rot):
        cube["pivot"] = _vec(e["origin"])
        cube["rotation"] = _rotation(rot)
    uv = {}
    for face in FACES:
        f = e["faces"].get(face)
        if f is None or f.get("texture") is None:
            continue
        x1, y1, x2, y2 = f["uv"]
        if face in ("up", "down"):
            uv[face] = {"uv": _vec((x2, y2)), "uv_size": _vec((x1 - x2, y1 - y2))}
        else:
            uv[SWAP.get(face, face)] = {"uv": _vec((x1, y1)), "uv_size": _vec((x2 - x1, y2 - y1))}
    cube["uv"] = uv
    return cube


def geometry(source: str, name: str, bounds=(6.0, 6.0, (0, 2.0, 0))) -> dict:
    """The geometry file (Bedrock format) of a Blockbench model; `name` is the model name the entity catalog uses."""
    bb = read(source)
    width, height = bb["resolution"]["width"], bb["resolution"]["height"]
    elements = {e["uuid"]: e for e in bb["elements"]}
    bones: list = []
    seen: set = set()

    def walk(nodes, parent):
        for g in nodes:
            if isinstance(g, str):
                continue
            if g["name"] in seen:
                raise ValueError(f"{source}: two groups are called {g['name']}")
            seen.add(g["name"])
            bone = {"name": g["name"], "pivot": _vec(g.get("origin") or [0, 0, 0])}
            if parent:
                bone["parent"] = parent
            rot = g.get("rotation") or [0, 0, 0]
            if any(abs(r) > 1e-6 for r in rot):
                bone["rotation"] = _rotation(rot)
            cubes = [elements[c] for c in g.get("children", []) if isinstance(c, str)]
            cubes = [e for e in cubes if e.get("export", True) and e.get("visibility", True)]
            if cubes:
                bone["cubes"] = [_cube(e) for e in cubes]
            bones.append(bone)
            walk(g.get("children", []), g["name"])

    outliner = bb["outliner"]
    if any(isinstance(n, str) for n in outliner):
        raise ValueError(f"{source}: a cube outside of every group")
    walk(outliner, None)
    bw, bh, off = bounds
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.lewandivka.{name}",
                "texture_width": width, "texture_height": height,
                "visible_bounds_width": bw, "visible_bounds_height": bh, "visible_bounds_offset": list(off),
            },
            "bones": bones,
        }],
    }


class ImportedModel:
    """What the entity emitter wants of a model: `to_json()`."""

    def __init__(self, source: str, name: str, bounds=(6.0, 6.0, (0, 2.0, 0))):
        self.source, self.name, self.bounds = source, name, bounds

    def to_json(self) -> dict:
        return geometry(self.source, self.name, self.bounds)


class ImportedTexture:
    """What the entity emitter wants of a texture: `save(path)`."""

    def __init__(self, source: str):
        self.source = source

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        Image.open(MODELS / f"{self.source}.png").convert("RGBA").save(path)
