# Plan: `zone-world-system` — `ZoneIndex` is itself the zone `WorldSystem`

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#77](https://github.com/SpartanLabsGaming/MyGameTools/issues/77)
  — *"World Systems Stage 2: wrap ZoneIndex as a WorldSystem"*. Sub-issue of tracking issue #87
  (World Systems).
- **Scope beyond the issue (user decision C9, 2026-09-30).** The issue says *"wrap"* and lists
  *"Out: any change to `ZoneIndex` itself"*. The design no longer wraps: `ZoneIndex` **is** the zone
  `WorldSystem` (extends `AbstractWorldSystem`, claims `ZONE`, public constructor, bookkeeping in
  `step()`, no `refresh` of any visibility). That expands #77 to rewrite `ZoneIndex`. It carries no
  semver weight: `ZoneIndex` (#47) and `WorldSystem` (#76) are unreleased and `5.2.0` will not be
  cut before #77 merges (C12).
- **Scope beyond the issue, again (user decision C22, 2026-10-02).** #77 now **explicitly expands
  to change `ZoneIndex.zoneOf`'s return type**: `zoneOf(entityId)` returns `Result<Zone>` instead of
  `Zone?`, a miss being `Result.failure(UnzonedEntityException(...))` — never a throw, never `null` —
  with `UnzonedEntityException` a new public type in this package. `entitiesIn(zone)` is unchanged.
  This too carries no semver weight, for the same reason: `ZoneIndex` (#47) is unreleased and
  `5.2.0` is not cut before #77 merges (C12).
- **Architecture:** `docs/plans/87-world-systems/77-zone-world-system/architecture.md`, unit `zone-world-system` (§10
  row 2; §1.2 C9, C13, C16–C19, C22; §4 inventory `ZoneIndex` and `UnzonedEntityException` rows;
  §4.3; §4.5; §7.2; §7.4 last row; §7.5; §8; §11.1 unit-2 bullet). Sibling:
  `docs/plans/87-world-systems/77-zone-world-system/plan-world-system-binding.md` (unit `world-system-binding`, lands first). This document **replaces** the superseded #77 plan of the same name (the
  `ZoneWorldSystem` adapter + `internal ZoneIndex` design); its committed predecessor is in git
  history (`7f19cff`).
- **Branch:** `feature/77-zone-world-system` (exists; HEAD `b5e57b0` plus unit 1's commits).
- **Commit:** TBD — this plan is committed **in this unit's first commit** (§8), together with the
  first implementation stage, so `git log --follow` binds the two.
- **PR:** TBD — the single #77 PR shared with unit 1 (`Closes #77`, `Part of #87`).
- **What this plans:** `gametools-world` only. `ZoneIndex` rewritten as a `WorldSystem`
  (`ZoneIndex.kt`), with `zoneOf` returning `Result<Zone>`; the new `UnzonedEntityException`
  (`UnzonedEntityException.kt`); `ZoneWorldSystem` and its four tests folded into `ZoneIndex`-named
  tests at the same levels; the existing zone tests driven through `installSystem` +
  `stepSystems()`, with every `zoneOf` assertion in the `Result` shape; the shared `ZoneFixtures.kt`
  and the test-only opt-in block committed; KDoc cleanup (C13); README / CONTRIBUTING / CHANGELOG
  entries; and the correction of `docs/plans/86-phase-1-map-and-space/49-physics/architecture.md`'s now-false
  `ZoneIndex.refresh` statements.
- **Status:** planning only — **no open decisions remain** (this plan's §10 question was answered by
  the user on 2026-10-02: architecture OD6, C22). No source, test, or build file has been modified by
  this document.
- **Target release:** no version bump here. Both published modules read `5.1.0`
  (`gametools-world/build.gradle.kts:14`); entries land under `CHANGELOG.md` `[Unreleased]`. The
  release that first contains #47 must not be cut before this PR merges (C12; §7 risk 1).

---

## 1. Context

### 1.1 What the requirement is

Acceptance shape (architecture §1.3), restricted to this unit: a `ZoneIndex` installed on a `World`
and driven by `stepSystems()` publishes `EntityChangedZone` exactly as the superseded
`ZoneWorldSystem` did; `world.systemOf<ZoneIndex>()` returns the installed index as a `Result`; a second
`ZoneIndex` on one `World` and a `ZoneIndex` bound to one `World` being installed on another are
both rejected by `World` before any `ZoneIndex` code runs; and `zoneIndex.zoneOf(id)` returns
`Result.success(zone)` for an entity the most recent step placed and otherwise `Result.failure`
carrying an `UnzonedEntityException` for that `id` (C22). Goals:

1. `ZoneIndex(grid)` — public constructor — `: AbstractWorldSystem()`; `coreSlot = ZONE`;
   `uniqueRole = ZoneIndex::class` (C16); `step()` holds the bookkeeping; `zoneOf`/`entitiesIn`
   public, `zoneOf` returning `Result<Zone>` (C22) and `entitiesIn` a `Set` as before; **no**
   `refresh`, **no** `onInstalled` (installing publishes nothing).
2. State retained across uninstall/re-install; the first step after a re-install publishes what
   changed while uninstalled (§7.2 of the architecture).
3. Every coverage item the four untracked `ZoneWorldSystem*Test` files held survives, folded into
   `ZoneIndex`-named tests at the same levels (§6.2 maps them one by one).
4. C13 housekeeping lands: `ZoneFixtures.kt` is the shared component fixture; the unused
   `GameEvent` import at `EntityChangedZoneTest.kt:7` (HEAD) goes; the `docs/` pointers in
   `ZoneGrid.kt` KDoc go.
5. C22 lands: `UnzonedEntityException` exists — extends `NoSuchElementException`, carries
   `val entityId: EntityId`, stackless, class-level `@ExperimentalGameToolsApi` like `ZoneIndex` —
   built in the one shape it shares with unit 1's `MissingWorldSystemException` (architecture §4.5);
   every `zoneOf` call site in the tests reads the `Result`; `UnzonedEntityException` has its own
   component tests.

### 1.2 What is in the tree (verified 2026-09-30, `git status`, `git show HEAD:…`)

- **`ZoneIndex.kt` at HEAD** (`gametools-world/src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneIndex.kt`,
  69 lines): `class ZoneIndex(private val grid: ZoneGrid)` with a public `refresh(world: World)`
  (`:38-62`), `zoneOf` (`:64-65`), `entitiesIn` (`:67-68`), and KDoc telling consumers to call
  `refresh` once per frame (`:10-17`). Its bookkeeping — `indexed: MutableMap<EntityId,
  Pair<GameObject, Zone>>` and `entitiesByZone: MutableMap<Zone, MutableSet<EntityId>>`
  (`:20-24`) — is keyed by `EntityId`, has no reset, and is written only by `refresh`. `refresh`
  reads only `world.gameObjects`, `obj.location`, `obj.entityId` and `world.events` (never
  `world.spatialIndex`). Every `World` numbers entities from `1` (`World.kt:135,173`), so two
  `World`s hand out the same raw `EntityId`s — the whole reason bind-for-life matters here.
- **The working tree is disposable for this unit.** It holds the superseded implementation:
  untracked `ZoneWorldSystem.kt` and four `ZoneWorldSystem*Test.kt`, an `internal` `ZoneIndex`
  constructor/`refresh`, rewritten tests, KDoc edits in `EntityChangedZone.kt`/`ZoneGrid.kt`/
  `CoreSystemSlot.kt`, and README/CHANGELOG/CONTRIBUTING hunks. **Unit 1's Step 0** parks all of
  them in one path-limited stash — the tracked ones return to HEAD and the untracked
  `ZoneWorldSystem*` files leave the tree; it keeps untracked `ZoneFixtures.kt` and the uncommitted
  `gametools-world/build.gradle.kts` opt-in block (`:21-26`) for this unit to commit. **This plan
  therefore starts from HEAD's `gametools-world`** plus those two. `ZoneWorldSystem.kt` and its four
  tests were never committed, so they leave no trace in any commit (only in the Step-0 stash); this
  plan confirms their absence (§3.5) rather than removing them.
- **Uncommitted `docs/*.md` hunks are planner-owned (architecture §11.2) — never staged by this
  unit.** `git add` with explicit paths only (§8).
- **Zone tests at HEAD** (all under
  `gametools-world/src/test/kotlin/com/spartanlabs/gaming/testing/`, all calling
  `ZoneIndex(grid)` + `index.refresh(world)`): `component/world/zone/ZoneIndexTest.kt` (7 tests),
  `component/world/zone/EntityChangedZoneTest.kt` (4), `deterministic/world/zone/ZoneIndexRefreshDeterminismTest.kt` (1),
  `integration/world/zone/ZoneRefreshWorldIntegrationTest.kt` (1), `e2e/world/zone/ZoneDrivenSimulationE2ETest.kt` (1).
  `component/world/zone/ZoneGridTest.kt` and `deterministic/world/zone/ZoneGridPartitionLawsTest.kt`
  never touch `ZoneIndex` and are unchanged.
- **`zoneOf` call sites (verified 2026-10-02, `git grep -n zoneOf HEAD -- gametools-world`).** At
  HEAD: the declaration (`ZoneIndex.kt:65`), **9 call sites** in 3 test files —
  `EntityChangedZoneTest.kt:107`; `ZoneIndexTest.kt:45`, `:59`, `:78`, `:94`, `:110`, `:129`;
  `ZoneDrivenSimulationE2ETest.kt:71`, `:72` — and 2 test names that mention it
  (`ZoneIndexTest.kt:41`, `:98`); no main-source caller. The working tree shows 22 lines: Step 0
  parks the 9 in `ZoneWorldSystem.kt` and the three `ZoneWorldSystem*Test.kt` files that mention it
  (8 call expressions on 7 lines — `ZoneWorldSystemTest.kt:63`, `:109` ×2, `:128`, `:154`;
  `ZoneWorldSystemSimulationLoopE2ETest.kt:74-75`; `ZoneWorldSystemOrderingIntegrationTest.kt:155` —
  one test name, `ZoneWorldSystemTest.kt:67`, and one KDoc line in `ZoneWorldSystem.kt`) and resets
  one KDoc line in `ZoneIndex.kt`, leaving HEAD's 12. §6 puts every surviving assertion, and every
  folded one, in the `Result` shape (C22).
- **Test infrastructure.** No MockK anywhere in the repo; tests are `kotlin.test` on the JUnit
  platform, with hand-rolled fakes (nothing external to mock at level 2). Level tasks filter on the
  package segment (`build-logic/src/main/kotlin/gametools.kotlin-library.gradle.kts:66-70`:
  `componentTest` ↔ `testing.component.*`, …). No `testing.gating` or `testing.uat` package exists
  in the repo and none is invented here (§6.1, §6.7).
- **No other consumer.** Nothing in `gametools-core`, `gametools-net`, `gametools` (umbrella) or
  `website/` (beyond the two `index.html` mentions `:176`, `:293`, which list the type name and
  stay valid) references `ZoneIndex`'s API. No cross-repo surface.

### 1.3 What this unit consumes from unit 1 (contract, used exactly as given)

```kotlin
// gametools-core, com.spartanlabs.gaming.gameobjects
@SubclassOptInRequired(ExperimentalGameToolsApi::class)
interface WorldSystem {
    val world: World
    fun onInstalled() {}
    fun onUninstalled() {}
    fun step() {}
    @ExperimentalGameToolsApi val coreSlot: CoreSystemSlot? get() = null
    @ExperimentalGameToolsApi val uniqueRole: KClass<out WorldSystem>? get() = null
}
@SubclassOptInRequired(ExperimentalGameToolsApi::class)
abstract class AbstractWorldSystem : WorldSystem {   // no-arg constructor
    final override val world: World                   // IllegalStateException before the first installSystem call whose checks pass
    @JvmSynthetic internal fun bindTo(world: World)   // gametools-core internal — ZoneIndex never calls it
    @JvmSynthetic internal fun boundWorldOrNull(): World?
}
// World (final), each @ExperimentalGameToolsApi:
fun installSystem(system: WorldSystem)      // IAE: already installed; bound to another World; slot occupied (checked BEFORE the role); role taken
fun uninstallSystem(system: WorldSystem)    // idempotent; binding kept
val installedSystems: List<WorldSystem>
fun stepSystems()                           // tier 1 by CoreSystemSlot.order (PHYSICS, ZONE), then tier 2 in install order
fun <T : WorldSystem> systemOf(role: KClass<T>): Result<T>   // exact key on the recorded uniqueRole; miss = Result.failure (C18) carrying unit 1's MissingWorldSystemException (C21)
inline fun <reified T : WorldSystem> systemOf(): Result<T>   // the reified form (C19) = systemOf(T::class)
```

This unit's tests read `systemOf` only through `getOrNull()` / `isFailure`, so none of them references
`MissingWorldSystemException`; asserting the miss type is unit 1's (its §6.2.1 test 3).

**Opt-in propagation (architecture §8 — confirmed).** `ZoneIndex` subclasses a class annotated
`@SubclassOptInRequired(ExperimentalGameToolsApi::class)`. Kotlin's opt-in documentation gives
three ways to satisfy that requirement; the third — *"use an experimental marker annotation to
propagate the requirement further to any uses of the class in your code"* — is annotating the
subclass with the marker itself, i.e. `@ExperimentalGameToolsApi class ZoneIndex …`. That one
annotation (a) satisfies the subclass requirement, (b) opts the body in to the overridden
`coreSlot`/`uniqueRole` (both carry the marker), to `World`'s marked members, and to constructing
the marked `UnzonedEntityException` inside `zoneOf` (compile-verified on Kotlin 2.2.0, architecture
§4.5), and (c) propagates the requirement to every user of `ZoneIndex`: constructing it, calling
`step`/`zoneOf`/`entitiesIn`, holding its type. Consequence, recorded once (architecture §8):
**`zoneOf`, `entitiesIn` and `UnzonedEntityException` need opt-in until #79**. The same mechanism
was compile-verified for an implementor of `WorldSystem` in the superseded plan; the Level-1 check
"main compiles with no opt-in flag" (§6.1) re-proves it for `AbstractWorldSystem`. No `@OptIn`, no `@SubclassOptInRequired` on `ZoneIndex` (it is `final`, so
there are no subclasses to propagate to).

---

## 2. Design

`ZoneIndex` becomes a final, concrete `AbstractWorldSystem`. Everything that used to be the job of
`ZoneWorldSystem` moves to something else that already exists:

| Old `ZoneWorldSystem` duty | Now done by |
|---|---|
| Bind-for-life guard (`boundWorld`, `check`) | `AbstractWorldSystem` + `World.installSystem` (`IllegalArgumentException`) — nothing in `ZoneIndex` |
| Build its own `ZoneIndex` so none is shared | Gone — the system *is* the index; a `ZoneIndex` cannot be shared between `World`s because it is bound to one |
| `installOn` does not refresh | No `onInstalled` override exists; the default is a no-op |
| `uninstallFrom` keeps the binding | `World.uninstallSystem` keeps it (bind-for-life); no `onUninstalled` override |
| `step(world)` → `zoneIndex.refresh(world)` | `ZoneIndex.step()` *is* the refresh body, reading the inherited `world` |
| Slot claim | `override val coreSlot = CoreWorldSystemSlot.ZONE` |
| (new, C16) | `override val uniqueRole = ZoneIndex::class` |
| (new, C22) | `zoneOf` returns `Result<Zone>`; a miss carries the new `UnzonedEntityException` (§3.7) |

```mermaid
sequenceDiagram
    participant Caller
    participant A as World A
    participant ZI as ZoneIndex
    participant B as World B

    Caller->>A: installSystem(zi)
    A->>A: checks (not installed, unbound, ZONE free, role free) then bind, record
    Note over A,ZI: no onInstalled override - nothing is published
    Caller->>A: systemOf<ZoneIndex>()
    A-->>Caller: Result.success(zi)

    loop once per frame
        Caller->>A: stepSystems()
        A->>ZI: step()  [tier 1, ZONE - after any PHYSICS-slot system]
        ZI->>ZI: reconcile world.gameObjects against grid
        ZI-->>A: world.events.publish(EntityChangedZone)
    end

    Caller->>ZI: zoneOf(id)
    ZI-->>Caller: Result.success(zone), or Result.failure(UnzonedEntityException(id)) - never throws, never null

    Caller->>B: installSystem(zi)
    B-->>Caller: IllegalArgumentException (bound to A) - B records nothing
    Caller->>A: uninstallSystem(zi)
    Note over ZI: still bound to A; bookkeeping retained
    Caller->>B: installSystem(zi)
    B-->>Caller: IllegalArgumentException (bound for life)
    Caller->>A: installSystem(zi)
    Note over A: accepted; the next stepSystems() publishes what changed while uninstalled
    Caller->>A: installSystem(ZoneIndex(grid))
    A-->>Caller: IllegalArgumentException naming the ZONE slot (slot check runs before the role check)
```

### 2.1 Decisions made within the architecture (none re-litigates C1–C22)

- **Body of `step()` is HEAD's `refresh` body, unchanged in logic**, with the `world` parameter
  replaced by the inherited property read **once** into a local at the top
  (`val world = this.world`). One read means one failure point: a `step()` on an index that was
  never installed throws the base class's `IllegalStateException` before `gameObjects` is touched
  and before any bookkeeping is mutated. (Nothing else is refactored: zone logic stays byte-for-
  byte comparable so the existing tests prove it unchanged.)
- **`zoneOf` / `entitiesIn` never touch `world`**, so they work on a never-installed index (`zoneOf`
  a failure carrying `UnzonedEntityException`, `entitiesIn` empty). A test locks that (§6.2), so
  nobody later "tidies" them into reading `world`.
- **`zoneOf`'s `Result` shape (C22).** The body becomes
  `indexed[entityId]?.second?.let { Result.success(it) } ?: Result.failure(UnzonedEntityException(entityId))`
  — still one map lookup; nothing else in `ZoneIndex` changes (`step()` reads its own map, never
  `zoneOf`). One miss covers every way an id can be unplaced: never seen by a step, outside the
  grid's extent, removed from the `World`, never numbered (`EntityId.UNASSIGNED`), or never assigned
  at all. `zoneOf` thereby matches its package-mate `ZoneGrid.zoneAt`, already `Result<Zone>`.
- **`UnzonedEntityException` is built in architecture §4.5's shape — the one unit 1's
  `MissingWorldSystemException` has, property for property:** `final` (the library is its only
  producer — not an extension point); a public constructor taking only the `entityId`; the message
  built inside the type from `EntityId`'s own `toString()` (`entity #7 is not placed in any zone`;
  `#0` for `UNASSIGNED`); no cause; `override fun fillInStackTrace(): Throwable = this`, so no stack
  trace. The user decided its name, parent, payload, stacklessness, package and tier (C22); the rest
  are planner calls the user can overturn in review.
- **Tests read `zoneOf` without ever throwing** (§6, "Misses through one helper"): a position
  assertion reads `zoneOf(id).getOrNull()`, so a miss fails as "expected <zone> but was null"; a miss
  assertion goes through a file-private `assertUnzoned(id)` helper per test class. No assertion calls
  `getOrThrow()` on `zoneOf`: a stackless exception escaping a test would point nowhere.
- **`step()` is public** (it is the interface method) and therefore directly callable. KDoc says
  `World.stepSystems()` is the entry point and direct calls are not part of the usage contract;
  behaviour is still defined: before the index is bound (by the first `installSystem` call whose
  checks pass) → `IllegalStateException`; afterwards → one extra pass against the bound `World`,
  even if uninstalled. Not guarded (the hook contract gives
  no way to distinguish the caller, and an extra pass is idempotent over unchanged positions).
- **No `refresh` anywhere.** A reflection test (§6.2) asserts `ZoneIndex` declares no method whose
  name starts with `refresh` (catches the `internal` mangled form `refresh$gametools_world` too).
- **No logger is added.** Justification under "Logging" (§3.1).
- **Stability.** `ZoneIndex` is class-level `@ExperimentalGameToolsApi` until #79 (C9), then
  untagged Stable Core; `UnzonedEntityException` carries the same class-level marker and graduates
  with it (C22). `ZoneIndex` is `final` and is the worked example of `AbstractWorldSystem`, so its
  KDoc is written to read as one. Substitution is by implementing `WorldSystem` (or extending
  `AbstractWorldSystem`) and claiming `ZONE`; such a substitute is **not** found by
  `systemOf(ZoneIndex::class)` (exact-key matching, architecture §4.4) and is rejected while a
  `ZoneIndex` holds the slot — documented in KDoc, locked by a test (§6.2).
- **Adoption verdict (architecture §7.2/§7.4).** No production code outside `world.zone`
  references `ZoneIndex` (§1.2), so there is nothing else to migrate. `systemOf` has no production
  caller; its first call sites are the tests in §6. Phase 3 interest filtering and #50 vision are
  the named follow-ups that should use `systemOf<ZoneIndex>()` (§10).

---

## 3. File-by-file changes (all in `gametools-world` unless stated)

### 3.1 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneIndex.kt` — rewritten

Starts from HEAD's file. Imports follow the repo's numbered region scheme (alphabetical inside a
group; unused imports removed — `World` stays imported because the KDoc links `World.installSystem`,
`World.stepSystems` and `World.systemOf`; `UnzonedEntityException` is in this package and needs no
import):

```kotlin
package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion
```

Declaration and signatures:

```kotlin
@ExperimentalGameToolsApi
class ZoneIndex(private val grid: ZoneGrid) : AbstractWorldSystem() {

    private val indexed: MutableMap<EntityId, Pair<GameObject, Zone>> = HashMap()        // unchanged
    private val entitiesByZone: MutableMap<Zone, MutableSet<EntityId>> = HashMap()        // unchanged

    override val coreSlot: CoreSystemSlot = CoreWorldSystemSlot.ZONE
    override val uniqueRole: KClass<out WorldSystem> = ZoneIndex::class

    override fun step() { /* HEAD's refresh body, reading a local `val world = this.world` */ }

    fun zoneOf(entityId: EntityId): Result<Zone> =                                    // C22 (was Zone?)
        indexed[entityId]?.second?.let { Result.success(it) }
            ?: Result.failure(UnzonedEntityException(entityId))                       // §3.7; never thrown here
    fun entitiesIn(zone: Zone): Set<EntityId>        // unchanged body
}
```

Removed: `fun refresh(world: World)` (public at HEAD). Changed: `zoneOf` returns `Result<Zone>` instead
of `Zone?` (C22). Not added: `onInstalled`, `onUninstalled`.

**Error handling, per signature.**

| Member | Returns / throws | Expected failures (→ `Result`) | Programmer errors (→ throw) | Unwrapped where |
|---|---|---|---|---|
| `ZoneIndex(grid)` | instance | none | none (`ZoneGrid` validated itself) | — |
| `coreSlot`, `uniqueRole` | constants, read once by `World` at install | none | none | — |
| `step()` | `Unit` | A point outside the grid's extent is an expected state: `grid.zoneAt(obj.location, clamped = false)` returns `Result<Zone>`, unwrapped **inside `step()`** with `.getOrNull()` (HEAD behaviour) so "no zone" is `null`, never an exception | `IllegalStateException` (from the inherited `world` getter) if stepped before the first `installSystem` call whose checks pass — thrown, not `Result` | `getOrNull()` in `step()`; a listener that throws is caught and logged by `EventBus`, never reaching `step()`; any other exception from `step()` propagates to the `World.stepSystems()` caller (hook contract) |
| `zoneOf(entityId)` | `Result<Zone>` (C22): `Result.success(zone)` for an entity the most recent step placed; otherwise `Result.failure(UnzonedEntityException(entityId))` — never a throw, never `null` | "not placed in any zone" is an expected outcome → `Result.failure` carrying `UnzonedEntityException`, which names the `entityId` and has no stack trace (so a miss costs one allocation) | none | by the caller: `getOrNull()` for "its zone, if any", `fold`/`onSuccess` to branch, `getOrThrow()` only where an unplaced id is a programmer error (the exception then propagates with no frames — its message names the id) |
| `entitiesIn(zone)` | fresh `Set<EntityId>` copy, empty if none (unchanged — C22 leaves it as is) | — | none | — |

**Mutability and concurrency.** `grid` is an immutable `val` (`ZoneGrid` is all-`val` — the fact
that lets one grid back any number of indexes across `World`s). `indexed`/`entitiesByZone` are
private mutable `HashMap`s written only inside `step()`; read-only queries return copies. Single-
threaded, on the thread driving the bound `World` (hook contract); no synchronisation. The
properties `coreSlot`/`uniqueRole` are initialised after `AbstractWorldSystem`'s constructor runs;
`World` reads them only from `installSystem`, never from a constructor (unit 1 guarantees the base
class's `init` reads no open member — §9).

**KDoc (Level 2) — what must be in it.** Final text belongs to the implementer; these are the
required points, all in published KDoc with **no `docs/` pointers and no issue numbers**:

- *Class:* the zone system; what it tracks and publishes (`EntityChangedZone` on `World.events`);
  install with `World.installSystem`, updated once per `World.stepSystems()`, always after any
  `PHYSICS`-slot system whatever the install order (it claims `ZONE`); installing does not publish
  — the first step places every entity; there is no separate refresh. **One `ZoneIndex`, one
  `World`, for life:** its bookkeeping is keyed by `EntityId`, every `World` numbers entities from
  the same start, and the bookkeeping cannot be reset, so an index that has seen one `World` would
  silently mix its state into another's — a second `World` is rejected with
  `IllegalArgumentException` (while installed or after uninstalling); re-installing on the same
  `World` is fine and its next step publishes what changed meanwhile; another `World` needs another
  `ZoneIndex`; the same `ZoneGrid` can back both. The bound `World` stays reachable from the index
  after uninstall (create an index per `World`; do not keep one in a static). Code that holds only
  a `World` gets the installed index from `world.systemOf<ZoneIndex>()` — a `Result`, a failure
  when none is installed — exact key: a substitute `ZONE` system is not found under it, and is
  rejected while a `ZoneIndex` holds the slot. Experimental paragraph in the repo's house wording
  ("may change incompatibly in a Feature release until it graduates; see
  [ExperimentalGameToolsApi]"); using `ZoneIndex`, including `zoneOf`/`entitiesIn`, needs opt-in.
  `@param grid`.
- *`indexed`/`entitiesByZone` (inner-core comments, WHY):* keyed by `EntityId`, no reset, valid for
  exactly one `World` — which is why the binding is for life — and deliberately retained across
  uninstall/re-install.
- *`coreSlot` / `uniqueRole`:* one line each (slot ⇒ always after `PHYSICS`; role ⇒ at most one per
  `World` and the key `systemOf` uses).
- *`step()`:* the recompute-and-diff contract (enter `from = null`, cross, leave the extent
  `to = null`, leave the `World` `to = null` naming the last-known reference, skip
  `EntityId.UNASSIGNED`); called by `World.stepSystems()`; `@throws IllegalStateException` if the
  index was never installed.
- *`zoneOf`:* the zone [entityId] was placed in by the most recent step (was "refresh"), as a
  [Result]. `@return` "[Result.success] with that [Zone], or [Result.failure] carrying an
  [UnzonedEntityException] for [entityId] if the most recent step placed it in no zone — because it
  is outside the grid's extent, has left the [World], is not yet numbered ([EntityId.UNASSIGNED]),
  was never seen, or the index has not stepped yet". Never throws, never returns `null`; a miss
  allocates one stackless exception. Works before install (a failure). `@param entityId`.
- *`entitiesIn`:* the entities the most recent step placed in [zone]; a fresh copy, empty if none;
  works before install (empty). Unchanged by C22.
- *Region markers:* none needed (one small class).

**Logging.** slf4j is not on `gametools-world`'s main path today (zero `LoggerFactory` occurrences
in its main source) and this unit does not introduce it. Lifecycle events are logged by `World`
(`installSystem`/`uninstallSystem` at `INFO`, roll-back — unit 1). The per-transition data flow is
observable as `EntityChangedZone` on the bus; a log line per transition or per `step()` would be
noise at tick rate. There is no failure path of `ZoneIndex`'s own to log (`step()` is total over
in-memory state; the one programmer error is an exception whose message is the diagnostic). Tests
run under the logback that `gametools.kotlin-library` already supplies.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.1 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneIndex.kt` — rewritten".

### 3.2 `src/main/kotlin/com/spartanlabs/gaming/world/zone/EntityChangedZone.kt` — KDoc only

All line numbers are HEAD's. Wording "refresh" → "step"; `[ZoneIndex.refresh]` →
`[ZoneIndex.step]` (public override, so the link resolves and Dokka documents it).

- `:10-12` — *"A [ZoneIndex.step] found [entity]'s zone membership had changed since the previous
  step, and published this on the owning [World]'s [World.events] bus."*
- `:15-16` — "(its first step while inside the grid's extent)".
- `:29-31` — *"… publishes one of these on every [ZoneIndex.step] it crosses on."*
- `:34-35` — `@property from` "the zone [entity] was in before this step"; `@property to` "after this
  step".

`:7`/imports unchanged (`GameEvent`, `GameObject`, `World` are all used). No code change.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.2 `src/main/kotlin/com/spartanlabs/gaming/world/zone/EntityChangedZone.kt` — KDoc only".

### 3.3 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneGrid.kt` — KDoc only (C13)

- `:21-22` — drop the pointer: *"… irregular zones are a later addition behind [zoneAt]'s existing
  contract."* (was: *"… existing contract (`docs/plans/86-phase-1-map-and-space/plan.md` Open Decision 11)."*).
- `:24-26` — drop *"(see the class's risk note in the plan this package implements)"*; the sentence
  ends after "a map mutated afterwards". Append one contract sentence the new tests lean on: *"A
  [ZoneGrid] never changes after construction, so one grid can safely back any number of
  [ZoneIndex]es, across [com.spartanlabs.gaming.gameobjects.World]s."* (fully-qualified link, so
  the file gains no import for a doc-only reference.)
- `:70-71` — *"… the contract [ZoneIndex.step] relies on, since silently clamping a departing
  entity to an edge zone would defeat the point of reporting that it left."*

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.3 `src/main/kotlin/com/spartanlabs/gaming/world/zone/ZoneGrid.kt` — KDoc only (C13)".

### 3.4 `build.gradle.kts` — commit the existing uncommitted block

The test-only `compileTestKotlin` opt-in block (already in the working tree, `:21-26`, placed after
`mavenPublishing {}` and before `dokka {}`, mirroring `gametools-core`'s) is **retained unchanged**
and committed by this unit (architecture §7.2). It stays until #79 removes it. Never add an opt-in
to the main source set.

### 3.5 Test sources (details and behaviours in §6)

| File (under `src/test/kotlin/com/spartanlabs/gaming/testing/`) | Change |
|---|---|
| `component/world/zone/ZoneFixtures.kt` | **Committed as-is** (untracked today, kept by unit 1). `internal class FixtureSpace`, `internal fun fixtureGrid(columns = 4, rows = 3)`, `internal fun recorder(world)`. References no Experimental API, so #79 never has to touch it. |
| `component/world/zone/ZoneIndexTest.kt` | Reworked: shared fixture, installs the index, steps via `world.stepSystems()`. |
| `component/world/zone/ZoneIndexWorldSystemTest.kt` | **New** — the folded `ZoneWorldSystemTest` plus the C16/`systemOf` coverage. |
| `component/world/zone/EntityChangedZoneTest.kt` | Reworked; `GameEvent` import (`:7`) removed; shared fixture. |
| `component/world/zone/UnzonedEntityExceptionTest.kt` | **New** (C22) — the exception's own five properties (§6.2). |
| `integration/world/zone/ZoneRefreshWorldIntegrationTest.kt` → `ZoneIndexWorldIntegrationTest.kt` | `git mv` + reworked. |
| `integration/world/zone/ZoneIndexOrderingIntegrationTest.kt` | **New** — the folded `ZoneWorldSystemOrderingIntegrationTest`. |
| `deterministic/world/zone/ZoneIndexRefreshDeterminismTest.kt` → `ZoneIndexDeterminismTest.kt` | `git mv` + reworked; absorbs `ZoneWorldSystemDeterminismTest`. |
| `e2e/world/zone/ZoneDrivenSimulationE2ETest.kt` | Reworked in place (name still accurate). |
| `e2e/world/zone/ZoneIndexSimulationLoopE2ETest.kt` | **New** — the folded `ZoneWorldSystemSimulationLoopE2ETest`. |

**Renames, decided.** "Refresh" no longer describes the integration and deterministic tests (there
is no refresh), so both are renamed with `git mv` (history-following). `ZoneDrivenSimulationE2ETest`
keeps its name (it describes the hand-rolled driver, not the method). The four `ZoneWorldSystem*`
names disappear because the type does.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.5 Test sources (details and behaviours in §6)".

**Confirming absence.** After unit 1's Step 0, and again before each commit, none of
`gametools-world/src/main/kotlin/…/zone/ZoneWorldSystem.kt` and
`…/testing/{component,deterministic,e2e,integration}/world/zone/ZoneWorldSystem*Test.kt` may exist
(`git status` shows no `??` for them; `grep -r ZoneWorldSystem gametools-world` returns nothing).

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.5 Test sources (details and behaviours in §6)".

### 3.6 Documentation files — see §5.

### 3.7 `src/main/kotlin/com/spartanlabs/gaming/world/zone/UnzonedEntityException.kt` — new (C22)

(Numbered last so that §3.4, which other documents cite, keeps its number.)

```kotlin
package com.spartanlabs.gaming.world.zone

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.EntityId
//endregion

@ExperimentalGameToolsApi
class UnzonedEntityException(val entityId: EntityId) :
    NoSuchElementException("entity $entityId is not placed in any zone") {   // EntityId.toString() is "#<raw>"

    // Throwable's constructor calls this before `entityId` is assigned, so it must read nothing.
    override fun fillInStackTrace(): Throwable = this
}
```

- **The user's (C22):** the name, the package, the `NoSuchElementException` parent,
  `val entityId: EntityId`, stackless, `@ExperimentalGameToolsApi` — `ZoneIndex`'s own tier after the
  rework (class-level Experimental until #79, C9; verified against §3.1) — graduating with `ZoneIndex`
  at #79.
- **The planner's — architecture §4.5's shape, identical to unit 1's `MissingWorldSystemException`:**
  `final` (the library is the only producer and consumers catch the type, so it is not an extension
  point; a consumer's substitute `ZONE` system that wants to report the same miss constructs one); a
  public constructor taking only the `entityId`; the message built here from `EntityId`'s own
  `toString()` (`entity #7 is not placed in any zone`); no cause.
- `NoSuchElementException` is the `kotlin` typealias of `java.util.NoSuchElementException` (no import).
  Mutability: immutable (one `val`; `EntityId` is a value class, stored unboxed). Concurrency: none.
  Errors: none of its own. Logging: none (slf4j stays off `gametools-world`'s main path, §3.1).
- Opt-in: `ZoneIndex`'s class-level marker lets `zoneOf` construct it with no `@OptIn`; any other use
  (`is`, `catch`, a constructor call) needs the opt-in — compile-verified on Kotlin 2.2.0 (architecture
  §4.5). For Java, its constructor and `entityId` getter are value-class-mangled (`getEntityId-<hash>`)
  — the same reason `zoneOf` itself was already mangled before C22.
- **KDoc (Level 2), with no `docs/` pointers and no issue numbers:** what it signals — a
  [ZoneIndex.zoneOf] lookup found [entityId] in no zone as of the index's most recent step (outside
  the grid's extent, gone from the `World`, not yet numbered, never seen, or not yet stepped). It is the
  *value* of an expected outcome: `zoneOf` never throws it but returns it inside [Result.failure]; it is
  thrown only by a caller that unwraps with `getOrThrow()`. It is a [NoSuchElementException], so a
  handler for that type handles it too. **Stackless by design**: no stack trace is recorded, so a miss
  costs one allocation; diagnose from the message and [entityId]. The Experimental paragraph in house
  wording ("may change incompatibly in a Feature release until it graduates; see
  [ExperimentalGameToolsApi]"). `@property entityId` — the id that was looked up. `@see ZoneIndex.zoneOf`.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §3.7 `src/main/kotlin/com/spartanlabs/gaming/world/zone/UnzonedEntityException.kt` — new (C22)".

---

## 4. Staging

Four landable stages inside the unit's commits (§8). Every commit compiles and passes
`./gradlew componentTest deterministicTest`:

1. **Groundwork** (commit 1) — plan document, `ZoneFixtures.kt`, the opt-in block. Compiles against
   HEAD's `gametools-world` (nothing uses the fixture yet; the opt-in flag is harmless unused).
2. **The miss type** (commit 2) — `UnzonedEntityException.kt` and `UnzonedEntityExceptionTest` (C22). Compiles
   against HEAD's `gametools-world` and needs nothing from unit 1: `EntityId` and
   `ExperimentalGameToolsApi` both exist at HEAD. Nothing in main uses it yet. Landing it before the
   rework lets the repo's first-of-its-kind type be reviewed on its own, beside unit 1's
   `MissingWorldSystemException`.
3. **The rework, atomic** (commit 3) — `ZoneIndex.kt` (as the system, and with `zoneOf` returning
   `Result<Zone>`) + the `step` KDoc in `EntityChangedZone.kt`/`ZoneGrid.kt` + every test. It cannot be split: removing `refresh`
   breaks all five existing test classes at once, the new tests need the new `ZoneIndex`, and the
   `zoneOf` assertions the rework rewrites anyway are the ones C22 reshapes — so `zoneOf`'s new shape
   rides here rather than costing a second pass over the same tests. (A separate earlier commit would
   also need a temporary main-code `@OptIn`: HEAD's untagged `ZoneIndex` could not construct the
   Experimental exception without one.)
4. **Docs** (commits 4–7) — the `ZoneGrid` KDoc `docs/`-pointer cleanup (C13), README and
   CONTRIBUTING, CHANGELOG, `docs/plans/86-phase-1-map-and-space/49-physics/architecture.md`.

---

## 5. Documentation impact

**Rings.** (1) Inner Core — import-group regions in every touched file; WHY comments on the
bookkeeping and on `fillInStackTrace` reading nothing. (2) Component — the `ZoneIndex`,
`UnzonedEntityException`, `EntityChangedZone`, `ZoneGrid` KDoc (§3), the Experimental paragraph,
`zoneOf`'s `@return`, `@throws`. (3) Boundary — the `EntityChangedZone` event on `World.events`
is the integration contract; its KDoc is the only place it is documented (§3.2). No wire or
serialization change. (4) Architectural — README, CHANGELOG, and the `issue-49` callout below.
Published KDoc carries no `docs/` pointers or issue numbers (consumers read the Dokka jar).

**README currency: yes — this change alters a module's contents and a usage pattern, so the README
updates in the same PR (this unit's part).** Line numbers are HEAD's; unit 1 edits earlier lines of
the same file, so locate each edit by its text.

- **Modules table, world row (`README.md:161`).** Replace
  `` `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47) `` with
  `` `Zone`, `ZoneGrid`, `ZoneIndex` (the zone `WorldSystem`, Experimental), `UnzonedEntityException` (Experimental), `EntityChangedZone` (#47, #77) ``.
  No `ZoneWorldSystem`. (The row lists the zone package's public types exhaustively, so the new type
  joins it.)
- **Features, installed-systems bullet (`:180`) — only the ZoneIndex clause.** Unit 1 rewrites the
  rest of the bullet. Unit 1 leaves the bullet with no zone clause and
  the anchor `(`CoreWorldSystemSlot.PHYSICS`, then `ZONE`)`; insert at that anchor, so it reads
  `(`CoreWorldSystemSlot.PHYSICS`, then `ZONE`; `gametools-world`'s `ZoneIndex` is the first
  shipped one, claiming `ZONE`)`. If the anchor moved, keep the meaning: *ZoneIndex is the first
  shipped system and it claims `ZONE`*.
- **Features, zone bullet (`:194`).** Replace the `ZoneIndex` sentence and the closing sentence:

  > … (`ZoneGrid(space, columns, rows)`, `zoneAt(point, clamped)`). `ZoneIndex(grid)` (Experimental)
  > is the zone system: install it on the `World` with `installSystem` and it tracks which zone
  > each `World`-owned object falls in from the tier-1 `ZONE` slot on every `stepSystems()` call
  > (after any physics system), publishing `EntityChangedZone` on `World.events` for every
  > entering, crossing, or leaving transition; installing publishes nothing, and there is no
  > separate refresh call. Read it on the index you installed — `zoneOf(entityId)` returns a
  > `Result<Zone>`, a failure carrying `UnzonedEntityException` when the entity is in no zone, and
  > `entitiesIn(zone)` a `Set`, empty when nobody is there — or, from code that holds only the
  > `World`, get the index with `world.systemOf<ZoneIndex>()` (or `world.systemOf(ZoneIndex::class)`)
  > — a `Result` that is a failure if none is installed. One `ZoneIndex` per `World`, for life
  > (another `World` needs another `ZoneIndex`; one `ZoneGrid` can back both). This is the seam
  > Phase 3 interest filtering and Phase 5 zone save/load build on.

  (Settled by C18/C19 on 2026-10-01 and C22 on 2026-10-02. It names this unit's
  `UnzonedEntityException`; the `systemOf` miss type, `MissingWorldSystemException`, is named once,
  in unit 1's `:180` bullet.)

  The Experimental opt-in sentence already lives in the `:180` bullet; the zone bullet adds
  "using `ZoneIndex`, including its reads, needs the same opt-in".

- **`CONTRIBUTING.md:36`** (world row) lists the zone package's types exhaustively
  (`` `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47) `` at HEAD). Unit 1's Step 0 restores
  the file to HEAD, which already has no `ZoneWorldSystem`. **One edit (new with C22):** append the new
  type, so the zone list reads
  `` `Zone`, `ZoneGrid`, `ZoneIndex`, `EntityChangedZone` (#47), `UnzonedEntityException` (#77) ``.
  Verify afterwards that `git diff HEAD -- CONTRIBUTING.md` shows that one change and nothing else.
  (Before C22 this plan recorded "no edit".)
- **Website: none.** Every World Systems website update waits for #86 (C13). `website/index.html`
  `:176`, `:293` name `ZoneIndex` only and stay true.

**CHANGELOG `[Unreleased]` → `### Added`** (locate by text). Unit 1 owns the #76 bullet; this unit
owns two:

1. **The #47 zone bullet** (HEAD `:62-69`) — keep "Nothing in `World`/`core` changes" and the
   Phase 3/5 seam sentence; drop the `refresh(World)` and "called explicitly" clauses:

   > - `com.spartanlabs.gaming.world.zone` — a static, uniform-grid map partition: `Zone` (a named,
   >   bounded cell), `ZoneGrid` (partitions any `Space`'s bounds into `columns × rows` zones,
   >   `zoneAt(Point, clamped)`), and `ZoneIndex` (entity↔zone bookkeeping, `zoneOf`/`entitiesIn`;
   >   itself an installable `WorldSystem`, below). A zone transition — entering, crossing, or
   >   leaving — publishes `EntityChangedZone` on `World.events`. Nothing in `World`/`core` changes
   >   for it. The seam Phase 3 interest filtering and Phase 5 zone save/load build on — nothing
   >   consumes it yet. (#47)

   C22 does not change this text: it names `zoneOf` without a shape, as it names `zoneAt`, and its
   "below" points to the #77 bullet, which carries `zoneOf`'s `Result<Zone>` shape and
   `UnzonedEntityException` — the read shape is #77's decision, so #77's bullet states it.

2. **New #77 bullet**, directly after it:

   > - `ZoneIndex` is itself a `WorldSystem` — the zone system. `ZoneIndex(grid)` extends
   >   `AbstractWorldSystem` and claims `CoreWorldSystemSlot.ZONE`: install it with
   >   `World.installSystem` and every `World.stepSystems()` call recomputes each entity's zone —
   >   always after any `PHYSICS`-slot system, whatever the install order — publishing
   >   `EntityChangedZone` for every transition. There is no separate refresh call, and installing
   >   does not publish; the first step places every entity. One `ZoneIndex` serves one `World` for
   >   life (its `EntityId`-keyed bookkeeping cannot be reset): installing it on a second `World`,
   >   even after uninstalling it, throws `IllegalArgumentException`, as does installing a second
   >   `ZoneIndex` on the same `World` (the `ZONE` slot is taken); a `ZoneGrid` is immutable and may
   >   back any number of indexes. It declares `ZoneIndex::class` as its `uniqueRole`, so
   >   `world.systemOf<ZoneIndex>()` (or `world.systemOf(ZoneIndex::class)`) returns the installed
   >   index as a `Result`. `zoneOf(entityId)` returns a `Result<Zone>` — a failure carrying the new
   >   `UnzonedEntityException`, a stackless `NoSuchElementException` that names the entity, when
   >   the entity is in no zone, never `null` — and `entitiesIn(zone)` still returns a `Set`, empty
   >   when nobody is there. Experimental: using `ZoneIndex`, including `zoneOf`/`entitiesIn`, or
   >   `UnzonedEntityException`, requires opting in to `ExperimentalGameToolsApi`. (#77)

   No `### Deprecated`/`### Removed` entry: `ZoneIndex`'s former `refresh` never shipped.

   > As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §5 Documentation impact".

**`docs/plans/86-phase-1-map-and-space/49-physics/architecture.md` — correction (the caller's explicit requirement).** Its
`:618` row claims *"`ZoneIndex.refresh` remains directly callable exactly as before"* — false: there
is no `refresh`. The body stays as historical record (C13); the fix is a dated callout item plus two
inline markers. Line numbers are the file's today (it has no uncommitted changes); apply the
edits **bottom-up** (`:618`, `:606`, then the header) so the numbers do not shift.

1. **`:618` row** — append to the end of the last cell (no `|` inside):
   ` **[Superseded 2026-09-30 — see item 4 of the header callout: there is no `ZoneIndex.refresh`; `ZoneIndex` is itself the zone `WorldSystem`, installed with `World.installSystem` and driven by `stepSystems()`.]**`
2. **`:606` bullet** — after `…own public contract.` append:
   ` *(Superseded 2026-09-30: `ZoneIndex` is itself the `WorldSystem` and has no `refresh` to call — see item 4 of the header callout.)*`
3. **Header callout (`:3-33`)** — change `:6` "Three things in this document are superseded:" to
   "Four things …", and insert a new item after item 3 (after `:30`, before the bare `>` at `:31`):

   ```markdown
   > 4. **The adapters and the `refresh` call — 2026-09-30, issue #77's design pass
   >    (`docs/plans/87-world-systems/77-zone-world-system/architecture.md`).** Item 1's "`ZoneWorldSystem` adapter (#77)"
   >    and "thin `PhysicsWorldSystem` adapter (#80)" were never built and no longer exist as
   >    planned. `ZoneIndex` is itself the zone `WorldSystem`: it extends `AbstractWorldSystem`,
   >    claims `CoreWorldSystemSlot.ZONE`, has a public constructor and **no `refresh` method** —
   >    a consumer installs it with `World.installSystem` and `World.stepSystems()` drives it; code
   >    holding only a `World` gets it from `world.systemOf<ZoneIndex>()` (a `Result`). `PhysicsSystem`
   >    is likewise itself the physics `WorldSystem` (it extends `AbstractWorldSystem` and claims
   >    `CoreWorldSystemSlot.PHYSICS`), so no `PhysicsWorldSystem` type exists and #80 is resolved by
   >    #49. Every statement below that `ZoneIndex.refresh(world)` exists, is directly callable, is
   >    called by `WorldSystems`, or is "meant to be called once per frame" (§3's `ZoneIndex.refresh`
   >    bullet; §4.1's `WorldSystems` row; §4.7 hazards 3 and 8; §4.9's sketch and order list; §5's
   >    flowchart, boundary note and `ZoneIndex` ↔ `WorldSystems` bullet; §6's `ZoneIndex`/`ZoneGrid`
   >    row; §12 decision 1) describes #47's API as merged-but-unreleased on 2026-09-22 and is historical;
   >    the physics → zone ordering requirement is unchanged (item 1). `PhysicsSystem`'s own
   >    signatures shown below (`step(world)`, `attach`/`detach`) predate the parameterless-hook
   >    contract and are #49's re-plan to revise — not corrected here. The unit plans written
   >    against this document carry matching 2026-10-01 callouts: `docs/plans/86-phase-1-map-and-space/49-physics/plan-physics-system.md`,
   >    `docs/plans/86-phase-1-map-and-space/49-physics/plan-physics-core-seams.md`, `docs/plans/86-phase-1-map-and-space/49-physics/plan-physics-resolution.md` and
   >    `docs/plans/87-world-systems/80-physics-world-system/superseded/plan-2026-09-30.md` (the last wholly superseded).
   ```

   > As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §5 Documentation impact".

   The enumerated list is a superset of the architecture's (§11.1 named `:193`, `:494`,
   `:515-519`, `:596`, `:606`, `:618`, `:785-788`); the additional `:224`, `:450`, `:455`, `:524`,
   `:543`, `:575` were found by `grep -n refresh` and carry the same claim (§9 reports this).

**Not this unit's:** the other planner-owned doc corrections (architecture §11.2) —
approved by the user and applied by the planner on 2026-10-01, landing as a separate docs commit;
this plan neither plans nor
contradicts them. Cross-references into the old `docs/plans/87-world-systems/77-zone-world-system/plan-zone-world-system.md` sections live in those
planner-owned hunks.

---

## 6. Test plan (5-level hierarchy)

Conventions (repo reality, not the generic ones): `kotlin.test` assertions on the JUnit platform
(no MockK in the repo; level 2 has nothing external to mock — `World`, `EventBus` and the grid are
real, in-memory), one class per file, backtick test names, hand-rolled fakes and recorders.
Every class that touches the Experimental seam carries a class-level
`@OptIn(ExperimentalGameToolsApi::class)` (as the ten core `WorldSystem` test classes do), on top of
the module-wide test flag; #79 removes both. Imports use the numbered region scheme (testing =
group 4.3). Tests never call a `refresh`; they drive the index with
`world.installSystem(index)` + `world.stepSystems()`.

**Lookup through one helper.** `systemOf` returns `Result<T>` (C18) and has a reified form (C19).
Wherever a test looks the index up by role, it does so through one file-private helper per test
class, written with the reified form — the one the README shows — and read with `getOrNull()`:

```kotlin
/** The installed ZoneIndex found by role, or null when the lookup's Result is a failure. */
private fun lookup(world: World): ZoneIndex? = world.systemOf<ZoneIndex>().getOrNull()
```

Reading through `getOrNull()` (or `isFailure`) means no test here references unit 1's
`MissingWorldSystemException` (C21); the failure type is unit 1's to test. The `KClass` form is pinned
once, in `ZoneIndexWorldSystemTest` 5. Unit 1's `systemOf` stage (its commit 2) must land before this
unit's commit 3, the rework; it no longer waits on any decision.

**Misses through one helper; positions through `getOrNull()` (C22).** `zoneOf` returns `Result<Zone>`.
A test that asserts *where* an entity is reads `index.zoneOf(id).getOrNull()`, so a miss fails as
"expected <zone> but was null" rather than throwing. A test that asserts an entity is *nowhere* uses
one file-private helper, declared in each test class that needs it — like `lookup`, and not in
`ZoneFixtures.kt`, which stays free of Experimental API so #79 never touches it:

```kotlin
/** Asserts [id] is unzoned: zoneOf is a failure carrying an UnzonedEntityException for exactly [id]. */
private fun ZoneIndex.assertUnzoned(id: EntityId) {
    val result = zoneOf(id)
    assertTrue(result.isFailure, "expected $id to be unzoned, but zoneOf returned ${result.getOrNull()}")
    assertEquals(id, assertIs<UnzonedEntityException>(result.exceptionOrNull()).entityId)
}
```

No assertion calls `getOrThrow()` on `zoneOf`: the exception is stackless, so one escaping a test
would point nowhere. These `zoneOf` tests reference only this unit's `UnzonedEntityException` —
nothing of unit 1's beyond the rework they already build on.

**Every `zoneOf` call site, old and folded, in its new shape:**

| Old site (HEAD, or the parked `ZoneWorldSystem*` file) | New home | New form |
|---|---|---|
| `ZoneIndexTest.kt:45` (fresh index) | `ZoneIndexTest` 1 | `index.assertUnzoned(EntityId(1L))` |
| `ZoneIndexTest.kt:59`, `:78` | `ZoneIndexTest` 2, 3 | `assertEquals(zone, index.zoneOf(actor.entityId).getOrNull())` |
| `ZoneIndexTest.kt:94` (`UNASSIGNED` skipped) | `ZoneIndexTest` 4 | `index.assertUnzoned(actor.entityId)` — the id is `EntityId.UNASSIGNED` |
| `ZoneIndexTest.kt:110` (outside the extent), `:129` (removed) | `ZoneIndexTest` 5, 6 | `index.assertUnzoned(actor.entityId)` |
| `ZoneIndexTest.kt:41`, `:98` (test names) | `ZoneIndexTest` 1, 5 | renamed (below) |
| `EntityChangedZoneTest.kt:107` | the non-`VisibleObject` test | `assertEquals(zone, index.zoneOf(trigger.entityId).getOrNull())` |
| `ZoneDrivenSimulationE2ETest.kt:71-72` | same test | `assertEquals(heroZone30, index.zoneOf(hero.entityId).getOrNull())`, likewise `ally` |
| `ZoneWorldSystemTest.kt:63` (installing does not place) | `ZoneIndexWorldSystemTest` 3 | `index.assertUnzoned(actor.entityId)` |
| `ZoneWorldSystemTest.kt:109` ×2 (step equals a direct refresh) | dropped as moot (§6.3) | — |
| `ZoneWorldSystemTest.kt:128`, `:154` | `ZoneIndexWorldSystemTest` 7, 9 | `getOrNull()` equals the expected zone |
| `ZoneWorldSystemSimulationLoopE2ETest.kt:74-75` | `ZoneIndexSimulationLoopE2ETest` | `getOrNull()`, as the hand-rolled e2e |
| `ZoneWorldSystemOrderingIntegrationTest.kt:155` (zone kept after uninstall) | `ZoneIndexOrderingIntegrationTest` 4 | `getOrNull()` equals the pre-uninstall zone |

### 6.1 Level 1 — gating (`testing.gating`: no package exists; none invented)

Level 1 is the pre-commit run of the level-2/4a suites plus three one-off checks; it adds no test
file.

- Before every commit: `./gradlew componentTest deterministicTest` (CONTRIBUTING "Running the
  build and tests"); before the PR additionally `./gradlew integrationTest e2eTest` (the world
  module's level-3/4b tests run under these tasks; they serialize on the ports lock but bind no
  port).
- **Main compiles with no opt-in flag:** `./gradlew :gametools-world:compileKotlin` — proves the
  class-level `@ExperimentalGameToolsApi` alone satisfies `@SubclassOptInRequired` on
  `AbstractWorldSystem` and opts the body in to `coreSlot`/`uniqueRole` (§1.3). Re-run if one of the
  open Kotlin 2.4.x dependabot PRs merges first.
- **No new Dokka warnings:** `./gradlew dokkaGeneratePublicationHtml`; compare against the
  pre-existing unresolved-link warnings tracked in #127 (Dokka warnings are non-fatal, so the exit
  code alone proves nothing — diff the warning list before and after). Specifically: every
  `[ZoneIndex.step]`, `[ZoneIndex.zoneOf]`, `[UnzonedEntityException]`, `[World.systemOf]` and
  `[World.stepSystems]` link resolves.
- **Opt-in enforced from outside** (cannot be an automated test — see §6.8): compile a three-line
  consumer module *without* the flag: `ZoneIndex(grid)`, `index.zoneOf(id)` and
  `t is UnzonedEntityException` must each fail with the Experimental-API error; with `@OptIn` they
  compile. (Probed on Kotlin 2.2.0 for the exception type: an unopted `is` check is a compile error —
  architecture §4.5.)

### 6.2 Level 2 — component (`testing.component.world.zone`)

**`ZoneIndexTest`** — `…/testing/component/world/zone/ZoneIndexTest.kt` (reworked). The bookkeeping
logic, through the real registry. Helper pattern for each test:
`val world = World(); val index = ZoneIndex(grid).also(world::installSystem)`; every former
`index.refresh(world)` is `world.stepSystems()`. Shared `fixtureGrid()`; private nested
`FixtureSpace`/`fixtureGrid()` removed. The seven existing tests keep their assertions, in the
`Result` shape of the table above; wording "refresh" → "step"; file-private `assertUnzoned`:

1. `a fresh ZoneIndex before any step reports every entity unzoned and every zone empty` (renamed
   from `…has empty zoneOf and entitiesIn`) — on a **never-installed** index (no `World`):
   `index.assertUnzoned(EntityId(1L))`, `entitiesIn(grid.zones.first())` empty, and neither call
   throws (locks that the reads never touch `world`).
2. `one step places entities in the expected zones` — `zoneOf(…).getOrNull()` per entity.
3. `a second step after crossing a zone boundary updates both the old and new zone`.
4. `an entity still at EntityId UNASSIGNED is skipped, not indexed` (`world.gameObjects += actor`
   without `World.add`, then `stepSystems()`): `index.assertUnzoned(actor.entityId)` — the miss
   carries `EntityId.UNASSIGNED`.
5. `an entity moved outside the grid's extent is dropped from entitiesIn and reported unzoned`
   (renamed from `…dropped from entitiesIn and zoneOf`): `index.assertUnzoned(actor.entityId)`.
6. `a removed entity is dropped from the index on the next step` (`world.removeList += actor;
   world.tick(); world.stepSystems()`): `index.assertUnzoned(actor.entityId)`.
7. `entitiesIn returns a copy - mutating it does not affect the index`.
8. **New** — `a scripted multi-actor path publishes exactly the expected EntityChangedZone
   sequence` (replaces the moot "step matches a direct refresh" comparison; see 6.3's mapping).
   Two actors on the 4×3 fixture (10×10 zones), positions set with `location.setTo` per tick,
   **no `world.tick()`** (so nothing but the test moves them), one `stepSystems()` per tick:

   | tick | actor 0 | actor 1 | events published this step (in order) |
   |---|---|---|---|
   | 1 | (5,5) → `zone-0-0` | (35,25) → `zone-3-2` | (a0, null, z00), (a1, null, z32) |
   | 2 | (15,5) → `zone-1-0` | (35,25) | (a0, z00, z10) |
   | 3 | (15,15) → `zone-1-1` | (-100,-100) out | (a0, z10, z11), (a1, z32, null) |
   | 4 | (15,15) unchanged | (25,25) → `zone-2-2` | (a1, null, z22) |
   | 5 | (35,5) → `zone-3-0` | (5,25) → `zone-0-2` | (a0, z11, z30), (a1, z22, z02) |

   Assert the recorder's full list equals the concatenation (compare `EntityChangedZone` values
   directly — the same `Actor` instances are in one `World`), and the final
   `zoneOf(…).getOrNull()` per actor (`zone-3-0`, `zone-0-2`); after tick 3, `actor 1` is unzoned
   (`assertUnzoned`). Covers placement, crossing, leaving the extent, re-entering from `null`, and a
   no-change tick.
9. **New (C22)** — `zoneOf an id no World ever assigned is a failure carrying exactly that id` —
   after a step that placed an actor, `index.assertUnzoned(EntityId(999L))`, and the placed actor's
   `zoneOf` is still a success: a miss for one id says nothing about another.

**`ZoneIndexWorldSystemTest`** — `…/testing/component/world/zone/ZoneIndexWorldSystemTest.kt`
(**new**; folds `ZoneWorldSystemTest`). `ZoneIndex` *as a `WorldSystem`*. Private fakes:
`RoleLessZoneSystem : AbstractWorldSystem()` with `coreSlot = CoreWorldSystemSlot.ZONE` and no role.

1. `coreSlot is CoreWorldSystemSlot ZONE` — bare read (folds old "coreSlot is ZONE").
2. `uniqueRole is ZoneIndex itself` — `assertEquals(ZoneIndex::class, index.uniqueRole)`.
3. `installing publishes nothing - the first stepSystems places the entity` (folds "installOn does
   not refresh"): actor added first, `installSystem`, recorder empty,
   `index.assertUnzoned(actor.entityId)`, `index.world === world`, `index in world.installedSystems`;
   then `stepSystems()` → exactly `EntityChangedZone(actor, null, zone)`, and
   `index.zoneOf(actor.entityId).getOrNull() == zone`.
4. `stepping an index that was never installed fails with IllegalStateException and publishes
   nothing` — `assertFailsWith<IllegalStateException> { ZoneIndex(grid).step() }` (the base class's
   guarded getter; also proves `step()` reads `world` before mutating anything).
5. `systemOf returns the installed index as a success, and a failure before install and after
   uninstall` — before install `assertTrue(world.systemOf<ZoneIndex>().isFailure)` and
   `assertNull(lookup(world))`; install; `assertSame(index, lookup(world))` and
   `assertSame(index, world.systemOf(ZoneIndex::class).getOrNull())` (the `KClass` form gives the
   same answer); uninstall; `assertTrue(world.systemOf<ZoneIndex>().isFailure)` (**C17–C19**; no
   assertion on the failure's exception type — `MissingWorldSystemException` is unit 1's to test,
   C21).
6. `a second ZoneIndex on the same World is rejected by the slot check naming ZONE` —
   install `first`; `assertFailsWith<IllegalArgumentException> { world.installSystem(ZoneIndex(grid)) }`
   with `message` containing `"ZONE"`; afterwards `world.installedSystems == listOf(first)` and
   `lookup(world) === first` (**C16**: the slot check runs before the role check, so the message
   names the slot, not the role).
7. `a ZoneIndex bound to one World is rejected by another, and the rejection changes nothing`
   (folds "installOn rejects a different World …"): `worldA.installSystem(index)`;
   `assertFailsWith<IllegalArgumentException> { worldB.installSystem(index) }`;
   `worldB.installedSystems` empty; `worldA.stepSystems()` still places A's actor normally (its
   event is recorded and `index.zoneOf(actor.entityId).getOrNull() == zone`).
8. `the binding is for life - rejected by another World even after uninstall; the bound World
   accepts it again` (folds "…even after uninstallFrom" and the level-3 registry test): install on A,
   `uninstallSystem`, B rejects (IAE, B empty), `A.installSystem(index)` succeeds and is listed.
9. `re-installing on the same World retains state and the next step publishes what changed
   meanwhile` (folds "installOn accepts the same World again …"): actor in zone 1, install, step
   (placed: `zoneOf(…).getOrNull() == zone1`), uninstall, record, move the actor to zone 2,
   re-install, step → exactly `EntityChangedZone(actor, zone1, zone2)` (no second "entered" event;
   `from` is the retained zone).
10. `uninstalling a never-installed ZoneIndex is a no-op and binds nothing` (folds "uninstallFrom on
    a never-installed instance …"): `worldA.uninstallSystem(index)` does not throw; afterwards
    `worldB.installSystem(index)` succeeds.
11. `one ZoneGrid backs two indexes on two Worlds without interference` (**new** — `ZoneGrid`
    immutability): `worldA` and `worldB` each with one actor (both get raw `EntityId(1)`), at
    different zones; one shared `grid`; two `ZoneIndex(grid)`, one per world; step both worlds;
    each index answers for its own world's entity, each recorder holds only its own event, and
    `indexA.zoneOf(id).getOrNull()` differs from `indexB.zoneOf(id).getOrNull()` — both successes —
    for the *same* `EntityId`: the concrete reason the binding is for life.
12. `a substitute ZONE-slot system is not found under ZoneIndex::class, and blocks installing a
    ZoneIndex` — install `RoleLessZoneSystem`; `world.systemOf<ZoneIndex>()` is a failure and
    `lookup(world)` is `null` (exact-key matching); `assertFailsWith<IllegalArgumentException> { world.installSystem(ZoneIndex(grid)) }`
    naming `"ZONE"`.
13. `ZoneIndex declares no refresh member` — `assertTrue(ZoneIndex::class.java.declaredMethods.none
    { it.name.startsWith("refresh") })` (Java reflection; `kotlin-reflect` is not on the classpath).
    The one automated guard that the design has a single entry point.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §6.2 Level 2 — component (`testing.component.world.zone`)".

*Dropped as moot (and why):* `each instance builds its own ZoneIndex, so two systems over the same
grid never share one` (the system no longer builds an index; replaced by 11) and `step matches
refreshing a ZoneIndex over the same grid directly` (there is now one code path; its script lives on
as `ZoneIndexTest` 8).

**`EntityChangedZoneTest`** — `…/testing/component/world/zone/EntityChangedZoneTest.kt` (reworked).
Shared `fixtureGrid()`/`recorder(world)`; the private `FixtureSpace`/`fixtureGrid()`/`recorder`
removed; `Trigger` stays private; **the unused `com.spartanlabs.gaming.event.GameEvent` import
(`:7` at HEAD) is deleted (C13)**; class-level `@OptIn`. Four tests unchanged in substance, driving
install + `stepSystems()`: entering publishes `from = null`; crossing publishes old and new; leaving
the grid publishes `to = null`; a non-`VisibleObject` `GameObject` is still tracked (its `:107`
assertion reads `index.zoneOf(trigger.entityId).getOrNull()`). Class KDoc: "`ZoneIndex.step`
publishing …".

**`UnzonedEntityExceptionTest`** — `…/testing/component/world/zone/UnzonedEntityExceptionTest.kt`
(**new**, C22). The same five properties unit 1's `MissingWorldSystemExceptionTest` checks for its
type, so the two shapes cannot drift apart:

1. `entityId is the id that was looked up` — `UnzonedEntityException(EntityId(7L)).entityId == EntityId(7L)`.
2. `it is a NoSuchElementException` — `assertIs<NoSuchElementException>(e)`, and a
   `catch (x: NoSuchElementException)` around `throw e` catches the same instance.
3. `it is stackless` — `e.stackTrace` is empty right after construction and still empty after
   `throw e` is caught; `e.fillInStackTrace()` returns `e` itself, and the trace stays empty.
4. `its message names the entity by its id` — the message contains `#7`; for
   `EntityId.UNASSIGNED` it contains `#0`.
5. `it has no cause` — `e.cause == null`.

### 6.3 Mapping of the folded `ZoneWorldSystem*` coverage (nobody needs the old files)

| Old test (deleted) | Where its coverage lives now |
|---|---|
| `ZoneWorldSystemTest`: slot is ZONE | `ZoneIndexWorldSystemTest` 1 |
| …: own index per instance | moot → `ZoneIndexWorldSystemTest` 11 |
| …: install does not refresh | `ZoneIndexWorldSystemTest` 3 |
| …: step matches direct refresh | moot (single path) → `ZoneIndexTest` 8 keeps its script |
| …: rejects other World while bound, rejection changes nothing | `ZoneIndexWorldSystemTest` 7 |
| …: rejects other World after uninstall | `ZoneIndexWorldSystemTest` 8 |
| …: same World again, next step publishes meanwhile | `ZoneIndexWorldSystemTest` 9 |
| …: uninstall on a never-installed instance binds nothing | `ZoneIndexWorldSystemTest` 10 |
| `ZoneWorldSystemOrderingIntegrationTest` (5 tests) | `ZoneIndexOrderingIntegrationTest` (below); the "second World via the real registry" test is `ZoneIndexWorldSystemTest` 7–8 (already real-registry) |
| `ZoneWorldSystemDeterminismTest` | `ZoneIndexDeterminismTest` (below) |
| `ZoneWorldSystemSimulationLoopE2ETest` | `ZoneIndexSimulationLoopE2ETest` (below) |

### 6.4 Level 3 — integration (`testing.integration.world.zone`)

**`ZoneIndexWorldIntegrationTest`** — `…/integration/world/zone/ZoneIndexWorldIntegrationTest.kt`
(`git mv` from `ZoneRefreshWorldIntegrationTest.kt`, reworked). Same `fixtureMap()` (4×3 all-grass
`TiledMap`, 40×30, `tileSize = 10`, `World().apply { space = map }`), same scripted path — `mover`
travels +x 10 units/tick from (5,5) with `Movement.Directional`, angle 0; `faller` sits at (35,25)
and is despawned (`world.removeList += faller`) before the third tick — but `ZoneIndex(grid)` is
installed with `world.installSystem` and each former `index.refresh(world)` after `world.tick()` is
`world.stepSystems()`. Test renamed `stepSystems after each tick publishes exactly the expected
EntityChangedZone sequence, including a despawn`; expected list unchanged:
`(mover,null,z10) (faller,null,z32) (mover,z10,z20) (mover,z20,z30) (faller,z32,null)`. **New
assertions (C22)**, across the `World` → `ZoneIndex` boundary of a despawn: capture
`val fallerId = faller.entityId` while it is owned, and after the last step
`index.assertUnzoned(fallerId)` (the id the `World` retired is a failure carrying exactly that id)
while `index.zoneOf(mover.entityId).getOrNull() == z30`. KDoc drops "the documented ordering,
`ZoneIndex`'s class KDoc" (that KDoc no longer documents a manual ordering).

**`ZoneIndexOrderingIntegrationTest`** — `…/integration/world/zone/ZoneIndexOrderingIntegrationTest.kt`
(**new**; folds the old ordering test). Real `World.installSystem`/`stepSystems`/`uninstallSystem`,
a real `ZoneIndex(grid)` over a private `fixtureMap()` 4×3 grid, and two fakes extending
`AbstractWorldSystem`:

```kotlin
private class TracingSystem(
    private val name: String,
    override val coreSlot: CoreSystemSlot?,
    private val trace: MutableList<String>,
    var onStep: () -> Unit = {},
) : AbstractWorldSystem() {
    override fun step() { trace += name; onStep() }
}
```

The index cannot write to a trace itself, so a `world.events` subscriber appends `"zone"` for every
`EntityChangedZone`; every asserted pass therefore needs at least one zone event (an actor added
before the pass).

1. `a PHYSICS-slot system installed after ZoneIndex still steps before it` — install `ZoneIndex`,
   then `TracingSystem("physics", CoreWorldSystemSlot.PHYSICS, trace)`; add an actor; one
   `stepSystems()`; `trace == ["physics", "zone"]`.
2. `a tier-2 system installed before ZoneIndex still steps after it` — tier-2 fake (`coreSlot =
   null`) first, then `ZoneIndex`; `trace == ["zone", "tier2"]`.
3. `a PHYSICS-slot move that crosses a zone boundary is observed by ZoneIndex in the same
   stepSystems call` (precursor of #49's headline test). Install order reversed on purpose: `ZoneIndex`
   first, then the `PHYSICS` fake. A first `stepSystems()` with the fake idle places the actor in zone A
   (assert the single event; `events.clear()`). Set `physics.onStep = { actor.location.setTo(15.0, 5.0) }`,
   call `stepSystems()` once more, assert exactly `[EntityChangedZone(actor, zoneA, zoneB)]` was
   recorded during that second call — not one call later — and
   `index.zoneOf(actor.entityId).getOrNull() == zoneB`.
4. `uninstallSystem removes ZoneIndex from installedSystems and stops further steps` — install, step
   once (1 event), `uninstallSystem`, `index !in installedSystems`, move the actor across a boundary,
   `stepSystems()`, still 1 event and `index.zoneOf(actor.entityId).getOrNull()` still the
   pre-uninstall zone.
5. **New** — `a tier-2 system holding only its World finds the ZoneIndex through systemOf and reads
   this frame's placement`. `PeerReader : AbstractWorldSystem()` (tier 2, installed **before** the
   index) whose `step()` does `sink += lookup(world)?.zoneOf(actorId())?.getOrNull()` via the
   file-private `lookup` helper (`sink: MutableList<Zone?>`; the `lookup` helper itself is unchanged
   by C22 — it never touches `zoneOf`). After one `stepSystems()` with an actor at (5,5): `sink == [zone-0-0]` (the
   tier-1 index stepped first, so the reader sees this frame, not `null`); move the actor to (15,5),
   another pass: `sink == [zone-0-0, zone-1-0]`. Demonstrates the README's "code holding only a
   `World`" story and the ordering guarantee together (architecture §7.4).

### 6.5 Level 4a — deterministic (`testing.deterministic.world.zone`)

**`ZoneIndexDeterminismTest`** — `…/deterministic/world/zone/ZoneIndexDeterminismTest.kt` (`git mv`
from `ZoneIndexRefreshDeterminismTest.kt`, reworked; absorbs `ZoneWorldSystemDeterminismTest`). Keeps
its own private 80×80 `FixtureSpace`/`fixtureGrid()` (4×4) and its `(actor index, from, to)`
projection (each run builds distinct `Actor`s, which compare by reference). `runScenario(seed)` now
does `val world = World(seed)` … `world.installSystem(ZoneIndex(fixtureGrid()))`, and per tick
`world.tick(); world.stepSystems()` instead of `index.refresh(world)`. `runScenario` also returns a
second projection (C22): the final `zoneOf` answer per actor index — the zone's name on a success,
`"unzoned"` on a failure. Two tests:

1. `the same seeded sequence of moves produces the same EntityChangedZone sequence and the same zoneOf
   answers every run, driven via installSystem and stepSystems` (renamed; `runScenario(47_2028L)`
   twice, `assertContentEquals` on both projections). The two predecessor tests differed only in seed
   and driver; one survives.
2. **New (C22)** — `zoneOf is a pure, repeatable read` — after one scenario run, for every actor and for
   `EntityId.UNASSIGNED` and an id never assigned: two calls give equal answers (equal `getOrNull()`;
   on a failure, equal `UnzonedEntityException.entityId`, which is the queried id), and the calls
   publish nothing and leave every `entitiesIn` unchanged. (Two failures are compared by their
   `entityId`, never with `==`: each miss builds a new exception.)

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §6.5 Level 4a — deterministic (`testing.deterministic.world.zone`)".

### 6.6 Level 4b — end-to-end (`testing.e2e.world.zone`)

Both tests load `src/test/resources/fixture-map.json` via `MapLoader.fromJson(...).getOrThrow()`,
build `ZoneGrid(map, columns = 4, rows = 3)` (40×30 map, 10×10 zones), `World().apply { space = map }`,
install `ZoneIndex(grid)`, and use spawn points `red-spawn` (hero, `Movement.Directional`, angle 0,
travels +x 10 units/tick) and `blue-spawn` (ally, stationary). Zones: `heroZone10 = zoneAt(15,5)`,
`heroZone20 = zoneAt(25,5)`, `heroZone30 = zoneAt(35,5)`, `allyZone = zoneAt(blueSpawn.position)`.
After three ticks both assert: `index.zoneOf(hero.entityId).getOrNull() == heroZone30`,
`index.zoneOf(ally.entityId).getOrNull() == allyZone` (C22's `Result` shape, read without throwing),
`entitiesIn(heroZone30) == {hero}`, `entitiesIn(allyZone) == {ally}`, and the full history
`[(hero,null,z10), (ally,null,allyZone), (hero,z10,z20), (hero,z20,z30)]`.

- **`ZoneDrivenSimulationE2ETest`** (reworked in place; keeps its deliberately hand-rolled loop):
  `repeat(3) { world.tick(); world.stepSystems() }`; queries on the `ZoneIndex` it installed.
- **`ZoneIndexSimulationLoopE2ETest`** (**new**; folds the old SimulationLoop e2e): identical setup,
  driven by `val loop = SimulationLoop(world, onTick = { world.stepSystems() })`,
  `val nanosPerTick = (1_000_000_000.0 / loop.settings.tickRateHz).toLong()`,
  `repeat(3) { loop.advance(nanosPerTick) }` — no thread, deterministic, #76's own pattern. Also
  asserts `lookup(world)` (its own file-private helper, reified form) `=== index` so the e2e covers
  the `systemOf` path.
  Together the two cover the scenario under both drivers; this is the first `gametools-world` test
  to drive `SimulationLoop`.

### 6.7 Level 4c and Level 5

- **4c nonfunctional — `ZoneIndexQueryThroughputTest`** (`…/nonfunctional/world/zone/`, added in the
  QA pass; as built, 2026-10-08). `a million zoneOf misses stay within a generous budget and record no
  stack trace` drives a million `zoneOf` misses on an installed, stepped index within the same
  `< 10 s` budget as the other throughput guards, and samples every 100 000th failure for an
  `UnzonedEntityException` with an empty `stackTrace`. A miss costs two small allocations, the
  stackless exception and its `Result` box, and a hit none. The plan first added no 4c test,
  reasoning that `step()` is the existing `O(n)` pass with no new cost (that part still holds) and
  leaving `zoneOf`'s throughput to its first bulk caller, Phase 3 interest filtering.
- **Level 5 UAT — not planned in #77.** By the user's decision of 2026-10-08, it is deferred to a
  separate, later plan. Related issue: #133, which defines a selective Level-5 `testing.uat` level.

### 6.8 What cannot be tested automatically

- **Opt-in enforcement** (`ZoneIndex`, its reads and `UnzonedEntityException` need `ExperimentalGameToolsApi`): a compile-time
  property of *other* modules; `@RequiresOptIn` has no runtime-visible annotation (BINARY
  retention), this module's tests opt in module-wide, and `kotlin-reflect` is absent. Covered by the
  Level-1 consumer compile check.
- **Real wall-clock `SimulationLoop` thread timing** — the e2e drives `advance()` directly for
  determinism, as `SimulationLoop`'s own suite does.
- **Rejection message text beyond naming `ZONE`** — asserted loosely (substring `"ZONE"`) on
  purpose; the full wording is unit 1's.

---

## 7. Risks & edge cases

1. **Release order (C12).** If a release containing #47 is cut before this PR merges, `ZoneIndex`
   would have shipped a public `refresh` and this rework would become a breaking change in a
   Feature release. Hold `5.2.0` until the PR merges; if it cannot be held, stop and re-plan.
2. **Zones now need an opt-in.** `ZoneIndex` is class-level Experimental, so constructing it,
   calling `zoneOf`/`entitiesIn`, and naming `UnzonedEntityException` require
   `ExperimentalGameToolsApi` until #79 (architecture §8; it reverses the earlier "reads stay
   untagged" call). Stated in KDoc, README, CHANGELOG.
3. **Directly callable `step()`.** Public by interface contract; before it is bound (by the first
   `installSystem` call whose checks pass) it throws `IllegalStateException`, and once bound (even if
   uninstalled) it runs one extra pass against the bound `World`. Not guarded; documented as not part of the usage contract (§2.1).
4. **Bind-for-life pins a `World`.** The index keeps its `World` — and, via `indexed`, the
   `GameObject`s it last saw — reachable after uninstall. A static/long-lived `ZoneIndex` keeps the
   whole `World` alive. KDoc says: one index per `World`.
5. **Retained state is intentional.** After uninstall the bookkeeping is stale; the first step after
   re-install publishes everything that changed (including "left" events for objects removed
   meanwhile). Locked by test 9.
6. **Slot/role overlap is accepted (C16).** A second `ZoneIndex` is rejected by the slot check and
   the message names `ZONE`, never the role. A consumer's own `ZONE` substitute is rejected too while
   a `ZoneIndex` is installed, and is not found by `systemOf(ZoneIndex::class)`.
7. **Exact-key surprise.** `systemOf(ZoneIndex::class)` finds only a system that *declared*
   `ZoneIndex::class`; `ZoneIndex` is `final`, so for it the exact key and `is` coincide. KDoc spells
   out the substitute case.
8. **Pre-existing, unchanged:** an `EntityChangedZone` listener that mutates `world.gameObjects`
   during the pass can disturb `step()`'s iteration (as it could `refresh`); a listener calling
   `world.stepSystems()` gets `IllegalStateException` from the re-entrancy rule, which `EventBus`
   catches and logs. Nothing new; nothing added.
9. **Dependency on unit 1's details** (§9): the slot-rejection message must name the slot; `AbstractWorldSystem`'s
   constructor must be accessible from `gametools-world`; its `init` must not read open members.
10. **Toolchain drift.** Re-run the Level-1 compile and Dokka checks if a Kotlin 2.4.x PR merges
    first.
11. **Cross-repo impact:** none. No wire/protocol change; `gametools-net`, the umbrella and
    `website/` are untouched; per standing guidance no issues are filed against downstream consumers.
12. **Versioning.** Relative to `v5.1.0` the zone package is wholly new; nothing in it is a breaking
    change — `zoneOf`'s `Result<Zone>` (C22) included, since `Zone?` never shipped. `feat:` ⇒ a
    Feature release under the organization guide's bump table, no `!`, no `BREAKING CHANGE:` footer.
13. **Every `zoneOf` caller unwraps a `Result` now (C22).** A caller that wants "its zone, if any"
    writes `zoneOf(id).getOrNull()`; one that branches uses `fold`/`onSuccess`. A miss allocates one
    stackless `UnzonedEntityException` — no stack walk — which a bulk caller asking about many
    unplaced ids pays per miss (§6.7). No main-source caller exists yet (§1.2); Phase 3 interest
    filtering and #50 vision are the first, and their plans unwrap accordingly.
14. **Stackless means no stack trace.** A `zoneOf(…).getOrThrow()` on a miss propagates an exception
    with no frames; the message and `entityId` are the whole diagnostic. KDoc says so, and §6's
    convention keeps `getOrThrow()` out of every test assertion.
15. **First custom exception types in the repo** (this one and unit 1's `MissingWorldSystemException`;
    no main source declared one before). They follow architecture §4.5's one shape, and their
    component tests check the same five properties (§6.2), so the two cannot drift apart.
16. **Java callers:** unchanged in kind. `zoneOf` was already mangled for Java by its value-class
    `EntityId` parameter before C22; `UnzonedEntityException` can be caught from Java and its message
    read, but its constructor and `entityId` getter are value-class-mangled (architecture §4.5).

---

## 8. Version control

- **Branch:** `feature/77-zone-world-system` (exists). Unit 1's commits land first; this unit's
  follow in the same PR.
- **Never stage the planner-owned `docs/*.md` hunks** (architecture §11.2, and §11.3's — approved
  2026-10-02 and applied) or anything else pre-existing in the working tree: `git add` explicit paths only,
  and check `git diff --cached --stat` before each commit. Those hunks are a separate planner docs
  commit, not this unit's.
- **Precondition check before commit 1:** `git status` shows unit 1's commits in `git log`;
  `ZoneWorldSystem*.kt` absent; `ZoneFixtures.kt` untracked and the `build.gradle.kts` block
  present; tracked `gametools-world` zone files equal HEAD (`git diff HEAD --stat -- gametools-world`
  shows only the `build.gradle.kts` block).
- **Commit sequence** (every body carries `Refs #77`; no `BREAKING CHANGE:` footer; **trailers:**
  use exactly the attribution rule the caller supplies at commit time and state it explicitly when
  delegating to the `manager` agent — never infer it from history, which is mixed: verified
  2026-09-30, 59 commits up to 2026-09-24 carry a `Co-Authored-By` trailer, the commits since,
  including #76's, carry none):
  1. `test(world): add the shared zone fixture and the test-only Experimental opt-in` —
     **`docs/plans/87-world-systems/77-zone-world-system/plan-zone-world-system.md` (this document)**, `ZoneFixtures.kt`,
     `gametools-world/build.gradle.kts`. Technically needs only Step 0; lands after unit 1's commits,
     per the landing order (precondition above).
  2. `feat(world): add UnzonedEntityException, the failure of a zone lookup miss` —
     `UnzonedEntityException.kt` (§3.7) and `UnzonedEntityExceptionTest` (§6.2). Technically needs
     only Step 0 — nothing from unit 1, not even its exception (C22). Body: the repo's first custom
     exception type alongside unit 1's `MissingWorldSystemException`, built in the same shape
     (architecture §4.5).
  3. `feat(world): make ZoneIndex the zone WorldSystem` — `ZoneIndex.kt` (the system, and `zoneOf`
     returning `Result<Zone>`), the `EntityChangedZone.kt` and `ZoneGrid.kt` `step` KDoc, all test
     work of §6 except commit 2's (`git mv` the two renames). Needs unit 1's commits 1–2
     (`AbstractWorldSystem`; `systemOf`, which its tests call). Body: `refresh` removed; `zoneOf`
     returns `Result<Zone>`, a miss carrying `UnzonedEntityException` (C22); no semver weight because
     `ZoneIndex` (#47) and `WorldSystem` (#76) are unreleased; `ZoneWorldSystem` never committed.
  4. `docs(world): drop docs/ pointers from ZoneGrid KDoc` — the `:22`/`:26` pointers and the
     immutability sentence (C13). Kept separate from commit 3: it answers a different standing rule.
  5. `docs: document ZoneIndex as the zone system in README and CONTRIBUTING` — §5 README edits and
     the CONTRIBUTING world-row addition (C22). Needs unit 1's commit 3 (the `:180` bullet it
     inserts into).
  6. `docs: changelog entries for the zone package and ZoneIndex as a WorldSystem` — §5 CHANGELOG.
  7. `docs(physics): mark issue-49's ZoneIndex.refresh statements superseded` — §5 callout + markers.
- **PR:** shared with unit 1. Title (becomes the merge-commit subject): unit 1/PR owner's call;
  suggested `feat(world): bind WorldSystems to their World and make ZoneIndex the zone system`.
  Body: `Closes #77`, `Part of #87`; calls out both scope expansions (C9: `ZoneIndex` is the system;
  C22: `zoneOf` returns `Result<Zone>`), the two new exception types, the Level-1 results, and
  the release-order precondition. Update with rebase, never a merge from `master`; lands as a merge
  commit. Publishing to Maven Central is the user's manual step.

> As built: see [final-implementation.md](final-implementation.md), "plan-zone-world-system.md, §8 Version control".

---

## 9. Interfaces with sibling units

**From `world-system-binding` (unit 1) — what this unit expects:**

- **Step 0 leaves `gametools-world` at HEAD** except untracked `ZoneFixtures.kt` and the
  uncommitted `gametools-world/build.gradle.kts` opt-in block (both kept), with
  `ZoneWorldSystem.kt` and the four `ZoneWorldSystem*Test.kt` out of the tree (parked in unit 1's
  path-limited Step-0 stash) and `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `CoreSystemSlot.kt`
  returned to HEAD before unit 1's own edits.
- **The contract quoted in §1.3**, verbatim: `WorldSystem` (parameterless hooks, `world`, `coreSlot`,
  `uniqueRole`), `AbstractWorldSystem` with a no-arg constructor **accessible from another module**
  (public/protected — `ZoneIndex` calls it), `final override val world` throwing
  `IllegalStateException` before the first `installSystem` call whose checks pass, `World.installSystem`/
  `uninstallSystem`/`installedSystems`/`stepSystems`/`systemOf`.
- **Rejections are `IllegalArgumentException`**, thrown before any user hook: already installed /
  bound to another `World` / slot occupied / role taken, in that order (**slot before role**), and
  the slot-occupied message **names the slot** (`"ZONE"` appears in `message`) — tests 6 and 12 of
  §6.2 assert that substring.
- **`AbstractWorldSystem`'s `init` reads no open member** (`coreSlot`, `uniqueRole`, `world`):
  `ZoneIndex`'s overrides are initialised after the base constructor.
- **`World.systemOf(role: KClass<T>): Result<T>` and the reified `systemOf<T>(): Result<T>`**
  (C18, C19), exact key on the recorded `uniqueRole`; `Result.failure` for an uninstalled or
  role-less system, carrying unit 1's `MissingWorldSystemException` (C21) — which this unit never
  references, reading the `Result` with `getOrNull()`/`isFailure` only; `@ExperimentalGameToolsApi` on
  both, graduating with the registry at #79.
- **Nothing for the `zoneOf` change (C22).** `UnzonedEntityException` and `zoneOf`'s `Result` shape
  are wholly this unit's: commit 2 needs only Step 0, and the `zoneOf` assertions of commit 3 need
  nothing of unit 1's beyond the rework commit 3 builds on anyway.
- **`CoreSystemSlot.kt` KDoc** (unit 1 owns it) says `ZONE` is claimed by `ZoneIndex`, with no
  mention of `ZoneWorldSystem` or "adapter".
- **README `:180` installed-systems bullet** is rewritten by unit 1; this unit adds only the clause
  naming `ZoneIndex` as the first shipped system claiming `ZONE`, after unit 1's rewrite lands.
  **CHANGELOG #76 `WorldSystem` bullet** is unit 1's; this unit adds the #47 rewrite and the #77
  bullet only and does not edit the #76 bullet.
- **No unit-1 main or test file mentions `ZoneWorldSystem`** (it never existed in a commit).

**To `world-system-binding`:** nothing consumed. `ZoneIndex` is the first concrete user of the
contract; if §6.2 tests 4–6 and 12 expose an inconsistency in unit 1's behaviour (message, check
order, pre-install error), it is reported against unit 1, not worked around here.

**To #79 `world-system-graduation`** (its plan revises per architecture §7.3):
- `ZoneIndex` replaces `ZoneWorldSystem` in the removal list: delete the class-level
  `@ExperimentalGameToolsApi` and the KDoc's Experimental paragraph (add the worked-example
  sentence naming it `AbstractWorldSystem`'s example); `zoneOf` (returning `Result<Zone>`, C22) and
  `entitiesIn` become untagged.
- **`UnzonedEntityException` joins the removal list too** (C22): its class-level marker and KDoc
  Experimental paragraph go with `ZoneIndex`'s. Left out, #79's completeness check — only the
  marker's own declaration remains — fails. (Unit 1 hands over `MissingWorldSystemException` the
  same way.)
- The `gametools-world` test-only opt-in block (`build.gradle.kts`, after `mavenPublishing {}`)
  and the class-level `@OptIn(ExperimentalGameToolsApi::class)` on the nine test classes of §6 that
  carry one (the eight `ZoneIndex` test classes and `UnzonedEntityExceptionTest`, listed in §3.5),
  to remove.
- `ZoneFixtures.kt` (`FixtureSpace`, `fixtureGrid()`, `recorder(world)`) in
  `testing.component.world.zone`, referencing no Experimental API, reusable by a graduation test.
- Review-checkpoint Q1–Q2 (peer discovery) now have a supported answer, `World.systemOf`; this
  unit's `ZoneIndexOrderingIntegrationTest` 5 is a call site of it.

**To #49 re-plan / `PhysicsSystem`:** `ZoneIndex` is the worked example of a slot-claiming,
role-declaring, `EntityId`-keyed `AbstractWorldSystem` (no wrapper, no adapter); `PHYSICS` orders
before `ZONE`, proven by `ZoneIndexOrderingIntegrationTest` 1 and 3, whose test-local `PHYSICS`
fake #49's regression test replaces with the real `PhysicsSystem`. `docs/plans/86-phase-1-map-and-space/49-physics/architecture.md`
gets the §5 callout from this unit; its other now-stale `PhysicsSystem` signatures are #49's.

**To #78 `ExperienceSystem`:** none (independent; same `AbstractWorldSystem` pattern).

**To Phase 3 (interest filtering) and #50 (vision):** `world.systemOf<ZoneIndex>()` (a `Result`) is the
supported way to reach a `World`'s zone index without threading references (architecture §7.4), and
`zoneOf` answers with a `Result<Zone>` — unwrap with `getOrNull()`/`fold`, mindful that each miss
allocates one stackless `UnzonedEntityException` (architecture §7.5).

**To the planner (architecture §11.2, §11.3):** the cross-references into the old
`docs/plans/87-world-systems/77-zone-world-system/plan-zone-world-system.md` (sections §2.2/§2.6/§6/§9) that the planner-owned hunks cite no longer
resolve; they sat in the planner's REWRITE hunks (applied 2026-10-01), not here. The older documents
that quoted `zoneOf: Zone?` carry §11.3's callouts (approved 2026-10-02 and applied), not this
unit's edits.

---

## 10. Open decisions

**None remain.**

**Resolved by the user on 2026-10-01** (architecture §13.1) and applied above: OD4a — `systemOf`
returns `Result<T>`, a miss is `Result.failure` (C18); OD4b — the reified `systemOf<T>()` exists
beside the `KClass` form (C19); OD4c — `systemOf` graduates with the registry at #79 (C20; this unit
is unaffected). This unit's `lookup` helper, test 5, test 12, the e2e assertion, the README zone
bullet, the CHANGELOG #77 bullet and the `issue-49` callout are written in that settled shape.

**Resolved by the user on 2026-10-02** (architecture §13.1) and applied above:

- **OD5 → `MissingWorldSystemException`** (C21, unit 1's). Every lookup here reads the `Result` with
  `getOrNull()` or `isFailure`, so this plan's text names the type only where it describes unit 1's
  contract (§1.3, §9); no test of this unit references it.
- **OD6, this plan's former planner question → `zoneOf` returns `Result<Zone>`** (C22). The planner had
  recommended keeping `zoneOf: Zone?`; the user chose `Result<Zone>`: a miss is
  `Result.failure(UnzonedEntityException(...))`, never a throw and never `null`, with
  `UnzonedEntityException` a new public type in this package (extends `NoSuchElementException`, carries
  `val entityId: EntityId`, stackless, `@ExperimentalGameToolsApi` like `ZoneIndex`); `entitiesIn` is
  unchanged; #77's scope expands explicitly (header). Applied in: the header and §1.1; §1.2 (the call
  sites); §1.3; §2 and §2.1; §3.1 (signature, error handling, KDoc); §3.5 and the new §3.7; §4 (a
  stage of its own for the type); §5 (README world row and zone bullet, CONTRIBUTING's world row, the
  #77 CHANGELOG bullet); §6 (the helper, the call-site table, every `zoneOf` assertion, the new
  `UnzonedEntityExceptionTest`, the new level-3, 4a and 4b assertions); §7 risks 12–16; §8 (commit 2,
  commit 3's body, commit 5); §9 (#79's removal list).

No other decision here is the human's: test renames, commit split, the CONTRIBUTING addition, the
no-logger call, and the planner calls inside C22 (`final`, the constructor, the message, no cause —
architecture §4.5) are made above with reasons, and the user can overturn any of them in review.

---

## 11. Sequencing & follow-ups

1. Unit 1 lands first — Step 0, then its commits 1–3 (the bound contract; `systemOf` with
   `MissingWorldSystemException`; README/CHANGELOG). No stage waits on a decision any more. Verify the
   §8 precondition, then this unit's commits 1–7 in order. Technical dependencies, for the record:
   commits 1–2 need only Step 0 (commit 2's `UnzonedEntityException` needs nothing from unit 1);
   commit 3 needs unit 1's commits 1–2 (`AbstractWorldSystem`, and `systemOf` for its tests);
   commit 5 needs unit 1's commit 3 (the README bullet it inserts into).
2. Commit 3 is the only risky one; run `componentTest deterministicTest`, then `integrationTest
   e2eTest`, then the Level-1 checks (§6.1), then the docs commits.
3. Hold any release containing #47 until the PR merges (§7 risk 1).
4. The planner's separate docs commit (architecture §11.2, plus §11.3's callouts — approved
   2026-10-02 and applied) may land before or after; no ordering dependency with this unit's files. (Its re-pointed
   references to "the replacement plan's test-opt-in section" cite §3.4 here, which keeps its number.)
5. **Follow-ups owned elsewhere:** #79 (graduation: §9 list, including `UnzonedEntityException`), #49's
   re-plan (`PhysicsSystem` as a `WorldSystem`; revise `step(world)` signatures in
   `docs/plans/86-phase-1-map-and-space/49-physics/architecture.md`; decide whether `bodyFor` follows `zoneOf` to a `Result` —
   architecture §7.5), Phase 3 interest filtering and #50 vision (use `systemOf<ZoneIndex>()`, unwrap
   `zoneOf`'s `Result`), and the website update when #86 closes (zones and World Systems together).
6. The owed `ZoneIndex` openness review (`docs/api-openness-decisions-6.0.0.md`) starts from this
   shape: a `final` `AbstractWorldSystem` with a public constructor, no `refresh`, `zoneOf` returning
   `Result<Zone>`, reads Experimental until #79 — and includes the `final` `UnzonedEntityException`
   (architecture §11.3 item 5 recorded that there — approved 2026-10-02 and applied).
