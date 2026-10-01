package ru.qw.qwhatcase.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.storage.PlayerData;

import java.util.UUID;
import java.util.logging.Level;

/** Вход/выход/смерть: загрузка данных, восстановление шляпы, уведомления о непоказанных итогах. */
public final class SessionListener implements Listener {
    private final QWHatCasePlugin plugin;

    public SessionListener(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    /** Асинхронное событие: данные игрока читаются из БД вне основного потока. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void preLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            PlayerData data = plugin.storage().blocking(db -> {
                db.touchPlayer(uuid, name);
                return db.load(uuid);
            });
            plugin.profiles().preload(data);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить данные игрока " + name + " — повторная попытка после входа", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.display().purgeServiceItems(player);
        if (plugin.profiles().activate(player.getUniqueId()) != null) {
            afterLoad(player);
        } else {
            plugin.reloadProfile(player, () -> afterLoad(player));
        }
    }

    private void afterLoad(Player player) {
        if (!player.isOnline()) {
            return;
        }
        plugin.migration().convertLegacyItems(player);
        plugin.display().apply(player);
        plugin.openings().showPending(player);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.packs().send(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.openings().onQuit(player);
        // Косметический предмет не сохраняется в файле игрока: при входе он создаётся заново.
        plugin.display().stripCosmetic(player);
        plugin.display().purgeServiceItems(player);
        plugin.profiles().remove(player.getUniqueId());
        plugin.packs().forget(player.getUniqueId());
    }

    /** Шляпа не выпадает при смерти и восстанавливается после возрождения. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(HatItems::isService);
        event.getItemsToKeep().removeIf(HatItems::isService);
        if (!event.getKeepInventory()) {
            plugin.display().stripCosmetic(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.display().apply(player);
            }
        });
    }
}
