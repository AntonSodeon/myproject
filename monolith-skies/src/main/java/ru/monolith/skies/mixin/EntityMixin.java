package ru.monolith.skies.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.monolith.skies.ship.ShipEntity;
import ru.monolith.skies.ship.ShipHolder;

/** Lets entities stand on and bump into ship blocks by adding them to vanilla movement collisions. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "findCollisionsForMovement", at = @At("RETURN"), cancellable = true)
    private static void monolithSkies$addShipCollisions(@Nullable Entity entity, World world, List<VoxelShape> regularCollisions,
                                                        Box movingEntityBoundingBox, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (entity instanceof ShipEntity) {
            return;
        }
        Set<ShipEntity> ships = ((ShipHolder) world).monolithSkies$getShips();
        if (ships.isEmpty()) {
            return;
        }
        List<VoxelShape> shapes = null;
        for (ShipEntity ship : ships) {
            if (!ship.getBoundingBox().intersects(movingEntityBoundingBox)) {
                continue;
            }
            if (shapes == null) {
                shapes = new ArrayList<>(cir.getReturnValue());
            }
            ship.collectCollisions(movingEntityBoundingBox, shapes);
        }
        if (shapes != null) {
            cir.setReturnValue(shapes);
        }
    }
}
