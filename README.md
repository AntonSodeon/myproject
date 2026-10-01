# MONOLITH: ARSENAL

Fabric mod for Minecraft **1.21.11** (Java 21). Mod ID: `monolith_arsenal`. Current version: **0.1.13**.

Military content: a test weapon with a Player Animation Library pose, a GeckoLib test entity, and — new in
0.1.13 — a physics backend for future vehicles built on **Valkyrien Skies** (an existing engine; no physics of our own).

## Installation (players)

See `INSTALL.txt` in the release archive. In short, the `mods` folder needs **two files**:

| File | Source |
|---|---|
| `monolith-arsenal-0.1.13.jar` | this project (Fabric API, GeckoLib and Player Animation Library are nested inside) |
| `ValkyrienSkies-Fabric-MC1.21.11-v3.1.1.jar` | [CurseForge, file 8724931](https://www.curseforge.com/minecraft/mc-mods/vs-unofficial-port/files/8724931) — the original, unmodified jar |

Fabric Loader 0.19.2+ is installed separately. Upgrading from 0.1.12? Read [docs/MIGRATION-from-0.1.12.md](docs/MIGRATION-from-0.1.12.md) first.

## Building

```bash
./gradlew clean build            # Windows: gradlew.bat clean build
```

The output is `build/libs/monolith-arsenal-0.1.13.jar`. Dev runs: `./gradlew runClient`, `./gradlew runServer`.
In-game checks: `./gradlew runClientGameTest` (needs a display; on headless Linux use `xvfb-run -a ./gradlew runClientGameTest`).

## Dependencies

| Library | Version | Maven coordinates | Repository | How it ships |
|---|---|---|---|---|
| Fabric Loader | 0.19.2 | `net.fabricmc:fabric-loader` | https://maven.fabricmc.net/ | installed by the Fabric installer |
| Fabric API | 0.141.6+1.21.11 | `net.fabricmc.fabric-api:fabric-api` | https://maven.fabricmc.net/ | nested (Jar-in-Jar) |
| GeckoLib | 5.4.5 | `software.bernie.geckolib:geckolib-fabric-1.21.11` | https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/ | nested (Jar-in-Jar) |
| Player Animation Library | 1.1.7+mc.1.21.11 | `com.zigythebird.playeranim:PlayerAnimationLibFabric` | https://repo.redlance.org/public/ | nested (Jar-in-Jar) |
| Valkyrien Skies (unofficial 1.21.11 port, LumixTeam) | 2.4.205+0d0017dd8a (file v3.1.1) | `curse.maven:vs-unofficial-port-1556051:8724931` | https://cursemaven.com | **separate jar in `mods`**, not bundled |
| Fabric Language Kotlin | 1.13.1+kotlin.2.1.10 | `net.fabricmc:fabric-language-kotlin` | https://maven.fabricmc.net/ | build only (VS ships its own copy) |
| Forge Config API Port | 21.11.1 | `maven.modrinth:forge-config-api-port:uXrWPsCu` | https://api.modrinth.com/maven | build/dev only (VS ships its own copy) |
| Night Config | 3.8.3 | `com.electronwill.night-config:core`, `:toml` | Maven Central | build/dev only |

Architectury API and Cloth Config are **not** needed: the VS port has no references to Architectury classes,
and it nests Fabric Language Kotlin, Forge Config API Port, MixinExtras and classmate itself.

## Vehicle physics

Game code uses `ru.monolith.arsenal.physics.VehicleBody` / `VehiclePhysicsBackend` only. The Valkyrien Skies
classes stay in `ru.monolith.arsenal.compat.valkyrienskies`. Design, threading rules and known limits are in
[docs/VEHICLE-PHYSICS.md](docs/VEHICLE-PHYSICS.md).

Operator diagnostics (permission level 2): `/monolith_arsenal physics backend | list | create_test <pos> | status <id> |
info <id> | impulse <id> <x y z> | angular_impulse <id> <x y z> | force <id> <x y z> <seconds> |
torque <id> <x y z> <seconds> | continuous <id> <channel> <force> <torque> | clear_continuous <id> <channel> |
remove <id>`, plus `create_plain_vs <pos> <marker> <attachment>` and `vs_position <id>` for reproducing
Valkyrien Skies behaviour without our code.
