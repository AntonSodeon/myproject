#!/usr/bin/env python3
"""Generates src/main/resources/hats.yml and docs/hat-mapping.md from the resource pack.

Usage: python3 tools/generate_catalog.py <unpacked resource pack dir>

Source of truth for the old plugin (PTrap 1.1.19): hats are CARVED_PUMPKIN items with
custom_model_data 90..370, old id = "hat_<custom_model_data>".
"""
import json
import os
import sys

FIRST, LAST = 90, 370

NAMES = {
    "halo": "Нимб", "chainsaw_head": "Голова-бензопила", "propeller_hat": "Кепка с пропеллером",
    "spartan_helmet": "Спартанский шлем", "gingerbread_mask": "Пряничная маска", "krampus_head": "Голова Крампуса",
    "cyberpunk_hat": "Киберпанк-шляпа", "reindeer_antlers": "Оленьи рога", "santa_hat": "Шапка Санты",
    "snowman_head": "Голова снеговика", "jester_hat": "Колпак шута", "elf_hat": "Шапка эльфа",
    "frostbite_helmet": "Морозный шлем", "alien_head_muncher": "Пришелец-обжора", "drink_hat_number_1": "Шляпа с напитками",
    "taco_hat": "Шляпа-тако", "top_hat": "Цилиндр", "pufferfish_hat": "Шляпа-иглобрюх",
    "cupid_arrow_headband": "Ободок со стрелой Купидона", "flamingo_hat": "Шляпа-фламинго", "flower_crown": "Венок из цветов",
    "heart_glasses": "Очки-сердечки", "heart_headband": "Ободок с сердечками", "valentines_bear_hat": "Валентиновый мишка",
    "sunflower_hat": "Шляпа-подсолнух", "cat_ears": "Кошачьи ушки", "valentines_knight": "Рыцарь Валентина",
    "valentines_warrior": "Воин Валентина", "sculk_monster_mouth": "Пасть скалк-монстра", "sculk_creeper_hat": "Скалк-крипер",
    "warden_horns": "Рога Хранителя", "baby_warden_hat": "Маленький Хранитель", "giant_warden_head": "Голова гигантского Хранителя",
    "warden_bucket_hat": "Панама Хранителя", "warden_crown": "Корона Хранителя", "warden_knight_helmet": "Шлем рыцаря Хранителя",
    "warden_warriors_helmet": "Шлем воина Хранителя", "warden_wizard_hat": "Шляпа мага Хранителя", "warden_sculk_crown": "Скалк-корона Хранителя",
    "clam_crown": "Корона-ракушка", "serpant_helmet": "Шлем морского змея", "scuba_helmet": "Шлем аквалангиста",
    "octopus_hat": "Шляпа-осьминог", "hotdog_hat": "Шляпа-хот-дог", "tv_head": "Голова-телевизор",
    "graduation_cap": "Шапочка выпускника", "kitty_headset": "Наушники с ушками", "plunger_hat": "Шляпа-вантуз",
    "axolotl_cap": "Кепка-аксолотль", "box_hat": "Шляпа-коробка", "qa_wrench_hat": "Шляпа с гаечным ключом",
    "shark_fin": "Акулий плавник", "translator_globe": "Глобус-переводчик", "ram_horns": "Бараньи рога",
    "dragon_skull": "Драконий череп", "banana_hat": "Шляпа-банан", "guardian_hat": "Шляпа-страж",
    "jellyfish_hat": "Шляпа-медуза", "valkyrie_helmet": "Шлем валькирии", "nemo": "Рыбка-клоун",
    "angler_hat": "Шляпа удильщика", "fisherman_hat": "Шляпа рыбака", "piglin_hat": "Шляпа пиглина",
    "red_devil_horns": "Красные рожки", "flaming_spartan_helmet": "Пылающий спартанский шлем", "flaming_skull": "Пылающий череп",
    "lava_hound": "Лавовая гончая", "lava_witch_hat": "Шляпа лавовой ведьмы", "magma_cowboy_hat": "Магмовая ковбойская шляпа",
    "nether_mushroom_hat": "Незерский гриб", "gerald_hat": "Шляпа Джеральда", "shulker_hat": "Шляпа-шалкер",
    "turkey_hat": "Шляпа-индейка", "brain_jar": "Мозг в банке", "breather_mask": "Дыхательная маска",
    "cookie_hat": "Шляпа-печенье", "corrupted_enderman_skull": "Порченый череп эндермена", "ender_miner_hat": "Каска эндер-шахтёра",
    "enderman_top_hat": "Цилиндр эндермена", "gas_mask": "Противогаз", "night_vision_goggles": "Очки ночного видения",
    "pylon_hat": "Шляпа-конус", "sunflower_crown": "Корона из подсолнухов", "toothful_hat": "Зубастая шляпа",
    "unicorn_hat": "Шляпа-единорог", "cyber_dragon_hat": "Кибердракон", "elite_warrior_helmet": "Шлем элитного воина",
    "crocodile_hat": "Шляпа-крокодил", "football_helmet": "Шлем регбиста", "nemean_mane": "Грива Немейского льва",
    "toque": "Вязаная шапка", "artemis_crown": "Корона Артемиды", "corinthian_helm": "Коринфский шлем",
    "golden_headpiece": "Золотой венец", "laurel_wreath": "Лавровый венок", "zeus_bolt": "Молния Зевса",
    "aviator_cap": "Шлем авиатора", "dingle_mask": "Маска Дингла", "steampunk_hat": "Стимпанк-шляпа",
    "wolf_fur_hat": "Волчья шапка", "sailor_cap": "Бескозырка", "rainbow_earmuffs": "Радужные наушники",
    "viking_beard": "Борода викинга", "medusa_hat": "Шляпа Медузы", "ash_hat": "Кепка тренера",
    "cute_bow": "Милый бантик", "twitch_cap": "Кепка стримера", "beanie": "Бини",
    "egg_hat": "Шляпа-яйцо", "hat_hat": "Шляпа на шляпе", "pine_cap": "Еловая шапка",
    "pot_hat": "Кастрюля", "straw_hat": "Соломенная шляпа", "bat_hat": "Шляпа-летучая мышь",
    "axe_hat": "Топор в голове", "eyeball_hat": "Шляпа-глаз", "mummy_mask": "Маска мумии",
    "pumpkin_headband": "Тыквенный ободок", "scream_mask": "Маска «Крик»", "spider_hat": "Шляпа-паук",
    "1year_cake_hat": "Праздничный торт", "bat_wings_hat": "Крылья летучей мыши", "camera_hat": "Шляпа-камера",
    "vr_headset": "VR-шлем", "snowstorm_helm": "Шлем снежной бури", "slime_control": "Слизневый контроль",
    "silly_glasses": "Смешные очки", "pirate_polly": "Пиратский попугай", "piranha_troubles": "Пиранья на голове",
    "idiot_sandwich": "Сэндвич", "ice_crown": "Ледяная корона", "heart_eyes_emoji": "Влюблённый смайлик",
    "gnome_rider": "Гном-наездник", "cool_can": "Крутая банка", "baby_skeleton_rider": "Скелетик-наездник",
    "balance_act": "Эквилибрист", "bee_goggles": "Пчелиные очки", "brick_cap": "Кирпичная кепка",
    "booksmart_hat": "Шляпа книжного червя", "cactus_cap": "Кактусовая кепка", "cool_glasses": "Крутые очки",
    "cornucopia_hat": "Рог изобилия", "creeper_fossil": "Окаменелый крипер", "fishbowl": "Аквариум",
    "gearhead": "Шестерёночная голова",
}

CATEGORIES = {
    "winter": "gingerbread_mask krampus_head reindeer_antlers santa_hat snowman_head elf_hat frostbite_helmet toque wolf_fur_hat snowstorm_helm ice_crown rainbow_earmuffs beanie pine_cap jester_hat",
    "valentine": "cupid_arrow_headband flower_crown heart_glasses heart_headband valentines_bear_hat valentines_knight valentines_warrior heart_eyes_emoji cute_bow",
    "sculk": "sculk_monster_mouth sculk_creeper_hat warden_horns baby_warden_hat giant_warden_head warden_bucket_hat warden_crown warden_knight_helmet warden_warriors_helmet warden_wizard_hat warden_sculk_crown",
    "ocean": "clam_crown serpant_helmet scuba_helmet octopus_hat pufferfish_hat shark_fin guardian_hat jellyfish_hat nemo angler_hat fisherman_hat sailor_cap pirate_polly piranha_troubles fishbowl axolotl_cap crocodile_hat",
    "nether": "piglin_hat red_devil_horns flaming_spartan_helmet flaming_skull lava_hound lava_witch_hat magma_cowboy_hat nether_mushroom_hat",
    "end": "shulker_hat corrupted_enderman_skull ender_miner_hat enderman_top_hat",
    "ancient": "spartan_helmet halo nemean_mane artemis_crown corinthian_helm golden_headpiece laurel_wreath zeus_bolt medusa_hat valkyrie_helmet viking_beard elite_warrior_helmet",
    "halloween": "bat_hat axe_hat eyeball_hat mummy_mask pumpkin_headband scream_mask spider_hat bat_wings_hat",
    "food": "taco_hat drink_hat_number_1 hotdog_hat banana_hat cookie_hat turkey_hat egg_hat idiot_sandwich cool_can cornucopia_hat pot_hat",
    "tech": "chainsaw_head cyberpunk_hat tv_head kitty_headset translator_globe qa_wrench_hat brain_jar breather_mask gas_mask night_vision_goggles pylon_hat cyber_dragon_hat camera_hat vr_headset steampunk_hat gearhead aviator_cap alien_head_muncher",
    "animals": "flamingo_hat cat_ears ram_horns unicorn_hat bee_goggles dragon_skull toothful_hat bat_hat",
}
CATEGORY_OF = {}
for cat, names in CATEGORIES.items():
    for n in names.split():
        CATEGORY_OF.setdefault(n, cat)

# 0 = базовая, 1 = улучшенная, 2 = премиальная
TIER2 = set("halo giant_warden_head warden_sculk_crown cyber_dragon_hat zeus_bolt ice_crown golden_headpiece flaming_skull warden_crown nemean_mane dragon_skull".split())
TIER1 = set(("sculk_monster_mouth sculk_creeper_hat warden_horns baby_warden_hat warden_bucket_hat warden_knight_helmet "
             "warden_warriors_helmet warden_wizard_hat spartan_helmet artemis_crown corinthian_helm laurel_wreath medusa_hat "
             "valkyrie_helmet viking_beard flaming_spartan_helmet lava_hound lava_witch_hat magma_cowboy_hat shulker_hat "
             "corrupted_enderman_skull enderman_top_hat cyberpunk_hat chainsaw_head krampus_head frostbite_helmet snowstorm_helm "
             "clam_crown serpant_helmet guardian_hat unicorn_hat valentines_knight valentines_warrior brain_jar gnome_rider "
             "baby_skeleton_rider creeper_fossil vr_headset steampunk_hat gearhead alien_head_muncher twitch_cap").split())
RARITY = {("normal", 0): "common", ("normal", 1): "uncommon", ("normal", 2): "rare",
          ("shiny", 0): "rare", ("shiny", 1): "epic", ("shiny", 2): "legendary"}
ELITE = {"1": "uncommon", "2": "uncommon", "3": "rare", "4": "rare", "5": "epic", "6": "legendary"}


def main():
    pack = sys.argv[1]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    with open(os.path.join(pack, "assets/minecraft/items/carved_pumpkin.json"), encoding="utf-8") as f:
        entries = {e["threshold"]: e["model"]["model"] for e in json.load(f)["model"]["entries"]}

    hats = []
    for cmd in range(FIRST, LAST + 1):
        model = entries[cmd]
        rel = model.split("item/", 1)[1]
        parts = rel.split("/")
        base = parts[1] if parts[0] == "wearable" else parts[-1]
        variant = parts[2] if parts[0] == "wearable" and len(parts) > 2 else ""
        name = NAMES[base]
        lore = []
        if variant == "shiny":
            hat_id = base + "_shiny"
            display = name + " &f✦"
            rarity = RARITY[("shiny", 2 if base in TIER2 else 1 if base in TIER1 else 0)]
            lore.append("&eСияющая версия")
        elif variant.isdigit():
            hat_id = f"{base}_{variant}"
            display = f"{name} {['I','II','III','IV','V','VI'][int(variant) - 1]}"
            rarity = ELITE[variant]
        else:
            hat_id = base
            display = name
            rarity = RARITY[("normal", 2 if base in TIER2 else 1 if base in TIER1 else 0)]
        hats.append({
            "id": hat_id, "name": display, "lore": lore, "rarity": rarity,
            "category": CATEGORY_OF.get(base, "misc"), "cmd": cmd, "model": model,
            "legacy": f"hat_{cmd}",
        })

    ids = [h["id"] for h in hats]
    assert len(ids) == len(set(ids)), "duplicate ids"

    out = []
    out.append("# Каталог шляп QWHatCase.")
    out.append("# Сгенерирован tools/generate_catalog.py из ресурс-пака (assets/minecraft/items/carved_pumpkin.json).")
    out.append("# Ключ секции = постоянный ID шляпы. Его НЕЛЬЗЯ менять после запуска: по нему хранятся коллекции.")
    out.append("# Название (name), описание (lore), редкость и категорию можно менять свободно.")
    out.append("#")
    out.append("# Поля шляпы:")
    out.append("#   name               отображаемое название (&-цвета, &#RRGGBB)")
    out.append("#   lore               описание (список строк)")
    out.append("#   rarity             ID редкости из config.yml -> rarities")
    out.append("#   category           ID категории из блока categories ниже")
    out.append("#   custom-model-data  номер модели в ресурс-паке (range_dispatch custom_model_data)")
    out.append("#   model              путь к модели в паке (справочно; плагин его не использует)")
    out.append("#   material/item-model  базовый предмет и item_model (по умолчанию из defaults)")
    out.append("#   available          false = шляпа не выпадает из кейсов и не продаётся, но остаётся у владельцев")
    out.append("#   compensation       жетоны за дубликат (если не задано — берётся из редкости)")
    out.append("#   legacy-ids         идентификаторы из старого плагина PTrap (для миграции)")
    out.append("")
    out.append("defaults:")
    out.append("  # Базовый предмет. PAPER не является блоком и не даёт защиты от эндерменов (в отличие от CARVED_PUMPKIN).")
    out.append("  material: PAPER")
    out.append("  # Определение предмета в паке, в котором лежит range_dispatch по custom_model_data.")
    out.append("  item-model: minecraft:carved_pumpkin")
    out.append("  lore:")
    out.append("    - '&7Кастомная шляпа'")
    out.append("")
    out.append("categories:")
    cat_names = {"winter": "&bЗима", "valentine": "&dДень святого Валентина", "sculk": "&3Скалк и Хранитель",
                 "ocean": "&9Океан", "nether": "&cНезер", "end": "&5Энд", "ancient": "&6Античность",
                 "halloween": "&6Хэллоуин", "food": "&eЕда", "tech": "&7Техника", "animals": "&aЖивотные", "misc": "&fРазное"}
    cat_icons = {"winter": "SNOWBALL", "valentine": "POPPY", "sculk": "SCULK", "ocean": "PRISMARINE_SHARD",
                 "nether": "NETHERRACK", "end": "END_STONE", "ancient": "GOLD_INGOT", "halloween": "JACK_O_LANTERN",
                 "food": "COOKIE", "tech": "REDSTONE", "animals": "BONE", "misc": "PAPER"}
    for c in cat_names:
        out.append(f"  {c}:")
        out.append(f"    name: '{cat_names[c]}'")
        out.append(f"    icon: {cat_icons[c]}")
    out.append("")
    out.append("hats:")
    for h in hats:
        out.append(f"  {h['id']}:")
        out.append(f"    name: '{h['name']}'")
        if h["lore"]:
            out.append("    lore:")
            out.append("      - '&7Кастомная шляпа'")
            for line in h["lore"]:
                out.append(f"      - '{line}'")
        out.append(f"    rarity: {h['rarity']}")
        out.append(f"    category: {h['category']}")
        out.append(f"    custom-model-data: {h['cmd']}")
        out.append(f"    model: '{h['model']}'")
        out.append(f"    legacy-ids: [{h['legacy']}]")
    with open(os.path.join(root, "src/main/resources/hats.yml"), "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")

    md = ["# Таблица соответствия шляп", "",
          "Старый плагин PTrap 1.1.19 хранил шляпу как `CARVED_PUMPKIN` с `custom_model_data = N` и ID `hat_N` (N = 90…370).",
          "Новый плагин хранит постоянный ID, не зависящий от названия. Модель в паке не менялась.", "",
          f"Всего шляп: **{len(hats)}**.", "",
          "| Старый ID | Новый ID | custom_model_data | Модель в ресурс-паке | Название | Редкость | Категория |",
          "|---|---|---|---|---|---|---|"]
    for h in hats:
        md.append(f"| `{h['legacy']}` | `{h['id']}` | {h['cmd']} | `{h['model']}` | {h['name'].replace(' &f✦', ' ✦')} | {h['rarity']} | {h['category']} |")
    with open(os.path.join(root, "docs/hat-mapping.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")

    from collections import Counter
    print(len(hats), Counter(h["rarity"] for h in hats), Counter(h["category"] for h in hats))


if __name__ == "__main__":
    main()
