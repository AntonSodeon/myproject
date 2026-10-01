package ru.qw.qwhatcase.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Данные одного игрока из старого плагина, подготовленные к переносу. */
public record LegacyImport(UUID uuid, String name, List<String> hatIds, List<Unknown> unknown,
                           Map<String, Map<String, Integer>> enchants) {
    public LegacyImport(UUID uuid, String name, List<String> hatIds, List<Unknown> unknown) {
        this(uuid, name, hatIds, unknown, Map.of());
    }

    /** Неизвестная запись: сохраняется как есть для восстановления. */
    public record Unknown(String rawKey, String rawValue) {
    }
}
