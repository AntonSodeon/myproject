# Изменения ресурс-пака: demoraRPuniversal v6.1 → v6.2 (QWHatCase)

Файл: `demoraRPuniversal_v6.2_QWHatCase.zip` · размер 13.1 МБ (было 17,5 МБ) · SHA-1 `fce683382eedc5ed62883f04b2c7e1fe88bcae91`

Пак собирается скриптом `tools/update_pack.py` из исходного `demoraRPuniversal_v6.1.zip` — изменения воспроизводимы.
**Модели, текстуры и пути шляп не менялись**: все 281 шляпа остаются на `assets/minecraft/items/carved_pumpkin.json`
с теми же номерами `custom_model_data` 90…370 (проверка `tools/validate_pack.py`: 0 ошибок).

## Что исправлено

1. **Файлы из мёртвой папки `hoplite/` перенесены в основной пак (36).** `hoplite/` — вложенный пак со своим
   `pack.mcmeta`; клиент Minecraft её не читает (это не оверлей). При этом основной пак ссылался на файлы из неё,
   поэтому у игроков не было звуков кейсов (`custom.mystery_crate.open/scroll/unlock/unlock.mythical`), звуков
   жетонов/монет (`custom.currency.*`), булавы, ветряного заряда, косметики и части текстур/моделей.
2. **Исправлены ссылки на звуки (72).** В `littleroom_yeti` и `emcromoknight*` звуки указывали на своё
   пространство имён, а файлы лежат в `minecraft` — звуки не проигрывались. Плюс одна опечатка в пути
   (`fallenreaper` → `fallenhero/attack4reaper1`).
3. **Создана прозрачная текстура** `civilization:item/ui/invisible_item` (модель «невидимого предмета» ссылалась на
   несуществующую текстуру и показывала фиолетово-чёрный квадрат).
4. **Удалена папка `hoplite/`** (~31 МБ) — после переноса нужного она не используется. Пак стал легче на ~4 МБ в архиве.
5. `pack.mcmeta`: описание `By _DEMORA_ (universal) · QWHatCase 1.21.11`; форматы прежние
   (min 32 … max 999, 1.21.11 = 75 поддерживается), оверлеи `bettermodel_*` без изменений.

Звуки кейсов QWHatCase в `cases.yml` и `config.yml` теперь используют звуки пака:
прокрутка — `custom.mystery_crate.scroll`, выигрыш — `custom.mystery_crate.unlock`,
легендарная — `custom.mystery_crate.unlock.mythical`, дубликат — `custom.currency.tokens`.

## Осталось как было (файлов нет ни в паке, ни в `hoplite/` — нужны исходники автора пака)

* assets/civilization/models/item/cosmetics/models/mahoujin.json: нет текстуры mahoutsukai:block/mahoujin
* assets/civilization/models/item/tools/penis.json: нет текстуры civilization:item/tools/textureadsd
* assets/civilization/models/item/tools/penis.json: нет текстуры civilization:item/tools/texturedsa
* assets/civilization/models/item/tools/penis.json: нет текстуры civilization:item/tools/texturesd
* assets/emcromoknights/sounds.json: событие custom.cromoknights.slashhita: нет файла звука custom/mobs/fallenhero/slashhit3
* assets/emcromoknights/sounds.json: событие custom.pepperknights.ost: нет файла звука custom/mobs/cromoknightboss/pprost
* assets/littleroom_yeti/models/yeti_hat.json: нет текстуры modelengine:entity/lr_yeti
* assets/minecraft/items/diamond_sword.json: нет модели civilization:item/tools/ice_haos
* assets/minecraft/items/stone_axe.json: нет модели civilization:item/tools/mjolnir/shattered

Эти предметы не относятся к шляпам и плагину QWHatCase.

## Перенесённые файлы

* `assets/civilization/models/item/ui/invisible_item.json`
* `(создана прозрачная текстура) civilization:item/ui/invisible_item`
* `assets/civilization/models/item/gameplay/fountain_of_youth/deactivated.json`
* `assets/civilization/textures/item/gameplay/fountain_of_youth/deactivated.png`
* `assets/civilization/textures/item/ui/empty_item.png`
* `assets/civilization/textures/item/ui/panacea_potion.png`
* `assets/minecraft/sounds/custom/coins.ogg`
* `assets/minecraft/sounds/custom/tokens.ogg`
* `assets/minecraft/sounds/custom/royale_victory.ogg`
* `assets/minecraft/sounds/custom/crate_open.ogg`
* `assets/minecraft/sounds/custom/crate_scroll.ogg`
* `assets/minecraft/sounds/custom/crate_unlock.ogg`
* `assets/minecraft/sounds/custom/crate_unlock_mythical.ogg`
* `assets/minecraft/sounds/custom/kill_effects/fart.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_air1.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_air2.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_air3.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_ground_heavy.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_ground1.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_ground2.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_ground3.ogg`
* `assets/minecraft/sounds/custom/tools/mace/smash_ground4.ogg`
* `assets/minecraft/sounds/custom/tools/wind_charge/wind_burst1.ogg`
* `assets/minecraft/sounds/custom/tools/wind_charge/wind_burst2.ogg`
* `assets/minecraft/sounds/custom/tools/wind_charge/wind_burst3.ogg`
* `assets/minecraft/sounds/custom/kill_effects/fly_swatter.ogg`
* `assets/minecraft/sounds/custom/kill_effects/bomb_drop.ogg`
* `assets/minecraft/sounds/custom/kill_effects/fire_ignition.ogg`
* `assets/minecraft/sounds/custom/cosmetics/flamethrower/loop.ogg`
* `assets/minecraft/sounds/custom/cosmetics/flamethrower/start.ogg`
* `assets/minecraft/sounds/custom/openers/ufo_beam_ray.ogg`
* `assets/minecraft/sounds/custom/cosmetics/vacuum/start.ogg`
* `assets/minecraft/sounds/custom/cosmetics/vacuum/loop.ogg`
* `assets/minecraft/sounds/custom/cosmetics/vacuum/stop.ogg`
* `assets/minecraft/sounds/custom/cosmetics/confetti_cannon/boom.ogg`
* `assets/minecraft/sounds/custom/cosmetics/go_kart/loop.ogg`

## Исправленные ссылки на звуки

* emcromoknightboss:custom.cromoknightboss.death: custom/mobs/cromoknightboss/death1 -> minecraft:custom/mobs/cromoknightboss/death1
* emcromoknightboss:custom.cromoknightboss.death: custom/mobs/cromoknightboss/death2 -> minecraft:custom/mobs/cromoknightboss/death2
* emcromoknightboss:custom.cromoknightboss.efforta: custom/mobs/cromoknightboss/efforta1 -> minecraft:custom/mobs/cromoknightboss/efforta1
* emcromoknightboss:custom.cromoknightboss.efforta: custom/mobs/cromoknightboss/efforta2 -> minecraft:custom/mobs/cromoknightboss/efforta2
* emcromoknightboss:custom.cromoknightboss.efforta: custom/mobs/cromoknightboss/efforta3 -> minecraft:custom/mobs/cromoknightboss/efforta3
* emcromoknightboss:custom.cromoknightboss.effortc: custom/mobs/cromoknightboss/effortc1 -> minecraft:custom/mobs/cromoknightboss/effortc1
* emcromoknightboss:custom.cromoknightboss.effortc: custom/mobs/cromoknightboss/effortc2 -> minecraft:custom/mobs/cromoknightboss/effortc2
* emcromoknightboss:custom.cromoknightboss.effortc: custom/mobs/cromoknightboss/effortc3 -> minecraft:custom/mobs/cromoknightboss/effortc3
* emcromoknightboss:custom.cromoknightboss.cast: custom/mobs/cromoknightboss/cast1 -> minecraft:custom/mobs/cromoknightboss/cast1
* emcromoknightboss:custom.cromoknightboss.cast: custom/mobs/cromoknightboss/cast2 -> minecraft:custom/mobs/cromoknightboss/cast2
* emcromoknightboss:custom.cromoknightboss.cast: custom/mobs/cromoknightboss/cast3 -> minecraft:custom/mobs/cromoknightboss/cast3
* emcromoknightboss:custom.cromoknightboss.cast: custom/mobs/cromoknightboss/cast4 -> minecraft:custom/mobs/cromoknightboss/cast4
* emcromoknightboss:custom.cromoknightboss.hurt: custom/mobs/cromoknightboss/hurt1 -> minecraft:custom/mobs/cromoknightboss/hurt1
* emcromoknightboss:custom.cromoknightboss.hurt: custom/mobs/cromoknightboss/hurt2 -> minecraft:custom/mobs/cromoknightboss/hurt2
* emcromoknightboss:custom.cromoknightboss.hurt: custom/mobs/cromoknightboss/hurt3 -> minecraft:custom/mobs/cromoknightboss/hurt3
* emcromoknightboss:custom.cromoknightboss.hurtedbad: custom/mobs/cromoknightboss/hurtedbad1 -> minecraft:custom/mobs/cromoknightboss/hurtedbad1
* emcromoknightboss:custom.cromoknightboss.hurtedbad: custom/mobs/cromoknightboss/hurtedbad2 -> minecraft:custom/mobs/cromoknightboss/hurtedbad2
* emcromoknights:custom.cromoknightboss.metalhit: custom/mobs/cromoknightboss/hit1 -> minecraft:custom/mobs/cromoknightboss/hit1
* emcromoknights:custom.cromoknightboss.metalhit: custom/mobs/cromoknightboss/hit2 -> minecraft:custom/mobs/cromoknightboss/hit2
* emcromoknights:custom.cromoknights.slashhita: custom/mobs/fallenhero/slashhit1 -> minecraft:custom/mobs/fallenhero/slashhit1
* emcromoknights:custom.cromoknights.slashhita: custom/mobs/fallenhero/slashhit2 -> minecraft:custom/mobs/fallenhero/slashhit2
* emcromoknights:custom.cromoknights.slashhitb: custom/mobs/fallenhero/slashhitb1 -> minecraft:custom/mobs/fallenhero/slashhitb1
* emcromoknights:custom.cromoknights.slashhitb: custom/mobs/fallenhero/slashhitb2 -> minecraft:custom/mobs/fallenhero/slashhitb2
* emcromoknights:custom.cromoknights.slashhitb: custom/mobs/fallenhero/slashhitb3 -> minecraft:custom/mobs/fallenhero/slashhitb3
* emcromoknights:custom.cromoknights.herostun: custom/spell/fallenhero/stun1 -> minecraft:custom/spell/fallenhero/stun1
* emcromoknights:custom.cromoknights.herostun: custom/spell/fallenhero/stun2 -> minecraft:custom/spell/fallenhero/stun2
* emcromoknights:custom.cromoknights.channelstun: custom/spell/fallenhero/channelstun -> minecraft:custom/spell/fallenhero/channelstun
* emcromoknights:custom.cromoknights.attackwindup: custom/mobs/fallenhero/attackwindup1 -> minecraft:custom/mobs/fallenhero/attackwindup1
* emcromoknights:custom.cromoknights.attackwindup: custom/mobs/fallenhero/attackwindup2 -> minecraft:custom/mobs/fallenhero/attackwindup2
* emcromoknights:custom.cromoknights.attackwindup: custom/mobs/fallenhero/attackwindup3 -> minecraft:custom/mobs/fallenhero/attackwindup3
* emcromoknights:custom.cromoknights.attackwindup: custom/mobs/fallenhero/attackwindup4 -> minecraft:custom/mobs/fallenhero/attackwindup4
* emcromoknights:custom.cromoknights.landshockwave: custom/spell/fallenhero/landshockwave1 -> minecraft:custom/spell/fallenhero/landshockwave1
* emcromoknights:custom.cromoknights.landshockwave: custom/spell/fallenhero/landshockwave2 -> minecraft:custom/spell/fallenhero/landshockwave2
* emcromoknights:custom.cromoknights.landshockwave: custom/spell/fallenhero/landshockwave3 -> minecraft:custom/spell/fallenhero/landshockwave3
* emcromoknights:custom.cromoknights.hurt: custom/mobs/fallenreaper/hurtgen1 -> minecraft:custom/mobs/fallenreaper/hurtgen1
* emcromoknights:custom.cromoknights.hurt: custom/mobs/fallenreaper/hurtgen2 -> minecraft:custom/mobs/fallenreaper/hurtgen2
* emcromoknights:custom.cromoknights.hurt: custom/mobs/fallenreaper/hurtgen3 -> minecraft:custom/mobs/fallenreaper/hurtgen3
* emcromoknights:custom.cromoknights.attack4reaper: custom/spell/fallenreaper/attack4reaper1 -> minecraft:custom/spell/fallenhero/attack4reaper1
* emcromoknights:custom.cromoknights.attack4reaper: custom/spell/fallenhero/attack4reaper2 -> minecraft:custom/spell/fallenhero/attack4reaper2
* emcromoknights:custom.cromoknights.attack4reaper: custom/spell/fallenhero/attack4reaper3 -> minecraft:custom/spell/fallenhero/attack4reaper3
* emcromoknights:custom.cromoknights.grandslashcast: custom/spell/fallenhero/granslashcast1 -> minecraft:custom/spell/fallenhero/granslashcast1
* emcromoknights:custom.cromoknights.grandslashcast: custom/spell/fallenhero/granslashcast2 -> minecraft:custom/spell/fallenhero/granslashcast2
* emcromoknights:custom.cromoknights.channelknight: custom/mobs/cromoknightboss/channelknight1 -> minecraft:custom/mobs/cromoknightboss/channelknight1
* emcromoknights:custom.cromoknights.directionsword: custom/mobs/cromoknightboss/directioncast1 -> minecraft:custom/mobs/cromoknightboss/directioncast1
* emcromoknights:custom.cromoknights.directionsword: custom/mobs/cromoknightboss/directioncast2 -> minecraft:custom/mobs/cromoknightboss/directioncast2
* emcromoknights:custom.cromoknights.directioncast: custom/mobs/cromoknightboss/sword_direction1 -> minecraft:custom/mobs/cromoknightboss/sword_direction1
* emcromoknights:custom.cromoknights.directioncast: custom/mobs/cromoknightboss/sword_direction2 -> minecraft:custom/mobs/cromoknightboss/sword_direction2
* littleroom_yeti:littleroom.yeti.impact1: littleroom/yeti/impact1 -> minecraft:littleroom/yeti/impact1
* littleroom_yeti:littleroom.yeti.impact2: littleroom/yeti/impact2 -> minecraft:littleroom/yeti/impact2
* littleroom_yeti:littleroom.yeti.impact3: littleroom/yeti/impact3 -> minecraft:littleroom/yeti/impact3
* littleroom_yeti:littleroom.yeti.impact4: littleroom/yeti/impact4 -> minecraft:littleroom/yeti/impact4
* littleroom_yeti:littleroom.yeti.swing1: littleroom/yeti/swing1 -> minecraft:littleroom/yeti/swing1
* littleroom_yeti:littleroom.yeti.swing2: littleroom/yeti/swing2 -> minecraft:littleroom/yeti/swing2
* littleroom_yeti:littleroom.yeti.swing3: littleroom/yeti/swing3 -> minecraft:littleroom/yeti/swing3
* littleroom_yeti:littleroom.yeti.swing4: littleroom/yeti/swing4 -> minecraft:littleroom/yeti/swing4
* littleroom_yeti:littleroom.yeti.swing5: littleroom/yeti/swing5 -> minecraft:littleroom/yeti/swing5
* littleroom_yeti:littleroom.yeti.ground_impact1: littleroom/yeti/ground_impact1 -> minecraft:littleroom/yeti/ground_impact1
* littleroom_yeti:littleroom.yeti.ground_impact2: littleroom/yeti/ground_impact2 -> minecraft:littleroom/yeti/ground_impact2
* littleroom_yeti:littleroom.yeti.snowball_impact: littleroom/yeti/snowball_impact -> minecraft:littleroom/yeti/snowball_impact
* littleroom_yeti:littleroom.yeti.snowball_roll: littleroom/yeti/snowball_roll -> minecraft:littleroom/yeti/snowball_roll
* littleroom_yeti:littleroom.yeti.yeti_dig: littleroom/yeti/yeti_dig -> minecraft:littleroom/yeti/yeti_dig
* littleroom_yeti:littleroom.yeti.yeti_wind: littleroom/yeti/yeti_wind -> minecraft:littleroom/yeti/yeti_wind
* littleroom_yeti:littleroom.yeti.grunt1: littleroom/yeti/grunt1 -> minecraft:littleroom/yeti/grunt1
* littleroom_yeti:littleroom.yeti.grunt2: littleroom/yeti/grunt2 -> minecraft:littleroom/yeti/grunt2
* littleroom_yeti:littleroom.yeti.grunt3: littleroom/yeti/grunt3 -> minecraft:littleroom/yeti/grunt3
* littleroom_yeti:littleroom.yeti.rage1: littleroom/yeti/rage1 -> minecraft:littleroom/yeti/rage1
* littleroom_yeti:littleroom.yeti.rage2: littleroom/yeti/rage2 -> minecraft:littleroom/yeti/rage2
* littleroom_yeti:littleroom.yeti.rage3: littleroom/yeti/rage3 -> minecraft:littleroom/yeti/rage3
* littleroom_yeti:littleroom.yeti.snow_grab1: littleroom/yeti/snow_grab1 -> minecraft:littleroom/yeti/snow_grab1
* littleroom_yeti:littleroom.yeti.snow_grab2: littleroom/yeti/snow_grab2 -> minecraft:littleroom/yeti/snow_grab2
* littleroom_yeti:littleroom.yeti.chest_spawn: littleroom/yeti/chest_spawn -> minecraft:littleroom/yeti/chest_spawn
* littleroom_yeti:littleroom.yeti.poof: littleroom/yeti/poof -> minecraft:littleroom/yeti/poof
