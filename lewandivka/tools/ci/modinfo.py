#!/usr/bin/env python3
"""Describes Modrinth projects: what the project is, which versions fit Minecraft 1.20.1 / Fabric, what is inside the jar of the newest
one (fabric.mod.json, the language file, the kinds of resources), so that a mod of the pack can be used by the mod without having
the jar at hand. The development machine of the mod cannot reach Modrinth, CI can.

usage: modinfo.py <request file with one slug per line> <output folder>
"""
import hashlib
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "evanematt-Check/lewandivka-ci (https://github.com/evanematt/Check)"
MINECRAFT = "1.20.1"


def get(url: str, binary: bool = False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    return data if binary else json.loads(data.decode("utf-8"))


def describe(slug: str, out: Path) -> None:
    print("=" * 100)
    print(f"# {slug}")
    try:
        project = get(f"{API}/project/{urllib.parse.quote(slug)}")
    except Exception as e:  # noqa: BLE001
        print(f"no such project: {e}")
        return
    print(f"title: {project['title']}  id: {project['id']}  type: {project['project_type']}  client: {project['client_side']}  server: {project['server_side']}")
    print(f"license: {(project.get('license') or {}).get('id')}  downloads: {project.get('downloads')}  updated: {project.get('updated')}")
    print(f"categories: {project.get('categories')} {project.get('additional_categories')}")
    print(f"game_versions: {project.get('game_versions')}")
    print(f"loaders: {project.get('loaders')}")
    print(f"description: {project.get('description')}")
    print("---- body ----")
    print((project.get("body") or "")[:6000])
    versions = get(f"{API}/project/{project['id']}/version")
    print(f"---- {len(versions)} versions ----")
    for v in versions[:40]:
        files = ", ".join(f["filename"] for f in v["files"])
        deps = [(d.get("dependency_type"), d.get("project_id") or d.get("file_name")) for d in v.get("dependencies", [])]
        print(f"{v['version_number']:28} {v['version_type']:8} mc={v['game_versions']} loaders={v['loaders']} files=[{files}] deps={deps}")
    fits = [v for v in versions if MINECRAFT in v["game_versions"] and "fabric" in v["loaders"]]
    print(f"---- {len(fits)} versions for Minecraft {MINECRAFT} / fabric ----")
    if not fits:
        return
    pick = fits[0]
    f = next((x for x in pick["files"] if x.get("primary")), pick["files"][0])
    data = get(f["url"], binary=True)
    ok = hashlib.sha1(data).hexdigest() == f["hashes"]["sha1"]
    print(f"picked {pick['version_number']}: {f['filename']} {len(data)} bytes, hash {'ok' if ok else 'MISMATCH'}")
    for d in pick.get("dependencies", []):
        if d.get("project_id"):
            try:
                dp = get(f"{API}/project/{d['project_id']}")
                print(f"dependency ({d.get('dependency_type')}): {dp['slug']} - {dp['title']}")
            except Exception as e:  # noqa: BLE001
                print(f"dependency {d}: {e}")
    jar = out / f"{slug}.jar"
    jar.write_bytes(data)
    inspect(jar)
    jar.unlink()


def inspect(jar: Path) -> None:
    with zipfile.ZipFile(jar) as z:
        names = z.namelist()
        print(f"---- jar: {len(names)} entries ----")
        kinds = {}
        for n in names:
            m = re.match(r"(assets|data)/([^/]+)/([^/]+)/", n)
            key = f"{m.group(1)}/{m.group(2)}/{m.group(3)}" if m else n.split("/")[0]
            kinds[key] = kinds.get(key, 0) + 1
        for k in sorted(kinds):
            print(f"  {k}: {kinds[k]}")
        for n in names:
            if n.endswith("fabric.mod.json"):
                print("---- " + n + " ----")
                print(z.read(n).decode("utf-8", "replace")[:4000])
        for n in names:
            if re.search(r"/lang/en_us\.json$", n):
                print("---- " + n + " ----")
                print(z.read(n).decode("utf-8", "replace")[:6000])
        print("---- class names (first 150) ----")
        classes = [n for n in names if n.endswith(".class") and "$" not in n]
        for n in classes[:150]:
            print("  " + n)
        print("---- other resources (models, recipes, loot, tags, sounds), first 120 ----")
        shown = 0
        for n in names:
            if re.search(r"/(recipes|loot_tables|tags|sounds|models/item|blockstates)/", n) or n.endswith("sounds.json"):
                print("  " + n)
                shown += 1
                if shown >= 120:
                    break
        # constants that tell how the mod registers its things
        print("---- registration hints (strings in classes) ----")
        hints = set()
        for n in classes:
            blob = z.read(n)
            for m in re.finditer(rb"(?:entity|item|block)\.[a-z0-9_]+\.[a-z0-9_.]+|[a-z0-9_]+:(?:car|truck|van|bus|vehicle|jeep|sedan)[a-z0-9_]*", blob):
                hints.add(m.group(0).decode("ascii", "replace"))
        for h in sorted(hints)[:150]:
            print("  " + h)


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    out = Path(argv[2])
    out.mkdir(parents=True, exist_ok=True)
    for line in Path(argv[1]).read_text(encoding="utf-8").splitlines():
        slug = line.split("#")[0].strip()
        if slug:
            describe(slug, out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
