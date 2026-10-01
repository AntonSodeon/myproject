package ru.monolith.arsenal;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.monolith.arsenal.compat.skies.MonolithSkiesCompat;
import ru.monolith.arsenal.compat.valkyrienskies.ValkyrienSkiesCompat;
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
        MonolithSkiesCompat.initialize();
        ValkyrienSkiesCompat.initialize();
        LOGGER.info("Monolith Arsenal initialized for Minecraft 1.21.11");
    }
}
