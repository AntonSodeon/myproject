package ru.qw.qwhatcase.service;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.storage.Database;
import ru.qw.qwhatcase.storage.LegacyImport;
import ru.qw.qwhatcase.util.Placeholders;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Перенос данных старого плагина. Порядок: предварительный просмотр без записи →
 * подтверждение → резервная копия (старые файлы + БД) → перенос в одной транзакции.
 * Старые файлы никогда не изменяются и не удаляются. Повторный запуск не создаёт дубликатов.
 */
public final class MigrationService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long CONFIRM_WINDOW_MS = 5 * 60 * 1000L;

    private final QWHatCasePlugin plugin;
    private final Map<String, Long> previewedBy = new LinkedHashMap<>();
    private final Set<java.util.UUID> warnedLegacy = new HashSet<>();

    public MigrationService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private record Source(String label, File file) {
    }

    private List<Source> sources() {
        File serverRoot = plugin.getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        List<Source> result = new ArrayList<>();
        for (String path : plugin.catalog().settings().migrationSources()) {
            File file = new File(path);
            if (!file.isAbsolute()) {
                file = new File(serverRoot, path);
            }
            if (file.isFile()) {
                result.add(new Source(path, file));
            }
        }
        return result;
    }

    private LegacyParser.Plan plan(Source source) throws IOException {
        String text = Files.readString(source.file().toPath(), StandardCharsets.UTF_8);
        return LegacyParser.parse(text, oldId -> plugin.catalog().byLegacy(oldId).map(Hat::id));
    }

    public void preview(CommandSender sender) {
        List<Source> sources = sources();
        if (sources.isEmpty()) {
            plugin.messages().send(sender, "migrate.no-source", Placeholders.of("paths",
                    String.join(", ", plugin.catalog().settings().migrationSources())));
            return;
        }
        plugin.messages().send(sender, "migrate.preview-start");
        plugin.storage().run(db -> {
            StringBuilder report = new StringBuilder("Предварительный просмотр миграции (без записи)\n");
            report.append("Время: ").append(LocalDateTime.now()).append("\n\n");
            int players = 0;
            int hats = 0;
            int mapped = 0;
            int unknown = 0;
            int present = 0;
            int toAdd = 0;
            List<String> problems = new ArrayList<>();
            for (Source source : sources) {
                LegacyParser.Plan plan;
                try {
                    plan = plan(source);
                } catch (IOException | RuntimeException e) {
                    problems.add(source.label() + ": не удалось прочитать файл: " + e.getMessage());
                    continue;
                }
                report.append("Источник: ").append(source.file().getAbsolutePath()).append('\n');
                players += plan.players().size();
                hats += plan.hats();
                mapped += plan.mapped();
                unknown += plan.unknown();
                problems.addAll(plan.problems());
                for (LegacyImport player : plan.players()) {
                    for (String hat : player.hatIds()) {
                        if (db.ownsHat(player.uuid(), hat)) {
                            present++;
                        } else {
                            toAdd++;
                        }
                    }
                    report.append("  ").append(player.name() == null ? "?" : player.name()).append(" (").append(player.uuid())
                            .append("): шляп ").append(player.hatIds().size()).append(", неизвестных ").append(player.unknown().size())
                            .append('\n');
                }
                report.append("  Ранее выполненных переносов из этого источника: ").append(db.migrationRuns(source.label())).append("\n\n");
            }
            report.append("Игроков: ").append(players).append('\n')
                    .append("Записей шляп: ").append(hats).append('\n')
                    .append("Сопоставлено: ").append(mapped).append('\n')
                    .append("Неизвестных: ").append(unknown).append('\n')
                    .append("Уже есть в новой БД: ").append(present).append('\n')
                    .append("Будет добавлено: ").append(toAdd).append("\n\nПроблемы:\n");
            problems.forEach(p -> report.append(" - ").append(p).append('\n'));
            File file = writeReport("preview", report.toString());
            return new Object[]{players, hats, mapped, unknown, present, toAdd, problems, file};
        }, data -> {
            Object[] d = data;
            @SuppressWarnings("unchecked") List<String> problems = (List<String>) d[6];
            plugin.messages().send(sender, "migrate.preview-result", Placeholders.of("players", d[0], "hats", d[1],
                    "mapped", d[2], "unknown", d[3], "present", d[4], "add", d[5], "problems", problems.size(),
                    "report", ((File) d[7]).getPath()));
            for (String problem : problems.stream().limit(10).toList()) {
                plugin.messages().sendPlain(sender, "migrate.problem-line", Placeholders.of("problem", problem));
            }
            previewedBy.put(sender.getName(), System.currentTimeMillis());
            plugin.messages().send(sender, "migrate.confirm-hint");
        }, error -> plugin.messages().send(sender, "error.database"));
    }

    public void run(CommandSender sender) {
        Long previewed = previewedBy.get(sender.getName());
        if (previewed == null || System.currentTimeMillis() - previewed > CONFIRM_WINDOW_MS) {
            plugin.messages().send(sender, "migrate.preview-first");
            return;
        }
        previewedBy.remove(sender.getName());
        List<Source> sources = sources();
        if (sources.isEmpty()) {
            plugin.messages().send(sender, "migrate.no-source", Placeholders.of("paths",
                    String.join(", ", plugin.catalog().settings().migrationSources())));
            return;
        }
        String stamp = LocalDateTime.now().format(STAMP);
        File backupDir = new File(plugin.getDataFolder(), "backups/migration-" + stamp);
        plugin.storage().run(db -> {
            // 1. Резервная копия старых данных (вся папка старого плагина) и текущей БД.
            if (!backupDir.mkdirs() && !backupDir.isDirectory()) {
                throw new IOException("Не удалось создать " + backupDir);
            }
            for (Source source : sources) {
                File folder = source.file().getParentFile();
                File target = new File(backupDir, folder.getName());
                copyFolder(folder, target);
            }
            db.backupTo(new File(backupDir, "qwhatcase-before.db"));
            // 2. Перенос.
            StringBuilder report = new StringBuilder("Миграция " + stamp + "\nРезервная копия: " + backupDir.getAbsolutePath() + "\n\n");
            List<Database.ImportStats> stats = new ArrayList<>();
            for (Source source : sources) {
                LegacyParser.Plan plan = plan(source);
                Database.ImportStats s = db.importLegacy(source.label(), plan.players(), sender.getName(),
                        backupDir.getAbsolutePath(), null);
                stats.add(s);
                report.append("Источник ").append(source.file().getAbsolutePath()).append(": игроков ").append(s.players())
                        .append(", записей ").append(s.hatsFound()).append(", добавлено ").append(s.hatsAdded())
                        .append(", неизвестных ").append(s.unknown()).append('\n');
                plan.problems().forEach(p -> report.append(" - ").append(p).append('\n'));
            }
            File file = writeReport("run", report.toString());
            int players = stats.stream().mapToInt(Database.ImportStats::players).sum();
            int found = stats.stream().mapToInt(Database.ImportStats::hatsFound).sum();
            int added = stats.stream().mapToInt(Database.ImportStats::hatsAdded).sum();
            int unknown = stats.stream().mapToInt(Database.ImportStats::unknown).sum();
            return new Object[]{players, found, added, unknown, file};
        }, d -> {
            plugin.messages().send(sender, "migrate.done", Placeholders.of("players", d[0], "hats", d[1], "added", d[2],
                    "unknown", d[3], "report", ((File) d[4]).getPath(), "backup", backupDir.getPath()));
            plugin.getLogger().info("Миграция выполнена: игроков " + d[0] + ", добавлено шляп " + d[2] + ", неизвестных " + d[3]);
            // Обновить кэш онлайн-игроков и перевести их старые предметы.
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.reloadProfile(player, () -> convertLegacyItems(player));
            }
        }, error -> plugin.messages().send(sender, "migrate.failed", Placeholders.of("error", String.valueOf(error.getMessage()))));
    }

    private static void copyFolder(File from, File to) throws IOException {
        if (!from.isDirectory()) {
            return;
        }
        try (var stream = Files.walk(from.toPath())) {
            for (var path : stream.toList()) {
                var target = to.toPath().resolve(from.toPath().relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private File writeReport(String kind, String text) throws IOException {
        File dir = new File(plugin.getDataFolder(), "migration");
        Files.createDirectories(dir.toPath());
        File file = new File(dir, kind + "-" + LocalDateTime.now().format(STAMP) + ".txt");
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Предметы старого плагина в инвентаре игрока. Если шляпа уже есть в новой коллекции,
     * старый предмет (с бронёй +3 и чарами) убирается, а шляпа из слота шлема становится выбранной.
     * Предметы шляп, которых нет в коллекции, НЕ трогаются и фиксируются в журнале.
     */
    public void convertLegacyItems(Player player) {
        if (!plugin.catalog().settings().convertLegacyItems()) {
            return;
        }
        Profile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        String helmetHat = null;
        List<String> converted = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            String legacy = HatItems.legacyId(contents[slot]);
            if (legacy == null) {
                continue;
            }
            Optional<Hat> hat = plugin.catalog().byLegacy(legacy);
            if (hat.isPresent() && profile.owns(hat.get().id())) {
                inventory.setItem(slot, null);
                converted.add(legacy + "->" + hat.get().id() + "@" + slot);
                if (slot == 39) {
                    helmetHat = hat.get().id();
                }
            } else {
                kept.add(legacy + "@" + slot);
            }
        }
        if (helmetHat != null && profile.selectedHat() == null) {
            plugin.display().equip(player, helmetHat);
        } else if (helmetHat != null) {
            plugin.display().apply(player);
        }
        if (!converted.isEmpty()) {
            String details = "items=" + converted;
            plugin.storage().submit(db -> {
                db.audit("system", player.getUniqueId(), "legacy.convert", details);
                return null;
            });
            plugin.messages().send(player, "migrate.items-converted", Placeholders.of("count", converted.size()));
        }
        if (!kept.isEmpty() && warnedLegacy.add(player.getUniqueId())) {
            plugin.getLogger().warning("У игрока " + player.getName() + " есть предметы старого плагина без записи в коллекции: "
                    + kept + ". Предметы не тронуты. Выполните /qwhatcase migrate или выдайте шляпы вручную.");
        }
    }
}
