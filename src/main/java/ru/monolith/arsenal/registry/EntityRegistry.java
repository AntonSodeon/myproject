package ru.monolith.arsenal.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.monolith.arsenal.MonolithArsenal;
import ru.monolith.arsenal.weapon.AnimatedTestEntity;

public final class EntityRegistry {
    public static final Identifier ANIMATED_TEST_ID = Identifier.of(MonolithArsenal.MOD_ID, "animated_test");
    public static final RegistryKey<EntityType<?>> ANIMATED_TEST_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, ANIMATED_TEST_ID);
    public static final EntityType<AnimatedTestEntity> ANIMATED_TEST = Registry.register(
            Registries.ENTITY_TYPE,
            ANIMATED_TEST_ID,
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, AnimatedTestEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8F, 1.0F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(3)
                    .build(ANIMATED_TEST_KEY)
    );

    private EntityRegistry() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(ANIMATED_TEST, AnimatedTestEntity.createMobAttributes());
        MonolithArsenal.LOGGER.info("Registered Monolith Arsenal entity {} with default mob attributes", ANIMATED_TEST_ID);
    }
}
