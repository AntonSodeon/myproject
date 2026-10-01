package ru.monolith.arsenal.compat.valkyrienskies;

import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

/**
 * Immutable command handed from the server thread to the VS physics thread. Only primitive components are stored,
 * so neither side can change a command after it was queued.
 */
sealed interface PhysicsCommand {
    /** Immutable 3-vector. */
    record V3(double x, double y, double z) {
        static final V3 ZERO = new V3(0, 0, 0);

        static V3 of(Vec3d v) {
            return new V3(v.x, v.y, v.z);
        }

        boolean isZero() {
            return this.x == 0 && this.y == 0 && this.z == 0;
        }

        /** A fresh mutable copy for passing into JOML-based APIs. */
        Vector3d toJoml(double scale) {
            return new Vector3d(this.x * scale, this.y * scale, this.z * scale);
        }
    }

    /**
     * Applied once. {@code bodyPoint} (relative to the centre of mass, body axes) is null for impulses through the
     * centre of mass.
     */
    record Impulse(V3 linear, V3 angular, V3 bodyPoint) implements PhysicsCommand {
    }

    /** Acts for {@code seconds} of physics time. */
    record Timed(V3 force, V3 torque, V3 bodyPoint, double seconds) implements PhysicsCommand {
    }
}
