package ru.monolith.skies;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.monolith.skies.command.SkiesCommands;
import ru.monolith.skies.item.ShipAssemblerItem;
import ru.monolith.skies.ship.ShipEntity;
import ru.monolith.skies.ship.ShipTrackedData;

/**
 * Monolith Skies: block ships with rigid-body physics for Minecraft 1.21.11 (Fabric).
 * An independent implementation of the "ships made of blocks" idea popularised by Valkyrien Skies.
 */
public final class MonolithSkies implements ModInitializer {
    public static final String MOD_ID = "monolith_skies";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier SHIP_ID = Identifier.of(MOD_ID, "ship");
    public static final RegistryKey<EntityType<?>> SHIP_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, SHIP_ID);
    public static final EntityType<ShipEntity> SHIP;

    public static final RegistryKey<Item> SHIP_ASSEMBLER_KEY = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "ship_assembler"));
    public static final Item SHIP_ASSEMBLER = Registry.register(Registries.ITEM, SHIP_ASSEMBLER_KEY,
            new ShipAssemblerItem(new Item.Settings().registryKey(SHIP_ASSEMBLER_KEY).maxCount(1)));

    static {
        // The tracked data handler must be known before ShipEntity registers its DataTracker fields.
        ShipTrackedData.initialize();
        SHIP = Registry.register(Registries.ENTITY_TYPE, SHIP_ID,
                EntityType.Builder.<ShipEntity>create(ShipEntity::new, SpawnGroup.MISC)
                        .dimensions(1.0F, 1.0F)
                        .maxTrackingRange(16)
                        .trackingTickInterval(1)
                        .alwaysUpdateVelocity(true)
                        .disableSummon()
                        .build(SHIP_KEY));
    }

    @Override
    public void onInitialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(SHIP_ASSEMBLER));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> SkiesCommands.register(dispatcher));
        LOGGER.info("Monolith Skies initialized (ship physics, API v{})", ru.monolith.skies.api.MonolithSkiesApi.API_VERSION);
    }
}
