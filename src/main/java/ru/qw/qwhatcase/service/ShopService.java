package ru.qw.qwhatcase.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Покупка шляп за жетоны. Списание и открытие шляпы — одна транзакция в БД. */
public final class ShopService {
    private final QWHatCasePlugin plugin;
    /** Одна покупка за раз на игрока: повторные клики не создают параллельных запросов. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public ShopService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public void buy(Player player, String hatId) {
        if (!player.hasPermission("qwhatcase.shop")) {
            plugin.messages().send(player, "error.no-permission");
            return;
        }
        Hat hat = plugin.catalog().hat(hatId).orElse(null);
        if (hat == null || !hat.available() || hat.shopPrice() <= 0) {
            plugin.messages().send(player, "shop.not-for-sale");
            return;
        }
        Profile profile = plugin.profiles().get(player);
        if (profile == null) {
            plugin.messages().send(player, "error.profile-loading");
            return;
        }
        if (profile.owns(hatId)) {
            plugin.messages().send(player, "shop.already-owned");
            return;
        }
        if (!pending.add(player.getUniqueId())) {
            plugin.messages().send(player, "shop.in-progress");
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        long price = hat.shopPrice();
        plugin.storage().run(db -> db.purchase(uuid, name, hatId, price), result -> {
            pending.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            Profile current = plugin.profiles().get(uuid).orElse(null);
            switch (result.status()) {
                case SUCCESS -> {
                    if (current != null) {
                        current.setTokens(result.tokensBalance());
                        current.addOwned(hatId, System.currentTimeMillis());
                    }
                    plugin.getLogger().info("Покупка: " + uuid + " шляпа=" + hatId + " цена=" + price);
                    if (online != null) {
                        plugin.messages().send(online, "shop.bought", Placeholders.of("hat", hat.coloredName(),
                                "price", price, "balance", result.tokensBalance()));
                    }
                }
                case ALREADY_OWNED -> {
                    if (current != null) {
                        current.addOwned(hatId, System.currentTimeMillis());
                    }
                    if (online != null) {
                        plugin.messages().send(online, "shop.already-owned");
                    }
                }
                case NOT_ENOUGH_TOKENS -> {
                    if (current != null) {
                        current.setTokens(result.tokensBalance());
                    }
                    if (online != null) {
                        plugin.messages().send(online, "shop.not-enough", Placeholders.of("price", price,
                                "balance", result.tokensBalance()));
                    }
                }
                case ERROR -> {
                    if (online != null) {
                        plugin.messages().send(online, "error.database");
                    }
                }
            }
        }, error -> pending.remove(uuid));
    }
}
