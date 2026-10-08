#!/usr/bin/env python3
"""Builds the modpack deliverables of "Левандівка: по той бік району".

Resolves every mod of tools/modpack/mods.json from the official Modrinth API for Minecraft 1.20.1 / Fabric, pins a
mutually compatible set (the fabric.mod.json inside every candidate jar is read and its dependency ranges are checked
against the other chosen mods), and writes:

  Lewandivka-<v>.mrpack                      Modrinth modpack (jars are referenced by URL + hash, never copied)
  Lewandivka-Server-<v>.zip                  dedicated server pack (server-side mods only, Fabric launcher, scripts)
  Lewandivka-TLauncher-Installer-<v>.zip     installer for TLauncher / any launcher that uses the .minecraft folder
  lewandivka-<v>.jar                         the mod itself (copied from --jar)
  THIRD_PARTY.md                             every third-party project, license, version, side and purpose
  resolved.json                              machine readable report of what was picked and why

Needs network access (Modrinth, Fabric meta). Standard library only.
"""
import argparse
import hashlib
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
API = "https://api.modrinth.com/v2"
USER_AGENT = "evanematt-Check/lewandivka-packaging (https://github.com/evanematt/Check)"
BUILTIN = {"minecraft", "fabricloader", "fabric-loader", "java", "fabric", "fabricapi_placeholder"}


# --------------------------------------------------------------------------------------------------- network

def fetch(url: str, binary: bool = False, retries: int = 4):
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            return data if binary else json.loads(data.decode("utf-8"))
        except urllib.error.HTTPError as e:
            last = e
            if e.code == 404:
                return None
            time.sleep(1.5 * (attempt + 1))
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"GET {url} failed: {last}")


def api(path: str, **params):
    query = ""
    if params:
        query = "?" + urllib.parse.urlencode({k: (json.dumps(v) if isinstance(v, (list, dict)) else v) for k, v in params.items()})
    return fetch(API + path + query)


# --------------------------------------------------------------------------------------------------- versions

def numbers(text: str, minecraft: str):
    """First dotted number sequence of a version string that is not the Minecraft version itself."""
    skip = {minecraft, minecraft.rsplit(".", 1)[0] if minecraft.count(".") > 1 else minecraft}
    for m in re.finditer(r"(\d+)\.(\d+)(?:\.(\d+))?", text or ""):
        if m.group(0) in skip:
            continue
        return (int(m.group(1)), int(m.group(2)), int(m.group(3) or 0))
    m = re.search(r"(\d+)", text or "")
    return (int(m.group(1)), 0, 0) if m else None


def comparator(token: str, version):
    """One Fabric version comparator against a (major, minor, patch) tuple; unknown syntax counts as satisfied."""
    token = token.strip()
    if token in ("", "*", "x", "X"):
        return True
    m = re.match(r"^(>=|<=|>|<|=|~|\^)?\s*v?(\d+|[xX*])(?:\.(\d+|[xX*]))?(?:\.(\d+|[xX*]))?", token)
    if not m:
        return True
    op = m.group(1) or "="
    parts = [m.group(2), m.group(3), m.group(4)]
    wild = [p is None or p in ("x", "X", "*") for p in parts]
    target = tuple(0 if w else int(p) for p, w in zip(parts, wild))
    if op == "=":
        for i in range(3):
            if not wild[i] and version[i] != target[i]:
                return False
        return True
    if op == ">=":
        return version >= target
    if op == ">":
        return version > target
    if op == "<=":
        return version <= target
    if op == "<":
        return version < target
    if op == "~":
        return version >= target and version[0] == target[0] and (wild[1] or version[1] == target[1])
    if op == "^":
        if target[0] > 0 or wild[1]:
            return version >= target and version[0] == target[0]
        return version >= target and version[0] == 0 and version[1] == target[1]
    return True


def satisfies(predicate, version) -> bool:
    """Fabric dependency predicates: a string or a list of strings (OR); inside a string, spaces mean AND."""
    if version is None:
        return True
    alternatives = predicate if isinstance(predicate, list) else [predicate]
    for alt in alternatives:
        for option in str(alt).split("||"):
            tokens = option.split()
            # ">= 1.2" written with a space
            merged, i = [], 0
            while i < len(tokens):
                if tokens[i] in (">=", "<=", ">", "<", "=", "~", "^") and i + 1 < len(tokens):
                    merged.append(tokens[i] + tokens[i + 1])
                    i += 2
                else:
                    merged.append(tokens[i])
                    i += 1
            if all(comparator(t, version) for t in merged):
                return True
    return False


# --------------------------------------------------------------------------------------------------- resolving

class Pick:
    def __init__(self, cfg, project, versions, index):
        self.cfg = cfg
        self.project = project
        self.versions = versions
        self.index = index
        self.jar = None
        self.meta = {}
        self.reason = "pack"

    @property
    def version(self):
        return self.versions[self.index]

    @property
    def file(self):
        files = self.version["files"]
        for f in files:
            if f.get("primary"):
                return f
        return files[0]

    @property
    def slug(self):
        return self.project["slug"]

    def load(self, minecraft: str):
        data = fetch(self.file["url"], binary=True)
        sha512 = self.file["hashes"].get("sha512")
        if sha512 and hashlib.sha512(data).hexdigest() != sha512:
            raise RuntimeError(f"hash mismatch for {self.file['filename']}")
        self.jar = data
        self.meta = {}
        self.nested = []
        if self.file["filename"].endswith(".jar"):
            try:
                with zipfile.ZipFile(io.BytesIO(data)) as z:
                    self.meta = json.loads(z.read("fabric.mod.json").decode("utf-8-sig"))
                    # jar-in-jar: Fabric API is a bundle of ~50 modules, other mods embed their libraries
                    for entry in self.meta.get("jars", []) or []:
                        try:
                            with zipfile.ZipFile(io.BytesIO(z.read(entry["file"]))) as nz:
                                self.nested.append(json.loads(nz.read("fabric.mod.json").decode("utf-8-sig")))
                        except Exception:  # noqa: BLE001
                            continue
            except Exception as e:  # noqa: BLE001
                print(f"  note: no readable fabric.mod.json in {self.file['filename']} ({e})")
        self.numeric = numbers(str(self.meta.get("version") or self.version["version_number"]), minecraft) or numbers(self.version["version_number"], minecraft)

    def provided(self, minecraft: str):
        """Every mod id this jar makes available (itself, 'provides' aliases, embedded modules) with its numeric version."""
        out = {}
        for meta in [self.meta] + self.nested:
            number = numbers(str(meta.get("version", "")), minecraft) or self.numeric
            for mid in [meta.get("id")] + [p if isinstance(p, str) else p.get("id") for p in meta.get("provides", []) or []]:
                if mid:
                    out[mid] = number
        return out


def find_project(cfg, minecraft: str):
    candidates = [cfg["slug"]] + list(cfg.get("alt", []))
    for slug in candidates:
        project = fetch(f"{API}/project/{urllib.parse.quote(slug)}")
        if project:
            return project
    wanted = cfg.get("type", "mod")
    facets = [[f"project_type:{wanted}"], [f"versions:{minecraft}"]]
    if wanted == "mod":
        facets.append(["categories:fabric"])
    found = api("/search", query=cfg["slug"].replace("-", " "), facets=facets, limit=5)
    for hit in (found or {}).get("hits", []):
        project = fetch(f"{API}/project/{hit['project_id']}")
        if project:
            return project
    return None


def list_versions(project, cfg, minecraft: str, loader: str):
    params = {"game_versions": [minecraft]}
    if cfg.get("type", "mod") == "mod":
        params["loaders"] = [loader]
    versions = api(f"/project/{project['id']}/version", **params) or []
    versions.sort(key=lambda v: v["date_published"], reverse=True)
    releases = [v for v in versions if v["version_type"] == "release"]
    rest = [v for v in versions if v["version_type"] != "release"]
    pin = cfg.get("pin")
    if pin:
        pinned = [v for v in versions if v["version_number"] == pin or v["version_number"].startswith(pin) or pin in v["version_number"]]
        if pinned:
            return pinned + [v for v in releases if v not in pinned]
    return releases + rest


def resolve(cfg, minecraft: str, loader: str, reason: str = "pack"):
    project = find_project(cfg, minecraft)
    if not project:
        raise RuntimeError(f"project {cfg['slug']} not found on Modrinth")
    versions = list_versions(project, cfg, minecraft, loader)
    if not versions:
        raise RuntimeError(f"{project['slug']} has no {loader} build for Minecraft {minecraft}")
    pick = Pick(cfg, project, versions, 0)
    pick.reason = reason
    return pick


def graph_problems(picks, minecraft: str = "1.20.1", loader_version: str = "0.16.10"):
    """Dependency ranges inside the chosen jars versus the other chosen mods, Minecraft and the loader."""
    provided = {}
    for p in picks:
        for mid, number in p.provided(minecraft).items():
            provided[mid] = (p, number)
    game = numbers(minecraft, "") or (1, 20, 1)
    loader = numbers(loader_version, "") or (0, 16, 10)
    problems = []
    for p in picks:
        for dep, pred in (p.meta.get("depends") or {}).items():
            if dep == "minecraft":
                if not satisfies(pred, game):
                    problems.append((p, f"needs minecraft {pred}", None))
                continue
            if dep in ("fabricloader", "fabric-loader"):
                if not satisfies(pred, loader):
                    problems.append((p, f"needs fabric loader {pred}", None))
                continue
            if dep in BUILTIN:
                continue
            target = provided.get(dep)
            if target is None:
                problems.append((p, f"needs {dep} which is not in the pack", None))
            elif not satisfies(pred, target[1]):
                problems.append((p, f"needs {dep} {pred} but the pack has {target[0].version['version_number']}", target[0]))
        for dep, pred in (p.meta.get("breaks") or {}).items():
            target = provided.get(dep)
            if target is not None and target[0] is not p and satisfies(pred, target[1]):
                problems.append((p, f"breaks with {dep} {target[0].version['version_number']}", target[0]))
    return problems


def build_graph(config, report):
    minecraft = config["minecraft"]
    loader = config["loader"]
    picks = {}
    skipped = []
    for cfg in config["mods"]:
        try:
            pick = resolve(cfg, minecraft, loader)
            pick.load(minecraft)
            picks[pick.project["id"]] = pick
            print(f"+ {pick.slug}: {pick.version['version_number']}")
        except Exception as e:  # noqa: BLE001
            if cfg.get("optional_pick"):
                print(f"- {cfg['slug']} skipped: {e}")
                skipped.append((cfg["slug"], str(e)))
            else:
                raise
    # required dependencies that nobody listed (Modrinth metadata)
    changed = True
    while changed:
        changed = False
        for pick in list(picks.values()):
            for dep in pick.version.get("dependencies", []):
                if dep["dependency_type"] != "required" or not dep.get("project_id") or dep["project_id"] in picks:
                    continue
                project = fetch(f"{API}/project/{dep['project_id']}")
                if not project:
                    continue
                cfg = {"slug": project["slug"], "client": "required", "server": "required", "role": "dependency",
                       "purpose": f"Required by {pick.project['title']}."}
                try:
                    extra = resolve(cfg, minecraft, loader, f"required by {pick.slug}")
                    extra.load(minecraft)
                    picks[extra.project["id"]] = extra
                    print(f"+ {extra.slug}: {extra.version['version_number']} (required by {pick.slug})")
                    changed = True
                except Exception as e:  # noqa: BLE001
                    print(f"! dependency {project['slug']} of {pick.slug} could not be resolved: {e}")
    # step back through older releases until every declared range holds
    for round_ in range(40):
        problems = graph_problems(list(picks.values()), minecraft, config["loader_version"])
        if not problems:
            break
        progressed = False
        for pick, text, target in problems:
            print(f"? {pick.slug} {pick.version['version_number']}: {text}")
            if pick.index + 1 < len(pick.versions) and not pick.cfg.get("pin"):
                pick.index += 1
                pick.load(minecraft)
                print(f"  -> trying {pick.slug} {pick.version['version_number']}")
                progressed = True
                break
        if not progressed:
            for pick, text, target in problems:
                if pick.cfg.get("optional_pick") or pick.cfg.get("client") == "optional":
                    print(f"- dropping {pick.slug}: {text}")
                    skipped.append((pick.slug, text))
                    picks.pop(pick.project["id"], None)
                    progressed = True
                    break
            if not progressed:
                raise RuntimeError("no compatible set: " + "; ".join(f"{p.slug}: {t}" for p, t, _ in problems))
    report["skipped"] = skipped
    return list(picks.values())


# --------------------------------------------------------------------------------------------------- outputs

def side(pick, which: str) -> str:
    cfg = pick.cfg.get(which)
    project = pick.project.get(f"{which}_side", "optional")
    if project == "unsupported":
        return "unsupported"
    if cfg:
        return cfg
    return project


def target_dir(pick) -> str:
    kind = pick.project.get("project_type", "mod")
    return {"shader": "shaderpacks", "resourcepack": "resourcepacks"}.get(kind, "mods")


def write_index(config, picks, version):
    files = []
    for p in sorted(picks, key=lambda x: x.slug):
        f = p.file
        files.append({
            "path": f"{target_dir(p)}/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": side(p, "client"), "server": side(p, "server")},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
    return {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": version,
        "name": config["pack_name"],
        "summary": config["summary"],
        "files": files,
        "dependencies": {"minecraft": config["minecraft"], "fabric-loader": config["loader_version"]},
    }


PRESETS = {
    "PERFORMANCE": {
        "options.txt": "renderDistance:10\nparticles:1\nentityDistanceScaling:1.0\nmaxFps:120\nlang:uk_ua\n",
        "config/iris.properties": "enableShaders=false\n",
        "README.txt": "PERFORMANCE: shaders off, normal particles, render distance 8-12 chunks (10 here).\n",
    },
    "CINEMATIC": {
        "options.txt": "renderDistance:14\nparticles:0\nentityDistanceScaling:1.0\nmaxFps:120\nlang:uk_ua\n",
        "config/iris.properties": "enableShaders=true\nshaderPack={shader}\n",
        "README.txt": "CINEMATIC: MakeUp Ultra Fast shader, dynamic lights and enhanced particles, render distance 12-16 chunks (14 here).\n"
                      "Shaders are never required: every puzzle and every telegraph is readable without them.\n",
    },
}

APPLY_SH = """#!/bin/sh
# usage: ./apply_preset.sh PERFORMANCE|CINEMATIC [path to the game folder, default: current folder]
set -e
PRESET="$1"
GAME="${2:-.}"
HERE="$(cd "$(dirname "$0")" && pwd)"
[ -d "$HERE/$PRESET" ] || { echo "unknown preset '$PRESET' (PERFORMANCE or CINEMATIC)"; exit 1; }
mkdir -p "$GAME/config"
cp -f "$HERE/$PRESET/options.txt" "$GAME/options.txt.preset"
[ -f "$HERE/$PRESET/config/iris.properties" ] && cp -f "$HERE/$PRESET/config/iris.properties" "$GAME/config/iris.properties"
echo "Preset $PRESET applied. options.txt.preset holds the video settings: copy the lines you want into options.txt."
"""

APPLY_BAT = """@echo off
rem usage: apply_preset.bat PERFORMANCE|CINEMATIC [game folder]
set PRESET=%1
set GAME=%2
if "%GAME%"=="" set GAME=.
if not exist "%~dp0%PRESET%" ( echo unknown preset - use PERFORMANCE or CINEMATIC & exit /b 1 )
if not exist "%GAME%\\config" mkdir "%GAME%\\config"
copy /Y "%~dp0%PRESET%\\options.txt" "%GAME%\\options.txt.preset" >nul
if exist "%~dp0%PRESET%\\config\\iris.properties" copy /Y "%~dp0%PRESET%\\config\\iris.properties" "%GAME%\\config\\iris.properties" >nul
echo Preset %PRESET% applied. options.txt.preset holds the video settings: copy the lines you want into options.txt.
"""


def preset_files(shader_name: str):
    out = {}
    for name, files in PRESETS.items():
        for rel, text in files.items():
            out[f"presets/{name}/{rel}"] = text.replace("{shader}", shader_name or "")
    out["presets/apply_preset.sh"] = APPLY_SH
    out["presets/apply_preset.bat"] = APPLY_BAT
    return out


def write_mrpack(path: Path, index, jar_path: Path, jar_name: str, shader_name: str):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        z.write(jar_path, f"overrides/mods/{jar_name}")
        # the default is the PERFORMANCE preset: shaders stay off until the player decides otherwise
        z.writestr("overrides/config/iris.properties", PRESETS["PERFORMANCE"]["config/iris.properties"])
        for rel, text in preset_files(shader_name).items():
            z.writestr(f"overrides/{rel}", text)


SERVER_PROPERTIES = """#Minecraft server properties (Lewandivka server pack)
motd=Левандівка: по той бік району
gamemode=survival
difficulty=normal
max-players=3
view-distance=10
simulation-distance=8
spawn-protection=0
enable-command-block=false
pvp=false
# TLauncher and other offline launchers cannot log in to Mojang's session servers: this pack runs in offline mode.
# Use a whitelist (white-list=true and /whitelist add <name>) when the server is reachable from the internet.
online-mode=false
white-list=false
enable-rcon=false
"""

START_SH = """#!/bin/sh
cd "$(dirname "$0")"
if ! grep -q "eula=true" eula.txt 2>/dev/null; then
  echo "Minecraft needs you to accept the EULA: https://aka.ms/MinecraftEULA"
  printf "Type yes to accept: "
  read answer
  [ "$answer" = "yes" ] || exit 1
  echo "eula=true" > eula.txt
fi
exec java -Xms2G -Xmx4G -jar fabric-server-launch.jar nogui
"""

START_BAT = """@echo off
cd /d "%~dp0"
findstr /c:"eula=true" eula.txt >nul 2>&1
if errorlevel 1 (
  echo Minecraft needs you to accept the EULA: https://aka.ms/MinecraftEULA
  set /p answer=Type yes to accept: 
  if /i not "%answer%"=="yes" exit /b 1
  echo eula=true> eula.txt
)
java -Xms2G -Xmx4G -jar fabric-server-launch.jar nogui
pause
"""

SERVER_README = """Lewandivka - dedicated server pack ({version})
==================================================

Requirements: Java 17 (64 bit), about 4 GB of free RAM, Minecraft 1.20.1 + Fabric (the launcher jar is included;
the vanilla server jar is downloaded by Fabric on the first start, so the first start needs internet access).

1. Start the server: start.bat (Windows) or ./start.sh (Linux / macOS). Accept the EULA when asked.
2. Players connect with Minecraft 1.20.1 + Fabric + the same mods (see the .mrpack or the TLauncher installer).
3. The first player to join starts the campaign in the district. 1, 2 or 3 players: everything scales.

Offline mode: server.properties has online-mode=false so that TLauncher accounts can join. Anyone can use any name:
turn on the whitelist (white-list=true, /whitelist add <name>) if the server is reachable from the internet.

Admin commands (permission level 2):
  /lewandivka status                       where the campaign is
  /lewandivka checkpoint                   back to the last checkpoint of the structure you are in
  /lewandivka reset encounter <name>       restart a dungeon (garage13, tram_stop, rainbow_garage, shelter, aquapark, ...)
  /lewandivka setstage <stage>             jump to a stage if the campaign got stuck
  /lewandivka party <1|2|3|auto>           force the scaling of puzzles and bosses
Fabric API and GeckoLib are separate jars in mods/ (they are never embedded in lewandivka.jar).
"""

PS1 = r"""# Lewandivka installer for TLauncher (and any launcher that uses the .minecraft folder)
# Run: right click > Run with PowerShell, or:  powershell -ExecutionPolicy Bypass -File install.ps1 [-Dir "C:\path\.minecraft"]
param([string]$Dir = "$env:APPDATA\.minecraft", [switch]$NoFabric)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$index = Get-Content -Raw -Encoding UTF8 (Join-Path $here "modrinth.index.json") | ConvertFrom-Json
Write-Host "Game folder: $Dir"
New-Item -ItemType Directory -Force -Path $Dir | Out-Null
foreach ($f in $index.files) {
  if ($f.env.client -eq "unsupported") { continue }
  $target = Join-Path $Dir ($f.path -replace "/", "\")
  New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
  Write-Host "downloading $($f.path)"
  Invoke-WebRequest -UseBasicParsing -Uri $f.downloads[0] -OutFile $target
  $hash = (Get-FileHash -Algorithm SHA1 $target).Hash.ToLower()
  if ($hash -ne $f.hashes.sha1) { throw "hash mismatch for $($f.path)" }
}
Copy-Item -Recurse -Force (Join-Path $here "overrides\*") $Dir
if (-not $NoFabric) {
  $jar = Join-Path $env:TEMP "fabric-installer.jar"
  Invoke-WebRequest -UseBasicParsing -Uri "{installer_url}" -OutFile $jar
  $java = (Get-Command java -ErrorAction SilentlyContinue)
  if (-not $java) { Write-Host "Java 17 was not found on PATH: install it, or pick 'Fabric 1.20.1' in the TLauncher version list."; exit 1 }
  & java -jar $jar client -dir $Dir -mcversion {minecraft} -loader {loader} -noprofile
}
Write-Host "Done. In TLauncher choose the version 'fabric-loader-{loader}-{minecraft}' and press Play."
"""

SH = r"""#!/bin/sh
# Lewandivka installer for TLauncher (Linux / macOS). usage: ./install.sh [game folder]
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="${1:-$HOME/.minecraft}"
echo "Game folder: $DIR"
mkdir -p "$DIR"
python3 - "$HERE" "$DIR" <<'PY'
import hashlib, json, os, sys, urllib.request
here, game = sys.argv[1:3]
index = json.load(open(os.path.join(here, "modrinth.index.json"), encoding="utf-8"))
for f in index["files"]:
    if f["env"]["client"] == "unsupported":
        continue
    target = os.path.join(game, *f["path"].split("/"))
    os.makedirs(os.path.dirname(target), exist_ok=True)
    print("downloading", f["path"])
    req = urllib.request.Request(f["downloads"][0], headers={"User-Agent": "lewandivka-installer"})
    data = urllib.request.urlopen(req).read()
    if hashlib.sha1(data).hexdigest() != f["hashes"]["sha1"]:
        raise SystemExit("hash mismatch for " + f["path"])
    open(target, "wb").write(data)
PY
cp -R "$HERE/overrides/." "$DIR/"
curl -fsSL -o /tmp/fabric-installer.jar "{installer_url}"
java -jar /tmp/fabric-installer.jar client -dir "$DIR" -mcversion {minecraft} -loader {loader} -noprofile
echo "Done. In TLauncher choose the version 'fabric-loader-{loader}-{minecraft}' and press Play."
"""

TL_README = """Lewandivka with TLauncher
=========================

What you need: TLauncher (https://tlauncher.org), Java 17 (TLauncher downloads it for Minecraft 1.20.1 automatically).

Windows
  1. Start TLauncher once with any 1.20.1 version, then close it (this creates the .minecraft folder).
  2. Unzip this archive and run install.ps1 (right click > Run with PowerShell).
     It downloads the mods from Modrinth (hash checked), copies lewandivka-{version}.jar, and installs Fabric Loader.
  3. In TLauncher choose the version  fabric-loader-{loader}-{minecraft}  and press Play.
     (TLauncher also lists "Fabric 1.20.1" in its version list: that works too, then copy the jars from mods/ yourself.)

Linux / macOS: ./install.sh [game folder]   (needs python3, curl and java)

Other launchers (Prism, MultiMC, ATLauncher, Modrinth App): import Lewandivka-{version}.mrpack instead.

Servers: Lewandivka-Server-{version}.zip. TLauncher accounts are "offline" accounts, so that server pack runs with
online-mode=false.

Graphics presets: presets/PERFORMANCE (default, shaders off) and presets/CINEMATIC (MakeUp Ultra Fast shader, dynamic
lights, more particles). Run presets/apply_preset.bat CINEMATIC (or .sh) to switch. Shaders are never required.

Language: the mod is Ukrainian first (uk_ua) with a full English translation (en_us). Switch it in the game options.
"""


def fabric_installer_url():
    versions = fetch("https://meta.fabricmc.net/v2/versions/installer") or []
    stable = [v for v in versions if v.get("stable")]
    chosen = (stable or versions)[0]["version"] if versions else "1.0.1"
    return chosen, f"https://maven.fabricmc.net/net/fabricmc/fabric-installer/{chosen}/fabric-installer-{chosen}.jar"


def write_server(path: Path, config, picks, jar_path: Path, jar_name: str, version: str):
    installer, _ = fabric_installer_url()
    launcher = fetch(f"https://meta.fabricmc.net/v2/versions/loader/{config['minecraft']}/{config['loader_version']}/{installer}/server/jar", binary=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        root = f"Lewandivka-Server-{version}/"
        z.write(jar_path, root + f"mods/{jar_name}")
        for p in picks:
            if target_dir(p) != "mods" or side(p, "server") == "unsupported":
                continue
            z.writestr(root + f"mods/{p.file['filename']}", p.jar)
        z.writestr(root + "fabric-server-launch.jar", launcher)
        z.writestr(root + "server.properties", SERVER_PROPERTIES)
        z.writestr(root + "eula.txt", "# Change to eula=true after reading https://aka.ms/MinecraftEULA (start.sh / start.bat ask you).\neula=false\n")
        info = z.ZipInfo(root + "start.sh")
        info.external_attr = 0o755 << 16
        z.writestr(info, START_SH)
        z.writestr(root + "start.bat", START_BAT)
        z.writestr(root + "README-SERVER.txt", SERVER_README.format(version=version))


def write_tlauncher(path: Path, config, index, jar_path: Path, jar_name: str, shader_name: str, version: str):
    _, installer_url = fabric_installer_url()
    fields = {"installer_url": installer_url, "minecraft": config["minecraft"], "loader": config["loader_version"], "version": version}
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        root = f"Lewandivka-TLauncher-Installer-{version}/"
        z.writestr(root + "modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        z.write(jar_path, root + f"overrides/mods/{jar_name}")
        z.writestr(root + "overrides/config/iris.properties", PRESETS["PERFORMANCE"]["config/iris.properties"])
        for rel, text in preset_files(shader_name).items():
            z.writestr(root + f"overrides/{rel}", text)
        z.writestr(root + "install.ps1", PS1.replace("{installer_url}", installer_url).replace("{minecraft}", fields["minecraft"]).replace("{loader}", fields["loader"]))
        info = z.ZipInfo(root + "install.sh")
        info.external_attr = 0o755 << 16
        z.writestr(info, SH.replace("{installer_url}", installer_url).replace("{minecraft}", fields["minecraft"]).replace("{loader}", fields["loader"]))
        z.writestr(root + "README-TLauncher.txt", TL_README.replace("{version}", version).replace("{loader}", fields["loader"]).replace("{minecraft}", fields["minecraft"]))


def license_text(project) -> str:
    lic = project.get("license") or {}
    name = lic.get("name") or lic.get("id") or "see project page"
    url = lic.get("url")
    return f"{name} ({lic.get('id')})" + (f" - {url}" if url else "")


def write_third_party(path: Path, config, picks, report, version, own_license: str):
    lines = [
        "# Third-party software used by the pack",
        "",
        f"Generated by `tools/modpack/build_modpack.py` for Minecraft {config['minecraft']} / Fabric Loader {config['loader_version']}.",
        "None of these projects is copied into `lewandivka-%s.jar`; the modpack references them by URL and hash" % version,
        "(Modrinth `.mrpack`), the server pack and the installer download or contain the unmodified jars of the projects that",
        "allow redistribution. Fabric API is **not** embedded in the mod.",
        "",
        "| Project | Page | License | Version | Client | Server | Purpose |",
        "|---|---|---|---|---|---|---|",
    ]
    for p in sorted(picks, key=lambda x: x.project["title"].lower()):
        purpose = p.cfg.get("purpose", "")
        lines.append("| {t} | https://modrinth.com/{kind}/{slug} | {lic} | {ver} | {c} | {s} | {purpose} |".format(
            t=p.project["title"], kind=p.project.get("project_type", "mod"), slug=p.slug, lic=license_text(p.project),
            ver=p.version["version_number"], c=side(p, "client"), s=side(p, "server"), purpose=purpose or p.reason))
    lines += [
        "| Fabric Loader / Fabric Installer | https://fabricmc.net | Apache-2.0 | %s | required | required | Mod loader (installed by the installer / bundled in the server launcher). |" % config["loader_version"],
        "",
        "## Not available at build time",
        "",
    ]
    skipped = report.get("skipped") or []
    lines += [f"- `{slug}`: {why}" for slug, why in skipped] or ["- nothing: every listed project was resolved."]
    lines += [
        "",
        "## This mod",
        "",
        f"`lewandivka` is {own_license}. All textures, sounds, models, animations and text are original and generated by the",
        "scripts in `tools/` (`tools/generate_resources.py`). The reference archive supplied with the task was used for",
        "architectural inspiration only; no file from it is included.",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", required=True, help="the built lewandivka jar")
    parser.add_argument("--out", default="dist")
    parser.add_argument("--config", default=str(HERE / "mods.json"))
    parser.add_argument("--license", default="MIT licensed")
    args = parser.parse_args()
    config = json.loads(Path(args.config).read_text(encoding="utf-8"))
    version = config["pack_version"]
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    jar = Path(args.jar)
    jar_name = f"lewandivka-{version}.jar"
    report = {}
    picks = build_graph(config, report)
    shader = next((p for p in picks if target_dir(p) == "shaderpacks"), None)
    shader_name = shader.file["filename"] if shader else ""
    index = write_index(config, picks, version)
    (out / jar_name).write_bytes(jar.read_bytes())
    write_mrpack(out / f"Lewandivka-{version}.mrpack", index, out / jar_name, jar_name, shader_name)
    write_server(out / f"Lewandivka-Server-{version}.zip", config, picks, out / jar_name, jar_name, version)
    write_tlauncher(out / f"Lewandivka-TLauncher-Installer-{version}.zip", config, index, out / jar_name, jar_name, shader_name, version)
    write_third_party(out / "THIRD_PARTY.md", config, picks, report, version, args.license)
    report["picks"] = [{"slug": p.slug, "version": p.version["version_number"], "file": p.file["filename"], "url": p.file["url"],
                        "sha1": p.file["hashes"]["sha1"], "client": side(p, "client"), "server": side(p, "server"), "reason": p.reason}
                       for p in sorted(picks, key=lambda x: x.slug)]
    (out / "resolved.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"wrote {len(picks)} mods into {out}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:  # noqa: BLE001
        print(f"modpack build failed: {exc}", file=sys.stderr)
        sys.exit(1)
