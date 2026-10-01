package ru.qw.qwhatcase.world;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.config.CaseDef;
import ru.qw.qwhatcase.storage.CasePoint;
import ru.qw.qwhatcase.util.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Постоянные надписи с названием кейса над точками (TextDisplay).
 * Надпись создаётся, когда загружены сущности чанка, и исчезает вместе с выгрузкой чанка
 * (сущность непостоянная). Повторная загрузка чанка не создаёт дубликатов: на точку хранится
 * ровно одна сущность, а все найденные «лишние» сущности с меткой этой точки удаляются.
 */
public final class CaseLabelService implements Listener {
    private final QWHatCasePlugin plugin;
    private final Map<String, UUID> labels = new HashMap<>();
    private final Map<String, Double> raised = new HashMap<>();

    public CaseLabelService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    private static Location base(CasePoint point, World world, double height) {
        return new Location(world, point.x() + 0.5, point.y() + 1.0 + height, point.z() + 0.5);
    }

    private TextDisplay entity(String key) {
        UUID id = labels.get(key);
        Entity e = id == null ? null : Bukkit.getEntity(id);
        return e instanceof TextDisplay td && td.isValid() ? td : null;
    }

    /** Создать или обновить надпись точки (если её чанк и сущности загружены). */
    public void ensure(CasePoint point) {
        World world = Bukkit.getWorld(point.world());
        CaseDef def = plugin.catalog().caseDef(point.caseId()).orElse(null);
        if (world == null || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
            return;
        }
        Chunk chunk = world.getChunkAt(point.x() >> 4, point.z() >> 4);
        LabelSettings s = def == null ? null : def.label();
        if (s == null || !s.enabled()) {
            remove(point.key());
            return;
        }
        TextDisplay current = entity(point.key());
        purgeStrays(chunk, point.key(), current == null ? null : current.getUniqueId());
        Location at = base(point, world, s.height() + raised.getOrDefault(point.key(), 0.0));
        if (current == null) {
            current = world.spawn(at, TextDisplay.class, td -> {
                WorldEntities.mark(td, WorldEntities.LABEL, point.key(), null);
                apply(td, s, def);
            });
            labels.put(point.key(), current.getUniqueId());
        } else {
            apply(current, s, def);
            current.teleport(at);
        }
    }

    private static void apply(TextDisplay td, LabelSettings s, CaseDef def) {
        String text = String.join("\n", s.lines()).replace("{case}", def.name());
        td.text(Text.chat(text));
        td.setBillboard(WorldEntities.billboard(s.billboard()));
        td.setShadowed(s.shadow());
        td.setSeeThrough(s.seeThrough());
        td.setLineWidth(s.lineWidth());
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        Color bg = WorldEntities.argb(s.background());
        if (bg == null) {
            td.setDefaultBackground(true);
        } else {
            td.setDefaultBackground(false);
            td.setBackgroundColor(bg);
        }
        td.setViewRange((float) (s.viewRange() / 64.0));
        td.setTransformation(WorldEntities.scaled(s.scale()));
        td.setPersistent(false);
    }

    private void purgeStrays(Chunk chunk, String key, UUID keep) {
        for (Entity e : chunk.getEntities()) {
            if (WorldEntities.LABEL.equals(WorldEntities.kind(e)) && key.equals(WorldEntities.point(e))
                    && !e.getUniqueId().equals(keep)) {
                e.remove();
            }
        }
    }

    public void remove(String key) {
        TextDisplay current = entity(key);
        if (current != null) {
            current.remove();
        }
        labels.remove(key);
        raised.remove(key);
    }

    /** Поднять надпись на время анимации (чтобы не перекрывала модели) и вернуть обратно. */
    public void raise(CasePoint point, double dy) {
        raised.put(point.key(), dy);
        ensure(point);
    }

    public void lower(CasePoint point) {
        if (raised.remove(point.key()) != null && plugin.points().byKey(point.key()) != null) {
            ensure(plugin.points().byKey(point.key()));
        }
    }

    /** Все надписи заново (старт, перезагрузка конфигурации, изменение точек). */
    public void refreshAll() {
        Set<String> alive = new HashSet<>();
        for (CasePoint point : plugin.points().all()) {
            alive.add(point.key());
            ensure(point);
        }
        for (String key : List.copyOf(labels.keySet())) {
            if (!alive.contains(key)) {
                remove(key);
            }
        }
    }

    /** Удалить ВСЕ сущности плагина в загруженных мирах (старт после аварийного перезапуска, выключение). */
    public int purgeAll(boolean labelsToo) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                String kind = WorldEntities.kind(e);
                if (kind != null && (labelsToo || !WorldEntities.LABEL.equals(kind))) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (labelsToo) {
            labels.clear();
        }
        return removed;
    }

    /**
     * Загрузка чанка. Наши надписи непостоянны и не сохраняются, поэтому для чанка без сохранённых сущностей
     * событие загрузки сущностей может не прийти — надпись восстанавливается по загрузке самого чанка.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void chunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        scheduleEnsure(event.getChunk());
    }

    private void scheduleEnsure(Chunk chunk) {
        String world = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (CasePoint point : plugin.points().all()) {
                if (point.world().equals(world) && point.x() >> 4 == cx && point.z() >> 4 == cz) {
                    ensure(point);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesLoad(EntitiesLoadEvent event) {
        // Наши сущности непостоянны и не должны загружаться с диска; если такие нашлись — это остаток, удаляем.
        for (Entity e : event.getEntities()) {
            if (WorldEntities.isOurs(e)) {
                e.remove();
            }
        }
        scheduleEnsure(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void entitiesUnload(EntitiesUnloadEvent event) {
        for (Entity e : event.getEntities()) {
            String kind = WorldEntities.kind(e);
            if (WorldEntities.LABEL.equals(kind)) {
                labels.remove(WorldEntities.point(e), e.getUniqueId());
            } else if (WorldEntities.ANIMATION.equals(kind)) {
                plugin.worldAnimations().chunkUnloaded(WorldEntities.point(e));
            }
        }
    }

    public int count() {
        return (int) labels.keySet().stream().filter(k -> entity(k) != null).count();
    }
}
