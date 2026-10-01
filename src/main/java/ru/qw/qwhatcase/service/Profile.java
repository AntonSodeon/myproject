package ru.qw.qwhatcase.service;

import ru.qw.qwhatcase.storage.PlayerData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Кэш данных онлайн-игрока для отображения меню. Изменяется только в основном потоке
 * и только значениями, которые вернула БД после успешной транзакции.
 * Решения о списании принимает БД, а не кэш.
 */
public final class Profile {
    private final UUID uuid;
    private long tokens;
    private String selectedHat;
    private final Map<String, Long> owned;
    private final Map<String, Long> keys;
    private final Map<String, Map<String, Integer>> enchants;

    public Profile(PlayerData data) {
        this.uuid = data.uuid();
        this.tokens = data.tokens();
        this.selectedHat = data.selectedHat();
        this.owned = new LinkedHashMap<>(data.owned());
        this.keys = new HashMap<>(data.keys());
        this.enchants = new HashMap<>();
        data.enchants().forEach((hat, map) -> enchants.put(hat, new LinkedHashMap<>(map)));
    }

    /** Личные чары шляпы (наложенные книгами или перенесённые из PTrap). */
    public Map<String, Integer> enchants(String hatId) {
        return enchants.getOrDefault(hatId, Map.of());
    }

    public void setEnchants(String hatId, Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            enchants.remove(hatId);
        } else {
            enchants.put(hatId, new LinkedHashMap<>(map));
        }
    }

    public void clearOwned() {
        owned.clear();
        enchants.clear();
        selectedHat = null;
    }

    public UUID uuid() {
        return uuid;
    }

    public long tokens() {
        return tokens;
    }

    public void setTokens(long tokens) {
        this.tokens = tokens;
    }

    public String selectedHat() {
        return selectedHat;
    }

    public void setSelectedHat(String selectedHat) {
        this.selectedHat = selectedHat;
    }

    public boolean owns(String hatId) {
        return owned.containsKey(hatId);
    }

    public Long obtainedAt(String hatId) {
        return owned.get(hatId);
    }

    public Map<String, Long> owned() {
        return owned;
    }

    public void addOwned(String hatId, long at) {
        owned.putIfAbsent(hatId, at);
    }

    public void removeOwned(String hatId) {
        owned.remove(hatId);
        enchants.remove(hatId);
        if (hatId.equals(selectedHat)) {
            selectedHat = null;
        }
    }

    public long keys(String keyType) {
        return keys.getOrDefault(keyType, 0L);
    }

    public void setKeys(String keyType, long amount) {
        keys.put(keyType, amount);
    }
}
