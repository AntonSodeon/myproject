package ru.qw.qwhatcase.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.qw.qwhatcase.QWHatCasePlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Защита меню: отменяются ВСЕ клики (ЛКМ/ПКМ, Shift, цифровые клавиши, F — вторая рука,
 * Q — выбрасывание, двойной клик-сбор, креатив-клонирование) и перетаскивания,
 * затронувшие окно плагина. Обработчик кнопки вызывается только после отмены события.
 */
public final class MenuListener implements Listener {
    private final QWHatCasePlugin plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public MenuListener(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int raw = event.getRawSlot();
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) {
            return;
        }
        ClickType type = event.getClick();
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.catalog().settings().clickCooldownMs();
        Long previous = lastClick.get(player.getUniqueId());
        if (previous != null && now - previous < cooldown) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);
        if (!menu.acceptsClicks()) {
            return;
        }
        menu.handleClick(raw, type);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder(false) instanceof Menu || event.getDestination().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu) {
            menu.onClose(event);
            if (event.getPlayer() instanceof Player player) {
                // Страховка: служебный предмет не должен остаться у игрока ни при каких условиях.
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.display().purgeServiceItems(player));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
