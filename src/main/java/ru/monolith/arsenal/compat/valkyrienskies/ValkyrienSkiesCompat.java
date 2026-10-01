package ru.monolith.arsenal.compat.valkyrienskies;

import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import ru.monolith.arsenal.MonolithArsenal;

/**
 * Optional detection of an externally installed Valkyrien Skies.
 * There is no official Valkyrien Skies build for Minecraft 1.21.11, so it is no longer a hard
 * dependency; ship physics is provided by the bundled Monolith Skies module instead.
 * The API is looked up reflectively so that no Valkyrien Skies classes are needed at compile time.
 */
public final class ValkyrienSkiesCompat {
    public static final String MOD_ID = "valkyrienskies";
    private static final String API_CLASS = "org.valkyrienskies.mod.api.VsApi";

    private ValkyrienSkiesCompat() {
    }

    public static void initialize() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(MOD_ID);
        if (container.isEmpty()) {
            MonolithArsenal.LOGGER.info("Valkyrien Skies is not installed; ship physics is provided by Monolith Skies");
            return;
        }

        String version = container.get().getMetadata().getVersion().getFriendlyString();
        MonolithArsenal.LOGGER.info("Valkyrien Skies detected");
        MonolithArsenal.LOGGER.info("Valkyrien Skies version: {}", version);
        try {
            Class<?> api = Class.forName(API_CLASS, false, ValkyrienSkiesCompat.class.getClassLoader());
            MonolithArsenal.LOGGER.info("Valkyrien Skies API visible: {}", api.getName());
            MonolithArsenal.LOGGER.info("Valkyrien Skies compatibility layer initialized");
        } catch (ClassNotFoundException e) {
            MonolithArsenal.LOGGER.warn("Valkyrien Skies {} does not expose {}; compatibility layer is inactive", version, API_CLASS);
        }
    }
}
