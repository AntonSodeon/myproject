package ru.qw.qwhatcase.service;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.Settings;
import ru.qw.qwhatcase.util.Text;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Отправка ресурс-пака и учёт его состояния у каждого игрока. */
public final class PackService {
    public enum State {
        PENDING, LOADED, DECLINED, FAILED
    }

    private final QWHatCasePlugin plugin;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public PackService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private Settings settings() {
        return plugin.catalog().settings();
    }

    public UUID packId() {
        Settings s = settings();
        return UUID.nameUUIDFromBytes(("qwhatcase:" + s.packUrl() + "#" + s.packSha1()).getBytes(StandardCharsets.UTF_8));
    }

    public boolean tracking() {
        return settings().packMode() != Settings.PackMode.NONE;
    }

    public State state(Player player) {
        return states.getOrDefault(player.getUniqueId(), State.PENDING);
    }

    public void setState(Player player, State state) {
        states.put(player.getUniqueId(), state);
    }

    public void forget(UUID uuid) {
        states.remove(uuid);
    }

    /** Можно ли открывать кейсы (по умолчанию — только после успешной загрузки пака). */
    public boolean canOpenCases(Player player) {
        return !tracking() || !settings().blockCasesUntilLoaded() || state(player) == State.LOADED;
    }

    /** Есть ли у игрока модели шляп (для предупреждения в меню). */
    public boolean hasModels(Player player) {
        return !tracking() || state(player) == State.LOADED;
    }

    /** Отправляет пак (режим PLUGIN). В режиме SERVER только сообщает, как перезайти. */
    public boolean send(Player player) {
        Settings s = settings();
        if (s.packMode() != Settings.PackMode.PLUGIN) {
            return false;
        }
        states.put(player.getUniqueId(), State.PENDING);
        ResourcePackInfo info = s.packSha1().isEmpty()
                ? ResourcePackInfo.resourcePackInfo(packId(), URI.create(s.packUrl()), "")
                : ResourcePackInfo.resourcePackInfo(packId(), URI.create(s.packUrl()), s.packSha1());
        ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(s.packRequired())
                .replace(false);
        if (!s.packPrompt().isEmpty()) {
            request.prompt(Text.chat(s.packPrompt()));
        }
        player.sendResourcePacks(request.build());
        return true;
    }
}
