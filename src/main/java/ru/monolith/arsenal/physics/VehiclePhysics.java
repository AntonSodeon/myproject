package ru.monolith.arsenal.physics;

import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.compat.valkyrienskies.ValkyrienSkiesCompat;

/**
 * Entry point for vehicle physics. Game code talks to {@link VehicleBody}/{@link VehiclePhysicsBackend} only;
 * the Valkyrien Skies classes stay inside {@code compat.valkyrienskies}.
 */
public final class VehiclePhysics {
    private static VehiclePhysicsBackend backend = new Unavailable("not initialized");

    private VehiclePhysics() {
    }

    public static void initialize() {
        if (FabricLoader.getInstance().isModLoaded(ValkyrienSkiesCompat.MOD_ID)) {
            backend = ValkyrienSkiesCompat.createBackend();
        } else {
            backend = new Unavailable("Valkyrien Skies is not installed");
        }
        MonolithArsenal.LOGGER.info("Vehicle physics backend: {}", backend.describe());

        ServerTickEvents.END_SERVER_TICK.register(server -> backend.tick(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> backend.clear());
    }

    public static VehiclePhysicsBackend backend() {
        return backend;
    }

    public static Optional<VehicleBody> find(ServerWorld world, long id) {
        return backend.find(world, id);
    }

    /** Reported when no physics engine is present; never pretends to simulate anything. */
    private record Unavailable(String reason) implements VehiclePhysicsBackend {
        @Override
        public String describe() {
            return "unavailable (" + this.reason + ")";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public Optional<VehicleBody> find(ServerWorld world, long id) {
            return Optional.empty();
        }

        @Override
        public List<VehicleBody> bodies(ServerWorld world) {
            return List.of();
        }

        @Override
        public BodyCreation createTestBody(ServerWorld world, BlockPos origin) {
            return BodyCreation.failed(this.describe());
        }

        @Override
        public Optional<BodyCreation> creation(long id) {
            return Optional.empty();
        }

        @Override
        public boolean remove(ServerWorld world, long id) {
            return false;
        }

        @Override
        public void tick(MinecraftServer server) {
        }

        @Override
        public void clear() {
        }
    }
}
