package ru.monolith.skies.ship;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricTrackedDataRegistry;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.util.Identifier;
import ru.monolith.skies.MonolithSkies;

public final class ShipTrackedData {
    public static final TrackedDataHandler<ShipStructure> STRUCTURE = TrackedDataHandler.create(ShipStructure.PACKET_CODEC);

    private ShipTrackedData() {
    }

    public static void initialize() {
        FabricTrackedDataRegistry.register(Identifier.of(MonolithSkies.MOD_ID, "ship_structure"), STRUCTURE);
    }
}
