package ru.monolith.arsenal.compat.skies;

import net.fabricmc.loader.api.FabricLoader;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.skies.MonolithSkies;
import ru.monolith.skies.api.MonolithSkiesApi;

/** Ship physics for Monolith Arsenal vehicles, provided by the bundled Monolith Skies module. */
public final class MonolithSkiesCompat {
    private MonolithSkiesCompat() {
    }

    public static void initialize() {
        String version = FabricLoader.getInstance().getModContainer(MonolithSkies.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        MonolithArsenal.LOGGER.info("Monolith Skies detected, version {}", version);
        MonolithArsenal.LOGGER.info("Monolith Skies API visible: {} (API v{})", MonolithSkiesApi.class.getName(), MonolithSkiesApi.API_VERSION);
        MonolithArsenal.LOGGER.info("Monolith Skies compatibility layer initialized");
    }
}
