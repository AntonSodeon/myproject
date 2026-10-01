package ru.monolith.arsenal.physics;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaterniondc;

/**
 * A rigid physics body that vehicles are built on (a hull made of blocks).
 * <p>
 * Instances are short-lived views: look a body up again by {@link #id()} every tick instead of keeping it.
 * Read methods return the state of the last completed physics step; force methods only queue work for the
 * next physics steps. Units follow the backend: blocks as metres, kilograms, seconds.
 * All methods must be called on the server thread.
 */
public interface VehicleBody {
    /** Stable identifier, persisted with the world. */
    long id();

    /** World position of the centre of mass. */
    Vec3d position();

    /** Rotation from body axes to world axes. */
    Quaterniondc rotation();

    /** Linear velocity, metres per second, world axes. */
    Vec3d linearVelocity();

    /** Angular velocity, radians per second, world axes. */
    Vec3d angularVelocity();

    /** Mass in kilograms. */
    double mass();

    /** Converts a point given relative to the centre of mass in body axes to world coordinates. */
    Vec3d localToWorld(Vec3d local);

    /** Converts a world point to body axes, relative to the centre of mass. */
    Vec3d worldToLocal(Vec3d world);

    /** Queues a force (newtons, world axes) through the centre of mass for the current game tick. */
    void applyForce(Vec3d force);

    /** Queues a force (newtons, world axes) acting at a world position for the current game tick. */
    void applyForceAt(Vec3d force, Vec3d worldPosition);

    /** Queues a torque (newton-metres, world axes) for the current game tick. */
    void applyTorque(Vec3d torque);
}
