# Plan: GameTools module split — `gametools-core` / `gametools-net` / umbrella

## Header / Association

- **Covers:** Instruction from Spartak Singh via a Claude Code session on 2026-09-05:
  *"begin planning phase 1."* In that session two scoping decisions were taken:
  1. the `gametools-*` module split (roadmap §1 row 18, §2.1; Phase 0 Open Decision A parked
     it at *"the start of Phase 1"*) ships **first, as its own standalone breaking release**,
     before any Phase 1 feature work;
  2. this document plans **only the split** — the Phase 1a (map & space) plan is written
     separately once the split has landed.
- **Status:** planning only. No source, test, or build file has been modified.
- **Baseline:** GameTools `3.1.0` (Phase 0 shipped 2026-09-06), single Gradle module,
  `master` clean, published as `io.github.spartanlabsgaming:GameTools:3.1.0`.
- **Target version:** `3.1.0` → **`4.0.0`** (one Major release). The published coordinate set
  changes and the umbrella artifact is renamed — a breaking distribution event even though no
  Kotlin symbol moves. See §7.
- **Scope of the split (this release):** create `gametools-core` and `gametools-net` as
  real modules, plus a source-less `gametools` umbrella that re-exports both. The remaining
  roadmap modules (`-world`, `-combat`, `-ai`, `-session`, `-persistence`, `-abilities`,
  `-items`) are **not** created here — each is created by the phase that first fills it.
- **Open GitHub issues addressed:** none directly. One new GameTools tracking issue is filed
  for the branch prefix (Open Decision E).
- **Upstream dependency:** none. WebTools / GeneralTools coordinates are unchanged.
- **Related docs:** `docs/framework-vision-and-roadmap.md` (§2.1 target module layout, §3
  phase boundaries); `docs/phase-0-foundations-plan.md` (plan-doc format precedent, Open
  Decision A which deferred this split); `docs/webtools-2.0.0c-upgrade-plan.md` (breaking-
  release + cross-repo precedent).

---

## 1. Context

### 1.1 What this release does

Splits the one Gradle module into three, moving existing files verbatim — no behaviour
change, no source-level API change beyond the Maven coordinate.

| New module | Coordinate (`io.github.spartanlabsgaming`) | Holds | Internal dep |
|---|---|---|---|
| `gametools-core` | `gametools-core:4.0.0` | `com.spartanlabs.gaming.{gameobjects,spatial,event,simulation}.*`, `com.spartanlabs.geometry.serializations.*` | — |
| `gametools-net` | `gametools-net:4.0.0` | `com.spartanlabs.gaming.networking.*` (`GameServer`, `MouseAction`) | `api(project(":gametools-core"))` |
| `gametools` (umbrella) | `gametools:4.0.0` | no source | `api` both of the above |

A consumer that today depends on `io.github.spartanlabsgaming:GameTools:3.1.0` moves to
`io.github.spartanlabsgaming:gametools:4.0.0` — one line, no import changes, everything still
resolves transitively. A consumer that only needs the object model can instead depend on
`gametools-core` alone.

### 1.2 Current-state facts (verified against `master` this session)

| # | Fact | Evidence |
|---|------|----------|
| 1 | Single module. `settings.gradle.kts` is 4 lines: foojay-resolver plugin + `rootProject.name = "MyGameTools"`. No `include(...)`. | `settings.gradle.kts` |
| 2 | Root `build.gradle.kts` (120 lines) carries all config: plugins (`java-library`, `kotlin("jvm") 2.2.0`, `kotlin("plugin.serialization") 2.2.0`, `com.vanniktech.maven.publish 0.36.0`, `org.jetbrains.dokka 2.0.0`), `api` deps (WebTools `2.0.0c`, GeneralTools `2.0.1`, `kotlinx-serialization-json 1.7.3`, `slf4j-api 2.0.16`), `testImplementation` (`kotlin("test")`, `logback-classic 1.5.18`), `GameServerPortsLock` shared build service, `registerLevelTest(...)` × 5, `dokka { moduleName = "GameTools" }`, `mavenPublishing { coordinates("io.github.spartanlabsgaming", "GameTools", "3.1.0") }` + full `pom {}`. | `build.gradle.kts` |
| 3 | Version lives only in `coordinates(...)` 3rd arg (`build.gradle.kts:93`). | `build.gradle.kts`; `CONTRIBUTING.md` |
| 4 | `src/main` packages: `gameobjects` (17 files), `spatial` (`Quadtree`), `event` (`EventBus`, `GameEvent`), `simulation` (`RandomSource`, `SimulationLoop`), `networking` (`GameServer`, `MouseAction`), `com.spartanlabs.geometry.serializations` (`PointSnapshot`, `DimensionsSnapshot`, `TwoDoublesSnapshot`). | `git ls-files src/main` |
| 5 | **`networking` is the only package nothing else imports.** `GameServer.kt` imports `webtools.*`, `gameobjects.DrawableSnapshot`, `gameobjects.VisibleObject`, `kotlinx.serialization.*`, `slf4j`. `MouseAction.kt` imports only `kotlinx.serialization`. `World` holds no `GameServer` reference. | `GameServer.kt:1-26`, `MouseAction.kt`, `World.kt` |
| 6 | `event` → `gameobjects`: `GameEvent.kt:5-7` imports `gameobjects.{Alive,GameObject,World}`; variants also reference `Alive.AttackEndReason`. | `GameEvent.kt` |
| 7 | `simulation.SimulationLoop` imports `gameobjects.World`; `gameobjects.World` imports `spatial.Quadtree`, `event.{EventBus,GameEvent}`, `simulation.{RandomSource,SeededRandom}`; `gameobjects.Actor` imports `spatial.Quadtree`; `gameobjects.Alive` imports `event.GameEvent`, `simulation.RandomSource`. → `gameobjects`+`spatial`+`event`+`simulation` are one cluster with no clean internal cut today. | `SimulationLoop.kt:1-10`, `World.kt:1-16`, `Actor.kt:1-9`, `Alive.kt:1-13` |
| 8 | `World.add()` special-cases `Alive`: `if (gameObject is Alive) gameObject.world = this` (`World.kt:172`). A `gametools-combat` module holding `Alive` would need a general `GameObject` "added to world" hook first — the roadmap defers that to **Phase 2.1** ("Move `Alive` + projectiles out of `core` into `combat`"). | `World.kt:169-174`; `framework-vision-and-roadmap.md:139` |
| 9 | Tests: `src/test/kotlin/com/spartanlabs/gaming/testing/{component,integration,deterministic,e2e,nonfunctional}` + `src/test/resources/logback-test.xml`. `component` has only core-owned sub-packages (`gameobjects`, `spatial`, `event`, `simulation`); `integration` has only `networking`; `e2e` is one file (`ClientServerRoundTripTest`); `nonfunctional` mixes core (`QuadtreeScalabilityTest`, `WorldTickThroughputTest`, `SimulationLoopTimingTest`) and net (`GameServerRobustnessTest`); `deterministic` is all core. | `git ls-files src/test` |
| 10 | Only the `GameServer` tests bind sockets. `registerLevelTest("integrationTest"/"e2eTest"/"nonfunctionalTest", bindsPorts = true, ...)` currently marks all three levels as port-binding; after the split every actual port-binder is in `gametools-net`. | `build.gradle.kts:70-74`; test bodies |
| 11 | CI (`ci.yml`) jobs: **"Fast tests (component + deterministic)"** (`./gradlew componentTest deterministicTest`), **"Integration tests (integration + e2e + nonfunctional)"** (`./gradlew integrationTest e2eTest nonfunctionalTest`), **"API docs (Dokka)"** (`./gradlew dokkaGeneratePublicationHtml`), **"Assemble"** (`./gradlew assemble`). Each uploads `build/reports/tests/` on failure. These 4 names are the required branch-protection checks. | `.github/workflows/ci.yml`; `version-control-workflow` memory |
| 12 | `release.yml` fires on `v*` tags → `./gradlew build` → `gh release create --generate-notes`. Maven publish is a manual `./gradlew publishAndReleaseToMavenCentral` (never in CI). | `.github/workflows/release.yml`; `CONTRIBUTING.md`; `release-process` memory |
| 13 | `CHANGELOG.md` `[Unreleased]` is currently `_Nothing yet._` (first entries since 3.1.0). Keep-a-Changelog format, `### Added/Changed/Fixed/Dependencies` subsections, compare-link refs at the bottom (2.0.0+ links point at `SpartanLabsGaming/MyGameTools`). | `CHANGELOG.md` |

### 1.3 Why only two content modules, not the roadmap's nine

- **Empty published artifacts are noise.** `gametools-world` / `-combat` / `-ai` / `-session`
  / `-persistence` / `-abilities` / `-items` have no code yet. Publishing seven empty jars to
  Maven Central to satisfy a diagram helps no one.
- **The value delivered here is the machinery**, not the module count: a `subprojects`-based
  shared build, per-module publishing + coordinates, per-module Dokka + `-javadoc.jar`, an
  umbrella that preserves the one-dependency experience, per-module test trees, CI that spans
  modules. Once that exists, `settings.gradle.kts` + one `build.gradle.kts` + a `git mv` is
  all a future module costs.
- **The only clean cut available today is `networking`** (fact 5). `event` / `simulation` /
  `spatial` cannot leave `core` without new seams (facts 6–7), and `Alive` → `combat` is
  a Phase 2 change by roadmap decree (fact 8). Forcing more cuts now means inventing
  refactors this release is explicitly not about.
- **Roadmap alignment:** §2.1's dependency rules (`world`/`session` depend on `core`;
  `combat` on `core`+`world`; `net` on `world`+`combat`+`session`) are the *end state*.
  Today `gametools-net` depending only on `gametools-core` is a strict subset of that — no
  rule is violated, and Phase 3 tightens `net`'s deps when `world`/`combat`/`session` exist.

### 1.4 Acceptance criteria

1. Three modules build: `./gradlew build` green; every per-level test task
   (`componentTest`, `integrationTest`, `deterministicTest`, `e2eTest`, `nonfunctionalTest`)
   green, spanning both content modules.
2. Every pre-split test passes **unmodified** (only its file path changes). No test is
   deleted, added, or rewritten in this release.
3. `./gradlew assemble` produces `gametools-core-4.0.0.jar`, `gametools-net-4.0.0.jar`,
   `gametools-4.0.0.jar` (+ sources + Dokka javadoc jars).
4. `./gradlew publishToMavenLocal` stages all three coordinates; a throwaway consumer project
   depending on `io.github.spartanlabsgaming:gametools:4.0.0` compiles a snippet using
   `World`, `Alive`, and `GameServer` with no other dependency line.
5. `./gradlew dokkaGeneratePublicationHtml` green and produces one aggregated doc site.
6. CI's 4 required checks still pass under their current names (no branch-protection edit),
   or the doc's §6 note is actioned if a name must change.
7. `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md` updated for the new layout and coordinates.
8. `git log --follow` works across the move for at least the principal files
   (`World.kt`, `GameServer.kt`).

---

## 2. Design

### 2.1 Module boundary (resolved)

```
gametools-core   com.spartanlabs.gaming.gameobjects.*      (GameObject → VisibleObject → Actor
                                                            → Alive; Player; Projectile,
                                                            Directional/HomingProjectile;
                                                            Movement; EntityId; World;
                                                            Buff, Capability, Moddable,
                                                            ModularStat, CombinedStat, StatMod;
                                                            all *Snapshot types)
                 com.spartanlabs.gaming.spatial.*          (Quadtree)
                 com.spartanlabs.gaming.event.*            (EventBus, GameEvent)
                 com.spartanlabs.gaming.simulation.*       (RandomSource, SeededRandom,
                                                            SimulationLoop, LoopSettings)
                 com.spartanlabs.geometry.serializations.* (Point/Dimensions/TwoDoubles Snapshot)

gametools-net    com.spartanlabs.gaming.networking.*       (GameServer, MouseAction)
                 → api gametools-core

gametools        (umbrella, no source)
                 → api gametools-core, api gametools-net
```

Dependency direction: `net → core`, `umbrella → {core, net}`. No cycles. `core` depends only
on the external `api` set it depends on today.

**`Alive` stays in `core`.** Roadmap Phase 2.1 owns the `Alive`/projectile move to
`gametools-combat`; doing it here would first require a general `GameObject.onAddedToWorld`
hook (to replace `World.add`'s `is Alive` check), which is feature work this release excludes.

**`Quadtree` stays in `core`.** Roadmap §2.1 files spatial under `gametools-world`, but
`World` (a `core` type) uses `Quadtree`, and `world → core` is the roadmap's own direction —
putting `Quadtree` in `world` now would invert it. Phase 1 decides whether `Quadtree` moves
to `world` alongside `World` or the incremental index supersedes it.

### 2.2 Gradle structure (resolved: `build-logic/` convention plugins — Open Decision B)

Shared config lives in **precompiled script plugins** in a `build-logic/` included build, not
in a root `subprojects {}` block. This is the idiomatic Gradle multi-module pattern, keeps the
full type-safe DSL (`kotlin { }`, `dependencies { api(...) }`, `mavenPublishing { }`) inside
the shared config, and scales to the roadmap's ~10 modules with no rework. The root
`build.gradle.kts`'s existing "no `kotlin-dsl`" warning is about the *library* module's own
classpath and does **not** apply to a separate included build — see §6 for the one thing to
verify.

- **`settings.gradle.kts`** — add `includeBuild("build-logic")` and
  `include("gametools-core", "gametools-net", "gametools")`. `rootProject.name` stays
  `MyGameTools` (Open Decision F). Keep the foojay plugin.
- **`build-logic/`** — a standalone build:
  - `build-logic/settings.gradle.kts` — `rootProject.name = "build-logic"`,
    `dependencyResolutionManagement { repositories { mavenCentral(); gradlePluginPortal() } }`.
  - `build-logic/build.gradle.kts` — `plugins { \`kotlin-dsl\` }`; `dependencies { }` on the
    plugin artifacts so the convention plugins can apply them by id
    (`org.jetbrains.kotlin.jvm`, `org.jetbrains.kotlin.plugin.serialization`,
    `com.vanniktech.maven.publish`, `org.jetbrains.dokka` — added as
    `implementation("<plugin-marker>:<version>")` lines, versions pinned here once).
  - `build-logic/src/main/kotlin/gametools.kotlin-library.gradle.kts` — applies
    `java-library` + `org.jetbrains.kotlin.jvm` + `org.jetbrains.kotlin.plugin.serialization`;
    `repositories { mavenCentral() }`; JVM toolchain 23; the shared `api` deps (WebTools
    `2.0.0c`, GeneralTools `2.0.1`, `kotlinx-serialization-json 1.7.3`, `slf4j-api 2.0.16`)
    and `testImplementation` deps (`kotlin("test")`, `logback-classic 1.5.18`); the
    `GameServerPortsLock` `gradle.sharedServices.registerIfAbsent(...)`;
    `tasks.test { useJUnitPlatform() }`; and the `registerLevelTest(...)` × 5 wiring (now a
    real function in this script, with `filter { isFailOnNoMatchingTests = false }` so a level
    absent from a module is a green no-op).
  - `build-logic/src/main/kotlin/gametools.published-library.gradle.kts` — `plugins {
    id("gametools.kotlin-library"); id("com.vanniktech.maven.publish");
    id("org.jetbrains.dokka") }`; `mavenPublishing { publishToMavenCentral();
    signAllPublications(); configure(JavaLibrary(javadocJar =
    JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = true)) }` **without**
    `coordinates(...)`; the shared `pom { }` (licence / developer / scm / inceptionYear /
    url); the Dokka `dokkaSourceSets.main { sourceLink { remoteLineSuffix = "#L" } }` common
    bits (the per-module `localDirectory` / `remoteUrl` stay in the module).
- **Root `build.gradle.kts`** — thin: `plugins { id("org.jetbrains.dokka") }` and the Dokka
  **aggregation** only (`dependencies { dokka(project(":gametools-core"));
  dokka(project(":gametools-net")) }` + `dokka { moduleName.set("GameTools") }`). No
  `subprojects {}`, no dependency list, no publishing config — it all lives in the convention
  plugins now.
- **`gametools-core/build.gradle.kts`**
  ```
  plugins { id("gametools.published-library") }
  mavenPublishing { coordinates("io.github.spartanlabsgaming", "gametools-core", "3.1.0") }
  mavenPublishing { pom { name.set("GameTools Core"); description.set("Game object model, stats, events, deterministic simulation.") } }
  dokka { dokkaSourceSets.main { sourceLink { localDirectory.set(file("src/main/kotlin")); remoteUrl(".../gametools-core/src/main/kotlin") } } }
  ```
- **`gametools-net/build.gradle.kts`** — same shape +
  `dependencies { api(project(":gametools-core")) }`; `pom` name `"GameTools Net"`,
  description `"UDP GameServer and wire types."`.
- **`gametools/build.gradle.kts`** — `plugins { id("gametools.published-library") }`;
  `dependencies { api(project(":gametools-core")); api(project(":gametools-net")) }`;
  `coordinates(..., "gametools", "3.1.0")`; `pom` name `"GameTools"`, description
  `"Umbrella artifact re-exporting every GameTools module."`. No `src/` (vanniktech still
  emits an empty sources/javadoc jar — Central accepts it). *(Both the `-sources.jar` and
  `-javadoc.jar` are populated since #40 — the umbrella folds in each module's
  `sourcesElements` and aggregates both modules' Dokka; the repo-wide Dokka `KotlinBasePlugin`
  blocker was fixed in the same PR. See `docs/issue-40-umbrella-empty-sources-jar.md`. The
  `.jar` (classes) stays deliberately empty.)*
- Version stays `3.1.0` in every `coordinates(...)` on the feature branches; the bump to
  `4.0.0` happens only on `release/4.0.0` (per `CONTRIBUTING.md` §Releasing). One place per
  module — no shared version constant this release (a `gradle.properties` `version` can come
  later if the three ever need to move in lockstep automatically).

### 2.3 Source & test file moves

All moves are `git mv` (preserves blame/`--follow`). Package declarations and imports are
**untouched** — the package names do not change, only the module root each file sits under.

| From | To |
|------|----|
| `src/main/kotlin/com/spartanlabs/gaming/{gameobjects,spatial,event,simulation}/**` | `gametools-core/src/main/kotlin/com/spartanlabs/gaming/{...}/**` |
| `src/main/kotlin/com/spartanlabs/geometry/serializations/**` | `gametools-core/src/main/kotlin/com/spartanlabs/geometry/serializations/**` |
| `src/main/kotlin/com/spartanlabs/gaming/networking/**` | `gametools-net/src/main/kotlin/com/spartanlabs/gaming/networking/**` |
| `src/test/.../testing/component/**`, `testing/deterministic/**`, `testing/nonfunctional/{QuadtreeScalabilityTest,WorldTickThroughputTest,SimulationLoopTimingTest}.kt` | `gametools-core/src/test/kotlin/...` |
| `src/test/.../testing/integration/networking/**`, `testing/e2e/ClientServerRoundTripTest.kt`, `testing/nonfunctional/GameServerRobustnessTest.kt` | `gametools-net/src/test/kotlin/...` |
| `src/test/resources/logback-test.xml` | copied to **both** `gametools-core/src/test/resources/` and `gametools-net/src/test/resources/` |

`gametools-net`'s test set needs `gametools-core` on its `testImplementation`/`testRuntime`
classpath — it gets that transitively from `api(project(":gametools-core"))`. Test helpers
`ServerFixture`, `FakeClientHarness`, `TestResultExtensions`, `TestVisibleObject` move with
the networking tests. The `deterministic` and `nonfunctional` level packages exist in *both*
modules after the move — intended; the per-level task fans out.

### 2.4 Dokka 2.0 multi-module

Dokka 2.0's Gradle plugin supports aggregation: each subproject applies
`org.jetbrains.dokka` and defines its own `dokkaSourceSets`; the root project applies the
plugin and adds `dokka(project(":..."))` entries under `dependencies`. `./gradlew
dokkaGeneratePublicationHtml` at the root emits the combined HTML (CI `docs` job unchanged).
Per module, `JavadocJar.Dokka("dokkaGeneratePublicationHtml")` still yields that module's own
`-javadoc.jar` from its own partial output for Maven Central.

### 2.5 Alternatives considered

- **Split into all nine roadmap modules now.** Rejected — §1.3. Seven empty published
  artifacts, and it forces `event`/`simulation`/`combat` refactors that belong to later
  phases.
- **Root `subprojects {}` block instead of `build-logic/` convention plugins.** Rejected
  (Open Decision B) — it loses the type-safe DSL in the shared config, is a Gradle
  cross-project-configuration anti-pattern (project-isolation-hostile), and grows
  `if (name == …)` conditionals across the roadmap's ~10 modules. `build-logic/` is the
  idiomatic answer and the `kotlin-dsl` slf4j concern that shaped the current single build
  does not reach an included build's siblings (§6).
- **Keep the umbrella artifactId `GameTools`.** Rejected (Open Decision A) — lowercase
  `gametools` matches the `gametools-*` family and the coordinate is changing anyway.
- **Ship a `gametools-bom` (`java-platform`) alongside the umbrella.** Deferred (Open
  Decision C) — the umbrella already delivers "one line, get everything"; a BOM earns its
  place only once a consumer wants per-module version management.
- **Publish only the umbrella, keep core/net unpublished `project(...)` deps.** Rejected —
  defeats the point; a consumer must be able to depend on `gametools-core` alone.

### 2.6 Staging

Four PRs (§8). PR 1 (scaffold + moves) is the load-bearing one and must land green on its
own. PRs 2–3 are follow-ups (CI polish, docs). PR 4 is the release branch. Nothing is
published until the user runs the manual Maven task after `v4.0.0` is tagged.

---

## 3. File-by-file changes

*All entries are plan-only; the executor makes the edits.*

### `settings.gradle.kts`
- Add `includeBuild("build-logic")` and `include("gametools-core", "gametools-net",
  "gametools")`. Keep the foojay plugin and `rootProject.name = "MyGameTools"`.

### `build-logic/` — new included build (see §2.2 for the file contents)
- `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts` (`\`kotlin-dsl\`` + pinned
  plugin deps), and two precompiled script plugins:
  `src/main/kotlin/gametools.kotlin-library.gradle.kts` (java-library + kotlin + serialization
  + toolchain + shared `api`/test deps + `GameServerPortsLock` + `registerLevelTest(...)` × 5)
  and `src/main/kotlin/gametools.published-library.gradle.kts` (adds vanniktech + dokka +
  shared `pom {}` + `mavenPublishing` config minus `coordinates`).

### `build.gradle.kts` (root) — reduced to the Dokka aggregator
- `plugins { id("org.jetbrains.dokka") }` only.
- The Dokka aggregation `dependencies { dokka(project(":gametools-core"));
  dokka(project(":gametools-net")) }` + `dokka { moduleName.set("GameTools") }`.
- The old single-module `dependencies { }`, `dokka { dokkaSourceSets.main { } }`,
  `mavenPublishing { }`, `pom { }`, `GameServerPortsLock`, and `registerLevelTest(...)` all
  leave the root — into the `build-logic` convention plugins (shared parts) and the module
  build files (per-module parts).

### `gametools-core/build.gradle.kts` — new
- `plugins { id("gametools.published-library") }`.
- `mavenPublishing { coordinates("io.github.spartanlabsgaming", "gametools-core", "3.1.0") }`.
- `mavenPublishing { pom { name.set("GameTools Core"); description.set("...") } }` override.
- `dokka { dokkaSourceSets.main { sourceLink { localDirectory / remoteUrl → .../gametools-core/src/main/kotlin } } }`.

### `gametools-net/build.gradle.kts` — new
- `plugins { id("gametools.published-library") }`; `dependencies { api(project(":gametools-core")) }`.
- `coordinates(..., "gametools-net", "3.1.0")`; `pom` name/description override; module
  `sourceLink`.

### `gametools/build.gradle.kts` — new
- `plugins { id("gametools.published-library") }`.
- `dependencies { api(project(":gametools-core")); api(project(":gametools-net")) }`.
- `coordinates(..., "gametools", "3.1.0")`; `pom { name.set("GameTools"); description.set(
  "Umbrella artifact re-exporting every GameTools module.") }`.

### Source / test tree — `git mv` per §2.3

Manifest (executor expands to individual `git mv` calls; directories move wholesale):
- `gameobjects/`, `spatial/`, `event/`, `simulation/` (main + `testing/component/*`,
  `testing/deterministic/*`, and the three core `testing/nonfunctional` files) → `gametools-core`
- `com/spartanlabs/geometry/serializations/` → `gametools-core`
- `networking/` (main + `testing/integration/networking/*`) → `gametools-net`
- `testing/e2e/ClientServerRoundTripTest.kt`, `testing/nonfunctional/GameServerRobustnessTest.kt`
  → `gametools-net`
- `src/test/resources/logback-test.xml` → copy into both modules
- delete the now-empty `src/` at repo root

### `.github/workflows/ci.yml`
- `fast-tests` / `integration-tests` failure-artifact `path:` `build/reports/tests/` →
  `**/build/reports/tests/`.
- No job-name or `run:` changes (Gradle spans modules).

### `.github/workflows/release.yml`
- No change (`./gradlew build` spans modules).

### `README.md`
- Overview: `(module io.github.spartanlabsgaming:GameTools)` → describe the three coordinates
  (umbrella + core + net).
- Architecture: add a short **"Modules"** subsection (or a column on the Layering table)
  mapping each type row to its module.
- Installation: snippets `GameTools:3.1.0` → `gametools:4.0.0`; add a note that
  `gametools-core` can be used without `gametools-net`.
- Maven Central badge URL `.../artifact/io.github.spartanlabsgaming/GameTools` →
  `.../gametools`.
- Tech Stack "Publishing" row: "…→ Maven Central (multi-module: `gametools-core`,
  `gametools-net`, `gametools` umbrella)".
- Testing section: note the per-level tasks now span modules.

### `CONTRIBUTING.md`
- Add a "Module layout" paragraph (the table from §1.1) and note per-level Gradle test tasks
  run across every module.
- Releasing section: `coordinates(...)` is now per-module — the release branch bumps all
  three.

### `CHANGELOG.md`
- New `## [4.0.0]` section on the release branch (feature branches only touch `[Unreleased]`):
  - `### Changed` — **BREAKING**: the library is now published as three coordinates.
    `io.github.spartanlabsgaming:GameTools` is replaced by
    `io.github.spartanlabsgaming:gametools` (umbrella, unchanged transitive contents) plus
    `gametools-core` and `gametools-net` for selective use. Migration: replace the single
    dependency line; no import changes.
  - `### Added` — `gametools-core` (object model, stats, events, deterministic simulation)
    and `gametools-net` (`GameServer`) as independently consumable artifacts.
- Add `[4.0.0]` compare-link ref.

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | Touched? | What moves |
|------|----------|-----------|
| **Inner core** (in-editor) | Minimal | No `//region` groups change; imports are untouched (package names stable). Only new build files gain import-region comments per `.aiassistant/rules` §6. |
| **Component ring** (KDoc) | No | No public type's KDoc changes — no symbol moved packages. |
| **Boundary ring** (protocol / integration) | **Yes** | The Maven coordinate is a cross-project integration contract. `README.md` Installation + `CHANGELOG.md [4.0.0]` state the old→new mapping and that the umbrella preserves transitive contents. Downstream repos consume the note (no issues filed — `no-downstream-consumer-issues`). |
| **Architectural outer layer** | **Yes** | `README.md` Architecture gains the module map; `CONTRIBUTING.md` gains the layout note; `docs/framework-vision-and-roadmap.md` §2.1 gets a one-line "as-built: `-core` + `-net` + umbrella exist; other modules per their phase" annotation. |

---

## 5. Test plan (5-level hierarchy)

**No new tests. No test edited.** This release is a build re-org; the existing suite is the
regression guard, and every test must pass with only its path changed.

| Level | Package | After the split | Check |
|-------|---------|-----------------|-------|
| 1 — gating | — | none exist | — |
| 2 — component | `...testing.component.*` | all in `gametools-core` (`gameobjects`, `spatial`, `event`, `simulation`) | `./gradlew componentTest` green (core; net no-op) |
| 3 — integration | `...testing.integration.networking` | all in `gametools-net` | `./gradlew integrationTest` green (net; core no-op), port-locked |
| 4a — deterministic | `...testing.deterministic` | all in `gametools-core` | `./gradlew deterministicTest` green |
| 4b — e2e | `...testing.e2e` | `ClientServerRoundTripTest` in `gametools-net` | `./gradlew e2eTest` green, port-locked |
| 4c — nonfunctional | `...testing.nonfunctional` | `Quadtree`/`WorldTick`/`SimulationLoopTiming` in `gametools-core`; `GameServerRobustness` in `gametools-net` | `./gradlew nonfunctionalTest` green across both |

### Additional checks unique to this release

- `./gradlew build` and `./gradlew test` green (span all modules).
- `./gradlew assemble` → three main jars + sources + Dokka javadoc jars at `4.0.0` (on the
  release branch) / `3.1.0` (on feature branches).
- `./gradlew publishToMavenLocal` stages `gametools-core`, `gametools-net`, `gametools`.
- **Scratch-consumer smoke test:** a throwaway Gradle project with the single line
  `implementation("io.github.spartanlabsgaming:gametools:4.0.0")` (from `mavenLocal`)
  compiles a file that constructs a `World`, an `Alive`, and a `GameServer`.
- `./gradlew dokkaGeneratePublicationHtml` green, one aggregated site.
- `git log --follow gametools-core/src/main/kotlin/com/spartanlabs/gaming/gameobjects/World.kt`
  and the `GameServer.kt` equivalent show pre-split history.

### Baseline to establish before starting

`./gradlew test assemble dokkaGeneratePublicationHtml` green on `master` at `3.1.0`, recorded,
so any post-split failure is attributable. JDK 23, Gradle wrapper 9.7.1 (`CONTRIBUTING.md`).

---

## 6. Risks & edge cases

- **Branch-protection check names.** CI job names are unchanged by design, so the 4 required
  checks keep matching. If a reviewer insists on renaming a job (e.g. to reflect modules),
  branch protection must be updated in the same change or the merge blocks. **Confirm the
  4 check names are untouched before opening PR 1.**
- **Vanniktech multi-module publish.** `com.vanniktech.maven.publish` supports per-subproject
  publications; `publishAndReleaseToMavenCentral` aggregates. Risk: the plugin's Central
  Portal upload bundles all modules into one deployment — verify in `publishToMavenLocal` /
  a Central *staging* run that all three coordinates appear before the user runs the
  irreversible release task. Signing (`signing.*` in `~/.gradle/gradle.properties`) must
  cover every module — it does, since the config is shared.
- **Dokka 2.0 aggregation.** The `dokka(project(...))` aggregation API is Dokka 2.0-specific;
  `dokkaGeneratePublicationHtml` must remain the task name CI calls. Verify the aggregated
  task exists at the root and each module still produces its partial output for its
  `-javadoc.jar`.
- **`build-logic` and the `kotlin-dsl` slf4j concern (Open Decision B).** `build-logic/
  build.gradle.kts` applies `\`kotlin-dsl\``, which puts `gradleApi()` (and its bundled slf4j
  provider) on `build-logic`'s *own* compile classpath. The root build's existing warning is
  about that provider reaching a **library module's test runtime** and silencing logback.
  An included build is compiled with a separate classloader and its output is on the
  build-script classpath, not any project's `main`/`test` classpath — so it does not leak.
  **Verify once during PR 1:** `./gradlew :gametools-core:dependencies --configuration
  testRuntimeClasspath` shows no `gradle-api` / stray slf4j binding, and a test that logs at
  INFO still prints under logback. If (unexpectedly) it leaks, fall back to Open Decision B's
  alternative (`subprojects {}`) — the module layout and everything else in this plan is
  unaffected.
- **`build-logic` plugin-version drift.** Plugin versions are pinned in
  `build-logic/build.gradle.kts` (as plugin-marker `implementation` deps) instead of the root
  `plugins {}` block. Keep the Kotlin/serialization versions in lockstep (both `2.2.0`); a
  mismatch fails at plugin resolution, loudly, not silently.
- **`git mv` history.** Bulk directory `git mv` preserves `--follow` for most tools but a
  single commit that both moves and edits a file can confuse blame — **PR 1 moves only, edits
  nothing inside a moved file** (build files are new, not edited-in-place).
- **`GeneralTools:2.0.1` is `api` on `gametools-core` only.** `gametools-net` gets it
  transitively (it needs `com.spartanlabs.geometry` types via the snapshots). Confirm
  `gametools-net` compiles without its own GeneralTools line; if a direct reference surfaces,
  add it explicitly rather than leaning on transitivity.
- **Port-lock scoping.** After the split every socket-binding test is in `gametools-net`;
  Gradle may still run `integrationTest`/`e2eTest`/`nonfunctionalTest` in parallel *within*
  that module, so the shared `GameServerPortsLock` is still required. `registerIfAbsent` by
  the name `"gameServerPortsLock"` from the `gametools.kotlin-library` convention plugin is
  idempotent across modules.
- **Empty umbrella jar.** Central accepts a jar with only a `MANIFEST`; some strict linters
  warn. Acceptable; the umbrella's value is its POM `<dependencies>`. The `.jar` (classes)
  stays deliberately empty, but the `-sources.jar` and `-javadoc.jar` are both populated since
  #40 (they aggregate both modules' sources and Dokka output).
- **Downstream build breakage on release day.** `MyGameServer` / `GameGraphics` keep
  resolving `GameTools:3.1.0` until they choose to move — publishing `4.0.0` does not break
  them. They migrate on their own schedule off the CHANGELOG note.
- **`rootProject.name` vs artifact names.** Keeping `rootProject.name = "MyGameTools"` while
  modules are `gametools-*` is only cosmetic in Gradle output; harmless (Open Decision F).

---

## 7. Breaking-change & cross-repo analysis

**Breaking? Yes — distribution only, not source.**

- The coordinate `io.github.spartanlabsgaming:GameTools` is retired; consumers must switch to
  `io.github.spartanlabsgaming:gametools` (umbrella) or a specific module. No Kotlin symbol
  moves package, so **zero import changes** for anyone on the umbrella.
- Conventional Commits: PR 1 is `refactor(build)!: split into gametools-core, gametools-net,
  and the gametools umbrella` with footer:
  `BREAKING CHANGE: the library is published as three coordinates. Replace
  io.github.spartanlabsgaming:GameTools:<v> with io.github.spartanlabsgaming:gametools:4.0.0
  (umbrella, same transitive contents), or depend on gametools-core / gametools-net
  directly. No source or import changes.`
- Per `CONTRIBUTING.md` → **one Major release, `4.0.0`**.

**Downstream consumers** (`SpartanLabsGaming/MyGameServer`, `SpartanLabsGaming/GameGraphics`):

- One-line dependency edit each. GameTools files **no issues** against them
  (`no-downstream-consumer-issues` memory); the `[4.0.0]` CHANGELOG entry with the mapping is
  the notice.
- Recommendation: merge PRs 1–3, cut `release/4.0.0`, tag `v4.0.0`, publish; the consumers
  bump when they next touch their build. Optionally publish a `4.0.0-SNAPSHOT` from `master`
  first for them to smoke-test against (mirrors the `webtools-2.0.0c` plan's approach).

**Upstream:** none. WebTools / GeneralTools untouched.

---

## 8. Version-control approach

Trunk-based, semi-linear; each branch off latest `master`, rebased before merge, `--no-ff`
merge commit, "Create a merge commit" the only enabled button (`version-control-workflow`
memory / `CONTRIBUTING.md`). PR titles are valid Conventional Commits. A GameTools tracking
issue is filed first (Open Decision E) for the branch prefix.

| PR | Branch | Contains | Green gate |
|----|--------|----------|-----------|
| 1 | `refactor/<issue#>-module-scaffold` | `settings.gradle.kts` (`includeBuild` + `include`); new `build-logic/` with the two convention plugins; root `build.gradle.kts` → Dokka aggregator; new `gametools-core` / `gametools-net` / `gametools` build files; **`git mv`** of all source & tests per §2.3; `logback-test.xml` into both modules; verify the §6 `kotlin-dsl` no-leak check; **this plan document** committed in the first commit (`git log --follow` binds plan to code). | `./gradlew build` + all 5 per-level tasks + `assemble` + `dokkaGeneratePublicationHtml` |
| 2 | `ci/<issue#>-multimodule-pipelines` | `ci.yml` artifact paths → `**/build/reports/tests/`; verify Dokka aggregation; add a `publishToMavenLocal` smoke step notes to CONTRIBUTING if wanted. | CI green under the 4 existing check names |
| 3 | `docs/<issue#>-module-split` | `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md [Unreleased]` (the `### Changed` BREAKING entry + `### Added`). | `dokkaGeneratePublicationHtml` (KDoc links) |
| 4 | `release/4.0.0` (after 1–3 merge) | bump every `coordinates(...)` `3.1.0` → `4.0.0`; move `CHANGELOG` `[Unreleased]` → `[4.0.0] — <date>` + compare-link; `README` install snippets → `4.0.0`. Merge `chore(release): 4.0.0`. Tag `v4.0.0` on `master`, `git push origin master --follow-tags`. **Then the user runs `./gradlew publishAndReleaseToMavenCentral`** (never the agent). | `release.yml` verify job; manual Central staging check for all 3 coordinates |

- Commits carry the `Co-Authored-By:` / `Claude-Session:` trailers.
- PR 1 compiles and is fully green on its own — the split is behaviour-neutral.
- Keep each branch up to date via **rebase**, never merge-from-master.

---

## 9. Open decisions

| ID | Decision | Recommendation |
|----|----------|----------------|
| A | **Umbrella artifactId: `gametools` (lowercase) or keep `GameTools`?** | **DECIDED (2026-09-05): `gametools`** (lowercase). Matches the `gametools-*` family and Maven convention; 4.0.0 is a breaking coordinate change regardless, so the rename is absorbed now rather than paid twice. Consumers move `io.github.spartanlabsgaming:GameTools:3.1.0` → `io.github.spartanlabsgaming:gametools:4.0.0`. New Maven Central artifact page from 4.0.0; the README version badge URL updates; CHANGELOG `[4.0.0]` documents the `GameTools` → `gametools` mapping. |
| B | **Shared build config: `subprojects {}` now, or `build-logic/` convention plugins now?** | **DECIDED (2026-09-05): `build-logic/` convention plugins** (`gametools.kotlin-library`, `gametools.published-library`). Idiomatic, keeps the type-safe DSL, scales to the ~10-module roadmap without rework. One-time check in PR 1 that `kotlin-dsl` in the included build does not leak `gradle-api`/slf4j into a library module's test classpath (§6); `subprojects {}` is the fallback if it does. |
| C | **Ship a `gametools-bom` (`java-platform`) alongside the umbrella?** | **DECIDED (2026-09-05): no BOM in 4.0.0.** The umbrella covers "get everything" and `gametools-core` covers "just the model". A `gametools-bom` is added in Phase 3+ once the module count and à-la-carte selection are real; adding one later is purely additive, non-breaking. |
| D | **`com.spartanlabs.geometry.serializations`: leave in `gametools-core` or split to `gametools-geometry`?** | **DECIDED (2026-09-05): leave in `gametools-core`.** Three tiny DTOs used only by `DrawableSnapshot`; no standalone consumer. If GeneralTools' `com.spartanlabs.geometry` is ever vendored into this repo, they move with it. |
| E | **How to track the split as GitHub issue(s)?** | **DECIDED (2026-09-05): one issue for the whole split** — *"Split into gametools-core / gametools-net / umbrella artifact"* on `SpartanLabsGaming/MyGameTools`; all four PR branches share its number; `release/4.0.0` closes it. (Phase 0's per-PR issues fit because its PRs were independent features; this is one staged refactor.) **The executor asks Spartak before running `gh issue create`.** |
| F | **Rename `rootProject.name` from `MyGameTools` to `gametools`?** | **DECIDED (2026-09-05): keep `MyGameTools`.** It names the Gradle build, not a published artifact; renaming churns IDE/build metadata for no functional gain. |
| G | **Publish a `4.0.0-SNAPSHOT` for downstream to test against before tagging?** | **DECIDED (2026-09-05): decide at release time, leaning SNAPSHOT.** Not a blocker for the work. After PRs 1–3 merge: if `MyGameServer` / `GameGraphics` are in active development, publish `4.0.0-SNAPSHOT` from `master` first and let them confirm the one-line migration (WebTools 2.0.0c precedent); if dormant, tag `v4.0.0` directly — they still resolve `3.1.0` until they choose to move. |

---

## 10. Constants

- New build files follow `.aiassistant/rules/CLAUDE.md`: region-grouped imports, KDoc where a
  block is non-obvious, `Result`-style error handling is N/A for build scripts.
- `README.md` + `CHANGELOG.md` + `CONTRIBUTING.md` updated in the PRs that change external
  shape (global README-currency rule).
- No new runtime dependency; the `api` set is unchanged, only redistributed.
- Every module carries its own five-level test tree under
  `com.spartanlabs.gaming.testing.<level>` (roadmap §6).
- Bugs/gaps found in WebTools/GeneralTools during the work are raised upstream before being
  worked around (none expected).
- Wire protocol, `GameServer` behaviour, and every public API are byte-for-byte unchanged.

---

*Status: planning only. No source, test, or build file has been modified.*
