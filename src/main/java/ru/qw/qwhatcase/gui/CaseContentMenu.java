package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.CaseReward;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Format;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Содержимое кейса: модель, название, редкость, фактическая вероятность, наличие, компенсация. */
public final class CaseContentMenu extends Menu {
    private static final int PER_PAGE = 45;
    private final String caseId;
    private final int page;

    public CaseContentMenu(QWHatCasePlugin plugin, Player viewer, String caseId, int page) {
        super(plugin, viewer, 6, plugin.messages().raw("menu.content.title", Placeholders.of("case",
                plugin.catalog().caseDef(caseId).map(CaseDef::name).orElse(caseId))));
        this.caseId = caseId;
        this.page = page;
    }

    @Override
    protected void render() {
        CaseDef def = plugin.catalog().caseDef(caseId).orElse(null);
        fill(Material.GRAY_STAINED_GLASS_PANE);
        if (def == null || !def.enabled()) {
            set(22, button(Material.BARRIER, "menu.content.disabled-name", "menu.content.disabled-lore", Map.of()));
            set(49, button(Material.ARROW, "menu.common.back-name", "menu.common.back-lore", Map.of()),
                    click -> new CasesMenu(plugin, viewer, 0).open());
            return;
        }
        Profile profile = plugin.profiles().get(viewer);
        List<CaseReward> rewards = def.rewards().stream()
                .sorted(Comparator.comparingDouble(CaseReward::weight)
                        .thenComparing(r -> -r.hat().rarity().order())
                        .thenComparing(r -> r.hat().id()))
                .toList();
        int pages = pages(rewards.size(), PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int index = current * PER_PAGE + i;
            if (index >= rewards.size()) {
                set(i, null);
                continue;
            }
            CaseReward reward = rewards.get(index);
            boolean owned = profile != null && profile.owns(reward.hat().id());
            Map<String, Object> ph = Placeholders.of(
                    "rarity", reward.hat().rarity().color() + reward.hat().rarity().name(),
                    "category", plugin.catalog().categories().get(reward.hat().category().id()).name(),
                    "chance", Format.percent(def.chancePercent(reward)),
                    "owned", msg().raw(owned ? "menu.content.owned-yes" : "menu.content.owned-no"),
                    "compensation", def.compensationFor(reward.hat()));
            set(i, HatItems.icon(reward.hat(), reward.hat().coloredName(),
                    concat(reward.hat().lore(), msg().lines("menu.content.reward-lore", ph)), false,
                    plugin.display().baseStats()));
        }
        long keys = profile == null ? 0 : profile.keys(def.keyType());
        Map<String, Object> ph = Placeholders.of("case", def.name(), "keys", keys, "cost", def.keyCost(),
                "rewards", rewards.size(), "page", current + 1, "pages", pages);
        set(45, button(Material.ARROW, "menu.common.back-name", "menu.common.back-lore", ph),
                click -> new CasesMenu(plugin, viewer, 0).open());
        if (current > 0) {
            set(48, button(Material.SPECTRAL_ARROW, "menu.common.prev-name", "menu.common.page-lore", ph),
                    click -> new CaseContentMenu(plugin, viewer, caseId, current - 1).open());
        }
        set(49, HatItems.button(def.iconMaterial(), Material.CHEST, def.iconItemModel(), def.iconCustomModelData(),
                def.name(), concat(def.description(), msg().lines("menu.content.info-lore", ph)), false));
        if (current + 1 < pages) {
            set(50, button(Material.SPECTRAL_ARROW, "menu.common.next-name", "menu.common.page-lore", ph),
                    click -> new CaseContentMenu(plugin, viewer, caseId, current + 1).open());
        }
        String deny = plugin.openings().denyReason(viewer, def);
        ph.put("reason", deny == null ? "" : msg().raw(deny, ph));
        set(53, button(deny == null ? Material.LIME_CONCRETE : Material.RED_CONCRETE, "menu.content.open-name",
                deny == null ? "menu.content.open-lore" : "menu.content.open-denied-lore", ph), click -> {
            viewer.closeInventory();
            plugin.openings().open(viewer, def.id(), click.isShiftClick() && plugin.catalog().settings().allowSkipAnimation());
        });
        if (!plugin.packs().hasModels(viewer)) {
            set(46, button(Material.YELLOW_STAINED_GLASS_PANE, "menu.common.no-pack-name", "menu.common.no-pack-lore", ph));
        }
    }
}
