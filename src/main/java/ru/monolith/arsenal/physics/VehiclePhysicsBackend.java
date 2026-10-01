package ru.monolith.arsenal.physics;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** A physics engine that can host vehicle bodies. Server thread only. */
public interface VehiclePhysicsBackend {
    /** Human-readable backend name and version for logs and diagnostics. */
    String describe();

    boolean isAvailable();

    /** Finds a ready body created by Monolith Arsenal in {@code world}. */
    Optional<VehicleBody> find(ServerWorld world, long id);

    /** Ready bodies created by Monolith Arsenal in {@code world}. */
    List<VehicleBody> bodies(ServerWorld world);

    /** Builds the diagnostic test hull from blocks around {@code origin} and turns it into a physics body. */
    BodyCreation createTestBody(ServerWorld world, BlockPos origin);

    /** Creation status of a body requested earlier, if it is still being tracked. */
    Optional<BodyCreation> creation(long id);

    /** Removes the body together with its blocks. Returns false if no such body is loaded. */
    boolean remove(ServerWorld world, long id);

    /** Called at the end of every server tick: advances pending creations. */
    void tick(MinecraftServer server);

    /** Called when the server stops: drops all per-server state. */
    void clear();
}
