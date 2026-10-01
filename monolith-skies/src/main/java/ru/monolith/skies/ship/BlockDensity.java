package ru.monolith.skies.ship;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/**
 * Relative densities (water = 1.0) and displaced volume per block.
 * Wooden hulls float, stone and metal sink, which is enough for ships to behave plausibly in water.
 */
public final class BlockDensity {
    public static final double WATER = 1.0;

    private static final double WOOD = 0.6;
    private static final double LIGHT = 0.25;
    private static final double GLASS = 2.5;
    private static final double METAL = 7.0;
    private static final double STONE = 2.4;

    private BlockDensity() {
    }

    public static double density(BlockState state) {
        if (state.isIn(BlockTags.WOOL) || state.isIn(BlockTags.WOOL_CARPETS) || state.isIn(BlockTags.LEAVES)) {
            return LIGHT;
        }
        if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isIn(BlockTags.WOODEN_STAIRS) || state.isIn(BlockTags.WOODEN_FENCES)
                || state.isIn(BlockTags.WOODEN_DOORS) || state.isIn(BlockTags.WOODEN_TRAPDOORS)) {
            return WOOD;
        }
        BlockSoundGroup sound = state.getSoundGroup();
        if (sound == BlockSoundGroup.WOOD || sound == BlockSoundGroup.NETHER_WOOD || sound == BlockSoundGroup.BAMBOO_WOOD
                || sound == BlockSoundGroup.CHERRY_WOOD || sound == BlockSoundGroup.LADDER || sound == BlockSoundGroup.SCAFFOLDING) {
            return WOOD;
        }
        if (sound == BlockSoundGroup.WOOL || sound == BlockSoundGroup.MOSS_CARPET) {
            return LIGHT;
        }
        if (sound == BlockSoundGroup.GLASS) {
            return GLASS;
        }
        if (sound == BlockSoundGroup.METAL || sound == BlockSoundGroup.ANVIL || sound == BlockSoundGroup.COPPER
                || sound == BlockSoundGroup.NETHERITE || sound == BlockSoundGroup.CHAIN || sound == BlockSoundGroup.LANTERN) {
            return METAL;
        }
        return STONE;
    }

    /** Volume in cubic blocks: full blocks displace 1, partial shapes (slabs, fences, torches) displace less. */
    public static double volume(BlockState state) {
        var shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        if (shape.isEmpty()) {
            return 0.1;
        }
        var box = shape.getBoundingBox();
        return Math.max(0.1, box.getLengthX() * box.getLengthY() * box.getLengthZ());
    }

    public static double mass(BlockState state) {
        return density(state) * volume(state);
    }
}
