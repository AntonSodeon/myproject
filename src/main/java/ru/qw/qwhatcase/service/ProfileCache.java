package ru.qw.qwhatcase.service;

import org.bukkit.entity.Player;
import ru.qw.qwhatcase.storage.PlayerData;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileCache {
    /** Загруженные в AsyncPlayerPreLoginEvent данные ждут входа игрока. */
    private final Map<UUID, PlayerData> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Profile> online = new ConcurrentHashMap<>();

    public void preload(PlayerData data) {
        preloaded.put(data.uuid(), data);
    }

    public Profile activate(UUID uuid) {
        PlayerData data = preloaded.remove(uuid);
        if (data == null) {
            return null;
        }
        Profile profile = new Profile(data);
        online.put(uuid, profile);
        return profile;
    }

    public void put(PlayerData data) {
        online.put(data.uuid(), new Profile(data));
    }

    public Optional<Profile> get(UUID uuid) {
        return Optional.ofNullable(online.get(uuid));
    }

    public Profile get(Player player) {
        return online.get(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        online.remove(uuid);
        preloaded.remove(uuid);
    }
}
