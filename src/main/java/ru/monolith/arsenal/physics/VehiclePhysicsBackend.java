package ru.monolith.arsenal.physics;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** A physics engine that can host vehicle bodies. Server thread only. */
public interface VehiclePhysicsBackend {
    /** Human-readable backend name and version for logs and diagnostics. */
    String describe();

    boolean isAvailable();

    /** Finds a loaded body created by Monolith Arsenal in {@code world}. */
    Optional<VehicleBody> find(ServerWorld world, long id);

    /** Loaded bodies created by Monolith Arsenal in {@code world}. */
    List<VehicleBody> bodies(ServerWorld world);

    /**
     * Builds the diagnostic test hull from blocks around {@code origin} and turns it into a physics body.
     * Returns the new body's id; the body may only become visible to {@link #find} on a later tick, once the
     * backend has loaded it. Empty if the space is occupied.
     */
    OptionalLong createTestBody(ServerWorld world, BlockPos origin);

    /** Removes the body together with its blocks. Returns false if no such body is loaded. */
    boolean remove(ServerWorld world, long id);

    /** Called once per server tick after game logic: hands queued forces to the physics thread. */
    void publishQueuedForces();

    /** Called when the server stops: drops all per-world state. */
    void clear();
}
