package ru.monolith.skies.api;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ru.monolith.skies.ship.ShipAssembler;
import ru.monolith.skies.ship.ShipEntity;
import ru.monolith.skies.ship.ShipHolder;
import ru.monolith.skies.ship.ShipStructure;

/** Entry point for other mods. */
public final class MonolithSkiesApi {
    public static final int API_VERSION = 1;

    private MonolithSkiesApi() {
    }

    /** Ships that are loaded and ticking in {@code world}. */
    public static List<Ship> getShips(World world) {
        return ((ShipHolder) world).monolithSkies$getShips().stream()
                .filter(ship -> !ship.isRemoved())
                .map(Ship.class::cast)
                .toList();
    }

    /** The ship that has a block at {@code worldPos}, if any. */
    public static Optional<Ship> getShipAt(World world, Vec3d worldPos) {
        return ((ShipHolder) world).monolithSkies$getShips().stream()
                .filter(ship -> !ship.isRemoved() && ship.getBoundingBox().contains(worldPos))
                .filter(ship -> {
                    Vec3d local = ship.worldToShip(worldPos);
                    ShipStructure structure = ship.getStructure();
                    return structure.get(MathHelper.floor(local.x), MathHelper.floor(local.y), MathHelper.floor(local.z)) != null;
                })
                .min(Comparator.comparingDouble(ship -> ship.getEntityPos().squaredDistanceTo(worldPos)))
                .map(Ship.class::cast);
    }

    /** Turns the blocks connected to {@code start} into a ship. */
    public static ShipAssembler.Result assemble(ServerWorld world, BlockPos start) {
        return ShipAssembler.assemble(world, start, ShipAssembler.DEFAULT_MAX_BLOCKS);
    }

    /** Places the ship's blocks back into the world, snapped to the nearest quarter turn. */
    public static ShipAssembler.Result disassemble(Ship ship) {
        if (!(ship instanceof ShipEntity entity) || !(entity.getEntityWorld() instanceof ServerWorld world)) {
            return ShipAssembler.Result.failure("message.monolith_skies.not_server");
        }
        return ShipAssembler.disassemble(world, entity);
    }
}
