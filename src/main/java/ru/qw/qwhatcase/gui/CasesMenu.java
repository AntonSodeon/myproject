package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.util.List;
import java.util.Map;

/**
 * Список кейсов. Для каждого кейса две РАЗНЫЕ кнопки: сверху иконка «Посмотреть содержимое»,
 * под ней отдельная кнопка «Открыть» (зелёная — ключей хватает, серая — нет).
 */
public final class CasesMenu extends Menu {
    private static final int[] ICON_SLOTS = {10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34};
    private final int page;

    public CasesMenu(QWHatCasePlugin plugin, Player viewer, int page) {
        super(plugin, viewer, 6, plugin.messages().raw("menu.cases.title"));
        this.page = page;
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        List<CaseDef> cases = plugin.catalog().enabledCases();
        int pages = pages(cases.size(), ICON_SLOTS.length);
        int current = Math.max(0, Math.min(page, pages - 1));
        Profile profile = plugin.profiles().get(viewer);
        for (int i = 0; i < ICON_SLOTS.length; i++) {
            int index = current * ICON_SLOTS.length + i;
            if (index >= cases.size()) {
                break;
            }
            CaseDef def = cases.get(index);
            long keys = profile == null ? 0 : profile.keys(def.keyType());
            Map<String, Object> ph = Placeholders.of("case", def.name(), "keys", keys, "cost", def.keyCost(),
                    "rewards", def.rewards().size(), "key", def.keyType());
            ItemStack icon = HatItems.button(def.iconMaterial(), Material.CHEST, def.iconItemModel(), def.iconCustomModelData(),
                    def.name(), concat(def.description(), msg().lines("menu.cases.icon-lore", ph)), false);
            set(ICON_SLOTS[i], icon, click -> new CaseContentMenu(plugin, viewer, def.id(), 0).open());
            boolean enough = keys >= def.keyCost();
            ItemStack openButton = button(enough ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                    "menu.cases.open-name", enough ? "menu.cases.open-lore" : "menu.cases.open-no-keys-lore", ph);
            set(ICON_SLOTS[i] + 9, openButton, click -> {
                viewer.closeInventory();
                plugin.openings().open(viewer, def.id(), click.isShiftClick() && plugin.catalog().settings().allowSkipAnimation());
            });
        }
        if (cases.isEmpty()) {
            set(22, button(Material.BARRIER, "menu.cases.empty-name", "menu.cases.empty-lore", Map.of()));
        }
        Map<String, Object> ph = Placeholders.of("page", current + 1, "pages", pages);
        set(49, button(Material.ARROW, "menu.common.back-name", "menu.common.back-main-lore", ph),
                click -> new MainMenu(plugin, viewer).open());
        if (current > 0) {
            set(45, button(Material.SPECTRAL_ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                    click -> new CasesMenu(plugin, viewer, current - 1).open());
        }
        if (current + 1 < pages) {
            set(53, button(Material.SPECTRAL_ARROW, "menu.common.next-name", "menu.common.page-lore", ph),
                    click -> new CasesMenu(plugin, viewer, current + 1).open());
        }
        if (plugin.catalog().settings().externalShopEnabled()) {
            set(51, button(Material.GOLD_INGOT, "menu.cases.buy-keys-name", "menu.cases.buy-keys-lore", ph), click -> {
                viewer.closeInventory();
                viewer.sendMessage(Text.chat(msg().raw("prefix") + msg().raw("cases.buy-keys-link"))
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(plugin.catalog().settings().externalShopUrl())));
            });
        }
        if (!plugin.packs().hasModels(viewer)) {
            set(47, button(Material.YELLOW_STAINED_GLASS_PANE, "menu.common.no-pack-name", "menu.common.no-pack-lore", ph));
        }
    }
}
