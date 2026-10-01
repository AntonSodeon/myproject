package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Map;

public final class ConfirmPurchaseMenu extends Menu {
    private final Hat hat;
    private final int returnPage;

    public ConfirmPurchaseMenu(QWHatCasePlugin plugin, Player viewer, Hat hat, int returnPage) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.confirm.title"));
        this.hat = hat;
        this.returnPage = returnPage;
    }

    @Override
    protected void render() {
        fill(Material.GRAY_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        long tokens = profile == null ? 0 : profile.tokens();
        Map<String, Object> ph = Placeholders.of("hat", hat.coloredName(), "price", hat.shopPrice(), "tokens", tokens,
                "after", tokens - hat.shopPrice());
        set(13, HatItems.icon(hat, hat.coloredName(), msg().lines("menu.confirm.hat-lore", ph), false));
        set(11, button(Material.LIME_CONCRETE, "menu.confirm.yes-name", "menu.confirm.yes-lore", ph), click -> {
            viewer.closeInventory();
            plugin.shop().buy(viewer, hat.id());
        });
        set(15, button(Material.RED_CONCRETE, "menu.confirm.no-name", "menu.confirm.no-lore", ph),
                click -> new ShopMenu(plugin, viewer, returnPage).open());
    }
}
