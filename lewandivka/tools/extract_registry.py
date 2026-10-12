#!/usr/bin/env python3
"""Builds tools/data/mc1201-registry.json from the PrismarineJS minecraft-data package (Minecraft 1.20 / 1.20.1).

Usage:  python3 tools/extract_registry.py <path to minecraft-data/data/pc/1.20>
        (npm pack minecraft-data && tar xzf minecraft-data-*.tgz package/minecraft-data/data/pc/1.20)

The result only lists registry names and block-state properties; it is used by tools/validate_registry.py and by
the unit tests to make sure that no vanilla block, item or entity id used by the mod is misspelled or newer than 1.20.1.
minecraft-data is MIT licensed (see THIRD_PARTY.md); it is a development-time data source and is not shipped.
"""
import json
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = Path(sys.argv[1])
    blocks = {}
    for b in json.loads((src / "blocks.json").read_text(encoding="utf-8")):
        props = {}
        for st in b.get("states", []):
            if st["type"] == "bool":
                props[st["name"]] = ["true", "false"]
            elif st["type"] == "enum":
                props[st["name"]] = st["values"]
            else:  # int: minecraft-data only gives the count; values start at 0 or 1 depending on the block
                n = st["num_values"]
                props[st["name"]] = [str(i) for i in range(0, n + 1)]
        blocks[b["name"]] = props
    out = {
        "version": "1.20.1",
        "blocks": blocks,
        "items": sorted(i["name"] for i in json.loads((src / "items.json").read_text(encoding="utf-8"))),
        "entities": sorted(e["name"] for e in json.loads((src / "entities.json").read_text(encoding="utf-8"))),
        "particles": sorted(p["name"] for p in json.loads((src / "particles.json").read_text(encoding="utf-8"))),
        "biomes": sorted(b["name"] for b in json.loads((src / "biomes.json").read_text(encoding="utf-8"))),
        "effects": sorted(e["name"] for e in json.loads((src / "effects.json").read_text(encoding="utf-8"))),
        "enchantments": sorted(e["name"] for e in json.loads((src / "enchantments.json").read_text(encoding="utf-8"))),
    }
    target = Path(__file__).resolve().parent / "data" / "mc1201-registry.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    print(f"wrote {target} ({target.stat().st_size // 1024} KiB): {len(blocks)} blocks, {len(out['items'])} items, {len(out['entities'])} entities")


if __name__ == "__main__":
    main()
