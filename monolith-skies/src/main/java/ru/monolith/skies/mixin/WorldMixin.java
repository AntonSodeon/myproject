package ru.monolith.skies.mixin;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.monolith.skies.ship.ShipEntity;
import ru.monolith.skies.ship.ShipHolder;

@Mixin(World.class)
public abstract class WorldMixin implements ShipHolder {
    @Unique
    private final Set<ShipEntity> monolithSkies$ships = new LinkedHashSet<>();

    @Override
    public Set<ShipEntity> monolithSkies$getShips() {
        this.monolithSkies$ships.removeIf(ShipEntity::isRemoved);
        return this.monolithSkies$ships;
    }
}
