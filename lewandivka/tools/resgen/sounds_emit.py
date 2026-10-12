"""Renders every sound of the catalog to Ogg Vorbis (mono, 22.05 kHz) and writes sounds.json."""
from __future__ import annotations

import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

from . import sounds_music  # noqa: F401  (registers the music recipes)
from . import sounds_recipes  # noqa: F401
from .sounds_synth import RECIPES, SR, normalize
from .spec import ASSETS, MOD_ID, sounds, write_json


def write_ogg(x: np.ndarray, path: Path, quality: int = 3) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    x = np.clip(normalize(x, 0.92) if np.max(np.abs(x)) > 0.98 else x, -1, 1)
    pcm = (x * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "x.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis",
                        "-q:a", str(quality), "-ac", "1", str(path)], check=True)


def missing_recipes() -> list:
    return [s["id"] for s in sounds() if s["id"] not in RECIPES]


def generate() -> int:
    missing = missing_recipes()
    if missing:
        raise SystemExit("no synthesis recipe for: " + ", ".join(missing))
    entries = {}
    n = 0
    for s in sounds():
        sid = s["id"]
        recipe = RECIPES[sid]
        files = []
        for v in range(s["variants"]):
            base = sid.replace(".", "/") + (f"_{v + 1}" if s["variants"] > 1 else "")
            write_ogg(recipe(v), ASSETS / "sounds" / f"{base}.ogg", 3 if s["category"] != "music" else 4)
            entry = {"name": f"{MOD_ID}:{base}", "volume": s["volume"]}
            if s["stream"]:
                entry["stream"] = True
            files.append(entry)
            n += 1
        entries[sid] = {"category": s["category"], "sounds": files, "subtitle": f"subtitles.{MOD_ID}.{sid}"}
    write_json(ASSETS / "sounds.json", entries)
    return n
