#!/usr/bin/env python3
"""Обновление ресурс-пака для QWHatCase / Minecraft 1.21.11.

Использование: python3 tools/update_pack.py <распакованный исходный пак> <папка результата> [--zip файл.zip]

Что делает (исходная папка не изменяется):
  1. Переносит из вложенной папки hoplite/ (клиент её НЕ читает: это не оверлей) все файлы, на которые
     ссылается основной пак, но которых в нём нет: звуки, текстуры, модели (рекурсивно по parent/textures).
  2. Исправляет пространство имён в sounds.json: если файл лежит по тому же пути в пространстве minecraft,
     ссылка переписывается на minecraft:<путь>.
  3. Удаляет hoplite/ (после переноса нужного) — минус ~31 МБ при скачивании.
  4. Обновляет описание pack.mcmeta, форматы оставляет (1.21.11 = 75 входит в диапазон).
  5. Печатает отчёт: что перенесено, что исправлено, что осталось неисправимым.
Шляпы, модели и пути не меняются.
"""
import glob
import json
import os
import shutil
import sys
import struct
import zipfile
import zlib


def transparent_png(path, size=16):
    """Полностью прозрачная текстура (для моделей «invisible_item»)."""
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"\x00\x00\x00\x00" * size for _ in range(size))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def split(ref, default_ns="minecraft"):
    return ref.split(":", 1) if ":" in ref else (default_ns, ref)


def main():
    src, out = sys.argv[1], sys.argv[2]
    zip_path = sys.argv[sys.argv.index("--zip") + 1] if "--zip" in sys.argv else None
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(src, out)
    hoplite_roots = []
    if os.path.isdir(os.path.join(out, "hoplite")):
        h = os.path.join(out, "hoplite")
        # Базовая папка hoplite и её оверлеи для новых версий (более новые — приоритетнее).
        hoplite_roots = [os.path.join(h, d) for d in ("overlay_1_21_5", "overlay_1_21_4", "overlay_1_21_2") if os.path.isdir(os.path.join(h, d))] + [h]

    report = {"copied": [], "sound_ns_fixed": [], "unresolved": []}

    def main_path(kind, ref, ext, default_ns="minecraft"):
        ns, path = split(ref, default_ns)
        return os.path.join(out, "assets", ns, kind, path + ext)

    def pull(kind, ref, ext, default_ns="minecraft"):
        """Гарантирует наличие файла в основном паке; при необходимости копирует из hoplite."""
        target = main_path(kind, ref, ext, default_ns)
        if os.path.isfile(target):
            return True
        ns, path = split(ref, default_ns)
        rel = os.path.join("assets", ns, kind, path + ext)
        for root in hoplite_roots:
            candidate = os.path.join(root, rel)
            if os.path.isfile(candidate):
                os.makedirs(os.path.dirname(target), exist_ok=True)
                shutil.copy2(candidate, target)
                report["copied"].append(rel)
                if ext == ".png" and os.path.isfile(candidate + ".mcmeta"):
                    shutil.copy2(candidate + ".mcmeta", target + ".mcmeta")
                    report["copied"].append(rel + ".mcmeta")
                if kind == "models":
                    check_model(target)
                return True
        return False

    def check_model(file):
        try:
            model = json.load(open(file, encoding="utf-8"))
        except ValueError:
            return
        parent = model.get("parent")
        if parent and not parent.startswith(("builtin/", "minecraft:builtin/")):
            ns, path = split(parent)
            if ns != "minecraft" and not pull("models", parent, ".json"):
                report["unresolved"].append(f"{os.path.relpath(file, out)}: нет родительской модели {parent}")
        for tex in (model.get("textures") or {}).values():
            if isinstance(tex, str) and not tex.startswith("#"):
                ns, path = split(tex)
                if ns != "minecraft" and not pull("textures", tex, ".png"):
                    if "invisible" in path.split("/")[-1]:
                        transparent_png(main_path("textures", tex, ".png"))
                        report["copied"].append(f"(создана прозрачная текстура) {tex}")
                    else:
                        report["unresolved"].append(f"{os.path.relpath(file, out)}: нет текстуры {tex}")

    def walk_models(node, acc):
        if isinstance(node, dict):
            if node.get("type") in ("model", "minecraft:model") and isinstance(node.get("model"), str):
                acc.append(node["model"])
            for value in node.values():
                walk_models(value, acc)
        elif isinstance(node, list):
            for value in node:
                walk_models(value, acc)

    # 1. Определения предметов → модели.
    for items_file in sorted(glob.glob(os.path.join(out, "assets", "*", "items", "*.json"))):
        refs = []
        walk_models(json.load(open(items_file, encoding="utf-8")), refs)
        for ref in sorted(set(refs)):
            ns, path = split(ref)
            if ns == "minecraft" and path.split("/")[0] in ("item", "block"):
                if not os.path.isfile(main_path("models", ref, ".json")):
                    continue  # ванильная модель
            if not pull("models", ref, ".json"):
                report["unresolved"].append(f"{os.path.relpath(items_file, out)}: нет модели {ref}")
    # 2. Все модели → parent/текстуры.
    for model_file in sorted(glob.glob(os.path.join(out, "assets", "*", "models", "**", "*.json"), recursive=True)):
        check_model(model_file)

    # 3. Звуки.
    for sounds_file in sorted(glob.glob(os.path.join(out, "assets", "*", "sounds.json"))):
        ns = sounds_file.split(os.sep)[-2]
        data = json.load(open(sounds_file, encoding="utf-8"))
        changed = False
        for event, entry in data.items():
            sounds = entry.get("sounds", [])
            for i, sound in enumerate(sounds):
                if isinstance(sound, dict) and sound.get("type") == "event":
                    continue
                name = sound if isinstance(sound, str) else sound["name"]
                if pull("sounds", name, ".ogg", ns):
                    continue
                s_ns, s_path = split(name, ns)
                fixed = None
                if s_ns != "minecraft" and os.path.isfile(os.path.join(out, "assets", "minecraft", "sounds", s_path + ".ogg")):
                    fixed = "minecraft:" + s_path
                if not fixed:
                    # Опечатка в папке: единственный файл с тем же именем в пространстве minecraft.
                    base = os.path.basename(s_path) + ".ogg"
                    hits = [h for h in glob.glob(os.path.join(out, "assets", "minecraft", "sounds", "**", base), recursive=True)]
                    if len(hits) == 1:
                        rel = os.path.relpath(hits[0], os.path.join(out, "assets", "minecraft", "sounds"))[:-4].replace(os.sep, "/")
                        if rel.split("/")[0] == s_path.split("/")[0]:
                            fixed = "minecraft:" + rel
                if fixed:
                    if isinstance(sound, str):
                        sounds[i] = fixed
                    else:
                        sound["name"] = fixed
                    changed = True
                    report["sound_ns_fixed"].append(f"{ns}:{event}: {name} -> {fixed}")
                else:
                    report["unresolved"].append(f"assets/{ns}/sounds.json: событие {event}: нет файла звука {name}")
        if changed:
            with open(sounds_file, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)

    # 4. Удаляем hoplite/ — клиент её не читал.
    if os.path.isdir(os.path.join(out, "hoplite")):
        shutil.rmtree(os.path.join(out, "hoplite"))

    # 5. pack.mcmeta
    meta_file = os.path.join(out, "pack.mcmeta")
    meta = json.load(open(meta_file, encoding="utf-8"))
    meta["pack"]["description"] = "§f§lBy  _DEMORA_ (universal) §7· QWHatCase 1.21.11"
    with open(meta_file, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False)

    print(f"Перенесено из hoplite/: {len(report['copied'])}")
    for line in report["copied"]:
        print("  +", line)
    print(f"Исправлено ссылок на звуки: {len(report['sound_ns_fixed'])}")
    for line in report["sound_ns_fixed"]:
        print("  ~", line)
    unresolved = sorted(set(report["unresolved"]))
    print(f"Осталось неисправимым (файлов нет нигде в паке): {len(unresolved)}")
    for line in unresolved:
        print("  !", line)

    if zip_path:
        if os.path.exists(zip_path):
            os.remove(zip_path)
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for dirpath, dirnames, filenames in os.walk(out):
                dirnames.sort()
                for name in sorted(filenames):
                    full = os.path.join(dirpath, name)
                    info = zipfile.ZipInfo(os.path.relpath(full, out).replace(os.sep, "/"), (2026, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    with open(full, "rb") as fh:
                        z.writestr(info, fh.read(), compresslevel=9)
        print("Архив:", zip_path, os.path.getsize(zip_path), "байт")


if __name__ == "__main__":
    main()
