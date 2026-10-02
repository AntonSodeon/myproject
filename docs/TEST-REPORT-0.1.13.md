# Отчёт о проверке 0.1.13

Все проверки ниже выполнены на **релизных файлах** — тех же, что переданы пользователю:

* `monolith-arsenal-0.1.13.jar`, SHA-256 `bab8edb98c89335dce4bf4cdc7ec5ae13dcdf2211ff28ff9e1b2830fe50b8325`;
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
* **Два исправления ошибок порта VS**, оба — миксины в НАШЕМ jar (jar VS не изменён):
  1. мост конфигов `ValkyrienSkiesConfigBridge`: регистрирует TOML-конфиги VS через API v5 Forge Config API Port,
     потому что API v4, на который собран порт, в FCAP для 1.21.9+ отсутствует. Без моста VS молча работает на
     значениях по умолчанию;
  2. `ShipyardChunkSaveMixin`: сохраняет чанк шипъярда с несохранёнными блоками перед тем, как VS выбросит его из
     памяти. Без этого тела теряли блоки при сохранении и после загрузки проваливались сквозь землю (раздел 6).

## 1. Выделенный сервер, без игроков (`srv_full.py`, keep-active только на время теста)

Физика 60 шагов/с. Без игроков VS замораживает тела, поэтому тест включает `/vs set-keep-active` только для
своего тела и **выключает его в конце** (по умолчанию тела не держатся активными; адаптер сам keep-active не включает).
Логи: `server/phase1.log`, `server/phase2_restart.log`.

| # | Проверка | Ожидалось | Получено | Критерий | Итог |
|---|---|---|---|---|---|
| S1 | Creation in occupied space fails immediately | error, no body id | Could not create the test body: space at -1, -51, -2 is occupied | message 'Could not create' | PASS |
| S2 | Creation reports PENDING then READY | PENDING, then READY within 100 ticks | id=3, READY after 0.30s | READY received | PASS |
| S3 | Gravity: falls and stops on the floor | y drops ~10 m to the floor (top -50), \|v\|<0.2 m/s | y -39.48 -> -49.48, vy=-0.0000 | -50.5<y<-48 and \|vy\|<0.2 | PASS |
| S4 | Force changes velocity | vy > 0 shortly after an upward 3g force | vy=4.996 m/s | vy > 1 | PASS |
| S5 | Impulse is applied exactly once | dvx = J/m = 5.000 m/s, unchanged afterwards | dvx=4.9932 m/s, 0.6 s later vx=4.9918 | \|dvx-5\|<0.15 and \|vx2-vx1\|<0.1 | PASS |
| S6 | Timed force F*T delivered exactly [fix] | dvz = F*T/m = 10*0.5 = 5.000 m/s | dvz=4.9839 m/s | \|dvz-5\|<0.15 | PASS |
| S7 | Continuous force acts until cleared | vx grows while set (4 m/s^2), stays constant after clear | vx 4.989 -> 8.177 (set) -> 8.563 -> 8.516 (cleared) | vx increased >1.5 while set; \|vx3-vx2\|<0.1 | PASS |
| S8 | Torque and angular impulse rotate on several axes | non-zero angular velocity on >=2 axes, pitch/roll change | angVel=[1.1418, 0.3313, 1.3628], pitch=37.5 yaw=37.1 roll=79.2 | >=2 axes \|w\|>0.1 | PASS |
| S9 | High-speed hit on a 1-block-thin wall | about 60 m/s, stops at the wall (face x=25), no tunnelling | peak vx=59.9 m/s, max x=23.25, final x=2.58 | peak>40 and max_x<25 and final x<25 | PASS |
| S10 | animated_test on the server: summon, damage, kill | damage applied, killed | Applied 2.0 damage | 'Applied 2.0 damage' | PASS |
| S11 | No errors beyond the VS-only baseline (phase 1, fix) | no new ERROR/Exception lines | clean | empty | PASS |
| S12 | Body restored after server restart | same id, same position and rotation | [2.581, -49.475, 0.405] rot [-0.0, 0.86, -0.0]; saved [2.581, -49.475, 0.405] rot [0.0, 0.86, 0.0] | position within 0.3 | PASS |
| S13 | Impulse after restart (controller restored, single) | dvy ~ 6 m/s minus gravity | dvy=4.494 m/s | 3 < dvy < 6.3 | PASS |
| S14 | Torque after restart (airborne) | angular velocity around X | angVel=[0.5623, -0.0, -0.0001], y=-46.12 | \|wx\|>0.1 | PASS |
| S15 | Safe removal | body and its blocks deleted; commands rejected afterwards | Removed vehicle body 5; info after: absent; list: 0 vehicle body | removed, absent, 0 bodies | PASS |
| S16 | No errors beyond the VS-only baseline (phase 2, fix) | no new ERROR/Exception lines | clean | empty | PASS |

## 2. Выделенный сервер + 2 релизных клиента (`release_mp.py`, без keep-active)

Два производственных клиента (`TesterA`, `TesterB`, запуск через KnotClient, `--quickPlayMultiplayer`). Логи:
`release/server_session1.log`, `release/server_session2.log`, скриншоты `release/*.png`.

| # | Проверка | Ожидалось | Получено | Критерий | Итог |
|---|---|---|---|---|---|
| R1 | Creation with players nearby (no keep-active) | PENDING then READY | id=3 READY | READY | PASS |
| R2 | Gravity and resting on the floor (players nearby, no keep-active) | y about -49.5, \|vy\|<0.2 | y=-49.475 vy=-0.0000 | -50.5<y<-48 and \|vy\|<0.2 | PASS |
| R3 | Force changes velocity (release build, clients connected) | vx>1 m/s while pushed | vx=2.897 | vx>1 | PASS |
| R4 | Player on the deck of a moving body | player moves with the deck, stays on top | body dx=7.47, player dx=7.47, player y=-49.00 (deck top ~-48.98) | player dx within 30% of body dx and player y >= deck top - 0.5 | PASS |
| R5 | Torque rotates on several axes (release) | >=2 axes \|w\|>0.1 | angVel=[0.5622, 0.281, 0.5618] | >=2 axes | PASS |
| R6 | Weapon pose after 5 slot switches (release client, 3rd person) | every weapon frame matches the first, every empty frame differs | weapon-vs-first diffs=[0.02, 0.02, 0.0, 0.02], weapon-vs-empty diffs=[11.2, 11.14, 11.06, 11.08, 11.24] | max(same) < min(diff)/2 | PASS |
| R7 | animated_test: rendered and animated, damage, kill (release client) | frames 1 s apart differ (idle animation); damage 2.0; gone after kill | entity-region frame diff=2.60 (static control 0.00); Applied 2.0 damage; after kill: absent | entity diff > 1 and control < 0.2, damage applied, absent | PASS |
| R8 | Players away: body stays loaded but frozen; queued impulse waits | body present, position unchanged while away | present=True, moved 0.0000 while away | present and moved < 0.01 | PASS |
| R9 | Players return: deferred impulse applied exactly once, no fall-through | rise about v^2/2g = 1.8 m (twice would be ~7 m), body back on the floor | rise=1.74 m, y now -49.475 | 1.0 < rise < 3.0 and y > -51 | PASS |
| R9b | One controller after players return | dvy about 4.8 m/s | dvy=4.996 | 3<dvy<6.3 | PASS |
| R10 | No server errors beyond the VS-only baseline (session 1) | none | clean | empty | PASS |
| R11 | Body restored after server restart (data and controller) | body present with the same id | present=True, first y=-49.475, saved [13.055, -49.475, 0.183] | present | PASS |
| R12 | Impulse right after restart is applied exactly once (controller restored, single) | rise about v^2/2g = 1.8 m (twice would be ~7 m) | rise=1.73 m | 1.0 < rise < 3.0 | PASS |
| R12b | Restored bodies stay on the ground after a restart (ours and a plain VS ship) | y unchanged within 0.5 m for 5 s | our body y samples [-49.48, -49.48, -49.48, -49.48, -49.48, -49.48, -49.48, -49.48, -49.48, -49.48]; plain VS ship before ( 1.550E+1 -4.950E+1 -9.498E+0) after ( 1.550E+1 -4.950E+1 -9.498E+0) | ours and plain within 0.5 m | PASS |
| R14 | Safe removal | removed, none left, commands on the id rejected | Removed vehicle body 3; list: 0 vehicle body; No ready vehicle body | all three | PASS |
| R15 | No server errors beyond the VS-only baseline (session 2) | none | clean | empty | PASS |

R6 сравнивает кадры от третьего лица в области рук (средняя разница пикселей): кадры «с оружием» совпадают с первым
(≤0,02), кадры «пустой слот» отличаются (≈11,1) — поза включается и выключается каждый из 5 раз.
R7: разница двух кадров с интервалом 1 с в области модели 2,60 при контрольной области 0,00 — idle-анимация идёт.

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
поза оружия 5 циклов, `animated_test`. После выхода и повторного входа в мир: импульс применён ровно один раз
(подъём 1,74 м при ожидаемых ~1,8; при двойном применении было бы ~7 м); наше тело (y −58,974 → −58,974) и тело VS
без нашего кода (y −58,500 → −58,500) остаются на земле — теперь это обязательная проверка, а не наблюдение.

## 6. Исправление VS: тела проваливались после загрузки

Причина и исправление описаны в `VEHICLE-PHYSICS.md` (раздел «VS fix: shipyard chunks lost on save»). Логи — `fall/`.

| Проверка | До исправления | После | Итог |
|---|---|---|---|
| Сохраняются ли блоки тел в чанках шипъярда (`save_probe.py`, 4–5 тел на прогон) | блоки потеряны в 9 из 10 прогонов, от 1 до 10 тел за прогон (тела VS без нашего кода — тоже) | 3 прогона × 4 тела: все 12 сохранены | PASS |
| Рестарт сервера с 11 телами (`fall_repro.py`) | упали 10 из 11 (до y ≈ −3600 за 28 с) | 0 из 11 упали за 19 с, y −59,5 у всех | PASS |
| Перезаход в одиночный мир (gametest) | тело y −127 → −353, тело VS без нашего кода — так же | оба на месте (раздел 5) | PASS |
| Рестарт с игроками (R12b) | наблюдение, без проверки | обязательная проверка: оба тела на месте | PASS |

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
* После рестарта сервера и перезахода в одиночный мир тела стоят на земле (исправление сохранения шипъярда).
* Безопасное удаление (тело и блоки удалены, команды по id отклоняются).
* Поза оружия после 5 переключений слота; `animated_test`: отображение, анимация, урон, удаление.
* keep-active включается только тестом и выключается в конце; по умолчанию не включается.

## Остающиеся ограничения

* Миры, сохранённые до этого исправления, могут содержать тела без блоков (пустой шипъярд); такие тела не
  восстановить — их нужно собрать заново.
* Порт VS неофициальный и закрытый; исправления (конфиги, сохранение шипъярда) — миксины в нашем моде, рассчитанные
  на версию 2.4.205+0d0017dd8a. С другой сборкой порта их нужно перепроверить.
* Без игроков рядом VS замораживает тела (так задумано портом); `physicsTicksPerGameTick` не работает.

## Не проверено

* Реальный Windows/macOS клиент и дискретная видеокарта (клиенты запускались под Xvfb с программным рендером).
* Звук; длительные сессии (часы); больше 2 клиентов; сервер под нагрузкой/лагами.
* Совместимость с другими аддонами VS (Eureka, Clockwork и т. п.) и с другими модами.
* Переход тел между измерениями.
* Путь таймаута создания (FAILED через 100 тиков с удалением тела) — проверен только чтением кода и путём
  «место занято»; в реальном запуске VS всегда загружал тело вовремя.
* Танки, самолёты, управление игроком — вне рамок этой версии (есть только тестовое тело).
* Исправление сохранения шипъярда проверено на тестовых телах до 11 штук и одной смене чанков; большие корабли
  (много чанков шипъярда) и долгие сессии с автосохранением не проверялись.
