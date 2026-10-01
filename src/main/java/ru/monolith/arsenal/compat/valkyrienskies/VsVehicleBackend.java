package ru.monolith.arsenal.compat.valkyrienskies;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.physics.BodyCreation;
import ru.monolith.arsenal.physics.VehicleBody;
import ru.monolith.arsenal.physics.VehiclePhysicsBackend;

/** Vehicle bodies backed by Valkyrien Skies ships. Server thread only, except {@link #onShipLoad}. */
final class VsVehicleBackend implements VehiclePhysicsBackend {
    /** How long a freshly assembled ship may take to load before the creation fails and is cleaned up. */
    static final int CREATION_TIMEOUT_TICKS = 100;

    /** Server ticks a ship's shipyard chunks must have been loaded before queued commands are released. */
    static final int RESUME_STREAK_TICKS = 10;

    /** Commands held on the server thread while their ship is not being simulated, by ship id. */
    private final Map<Long, java.util.ArrayDeque<PhysicsCommand>> held = new java.util.HashMap<>();
    /** Consecutive server ticks each tracked ship has had all of its shipyard chunks loaded. */
    private final Map<Long, Integer> tickingStreak = new java.util.HashMap<>();

    private MinecraftServer lastServer;

    /** Pending and recently finished creations by ship id. */
    private final Map<Long, Pending> creations = new ConcurrentHashMap<>();

    private record Pending(BodyCreation creation, RegistryKey<World> world, String kind) {
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
        LoadedServerShip ship = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(id);
        return ship == null ? Optional.empty() : wrap(ship, ValkyrienSkies.api().getDimensionId(world));
    }

    @Override
    public List<VehicleBody> bodies(ServerWorld world) {
        String dimension = ValkyrienSkies.api().getDimensionId(world);
        List<VehicleBody> result = new ArrayList<>();
        for (LoadedServerShip ship : ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips()) {
            wrap(ship, dimension).ifPresent(result::add);
        }
        return result;
    }

    private Optional<VehicleBody> wrap(LoadedServerShip ship, String dimension) {
        if (!ship.getChunkClaimDimension().equals(dimension)) {
            return Optional.empty();
        }
        VehicleControlAttachment control = ship.getAttachment(VehicleControlAttachment.class);
        return control == null ? Optional.empty() : Optional.of(new VsVehicleBody(ship, control, this));
    }

    /**
     * Diagnostic hull: a 3x5 iron deck with a redstone block marking the front (-Z), assembled by VS's own
     * {@link ShipAssembler}. The returned creation is READY only once VS has loaded the ship and our controller is
     * attached.
     */
    @Override
    public BodyCreation createTestBody(ServerWorld world, BlockPos origin) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.add(origin.add(x, 0, z));
            }
        }
        BlockPos marker = origin.add(0, 1, -2);
        blocks.add(marker);
        for (BlockPos pos : blocks) {
            if (!world.isInBuildLimit(pos) || !world.getBlockState(pos).isReplaceable()) {
                return BodyCreation.failed("space at " + pos.toShortString() + " is occupied");
            }
        }
        BlockState hull = Blocks.IRON_BLOCK.getDefaultState();
        for (BlockPos pos : blocks) {
            world.setBlockState(pos, pos.equals(marker) ? Blocks.REDSTONE_BLOCK.getDefaultState() : hull);
        }

        ServerShip ship;
        try {
            ship = ShipAssembler.assembleToShip(world, blocks, 1.0);
        } catch (RuntimeException e) {
            // Assembly failed: put the world back the way it was.
            for (BlockPos pos : blocks) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
            MonolithArsenal.LOGGER.error("Valkyrien Skies could not assemble the test body at {}", origin.toShortString(), e);
            return BodyCreation.failed("assembly failed: " + e);
        }

        BodyCreation creation = new BodyCreation(ship.getId(), world.getServer().getTicks() + CREATION_TIMEOUT_TICKS);
        this.creations.put(ship.getId(), new Pending(creation, world.getRegistryKey(), VehicleControlAttachment.KIND_TEST_BODY));
        MonolithArsenal.LOGGER.info("Assembled Valkyrien Skies test body {} at {}; waiting for it to load", ship.getId(), origin.toShortString());
        LoadedServerShip loaded = ValkyrienSkies.api().getServerShipWorld(world.getServer()).getLoadedShips().getById(ship.getId());
        if (loaded != null) {
            this.attach(loaded, VehicleControlAttachment.KIND_TEST_BODY);
            this.finish(ship.getId(), BodyCreation.State.READY, "");
        }
        return creation;
    }

    @Override
    public Optional<BodyCreation> creation(long id) {
        Pending pending = this.creations.get(id);
        return pending == null ? Optional.empty() : Optional.of(pending.creation());
    }

    /**
     * VS ShipLoadEvent. Attaches the controller to ships we assembled; ships loaded from a save already carry their
     * deserialized attachment, and an existing attachment is never replaced, so repeated load events cannot create
     * a second controller.
     */
    void onShipLoad(LoadedServerShip ship) {
        Pending pending = this.creations.get(ship.getId());
        if (pending != null && pending.creation().state() == BodyCreation.State.PENDING) {
            this.attach(ship, pending.kind());
        }
    }

    private void attach(LoadedServerShip ship, String kind) {
        if (ship.getAttachment(VehicleControlAttachment.class) == null) {
            ship.setAttachment(VehicleControlAttachment.class, new VehicleControlAttachment(kind));
        }
    }

    /**
     * True if VS is simulating the ship now. This VS port keeps ships without nearby players (and without
     * keep-active) in memory but holds them still, ignoring forces, while their shipyard chunks are not loaded.
     * That state is not exposed through the VS API, so it is derived from those chunks, with a short streak to
     * cover VS resuming a tick later.
     */
    boolean isSimulated(LoadedServerShip ship, VehicleControlAttachment control) {
        return this.tickingStreak.getOrDefault(ship.getId(), 0) >= RESUME_STREAK_TICKS && control.isStepping();
    }

    /** Server thread: queue a command, holding it while the ship is not simulated. */
    boolean submit(LoadedServerShip ship, VehicleControlAttachment control, PhysicsCommand command) {
        java.util.ArrayDeque<PhysicsCommand> waiting = this.held.get(ship.getId());
        if (waiting == null && this.isSimulated(ship, control)) {
            return control.enqueue(command);
        }
        if (waiting == null) {
            waiting = new java.util.ArrayDeque<>();
            this.held.put(ship.getId(), waiting);
        }
        if (waiting.size() >= VehicleControlAttachment.MAX_QUEUED) {
            return false;
        }
        waiting.add(command);
        return true;
    }

    int heldCount(long id) {
        java.util.ArrayDeque<PhysicsCommand> waiting = this.held.get(id);
        return waiting == null ? 0 : waiting.size();
    }

    int tickingStreak(long id) {
        return this.tickingStreak.getOrDefault(id, 0);
    }

    String voxelChunksOf(LoadedServerShip ship) {
        return this.lastServer == null ? "?" : this.voxelChunks(this.lastServer, ship);
    }

    /** Diagnostics: "loaded/entity-ticking/total" shipyard (voxel) chunks of the ship. */
    String voxelChunks(MinecraftServer server, LoadedServerShip ship) {
        ServerWorld world = worldOf(server, ship);
        if (world == null) {
            return "?";
        }
        int[] counts = new int[3];
        ship.getActiveChunksSet().forEach((x, z) -> {
            counts[2]++;
            if (world.getChunkManager().isChunkLoaded(x, z)) {
                counts[0]++;
            }
            if (world.shouldTickEntityAt(new BlockPos(x << 4, 0, z << 4))) {
                counts[1]++;
            }
        });
        return counts[0] + "/" + counts[1] + "/" + counts[2];
    }

    @Override
    public void tick(MinecraftServer server) {
        this.lastServer = server;
        this.updateActivity(server);
        if (this.creations.isEmpty()) {
            return;
        }
        var loadedShips = ValkyrienSkies.api().getServerShipWorld(server).getLoadedShips();
        for (Map.Entry<Long, Pending> entry : List.copyOf(this.creations.entrySet())) {
            long id = entry.getKey();
            BodyCreation creation = entry.getValue().creation();
            if (creation.state() != BodyCreation.State.PENDING) {
                // Keep finished entries around briefly for /status, then forget them.
                if (server.getTicks() > creation.deadlineTick() + 1200) {
                    this.creations.remove(id);
                }
                continue;
            }
            LoadedServerShip ship = loadedShips.getById(id);
            if (ship != null && ship.getAttachment(VehicleControlAttachment.class) == null) {
                this.attach(ship, entry.getValue().kind());
            }
            if (ship != null) {
                this.finish(id, BodyCreation.State.READY, "");
            } else if (server.getTicks() >= creation.deadlineTick()) {
                this.cleanUpFailedCreation(server, id, entry.getValue());
            }
        }
    }

    /** Tracks which of our ships sit in entity-ticking chunks and releases held commands once they do. */
    private void updateActivity(MinecraftServer server) {
        Set<Long> seen = new java.util.HashSet<>();
        for (LoadedServerShip ship : ValkyrienSkies.api().getServerShipWorld(server).getLoadedShips()) {
            VehicleControlAttachment control = ship.getAttachment(VehicleControlAttachment.class);
            if (control == null) {
                continue;
            }
            seen.add(ship.getId());
            ServerWorld world = worldOf(server, ship);
            boolean ready = world != null && allVoxelChunksLoaded(world, ship);
            this.tickingStreak.put(ship.getId(), ready ? this.tickingStreak.getOrDefault(ship.getId(), 0) + 1 : 0);
            java.util.ArrayDeque<PhysicsCommand> waiting = this.held.get(ship.getId());
            if (waiting != null && this.isSimulated(ship, control)) {
                PhysicsCommand command;
                while ((command = waiting.peek()) != null && control.enqueue(command)) {
                    waiting.poll();
                }
                if (waiting.isEmpty()) {
                    this.held.remove(ship.getId());
                }
            }
        }
        this.tickingStreak.keySet().retainAll(seen);
        this.held.keySet().removeIf(id -> !seen.contains(id) && !this.creations.containsKey(id));
    }

    /** VS only simulates a ship whose shipyard (voxel) chunks are loaded; otherwise it holds it in place. */
    private static boolean allVoxelChunksLoaded(ServerWorld world, LoadedServerShip ship) {
        boolean[] all = {ship.getActiveChunksSet().getSize() > 0};
        ship.getActiveChunksSet().forEach((x, z) -> {
            if (!world.getChunkManager().isChunkLoaded(x, z)) {
                all[0] = false;
            }
        });
        return all[0];
    }

    private static ServerWorld worldOf(MinecraftServer server, LoadedServerShip ship) {
        String dimension = ship.getChunkClaimDimension();
        for (ServerWorld world : server.getWorlds()) {
            if (ValkyrienSkies.api().getDimensionId(world).equals(dimension)) {
                return world;
            }
        }
        return null;
    }

    private void cleanUpFailedCreation(MinecraftServer server, long id, Pending pending) {
        ServerWorld world = server.getWorld(pending.world());
        ServerShip ship = ValkyrienSkies.api().getServerShipWorld(server).getAllShips().getById(id);
        if (world != null && ship != null) {
            ShipAssembler.INSTANCE.deleteShip(world, ship, true, false);
        }
        MonolithArsenal.LOGGER.warn("Valkyrien Skies test body {} did not load within {} ticks; removed it", id, CREATION_TIMEOUT_TICKS);
        this.finish(id, BodyCreation.State.FAILED, "not loaded within " + CREATION_TIMEOUT_TICKS + " ticks");
    }

    private void finish(long id, BodyCreation.State state, String reason) {
        Pending pending = this.creations.get(id);
        if (pending != null) {
            pending.creation().complete(state, reason);
            if (state == BodyCreation.State.READY) {
                MonolithArsenal.LOGGER.info("Valkyrien Skies test body {} is ready", id);
            }
        }
    }

    @Override
    public boolean remove(ServerWorld world, long id) {
        Optional<VehicleBody> body = this.find(world, id);
        if (body.isEmpty()) {
            return false;
        }
        VsVehicleBody vsBody = (VsVehicleBody) body.get();
        vsBody.control().clearAll();
        this.held.remove(id);
        this.tickingStreak.remove(id);
        this.creations.remove(id);
        // deleteBlocks = true removes the hull from the shipyard; dropBlocks = false spawns no items.
        ShipAssembler.INSTANCE.deleteShip(world, vsBody.ship(), true, false);
        MonolithArsenal.LOGGER.info("Removed Valkyrien Skies vehicle body {}", id);
        return true;
    }

    @Override
    public void clear() {
        this.creations.clear();
        this.held.clear();
        this.tickingStreak.clear();
        this.lastServer = null;
    }
}
