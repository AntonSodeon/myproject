package ru.qw.qwhatcase.world;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Чтение настроек внешней анимации и надписей: значение из секции кейса (если задано),
 * иначе из общей секции config.yml, иначе значение по умолчанию.
 */
public final class WorldConfig {
    private final ConfigurationSection override;
    private final ConfigurationSection global;
    private final List<String> problems;
    private final String where;

    private WorldConfig(ConfigurationSection override, ConfigurationSection global, List<String> problems, String where) {
        this.override = override;
        this.global = global;
        this.problems = problems;
        this.where = where;
    }

    private ConfigurationSection src(String path) {
        if (override != null && override.contains(path)) {
            return override;
        }
        return global != null && global.contains(path) ? global : null;
    }

    private double dbl(String path, double def, double min, double max) {
        ConfigurationSection s = src(path);
        double v = s == null ? def : s.getDouble(path, def);
        if (v < min || v > max || Double.isNaN(v)) {
            problems.add(where + "." + path + ": значение " + v + " вне диапазона " + min + "…" + max + " — использовано " + def);
            return def;
        }
        return v;
    }

    private int integer(String path, int def, int min, int max) {
        return (int) Math.round(dbl(path, def, min, max));
    }

    private boolean bool(String path, boolean def) {
        ConfigurationSection s = src(path);
        return s == null ? def : s.getBoolean(path, def);
    }

    private String str(String path, String def) {
        ConfigurationSection s = src(path);
        return s == null ? def : s.getString(path, def);
    }

    private List<String> lines(String path, List<String> def) {
        ConfigurationSection s = src(path);
        if (s == null) {
            return def;
        }
        return s.isList(path) ? List.copyOf(s.getStringList(path)) : List.of(s.getString(path, ""));
    }

    private SoundSpec sound(String path, String defSound, float defVolume, float defPitch) {
        return new SoundSpec(bool(path + ".enabled", true), str(path + ".sound", defSound),
                (float) dbl(path + ".volume", defVolume, 0, 10), (float) dbl(path + ".pitch", defPitch, 0.5, 2.0));
    }

    private ParticleSpec particles(String path, String defType, int defCount, double defOffset, double defSpeed) {
        return new ParticleSpec(bool(path + ".enabled", true), str(path + ".type", defType).toUpperCase(Locale.ROOT),
                integer(path + ".count", defCount, 0, 500),
                dbl(path + ".offset-x", defOffset, 0, 10), dbl(path + ".offset-y", defOffset, 0, 10),
                dbl(path + ".offset-z", defOffset, 0, 10), dbl(path + ".speed", defSpeed, 0, 10),
                str(path + ".color", ""), (float) dbl(path + ".size", 1.2, 0.1, 4));
    }

    public static WorldAnimSettings animation(ConfigurationSection global, ConfigurationSection override,
                                              List<String> problems, String where) {
        WorldConfig c = new WorldConfig(override, global, problems, where);
        int visible = c.integer("visible-models", 5, 1, 15);
        if (visible % 2 == 0) {
            problems.add(where + ".visible-models: нужно нечётное число, чтобы была центральная позиция — использовано " + (visible + 1));
            visible++;
        }
        String transform = c.str("model-transform", "HEAD").toUpperCase(Locale.ROOT);
        return new WorldAnimSettings(
                c.bool("enabled", true),
                (long) c.dbl("spin-duration-ms", 6000, 1000, 60000),
                (long) c.dbl("result-duration-ms", 3000, 500, 60000),
                visible, c.integer("scroll-items", 30, 3, 500),
                c.dbl("height", 0.9, -2, 10), c.dbl("spacing", 0.7, 0.1, 5),
                (float) c.dbl("model-scale", 0.6, 0.05, 5), (float) c.dbl("center-scale", 0.8, 0.05, 5),
                (float) c.dbl("winner-scale", 1.2, 0.05, 8), c.dbl("winner-rise", 0.3, -2, 5),
                c.dbl("rotation-speed", 120, 0, 2000), c.dbl("deceleration-power", 3.0, 1.0, 8.0),
                c.dbl("view-range", 48, 4, 256),
                transform, (float) c.dbl("model-yaw-offset", 0, -360, 360),
                c.bool("glow-center", true), c.str("center-marker", "&e▼"), c.dbl("slowdown-at", 0.75, 0, 1),
                c.sound("sounds.start", "minecraft:custom.mystery_crate.open", 1f, 1f),
                c.sound("sounds.tick", "minecraft:custom.mystery_crate.scroll", 0.6f, 0.9f),
                (float) c.dbl("sounds.tick.pitch-end", 1.4, 0.5, 2.0),
                c.sound("sounds.slowdown", "minecraft:block.note_block.chime", 0.8f, 0.7f),
                c.sound("sounds.win", "minecraft:custom.mystery_crate.unlock", 1f, 1f),
                c.sound("sounds.duplicate", "minecraft:custom.currency.tokens", 1f, 1f),
                c.particles("particles.start", "ENCHANT", 40, 0.6, 0.6),
                c.particles("particles.spin", "END_ROD", 2, 0.05, 0.01),
                c.particles("particles.result", "DUST", 40, 0.5, 0.0),
                c.integer("particles.max-per-tick", 80, 0, 1000),
                c.lines("result-label.lines", List.of("&fВы получили: {hat}", "&7Редкость: {rarity}")),
                c.lines("result-label.duplicate-lines", List.of("&fВыпала: {hat}", "&eУже есть в коллекции",
                        "&7Компенсация: &e{tokens} жетонов")),
                (float) c.dbl("result-label.scale", 0.9, 0.1, 5), c.dbl("result-label.offset-y", 0.75, -3, 5),
                c.str("result-label.background", "#80000000"), c.bool("result-label.shadow", true));
    }

    public static LabelSettings label(ConfigurationSection global, ConfigurationSection override,
                                      List<String> problems, String where) {
        WorldConfig c = new WorldConfig(override, global, problems, where);
        String billboard = c.str("billboard", "CENTER").toUpperCase(Locale.ROOT);
        if (!List.of("CENTER", "VERTICAL", "HORIZONTAL", "FIXED").contains(billboard)) {
            problems.add(where + ".billboard: ожидается CENTER, VERTICAL, HORIZONTAL или FIXED — использовано CENTER");
            billboard = "CENTER";
        }
        String background = c.str("background", "#40000000");
        if (!background.equalsIgnoreCase("default") && !background.matches("#[0-9a-fA-F]{8}")) {
            problems.add(where + ".background: ожидается #AARRGGBB или default — использовано default");
            background = "default";
        }
        return new LabelSettings(c.bool("enabled", true), c.dbl("height", 0.45, -2, 10),
                new ArrayList<>(c.lines("lines", List.of("{case}", "&7ПКМ — открыть кейс"))),
                (float) c.dbl("scale", 1.0, 0.1, 10), c.dbl("view-range", 32, 2, 256), background,
                c.bool("shadow", true), c.bool("see-through", false), billboard,
                c.integer("line-width", 200, 20, 1000), c.dbl("raise-during-animation", 0.9, 0, 5));
    }
}
