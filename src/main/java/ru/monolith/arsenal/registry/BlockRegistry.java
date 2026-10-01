package ru.monolith.arsenal.registry;

import ru.monolith.arsenal.MonolithArsenal;

public final class BlockRegistry {
    private BlockRegistry() {
    }

    public static void initialize() {
        MonolithArsenal.LOGGER.info("BlockRegistry initialized (no blocks registered yet)");
    }
}
