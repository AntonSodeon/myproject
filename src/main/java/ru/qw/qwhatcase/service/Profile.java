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

    public Profile(PlayerData data) {
        this.uuid = data.uuid();
        this.tokens = data.tokens();
        this.selectedHat = data.selectedHat();
        this.owned = new LinkedHashMap<>(data.owned());
        this.keys = new HashMap<>(data.keys());
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
