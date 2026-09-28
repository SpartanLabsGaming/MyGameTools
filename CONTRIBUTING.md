# Contributing to GameTools

GameTools follows the organization-wide
[Spartan Labs contributing guide](https://github.com/SpartanLabsGaming/.github/blob/main/CONTRIBUTING.md) —
issues and tracking issues, branching, Conventional
Commits, pull requests, semi-linear merges, versioning, releasing and deployment environments
all live there. This file adds only what is specific to GameTools.

- [Coding rules](#coding-rules)
- [Module layout](#module-layout)
- [Planning large work](#planning-large-work)
- [Commit scopes](#commit-scopes)
- [Running the build and tests](#running-the-build-and-tests)
- [Versioning](#versioning)
- [Releasing](#releasing)

## Coding rules

All code, tests, and documentation follow [`.aiassistant/rules/CLAUDE.md`](.aiassistant/rules/CLAUDE.md):
Kotlin idioms blended OO/FP, `Result` instead of thrown exceptions for expected failures,
structured slf4j logging, KDoc on every public declaration, region-grouped imports, one test
class per file. Tests are organised by the five-level hierarchy into
`com.spartanlabs.gaming.testing.<level>` packages. Public surface is tiered: Stable Core is
untagged, a likely-but-non-core seam carries `@SupportedExtension` (the same semver guarantee as
Stable Core), and an unproven seam is gated `@ExperimentalGameToolsApi` until it graduates.

## Module layout

Since `4.0.0` the library is a Gradle multi-module build. As of the `gametools-world`
bootstrap (issue #48) it is published as four Maven coordinates:

| Module | Coordinate | Contents | Depends on |
| --- | --- | --- | --- |
| `gametools-core` | `io.github.spartanlabsgaming:gametools-core` | `com.spartanlabs.gaming.{gameobjects,spatial,event,simulation,annotation}.*`, `com.spartanlabs.geometry.serializations.*` | — |
| `gametools-net` | `io.github.spartanlabsgaming:gametools-net` | `com.spartanlabs.gaming.networking.*` (`GameServer`, `MouseAction`) | `api(project(":gametools-core"))` |
| `gametools-world` | `io.github.spartanlabsgaming:gametools-world` | Phase 1 map/zone/physics/vision systems (issues #46–#50); `com.spartanlabs.gaming.world.map.*` — `TiledMap`, `TerrainLayer`, `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` (#46); `com.spartanlabs.gaming.world.zone.*` — `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47) | `api(project(":gametools-core"))` |
| `gametools` (umbrella) | `io.github.spartanlabsgaming:gametools` | no source — `api` re-export of every module above | all three |

Shared build configuration lives in the `build-logic/` included build as the
`gametools.kotlin-library` / `gametools.published-library` convention plugins; a module build
file is a `plugins {}` block plus its `coordinates(...)` and `pom {}`. New modules on the
roadmap (`gametools-combat`, …) are added the same way.

Every module has its own five-level test tree under `src/test/kotlin/com/spartanlabs/gaming/testing/<level>/`.
The per-level Gradle tasks (`componentTest`, `integrationTest`, `deterministicTest`,
`e2eTest`, `nonfunctionalTest`) and `./gradlew build` span every module; the four CI check
names are unchanged by the split.

## Planning large work

The tracking-issue, milestone and Project rules are in the
[organization guide](https://github.com/SpartanLabsGaming/.github/blob/main/CONTRIBUTING.md#planning-large-work). For GameTools, milestones are named after the
release that ships them (`5.3.0`), and the roadmap Project is the single *GameTools Roadmap*.

**GameTools Roadmap fields**

| Field | Answers | Values | Replaces |
| --- | --- | --- | --- |
| `Status` | Where is the item? | Todo · Planned · In progress · Blocked · Done | the `status: blocked` label |
| `Initiative` | Which tracking issue owns it? | Map & space · World Systems · Combat · … (one per tracking issue, named after the work) | — |
| `Phase` | Which roadmap phase does it deliver? | Phase 0 — Foundations · Phase 1 — Map & space · Phase 2 — Rich combat · Phase 3 — Authoritative networking · Phase 4 — AI & pathfinding · Phase 5 — Persistence & persistent world · Phase 6 — Abilities & items · Phase 7 — Scale hardening | — |

`Initiative` and `Phase` are independent. Stages of one initiative can deliver different
phases: World Systems' #78 is the Phase 2 XP hook delivered early, while its other stages are
Phase 1. Leave `Phase` empty on work outside the roadmap, such as bug fixes; the Project groups
those under "No Phase".

**Current tracking issues**

| Initiative | Tracking issue | Sub-issues | Phase |
| --- | --- | --- | --- |
| Map & space | #86 | #46, #47, #48, #49, #50 | Phase 1 |
| World Systems | #87 | #76, #77, #78, #79, #80 | Phase 1, except #78: Phase 2 |

## Commit scopes

`gameobjects`, `networking`, `spatial`, `simulation`, `serialization`, `world`, `build`,
`contributing`, … — for example `fix(networking): …`. Branch names follow the organization guide, e.g.
`feature/77-zone-world-system`.

## Running the build and tests

Requires JDK 23. The Gradle wrapper pins Gradle 9.7.1.

| Command | What it runs |
| --- | --- |
| `./gradlew componentTest deterministicTest` | Levels 2 + 4a — fast, no sockets. Run before every push. |
| `./gradlew integrationTest e2eTest nonfunctionalTest` | Levels 3 + 4b + 4c — bind fixed UDP ports; serialized by the `GameServerPortsLock` build service. |
| `./gradlew test` | Every level. |
| `./gradlew dokkaGeneratePublicationHtml` | API docs — also catches broken KDoc links. |
| `./gradlew build` | Compile, test, assemble. |

> The port-binding tests fail with `java.net.BindException` if a `MainKt` game server (or a
> previous test run) is still holding the common UDP ports. That is an environment problem,
> not a code failure — stop the stray process and re-run.

## Versioning

The scheme and bump table are in the [organization guide](https://github.com/SpartanLabsGaming/.github/blob/main/CONTRIBUTING.md#versioning). In GameTools the
version lives only in the `coordinates(...)` call in each published module's
`build.gradle.kts` (`gametools-core`, `gametools-net`, `gametools-world`, `gametools`) — every
module releases together on one version, so the release branch bumps them all.

One GameTools-specific addition to the bump table: an incompatible change limited to
`@ExperimentalGameToolsApi` surface is a **Feature release, not a Major** — commit it without
`!` or a `BREAKING CHANGE:` footer (`1.9.0` → `1.10.0`). Graduation out of Experimental is
recorded in `CHANGELOG.md`.

## Releasing

Follow the [organization release steps](https://github.com/SpartanLabsGaming/.github/blob/main/CONTRIBUTING.md#releasing). In step 2 bump every module's
`coordinates(...)`. For the manual publish step:

```
./gradlew publishAndReleaseToMavenCentral
```

stages and releases every coordinate on Maven Central. Credentials are in
`~/.gradle/gradle.properties` (`mavenCentralUsername` / `mavenCentralPassword`, `signing.*`).
`release.yml` creates the GitHub Release from the `v`-prefixed tag.
