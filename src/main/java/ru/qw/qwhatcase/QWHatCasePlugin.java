package ru.qw.qwhatcase;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.qw.qwhatcase.api.ApiImpl;
import ru.qw.qwhatcase.api.QWHatCaseApi;
import ru.qw.qwhatcase.command.AdminCommand;
import ru.qw.qwhatcase.command.PlayerCommands;
import ru.qw.qwhatcase.config.Catalog;
import ru.qw.qwhatcase.config.ConfigLoader;
import ru.qw.qwhatcase.config.Messages;
import ru.qw.qwhatcase.gui.Menu;
import ru.qw.qwhatcase.gui.MenuListener;
import ru.qw.qwhatcase.listener.HatProtectionListener;
import ru.qw.qwhatcase.listener.PackListener;
import ru.qw.qwhatcase.listener.PointListener;
import ru.qw.qwhatcase.listener.SessionListener;
import ru.qw.qwhatcase.service.HatDisplayService;
import ru.qw.qwhatcase.service.MigrationService;
import ru.qw.qwhatcase.service.OpeningService;
import ru.qw.qwhatcase.service.PackService;
import ru.qw.qwhatcase.service.PlayerResolver;
import ru.qw.qwhatcase.service.PointService;
import ru.qw.qwhatcase.service.ProfileCache;
import ru.qw.qwhatcase.service.ShopService;
import ru.qw.qwhatcase.storage.Database;
import ru.qw.qwhatcase.storage.Storage;
import ru.qw.qwhatcase.util.Placeholders;
import ru.qw.qwhatcase.util.Text;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class QWHatCasePlugin extends JavaPlugin {
    private static final List<String> FILES = List.of("config.yml", "hats.yml", "cases.yml", "shop.yml", "messages.yml");

    private volatile Catalog catalog;
    private volatile Messages messages;
    private Storage storage;
    private final ProfileCache profiles = new ProfileCache();
    private HatDisplayService display;
    private PackService packs;
    private PointService points;
    private OpeningService openings;
    private ShopService shop;
    private MigrationService migration;
    private PlayerResolver resolver;
    private PlayerCommands commands;
    private ApiImpl api;
    private ru.qw.qwhatcase.service.ChatInput chatInput;
    private BukkitTask announcement;

    @Override
    public void onEnable() {
        for (String file : FILES) {
            if (!new File(getDataFolder(), file).exists()) {
                saveResource(file, false);
            }
        }
        try {
            catalog = loadCatalog();
            messages = loadMessages();
        } catch (Exception e) {
            getLogger().severe("Конфигурация не загружена, плагин выключен: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        catalog.problems().forEach(problem -> getLogger().warning(problem));
        checkRegistries(catalog);

        try {
            File dbFile = new File(getDataFolder(), catalog.settings().databaseFile());
            Database database = new Database(dbFile);
            database.setLogger(getLogger());
            storage = new Storage(this, database);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось открыть базу данных, плагин выключен", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        display = new HatDisplayService(this);
        packs = new PackService(this);
        points = new PointService(this);
        openings = new OpeningService(this);
        shop = new ShopService(this);
        migration = new MigrationService(this);
        resolver = new PlayerResolver(this);
        commands = new PlayerCommands(this);
        api = new ApiImpl(this);
        chatInput = new ru.qw.qwhatcase.service.ChatInput(this);

        try {
            points.load(storage.blocking(Database::points));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Не удалось загрузить точки кейсов", e);
        }

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new HatProtectionListener(this), this);
        pm.registerEvents(new SessionListener(this), this);
        pm.registerEvents(new PointListener(this), this);
        pm.registerEvents(new PackListener(this), this);
        pm.registerEvents(chatInput, this);

        bind("hats", commands);
        bind("cases", commands);
        AdminCommand admin = new AdminCommand(this);
        bind("hatcases", admin);

        Bukkit.getServicesManager().register(QWHatCaseApi.class, api, this, ServicePriority.Normal);

        // Перезагрузка плагина при онлайне (например, /reload): загрузить данные текущих игроков.
        for (Player player : Bukkit.getOnlinePlayers()) {
            reloadProfile(player, () -> {
                migration.convertLegacyItems(player);
                display.apply(player);
            });
        }
        scheduleAnnouncement();
        getLogger().info("QWHatCase включён: шляп " + catalog.hats().size() + ", кейсов " + catalog.cases().size()
                + " (включено " + catalog.enabledCases().size() + "), точек " + points.all().size());
    }

    private void bind(String name, Object executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), name);
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
    }

    @Override
    public void onDisable() {
        if (openings != null) {
            openings.shutdown();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                player.closeInventory();
            }
            if (display != null) {
                // Косметический предмет не должен остаться у игрока, если плагин удалят.
                display.stripCosmetic(player);
                display.purgeServiceItems(player);
            }
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (storage != null) {
            storage.shutdown();
        }
    }

    // ------------------------------------------------------------------ configuration

    private YamlConfiguration read(String name) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), name));
        return yaml;
    }

    private Catalog loadCatalog() throws Exception {
        return new ConfigLoader().load(read("config.yml"), read("hats.yml"), read("cases.yml"), read("shop.yml"));
    }

    private Messages loadMessages() throws Exception {
        YamlConfiguration defaults = null;
        var stream = getResource("messages.yml");
        if (stream != null) {
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults = YamlConfiguration.loadConfiguration(reader);
            }
        }
        return new Messages(read("messages.yml"), defaults);
    }

    /**
     * Проверяет и применяет конфигурацию. Ошибочная конфигурация не заменяет последнюю рабочую.
     * Уже начатые открытия используют свой снимок настроек.
     */
    public void reloadAll(CommandSender sender) {
        Catalog next;
        Messages nextMessages;
        try {
            next = loadCatalog();
            nextMessages = loadMessages();
        } catch (Exception e) {
            messages.send(sender, "admin.reload-failed", Placeholders.of("error", String.valueOf(e.getMessage())));
            getLogger().warning("Перезагрузка отклонена, оставлена прежняя конфигурация: " + e.getMessage());
            return;
        }
        if (!next.settings().databaseFile().equals(catalog.settings().databaseFile())) {
            next.problems();
            getLogger().warning("storage.file изменён — новый файл БД будет использован только после перезапуска сервера");
        }
        catalog = next;
        messages = nextMessages;
        checkRegistries(next);
        next.problems().forEach(problem -> getLogger().warning(problem));
        messages.send(sender, "admin.reloaded", Placeholders.of("hats", next.hats().size(), "cases", next.cases().size(),
                "enabled", next.enabledCases().size(), "problems", next.problems().size()));
        for (String problem : next.problems().stream().limit(15).toList()) {
            sender.sendMessage(Text.chat("&8 - &e" + problem));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            display.refresh(player);
        }
        scheduleAnnouncement();
    }

    /** Проверка названий чар и атрибутов из hat-item (нужен работающий сервер). */
    private void checkRegistries(Catalog c) {
        c.settings().hatEnchantments().keySet().stream()
                .filter(k -> ru.qw.qwhatcase.service.HatItems.enchantment(k) == null)
                .forEach(k -> getLogger().warning("hat-item.enchantments: неизвестные чары '" + k + "' — пропущены"));
        c.settings().hatAttributes().keySet().stream()
                .filter(k -> ru.qw.qwhatcase.service.HatItems.attribute(k) == null)
                .forEach(k -> getLogger().warning("hat-item.attributes: неизвестный атрибут '" + k + "' — пропущен"));
    }

    private void scheduleAnnouncement() {
        if (announcement != null) {
            announcement.cancel();
            announcement = null;
        }
        if (catalog.settings().announcementEnabled() && !catalog.settings().announcementMessage().isBlank()) {
            long ticks = catalog.settings().announcementMinutes() * 60L * 20L;
            announcement = Bukkit.getScheduler().runTaskTimer(this,
                    () -> Bukkit.broadcast(Text.chat(catalog.settings().announcementMessage())), ticks, ticks);
        }
    }

    /** Загрузить данные игрока из БД асинхронно и выполнить действие в основном потоке. */
    public void reloadProfile(Player player, Runnable then) {
        storage.run(db -> {
            db.touchPlayer(player.getUniqueId(), player.getName());
            return db.load(player.getUniqueId());
        }, data -> {
            if (player.isOnline()) {
                profiles.put(data);
                then.run();
            }
        }, error -> getLogger().severe("Не удалось загрузить данные игрока " + player.getName()));
    }

    // ------------------------------------------------------------------ accessors

    public Catalog catalog() {
        return catalog;
    }

    public Messages messages() {
        return messages;
    }

    public Storage storage() {
        return storage;
    }

    public ProfileCache profiles() {
        return profiles;
    }

    public HatDisplayService display() {
        return display;
    }

    public PackService packs() {
        return packs;
    }

    public PointService points() {
        return points;
    }

    public OpeningService openings() {
        return openings;
    }

    public ShopService shop() {
        return shop;
    }

    public MigrationService migration() {
        return migration;
    }

    public PlayerResolver resolver() {
        return resolver;
    }

    public PlayerCommands commands() {
        return commands;
    }

    public QWHatCaseApi api() {
        return api;
    }

    public ru.qw.qwhatcase.service.ChatInput chatInput() {
        return chatInput;
    }
}
