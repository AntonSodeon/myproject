package ru.monolith.arsenal.compat.valkyrienskies;

import org.valkyrienskies.mod.api.ValkyrienSkies;
import ru.monolith.arsenal.physics.VehiclePhysicsBackend;

/**
 * Valkyrien Skies integration. Only loaded when the {@code valkyrienskies} mod is present, so no VS class is
 * touched otherwise.
 */
public final class ValkyrienSkiesCompat {
    public static final String MOD_ID = "valkyrienskies";

    private ValkyrienSkiesCompat() {
    }

    /**
     * Diagnostics: assembles the test hull with VS alone (optionally with the marker block and/or our controller),
     * to tell VS behaviour apart from adapter behaviour. Returns the ship id.
     */
    public static long createPlainVsShip(net.minecraft.server.world.ServerWorld world, net.minecraft.util.math.BlockPos origin,
                                         boolean marker, boolean attachment) {
        java.util.Set<net.minecraft.util.math.BlockPos> blocks = new java.util.LinkedHashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.add(origin.add(x, 0, z));
                world.setBlockState(origin.add(x, 0, z), net.minecraft.block.Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        if (marker) {
            blocks.add(origin.add(0, 1, -2));
            world.setBlockState(origin.add(0, 1, -2), net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
        }
        var ship = org.valkyrienskies.mod.common.assembly.ShipAssembler.assembleToShip(world, blocks, 1.0);
        if (attachment && ship instanceof org.valkyrienskies.core.api.ships.LoadedServerShip loaded) {
            loaded.setAttachment(VehicleControlAttachment.class, new VehicleControlAttachment("diagnostic"));
        }
        return ship.getId();
    }

    /** Diagnostics: world position of any loaded VS ship. */
    public static String vsShipPosition(net.minecraft.server.world.ServerWorld world, long id) {
        var ship = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(id);
        return ship == null ? "not loaded" : ship.getTransform().getPositionInWorld().toString();
    }

    /** Diagnostics: world Y of any loaded VS ship, NaN if not loaded. */
    public static double vsShipY(net.minecraft.server.world.ServerWorld world, long id) {
        var ship = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(id);
        return ship == null ? Double.NaN : ship.getTransform().getPositionInWorld().y();
    }

    /** Diagnostics: save-relevant state of the shipyard chunks holding a ship's blocks. */
    public static String vsYardState(net.minecraft.server.world.ServerWorld world, long id) {
        var ship = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(id);
        if (ship == null) {
            return "not loaded";
        }
        StringBuilder out = new StringBuilder();
        ship.getActiveChunksSet().forEach((x, z) -> {
            var holder = world.getChunkManager().chunkLoadingManager.getCurrentChunkHolder(net.minecraft.util.math.ChunkPos.toLong(x, z));
            if (holder == null) {
                out.append(String.format("[%d,%d no-holder]", x, z));
                return;
            }
            var chunk = holder.getLatest();
            int solid = 0;
            if (chunk != null) {
                for (var section : chunk.getSectionArray()) {
                    if (!section.isEmpty()) {
                        for (int i = 0; i < 4096; i++) {
                            solid += section.getBlockState(i & 15, (i >> 8) & 15, (i >> 4) & 15).isAir() ? 0 : 1;
                        }
                    }
                }
            }
            var viaWorld = world.getChunkManager().getWorldChunk(x, z);
            out.append(String.format("[%d,%d lvl=%d acc=%s dirty=%s solid=%d sameAsWorld=%s]", x, z, holder.getLevel(), holder.isAccessible(),
                    chunk == null ? "null" : chunk.needsSaving(), solid, viaWorld == chunk));
        });
        return out.toString();
    }

    public static VehiclePhysicsBackend createBackend() {
        // Saved with each ship (Jackson); must be registered before ships load.
        ValkyrienSkies.api().registerAttachment(VehicleControlAttachment.class);
        VsVehicleBackend created = new VsVehicleBackend();
        ValkyrienSkies.api().getShipLoadEvent().on(event -> created.onShipLoad(event.getShip()));
        return created;
    }
}
