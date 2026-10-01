package ru.qw.qwhatcase.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Магазин шляп PTrap (/hat menu): каталог всех шляп с ценой и покупкой через Telegram. */
public final class DonateShopMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final int page;

    public DonateShopMenu(QWHatCasePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.messages().raw("menu.donate.title"));
        this.page = page;
    }

    static List<Hat> hats(QWHatCasePlugin plugin) {
        return plugin.catalog().hats().values().stream().filter(Hat::available)
                .sorted(Comparator.comparingInt(Hat::customModelData).thenComparing(Hat::id)).toList();
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        List<Hat> hats = hats(plugin);
        int pages = pages(hats.size(), PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Profile profile = plugin.profiles().get(viewer);
        String price = plugin.catalog().settings().donatePrice();
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= hats.size()) {
                set(i, null);
                continue;
            }
            Hat hat = hats.get(index);
            boolean owned = profile != null && profile.owns(hat.id());
            Map<String, Object> ph = Placeholders.of("price", price, "rarity", hat.rarity().color() + hat.rarity().name());
            set(i, HatItems.icon(hat, hat.coloredName(), concat(hat.lore(), msg().lines(owned ? "menu.donate.owned-lore" : "menu.donate.hat-lore", ph)),
                    false, plugin.display().baseStats()), click -> new DonateDetailMenu(plugin, viewer, hat, current).open());
        }
        Map<String, Object> ph = Placeholders.of("page", current + 1, "pages", pages, "price", price);
        if (current > 0) {
            set(45, button(Material.ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                    click -> new DonateShopMenu(plugin, viewer, current - 1).open());
        }
        set(49, button(Material.PAPER, "menu.donate.page-name", "menu.donate.page-lore", ph),
                click -> new MainMenu(plugin, viewer).open());
        if (current + 1 < pages) {
            set(53, button(Material.LIME_CONCRETE, "menu.common.next-name", "menu.common.page-lore", ph),
                    click -> new DonateShopMenu(plugin, viewer, current + 1).open());
        }
    }

    /** Ссылка на Telegram для покупки (как в PTrap). */
    public static void sendTelegram(QWHatCasePlugin plugin, Player player, Hat hat) {
        String user = plugin.catalog().settings().telegram();
        String url = "https://t.me/" + user;
        player.sendMessage(Text.chat(plugin.messages().raw("donate.buy-line", Placeholders.of("hat", hat.coloredName())))
                .append(Text.chat(plugin.messages().raw("donate.link", Placeholders.of("user", user)))
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(Component.text(plugin.messages().raw("donate.hover")))));
        plugin.messages().sendPlain(player, "donate.hint", Map.of());
    }
}
