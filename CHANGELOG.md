# Changelog

## 0.1.13

Real Valkyrien Skies integration for future military vehicles. Monolith Skies is removed.

* **Valkyrien Skies is the physics backend**: the unofficial Fabric port for 1.21.11 by LumixTeam
  (`ValkyrienSkies-Fabric-MC1.21.11-v3.1.1.jar`, mod id `valkyrienskies` 2.4.205+0d0017dd8a, CurseForge file 8724931),
  used unchanged as a separate mod (`curse.maven:vs-unofficial-port-1556051:8724931` at build time). It is not bundled.
* New VS-independent API in `ru.monolith.arsenal.physics`: `VehicleBody` (position, rotation, velocities, mass,
  local/world transforms, impulses, timed forces/torques, continuous channels, `isSimulated`),
  `VehiclePhysicsBackend` and `BodyCreation` (PENDING → READY/FAILED with timeout and cleanup).
* Adapter in `compat/valkyrienskies`: forces go through immutable commands into a VS `ShipPhysicsListener`
  attachment on the physics thread. Impulses are applied exactly once; timed forces are exact in physics time.
  Commands sent while VS keeps a ship frozen are held, not lost.
* Patch for the VS port's TOML configs: VS used a Forge Config API Port API that no 1.21.9+ build has, so it
  silently ran on defaults. The configs are now registered through FCAP v5 by a mixin in this mod; the VS jar is
  unchanged. Verified by changing `physicsSpeed`.
* Diagnostics: `/monolith_arsenal physics …` (permission level 2).
* **Removed:** Monolith Skies (`monolith_skies`), its ship assembler item and the `monolith_skies:ship` entity.
  See `docs/MIGRATION-from-0.1.12.md` **before** opening 0.1.12 worlds that contain such ships.
* Unchanged: items, creative tab, localisation, test weapon and its pose, `animated_test`; Fabric API, GeckoLib and
  Player Animation Library stay nested in the jar.
* Known VS port limitation: bodies restored from a save fall through terrain (plain VS ships behave the same).
  Details are in `docs/VEHICLE-PHYSICS.md`.

## 0.1.12

Dependency restoration after the libraries were stripped from the uploaded project (30 MB upload limit).

* Recovered the full source code and Gradle project from the 0.1.11 release jar. The restored classes compile to
  bytecode identical to 0.1.11, except `ValkyrienSkiesCompat` (see below).
* Gradle 9.5.1 wrapper, Fabric Loom 1.17.21, Yarn 1.21.11+build.6, Fabric Loader 0.19.2, Java 21.
* Dependencies from official Maven repositories: Fabric API 0.141.6+1.21.11, GeckoLib 5.4.5,
  Player Animation Library 1.1.7+mc.1.21.11. All of them are nested into the mod jar (Jar-in-Jar):
  the release is a single self-contained file.
* **Valkyrien Skies replaced by Monolith Skies 0.1.0** (our own module, nested via Jar-in-Jar).
  Official Valkyrien Skies only exists up to Minecraft 1.21.1. The `2.4.205` version required by 0.1.11 is an
  unofficial third-party port. Monolith Skies provides block ships with gravity, buoyancy, collisions,
  walkable decks, assembly and disassembly, persistence and an API.
* `ValkyrienSkiesCompat` no longer needs Valkyrien Skies at compile time: it detects an installed copy at runtime.
* Architectury API is no longer required (it was needed only by the Valkyrien Skies port).
* Added the missing `items/debug_tool.json` item model definition (before this fix, the Debug Tool rendered as a missing-texture cube).
* Added a client game test (`./gradlew runClientGameTest`) covering the manual checklist.
