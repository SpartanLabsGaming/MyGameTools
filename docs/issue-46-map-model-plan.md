# Plan: Phase 1 bounded tiled map model (`TiledMap`, terrain, static geometry, spawn points)

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#46](https://github.com/SpartanLabsGaming/MyGameTools/issues/46)
  — *"Phase 1: bounded tiled map model (terrain, static geometry, spawn points)"*, filed by
  Spartak Singh, Phase 1 item 1 of 5.
- **Branch:** `feature/46-map-model`
- **Commit:** TBD
- **PR:** TBD — this plan document is committed together with the first commit of its
  implementation (§9) so `git log --follow` binds the two.
- **What this plans:** the map-model slice of `docs/phase-1-map-and-space-plan.md` §2.2 —
  `TiledMap`, `TerrainLayer`, `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`
  (+ its snapshot DTOs), and `MapLoader`, all new in `gametools-world`'s
  `com.spartanlabs.gaming.world.map` package; plus wiring `World.space: Space?` in
  `gametools-core`. Zones (#47), the spatial-index rework (already shipped, #48), physics
  (#49) and vision (#50) are **not** in scope here.
- **Status:** planning only. No source, test, or build file has been modified by this document.
- **Target version:** `5.2.0` (Feature release, unreleased) — the second of the three
  `feature/48-*` / `feature/46-map-model` / `feature/47-zones` branches that make up the
  `5.2.0` series per `docs/phase-1-map-and-space-plan.md` §8. No version bump happens on this
  branch; that happens once on the later `release/5.2.0` branch.
- **Related docs:** `docs/phase-1-map-and-space-plan.md` (the Phase 1 umbrella plan — §2.2,
  §3 file lists, §9 Open Decisions 1/6/7/8/10 for this item; treated as input here, verified
  against current `master` below, **not** edited by this document); `docs/framework-vision-and-roadmap.md`
  §3 Phase 1; `CONTRIBUTING.md` (module layout, branching, commit/versioning conventions).

---

## 1. Context

### 1.1 What's already landed vs. what #46 adds

Verified against `master` at `98e099a` (2026-09-13):

- **`gametools-world` module scaffold** — `gametools-world/build.gradle.kts` exists
  (`plugins { id("gametools.published-library") }`, `api(project(":gametools-core"))`,
  `coordinates(..., "5.1.0")`), `settings.gradle.kts` includes it, the umbrella re-exports it.
  Landed via PR #61 (issue #48's module-bootstrap branch). **It has no production Kotlin
  source yet** (`gametools-world/src/main/kotlin` is empty) — #46 is the first real content.
- **`Space` port** — `gametools-core/.../gameobjects/Space.kt` already exists exactly as the
  issue's sketch: `bounds: Square`, `contains(Point): Boolean`, `isWalkable(Point): Boolean`,
  with KDoc already stating *"a `World` with no `Space` simulates in the pre-Phase-1 unbounded
  plane."* Also landed via #48/PR #61.
- **`World.space` is NOT wired yet.** `gametools-core/.../gameobjects/World.kt` has no `space`
  property. The `[Unreleased]` `CHANGELOG.md` entry for #48 says so explicitly: *"`Space`
  (the bounded-playfield contract `World` will accept once the map model lands). ... Neither
  is wired into `World` yet."* **Wiring `var space: Space? = null` onto `World` is therefore
  in scope for #46**, per the issue body's own proposed API.
- **The spatial-index rework (#48) and its follow-up (#63/PR #64) are fully merged** —
  `SpatialIndex<E>`, `Quadtree`, `QuadtreeSpatialIndex`, `UniformGrid` all live in
  `gametools-core`'s `spatial` package; `World.spatialIndex` is pluggable and incrementally
  reconciled; `World.quadtree` is a deprecated delegating alias. None of this is touched here.
- **The y-axis inconsistency the issue describes as Open Decision 7 is already resolved.**
  Commit `6d50a20` (`fix(spatial): stop Quadtree.insert reusing dead slots at the wrong
  position`, part of the #48 work) rewrote `Quadtree`'s KDoc from *"north being the `+y`
  direction, matching the rest of [Movement]'s convention"* to: *"named as if `+y` were north -
  a labelling convention internal to the tree's own logic, independent of the caller's
  coordinate convention (the engine's own world space is **y-down**: `-y` is up)."*
  `Movement.kt` carries no north/south wording at all (grepped, no matches).
  `Alive.healthBar`'s `location.y - healthBarYOffset` (draws the bar *above* the actor) already
  matches y-down. **Conclusion: no source change is needed for the y-axis question by #46.**
  The map model's own new KDoc (§4 below) simply states the already-settled convention —
  *origin `(0, 0)`, `y` grows downward* — so it doesn't have to be independently rediscovered
  from a fresh set of classes.
- **The parent plan's Open Decision 8 (upstream geometry primitives) has already shipped,
  changing this item's design.** The `[Unreleased]` `CHANGELOG.md` `### Changed` entry records
  bumping `GeneralTools` `2.0.1` → `2.2.0`, adding `com.spartanlabs.geometry`'s `Segment`,
  `Ray`, `AxisAlignedBox`, `CenteredBox`, and `Point` vector algebra. Confirmed by decompiling
  `GeneralTools-2.2.0.jar` and reading the upstream source
  (`SpartanLaboratories/GeneralTools` `src/main/kotlin/geometry/{AxisAlignedBox,CenteredBox}.kt`
  on `master`): `AxisAlignedBox` is an interface (`min`, `max`, `center`, `size`, `contains`)
  implemented by both `Square` (top-left origin, pre-existing) and the new `CenteredBox`
  (`center: Point`, `halfExtents: Dimensions` — centre-origin, matching the engine's own
  collision convention documented on `VisibleObject.collidesWith`). **This resolves Open
  Decision 6 and 8 together: `StaticGeometry` uses GeneralTools' `CenteredBox` directly for its
  obstacle boxes.** No internal `world.geometry.Aabb` type, and no upstream issue to file — the
  gap the parent plan's §10 proposed is already closed.
- **No existing boundary/clamping mechanism to migrate.** Grepped `Movement.kt`, `Actor.kt`,
  and the rest of `gameobjects` for bound/clamp logic — none exists; a `Movement` strategy
  writes `actor.location` directly with no notion of a playfield edge today. There is therefore
  nothing pre-existing for `TiledMap`/`Space` to replace or for another class to be migrated
  onto in this item — `Space` is purely new, additive surface. (Blast-radius adoption check:
  the one place that *will* want to consume `isWalkable`/`StaticGeometry` — `Movement`, via
  collision resolution — is explicitly Phase 1 item 4 (physics, issue #49), not this item;
  wiring enforcement into movement here would be new behaviour on every existing `Actor`,
  contradicting the issue's "fully additive" requirement and acceptance criterion 2 below.)
- **Build wiring needs no changes.** `gametools-world/build.gradle.kts` already applies
  `gametools.published-library` → `gametools.kotlin-library`, which already supplies
  `kotlinx-serialization-json:1.7.3`, `GeneralTools:2.2.0`, `slf4j-api`, the JUnit5/kotlin-test
  test dependencies, and the five per-level test tasks (`componentTest` … `nonfunctionalTest`),
  scoped by `com.spartanlabs.gaming.testing.<level>.*` package matching. Adding
  `src/main/kotlin/.../world/map/*.kt` and
  `src/test/kotlin/com/spartanlabs/gaming/testing/<level>/world/map/*.kt` files is enough —
  no `build.gradle.kts` edit in either module.

### 1.2 Acceptance criteria (scoped to #46, drawn from the parent plan's §1.4 and the issue body)

1. `TiledMap : Space` — `bounds`, `contains`, `isWalkable`, `tileAt`, `terrainAt`,
   `spawnPoint(name)`, `addSpawnPoint` — all correct against a fixture map, KDoc'd per the
   Audience-Reach standard.
2. `World` constructed the pre-#46 way (`space` left `null`) ticks identically — every existing
   `gametools-core` / `gametools-net` test still passes unmodified, and a new test locks this
   down explicitly.
3. `MapDefinition` is a pure, `@Serializable` data payload; `MapLoader.fromJson` /
   `fromDefinition` build a `TiledMap` from it, returning `Result` for malformed input, per
   `.aiassistant/rules/CLAUDE.md` §2. No file IO in the library.
4. `MapDefinition` round-trips through `kotlinx-serialization-json` with structural identity
   (an actual behavioural test, not just "it compiles").
5. `README.md`, `CONTRIBUTING.md` (module table), and `CHANGELOG.md [Unreleased]` are updated
   for the new public surface, per the global README-currency rule.

---

## 2. Prior art & best practices

- **Flat tile array + a small ID→properties palette is the industry-standard tile-map shape**,
  not a house invention. Tiled's own TMX format stores each layer as a flat, row-major array of
  global tile IDs referencing a shared tileset/palette
  (<https://doc.mapeditor.org/en/stable/reference/tmx-map-format/> — `<layer>`/`<tile gid=...>`).
  `MapDefinition.tiles` (flat, row-major, indices into `terrainPalette`) follows this shape
  deliberately, which is also exactly what the issue's sketch and the parent plan already
  proposed — confirming rather than changing that part of the design.
- **A Kotlin `data class` with an `Array`/`IntArray` property silently breaks structural
  `equals()`/`hashCode()`** — arrays use reference equality, which a `data class`'s
  auto-generated methods do not override for array-typed properties
  (<https://proandroiddev.com/avoid-using-array-in-the-data-class-constructor-in-kotlin-ebc308e46a95>).
  This is well-known enough that JetBrains ships an IDE inspection with an auto-fix for it
  (`KT-15893`, <https://github.com/JetBrains/kotlin/pull/1135>). Acceptance criterion 4 above
  (a real round-trip **identity** test) would silently pass-by-accident or fail depending on
  object identity rather than content if `MapDefinition.tiles` were typed `IntArray` as the
  issue's prose literally says ("tiles as flat IntArray"). **Design decision:** type it
  `List<Int>` instead — still a flat, row-major sequence conceptually, but with correct
  structural equality out of the box. Tile counts at this project's stated medium target
  (§1 of `docs/framework-vision-and-roadmap.md`, ≤10k entities, not tile-count-bound) are in
  the tens of thousands at most for a reasonably sized RTS/MOBA map — boxed `Integer` overhead
  is immaterial next to the correctness win.
- **`Result`-typed decode + `require()`-validated construction is this repo's own established
  split**, not a new pattern to invent: `ClientCommandCodec.decode` wraps `Json.decodeFromString`
  in `runCatching { }` to produce `Result<ClientCommand>`
  (`gametools-net/.../command/ClientCommandCodec.kt:80-81`), while `CombinedStat`,
  `DirectionalProjectile`, and `SimulationLoop` all validate constructor preconditions with a
  bare `require()` (programmer-contract violation → `IllegalArgumentException`), not a
  `Result`-returning factory. §4 below follows the same split: `TiledMap`/`TerrainLayer`
  constructors `require()`; `MapLoader.fromJson`/`fromDefinition` are the `Result`-returning
  boundary.

---

## 3. Design

### 3.1 `gametools-core` — wiring the port (small, additive)

```kotlin
// World.kt — new property, no other change
/**
 * The bounded playfield this world simulates in, or `null` for the pre-Phase-1 unbounded
 * plane (every coordinate in bounds and walkable). Purely descriptive here: [tick] does not
 * consult [space] - no existing [add]/[tick] behaviour changes when this is set. A `gametools-world`
 * `TiledMap` is the standard implementation; a system that wants to enforce bounds or
 * walkability (movement, physics, pathfinding) queries [space] itself. `null` by default, so
 * an existing `World` behaves exactly as it did before this property existed.
 */
var space: Space? = null
```

No change to `tick()`, `add()`, `reconcileSpatialIndex()`, or any `Movement` strategy. This is
deliberately inert data, matching the issue's "fully additive" framing and acceptance criterion
2. Enforcing `isWalkable` against `Movement` output is Phase 1 item 4 (physics, #49) — see §1.1's
adoption note.

### 3.2 `gametools-world` — the map model

Package `com.spartanlabs.gaming.world.map`.

**`TerrainType`** — plain, immutable data:

```kotlin
data class TerrainType(
    val walkable: Boolean,
    val movementCost: Double,   // for Phase 4 pathfinding; defined now, unused here
    val heightLevel: Int,       // for Phase 1 item 5 LOS occlusion; defined now, unused here
    val blocksVision: Boolean,  // ditto
)
```

**`TileIndex`** — an integer grid coordinate, the tile-space analogue of `Point`:

```kotlin
data class TileIndex(val x: Int, val y: Int)
```

**`TerrainLayer`** — the `widthTiles × heightTiles` grid, as a flat, row-major list of palette
indices plus the palette itself:

```kotlin
class TerrainLayer(
    val widthTiles: Int,
    val heightTiles: Int,
    tileTypeIndices: List<Int>,   // flat, row-major: index = y * widthTiles + x
    palette: List<TerrainType>,
) {
    init {
        require(widthTiles > 0 && heightTiles > 0) { "widthTiles/heightTiles must be positive" }
        require(palette.isNotEmpty()) { "palette must not be empty" }
        require(tileTypeIndices.size == widthTiles * heightTiles) {
            "tileTypeIndices.size (${tileTypeIndices.size}) must equal widthTiles*heightTiles (${widthTiles * heightTiles})"
        }
        tileTypeIndices.forEachIndexed { i, p ->
            require(p in palette.indices) { "tileTypeIndices[$i] = $p is out of range for a palette of ${palette.size}" }
        }
    }

    private val tileTypeIndices = tileTypeIndices.toList()
    private val palette = palette.toList()

    /**
     * The [TerrainType] at [tile], or [Result.failure] if [tile] falls outside the grid - an
     * out-of-range tile lookup is an operational condition a caller (pathfinding, an LOS
     * raycast walking off the grid edge) can hit routinely, not a programmer error.
     */
    fun terrainAt(tile: TileIndex): Result<TerrainType> =
        if (tile.x !in 0 until widthTiles || tile.y !in 0 until heightTiles)
            Result.failure(IndexOutOfBoundsException("$tile is outside a ${widthTiles}x$heightTiles grid"))
        else Result.success(palette[tileTypeIndices[tile.y * widthTiles + tile.x]])
}
```

**`StaticGeometry`** — a list of `CenteredBox` obstacles (GeneralTools 2.2.0), AABB-only in v1
per Open Decision 6; polygons are a later addition behind the same `blocksPoint` query:

```kotlin
class StaticGeometry(val obstacles: List<CenteredBox>) {
    /** Whether [point] falls inside any obstacle. */
    fun blocksPoint(point: Point): Boolean = obstacles.any { it.contains(point) }
}
```

**`SpawnPoint`**:

```kotlin
data class SpawnPoint(
    val name: String,
    val position: Point,
    val facing: Int? = null,   // degrees, same convention as VisibleObject.angle, when set
    val team: String? = null,
)
```

**`TiledMap`** — the `Space` implementation, matching the issue's sketch exactly for its public
shape, with the `require()`/nullable-query split from §2:

```kotlin
class TiledMap(
    val widthTiles: Int,
    val heightTiles: Int,
    val tileSize: Double,
    val terrain: TerrainLayer,
    val staticGeometry: StaticGeometry,
    spawnPoints: List<SpawnPoint> = emptyList(),
) : Space {
    init {
        require(widthTiles > 0 && heightTiles > 0 && tileSize > 0.0) { "widthTiles/heightTiles/tileSize must be positive" }
        require(terrain.widthTiles == widthTiles && terrain.heightTiles == heightTiles) {
            "terrain's ${terrain.widthTiles}x${terrain.heightTiles} grid does not match the map's ${widthTiles}x$heightTiles"
        }
        val duplicate = spawnPoints.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicate.isEmpty()) { "duplicate spawn point name(s): $duplicate" }
    }

    override val bounds: Square = Square(Point(0.0, 0.0), Dimensions(widthTiles * tileSize, heightTiles * tileSize))

    override fun contains(point: Point): Boolean = bounds.contains(point)

    /** Floors [point] to its tile coordinate. Always succeeds - may return an index outside the grid for an out-of-bounds [point]; use [terrainAt] / [contains] to test that. */
    fun tileAt(point: Point): TileIndex = TileIndex(floor(point.x / tileSize).toInt(), floor(point.y / tileSize).toInt())

    /** The terrain at [point], or `null` if [point] is outside the grid - a normal, frequent query result near a map edge, not treated as a failure (mirrors [Space.isWalkable]'s own "outside bounds" handling). */
    fun terrainAt(point: Point): TerrainType? = terrain.terrainAt(tileAt(point)).getOrNull()

    override fun isWalkable(point: Point): Boolean =
        contains(point) && terrainAt(point)?.walkable == true && !staticGeometry.blocksPoint(point)

    private val spawnPointsByName: MutableMap<String, SpawnPoint> = spawnPoints.associateByTo(LinkedHashMap()) { it.name }

    /** The named spawn point, or `null` if no spawn point of that name exists. */
    fun spawnPoint(name: String): SpawnPoint? = spawnPointsByName[name]

    /** Registers an additional spawn point at runtime (Open Decision 10: the map owns the ones it was built with; a game may add more). */
    fun addSpawnPoint(spawn: SpawnPoint) {
        require(spawn.name !in spawnPointsByName) { "a spawn point named '${spawn.name}' already exists" }
        spawnPointsByName[spawn.name] = spawn
    }
}
```

Why `terrainAt(Point): TerrainType?` (nullable) rather than the issue sketch's literal
`TerrainType` return type: an out-of-bounds query is exactly the kind of "not found" outcome
Kotlin idiom and this codebase's own precedent (`World.byId(id): GameObject?`) model as
nullable, not as a thrown exception or a `Result` the caller has to unwrap on every per-tick
query. `TerrainLayer.terrainAt(TileIndex)` — the lower-level, grid-only primitive with no
notion of "a point can legitimately be off the map" — is the one that returns `Result`,
matching the parent plan's own test expectation ("out-of-range tile access is a Result failure
not a throw"). `TiledMap.terrainAt` composes the two: convert the grid-level `Result.failure`
into the map-level `null`.

**`MapDefinition`** and its snapshot DTOs (mirrors the existing
`com.spartanlabs.geometry.serializations.PointSnapshot`/`DimensionsSnapshot` pattern, which
`gametools-world` can reuse directly since it depends on `gametools-core`):

```kotlin
@Serializable
data class TerrainTypeSnapshot(val walkable: Boolean, val movementCost: Double, val heightLevel: Int, val blocksVision: Boolean)

@Serializable
data class ObstacleSnapshot(val center: PointSnapshot, val halfExtents: DimensionsSnapshot)

@Serializable
data class SpawnPointSnapshot(val name: String, val position: PointSnapshot, val facing: Int? = null, val team: String? = null)

/**
 * The pure-data, wire/file-shape description of a [TiledMap]. [tiles] is flat and row-major
 * (`index = y * widthTiles + x`, matching Tiled's own TMX layer convention), each entry an
 * index into [terrainPalette]. No file IO here - a consumer reads the file/asset and hands the
 * JSON text (or a decoded [MapDefinition]) to [MapLoader].
 */
@Serializable
data class MapDefinition(
    val widthTiles: Int,
    val heightTiles: Int,
    val tileSize: Double,
    val tiles: List<Int>,
    val terrainPalette: List<TerrainTypeSnapshot>,
    val obstacles: List<ObstacleSnapshot> = emptyList(),
    val spawnPoints: List<SpawnPointSnapshot> = emptyList(),
)
```

**`MapLoader`**:

```kotlin
object MapLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /** Decodes [source] as a [MapDefinition] and builds the [TiledMap], or [Result.failure] if either step fails. */
    fun fromJson(source: String): Result<TiledMap> =
        runCatching { json.decodeFromString(MapDefinition.serializer(), source) }
            .mapCatching(::buildTiledMap)

    /** Builds the [TiledMap] [definition] describes, or [Result.failure] if it is structurally invalid. */
    fun fromDefinition(definition: MapDefinition): Result<TiledMap> = runCatching { buildTiledMap(definition) }

    private fun buildTiledMap(definition: MapDefinition): TiledMap = with(definition) {
        TiledMap(
            widthTiles, heightTiles, tileSize,
            terrain = TerrainLayer(widthTiles, heightTiles, tiles, terrainPalette.map { it.toDomain() }),
            staticGeometry = StaticGeometry(obstacles.map { it.toDomain() }),
            spawnPoints = spawnPoints.map { it.toDomain() },
        )
    }
}
```

(`toDomain()` extension functions on the three snapshot DTOs, named to match GeneralTools' own
`Square.toCenteredBox()` / `AxisAlignedBox.toSquare()` convention — each constructs fresh
`Point`/`Dimensions` instances rather than aliasing a shared one, since both are mutable value
types upstream.)

### 3.3 Rejected alternatives

- **Internal `world.geometry.Aabb` type** (the parent plan's original §2.2/§10 shape) — rejected
  now that GeneralTools 2.2.0 ships `CenteredBox`/`AxisAlignedBox` (§1.1); using them directly
  avoids a throwaway type and the later migration the parent plan flagged as a risk.
- **`MapDefinition.tiles: IntArray`** — rejected per §2's data-class/array pitfall; `List<Int>`.
- **Enforcing `isWalkable` inside `World.add`/`tick` now** — rejected; out of scope for the map
  model itself (no behaviour change on an existing `World`), belongs to physics (#49).
- **A Tiled `.tmx` importer in this module** — rejected for v1 per the parent plan's Open
  Decision 1 lean; `MapDefinition` is the only supported format. A `.tmx` importer, if ever
  wanted, is a separate add-on module later.

---

## 4. File-by-file changes

### `gametools-core`

- **modified** `gameobjects/World.kt` — add `var space: Space? = null` (§3.1). Update the
  class-level KDoc's opening sentence (currently lists `gameObjects`, `spatialIndex`, `byId`,
  `events`, `rng`) to also name `space`. No change to `tick()`, `add()`, or any other member.

### `gametools-world` (all new)

- **new** `world/map/TerrainType.kt` — `TerrainType` data class.
- **new** `world/map/TileIndex.kt` — `TileIndex` data class.
- **new** `world/map/TerrainLayer.kt` — `TerrainLayer` class (§3.2).
- **new** `world/map/StaticGeometry.kt` — `StaticGeometry` class, `import com.spartanlabs.geometry.CenteredBox`.
- **new** `world/map/SpawnPoint.kt` — `SpawnPoint` data class.
- **new** `world/map/TiledMap.kt` — `TiledMap` class implementing `com.spartanlabs.gaming.gameobjects.Space`.
- **new** `world/map/MapDefinition.kt` — `MapDefinition`, `TerrainTypeSnapshot`, `ObstacleSnapshot`,
  `SpawnPointSnapshot`, and their `toDomain()` extension functions.
- **new** `world/map/MapLoader.kt` — `MapLoader` object.
- **new** `src/test/kotlin/com/spartanlabs/gaming/testing/component/world/map/` —
  `TiledMapTest.kt`, `TerrainLayerTest.kt`, `StaticGeometryTest.kt`.
- **new** `src/test/kotlin/com/spartanlabs/gaming/testing/integration/world/map/MapLoaderIntegrationTest.kt`.
- **new** `src/test/kotlin/com/spartanlabs/gaming/testing/deterministic/world/map/` —
  `MapDefinitionRoundTripTest.kt`, `TiledMapQueryLawsTest.kt`.
- **new** `src/test/kotlin/com/spartanlabs/gaming/testing/e2e/world/map/MapDrivenWorldE2ETest.kt`.
- **new** `src/test/resources/fixture-map.json` — the fixture `MapDefinition` JSON the
  integration/e2e tests load.

### `gametools-core` tests

- **new** `src/test/kotlin/com/spartanlabs/gaming/testing/component/gameobjects/WorldSpaceTest.kt`
  — `World.space` defaults to `null`; assigning a `Space` is retained by the getter; a `World`
  with `space` set still ticks/adds exactly as one with `space == null` (regression guard for
  acceptance criterion 2).

### Root & meta

- **modified** `README.md` — Modules table's **world** row: replace "no public types yet" with
  a summary of `com.spartanlabs.gaming.world.map`'s new types. Architecture Mermaid diagram:
  add `TiledMap ..|> Space`, `Space` as a small interface node (`bounds`, `contains`,
  `isWalkable`), and `World "1" o-- "0..1" Space`. A short new "🗺️ Map & Space" bullet under
  Features, stating the origin/y-down convention centrally (per the parent plan's Open
  Decision 7 resolution) and the "no file IO" `MapLoader` contract.
- **modified** `CONTRIBUTING.md` — the `gametools-world` module-table row: replace "no public
  types yet" with the same short summary.
- **modified** `CHANGELOG.md` — `[Unreleased]` → `### Added`: `TiledMap`, `TerrainLayer`,
  `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` in
  `gametools-world`; `World.space` in `gametools-core`. (#46)
- **no change** — `docs/framework-vision-and-roadmap.md` (no architectural fact this item
  changes; the roadmap's §2.1 note about the spatial index already reflects #48's landing).
  **No change** to any `build.gradle.kts` (§1.1) or `settings.gradle.kts`.

---

## 5. Documentation impact (Audience-Reach rings)

- **Inner Core** — `//region`/`//endregion` import grouping in every new file, per
  `.aiassistant/rules/CLAUDE.md` §6. No `TODO`s: the v1 scope (AABB-only geometry, no `.tmx`
  importer) is a deliberate, documented boundary, not unfinished work.
- **Component ring (KDoc)** — this item's primary deliverable. Every public class/function in
  `world/map/*` gets full KDoc (`@param`/`@return` where non-obvious), matching the style
  already used in `Space.kt`/`PointSnapshot.kt`/`World.kt`. `World.space`'s KDoc explicitly
  states it is inert until a system (physics, #49) chooses to consult it.
- **Boundary ring** — `MapDefinition`'s KDoc is the de-facto file-format contract a level
  author or tool authors JSON against: document the row-major flatten order, that
  `terrainPalette`/`obstacles`/`spawnPoints` indices/positions are 0-based and in `tileSize`
  units vs. world units respectively, and that `MapLoader` does no file IO.
- **Architectural outer layer** — README Architecture diagram + Modules table, CONTRIBUTING
  module table, CHANGELOG — all listed in §4. This is `gametools-world`'s first real content,
  so it's also the first time the module's "Contains" column in both tables reflects actual
  types rather than "no public types yet."

---

## 6. Test plan (5-level hierarchy)

Per `.aiassistant/rules/CLAUDE.md` §4 and the global testing note: one test class per file,
package mirrors the production package under `com.spartanlabs.gaming.testing.<level>`.

**Level 2 — component** (`testing.component.world.map`, `gametools-world`; mock nothing —
these are pure in-memory domain objects with no external calls to mock)
- `TiledMapTest` — `bounds` matches `widthTiles/heightTiles/tileSize`; `tileAt` for interior,
  edge, and out-of-bounds points (including negative coordinates); `isWalkable` for: inside
  bounds + walkable terrain + no obstacle (true); outside bounds (false); non-walkable terrain
  (false); inside a `StaticGeometry` obstacle (false); `terrainAt` in/out of bounds
  (value / `null`); `spawnPoint(name)` hit/miss; `addSpawnPoint` adds a new one and rejects a
  duplicate name (`IllegalArgumentException`); constructor `require()` violations (mismatched
  terrain grid size, non-positive dimensions, duplicate spawn names at construction time).
- `TerrainLayerTest` — palette lookup by `TileIndex`; `terrainAt` returns `Result.failure` (not
  a thrown exception) for a `TileIndex` outside the grid on every edge (negative, `== width`,
  `== height`); constructor `require()` violations (size mismatch, out-of-range palette index,
  empty palette, non-positive dimensions).
- `StaticGeometryTest` — `blocksPoint` for a point inside/outside/exactly on the edge of a
  single obstacle, across multiple obstacles, and with an empty obstacle list (never blocks).

**`gametools-core`, `testing.component.gameobjects`**
- `WorldSpaceTest` — `space` defaults to `null`; assignment is retained; `tick()`/`add()`
  produce identical `gameObjects`/`spatialIndex`/`byId` state whether `space` is `null` or set
  to a stub `Space` (acceptance criterion 2).

**Level 3 — integration** (`testing.integration.world.map`; real file-format round trip, no
sockets)
- `MapLoaderIntegrationTest` — loads `src/test/resources/fixture-map.json` (a small hand-authored
  map: a few terrain types, one obstacle, two named spawn points) through `MapLoader.fromJson`,
  asserts the resulting `TiledMap`'s `bounds`/`isWalkable`/`terrainAt`/`spawnPoint` match the
  fixture's known values; a malformed-JSON string and a structurally-invalid `MapDefinition`
  (tile/palette index out of range) both return `Result.failure`, not a thrown exception.

**Level 4a — deterministic** (`testing.deterministic.world.map`)
- `MapDefinitionRoundTripTest` — `MapDefinition` → JSON (`kotlinx-serialization-json`) → decoded
  `MapDefinition` is `equals()`-identical to the original, across a handful of fixture shapes
  including one with obstacles and spawn points — the test this plan's `List<Int>` choice (§2)
  exists to make meaningful.
- `TiledMapQueryLawsTest` — for a fixture map, `tileAt`/`terrainAt`/`isWalkable` agree with a
  naive brute-force reference computation (direct index arithmetic, no `TiledMap` internals)
  over a seeded, randomized sample of points spanning inside, outside, and exactly on
  tile/obstacle boundaries — same "queried structure agrees with a linear-scan oracle" shape as
  the existing `SpatialIndexQueryLawsTest`.

**Level 4b — e2e** (`testing.e2e.world.map`)
- `MapDrivenWorldE2ETest` — `MapLoader.fromJson` the fixture, assign the result to a real
  `World.space`, add a couple of `Actor`s at named spawn points, run several `World.tick()`s
  (no `SimulationLoop`, no `GameServer` — this item has no wire or loop change), assert the
  world ticks normally and `world.space` still answers queries correctly afterward. This is the
  "the whole new surface actually composes with `World`" smoke test; it deliberately does
  **not** assert any movement-blocked-by-`isWalkable` behaviour, since that enforcement doesn't
  exist until #49.

**Level 4c — nonfunctional:** none for this item. `TerrainLayer.terrainAt` is an O(1) flat-list
index; `StaticGeometry.blocksPoint` is O(obstacle count), expected to be small (tens, not
thousands) for a hand-authored map. There is no per-tick hot path in scope here to benchmark —
the existing `SpatialIndexScalabilityTest` (#48) already covers the one that exists today, and
Phase 1 item 4's `PhysicsThroughputTest` (#49) is where `TiledMap` queries first run at
simulation scale.

**Level 5 — UAT:** not applicable; no user-facing surface (library code consumed by another
program).

---

## 7. Risks & edge cases

| Risk | Mitigation |
|---|---|
| A consumer assumes setting `World.space` automatically blocks out-of-bounds movement | `World.space`'s KDoc says explicitly it's inert until a system consults it; `MapDrivenWorldE2ETest` deliberately does not assert enforcement, so a future regression can't silently start relying on movement being blocked before #49 actually implements it. |
| `MapDefinition.tiles` typed `IntArray` (as the issue's prose literally says) would break the round-trip identity test via reference-equality `equals()` | Typed `List<Int>` instead (§2, §3.3), with a citation-backed rationale so a reviewer doesn't "fix" it back to `IntArray`. |
| `Point`/`Dimensions` are mutable value types (GeneralTools) | Every domain object built from a snapshot DTO in `MapLoader` constructs fresh `Point`/`Dimensions` instances; the design explicitly calls this out (§3.2) so aliasing doesn't sneak in when a `toDomain()` extension is implemented. |
| Floor-division edge cases at exact tile boundaries (`point.x` exactly a multiple of `tileSize`) | Covered by `TiledMapQueryLawsTest`'s boundary sampling and `TiledMapTest`'s explicit edge cases. |
| `gametools-world`'s only two published types so far (`SpatialIndex`, `Space` — both in `core`) meant zero real API-surface review has happened yet for this module; this is the first Dokka-published content | `dokkaGeneratePublicationHtml` is part of the standard green-gate build; run it before opening the PR to catch broken KDoc links. |
| Cross-repo | None. No wire-protocol change (`DrawableSnapshot`/`ClientCommand`/`GameServer` untouched). `MyGameServer` / the client project gain an *optional* surface to adopt on their own schedule (no forced change, per the issue body); terrain reaches the client only in Phase 3. `GeneralTools` needs no further change — the geometry primitives this item needs already shipped in `2.2.0`. |
| Breaking changes | None. `World.space` is a new, nullable, default-`null` property; `gametools-world` is a brand-new module nothing depends on yet. |

---

## 8. Version control

Per `CONTRIBUTING.md` (trunk-based, semi-linear merge, Conventional Commits, PR-per-change).

- **Branch:** `feature/46-map-model`, off the latest `master`.
- **Commit sequence** (rebased into coherent units before the PR is opened; the first commit
  carries this plan document, binding doc and implementation per the Association requirement):
  1. `feat(world): add the map domain model (TiledMap, TerrainLayer, TerrainType, StaticGeometry, SpawnPoint)` —
     `docs/issue-46-map-model-plan.md` (this file) + all of §4's new `world/map/*.kt` domain
     types (not yet `MapDefinition`/`MapLoader`) + their Level 2 component tests.
  2. `feat(core): wire World.space to the Space port` — `World.kt` change + `WorldSpaceTest`.
  3. `feat(world): load a TiledMap from a MapDefinition` — `MapDefinition.kt`, `MapLoader.kt`,
     the fixture JSON, and the Level 3/4a/4b tests.
  4. `docs: update README, CONTRIBUTING and CHANGELOG for the map model` — §4's doc changes.
- **PR:** title must itself be a valid Conventional Commit subject (it becomes the merge-commit
  subject) — e.g. `feat(world): bounded tiled map model (#46)`. Body: `Closes #46`. Green gate:
  `./gradlew build dokkaGeneratePublicationHtml`.
- **Trailer convention reminder:** this session's commits end with the attribution footer
  given in the system reminder, per the standing convention; no other trailer is required by
  `CONTRIBUTING.md` beyond the `Closes #46` reference in the PR body (issue references belong
  in the PR/commit body, not as a special footer key).
- Do **not** bump any `coordinates(...)` version on this branch — that happens once, later, on
  `release/5.2.0` alongside #47's landing.

---

## 9. Open decisions

None block execution. Every open decision the parent plan flagged as relevant to this item is
already resolved, either by repo state already on `master` or by direct correspondence to the
issue's own proposed API:

| Parent-plan ID | Resolution |
|---|---|
| 1 — map data format | Native `MapDefinition` JSON only in this item, per the parent plan's own lean; no `.tmx` importer. |
| 6 — static-geometry shape | AABB-only v1, using GeneralTools' `CenteredBox` (§1.1, §3.2) — resolved concretely now that `CenteredBox` exists upstream. |
| 7 — y-axis convention | Already resolved on `master` (commit `6d50a20`, part of #48) — y-down, origin `(0,0)`; this item's new docs state it, no source change needed (§1.1). |
| 8 — geometry primitives, internal vs. upstream | Already resolved — GeneralTools `2.2.0` (already a dependency) ships `Segment`/`Ray`/`AxisAlignedBox`/`CenteredBox`; no upstream issue to file for this item's needs. |
| 10 — spawn-point ownership | Map owns them as constructor data; `addSpawnPoint` allows runtime additions — matches the issue's own sketch exactly (§3.2). |

---

## 10. Sequencing & follow-ups

- Lands after #48 (already merged) and before #47 (zones), matching
  `docs/phase-1-map-and-space-plan.md` §8's ordering; nothing here blocks or is blocked by #47
  beyond both needing to land before `release/5.2.0`.
- Deliberately deferred, not part of this item: enforcing `isWalkable`/`StaticGeometry` against
  `Movement` output (physics, #49); consuming `TerrainType.heightLevel`/`blocksVision` for
  occlusion (vision, #50); partitioning a `TiledMap` into zones (#47); a `.tmx`/external-format
  importer (no issue yet — only if a real content pipeline later needs it).
- `release/5.2.0` (after #46 and #47 both land): bump all four `coordinates(...)`, move the
  `CHANGELOG.md [Unreleased]` entries under `[5.2.0]`, per `CONTRIBUTING.md`'s Releasing
  section — out of scope for this document, covered by the parent plan's §8.
