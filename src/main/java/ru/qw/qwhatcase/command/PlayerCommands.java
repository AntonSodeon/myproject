package ru.qw.qwhatcase.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.config.Settings;
import ru.qw.qwhatcase.gui.CaseContentMenu;
import ru.qw.qwhatcase.gui.CasesMenu;
import ru.qw.qwhatcase.gui.CollectionMenu;
import ru.qw.qwhatcase.gui.MainMenu;
import ru.qw.qwhatcase.gui.ShopMenu;
import ru.qw.qwhatcase.service.HatDisplayService;
import ru.qw.qwhatcase.service.Profile;
import ru.qw.qwhatcase.util.Placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /hats и /cases. */
public final class PlayerCommands implements CommandExecutor, TabCompleter {
    private final QWHatCasePlugin plugin;

    public PlayerCommands(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("hats") && args.length >= 1 && args[0].equalsIgnoreCase("grant")) {
            grant(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.players-only");
            return true;
        }
        if (command.getName().equalsIgnoreCase("cases")) {
            cases(player, args);
        } else if (args.length == 0 && label.equalsIgnoreCase("hat")) {
            // Как в PTrap: /hat без аргументов — «Мои шляпы».
            if (check(player, "qwhatcase.menu")) {
                new CollectionMenu(plugin, player).open();
            }
        } else {
            hats(player, args);
        }
        return true;
    }

    private boolean check(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(player, "error.no-permission");
        return false;
    }

    private void hats(Player player, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (check(player, "qwhatcase.menu")) {
                    new MainMenu(plugin, player).open();
                }
            }
            case "collection" -> {
                if (check(player, "qwhatcase.menu")) {
                    new CollectionMenu(plugin, player).open();
                }
            }
            case "menu" -> {
                if (check(player, "qwhatcase.menu")) {
                    if (plugin.catalog().settings().donateShopEnabled()) {
                        new ru.qw.qwhatcase.gui.DonateShopMenu(plugin, player, 0).open();
                    } else {
                        new ShopMenu(plugin, player, 0).open();
                    }
                }
            }
            case "shop" -> {
                if (check(player, "qwhatcase.menu")) {
                    new ShopMenu(plugin, player, 0).open();
                }
            }
            case "equip" -> {
                if (!check(player, "qwhatcase.equip")) {
                    return;
                }
                if (args.length < 2) {
                    plugin.messages().send(player, "usage.equip");
                    return;
                }
                Hat hat = plugin.catalog().hat(args[1]).orElse(null);
                if (hat == null) {
                    plugin.messages().send(player, "hat.unknown", Placeholders.of("hat", args[1]));
                    return;
                }
                reportEquip(player, hat, plugin.display().equip(player, hat.id()));
            }
            case "unequip" -> {
                if (!check(player, "qwhatcase.equip")) {
                    return;
                }
                plugin.messages().send(player, plugin.display().unequip(player) ? "hat.unequipped" : "hat.nothing-to-unequip");
            }
            case "pack" -> {
                if (check(player, "qwhatcase.menu")) {
                    offerPack(player);
                }
            }
            default -> plugin.messages().lines("usage.hats", Map.of()).forEach(line ->
                    player.sendMessage(ru.qw.qwhatcase.util.Text.chat(line)));
        }
    }

    private void cases(Player player, String[] args) {
        if (!check(player, "qwhatcase.cases")) {
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> new CasesMenu(plugin, player, 0).open();
            case "view" -> {
                CaseDef def = args.length < 2 ? null : plugin.catalog().caseDef(args[1]).filter(CaseDef::enabled).orElse(null);
                if (def == null) {
                    plugin.messages().send(player, "cases.unknown", Placeholders.of("case", args.length < 2 ? "?" : args[1]));
                    return;
                }
                new CaseContentMenu(plugin, player, def.id(), 0).open();
            }
            case "open" -> {
                if (args.length < 2) {
                    plugin.messages().send(player, "usage.open");
                    return;
                }
                boolean fast = args.length >= 3 && args[2].equalsIgnoreCase("fast") && plugin.catalog().settings().allowSkipAnimation();
                plugin.openings().open(player, args[1], fast);
            }
            case "keys" -> {
                Profile profile = plugin.profiles().get(player);
                plugin.messages().send(player, "cases.keys-header");
                for (CaseDef def : plugin.catalog().enabledCases()) {
                    plugin.messages().sendPlain(player, "cases.keys-line", Placeholders.of("case", def.name(),
                            "keys", profile == null ? 0 : profile.keys(def.keyType()), "cost", def.keyCost()));
                }
            }
            default -> plugin.messages().lines("usage.cases", Map.of()).forEach(line ->
                    player.sendMessage(ru.qw.qwhatcase.util.Text.chat(line)));
        }
    }

    /**
     * /hat grant — админ-меню выдачи (как в PTrap); /hat grant &lt;игрок&gt; &lt;id&gt; — выдать шляпу.
     * ID можно указывать новый (pirate_hat) или старый из PTrap (hat_90).
     */
    private void grant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("qwhatcase.admin.hats")) {
            plugin.messages().send(sender, "error.no-permission");
            return;
        }
        if (args.length == 1) {
            if (sender instanceof Player player) {
                new ru.qw.qwhatcase.gui.AdminMenus.Players(plugin, player, 0, null).open();
            } else {
                plugin.messages().send(sender, "usage.grant");
            }
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "usage.grant");
            return;
        }
        Hat hat = plugin.catalog().hat(args[2]).or(() -> plugin.catalog().byLegacy(args[2])).orElse(null);
        if (hat == null) {
            plugin.messages().send(sender, "hat.unknown", Placeholders.of("hat", args[2]));
            return;
        }
        plugin.resolver().resolve(args[1]).whenComplete((target, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null || target.isEmpty()) {
                plugin.messages().send(sender, "error.unknown-player", Placeholders.of("player", args[1]));
                return;
            }
            var t = target.get();
            plugin.api().grantHat(t.uuid(), hat.id(), sender.getName()).whenComplete((added, err) ->
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        Map<String, Object> ph = Placeholders.of("player", t.name(), "hat", hat.coloredName());
                        if (err != null) {
                            plugin.messages().send(sender, "error.database");
                            return;
                        }
                        plugin.messages().send(sender, added ? "admin.hat-given" : "admin.hat-already", ph);
                        Player online = org.bukkit.Bukkit.getPlayer(t.uuid());
                        if (added && online != null) {
                            plugin.messages().send(online, "hat.received", ph);
                        }
                    }));
        }));
    }

    public void reportEquip(Player player, Hat hat, HatDisplayService.Result result) {
        String key = switch (result) {
            case EQUIPPED -> "hat.equipped";
            case ALREADY_WORN -> "hat.already-worn";
            case NOT_OWNED -> "hat.not-owned";
            case UNKNOWN_HAT -> "hat.unknown";
            case HELMET_OCCUPIED -> "hat.helmet-occupied";
            case INVENTORY_FULL -> "hat.inventory-full";
            case HELMET_CURSED -> "hat.helmet-cursed";
            case NOT_LOADED -> "error.profile-loading";
        };
        plugin.messages().send(player, key, Placeholders.of("hat", hat.coloredName()));
    }

    public void offerPack(Player player) {
        Settings settings = plugin.catalog().settings();
        switch (settings.packMode()) {
            case PLUGIN -> {
                plugin.packs().send(player);
                plugin.messages().send(player, "pack.sent");
            }
            case SERVER -> plugin.messages().send(player, "pack.server-mode");
            case NONE -> plugin.messages().send(player, "pack.not-configured");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("cases")) {
            if (args.length == 1) {
                options.addAll(List.of("view", "open", "keys"));
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("open"))) {
                plugin.catalog().enabledCases().forEach(c -> options.add(c.id()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
                options.add("fast");
            }
        } else {
            if (args.length == 1) {
                options.addAll(List.of("collection", "equip", "unequip", "pack", "shop", "menu"));
                if (sender.hasPermission("qwhatcase.admin.hats")) {
                    options.add("grant");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("grant")) {
                org.bukkit.Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("grant")) {
                options.addAll(plugin.catalog().hats().keySet());
            } else if (args.length == 2 && args[0].equalsIgnoreCase("equip") && sender instanceof Player player) {
                Profile profile = plugin.profiles().get(player);
                if (profile != null) {
                    options.addAll(profile.owned().keySet());
                }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(100).toList();
    }
}
