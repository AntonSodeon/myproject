package ru.qw.qwhatcase.config;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import ru.qw.qwhatcase.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Все пользовательские тексты — из messages.yml (файл локализации). */
public final class Messages {
    private final ConfigurationSection file;
    private final ConfigurationSection defaults;

    public Messages(ConfigurationSection file, ConfigurationSection defaults) {
        this.file = file;
        this.defaults = defaults;
    }

    public String raw(String key) {
        String value = file.getString(key);
        if (value == null && defaults != null) {
            value = defaults.getString(key);
        }
        return value == null ? key : value;
    }

    public String raw(String key, Map<String, ?> placeholders) {
        return Text.apply(raw(key), placeholders);
    }

    public List<String> lines(String key, Map<String, ?> placeholders) {
        List<String> source = file.isList(key) ? file.getStringList(key)
                : defaults != null && defaults.isList(key) ? defaults.getStringList(key)
                : file.isString(key) || (defaults != null && defaults.isString(key)) ? List.of(raw(key)) : List.of();
        List<String> result = new ArrayList<>(source.size());
        for (String line : source) {
            result.add(Text.apply(line, placeholders));
        }
        return result;
    }

    public Component component(String key, Map<String, ?> placeholders) {
        return Text.chat(raw("prefix") + raw(key, placeholders));
    }

    public void send(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(component(key, placeholders));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** Строка без префикса. */
    public void sendPlain(CommandSender sender, String key, Map<String, ?> placeholders) {
        sender.sendMessage(Text.chat(raw(key, placeholders)));
    }
}
