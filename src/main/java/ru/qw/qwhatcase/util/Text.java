package ru.qw.qwhatcase.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Перевод строк с &-цветами и &#RRGGBB в компоненты Adventure. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .hexCharacter('#')
            .build();

    private Text() {
    }

    public static String apply(String template, Map<String, ?> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    /** Текст для чата. */
    public static Component chat(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy);
    }

    /** Текст для названий и описаний предметов: без курсива по умолчанию. */
    public static Component item(String legacy) {
        return chat(legacy).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines) {
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(item(line));
        }
        return result;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String strip(String legacy) {
        return plain(chat(legacy));
    }
}
