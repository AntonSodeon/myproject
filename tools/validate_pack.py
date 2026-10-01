#!/usr/bin/env python3
"""Проверка ресурс-пака для QWHatCase.

Использование: python3 tools/validate_pack.py <распакованный пак> [hats.yml]

Проверяет для каждой шляпы из hats.yml:
  * есть запись custom_model_data в assets/minecraft/items/carved_pumpkin.json;
  * модель и вся цепочка parent существуют, JSON корректен;
  * все текстуры не из пространства minecraft существуют;
  * у модели есть трансформация display.head (иначе положение на голове надо проверить визуально);
  * две шляпы не ссылаются на одну модель.
Также выводит pack.mcmeta и проверяет, что формат 1.21.11 (75) входит в поддерживаемый диапазон.
"""
import json
import os
import sys

import yaml


def main():
    root = sys.argv[1]
    hats_file = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), "../src/main/resources/hats.yml")
    hats = yaml.safe_load(open(hats_file, encoding="utf-8"))["hats"]

    meta = json.load(open(os.path.join(root, "pack.mcmeta"), encoding="utf-8"))
    pack = meta["pack"]
    lo = pack.get("min_format", pack.get("pack_format"))
    hi = pack.get("max_format", pack.get("pack_format"))
    lo = lo[0] if isinstance(lo, list) else lo
    hi = hi[0] if isinstance(hi, list) else hi
    print(f"pack.mcmeta: pack_format={pack.get('pack_format')} min={lo} max={hi}")
    print("  1.21.11 (формат 75):", "поддерживается" if lo <= 75 <= hi else "НЕ поддерживается")

    def model_file(ref):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        return os.path.join(root, "assets", ns, "models", path + ".json")

    def texture_file(ref):
        ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
        return ns, os.path.join(root, "assets", ns, "textures", path + ".png")

    items = json.load(open(os.path.join(root, "assets/minecraft/items/carved_pumpkin.json"), encoding="utf-8"))
    entries = {e["threshold"]: e["model"]["model"] for e in items["model"]["entries"]}

    errors, warnings, used = [], [], {}
    for hat_id, hat in hats.items():
        cmd = hat["custom-model-data"]
        ref = entries.get(cmd)
        if ref is None:
            errors.append(f"{hat_id}: нет записи custom_model_data={cmd}")
            continue
        used.setdefault(ref, []).append(hat_id)
        has_head = False
        current = ref
        while current:
            path = model_file(current)
            if not os.path.isfile(path):
                if current.split(":")[-1].startswith(("block/", "item/", "builtin/")) and (":" not in current or current.startswith("minecraft:")):
                    break  # ванильная модель
                errors.append(f"{hat_id}: нет модели {current}")
                break
            try:
                model = json.load(open(path, encoding="utf-8"))
            except ValueError as e:
                errors.append(f"{hat_id}: битый JSON {path}: {e}")
                break
            for tex in (model.get("textures") or {}).values():
                if tex.startswith("#"):
                    continue
                ns, tf = texture_file(tex)
                if ns != "minecraft" and not os.path.isfile(tf):
                    errors.append(f"{hat_id}: нет текстуры {tex}")
            if "head" in (model.get("display") or {}):
                has_head = True
            current = model.get("parent")
        if not has_head:
            warnings.append(f"{hat_id} (cmd {cmd}, {ref}): нет display.head — выводится как блок на голове (как тыква)")
    for ref, ids in used.items():
        if len(ids) > 1:
            errors.append(f"одна модель {ref} у нескольких шляп: {ids}")

    print(f"Шляп проверено: {len(hats)}; ошибок: {len(errors)}; предупреждений: {len(warnings)}")
    for e in errors:
        print("  ОШИБКА:", e)
    for w in warnings:
        print("  ВНИМАНИЕ:", w)
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
