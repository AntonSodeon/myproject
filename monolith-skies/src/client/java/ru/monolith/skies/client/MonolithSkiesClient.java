package ru.monolith.skies.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ru.monolith.skies.MonolithSkies;

@Environment(EnvType.CLIENT)
public final class MonolithSkiesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MonolithSkies.SHIP, ShipEntityRenderer::new);
        MonolithSkies.LOGGER.info("Monolith Skies client initialized");
    }
}
