package ru.monolith.arsenal.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.registry.EntityRegistry;

@Environment(EnvType.CLIENT)
public final class MonolithArsenalClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(EntityRegistry.ANIMATED_TEST, AnimatedTestRenderer::new);
        TestWeaponPose.register();
        MonolithArsenal.LOGGER.info("Monolith Arsenal client initialized");
    }
}
