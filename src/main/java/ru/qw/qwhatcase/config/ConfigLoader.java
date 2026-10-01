package ru.qw.qwhatcase.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Загружает и проверяет конфигурацию. Не зависит от сервера, поэтому проверяется unit-тестами.
 * Фатальные ошибки (нет редкостей, нет каталога) бросают {@link ConfigException} —
 * в этом случае плагин оставляет последнюю рабочую конфигурацию.
 * Ошибки отдельного кейса отключают только этот кейс.
 */
public final class ConfigLoader {
    private static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{1,64}");

    public static final class ConfigException extends Exception {
        public ConfigException(String message) {
            super(message);
        }
    }

    private final List<String> problems = new ArrayList<>();

    public Catalog load(ConfigurationSection config, ConfigurationSection hatsFile,
                        ConfigurationSection casesFile, ConfigurationSection shopFile) throws ConfigException {
        problems.clear();
        Settings settings = loadSettings(config);
        Map<String, Rarity> rarities = loadRarities(config.getConfigurationSection("rarities"));
        Map<String, Category> categories = loadCategories(hatsFile.getConfigurationSection("categories"));
        Map<String, Long> prices = loadPrices(shopFile, rarities);
        Map<String, Hat> hats = new LinkedHashMap<>();
        Map<String, String> legacy = new LinkedHashMap<>();
        loadHats(hatsFile, rarities, categories, prices, hats, legacy);
        for (String priced : prices.keySet()) {
            if (!priced.startsWith("@") && !hats.containsKey(priced)) {
                problems.add("shop.yml: неизвестная шляпа '" + priced + "' — пропущена");
            }
        }
        Map<String, CaseDef> cases = loadCases(casesFile, hats, rarities, settings);
        return new Catalog(settings, rarities, categories, hats, legacy, cases, problems);
    }

    private Settings loadSettings(ConfigurationSection c) throws ConfigException {
        Settings.HelmetPolicy policy = parseEnum(Settings.HelmetPolicy.class,
                c.getString("hat-display.helmet-occupied", "MOVE_TO_INVENTORY"), "hat-display.helmet-occupied");
        Settings.PackMode packMode = parseEnum(Settings.PackMode.class,
                c.getString("resource-pack.mode", "PLUGIN"), "resource-pack.mode");
        String url = c.getString("resource-pack.url", "").trim();
        String sha1 = c.getString("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (!sha1.isEmpty() && !sha1.matches("[0-9a-f]{40}")) {
            throw new ConfigException("resource-pack.sha1 должен состоять из 40 шестнадцатеричных символов");
        }
        if (packMode == Settings.PackMode.PLUGIN && url.isEmpty()) {
            problems.add("resource-pack.url не задан: ресурс-пак не отправляется и не проверяется (режим NONE)");
            packMode = Settings.PackMode.NONE;
        }
        Settings.LegacyItems legacyItems = parseEnum(Settings.LegacyItems.class,
                c.getString("migration.legacy-items", "IMPORT"), "migration.legacy-items");
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        ConfigurationSection ench = c.getConfigurationSection("hat-item.enchantments");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                int level = ench.getInt(key);
                if (level < 1 || level > 255) {
                    throw new ConfigException("hat-item.enchantments." + key + ": уровень должен быть от 1 до 255");
                }
                enchantments.put(normalizeKey(key), level);
            }
        }
        Map<String, Double> attributes = new LinkedHashMap<>();
        ConfigurationSection attr = c.getConfigurationSection("hat-item.attributes");
        if (attr != null) {
            for (String key : attr.getKeys(false)) {
                double value = attr.getDouble(key);
                if (value != 0) {
                    attributes.put(normalizeKey(key), value);
                }
            }
        }
        return new Settings(
                c.getString("storage.file", "data.db"),
                policy,
                packMode, url, sha1,
                c.getBoolean("resource-pack.required", false),
                c.getString("resource-pack.prompt", ""),
                c.getBoolean("resource-pack.block-cases-until-loaded", true),
                c.getBoolean("animation.allow-skip-with-shift", true),
                Math.max(0, c.getLong("gui.click-cooldown-ms", 250)),
                c.getBoolean("announcement.enabled", false),
                Math.max(1, c.getLong("announcement.interval-minutes", 35)),
                c.getString("announcement.message", ""),
                c.getBoolean("external-shop.enabled", false),
                c.getString("external-shop.url", ""),
                c.getStringList("migration.source-files"),
                legacyItems,
                Math.max(1, c.getInt("history.page-size", 10)),
                Map.copyOf(enchantments), Map.copyOf(attributes),
                c.getBoolean("hat-item.glint", false),
                c.getBoolean("hat-item.book-enchanting", true),
                c.getBoolean("donate-shop.enabled", true),
                c.getString("donate-shop.price", "75 ₽"),
                c.getString("donate-shop.telegram", "AntonSodeon").replace("@", "").trim());
    }

    private Map<String, Rarity> loadRarities(ConfigurationSection section) throws ConfigException {
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new ConfigException("config.yml: секция rarities пуста");
        }
        Map<String, Rarity> result = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection r = section.getConfigurationSection(id);
            if (r == null || !ID.matcher(id).matches()) {
                throw new ConfigException("config.yml: некорректная редкость '" + id + "'");
            }
            long compensation = r.getLong("compensation", 0);
            if (compensation < 0) {
                throw new ConfigException("config.yml: rarities." + id + ".compensation не может быть отрицательной");
            }
            result.put(id, new Rarity(id, r.getString("name", id), r.getString("color", "&f"),
                    r.getInt("order", result.size()), compensation,
                    r.getString("pane", "WHITE_STAINED_GLASS_PANE"), r.getString("win-sound", "")));
        }
        return result;
    }

    private Map<String, Category> loadCategories(ConfigurationSection section) {
        Map<String, Category> result = new LinkedHashMap<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(id);
                result.put(id, new Category(id, s == null ? id : s.getString("name", id),
                        s == null ? "PAPER" : s.getString("icon", "PAPER")));
            }
        }
        result.putIfAbsent("misc", new Category("misc", "&fРазное", "PAPER"));
        return result;
    }

    /** Ключи: ID шляпы или "@rarity" для цены всех шляп редкости. */
    private Map<String, Long> loadPrices(ConfigurationSection shop, Map<String, Rarity> rarities) {
        Map<String, Long> prices = new LinkedHashMap<>();
        if (shop == null) {
            return prices;
        }
        ConfigurationSection byRarity = shop.getConfigurationSection("rarity-prices");
        if (byRarity != null) {
            for (String rarity : byRarity.getKeys(false)) {
                if (!rarities.containsKey(rarity)) {
                    problems.add("shop.yml: неизвестная редкость '" + rarity + "' в rarity-prices");
                    continue;
                }
                long price = byRarity.getLong(rarity);
                if (price > 0) {
                    prices.put("@" + rarity, price);
                }
            }
        }
        ConfigurationSection items = shop.getConfigurationSection("items");
        if (items != null) {
            for (String hat : items.getKeys(false)) {
                long price = items.getLong(hat, -1);
                if (price < 0) {
                    problems.add("shop.yml: у шляпы '" + hat + "' некорректная цена");
                    continue;
                }
                prices.put(hat, price);
            }
        }
        return prices;
    }

    private void loadHats(ConfigurationSection file, Map<String, Rarity> rarities, Map<String, Category> categories,
                          Map<String, Long> prices, Map<String, Hat> out, Map<String, String> legacy) throws ConfigException {
        ConfigurationSection hats = file.getConfigurationSection("hats");
        if (hats == null || hats.getKeys(false).isEmpty()) {
            throw new ConfigException("hats.yml: секция hats пуста");
        }
        String defMaterial = file.getString("defaults.material", "PAPER");
        String defModel = file.getString("defaults.item-model", "minecraft:carved_pumpkin");
        List<String> defLore = file.getStringList("defaults.lore");
        for (String id : hats.getKeys(false)) {
            ConfigurationSection h = hats.getConfigurationSection(id);
            if (h == null || !ID.matcher(id).matches()) {
                problems.add("hats.yml: некорректный ID шляпы '" + id + "' — шляпа пропущена");
                continue;
            }
            Rarity rarity = rarities.get(h.getString("rarity", ""));
            if (rarity == null) {
                problems.add("hats.yml: у шляпы '" + id + "' неизвестная редкость '" + h.getString("rarity") + "' — шляпа пропущена");
                continue;
            }
            Category category = categories.get(h.getString("category", "misc"));
            if (category == null) {
                problems.add("hats.yml: у шляпы '" + id + "' неизвестная категория — использована misc");
                category = categories.get("misc");
            }
            long compensation = h.contains("compensation") ? h.getLong("compensation") : rarity.compensation();
            if (compensation < 0) {
                problems.add("hats.yml: у шляпы '" + id + "' отрицательная компенсация — взята из редкости");
                compensation = rarity.compensation();
            }
            Long price = prices.get(id);
            if (price == null) {
                price = prices.getOrDefault("@" + rarity.id(), 0L);
            }
            List<String> lore = h.isList("lore") ? h.getStringList("lore") : defLore;
            List<String> legacyIds = h.getStringList("legacy-ids");
            Hat hat = new Hat(id, h.getString("name", id), List.copyOf(lore), rarity, category,
                    h.getString("material", defMaterial), h.getString("item-model", defModel),
                    h.getInt("custom-model-data", 0), h.getString("model", ""),
                    h.getBoolean("available", true), compensation, price, List.copyOf(legacyIds));
            out.put(id, hat);
            for (String old : legacyIds) {
                String previous = legacy.putIfAbsent(old, id);
                if (previous != null) {
                    problems.add("hats.yml: старый ID '" + old + "' привязан к двум шляпам: " + previous + " и " + id);
                }
            }
        }
        if (out.isEmpty()) {
            throw new ConfigException("hats.yml: нет ни одной корректной шляпы");
        }
    }

    private Map<String, CaseDef> loadCases(ConfigurationSection file, Map<String, Hat> hats,
                                           Map<String, Rarity> rarities, Settings settings) {
        Map<String, CaseDef> result = new LinkedHashMap<>();
        ConfigurationSection cases = file == null ? null : file.getConfigurationSection("cases");
        if (cases == null) {
            problems.add("cases.yml: секция cases пуста — кейсов нет");
            return result;
        }
        for (String id : cases.getKeys(false)) {
            try {
                result.put(id, loadCase(id, cases.getConfigurationSection(id), hats, rarities));
            } catch (ConfigException e) {
                problems.add("Кейс '" + id + "' отключён: " + e.getMessage());
            }
        }
        return result;
    }

    private CaseDef loadCase(String id, ConfigurationSection c, Map<String, Hat> hats,
                             Map<String, Rarity> rarities) throws ConfigException {
        if (c == null) {
            throw new ConfigException("секция кейса некорректна");
        }
        if (!ID.matcher(id).matches()) {
            throw new ConfigException("ID может содержать только a-z, 0-9, _ и -");
        }
        String keyType = c.getString("key", id);
        if (!ID.matcher(keyType).matches()) {
            throw new ConfigException("некорректный тип ключа '" + keyType + "'");
        }
        int keyCost = c.getInt("key-cost", 1);
        if (keyCost < 1) {
            throw new ConfigException("key-cost должен быть не меньше 1");
        }
        List<CaseReward> rewards = new ArrayList<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        List<Map<?, ?>> list = c.getMapList("rewards");
        if (list.isEmpty()) {
            throw new ConfigException("список rewards пуст");
        }
        int n = 0;
        for (Map<?, ?> raw : list) {
            n++;
            double weight = toDouble(raw.get("weight"), Double.NaN);
            if (!(weight > 0) || Double.isInfinite(weight)) {
                throw new ConfigException("награда #" + n + ": вес должен быть положительным числом");
            }
            List<Hat> matched = new ArrayList<>();
            if (raw.get("hat") != null) {
                Hat hat = hats.get(String.valueOf(raw.get("hat")));
                if (hat == null) {
                    throw new ConfigException("награда #" + n + ": неизвестная шляпа '" + raw.get("hat") + "'");
                }
                if (!hat.available()) {
                    problems.add("Кейс '" + id + "': шляпа '" + hat.id() + "' недоступна (available: false) — награда пропущена");
                    continue;
                }
                matched.add(hat);
            } else if (raw.get("pool") instanceof Map<?, ?> pool) {
                Object rarity = pool.get("rarity");
                Object category = pool.get("category");
                if (rarity != null && !rarities.containsKey(String.valueOf(rarity))) {
                    throw new ConfigException("награда #" + n + ": неизвестная редкость '" + rarity + "'");
                }
                for (Hat hat : hats.values()) {
                    if (!hat.available()) {
                        continue;
                    }
                    if (rarity != null && !hat.rarity().id().equals(String.valueOf(rarity))) {
                        continue;
                    }
                    if (category != null && !hat.category().id().equals(String.valueOf(category))) {
                        continue;
                    }
                    matched.add(hat);
                }
                if (matched.isEmpty()) {
                    throw new ConfigException("награда #" + n + ": под условия pool не подходит ни одна шляпа");
                }
            } else {
                throw new ConfigException("награда #" + n + ": укажите hat или pool");
            }
            for (Hat hat : matched) {
                Integer existing = index.get(hat.id());
                if (existing != null) {
                    // Одна шляпа в двух строках: веса складываются, чтобы вероятность в меню была честной.
                    CaseReward old = rewards.get(existing);
                    rewards.set(existing, new CaseReward(hat, old.weight() + weight));
                } else {
                    index.put(hat.id(), rewards.size());
                    rewards.add(new CaseReward(hat, weight));
                }
            }
        }
        if (rewards.isEmpty()) {
            throw new ConfigException("нет ни одной доступной награды");
        }
        double total = rewards.stream().mapToDouble(CaseReward::weight).sum();

        ConfigurationSection a = c.getConfigurationSection("animation");
        AnimationSettings animation = new AnimationSettings(
                a == null || a.getBoolean("enabled", true),
                Math.max(500, a == null ? 5000 : a.getLong("duration-ms", 5000)),
                Math.max(10, Math.min(200, a == null ? 45 : a.getInt("steps", 45))),
                a == null || a.getBoolean("sounds", true),
                a == null ? "minecraft:ui.button.click" : a.getString("tick-sound", "minecraft:ui.button.click"),
                a == null ? "minecraft:entity.player.levelup" : a.getString("finish-sound", "minecraft:entity.player.levelup"),
                a == null ? "minecraft:entity.experience_orb.pickup" : a.getString("duplicate-sound", "minecraft:entity.experience_orb.pickup"),
                a == null ? "BLACK_STAINED_GLASS_PANE" : a.getString("frame", "BLACK_STAINED_GLASS_PANE"),
                a == null ? "LIME_STAINED_GLASS_PANE" : a.getString("pointer", "LIME_STAINED_GLASS_PANE"));

        int broadcastMin = Integer.MAX_VALUE;
        String minRarity = c.getString("broadcast.min-rarity", "");
        if (!minRarity.isEmpty()) {
            Rarity r = rarities.get(minRarity);
            if (r == null) {
                throw new ConfigException("broadcast.min-rarity: неизвестная редкость '" + minRarity + "'");
            }
            broadcastMin = r.order();
        }
        double multiplier = c.getDouble("duplicate.multiplier", 1.0);
        long bonus = c.getLong("duplicate.bonus", 0);
        if (multiplier < 0 || bonus < 0) {
            throw new ConfigException("duplicate.multiplier и duplicate.bonus не могут быть отрицательными");
        }
        return new CaseDef(id, c.getBoolean("enabled", true), c.getString("name", id),
                List.copyOf(c.getStringList("description")),
                c.getString("icon.material", "CHEST"), c.getString("icon.item-model", ""),
                c.getInt("icon.custom-model-data", 0),
                keyType, keyCost, c.getString("permission", ""),
                animation, c.getString("win-message", ""),
                c.getBoolean("broadcast.enabled", false), broadcastMin, c.getString("broadcast.message", ""),
                multiplier, bonus, List.copyOf(rewards), total);
    }

    /** "protection" → "minecraft:protection". */
    public static String normalizeKey(String key) {
        String k = key.trim().toLowerCase(Locale.ROOT);
        return k.contains(":") ? k : "minecraft:" + k;
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String path) throws ConfigException {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ConfigException(path + ": недопустимое значение '" + value + "'");
        }
    }
}
