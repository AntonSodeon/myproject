package ru.monolith.arsenal;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.monolith.arsenal.physics.PhysicsCommands;
import ru.monolith.arsenal.physics.VehiclePhysics;
import ru.monolith.arsenal.registry.BlockRegistry;
import ru.monolith.arsenal.registry.EntityRegistry;
import ru.monolith.arsenal.registry.ItemRegistry;
import ru.monolith.arsenal.registry.ModItemGroups;

public final class MonolithArsenal implements ModInitializer {
    public static final String MOD_ID = "monolith_arsenal";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ItemRegistry.initialize();
        ModItemGroups.initialize();
        BlockRegistry.initialize();
        EntityRegistry.initialize();
        VehiclePhysics.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PhysicsCommands.register(dispatcher));
        LOGGER.info("Monolith Arsenal initialized for Minecraft 1.21.11");
    }
}
