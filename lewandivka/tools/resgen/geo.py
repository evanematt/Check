"""GeckoLib (Bedrock-style) geometry and animation builders with automatic UV allocation for box models."""
from __future__ import annotations

from dataclasses import dataclass, field

from .paint import Tex, shade, C


@dataclass
class Part:
    """One cube of a model together with its UV rectangles (for painting)."""
    bone: str
    name: str
    origin: tuple
    size: tuple
    uv: tuple
    inflate: float = 0.0
    mirror: bool = False
    pivot: tuple | None = None
    rotation: tuple | None = None

    @property
    def sx(self) -> int:
        return int(self.size[0])

    @property
    def sy(self) -> int:
        return int(self.size[1])

    @property
    def sz(self) -> int:
        return int(self.size[2])

    def rect(self, face: str) -> tuple:
        """(x0, y0, x1, y1) inclusive pixel rectangle of a face in the texture."""
        u, v = self.uv
        sx, sy, sz = self.sx, self.sy, self.sz
        if face == "up":
            return (u + sz, v, u + sz + sx - 1, v + sz - 1)
        if face == "down":
            return (u + sz + sx, v, u + sz + 2 * sx - 1, v + sz - 1)
        if face == "east":
            return (u, v + sz, u + sz - 1, v + sz + sy - 1)
        if face == "north":     # the front of an entity model
            return (u + sz, v + sz, u + sz + sx - 1, v + sz + sy - 1)
        if face == "west":
            return (u + sz + sx, v + sz, u + 2 * sz + sx - 1, v + sz + sy - 1)
        if face == "south":     # the back
            return (u + 2 * sz + sx, v + sz, u + 2 * sz + 2 * sx - 1, v + sz + sy - 1)
        raise ValueError(face)

    def extent(self) -> tuple:
        u, v = self.uv
        return (2 * (self.sx + self.sz), self.sy + self.sz)

    def fill(self, tex: Tex, base, variance: float = 0.06, seed: int = 1, top_light: bool = True) -> "Part":
        """Fills all faces with a base colour; top faces lighter, bottom/sides slightly darker for a readable shape."""
        for face in ("up", "down", "east", "north", "west", "south"):
            x0, y0, x1, y1 = self.rect(face)
            k = {"up": 1.12, "down": 0.78, "east": 0.9, "west": 0.9, "north": 1.0, "south": 0.94}[face] if top_light else 1.0
            tex.noise(shade(base, k), variance, seed + len(face), area=(x0, y0, x1, y1))
        return self

    def face(self, tex: Tex, face: str, color) -> "Part":
        x0, y0, x1, y1 = self.rect(face)
        tex.rect(x0, y0, x1, y1, color)
        return self

    def sub(self, face: str, dx: int, dy: int) -> tuple:
        """Absolute pixel of (dx, dy) inside a face."""
        x0, y0, _, _ = self.rect(face)
        return (x0 + dx, y0 + dy)

    def px(self, tex: Tex, face: str, dx: int, dy: int, color) -> "Part":
        x, y = self.sub(face, dx, dy)
        tex.set(x, y, color)
        return self

    def box(self, tex: Tex, face: str, dx0: int, dy0: int, dx1: int, dy1: int, color) -> "Part":
        x, y = self.sub(face, dx0, dy0)
        tex.rect(x, y, x + (dx1 - dx0), y + (dy1 - dy0), color)
        return self

    def to_json(self) -> dict:
        d = {"origin": list(self.origin), "size": list(self.size), "uv": list(self.uv)}
        if self.inflate:
            d["inflate"] = self.inflate
        if self.mirror:
            d["mirror"] = True
        if self.pivot is not None:
            d["pivot"] = list(self.pivot)
        if self.rotation is not None:
            d["rotation"] = list(self.rotation)
        return d


class Model:
    def __init__(self, name: str, tw: int, th: int, bounds=(4.0, 4.0, (0, 1.5, 0))):
        self.name = name
        self.tw, self.th = tw, th
        self.bounds = bounds
        self.bones: dict = {}
        self.order: list = []
        self.parts: dict = {}
        # shelf packing cursor for automatic UVs
        self._x = 0
        self._y = 0
        self._row = 0

    def bone(self, name: str, parent: str | None = None, pivot=(0, 0, 0), rotation=None) -> str:
        self.bones[name] = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent:
            self.bones[name]["parent"] = parent
        if rotation:
            self.bones[name]["rotation"] = list(rotation)
        self.order.append(name)
        return name

    def _alloc(self, w: int, h: int) -> tuple:
        if self._x + w > self.tw:
            self._x = 0
            self._y += self._row
            self._row = 0
        if self._y + h > self.th:
            raise ValueError(f"model {self.name}: texture {self.tw}x{self.th} is full at part of size {w}x{h}")
        u, v = self._x, self._y
        self._x += w
        self._row = max(self._row, h)
        return (u, v)

    def cube(self, bone: str, name: str, origin, size, uv=None, inflate: float = 0.0, mirror: bool = False,
             pivot=None, rotation=None) -> Part:
        w = int(2 * (size[0] + size[2]))
        h = int(size[1] + size[2])
        if uv is None:
            uv = self._alloc(w, h)
        p = Part(bone, name, tuple(origin), tuple(size), tuple(uv), inflate, mirror, pivot, rotation)
        self.bones[bone]["cubes"].append(p)
        self.parts[name] = p
        return p

    def to_json(self) -> dict:
        bones = []
        for n in self.order:
            b = dict(self.bones[n])
            b["cubes"] = [c.to_json() for c in b["cubes"]]
            if not b["cubes"]:
                del b["cubes"]
            bones.append(b)
        bw, bh, off = self.bounds
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.lewandivka.{self.name}",
                    "texture_width": self.tw, "texture_height": self.th,
                    "visible_bounds_width": bw, "visible_bounds_height": bh, "visible_bounds_offset": list(off),
                },
                "bones": bones,
            }],
        }

    def new_texture(self) -> Tex:
        return Tex(self.tw, self.th)


class Anim:
    """Keyframes of one animation: bone -> channel -> {time: [x, y, z]}."""

    def __init__(self, name: str, length: float, loop: bool = True, hold_on_end: bool = False):
        self.name = name
        self.length = length
        self.loop = loop
        self.hold = hold_on_end
        self.bones: dict = {}

    def _ch(self, bone: str, ch: str, frames: dict) -> "Anim":
        self.bones.setdefault(bone, {})[ch] = {str(round(t, 3)): list(v) for t, v in sorted(frames.items())}
        return self

    def rot(self, bone: str, frames: dict) -> "Anim":
        return self._ch(bone, "rotation", frames)

    def pos(self, bone: str, frames: dict) -> "Anim":
        return self._ch(bone, "position", frames)

    def scale(self, bone: str, frames: dict) -> "Anim":
        return self._ch(bone, "scale", frames)

    def to_json(self) -> dict:
        d = {"animation_length": self.length, "bones": self.bones}
        if self.loop:
            d["loop"] = True
        elif self.hold:
            d["loop"] = "hold_on_last_frame"
        return d


def animations_json(prefix: str, anims: list) -> dict:
    return {"format_version": "1.8.0", "animations": {f"animation.{prefix}.{a.name}": a.to_json() for a in anims}}


def swing(bone: str, anim: Anim, axis: int, amp: float, length: float, phase: float = 0.0, steps: int = 4) -> None:
    """Sinusoidal swing of one rotation axis as 4-step keyframes."""
    import math
    frames = {}
    for i in range(steps + 1):
        t = length * i / steps
        v = [0.0, 0.0, 0.0]
        v[axis] = round(amp * math.sin(2 * math.pi * (i / steps) + phase), 2)
        frames[t] = v
    anim.rot(bone, frames)
