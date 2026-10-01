package ru.qw.qwhatcase.service;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.config.Settings;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Optional;

/**
 * Отображение шляпы в слоте шлема (как в старом плагине). Аудит показал, что других механизмов
 * отображения (пакетные шляпы, сущности-пассажиры) старый плагин не использовал, поэтому
 * одновременное ношение брони и шляпы не поддерживается: настоящий шлем никогда не удаляется —
 * он переносится в инвентарь либо надевание отклоняется с объяснением.
 */
public final class HatDisplayService {
    public enum Result {
        EQUIPPED, ALREADY_WORN, NOT_OWNED, UNKNOWN_HAT, HELMET_OCCUPIED, INVENTORY_FULL, HELMET_CURSED, NOT_LOADED
    }

    private final QWHatCasePlugin plugin;

    public HatDisplayService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public Result equip(Player player, String hatId) {
        Profile profile = plugin.profiles().get(player);
        if (profile == null) {
            return Result.NOT_LOADED;
        }
        Optional<Hat> hat = plugin.catalog().hat(hatId);
        if (hat.isEmpty()) {
            return Result.UNKNOWN_HAT;
        }
        if (!profile.owns(hatId)) {
            return Result.NOT_OWNED;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getHelmet();
        if (hatId.equals(HatItems.cosmeticId(current))) {
            return Result.ALREADY_WORN;
        }
        if (current != null && !current.isEmpty() && !HatItems.isCosmetic(current)) {
            if (current.containsEnchantment(Enchantment.BINDING_CURSE)) {
                return Result.HELMET_CURSED;
            }
            if (plugin.catalog().settings().helmetPolicy() == Settings.HelmetPolicy.REFUSE) {
                return Result.HELMET_OCCUPIED;
            }
            int free = inventory.firstEmpty();
            if (free < 0) {
                return Result.INVENTORY_FULL;
            }
            // Шлем перемещается в конкретный пустой слот: предмет не может потеряться или раздвоиться.
            inventory.setItem(free, current);
            inventory.setHelmet(null);
        }
        inventory.setHelmet(HatItems.cosmetic(hat.get()));
        profile.setSelectedHat(hatId);
        plugin.storage().submit(db -> {
            db.setSelectedHat(player.getUniqueId(), hatId);
            return null;
        });
        return Result.EQUIPPED;
    }

    /** @return true, если шляпа была надета и снята. */
    public boolean unequip(Player player) {
        Profile profile = plugin.profiles().get(player);
        boolean removed = stripCosmetic(player);
        if (profile != null && profile.selectedHat() != null) {
            profile.setSelectedHat(null);
            removed = true;
            plugin.storage().submit(db -> {
                db.setSelectedHat(player.getUniqueId(), null);
                return null;
            });
        }
        return removed;
    }

    /** Убирает косметический предмет из слота шлема (без изменения выбора). */
    public boolean stripCosmetic(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        if (HatItems.isCosmetic(helmet)) {
            player.getInventory().setHelmet(null);
            return true;
        }
        return false;
    }

    /** Удаляет служебные предметы плагина из всех слотов, кроме надетой шляпы. */
    public int purgeServiceItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.isEmpty()) {
                continue;
            }
            boolean helmetSlot = slot == 39;
            if (HatItems.isGui(item) || (HatItems.isCosmetic(item) && !helmetSlot)) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        if (HatItems.isService(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    /** Восстанавливает выбранную шляпу (вход, возрождение, перезагрузка). */
    public void apply(Player player) {
        Profile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        String selected = profile.selectedHat();
        ItemStack helmet = player.getInventory().getHelmet();
        if (selected == null) {
            stripCosmetic(player);
            return;
        }
        Optional<Hat> hat = plugin.catalog().hat(selected);
        if (hat.isEmpty() || !profile.owns(selected)) {
            stripCosmetic(player);
            profile.setSelectedHat(null);
            plugin.storage().submit(db -> {
                db.setSelectedHat(player.getUniqueId(), null);
                return null;
            });
            return;
        }
        if (helmet == null || helmet.isEmpty() || HatItems.isCosmetic(helmet)) {
            player.getInventory().setHelmet(HatItems.cosmetic(hat.get()));
        } else {
            // Настоящий шлем имеет приоритет: шляпа не надевается поверх него.
            plugin.messages().send(player, "hat.helmet-blocks-restore", Placeholders.of("hat", hat.get().coloredName()));
        }
    }

    /** Пересоздаёт предмет надетой шляпы после перезагрузки конфигурации (название/модель могли измениться). */
    public void refresh(Player player) {
        if (HatItems.isCosmetic(player.getInventory().getHelmet())) {
            apply(player);
        }
    }
}
