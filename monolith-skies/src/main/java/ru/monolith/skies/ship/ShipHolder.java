package ru.monolith.skies.ship;

import java.util.Set;

/** Implemented on {@link net.minecraft.world.World} by a mixin: the ships currently ticking in that world. */
public interface ShipHolder {
    Set<ShipEntity> monolithSkies$getShips();
}
