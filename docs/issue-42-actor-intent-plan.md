# Plan: `Actor.intent` — the standing-order layer (Phase 2 slice)

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#42](https://github.com/SpartanLabsGaming/MyGameTools/issues/42)
  — *"Model a unit's standing order as a first-class Intent on Actor"*.
- **Supersedes:** `docs/actor-intent-orders-plan.md` (design exploration; all ten open
  decisions A–J it raised are closed — see §0 below for how each is carried into this plan).
  That document stays in `docs/` as the historical record of the exploration; it is not
  deleted, and its own final line already says it is superseded by this one.
- **Branch:** `feature/42-actor-intent`
- **Commit:** TBD
- **PR:** TBD — this plan document is committed together with the first commit of its
  implementation (or, if staged, the first stage's commit) so `git log --follow` binds the
  two.
- **What this plans:** Strategy B (intent orchestrates existing mechanisms) for the v1 slice
  of the intent layer: the `Intent` base type, `Idle`, `Move`, `AttackIntent`,
  `Actor.intent`/`issue`/`clearIntent`, `GameEvent.IntentIssued`/`IntentCleared`, the six
  standard commands routed through `issue`, deletion of the #39 `cancelAttack()`
  special-case, and a minimal read-only `intent` tag on `ActorSnapshot`. Consumer intents
  (`AttackMove`, `Patrol`, `Hold`/`HoldPosition`) are explicitly **out of scope** — see §7.
- **Status:** planning only. No source, test, or build file has been modified by this
  document.
- **Baseline:** GameTools `5.1.0` on `master` (PR #41, #44, #45 landed; `docs/phase-1-map-and-space-plan.md`
  merged as a *plan*, not yet implemented). `Actor` and `Alive` both live in `gametools-core`
  today.
- **Related docs:** `docs/actor-intent-orders-plan.md` (concept, rejected alternatives, full
  open-decision record); `docs/framework-vision-and-roadmap.md` (§3 Phase 2 "rich combat" —
  moves `Alive` to `gametools-combat`, unrelated to and not a prerequisite for this plan; §3
  Phase 4 "AI & pathfinding" — where `AttackMove`/`Patrol`/`Hold` attach); `docs/client-command-protocol-plan.md`
  (the open-hierarchy + consumer-registration pattern `Intent` mirrors).

### A note on "Phase 2" / "Phase 4"

Open Decision I (in the superseded doc) names this slice **"Phase 2"** and the consumer
intents **"Phase 4"**. Those labels borrow the *domain* association from
`framework-vision-and-roadmap.md`'s Phase 2 (rich combat) and Phase 4 (AI & pathfinding) —
`AttackIntent` is combat-shaped, `AttackMove`/`Patrol`/`Hold` are AI-shaped — **not** a
scheduling dependency on those phases actually shipping first. This plan's own §7 (breaking-
change analysis, carried from the superseded doc's §7) already establishes Strategy B ships
as an **additive Feature release**, independent of and not blocked on the Phase 1 / Phase 2
module-split work. Read every "Phase 2" / "Phase 4" below as *scope label*, not *roadmap
gate*.

---

## 0. How the closed decisions land here

| ID | Decision | Where it shows up in this plan |
|----|----------|--------------------------------|
| A | Ship Strategy B now; Strategy A deferred to the (separate, later) Phase 2 combat reshape | §2, §3 — every file change below is additive |
| B | `Move` owns the `Movement` mode + destination | §2.2, §3 `Intent.kt` |
| C | Self-clearing goes through the event bus, not a direct callback | §2.3, §3 `Alive.kt` (`AttackIntent`) |
| D | Minimal read-only string tag on the snapshot now; full treatment deferred | §3 `Actor.kt` (`ActorSnapshot.intent`) |
| E | Add `GameEvent.IntentIssued` / `IntentCleared` | §3 `GameEvent.kt` |
| F | Name it `Intent` | throughout |
| G | Single slot, no queue, in v1 | `Actor.intent` is a single `var`, not a `Deque` |
| H | A suppressed intent is held, not cleared | §2.4 — needs **no new code**, see below |
| I | Core `Intent` + `AttackIntent` now (this plan); `AttackMove`/`Patrol`/`Hold` later | §7 scopes those three out |
| J | `Stop` → `Idle` (no order at all); pin-in-place is a separate future `Hold`, not grafted onto `Stop`/`Idle` | §2.2 — this is the trickiest one to implement correctly; see below |

---

## 1. Context

### 1.1 Root cause / requirement this closes

Today "what is this unit doing" is spread across `Actor.movement` (a `Movement` strategy)
and `Alive`'s private `AttackState` + `attackTarget`, with no single answer to "what did the
player order this unit to do." The symptom that surfaced it, #39, is now patched as a
special case: `StandardCommandApplier.onActor` (`gametools-net/src/main/kotlin/com/spartanlabs/gaming/networking/command/StandardCommandApplier.kt:130`)
hard-codes `(actor as? Alive)?.cancelAttack()` after every movement command applies. That is
one interaction out of a combinatorial set an RTS unit-order model needs (move cancels
attack, attack-move, patrol, hold position, …); the intent layer generalizes it into "issuing
any intent clears the last one," so #39's rule becomes the *general* rule instead of the
first of many one-off patches.

### 1.2 Current-state facts (verified against `master`, this session)

- `Actor` (`gametools-core/src/main/kotlin/com/spartanlabs/gaming/gameobjects/Actor.kt`):
  `var movement: Movement`, `var destination: Point`, `internal var hasSettled`. **No `world`
  reference and no order/intent concept.**
- `Alive` (`gametools-core/src/main/kotlin/com/spartanlabs/gaming/gameobjects/Alive.kt`):
  `var world: World? = null` (line 77) — **this is the only place `world` is declared today**;
  a plain `Actor` has no way to reach the event bus. Private `AttackState { NONE, ISSUED,
  INPROGRESS }`, `attackTarget: Alive?`. `issueAttack(target)` / `cancelAttack()` public.
  `considerAttack()` (private, called from `onUpdate`) sets `destination = attackTarget.location`
  while closing to range — the attack mechanism already drives movement, independent of
  `Actor.movement`, which stays whatever it was set to. `endAttackIfTargetLost()` fires
  `onAttackEnded` + `GameEvent.AttackEnded` when the target dies or leaves the world, and is
  reached only from inside `considerAttack()` — **it already publishes on the world bus with
  no calling code needing to know about the intent layer.**
- `GameObject.can(Capability)` (`gametools-core/.../gameobjects/GameObject.kt:130`) gates
  `Actor.onUpdate`'s `move()` call on `CoreCapability.MOVE` and `Alive.onUpdate`'s
  `considerAttack()` call on `CoreCapability.ATTACK`. **Suppressing a capability already
  freezes the mechanism without touching any order/target state** — this is exactly Decision
  H's behavior, and it requires zero new code (see §2.4).
- `StandardCommandApplier.applyTo` (`gametools-net/.../command/StandardCommandApplier.kt:90-140`):
  a `when` over the six standard commands; `private inline fun onActor` calls the movement
  action and then, unconditionally on the success path, `(actor as? Alive)?.cancelAttack()`
  — the #39 fix, to be deleted.
- `ClientCommand` (`gametools-net/.../command/ClientCommand.kt`): `MoveTo`, `MoveDir`,
  `Follow`, `Stop`, `Attack`, `StopAttack` — unchanged wire shape; only their KDoc and
  `applyTo` mechanism change.
- Event bus: `GameEvent` sealed interface (`gametools-core/.../event/GameEvent.kt`) —
  `EntitySpawned`, `EntityRemoved`, `AttackIssued`, `AttackLanded`, `DamageDealt`,
  `EntityDied`, `AttackCancelled`, `AttackEnded`. `EventBus.subscribe` returns a `Subscription`
  with `cancel()` (`gametools-core/.../event/EventBus.kt:39,59`).
- Snapshots: `ActorSnapshot` (`id`, `visibleObject`, `speed`, `destination`); `AliveSnapshot`
  wraps it and adds health/faction/owner/combat stats. Neither carries anything order-shaped.
- Existing test coverage that this plan touches: `StandardCommandApplierTest`
  (`gametools-net/src/test/kotlin/com/spartanlabs/gaming/testing/deterministic/networking/command/StandardCommandApplierTest.kt`)
  and `AliveAttackLifecycleTest`
  (`gametools-core/src/test/kotlin/com/spartanlabs/gaming/testing/component/gameobjects/AliveAttackLifecycleTest.kt`).

---

## 2. Design

### 2.1 `Intent` shape

```kotlin
/**
 * A unit's current standing order. Exactly one is active on an [Actor] at a time
 * ([Actor.intent]); issuing a new one via [Actor.issue] always tears down the previous one
 * first via [clear].
 */
abstract class Intent {
    /** Stable wire label for this intent — see [ActorSnapshot.intent]. */
    abstract val label: String

    /** Wires up the mechanism this order drives. Called by [Actor.issue] after the previous intent's [clear]. */
    open fun issue(actor: Actor) {}

    /** Stops the mechanism and undoes any side effect. Called when this intent is replaced or explicitly cleared. */
    open fun clear(actor: Actor) {}
}
```

`issue`/`clear` are **public**, not `protected` (the exploration doc's sketch used
`protected`, which does not compile: `Actor.issue(next: Intent)` calls `next.issue(this)` /
`intent.clear(this)` from *outside* `Intent`'s own hierarchy, and a consumer's own `Intent`
subclass in a different module needs to be constructible and callable from `gametools-net`
and app code the same way `ClientCommand` implementations are). `open` with a no-op default
body (not `abstract`) so `Idle` needs no overrides at all.

Not a Kotlin `enum` (final, cannot be extended by `Alive` or a consumer) and not `sealed`
(does not survive `Alive`'s eventual module move, and intents carry operands — a `Move`'s
destination, an `AttackIntent`'s target — which enum constants cannot hold). This mirrors why
`ClientCommand` is a plain open `interface` rather than a closed hierarchy.

### 2.2 `Actor` gains `world` and `intent`

`Alive.world: World?` is **promoted to `Actor`**. This is required, not optional: publishing
`GameEvent.IntentIssued`/`IntentCleared` (Decision E) from `Actor.issue` needs a bus, and only
`Alive` has one today. Promoting it is additive and non-breaking — `Alive` still exposes
`.world` (inherited), every existing read/write site (`Alive.kt`, `World.add`) keeps compiling
against the same property name and type, just declared one level up.

```kotlin
// Actor.kt
/**
 * The [World] this actor belongs to, or `null` when it is not in one. Promoted here from
 * `Alive` so any [Actor] — not only an [Alive] — can publish [GameEvent.IntentIssued] /
 * [GameEvent.IntentCleared] through [issue]. [World.add] sets it.
 */
var world: World? = null

/** The actor's current standing order. [Idle] until [issue] is called. */
var intent: Intent = Idle
    private set

/**
 * Replaces [intent] with [next]: runs the outgoing intent's [Intent.clear], installs [next],
 * then runs its [Intent.issue] — so orders are mutually exclusive by construction. Publishes
 * [GameEvent.IntentCleared] when [next] is [Idle], otherwise [GameEvent.IntentIssued].
 */
fun issue(next: Intent) {
    val previous = intent
    previous.clear(this)
    intent = next
    next.issue(this)
    log.debug("Actor intent changed from {} to {}", previous.label, next.label)
    world?.events?.publish(
        if (next === Idle) GameEvent.IntentCleared(this, previous)
        else GameEvent.IntentIssued(this, next)
    )
}

/** Returns this actor to [Idle] — the general form of "cancel whatever order is active." */
fun clearIntent() = issue(Idle)
```

`World.add` changes its `if (gameObject is Alive) gameObject.world = this` check to
`if (gameObject is Actor) gameObject.world = this` (§3).

### 2.2.1 `Idle` and `Move` — resolving the `Stop`/Decision J split concretely

The superseded doc leaves one seam unresolved: Decision J says `Stop` maps to `Idle`, *and*
"no order at all," *and* pin-in-place must **not** be grafted onto `Stop`/`Idle` — but today's
`Stop` command halts movement (`Movement.Targeting` + destination pinned to the current
location). Something in the new model must still do that, or `Stop` silently stops being able
to actually stop a `Movement.Directional` or `Movement.Homing` actor (which ignore or
overwrite `destination` on their own).

**Resolution:** the halt is `Move`'s own **`clear()`**, not `Idle`'s **`issue()`**:

```kotlin
/** Standing order: advance under [movement], optionally retargeting [destination] as it is issued. */
data class Move(val movement: Movement, val destination: Point? = null) : Intent() {
    override val label = "move"

    override fun issue(actor: Actor) {
        destination?.let { actor.destination = it }
        actor.movement = movement
    }

    /** Undoes this order's mechanism: returns the actor to a neutral, stopped-in-place state. */
    override fun clear(actor: Actor) {
        actor.movement = Movement.Targeting
        actor.destination = Point(actor.location)
    }
}

/** No standing order. The actor holds whatever [Actor.movement] / [Actor.destination] it last had. */
data object Idle : Intent() {
    override val label = "idle"
}
```

This is `Intent.clear`'s documented contract applied literally — "stop the mechanism [advancing
under a `Movement` strategy] and undo any side effect [being mid-flight toward a stale
destination]" — not a new "Halt" concept. `Idle.issue` stays a true no-op, satisfying Decision
J's letter: nothing about "pin destination" is *added* to `Idle`; it was already the case that
tearing down whatever was previously active is what halts. The practical consequence, called
out here because the superseded doc did not need to be this concrete:

- **`Stop` while the active intent is a `Move`:** identical to today — `Move.clear` halts in
  place, matching the current `Stop` KDoc exactly.
- **`Stop` while the active intent is an `AttackIntent`:** `AttackIntent.clear` only cancels
  the attack (keeps the current destination, per `Alive.cancelAttack`'s existing contract) —
  it does **not** additionally snap the actor to a fresh pinned point. This is a small,
  intentional behavior difference from `5.1.0`'s `Stop` (which always pins regardless of what
  was active). It is the accepted cost of Decision J and is called out again in §8 and as a
  `CHANGELOG.md` note.
- Nothing here reintroduces `HoldPosition` — a deliberate defensive *stance* (attack anything
  that enters range without chasing) is a materially different, richer behavior than "undo
  whatever mechanism was running," and stays a Phase 4 consumer intent (§7).

### 2.3 `AttackIntent` and self-clearing (Decision C)

```kotlin
/** Standing order: attack [target] — closes to range, then swings until cleared. */
class AttackIntent(val target: Alive) : Intent() {
    override val label = "attack"
    private var subscription: EventBus.Subscription? = null

    override fun issue(actor: Actor) {
        val alive = actor as? Alive ?: return
        alive.issueAttack(target)
        // Decision C: self-clear through the event bus, not a direct Alive -> Intent callback.
        subscription = alive.world?.events?.subscribe { event ->
            if (event is GameEvent.AttackEnded && event.attacker === alive) alive.clearIntent()
        }
    }

    override fun clear(actor: Actor) {
        subscription?.cancel()
        subscription = null
        (actor as? Alive)?.cancelAttack()
    }
}
```

`Alive.endAttackIfTargetLost()` needs **no change** — it already publishes
`GameEvent.AttackEnded` on the world bus (`Alive.kt:210`) with no knowledge of `Intent` at
all; `AttackIntent` is purely a subscriber. This is why `core` stays decoupled from the intent
layer's self-clearing: `Alive` never calls back into `Intent`, it just keeps publishing what
it already publishes. Cancelling the subscription in `clear()` is required, not cosmetic — an
`AttackIntent` that is replaced (by a `Move`, or a fresh `AttackIntent` on a new target)
without cancelling its listener would leak a bus subscription per issued attack.

If the actor has no `world` (a worldless unit test, or an actor not yet added to a `World`),
`subscription` stays `null` and the intent simply cannot self-clear on `AttackEnded` — it can
still be cleared explicitly via `issue`/`clearIntent`. This matches the existing worldless
behavior of `cancelAttack`/`onAttackEnded` (hooks fire; the event just has nowhere to publish).

### 2.4 Decision H needs no new code

A suppressed `CoreCapability.ATTACK` or `CoreCapability.MOVE` already freezes the mechanism
(`Actor.onUpdate` / `Alive.onUpdate` gate `move()` / `considerAttack()` on `can(...)`) without
touching `attackTarget`, `movement`, or now `intent` at all. `actor.intent` stays whatever it
was — held, not cleared — for the whole duration of the suppression, which is exactly
Decision H. This plan changes nothing in `Buff`/`Capability`/`GameObject.can`.

### 2.5 `ClientCommand.applyTo` under the intent model

| Command | New `applyTo` body |
|---|---|
| `MoveTo` | `it.issue(Move(Movement.Targeting, destination = Point(x, y)))` |
| `MoveDir` | `mover.angle = angleDegrees; mover.issue(Move(Movement.Directional))` |
| `Follow` | `mover.issue(Move(Movement.Homing(chased)))` |
| `Stop` | `mover.clearIntent()` |
| `Attack` | `aggressor.issue(AttackIntent(victimAlive))` |
| `StopAttack` | `it.clearIntent()` |

The `(actor as? Alive)?.cancelAttack()` line in `onActor` is **deleted**. Issuing a `Move`
tears down whatever was active — if it was an `AttackIntent`, its `clear()` calls
`cancelAttack()`, reproducing #39's fix exactly, generally, with no special case.

One incidental, positive behavior fix: `MoveTo` previously only assigned `destination`,
relying on the actor already being in `Movement.Targeting` (its default) — on a
`Directional`/`Homing` actor it was a silent no-op (the strategy ignores or overwrites
`destination`). `MoveTo` now always forces `Movement.Targeting`, matching its own KDoc ("assigns
`destination`, which the default `Targeting` strategy walks to") for real regardless of prior
mode. Called out in §8 / `CHANGELOG.md`, not hidden as "purely internal."

---

## 3. File-by-file changes

### `gametools-core`

**New — `gameobjects/Intent.kt`**
- `abstract class Intent` (§2.1): `label`, `issue`, `clear`.
- `data object Idle : Intent()`.
- `data class Move(val movement: Movement, val destination: Point? = null) : Intent()` (§2.2.1).
- KDoc on every public declaration (Level-2 KDoc standard); region-grouped imports per
  `.aiassistant/rules/CLAUDE.md` §6.

**Modified — `gameobjects/Actor.kt`**
- Add `var world: World? = null` (promoted from `Alive`).
- Add `var intent: Intent = Idle; private set`, `fun issue(next: Intent)`, `fun clearIntent()`
  (§2.2).
- `ActorSnapshot` gains `val intent: String = Idle.label`, and its `from(actor)` factory sets
  `intent = actor.intent.label` (Decision D — minimal read-only tag; default keeps every
  existing direct `ActorSnapshot(...)` test/call site compiling).
- Import `com.spartanlabs.gaming.event.GameEvent`, `com.spartanlabs.gaming.gameobjects.World`
  is already local to the package (no import needed).
- Class KDoc gains one paragraph pointing at `intent` as the order-tracking concept alongside
  `movement`.

**Modified — `gameobjects/Alive.kt`**
- Remove `var world: World? = null` (now inherited from `Actor`) — delete lines 76–77; no
  other change to any attack method. `issueAttack`/`cancelAttack`/`considerAttack`/
  `endAttackIfTargetLost` are untouched (Strategy B: intent orchestrates, does not own).
- New — `class AttackIntent(val target: Alive) : Intent()` (§2.3), appended in its own
  `//region INTENT` block near `//region COMBAT`, so it moves as one unit with `Alive` if/when
  the framework's Phase 2 combat reshape relocates `Alive` to `gametools-combat` (not this
  plan's concern — see the header note on "Phase 2"/"Phase 4").
- Imports: add `com.spartanlabs.gaming.event.EventBus` (for `EventBus.Subscription`).

**Modified — `event/GameEvent.kt`**
- Add:
  ```kotlin
  /** An [Actor] was given a new standing order via [Actor.issue]. */
  data class IntentIssued(val actor: Actor, val intent: Intent) : GameEvent

  /** An [Actor]'s standing order was cleared back to [Idle] via [Actor.issue] / [Actor.clearIntent]. */
  data class IntentCleared(val actor: Actor, val previous: Intent) : GameEvent
  ```
- Imports: add `com.spartanlabs.gaming.gameobjects.Actor`, `com.spartanlabs.gaming.gameobjects.Intent`
  to the existing `1.2 Spartan Gaming` import group (alphabetical, alongside `Alive`,
  `GameObject`, `World`).

**Modified — `gameobjects/World.kt`**
- `fun add(gameObject: GameObject)`: change `if (gameObject is Alive) gameObject.world = this`
  to `if (gameObject is Actor) gameObject.world = this`. KDoc on `add` updated to say "for an
  `Actor`" instead of "for an `Alive`."

### `gametools-net`

**Modified — `networking/command/StandardCommandApplier.kt`**
- Rewrite the `when` per §2.5's table.
- Delete `(actor as? Alive)?.cancelAttack()` from `private inline fun onActor` — it goes back
  to being a plain "resolve, run the action, report `Applied`" helper, same shape as `onAlive`.
- Delete the "A movement order calls off a pending attack" KDoc section on `applyTo`; replace
  with a short paragraph: issuing any standing order clears the previous one, so a movement
  order clearing a pending attack is a consequence of `Intent`, not a special case (link to
  `Actor.issue` / `AttackIntent.clear`).
- Import `com.spartanlabs.gaming.gameobjects.AttackIntent`, `com.spartanlabs.gaming.gameobjects.Move`.

**Modified — `networking/command/ClientCommand.kt`**
- KDoc on `MoveTo`/`MoveDir`/`Follow`/`Stop`/`Attack`/`StopAttack`: replace "assigns
  `Actor.destination`" / "calls `Alive.cancelAttack`" language with "issues a `Move` /
  `AttackIntent`" language, keeping the observable-behavior description (still true) but
  pointing at the new mechanism.
- No change to any `@Serializable` shape, `@SerialName`, or field — wire-compatible.

### Docs

- `README.md`: the "Typed command protocol" paragraph (currently describing `applyTo` calling
  off attacks as a special case) updated to describe intent-based routing; one new sentence
  under the game-object bullet introducing `Actor.intent` as the standing-order concept
  alongside `movement`.
- `CHANGELOG.md`: new `[Unreleased] / Changed` entries — see §6.
- `docs/framework-vision-and-roadmap.md`: no content change required by this plan (the intent
  layer is not one of its listed phase items); optionally add one clarifying sentence to §3
  Phase 4 noting `AttackMove`/`Patrol`/`Hold` build on `Intent` once this ships — left to the
  executor's judgment, not required for this issue to close.

---

## 4. Documentation impact (Audience-Reach rings)

| Ring | What moves |
|---|---|
| **Inner Core** | `//region INTENT` in `Alive.kt`; no new `TODO`/`FIXME` expected. |
| **Component / KDoc** | Full KDoc on `Intent`, `Idle`, `Move`, `AttackIntent`, `Actor.intent`/`issue`/`clearIntent`, `GameEvent.IntentIssued`/`IntentCleared`, `ActorSnapshot.intent`. Updated KDoc on `ClientCommand`'s six commands and `StandardCommandApplier.applyTo`. |
| **Boundary / Protocol** | `ActorSnapshot.intent` is a new wire field (JSON codec) — document in the `README.md` protocol paragraph and in `CHANGELOG.md` as additive (old clients decoding a payload that includes it simply have an extra string field if they use a lenient/permissive decoder; kotlinx.serialization's default behavior for unknown or new fields on the *sending* side needs no client change since this is server→client only). |
| **Architectural** | No `docs/framework-vision-and-roadmap.md` diagram change is required; the module dependency graph is unaffected (no new module). |

`README.md` update is **required** by the global README-currency rule: this changes what
`ClientCommand.applyTo` does (an external behavior) and adds a wire field.

---

## 5. Test plan (5-level hierarchy)

All new/changed tests use `com.spartanlabs.gaming.testing.<level>`, mirroring the production
package, one class per file, per `.aiassistant/rules/CLAUDE.md` §4.

### Level 1 — local gating (no persisted files)
Run `./gradlew componentTest deterministicTest` before every push (existing repo convention;
this repo has no committed `testing.gating` folder — see `CONTRIBUTING.md`).

### Level 2 — component (`gametools-core/src/test/kotlin/com/spartanlabs/gaming/testing/component/gameobjects/`)

**New — `IntentTest.kt`**
- `Actor.issue` runs the previous intent's `clear` before installing and `issue`-ing the next
  (assert ordering via a tracking fake `Intent`).
- `clearIntent()` returns `intent` to `Idle` and publishes `GameEvent.IntentCleared` with the
  previous intent.
- Issuing a non-`Idle` intent publishes `GameEvent.IntentIssued` carrying that intent.
- Issuing the same intent *type* twice still runs `clear` then `issue` both times (no
  short-circuit on equal values).
- A freshly-constructed `Actor`'s `intent` is `Idle` and its `ActorSnapshot.intent` is
  `"idle"`.

**New — `MoveIntentTest.kt`**
- `Move(Movement.Targeting, destination).issue(actor)` sets both `movement` and `destination`.
- `Move(Movement.Directional).issue(actor)` (no destination) leaves `destination` untouched.
- `Move.clear(actor)` resets `movement` to `Targeting` and `destination` to the actor's current
  `location` — covers §2.2.1's halt behavior directly, independent of `Stop`.

**Modified — `AliveAttackLifecycleTest.kt`**
- Add: issuing `AttackIntent` on an `Alive` in a `World` and then removing the target from the
  world (or reducing it to death) causes the actor's `intent` to return to `Idle` once
  `GameEvent.AttackEnded` is delivered — the concrete Decision-C behavior. Assert via
  `actor.intent === Idle` after the tick that fires `AttackEnded`.
- Add: `AttackIntent.clear` cancels its event subscription — issue `AttackIntent(a)`, replace
  it with `Move(...)`, then drive `a`'s (former) target through the death path and assert
  `cancelledHookCalls`/`intent` on the *original* actor is unaffected (no double-clear, no
  stale-listener side effect).

### Level 3 — integration (`gametools-core/src/test/kotlin/com/spartanlabs/gaming/testing/integration/gameobjects/` — **new package**, this plan's first user)

**New — `IntentSelfClearIntegrationTest.kt`**
- A real `World` (no sockets — this is a same-module integration point between `Intent`,
  `Alive`, and `EventBus`, not a network integration; matches this repo's existing use of
  Level 3 for "boundary conditions and data exchange with actual... interfaces" applied to the
  event bus as the interface under test): an attacker's `AttackIntent` target dies mid-tick;
  assert the attacker's `intent` is `Idle` by the next tick with no manual intervention, and
  that exactly one `IntentCleared` event was published (not one per tick it stays cleared).

### Level 4a — deterministic (`gametools-net/src/test/kotlin/com/spartanlabs/gaming/testing/deterministic/networking/command/`)

**Modified — `StandardCommandApplierTest.kt`**
- Re-express the existing "calls off a pending attack" tests (`Stop`, `MoveTo`, `MoveDir`,
  `Follow`) against `actor.intent`: after each, assert `intent is Move` (or `is Idle` for
  `Stop`) and that a `GameEvent.AttackCancelled` still fires (via `AttackIntent.clear` ->
  `cancelAttack`) when one was pending.
- New: `Stop` on an actor whose intent is `Move` leaves it at rest at its current location
  (via `Move.clear`) — the direct assertion for §2.2.1's resolution.
- New: `Stop` on an `Alive` whose intent is `AttackIntent` clears the attack but does **not**
  reset `movement`/`destination` — the explicit regression-guard for the behavior difference
  called out in §2.2.1/§8.
- New: `Attack` sets `intent` to an `AttackIntent` naming the resolved target.
- New: `MoveTo` on an actor previously in `Movement.Directional` now actually moves it (the
  incidental fix in §2.5), replacing any assumption that `MoveTo` only ever touches
  `destination`.

### Level 4b — e2e (`gametools-net/src/test/kotlin/com/spartanlabs/gaming/testing/e2e/`)

**Modified — `ClientServerRoundTripTest.kt`**
- Extend the existing round trip: after a `MoveTo` COMMAND applies and the world ticks, the
  next `STATE` broadcast's decoded `ActorSnapshot.intent` is `"move"`; after `Stop`, it is
  `"idle"`.

### Level 4c — nonfunctional (`gametools-core/src/test/kotlin/com/spartanlabs/gaming/testing/nonfunctional/`)

**Modified — `WorldTickThroughputTest.kt`** (extend its existing large-world scenarios, do not
duplicate the harness): issuing and clearing an intent (a `Move` reassignment) on every one of
its 10k drifting actors, every tick, stays within the same time budget the file already
asserts — the medium-scale target documented in `docs/framework-vision-and-roadmap.md` §1,
decision 12 ("Scale target": ≤200 players, ≤10k entities, 10–20 Hz).

### Level 5 — UAT
Not automatable: whether an `AttackMove`/`Patrol`/`Hold` consumer intent (Phase 4, not built
here) will compose cleanly against this `Intent` base is a design-review judgment call for
whoever builds Phase 4, not something this PR's tests can exercise.

---

## 6. `CHANGELOG.md` entries (draft for `[Unreleased]`)

```md
### Added
- `Actor.intent` — a unit's current standing order (`Idle` by default), with `Actor.issue(Intent)`
  and `Actor.clearIntent()`. Issuing a new intent always tears down the previous one first, so
  orders are mutually exclusive by construction. GameTools ships `Idle`, `Move`, and (on
  `Alive`) `AttackIntent`; a consumer adds its own the same way it adds a `ClientCommand`.
  `GameEvent.IntentIssued` / `IntentCleared` report every change on the world bus.
  `ActorSnapshot` gains a read-only `intent` string tag (`"idle"` / `"move"` / `"attack"`).
  (#42)

### Changed
- The six standard commands now apply by issuing the corresponding `Intent` rather than
  poking `Actor`/`Alive` mechanisms directly. Observable behavior is the same for `MoveDir`,
  `Follow`, `Attack`, and `StopAttack`. Two small, deliberate differences:
  - `MoveTo` now always switches the actor to `Movement.Targeting`; previously it only
    assigned `destination`, which was silently ignored on an actor in `Directional` or
    `Homing` movement.
  - `Stop` on a unit that is currently attacking now only cancels the attack (same as
    `StopAttack`) and no longer additionally pins the actor to a freshly-snapped destination;
    `Stop` on a unit that is moving is unchanged (still halts in place). A future `HoldPosition`
    intent will offer the stronger "stay here and defend" stance this does not replace. (#42)
- `Alive.world` is now declared on `Actor` (inherited by `Alive`, source-compatible) so any
  `Actor` can publish through the world event bus, not only an `Alive`. (#42)

### Fixed
- Applying a movement command through `ClientCommand.applyTo` no longer needs a hard-coded
  `cancelAttack()` call (the interim fix from #39) — it now falls out generally from issuing
  any intent clearing the previous one. (#42)
```

---

## 7. Explicitly out of scope — where Phase 4 attaches later

Not designed or implemented by this plan; each is a **source-compatible** follow-on once
`Intent` ships:

- **`AttackMove`** — a consumer (or later core) intent combining `Move`'s destination-seeking
  with in-range auto-engagement; attaches as another `Intent` subclass, issued the same way
  `Attack`/`MoveTo` are.
- **`Patrol`** — a multi-waypoint standing order; likely needs its own per-tick state (current
  leg, direction) inside the intent instance, same shape as `AttackIntent` carrying `target`.
- **`Hold` / `HoldPosition`** — the defensive "don't chase, but fight what comes into range"
  stance flagged in §2.2.1 as distinct from `Stop`/`Idle`. Needs its own `issue`/`clear`
  wired to whatever aggro/threat mechanism exists when it is built (Phase 4, after
  `framework-vision-and-roadmap.md`'s `gametools-ai` module or the threat table from Phase 2
  rich combat — whichever lands first).

None of these require a change to `Intent`, `Actor.issue`, or the event pair shipped here;
they are new subclasses plus new `ClientCommand`s, exactly the extension path `AttackIntent`
itself takes.

---

## 8. Risks & edge cases

| Risk | Mitigation / note |
|---|---|
| `Stop` on an attacking unit no longer pins a fresh destination (§2.2.1) | Intentional per Decision J; called out in `CHANGELOG.md`; covered by an explicit regression test (§5, L4a). |
| `MoveTo` now forces `Movement.Targeting` where it previously silently no-op'd on `Directional`/`Homing` | Behavior fix, not a bug; called out in `CHANGELOG.md`; covered by an explicit test (§5, L4a). |
| Promoting `world` from `Alive` to `Actor` widens who gets a world back-reference | Purely additive (a plain `Actor` gaining a property it didn't have does not remove capability from `Alive`); `World.add`'s type check change is the only other touch point — covered by existing `World`/`Alive` tests plus a new assertion that a plain `Actor` added via `World.add` has `world` set. |
| `AttackIntent`'s event-bus subscription leaking if `clear()` is skipped | `clear()` always cancels it; `Actor.issue` always calls `clear()` on the outgoing intent before installing the next one — there is no path that replaces `intent` without going through `issue`. |
| Third "source of truth" (`intent` vs `movement` vs `Alive`'s private attack state) persists in v1 | Documented as a known, accepted limitation of Strategy B (carried from the superseded doc); Strategy A collapses it at the framework's Phase 2 combat reshape, out of scope here. |
| Wire compatibility | `ActorSnapshot.intent` is a new field with a default; existing JSON encode/decode round-trips are unaffected for any payload that does not need to read it. No breaking change to `@SerialName`s or existing fields. |
| Cross-repo impact | See below. |

### Cross-repo impact

- **MyGameServer** (consumer, `SpartanLabsGaming/MyGameServer`): can drop any local
  `cancelAttack()` wrapper it added around move commands once this ships (already possible
  independently since #41/`5.1.0`). Gains the ability to read `actor.intent` /
  `ActorSnapshot.intent` if it wants to. No issue filed against it — standing guidance is to
  never file issues against downstream consumers of this project.
- **The separate client project**: benefits from `ActorSnapshot.intent` for unit-state
  rendering/animation without inferring it from movement deltas; no action required — it is
  additive JSON.
- **GameGraphics**: client-side rendering only, does not call `applyTo` or decode
  `ActorSnapshot` server-side logic — unaffected.
- **WebTools**: no transport-layer involvement — unaffected.

---

## 9. Version control

- **Branch:** `feature/42-actor-intent`, off `master`.
- **Release shape:** Strategy B is additive on `Actor`/`Alive` (new `intent`/`issue`/
  `clearIntent`/`world`-promotion; no existing public signature removed or narrowed) and
  `ActorSnapshot` gains a defaulted field. Per `CONTRIBUTING.md`'s versioning table this is a
  **Feature release** (`feat:` → e.g. `5.1.0` → `5.2.0`), not Major — nothing here breaks
  `4.x`/`5.x` consumer source or wire compatibility.
- **Suggested commit sequence** (squash further only if the whole PR is small enough to
  review as one; keep as separate commits if reviewed incrementally):
  1. `feat(gameobjects): add Intent, Idle, and Move` — new `Intent.kt`; `Actor.world`/`intent`/
     `issue`/`clearIntent`; `World.add`'s `is Actor` change; `GameEvent.IntentIssued`/
     `IntentCleared`. Includes this plan doc (`docs/issue-42-actor-intent-plan.md`) in the same
     commit, per the plan/implementation-binding convention.
  2. `feat(gameobjects): add AttackIntent and self-clear it on AttackEnded` — `Alive.kt`'s
     `AttackIntent`, removal of `Alive.world`.
  3. `feat(networking): route standard commands through Intent` — `StandardCommandApplier.kt`,
     `ClientCommand.kt` KDoc; deletes the #39 special case.
  4. `feat(gameobjects): add a minimal intent tag to ActorSnapshot` — `ActorSnapshot.intent`.
  5. `test: cover Intent, Move, AttackIntent, and the updated StandardCommandApplier` — all
     new/modified test files from §5.
  6. `docs: update README and CHANGELOG for the intent layer` — if not folded into commit 1.
  - Each commit's body references `Refs #42`; the PR itself closes it (`Closes #42`).
- Conventional Commits, PR-per-change, CI green, semi-linear merge (`--no-ff` up to `master`),
  rebase-only locally — all per `CONTRIBUTING.md`, unchanged by this plan.
- This is not a `release/*` branch — the version bump to the next Feature release happens
  separately, at the next scheduled release, per `CONTRIBUTING.md`'s Releasing steps.

---

## 10. Open decisions

None. All ten (A–J) are closed in the superseded document and carried into this plan per §0.
The one item this plan resolves *concretely* that the superseded doc left as prose (the
`Move.clear()` vs `Idle.issue()` split under Decision J, §2.2.1) is a design call, not a
decision left for a human — flagged here for visibility, not for approval, since it follows
directly from Decision J's own wording ("do not graft pin-destination onto `Stop`/`Idle`") and
`Intent.clear`'s documented contract.

---

## 11. Sequencing & follow-ups

1. Land this plan's six commits (§9) as one PR against `master`.
2. Update `docs/framework-vision-and-roadmap.md` §3 Phase 4 with one sentence noting
   `AttackMove`/`Patrol`/`Hold` build on `Intent` — small enough to fold into this PR's docs
   commit, or its own trivial `docs:` follow-up.
3. Next scheduled Feature release picks this up along with anything else merged since
   `5.1.0`; no dedicated release is required by this issue alone.
4. Deliberately deferred, not part of this or any near-term follow-up until their own plan
   docs exist: `AttackMove` / `Patrol` / `Hold` (§7, Phase 4); Strategy A's mechanism-owning
   reshape (Phase 2 rich combat, a Major release, moves `Alive` to `gametools-combat`); full
   `@Serializable` open `Intent` hierarchy on the wire (deferred to the Phase 3 networking
   rework per Decision D).
