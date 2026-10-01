package ru.monolith.arsenal.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaterniondc;

/**
 * A rigid physics body that vehicles are built on (a hull made of blocks).
 * <p>
 * Instances are short-lived views: look a body up again by {@link #id()} instead of keeping it across ticks.
 * Read methods return the state of the last completed physics step. Units follow the backend: blocks as metres,
 * kilograms, seconds; vectors are in world axes unless stated otherwise. Server thread only.
 * <p>
 * Forces are measured in physics time, so the total effect does not depend on how many physics steps run per
 * server tick or on server lag:
 * <ul>
 *   <li>impulses are applied exactly once, on the next physics step;</li>
 *   <li>timed forces act until their physics-time duration is used up (the last step is applied partially);</li>
 *   <li>continuous forces stay active until replaced or cleared — use them for engines, tracks and lift.</li>
 * </ul>
 * Queued impulses and timed forces are runtime-only and are not saved with the world.
 */
public interface VehicleBody {
    /** Stable identifier, persisted with the world. */
    long id();

    /** World position of the centre of mass. */
    Vec3d position();

    /** Rotation from body axes to world axes. Read-only view. */
    Quaterniondc rotation();

    /** Linear velocity, metres per second. */
    Vec3d linearVelocity();

    /** Angular velocity, radians per second. */
    Vec3d angularVelocity();

    /** Mass in kilograms. */
    double mass();

    /** Converts a point given relative to the centre of mass in body axes to world coordinates. */
    Vec3d localToWorld(Vec3d local);

    /** Converts a world point to body axes, relative to the centre of mass. */
    Vec3d worldToLocal(Vec3d world);

    /** Linear impulse (newton-seconds) through the centre of mass, applied once. */
    boolean applyImpulse(Vec3d impulse);

    /** Linear impulse (newton-seconds) at a world point, applied once; the point moves with the body. */
    boolean applyImpulseAt(Vec3d impulse, Vec3d worldPosition);

    /** Angular impulse (newton-metre-seconds), applied once. */
    boolean applyAngularImpulse(Vec3d angularImpulse);

    /** Force (newtons) through the centre of mass for {@code seconds} of physics time. */
    boolean applyForce(Vec3d force, double seconds);

    /** Force (newtons) at a world point for {@code seconds} of physics time; the point moves with the body. */
    boolean applyForceAt(Vec3d force, Vec3d worldPosition, double seconds);

    /** Torque (newton-metres) for {@code seconds} of physics time. */
    boolean applyTorque(Vec3d torque, double seconds);

    /** Sets a force and torque that act every physics step until changed or cleared. Keyed by {@code channel}. */
    void setContinuous(String channel, Vec3d force, Vec3d torque);

    void clearContinuous(String channel);
}
