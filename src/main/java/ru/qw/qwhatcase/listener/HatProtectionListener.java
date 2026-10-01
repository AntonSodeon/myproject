package ru.qw.qwhatcase.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.service.HatItems;

/**
 * Защита косметической шляпы в слоте шлема и служебных предметов:
 * нельзя снять, переложить, выбросить, обменять цифрами/второй рукой, вытащить в креативе,
 * подобрать или получить через раздатчик; настоящий шлем при этом не теряется.
 */
public final class HatProtectionListener implements Listener {
    private static final int HELMET_SLOT = 39;
    private final QWHatCasePlugin plugin;

    public HatProtectionListener(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean isHeadItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (item.getType().getEquipmentSlot() == EquipmentSlot.HEAD) {
            return true;
        }
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasEquippable() && meta.getEquippable().getSlot() == EquipmentSlot.HEAD;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean blocked = HatItems.isService(event.getCurrentItem()) || HatItems.isService(event.getCursor());
        if (!blocked && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            blocked = HatItems.isService(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (!blocked && event.getClick() == ClickType.SWAP_OFFHAND) {
            blocked = HatItems.isService(player.getInventory().getItemInOffHand());
        }
        boolean helmetSlot = event.getClickedInventory() instanceof PlayerInventory
                && event.getSlotType() == InventoryType.SlotType.ARMOR && event.getSlot() == HELMET_SLOT;
        if (helmetSlot && HatItems.isCosmetic(player.getInventory().getHelmet())) {
            blocked = true;
            plugin.messages().send(player, "hat.slot-locked");
        }
        if (blocked) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void drag(InventoryDragEvent event) {
        if (HatItems.isService(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }
        if (event.getWhoClicked() instanceof Player player && HatItems.isCosmetic(player.getInventory().getHelmet())) {
            for (int raw : event.getRawSlots()) {
                if (event.getView().getInventory(raw) instanceof PlayerInventory
                        && event.getView().convertSlot(raw) == HELMET_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    /** ПКМ шлемом/тыквой при надетой шляпе: ванильная замена положила бы шляпу в руку. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        Player player = event.getPlayer();
        if (HatItems.isCosmetic(player.getInventory().getHelmet()) && isHeadItem(event.getItem())) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (event.getClickedBlock() == null) {
                event.setCancelled(true);
            }
            plugin.messages().send(player, "hat.slot-locked");
            plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void dispense(BlockDispenseArmorEvent event) {
        if (event.getTargetEntity() instanceof Player player && HatItems.isCosmetic(player.getInventory().getHelmet())
                && isHeadItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void drop(PlayerDropItemEvent event) {
        if (HatItems.isService(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.display().purgeServiceItems(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (HatItems.isService(event.getMainHandItem()) || HatItems.isService(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (HatItems.isService(event.getPlayerItem()) || HatItems.isService(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void spawn(ItemSpawnEvent event) {
        if (HatItems.isService(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void pickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (HatItems.isService(item.getItemStack())) {
            event.setCancelled(true);
            item.remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void hopper(InventoryPickupItemEvent event) {
        if (HatItems.isService(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    /** Страховка: после закрытия любого окна служебные предметы вне слота шлема удаляются. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    plugin.display().purgeServiceItems(player);
                }
            });
        }
    }
}
