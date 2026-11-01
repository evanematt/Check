#!/usr/bin/env python3
"""Regenerates every generated resource of the mod (textures, models, languages, data, sounds).

    python3 tools/generate_resources.py            # everything
    python3 tools/generate_resources.py blocks lang # selected stages

Prerequisite: `gradle -p core exportSpec` (writes tools/data/spec/*.json from the Java catalogs).
Requires Python 3.10+, Pillow, numpy and ffmpeg (with libvorbis) for the sounds.
"""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from resgen import blocks_emit, data_emit, entities_emit, gui_paint, items_emit, lang  # noqa: E402

STAGES = {
    "blocks": blocks_emit.generate,
    "items": items_emit.generate,
    "entities": entities_emit.generate,
    "gui": gui_paint.generate,
    "lang": lang.generate,
    "data": data_emit.generate,
}


def sounds_stage() -> int:
    from resgen import sounds_emit
    return sounds_emit.generate()


STAGES["sounds"] = sounds_stage


def main(argv: list) -> int:
    wanted = argv or list(STAGES)
    for name in wanted:
        if name not in STAGES:
            print(f"unknown stage '{name}'; stages: {', '.join(STAGES)}")
            return 2
        t = time.time()
        n = STAGES[name]()
        print(f"{name:9s} {n:5d} files/entries  ({time.time() - t:.1f}s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
