package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Каталог за жетоны: постоянный список шляп с ценой. */
public final class ShopMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final int page;

    public ShopMenu(QWHatCasePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.messages().raw("menu.shop.title"));
        this.page = page;
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        long tokens = profile == null ? 0 : profile.tokens();
        List<Hat> hats = plugin.catalog().shopHats().stream()
                .sorted(Comparator.comparingLong(Hat::shopPrice).thenComparing(Hat::id)).toList();
        int pages = pages(hats.size(), PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= hats.size()) {
                set(i, null);
                continue;
            }
            Hat hat = hats.get(index);
            boolean owned = profile != null && profile.owns(hat.id());
            boolean affordable = tokens >= hat.shopPrice();
            Map<String, Object> ph = Placeholders.of("price", hat.shopPrice(), "tokens", tokens,
                    "rarity", hat.rarity().color() + hat.rarity().name(), "category", hat.category().name());
            String loreKey = owned ? "menu.shop.owned-lore" : affordable ? "menu.shop.buy-lore" : "menu.shop.expensive-lore";
            set(i, HatItems.icon(hat, hat.coloredName(), concat(hat.lore(), msg().lines(loreKey, ph)), false), click -> {
                if (owned) {
                    msg().send(viewer, "shop.already-owned");
                    return;
                }
                new ConfirmPurchaseMenu(plugin, viewer, hat, current).open();
            });
        }
        Map<String, Object> ph = Placeholders.of("page", current + 1, "pages", pages, "tokens", tokens);
        if (current > 0) {
            set(45, button(Material.SPECTRAL_ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                    click -> new ShopMenu(plugin, viewer, current - 1).open());
        }
        set(49, button(Material.ARROW, "menu.common.back-name", "menu.common.back-main-lore", ph),
                click -> new MainMenu(plugin, viewer).open());
        set(47, button(Material.SUNFLOWER, "menu.shop.balance-name", "menu.shop.balance-lore", ph));
        if (current + 1 < pages) {
            set(53, button(Material.SPECTRAL_ARROW, "menu.common.next-name", "menu.common.page-lore", ph),
                    click -> new ShopMenu(plugin, viewer, current + 1).open());
        }
        if (hats.isEmpty()) {
            set(22, button(Material.BARRIER, "menu.shop.empty-name", "menu.shop.empty-lore", ph));
        }
    }
}
