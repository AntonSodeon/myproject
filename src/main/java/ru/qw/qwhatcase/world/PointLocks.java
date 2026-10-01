package ru.qw.qwhatcase.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Занятость физических точек кейсов: на одной точке одновременно одно внешнее открытие
 * (или предпросмотр). Разные точки независимы, даже если относятся к одному кейсу.
 * Захват выполняется до списания ключа, освобождение — при завершении или любой ошибке.
 */
public final class PointLocks {
    public record Holder(UUID player, String operation) {
    }

    private final Map<String, Holder> locks = new ConcurrentHashMap<>();

    /** @return true, если точка захвачена этим вызовом */
    public boolean tryLock(String point, UUID player, String operation) {
        return locks.putIfAbsent(point, new Holder(player, operation)) == null;
    }

    /** Переназначить операцию захваченной точки (ID операции известен только после розыгрыша). */
    public void setOperation(String point, UUID player, String operation) {
        locks.computeIfPresent(point, (k, h) -> h.player().equals(player) ? new Holder(player, operation) : h);
    }

    public void unlock(String point, String operation) {
        locks.computeIfPresent(point, (k, h) -> h.operation().equals(operation) ? null : h);
    }

    public boolean isLocked(String point) {
        return locks.containsKey(point);
    }

    public Holder holder(String point) {
        return locks.get(point);
    }

    public void clear() {
        locks.clear();
    }

    public int size() {
        return locks.size();
    }
}
