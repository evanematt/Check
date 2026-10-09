#!/usr/bin/env python3
"""Downloads the furniture mod of the pack and its library from Modrinth into the mods folders of the development server and client
of the client test, so that the pictures of the test show the furniture of the rooms the way the pack does (the development game
loads the jars of its mods folder, remapped). The versions are those of the pack (see THIRD_PARTY.md); every file is hash checked.

usage: fetch_decor.py <mods folder> [<mods folder> ...]
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "evanematt-Check/lewandivka-ci (https://github.com/evanematt/Check)"
MINECRAFT = "1.20.1"
LOADER = "fabric"
# project slug -> the number (or a part of it) of the version of the pack
PACK = {"handcrafted": "3.0.6", "resourceful-lib": "2.1.29"}


def get(url: str, binary: bool = False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    return data if binary else json.loads(data.decode("utf-8"))


def main(folders: list) -> int:
    if not folders:
        print(__doc__)
        return 2
    for slug, number in PACK.items():
        query = urllib.parse.urlencode({"game_versions": json.dumps([MINECRAFT]), "loaders": json.dumps([LOADER])})
        versions = get(f"{API}/project/{slug}/version?{query}")
        picked = [v for v in versions if number in v["version_number"]]
        if not picked:
            print(f"{slug}: no version {number} for Minecraft {MINECRAFT} / {LOADER} (found {[v['version_number'] for v in versions][:5]})")
            return 1
        files = picked[0]["files"]
        f = next((x for x in files if x.get("primary")), files[0])
        data = get(f["url"], binary=True)
        if hashlib.sha1(data).hexdigest() != f["hashes"]["sha1"]:
            print(f"{slug}: hash mismatch")
            return 1
        for folder in folders:
            Path(folder).mkdir(parents=True, exist_ok=True)
            (Path(folder) / f["filename"]).write_bytes(data)
        print(f"{slug} {picked[0]['version_number']}: {f['filename']} ({len(data)} bytes) -> {', '.join(folders)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
