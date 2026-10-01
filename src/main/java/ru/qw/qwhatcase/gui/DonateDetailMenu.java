package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Map;

/** «Покупка шляпы» из PTrap: шляпа, цена, кнопка «Купить в Telegram», «Назад». */
public final class DonateDetailMenu extends Menu {
    private final Hat hat;
    private final int returnPage;

    public DonateDetailMenu(QWHatCasePlugin plugin, Player viewer, Hat hat, int returnPage) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.donate.detail-title"));
        this.hat = hat;
        this.returnPage = returnPage;
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        Map<String, Object> ph = Placeholders.of("price", plugin.catalog().settings().donatePrice(),
                "user", plugin.catalog().settings().telegram());
        set(13, HatItems.icon(hat, hat.coloredName(), concat(hat.lore(), msg().lines("menu.donate.detail-lore", ph)), false,
                plugin.display().baseStats()));
        set(11, button(Material.EMERALD, "menu.donate.buy-name", "menu.donate.buy-lore", ph), click -> {
            viewer.closeInventory();
            DonateShopMenu.sendTelegram(plugin, viewer, hat);
        });
        set(15, button(Material.ARROW, "menu.common.back-name", "menu.donate.back-lore", ph),
                click -> new DonateShopMenu(plugin, viewer, returnPage).open());
    }
}
