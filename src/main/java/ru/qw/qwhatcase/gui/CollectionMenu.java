package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Category;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.config.Rarity;
import ru.qw.qwhatcase.service.HatDisplayService;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Format;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Коллекция: страницы, фильтры по редкости и категории, «Полученные / Все», сортировка, выделение надетой. */
public final class CollectionMenu extends Menu {
    public enum Sort {
        NAME, RARITY, DATE
    }

    private static final int PER_PAGE = 45;
    private int page;
    private String rarity;
    private String category;
    private boolean ownedOnly = true;
    private Sort sort = Sort.RARITY;

    public CollectionMenu(QWHatCasePlugin plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().raw("menu.collection.title"));
    }

    private List<Hat> visible(Profile profile) {
        List<Hat> list = new ArrayList<>();
        for (Hat hat : plugin.catalog().hats().values()) {
            boolean owned = profile != null && profile.owns(hat.id());
            if (ownedOnly && !owned) {
                continue;
            }
            if (rarity != null && !hat.rarity().id().equals(rarity)) {
                continue;
            }
            if (category != null && !hat.category().id().equals(category)) {
                continue;
            }
            list.add(hat);
        }
        Comparator<Hat> byName = Comparator.comparing(h -> Text.strip(h.name()).toLowerCase());
        Comparator<Hat> comparator = switch (sort) {
            case NAME -> byName;
            case RARITY -> Comparator.<Hat>comparingInt(h -> -h.rarity().order()).thenComparing(byName);
            case DATE -> Comparator.<Hat>comparingLong(h -> {
                Long at = profile == null ? null : profile.obtainedAt(h.id());
                return at == null ? Long.MIN_VALUE : -at;
            }).thenComparing(byName);
        };
        list.sort(comparator);
        return list;
    }

    @Override
    protected void render() {
        fill(Material.BLACK_STAINED_GLASS_PANE);
        Profile profile = plugin.profiles().get(viewer);
        List<Hat> hats = visible(profile);
        int pages = pages(hats.size(), PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        String worn = profile == null ? null : profile.selectedHat();
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= hats.size()) {
                set(i, null);
                continue;
            }
            Hat hat = hats.get(index);
            Long at = profile == null ? null : profile.obtainedAt(hat.id());
            boolean owned = at != null;
            boolean isWorn = hat.id().equals(worn);
            Map<String, Object> ph = Placeholders.of("rarity", hat.rarity().color() + hat.rarity().name(),
                    "category", hat.category().name(), "date", owned ? Format.date(at) : "-", "id", hat.id());
            String loreKey = isWorn ? "menu.collection.worn-lore" : owned ? "menu.collection.owned-lore" : "menu.collection.locked-lore";
            String name = owned ? hat.coloredName() : msg().raw("menu.collection.locked-name", Placeholders.of("hat", Text.strip(hat.name())));
            set(i, HatItems.icon(hat, name, concat(hat.lore(), msg().lines(loreKey, ph)), isWorn), click -> {
                if (!owned) {
                    msg().send(viewer, "hat.locked");
                    return;
                }
                if (isWorn) {
                    return;
                }
                HatDisplayService.Result result = plugin.display().equip(viewer, hat.id());
                plugin.commands().reportEquip(viewer, hat, result);
                redraw();
            });
        }

        int ownedCount = profile == null ? 0 : (int) profile.owned().keySet().stream().filter(plugin.catalog().hats()::containsKey).count();
        Map<String, Object> ph = Placeholders.of("page", page + 1, "pages", pages, "owned", ownedCount,
                "total", plugin.catalog().hats().size(), "shown", hats.size(),
                "rarity", rarity == null ? msg().raw("menu.collection.filter-all")
                        : plugin.catalog().rarities().get(rarity).color() + plugin.catalog().rarities().get(rarity).name(),
                "category", category == null ? msg().raw("menu.collection.filter-all") : plugin.catalog().categories().get(category).name(),
                "mode", msg().raw(ownedOnly ? "menu.collection.mode-owned" : "menu.collection.mode-all"),
                "sort", msg().raw("menu.collection.sort-by-" + sort.name().toLowerCase()),
                "tokens", profile == null ? 0 : profile.tokens());

        if (page > 0) {
            set(45, button(Material.SPECTRAL_ARROW, "menu.common.prev-name", "menu.common.page-lore", ph), click -> {
                page--;
                redraw();
            });
        }
        set(46, button(Material.AMETHYST_SHARD, "menu.collection.rarity-name", "menu.collection.filter-lore", ph), click -> {
            rarity = cycle(new ArrayList<>(plugin.catalog().rarities().values().stream().map(Rarity::id).toList()), rarity, click);
            page = 0;
            redraw();
        });
        set(47, button(Material.BOOKSHELF, "menu.collection.category-name", "menu.collection.filter-lore", ph), click -> {
            category = cycle(new ArrayList<>(plugin.catalog().categories().values().stream().map(Category::id).toList()), category, click);
            page = 0;
            redraw();
        });
        set(48, button(ownedOnly ? Material.ENDER_EYE : Material.ENDER_PEARL, "menu.collection.mode-name", "menu.collection.mode-lore", ph), click -> {
            ownedOnly = !ownedOnly;
            page = 0;
            redraw();
        });
        set(49, button(Material.ARROW, "menu.common.back-name", "menu.common.back-main-lore", ph),
                click -> new MainMenu(plugin, viewer).open());
        set(50, button(Material.HOPPER, "menu.collection.sort-name", "menu.collection.sort-lore", ph), click -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            page = 0;
            redraw();
        });
        set(51, button(Material.BARRIER, "menu.collection.unequip-name", "menu.collection.unequip-lore", ph), click -> {
            if (plugin.display().unequip(viewer)) {
                msg().send(viewer, "hat.unequipped");
            } else {
                msg().send(viewer, "hat.nothing-to-unequip");
            }
            redraw();
        });
        set(52, button(Material.EMERALD, "menu.collection.shop-name", "menu.collection.shop-lore", ph),
                click -> new ShopMenu(plugin, viewer, 0).open());
        if (page + 1 < pages) {
            set(53, button(Material.SPECTRAL_ARROW, "menu.common.next-name", "menu.common.page-lore", ph), click -> {
                page++;
                redraw();
            });
        }
        if (hats.isEmpty()) {
            set(22, button(Material.STRUCTURE_VOID, "menu.collection.empty-name", "menu.collection.empty-lore", ph));
        }
    }

    /** ЛКМ — следующее значение фильтра, ПКМ — предыдущее; null = «все». */
    private static String cycle(List<String> values, String current, ClickType click) {
        values.add(0, null);
        int index = values.indexOf(current);
        int next = click.isRightClick() ? index - 1 : index + 1;
        if (next < 0) {
            next = values.size() - 1;
        }
        return values.get(next % values.size());
    }
}
