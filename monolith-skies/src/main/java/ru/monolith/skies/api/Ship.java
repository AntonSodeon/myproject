package ru.monolith.skies.api;

import java.util.UUID;
import net.minecraft.util.math.Vec3d;

/** A physics ship. All methods must be called on the thread that owns the ship's world. */
public interface Ship {
    UUID getUuid();

    /** World position of the centre of mass (the rotation pivot). */
    Vec3d getPosition();

    /** Heading in degrees, rotating around +Y; 90 degrees equals a clockwise quarter turn of the blocks. */
    double getShipYaw();

    /** Velocity in blocks per tick. */
    Vec3d getVelocity();

    /** Angular velocity around +Y in degrees per tick. */
    double getYawVelocity();

    /** Mass in "water blocks": a block of water weighs 1.0. */
    double getMass();

    int getBlockCount();

    /** Share of the ship's volume that is under water, 0..1. */
    double getSubmergedFraction();

    /** Changes velocity by {@code impulse / mass}. Server side only. */
    void applyImpulse(Vec3d impulse);

    /** Changes velocity directly, ignoring mass. Server side only. */
    void addVelocity(Vec3d velocity);

    /** Changes the angular velocity, in degrees per tick. Server side only. */
    void addYawVelocity(double degreesPerTick);

    /** Converts a point in ship-local block coordinates to world coordinates. */
    Vec3d shipToWorld(Vec3d local);

    /** Converts a world point into ship-local block coordinates. */
    Vec3d worldToShip(Vec3d world);
}
