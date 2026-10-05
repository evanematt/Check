"""Збирає модпак із Modrinth: .mrpack, серверний архів, архів для TLauncher, THIRD_PARTY.md.

Запуск (потрібен інтернет, є в GitHub Actions):
  python3 tools/build_pack.py <шлях до lewandivka-*.jar> <каталог виводу>

Принципи: не вбудовуємо чужі моди в наш jar; кожен мод — окремий файл із Modrinth;
несуттєвий мод, що не знайшовся для 1.20.1 або не пройшов перевірку сумісності, виключається (звіт у pack_report.txt).
"""
import hashlib
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

    # Лише клієнтський шейдер.
    shader = None
    try:
        sv = pick(jget(f"project/{cfg['shader']['slug']}/version"))
        if sv:
            shader = (sv, next((x for x in sv["files"] if x.get("primary")), sv["files"][0]), jget(f"project/{cfg['shader']['slug']}"))
            report.append(f"OK   shader {sv['version_number']}")
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
        dest = os.path.join(dl, c["f"]["filename"])
        with open(dest, "wb") as fh:
            fh.write(get(c["f"]["url"]))
    shader_path = None
    if shader:
        shader_path = os.path.join(dl, shader[1]["filename"])
        with open(shader_path, "wb") as fh:
            fh.write(get(shader[1]["url"]))

    # --- TLauncher: готова папка mods (розпакувати в .minecraft)
    tl = os.path.join(out, f"Lewandivka-TLauncher-{VERSION}.zip")
    with zipfile.ZipFile(tl, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, f"mods/{jar_name}")
        for c in chosen:
            z.write(os.path.join(dl, c["f"]["filename"]), f"mods/{c['f']['filename']}")
        if shader_path:
            z.write(shader_path, f"shaderpacks/{os.path.basename(shader_path)}")
        z.writestr("config/iris.properties", open(os.path.join(HERE, "presets", "performance", "config", "iris.properties")).read())
        z.writestr("ПРОЧИТАЙ.txt",
                   "1. Встанови Fabric 1.20.1 у TLauncher (версія Fabric 1.20.1).\n"
                   "2. Видали зі старої папки mods усе, що там було (особливо Forge-моди та старий lewandivka).\n"
                   "3. Розпакуй цей архів у %appdata%\\.minecraft (папка mods має злитися).\n"
                   "4. Запусти профіль Fabric 1.20.1.\n"
                   "Шейдер за замовчуванням ВИМКНЕНИЙ (пресет Performance). Для Cinematic: Параметри -> Відеоналаштування -> Шейдери -> MakeUp UltraFast.\n")

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


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
