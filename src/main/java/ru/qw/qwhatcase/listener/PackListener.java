package ru.qw.qwhatcase.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Settings;
import ru.qw.qwhatcase.service.PackService;

/** Учёт состояния ресурс-пака: загружен / отказ / ошибка. */
public final class PackListener implements Listener {
    private final QWHatCasePlugin plugin;

    public PackListener(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void status(PlayerResourcePackStatusEvent event) {
        Settings settings = plugin.catalog().settings();
        if (settings.packMode() == Settings.PackMode.NONE) {
            return;
        }
        if (settings.packMode() == Settings.PackMode.PLUGIN && !plugin.packs().packId().equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                plugin.packs().setState(player, PackService.State.LOADED);
                plugin.messages().send(player, "pack.loaded");
            }
            case DECLINED -> {
                plugin.packs().setState(player, PackService.State.DECLINED);
                plugin.messages().send(player, "pack.declined");
                plugin.openings().sendPackRetry(player);
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                plugin.packs().setState(player, PackService.State.FAILED);
                plugin.messages().send(player, "pack.failed");
                plugin.openings().sendPackRetry(player);
            }
            default -> {
                if (plugin.packs().state(player) != PackService.State.LOADED) {
                    plugin.packs().setState(player, PackService.State.PENDING);
                }
            }
        }
    }
}
