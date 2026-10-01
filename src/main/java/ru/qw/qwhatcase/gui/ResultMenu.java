package ru.qw.qwhatcase.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.HatItems;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.storage.OpeningResult;

import java.util.Map;

/** Итог открытия: выпавшая шляпа, пометка о дубликате, жетоны и новый баланс. */
public final class ResultMenu extends Menu {
    private final CaseDef caseDef;
    private final Hat hat;
    private final OpeningResult result;

    public ResultMenu(QWHatCasePlugin plugin, Player viewer, CaseDef caseDef, Hat hat, OpeningResult result) {
        super(plugin, viewer, 3, plugin.messages().raw("menu.result.title"));
        this.caseDef = caseDef;
        this.hat = hat;
        this.result = result;
    }

    @Override
    protected void render() {
        fill(HatItems.material(hat.rarity().pane(), Material.GRAY_STAINED_GLASS_PANE));
        Map<String, Object> ph = plugin.openings().placeholders(viewer, caseDef, hat, result);
        boolean duplicate = result.outcome() == OpeningResult.Outcome.DUPLICATE;
        set(13, HatItems.icon(hat, hat.coloredName(),
                msg().lines(duplicate ? "menu.result.duplicate-lore" : "menu.result.new-lore", ph), !duplicate,
                plugin.display().stats(plugin.profiles().get(viewer), hat.id())));
        set(11, button(Material.ARMOR_STAND, "menu.result.collection-name", "menu.result.collection-lore", ph),
                click -> new CollectionMenu(plugin, viewer).open());
        Profile profile = plugin.profiles().get(viewer);
        long keys = profile == null ? 0 : profile.keys(caseDef.keyType());
        ph.put("keys", keys);
        ph.put("cost", caseDef.keyCost());
        boolean canAgain = keys >= caseDef.keyCost();
        set(15, button(canAgain ? Material.LIME_DYE : Material.GRAY_DYE, "menu.result.again-name",
                canAgain ? "menu.result.again-lore" : "menu.result.again-no-keys-lore", ph),
                click -> plugin.openings().open(viewer, caseDef.id(), click.isShiftClick() && plugin.catalog().settings().allowSkipAnimation()));
        set(22, button(Material.ARROW, "menu.common.back-name", "menu.result.back-lore", ph),
                click -> new CaseContentMenu(plugin, viewer, caseDef.id(), 0).open());
    }
}
