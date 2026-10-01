package ru.monolith.arsenal.physics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Forces and torques that keep acting for a number of game ticks (diagnostic commands, later engines).
 * Each tick they are re-queued on the body; the backend then hands them to the physics thread.
 */
public final class ScheduledForces {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private ScheduledForces() {
    }

    public static void add(ServerWorld world, long bodyId, Vec3d force, Vec3d torque, int ticks) {
        ENTRIES.add(new Entry(world.getRegistryKey(), bodyId, force, torque, ticks));
    }

    static void tick(MinecraftServer server) {
        ENTRIES.removeIf(entry -> {
            ServerWorld world = server.getWorld(entry.world);
            var body = world == null ? null : VehiclePhysics.find(world, entry.bodyId).orElse(null);
            if (body == null) {
                return true;
            }
            if (entry.force.lengthSquared() > 0) {
                body.applyForce(entry.force);
            }
            if (entry.torque.lengthSquared() > 0) {
                body.applyTorque(entry.torque);
            }
            return --entry.ticksLeft <= 0;
        });
    }

    static void clear() {
        ENTRIES.clear();
    }

    public static void cancel(long bodyId) {
        ENTRIES.removeIf(entry -> entry.bodyId == bodyId);
    }

    private static final class Entry {
        final RegistryKey<World> world;
        final long bodyId;
        final Vec3d force;
        final Vec3d torque;
        int ticksLeft;

        Entry(RegistryKey<World> world, long bodyId, Vec3d force, Vec3d torque, int ticks) {
            this.world = world;
            this.bodyId = bodyId;
            this.force = force;
            this.torque = torque;
            this.ticksLeft = ticks;
        }
    }
}
