#!/usr/bin/env python3
"""Prints the rcon commands that build a gallery of every mod block on a stone platform in the air of the district
(used by the client test; the gallery is far away from every structure). Run from the repository directory."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
blocks = json.loads((ROOT / "tools" / "data" / "spec" / "blocks.json").read_text(encoding="utf-8"))
X0, Z0, Y = 1000, 990, 150
dim = "execute in lewandivka:district run "
print(dim + f"forceload add {X0 - 2} {Z0} {X0 + 52} {Z0 + 40}")
print(dim + f"fill {X0 - 2} {Y} {Z0} {X0 + 52} {Y} {Z0 + 40} minecraft:smooth_stone")
print(dim + f"fill {X0 - 2} {Y + 1} {Z0} {X0 + 52} {Y + 12} {Z0 + 40} minecraft:air")
for i, b in enumerate(blocks):
    x = X0 + 2 + (i % 18) * 2
    z = Z0 + 10 + (i // 18) * 4
    print(dim + f"setblock {x} {Y + 1} {z} lewandivka:{b['id']}")
