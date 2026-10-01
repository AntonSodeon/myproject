package ru.qw.qwhatcase.world;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Служебные метки сущностей плагина. Плагин удаляет только сущности со своей меткой —
 * никогда не по одному лишь типу или близости к блоку.
 * Все сущности создаются непостоянными (setPersistent(false)): они не сохраняются в файлы мира,
 * поэтому после перезапуска или выгрузки чанка не остаётся «осиротевших» копий.
 */
public final class WorldEntities {
    public static final NamespacedKey KIND = new NamespacedKey("qwhatcase", "entity");
    public static final NamespacedKey POINT = new NamespacedKey("qwhatcase", "point");
    public static final NamespacedKey OPERATION = new NamespacedKey("qwhatcase", "operation");
    public static final String TAG = "qwhatcase";
    public static final String LABEL = "label";
    public static final String ANIMATION = "anim";

    private WorldEntities() {
    }

    public static void mark(Entity entity, String kind, String point, String operation) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(KIND, PersistentDataType.STRING, kind);
        pdc.set(POINT, PersistentDataType.STRING, point);
        if (operation != null) {
            pdc.set(OPERATION, PersistentDataType.STRING, operation);
        }
        entity.setPersistent(false);
        entity.addScoreboardTag(TAG);
        entity.addScoreboardTag(TAG + "_" + kind);
    }

    public static String kind(Entity entity) {
        return entity.getPersistentDataContainer().get(KIND, PersistentDataType.STRING);
    }

    public static String point(Entity entity) {
        return entity.getPersistentDataContainer().get(POINT, PersistentDataType.STRING);
    }

    public static String operation(Entity entity) {
        return entity.getPersistentDataContainer().get(OPERATION, PersistentDataType.STRING);
    }

    public static boolean isOurs(Entity entity) {
        return kind(entity) != null;
    }

    public static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    public static Transformation scaledRotated(float scale, float yawRadians, float dy) {
        return new Transformation(new Vector3f(0, dy, 0), new Quaternionf().rotateY(yawRadians),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    public static Display.Billboard billboard(String name) {
        try {
            return Display.Billboard.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Display.Billboard.CENTER;
        }
    }

    /** #AARRGGBB → Color (null — стандартный фон). */
    public static Color argb(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{8}")) {
            return null;
        }
        return Color.fromARGB((int) Long.parseLong(value.substring(1), 16));
    }
}
