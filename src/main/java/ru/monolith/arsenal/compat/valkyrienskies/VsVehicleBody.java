package ru.monolith.arsenal.compat.valkyrienskies;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import ru.monolith.arsenal.physics.VehicleBody;

/** {@link VehicleBody} view of a loaded Valkyrien Skies ship. */
final class VsVehicleBody implements VehicleBody {
    private final LoadedServerShip ship;
    private final VsVehicleBackend backend;

    VsVehicleBody(LoadedServerShip ship, VsVehicleBackend backend) {
        this.ship = ship;
        this.backend = backend;
    }

    @Override
    public long id() {
        return this.ship.getId();
    }

    @Override
    public Vec3d position() {
        return toMc(this.ship.getTransform().getPositionInWorld());
    }

    @Override
    public Quaterniondc rotation() {
        return this.ship.getTransform().getShipToWorldRotation();
    }

    @Override
    public Vec3d linearVelocity() {
        return toMc(this.ship.getVelocity());
    }

    @Override
    public Vec3d angularVelocity() {
        return toMc(this.ship.getAngularVelocity());
    }

    @Override
    public double mass() {
        return this.ship.getInertiaData().getShipMass();
    }

    @Override
    public Vec3d localToWorld(Vec3d local) {
        ShipTransform transform = this.ship.getTransform();
        Vector3d point = new Vector3d(local.x, local.y, local.z).add(transform.getPositionInShip());
        return toMc(transform.getShipToWorld().transformPosition(point));
    }

    @Override
    public Vec3d worldToLocal(Vec3d world) {
        ShipTransform transform = this.ship.getTransform();
        Vector3d point = transform.getWorldToShip().transformPosition(new Vector3d(world.x, world.y, world.z));
        return toMc(point.sub(transform.getPositionInShip()));
    }

    @Override
    public void applyForce(Vec3d force) {
        this.backend.queue(this.ship.getId()).force.add(force.x, force.y, force.z);
    }

    @Override
    public void applyForceAt(Vec3d force, Vec3d worldPosition) {
        this.backend.queue(this.ship.getId()).pointForces.add(new ForceSnapshot.PointForce(
                new Vector3d(force.x, force.y, force.z), new Vector3d(worldPosition.x, worldPosition.y, worldPosition.z)));
    }

    @Override
    public void applyTorque(Vec3d torque) {
        this.backend.queue(this.ship.getId()).torque.add(torque.x, torque.y, torque.z);
    }

    LoadedServerShip ship() {
        return this.ship;
    }

    static Vec3d toMc(Vector3dc v) {
        return new Vec3d(v.x(), v.y(), v.z());
    }
}
