package ru.qw.qwhatcase.command;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.service.DistributionCheck;
import ru.qw.qwhatcase.service.PlayerResolver;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.storage.Database;
import ru.qw.qwhatcase.storage.HistoryEntry;
import ru.qw.qwhatcase.util.Format;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** /hatcases — административные команды (работают и из консоли, и для офлайн-игроков). */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final long MAX_AMOUNT = 1_000_000_000L;
    private final QWHatCasePlugin plugin;

    public AdminCommand(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "error.no-permission");
        return false;
    }

    private void usage(CommandSender sender) {
        plugin.messages().lines("usage.admin", Map.of()).forEach(line -> sender.sendMessage(Text.chat(line)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "key" -> {
                if (check(sender, "qwhatcase.admin.keys")) {
                    keys(sender, args);
                }
            }
            case "hat" -> {
                if (check(sender, "qwhatcase.admin.hats")) {
                    hats(sender, args);
                }
            }
            case "tokens" -> {
                if (check(sender, "qwhatcase.admin.tokens")) {
                    tokens(sender, args);
                }
            }
            case "point" -> {
                if (check(sender, "qwhatcase.admin.points")) {
                    points(sender, args);
                }
            }
            case "inspect" -> {
                if (check(sender, "qwhatcase.admin.inspect")) {
                    inspect(sender, args);
                }
            }
            case "history" -> {
                if (check(sender, "qwhatcase.admin.history")) {
                    history(sender, args);
                }
            }
            case "reload" -> {
                if (check(sender, "qwhatcase.admin.reload")) {
                    plugin.reloadAll(sender);
                }
            }
            case "migrate" -> {
                if (check(sender, "qwhatcase.admin.migrate")) {
                    if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
                        plugin.migration().run(sender);
                    } else {
                        plugin.migration().preview(sender);
                    }
                }
            }
            case "simulate" -> {
                if (check(sender, "qwhatcase.admin.simulate")) {
                    simulate(sender, args);
                }
            }
            default -> usage(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private Long amount(CommandSender sender, String raw, boolean allowZero) {
        try {
            long value = Long.parseLong(raw);
            if (value < (allowZero ? 0 : 1) || value > MAX_AMOUNT) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "error.bad-amount", Placeholders.of("value", raw));
            return null;
        }
    }

    private void withTarget(CommandSender sender, String input, Consumer<PlayerResolver.Target> action) {
        plugin.resolver().resolve(input).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.messages().send(sender, "error.database");
            } else if (result.isEmpty()) {
                plugin.messages().send(sender, "error.unknown-player", Placeholders.of("player", input));
            } else {
                action.accept(result.get());
            }
        }));
    }

    private <T> void reply(CommandSender sender, CompletableFuture<T> future, Consumer<T> onSuccess) {
        future.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.messages().send(sender, "error.database");
            } else {
                onSuccess.accept(value);
            }
        }));
    }

    private static String actor(CommandSender sender) {
        return sender instanceof Player ? sender.getName() : "CONSOLE";
    }

    // ------------------------------------------------------------------ keys

    private void keys(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.messages().send(sender, "usage.key");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        CaseDef def = plugin.catalog().caseDef(args[3]).orElse(null);
        if (def == null) {
            plugin.messages().send(sender, "cases.unknown", Placeholders.of("case", args[3]));
            return;
        }
        Long amount = amount(sender, args[4], action.equals("set"));
        if (amount == null) {
            return;
        }
        withTarget(sender, args[2], target -> {
            Map<String, Object> ph = Placeholders.of("player", target.name(), "case", def.name(), "amount", amount);
            switch (action) {
                case "give" -> reply(sender, plugin.api().giveKeys(target.uuid(), def.id(), amount, actor(sender)), change -> {
                    ph.put("balance", change.balance());
                    plugin.messages().send(sender, "admin.keys-given", ph);
                    notify(target.uuid(), "keys.received", ph);
                });
                case "take" -> reply(sender, plugin.api().takeKeys(target.uuid(), def.id(), amount, actor(sender)), change -> {
                    ph.put("balance", change.balance());
                    ph.put("taken", change.changed());
                    plugin.messages().send(sender, "admin.keys-taken", ph);
                });
                case "set" -> reply(sender, plugin.api().setKeys(target.uuid(), def.id(), amount, actor(sender)), change -> {
                    ph.put("balance", change.balance());
                    plugin.messages().send(sender, "admin.keys-set", ph);
                });
                default -> plugin.messages().send(sender, "usage.key");
            }
        });
    }

    private void notify(UUID uuid, String key, Map<String, Object> ph) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            plugin.messages().send(online, key, ph);
        }
    }

    // ------------------------------------------------------------------ hats

    private void hats(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "usage.hat");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Hat hat = plugin.catalog().hat(args[3]).orElse(null);
        if (hat == null && action.equals("give")) {
            plugin.messages().send(sender, "hat.unknown", Placeholders.of("hat", args[3]));
            return;
        }
        String hatId = args[3];
        String hatName = hat == null ? hatId : hat.coloredName();
        withTarget(sender, args[2], target -> {
            Map<String, Object> ph = Placeholders.of("player", target.name(), "hat", hatName);
            switch (action) {
                case "give" -> reply(sender, plugin.api().grantHat(target.uuid(), hatId, actor(sender)), added -> {
                    plugin.messages().send(sender, added ? "admin.hat-given" : "admin.hat-already", ph);
                    if (added) {
                        notify(target.uuid(), "hat.received", ph);
                    }
                });
                case "revoke" -> reply(sender, plugin.api().revokeHat(target.uuid(), hatId, actor(sender)), removed ->
                        plugin.messages().send(sender, removed ? "admin.hat-revoked" : "admin.hat-missing", ph));
                default -> plugin.messages().send(sender, "usage.hat");
            }
        });
    }

    // ------------------------------------------------------------------ tokens

    private void tokens(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "usage.tokens");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Long amount = amount(sender, args[3], false);
        if (amount == null) {
            return;
        }
        withTarget(sender, args[2], target -> {
            Map<String, Object> ph = Placeholders.of("player", target.name(), "amount", amount);
            switch (action) {
                case "give" -> reply(sender, plugin.api().giveTokens(target.uuid(), amount, actor(sender)), change -> {
                    ph.put("balance", change.balance());
                    plugin.messages().send(sender, "admin.tokens-given", ph);
                    notify(target.uuid(), "tokens.received", ph);
                });
                case "take" -> reply(sender, plugin.api().takeTokens(target.uuid(), amount, actor(sender)), change -> {
                    ph.put("balance", change.balance());
                    ph.put("taken", change.changed());
                    plugin.messages().send(sender, "admin.tokens-taken", ph);
                });
                default -> plugin.messages().send(sender, "usage.tokens");
            }
        });
    }

    // ------------------------------------------------------------------ points

    private void points(CommandSender sender, String[] args) {
        String action = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            List<CasePoint> list = plugin.points().all();
            list.sort(Comparator.comparing(CasePoint::key));
            plugin.messages().send(sender, "point.list-header", Placeholders.of("count", list.size()));
            for (CasePoint point : list) {
                plugin.messages().sendPlain(sender, "point.list-line", Placeholders.of("world", point.world(), "x", point.x(),
                        "y", point.y(), "z", point.z(), "case", point.caseId()));
            }
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "error.players-only");
            return;
        }
        Block block = player.getTargetBlockExact(6);
        if (block == null || block.getType().isAir()) {
            plugin.messages().send(player, "point.no-target");
            return;
        }
        Map<String, Object> ph = Placeholders.of("world", block.getWorld().getName(), "x", block.getX(), "y", block.getY(),
                "z", block.getZ(), "block", block.getType().getKey().getKey());
        switch (action) {
            case "add" -> {
                if (args.length < 3 || plugin.catalog().caseDef(args[2]).isEmpty()) {
                    plugin.messages().send(player, "cases.unknown", Placeholders.of("case", args.length < 3 ? "?" : args[2]));
                    return;
                }
                ph.put("case", args[2]);
                plugin.points().add(new CasePoint(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), args[2]),
                        player.getName(), () -> plugin.messages().send(player, "point.added", ph));
            }
            case "remove" -> plugin.points().remove(block, player.getName(),
                    removed -> plugin.messages().send(player, removed ? "point.removed" : "point.not-found", ph));
            default -> plugin.messages().send(player, "usage.point");
        }
    }

    // ------------------------------------------------------------------ inspect / history

    private void inspect(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.inspect");
            return;
        }
        withTarget(sender, args[1], target -> reply(sender, plugin.storage().submit(db -> db.load(target.uuid())), data -> {
            Map<String, Object> ph = Placeholders.of("player", target.name(), "uuid", target.uuid(), "tokens", data.tokens(),
                    "owned", data.owned().size(), "total", plugin.catalog().hats().size(),
                    "selected", data.selectedHat() == null ? "-" : data.selectedHat());
            plugin.messages().lines("admin.inspect", ph).forEach(line -> sender.sendMessage(Text.chat(line)));
            for (CaseDef def : plugin.catalog().cases().values()) {
                plugin.messages().sendPlain(sender, "admin.inspect-key", Placeholders.of("case", def.name(), "id", def.id(),
                        "keys", data.keys().getOrDefault(def.keyType(), 0L)));
            }
            List<String> hats = new ArrayList<>();
            data.owned().forEach((id, at) -> hats.add(id + (plugin.catalog().hats().containsKey(id) ? "" : "(?)")));
            plugin.messages().sendPlain(sender, "admin.inspect-hats", Placeholders.of("hats", hats.isEmpty() ? "-" : String.join(", ", hats)));
        }));
    }

    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.history");
            return;
        }
        if (args[1].equalsIgnoreCase("op") && args.length >= 3) {
            UUID op;
            try {
                op = UUID.fromString(args[2]);
            } catch (IllegalArgumentException e) {
                plugin.messages().send(sender, "usage.history");
                return;
            }
            reply(sender, plugin.storage().submit(db -> db.historyEntry(op)), entry -> {
                if (entry.isEmpty()) {
                    plugin.messages().send(sender, "admin.history-not-found");
                } else {
                    printHistory(sender, entry.get(), true);
                }
            });
            return;
        }
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        int size = plugin.catalog().settings().historyPageSize();
        int offset = (page - 1) * size;
        int shownPage = page;
        withTarget(sender, args[1], target -> reply(sender, plugin.storage().submit(db -> {
            List<HistoryEntry> entries = db.history(target.uuid(), size, offset);
            int count = db.historyCount(target.uuid());
            List<Database.AuditEntry> audit = db.auditFor(target.uuid(), 5);
            return new Object[]{entries, count, audit};
        }), data -> {
            @SuppressWarnings("unchecked") List<HistoryEntry> entries = (List<HistoryEntry>) data[0];
            int count = (int) data[1];
            @SuppressWarnings("unchecked") List<Database.AuditEntry> audit = (List<Database.AuditEntry>) data[2];
            plugin.messages().send(sender, "admin.history-header", Placeholders.of("player", target.name(), "count", count,
                    "page", shownPage, "pages", Math.max(1, (count + size - 1) / size)));
            entries.forEach(entry -> printHistory(sender, entry, false));
            if (!audit.isEmpty()) {
                plugin.messages().sendPlain(sender, "admin.audit-header", Map.of());
                for (Database.AuditEntry a : audit) {
                    plugin.messages().sendPlain(sender, "admin.audit-line", Placeholders.of("date", Format.date(a.ts()),
                            "actor", a.actor(), "action", a.action(), "details", a.details()));
                }
            }
        }));
    }

    private void printHistory(CommandSender sender, HistoryEntry entry, boolean full) {
        String hat = plugin.catalog().hat(entry.hatId()).map(Hat::coloredName).orElse(entry.hatId());
        plugin.messages().sendPlain(sender, full ? "admin.history-full" : "admin.history-line", Placeholders.of(
                "date", Format.date(entry.createdAt()), "operation", full ? entry.operationId() : entry.operationId().toString().substring(0, 8),
                "case", entry.caseId(), "hat", hat, "keys", entry.keysSpent(),
                "outcome", plugin.messages().raw("DUPLICATE".equals(entry.outcome()) ? "admin.outcome-duplicate" : "admin.outcome-new"),
                "tokens", entry.tokensAwarded(), "balance", entry.tokensBalance(),
                "shown", plugin.messages().raw(entry.shown() ? "admin.shown-yes" : "admin.shown-no"),
                "player", entry.player()));
    }

    // ------------------------------------------------------------------ simulate

    private void simulate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.simulate");
            return;
        }
        Optional<CaseDef> def = plugin.catalog().caseDef(args[1]);
        if (def.isEmpty()) {
            plugin.messages().send(sender, "cases.unknown", Placeholders.of("case", args[1]));
            return;
        }
        long rolls = 1_000_000;
        if (args.length >= 3) {
            Long parsed = amount(sender, args[2], false);
            if (parsed == null) {
                return;
            }
            rolls = Math.min(parsed, 50_000_000L);
        }
        long n = rolls;
        plugin.messages().send(sender, "admin.simulate-start", Placeholders.of("case", def.get().name(), "rolls", n));
        CompletableFuture.supplyAsync(() -> DistributionCheck.run(def.get(), plugin.openings().roller(), n))
                .whenComplete((report, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        sender.sendMessage(error.toString());
                        return;
                    }
                    plugin.messages().send(sender, report.passed() ? "admin.simulate-ok" : "admin.simulate-warn",
                            Placeholders.of("rolls", report.rolls(), "chi", String.format(Locale.ROOT, "%.1f", report.chiSquare()),
                                    "df", report.degreesOfFreedom(), "z", String.format(Locale.ROOT, "%.2f", report.z()),
                                    "dev", String.format(Locale.ROOT, "%.4f", report.maxDeviationPp())));
                    report.rows().stream()
                            .sorted(Comparator.comparingDouble(r -> -Math.abs(r.actualPercent() - r.expectedPercent())))
                            .limit(8)
                            .forEach(row -> plugin.messages().sendPlain(sender, "admin.simulate-line", Placeholders.of(
                                    "hat", row.reward().hat().coloredName(), "expected", Format.percent(row.expectedPercent()),
                                    "actual", Format.percent(row.actualPercent()), "count", row.count())));
                }));
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        List<String> options = new ArrayList<>();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (args.length) {
            case 1 -> options.addAll(List.of("key", "hat", "tokens", "point", "inspect", "history", "reload", "migrate", "simulate"));
            case 2 -> {
                switch (sub) {
                    case "key" -> options.addAll(List.of("give", "take", "set"));
                    case "hat" -> options.addAll(List.of("give", "revoke"));
                    case "tokens" -> options.addAll(List.of("give", "take"));
                    case "point" -> options.addAll(List.of("add", "remove", "list"));
                    case "migrate" -> options.addAll(List.of("preview", "confirm"));
                    case "simulate" -> options.addAll(plugin.catalog().cases().keySet());
                    case "inspect", "history" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    default -> {
                    }
                }
            }
            case 3 -> {
                switch (sub) {
                    case "key", "hat", "tokens" -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    case "point" -> {
                        if (args[1].equalsIgnoreCase("add")) {
                            options.addAll(plugin.catalog().cases().keySet());
                        }
                    }
                    default -> {
                    }
                }
            }
            case 4 -> {
                if (sub.equals("key")) {
                    options.addAll(plugin.catalog().cases().keySet());
                } else if (sub.equals("hat")) {
                    options.addAll(plugin.catalog().hats().keySet());
                }
            }
            default -> {
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(100).toList();
    }
}
