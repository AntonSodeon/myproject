package ru.monolith.skies.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One block of a ship in ship-local coordinates.
 * {@code blockEntity} keeps the full block entity data (inventories, signs...) so it can be restored on disassembly;
 * it is never sent to clients.
 */
public record ShipBlock(BlockPos pos, BlockState state, Optional<NbtCompound> blockEntity) {
    public static final Codec<ShipBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ShipBlock::pos),
            BlockState.CODEC.fieldOf("state").forGetter(ShipBlock::state),
            NbtCompound.CODEC.optionalFieldOf("block_entity").forGetter(ShipBlock::blockEntity)
    ).apply(instance, ShipBlock::new));
}
