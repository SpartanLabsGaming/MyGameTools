# Plan: spatial-index rework — `UniformGrid`, `QuadtreeSpatialIndex`, incremental `World` reconcile

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#48](https://github.com/SpartanLabsGaming/MyGameTools/issues/48)
  — *"Phase 1: spatial-index rework — SpatialIndex interface, incremental update, uniform
  grid"* (item 3 of 5 in `docs/framework-vision-and-roadmap.md` §3 Phase 1). This is the
  **second half** of #48: the `SpatialIndex<E>` interface itself and the `Space` port already
  landed on `master` via PR #61 (`feat(build): bootstrap the gametools-world module`,
  commit `d096847`). This plan covers everything else the issue asks for.
- **Branch:** `feature/48-spatial-index-rework`, based on current `master` (which already has
  the merged #48-bootstrap changes).
- **Commit:** TBD
- **PR:** TBD — this plan document is committed together with the first commit of its
  implementation so `git log --follow` binds the two.
- **What this plans:** `UniformGrid<E>` and `QuadtreeSpatialIndex<E>` (the two
  `SpatialIndex<E>` implementations), `World.spatialIndex` wiring with `QuadtreeSpatialIndex`
  as the default (Open Decision 2's lean), the deprecated `World.quadtree` delegating
  accessor, `World.reindexSpatial()`, and the incremental-reconcile rewrite of `World.tick()`
  step 2 (`VisibleObject.lastIndexedLocation`, `World.rebuildQuadtree()` removed). **Revised
  during implementation (§1.1.1, §2.5):** also a correctness fix inside `Quadtree.insert`
  itself — its dead-slot reuse silently mis-positioned any element reinserted after a `remove`,
  which broke `QuadtreeSpatialIndex.move` (and so `World`'s incremental reconcile) in the
  common case. This is now the plan's most consequential single change.
- **Status:** planning only. No source, test, or build file has been modified by this
  document. (This revision was written after a first implementation pass already put
  `UniformGrid.kt`, `QuadtreeSpatialIndex.kt`, and the `World`/`VisibleObject` wiring on disk,
  uncommitted; §3 marks which of those files are already correct as-is and which still need
  the `Quadtree.kt` fix and its accompanying test update.)
- **Baseline:** GameTools `master` at `d096847` (PR #61 merged; three modules published today
  — `gametools-core`, `gametools-net`, `gametools`; `gametools-world` bootstrapped empty).
  Target release: `5.2.0` per `docs/phase-1-map-and-space-plan.md` §7.
- **Related docs:** `docs/phase-1-map-and-space-plan.md` (§1.3 current-state facts, §2.1 the
  `core` ports, §2.4 spatial-index rework design, §3 file list, §4 test plan, §5 risks, §9
  Open Decision 2); `docs/module-split-plan.md` (why `Quadtree` stays in `core`).

---

## 1. Context

### 1.1 The problem (from the issue, verified against source)

`World.tick()` step 2 — `World.kt:182` (`rebuildQuadtree()`, defined at `World.kt:203-208`) —
clears `quadtree` and re-inserts **every** `VisibleObject` in `gameObjects` every tick,
regardless of whether it moved. At the roadmap's medium-scale target (≤10k entities, 10–20
Hz) this is wasted work whenever most entities are static (buildings, idle units, projectiles
that haven't spawned yet). `Quadtree` (`Quadtree.kt`) also has no `move` — its `remove`
(`Quadtree.kt:81-99`) leaves a dead slot rather than compacting — so there is no cheaper
incremental alternative today, and it is the only index `World` can use. A uniform-density
field (the stated RTS/MOBA case) is better served by a grid with O(1) amortised update.

### 1.1.1 Follow-up finding — `Quadtree.insert`'s dead-slot reuse is unsound for `move` (found during implementation)

The first implementation pass (this branch) surfaced a real bug in `Quadtree` itself, not in
the code written against it. `Quadtree.insert(node, x, y, element)`
(`Quadtree.kt:39-56`, specifically the early-exit at `Quadtree.kt:42-45`):

```kotlin
if (node.element == null) {
    node.element = element
    return node
}
```

reuses **the first dead slot encountered on the walk toward `(x, y)`**, without updating that
node's `x`/`y` (both `val`, set once at construction, `Quadtree.kt:24`) to the coordinates the
caller actually asked for. A `move` implemented as `remove(fromX, fromY, element)` then
`insert(toX, toY, element)` walks from the root toward `(toX, toY)` for the `insert` half —
and the node most likely to be a dead slot on that path is the one `remove` *just* vacated, at
`(fromX, fromY)`, especially in the common single- or few-element case. The moved element ends
up stored under a node whose `(x, y)` is still the **old** position; `retrieveBox`'s
box-query descent (`Quadtree.kt:65-78`, `lessMinX`/`lessMinY`/`lessMaxX`/`lessMaxY` computed
against `node.x`/`node.y`) then places and prunes around that stale coordinate, so the moved
element is found at its old position and not its new one — silently, since nothing throws.

This reproduced on: a single-element tree (`QuadtreeSpatialIndexTest.kt`, `"move removes the
element from its old position and indexes it at the new one"`), the pre-existing two-actor
`WorldTest` move scenario (mirrored now by `WorldSpatialIndexTest.kt`'s
`"a UniformGrid-backed world exhibits the same indexing behaviour as the default
Quadtree-backed world"`), and a 300-point seeded-random move sequence
(`SpatialIndexQueryLawsTest.kt`, `"the query laws hold after each of a sequence of seeded
random moves"`). All three test files already exist on this branch and are the tests that
caught it — none of them need new scenarios added, only the underlying `Quadtree.insert` bug
fixed (§2.5).

The bug is **general**, not specific to `move`: any `insert()` call that follows *any*
`remove()` call risks landing on and silently mis-positioning a dead slot for whatever
unrelated element it inserts next. It never surfaced before this issue because `World`'s only
usage pattern was `clear()` (which discards the whole node tree, `Quadtree.kt:105-107`)
followed by a full re-`insert()` of everything — dead slots never survived across a tick
boundary before `reconcileSpatialIndex()` (§2.4) introduced calls to `move()`/`remove()`
without an accompanying `clear()`.

**The fix is not "make `insert` update the reused slot's `x`/`y`."** A point-region
quadtree's shape is a record of the less-than/greater-than comparisons made against each
node's position at insert time (`insert`'s `lessX`/`lessY` branching, `retrieveBox`'s matching
descent). Silently relocating a node's stored coordinates without re-walking it to the tree
position consistent with the *new* coordinates would violate that invariant and could make
`retrieveBox` wrongly prune the branch containing it — a worse, harder-to-detect bug than the
one being fixed. §2.5 (revised) chooses instead to stop reusing dead slots at all.

**`UniformGrid` has no analogous issue — confirmed, not assumed.** `UniformGrid.move`
(`UniformGrid.kt:58-71`) never reuses a stale record: same-cell moves overwrite the stored
`Entry` in place with a freshly constructed `Entry(toX, toY, element)` (an immutable data
class holding its own position, not a mutable slot with independent identity), and
cross-cell moves are a plain `remove` then `insert`, each of which always appends a brand-new
`Entry` carrying the coordinates it was actually called with. There is no dead-slot concept in
`UniformGrid` at all — a removed `Entry` is spliced out of its cell's list (and the cell
dropped from the map if it empties), never left behind for a future call to silently repurpose.
`UniformGrid`'s own component and deterministic tests (already implemented on this branch)
pass today and needed no fix.

### 1.2 What already exists (read, not redesigned)

- `SpatialIndex<E>` (`gametools-core/.../spatial/SpatialIndex.kt`, merged in PR #61) —
  `insert`/`move`/`remove`/`queryBox`/`queryRadius`/`clear`, all `Double` coordinates, element
  identity by reference. Its KDoc already documents `move`'s contract and that box/circle
  results are "in unspecified order" — it does **not** pin down box-edge inclusivity, so each
  implementation is free to document its own (§2.2 below explains why both new
  implementations end up agreeing anyway).
- `Quadtree<N, E>` (`Quadtree.kt`) — point-region, unbalanced. `insert` reuses a dead slot
  found on the path (`Quadtree.kt:36-53`). `retrieveBox` (`Quadtree.kt:59-75`) is the only
  query, over the **half-open** box `(minX, maxX] x (minY, maxY]`. `remove` (`Quadtree.kt:81-99`)
  clears the first node holding the element (by `===`) on the path to `(x, y)`, leaving the
  node as a reusable empty slot. No `move`. Its KDoc (`Quadtree.kt:8-9`) currently says "north
  being the `+y` direction" — Phase 1's Open Decision 7 (y-down) means this line needs
  correcting; the code (the `northWest`/`northEast`/`southEast`/`southWest` field names and
  the quadrant-splitting logic) does not change — the labels are internal to the tree and
  don't need to match a caller's own axis convention, since the tree only ever compares
  magnitudes.
- `World` (`World.kt`) — owns `quadtree: Quadtree<Double, VisibleObject>` (`World.kt:77`) as a
  `val`, rebuilt every tick by the private `rebuildQuadtree()`. Nothing else in `gametools-core`
  production code touches `World.quadtree` directly.
- **`Actor.nearby(quadtree: Quadtree<Double, VisibleObject>, range: Double)`**
  (`Actor.kt:226-230`), **`DirectionalProjectile`** (`DirectionalProjectile.kt:36`) and
  **`HomingProjectile`** (`HomingProjectile.kt:35`) all take a concrete
  `Quadtree<Double, VisibleObject>` — not `World`, not `SpatialIndex` — as a constructor/method
  parameter; a consumer builds and refreshes its own `Quadtree` (or passes `world.quadtree`)
  by hand. None of these are changed by this issue (§5.3 explains why, and what it means for
  a consumer that opts into `UniformGrid`).
- `VisibleObject` (`VisibleObject.kt`) has no position-history field today.
- `GameObject.location` (`GameObject.kt:39`) is a `val location: Point`, and `Point` is
  mutated **in place** — `Actor.kt:201` (`location.setTo(destination)`),
  `Actor.kt:203`/`213` (`location += ...`), `Alive.kt:400`
  (`healthBar.location.setTo(...)`). The same `Point` instance lives for the object's whole
  life; it is never reassigned. This matters directly for `lastIndexedLocation` (§2.3).

### 1.3 Acceptance criteria (from `docs/phase-1-map-and-space-plan.md` §1.4, items 4–5)

1. `World.spatialIndex` is pluggable and incrementally maintained; a `nonfunctional` test shows
   the reconcile step doing materially less work than a full rebuild for a mostly-static field.
2. `World.quadtree` still compiles and returns a working index (deprecated, delegates).
3. Every existing `gametools-core` / `gametools-net` test keeps passing unmodified (a `World`
   built the `5.1.0` way ticks identically) — the default index does not change query
   semantics.

---

## 2. Design

### 2.1 `SpatialIndex<E>` implementations

Two new files in `gametools-core/.../spatial/`, both `final` (no subclassing hook needed).

#### `UniformGrid<E>(cellSize: Double)`

A `HashMap<Long, MutableList<Entry<E>>>` keyed by packed `(cellX, cellY)` cell coordinates,
where `private data class Entry<E>(val x: Double, val y: Double, val element: E)` — each entry
carries its own last-known position, because a query has to test the *element's* position
against the query shape, not just "which cell is it in".

- `cellCoord(c: Double): Int = floor(c / cellSize).toInt()`; `cellKey(cx: Int, cy: Int): Long =
  (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)` — a lossless `(Int, Int) -> Long`
  pack (must be `or`, not `xor`, or distinct cell coordinates can collide).
- `insert` — `O(1)` amortised: compute the cell, append an `Entry`.
- `move` — compute both cells; same cell ⇒ overwrite the stored `Entry` in place (`O(list
  size)`, i.e. `O(1)` for a normally-sized cell); different cell ⇒ `remove` then `insert`.
  This is the operation the incremental reconcile calls for every object that actually moved,
  and it never touches any cell but the one or two involved — unlike a full rebuild.
- `remove` — locate the cell, drop the entry by `===` identity (never `equals`, matching
  `SpatialIndex`'s documented identity contract and `Quadtree.remove`'s own behaviour); if the
  cell's list empties, drop the map entry too so `clear()`-free long-run memory doesn't grow
  unbounded with visited-then-abandoned cells.
- `queryBox` / `queryRadius` — walk only the cells overlapping the query shape's bounding box,
  filter each `Entry` by its exact stored position.
- **Box-edge convention:** `UniformGrid` documents and implements the **same half-open box**
  `Quadtree` already uses — `minX < x <= maxX && minY < y <= maxY` — rather than an inclusive
  box. `SpatialIndex`'s own KDoc leaves this unspecified, so nothing forces this choice, but it
  is the right one here: `docs/phase-1-map-and-space-plan.md` §4 requires a `WorldSpatialIndexTest`
  where swapping in `UniformGrid` produces results identical to `QuadtreeSpatialIndex` for the
  same input — matching `Quadtree`'s existing, released (`5.1.0`) box semantics exactly is what
  makes that an exact-equality test instead of one that has to dodge edge coordinates.
  `queryRadius` has no such precedent to match — it uses a true Euclidean circle
  (`dx*dx + dy*dy <= radius*radius`) in both implementations, which is unambiguous.
- `clear()` — drop every cell.

#### `QuadtreeSpatialIndex<E>(internal val tree: Quadtree<Double, E> = Quadtree())`

Wraps a `Quadtree<Double, E>` rather than reimplementing indexing:

- `insert(x, y, element)` → `tree.insert(x, y, element)`.
- `move(fromX, fromY, toX, toY, element)` → `tree.remove(fromX, fromY, element)` then
  `tree.insert(toX, toY, element)`, exactly as the issue specifies — but this is only correct
  once `Quadtree.insert` stops reusing dead slots (§1.1.1, §2.5 revised). The plan's original
  reasoning here — that dead-slot reuse made this "cheap" — was wrong: reuse doesn't update the
  reused node's stored `(x, y)`, so it silently left the moved element indexed at its **old**
  position. With §2.5's fix, `move` is a full root-to-leaf walk on each half (no reuse
  shortcut at all now) — it is not `UniformGrid`'s `O(1)`, which is precisely why `UniformGrid`
  exists for the uniform-density case, but it is *correct*, which the original reasoning's
  "cheap" version was not.
- `queryBox` → `tree.retrieveBox(minX, minY, maxX, maxY)` **verbatim**, preserving `Quadtree`'s
  exact half-open semantics — this is what makes the default index's behaviour identical to
  `5.1.0`'s, satisfying acceptance criterion 3.
- `queryRadius` — **problem:** `Quadtree.retrieveBox` returns only elements, never their
  positions, so a box-then-filter-by-distance approach (the standard way to build a circle
  query on a box index) cannot filter accurately without knowing each candidate's exact
  position. `Quadtree` is explicitly not gaining new API (issue: "`Quadtree.kt` keeps its
  behaviour"). Resolution: `QuadtreeSpatialIndex` keeps its own
  `IdentityHashMap<E & Any, DoubleArray>` (last known position per currently-indexed element,
  by reference identity) alongside `tree`, updated in lockstep by `insert`/`move`/`remove`/`clear`.
  `queryRadius` then does `tree.retrieveBox(x-radius, y-radius, x+radius, y+radius)` for
  candidates and filters by true Euclidean distance using the tracked position. This is
  bookkeeping `QuadtreeSpatialIndex` owns, not a `Quadtree` change.
- `clear()` → clears both `tree` and the position map.
- `tree` is exposed `internal` (module-visible — `gametools-core` is one Gradle module, so
  `spatial` and `gameobjects` can see it) specifically so `World.quadtree`'s deprecated
  accessor (§2.4) can hand back the live tree with zero copying when it is the backing index.

### 2.2 Why both implementations end up with identical query semantics

This is a deliberate, non-obvious consequence worth stating once: because `UniformGrid`
adopts `Quadtree`'s existing half-open box convention and both use a true Euclidean circle for
radius queries, `queryBox`/`queryRadius` return **exactly the same set** for the same inputs
regardless of which `SpatialIndex` backs a `World` — not just "close enough away from edges".
That is what lets `WorldSpatialIndexTest` (§4) assert exact equality, and it directly
mitigates `docs/phase-1-map-and-space-plan.md` §5's risk: *"Incremental spatial index diverges
from the full-rebuild semantics `5.1.0` callers rely on."*

### 2.3 `VisibleObject.lastIndexedLocation` — why it cannot just store a `Point`

`GameObject.location` is a `val` — the same `Point` instance for the object's life, mutated in
place (§1.2). If `lastIndexedLocation` aliased that instance (`lastIndexedLocation =
obj.location`), every future comparison `lastIndexedLocation != obj.location` would compare
the object to itself and always report "unchanged", even after real movement — the reconcile
would silently stop calling `move()` for anything, and stale index entries would accumulate
forever. The fix is to snapshot **independent** `Double` values, not a `Point` reference, and
compare by value:

```kotlin
// VisibleObject.kt — internal, not part of the public API
internal data class IndexedPosition(val x: Double, val y: Double)
```

```kotlin
open class VisibleObject(...) : GameObject(...) {
    // ...
    /**
     * Where [World]'s spatial index last indexed this object, or `null` if it has never been
     * indexed - a value snapshot, not a reference to [location] (which is mutated in place;
     * aliasing it here would make every object look permanently unmoved). Internal: a
     * [World]-maintained bookkeeping field, not part of the public API.
     */
    internal var lastIndexedLocation: IndexedPosition? = null
}
```

A dedicated regression test (`WorldSpatialIndexTest`, §4) exercises exactly this: multiple
ticks of real movement against both the default and a `UniformGrid`-backed `World`, asserting
stale positions are gone and new ones are present — this is the test that would fail first if
a future edit reintroduced the aliasing bug.

### 2.4 `World` wiring

```kotlin
// World.kt

/**
 * The broad-phase index over this world's [VisibleObject]s, reconciled incrementally at the
 * start of every [tick] (see [reconcileSpatialIndex]) rather than rebuilt from scratch.
 * Defaults to a [QuadtreeSpatialIndex], matching `5.1.0`'s query semantics exactly; assign a
 * [UniformGrid] for a roughly uniform-density field instead. After replacing this mid-game,
 * call [reindexSpatial] once so the new (empty) index is populated and every object's
 * incremental-reconcile marker is reset against it.
 */
var spatialIndex: SpatialIndex<VisibleObject> = QuadtreeSpatialIndex()

/**
 * The pre-`5.2.0` [Quadtree] view of [spatialIndex]: the live tree itself when [spatialIndex]
 * is a [QuadtreeSpatialIndex] (the default - no copying), or a freshly built snapshot from
 * [gameObjects] otherwise (an `O(n)` rebuild on every access - a [UniformGrid]-backed world
 * still using this accessor, [Actor.nearby], or a [Quadtree]-typed [DirectionalProjectile] /
 * [HomingProjectile] pays that cost; migrate to [spatialIndex] directly to avoid it).
 */
@Deprecated(
    "Use spatialIndex; Quadtree is one SpatialIndex implementation among several now.",
    ReplaceWith("spatialIndex")
)
val quadtree: Quadtree<Double, VisibleObject>
    get() = (spatialIndex as? QuadtreeSpatialIndex<VisibleObject>)?.tree
        ?: Quadtree<Double, VisibleObject>().apply {
            gameObjects.filterIsInstance<VisibleObject>()
                .forEach { insert(it.location.x, it.location.y, it) }
        }
```

`tick()` step 2 changes from `rebuildQuadtree()` to `reconcileSpatialIndex()`:

```kotlin
fun tick() {
    tickCount++
    reconcileSpatialIndex()
    reindexEntities()
    // ... steps 3-5 unchanged
}

/**
 * Reconciles [spatialIndex] against the positions the owned [VisibleObject]s hold right now -
 * a first-seen object is [SpatialIndex.insert]ed, a moved one is [SpatialIndex.move]d, and an
 * unchanged one costs nothing. Replaces the pre-`5.2.0` clear-and-reinsert-everything
 * [rebuildQuadtree] this method used to be.
 */
private fun reconcileSpatialIndex() {
    gameObjects.forEach { obj ->
        if (obj !is VisibleObject) return@forEach
        val last = obj.lastIndexedLocation
        val x = obj.location.x
        val y = obj.location.y
        when {
            last == null -> spatialIndex.insert(x, y, obj)
            last.x != x || last.y != y -> spatialIndex.move(last.x, last.y, x, y, obj)
        }
        obj.lastIndexedLocation = IndexedPosition(x, y)
    }
}

/**
 * Clears [spatialIndex] and re-inserts every [VisibleObject] in [gameObjects] at its current
 * position, resetting each one's incremental-reconcile marker to match. [tick] no longer does
 * this every frame; call it after bulk-mutating positions outside of [tick], or right after
 * assigning a new [spatialIndex].
 */
fun reindexSpatial() {
    spatialIndex.clear()
    gameObjects.filterIsInstance<VisibleObject>().forEach { obj ->
        spatialIndex.insert(obj.location.x, obj.location.y, obj)
        obj.lastIndexedLocation = IndexedPosition(obj.location.x, obj.location.y)
    }
}
```

Step 5 (the `removeList` drain, `World.kt:189-199`) gains one line per removed
`VisibleObject`: remove it from `spatialIndex` using **its `lastIndexedLocation`**, not its
current `location` — an object can move during its own tick (step 4) after already being
reconciled this tick (step 2) and before being dropped, so the index still holds it at the
step-2 position:

```kotlin
removed.forEach { gone ->
    if (gone is VisibleObject) {
        gone.lastIndexedLocation?.let { spatialIndex.remove(it.x, it.y, gone) }
        gone.lastIndexedLocation = null
    }
    byId.remove(gone.entityId)
    announced.remove(gone.entityId)
    events.publish(GameEvent.EntityRemoved(gone))
}
```

The class KDoc's "What one `tick` does" list (`World.kt:26-39`) is updated: step 2 becomes
*"`spatialIndex` is reconciled against the current positions of the owned `VisibleObject`s -
moved objects are relocated, new ones inserted, unchanged ones untouched"*, and the class doc's
opening paragraph (`World.kt:18-22`, which currently names `quadtree` specifically) is reworded
to name `spatialIndex`.

### 2.5 `Quadtree.kt` — revised: drop dead-slot reuse from `insert` (behavior change), plus the y-axis KDoc line

**This section supersedes the original plan's "KDoc-only" scope for `Quadtree.kt`.** §1.1.1
found that `insert`'s dead-slot reuse is unsound the moment a caller mixes `remove`/`insert`
without an intervening `clear()` — exactly what `QuadtreeSpatialIndex.move` (and therefore
`World`'s incremental reconcile, the whole point of this issue) now does. The fix belongs in
`Quadtree.kt` itself, not in `QuadtreeSpatialIndex`, because the unsoundness is in what
`insert` does with a dead slot's stale `(x, y)`, and any workaround built on top of `Quadtree`
without touching that would have to duplicate `Quadtree`'s own routing logic to know which
slots are safe to trust — see the rejected alternatives below.

**The fix:** delete `insert`'s dead-slot reuse branch entirely.

```kotlin
// Quadtree.kt — private fun insert(node: Node?, x: N, y: N, element: E): Node
private fun insert(node: Node?, x: N, y: N, element: E): Node {
    if (node == null) return Node(x, y, element)

    // (the `if (node.element == null) { node.element = element; return node }` early-exit is
    // removed — see the class KDoc and CHANGELOG for why)

    val lessX = x < node.x
    val lessY = y < node.y
    when {
        lessX && !lessY -> node.northWest = insert(node.northWest, x, y, element)
        !lessX && !lessY -> node.northEast = insert(node.northEast, x, y, element)
        !lessX && lessY -> node.southEast = insert(node.southEast, x, y, element)
        else -> node.southWest = insert(node.southWest, x, y, element)
    }
    return node
}
```

`insert` now always routes purely by comparing `x`/`y` against the node it is visiting,
whether that node is alive or dead, and only ever creates a *new* `Node` — at the caller's
own `(x, y)` — when it reaches an actually-empty child link. A dead node (`element == null`)
keeps functioning exactly as before as a routing waypoint (its own `x`/`y` never changes,
so every existing live descendant is still reached exactly as it was), it is simply never
overwritten with an unrelated element at different coordinates. `retrieveBox` needs no change:
it already skips a `null` element (`Quadtree.kt:73`, `node.element?.let(into::add)`) and its
descent logic depends only on each node's own `(x, y)`, which no longer changes identity.

**Why this is safe for every existing caller (the proof, not just an assertion):**

- `retrieveBox`'s correctness depends on every node's `(x, y)` matching the point that was
  actually routed there. That invariant now holds unconditionally — a node's `(x, y)` is fixed
  at construction and never repurposed for a different point. Before this fix it could be
  violated exactly when a dead slot got reused; there is no other way it could ever have been
  violated (no other code sets `node.element` or is reachable to mutate `x`/`y`, both `val`).
- `QuadtreeTest.kt`'s `"a removed slot is reused by the next insert at that node"` is the one
  test that directly locks in the *old*, buggy behavior — it must be rewritten (§3), not left
  green, because it is a regression test for exactly the semantics being removed. Every other
  `QuadtreeTest.kt` case (half-open box retrieval, wide/empty-region queries, plain removal,
  `clear`) exercises `retrieveBox`/`remove` alone and is unaffected — confirmed by reading each
  one (none does a `remove` immediately followed by an `insert` at different coordinates
  except the one being rewritten).
- `QuadtreeScalabilityTest.kt` never calls `remove`, so it cannot exercise dead-slot reuse at
  all; confirmed by reading the file. It stays green unmodified, including its sub-linear-query
  and pathological-insertion-order assertions, since neither depends on this code path.
- `Actor.nearby`, `DirectionalProjectile`, `HomingProjectile` (`Actor.kt:226-230`,
  `DirectionalProjectile.kt:35-36`, `HomingProjectile.kt:34-35`) only call
  `Quadtree.retrieveBox` on a tree a caller built and refreshed elsewhere — none calls `insert`
  or `remove` itself, so none can observe the change in `insert`'s reuse behavior; confirmed by
  grep (`gh`-style repo grep for `Quadtree(` construction and `.insert(`/`.remove(`/
  `.retrieveBox(` outside the new `spatial` files turned up only these three read-only call
  sites plus the two test files already accounted for above).

**New behavior, and why it's the right trade rather than a silently accepted cost:** a node
whose element is removed is never reclaimed — it persists as a routing-only waypoint until the
whole tree is discarded by `clear()`. Under `World`'s pre-`5.2.0` usage (`clear()` then full
re-`insert()` every tick) this was moot: the entire tree, dead nodes included, was thrown away
every frame. Under this issue's incremental reconcile, a long-running `World` whose objects
move via `spatialIndex.move()` will accumulate one permanent dead node per move that lands on a
previously-unvisited path shape, growing the tree's node count and potentially its depth over
the life of the `World` — a real, if slow, resource-growth concern for exactly the "avoid
wasted-rebuild-cost" use case this issue targets. `World.reindexSpatial()` (§2.4, already
planned) is the existing escape hatch: it does a `clear()` + full re-`insert()` from
`gameObjects`' current positions, which compacts the tree back to exactly the live element
count. This plan does not add automatic periodic compaction — see Open Decision (§9) for the
recommendation and reasoning.

**Alternatives considered and rejected:**

- *Update the reused slot's `(x, y)` to the new point instead of removing reuse* — rejected
  outright; this is the "obvious fix" the issue investigation already ruled unsafe (§1.1.1):
  it would desynchronize the node's position from the tree-structural invariant that
  `retrieveBox`'s descent relies on, corrupting query results for any *other* element that was
  correctly routed relative to the old position — a worse, silent bug than the one being fixed.
- *Leave `Quadtree.kt` untouched; make `QuadtreeSpatialIndex` maintain its own bookkeeping and
  do a full `clear()` + reinsert-from-bookkeeping inside `move`* — rejected on performance
  grounds: `World.tick()`'s incremental reconcile calls `move()` once per moved object, so an
  `O(n)`-per-`move()` rebuild degenerates a per-tick cost of `O(k)` (k = number of moved
  objects) into `O(k·n)`, i.e. `O(n²)` in the worst case where most objects move — exactly the
  wasted-rebuild cost issue #48 exists to eliminate, just moved from "every tick" to "every
  moved object every tick," which is strictly worse. Rejected as unacceptable per the issue's
  own performance goal, not merely less elegant.
- *A real structural `remove` that unlinks the node from its parent, re-inserting any live
  descendants elsewhere* — rejected as disproportionate: unlinking a node in a point-region
  quadtree without breaking reachability of its subtree requires re-inserting that entire
  subtree (a node's children are only reachable via comparisons against *that node's* `(x, y)`),
  which is `O(subtree size)` per `remove` — worse than the `O(depth)` `remove` already offers,
  and worse than the resource-growth cost being traded for above. Also a materially larger,
  higher-risk change to `Quadtree.kt` than deleting four lines, for a benefit (bounded node
  count) that `reindexSpatial()` already provides on demand.
- *Do nothing; document `move` as "may return stale results in rare cases"* — rejected: the bug
  reproduced on a single-element tree and a two-actor scenario, not just at scale under
  contrived conditions; it is not rare enough to leave undocumented-and-unfixed in code the
  issue is explicitly introducing incremental `move` usage for.

**The y-axis KDoc line** (unchanged from the original plan, folded into the same file edit
since both touch `Quadtree.kt`'s class KDoc): `Quadtree.kt:8-9` currently reads *"...splits the
rest of the plane into four quadrants about that point - north being the `+y` direction,
matching the rest of the engine."* That last clause is wrong per Phase 1 Open Decision 7
(y-down: the health-bar math and the `vertical-placement-is-y-down` convention both treat `-y`
as up). Corrected to something like:

> Each node stores one (`x`, `y`) point with its element and splits the rest of the plane into
> four quadrants about that point, named as if `+y` were north - a labelling convention
> internal to the tree's own logic, independent of the caller's coordinate convention (the
> engine's own world space is y-down: `-y` is up). The tree only ever compares magnitudes, so
> the two conventions never need to agree.

The class KDoc's `insert`/`remove` method docs also need updating to describe the *new*
behavior accurately (§3): `insert`'s doc currently claims the now-removed reuse behavior;
`remove`'s doc ("leaving the node in place as an empty slot for reuse") should instead say the
node is left in place as a permanent routing waypoint, not describe a reuse that no longer
happens.

---

## 3. File-by-file changes

### `gametools-core`

- **new** `spatial/UniformGrid.kt` — `UniformGrid<E>(cellSize: Double) : SpatialIndex<E>` per
  §2.1. KDoc per the Component-Ring standard (`@param cellSize`, behaviour/complexity notes,
  the half-open box-edge convention stated explicitly).
- **new** `spatial/QuadtreeSpatialIndex.kt` — `QuadtreeSpatialIndex<E>(internal val tree:
  Quadtree<Double, E> = Quadtree()) : SpatialIndex<E>` per §2.1, including the `IdentityHashMap`
  position-tracking used only by `queryRadius`. **Already implemented correctly on this
  branch and needs no further change** — its `move` is exactly `tree.remove(...)` then
  `tree.insert(...)`, which becomes correct once `Quadtree.insert` no longer reuses dead slots
  (§2.5); nothing in this file's own logic was wrong.
- **modified** `spatial/Quadtree.kt` (revised — was "KDoc-only", now a real behavior change,
  §2.5): delete the dead-slot-reuse early-exit from the private `insert(node, x, y, element)`
  overload (4 lines); update `insert`'s and `remove`'s KDoc to describe the corrected behavior
  (no more "empty slot... reused", `remove` now documented as leaving a permanent routing
  waypoint); apply the y-axis KDoc correction to the class doc. No signature change, no change
  to `retrieveBox` or `clear`. **`QuadtreeTest.kt` gains one rewritten test** (the one that
  locked in the old reuse behavior) and is otherwise unmodified; `QuadtreeScalabilityTest.kt`
  stays green unmodified (it never calls `remove`).
- **modified** `gameobjects/VisibleObject.kt` — add `internal data class IndexedPosition(val
  x: Double, val y: Double)` and `internal var lastIndexedLocation: IndexedPosition? = null`
  per §2.3. **Already implemented correctly on this branch; no further change needed** — the
  `IndexedPosition` value-snapshot design in §2.3 was never the source of the bug.
- **modified** `gameobjects/World.kt` — `spatialIndex` field, deprecated `quadtree` accessor,
  `reindexSpatial()`, `tick()` step 2 → `reconcileSpatialIndex()`, the `removeList` drain gains
  the `spatialIndex.remove(...)` call, `rebuildQuadtree()` deleted, class KDoc updated, per
  §2.4. New imports: `com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex`,
  `com.spartanlabs.gaming.spatial.SpatialIndex` (alongside the existing `Quadtree` import,
  still needed for the deprecated accessor's return type). **Already implemented correctly on
  this branch; no further change needed** — `World`'s wiring only ever calls through
  `SpatialIndex`/`QuadtreeSpatialIndex`, so it inherits the fix automatically once §2.5 lands.
- **modified** `testing/nonfunctional/WorldTickThroughputTest.kt` and
  `testing/component/gameobjects/WorldTest.kt` — both call `world.quadtree.retrieveBox(...)`
  directly (`WorldTickThroughputTest.kt:130`; `WorldTest.kt:73,84,85,89,101,113`). No behaviour
  change is needed (the default index keeps returning the same live `Quadtree`), but both
  files now touch a `@Deprecated` member; add `@Suppress("DEPRECATION")` at the point of use
  (class- or function-level, whichever is narrower) so the deliberate backward-compatibility
  coverage doesn't read as an oversight in the build log.
- **new** `testing/component/spatial/UniformGridTest.kt`,
  `testing/component/spatial/QuadtreeSpatialIndexTest.kt`,
  `testing/component/gameobjects/WorldSpatialIndexTest.kt`,
  `testing/deterministic/spatial/SpatialIndexQueryLawsTest.kt`,
  `testing/nonfunctional/spatial/SpatialIndexScalabilityTest.kt` — see §4. **Already
  implemented on this branch except `SpatialIndexScalabilityTest.kt` (not yet written).** No
  changes needed to any of the four already-written files for this fix — they are, in fact,
  the tests that caught the `move` bug (§1.1.1) once `Quadtree.insert`'s dead-slot reuse is
  removed (§2.5), all four are expected to pass unmodified.
- **modified** `testing/component/spatial/QuadtreeTest.kt` — rewrite
  `"a removed slot is reused by the next insert at that node"` (currently asserts the exact
  buggy reuse behavior being removed) into a regression test locking in the fix at the
  `Quadtree` level directly, e.g. `"insert after remove indexes the new element at its own
  position, not the dead slot's"`: remove an element, insert a different element at different
  coordinates, assert `retrieveBox` finds the new element only at its own coordinates and finds
  nothing at the dead slot's old coordinates. Every other test in the file is unaffected (§2.5)
  and stays as-is.

No other module changes. `gametools-world`, `gametools-net`, and the umbrella are untouched by
this issue.

---

## 4. Test plan (5-level hierarchy)

### Level 2 — component (`testing.component.*`, mock external calls — none apply here, no I/O)

- **`spatial/UniformGridTest.kt`** — insert-then-queryBox/queryRadius correctness against a
  small brute-force reference; `move` within the same cell and across cells; `remove` of a
  present and an absent element (no-op); multiple elements sharing one cell; `clear()`; the
  half-open box-edge convention explicitly (`4.0` on the `maxX` edge is included, on the `minX`
  edge excluded — a targeted case, not just random data); a degenerate `minX > maxX` query
  returns empty rather than throwing.
- **`spatial/QuadtreeSpatialIndexTest.kt`** — `queryBox` matches `Quadtree.retrieveBox`
  verbatim for the same inputs (delegation, not reimplementation); `move` removes from the old
  position and inserts at the new one (verified via `queryBox`, not by peeking at `tree`);
  `queryRadius` returns only elements truly within the circle, including a candidate that is in
  the bounding box `retrieveBox` would return but outside the true circle (the case the
  position-tracking map exists to handle); `clear()` empties both the tree and the position
  map (a `queryRadius` after `clear()` then re-`insert()` must not see a stale tracked
  position).
- **`gameobjects/WorldSpatialIndexTest.kt`** — the central regression suite for this issue:
  - `World()`'s default `spatialIndex` is a `QuadtreeSpatialIndex`, and the deprecated
    `quadtree` accessor returns the *same instance* (`===`) across repeated calls (zero-cost
    delegation, not a rebuild) — locks in acceptance criterion 2/3.
  - a hand-written spy `SpatialIndex<VisibleObject>` (records every call) installed as
    `spatialIndex` before ticking proves the reconcile step calls `insert` only for newly
    added objects, `move` only for objects whose `location` actually changed tick-to-tick, and
    never calls `clear()` — the deterministic, non-flaky lock-in of "no more full rebuild"
    that a wall-clock test alone can't guarantee.
  - the exact three `WorldTest` scenarios this replaces conceptually — "indexes at tick
    start", "stale entry does not linger", "moved object is found at its new position, not its
    old one" — repeated against a `World` whose `spatialIndex` is swapped to `UniformGrid`
    right after construction (plus one `reindexSpatial()` call, per the new field's own KDoc),
    asserting **identical** `queryBox` results to an equivalent `QuadtreeSpatialIndex`-backed
    `World` driven through the same scripted moves — the parity test
    `docs/phase-1-map-and-space-plan.md` §4 calls for.
  - removing a `VisibleObject` mid-tick (via `removeList`, after it moved during its own
    `onUpdate`) leaves no trace of it at either its old or its new position — this is what
    exercises the `lastIndexedLocation`-at-removal-time logic in §2.4's `removeList` drain.
  - a `UniformGrid`-backed `World`'s deprecated `quadtree` accessor still returns correct
    (if freshly rebuilt) membership — the fallback path in §2.4 works, functionally, even
    though it is not the default.
- **`spatial/QuadtreeTest.kt`** (revised — now modified, not untouched; see §2.5/§3): one test
  rewritten to lock in the dead-slot-reuse fix instead of the bug it used to assert; every
  other case — half-open box retrieval, wide/empty-region queries, plain `remove`, `clear` —
  is a regression net proving `Quadtree`'s query behavior is otherwise exactly `5.1.0`'s.
- Existing `testing/component/gameobjects/DirectionalProjectileTest.kt`,
  `testing/component/gameobjects/HomingProjectileTest.kt`, and the untouched parts of
  `WorldTest.kt` — no changes; they are the regression net proving `Actor.nearby` and the two
  projectile types behave exactly as in `5.1.0` (none of the three calls `Quadtree.insert` or
  `.remove` itself, so §2.5's fix cannot change their behavior — confirmed by reading/grepping
  each, §2.5).

### Level 3 — integration

None added. `UniformGrid`/`QuadtreeSpatialIndex`/the `World` wiring have no external interface,
database, or network boundary to validate — component + deterministic coverage is the right
level for this change.

### Level 4a — deterministic (`testing.deterministic.*`)

- **`spatial/SpatialIndexQueryLawsTest.kt`** — seeded-random property test, run against
  **both** `UniformGrid` and `QuadtreeSpatialIndex` through one shared parameterised body
  (they share query semantics per §2.2, so one oracle applies to both): insert N random points,
  then for many random boxes and many random circles assert `queryBox`/`queryRadius` return
  exactly the set a linear scan of the same points would (using the half-open box rule and true
  Euclidean circle as the oracle's own rule) — this is `docs/phase-1-map-and-space-plan.md`
  §4's `SpatialIndexQueryLawsTest`. A second scenario drives a sequence of seeded random
  `move`s (simulating ticks) and re-asserts the law after each one, to catch any
  incremental-update bug a static insert-only scenario would miss — **this is, concretely, the
  test that caught §1.1.1's bug** for `QuadtreeSpatialIndex` over a 300-point sequence; already
  implemented on this branch and expected to pass unmodified once §2.5's fix lands.

### Level 4c — nonfunctional (`testing.nonfunctional.*`)

- **`spatial/SpatialIndexScalabilityTest.kt`** — mirrors the existing
  `QuadtreeScalabilityTest.kt`'s wall-clock-comparison style: build a `World` with ~10k
  `VisibleObject`s, mostly static with a small moving fraction, and assert N ticks under the
  new `reconcileSpatialIndex()` complete materially faster than N calls to `reindexSpatial()`
  (the old full-rebuild behaviour, kept available specifically so this comparison has a
  same-process baseline) — the acceptance-criterion-4 evidence
  `docs/phase-1-map-and-space-plan.md` calls for.

### What can't be automated

Nothing in this slice is inherently manual — no UI, no timing-sensitive network behaviour
beyond what the existing wall-clock-comparison pattern already accepts as this repo's norm for
`Quadtree`.

---

## 5. Risks & edge cases

| Risk | Mitigation |
|---|---|
| **`Quadtree.insert`'s dead-slot reuse silently mis-positions a moved element** (§1.1.1) — `QuadtreeSpatialIndex.move`'s `remove`-then-`insert` very often lands the `insert` half on the slot `remove` just vacated, indexing the moved element at its *old* position, not its new one. This is a bug in `Quadtree` itself, latent since `5.1.0` but only triggered by this issue's incremental `move`/`remove` usage. | Delete the dead-slot-reuse early-exit from `Quadtree.insert` (§2.5) — insert always creates a fresh node at the caller's own coordinates; a dead node persists only as a routing waypoint. Confirmed safe for every other caller by reading/grepping every direct `Quadtree` user (§2.5). Already-implemented `QuadtreeSpatialIndexTest`, `WorldSpatialIndexTest`, and `SpatialIndexQueryLawsTest` reproduce the bug today and are expected to pass once the fix lands — no new tests needed to detect a regression here. **Behavior change, not signature change** — flagged in CHANGELOG `### Fixed` (§8), not folded silently into the `Added` entry. |
| Dropping dead-slot reuse means a removed node's slot is never reclaimed — a long-running `World` whose objects move via `spatialIndex.move()` accumulates permanent dead nodes, growing `Quadtree`'s node count (and potentially depth) over the `World`'s lifetime | Documented as a known trade-off in `Quadtree`'s KDoc (§2.5); `World.reindexSpatial()` (already planned, §2.4) is the existing compaction escape hatch — a `clear()` + full re-`insert()` from current positions. No automatic periodic compaction added in this issue (Open Decision, §9); revisit if a `SpatialIndexScalabilityTest`-style long-run measurement later shows it matters in practice. |
| `UniformGrid` / `QuadtreeSpatialIndex` disagreeing on box-edge inclusivity, making the `UniformGrid`-vs-`Quadtree` parity test flaky or edge-dependent | Both implement the identical half-open convention `Quadtree` already ships (§2.2) — parity is exact, not just "away from edges". |
| `Quadtree.retrieveBox` cannot recover an element's position, blocking an accurate `queryRadius` on `QuadtreeSpatialIndex` | An internal `IdentityHashMap` position cache inside `QuadtreeSpatialIndex` only (no `Quadtree` change), updated on every `insert`/`move`/`remove`/`clear`. |
| `Actor.nearby`, `DirectionalProjectile`, `HomingProjectile` all hard-code `Quadtree<Double, VisibleObject>` (§1.2), not `SpatialIndex` — a consumer that switches `World.spatialIndex` to `UniformGrid` gets **no** speed-up for code going through these three, only through the deprecated `quadtree`'s `O(n)`-per-access fallback | Explicitly out of scope for this issue (not listed in `docs/phase-1-map-and-space-plan.md` §3's file list either); documented in `World.quadtree`'s own KDoc and called out again here. §7 recommends a follow-up issue to add `SpatialIndex`-typed overloads (additive, non-breaking) once the Phase 1 physics/vision systems (#49/#50) need real broad-phase queries against a pluggable index anyway. |
| Default-index regression — any change to `QuadtreeSpatialIndex`'s delegation accidentally alters `5.1.0` query results | `queryBox` delegates to `tree.retrieveBox` verbatim, no reimplementation; every existing `Quadtree`-level test (`QuadtreeTest`, `QuadtreeScalabilityTest`) stays unmodified and green; `WorldSpatialIndexTest` locks in that `World`'s default `quadtree` accessor is the same live instance. |
| Deprecation-warning noise failing a warnings-as-errors build | Checked: no `allWarningsAsErrors`/`-Werror` in any `build.gradle.kts` or the convention plugins — deprecation is a warning only. `@Suppress("DEPRECATION")` added at the two existing test call sites anyway, for a clean build log. |
| `UniformGrid` sized with an inappropriately small `cellSize` relative to query radii/box sizes causing many empty-cell iterations | Documented as the caller's sizing responsibility in `UniformGrid`'s KDoc (mirrors `docs/phase-1-map-and-space-plan.md`'s own framing: "sized for the medium target"); no default `cellSize` is offered, forcing a deliberate choice. |
| Cross-repo impact | None. No wire format, no `GameServer`/`ClientCommand` change. `MyGameServer` (consumer) is unaffected unless it opts into the new surface (no issue filed there, per standing "no downstream consumer issues" guidance). |

---

## 6. Documentation impact

- **Component ring (KDoc)** — every new public/internal declaration
  (`UniformGrid`, `QuadtreeSpatialIndex`, `World.spatialIndex`, `World.quadtree`,
  `World.reindexSpatial`, `VisibleObject.lastIndexedLocation`/`IndexedPosition`) gets full KDoc
  per §2's snippets, following the existing style in `SpatialIndex.kt`/`Quadtree.kt`. **Revised
  addition:** `Quadtree.insert`'s and `Quadtree.remove`'s existing KDoc also gets corrected in
  the same change (§2.5) — `insert`'s currently documents the removed reuse behavior,
  `remove`'s "empty slot for reuse" phrasing becomes "permanent routing waypoint," and the
  class KDoc gains a short note on the dead-node-growth trade-off and `World.reindexSpatial()`
  as the compaction escape hatch, so a future reader of `Quadtree.kt` alone (not just this plan)
  understands the trade-off being made.
- **Architectural ring** — `README.md`:
  - the object-model Mermaid diagram's `World "1" *-- "1" Quadtree` line and the `World` row's
    "rebuilds the `Quadtree` ... each frame" description (`README.md:99,114`) are updated to
    describe the pluggable `spatialIndex` and incremental reconcile instead.
  - the "Layering" table's `Quadtree<N, E>` row (`README.md:117`) gains sibling rows (or an
    expanded description) for `SpatialIndex<E>`, `UniformGrid<E>`, and `QuadtreeSpatialIndex<E>`.
  - the module table's **core** row `Contains` cell (`README.md:127`), which already lists
    `Quadtree`, `SpatialIndex`, `Space` from the #48-bootstrap PR, gains `UniformGrid` and
    `QuadtreeSpatialIndex`.
- `CONTRIBUTING.md` — no change; the module table already lists `gametools-world` and this
  issue adds no module.
- `docs/framework-vision-and-roadmap.md` — no change needed yet; Phase 1 item 3 isn't marked
  done until the whole issue (this PR) merges, which is a release-PR-time edit per
  `docs/phase-1-map-and-space-plan.md` §8, not this feature PR's job.

---

## 7. Breaking-change assessment

**None.** Every change is additive or documentation-only:

- `World.spatialIndex` is a new field, defaulting to a `QuadtreeSpatialIndex` that reproduces
  `5.1.0`'s exact `Quadtree` query semantics (§2.1, §2.2) — an untouched `World` behaves
  identically to today.
- `World.quadtree` is deprecated, not removed, and keeps returning a working index in both the
  default and non-default cases (§2.4) — every existing caller keeps compiling.
- `World.reindexSpatial()` and `World.rebuildQuadtree()`'s removal: `rebuildQuadtree()` was
  `private`, so removing it cannot break any caller outside `World.kt` itself. Confirmed by
  grep — only `WorldTest.kt`/`WorldTickThroughputTest.kt` (both in `gametools-core`, both
  updated by this change) and `docs/*.md` reference `quadtree`/`rebuildQuadtree`; nothing in
  `gametools-net` or the umbrella does, and no other file in this repository references either
  symbol in a way this breaks.
- **Revised:** `Quadtree.kt` gets a **behavioral fix**, not just a KDoc edit (§2.5) — `insert`
  no longer reuses a dead slot's node for an element at different coordinates. This is a bug
  fix (the old behavior silently corrupted `retrieveBox` results after certain
  `remove`-then-`insert` sequences, §1.1.1), not a designed contract any caller could
  correctly have depended on, and no signature changes. It is still called out explicitly
  here, and in CHANGELOG `### Fixed` (§8), rather than folded silently into the KDoc-only
  framing the original plan used — confirmed safe for `Actor.nearby`, `DirectionalProjectile`,
  `HomingProjectile` (none calls `insert`/`remove`, §2.5) and for every other direct `Quadtree`
  caller via grep (§2.5). No Major-version bump: `Quadtree`'s public signatures are unchanged,
  and the fixed behavior is strictly more correct than the old one, not a new incompatible
  contract.
- `Actor.nearby`, `DirectionalProjectile`, `HomingProjectile` are untouched — still compile,
  still behave as today, still only work against a literal `Quadtree` (§5's documented
  limitation, not a regression).

⇒ This lands as part of the `5.2.0` Feature release per `docs/phase-1-map-and-space-plan.md`
§7 — no Major-version bump required for this slice.

---

## 8. Version control

- **Branch:** `feature/48-spatial-index-rework`, cut from current `master`.
- **Commits** (each a coherent, buildable unit; revised to insert the `Quadtree` fix as its own
  commit ahead of the code that depends on its correctness):
  1. `fix(spatial): stop Quadtree.insert reusing dead slots at the wrong position` — the §2.5
     fix (delete the dead-slot-reuse branch), the rewritten `QuadtreeTest.kt` case, the KDoc
     corrections (`insert`/`remove`/the y-axis line), and this plan document (association
     requirement — committed with the first commit of its implementation, since this is now
     where the plan's most consequential change lands).
  2. `feat(spatial): add UniformGrid and QuadtreeSpatialIndex` — the two new
     `SpatialIndex<E>` implementations plus their component/deterministic/nonfunctional tests
     (§3, §4) — already implemented on this branch and unchanged by this revision, landing
     after commit 1 so `QuadtreeSpatialIndexTest`'s `move` test and `SpatialIndexQueryLawsTest`
     pass against the corrected `Quadtree` from the start rather than passing through a
     temporarily-broken intermediate commit.
  3. `feat(gameobjects): wire World.spatialIndex with incremental reconcile` —
     `VisibleObject.lastIndexedLocation`, `World.spatialIndex`/`quadtree`/`reindexSpatial`,
     the `tick()` rewrite, `rebuildQuadtree()` removal, `WorldSpatialIndexTest`, the
     `@Suppress("DEPRECATION")` touch-ups to `WorldTest`/`WorldTickThroughputTest`.
  4. `docs: update README for the spatial-index rework` — §6's README edits.
  5. `docs: changelog entry for the spatial-index rework` — CHANGELOG (§9 below).
- Each commit ends with the repo's standard trailer (`CONTRIBUTING.md`'s Conventional Commits
  convention); reference `(#48)` in each commit subject or body per the existing CHANGELOG
  style already used for the #48-bootstrap entries.
- PR targets `master`, semi-linear merge, per `CONTRIBUTING.md`.

### CHANGELOG.md — `[Unreleased]` additions

Under `### Added`, alongside the existing #48-bootstrap bullet:

> - `UniformGrid<E>` and `QuadtreeSpatialIndex<E>` — the two `SpatialIndex<E>` implementations
>   (#48). `World.spatialIndex` is now pluggable (defaults to `QuadtreeSpatialIndex`, matching
>   `5.1.0`'s exact query behaviour); `World.tick()` reconciles it incrementally instead of
>   rebuilding it from scratch every frame, and `World.reindexSpatial()` is available for a
>   caller that bulk-mutates positions outside of `tick()` or that just replaced `spatialIndex`.
>   `World.quadtree` is deprecated in favour of `spatialIndex` but keeps working. (#48)

Under `### Changed`:

> - `Quadtree`'s KDoc now describes its `+y`-as-north field naming as an internal labelling
>   convention, not a claim about the engine's own coordinate system (the engine is y-down).
>   No behavioural change. (#48)

Under `### Deprecated` (new subsection if the file doesn't have one yet in `[Unreleased]` —
check at write time):

> - `World.quadtree` — use `World.spatialIndex` instead; see the `Added` entry above. (#48)

**Revised — new bullet required under `### Fixed`** (the file already has a `### Fixed`
subsection in `[Unreleased]`, so this joins it rather than creating one):

> - **`Quadtree.insert` no longer silently mis-positions an element reinserted after a
>   `remove`.** Reusing a removed node's dead slot for a different element without updating
>   the slot's stored coordinates meant `retrieveBox` could keep returning that element at its
>   *old* position — most visibly, a `QuadtreeSpatialIndex.move` (`World`'s incremental
>   spatial-index reconcile, added above) would very often leave the moved object indexed
>   where it used to be, not where it moved to. `insert` now always creates a fresh node at
>   the position given; a removed node persists only as a routing waypoint until the tree is
>   next `clear()`ed. Behavioral fix, not a signature change; no caller could correctly have
>   depended on the old, buggy positioning. (#48)

Per this plan's existing commit convention (§8), all CHANGELOG bullets land together in the
final `docs: changelog entry for the spatial-index rework` commit (commit 5) alongside the
other `[Unreleased]` additions for this issue — this `### Fixed` bullet is no exception, kept
consistent with how the `Added`/`Changed`/`Deprecated` bullets above are already sequenced.

---

## 9. Open decisions

**Revised — one new item, not blocking.** The `Quadtree.insert` dead-slot-reuse fix itself
(§2.5) is *not* an open decision: given the invariant `retrieveBox` depends on, and the
rejected alternatives' correctness/performance problems (§2.5), deleting the reuse branch is
the only approach that is both correct and keeps `move`/`insert`/`remove` at `O(depth)` rather
than degrading to `O(n)` per call — confident enough to hand straight back for
re-implementation without waiting on this section.

- **Should a `QuadtreeSpatialIndex`-backed `World` auto-compact (periodically re-run the
  equivalent of `reindexSpatial()`) to bound the dead-node growth §2.5 accepts as a trade-off,
  or is documenting `reindexSpatial()` as a manual escape hatch enough for this issue?**
  Recommendation: **document only, do not add auto-compaction here.** Nothing in issue #48 or
  `docs/phase-1-map-and-space-plan.md`'s acceptance criteria asks for bounded memory under
  sustained movement, `reindexSpatial()` already gives a caller who needs it a one-line fix,
  and auto-compaction raises its own design questions (compact on a node-count threshold? a
  tick-count cadence? does a caller providing a custom `SpatialIndex` want this at all?) that
  are better answered once real telemetry from #49/#50 (physics/vision, the next Phase 1 work)
  shows whether it actually matters at the target entity counts. Revisit as a follow-up issue
  if it does.

Everything else in this plan either follows directly from what's already merged (the
`SpatialIndex<E>` interface, `docs/phase-1-map-and-space-plan.md`'s Open Decision 2 lean of
keeping `QuadtreeSpatialIndex` as `World`'s default in `5.2.0`) or is an implementation detail
with a single defensible answer given the existing code's constraints (the `Point`-aliasing
hazard in §2.3, `Quadtree`'s lack of position recovery forcing the `IdentityHashMap` in §2.1,
matching `Quadtree`'s box convention in `UniformGrid` for exact parity in §2.2). The one
genuine design gap found during research — `Actor.nearby`/`DirectionalProjectile`/
`HomingProjectile` staying hard-coded to `Quadtree`, so a `UniformGrid`-backed `World` doesn't
speed those up — is not blocking (nothing in the issue or the authoritative plan doc's file
list asks for it to be fixed here) and is recorded as a follow-up below rather than an open
decision.

---

## 10. Sequencing & follow-ups

1. Land this issue's PR into `master` as part of the `5.2.0` series (`docs/phase-1-map-and-space-plan.md`
   §8 lists it as the second `feature/48-*` branch, after the already-merged bootstrap PR #61).
2. Recommend a follow-up issue (against `SpartanLabsGaming/MyGameTools`, filed only on
   confirmation per the global "surface issues" guidance) to add `SpatialIndex<VisibleObject>`-typed
   overloads to `Actor.nearby`, `DirectionalProjectile`, and `HomingProjectile` alongside their
   existing `Quadtree`-typed ones (additive, non-breaking) — natural to pick up when #49
   (physics) or #50 (vision) need real broad-phase queries against a pluggable index, since
   both are building `SpatialIndex`-consuming systems anyway.
3. `docs/framework-vision-and-roadmap.md` §3 Phase 1 item 3 gets marked done at `5.2.0`
   release time, alongside items 1–2 (#46, #47) if they've landed by then — a release-PR edit,
   not part of this feature PR (`docs/phase-1-map-and-space-plan.md` §8).
4. Items 4–5 (physics, vision — #49, #50) are the next Phase 1 work and are the actual
   consumers of `spatialIndex.queryBox`/`queryRadius` as a genuine broad phase; nothing here
   blocks them starting once `SpatialIndex`, `UniformGrid`, and `QuadtreeSpatialIndex` exist.
5. If #49/#50 (or a `SpatialIndexScalabilityTest`-style long-run measurement) show
   `QuadtreeSpatialIndex`'s unbounded dead-node growth under sustained `move()` (§2.5, §9)
   actually costs measurable query performance or memory at the target entity counts, file a
   follow-up issue for automatic compaction (only on confirmation, per the global "surface
   issues" guidance) rather than treating `World.reindexSpatial()` as the permanent answer.
