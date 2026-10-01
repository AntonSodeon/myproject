package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatDisplayService;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Map;

/** Действия со шляпой из коллекции (как в PTrap): «Наложить чары», «Надеть», «Назад». */
public final class HatActionsMenu extends Menu {
    private final Hat hat;

    public HatActionsMenu(QWHatCasePlugin plugin, Player viewer, Hat hat) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.actions.title", Placeholders.of("hat", hat.coloredName())));
        this.hat = hat;
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        if (profile == null || !profile.owns(hat.id())) {
            msg().send(viewer, "hat.not-owned", Placeholders.of("hat", hat.coloredName()));
            set(22, button(Material.ARROW, "menu.common.back-name", "menu.actions.back-lore", Map.of()),
                    click -> new CollectionMenu(plugin, viewer).open());
            return;
        }
        Map<String, Object> ph = Placeholders.of("hat", hat.coloredName());
        set(13, HatItems.icon(hat, hat.coloredName(), hat.lore(), false, plugin.display().stats(profile, hat.id())));
        if (plugin.catalog().settings().bookEnchanting()) {
            set(11, button(Material.ENCHANTING_TABLE, "menu.actions.enchant-name", "menu.actions.enchant-lore", ph),
                    click -> new EnchantMenu(plugin, viewer, hat).open());
        }
        set(15, button(Material.DIAMOND_HELMET, "menu.actions.equip-name", "menu.actions.equip-lore", ph), click -> {
            HatDisplayService.Result result = plugin.display().equip(viewer, hat.id());
            plugin.commands().reportEquip(viewer, hat, result);
            if (result == HatDisplayService.Result.EQUIPPED || result == HatDisplayService.Result.ALREADY_WORN) {
                viewer.closeInventory();
            }
        });
        set(22, button(Material.ARROW, "menu.common.back-name", "menu.actions.back-lore", ph),
                click -> new CollectionMenu(plugin, viewer).open());
    }
}
