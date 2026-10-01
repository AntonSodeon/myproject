package ru.monolith.arsenal.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.monolith.arsenal.MonolithArsenal;

public final class ItemRegistry {
    public static final Item TEST_WEAPON = register("test_weapon");
    public static final Item DEBUG_TOOL = register("debug_tool");

    private ItemRegistry() {
    }

    private static Item register(String path) {
        Identifier id = Identifier.of(MonolithArsenal.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, new Item(new Item.Settings().registryKey(key)));
    }

    public static void initialize() {
        MonolithArsenal.LOGGER.info("Registered Monolith Arsenal items");
    }
}
