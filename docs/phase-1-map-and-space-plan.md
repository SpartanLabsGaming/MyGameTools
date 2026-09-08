# Plan: GameTools Phase 1 — Map & Space

## Header / Association

- **Covers:** Phase 1 of `docs/framework-vision-and-roadmap.md` §3. Instruction from Spartak
  Singh via a Claude Code session on 2026-09-07: *"plan phase 1."*
- **Roadmap scope of Phase 1** (verbatim from the roadmap, §3): a new `gametools-world`
  module holding — (1) a bounded/tiled **map model** with terrain layers, static collision
  geometry, walkable regions and named spawn points, loadable from a data format; (2)
  **zones / chunks**, each owning an interest scope and a subset of the entity set; (3) a
  **spatial-index rework** — incremental update, no per-frame full rebuild, keep a `Quadtree`
  option and add a uniform grid; (4) **physics** — velocity / acceleration / force
  integration plus collision detection *and* resolution (push-out / slide); (5) **vision /
  LOS** — sight radius, terrain occlusion, reveal sources, per-player / per-team vision maps,
  consumed later by the netcode.
- **Status:** planning only. No source, test, or build file has been modified by this
  document.
- **Baseline:** GameTools `5.1.0` (released 2026-09-07, tag `v5.1.0`, PR #45 — the #39
  movement-cancels-attack change and the #40 documentation-jar fixes). Three modules —
  `gametools-core`, `gametools-net`, `gametools` (umbrella).
- **Target version:** see §7. Lean: **two Feature releases** — `5.2.0` (map + zones + spatial
  index, items 1–3) then `5.3.0` (physics + vision, items 4–5). Every change is additive;
  the breaking cleanup rides the Phase 2 Major (`6.0.0`), per the roadmap rule that breaking
  changes are batched at phase boundaries.
- **Upstream dependency:** none blocking. One **enhancement** is wanted from
  `SpartanLaboratories/GeneralTools` (geometry primitives — see §9 Open Decision 8 and §10);
  Phase 1 ships an internal fallback until it lands, so it is not a blocker. Unlike Phase 3,
  Phase 1 touches no WebTools surface.
- **GitHub tracking issues** (`CONTRIBUTING.md` requires an `<issue#>` branch prefix, so
  these were filed *before* their branches are cut — the Phase 0 precedent, its Open Decision
  I), one per roadmap work item, against `SpartanLabsGaming/MyGameTools`:
  [#46](https://github.com/SpartanLabsGaming/MyGameTools/issues/46) map model,
  [#47](https://github.com/SpartanLabsGaming/MyGameTools/issues/47) zones,
  [#48](https://github.com/SpartanLabsGaming/MyGameTools/issues/48) spatial-index rework,
  [#49](https://github.com/SpartanLabsGaming/MyGameTools/issues/49) physics,
  [#50](https://github.com/SpartanLabsGaming/MyGameTools/issues/50) vision / LOS. The
  upstream geometry-primitives enhancement is
  [`SpartanLaboratories/GeneralTools#3`](https://github.com/SpartanLaboratories/GeneralTools/issues/3)
  (§10).
- **Related docs:** `docs/framework-vision-and-roadmap.md` (§2 target architecture, §3 phases,
  §4 pathfinding recommendation, §5 open decisions C/G, §7 transport boundary);
  `docs/module-split-plan.md` (how a module is added; why `Quadtree` was left in `core`);
  `docs/phase-0-foundations-plan.md` (the `EntityId` index, event bus, seeded tick and
  `SimulationLoop` this phase builds on); `docs/webtools-2.0.0c-upgrade-plan.md` (plan-doc
  format precedent).

---

## 1. Context

### 1.1 What Phase 1 builds

`gametools-world` — a new published module, `world → core` in the dependency graph — plus a
small set of **ports on `core`'s `World`** so the world layer plugs in without inverting that
arrow. Everything defaults to today's behaviour: a `World` with no map, no zones and the
current spatial index behaves exactly as `5.1.0` does.

| # | Capability | Home | New public surface (summary) |
|---|-----------|------|------------------------------|
| 1 | **Map model** | `gametools-world` | `TiledMap` (bounded, tile-sized), `TerrainLayer` (per-tile terrain type + movement cost + height), `StaticGeometry` (obstacle rectangles), `SpawnPoint` (named), `MapDefinition` (`@Serializable`) + `MapLoader`. `isWalkable(Point)` / `terrainAt(Point)` queries. |
| 2 | **Zones / chunks** | `gametools-world` | `ZoneGrid` partitioning a `TiledMap` into named rectangular `Zone`s; `ZoneIndex` (entity ↔ zone, refreshed per tick); `GameEvent.EntityChangedZone`. The seam Phase 3 interest filtering and Phase 5 zone save/load build on. |
| 3 | **Spatial-index rework** | `gametools-core` (`spatial` package) | `SpatialIndex<E>` interface (`insert` / `move` / `remove` / `queryBox` / `queryRadius`); `Quadtree` adapted to it; new `UniformGrid` impl; `World.spatialIndex` (pluggable, incremental); `World.quadtree` kept as a deprecated delegating alias. |
| 4 | **Physics** | `gametools-world` | `PhysicsBody` (velocity, acceleration, inverse mass, restitution) as an **opt-in** per-entity component; `PhysicsSystem` — motion integration, broad-phase via `SpatialIndex`, narrow-phase AABB, push-out / slide resolution against bodies and `StaticGeometry`; swept check for fast movers (Open Decision C). |
| 5 | **Vision / LOS** | `gametools-world` | `VisionSystem` — per-entity `sightRadius`, terrain-occlusion raycast on the tile grid; per-team aggregation keyed by `Alive.faction` (Open Decision G); `visibleTo(team)` / `canSee(viewer, target)` queries; `GameEvent.VisionEntered` / `VisionExited`. Computed and queryable in Phase 1; **not** yet wired into any broadcast (that is Phase 3 item 5). |

### 1.2 The central design problem — `world → core`, but `World` is in `core`

Roadmap §2.1 files the map, the spatial index, physics and vision under `gametools-world`,
and fixes the dependency direction as `world` depends on `core`. But `World` — the entity
container that owns `gameObjects`, rebuilds the spatial index and drives `tick()` — is a
`core` type (`gametools-core/.../gameobjects/World.kt`), and every Phase 1 system has to
compose with it. `World` **cannot** depend on `gametools-world`.

Three ways out, with the decision in §2.1:

| Option | Shape | Verdict |
|--------|-------|---------|
| **A — ports in `core`, implementations in `world`** | `core` defines tiny interfaces (`Space`, `SpatialIndex`); `World` gains nullable `space` / pluggable `spatialIndex`; `gametools-world` provides `TiledMap : Space`, `UniformGrid : SpatialIndex`, and the physics / vision / zone **systems** as event-bus subscribers + explicit tick participants a game runs alongside `World.tick()`. | **Chosen.** Smallest `core` surface, keeps `world → core`, keeps `World.tick()` central, fully additive. |
| B — a higher container in `world` | `gametools-world` introduces `Zone` / `GameWorld` that *wraps* a `core` `World` + a map; games target the `world` type. | Rejected for Phase 1 — forces every consumer to re-pivot their top-level type now, for a benefit (multi-zone in one process) the roadmap doesn't need until Phase 5. Revisit then. |
| C — move `World` into `gametools-world` | — | Rejected. Inverts the roadmap's own arrow and moves an import that every consumer and both other modules use. |

**Consequence for the spatial index (roadmap §2.1 deviation, deliberate):** roadmap §2.1
lists "spatial index" under `gametools-world`, but under Option A `World` needs its index at
compile time, so the `SpatialIndex` interface, the adapted `Quadtree`, and the new
`UniformGrid` **stay in `gametools-core`'s `spatial` package**. This is the same reasoning
`docs/module-split-plan.md` §used to leave `Quadtree` in `core` ("`world → core` is the
roadmap's own direction — putting `Quadtree` in `world` now would invert it"), and that doc
explicitly deferred the question to "Phase 1 decides". Phase 1 decides: it stays in `core`.
The roadmap §2 architecture table is updated to match (§3 file list).

### 1.3 Current-state facts (verified against `master` at `20c3406`)

- **`World` (`gametools-core/.../gameobjects/World.kt`)** owns `gameObjects: ArrayList<GameObject>`,
  a `quadtree: Quadtree<Double, VisibleObject>`, the `byId` `EntityId` index, an `events: EventBus`,
  a seeded `rng`, and `tickCount`. `tick()` order today: `tickCount++` →
  `rebuildQuadtree()` (clear + re-insert every `VisibleObject`) → `reindexEntities()` (clear +
  re-enrol `byId`, fire `EntitySpawned`) → tick every object over a `toList()` snapshot →
  drain `removeList` (fire `EntityRemoved`). No bounds, no map, no notion of terrain or space
  beyond raw `Point` coordinates.
- **`Quadtree<N, E>` (`gametools-core/.../spatial/Quadtree.kt`)** — point-region, unbalanced,
  insertion-order shape, **not** incrementally updatable (`remove` leaves an empty slot;
  there is no `move`). `retrieveBox(minX, minY, maxX, maxY)` is the only query. KDoc says
  *"north being the `+y` direction"* — see the y-axis note below. `World.rebuildQuadtree()`
  is its only production caller; it is rebuilt from scratch every tick.
- **Movement (`Actor.kt`, `Movement.kt`)** — `Actor` has `var movement: Movement` (`Targeting`
  default / `Persistent` / `Directional` / `Homing`) and `var destination: Point` (its setter
  re-aims `angle`). `Movement.step(actor)` mutates `actor.location` directly each tick —
  there is no "proposed delta then reconcile" seam, so physics resolution has to interpose
  deliberately (§2.5). `Actor.onUpdate()` calls `move()` while `can(CoreCapability.MOVE)`.
- **Collision** — `VisibleObject.collidesWith(other)` is an **AABB centred on `location`**
  (`abs(dx) <= (w1+w2)/2 && abs(dy) <= (h1+h2)/2`). Note this treats `location` as the box
  **centre**, whereas GeneralTools' `Square` KDoc calls its `location` the **top-left**. The
  engine's convention is centre; Phase 1 geometry helpers must follow the engine, not `Square`.
- **`Alive.faction`** is a bare `var faction: String = "neutral"` (`DEFAULT_FACTION`). There
  is no team / alliance / shared-vision concept — roadmap Open Decision G. `Player` /
  `Alive.owner` is the only grouping with real machinery.
- **Event bus** — `GameEvent` is a `sealed interface` in `gametools-core/.../event/GameEvent.kt`
  with the Phase 0 set (`EntitySpawned`, `EntityRemoved`, `AttackIssued`, `AttackLanded`,
  `DamageDealt`, `EntityDied`, `AttackCancelled`, `AttackEnded`). Its KDoc says *"combat,
  ability, and item events are added by later phases"* — Phase 1 adds spatial events. It is
  `sealed`, and everything in Phase 1 that publishes lives in `core` or `world`; a `world`
  event type cannot be a `sealed` member declared in `core`. **Open Decision 5** resolves how
  world events reach the bus (lean: `EventBus` already carries `Any`-typed events — verify;
  else widen `GameEvent` to an open interface, mirroring `ClientCommand`).
- **`SimulationLoop` (`gametools-core/.../simulation/SimulationLoop.kt`)** — opt-in daemon
  driver, calls `World.tick()` then an `onTick(tickCount)` callback on the loop thread.
  Phase 1 systems that need per-frame work (physics, zone index, vision) run either from that
  callback or from an explicit `WorldSystems.step()` a game calls next to `World.tick()`
  (Open Decision 3).
- **Geometry available** — `com.spartanlabs.geometry` (from GeneralTools 2.0.1) ships only
  `Point`, `Dimensions`, `Square`, `TwoDoubles`. **No** line segment, ray, polygon,
  segment/segment or segment/AABB intersection, dot / cross / normalize. Phase 1 physics and
  LOS need these — §10.
- **y-axis convention is inconsistent in the repo.** `Quadtree` KDoc and `Movement`
  comments say `+y` is "north" (y-up); the auto-memory note `vertical-placement-is-y-down`
  (2026-something, "reverses the old y-up note") says "above" an object is `-y` (y-down,
  screen coordinates). `Alive`'s health-bar sits at `location.y - healthBarYOffset` — i.e.
  it treats **up = `-y`** (y-down). Phase 1 introduces terrain height and "reveal from high
  ground", which forces the question. **Open Decision 7** picks one (lean: y-down / screen
  coordinates — it matches the health-bar code and the memory note) and this phase aligns
  every KDoc and the new map docs to it.
- **Test layout** — every module has `src/test/kotlin/com/spartanlabs/gaming/testing/{component,integration,deterministic,e2e,nonfunctional}/`.
  The `gametools.kotlin-library` convention plugin registers `componentTest` /
  `integrationTest` / `deterministicTest` / `e2eTest` / `nonfunctionalTest`, each filtering
  `com.spartanlabs.gaming.testing.<level>.*` and spanning every module. No `gating` (L1) or
  `uat` (L5) packages exist yet.
- **Module mechanics** — a module is `include(...)` in `settings.gradle.kts` + a
  `build.gradle.kts` of `plugins { id("gametools.published-library") }` + `coordinates(...)`
  + `pom {}` + a `dokka { sourceLink }` block + `dependencies { api(project(":gametools-core")) }`.
  The root `build.gradle.kts` adds `dokka(project(":gametools-world"))`; the umbrella
  `gametools/build.gradle.kts` adds `api(project(":gametools-world"))`, a
  `reexportedModuleSources(project(path = ":gametools-world", configuration = "sourcesElements"))`
  entry, `dokka(project(":gametools-world"))`, and the umbrella's `verifyUmbrellaSourcesJar`
  / `verifyUmbrellaJavadocJar` checks gain a `world` marker assertion.

### 1.4 Acceptance criteria

1. `gametools-world` exists as a published module (`io.github.spartanlabsgaming:gametools-world`),
   `api(project(":gametools-core"))`, its own five-level test tree, re-exported by the umbrella.
2. A `World` constructed the `5.1.0` way — no map, no zones — ticks identically: every
   existing `gametools-core` / `gametools-net` test passes unmodified.
3. `TiledMap` loads from a `@Serializable` `MapDefinition`; `isWalkable` / `terrainAt` /
   `spawnPoint(name)` answer correctly against a fixture map, with KDoc per the Audience-Reach
   standard.
4. `World.spatialIndex` is pluggable and **incrementally maintained** — a
   `nonfunctional` test shows tick cost scales sub-linearly with entity count vs the
   `5.1.0` full-rebuild baseline for a mostly-static field.
5. `World.quadtree` still compiles and returns a working index (deprecated, delegates).
6. `PhysicsBody` is opt-in: an `Actor` with none moves exactly as today; two bodies that
   overlap are pushed apart; a body cannot enter `StaticGeometry`; a fast body does not
   tunnel a thin wall (swept check).
7. `VisionSystem.canSee` respects `sightRadius` and terrain occlusion on a fixture map;
   per-team vision is the union over a team's entities. No broadcast path changes.
8. New `GameEvent`s (`EntityChangedZone`, `VisionEntered`, `VisionExited`) publish on
   `World.events` and are covered.
9. `./gradlew build dokkaGeneratePublicationHtml :build-logic:integrationTest` green.
10. `README.md`, `CONTRIBUTING.md` (module table), `CHANGELOG.md [Unreleased]`, and
    `docs/framework-vision-and-roadmap.md` §2 updated. The Phase 1 issues close.
11. The auto-memory `maven-central-namespace` note is updated to record the 4th coordinate.

---

## 2. Design

### 2.1 Module & the `core` ports

**`gametools-core` gains two interfaces and two hooks on `World`, nothing else:**

```kotlin
// gametools-core/.../spatial/SpatialIndex.kt   (new)
/** A broad-phase spatial index over [E]s positioned in the 2D plane. */
interface SpatialIndex<E> {
    fun insert(x: Double, y: Double, element: E)
    fun move(fromX: Double, fromY: Double, toX: Double, toY: Double, element: E)
    fun remove(x: Double, y: Double, element: E)
    fun queryBox(minX: Double, minY: Double, maxX: Double, maxY: Double): List<E>
    fun queryRadius(x: Double, y: Double, radius: Double): List<E>
    fun clear()
}

// gametools-core/.../gameobjects/Space.kt   (new — the port TiledMap implements)
/**
 * The bounded playfield a [World] simulates in. `null` on a [World] means "unbounded", the
 * pre-Phase-1 behaviour. A [gametools-world] `TiledMap` is the standard implementation.
 */
interface Space {
    val bounds: Square
    fun contains(point: Point): Boolean
    fun isWalkable(point: Point): Boolean
}
```

```kotlin
// World.kt additions — all additive, all defaulting to today's behaviour
var space: Space? = null
var spatialIndex: SpatialIndex<VisibleObject> = QuadtreeSpatialIndex()   // was: quadtree

@Deprecated("Use spatialIndex; Quadtree is one SpatialIndex implementation now",
            ReplaceWith("spatialIndex"))
val quadtree: Quadtree<Double, VisibleObject> get() = /* the QuadtreeSpatialIndex's tree, or a shim */
```

`World.tick()` step 2 changes from *"clear + re-insert every `VisibleObject`"* to
*"reconcile the index against this tick's position changes"* (§2.4). Steps 1, 3, 4, 5 are
unchanged.

**`gametools-world` provides** (`com.spartanlabs.gaming.world.*`):

| Package | Types |
|---------|-------|
| `world.map` | `TiledMap : Space`, `TerrainLayer`, `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition` (`@Serializable`), `MapLoader` |
| `world.zone` | `Zone`, `ZoneGrid`, `ZoneIndex` |
| `world.physics` | `PhysicsBody`, `PhysicsSystem`, `Collision`, `Contact` |
| `world.vision` | `VisionSystem`, `VisionProfile`, `TeamVision` |
| `world.geometry` | internal `Segment`, `Ray`, `Aabb` + intersection helpers (until GeneralTools ships them — §10) |
| `world.system` | `WorldSystems` — the object a game runs each frame alongside `World.tick()` (Open Decision 3) |

Dependency graph after Phase 1: `core ← world ← (net stays on core only)`. `net` does **not**
gain a `world` dependency in Phase 1 — interest filtering that needs vision is Phase 3, and
that phase adds `net → world` then.

### 2.2 Map model (item 1)

- **`TiledMap`** — `widthTiles`, `heightTiles`, `tileSize: Double`. `bounds` is the pixel
  rectangle. World coordinates are continuous `Point`s; `tileAt(point)` floors to a tile
  index. Origin at `(0, 0)`; **y grows downward** (Open Decision 7).
- **`TerrainLayer`** — one per map, a `widthTiles × heightTiles` grid of `TerrainType`
  ids. `TerrainType` carries `walkable: Boolean`, `movementCost: Double` (for Phase 4
  pathfinding — defined now, unused here), `heightLevel: Int` (for LOS occlusion in item 5),
  `blocksVision: Boolean`.
- **`StaticGeometry`** — a list of axis-aligned obstacle `Aabb`s (v1; polygons are a later
  addition behind the same query). `isWalkable(point)` = in bounds ∧ terrain walkable ∧ not
  inside any static obstacle.
- **`SpawnPoint`** — `name: String`, `position: Point`, optional `facing: Int`, optional
  `team: String`. `map.spawnPoint("blue-base")`.
- **`MapDefinition`** — `@Serializable` DTO (tiles as a flat `IntArray` + a
  `terrainPalette`, obstacles as `List<AabbSnapshot>`, spawns as `List<SpawnPointSnapshot>`).
  `MapLoader.fromJson(string)` / `fromDefinition(def)`. **Open Decision 1**: framework-native
  JSON only (lean), vs also a Tiled `.tmx` importer (defer — a `world.map.tmx` add-on later).
- Loading is pure data → objects; no file IO in the library (a consumer reads the file). A
  `MapDefinition` round-trips through `kotlinx-serialization-json`, tested at L4a.

### 2.3 Zones / chunks (item 2)

- **`ZoneGrid`** partitions a `TiledMap` into an `M × N` grid of rectangular **`Zone`**s
  (named `"zone-0-0"` … or from the `MapDefinition`). v1 is a static uniform grid; irregular
  zones are a later addition behind `ZoneIndex`.
- **`ZoneIndex`** maps `EntityId → Zone` and `Zone → Set<EntityId>`, refreshed once per
  frame from entity positions (via `WorldSystems.step()` — Open Decision 3). A zone change
  publishes `GameEvent.EntityChangedZone(entity, from, to)`.
- **Purpose now:** none of Phase 1 *consumes* zones — they exist so Phase 3 interest
  filtering ("vision → zone → distance") and Phase 5 zone save/load have a stable seam.
  Phase 1 ships the partition, the index, the event, and the queries (`zoneIndex.entitiesIn(zone)`,
  `zoneIndex.zoneOf(entityId)`), tested, and stops there. Explicitly **not** in Phase 1:
  per-zone tick budgeting, per-zone `World`s, cross-zone hand-off (all Phase 5 / Phase 7,
  roadmap Open Decision F).

### 2.4 Spatial-index rework (item 3)

- **`SpatialIndex<E>`** interface (§2.1). **`UniformGrid`** — new: fixed cell size, a
  `HashMap<Long, MutableList<E>>` keyed by packed cell coords; `insert` / `move` / `remove`
  are O(1) amortised; `queryBox` / `queryRadius` visit only overlapped cells. Sized for the
  medium target (≤10k entities). Good when density is roughly uniform — the RTS / MOBA case.
- **`QuadtreeSpatialIndex`** — wraps the existing `Quadtree`, adding `move` as `remove` +
  `insert` (the tree's slot reuse makes this cheap-ish) so the interface is honoured. Kept
  as the option for wildly non-uniform density (a few dense clusters in a large empty map).
- **Incremental maintenance.** `VisibleObject` gets an internal `lastIndexedLocation: Point`.
  `World.tick()` step 2 becomes: for each owned `VisibleObject`, if `location != lastIndexedLocation`,
  call `spatialIndex.move(...)` and update the marker; newly spawned objects `insert`;
  removed objects `remove` (in the `removeList` drain). No full clear. `World.rebuildQuadtree()`
  is removed; a `World.reindexSpatial()` full rebuild stays available for a consumer that
  bulk-mutates positions outside the tick.
- **Default choice.** `World.spatialIndex` defaults to `QuadtreeSpatialIndex` to preserve
  `5.1.0` query semantics exactly; a game opts into `UniformGrid(cellSize)`. **Open Decision
  2**: flip the default to `UniformGrid` now (behaviour-compatible, better medium-scale
  numbers) vs keep `Quadtree` default until the Phase 2 Major. Lean: keep `Quadtree` default
  in 5.2.0, switch in 6.0.0.
- `Quadtree` itself does **not** move modules and is **not** removed — `World.quadtree`
  (deprecated) and any consumer using it directly keep working.

### 2.5 Physics (item 4)

- **`PhysicsBody`** — opt-in, attached to an `Actor` via
  `physicsSystem.attach(actor, PhysicsBody(...))` (a `HashMap<EntityId, PhysicsBody>` inside
  `PhysicsSystem`, **not** a field on `Actor` — keeps `core` free of physics). Fields:
  `velocity: Point`, `acceleration: Point`, `inverseMass: Double` (0 = immovable),
  `restitution: Double`, `radiusOrHalfExtents`.
- **`PhysicsSystem.step(dt)`** (run from `WorldSystems.step()`):
  1. integrate — semi-implicit Euler: `velocity += acceleration * dt`, proposed
     `newPos = location + velocity * dt`.
  2. broad-phase — `world.spatialIndex.queryBox` around each moving body's swept bounds.
  3. narrow-phase — AABB / circle overlap (reusing the engine's centre-origin convention);
     for a body whose `|velocity * dt|` exceeds its own half-extent, a **swept** segment-vs-AABB
     test against `StaticGeometry` and other bodies (Open Decision C — discrete + swept for
     fast movers; not full continuous physics).
  4. resolve — positional push-out (split by `inverseMass`) + velocity response scaled by
     `restitution`; slide along a static surface (project out the normal component).
  5. commit — write resolved positions back to `actor.location`.
- **Interaction with `Movement` strategies.** An `Actor` with a `PhysicsBody` has its
  `Movement.step` output treated as a **desired velocity** the system reconciles, rather than
  a direct `location` write. Concretely: `PhysicsSystem` reads the actor's post-`move()`
  `location` as the *desired* position, computes the delta as desired velocity, then resolves
  collisions and rewrites `location`. An `Actor` **without** a body is untouched — `move()`
  is authoritative, exactly as today. **Open Decision 4**: this "desired position diff" glue
  vs a cleaner but breaking `Movement` refactor that returns a delta (defer the refactor to
  Phase 2's Major).
- **Ordering** inside `WorldSystems.step()` relative to `World.tick()` — Open Decision 3.
  Lean: `World.tick()` first (movement, combat, spawn/despawn), then `zoneIndex`, then
  `physics`, then `vision`, all within one frame, order documented like `World.tick()`'s is.

### 2.6 Vision / LOS (item 5)

- **`VisionProfile`** — per entity: `sightRadius: Double`, `eyeHeight: Int` (terrain height
  level the viewer sees over), `revealsStealthWithin: Double`. Attached like `PhysicsBody`
  (a map in `VisionSystem`, keyed by `EntityId`) — `core` stays vision-free.
- **`VisionSystem.step()`** — for each viewer, `queryRadius(sightRadius)` for candidates,
  then an occlusion raycast: walk the tile line (Bresenham/DDA) from viewer to target; block
  if a tile has `blocksVision` or `heightLevel > viewer.eyeHeight` before reaching the
  target's tile. Result: `Set<EntityId>` visible per viewer.
- **Team aggregation.** `TeamVision` unions the visible sets of every entity sharing a
  `faction` string (Open Decision G — v1 keys on the existing `Alive.faction`; a real `Team`
  type with alliances is Phase 2 combat scope). `visionSystem.visibleTo("blue")`.
- **Events** — `GameEvent.VisionEntered(viewerTeam, entity)` / `VisionExited(...)` on team
  vision transitions (debounced across the frame, not per viewer).
- **What Phase 1 does NOT do:** no change to `GameServer.broadcast`, no per-player snapshot
  filtering, no fog-of-war state machine. Vision is *computed and queryable*; Phase 3 item 5
  makes the netcode consume it. This keeps Phase 1 a Feature release with no wire change.

### 2.7 New `GameEvent`s

`EntityChangedZone`, `VisionEntered`, `VisionExited` — added to the bus. Mechanism depends
on Open Decision 5 (`GameEvent` stays `sealed` and these are declared in `core` as
data-less carriers the `world` systems populate via a `core`-side factory; **or** `GameEvent`
becomes an open interface so `world` declares its own). Lean: **open the interface**, matching
how `ClientCommand` was deliberately left open for a cross-module hierarchy
(`docs/client-command-protocol-plan.md`). That is a one-line `sealed` → `interface` change on
`GameEvent`, source-compatible for every existing `when` that stays exhaustive by adding an
`else`, and Dokka-checked.

---

## 3. File-by-file (indicative — shapes settled at implementation)

### `gametools-core` (module 5.2.0 work)

- **new** `spatial/SpatialIndex.kt` — the interface.
- **new** `spatial/UniformGrid.kt` — the grid implementation.
- **new** `spatial/QuadtreeSpatialIndex.kt` — `Quadtree` adapter (`move` = remove+insert).
- **modified** `spatial/Quadtree.kt` — no behaviour change; KDoc y-axis line corrected to
  match Open Decision 7; a `move` convenience may be added.
- **new** `gameobjects/Space.kt` — the `Space` port.
- **modified** `gameobjects/World.kt` — `var space: Space?`; `var spatialIndex`; deprecated
  `quadtree` accessor; `tick()` step 2 → incremental `reconcileSpatial()`; keep a public
  `reindexSpatial()` full-rebuild; class KDoc "what one tick does" list updated.
- **modified** `gameobjects/VisibleObject.kt` — internal `lastIndexedLocation` marker.
- **modified** `event/GameEvent.kt` — `sealed interface` → `interface` (Open Decision 5);
  add `EntityChangedZone`, `VisionEntered`, `VisionExited` **or** leave those to `world`.
- **modified** `event/EventBus.kt` — only if it currently constrains to `GameEvent` subtypes
  in a way the open interface needs relaxed (verify).

### `gametools-world` (new module)

- `build.gradle.kts` — `plugins { id("gametools.published-library") }`,
  `coordinates("io.github.spartanlabsgaming", "gametools-world", "5.2.0")`, `pom {}`,
  `dokka { sourceLink }`, `dependencies { api(project(":gametools-core")) }`.
- `world/map/` — `TiledMap.kt`, `TerrainLayer.kt`, `TerrainType.kt`, `StaticGeometry.kt`,
  `SpawnPoint.kt`, `MapDefinition.kt` (+ `@Serializable` snapshot DTOs), `MapLoader.kt`.
- `world/zone/` — `Zone.kt`, `ZoneGrid.kt`, `ZoneIndex.kt`.
- `world/physics/` — `PhysicsBody.kt`, `PhysicsSystem.kt`, `Collision.kt` *(5.3.0)*.
- `world/vision/` — `VisionSystem.kt`, `VisionProfile.kt`, `TeamVision.kt` *(5.3.0)*.
- `world/geometry/` — `Segment.kt`, `Ray.kt`, `Aabb.kt`, `Intersections.kt` (internal;
  removed / thinned if GeneralTools ships equivalents — §10).
- `world/system/WorldSystems.kt` — the per-frame aggregator.
- `src/test/kotlin/com/spartanlabs/gaming/testing/{component,integration,deterministic,e2e,nonfunctional}/world/...`.
- `src/test/resources/` — a small fixture `MapDefinition` JSON.

### `gametools` (umbrella)

- `build.gradle.kts` — `api(project(":gametools-world"))`;
  `reexportedModuleSources(project(path = ":gametools-world", configuration = "sourcesElements"))`;
  `dokka(project(":gametools-world"))`; `verifyUmbrellaSourcesJar` / `verifyUmbrellaJavadocJar`
  gain a `contains("world")` assertion.

### Root & meta

- `settings.gradle.kts` — `include(... , "gametools-world")`.
- `build.gradle.kts` (root) — `dokka(project(":gametools-world"))`.
- `README.md` — modules table (+row), Architecture (map/zone/vision layer), Features section.
- `CONTRIBUTING.md` — "Module layout" table gains `gametools-world`.
- `CHANGELOG.md` — `[Unreleased]` → `### Added` for each shipped item.
- `docs/framework-vision-and-roadmap.md` — §2.1 note that the spatial index stays in `core`;
  §3 Phase 1 marked in-progress / done; vision→netcode dependency reaffirmed for Phase 3.
- `build-logic/` — **no change** expected (the convention plugins already span any module);
  confirm `registerLevelTest` picks up the new module with no edit.

---

## 4. Test plan (5-level hierarchy)

Per-module tree under `com.spartanlabs.gaming.testing.<level>`; one class per file; mock
external calls at L2.

**`gametools-world` — Level 2 component** (`testing.component.world.*`)
- `TiledMapTest` — bounds, `tileAt`, `isWalkable` (in/out of bounds, non-walkable terrain,
  inside a static obstacle), `spawnPoint(name)` hit/miss.
- `TerrainLayerTest` — palette lookup, out-of-range tile access is a `Result` failure not a
  throw (per `.aiassistant/rules`).
- `ZoneGridTest` / `ZoneIndexTest` — entity → zone assignment, boundary tiles, an entity
  moving across a zone line updates both directions of the index.
- `UniformGridTest` (in `gametools-core` `testing.component.spatial`) — `insert` / `move` /
  `remove` / `queryBox` / `queryRadius` correctness against a brute-force oracle.
- `PhysicsBodyTest`, `PhysicsSystemTest` — integration step; two overlapping bodies separate;
  immovable body (`inverseMass == 0`) does not move; body vs `StaticGeometry` stop; slide.
- `VisionSystemTest` — radius cutoff; occlusion by `blocksVision` tile; occlusion by height;
  team union.
- `WorldSpatialIndexTest` (core) — `World` with a `UniformGrid` returns the same
  `nearby(...)` results as with the `Quadtree`; `quadtree` deprecated accessor still works.

**Level 3 integration** (`testing.integration.world.*`)
- `MapLoaderIntegrationTest` — load the fixture `MapDefinition` JSON from `src/test/resources`,
  assert the built `TiledMap` matches.
- `WorldSystemsIntegrationTest` — a real `World` + `TiledMap` + `WorldSystems`, driven for N
  ticks (no sockets): zone index, physics and vision all update in the documented order; a
  moving `Alive` triggers `EntityChangedZone` and `VisionEntered/Exited` on `World.events`.

**Level 4a deterministic** (`testing.deterministic.world.*`)
- `MapDefinitionRoundTripTest` — `MapDefinition` → JSON → `MapDefinition` is identity.
- `SpatialIndexQueryLawsTest` — `queryBox` / `queryRadius` return exactly the elements a
  linear scan would, over randomised (seeded) inputs, for both implementations.
- `PhysicsResolutionLawsTest` — resolution is symmetric (equal masses separate equally),
  conserves the immovable constraint, and is order-independent for a 2-body contact.
- `VisionSymmetryTest` — with equal `eyeHeight` and no stealth, `canSee(a,b) == canSee(b,a)`.
- `LineOfSightDeterminismTest` — same map + same positions ⇒ same visible set across runs.

**Level 4b e2e** (`testing.e2e.world.*`)
- `MapDrivenSimulationE2ETest` — build a `World` from a fixture map, spawn units at named
  spawn points, run a `SimulationLoop` for a fixed tick count, assert final positions /
  zone membership / vision sets. No `GameServer` (Phase 1 has no wire change) — this is the
  "full simulation stack minus networking" gate.

**Level 4c nonfunctional** (`testing.nonfunctional.world.*` + core)
- `SpatialIndexScalabilityTest` — `UniformGrid` tick-reconcile cost for 10k mostly-static
  entities is materially below the `5.1.0` full-rebuild baseline; query cost within budget.
- `PhysicsThroughputTest` — physics step for ~2k active bodies inside a 20 Hz frame budget.
- `VisionThroughputTest` — per-team vision for 200 viewers / 10k entities within a frame at
  10–20 Hz (the roadmap medium target, §1 decision 12).

**`build-logic` Level 3** — the existing `UmbrellaArtifactAggregationTest` gains a
`gametools-world` assertion (umbrella sources/javadoc jars now aggregate three modules).

---

## 5. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `world → core` inversion pressure (physics/vision "want" to live on `Actor`) | Systems hold per-entity components in `EntityId`-keyed maps; `core` gains only `Space` + `SpatialIndex` interfaces. Enforced by the module boundary — `core` cannot see `world`. |
| Incremental spatial index diverges from the full-rebuild semantics `5.1.0` callers rely on | `Quadtree` stays the default; `WorldSpatialIndexTest` asserts identical `nearby` results; `reindexSpatial()` escape hatch for bulk external mutation. |
| Physics silently changes movement for existing `Actor`s | `PhysicsBody` is strictly opt-in; a bodyless actor's `move()` is authoritative and untested-path-free. An L4b test locks "no body ⇒ identical trajectory". |
| Fast movers tunnel thin walls at 10–20 Hz (roadmap Open Decision C) | Swept segment-vs-AABB for any body moving more than its half-extent per step; documented ceiling; full continuous physics explicitly deferred. |
| `GameEvent` `sealed` → open is a breaking change for an exhaustive `when` in a consumer | It is source-compatible if the consumer's `when` has an `else` or is a statement; call it out in the CHANGELOG under `### Changed`; it is additive at the wire level (events are not serialized). If judged too invasive, fall back to declaring the 3 events in `core` (Open Decision 5). |
| y-axis convention change ripples through client expectations | Pick the convention that already matches the health-bar code and the memory note (y-down); the change is *documentation + one Quadtree KDoc line*, not behaviour — no coordinate math flips. Cross-repo note to GameGraphics / the client project in the CHANGELOG. |
| Scope: 5 substantial systems in one release stalls | Split into 5.2.0 (items 1–3) and 5.3.0 (items 4–5); items 4–5 depend on 1 + 3 anyway. Each roadmap item is its own issue + branch + PR. |
| Map data format lock-in | `MapDefinition` is an internal `@Serializable` DTO, versioned; no external format (Tiled TMX) is committed to in Phase 1 — an importer is a later opt-in add-on module. |
| GeneralTools geometry gap blocks physics/LOS | Ship `world.geometry` internal helpers now; the GeneralTools enhancement (§10) is a parallel, non-blocking track; swap the internals for the shared types when they land (source-compatible — they are value types). |

---

## 6. Interaction with pending / shipped work

- **5.1.0 (`[Unreleased]`: #39, #40)** must release before Phase 1 branches cut, so Phase 1
  starts from a clean released baseline. Phase 1 does not touch `ClientCommand`,
  `StandardCommandApplier`, or the documentation-jar wiring.
- **Issue #42 (Actor intent layer)** — independent of Phase 1; its roadmap slot is Phase 2 /
  Phase 4. If it lands first, `Move` intent will want to compose with `PhysicsBody`
  (desired-velocity, §2.5) — noted for whichever ships second; no ordering constraint.
- **`docs/module-split-plan.md`** — Phase 1 resolves that doc's deferred "does `Quadtree`
  move to `world`" question (answer: no) and its "`Alive`-free spawn hook" note is **not**
  needed here (`World.add`'s `is Alive` check is untouched).

---

## 7. Breaking-change & cross-repo analysis

**Version.** Every Phase 1 change is additive:
- `World` gains `space` (nullable, default `null`) and `spatialIndex` (default
  `QuadtreeSpatialIndex`, query-compatible with today's `quadtree`); `quadtree` stays as a
  deprecated delegating accessor.
- `gametools-world` is a brand-new coordinate — nothing depends on it yet.
- `GameEvent` `sealed` → open interface is source-compatible for statement-position `when`
  and for expression `when` with an `else` (Open Decision 5 keeps a non-breaking fallback).
- No wire-protocol change: `DrawableSnapshot`, `ClientCommand`, `GameServer` untouched.

⇒ **Feature release(s): `5.2.0` (items 1–3), `5.3.0` (items 4–5).** The roadmap's "likely a
breaking constructor change" is deferred: any `World` constructor / default-flip / `Movement`
delta refactor / `quadtree` removal is **batched into the Phase 2 Major (`6.0.0`)**, which
already reshapes the object model. All four coordinates (`gametools`, `-core`, `-net`,
`-world`) release together on one version, per `CONTRIBUTING.md`.

**Cross-repo:**
- **`SpartanLaboratories/GeneralTools`** — propose a geometry-primitives enhancement (§10).
  Non-blocking; GameTools ships an internal fallback. This is an *upstream dependency*, so an
  issue is appropriate (per the global "surface issues" rule) — filed only on confirmation.
- **`SpartanLaboratories/WebTools`** — untouched in Phase 1.
- **`MyGameServer`** (consumer) — gains the option to build a `World` from a `TiledMap` and
  to run `WorldSystems`; nothing forces it to. **No issue filed** (downstream consumer —
  standing guidance, memory `no-downstream-consumer-issues`). CHANGELOG documents the new
  surface so it can adopt on its own schedule.
- **`GameGraphics` / the client project** — Phase 1 sends no new data over the wire. The
  only thing to communicate is the y-axis convention decision (Open Decision 7), in the
  CHANGELOG. Terrain / vision reach the client in Phase 3.

**Maven Central namespace** — this adds the **4th** coordinate,
`io.github.spartanlabsgaming:gametools-world`, to the 3 recorded in the auto-memory
`maven-central-namespace`. That memory is updated as part of the release.

---

## 8. Version-control approach

Per `CONTRIBUTING.md` (trunk-based, semi-linear, Conventional Commits, PR-per-change) and the
Phase 0 precedent.

1. **Release `5.1.0`** — done (PR #45, tag `v5.1.0`, 2026-09-07). Phase 1 branches off the
   resulting `master`.
2. **Phase 1 issues** (#46–#50) filed — done — so branches can carry the `<issue#>` prefix.
3. **5.2.0 series** (items 1–3), each a `feature/<issue#>-<slug>` branch → PR → semi-linear
   merge:
   - `feature/48-gametools-world-module` — the empty published module + umbrella wiring +
     `SpatialIndex` interface + `Space` port + `GameEvent` open-interface change. Green gate:
     `build`, `dokkaGeneratePublicationHtml`, `:build-logic:integrationTest`. (Grouped under
     #48 as the module-bootstrap step; the interface it introduces is #48's.)
   - `feature/48-spatial-index-rework` — `UniformGrid`, `QuadtreeSpatialIndex`, incremental
     `World.tick()` reconcile, deprecated `quadtree`.
   - `feature/46-map-model` — `TiledMap` & friends, `MapDefinition`, `MapLoader`.
   - `feature/47-zones` — `ZoneGrid`, `ZoneIndex`, `EntityChangedZone`.
   - `release/5.2.0`.
4. **5.3.0 series** (items 4–5):
   - `feature/49-physics` — `PhysicsBody`, `PhysicsSystem`, swept check, `WorldSystems`.
   - `feature/50-vision-los` — `VisionSystem`, `VisionProfile`, `TeamVision`, vision events.
   - `release/5.3.0`.
5. Each feature PR updates `README.md` / `CHANGELOG.md` for the surface it adds (global
   README rule). Each release PR updates all 4 `coordinates(...)`, the CHANGELOG headings,
   `docs/framework-vision-and-roadmap.md`, and the `maven-central-namespace` memory.
6. **Publishing to Maven Central is Spartak's manual step** (`./gradlew
   publishAndReleaseToMavenCentral`) — never done automatically.

---

## 9. Open decisions

| ID | Decision | Notes / lean |
|----|----------|--------------|
| 1 | **Map data format.** Framework-native `@Serializable` `MapDefinition` only, or also a Tiled `.tmx` importer? | Lean: native JSON only in Phase 1; a `gametools-world-tmx` add-on later if a real content pipeline needs it. |
| 2 | **Default `World.spatialIndex`.** Keep `QuadtreeSpatialIndex` (exact `5.1.0` semantics) or flip to `UniformGrid` (better medium-scale) now? | Lean: keep `Quadtree` default in 5.2.0; flip in the Phase 2 Major. |
| 3 | **How per-frame world systems run.** A `WorldSystems.step()` a game calls next to `World.tick()`; a `SimulationLoop.onTick` hook; or `World` gaining an opt-in `systems` list it ticks itself. | Lean: explicit `WorldSystems.step()` — keeps `core` unaware of `world`, and matches "the loop is opt-in". Document the tick↔systems order like `World.tick()`'s order is documented. |
| 4 | **Physics ↔ `Movement` glue.** "Desired position diff" adapter (additive, slightly indirect) vs refactor `Movement.step` to return a delta the system reconciles (cleaner, breaking). | Lean: adapter in Phase 1; the `Movement` delta refactor rides the Phase 2 Major. |
| 5 | **`GameEvent` for cross-module events.** Open the `sealed interface` to a plain `interface` (like `ClientCommand`) so `world` declares its own events; or declare `EntityChangedZone` / `VisionEntered` / `VisionExited` in `core` as carriers `world` populates. | Lean: open the interface. Fallback is non-breaking if that proves too invasive for a consumer's exhaustive `when`. |
| 6 | **Collision shape.** Circles, AABBs, or both for `PhysicsBody`? | Lean: both — circle for units (cheap, rotation-free), AABB for structures; `StaticGeometry` is AABB-only in v1. Polygons later. |
| 7 | **y-axis convention (repo-wide).** y-up ("north = +y", per current `Quadtree`/`Movement` KDoc) vs y-down / screen coordinates (per the `vertical-placement-is-y-down` memory and the health-bar code). | Lean: **y-down**. It matches the health-bar math and the memory note; the fix is documentation + one `Quadtree` KDoc line, no coordinate math changes. Phase 1 aligns all docs and states it centrally (README Architecture + the new map docs). |
| 8 | **Geometry primitives — where.** Internal `world.geometry` forever, or push `Segment` / `Ray` / `Aabb` + intersections into `SpartanLaboratories/GeneralTools`? | Lean: file a GeneralTools enhancement; ship the internal helpers now; migrate when it lands. §10. |
| 9 | **Team model depth (roadmap Open Decision G).** Phase 1 vision keys on the `Alive.faction` string; is a real `Team` / alliance type in Phase 1 scope or Phase 2 combat scope? | Lean: `faction` string for Phase 1 vision; the `Team` type (alliances, friendly-fire, shared vision as first-class) is Phase 2. |
| 10 | **`SpawnPoint` ownership.** Does the map own spawn points (data), or does a game register them at runtime? | Lean: map owns them (they are map data); a game may add more at runtime through `TiledMap.addSpawnPoint`. |
| 11 | **Zone shape v1.** Uniform `M × N` grid only, or allow irregular zones from the `MapDefinition`? | Lean: uniform grid in Phase 1; `ZoneIndex` interface leaves room for irregular later. |
| 12 | **Release split.** One `5.2.0` for all five items, or `5.2.0` (1–3) + `5.3.0` (4–5)? | Lean: split — 4–5 are the heavy ones and depend on 1 + 3. |

---

## 10. GeneralTools geometry enhancement (proposed upstream issue)

**Repo:** `SpartanLaboratories/GeneralTools` (`com.spartanlabs.geometry`). **Gap:** the
package ships only `Point`, `Dimensions`, `Square`, `TwoDoubles` — no line segment, ray,
axis-aligned box intersection, or vector algebra (dot / cross / normalize / project).
GameTools Phase 1 physics (swept collision) and LOS (occlusion raycast) need all of these,
and they are geometry-generic, not game-specific.

**Proposed additions:** `Segment(a: Point, b: Point)`, `Ray(origin: Point, direction: Point)`,
an `Aabb` (or documented `Square` semantics — its KDoc currently says top-left while GameTools
uses centre-origin; that mismatch is worth resolving upstream too), and free functions:
`segmentIntersectsSegment`, `segmentIntersectsAabb`, `rayIntersectsAabb`, plus `Point` vector
ops (`dot`, `cross`, `length`, `normalized`, `projectedOnto`). All returning `Result` for
degenerate input, per the house style.

**Handling:** GameTools ships `com.spartanlabs.gaming.world.geometry` internal equivalents in
Phase 1 so nothing is blocked. If the upstream issue is accepted and released, a follow-up
GameTools change swaps the internals for the shared types (source-compatible — value types).
**File only on Spartak's confirmation** (global "surface issues" rule; this is an upstream
dependency, so an issue is appropriate).

---

## 11. Constants

- Five-level test tree per module (`com.spartanlabs.gaming.testing.<level>`), one class per
  file, external calls mocked at L2 — `.aiassistant/rules/CLAUDE.md` + `~/.claude/CLAUDE.md`.
- KDoc on every public declaration (`@param` / `@return` / `@throws`), `Result` for expected
  failures, structured slf4j logging bound to the facade, region-grouped imports.
- `README.md` and the module table in `CONTRIBUTING.md` updated with every phase that changes
  the external shape (global README rule).
- `docs/framework-vision-and-roadmap.md` kept in step (§2 architecture, §3 phase status).
- Bugs / gaps found in `WebTools` or `GeneralTools` during the phase are raised upstream
  before being worked around.
- This document is superseded by the per-item implementation as it lands; material design
  changes are folded back here until then.
