package ru.monolith.skies.ship;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import ru.monolith.skies.MonolithSkies;

/** Converts world blocks into ships and back. */
public final class ShipAssembler {
    public static final int DEFAULT_MAX_BLOCKS = 4096;

    /** Removing ship blocks must not drop items, scatter inventories or break attached blocks mid-way. */
    private static final int REMOVE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS | Block.MOVED
            | Block.SKIP_BLOCK_ENTITY_REPLACED_CALLBACK;
    private static final int PLACE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.MOVED;

    private ShipAssembler() {
    }

    public record Result(boolean success, String messageKey, @Nullable ShipEntity ship, int blocks) {
        public static Result failure(String messageKey) {
            return new Result(false, messageKey, null, 0);
        }
    }

    public static boolean canInclude(ServerWorld world, BlockPos pos, BlockState state) {
        return !state.isAir()
                && !(state.getBlock() instanceof FluidBlock)
                && state.getHardness(world, pos) >= 0.0F
                && !state.isOf(Blocks.MOVING_PISTON);
    }

    public static Result assemble(ServerWorld world, BlockPos start, int maxBlocks) {
        BlockState startState = world.getBlockState(start);
        if (!canInclude(world, start, startState)) {
            return Result.failure("message.monolith_skies.assemble.invalid_block");
        }

        // Flood fill across faces; a structure resting on terrain would pull in the whole ground and hit the limit.
        List<BlockPos> found = new ArrayList<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.toImmutable());
        visited.add(start.asLong());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            found.add(pos);
            if (found.size() > maxBlocks) {
                return Result.failure("message.monolith_skies.assemble.too_big");
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.offset(direction);
                if (visited.add(next.asLong()) && world.isInBuildLimit(next)
                        && canInclude(world, next, world.getBlockState(next))) {
                    queue.add(next);
                }
            }
        }

        List<ShipBlock> blocks = new ArrayList<>(found.size());
        for (BlockPos pos : found) {
            BlockState state = world.getBlockState(pos);
            BlockEntity blockEntity = world.getBlockEntity(pos);
            Optional<NbtCompound> data = blockEntity == null
                    ? Optional.empty()
                    : Optional.of(blockEntity.createNbtWithIdentifyingData(world.getRegistryManager()));
            blocks.add(new ShipBlock(pos.subtract(start), state, data));
        }
        ShipStructure structure = ShipStructure.of(blocks);

        for (BlockPos pos : found) {
            BlockState state = world.getBlockState(pos);
            if (world.getBlockEntity(pos) != null) {
                world.removeBlockEntity(pos);
            }
            BlockState replacement = state.getFluidState().isIn(FluidTags.WATER) && state.getFluidState().isStill()
                    ? Blocks.WATER.getDefaultState()
                    : Blocks.AIR.getDefaultState();
            world.setBlockState(pos, replacement, REMOVE_FLAGS);
        }
        for (BlockPos pos : found) {
            world.updateNeighbors(pos, world.getBlockState(pos).getBlock());
        }

        ShipEntity ship = MonolithSkies.SHIP.create(world, net.minecraft.entity.SpawnReason.COMMAND);
        if (ship == null) {
            return Result.failure("message.monolith_skies.assemble.failed");
        }
        ship.setStructure(structure);
        Vec3d origin = Vec3d.of(start).add(structure.centerOfMass());
        ship.refreshPositionAndAngles(origin.x, origin.y, origin.z, 0.0F, 0.0F);
        ship.setShipYaw(0.0);
        world.spawnEntity(ship);
        MonolithSkies.LOGGER.info("Assembled ship {} from {} blocks at {} (mass {})",
                ship.getUuidAsString(), structure.size(), start.toShortString(), String.format("%.1f", structure.mass()));
        return new Result(true, "message.monolith_skies.assemble.success", ship, structure.size());
    }

    public static Result disassemble(ServerWorld world, ShipEntity ship) {
        ShipStructure structure = ship.getStructure();
        int quarterTurns = Math.floorMod(Math.round(ship.getShipYaw() / 90.0), 4);
        double snappedYaw = quarterTurns * 90.0;
        BlockRotation rotation = ShipEntity.rotationFor(quarterTurns);
        Vec3d position = ship.getEntityPos();
        Vec3d com = structure.centerOfMass();

        List<BlockPos> targets = new ArrayList<>(structure.size());
        Set<BlockPos> unique = new HashSet<>();
        for (ShipBlock block : structure.blocks()) {
            Vec3d center = position.add(ShipEntity.rotateY(Vec3d.ofCenter(block.pos()).subtract(com), snappedYaw));
            BlockPos target = BlockPos.ofFloored(center.x, center.y, center.z);
            if (!world.isInBuildLimit(target) || !unique.add(target) || !world.getBlockState(target).isReplaceable()) {
                return Result.failure("message.monolith_skies.disassemble.obstructed");
            }
            targets.add(target);
        }

        for (int i = 0; i < targets.size(); i++) {
            ShipBlock block = structure.blocks().get(i);
            BlockPos target = targets.get(i);
            BlockState state = block.state().rotate(rotation);
            world.setBlockState(target, state, PLACE_FLAGS);
            if (block.blockEntity().isPresent()) {
                BlockEntity restored = BlockEntity.createFromNbt(target, state, block.blockEntity().get(), world.getRegistryManager());
                if (restored != null) {
                    world.addBlockEntity(restored);
                }
            }
        }
        for (int i = 0; i < targets.size(); i++) {
            world.updateNeighbors(targets.get(i), world.getBlockState(targets.get(i)).getBlock());
        }

        ship.discard();
        MonolithSkies.LOGGER.info("Disassembled ship {} ({} blocks, rotation {})",
                ship.getUuidAsString(), targets.size(), rotation);
        return new Result(true, "message.monolith_skies.disassemble.success", null, targets.size());
    }

    /** Finds the closest ship block hit by a ray, as the distance along the ray. */
    public static Optional<ShipHit> raycast(Iterable<ShipEntity> ships, Vec3d start, Vec3d end) {
        ShipHit best = null;
        Vec3d direction = end.subtract(start);
        double length = direction.length();
        if (length < 1.0E-6) {
            return Optional.empty();
        }
        Vec3d step = direction.multiply(0.05 / length);
        for (ShipEntity ship : ships) {
            if (ship.isRemoved() || ship.getBoundingBox().raycast(start, end).isEmpty() && !ship.getBoundingBox().contains(start)) {
                continue;
            }
            Vec3d point = start;
            for (double travelled = 0.0; travelled <= length; travelled += 0.05) {
                Vec3d local = ship.worldToShip(point);
                ShipBlock block = ship.getStructure().get(MathHelper.floor(local.x), MathHelper.floor(local.y), MathHelper.floor(local.z));
                if (block != null) {
                    if (best == null || travelled < best.distance()) {
                        best = new ShipHit(ship, travelled);
                    }
                    break;
                }
                point = point.add(step);
            }
        }
        return Optional.ofNullable(best);
    }

    public record ShipHit(ShipEntity ship, double distance) {
    }
}
