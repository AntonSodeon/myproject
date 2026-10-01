package ru.monolith.arsenal.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.monolith.arsenal.MonolithArsenal;

public final class ModItemGroups {
    public static final RegistryKey<ItemGroup> MONOLITH_ARSENAL = RegistryKey.of(
            RegistryKeys.ITEM_GROUP, Identifier.of(MonolithArsenal.MOD_ID, "monolith_arsenal")
    );

    private ModItemGroups() {
    }

    public static void initialize() {
        Registry.register(
                Registries.ITEM_GROUP,
                MONOLITH_ARSENAL,
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ItemRegistry.TEST_WEAPON))
                        .displayName(Text.translatable("itemGroup.monolith_arsenal"))
                        .entries((context, entries) -> {
                            entries.add(ItemRegistry.TEST_WEAPON);
                            entries.add(ItemRegistry.DEBUG_TOOL);
                        })
                        .build()
        );
        MonolithArsenal.LOGGER.info("Registered Monolith Arsenal creative tab");
    }
}
