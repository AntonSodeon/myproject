package ru.qw.qwhatcase.world;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.config.Hat;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.storage.OpeningResult;
import ru.qw.qwhatcase.util.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Активные открытия в мире. Отвечает за занятость точек, завершение и любые прерывания:
 * при каждом исходе точка освобождается, задачи отменяются, временные сущности удаляются,
 * а награда (уже сохранённая в БД) не трогается.
 */
public final class WorldAnimationService implements Listener {
    private final QWHatCasePlugin plugin;
    private final PointLocks locks = new PointLocks();
    private final Map<String, WorldAnimation> active = new ConcurrentHashMap<>();

    public WorldAnimationService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public PointLocks locks() {
        return locks;
    }

    public boolean isActive(String pointKey) {
        return active.containsKey(pointKey);
    }

    public int activeCount() {
        return active.size();
    }

    /**
     * Запуск после успешного сохранения (или предпросмотра). Точка должна быть уже захвачена вызывающим
     * с тем же токеном operationToken.
     * @return false — анимацию запустить нельзя; точка освобождена, итог надо показать иначе
     */
    public boolean start(String lockToken, CasePoint point, CaseDef def, Hat winner, OpeningResult result,
                         UUID owner, String ownerName, Location viewer, boolean preview, String busyToken) {
        String operation = result != null ? result.operationId().toString() : lockToken;
        locks.setOperation(point.key(), owner, operation);
        WorldAnimation animation = new WorldAnimation(plugin, this, operation, point, def, winner, result,
                owner, ownerName, preview, busyToken);
        active.put(point.key(), animation);
        boolean started;
        try {
            started = animation.start(viewer);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Анимация на точке " + point.key() + " не запущена: " + e);
            animation.cleanup();
            started = false;
        }
        if (!started) {
            active.remove(point.key(), animation);
            locks.unlock(point.key(), operation);
        }
        return started;
    }

    void finish(WorldAnimation animation) {
        end(animation);
    }

    /** Прерывание: награда уже сохранена — если игрок ещё не видел итог, сообщаем его в чат. */
    void abort(WorldAnimation animation, String reason) {
        if (!animation.revealed()) {
            animation.reveal();
        }
        plugin.getLogger().info("Анимация " + animation.operation + " на точке " + animation.point.key() + " прервана: " + reason);
        end(animation);
    }

    public void abort(String pointKey, String reason) {
        WorldAnimation animation = active.get(pointKey);
        if (animation != null) {
            abort(animation, reason);
        }
    }

    private void end(WorldAnimation animation) {
        animation.cleanup();
        active.remove(animation.point.key(), animation);
        locks.unlock(animation.point.key(), animation.operation);
        if (animation.busyToken != null) {
            plugin.openings().releaseBusy(animation.owner, animation.busyToken);
        }
    }

    /** Чанк точки выгружается (несмотря на тикет) — завершаем безопасно. */
    public void chunkUnloaded(String pointKey) {
        WorldAnimation animation = active.get(pointKey);
        if (animation != null) {
            abort(animation, "chunk-unloaded");
        }
    }

    @EventHandler
    public void worldUnload(WorldUnloadEvent event) {
        for (WorldAnimation animation : List.copyOf(active.values())) {
            if (animation.point.world().equals(event.getWorld().getName())) {
                abort(animation, "world-unloaded");
            }
        }
    }

    /** Владелец вышел: анимация доигрывается для окружающих, итог он увидит при входе (open.pending). */
    public void ownerQuit(UUID uuid) {
        for (WorldAnimation animation : active.values()) {
            if (animation.owner.equals(uuid)) {
                animation.ownerLeft = true;
            }
        }
    }

    /** Выключение: только очистка (итог не помечается показанным — игрок увидит его при входе). */
    public void shutdown() {
        for (WorldAnimation animation : List.copyOf(active.values())) {
            animation.ownerLeft = true;
            animation.cleanup();
        }
        active.clear();
        locks.clear();
    }

    void revealToOwner(WorldAnimation animation) {
        Map<String, Object> ph = animation.placeholders();
        if (animation.preview) {
            Player p = animation.ownerPlayer();
            if (p != null) {
                plugin.messages().send(p, "world.preview-result", ph);
            }
            return;
        }
        OpeningResult result = animation.result;
        boolean duplicate = result.outcome() == OpeningResult.Outcome.DUPLICATE;
        Player player = animation.ownerLeft ? null : animation.ownerPlayer();
        if (player != null) {
            for (String line : plugin.messages().lines(duplicate ? "world.result-duplicate" : "world.result-new", ph)) {
                player.sendMessage(Text.chat(line));
            }
            plugin.storage().submit(db -> {
                db.markShown(result.operationId());
                return null;
            });
        }
        plugin.openings().broadcast(animation.caseDef, animation.winner, ph);
    }

    /** Для отчёта /hatcases point list и проверок. */
    public Map<String, WorldAnimation> active() {
        return active;
    }
}
