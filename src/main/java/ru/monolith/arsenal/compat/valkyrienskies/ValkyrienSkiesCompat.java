package ru.monolith.arsenal.compat.valkyrienskies;

import org.valkyrienskies.mod.api.ValkyrienSkies;
import ru.monolith.arsenal.physics.VehiclePhysicsBackend;

/**
 * Valkyrien Skies integration. Only loaded when the {@code valkyrienskies} mod is present, so no VS class is
 * touched otherwise.
 */
public final class ValkyrienSkiesCompat {
    public static final String MOD_ID = "valkyrienskies";

    private ValkyrienSkiesCompat() {
    }

    public static VehiclePhysicsBackend createBackend() {
        // Saved with each ship (Jackson); must be registered before ships load.
        ValkyrienSkies.api().registerAttachment(VehicleControlAttachment.class);
        VsVehicleBackend backend = new VsVehicleBackend();
        ValkyrienSkies.api().getShipLoadEvent().on(event -> backend.onShipLoad(event.getShip()));
        return backend;
    }
}
