"""Tiny orthographic previewer for geometry + texture (front/side/back views, no animation)."""
from __future__ import annotations

from PIL import Image

from .geo import Model, Part
from .paint import Tex


def _cubes(model: Model) -> list:
    out = []
    for name in model.order:
        out.extend(model.bones[name]["cubes"])
    return out


def render_view(model: Model, tex: Tex, view: str = "front", scale: int = 4, margin: int = 2) -> Image.Image:
    cubes = _cubes(model)
    xs = [p.origin[0] - p.inflate for p in cubes] + [p.origin[0] + p.size[0] + p.inflate for p in cubes]
    ys = [p.origin[1] - p.inflate for p in cubes] + [p.origin[1] + p.size[1] + p.inflate for p in cubes]
    zs = [p.origin[2] - p.inflate for p in cubes] + [p.origin[2] + p.size[2] + p.inflate for p in cubes]
    minx, maxx, miny, maxy, minz, maxz = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
    if view in ("front", "back"):
        w = maxx - minx
    else:
        w = maxz - minz
    h = maxy - miny
    img = Image.new("RGBA", (int((w + 2 * margin) * scale), int((h + 2 * margin) * scale)), (60, 60, 70, 255))

    def depth(p: Part) -> float:
        if view == "front":
            return -(p.origin[2] + p.size[2] / 2)
        if view == "back":
            return p.origin[2] + p.size[2] / 2
        if view == "right":      # viewer at +X
            return -(p.origin[0] + p.size[0] / 2)
        return p.origin[0] + p.size[0] / 2

    face = {"front": "north", "back": "south", "right": "east", "left": "west"}[view]
    for p in sorted(cubes, key=lambda c: (depth(c), c.inflate)):
        i = p.inflate
        x0, x1 = p.origin[0] - i, p.origin[0] + p.size[0] + i
        z0, z1 = p.origin[2] - i, p.origin[2] + p.size[2] + i
        y0, y1 = p.origin[1] - i, p.origin[1] + p.size[1] + i
        if view == "front":
            sx0, sx1 = maxx - x1, maxx - x0
        elif view == "back":
            sx0, sx1 = x0 - minx, x1 - minx
        elif view == "right":
            sx0, sx1 = maxz - z1, maxz - z0
        else:
            sx0, sx1 = z0 - minz, z1 - minz
        sy0, sy1 = maxy - y1, maxy - y0
        ux0, uy0, ux1, uy1 = p.rect(face)
        crop = tex.im.crop((ux0, uy0, ux1 + 1, uy1 + 1))
        if p.mirror:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        tw = max(1, int(round((sx1 - sx0) * scale)))
        th = max(1, int(round((sy1 - sy0) * scale)))
        crop = crop.resize((tw, th), Image.NEAREST)
        img.alpha_composite(crop, (int((sx0 + margin) * scale), int((sy0 + margin) * scale)))
    return img


def render_views(model: Model, tex: Tex, scale: int = 4) -> Image.Image:
    views = [render_view(model, tex, v, scale) for v in ("front", "right", "back", "left")]
    w = sum(v.width for v in views) + 4 * (len(views) + 1)
    h = max(v.height for v in views) + 8
    img = Image.new("RGBA", (w, h), (30, 30, 36, 255))
    x = 4
    for v in views:
        img.alpha_composite(v, (x, 4))
        x += v.width + 4
    return img


# ------------------------------------------------------------------------------------------------ pose preview
import math

from PIL import ImageDraw


def _avg(tex: Tex, rect) -> tuple:
    x0, y0, x1, y1 = rect
    r = g = b = n = 0
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px = tex.get(x, y)
            if px[3]:
                r += px[0]
                g += px[1]
                b += px[2]
                n += 1
    return (r // n, g // n, b // n, 255) if n else (200, 0, 200, 255)


def pose_side(model: Model, tex: Tex, rot: dict | None = None, pos: dict | None = None, scale: int = 8, margin: int = 4,
              view: str = "side") -> Image.Image:
    """Side silhouette (looking from +X, front of the model on the left) of the model in a pose.
    rot: bone -> (x, y, z) degrees (Bedrock convention, X positive = pitch down), pos: bone -> (x, y, z) units."""
    rot = rot or {}
    pos = pos or {}
    # world matrices as (rotation 3x3, translation) built along the parent chain
    def rotm(x, y, z):
        ax, ay, az = math.radians(-x), math.radians(-y), math.radians(z)
        cx, sx_ = math.cos(ax), math.sin(ax)
        cy, sy_ = math.cos(ay), math.sin(ay)
        cz, sz_ = math.cos(az), math.sin(az)
        rx = [[1, 0, 0], [0, cx, -sx_], [0, sx_, cx]]
        ry = [[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]]
        rz = [[cz, -sz_, 0], [sz_, cz, 0], [0, 0, 1]]
        def mul(a, b):
            return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
        return mul(rz, mul(ry, rx))

    def apply(m, v):
        return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]

    cache: dict = {}

    def world(name: str):
        if name in cache:
            return cache[name]
        b = model.bones[name]
        pivot = b["pivot"]
        r = rotm(*rot.get(name, (0, 0, 0)))
        off = pos.get(name, (0, 0, 0))
        parent = b.get("parent")
        if parent:
            pr, pt = world(parent)
        else:
            pr, pt = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0]
        # local transform: p' = R (p - pivot) + pivot + off ; world: parent(local(p))
        def local(p):
            q = apply(r, [p[0] - pivot[0], p[1] - pivot[1], p[2] - pivot[2]])
            return [q[0] + pivot[0] + off[0], q[1] + pivot[1] + off[1], q[2] + pivot[2] + off[2]]
        def to_world(p):
            q = local(p)
            if parent:
                return world_apply(parent, q)
            return q
        cache[name] = (None, to_world)
        return cache[name]

    def world_apply(name: str, p):
        return world(name)[1](p)

    polys = []
    for name in model.order:
        for c in model.bones[name]["cubes"]:
            ox, oy, oz = c.origin
            sx, sy, sz = c.size
            i = c.inflate
            corners = [(ox - i + dx * (sx + 2 * i), oy - i + dy * (sy + 2 * i), oz - i + dz * (sz + 2 * i))
                       for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            w = [world_apply(name, list(p)) for p in corners]
            if view == "side":
                pts = [(-q[2], q[1], q[0]) for q in w]        # screen x = -z (front on the right?), y up
            else:
                pts = [(q[0], q[1], q[2]) for q in w]
            col = _avg(tex, c.rect("west" if view == "side" else "north"))
            depth = sum(p[2] for p in pts) / 8.0
            polys.append((depth, c.inflate, pts, col))
    xs = [p[0] for _, _, pts, _ in polys for p in pts]
    ys = [p[1] for _, _, pts, _ in polys for p in pts]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    img = Image.new("RGBA", (int((maxx - minx + 2 * margin) * scale), int((maxy - miny + 2 * margin) * scale)), (60, 60, 70, 255))
    d = ImageDraw.Draw(img)

    def hull(points):
        pts = sorted(set(points))
        if len(pts) <= 2:
            return pts
        def cross(o, a, b):
            return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
        lower = []
        for p in pts:
            while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
                lower.pop()
            lower.append(p)
        upper = []
        for p in reversed(pts):
            while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
                upper.pop()
            upper.append(p)
        return lower[:-1] + upper[:-1]

    for depth, infl, pts, col in sorted(polys, key=lambda t: (t[0], t[1])):
        screen = [((p[0] - minx + margin) * scale, (maxy - p[1] + margin) * scale) for p in pts]
        poly = hull(screen)
        if len(poly) >= 3:
            d.polygon(poly, fill=col, outline=(20, 20, 24, 255))
    # ground line
    gy = (maxy - 0 + margin) * scale
    d.line([(0, gy), (img.width, gy)], fill=(120, 200, 120, 255), width=1)
    return img
