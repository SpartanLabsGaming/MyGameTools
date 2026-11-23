# Contributing to GameTools
<!-- {"name":"Contributing to GameTools","code":"P-O-SLG-MGT","id":146} -->

GameTools follows the organization-wide
[Spartan Labs contributing guide](https://github.com/SpartanLabsGaming/.github/blob/main/CONTRIBUTING.md) —
issues and tracking issues, branching, Conventional
Commits, pull requests, semi-linear merges, versioning and deployment environments
all live there. This file adds only what is specific to GameTools.

- [R-1305 (Coding rules)](#coding-rules "1305-T1")
- [R-1306 (Module layout)](#module-layout "1306-T1")
- [R-1307 (Planning large work)](#planning-large-work "1307-T1")
- [R-1308 (Commit scopes)](#commit-scopes "1308-T1")
- [R-1309 (Running the build and tests)](#running-the-build-and-tests "1309-T1")
- [R-1310 (Versioning)](#versioning "1310-T1")

## Coding rules
<!-- {"name":"Coding rules","code":"P-O-SLG-MGT-CR","id":1305} -->

All code, tests, and documentation follow the organization's
[R-2 (Policy)](https://github.com/SpartanLaboratories/.github/blob/main/POLICY.md "2-T4") and
[R-3 (Design Manifesto)](https://github.com/SpartanLaboratories/.github/blob/main/DESIGN.md "3-T7"). In GameTools, the library's test
packages are `com.spartanlabs.gaming.testing.<set>`, and the Experimental tier's opt-in marker is
`@ExperimentalGameToolsApi`.

## Module layout
<!-- {"name":"Module layout","code":"P-O-SLG-MGT-ML","id":1306} -->

Since `4.0.0` the library is a Gradle multi-module build. As of the `gametools-world`
bootstrap (issue #48) it is published as four Maven coordinates:

| Module | Coordinate | Contents | Depends on |
| --- | --- | --- | --- |
| `gametools-core` | `io.github.spartanlabsgaming:gametools-core` | `com.spartanlabs.gaming.{gameobjects,spatial,event,simulation,annotation}.*`, `com.spartanlabs.geometry.serializations.*` | — |
| `gametools-net` | `io.github.spartanlabsgaming:gametools-net` | `com.spartanlabs.gaming.networking.*` (`GameServer`, `MouseAction`) | `api(project(":gametools-core"))` |
| `gametools-world` | `io.github.spartanlabsgaming:gametools-world` | Phase 1 map/zone/physics/vision systems (issues #46–#50); `com.spartanlabs.gaming.world.map.*` — `TiledMap`, `TerrainLayer`, `TerrainType`, `TileIndex`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` (#46), `MissingSpawnPointException`, `OutOfGridException` (#77); `com.spartanlabs.gaming.world.zone.*` — `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47), `UnzonedEntityException`, `UnzonedPointException` (#77) | `api(project(":gametools-core"))` |
| `gametools` (umbrella) | `io.github.spartanlabsgaming:gametools` | no source — `api` re-export of every module above | all three |

Shared build configuration lives in the `build-logic/` included build as the
`gametools.kotlin-library` / `gametools.published-library` convention plugins; a module build
file is a `plugins {}` block plus its `coordinates(...)` and `pom {}`. New modules on the
roadmap (`gametools-combat`, …) are added the same way.

Every module has its own test tree, one folder per set, under `src/test/kotlin/com/spartanlabs/gaming/testing/<set>/`.
The per-set Gradle tasks (`componentTest`, `integrationTest`, `deterministicTest`,
`e2eTest`, `nonfunctionalTest`) and `./gradlew build` span every module; the four CI check
names are unchanged by the split.

## Planning large work
<!-- {"name":"Planning large work","code":"P-O-SLG-MGT-PL","id":1307} -->

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
<!-- {"name":"Commit scopes","code":"P-O-SLG-MGT-CS","id":1308} -->

`gameobjects`, `networking`, `spatial`, `simulation`, `serialization`, `world`, `build`,
`contributing`, … — for example `fix(networking): …`. Branch names follow the organization guide, e.g.
`feature/77-zone-world-system`.

## Running the build and tests
<!-- {"name":"Running the build and tests","code":"P-O-SLG-MGT-RB","id":1309} -->

Requires JDK 23. The Gradle wrapper pins Gradle 9.7.1.

| Command | What it runs |
| --- | --- |
| `./gradlew componentTest deterministicTest` | component + deterministic — fast, no sockets. Run before every push. |
| `./gradlew integrationTest e2eTest nonfunctionalTest` | integration + e2e + nonfunctional — bind fixed UDP ports; serialized by the `GameServerPortsLock` build service. |
| `./gradlew test` | Every set. |
| `./gradlew dokkaGeneratePublicationHtml` | API docs — also catches broken KDoc links. |
| `./gradlew build` | Compile, test, assemble. |

> The port-binding tests fail with `java.net.BindException` if a `MainKt` game server (or a
> previous test run) is still holding the common UDP ports. That is an environment problem,
> not a code failure — stop the stray process and re-run.

## Versioning
<!-- {"name":"Versioning","code":"P-O-SLG-MGT-VE","id":1310} -->

The scheme and bump table are on the organization's [R-25 (Versioning)](https://github.com/SpartanLaboratories/.github/blob/main/policy/version/README.md "25-T7") page. In GameTools the
version lives only in the `coordinates(...)` call in each published module's
`build.gradle.kts` (`gametools-core`, `gametools-net`, `gametools-world`, `gametools`) — every
module releases together on one version, so the release branch bumps them all.

One GameTools-specific addition to the bump table: an incompatible change limited to
`@ExperimentalGameToolsApi` surface is a **Feature release, not a Major** — commit it without
`!` or a `BREAKING CHANGE:` footer (`1.9.0` → `1.10.0`). Graduation out of Experimental is
recorded in `CHANGELOG.md`.
