# Changelog

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
