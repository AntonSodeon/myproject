package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.PackService;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Map;

/** Главное меню: Кейсы, Мои шляпки, Каталог за жетоны, Снять шляпку, Ресурс-пак. */
public final class MainMenu extends Menu {
    public MainMenu(QWHatCasePlugin plugin, Player viewer) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.main.title"));
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        int owned = profile == null ? 0 : (int) profile.owned().keySet().stream().filter(id -> plugin.catalog().hats().containsKey(id)).count();
        Map<String, Object> ph = Placeholders.of("owned", owned, "total", plugin.catalog().hats().size(),
                "tokens", profile == null ? 0 : profile.tokens(), "player", viewer.getName());

        ItemStack head = button(Material.PLAYER_HEAD, "menu.main.stats-name", "menu.main.stats-lore", ph);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(viewer));
        set(4, head);

        set(10, button(Material.CHEST, "menu.main.cases-name", "menu.main.cases-lore", ph),
                click -> new CasesMenu(plugin, viewer, 0).open());
        set(12, button(Material.ARMOR_STAND, "menu.main.collection-name", "menu.main.collection-lore", ph),
                click -> new CollectionMenu(plugin, viewer).open());
        set(14, button(Material.EMERALD, "menu.main.shop-name", "menu.main.shop-lore", ph),
                click -> new ShopMenu(plugin, viewer, 0).open());
        set(16, packButton(ph), click -> {
            viewer.closeInventory();
            plugin.commands().offerPack(viewer);
        });
        if (plugin.catalog().settings().donateShopEnabled()) {
            set(23, button(Material.GOLD_INGOT, "menu.main.donate-name", "menu.main.donate-lore", ph),
                    click -> new DonateShopMenu(plugin, viewer, 0).open());
        }
        set(21, button(Material.BARRIER, "menu.main.unequip-name", "menu.main.unequip-lore", ph), click -> {
            if (plugin.display().unequip(viewer)) {
                msg().send(viewer, "hat.unequipped");
            } else {
                msg().send(viewer, "hat.nothing-to-unequip");
            }
            redraw();
        });
        if (!plugin.packs().hasModels(viewer)) {
            set(26, button(Material.YELLOW_STAINED_GLASS_PANE, "menu.common.no-pack-name", "menu.common.no-pack-lore", ph));
        }
    }

    private ItemStack packButton(Map<String, Object> ph) {
        PackService.State state = plugin.packs().state(viewer);
        String stateKey = !plugin.packs().tracking() ? "pack.state.not-tracked" : switch (state) {
            case LOADED -> "pack.state.loaded";
            case DECLINED -> "pack.state.declined";
            case FAILED -> "pack.state.failed";
            case PENDING -> "pack.state.pending";
        };
        ph.put("state", msg().raw(stateKey));
        ItemStack item = button(Material.PAINTING, "menu.main.pack-name", "menu.main.pack-lore", ph);
        if (state == PackService.State.LOADED) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }
}
