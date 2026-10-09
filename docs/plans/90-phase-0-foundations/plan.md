# Plan: GameTools Phase 0 — Foundations

## Header / Association

- **Covers:** Phase 0 of `docs/framework-vision-and-roadmap.md` §3. Instruction from Spartak
  Singh via a Claude Code session on 2026-09-05: *"plan phase 0."*
- **Roadmap scope of Phase 0** (verbatim from the roadmap): entity identity, a typed event
  bus, a deterministic tick, an opt-in fixed-timestep loop, and the two combat bug fixes
  (`Alive.cancelAttack()` #1, "keeps attacking a dead target" #2). Also named as the *root
  cause* fix for snapshot identity (#3).
- **Status:** planning only. No source, test, or build file has been modified.
- **Baseline:** GameTools `3.0.0`, single Gradle module, `master` clean at `262a347`.
- **Target version:** `3.0.0` → **`3.1.0`** (one Feature release). Every change in this phase
  is additive or a bug fix — see §7. No major bump.
- **Open GitHub issues addressed:** #1 (feature), #2 (bug), #3 (bug) — all three close in this
  phase (see Open Decision D for #3's scope).
- **New tracking issues to file** (Open Decision I): "event bus", "deterministic tick",
  "simulation loop", and an "entity identity" issue distinct from #3. CONTRIBUTING requires a
  `<issue#>` branch prefix, so these are filed before their branches are cut.
- **Upstream dependency:** none. Unlike Phase 3, Phase 0 touches no WebTools surface.
- **Related docs:** `docs/framework-vision-and-roadmap.md` (§2 target architecture, §3 phases,
  §7 transport boundary); `docs/webtools-2.0.0c-upgrade-plan.md` (plan-doc format precedent).

---

## 1. Context

### 1.1 What Phase 0 builds

Five foundational capabilities, all in the **current single module** (Open Decision A):

| # | Capability | New public surface (summary) |
|---|-----------|------------------------------|
| 1 | **Entity identity** | `EntityId` value class; `GameObject.entityId` (assigned by the owning `World`); `World.byId(id)`; `id` field on every `DrawableSnapshot` variant. Closes #3. |
| 2 | **Typed event bus** | `GameEvent` sealed hierarchy; `EventBus`; `World.events`. `World` and `Alive` publish lifecycle/combat/death events. |
| 3 | **Deterministic tick** | `RandomSource`; `World(seed: Long)`; `World.random`; `World.tickCount`. `Math.random()` in `Alive` is routed through the seeded source. Tick/event ordering documented on `World.tick`. |
| 4 | **Opt-in fixed-timestep loop** | `SimulationLoop`; `LoopSettings` (live-mutable, thread-safe). Convenience over `World.tick()`; nothing depends on it. |
| 5 | **Combat lifecycle fixes** | `Alive.cancelAttack()` + `onAttackCancelled()` (#1); auto-end an attack when the target dies/leaves + `onAttackEnded()` (#2). |

### 1.2 Current-state facts (verified against `master`)

- **No identity anywhere.** `GameObject` has `location`, `active`, `capabilities`, `buffs` —
  no id. `World.gameObjects` is an `ArrayList<GameObject>` in insertion order; `broadcast()`
  sends `List<DrawableSnapshot>` positionally (issue #3). `World` has no `id → object` lookup.
- **Two add paths.** `World.add(obj)` (also sets `Alive.world`) *and* direct
  `world.gameObjects += obj`. `WorldTest` and others use the direct path heavily. Any
  id-assignment or event-on-spawn hook must cover both (see §2.1).
- **One source of nondeterminism.** `Alive.attemptHit` (`Alive.kt:169`):
  `if (Math.random() > target.evasion) hit(target)`. Grep confirms it is the only
  `Math.random` / `Random` / `System.*` / time call in `src/main`.
- **No loop.** `World.tick()` KDoc: *"World does not run itself — an external game loop calls
  tick once per frame."* Memory note `gameserver-is-library-no-loop` (updated 2026-09-05):
  an opt-in loop is now allowed; a *mandatory* loop is not.
- **`World.tick()` order today:** `rebuildQuadtree()` → tick each object over
  `gameObjects.toList()` (snapshot, so mid-pass adds are safe) → drain `removeList`. Per
  object: `ageBuffs()` → `onUpdate()` → `pruneExpiredBuffs()` (in `GameObject.tick`).
- **`Alive` attack cycle** (`Alive.kt:100-153`): `attackState ∈ {NONE, ISSUED, INPROGRESS}`,
  all `private`, plus `private var attackTarget: Alive?` / `attackProgress: Double`.
  `considerAttack()` runs every `onUpdate()` while `can(ATTACK)`. In `ISSUED`, while out of
  range it **overwrites `destination` every tick** with the target's location — so an external
  move order cannot stick (issue #1). `progressAttack()` on a landed swing resets to `ISSUED`
  and loops; nothing checks whether `attackTarget` is still alive (issue #2). `die()` is
  idempotent via `deathHandled`.
- **`Alive` already has a `world: World?` back-reference** (set by `World.add`, null on the
  direct path unless set by hand). `GameObject` does **not**. Combat tests
  (`AliveCombatTest`) never put the attacker or target in a `World` — the #2 guard must be
  safe when `attackTarget.world == null` (Open Decision G).
- **Snapshot types** live beside their domain classes (`GameObjectSnapshot` in `GameObject.kt`,
  `VisibleObjectSnapshot` in `VisibleObject.kt`, `ActorSnapshot` in `Actor.kt`, `AliveSnapshot`
  in `Alive.kt`), all `@Serializable`, polymorphic via `DrawableSnapshot` sealed interface with
  a `type` discriminator. `GameObjectSnapshot.buffs` already uses the
  `= emptyList()` default-for-back-compat pattern that #3's new field will reuse.
- **Test layout:** `src/test/kotlin/com/spartanlabs/gaming/testing/{component,integration,deterministic,e2e,nonfunctional}`.
  No `gating` (L1) or `uat` (L5) packages. `build.gradle.kts` wires one Gradle task per
  existing level via `registerLevelTest(...)`.
- **Versioning** (`CONTRIBUTING.md`): version lives only in `coordinates(...)` in
  `build.gradle.kts`. `feat:` → Feature release; `fix:` → MinorChange/letter;
  `feat!:`/`BREAKING CHANGE:` → Major. Release via a `release/<v>` branch that bumps the
  coordinate + moves `CHANGELOG.md [Unreleased]`.

### 1.3 Acceptance criteria

1. All five capabilities implemented in `src/main`, each with KDoc per the Audience-Reach
   standard and region-grouped imports.
2. Every existing test still passes, unmodified where possible; the few that must change
   (see §5.6) change only to accommodate additive surface, not to paper over regressions.
3. New tests at the levels in §5, in the correct `testing.<level>` packages, one class per file.
4. `Math.random()` no longer appears in `src/main`. A seeded `World` reproduces identical
   combat outcomes across two runs of the same script (L4a test proves it).
5. `SimulationLoop` is provably optional: `World` / `GameObject` / `GameServer` compile and
   all non-loop tests pass with no reference to it.
6. Issues #1, #2, #3 close. `README.md` + `CHANGELOG.md [Unreleased]` updated.
7. `./gradlew build dokkaGeneratePublicationHtml` is green (Dokka also catches broken KDoc links).
8. `CHANGELOG.md` 3.1.0 entry documents the new snapshot `id` field so downstream consumers
   can find and adopt it on their own schedule. (GameTools files no issues against consumers.)

---

## 2. Design

### 2.1 Entity identity

**`EntityId`** — `@JvmInline value class EntityId(val raw: Long)`, in a new file
`gameobjects/EntityId.kt`. `Comparable`, `toString()` → `"#<raw>"`. Companion holds
`UNASSIGNED = EntityId(0L)` (an object the owning `World` has not numbered yet, and the
decode sentinel for a pre-3.1 snapshot). Ids are opaque handles; a consumer addresses by them
but does not parse them.

**Allocation — per-`World` (Open Decision B, decided).** Each `World` owns a monotonic
allocator; ids are assigned by the `World` when an object joins it, so they are **reproducible
per world for a given join order** (a determinism and Phase-5-persistence win) and two `World`s
in one process each start from 1 without conflict (`byId` is per-world).

```kotlin
// World.kt
private var nextRawId: Long = 0L
private val byId: HashMap<EntityId, GameObject> = HashMap()

fun byId(id: EntityId): GameObject? = byId[id]

/** Assigns [obj] an id if it has none, indexes it (and its sub-object tree, so
 *  health bars / nameplates are stable too), and returns whether it was new. */
private fun enrol(obj: GameObject): Boolean { /* number if UNASSIGNED, put in byId, recurse subObjects */ }
```

`GameObject` gets `var entityId: EntityId = EntityId.UNASSIGNED` — a `var` set **once** by the
first `World` to enrol it (guarded: re-enrolling a numbered object keeps its id). An object
never added to a `World` (a standalone test object, a detached sub-object) keeps `UNASSIGNED`,
and its snapshot `id` serializes as `0` — fine, since only world-resident top-level objects
are command targets.

**Kept in sync** at three points so both add paths and mid-tick spawns are covered without
coupling everything to `add()`:

- `World.add(obj)` enrols immediately, so `byId` works before the first tick.
- `World.tick()` enrols, at the top (after `rebuildQuadtree`), any `gameObjects` entry not yet
  numbered — this catches direct `world.gameObjects += obj` and last tick's mid-pass spawns —
  and drops `byId` entries whose object is no longer in `gameObjects`.
- `removeList` drain de-indexes.

An object that leaves a `World` keeps its `entityId` (it is not cleared), so re-adding the
same instance is idempotent and a late-arriving command that names it still resolves — to an
object `byId` no longer returns once it is gone, which is the correct "target no longer here"
signal.

**Snapshot field** (closes #3 — Open Decision D) — add `val id: Long` to each
`DrawableSnapshot` variant, defaulted for decode back-compat:

```kotlin
data class VisibleObjectSnapshot(
    val id: Long = UNIDENTIFIED,   // 2.1: 0 = a pre-3.1 payload with no id
    val gameObject: GameObjectSnapshot,
    /* ...unchanged... */)
```

`DrawableSnapshot` gains `val id: Long` as an interface member; `UNIDENTIFIED = 0L` constant
in its companion. Each `from(...)` factory passes `visibleObject.entityId.raw`. Sub-object
snapshots carry their own id the same way. No change to the polymorphic `type` machinery.

**Consumer story:** the server keeps the `World`; incoming commands name an `id`; it calls
`world.byId(EntityId(id))`. `GameServer` itself is *not* changed in Phase 0 (it still
broadcasts a positional list — that list now simply carries ids). Wiring `GameServer` command
resolution to ids is downstream work (§7).

### 2.2 Typed event bus

**`GameEvent`** — `sealed interface GameEvent` in `event/GameEvent.kt`. Phase 0 variants
(kept deliberately small; Phase 2/6 add ability/item events):

| Event | Published by | When |
|-------|-------------|------|
| `EntitySpawned(entity: GameObject)` | `World` | object first seen in `gameObjects` during `tick()` reconcile, or in `add()` |
| `EntityRemoved(entity: GameObject)` | `World` | after `removeList` drain |
| `AttackIssued(attacker: Alive, target: Alive)` | `Alive.issueAttack` | on issue |
| `AttackLanded(attacker: Alive, target: Alive, damage: Double)` | `Alive.hit` | each landed swing |
| `DamageDealt(source: Alive?, target: Alive, amount: Double)` | `Alive.takeDamage` | each health subtraction (source null for non-combat damage) |
| `EntityDied(entity: Alive, killer: Alive?)` | `Alive.die` | first tick health ≤ 0 |
| `AttackCancelled(attacker: Alive, formerTarget: Alive?)` | `Alive.cancelAttack` | §2.5 |
| `AttackEnded(attacker: Alive, formerTarget: Alive?, reason: EndReason)` | `Alive` | §2.5 (target died/left) |
| `BuffApplied(target: GameObject, buff: Buff)` / `BuffExpired(...)` | `GameObject.applyBuff` / `pruneExpiredBuffs` | on apply / expiry |

**`EventBus`** — in `event/EventBus.kt`:

```kotlin
class EventBus {
    fun interface Listener { fun on(event: GameEvent) }
    fun subscribe(listener: Listener): Subscription
    fun publish(event: GameEvent)
}
```

- Synchronous, single-threaded, listeners invoked in subscription order (Open Decision E:
  direct dispatch, not a drained queue).
- A throwing listener is caught + logged (slf4j `warn`) and does not abort the publish or the
  tick.
- Re-entrant `publish` (a listener that publishes) is enqueued and drained after the current
  listener returns, so ordering stays deterministic and the stack stays shallow.
- `Subscription.close()` unsubscribes; `World` holds no strong opinion on lifetime.

**Location & object access** (Open Decision C) — the bus lives on `World` as
`val events: EventBus`. `World` publishes the lifecycle events itself. `Alive` publishes via
its existing `world?.events` — a **no-op when the actor is not in a `World`**, which matches
how `die()` already tolerates a null `world`. Non-`Alive` objects that later need to emit
events get access when Phase 1 introduces a broader per-tick context / `GameObject.world`
back-reference; Phase 0 does not need it.

### 2.3 Deterministic tick

**`RandomSource`** — `event`-free small interface in `simulation/RandomSource.kt`:

```kotlin
interface RandomSource {
    fun nextDouble(): Double            // [0.0, 1.0)
    fun nextInt(untilExclusive: Int): Int
    fun nextBoolean(): Boolean
}
```

Default impl `SeededRandom(seed: Long) : RandomSource` wrapping `kotlin.random.Random(seed)`.

**`World`** gains:

```kotlin
class World(val seed: Long = Random.nextLong()) {
    val random: RandomSource = SeededRandom(seed)
    var tickCount: Long = 0L; private set
    /* ... */
}
```

The default arg keeps `World()` working. The chosen `seed` is logged at `info` on
construction so a failing scenario can be replayed by pinning it.

**`Alive.attemptHit`** changes from `Math.random()` to:

```kotlin
private infix fun attemptHit(target: Alive) =
    if (rng().nextDouble() > target.evasion) hit(target) else Unit

private fun rng(): RandomSource = world?.random ?: fallbackRandom
private val fallbackRandom = SeededRandom(entityId.raw)   // deterministic even when worldless
```

Worldless combat (the current `AliveCombatTest` style) stays deterministic per-instance
rather than depending on global `Math.random()`.

**Documented ordering** — `World.tick()` KDoc is expanded to state the canonical order, and
`tickCount` increments once per call:

1. `tickCount++`
2. `rebuildQuadtree()`
3. reconcile `byId`; publish `EntitySpawned` for newly-seen objects
4. tick each object in **`gameObjects` insertion order** (over a `toList()` snapshot)
   — per object: `ageBuffs()` → `onUpdate()` → `pruneExpiredBuffs()`
5. drain `removeList`; publish `EntityRemoved` for each
6. (event bus drains synchronously at each `publish`, not batched here)

### 2.4 Opt-in fixed-timestep loop

**`SimulationLoop`** in `simulation/SimulationLoop.kt`:

```kotlin
class SimulationLoop(
    private val world: World,
    val settings: LoopSettings = LoopSettings(),
    private val onTick: (tickCount: Long) -> Unit = {},
    private val clock: () -> Long = System::nanoTime,   // injectable for tests
) {
    fun start(): Result<Unit>   // spawns one daemon thread "gametools-sim-loop"
    fun stop(): Result<Unit>    // idempotent
    val isRunning: Boolean
}

class LoopSettings(tickRateHz: Double = 20.0, maxCatchUpTicks: Int = 5) {
    @Volatile var tickRateHz: Double = tickRateHz      // validated > 0 on set
    @Volatile var maxCatchUpTicks: Int = maxCatchUpTicks
}
```

- Fixed-timestep accumulator: each iteration adds real elapsed time, runs `world.tick()` +
  `onTick(world.tickCount)` while the accumulator holds a full step, capped at
  `maxCatchUpTicks` per iteration (spiral-of-death guard), then parks the remainder.
- Runs `world.tick()` on the loop thread; `onTick` runs there too. Documented single-threaded
  contract: do not mutate the world from another thread while the loop runs.
- `clock` injection lets L2 tests drive it with a fake clock — no real sleeping in unit tests.
- **Optionality is a hard requirement**: `SimulationLoop` imports `World`, never the reverse.
  A `grep` in CI-adjacent review confirms `World.kt` / `GameObject.kt` / `GameServer.kt` have
  no `simulation.*` import.

Open Decision F: ship the thread-owning form (recommended) vs. a caller-driven
`advance(realElapsedNanos)` with no thread. Recommendation keeps both — `advance()` is the
internal step the thread calls, and it can be left `public` for callers who want to drive it
from their own loop.

### 2.5 Combat lifecycle: `cancelAttack()` (#1) and auto-end on target loss (#2)

Add to `Alive`'s `//region COMBAT`:

```kotlin
/** Cancels any pending or in-progress attack. Keeps the current [destination];
 *  issue a fresh move order separately if needed. No-op if not attacking. */
fun cancelAttack() {
    if (attackState == AttackState.NONE) return
    val former = attackTarget
    clearAttack()
    onAttackCancelled()
    world?.events?.publish(GameEvent.AttackCancelled(this, former))
}

protected open fun onAttackCancelled() {}

/** Hook run on the attacker when an attack stops on its own because the target
 *  died or left the world. Does nothing by default. */
protected open fun onAttackEnded(reason: EndReason) {}
enum class EndReason { TARGET_DIED, TARGET_REMOVED }

private fun clearAttack() {
    attackState = AttackState.NONE
    attackTarget = null
    attackProgress = 0.0
}
```

**#2 fix** — a guard at the top of `considerAttack()`:

```kotlin
private fun considerAttack() {
    if (attackState != AttackState.NONE) {
        val reason = attackEndReason()
        if (reason != null) {
            val former = attackTarget
            clearAttack()
            onAttackEnded(reason)
            world?.events?.publish(GameEvent.AttackEnded(this, former, reason))
            return
        }
    }
    when (attackState) { /* ...existing NONE / ISSUED / INPROGRESS branches... */ }
}

/** Non-null when the current target can no longer be fought. */
private fun attackEndReason(): EndReason? = when {
    attackTarget == null -> null                       // defensive; state machine keeps these paired
    !attackTarget!!.isAlive -> EndReason.TARGET_DIED    // the filed #2 repro
    attackTargetLeftWorld() -> EndReason.TARGET_REMOVED
    else -> null
}

/** True only if the target *had* a world and is no longer in it — never true for
 *  a target that was never added to a World (so worldless combat tests are unaffected). */
private fun attackTargetLeftWorld(): Boolean {
    val w = attackTarget!!.world ?: return false
    return attackTarget !in w.gameObjects && attackTarget !in w.removeList
}
```

`TARGET_DIED` alone covers the exact filed repro; `TARGET_REMOVED` is the cheap extra
(Open Decision G). The `world == null` care-taking keeps every existing `AliveCombatTest`
green (those actors are never in a `World`).

`issueAttack` also starts publishing `AttackIssued` (via `world?.events`), and `die()`
publishes `EntityDied` with the killer when known (the killer is the `Alive` currently in
`attack`/`hit`/`dealDamage` against this target — thread the attacker through
`takeDamage`/`calculateDamageTaken` as a nullable `source`, or capture it in a
`private var lastDamagedBy: Alive?`). Recommendation: add a nullable `source: Alive?` param
down the private `dealDamage → takeDamage → die`-triggering path; it is all private so no
API impact.

---

## 3. File-by-file changes

### 3.1 New source files (`src/main/kotlin/com/spartanlabs/gaming/`)

| File | Contents |
|------|----------|
| `gameobjects/EntityId.kt` | `EntityId` value class + `EntityId.UNASSIGNED`. (Allocation lives in `World`, not here.) |
| `event/GameEvent.kt` | `sealed interface GameEvent` + Phase 0 variants |
| `event/EventBus.kt` | `EventBus`, `EventBus.Listener`, `Subscription` |
| `simulation/RandomSource.kt` | `RandomSource` interface + `SeededRandom` |
| `simulation/SimulationLoop.kt` | `SimulationLoop`, `LoopSettings` |

New packages `com.spartanlabs.gaming.event` and `com.spartanlabs.gaming.simulation`. (If
Open Decision A defers the module split — recommended — these are just packages, not modules.)

### 3.2 Modified source files

| File | Change |
|------|--------|
| `gameobjects/GameObject.kt` | add `var entityId: EntityId = EntityId.UNASSIGNED` (set once by the enrolling `World`). Buff events (`BuffApplied`/`BuffExpired`) are **dropped from Phase 0** — no clean publish path without a `GameObject`→bus link; added in Phase 1 with the tick-context. §2.2 table reflects this. |
| `gameobjects/World.kt` | `seed` ctor param + `random` + `tickCount` + `events`; `nextRawId` allocator + `byId` map + `byId()` accessor + `enrol()`; `tick()` reconcile (enrol unnumbered, drop departed) + lifecycle events + documented ordering; `add()` enrols + publishes `EntitySpawned`; `removeList` drain publishes `EntityRemoved` + de-indexes |
| `gameobjects/Alive.kt` | `cancelAttack()` + `onAttackCancelled()` + `onAttackEnded()` + `EndReason` + `clearAttack()`; `#2` guard in `considerAttack()`; route `attemptHit` through `rng()`; publish `AttackIssued`/`AttackLanded`/`DamageDealt`/`EntityDied`/`AttackCancelled`/`AttackEnded`; thread nullable `source: Alive?` down the private damage path |
| `gameobjects/VisibleObject.kt` | `id: Long` on `DrawableSnapshot` (interface) + `VisibleObjectSnapshot` (+ `UNIDENTIFIED` const); `from` passes `entityId.raw` |
| `gameobjects/Actor.kt` | `id` on `ActorSnapshot`; `from` passes it through |
| `gameobjects/GameObject.kt` (snapshot) | `id` on `GameObjectSnapshot`? — **no**: `GameObjectSnapshot` is nested inside the drawable variants, one `id` at the `DrawableSnapshot` level is enough. Leave `GameObjectSnapshot` alone. |
| `gameobjects/Alive.kt` (snapshot) | `id` on `AliveSnapshot`; `from` passes it through |

### 3.3 Build / docs

| File | Change |
|------|--------|
| `build.gradle.kts` | no version bump here (that is the `release/3.1.0` branch's job). No new test task unless a `gating` level is introduced (not planned). |
| `README.md` | Features: new "Simulation" subsection (loop, seeded `World`, event bus, entity ids); note `broadcast` snapshots now carry a stable `id`; Architecture table gains `SimulationLoop` / `EventBus` rows |
| `CHANGELOG.md` | `[Unreleased]`: Added (entity ids on snapshots + `World.byId`, `EventBus`, `RandomSource`/seeded `World`, `SimulationLoop`, `Alive.cancelAttack`), Fixed (#2), plus "Closes #1 #2 #3" |

---

## 4. (reserved)

---

## 5. Test plan (5-level hierarchy)

### 5.1 Level 2 — component (`testing.component`, no sockets)

| New file | Asserts |
|----------|---------|
| `gameobjects/EntityIdTest.kt` | value semantics / equality / ordering; `toString`; `UNASSIGNED` is `raw == 0` |
| `event/EventBusTest.kt` | listeners fire in subscription order; a throwing listener is isolated (others still fire, publish returns); re-entrant publish drains after the current listener; `Subscription.close()` stops delivery |
| `gameobjects/WorldEntityRegistryTest.kt` | `add()` and `world.gameObjects +=` both get the object numbered and into `byId`; a standalone object is `UNASSIGNED`; ids are per-world monotonic from 1 and reproducible for a fixed join order; two `World`s allocate independently; `byId` returns null after removal; a mid-tick spawn is numbered + resolvable next tick; a re-added instance keeps its id; sub-objects (health bar) get numbered; `EntitySpawned`/`EntityRemoved` fire once each at the right time |
| `gameobjects/AliveAttackLifecycleTest.kt` | `cancelAttack()` is a no-op when idle; clears state; **leaves `destination` untouched** (the #1 core complaint); `onAttackCancelled` fires; `AttackCancelled` published. #2: attacker stops within one tick of the target's death — `attackTarget` cleared, `destination` no longer re-pointed, `onAttackEnded(TARGET_DIED)` fired (this is the issue #2 repro, scripted) |
| `simulation/SimulationLoopTest.kt` | with an injected fake clock: N accumulated steps ⇒ N `world.tick()` calls; `maxCatchUpTicks` caps a long stall; changing `settings.tickRateHz` mid-run takes effect; `stop()` is idempotent; `start()` twice is rejected/no-ops |
| `simulation/LoopSettingsTest.kt` | non-positive `tickRateHz` rejected; fields are `@Volatile` (documented contract) |

### 5.2 Level 3 — integration

None. Phase 0 adds no external interface. (`GameServer` is untouched.)

### 5.3 Level 4a — deterministic (`testing.deterministic`)

| New file | Asserts |
|----------|---------|
| `SeededCombatDeterminismTest.kt` | two `World(seed = 42)` instances running the **same** scripted attack sequence produce bit-identical `health.current` timelines; `World(seed = 1)` vs `World(seed = 2)` are allowed to diverge (proves the RNG is actually in play) |
| `EntityIdSequenceLawsTest.kt` | within one `World`, ids assigned in join order are `1, 2, 3, …`; never reused after removal; the same scripted build produces the same id→object mapping on a re-run; `EntityId` ordering matches `raw` ordering |

### 5.4 Level 4b — e2e (`testing.e2e`, binds ports)

- Extend `ClientServerRoundTripTest.kt`: the broadcast `STATE` payload carries a non-zero
  `id` per object; an object added between two broadcasts does **not** change the `id` of the
  others (the concrete #3 scenario); a client can round-trip an `id` back and the server
  resolves it via `world.byId`.

### 5.5 Level 4c — nonfunctional (`testing.nonfunctional`, binds ports)

- Extend `WorldTickThroughputTest.kt`: tick throughput with an `EventBus` that has K
  subscribers stays within an acceptable factor of the no-subscriber baseline (guards against
  per-tick allocation blowups in event publishing).
- `SimulationLoopTimingTest.kt`: with the **real** clock and a trivial world, measured tick
  rate is within tolerance of `settings.tickRateHz` over a few seconds; raising the rate
  mid-run is observed.

### 5.6 Existing tests — expected impact

| File | Impact |
|------|--------|
| `AliveCombatTest.kt` | actors are never in a `World`; the #2 guard's `attackTargetLeftWorld()` returns false for `world == null`, and `!isAlive` only trips on actual death → **the "sustained attacks deplete… `assertFalse(target.isAlive)`" test still passes** (death happens on the final tick; the guard clearing state afterward doesn't un-kill the target). Verify no test ticks *past* the kill and asserts the attacker is still swinging — none currently do. Likely **no change needed**. |
| `AliveDeathTest.kt` | unaffected — no attacker involved. |
| `WorldTest.kt` | `World()` still valid (default `seed`). `Spawner`/`TickCounter` still work. May add assertions for `EntitySpawned`, but not required. **No change needed.** |
| `GameServerBroadcastTest.kt` / `DrawableSnapshotTest.kt` / `SnapshotRoundTripTest.kt` | new `id` field has a default ⇒ existing decode assertions pass; encode assertions that pin exact JSON gain an `"id"` key — **these tests update** to include it. |
| `ClientServerRoundTripTest.kt` | updated per §5.4. |

---

## 6. Risks & mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|-----------|
| The #2 guard breaks worldless `AliveCombatTest` scenarios | Medium | `attackTargetLeftWorld()` explicitly returns false on `world == null`; `!isAlive` is the only other trip. L2 test covers both worldless and world-resident cases. |
| Per-`World` allocator: an object used across two `World`s gets its id from the first and keeps it, so it will not match the second world's `byId` under a *different* number | Low | `entityId` is set once and never cleared; `enrol` in the second world indexes it under its existing id. Documented. Reusing one `GameObject` instance across worlds is already unusual. |
| Direct `world.gameObjects +=` then reading `byId` *before* a tick returns null (not yet enrolled) | Medium | `add()` enrols eagerly; the gap is only for the direct path pre-first-tick. Documented on `byId`'s KDoc: "resolves objects added via `add()` immediately, and direct-list additions from the next `tick()`." |
| Event publishing adds per-tick allocation / GC pressure at the medium scale target | Medium | Events are small `data class`es; L4c throughput test guards it. If it bites, Phase 3 can switch hot events to a pooled/struct form — not a Phase 0 concern. |
| `SimulationLoop` accidentally becomes load-bearing | Low | Hard rule in §2.4 + a review grep; AC 5 makes it explicit. |
| `id` on snapshots is a wire change that breaks an existing hand-rolled client | Low | Additive field with a decode default (same pattern as `buffs`). Documented in CHANGELOG + §7. GameTools' own `GameServer`/tests are the only in-repo consumers. |
| Scope creep — event taxonomy balloons | Medium | Phase 0 variant list in §2.2 is frozen; buff events explicitly deferred to Phase 1 (§3.2). |
| `killer` attribution threading touches many private methods in `Alive` | Low | All private; no API surface; if it gets messy, fall back to a `private var lastDamagedBy` captured in `takeDamage`. |

---

## 7. Breaking-change & cross-repo analysis

**Breaking? No.** Every item is additive or a bug fix:

- `EntityId`, `World.byId`, `World.random`, `World.tickCount`, `World.events`,
  `World(seed)` (default arg), `EventBus`, `RandomSource`, `SimulationLoop`,
  `Alive.cancelAttack`, `onAttackCancelled`, `onAttackEnded` — all new.
- `id` on `DrawableSnapshot` variants — new field, decode-defaulted (`0`).
- `Alive` no longer attacks a dead target — **bug fix** (`fix(gameobjects)`, Refs #2).
- `Math.random()` → seeded — behaviour becomes *reproducible*; not an API change.

⇒ Conventional Commits: `feat(gameobjects)`, `feat(serialization)`, `feat(simulation)`,
one `fix(gameobjects)`. Per `CONTRIBUTING.md` → **one Feature release, `3.1.0`**.

**Downstream consumers** (`SpartanLabsGaming/MyGameServer`, `SpartanLabsGaming/GameGraphics`):

- Both address broadcast objects **by list index** today (issue #3). After 3.1.0 they *can*
  and *should* switch to the `id` field + `world.byId` on the server. This is a matching
  change, not a forced break — index addressing keeps working until objects spawn/despawn at
  runtime (which the combat/death systems already do, so it is latent-broken now).
- **The downstream projects track and make that change themselves** — GameTools does not file
  issues against its consumers. The `CHANGELOG.md` 3.1.0 entry documents the new `id` field so
  the change is discoverable from the release notes.

**Upstream:** none. Phase 0 is the one phase with zero WebTools dependency — a good place to
build momentum while the WebTools prerequisites (#8–#11) for Phase 3 are worked separately.

---

## 8. Version-control approach

Phase 0 is too large for one branch (`CONTRIBUTING.md`: branches live hours-to-days). Split
into **five feature PRs**, each rebased on `master`, each merged as a `--no-ff` merge commit
(semi-linear history):

| PR | Branch | Closes | Depends on | Commits (rough) |
|----|--------|--------|-----------|-----------------|
| 1 | `feature/<new>-entity-identity` | #3 | — | `feat(gameobjects): EntityId + World registry`; `feat(serialization): stable id on DrawableSnapshot` |
| 2 | `feature/<new>-event-bus` | — | PR 1 (events reference entities) | `feat(event): GameEvent + EventBus`; `feat(gameobjects): World publishes lifecycle events` |
| 3 | `feature/<new>-deterministic-tick` | — | — (parallel to 2) | `feat(simulation): RandomSource + seeded World`; `refactor(gameobjects): route evasion roll through RandomSource`; `docs(gameobjects): document World.tick ordering` |
| 4 | `feature/1-alive-attack-lifecycle` | #1, #2 | PR 2 (publishes attack events), PR 3 (rng) | `feat(gameobjects): Alive.cancelAttack`; `fix(gameobjects): stop attacking a dead or removed target` |
| 5 | `feature/<new>-simulation-loop` | — | PR 3 (`tickCount`) | `feat(simulation): opt-in fixed-timestep SimulationLoop` |

- This plan document is committed **in PR 1's first commit** (precedent:
  `webtools-2.0.0c-upgrade-plan.md`), so `git log --follow` binds plan to code.
- `CHANGELOG.md [Unreleased]` accretes across PRs 1–5.
- Each PR: `./gradlew build dokkaGeneratePublicationHtml` green, branch up to date via
  **rebase** (never merge from master), PR title a valid Conventional Commit.
- After PR 5 merges: **`release/3.1.0`** branch — bump `coordinates(...)` to `3.1.0`, move
  `[Unreleased]` → `[3.1.0] — <date>`, PR → merge (`chore(release): 3.1.0`), tag `v3.1.0`,
  push `--follow-tags`, then the manual `./gradlew publishAndReleaseToMavenCentral` (Spartak
  runs this — never the agent).
- New GameTools tracking issues to file first so each branch has an `<issue#>` prefix:
  entity-identity (or reuse #3 for PR 1), event-bus, deterministic-tick, simulation-loop.
  Filed at implementation start, not before.

---

## 9. Open decisions

| ID | Decision | Recommendation |
|----|----------|----------------|
| A | **Module split now, or stay single-module for Phase 0?** | **DECIDED: stay single-module.** Phase 0 adds *packages* (`event`, `simulation`), not modules. The `gametools-*` split (per-module Maven coordinates or a BOM, package moves, downstream coordinate churn) is a dedicated change folded into the start of Phase 1. |
| B | **`EntityId` allocation: process-global counter vs. per-`World` allocator.** | **DECIDED: per-`World` allocator.** Each `World` numbers its own entities from 1 in join order (see §2.1). Reproducible per world, friendly to Phase 5 persistence (persist the high-water mark). Objects never added to a `World` stay `EntityId.UNASSIGNED`. |
| C | **Event bus access for non-`Alive` objects.** Phase 0 needs it only for `World` (lifecycle) and `Alive` (combat, via its existing `world?`). | **`world.events` + `Alive.world?.events`; defer a general `GameObject.world` back-reference / tick-context to Phase 1**, where the map model and zones need a context object anyway. Drop `BuffApplied`/`BuffExpired` from Phase 0 (no clean publish path yet). |
| D | **Does Phase 0 close #3 fully, or only the "root"?** | **DECIDED: close #3 fully in Phase 0.** The snapshot `id` field is ~1 line per DTO, additive, decode-defaulted. Phase 3's delta protocol is about *diffing* snapshots and does not depend on #3 staying open. |
| E | **Event dispatch: synchronous direct vs. drained queue at tick boundaries.** | **Synchronous direct**, with a small internal queue only to flatten re-entrant `publish`. Simpler, and the netcode (Phase 3) wants events available within the tick that produced them. |
| F | **`SimulationLoop`: owns a daemon thread, or caller-driven `advance()` only.** | **Both** — `advance(realElapsedNanos)` is the public step; `start()/stop()` wrap it in a daemon thread for the common case. |
| G | **#2 auto-end scope: `!isAlive` only, or also "removed from world while still alive".** | **Both**, since `attackTargetLeftWorld()` is cheap and safe (returns false for a never-in-a-world target, so existing tests are unaffected). `TARGET_DIED` is the filed repro; `TARGET_REMOVED` is the cheap completeness win. |
| H | **`killer` attribution in `EntityDied`.** Thread a nullable `source: Alive?` down the private damage path, or capture `lastDamagedBy`. | **Thread `source: Alive?`** through the private `dealDamage → takeDamage` path (all private, no API cost). Fall back to `lastDamagedBy` if it gets unwieldy. |
| I | **Tracking issues.** | **DECIDED: file the four GameTools tracking issues at implementation start** (needed for branch prefixes). **Never file issues against downstream consumers** (MyGameServer, GameGraphics) — those projects track their own matching changes; the 3.1.0 CHANGELOG entry is the notice. |

---

## 10. Constants

- All new code follows `.aiassistant/rules/CLAUDE.md`: Kotlin OO/FP blend, `Result` over
  thrown exceptions for expected failures, structured slf4j logging on lifecycle events,
  KDoc on every public declaration, region-grouped imports (`// 1. Organization Internal` …),
  one test class per file, tests in `testing.<level>` packages.
- `README.md` + `CHANGELOG.md` updated in the same PRs that change external shape.
- No new runtime dependency. `kotlin.random`, `java.util.concurrent.atomic` are stdlib/JDK.
