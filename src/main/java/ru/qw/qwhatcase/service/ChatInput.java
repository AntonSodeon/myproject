package ru.qw.qwhatcase.service;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import ru.qw.qwhatcase.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Ввод текста в чат (поиск игрока по нику в админ-меню, как в PTrap). */
public final class ChatInput implements Listener {
    private final Plugin plugin;
    private final Map<UUID, Consumer<String>> awaiting = new ConcurrentHashMap<>();

    public ChatInput(Plugin plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, Consumer<String> callback) {
        awaiting.put(player.getUniqueId(), callback);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent event) {
        Consumer<String> callback = awaiting.remove(event.getPlayer().getUniqueId());
        if (callback == null) {
            return;
        }
        event.setCancelled(true);
        String text = Text.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                callback.accept(text);
            }
        });
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        awaiting.remove(event.getPlayer().getUniqueId());
    }
}
