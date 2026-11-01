"""Four of the five bosses are made in Blockbench (tools/models): the models and the textures come from those files, the animations are made here.

The files are the models of the characters as they were supplied; which boss wears which was decided by what the character is: the beast in the
crimson coat is the Garage King, the jester cat the Collar Collector (the cats are his), the black conductor with the golden sun for a face the
Conductor, and the pale hooded watcher with one eye the Colorless Head.
"""
from __future__ import annotations

import math

from . import blockbench as BB
from .entities_bosses import boss_animations

#: model of the catalog -> the Blockbench file it is made from
SOURCES = {
    "garage_king": "crimson_beast",
    "collar_collector": "jester_cat",
    "conductor": "sun_keeper",
    "colorless_head": "hooded_watcher",
}


def model(name: str) -> BB.ImportedModel:
    return BB.ImportedModel(SOURCES[name], name)


def texture(name: str) -> BB.ImportedTexture:
    return BB.ImportedTexture(SOURCES[name])


def _channel(d: dict, name: str, anim: str, bone: str, kind: str, frames: dict) -> None:
    a = d["animations"][f"animation.{name}.{anim}"]
    a["bones"].setdefault(bone, {})[kind] = {str(round(t, 3)): list(v) for t, v in sorted(frames.items())}


def _wave(length: float, amp: float, axis: int, phase: float = 0.0, steps: int = 8) -> dict:
    """A sine wave of one axis over the whole length of a looping animation (the last key is the first)."""
    frames = {}
    for i in range(steps + 1):
        v = [0.0, 0.0, 0.0]
        v[axis] = round(amp * math.sin(2 * math.pi * i / steps + phase), 2)
        frames[length * i / steps] = v
    return frames


def _conductor(d: dict) -> None:
    """The eight rays of the sun flicker when he stands and walks, and flare when he strikes, shields himself and changes the phase."""
    for i in range(8):
        ray = f"ray_{i}"
        _channel(d, "conductor", "idle", ray, "rotation", _wave(4.0, 7, 2, i * math.pi / 4))
        _channel(d, "conductor", "walk", ray, "rotation", _wave(1.2, 10, 2, i * math.pi / 4))
        _channel(d, "conductor", "shield", ray, "scale", {0: (1, 1, 1), 1: (1.15, 1.15, 1.15), 2: (1, 1, 1)})
        _channel(d, "conductor", "telegraph", ray, "scale", {0: (1, 1, 1), 1.0: (1.3, 1.3, 1.3)})
        _channel(d, "conductor", "phase", ray, "scale", {0: (1, 1, 1), 0.4: (1.4, 1.4, 1.4), 1.2: (1.4, 1.4, 1.4), 1.6: (1, 1, 1)})


def _collar_collector(d: dict) -> None:
    """The tail of four segments sways, every segment a little behind the one before it."""
    for k, bone in enumerate(("tail_01", "tail_02", "tail_03", "tail_04")):
        _channel(d, "collar_collector", "idle", bone, "rotation", _wave(4.0, 5 + 2 * k, 1, -0.6 * k))
        _channel(d, "collar_collector", "walk", bone, "rotation", _wave(1.2, 9 + 2 * k, 1, -0.6 * k))


def _colorless_head(d: dict) -> None:
    """The watcher floats: the cloak sways and the whole body rises and sinks a little when he stands."""
    _channel(d, "colorless_head", "idle", "cloak", "rotation", _wave(4.0, 2.5, 0))
    _channel(d, "colorless_head", "walk", "cloak", "rotation", _wave(1.2, 5, 0))
    _channel(d, "colorless_head", "idle", "body", "position", {0: (0, 0, 0), 2: (0, 1, 0), 4: (0, 0, 0)})


EXTRAS = {"conductor": _conductor, "collar_collector": _collar_collector, "colorless_head": _colorless_head}


def animations(name: str) -> dict:
    d = boss_animations(name)
    if name in EXTRAS:
        EXTRAS[name](d)
    return d
