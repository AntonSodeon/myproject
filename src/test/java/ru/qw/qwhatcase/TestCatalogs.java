package ru.qw.qwhatcase;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.qw.qwhatcase.config.Catalog;
import ru.qw.qwhatcase.config.ConfigLoader;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Загрузка конфигурации, поставляемой в JAR. */
public final class TestCatalogs {
    private TestCatalogs() {
    }

    public static YamlConfiguration resource(String name) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                TestCatalogs.class.getClassLoader().getResourceAsStream(name), name), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static YamlConfiguration yaml(String text) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return yaml;
    }

    public static Catalog bundled() throws ConfigLoader.ConfigException {
        return new ConfigLoader().load(resource("config.yml"), resource("hats.yml"), resource("cases.yml"), resource("shop.yml"));
    }
}
