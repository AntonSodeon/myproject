package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Map;
import java.util.UUID;

/**
 * Наложение чар на шляпу зачарованной книгой (механика «виртуальной наковальни» PTrap):
 * игрок кликает по книге в своём инвентаре, книга расходуется, все её чары переносятся на шляпу
 * без ограничений совместимости и без затрат опыта. Уровень с книги заменяет прежний.
 *
 * Защита от дюпа: книга сначала изымается из инвентаря (одна штука), затем чары сохраняются в БД;
 * при ошибке БД книга возвращается игроку.
 */
public final class EnchantMenu extends Menu {
    private final Hat hat;
    private boolean pending;

    public EnchantMenu(QWHatCasePlugin plugin, Player viewer, Hat hat) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.enchant.title", Placeholders.of("hat", hat.coloredName())));
        this.hat = hat;
    }

    @Override
    protected void render() {
        fill(Material.PURPLE_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        set(13, HatItems.icon(hat, hat.coloredName(), hat.lore(), false, plugin.display().stats(profile, hat.id())));
        set(4, button(Material.ENCHANTED_BOOK, "menu.enchant.info-name", "menu.enchant.info-lore", Map.of()));
        set(22, button(Material.ARROW, "menu.common.back-name", "menu.actions.back-lore", Map.of()),
                click -> new HatActionsMenu(plugin, viewer, hat).open());
    }

    @Override
    public void handleBottomClick(int slot, ItemStack clicked, ClickType type) {
        if (pending) {
            return;
        }
        if (clicked == null || clicked.getType() != Material.ENCHANTED_BOOK
                || !(clicked.getItemMeta() instanceof EnchantmentStorageMeta meta) || meta.getStoredEnchants().isEmpty()) {
            msg().send(viewer, "enchant.wrong-item");
            return;
        }
        Profile profile = plugin.profiles().get(viewer);
        if (profile == null || !profile.owns(hat.id())) {
            msg().send(viewer, "hat.not-owned", Placeholders.of("hat", hat.coloredName()));
            return;
        }
        ItemStack inSlot = viewer.getInventory().getItem(slot);
        if (inSlot == null || !inSlot.isSimilar(clicked)) {
            return;
        }
        Map<String, Integer> book = HatItems.enchantKeys(meta.getStoredEnchants());
        // Изымаем ровно одну книгу до записи в БД.
        ItemStack escrow = inSlot.asOne();
        if (inSlot.getAmount() > 1) {
            inSlot.setAmount(inSlot.getAmount() - 1);
        } else {
            viewer.getInventory().setItem(slot, null);
        }
        pending = true;
        UUID uuid = viewer.getUniqueId();
        String hatId = hat.id();
        plugin.storage().run(db -> db.enchantHat(uuid, hatId, book, viewer.getName()), result -> {
            pending = false;
            if (result.isEmpty()) {
                giveBack(escrow);
                msg().send(viewer, "hat.not-owned", Placeholders.of("hat", hat.coloredName()));
                return;
            }
            Profile current = plugin.profiles().get(viewer);
            if (current != null) {
                current.setEnchants(hatId, result.get());
            }
            if (hatId.equals(HatItems.cosmeticId(viewer.getInventory().getHelmet()))) {
                plugin.display().refresh(viewer);
            }
            msg().send(viewer, "enchant.done", Placeholders.of("hat", hat.coloredName()));
            if (viewer.getOpenInventory().getTopInventory().getHolder(false) == this) {
                redraw();
            }
        }, error -> {
            pending = false;
            giveBack(escrow);
            msg().send(viewer, "error.database");
        });
    }

    private void giveBack(ItemStack book) {
        if (!viewer.isOnline()) {
            plugin.getLogger().warning("Книга для чар не возвращена (игрок вышел): " + viewer.getName() + " " + book);
            return;
        }
        viewer.getInventory().addItem(book).values()
                .forEach(rest -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), rest));
    }
}
