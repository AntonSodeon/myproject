# MONOLITH: ARSENAL

Fabric mod for Minecraft **1.21.11** (Java 21). Mod ID: `monolith_arsenal`. Current version: **0.1.12**.

The repository also contains **Monolith Skies** (`monolith-skies/`, mod ID `monolith_skies`): our own ship
physics module. It replaces Valkyrien Skies, which has no official build for Minecraft 1.21.11.
The release jar is **self-contained**: Fabric API, GeckoLib, Player Animation Library and Monolith Skies are
nested into it with Fabric Loom Jar-in-Jar (`META-INF/jars/`), so the player puts **one file** into `mods`.

## Building

```bash
./gradlew clean build          # Windows: gradlew.bat clean build
```

Output:

| File | Contents |
|---|---|
| `build/libs/monolith-arsenal-0.1.12.jar` | the mod with all libraries nested (`META-INF/jars/`) |
| `monolith-skies/build/libs/monolith-skies-0.1.0.jar` | Monolith Skies as a standalone mod (for other modpacks) |

Development runs: `./gradlew runClient`, `./gradlew runServer`.
Automated in-game checks: `./gradlew runClientGameTest` (needs a display; on a headless Linux use
`xvfb-run -a ./gradlew runClientGameTest`). Screenshots go to `build/run/clientGameTest/screenshots/`.

## Dependencies

| Library | Version | Maven coordinates | Repository | How it ships |
|---|---|---|---|---|
| Fabric Loader | 0.19.2 | `net.fabricmc:fabric-loader:0.19.2` | https://maven.fabricmc.net/ | installed by the Fabric installer |
| Fabric API | 0.141.6+1.21.11 | `net.fabricmc.fabric-api:fabric-api:0.141.6+1.21.11` | https://maven.fabricmc.net/ | nested in the jar (Jar-in-Jar) |
| GeckoLib | 5.4.5 | `software.bernie.geckolib:geckolib-fabric-1.21.11:5.4.5` | https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/ | nested in the jar (Jar-in-Jar) |
| Player Animation Library | 1.1.7+mc.1.21.11 | `com.zigythebird.playeranim:PlayerAnimationLibFabric:1.1.7+mc.1.21.11` | https://repo.redlance.org/public/ | nested in the jar (Jar-in-Jar) |
| Monolith Skies | 0.1.0 | Gradle subproject `:monolith-skies` | this repository | nested in the jar (Jar-in-Jar) |
| Yarn mappings | 1.21.11+build.6 | `net.fabricmc:yarn:1.21.11+build.6:v2` | https://maven.fabricmc.net/ | build only |

Nested libraries keep their own `fabric.mod.json`, mixins and access wideners. If a player also installs
one of them separately, Fabric Loader loads a single copy (the newest), with no conflict.
Licences allow redistribution: Fabric API is Apache-2.0, GeckoLib and Player Animation Library are MIT; their licence files stay inside the nested jars.

Valkyrien Skies and Architectury API aren't required anymore. If an external Valkyrien Skies is installed anyway,
`ValkyrienSkiesCompat` detects it at runtime and logs its version.

## Monolith Skies — ships

* **Assembly**: right-click any block of a structure with the *Ship Assembler* (Tools tab), or
  `/monolith_skies assemble <x y z>`. All blocks connected to it face-to-face become a ship (limit 4096 blocks,
  so a structure touching the ground is rejected). Chests and other block entities keep their contents.
* **Physics** (server-side): gravity; buoyancy based on block density (wood 0.6, wool 0.25, stone 2.4,
  metal 7.0, water 1.0), so wooden hulls float and stone sinks; water and air drag; ground friction;
  world collisions; rotation around the vertical axis.
* **Players and mobs** can walk on the deck and are carried along when the ship moves or turns.
* **Disassembly**: right-click the ship with the assembler, or `/monolith_skies disassemble <ships>`.
  The heading snaps to the nearest 90°, and block rotation and block entities are restored.
* Ships are saved with the world.
* Operator commands: `/monolith_skies list`, `push <ships> <dx dy dz>`, `spin <ships> <deg/tick>`.
* API for other mods: `ru.monolith.skies.api.MonolithSkiesApi` / `Ship` (list ships, find a ship at a point,
  impulses, coordinate conversion, assemble and disassemble).

Current limits of version 0.1.0: rotation is yaw-only (no pitch or roll), ships don't collide with each other,
block entities with special renderers (chests, signs) are invisible while a block is part of a ship,
and ship blocks can't be targeted or edited directly (disassemble first).
