package ru.qw.qwhatcase.service;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import ru.qw.qwhatcase.storage.LegacyImport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Разбор players.yml старого плагина PTrap:
 * <pre>
 * &lt;uuid&gt;:
 *   name: Ник
 *   hats:
 *     hat_90: { ==: org.bukkit.inventory.ItemStack, ... }
 * </pre>
 * YAML читается «как есть» (без создания предметов), поэтому разбор не зависит от версии сервера,
 * а неизвестные записи сохраняются в исходном виде.
 */
public final class LegacyParser {
    public record Plan(List<LegacyImport> players, int hats, int mapped, int unknown, List<String> problems) {
    }

    private LegacyParser() {
    }

    public static Plan parse(String yamlText, Function<String, Optional<String>> mapper) {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(64 * 1024 * 1024);
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object root = yaml.load(yamlText);
        List<LegacyImport> players = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int hats = 0;
        int mapped = 0;
        int unknown = 0;
        if (!(root instanceof Map<?, ?> map)) {
            return new Plan(players, 0, 0, 0, problems);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                problems.add("Пропущен корневой ключ, не являющийся UUID: " + key);
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> data)) {
                problems.add("Игрок " + uuid + ": неожиданный формат записи");
                continue;
            }
            String name = data.get("name") == null ? null : String.valueOf(data.get("name"));
            List<String> ids = new ArrayList<>();
            List<LegacyImport.Unknown> unknowns = new ArrayList<>();
            if (data.get("hats") instanceof Map<?, ?> owned) {
                for (Map.Entry<?, ?> hat : owned.entrySet()) {
                    hats++;
                    String oldId = String.valueOf(hat.getKey());
                    Optional<String> newId = mapper.apply(oldId);
                    if (newId.isPresent()) {
                        mapped++;
                        if (!ids.contains(newId.get())) {
                            ids.add(newId.get());
                        }
                    } else {
                        unknown++;
                        unknowns.add(new LegacyImport.Unknown(oldId, dump(Map.of(oldId, hat.getValue() == null ? "" : hat.getValue()))));
                        problems.add("Игрок " + (name == null ? uuid : name + " (" + uuid + ")") + ": неизвестная шляпа '" + oldId + "'");
                    }
                }
            } else if (data.containsKey("hats")) {
                problems.add("Игрок " + uuid + ": секция hats имеет неожиданный формат");
            }
            if (!ids.isEmpty() || !unknowns.isEmpty()) {
                players.add(new LegacyImport(uuid, name, ids, unknowns));
            }
        }
        return new Plan(players, hats, mapped, unknown, problems);
    }

    private static String dump(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(new Representer(options), options).dump(value instanceof Map<?, ?> m ? new LinkedHashMap<>(m) : value);
    }
}
