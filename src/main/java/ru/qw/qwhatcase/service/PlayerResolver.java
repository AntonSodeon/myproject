package ru.qw.qwhatcase.service;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Поиск игрока для админ-команд: онлайн → БД плагина → кэш сервера (usercache).
 * Без обращения к сети Mojang и без создания «пустых» игроков по опечатке в нике.
 */
public final class PlayerResolver {
    public record Target(UUID uuid, String name) {
    }

    private final QWHatCasePlugin plugin;

    public PlayerResolver(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Optional<Target>> resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Target(online.getUniqueId(), online.getName())));
        }
        UUID parsed = null;
        try {
            parsed = UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // это ник
        }
        OfflinePlayer cached = parsed == null ? Bukkit.getOfflinePlayerIfCached(input) : Bukkit.getOfflinePlayer(parsed);
        final UUID byUuid = parsed;
        return plugin.storage().submit(db -> {
            if (byUuid != null) {
                if (db.playerExists(byUuid) || (cached != null && cached.hasPlayedBefore())) {
                    return Optional.of(new Target(byUuid, cached != null && cached.getName() != null ? cached.getName() : byUuid.toString()));
                }
                return Optional.<Target>empty();
            }
            Optional<UUID> known = db.findByName(input);
            if (known.isPresent()) {
                return Optional.of(new Target(known.get(), input));
            }
            if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
                return Optional.of(new Target(cached.getUniqueId(), cached.getName() == null ? input : cached.getName()));
            }
            return Optional.<Target>empty();
        });
    }
}
