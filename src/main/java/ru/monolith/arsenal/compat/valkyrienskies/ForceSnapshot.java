package ru.monolith.arsenal.compat.valkyrienskies;

import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Immutable forces for one game tick, built on the server thread and read by the physics thread.
 * Forces through the centre of mass and torques are summed; point forces are kept separately.
 */
record ForceSnapshot(Vector3dc force, Vector3dc torque, List<PointForce> pointForces) {
    static final ForceSnapshot EMPTY = new ForceSnapshot(new Vector3d(), new Vector3d(), List.of());

    record PointForce(Vector3dc force, Vector3dc worldPosition) {
    }

    boolean isEmpty() {
        return this.force.lengthSquared() == 0.0 && this.torque.lengthSquared() == 0.0 && this.pointForces.isEmpty();
    }
}
