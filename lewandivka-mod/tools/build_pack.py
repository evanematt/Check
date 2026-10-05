"""Збирає модпак із Modrinth: .mrpack, серверний архів, архів для TLauncher, THIRD_PARTY.md.

Запуск (потрібен інтернет, є в GitHub Actions):
  python3 tools/build_pack.py <шлях до lewandivka-*.jar> <каталог виводу>

Принципи: не вбудовуємо чужі моди в наш jar; кожен мод — окремий файл із Modrinth;
несуттєвий мод, що не знайшовся для 1.20.1 або не пройшов перевірку сумісності, виключається (звіт у pack_report.txt).
"""
import hashlib
import re
import json
import os
import shutil
import sys
import urllib.parse
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "lewandivka-pack-builder/1.0 (github.com/evanematt/Check)"}
VERSION = "1.0.0"


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def jget(path, **params):
    q = urllib.parse.urlencode({k: json.dumps(v) if isinstance(v, (list, dict)) else v for k, v in params.items()})
    return json.loads(get(f"{API}/{path}?{q}"))


def versions(slug, mc):
    return jget(f"project/{slug}/version", game_versions=[mc], loaders=["fabric"])


def pick(vs, prefer=None):
    stable = [v for v in vs if v["version_type"] == "release"] or vs
    if prefer:
        for v in stable:
            if prefer in v["version_number"]:
                return v
    return stable[0] if stable else None


def vtuple(v):
    v = v.split("+")[0].split("-")[0]
    out = []
    for part in v.split("."):
        m = re.match(r"\d+", part)
        out.append(int(m.group()) if m else 0)
    return tuple(out)


def clause_ok(clause, ver):
    clause = clause.strip()
    if clause in ("*", ""):
        return True
    for op in (">=", "<=", ">", "<", "="):
        if clause.startswith(op):
            a, b = vtuple(ver), vtuple(clause[len(op):])
            n = max(len(a), len(b))
            a, b = a + (0,) * (n - len(a)), b + (0,) * (n - len(b))
            return {">=": a >= b, "<=": a <= b, ">": a > b, "<": a < b, "=": a == b}[op]
    if clause[0] in "~^":
        return True
    c = clause.replace("x", "0").replace("X", "0")
    a, b = vtuple(ver), vtuple(c)
    return a[: len(b)] == b if "x" in clause.lower() else a == b


def range_ok(spec, ver):
    specs = spec if isinstance(spec, list) else [spec]
    return any(all(clause_ok(c, ver) for c in re.split(r"\s+", alt.strip()) if c) for s in specs for alt in str(s).split("||"))


def validate(chosen, dl, our_jar, mc):
    """Офлайн-перевірка залежностей: кожен потрібний мод присутній і його версія підходить (як зробив би Fabric Loader)."""
    import io
    metas = []

    def read_jar(zf, label):
        metas.append((label, json.loads(zf.read("fabric.mod.json"), strict=False)))
        for n in zf.namelist():  # вкладені jar-и (Fabric API, LambDynamicLights API тощо)
            if n.startswith("META-INF/jars/") and n.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(zf.read(n))) as inner:
                    if "fabric.mod.json" in inner.namelist():
                        read_jar(inner, label + "!" + os.path.basename(n))

    for path in [os.path.join(dl, c["f"]["filename"]) for c in chosen] + [our_jar]:
        with zipfile.ZipFile(path) as z:
            read_jar(z, os.path.basename(path))
    ids = {}
    for name, m in metas:
        ids[m["id"]] = m["version"]
        for pid in m.get("provides", []):
            ids[pid] = m["version"]
    ids.update({"minecraft": mc, "java": "17", "fabricloader": "0.16.10"})
    probs = []
    for name, m in metas:
        for dep, spec in (m.get("depends") or {}).items():
            if dep not in ids:
                probs.append(f"FAIL {m['id']} потребує '{dep}', якого немає")
            elif dep not in ("minecraft", "java", "fabricloader") and not range_ok(spec, ids[dep]):
                probs.append(f"FAIL {m['id']} потребує {dep} {spec}, а є {ids[dep]}")
        for dep, spec in (m.get("breaks") or {}).items():
            if dep in ids and range_ok(spec, ids[dep]):
                probs.append(f"FAIL {m['id']} несумісний з {dep} {ids[dep]}")
    return probs or ["VALIDATION OK: залежності всіх модів задоволені"]


def main(jar, out):
    cfg = json.load(open(os.path.join(HERE, "pack_mods.json")))
    mc = cfg["minecraft"]
    os.makedirs(out, exist_ok=True)
    work = os.path.join(out, "_work")
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    report, chosen = [], []

    sodium_id = None
    for m in cfg["mods"]:
        slug = m["slug"]
        try:
            vs = versions(slug, mc)
            if m.get("pair_with") == "sodium" and sodium_id:
                # Iris обираємо за залежностями: не форсуємо пару, яка конфліктує з вибраним Sodium.
                ok = None
                for v in [x for x in vs if x["version_type"] == "release"] or vs:
                    deps = [d for d in v.get("dependencies", []) if d.get("project_id") and d.get("dependency_type") in ("required", "incompatible")]
                    sod = [d for d in deps if d["dependency_type"] == "required" and d.get("version_id")]
                    if all(d["version_id"] == sodium_id for d in sod if d.get("version_id")):
                        ok = v
                        break
                v = ok
            else:
                v = pick(vs, m.get("prefer"))
            if v is None:
                raise RuntimeError("немає версії для " + mc)
            if slug == "sodium":
                sodium_id = v["id"]
            f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
            proj = jget(f"project/{slug}")
            chosen.append({"m": m, "v": v, "f": f, "p": proj})
            report.append(f"OK   {slug} {v['version_number']}")
        except Exception as e:  # noqa: BLE001
            report.append(f"SKIP {slug}: {e}")
            if m.get("essential"):
                raise

    # Лише клієнтський шейдер (шукаємо за назвою, бо slug може змінитись).
    shader = None
    try:
        hits = jget("search", query=cfg["shader"]["query"], facets=[["project_type:shader"]], limit=5)["hits"]
        hit = next((h for h in hits if "makeup" in h["slug"] and "ultra" in h["slug"]), hits[0] if hits else None)
        if hit:
            sv = pick(jget(f"project/{hit['slug']}/version"))
            if sv:
                cfg["shader"]["slug"] = hit["slug"]
                shader = (sv, next((x for x in sv["files"] if x.get("primary")), sv["files"][0]), jget(f"project/{hit['slug']}"))
                report.append(f"OK   shader {hit['slug']} {sv['version_number']}")
        if not shader:
            report.append("SKIP shader: не знайдено")
    except Exception as e:  # noqa: BLE001
        report.append(f"SKIP shader: {e}")

    jar_name = os.path.basename(jar)
    files = []
    for c in chosen:
        f = c["f"]
        side = c["m"]["side"]
        files.append({
            "path": f"mods/{f['filename']}",
            "hashes": {"sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"]},
            "env": {"client": "required", "server": "required" if side == "both" else "unsupported"},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
    if shader:
        files.append({
            "path": f"shaderpacks/{shader[1]['filename']}",
            "hashes": {"sha1": shader[1]["hashes"]["sha1"], "sha512": shader[1]["hashes"]["sha512"]},
            "env": {"client": "optional", "server": "unsupported"},
            "downloads": [shader[1]["url"]],
            "fileSize": shader[1]["size"],
        })
    index = {
        "formatVersion": 1, "game": "minecraft", "versionId": VERSION, "name": "Левандівка: по той бік району",
        "summary": "Кооперативна пригода на 1–3 гравців (Fabric 1.20.1)",
        "files": files,
        "dependencies": {"minecraft": mc, "fabric-loader": cfg["fabric_loader"]},
    }

    # --- .mrpack (наш мод лежить в overrides, як окремий файл, а не вбудований у чужі)
    mrpack = os.path.join(out, f"Lewandivka-{VERSION}.mrpack")
    with zipfile.ZipFile(mrpack, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=2))
        z.write(jar, f"overrides/mods/{jar_name}")
        for root, _, names in os.walk(os.path.join(HERE, "presets", "performance")):
            for n in names:
                full = os.path.join(root, n)
                z.write(full, "overrides/" + os.path.relpath(full, os.path.join(HERE, "presets", "performance")))

    # --- завантаження для серверного та TLauncher-архівів
    dl = os.path.join(work, "dl")
    os.makedirs(dl)
    for c in chosen:
        with open(os.path.join(dl, c["f"]["filename"]), "wb") as fh:
            fh.write(get(c["f"]["url"]))
    problems = validate(chosen, dl, jar, mc)
    report.extend(problems)

    # --- TLauncher: наш jar + інсталятор, що завантажує решту модів з Modrinth на комп'ютері гравця.
    # (Не перепаковуємо чужі моди з обмежувальними ліцензіями — кожен качається з офіційного джерела.)
    tl = os.path.join(out, f"Lewandivka-TLauncher-{VERSION}.zip")
    ps1 = r"""$ErrorActionPreference = 'Stop'
$mc = Join-Path $env:APPDATA '.minecraft'
$mods = Join-Path $mc 'mods'
New-Item -ItemType Directory -Force $mods | Out-Null
Get-ChildItem $mods -Filter 'lewandivka*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force
$idx = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'modrinth.index.json') | ConvertFrom-Json
$keep = @($idx.files | ForEach-Object { Split-Path $_.path -Leaf })
Get-ChildItem $mods -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^(sodium|iris|indium|continuity|lambdynamiclights|visuality|sound-physics|presencefootsteps|modmenu|moreculling|immediatelyfast|particle-rain|particlerain|cloth-config|fabric-api|lithium|ferritecore)' -and $keep -notcontains $_.Name } | ForEach-Object { Write-Host "del  $($_.Name)"; Remove-Item $_.FullName -Force }
Copy-Item (Join-Path $PSScriptRoot 'mods\*.jar') $mods -Force
foreach ($f in $idx.files) {
  if ($f.env.client -eq 'unsupported') { continue }
  $dest = Join-Path $mc ($f.path -replace '/', '\')
  New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
  if (Test-Path $dest) { if ((Get-FileHash $dest -Algorithm SHA1).Hash -eq $f.hashes.sha1.ToUpper()) { Write-Host "ok   $($f.path)"; continue } }
  Write-Host "load $($f.path)"
  Invoke-WebRequest -Uri $f.downloads[0] -OutFile $dest -UseBasicParsing
  if ((Get-FileHash $dest -Algorithm SHA1).Hash -ne $f.hashes.sha1.ToUpper()) { Remove-Item $dest; throw "Hash mismatch: $($f.path)" }
}
Write-Host ''
Write-Host 'Gotovo! Zapusti profil Fabric 1.20.1 u TLauncher.'
"""
    bat = "@echo off\r\nchcp 65001 >nul\r\npowershell -NoProfile -ExecutionPolicy Bypass -File \"%~dp0install.ps1\"\r\npause\r\n"
    with zipfile.ZipFile(tl, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, f"mods/{jar_name}")
        z.writestr("modrinth.index.json", json.dumps(index, ensure_ascii=False, indent=2))
        z.writestr("install.ps1", "\ufeff" + ps1)
        z.writestr("Install.bat", bat)
        z.writestr("config/iris.properties", open(os.path.join(HERE, "presets", "performance", "config", "iris.properties")).read())
        z.writestr("README.txt",
                   "1. У TLauncher вибери версію Fabric 1.20.1 і один раз запусти її (щоб поставилась).\n"
                   "2. Розпакуй цей архів у будь-яку папку і двічі клацни Install.bat.\n"
                   "   Він завантажить Fabric API та інші моди з Modrinth у %appdata%\\.minecraft\\mods і скопіює Lewandivka.\n"
                   "3. Запусти профіль Fabric 1.20.1. Усі гравці мають зробити те саме.\n"
                   "Шейдер за замовчуванням ВИМКНЕНИЙ (пресет Performance). Для Cinematic: Параметри -> Відеоналаштування -> Шейдери.\n"
                   "Без інтернету або без Install.bat: поклади в mods lewandivka-*.jar та Fabric API (modrinth.com/mod/fabric-api).\n")

    # --- сервер: лише спільні моди + наш + лаунчер Fabric
    srv = os.path.join(out, f"Lewandivka-Server-{VERSION}.zip")
    launcher = None
    try:
        launcher = get(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{cfg['fabric_loader']}/{cfg['fabric_installer']}/server/jar")
    except Exception as e:  # noqa: BLE001
        report.append(f"WARN fabric server launcher not downloaded: {e}")
    with zipfile.ZipFile(srv, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, f"mods/{jar_name}")
        for c in chosen:
            if c["m"]["side"] == "both":
                z.write(os.path.join(dl, c["f"]["filename"]), f"mods/{c['f']['filename']}")
        if launcher:
            z.writestr("fabric-server-launch.jar", launcher)
        z.writestr("start.sh", "#!/bin/sh\necho eula=true > eula.txt\njava -Xmx4G -jar fabric-server-launch.jar nogui\n")
        z.writestr("start.bat", "@echo off\necho eula=true> eula.txt\njava -Xmx4G -jar fabric-server-launch.jar nogui\npause\n")
        z.writestr("README-SERVER.txt",
                   "Серверний пакет Левандівки (Fabric 1.20.1, Java 17+).\n"
                   "Клієнтські візуальні моди (Sodium, Iris, тощо) сюди НЕ входять — вони лише в клієнтському пакеті.\n"
                   "Запуск: start.sh / start.bat. Запускаючи, ви приймаєте Minecraft EULA (https://aka.ms/MinecraftEULA).\n")

    # --- THIRD_PARTY.md зі справжніх метаданих Modrinth
    lines = ["# Сторонні компоненти\n",
             "Згенеровано `tools/build_pack.py` з метаданих Modrinth. Жоден сторонній код не вбудований у `lewandivka-*.jar`.\n",
             "| Проєкт | Сторінка | Ліцензія | Версія | Клієнт/сервер | Навіщо |", "|---|---|---|---|---|---|"]
    for c in chosen:
        p, m = c["p"], c["m"]
        lic = (p.get("license") or {}).get("id", "?")
        side = {"both": "обидва", "client": "клієнт"}[m["side"]]
        lines.append(f"| {p['title']} | https://modrinth.com/mod/{m['slug']} | {lic} | {c['v']['version_number']} | {side} | {m['purpose']} |")
    if shader:
        lines.append(f"| {shader[2]['title']} | https://modrinth.com/shader/{cfg['shader']['slug']} | {(shader[2].get('license') or {}).get('id', '?')} | "
                     f"{shader[0]['version_number']} | клієнт (необов'язково) | {cfg['shader']['purpose']} |")
    open(os.path.join(out, "THIRD_PARTY.md"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
    open(os.path.join(out, "pack_report.txt"), "w", encoding="utf-8").write("\n".join(report) + "\n")
    shutil.rmtree(work, ignore_errors=True)
    print("\n".join(report))
    if any(r.startswith("FAIL") for r in report):
        sys.exit(2)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
