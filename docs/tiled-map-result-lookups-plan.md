# Plan: `tiled-map-result-lookups` — Result lookups: `TiledMap` + `ZoneGrid.zoneAt`

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#77](https://github.com/SpartanLabsGaming/MyGameTools/issues/77)
  — the user's design decisions of 2026-10-04 and 2026-10-05 (architecture C23–C34). Three lookups that can miss now
  fail with stackless, dedicated exceptions:
  - `TiledMap.spawnPoint(name)` returns `Result<SpawnPoint>`, its miss a `MissingSpawnPointException`.
  - `TiledMap.terrainAt(point)` returns `Result<TerrainType>`, its miss the `OutOfGridException` that
    `TerrainLayer.terrainAt(tile)` now raises and `TiledMap` passes through.
  - `ZoneGrid.zoneAt(point, clamped = false)` fails with an `UnzonedPointException`.
  
  The user judged this a small decision rather than an issue of its own, so it is recorded as part of
  #77's design and lands in #77's one PR (C27, C31). It reworks parts of #46's and #47's merged,
  unreleased map and zone models.
- **Architecture:** `docs/world-system-binding-architecture.md`, unit `tiled-map-result-lookups`
  (§10's third row; §1.2 C23–C34; §4.6; §7.5–§7.6; §8; §11.1; §13). Siblings:
  `docs/world-system-binding-plan.md` (unit 1) and `docs/zone-world-system-plan.md` (unit 2). Both
  land first, and this unit changes neither their content nor their commit order (C27, C31).
- **Branch:** `feature/77-zone-world-system` (units 1 and 2's commits underneath).
- **Commit:** TBD — this plan is committed in this unit's first commit (§8), so `git log --follow`
  binds it to the implementation.
- **PR:** the single #77 PR (`Closes #77`, `Part of #87`).
- **What this plans:** `gametools-world` only:
  - in the map package, the two `TiledMap` lookups and `TerrainLayer.terrainAt`, with
    `MissingSpawnPointException` and `OutOfGridException`;
  - in the zone package, `ZoneGrid.zoneAt` with `UnzonedPointException`;
  - `isWalkable` re-expressed with `fold`, answering exactly as before;
  - every call site in main and test source, two of them in test files unit 2 owns;
  - the README / CONTRIBUTING / CHANGELOG map and zone entries, including the missing `TileIndex`
    (C32);
  - two nonfunctional guards on the miss paths.
- **Status:** planning only. **No open decisions remain.** The last one, OD11 — whether
  `UnzonedPointException` also copies its point when the point is read — was answered on 2026-10-05:
  (a), one copy at construction, which every read returns (architecture C34, §13.1). This plan was
  already written for (a), so nothing in it changes; §9 records the answer. No source, test, or build
  file has been modified by this document. References are by section for documents and by symbol,
  region or test name for code — never by line number (C33).
- **Target release:** none. #46 and #47 are unreleased (neither is in `v5.1.0`), and `5.2.0` is not
  cut before #77 merges (C12). Changing these lookups therefore carries no semver weight. **But** the
  map package and `ZoneGrid` are untagged surface. The three new exception types become Stable Core in
  the first release that contains #46 (the two map types) or #47 (`UnzonedPointException`), and their
  shapes are frozen there.

---

## 1. Context

### 1.1 The requirement (architecture §1.2 — binding)

- **C23 (standing convention):** always prefer `Result<T>` over a nullable `T?` for lookups and reads
  that can miss.
- **C24:** `TiledMap.spawnPoint(name)` returns `Result<SpawnPoint>`. A miss is
  `Result.failure(MissingSpawnPointException(name))`, a stackless `NoSuchElementException` carrying
  `val name: String`.
- **C25, C28, C29:** `TiledMap.terrainAt(point)` returns `Result<TerrainType>`. An off-grid miss
  carries `OutOfGridException`, a stackless `IndexOutOfBoundsException` carrying `val tile: TileIndex`.
  Its constructor is `(tile)` only, and its message is built inside the type
  (`tile (4, 3) is outside the terrain grid`).
- **C26, C28:** `TerrainLayer.terrainAt(tile)` keeps returning `Result<TerrainType>`, but its miss
  switches from a stack-traced `IndexOutOfBoundsException` to the same `OutOfGridException`.
  `TiledMap.terrainAt(point)` stays a pure pass-through of that failure.
- **C30, C34:** `ZoneGrid.zoneAt(point, clamped = false)` fails with `UnzonedPointException` instead
  of a stack-traced `IndexOutOfBoundsException`. The type is:
  - `final` and stackless, extending `IndexOutOfBoundsException`;
  - built with a public constructor `(point)` only, the message built inside the type, no cause;
  - carrying `val point: Point`, defensively copied because `Point` is mutable — once, when the
    exception is built, with every read returning that same copy (C34);
  - untagged, matching `ZoneGrid`.
- **C27, C31:** all of this lands in #77's one PR as unit 3, "Result lookups: `TiledMap` +
  `ZoneGrid.zoneAt`", after unit 2. Units 1 and 2 keep their content and commit order. Unit 2 is
  implemented and green, and this unit is additive and lands last.
- **C32:** this unit's docs commit adds the missing `TileIndex` to the README and CONTRIBUTING map
  lists.
- **C33 (standing documentation rule):** cite documents by section and code by symbol or region,
  never by line number.

**Acceptance criteria.**

1. `map.spawnPoint(name)` is `Result.success(spawn)` for a registered name — one the map was built
   with or one added by `addSpawnPoint` — and otherwise `Result.failure` carrying a
   `MissingSpawnPointException` whose `name` is the queried name.
2. `map.terrainAt(point)` is `Result.success(terrain)` when `point`'s tile is in the grid, and
   otherwise `Result.failure` carrying an `OutOfGridException` whose `tile` is `map.tileAt(point)`: the
   failure `map.terrain.terrainAt(map.tileAt(point))` returns, passed through unchanged.
3. `TerrainLayer.terrainAt(tile)` misses with an `OutOfGridException` for `tile`, which is still an
   `IndexOutOfBoundsException`, so a handler for that type keeps working.
4. `grid.zoneAt(point, clamped = false)` for a point outside the grid is `Result.failure` carrying an
   `UnzonedPointException` whose `point` equals the queried point but is a distinct copy. Mutating the
   caller's point afterwards changes neither the exception's point nor its message. With
   `clamped = true`, `zoneAt` is unchanged.
5. No lookup throws or returns `null`, and none of the three exceptions carries a stack trace.
6. `map.isWalkable(point)` answers exactly as before for every point (the level-4a oracle test proves
   it), with no allocation on the in-grid path beyond what `tileAt` already makes.
7. `ZoneIndex.step` behaves exactly as before: its `getOrNull()` never inspects the failure type, and
   unit 2's `ZoneIndex` tests stay green unchanged.

### 1.2 What is in the tree (verified 2026-10-04)

The map package is as at `HEAD` `b5e57b0`. The zone package is as unit 2 implemented it in the working
tree.

- **`TiledMap`:**
  - `terrainAt(point): TerrainType? = terrain.terrainAt(tileAt(point)).getOrNull()`, whose KDoc says
    "or `null` if `point` is outside the grid … not treated as a failure".
  - `isWalkable(point) = contains(point) && terrainAt(point)?.walkable == true && !staticGeometry.blocksPoint(point)`.
  - `spawnPoint(name): SpawnPoint? = spawnPointsByName[name]`.
  - `tileAt`'s KDoc: "Always succeeds - may return an index outside the grid for an out-of-bounds
    [point]".
  - `addSpawnPoint` rejects a duplicate name with `IllegalArgumentException("a spawn point named '…' already exists")`.
- **`TerrainLayer.terrainAt(tile): Result<TerrainType>`** fails with
  `IndexOutOfBoundsException("$tile is outside a ${widthTiles}x$heightTiles grid")`, a full stack
  trace per miss.
- **`ZoneGrid.zoneAt(point, clamped)`**: its `clamped = false` branch fails with
  `IndexOutOfBoundsException("$point is outside a ${columns}x$rows zone grid")`. Its KDoc `@return`
  says "a [Result.failure] wrapping an [IndexOutOfBoundsException]"; its `@param clamped` (reworded by
  unit 2) names "the contract [ZoneIndex.step] relies on".
- **`ZoneIndex.step`** is `zoneAt`'s only main-source caller. It calls
  `grid.zoneAt(obj.location, clamped = false).getOrNull()` once per entity per step and **never
  inspects the exception type**. So every entity outside the grid's extent costs a stack walk per
  frame today.
- **Types:**
  - `TileIndex` is an immutable `data class TileIndex(val x: Int, val y: Int)`, and `SpawnPoint` is a
    data class.
  - `com.spartanlabs.geometry.Point` (GeneralTools 2.2.0) is a **mutable** final class (`setX`, `setY`,
    `setTo`) with value equality, verified with `javap`.
  - `Point.toString()` prints `1.5, -2.0`, without parentheses (verified by running it).
- **Tier: untagged.** No declaration in `com.spartanlabs.gaming.world.map`, and not `ZoneGrid`,
  carries `@ExperimentalGameToolsApi` or `@SupportedExtension` (verified by search). The three new
  exception types are untagged too: they are part of untagged methods' contracts. They are **not** in
  #79's removal list.
- **Main-source callers.** `TiledMap.isWalkable` calls `terrainAt`; nothing calls `spawnPoint`;
  `ZoneIndex.step` calls `zoneAt`. `gametools-core`, `gametools-net`, the umbrella and `website/` call
  none of them (verified by search).
- **`isWalkable`'s hot path.** `contains(point)` runs first.
  - It is inclusive on both edges: `Square.contains` resolves to `AxisAlignedBox.contains`, which tests
    `x >= min.x && x <= max.x` (and likewise on `y`), verified by `javap -c`. So an out-of-bounds point
    never reaches `terrainAt`.
  - A point exactly on a far edge does reach it: `contains` is `true`, but `tileAt` floors to the next
    tile, off the grid. `TiledMapTest`'s `tileAt floors interior, edge and out-of-bounds points,
    including negative coordinates` locks the flooring.
- **No nonfunctional tests exist in `gametools-world` yet.** The build registers `nonfunctionalTest`
  for every module (package segment `testing.nonfunctional`), so this unit's two guards are the
  module's first.

**Call sites in tests**, all under `gametools-world/src/test/kotlin/com/spartanlabs/gaming/testing/`.
"Silent" means the site still compiles against the new `Result` and fails only at run time: in
`assertEquals(expected, actual)`, `T` infers to `Any`, and `assertNull` takes `Any?`. This was
compile- and run-verified on Kotlin 2.2.0 with a stand-in generic `eq(expected, actual)`.

| File (level) | Test and statement | Today | Under this unit |
|---|---|---|---|
| `component/world/map/TerrainLayerTest.kt` (2) | `terrainAt resolves each tile through the palette` | hits via `getOrThrow()` | unchanged |
| | `terrainAt fails with Result rather than throwing for an out-of-range tile on every edge` | miss: `assertIs<IndexOutOfBoundsException>` | still passes (subtype); strengthened (§6.2) |
| `component/world/map/TiledMapTest.kt` (2) | `terrainAt returns the tile's TerrainType in bounds and null out of bounds` — its two hits | `assertEquals(grass, map.terrainAt(…))` | **silent** |
| | the same test — its two misses | `assertNull(map.terrainAt(…))` | **silent** |
| | `spawnPoint resolves a registered name and misses on an unknown one` — its hit | `assertEquals(spawn, map.spawnPoint("start"))` | **silent** |
| | the same test — its miss | `assertNull(map.spawnPoint("unknown"))` | **silent** |
| | `addSpawnPoint registers a new spawn point and rejects a duplicate name` — its lookup | `assertEquals(extra, map.spawnPoint("extra"))` | **silent** |
| `deterministic/world/map/TiledMapQueryLawsTest.kt` (4a) | `assertQueryLawsHold`'s `terrainAt` assertion | `assertEquals(naiveTerrainAt(…), map.terrainAt(point))` | **silent** |
| `integration/world/map/MapLoaderIntegrationTest.kt` (3) | `fromJson builds a TiledMap whose bounds, isWalkable, terrainAt and spawnPoint match the fixture` — its `grass` / `water` lookups | `val grass = map.terrainAt(…); checkNotNull(grass); grass.walkable` | compile error |
| | the same test — its two `SpawnPoint` assertions | `assertEquals(SpawnPoint(…), map.spawnPoint(…))` | **silent** |
| `e2e/world/map/MapDrivenWorldE2ETest.kt` (4b) | `a TiledMap-backed World ticks normally and its space still answers queries correctly afterward` — its `redSpawn` / `blueSpawn` lookups | `val redSpawn = map.spawnPoint(…); checkNotNull(redSpawn); redSpawn.position` | compile error |
| | the same test — its closing lookup | `assertEquals(redSpawn, sameMap.spawnPoint("red-spawn"))` | **silent** |
| `e2e/world/zone/ZoneDrivenSimulationE2ETest.kt` (4b; unit 2's) | `a ZoneGrid over a loaded TiledMap tracks two spawned actors through several ticks` — its `redSpawn` / `blueSpawn` setup | `checkNotNull(map.spawnPoint(…))`, then `.position` | compile error |
| `e2e/world/zone/ZoneIndexSimulationLoopE2ETest.kt` (4b; unit 2's) | its spawn-point setup | as above | compile error |
| `component/world/zone/ZoneGridTest.kt` (2) | `zoneAt with clamped = false fails on the far edge and on a negative out-of-bounds point` | `assertIs<IndexOutOfBoundsException>` (×2) | still passes (subtype); tightened (§6.2) |
| `deterministic/world/zone/ZoneGridPartitionLawsTest.kt` (4a) | `zoneAt(clamped = false) succeeds iff the point is within bounds, and the resolved zone contains the point` | reads `isSuccess` / `getOrNull()` | unaffected; gains a payload law (§6.4) |

So the table has sixteen rows: twelve in the five map test files (all eight silent rows are among
them), two in unit 2's zone e2e tests, and two in the zone tests.

The `isWalkable` overrides in test fakes of `Space` (`WorldSpaceTest`, `ZoneFixtures`, the zone tests)
implement the unchanged `Space.isWalkable` and are untouched. Unit 2's `ZoneIndexTest` drives
`ZoneIndex.step` through the out-of-extent path in `an entity moved outside the grid's extent is
dropped from entitiesIn and reported unzoned`, by mutating the entity's live `location` with `setTo`
— exactly the caller-owned point C30's copy protects. That test, like the rest of unit 2's `ZoneIndex`
tests, stays unchanged and must stay green, which proves `step` is indifferent to `zoneAt`'s failure
type.

### 1.3 Relationship to units 1 and 2

This unit is independent of both in code. It consumes nothing from `WorldSystem`, `World` or
`ZoneIndex`, but it **lands after unit 2** and edits things unit 2 created or touched:

- the e2e tests `ZoneDrivenSimulationE2ETest` (reworked by unit 2) and
  `ZoneIndexSimulationLoopE2ETest` (created by unit 2): their `spawnPoint` calls stop compiling under
  C24 (§6.5);
- `ZoneGrid`, whose KDoc unit 2 reworded (§4.5 edits only `zoneAt`'s `@return` and body);
- the README's Modules world row and Map & Space zone bullet, and CONTRIBUTING's Module layout
  `gametools-world` row, which unit 2 edited. Each edit here is located by the text unit 2 left
  (§4.9).

The exception shape follows architecture §4.5, as units 1 and 2 built it
(`MissingWorldSystemException`, `UnzonedEntityException`), minus the Experimental marker and KDoc
paragraph, since these types are untagged.

---

## 2. Design

### 2.1 Signatures

```kotlin
// TiledMap — gametools-world, com.spartanlabs.gaming.world.map
fun terrainAt(point: Point): Result<TerrainType> = terrain.terrainAt(tileAt(point))   // C25, C26, C28: the layer's failure, passed through unchanged

override fun isWalkable(point: Point): Boolean =
    contains(point) &&
        terrainAt(point).fold(onSuccess = { it.walkable }, onFailure = { false }) &&
        !staticGeometry.blocksPoint(point)

fun spawnPoint(name: String): Result<SpawnPoint> =
    spawnPointsByName[name]?.let { Result.success(it) } ?: Result.failure(MissingSpawnPointException(name))

// TerrainLayer
fun terrainAt(tile: TileIndex): Result<TerrainType> =
    if (tile.x !in 0 until widthTiles || tile.y !in 0 until heightTiles)
        Result.failure(OutOfGridException(tile))
    else Result.success(palette[tileTypeIndices[tile.y * widthTiles + tile.x]])

// ZoneGrid — gametools-world, com.spartanlabs.gaming.world.zone (C30); only the failure changes
fun zoneAt(point: Point, clamped: Boolean = true): Result<Zone> {
    // … column / row computed as today …
    if (!inBounds && !clamped)
        return Result.failure(UnzonedPointException(point))
    // … clamping and success as today …
}
```

Unchanged: `tileAt`, `contains`, `bounds`, `addSpawnPoint`, the `TiledMap`, `TerrainLayer` and
`ZoneGrid` constructors, `zoneAt`'s `clamped = true` path and its success values, `ZoneIndex.step`,
and `Space` itself.

### 2.2 The three exception types (architecture §4.5's shape, untagged)

```kotlin
// new, world.map: MissingSpawnPointException (C24)
class MissingSpawnPointException(val name: String) :
    NoSuchElementException("no spawn point named '$name'") {
    override fun fillInStackTrace(): Throwable = this
}

// new, world.map: OutOfGridException (C25, C28, C29)
class OutOfGridException(val tile: TileIndex) :
    IndexOutOfBoundsException("tile (${tile.x}, ${tile.y}) is outside the terrain grid") {
    override fun fillInStackTrace(): Throwable = this
}

// new, world.zone: UnzonedPointException (C30)
class UnzonedPointException(point: Point) :
    IndexOutOfBoundsException("point (${point.x}, ${point.y}) is outside the zone grid") {
    val point: Point = Point(point)                     // defensive copy, taken once at construction (OD11 (a))
    override fun fillInStackTrace(): Throwable = this
}
```

These were compile- and run-verified on Kotlin 2.2.0 in two scratch probes outside the repo on
2026-10-04, the first with `kotlin-stdlib` alone and the second against GeneralTools 2.2.0's real
`Point`:
- All three compile as `final` subclasses of `java.util.NoSuchElementException` or
  `java.lang.IndexOutOfBoundsException`. `stackTrace` is empty after construction and after
  `throw`/`catch`.
- A `TerrainLayer` failure passed through `TiledMap.terrainAt` arrives as the same type, and
  `isWalkable` via `fold` answers correctly.
- `UnzonedPointException`'s property initializer copies the constructor parameter of the same name.
  The copy equals the caller's point but is a distinct instance, and mutating the caller's point
  afterwards changes neither the copy nor the message.
- For Java callers, `TiledMap.terrainAt` and `spawnPoint` become name-mangled methods returning
  `Object` (§7), while `UnzonedPointException`'s constructor and `getPoint()` stay plain.

### 2.3 Calls this plan makes within the decisions' room (not open decisions)

- **The map types' shape**: `final`, a public constructor taking only the key, the message built
  inside the type, no cause. This is architecture §4.5's shape, already used by units 1 and 2; the
  user can overturn it in review. For `OutOfGridException` the user fixed the key and constructor
  (C28, C29), and for `UnzonedPointException` the user decided every one of these (C30).
- **Untagged**, matching the host APIs (verified, §1.2; C30 for `UnzonedPointException`). An
  Experimental exception returned by an untagged method would force an opt-in on stable callers
  merely to inspect the failure.
- **Messages.**
  - `no spawn point named '<name>'` mirrors the map's own `addSpawnPoint` message and `GameServer`'s
    `No connected player named '…'`.
  - `tile (x, y) is outside the terrain grid` is C29's example.
  - `point (x, y) is outside the zone grid` is built from `x` and `y` explicitly, because
    `Point.toString()` has no parentheses. It is built from the constructor parameter, so it shows
    the queried point.
- **The copy lives in the property initializer** (`val point: Point = Point(point)`), so it happens
  after `Throwable`'s constructor has run `fillInStackTrace` — which reads nothing.
- **`TiledMap.terrainAt` is a pure pass-through** of `terrain.terrainAt(tileAt(point))`, with no
  bounds check of its own and no re-wrapping (C26, C28).
- **`isWalkable` unwraps with `fold`**, the idiom `docs/physics-narrow-phase-plan.md` §2.11 already
  plans for `TerrainLayer.terrainAt`. `contains` stays first.
- **`ZoneIndex.step` is left alone.** It unwraps with `getOrNull()`, which is indifferent to the
  failure type.
- **No logging.** The map and zone types have no logger, and misses are values.
- **Test conventions** (§6), as unit 2's: a position or hit is read with `getOrNull()`; a miss is
  asserted through a file-private helper or a direct `assertIs` on `exceptionOrNull()`; no assertion
  calls `getOrThrow()` on a lookup that might miss.
- **Commit split** (§8): one commit per lookup, then the docs.

### 2.4 Error handling, per signature

| Member | Returns | Expected failures (→ `Result`) | Programmer errors (→ throw) | Unwrapped where |
|---|---|---|---|---|
| `TiledMap.spawnPoint(name)` | `Result<SpawnPoint>` | an unregistered name → `Result.failure(MissingSpawnPointException(name))` | none | by the caller: `getOrNull()` / `fold`; `getOrThrow()` where a missing spawn point is a content error to surface (the exception carries no frames — its message names the spawn point) |
| `TiledMap.terrainAt(point)` | `Result<TerrainType>` | `point`'s tile off the grid → `TerrainLayer.terrainAt`'s `OutOfGridException`, passed through | none | by the caller; `isWalkable` folds it to `false` |
| `TiledMap.isWalkable(point)` | `Boolean` | — (`terrainAt`'s failure folds to `false`) | none | — |
| `TerrainLayer.terrainAt(tile)` | `Result<TerrainType>` | `tile` off the grid → `Result.failure(OutOfGridException(tile))` | none | `TiledMap.terrainAt` (passes it through); #49's planned `TerrainCollisionIndex` folds it |
| `ZoneGrid.zoneAt(point, clamped)` | `Result<Zone>` | `clamped = false` and `point` outside the grid → `Result.failure(UnzonedPointException(point))`; with `clamped = true` it always succeeds | none | `ZoneIndex.step` (`getOrNull()`); any other caller |
| the three exception constructors | instance | — | none; `fillInStackTrace()` returns `this` with no stack walk | never thrown by the library; thrown only by a caller's `getOrThrow()` |

### 2.5 The hot paths

| Path | Today | After this unit |
|---|---|---|
| `isWalkable`, inside the grid | `getOrNull()` on a success — no allocation beyond the `TileIndex` | identical: `Result.success` of a reference type is not boxed and `fold` is inline |
| `isWalkable`, outside `bounds` | short-circuited by `contains` | identical |
| `isWalkable`, exactly on a far edge (`contains` true, tile off the grid) | one **stack-traced** `IndexOutOfBoundsException`, discarded | one stackless `OutOfGridException` plus one `Result` failure box — no stack walk |
| `ZoneIndex.step`, per entity outside the grid's extent, every step | one **stack-traced** `IndexOutOfBoundsException`, discarded by `getOrNull()` | one stackless `UnzonedPointException`, one `Point` copy and one `Result` failure box — no stack walk |
| any in-grid `zoneAt` | success | identical |

Every miss path gets cheaper, and the two level-4c tests (§6.6) guard against a stack-traced miss
coming back.

---

## 3. Documentation rings touched

| Ring | What moves |
|---|---|
| 1 — in-editor | a Level-1 line comment on each new `fillInStackTrace` (it runs from `Throwable`'s constructor and must read nothing). The two map types need no import region (`TileIndex` is in their package); `UnzonedPointException` imports `com.spartanlabs.geometry.Point` under `// 1.1 Spartan Laboratories` |
| 2 — component / API contract | Level-2 KDoc on `TiledMap.terrainAt` / `spawnPoint`, `TerrainLayer.terrainAt` and `ZoneGrid.zoneAt` (new `@return`s), and on the three new types (§4.3, §4.4, §4.6) |
| 3 — boundary | the README's map and zone bullets and the CHANGELOG entry state the new failure shapes |
| 4 — architectural | architecture §4.6 (unit 3's sketch) and §7.6 (adoption) |

**README currency:** yes. Public return types and failure types change and three public types are
added, so the same PR updates `README.md`, `CONTRIBUTING.md` and `CHANGELOG.md` (§4.9).

---

## 4. File-by-file changes

`MAP` = `gametools-world/src/main/kotlin/com/spartanlabs/gaming/world/map`;
`ZONE` = `gametools-world/src/main/kotlin/com/spartanlabs/gaming/world/zone`;
`TEST` = `gametools-world/src/test/kotlin/com/spartanlabs/gaming/testing`.

### 4.1 Changed: `MAP/TiledMap.kt`

- **`terrainAt`**: signature and body as in §2.1. Its KDoc is replaced by:

  ```kotlin
  /**
   * The terrain at [point], as a [Result]: [Result.success] with the [TerrainType] of the tile
   * [point] falls in, or [Result.failure] carrying an [OutOfGridException] for that tile when it is
   * outside the grid - a normal, frequent outcome near a map edge, including a point exactly on the
   * far edge, which [contains] accepts but which floors to the next tile (see [tileAt]). The failure
   * is [terrain]'s own, from [TerrainLayer.terrainAt], passed through unchanged. Never throws and
   * never returns `null`; a miss costs one small allocation and no stack walk.
   *
   * @param point the world coordinate to look up
   * @return the [TerrainType] at [point], or a failure carrying an [OutOfGridException] if [point]
   *   is off the grid
   */
  ```

- **`isWalkable`**: body as in §2.1; its KDoc is unchanged.
- **`spawnPoint`**: signature and body as in §2.1. KDoc:

  ```kotlin
  /**
   * The spawn point named [name], as a [Result]: [Result.success] with it if this map was built with
   * it or it was added with [addSpawnPoint], otherwise [Result.failure] carrying a
   * [MissingSpawnPointException] for [name]. Never throws and never returns `null`.
   *
   * @param name the spawn point's [SpawnPoint.name]
   * @return the spawn point, or a failure carrying a [MissingSpawnPointException] if none is named
   *   [name]
   */
  ```

- `tileAt`'s KDoc ("use [terrainAt] / [contains] to test that") stays true and is unchanged. There is
  no import change. Mutability, concurrency and logging are unchanged.

### 4.2 Changed: `MAP/TerrainLayer.kt`

- `terrainAt`: the failure becomes `Result.failure(OutOfGridException(tile))`; the success branch is
  unchanged.
- KDoc: the summary paragraph is unchanged. Its `@return` becomes:

  ```kotlin
   * @return the tile's [TerrainType], or a failed [Result] carrying an [OutOfGridException] for
   *   [tile] if [tile] is outside the grid - an [IndexOutOfBoundsException], so a handler for that
   *   type still catches it, and stackless, so a miss costs no stack walk
  ```

### 4.3 New: `MAP/MissingSpawnPointException.kt` (C24)

```kotlin
package com.spartanlabs.gaming.world.map

/**
 * Signals that a [TiledMap.spawnPoint] lookup found no spawn point named [name] - neither one the
 * map was built with nor one added later with [TiledMap.addSpawnPoint].
 *
 * It is the *value* of an expected outcome: [TiledMap.spawnPoint] never throws it but returns it
 * inside [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is a
 * [NoSuchElementException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs one allocation and no stack
 * walk. Diagnose from the message and [name].
 *
 * @property name the spawn-point name that was looked up
 * @see TiledMap.spawnPoint
 */
class MissingSpawnPointException(val name: String) :
    NoSuchElementException("no spawn point named '$name'") {

    // Throwable's constructor calls this before `name` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
```

Immutable (one `val`), with no concurrency concerns, no errors of its own and no logging. Untagged,
with no Experimental paragraph.

### 4.4 New: `MAP/OutOfGridException.kt` (C25, C28, C29)

```kotlin
package com.spartanlabs.gaming.world.map

/**
 * Signals that a terrain lookup fell outside the grid: [TerrainLayer.terrainAt] was asked for a
 * [tile] beyond its `widthTiles x heightTiles` extent, or [TiledMap.terrainAt] for a point whose
 * tile is - which includes a point exactly on the map's far edge (see [TiledMap.tileAt]).
 *
 * It is the *value* of an expected outcome: neither lookup throws it, both return it inside
 * [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is an
 * [IndexOutOfBoundsException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs one allocation and no stack
 * walk. Diagnose from the message and [tile].
 *
 * @property tile the tile that was looked up
 * @see TerrainLayer.terrainAt
 * @see TiledMap.terrainAt
 */
class OutOfGridException(val tile: TileIndex) :
    IndexOutOfBoundsException("tile (${tile.x}, ${tile.y}) is outside the terrain grid") {

    // Throwable's constructor calls this before `tile` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
```

Immutable (`TileIndex` is an immutable data class). Untagged.

### 4.5 Changed: `ZONE/ZoneGrid.kt` (C30)

- `zoneAt`: the `!inBounds && !clamped` branch returns `Result.failure(UnzonedPointException(point))`
  instead of `Result.failure(IndexOutOfBoundsException("$point is outside a ${columns}x$rows zone grid"))`.
  Nothing else in the method changes.
- KDoc of `zoneAt`: the summary and `@param point` are unchanged, as is `@param clamped` (as unit 2
  left it, naming "the contract [ZoneIndex.step] relies on"). Its `@return` becomes:

  ```kotlin
   * @return the resolved [Zone] on success; on failure (only possible with `clamped = false`), a
   *   [Result.failure] carrying an [UnzonedPointException] for [point] - an
   *   [IndexOutOfBoundsException], so a handler for that type still catches it, and stackless, so
   *   [ZoneIndex.step] pays no stack walk for an entity outside the grid
  ```

- No import change (`UnzonedPointException` is in this package). The class is not tagged. Mutability
  and concurrency are unchanged: `ZoneGrid` is all-`val`.

### 4.6 New: `ZONE/UnzonedPointException.kt` (C30)

```kotlin
package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

/**
 * Signals that a [ZoneGrid.zoneAt] lookup with `clamped = false` found the point outside the grid's
 * covered extent.
 *
 * It is the *value* of an expected outcome: [ZoneGrid.zoneAt] never throws it but returns it inside
 * [Result.failure]; it is thrown only by a caller that unwraps with `getOrThrow()`. It is an
 * [IndexOutOfBoundsException], so a handler for that type handles it too.
 *
 * **Stackless by design**: no stack trace is recorded, so a miss costs one allocation and no stack
 * walk - [ZoneIndex.step] meets one per entity outside the grid, every step. Diagnose from the
 * message and [point].
 *
 * @param point the world coordinate that was looked up; it is copied, because [Point] is mutable
 * @see ZoneGrid.zoneAt
 */
class UnzonedPointException(point: Point) :
    IndexOutOfBoundsException("point (${point.x}, ${point.y}) is outside the zone grid") {

    /**
     * A copy of the point that was looked up, taken at construction: later changes to the caller's
     * point do not reach it.
     */
    val point: Point = Point(point)

    // Throwable's constructor calls this before `point` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
```

The property is written for OD11 (a). Under (b) it would become a private stored copy behind a
getter that returns `Point(stored)`, and its KDoc would say "a fresh copy on every read" (§9). It is
untagged and has no concurrency concerns: each miss builds its own instance.

### 4.7 KDoc: Dokka checks

Every one of these links resolves within the module (§6.1): `[OutOfGridException]`,
`[MissingSpawnPointException]`, `[UnzonedPointException]`, `[TiledMap.addSpawnPoint]`,
`[TerrainLayer.terrainAt]`, `[TiledMap.terrainAt]`, `[TiledMap.tileAt]`, `[ZoneGrid.zoneAt]` and
`[ZoneIndex.step]`.

### 4.8 Tests — see §6. Files touched: the five map test files and the two zone test files of §1.2, unit 2's two zone e2e tests (§1.3), three new component tests and two new nonfunctional tests.

### 4.9 `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md` (commit 4; each edit located by the text units 1 and 2 leave)

- **README, Modules table, world row.**
  - Map sub-list: replace
    `` `TiledMap` (the `Space` implementation), `TerrainLayer`/`TerrainType`, `StaticGeometry`, `SpawnPoint`, and the `MapDefinition`/`MapLoader` JSON file format (#46) ``
    with
    `` `TiledMap` (the `Space` implementation), `TerrainLayer`/`TerrainType`, `TileIndex`, `StaticGeometry`, `SpawnPoint`, `MissingSpawnPointException` and `OutOfGridException` (the failures its keyed lookups return), and the `MapDefinition`/`MapLoader` JSON file format (#46, #77) ``.
    `TileIndex` was missing (C32).
  - Zone sub-list, as unit 2 left it: replace
    `` `Zone`, `ZoneGrid`, `ZoneIndex` (the zone `WorldSystem`, Experimental) ``
    with
    `` `Zone`, `ZoneGrid`, `UnzonedPointException` (the failure of an unclamped `zoneAt`), `ZoneIndex` (the zone `WorldSystem`, Experimental) ``.
    The rest of the sub-list (`UnzonedEntityException` (Experimental), `EntityChangedZone` (#47, #77))
    is unchanged.
- **README, Map & Space section.**
  - `TiledMap` bullet: replace the parenthetical
    `` (`tileAt`, `terrainAt`, `spawnPoint(name)`, `addSpawnPoint`) `` with
    `` (`tileAt`, `terrainAt`, `spawnPoint(name)`, `addSpawnPoint`; `terrainAt(point)` and `spawnPoint(name)` return a `Result` — a failure carrying `OutOfGridException` for a point off the grid, or `MissingSpawnPointException` for an unknown name) ``.
  - Zone bullet: replace `` (`ZoneGrid(space, columns, rows)`, `zoneAt(point, clamped)`) `` with
    `` (`ZoneGrid(space, columns, rows)`, `zoneAt(point, clamped)` — with `clamped = false`, a point outside the grid is a failure carrying `UnzonedPointException`) ``.
- **CONTRIBUTING, Module layout table, `gametools-world` row.**
  - Map sub-list: replace
    `` `TiledMap`, `TerrainLayer`, `TerrainType`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` (#46) ``
    with
    `` `TiledMap`, `TerrainLayer`, `TerrainType`, `TileIndex`, `StaticGeometry`, `SpawnPoint`, `MapDefinition`, `MapLoader` (#46), `MissingSpawnPointException`, `OutOfGridException` (#77) ``.
  - Zone sub-list, as unit 2 left it: replace `` `UnzonedEntityException` (#77) `` with
    `` `UnzonedEntityException`, `UnzonedPointException` (#77) ``.
- **`CHANGELOG.md` `[Unreleased]` → `### Added`**: a **new** bullet directly after unit 2's #77 bullet
  (which begins `` - `ZoneIndex` is itself a `WorldSystem` ``). The #46 and #47 bullets name
  `terrainAt`, `spawnPoint` and `zoneAt` without a failure shape and are unchanged.

  > - Lookups that can miss return a `Result` whose failure is a stackless, dedicated exception.
  >   `TiledMap.spawnPoint(name)` is a `Result<SpawnPoint>` — a failure carrying the new
  >   `MissingSpawnPointException` (a `NoSuchElementException` naming the spawn point) for an unknown
  >   name — and `TiledMap.terrainAt(point)` is a `Result<TerrainType>`, a failure carrying the new
  >   `OutOfGridException` (an `IndexOutOfBoundsException` naming the tile) when the point's tile is
  >   off the grid. `TerrainLayer.terrainAt` fails with the same `OutOfGridException`, which
  >   `TiledMap.terrainAt` passes through, and `isWalkable` answers exactly as before.
  >   `ZoneGrid.zoneAt(point, clamped = false)` fails with the new `UnzonedPointException` (an
  >   `IndexOutOfBoundsException` carrying a copy of the point) for a point outside the grid. (#77)

- **Website: none.** World Systems, map and zone website updates wait for #86. `website/` names
  neither the lookups nor their failures.

### 4.10 Unchanged, asserted

- `Space` and its fakes; `tileAt` and its tests.
- `MapLoader` / `MapDefinition`, which build `TiledMap`s but call neither lookup.
- `ZoneIndex` and all of unit 2's `ZoneIndex` tests.
- `zoneAt`'s `clamped = true` path.
- Every `gametools-core` and `gametools-net` file, and the `gametools-world` build file.

---

## 5. Adoption / blast radius (architecture §7.6)

- **Main source:** `TiledMap.isWalkable` (re-expressed, §2.1) and `ZoneIndex.step` (unchanged:
  `getOrNull()` ignores the failure type). No other module calls any of the three lookups.
- **Test source:** the sixteen rows of §1.2's table — twelve in the map test files (eight of them
  silent), two in unit 2's zone e2e tests, two in the zone tests. §6 gives each its new form.
- **Docs:** the C23–C26 callouts of architecture §11.4 were applied on 2026-10-04 (issue-46 plan,
  issue-47 plan, api-openness). The C30 sentence for the issue-47 plan, architecture §11.5, was
  approved and applied on 2026-10-05.
- **C23 beyond this unit** (architecture §7.6):
  - `World.byId` keeps its nullable shape in #77 (released since `v3.1.0`).
  - `SpawnPoint.facing` and `team` are optional data, not lookups.
  - `ZoneIndex.zoneOf` became `Result` in unit 2 (C22).

---

## 6. Test plan (5-level hierarchy)

The conventions are unit 2's (`kotlin.test` on JUnit 5, one class per file, backtick names,
hand-rolled fixtures, no MockK), plus these:

- A hit or position reads `….getOrNull()`, so a miss fails as "expected X but was null".
- A miss goes through a file-private helper, or a direct `assertIs` on `exceptionOrNull()`. No
  assertion calls `getOrThrow()` on a lookup that might miss: the exceptions are stackless, so one
  escaping a test would point nowhere.
- The new types are untagged, so their tests need no `@OptIn`.
- **The silent sites of §1.2 compile unchanged and fail only at run time**, so the full suite runs
  before each commit, not just `compileTestKotlin`.

```kotlin
/** Asserts [name] misses: spawnPoint is a failure carrying a MissingSpawnPointException for exactly [name]. */
private fun TiledMap.assertNoSpawnPoint(name: String) {
    val result = spawnPoint(name)
    assertTrue(result.isFailure, "expected no spawn point named '$name', but got ${result.getOrNull()}")
    assertEquals(name, assertIs<MissingSpawnPointException>(result.exceptionOrNull()).name)
}

/** Asserts [point] is off the grid: terrainAt is a failure carrying an OutOfGridException for point's tile. */
private fun TiledMap.assertOffGrid(point: Point) {
    val result = terrainAt(point)
    assertTrue(result.isFailure, "expected $point to be off the grid, but terrainAt returned ${result.getOrNull()}")
    assertEquals(tileAt(point), assertIs<OutOfGridException>(result.exceptionOrNull()).tile)
}
```

### 6.1 Level 1 — gating (no checked-in files; no `testing.gating` package exists, none invented)

- Before each commit: `./gradlew :gametools-world:componentTest :gametools-world:deterministicTest`.
  Before the PR: `integrationTest e2eTest nonfunctionalTest` as well (serialised by the ports lock;
  none of these tests binds a port).
- `./gradlew :gametools-world:compileKotlin :gametools-world:compileTestKotlin` after each commit.
- Dokka: `./gradlew dokkaGeneratePublicationHtml`, diffing the warning list before and after (Dokka
  warnings are non-fatal). Every link of §4.7 resolves.
- After commit 1, `grep -rn "spawnPoint(" gametools-world/src` shows no call without a `Result` unwrap.
  After commit 2, the same holds for `terrainAt(`.

### 6.2 Level 2 — component

**`TerrainLayerTest`** (`TEST/component/world/map/TerrainLayerTest.kt`, commit 2):
- `terrainAt resolves each tile through the palette`: unchanged.
- `terrainAt fails with Result rather than throwing for an out-of-range tile on every edge` → rename to
  `terrainAt fails with an OutOfGridException rather than throwing for an out-of-range tile on every
  edge`. Keep the four edge tiles. For each one assert:
  - `assertIs<OutOfGridException>(result.exceptionOrNull())`, whose `tile` equals the queried tile;
  - `assertIs<IndexOutOfBoundsException>(…)` (the subtype contract, kept);
  - an empty `stackTrace`.
- Add the import of `OutOfGridException`.

**`TiledMapTest`** (`TEST/component/world/map/TiledMapTest.kt`). The `assertNull` import goes, since
nothing else uses it; the imports of `MissingSpawnPointException`, `OutOfGridException` and
`kotlin.test.assertIs` are added.
- Commit 1 (with `assertNoSpawnPoint`):
  - `spawnPoint resolves a registered name and misses on an unknown one` → `spawnPoint is a success
    for a registered name and a MissingSpawnPointException failure for an unknown one`:
    `assertEquals(spawn, map.spawnPoint("start").getOrNull())`; `map.assertNoSpawnPoint("unknown")`.
  - `addSpawnPoint registers a new spawn point and rejects a duplicate name`: its lookup becomes
    `assertEquals(extra, map.spawnPoint("extra").getOrNull())`. The duplicate rejection is unchanged.
- Commit 2 (with `assertOffGrid`):
  - `terrainAt returns the tile's TerrainType in bounds and null out of bounds` → `terrainAt is a
    success in the grid and an OutOfGridException failure off it`:
    `assertEquals(grass, map.terrainAt(Point(1.0, 1.0)).getOrNull())`, and the same for `water`.
    Then `map.assertOffGrid(Point(-1.0, 1.0))`, `map.assertOffGrid(Point(25.0, 1.0))`, and the far
    edge `map.assertOffGrid(Point(20.0, 5.0))` (the fixture is 2×2 tiles of size 10).
  - **New** `terrainAt passes TerrainLayer's failure through unchanged`. For an off-grid point `p`,
    `map.terrainAt(p).exceptionOrNull()` and `map.terrain.terrainAt(map.tileAt(p)).exceptionOrNull()`
    are both `OutOfGridException`s with equal `tile` and equal `message`. (Identity is not
    observable, because each call builds its own exception.)
  - **New** `isWalkable is false exactly on the far edge, where contains is true but the tile is off
    the grid`: `assertTrue(map.contains(Point(20.0, 5.0)))` and
    `assertFalse(map.isWalkable(Point(20.0, 5.0)))`.

**`ZoneGridTest`** (`TEST/component/world/zone/ZoneGridTest.kt`, commit 3):
- `zoneAt with clamped = false fails on the far edge and on a negative out-of-bounds point` → rename
  to `zoneAt with clamped = false fails with an UnzonedPointException on the far edge and on a negative
  out-of-bounds point`. For both points, `Point(40.0, 30.0)` and `Point(-5.0, -5.0)`, assert:
  - `assertIs<UnzonedPointException>(result.exceptionOrNull())`, whose `point` equals the queried
    point;
  - the kept `assertIs<IndexOutOfBoundsException>`;
  - an empty `stackTrace`.
- Add the import of `UnzonedPointException`.

**Three new exception tests**, each checking the same five properties as units 1 and 2's exception
tests:

- **`MissingSpawnPointExceptionTest`** (`TEST/component/world/map/MissingSpawnPointExceptionTest.kt`,
  commit 1):
  1. `name is the name that was looked up`.
  2. `it is a NoSuchElementException`: `assertIs`, and a `catch (x: NoSuchElementException)` around
     `throw e` catches the same instance.
  3. `it is stackless`: `stackTrace` is empty after construction and after `throw`/`catch`, and
     `fillInStackTrace()` returns `e` with the trace still empty.
  4. `its message names the spawn point`: it contains `'red-spawn'` for
     `MissingSpawnPointException("red-spawn")`.
  5. `it has no cause`.
- **`OutOfGridExceptionTest`** (`TEST/component/world/map/OutOfGridExceptionTest.kt`, commit 2):
  1. `tile is the tile that was looked up`.
  2. `it is an IndexOutOfBoundsException`.
  3. `it is stackless`.
  4. `its message names the tile`: it contains `(4, 3)` for `TileIndex(4, 3)`.
  5. `it has no cause`.
- **`UnzonedPointExceptionTest`** (`TEST/component/world/zone/UnzonedPointExceptionTest.kt`,
  commit 3):
  1. `point is a copy of the point that was looked up`. It is `assertEquals` to the original and
     `assertNotSame` from it, and after `original.setTo(99.0, 99.0)` it still equals
     `Point(5.0, 25.0)`.
  2. `it is an IndexOutOfBoundsException`.
  3. `it is stackless`.
  4. `its message names the point`: it contains `(5.0, 25.0)`, and it is unchanged after the original
     is mutated.
  5. `it has no cause`.

### 6.3 Level 3 — integration (`testing.integration.world.map`)

**`MapLoaderIntegrationTest`**: JSON loading across the `MapLoader` → `TiledMap` boundary, in
`fromJson builds a TiledMap whose bounds, isWalkable, terrainAt and spawnPoint match the fixture`.
- Commit 1: its two `SpawnPoint` assertions read `map.spawnPoint("red-spawn").getOrNull()` and the
  same for `blue-spawn`. **New**:
  `assertEquals("nope", assertIs<MissingSpawnPointException>(map.spawnPoint("nope").exceptionOrNull()).name)`.
- Commit 2: its `grass` and `water` lookups read `map.terrainAt(Point(5.0, 5.0)).getOrNull()`, and
  likewise for `water`, with the existing `checkNotNull` kept so the smart cast works. **New**: an
  off-grid point of the loaded 4×3 map, `map.terrainAt(Point(45.0, 5.0))`, is a failure whose
  `OutOfGridException` carries `TileIndex(4, 0)`.
- Imports added as needed: `MissingSpawnPointException`, `OutOfGridException`, `TileIndex`,
  `kotlin.test.assertIs`.

`zoneAt`'s change needs no new level-3 test: a failure type crosses no external interface, and
`ZoneGridTest` and the 4a law cover it. Unit 2's `ZoneIndexWorldIntegrationTest` (`stepSystems after each tick
publishes exactly the expected EntityChangedZone sequence, including a despawn`) drives
`ZoneIndex.step` across the `World` boundary and must stay green unchanged; the out-of-extent path is
`ZoneIndexTest`'s (§1.2).

### 6.4 Level 4a — deterministic

- **`TiledMapQueryLawsTest`** (commit 2):
  - `assertQueryLawsHold`'s `terrainAt` assertion becomes
    `assertEquals(naiveTerrainAt(point.x, point.y), map.terrainAt(point).getOrNull(), …)`. The oracle
    stays nullable: it is test code, and the convention governs API.
  - **New law** in `assertQueryLawsHold`: wherever the oracle says off-grid, the failure is an
    `OutOfGridException` whose `tile` equals `naiveTileAt(point.x, point.y)`, over the boundary
    points and the 500 seeded random ones.
  - The `isWalkable` law is unchanged and proves acceptance criterion 6.
  - Imports added: `OutOfGridException`, `kotlin.test.assertIs`.
- **`ZoneGridPartitionLawsTest`** (commit 3): in `zoneAt(clamped = false) succeeds iff the point is
  within bounds, and the resolved zone contains the point`, a **new** assertion. Whenever the result
  is a failure, it is an `UnzonedPointException` whose `point` equals the queried point, across the
  seeded random configurations. Imports added: `UnzonedPointException`, `kotlin.test.assertIs`.

### 6.5 Level 4b — end-to-end (`testing.e2e.world.*`), all commit 1

- **`MapDrivenWorldE2ETest`**, in `a TiledMap-backed World ticks normally and its space still answers
  queries correctly afterward`:
  - its `redSpawn` / `blueSpawn` lookups read `map.spawnPoint("red-spawn").getOrNull()` and the same
    for blue. The `checkNotNull` lines after them stay and smart-cast as before.
  - its closing lookup becomes `assertEquals(redSpawn, sameMap.spawnPoint("red-spawn").getOrNull())`.
- **`ZoneDrivenSimulationE2ETest`** (as unit 2 left it): in its spawn-point setup, every
  `checkNotNull(map.spawnPoint("…"))` becomes `checkNotNull(map.spawnPoint("…").getOrNull())`.
- **`ZoneIndexSimulationLoopE2ETest`** (created by unit 2): the same edit at every `spawnPoint(` call,
  located by text.

### 6.6 Level 4c — nonfunctional (**new**; the module's first)

These are the guards on the miss paths of §2.5. Both use the `< 10 s` framing of the existing
`WorldSystemRegistryRobustnessTest` throughput tests (`elapsedMillis < 10_000`): a regression
tripwire, not an SLA. They catch a stack-traced miss coming back, which is orders of magnitude
slower.

- **`TiledMapQueryThroughputTest`** (`TEST/nonfunctional/world/map/TiledMapQueryThroughputTest.kt`,
  commit 2). Fixture: a 64×64 map with `tileSize = 1.0`, a checkerboard palette and one obstacle.
  1. `isWalkable over a million mixed points stays within a generous budget`. The points cycle
     through in-grid, out-of-bounds and exactly-on-a-far-edge.
  2. `a million off-grid terrainAt misses stay within a generous budget and record no stack trace`.
     Every 100 000th failure is sampled and checked to be an `OutOfGridException` with an empty
     `stackTrace`.
- **`ZoneGridQueryThroughputTest`** (`TEST/nonfunctional/world/zone/ZoneGridQueryThroughputTest.kt`,
  commit 3). Fixture: an 8×8 `ZoneGrid` over a file-private 64×64 `Space` fake (the shape of
  `ZoneGridPartitionLawsTest`'s `FixtureSpace`; no import from another level's package), with query
  points cycling through all four sides outside the extent.
  1. `a million out-of-extent zoneAt misses with clamped = false stay within a generous budget and
     record no stack trace`. This is the per-frame miss path of `ZoneIndex.step`. Every 100 000th
     failure is sampled and checked to be an `UnzonedPointException` with an empty `stackTrace`.

### 6.7 Level 5 — UAT

Not planned in #77. By the user's decision of 2026-10-08, Level 5 (UAT) is deferred to a
separate, later plan. Related issue: #133, which defines a selective Level-5 `testing.uat` level.

### 6.8 What cannot be tested automatically

- The Java-side shape: `terrainAt` and `spawnPoint` become mangled, `Object`-returning methods, and
  there is no Java test source set (§7 risk 2).
- That no external consumer parses `TerrainLayer`'s or `ZoneGrid`'s old messages (§7 risk 4).
- Acceptance criterion 6's allocation half: that `isWalkable`'s in-grid path allocates nothing
  beyond what `tileAt` already makes. It is verified by review (as built, 2026-10-08, below). Its
  "same answers" half stays automated by the level-4a oracle law (§6.4).

---

## 7. Risks & edge cases

1. **Silent test sites.** Eight call sites compile unchanged against the new `Result` and fail only at
   run time (§1.2). All are listed in §6, and the full suite runs before each commit.
2. **Java callers.** `TiledMap.terrainAt` and `spawnPoint` become name-mangled
   (`terrainAt-<hash>`, `spawnPoint-<hash>`) and return `Object` to Java (`javap`-verified), as
   `TerrainLayer.terrainAt` already did, so Java code loses clean access to them. This is a
   consequence of C24 and C25, recorded as architecture §12 records the same for `systemOf`.
   `ZoneGrid.zoneAt` already returned a `Result`, so nothing changes for Java there.
3. **Stable Core freeze.** All three new types are untagged. They ship as Stable Core in the first
   release containing #46 or #47, and any later change to a payload or constructor needs a major.
4. **Message texts change.** `TerrainLayer`'s miss changes from `TileIndex(x=…, y=…) is outside a WxH
   grid` to `tile (x, y) is outside the terrain grid`, and `ZoneGrid`'s from `<x>, <y> is outside a CxR
   zone grid` to `point (x, y) is outside the zone grid`. Nothing in the repo parses either. Handlers
   that catch `IndexOutOfBoundsException` keep working, because both new types are subtypes.
5. **A mutable key.** `UnzonedPointException` copies the caller's point at construction (C30), so a
   live `location` passed in cannot move it. A reader can still mutate the exception's own copy,
   which affects only that exception: the copy is taken once and every read returns it (C34).
6. **Ordering with unit 2.** This unit edits files unit 2 created or touched: two e2e tests,
   `ZoneGrid`'s KDoc, and the README / CONTRIBUTING rows and zone bullet. It therefore lands only
   after all of unit 2's commits (C27, C31), and every docs edit is located by text.
7. **Stackless means no stack trace.** A `getOrThrow()` on a miss propagates an exception with no
   frames; the message and key are the diagnostic (architecture §12).
8. **Release order (C12).** No release containing #46 or #47 may be cut before this PR merges.
9. **Cross-repo impact:** none. No wire or protocol change, and no other module calls these lookups;
   per standing guidance, no issue is filed against downstream consumers.

---

## 8. Version control

- **Branch:** `feature/77-zone-world-system`. This unit's commits land **after all seven of unit 2's**
  (C27, C31). Conventional Commits with scope `world`. No `!` and no `BREAKING CHANGE:` footer: the
  map and zone models are unreleased.
- **Precondition:** `git log` shows unit 2's commits, and
  `git diff HEAD --stat -- gametools-world/src/main/kotlin/com/spartanlabs/gaming/world/map gametools-world/src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneGrid.kt`
  is empty. Stage by explicit path only. Never stage the planner-owned `docs/*.md` hunks; they ride
  the planner's separate `docs:` commit.

| # | Message | Files | Needs |
|---|---|---|---|
| 1 | `feat(world): return a Result from TiledMap.spawnPoint` | **`docs/tiled-map-result-lookups-plan.md` (this plan)**; `MissingSpawnPointException` (new) and its test; `TiledMap.spawnPoint` and its KDoc; the spawn sites of `TiledMapTest` (with `assertNoSpawnPoint`), `MapLoaderIntegrationTest`, `MapDrivenWorldE2ETest`, `ZoneDrivenSimulationE2ETest`, `ZoneIndexSimulationLoopE2ETest` | unit 2's commits; no open decision |
| 2 | `feat(world): return a Result from TiledMap.terrainAt, failing with OutOfGridException` | `OutOfGridException` (new) and its test; `TerrainLayer.terrainAt` and its KDoc; `TiledMap.terrainAt`, `isWalkable` and their KDoc; the terrain sites of `TerrainLayerTest`, `TiledMapTest` (with `assertOffGrid` and two new tests), `TiledMapQueryLawsTest`, `MapLoaderIntegrationTest`; `TiledMapQueryThroughputTest` (new) | commit 1; no open decision |
| 3 | `feat(world): fail ZoneGrid.zoneAt with a stackless UnzonedPointException` | `UnzonedPointException` (new) and its test; `ZoneGrid.zoneAt` and its KDoc `@return`; `ZoneGridTest`'s off-grid test; `ZoneGridPartitionLawsTest`'s law; `ZoneGridQueryThroughputTest` (new) | commit 2; no open decision (OD11 → (a), C34) |
| 4 | `docs: document the Result lookups in README, CONTRIBUTING and CHANGELOG` | `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md` (§4.9, including `TileIndex`) | commit 3; unit 2's docs commits |

- **Each commit compiles and is green on its own.** Commits 1–3 each change one lookup's type or
  failure, together with all of its call sites and the test of its new exception type.
  `MapLoaderIntegrationTest` is touched by commits 1 and 2, in different statements. Commit 4 changes
  only Markdown.
- **Message bodies:** what and why, with the C-numbers involved — commit 1: C23, C24; commit 2: C23,
  C25, C26, C28, C29; commit 3: C30, C31, C34; commit 4: C32 — and "#46 / #47 are unreleased, so no semver
  weight", plus `Refs #77`. **Trailers:** use exactly the attribution rule the caller supplies
  at commit time, and state it explicitly when delegating to the `manager` agent. Never infer it from
  history, which is mixed.
- **PR body** (shared): add this unit's scope — Result lookups for `TiledMap` and `ZoneGrid.zoneAt`
  (C23–C32, C34), with three new untagged exception types.

---

## 9. Decisions (architecture §13)

### Resolved by the user on 2026-10-04 and 2026-10-05 — applied throughout this plan

No open decisions remain.

- **OD7 → change the `TiledMap` lookups in #77, as unit 3** (C23–C27).
- **OD8 → (a):** `OutOfGridException` carries `val tile: TileIndex`, and `TiledMap.terrainAt` stays a
  pure pass-through (C28).
- **OD9 → (i):** the constructor is `(tile)` only, with the message built inside the type (C29).
- **OD10 → (b):** `ZoneGrid.zoneAt`'s `clamped = false` miss is a stackless type of its own,
  `UnzonedPointException`, specified in full by the user (C30). Unit 3 widens to carry it (C31).
- **The missing `TileIndex`** joins the README and CONTRIBUTING map lists in commit 4 (C32).
- **References** are by section for documents and by symbol, region or test name for code (C33).
- **OD11 → (a)** (2026-10-05): `UnzonedPointException` copies its point once, when it is built
  (`val point: Point = Point(point)`), and every read returns that same copy (C34). This plan was
  written for (a), so nothing in it changes.
- **The plan's name** (2026-10-05): the file and the unit slug stay `tiled-map-result-lookups` under
  the unit's wider title.

### OD11 as it was put to the user — resolved 2026-10-05: (a)

C30 says the exception "carries `val point: Point`, defensively copied because `Point` is mutable",
which reads two ways. The classic defensive-copy guidance (Effective Java's item on defensive copies)
copies on both sides when a class must not be changed from outside: mutable constructor arguments on
the way in, and mutable fields on the way out of accessors. Which half C30 means is the question.

- **(a) Copy once, at construction** — `val point: Point = Point(point)`. The caller's point (for
  example an entity's live `location`, which `ZoneIndex.step` passes in) can no longer move the
  exception's key. Every read returns the exception's own copy, which a reader could mutate, affecting
  only that exception. This is the common sense of a defensive copy of a constructor argument, and it
  costs one copy per miss. **Recommended; this plan is written for it. Chosen (C34).**
- **(b) Copy at construction and on every read** — a private stored copy behind
  `val point: Point get() = Point(stored)`. The key is then immutable from outside, like the other
  four exception types' keys (`KClass`, `EntityId`, `String`, `TileIndex`), at one allocation per
  read. Reads are rare: only when a miss is diagnosed. It also makes a `val` that returns a different
  instance on every read (`e.point !== e.point`), and a `setX` on it that silently changes nothing
  visible.

Either way the signature stays `val point: Point`, so switching later breaks no caller. Under (b),
two places in this plan would have changed:
- §4.6: the property becomes a private stored copy plus the getter, with its KDoc saying "a fresh copy
  on every read";
- §6.2's `UnzonedPointExceptionTest` test 1: it adds that two reads are equal but `assertNotSame`.

The user chose (a), so neither change is made, and no commit waits on a decision.

---

## 10. Interfaces with sibling units

- **Units 1 and 2 (unchanged, C27, C31).** This unit consumes no code from either and lands after
  both. It edits unit 2's two zone e2e test files (one `.getOrNull()` per `spawnPoint` call), the
  `zoneAt` method and its KDoc `@return` in `ZoneGrid`, whose other KDoc unit 2 reworded, and the map
  and zone sub-lists of the README and CONTRIBUTING rows unit 2 also edits. `ZoneIndex.step` keeps its
  `getOrNull()`, and unit 2's `ZoneIndex` tests stay unchanged and green.
- **#79 graduation:** nothing. The new types are untagged, so they are not in its removal list.
- **#49 physics:** the `TerrainCollisionIndex.build` that `docs/physics-narrow-phase-plan.md` §2.11
  plans folds `terrain.terrainAt(TileIndex(x, y))` with `{ false }` on failure, which is compatible
  with any failure type, so it needs no change. #49's re-plan should unwrap `TiledMap.terrainAt`'s
  `Result` wherever it used the nullable.
- **Phase 3 interest filtering, #50 vision, pathfinding:** consume `Result`s. A miss allocates one
  stackless exception.

---

## 11. Sequencing & follow-ups

1. Units 1 and 2 land first (C27, C31). Check the §8 precondition, then commits 1 and 2.
2. Then commit 3 and commit 4. Nothing waits on a decision: OD11 was answered (a) on 2026-10-05
   (C34), which is what §4.6 and §6.2 already describe.
3. Hold any release containing #46 or #47 until the PR merges.
4. The planner's docs commit (architecture §11.2–§11.5, all approved and applied) is independent of
   this unit's files.
5. **Follow-ups owned elsewhere:** #49's re-plan unwraps `terrainAt`'s `Result`; the owed openness
   review in `docs/api-openness-decisions-6.0.0.md` now lists the three new `final` types (its
   2026-10-04 note); the website, when #86 closes.
