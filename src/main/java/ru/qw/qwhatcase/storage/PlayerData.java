package ru.qw.qwhatcase.storage;

import java.util.Map;
import java.util.UUID;

/**
 * Снимок данных игрока из БД.
 *
 * @param owned ID шляпы → время получения (мс)
 * @param keys  тип ключа → количество
 * @param enchants ID шляпы → личные чары (minecraft:key → уровень), наложенные книгами
 */
public record PlayerData(UUID uuid, String name, long tokens, String selectedHat,
                         Map<String, Long> owned, Map<String, Long> keys,
                         Map<String, Map<String, Integer>> enchants) {
}
