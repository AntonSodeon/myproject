# Vehicle physics backend (0.1.13)

## What is reused and what is ours

* **Reused, unchanged:** Valkyrien Skies, unofficial Fabric port for 1.21.11 by LumixTeam:
  `ValkyrienSkies-Fabric-MC1.21.11-v3.1.1.jar` (mod id `valkyrienskies`, version `2.4.205+0d0017dd8a`,
  32 936 032 bytes, SHA-256 `30899ecc694c752e89090b40b7c8394c1dc6e4d9e65ef1377e205a6100c023bf`).
  The jar is byte-identical on CurseForge (file 8724931), cursemaven and Modrinth (project `valkyrien-skies-unnof-port`).
  VS provides mass and inertia, gravity, collisions, rotation on three axes, client sync and saving.
  We do not run a second simulation, teleport entities or set yaw by hand.
* **Ours:**
  * `physics/` — VS-independent API: `VehicleBody`, `VehiclePhysicsBackend`, `BodyCreation`, and diagnostic commands.
  * `compat/valkyrienskies/` — the adapter (`VsVehicleBackend`, `VsVehicleBody`, `VehicleControlAttachment`,
    `PhysicsCommand`).
  * Two fixes for bugs of the VS port, applied as mixins in our mod (the VS jar itself is not modified):
    the config bridge (`ValkyrienSkiesConfigBridge`) and the shipyard chunk save fix (`ShipyardChunkSaveMixin`).

## Bodies

A body is a VS ship. The first-stage test body is a 3×5 iron deck with a redstone block marking the front.
It is assembled by VS's own `ShipAssembler` and deleted by its `deleteShip` (blocks removed, no drops).
Our ships carry a `VehicleControlAttachment`, registered with `VsCoreApi.registerAttachment`. The attachment is
saved with the ship by Jackson (public fields only), and the adapter uses it to recognise our bodies.

Creation is asynchronous: `createTestBody` returns a `BodyCreation`. Its state is `PENDING` until VS has loaded
the ship and the attachment is present (`READY`). If that does not happen within 100 ticks, the state becomes
`FAILED` and the ship is deleted. Occupied space fails immediately with no id.

## Forces and threads

| Kind | API | Semantics |
|---|---|---|
| Impulse | `applyImpulse`, `applyImpulseAt`, `applyAngularImpulse` | applied **exactly once**, as `J/dt` on one physics step |
| Timed force or torque | `applyForce`, `applyForceAt`, `applyTorque` (with seconds) | acts for that much **physics time**; the last step is partial, so `F·T` is exact |
| Continuous | `setContinuous(channel, force, torque)` / `clearContinuous` | latest value per channel, applied every step until cleared (for engines, tracks, lift) |

* The server thread creates immutable records of `double`s (`PhysicsCommand`). Vectors are copied on entry and never shared.
* Commands pass through a lock-free queue (bounded at 1024) into `VehicleControlAttachment.physTick(ship, level, dt)`,
  which runs on the VS physics thread. It reads only its own queue and never touches the Minecraft world.
* Results do not depend on how many physics steps run per server tick, or on server lag. This was checked with
  physics step lengths of 1/60 s and 1/120 s.
* **Frozen ships.** This VS port keeps ships without nearby players (and without keep-active) loaded but frozen,
  and while frozen it ignores forces. That state is not exposed by the VS API. The adapter derives it on the
  server thread: a body counts as simulated when its shipyard chunks are loaded and the chunk at its position is
  entity-ticking, both for 10 consecutive ticks. Until then, commands wait in a server-side queue, so they are
  neither lost nor applied twice. `VehicleBody.isSimulated()` reports this state.
* Commands that are still queued are runtime-only and are not saved with the world. The attachment, and with it
  the controller, is saved and restored. VS creates one attachment instance per ship, and the adapter never
  replaces an existing one, so repeated load events cannot add a second controller.

## Synchronisation and network

VS syncs ships to clients itself. Monolith Arsenal adds **no network packets**. The only entry points are server
commands, which require permission level 2.

## VS config patch

The port registers its TOML configs through `fuzs.forgeconfigapiport.fabric.api.neoforge.v4`, which no Forge
Config API Port build for Minecraft 1.21.9+ contains. VS catches the error and silently runs on defaults.
`ValkyrienSkiesConfigBridge` runs after VS's own initializer (mixin at `TAIL`) and repeats the same
registration — same specs, file names and load handler — through the `api.v5` that FCAP 21.11.1 provides.
It registers the load listeners **before** the configs, because STARTUP configs load inside `register()`.
It does nothing if the v4 API exists, and it only logs a warning if FCAP is missing.
Verified: `physicsSpeed = 0.5` halves the fall speed (−13.24 → −6.41 m/s after 1.6 s).

## VS fix: shipyard chunks lost on save (ships fell through terrain after a reload)

Symptom: after reopening a singleplayer world or restarting a server, restored ships fell through the ground.
Our bodies and plain VS ships were affected alike.

Cause: the blocks of a ship live in "shipyard" chunks. The port's `MixinChunkMapScheduleUnload`
(`vs$bulkEvictShipyardAtHead`) drops shipyard chunks whose tickets have expired straight from the chunk map,
without the save that vanilla unloading does. Right after assembly the shipyard chunk briefly has no VS ticket
(the temporary loading ticket expires before VS adds its own), so the chunk with the freshly placed blocks could
be thrown away. VS then loaded the chunk again from disk, empty. In memory the ship still worked, because physics
had already received its blocks, but the world saved an empty shipyard. After a reload the ship had mass and no
collision shape, so it fell. It was a race: in tests, 0 to 10 of 2–11 freshly built ships lost their blocks, depending on timing.

How it was found: per-tick probes showed that terrain and shipyard data reached physics before the fall. Holding
the ships static and re-sending terrain changed nothing. The saved region files had the shipyard chunks of the
falling ships empty, and the chunk in memory lost its blocks within 0.3 s of assembly.

Fix: `ShipyardChunkSaveMixin` (priority 500, so it runs before the VS eviction) saves a shipyard chunk with
unsaved changes right before it is evicted or unloaded. Nothing else changes: VS still evicts the chunk.
Results: 0 of 12 ships lost blocks in 3 runs (before the fix: blocks were lost in 9 of 10 runs, 1 to 10 ships per run); 0 of 11 ships fell after a
restart (before: 10 of 11); the singleplayer reload test and release test R12b now assert that our body and a
plain VS ship stay on the ground. Worlds saved **before** this fix still contain empty shipyard chunks, and
ships in them cannot be repaired; rebuild such ships.

## Known limitations

1. Without players nearby (and without `/vs set-keep-active`), ships are frozen. On a dedicated server without
   players, tests use VS's keep-active command and turn it off afterwards. The adapter never enables it on its own.
2. `physicsTicksPerGameTick` in `valkyrienskies-core-server.toml` has no effect in this port: physics runs at about 60 steps/s.
3. The console shows VS's own `NoClassDefFoundError … NeoForgeConfigRegistry` warning at startup (logged by VS
   before our bridge runs), plus harmless `vs_eureka` recipe errors (recipes for an addon that is not installed).
4. `isSimulated()` is derived from chunk state, not from VS internals. A future VS build may need this rule adjusted.
