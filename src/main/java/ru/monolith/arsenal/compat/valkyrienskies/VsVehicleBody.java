package ru.monolith.arsenal.compat.valkyrienskies;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import ru.monolith.arsenal.physics.VehicleBody;

/** {@link VehicleBody} view of a loaded Valkyrien Skies ship that carries a {@link VehicleControlAttachment}. */
final class VsVehicleBody implements VehicleBody {
    private final LoadedServerShip ship;
    private final VehicleControlAttachment control;

    VsVehicleBody(LoadedServerShip ship, VehicleControlAttachment control) {
        this.ship = ship;
        this.control = control;
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
        return new Quaterniond(this.ship.getTransform().getShipToWorldRotation());
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
    public boolean applyImpulse(Vec3d impulse) {
        return this.control.enqueue(new PhysicsCommand.Impulse(PhysicsCommand.V3.of(impulse), PhysicsCommand.V3.ZERO, null));
    }

    @Override
    public boolean applyImpulseAt(Vec3d impulse, Vec3d worldPosition) {
        return this.control.enqueue(new PhysicsCommand.Impulse(PhysicsCommand.V3.of(impulse), PhysicsCommand.V3.ZERO,
                PhysicsCommand.V3.of(this.worldToLocal(worldPosition))));
    }

    @Override
    public boolean applyAngularImpulse(Vec3d angularImpulse) {
        return this.control.enqueue(new PhysicsCommand.Impulse(PhysicsCommand.V3.ZERO, PhysicsCommand.V3.of(angularImpulse), null));
    }

    @Override
    public boolean applyForce(Vec3d force, double seconds) {
        return this.control.enqueue(new PhysicsCommand.Timed(PhysicsCommand.V3.of(force), PhysicsCommand.V3.ZERO, null, seconds));
    }

    @Override
    public boolean applyForceAt(Vec3d force, Vec3d worldPosition, double seconds) {
        return this.control.enqueue(new PhysicsCommand.Timed(PhysicsCommand.V3.of(force), PhysicsCommand.V3.ZERO,
                PhysicsCommand.V3.of(this.worldToLocal(worldPosition)), seconds));
    }

    @Override
    public boolean applyTorque(Vec3d torque, double seconds) {
        return this.control.enqueue(new PhysicsCommand.Timed(PhysicsCommand.V3.ZERO, PhysicsCommand.V3.of(torque), null, seconds));
    }

    @Override
    public void setContinuous(String channel, Vec3d force, Vec3d torque) {
        this.control.setContinuous(channel, new VehicleControlAttachment.Continuous(PhysicsCommand.V3.of(force), PhysicsCommand.V3.of(torque)));
    }

    @Override
    public void clearContinuous(String channel) {
        this.control.setContinuous(channel, new VehicleControlAttachment.Continuous(PhysicsCommand.V3.ZERO, PhysicsCommand.V3.ZERO));
    }

    @Override
    public String diagnostics() {
        return String.format(java.util.Locale.ROOT, "physicsSteps=%d lastDt=%.5f", this.control.physicsSteps(), this.control.lastDelta());
    }

    LoadedServerShip ship() {
        return this.ship;
    }

    VehicleControlAttachment control() {
        return this.control;
    }

    static Vec3d toMc(Vector3dc v) {
        return new Vec3d(v.x(), v.y(), v.z());
    }
}
