package ru.monolith.arsenal.compat.valkyrienskies;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.physics.ScheduledForces;
import ru.monolith.arsenal.physics.VehicleBody;
import ru.monolith.arsenal.physics.VehiclePhysicsBackend;

/** Vehicle bodies backed by Valkyrien Skies ships. Server thread only. */
final class VsVehicleBackend implements VehiclePhysicsBackend {
    /** Forces queued during the current game tick, by ship id. */
    private final Long2ObjectOpenHashMap<Accumulator> queued = new Long2ObjectOpenHashMap<>();
    /** Ships that received a non-empty snapshot last tick and must be reset if nothing new arrives. */
    private final LongOpenHashSet published = new LongOpenHashSet();
    /** Ships assembled before they were loaded: id -> attachment kind, consumed by {@link #onShipLoad}. */
    private final Map<Long, String> pendingAttachments = new ConcurrentHashMap<>();
    private MinecraftServer server;

    static final class Accumulator {
        final Vector3d force = new Vector3d();
        final Vector3d torque = new Vector3d();
        final List<ForceSnapshot.PointForce> pointForces = new ArrayList<>();
    }

    @Override
    public String describe() {
        String version = FabricLoader.getInstance().getModContainer(ValkyrienSkiesCompat.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        return "Valkyrien Skies " + version;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<VehicleBody> find(ServerWorld world, long id) {
        this.server = world.getServer();
        LoadedServerShip ship = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(id);
        if (ship == null || !ship.getChunkClaimDimension().equals(ValkyrienSkies.api().getDimensionId(world))
                || ship.getAttachment(VehicleControlAttachment.class) == null) {
            return Optional.empty();
        }
        return Optional.of(new VsVehicleBody(ship, this));
    }

    @Override
    public List<VehicleBody> bodies(ServerWorld world) {
        this.server = world.getServer();
        String dimension = ValkyrienSkies.api().getDimensionId(world);
        List<VehicleBody> result = new ArrayList<>();
        for (LoadedServerShip ship : ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips()) {
            if (ship.getChunkClaimDimension().equals(dimension) && ship.getAttachment(VehicleControlAttachment.class) != null) {
                result.add(new VsVehicleBody(ship, this));
            }
        }
        return result;
    }

    /**
     * Diagnostic hull: a 3x5 iron deck with a redstone block marking the front (-Z), assembled by VS's own
     * {@link ShipAssembler}. Fails if any target position is occupied.
     */
    @Override
    public OptionalLong createTestBody(ServerWorld world, BlockPos origin) {
        this.server = world.getServer();
        Set<BlockPos> blocks = new LinkedHashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.add(origin.add(x, 0, z));
            }
        }
        BlockPos marker = origin.add(0, 1, -2);
        for (BlockPos pos : blocks) {
            if (!world.getBlockState(pos).isReplaceable()) {
                return OptionalLong.empty();
            }
        }
        if (!world.getBlockState(marker).isReplaceable()) {
            return OptionalLong.empty();
        }
        BlockState hull = Blocks.IRON_BLOCK.getDefaultState();
        for (BlockPos pos : blocks) {
            world.setBlockState(pos, hull);
        }
        world.setBlockState(marker, Blocks.REDSTONE_BLOCK.getDefaultState());
        blocks.add(marker);

        ServerShip ship = ShipAssembler.assembleToShip(world, blocks, 1.0);
        MonolithArsenal.LOGGER.info("Created Valkyrien Skies test body {} at {}", ship.getId(), origin.toShortString());
        LoadedServerShip loaded = ship instanceof LoadedServerShip l ? l
                : ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(ship.getId());
        if (loaded == null) {
            // VS loads new ships on a following tick; the ShipLoadEvent listener adds the attachment then.
            this.pendingAttachments.put(ship.getId(), VehicleControlAttachment.KIND_TEST_BODY);
        } else {
            loaded.setAttachment(VehicleControlAttachment.class, new VehicleControlAttachment(VehicleControlAttachment.KIND_TEST_BODY));
        }
        return OptionalLong.of(ship.getId());
    }

    @Override
    public boolean remove(ServerWorld world, long id) {
        Optional<VehicleBody> body = this.find(world, id);
        if (body.isEmpty()) {
            return false;
        }
        LoadedServerShip ship = ((VsVehicleBody) body.get()).ship();
        ship.getAttachment(VehicleControlAttachment.class).publish(ForceSnapshot.EMPTY);
        this.queued.remove(id);
        this.published.remove(id);
        ScheduledForces.cancel(id);
        // deleteBlocks = true removes the hull from the shipyard; dropBlocks = false spawns no items.
        ShipAssembler.INSTANCE.deleteShip(world, ship, true, false);
        MonolithArsenal.LOGGER.info("Removed Valkyrien Skies test body {}", id);
        return true;
    }

    void onShipLoad(LoadedServerShip ship) {
        String kind = this.pendingAttachments.remove(ship.getId());
        if (kind != null && ship.getAttachment(VehicleControlAttachment.class) == null) {
            ship.setAttachment(VehicleControlAttachment.class, new VehicleControlAttachment(kind));
        }
    }

    Accumulator queue(long id) {
        return this.queued.computeIfAbsent(id, ignored -> new Accumulator());
    }

    @Override
    public void publishQueuedForces() {
        if (this.server == null || (this.queued.isEmpty() && this.published.isEmpty())) {
            return;
        }
        var ships = ValkyrienSkies.api().getServerShipWorld(this.server).getLoadedShips();
        LongOpenHashSet stillActive = new LongOpenHashSet();
        for (var entry : this.queued.long2ObjectEntrySet()) {
            LoadedServerShip ship = ships.getById(entry.getLongKey());
            VehicleControlAttachment attachment = ship == null ? null : ship.getAttachment(VehicleControlAttachment.class);
            if (attachment == null) {
                continue;
            }
            Accumulator a = entry.getValue();
            attachment.publish(new ForceSnapshot(new Vector3d(a.force), new Vector3d(a.torque), List.copyOf(a.pointForces)));
            stillActive.add(entry.getLongKey());
        }
        // Bodies that got forces last tick but none this tick stop accelerating.
        for (long id : this.published) {
            if (!stillActive.contains(id)) {
                LoadedServerShip ship = ships.getById(id);
                VehicleControlAttachment attachment = ship == null ? null : ship.getAttachment(VehicleControlAttachment.class);
                if (attachment != null) {
                    attachment.publish(ForceSnapshot.EMPTY);
                }
            }
        }
        this.published.clear();
        this.published.addAll(stillActive);
        this.queued.clear();
    }

    @Override
    public void clear() {
        this.queued.clear();
        this.published.clear();
        this.pendingAttachments.clear();
        this.server = null;
    }
}
