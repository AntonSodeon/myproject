# Команды и права

## Игрок

| Команда | Право | Описание |
|---|---|---|
| `/hats` (`/hat`) | `qwhatcase.menu` | Главное меню: Кейсы, Мои шляпки, Каталог за жетоны, Снять шляпку, Ресурс-пак |
| `/hats collection` | `qwhatcase.menu` | Коллекция (страницы, фильтры, сортировка) |
| `/hats shop` | `qwhatcase.menu` (+ `qwhatcase.shop` для покупки) | Каталог за жетоны |
| `/hats equip <hat_id>` | `qwhatcase.equip` | Надеть открытую шляпку |
| `/hats unequip` | `qwhatcase.equip` | Снять шляпку |
| `/hats pack` | `qwhatcase.menu` | Повторно предложить ресурс-пак |
| `/cases` (`/case`) | `qwhatcase.cases` | Список кейсов |
| `/cases view <case_id>` | `qwhatcase.cases` | Содержимое и вероятности |
| `/cases open <case_id> [fast]` | `qwhatcase.open` (+ `permission` кейса) | Открыть; `fast` — без анимации |
| `/cases keys` | `qwhatcase.cases` | Баланс ключей |

`qwhatcase.player` (default: true) включает все права игрока.

## Администратор (`/hatcases`, алиасы `/qwhatcase`, `/qwhc`)

Все команды работают из консоли и для офлайн-игроков, которые хоть раз заходили на сервер
(поиск: онлайн → БД плагина → кэш сервера; ник или UUID).

| Команда | Право |
|---|---|
| `/hatcases key give\|take\|set <player> <case_id> <amount>` | `qwhatcase.admin.keys` |
| `/hatcases hat give\|revoke <player> <hat_id>` | `qwhatcase.admin.hats` |
| `/hatcases tokens give\|take <player> <amount>` | `qwhatcase.admin.tokens` |
| `/hatcases point add <case_id>` — привязать блок под прицелом | `qwhatcase.admin.points` |
| `/hatcases point remove` — убрать привязку блока под прицелом | `qwhatcase.admin.points` |
| `/hatcases point list` | `qwhatcase.admin.points` |
| `/hatcases inspect <player>` — коллекция и балансы | `qwhatcase.admin.inspect` |
| `/hatcases history <player> [page]`, `/hatcases history op <operation_id>` | `qwhatcase.admin.history` |
| `/hatcases reload` — проверить и применить конфигурацию | `qwhatcase.admin.reload` |
| `/hatcases migrate` (предпросмотр) → `/hatcases migrate confirm` | `qwhatcase.admin.migrate` |
| `/hatcases simulate <case_id> [N]` — статистическая проверка вероятностей (по умолчанию 1 000 000) | `qwhatcase.admin.simulate` |

`qwhatcase.admin` (default: op) включает все административные права. Обычным игрокам они не выдаются.

`take` списывает не больше, чем есть (баланс не уходит в минус) и сообщает, сколько списано фактически.

## Выдача ключей из других плагинов

Магазин/квесты/голосование выполняют консольную команду, например:

```
hatcases key give %player% basic 1
```

или используют API:

```java
QWHatCaseApi api = Bukkit.getServicesManager().load(QWHatCaseApi.class);
api.giveKeys(player.getUniqueId(), "basic", 1, "vote").thenAccept(change -> { /* change.balance() */ });
```

Методы API: `giveKeys/takeKeys/setKeys/getKeys`, `giveTokens/takeTokens/getTokens`, `grantHat/revokeHat/hasHat`,
`caseIds()`, `hatIds()`. Событие `ru.qw.qwhatcase.api.CaseOpenedEvent` вызывается после сохранения результата.
