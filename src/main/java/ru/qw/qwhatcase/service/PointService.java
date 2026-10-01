package ru.qw.qwhatcase.service;

import org.bukkit.block.Block;
import ru.qw.qwhatcase.QWHatCasePlugin;
import ru.qw.qwhatcase.storage.CasePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Блоки в мире, привязанные к кейсам. Кэш в памяти, источник истины — БД. */
public final class PointService {
    private final QWHatCasePlugin plugin;
    private final Map<String, CasePoint> points = new ConcurrentHashMap<>();

    public PointService(QWHatCasePlugin plugin) {
        this.plugin = plugin;
    }

    public static String key(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    public void load(List<CasePoint> loaded) {
        points.clear();
        for (CasePoint point : loaded) {
            points.put(point.key(), point);
        }
    }

    public CasePoint at(Block block) {
        return points.isEmpty() ? null : points.get(key(block));
    }

    public List<CasePoint> all() {
        return new ArrayList<>(points.values());
    }

    public void add(CasePoint point, String actor, Runnable onDone) {
        plugin.storage().run(db -> {
            db.addPoint(point, actor);
            return null;
        }, ignored -> {
            points.put(point.key(), point);
            onDone.run();
        }, error -> plugin.messages().send(plugin.getServer().getConsoleSender(), "error.database"));
    }

    public void remove(Block block, String actor, java.util.function.Consumer<Boolean> onDone) {
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        plugin.storage().run(db -> db.removePoint(world, x, y, z, actor), removed -> {
            points.remove(world + ";" + x + ";" + y + ";" + z);
            onDone.accept(removed);
        }, error -> onDone.accept(false));
    }
}
