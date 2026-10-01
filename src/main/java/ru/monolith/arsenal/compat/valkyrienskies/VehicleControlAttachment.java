package ru.monolith.arsenal.compat.valkyrienskies;

import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

/**
 * Marks a Valkyrien Skies ship as a Monolith Arsenal vehicle body and applies its queued forces.
 * <p>
 * Saved with the ship (Jackson, public fields only). {@link #physTick} runs on the VS physics thread and only
 * reads the volatile {@link ForceSnapshot} published by the server thread — it never touches the Minecraft world.
 */
public final class VehicleControlAttachment implements ShipPhysicsListener {
    public static final String KIND_TEST_BODY = "test_body";

    /** What created this body ("test_body" for the diagnostic hull). */
    public String kind = KIND_TEST_BODY;
    /** Version of this attachment's saved format. */
    public int formatVersion = 1;

    private transient volatile ForceSnapshot snapshot = ForceSnapshot.EMPTY;

    public VehicleControlAttachment() {
    }

    public VehicleControlAttachment(String kind) {
        this.kind = kind;
    }

    void publish(ForceSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    boolean hasForces() {
        return !this.snapshot.isEmpty();
    }

    @Override
    public void physTick(PhysShip ship, PhysLevel level) {
        ForceSnapshot current = this.snapshot;
        if (current.isEmpty()) {
            return;
        }
        if (current.force().lengthSquared() > 0.0) {
            ship.applyWorldForceToBodyPos(current.force(), new Vector3d());
        }
        if (current.torque().lengthSquared() > 0.0) {
            ship.applyWorldTorque(current.torque());
        }
        for (ForceSnapshot.PointForce pointForce : current.pointForces()) {
            ship.applyWorldForce(pointForce.force(), pointForce.worldPosition());
        }
    }
}
