package ru.monolith.arsenal.compat.valkyrienskies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.ships.ShipPhysicsListener;
import org.valkyrienskies.core.api.world.PhysLevel;

/**
 * Marks a Valkyrien Skies ship as a Monolith Arsenal vehicle body and applies its forces on the VS physics thread.
 * <p>
 * Saved with the ship (Jackson; only the public fields). VS calls {@link #physTick(PhysShip, PhysLevel, double)}
 * on its physics thread; the server thread only enqueues immutable {@link PhysicsCommand}s into a lock-free queue
 * or replaces continuous channel values. Nothing here touches the Minecraft world.
 */
public final class VehicleControlAttachment implements ShipPhysicsListener {
    public static final String KIND_TEST_BODY = "test_body";
    /** Guards against unbounded growth if the physics thread is stalled (e.g. ship asleep). */
    static final int MAX_QUEUED = 1024;
    private static final double EPSILON = 1.0E-9;

    /** What created this body ("test_body" for the diagnostic hull). */
    public String kind = KIND_TEST_BODY;
    /** Version of this attachment's saved format. */
    public int formatVersion = 2;

    private final transient ConcurrentLinkedQueue<PhysicsCommand> inbox = new ConcurrentLinkedQueue<>();
    private final transient AtomicInteger queued = new AtomicInteger();
    private final transient Map<String, Continuous> continuous = new ConcurrentHashMap<>();
    /** Timed forces in progress. Physics thread only. */
    private final transient List<ActiveTimed> active = new ArrayList<>();
    /** Physics steps observed, for diagnostics. */
    private final transient AtomicInteger physicsSteps = new AtomicInteger();
    /** Length of the last physics step in seconds, for diagnostics. */
    private transient volatile double lastDelta;

    record Continuous(PhysicsCommand.V3 force, PhysicsCommand.V3 torque) {
    }

    private static final class ActiveTimed {
        final PhysicsCommand.Timed command;
        double remaining;

        ActiveTimed(PhysicsCommand.Timed command) {
            this.command = command;
            this.remaining = command.seconds();
        }
    }

    public VehicleControlAttachment() {
    }

    public VehicleControlAttachment(String kind) {
        this.kind = kind;
    }

    /** Server thread. Returns false if the queue is full. */
    boolean enqueue(PhysicsCommand command) {
        if (this.queued.incrementAndGet() > MAX_QUEUED) {
            this.queued.decrementAndGet();
            return false;
        }
        this.inbox.add(command);
        return true;
    }

    /** Server thread. */
    void setContinuous(String channel, Continuous value) {
        if (value.force().isZero() && value.torque().isZero()) {
            this.continuous.remove(channel);
        } else {
            this.continuous.put(channel, value);
        }
    }

    /** Server thread: drops everything not yet applied (used before deleting the body). */
    void clearAll() {
        this.inbox.clear();
        this.queued.set(0);
        this.continuous.clear();
    }

    int physicsSteps() {
        return this.physicsSteps.get();
    }

    double lastDelta() {
        return this.lastDelta;
    }

    @Override
    public void physTick(PhysShip ship, PhysLevel level) {
        // VS calls the overload with the step length; this one exists only to satisfy the interface.
    }

    @Override
    public void physTick(PhysShip ship, PhysLevel level, double delta) {
        this.physicsSteps.incrementAndGet();
        this.lastDelta = delta;
        if (delta <= 0.0) {
            return;
        }
        PhysicsCommand command;
        while ((command = this.inbox.poll()) != null) {
            this.queued.decrementAndGet();
            if (command instanceof PhysicsCommand.Impulse impulse) {
                // An impulse J becomes the force J/dt for exactly this one step.
                apply(ship, impulse.linear(), impulse.angular(), impulse.bodyPoint(), 1.0 / delta);
            } else if (command instanceof PhysicsCommand.Timed timed && timed.seconds() > EPSILON) {
                this.active.add(new ActiveTimed(timed));
            }
        }
        for (int i = this.active.size() - 1; i >= 0; i--) {
            ActiveTimed timed = this.active.get(i);
            // The last step only covers the remaining time, so F*T is delivered exactly.
            double fraction = Math.min(1.0, timed.remaining / delta);
            apply(ship, timed.command.force(), timed.command.torque(), timed.command.bodyPoint(), fraction);
            timed.remaining -= delta;
            if (timed.remaining <= EPSILON) {
                this.active.remove(i);
            }
        }
        for (Continuous value : this.continuous.values()) {
            apply(ship, value.force(), value.torque(), null, 1.0);
        }
    }

    private static void apply(PhysShip ship, PhysicsCommand.V3 force, PhysicsCommand.V3 torque, PhysicsCommand.V3 bodyPoint, double scale) {
        if (!force.isZero()) {
            var at = bodyPoint == null ? new org.joml.Vector3d() : bodyPoint.toJoml(1.0);
            ship.applyWorldForceToBodyPos(force.toJoml(scale), at);
        }
        if (!torque.isZero()) {
            ship.applyWorldTorque(torque.toJoml(scale));
        }
    }
}
