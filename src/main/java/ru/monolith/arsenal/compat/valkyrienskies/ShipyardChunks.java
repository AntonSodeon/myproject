package ru.monolith.arsenal.compat.valkyrienskies;

import net.fabricmc.loader.api.FabricLoader;
import org.valkyrienskies.mod.common.VS2ChunkAllocator;

/** Shipyard chunk test, safe to call when Valkyrien Skies is absent. */
public final class ShipyardChunks {
    private static final boolean VS_LOADED = FabricLoader.getInstance().isModLoaded(ValkyrienSkiesCompat.MOD_ID);

    private ShipyardChunks() {
    }

    public static boolean isShipyard(int chunkX, int chunkZ) {
        return VS_LOADED && Vs.isShipyard(chunkX, chunkZ);
    }

    /** Separate class so VS classes are only resolved when VS is installed. */
    private static final class Vs {
        static boolean isShipyard(int chunkX, int chunkZ) {
            return VS2ChunkAllocator.INSTANCE.isChunkInShipyardCompanion(chunkX, chunkZ);
        }
    }
}
