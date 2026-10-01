package ru.monolith.arsenal.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Result of asking a backend for a new body. Having an id does not mean the body can be used yet: it becomes
 * {@link State#READY} once the backend has loaded it and attached the Monolith Arsenal controller, or
 * {@link State#FAILED} (with everything already cleaned up) if that does not happen in time. Server thread only.
 */
public final class BodyCreation {
    public enum State { PENDING, READY, FAILED }

    private final long id;
    private final long deadlineTick;
    private State state = State.PENDING;
    private String failure = "";
    private final List<Consumer<BodyCreation>> listeners = new ArrayList<>();

    public BodyCreation(long id, long deadlineTick) {
        this.id = id;
        this.deadlineTick = deadlineTick;
    }

    public static BodyCreation failed(String reason) {
        BodyCreation creation = new BodyCreation(-1, 0);
        creation.state = State.FAILED;
        creation.failure = reason;
        return creation;
    }

    public long id() {
        return this.id;
    }

    public State state() {
        return this.state;
    }

    public String failure() {
        return this.failure;
    }

    public long deadlineTick() {
        return this.deadlineTick;
    }

    /** Runs {@code listener} when the creation finishes, or right away if it already has. */
    public void onComplete(Consumer<BodyCreation> listener) {
        if (this.state == State.PENDING) {
            this.listeners.add(listener);
        } else {
            listener.accept(this);
        }
    }

    public void complete(State result, String reason) {
        if (this.state != State.PENDING || result == State.PENDING) {
            return;
        }
        this.state = result;
        this.failure = reason == null ? "" : reason;
        List<Consumer<BodyCreation>> pending = List.copyOf(this.listeners);
        this.listeners.clear();
        pending.forEach(listener -> listener.accept(this));
    }
}
