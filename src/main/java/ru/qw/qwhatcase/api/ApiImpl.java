package ru.qw.qwhatcase.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.storage.BalanceChange;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Реализация API; её же используют админ-команды. После фиксации транзакции кэш онлайн-игрока
 * обновляется в основном потоке значением, которое вернула БД.
 */
public final class ApiImpl implements QWHatCaseApi {
    private final QWHatCasePlugin plugin;

    public ApiImpl(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private String keyType(String caseId) {
        CaseDef def = plugin.catalog().caseDef(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный кейс: " + caseId));
        return def.keyType();
    }

    private void requireHat(String hatId) {
        if (plugin.catalog().hat(hatId).isEmpty()) {
            throw new IllegalArgumentException("Неизвестная шляпа: " + hatId);
        }
    }

    private static String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player == null ? null : player.getName();
    }

    /** Вызывается в потоке БД сразу после транзакции — порядок задач сохраняется. */
    private void onMain(UUID uuid, Consumer<Profile> update) {
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> plugin.profiles().get(uuid).ifPresent(update));
    }

    @Override
    public Set<String> caseIds() {
        return plugin.catalog().cases().keySet();
    }

    @Override
    public Set<String> hatIds() {
        return plugin.catalog().hats().keySet();
    }

    @Override
    public CompletableFuture<Long> getKeys(UUID player, String caseId) {
        String type = keyType(caseId);
        return plugin.storage().submit(db -> db.getKeys(player, type));
    }

    @Override
    public CompletableFuture<BalanceChange> giveKeys(UUID player, String caseId, long amount, String source) {
        String type = keyType(caseId);
        String name = nameOf(player);
        return plugin.storage().submit(db -> {
            BalanceChange change = db.addKeys(player, name, type, amount, source);
            onMain(player, p -> p.setKeys(type, change.balance()));
            return change;
        });
    }

    @Override
    public CompletableFuture<BalanceChange> takeKeys(UUID player, String caseId, long amount, String source) {
        String type = keyType(caseId);
        return plugin.storage().submit(db -> {
            BalanceChange change = db.takeKeys(player, type, amount, source);
            onMain(player, p -> p.setKeys(type, change.balance()));
            return change;
        });
    }

    @Override
    public CompletableFuture<BalanceChange> setKeys(UUID player, String caseId, long amount, String source) {
        String type = keyType(caseId);
        String name = nameOf(player);
        return plugin.storage().submit(db -> {
            BalanceChange change = db.setKeys(player, name, type, amount, source);
            onMain(player, p -> p.setKeys(type, change.balance()));
            return change;
        });
    }

    @Override
    public CompletableFuture<Long> getTokens(UUID player) {
        return plugin.storage().submit(db -> db.getTokens(player));
    }

    @Override
    public CompletableFuture<BalanceChange> giveTokens(UUID player, long amount, String source) {
        String name = nameOf(player);
        return plugin.storage().submit(db -> {
            BalanceChange change = db.addTokens(player, name, amount, source, null);
            onMain(player, p -> p.setTokens(change.balance()));
            return change;
        });
    }

    @Override
    public CompletableFuture<BalanceChange> takeTokens(UUID player, long amount, String source) {
        return plugin.storage().submit(db -> {
            BalanceChange change = db.takeTokens(player, amount, source);
            onMain(player, p -> p.setTokens(change.balance()));
            return change;
        });
    }

    @Override
    public CompletableFuture<Boolean> hasHat(UUID player, String hatId) {
        return plugin.storage().submit(db -> db.ownsHat(player, hatId));
    }

    @Override
    public CompletableFuture<Boolean> grantHat(UUID player, String hatId, String source) {
        requireHat(hatId);
        String name = nameOf(player);
        return plugin.storage().submit(db -> {
            boolean added = db.grantHat(player, name, hatId, "admin:" + source, source);
            onMain(player, p -> p.addOwned(hatId, System.currentTimeMillis()));
            return added;
        });
    }

    @Override
    public CompletableFuture<Boolean> revokeHat(UUID player, String hatId, String source) {
        return plugin.storage().submit(db -> {
            boolean removed = db.revokeHat(player, hatId, source);
            onMain(player, p -> {
                p.removeOwned(hatId);
                Player online = Bukkit.getPlayer(player);
                if (online != null) {
                    plugin.display().apply(online);
                }
            });
            return removed;
        });
    }
}
