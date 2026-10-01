package ru.monolith.skies.ship;

import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PositionInterpolator;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import ru.monolith.skies.api.Ship;

/**
 * A rigid body made of blocks. The server simulates it; clients interpolate position and heading.
 * <p>
 * Coordinates: a ship-local point {@code l} maps to the world as
 * {@code position + rotateY(l - centerOfMass, yaw)}, so the entity position is the centre of mass.
 * Ships rotate around the vertical axis only.
 */
public class ShipEntity extends Entity implements Ship {
    public static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.98;
    private static final double WATER_DRAG = 0.90;
    private static final double WATER_VERTICAL_DRAG = 0.80;
    private static final double GROUND_FRICTION = 0.6;
    private static final double MAX_SPEED = 4.0;
    private static final double MAX_YAW_SPEED = 10.0;
    /** Rotations closer than this to a multiple of 90 degrees use exact block collision shapes. */
    private static final double SNAP_EPSILON = 0.5;

    private static final TrackedData<ShipStructure> STRUCTURE = DataTracker.registerData(ShipEntity.class, ShipTrackedData.STRUCTURE);
    private static final TrackedData<Float> SHIP_YAW = DataTracker.registerData(ShipEntity.class, TrackedDataHandlerRegistry.FLOAT);

    private final PositionInterpolator interpolator = new PositionInterpolator(this, 3);
    private ShipStructure structure = ShipStructure.EMPTY;
    private double shipYaw;
    private double lastShipYaw;
    private double yawVelocity;
    private double submergedFraction;

    public ShipEntity(EntityType<? extends ShipEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STRUCTURE, ShipStructure.EMPTY);
        builder.add(SHIP_YAW, 0.0F);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (STRUCTURE.equals(data)) {
            this.structure = this.dataTracker.get(STRUCTURE);
            this.refreshPosition();
        }
    }

    public void setStructure(ShipStructure structure) {
        this.structure = structure;
        this.dataTracker.set(STRUCTURE, structure);
        this.refreshPosition();
    }

    public ShipStructure getStructure() {
        return this.structure;
    }

    public void setShipYaw(double yaw) {
        this.shipYaw = MathHelper.wrapDegrees(yaw);
        this.lastShipYaw = this.shipYaw;
        this.dataTracker.set(SHIP_YAW, (float) this.shipYaw);
        this.refreshPosition();
    }

    @Override
    public double getShipYaw() {
        return this.shipYaw;
    }

    public float getLerpedShipYaw(float tickProgress) {
        return MathHelper.lerpAngleDegrees(tickProgress, (float) this.lastShipYaw, (float) this.shipYaw);
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        ((ShipHolder) this.getEntityWorld()).monolithSkies$getShips().add(this);

        Vec3d oldPos = this.getEntityPos();
        double oldYaw = this.shipYaw;
        this.lastShipYaw = this.shipYaw;

        if (this.getEntityWorld().isClient()) {
            this.interpolator.tick();
            double target = this.dataTracker.get(SHIP_YAW);
            double delta = MathHelper.wrapDegrees(target - this.shipYaw);
            this.shipYaw = Math.abs(delta) < 0.01 ? target : MathHelper.wrapDegrees(this.shipYaw + delta * 0.5);
        } else {
            if (this.structure.isEmpty()) {
                this.discard();
                return;
            }
            this.simulate();
        }

        this.carryPassengers(oldPos, oldYaw);
    }

    private void simulate() {
        World world = this.getEntityWorld();
        if (!world.isPosLoaded(this.getBlockPos())) {
            return;
        }

        if (this.collides(this.getEntityPos(), this.shipYaw)) {
            // Embedded in terrain (e.g. blocks placed inside it): climb out instead of exploding through.
            this.setPosition(this.getEntityPos().add(0.0, 0.1, 0.0));
            this.setVelocity(Vec3d.ZERO);
            return;
        }

        this.submergedFraction = this.computeSubmergedVolume() / Math.max(this.structure.volume(), 1.0E-6);
        double displaced = this.submergedFraction * this.structure.volume();
        double buoyancy = this.hasNoGravity() ? 0.0 : GRAVITY * displaced * BlockDensity.WATER / this.structure.mass();
        double gravity = this.hasNoGravity() ? 0.0 : GRAVITY;

        Vec3d velocity = this.getVelocity().add(0.0, buoyancy - gravity, 0.0);
        double horizontalDrag = MathHelper.lerp(this.submergedFraction, AIR_DRAG, WATER_DRAG);
        double verticalDrag = MathHelper.lerp(this.submergedFraction, AIR_DRAG, WATER_VERTICAL_DRAG);
        velocity = new Vec3d(velocity.x * horizontalDrag, velocity.y * verticalDrag, velocity.z * horizontalDrag);
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().multiply(MAX_SPEED);
        }
        this.yawVelocity = MathHelper.clamp(this.yawVelocity * (this.submergedFraction > 0 ? 0.9 : 0.96), -MAX_YAW_SPEED, MAX_YAW_SPEED);

        Vec3d moved = this.moveWithCollisions(velocity);
        boolean landed = velocity.y < 0.0 && moved.y > velocity.y + 1.0E-7;
        this.setOnGround(landed);
        double vx = moved.x != velocity.x ? 0.0 : velocity.x;
        double vy = moved.y != velocity.y ? 0.0 : velocity.y;
        double vz = moved.z != velocity.z ? 0.0 : velocity.z;
        if (landed) {
            vx *= GROUND_FRICTION;
            vz *= GROUND_FRICTION;
            this.yawVelocity *= GROUND_FRICTION;
        }
        this.setVelocity(Math.abs(vx) < 1.0E-4 ? 0.0 : vx, Math.abs(vy) < 1.0E-4 ? 0.0 : vy, Math.abs(vz) < 1.0E-4 ? 0.0 : vz);

        if (Math.abs(this.yawVelocity) > 1.0E-3) {
            double newYaw = MathHelper.wrapDegrees(this.shipYaw + this.yawVelocity);
            if (this.collides(this.getEntityPos(), newYaw)) {
                this.yawVelocity = 0.0;
            } else {
                this.shipYaw = newYaw;
                this.dataTracker.set(SHIP_YAW, (float) this.shipYaw);
                this.refreshPosition();
            }
        } else {
            this.yawVelocity = 0.0;
        }
        this.velocityDirty = true;
    }

    /** Moves axis by axis (Y, X, Z), stopping at the first world contact on each axis. Returns the applied offset. */
    private Vec3d moveWithCollisions(Vec3d movement) {
        Vec3d pos = this.getEntityPos();
        double[] applied = new double[3];
        double[] wanted = {movement.x, movement.y, movement.z};
        int[] order = {1, 0, 2};
        for (int axis : order) {
            double distance = wanted[axis];
            if (distance == 0.0) {
                continue;
            }
            Vec3d full = pos.add(axisVector(axis, distance));
            if (!this.collides(full, this.shipYaw)) {
                pos = full;
                applied[axis] = distance;
                continue;
            }
            double free = 0.0;
            double blocked = distance;
            for (int i = 0; i < 8; i++) {
                double mid = (free + blocked) * 0.5;
                if (this.collides(pos.add(axisVector(axis, mid)), this.shipYaw)) {
                    blocked = mid;
                } else {
                    free = mid;
                }
            }
            pos = pos.add(axisVector(axis, free));
            applied[axis] = free;
        }
        if (applied[0] != 0.0 || applied[1] != 0.0 || applied[2] != 0.0) {
            this.setPosition(pos);
        }
        return new Vec3d(applied[0], applied[1], applied[2]);
    }

    private static Vec3d axisVector(int axis, double distance) {
        return switch (axis) {
            case 0 -> new Vec3d(distance, 0.0, 0.0);
            case 1 -> new Vec3d(0.0, distance, 0.0);
            default -> new Vec3d(0.0, 0.0, distance);
        };
    }

    /** True if any hull block would overlap world collision at the given pose. */
    public boolean collides(Vec3d position, double yaw) {
        World world = this.getEntityWorld();
        for (ShipBlock block : this.structure.hull()) {
            Vec3d center = toWorld(Vec3d.ofCenter(block.pos()), position, yaw);
            Box box = new Box(center.x - 0.499, center.y - 0.499, center.z - 0.499, center.x + 0.499, center.y + 0.499, center.z + 0.499);
            for (VoxelShape shape : world.getBlockCollisions(null, box)) {
                if (!shape.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Volume of blocks below the water surface, in cubic blocks. */
    private double computeSubmergedVolume() {
        World world = this.getEntityWorld();
        double submerged = 0.0;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (ShipBlock block : this.structure.blocks()) {
            Vec3d center = this.shipToWorld(Vec3d.ofCenter(block.pos()));
            double bottom = center.y - 0.5;
            cursor.set(center.x, bottom + 1.0E-3, center.z);
            FluidState fluid = world.getFluidState(cursor);
            if (!fluid.isIn(FluidTags.WATER)) {
                continue;
            }
            double surface = cursor.getY() + fluid.getHeight(world, cursor);
            cursor.move(0, 1, 0);
            FluidState above = world.getFluidState(cursor);
            if (above.isIn(FluidTags.WATER)) {
                surface = cursor.getY() + above.getHeight(world, cursor);
            }
            double fraction = MathHelper.clamp(surface - bottom, 0.0, 1.0);
            submerged += fraction * BlockDensity.volume(block.state());
        }
        return submerged;
    }

    // ---------------------------------------------------------------- passengers standing on deck

    private void carryPassengers(Vec3d oldPos, double oldYaw) {
        Vec3d newPos = this.getEntityPos();
        double deltaYaw = MathHelper.wrapDegrees(this.shipYaw - oldYaw);
        if (newPos.equals(oldPos) && deltaYaw == 0.0) {
            return;
        }
        Box area = this.getBoundingBox().expand(1.0, 2.0, 1.0).offset(oldPos.subtract(newPos));
        List<Entity> candidates = this.getEntityWorld().getOtherEntities(this, area, entity ->
                !(entity instanceof ShipEntity) && !entity.hasVehicle() && !entity.isSpectator() && !entity.noClip
                        && entity.isLogicalSideForUpdatingMovement());
        for (Entity entity : candidates) {
            if (!this.isStandingOn(entity.getBoundingBox(), oldPos, oldYaw)) {
                continue;
            }
            Vec3d relative = rotateY(entity.getEntityPos().subtract(oldPos), deltaYaw);
            entity.setPosition(newPos.add(relative));
            if (deltaYaw != 0.0) {
                float yaw = entity.getYaw() + (float) deltaYaw;
                entity.setYaw(yaw);
                if (entity instanceof LivingEntity living) {
                    living.setHeadYaw(living.getHeadYaw() + (float) deltaYaw);
                    living.setBodyYaw(living.bodyYaw + (float) deltaYaw);
                }
            }
            entity.onLanding();
        }
    }

    private boolean isStandingOn(Box feet, Vec3d position, double yaw) {
        double y = feet.minY - 0.1;
        double[][] samples = {
                {feet.minX, feet.minZ}, {feet.maxX, feet.minZ}, {feet.minX, feet.maxZ}, {feet.maxX, feet.maxZ},
                {(feet.minX + feet.maxX) * 0.5, (feet.minZ + feet.maxZ) * 0.5}
        };
        for (double[] sample : samples) {
            Vec3d local = toLocal(new Vec3d(sample[0], y, sample[1]), position, yaw);
            ShipBlock block = this.structure.get(MathHelper.floor(local.x), MathHelper.floor(local.y), MathHelper.floor(local.z));
            if (block != null && ShipStructure.hasCollision(block.state())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- collisions offered to other entities

    /** Adds the collision shapes of ship blocks that intersect {@code area} (world coordinates). */
    public void collectCollisions(Box area, List<VoxelShape> out) {
        if (this.structure.isEmpty() || !this.getBoundingBox().intersects(area)) {
            return;
        }
        Box local = this.toLocalBounds(area);
        int minX = MathHelper.floor(local.minX), maxX = MathHelper.floor(local.maxX);
        int minY = MathHelper.floor(local.minY), maxY = MathHelper.floor(local.maxY);
        int minZ = MathHelper.floor(local.minZ), maxZ = MathHelper.floor(local.maxZ);
        if ((long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 32768) {
            return;
        }

        int quarterTurns = Math.floorMod(Math.round(this.shipYaw / 90.0), 4);
        boolean snapped = Math.abs(MathHelper.wrapDegrees(this.shipYaw - quarterTurns * 90.0)) < SNAP_EPSILON;
        BlockRotation rotation = rotationFor(quarterTurns);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    ShipBlock block = this.structure.get(x, y, z);
                    if (block == null) {
                        continue;
                    }
                    Vec3d center = this.shipToWorld(new Vec3d(x + 0.5, y + 0.5, z + 0.5));
                    VoxelShape shape;
                    if (snapped) {
                        BlockState rotated = block.state().rotate(rotation);
                        shape = ShipStructure.collisionShape(rotated).offset(center.x - 0.5, center.y - 0.5, center.z - 0.5);
                    } else {
                        VoxelShape own = ShipStructure.collisionShape(block.state());
                        if (own.isEmpty()) {
                            continue;
                        }
                        Box bounds = own.getBoundingBox();
                        shape = VoxelShapes.cuboid(center.x - 0.5, center.y - 0.5 + bounds.minY, center.z - 0.5,
                                center.x + 0.5, center.y - 0.5 + bounds.maxY, center.z + 0.5);
                    }
                    if (!shape.isEmpty() && shape.getBoundingBox().intersects(area)) {
                        out.add(shape);
                    }
                }
            }
        }
    }

    private Box toLocalBounds(Box world) {
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{world.minX, world.maxX}) {
            for (double z : new double[]{world.minZ, world.maxZ}) {
                Vec3d local = this.worldToShip(new Vec3d(x, world.minY, z));
                minX = Math.min(minX, local.x);
                maxX = Math.max(maxX, local.x);
                minZ = Math.min(minZ, local.z);
                maxZ = Math.max(maxZ, local.z);
            }
        }
        double offsetY = this.structure.centerOfMass().y - this.getY();
        return new Box(minX, world.minY + offsetY, minZ, maxX, world.maxY + offsetY, maxZ);
    }

    public static BlockRotation rotationFor(int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    // ---------------------------------------------------------------- transforms

    /** Rotates around +Y so that 90 degrees matches {@link BlockRotation#CLOCKWISE_90}: (x, z) -> (-z, x). */
    public static Vec3d rotateY(Vec3d v, double degrees) {
        if (degrees == 0.0) {
            return v;
        }
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new Vec3d(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    private Vec3d toWorld(Vec3d local, Vec3d position, double yaw) {
        return position.add(rotateY(local.subtract(this.structure.centerOfMass()), yaw));
    }

    private Vec3d toLocal(Vec3d world, Vec3d position, double yaw) {
        return rotateY(world.subtract(position), -yaw).add(this.structure.centerOfMass());
    }

    @Override
    public Vec3d shipToWorld(Vec3d local) {
        return this.toWorld(local, this.getEntityPos(), this.shipYaw);
    }

    @Override
    public Vec3d worldToShip(Vec3d world) {
        return this.toLocal(world, this.getEntityPos(), this.shipYaw);
    }

    @Override
    protected Box calculateDefaultBoundingBox(Vec3d pos) {
        // `structure` is still null while the Entity constructor sets the initial position.
        if (this.structure == null || this.structure.isEmpty()) {
            return super.calculateDefaultBoundingBox(pos);
        }
        Box local = this.structure.localBounds();
        Vec3d com = this.structure.centerOfMass();
        double radius = this.structure.radius();
        return new Box(pos.x - radius, pos.y + local.minY - com.y, pos.z - radius,
                pos.x + radius, pos.y + local.maxY - com.y, pos.z + radius);
    }

    // ---------------------------------------------------------------- Ship API

    @Override
    public Vec3d getPosition() {
        return this.getEntityPos();
    }

    @Override
    public double getMass() {
        return this.structure.mass();
    }

    @Override
    public int getBlockCount() {
        return this.structure.size();
    }

    @Override
    public double getSubmergedFraction() {
        return this.submergedFraction;
    }

    @Override
    public void applyImpulse(Vec3d impulse) {
        this.setVelocity(this.getVelocity().add(impulse.multiply(1.0 / this.structure.mass())));
        this.velocityDirty = true;
    }

    @Override
    public void addVelocity(Vec3d velocity) {
        this.setVelocity(this.getVelocity().add(velocity));
        this.velocityDirty = true;
    }

    @Override
    public void addYawVelocity(double degreesPerTick) {
        this.yawVelocity = MathHelper.clamp(this.yawVelocity + degreesPerTick, -MAX_YAW_SPEED, MAX_YAW_SPEED);
    }

    @Override
    public double getYawVelocity() {
        return this.yawVelocity;
    }

    // ---------------------------------------------------------------- vanilla entity behaviour

    @Override
    public PositionInterpolator getInterpolator() {
        return this.interpolator;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean canUsePortals(boolean allowVehicles) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        double size = Math.max(this.getBoundingBox().getAverageSideLength(), 1.0) * 64.0 * getRenderDistanceMultiplier();
        return distance < size * size;
    }

    @Override
    protected void readCustomData(ReadView view) {
        this.setStructure(view.read("Structure", ShipStructure.CODEC).orElse(ShipStructure.EMPTY));
        this.setShipYaw(view.getDouble("ShipYaw", 0.0));
        this.yawVelocity = view.getDouble("YawVelocity", 0.0);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        view.put("Structure", ShipStructure.CODEC, this.structure);
        view.putDouble("ShipYaw", this.shipYaw);
        view.putDouble("YawVelocity", this.yawVelocity);
    }
}
