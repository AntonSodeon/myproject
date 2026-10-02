package ru.monolith.arsenal.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.BooleanSupplier;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.monolith.arsenal.compat.valkyrienskies.ShipyardChunks;

/**
 * Fix for the Valkyrien Skies port: its {@code MixinChunkMapScheduleUnload} evicts shipyard chunks whose tickets
 * have expired straight from the chunk map, without the save that vanilla unloading does. Right after a ship is
 * assembled, its shipyard chunk briefly has no VS ticket, so the chunk with the ship's blocks could be thrown
 * away and later reloaded empty from disk: after a reload the ship has no collision and falls through terrain.
 * <p>
 * This runs before the VS eviction (lower mixin priority) and saves such chunks if they have unsaved changes.
 * It changes nothing else: chunks are still evicted by VS as before.
 */
@Mixin(value = ServerChunkLoadingManager.class, priority = 500)
public abstract class ShipyardChunkSaveMixin {
    @Shadow
    @Final
    LongSet unloadedChunks;

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> currentChunkHolders;

    @Shadow
    protected abstract boolean save(Chunk chunk);

    @Inject(method = "unloadChunks", at = @At("HEAD"))
    private void monolithArsenal$saveShipyardChunksBeforeEviction(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (this.unloadedChunks.isEmpty()) {
            return;
        }
        for (LongIterator it = this.unloadedChunks.iterator(); it.hasNext(); ) {
            long pos = it.nextLong();
            this.monolithArsenal$saveIfShipyard(pos, this.currentChunkHolders.get(pos));
        }
    }

    @Inject(method = "tryUnloadChunk", at = @At("HEAD"))
    private void monolithArsenal$saveShipyardChunkOnUnload(long pos, ChunkHolder holder, CallbackInfo ci) {
        this.monolithArsenal$saveIfShipyard(pos, holder);
    }

    private void monolithArsenal$saveIfShipyard(long pos, ChunkHolder holder) {
        if (holder == null || !ShipyardChunks.isShipyard(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos))) {
            return;
        }
        if (holder.getLatest() instanceof WorldChunk chunk && chunk.needsSaving()) {
            this.save(chunk);
        }
    }
}
