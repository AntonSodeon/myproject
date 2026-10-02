# Отчёт о проверке 0.1.13

Все проверки ниже выполнены на **релизных файлах** — тех же, что переданы пользователю:

* `monolith-arsenal-0.1.13.jar`, SHA-256 `4014a67f5e3e9103bb30b3846c46ca1599e44e4bfd8a4dfc2556ee0dceec8f18`;
* `ValkyrienSkies-Fabric-MC1.21.11-v3.1.1.jar`, SHA-256 `30899ecc694c752e89090b40b7c8394c1dc6e4d9e65ef1377e205a6100c023bf`;
* Fabric Loader 0.19.2, ванильный сервер/клиент 1.21.11, Java 21, Linux (Xvfb, программный рендер Mesa).

В `mods` лежали только эти два файла: dev-зависимости Gradle не участвовали, поэтому отсутствующие классы проявились
бы (именно так была найдена ошибка `NeoForgeConfigRegistry`). Логи и скриншоты — в архиве с доказательствами
(`monolith-arsenal-0.1.13-evidence.zip`), пути ниже указаны относительно него.

## Что переиспользовано, что сделано нами

* **Valkyrien Skies (порт LumixTeam) используется без изменений.** Jar не распакован, не пропатчен, не вложен в наш мод.
  Масса, инерция, гравитация, столкновения, вращение, синхронизация с клиентами и сохранение — его.
* **Наше:** VS-независимый API `physics/` и тонкий адаптер `compat/valkyrienskies/`
  (команды сил через неизменяемые записи → очередь → `ShipPhysicsListener` на потоке физики VS).
* **Единственная правка поведения VS** — мост конфигов `ValkyrienSkiesConfigBridge` (mixin в НАШЕМ jar, в `TAIL`
  инициализатора VS): регистрирует TOML-конфиги VS через API v5 Forge Config API Port, потому что API v4, на
  который собран порт, в FCAP для 1.21.9+ отсутствует. Без моста VS молча работает на значениях по умолчанию.

## 1. Выделенный сервер, без игроков (`srv_full.py`, keep-active только на время теста)

Физика 60 шагов/с. Без игроков VS замораживает корабли, поэтому тест включает `/vs set-keep-active` только для
своего тела и **выключает его в конце** (по умолчанию тела не держатся активными; адаптер сам keep-active не включает).
Лог: `server/phase1.log`, `server/phase2_restart.log`.

| # | Проверка | Ожидалось | Получено | Критерий | Итог |
|---|---|---|---|---|---|
| S1 | Creation in occupied space fails immediately | error, no body id | Could not create the test body: space at -1, -51, -2 is occupied | message 'Could not create' | PASS |
| S2 | Creation reports PENDING then READY | PENDING, then READY within 100 ticks | id=3, READY after 0.38s | READY received | PASS |
| S3 | Gravity: falls and stops on the floor | y drops ~10 m to the floor (top -50), \|v\|<0.2 m/s | y -39.48 -> -49.48, vy=-0.0000 | -50.5<y<-48 and \|vy\|<0.2 | PASS |
| S4 | Force changes velocity | vy > 0 shortly after an upward 3g force | vy=4.996 m/s | vy > 1 | PASS |
| S5 | Impulse is applied exactly once | dvx = J/m = 5.000 m/s, unchanged afterwards | dvx=4.9917 m/s, 0.6 s later vx=4.9883 | \|dvx-5\|<0.15 and \|vx2-vx1\|<0.1 | PASS |
| S6 | Timed force F*T delivered exactly [final] | dvz = F*T/m = 10*0.5 = 5.000 m/s | dvz=4.9833 m/s | \|dvz-5\|<0.15 | PASS |
| S7 | Continuous force acts until cleared | vx grows while set (4 m/s^2), stays constant after clear | vx 4.984 -> 8.171 (set) -> 8.556 -> 8.510 (cleared) | vx increased >1.5 while set; \|vx3-vx2\|<0.1 | PASS |
| S8 | Torque and angular impulse rotate on several axes | non-zero angular velocity on >=2 axes, pitch/roll change | angVel=[1.1457, 0.3338, 1.3644], pitch=37.3 yaw=37.6 roll=79.4 | >=2 axes \|w\|>0.1 | PASS |
| S9 | High-speed hit on a 1-block-thin wall | about 60 m/s, stops at the wall (face x=25), no tunnelling | peak vx=59.9 m/s, max x=23.24, final x=1.32 | peak>40 and max_x<25 and final x<25 | PASS |
| S10 | animated_test on the server: summon, damage, kill | damage applied, killed | Applied 2.0 damage | 'Applied 2.0 damage' | PASS |
| S11 | No errors beyond the VS-only baseline (phase 1, final) | no new ERROR/Exception lines | clean | empty | PASS |
| S12 | Body restored after server restart | same id, same position and rotation | [1.319, -49.475, 0.395] rot [-0.0, 1.16, -0.0]; saved [1.319, -49.475, 0.395] rot [-0.0, 1.16, -0.0] | position within 0.3 | PASS |
| S13 | Impulse after restart (controller restored, single) | dvy ~ 6 m/s minus gravity | dvy=4.494 m/s | 3 < dvy < 6.3 | PASS |
| S14 | Torque after restart (airborne) | angular velocity around X | angVel=[0.5623, 0.0, -0.0001], y=-46.12 | \|wx\|>0.1 | PASS |
| S15 | Safe removal | body and its blocks deleted; commands rejected afterwards | Removed vehicle body 5; info after: absent; list: 0 vehicle body | removed, absent, 0 bodies | PASS |
| S16 | No errors beyond the VS-only baseline (phase 2, final) | no new ERROR/Exception lines | clean | empty | PASS |

## 2. Выделенный сервер + 2 релизных клиента (`release_mp.py`, без keep-active)

Два производственных клиента (`TesterA`, `TesterB`, запуск через KnotClient, `--quickPlayMultiplayer`). Логи:
`release/server_session1.log`, `release/server_session2.log`, скриншоты `release/*.png`.

| # | Проверка | Ожидалось | Получено | Критерий | Итог |
|---|---|---|---|---|---|
| R1 | Creation with players nearby (no keep-active) | PENDING then READY | id=3 READY | READY | PASS |
| R2 | Gravity and resting on the floor (players nearby, no keep-active) | y about -49.5, \|vy\|<0.2 | y=-49.475 vy=-0.0000 | -50.5<y<-48 and \|vy\|<0.2 | PASS |
| R3 | Force changes velocity (release build, clients connected) | vx>1 m/s while pushed | vx=2.897 | vx>1 | PASS |
| R4 | Player on the deck of a moving body | player moves with the deck, stays on top | body dx=7.47, player dx=7.47, player y=-49.00 (deck top ~-48.98) | player dx within 30% of body dx and player y >= deck top - 0.5 | PASS |
| R5 | Torque rotates on several axes (release) | >=2 axes \|w\|>0.1 | angVel=[0.562, 0.2809, 0.5619] | >=2 axes | PASS |
| R6 | Weapon pose after 5 slot switches (release client, 3rd person) | every weapon frame matches the first, every empty frame differs | weapon-vs-first diffs=[0.02, 0.02, 0.0, 0.0], weapon-vs-empty diffs=[11.41, 11.51, 11.54, 11.47, 11.42] | max(same) < min(diff)/2 | PASS |
| R7 | animated_test: rendered and animated, damage, kill (release client) | frames 1 s apart differ (idle animation); damage 2.0; gone after kill | entity-region frame diff=3.04 (static control 0.00); Applied 2.0 damage; after kill: absent | entity diff > 1 and control < 0.2, damage applied, absent | PASS |
| R8 | Players away: body stays loaded but frozen; queued impulse waits | body present, position unchanged while away | present=True, moved 0.0000 while away | present and moved < 0.01 | PASS |
| R9 | Players return: deferred impulse applied exactly once, no fall-through | rise about v^2/2g = 1.8 m (twice would be ~7 m), body back on the floor | rise=1.74 m, y now -49.475 | 1.0 < rise < 3.0 and y > -51 | PASS |
| R9b | One controller after players return | dvy about 4.8 m/s | dvy=5.163 | 3<dvy<6.3 | PASS |
| R10 | No server errors beyond the VS-only baseline (session 1) | none | clean | empty | PASS |
| R11 | Body restored after server restart (data and controller) | body present with the same id | present=True, first y=-49.475, saved [12.916, -49.475, -0.316] | present | PASS |
| R12 | Impulse right after restart is applied exactly once (controller restored, single) | rise about v^2/2g = 1.8 m (twice would be ~7 m) | rise=1.74 m | 1.0 < rise < 3.0 | PASS |
| R12b | KNOWN VS PORT DEFECT: ships fall through terrain after a restart (observation, players nearby) | a restored body stays on the ground (expected of a correct engine) | our body y samples [-49.5, -49.5, -49.5, -49.5, -49.5, -49.5, -49.5, -49.5, -49.5, -49.5]; plain VS ship before ( 1.550E+1 -4.950E+1 -9.498E+0) after ( 1.550E+1 -4.950E+1 -9.498E+0) | observation only: compared with a plain VS ship | PASS |
| R14 | Safe removal | removed, none left, commands on the id rejected | Removed vehicle body 3; list: 0 vehicle body; No ready vehicle body | all three | PASS |
| R15 | No server errors beyond the VS-only baseline (session 2) | none | clean | empty | PASS |

R6 сравнивает кадры от третьего лица в области рук (средняя разница пикселей): кадры «с оружием» совпадают с первым
(≤0,02), кадры «пустой слот» отличаются (≈11,4) — поза включается и выключается каждый из 5 раз.
R7: разница двух кадров с интервалом 1 с в области модели 3,04 при контрольной области 0,00 — idle-анимация идёт.

## 3. Конфиг VS действительно применяется

| Проверка | Ожидалось | Получено | Итог |
|---|---|---|---|
| Лог при старте | `Applied Valkyrien Skies config …` для core-server, server, client | есть (`config/physicsSpeed-*.log`, строки 110, 111, 761) | PASS |
| `physicsSpeed = 0.5` в `valkyrienskies-core-server.toml` | падение вдвое медленнее | vy через 1,6 с: −6,41 м/с при 0,5 против −13,24 м/с при 1,0 | PASS |

## 4. Независимость от частоты физики

`physicsTicksPerGameTick` в этом порту не меняет частоту (≈60 шагов/с всегда), поэтому длина шага менялась через
`physicsSpeed` (dt 1/60 и 1/120 с). Импульс 5 м/с: 5,0021 / 5,0000; сила на время F·T/m = 5 м/с: 4,9845 / 4,9983
(`physics-rate/speed_*.log`). Импульс применяется ровно один раз, сила — ровно заданное физическое время.

## 5. Внутриигровой gametest (dev-клиент, `./gradlew runClientGameTest`)

`ALL CHECKS PASSED` (`gametest/client_gametest.log`): создание READY, падение и покой, совпадение позиции на клиенте
и сервере, сила, угловой импульс, игрок на палубе, заморозка без игрока и отложенный импульс, один контроллер,
эталонный «голый» корабль VS, импульс сразу после повторного открытия мира (dvx = 6,0 один раз), удаление, поза
оружия 5 циклов, `animated_test`.

## 6. Миграция с 0.1.12 (`migration.py`)

| # | Проверка | Ожидалось | Получено | Критерий | Итог |
|---|---|---|---|---|---|
| M1 | 0.1.12 world with a Monolith Skies ship | ship saved | 1 ship(s) loaded | 1 ship | PASS |
| M2 | Opening the 0.1.12 world directly with 0.1.13 (NOT recommended) | ship and its blocks are lost (unknown entity type) | chest with diamonds found: False; log lines mentioning monolith_skies: ['[03:11:00] [Server thread/WARN]: Skipping Entity with id monolith_skies:ship', 'chunk@[0, 0].Entities[0]: Failed to decode value \'"monolith_skies:ship"\' from field \'id\': Unknown registry key in ResourceKey[minecraft:root / minecraft:entity_type]: monolith_skies:ship'] | documents data loss | PASS |
| M3 | Disassemble the ship with 0.1.12 | blocks placed back, no ships left | Ship placed back into the world (26 blocks); 0 ship(s) loaded | placed, 0 ships | PASS |
| M4 | 0.1.13 opens the disassembled world: blocks and chest contents intact | chest with 7 diamonds and the deck under it | chest: (2, -56, 2, '[{count: 7, Slot: 0b, id: "minecraft:diamond"}]'); plank under chest: passed | chest with 7 diamonds present, plank present | PASS |
| M5 | No errors beyond the VS-only baseline after migration | none | clean | empty | PASS |

## Подтверждённые сценарии

* Сборка из двух файлов в `mods` запускается на выделенном сервере и на клиенте без dev-зависимостей.
* TOML-конфиги VS читаются и применяются (physicsSpeed).
* Создание тела: PENDING → READY; занятое место → ошибка без id.
* Гравитация, падение и покой на земле; игрок стоит на движущейся палубе и едет вместе с ней (2 клиента).
* Сила, импульс (ровно один раз), сила на время (F·T точно), постоянный канал до сброса; при dt 1/60 и 1/120 с.
* Момент и угловой импульс — вращение по нескольким осям.
* Удар о стену толщиной в 1 блок на ~60 м/с — без пролёта.
* Без игроков тело заморожено, команды ждут; игрок вернулся — импульс применён один раз.
* Сохранение/загрузка: тело, данные и контроллер восстанавливаются; силы и моменты работают после рестарта; один контроллер.
* Безопасное удаление (тело и блоки удалены, команды по id отклоняются).
* Поза оружия после 5 переключений слота; `animated_test`: отображение, анимация, урон, удаление.
* keep-active включается только тестом и выключается в конце; по умолчанию не включается.
* Переход с 0.1.12 через разборку кораблей сохраняет блоки и содержимое сундуков; прямой переход удаляет корабль.

## Известный дефект порта VS (воспроизводится без нашего кода)

Восстановленные из сохранения корабли **проваливаются сквозь землю** после повторного открытия одиночного мира
(gametest: y −127,4 → −352,9, «голый» корабль VS — так же) и — не всегда — после рестарта выделенного
сервера без игроков (в одном прогоне тело ушло на y −130,7; в финальном прогоне S14 этого не было, y −46,1).
На выделенном сервере, куда после рестарта зашли игроки, этого **не** произошло (R12b: y стабильно −49,5, «голый»
корабль VS тоже на месте). Данные и контроллер при этом восстанавливаются, импульс после загрузки применяется
ровно один раз. Исправить в адаптере не удалось, исходники этой сборки порта не опубликованы. Поэтому порт **нельзя
назвать полностью исправным**: сохранённые машины в одиночной игре сейчас ненадёжны.

## Не проверено

* Реальный Windows/macOS клиент и дискретная видеокарта (клиенты запускались под Xvfb с программным рендером).
* Звук; длительные сессии (часы); больше 2 клиентов; сервер под нагрузкой/лагами.
* Совместимость с другими аддонами VS (Eureka, Clockwork и т. п.) и с другими модами.
* Переход тел между измерениями.
* Путь таймаута создания (FAILED через 100 тиков с удалением корабля) — проверен только чтением кода и путём
  «место занято»; в реальном запуске VS всегда загружал корабль вовремя.
* Танки, самолёты, управление игроком — вне рамок этой версии (есть только тестовое тело).
* Миграция кораблей Monolith Skies в корабли VS — не реализована (только ручной путь через разборку).
