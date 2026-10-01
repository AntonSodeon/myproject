package ru.monolith.skies.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable set of blocks forming a ship, in ship-local block coordinates.
 * The ship rotates around {@link #centerOfMass()}, which is also where its entity is positioned.
 */
public final class ShipStructure {
    public static final ShipStructure EMPTY = new ShipStructure(List.of(), Vec3d.ZERO);

    public static final Codec<ShipStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ShipBlock.CODEC.listOf().fieldOf("blocks").forGetter(ShipStructure::blocks),
            Vec3d.CODEC.fieldOf("center_of_mass").forGetter(ShipStructure::centerOfMass)
    ).apply(instance, ShipStructure::new));

    /** Client copy: positions, states and the rotation pivot. Block entity data stays on the server. */
    public static final PacketCodec<RegistryByteBuf, ShipStructure> PACKET_CODEC = PacketCodec.ofStatic(
            (buf, structure) -> {
                buf.writeVarInt(structure.blocks.size());
                for (ShipBlock block : structure.blocks) {
                    buf.writeLong(block.pos().asLong());
                    buf.writeVarInt(Block.getRawIdFromState(block.state()));
                }
                buf.writeDouble(structure.centerOfMass.x);
                buf.writeDouble(structure.centerOfMass.y);
                buf.writeDouble(structure.centerOfMass.z);
            },
            buf -> {
                int size = buf.readVarInt();
                List<ShipBlock> blocks = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    BlockPos pos = BlockPos.fromLong(buf.readLong());
                    BlockState state = Block.getStateFromRawId(buf.readVarInt());
                    blocks.add(new ShipBlock(pos, state, Optional.empty()));
                }
                Vec3d centerOfMass = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
                return new ShipStructure(blocks, centerOfMass);
            }
    );

    private final List<ShipBlock> blocks;
    private final Vec3d centerOfMass;
    private final Long2ObjectOpenHashMap<ShipBlock> byPos = new Long2ObjectOpenHashMap<>();
    private final List<ShipBlock> hull = new ArrayList<>();
    private final double mass;
    private final double volume;
    private final Box localBounds;
    private final double radius;

    public ShipStructure(List<ShipBlock> blocks, Vec3d centerOfMass) {
        this.blocks = List.copyOf(blocks);
        this.centerOfMass = centerOfMass;

        double mass = 0.0;
        double volume = 0.0;
        for (ShipBlock block : this.blocks) {
            this.byPos.put(block.pos().asLong(), block);
            mass += BlockDensity.mass(block.state());
            volume += BlockDensity.volume(block.state());
        }
        this.mass = Math.max(mass, 0.1);
        this.volume = volume;

        // Only blocks with an exposed face can touch the world, so collision checks skip the interior.
        for (ShipBlock block : this.blocks) {
            if (hasCollision(block.state()) && isExposed(block.pos())) {
                this.hull.add(block);
            }
        }

        if (this.blocks.isEmpty()) {
            this.localBounds = new Box(0, 0, 0, 0, 0, 0);
            this.radius = 0.0;
        } else {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (ShipBlock block : this.blocks) {
                BlockPos p = block.pos();
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX() + 1);
                maxY = Math.max(maxY, p.getY() + 1);
                maxZ = Math.max(maxZ, p.getZ() + 1);
            }
            this.localBounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
            double dx = Math.max(Math.abs(minX - centerOfMass.x), Math.abs(maxX - centerOfMass.x));
            double dz = Math.max(Math.abs(minZ - centerOfMass.z), Math.abs(maxZ - centerOfMass.z));
            this.radius = Math.sqrt(dx * dx + dz * dz);
        }
    }

    /** Builds a structure whose pivot is the mass-weighted centre of its blocks. */
    public static ShipStructure of(List<ShipBlock> blocks) {
        double totalMass = 0.0;
        double x = 0.0, y = 0.0, z = 0.0;
        for (ShipBlock block : blocks) {
            double m = BlockDensity.mass(block.state());
            totalMass += m;
            x += (block.pos().getX() + 0.5) * m;
            y += (block.pos().getY() + 0.5) * m;
            z += (block.pos().getZ() + 0.5) * m;
        }
        Vec3d center = totalMass > 0 ? new Vec3d(x / totalMass, y / totalMass, z / totalMass) : Vec3d.ZERO;
        return new ShipStructure(blocks, center);
    }

    public static boolean hasCollision(BlockState state) {
        return !collisionShape(state).isEmpty();
    }

    public static VoxelShape collisionShape(BlockState state) {
        return state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            ShipBlock neighbour = this.byPos.get(pos.offset(direction).asLong());
            if (neighbour == null || !hasCollision(neighbour.state())) {
                return true;
            }
        }
        return false;
    }

    public List<ShipBlock> blocks() {
        return this.blocks;
    }

    public List<ShipBlock> hull() {
        return this.hull;
    }

    public Vec3d centerOfMass() {
        return this.centerOfMass;
    }

    public double mass() {
        return this.mass;
    }

    public double volume() {
        return this.volume;
    }

    public Box localBounds() {
        return this.localBounds;
    }

    /** Horizontal distance from the pivot to the farthest block corner. */
    public double radius() {
        return this.radius;
    }

    public boolean isEmpty() {
        return this.blocks.isEmpty();
    }

    public int size() {
        return this.blocks.size();
    }

    @Nullable
    public ShipBlock get(int x, int y, int z) {
        return this.byPos.get(BlockPos.asLong(x, y, z));
    }
}
