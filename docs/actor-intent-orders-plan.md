# Plan: `Actor` intent / unit-order layer

## Header / Association

- **Covers:** [SpartanLabsGaming/MyGameTools#42](https://github.com/SpartanLabsGaming/MyGameTools/issues/42)
  — *"Model a unit's standing order as a first-class Intent on Actor"*. The idea was raised by
  Spartak Singh via a Claude Code session on 2026-09-07, immediately after the interim fix for
  [#39](https://github.com/SpartanLabsGaming/MyGameTools/issues/39): *"Add an Intent open enum
  to Actor, default values NONE and MOVE. Alive will add ATTACK. Each intent will have an
  `issue()` and a `clear()`. When a new intent is issued the previous will clear first."*
- **Status:** **design exploration — not scheduled.** No source, test, or build file is
  modified by this document. It exists to capture the concept, correct the Kotlin shape, and
  enumerate the decisions that must be closed before it can be planned for real (per the
  roadmap rule that a change of this size gets its own approved plan doc).
- **Baseline:** GameTools `5.0.0` (heading to `5.1.0` with the #39 fix in PR #41), three
  modules (`gametools-core`, `gametools-net`, `gametools`). `Actor` and `Alive` both live in
  `gametools-core` today; the roadmap moves `Alive` to a new `gametools-combat` in Phase 2.
- **Relationship to #39:** #39's fix hard-wires one interaction — a movement command applied
  through `ClientCommand.applyTo` calls `Alive.cancelAttack()`. This document is the
  general form of that: a single "what is this unit ordered to do" concept from which that
  interaction, and every future one, falls out. The #39 fix is designed to be cleanly
  superseded (see §6).
- **Related docs:** `docs/framework-vision-and-roadmap.md` (§3 phases — Phase 2 rich combat,
  Phase 4 AI; Open Decision G teams); `docs/client-command-protocol-plan.md` (the
  open-hierarchy + consumer-registration pattern this mirrors); `docs/phase-0-foundations-plan.md`
  (the `AttackState` machine and event bus this builds on).

---

## 1. Context

### 1.1 The concept

Model the unit's **current standing order** as a first-class thing on `Actor`:

- `Actor` has one active `Intent` at a time (`Idle` by default) plus a `Move` intent.
- `Alive` contributes an `AttackIntent`.
- Each `Intent` has `issue()` (wire up the mechanism it represents) and `clear()` (tear it
  down — stop the mechanism, undo side effects).
- Issuing a new intent runs the previous intent's `clear()` first, so orders are mutually
  exclusive by construction.
- Consumers add their own (`Build`, `Harvest`, `Patrol`, `HoldPosition`, `AttackMove`,
  `CastAbility`, …) the same way they add `ClientCommand`s today.

`ClientCommand.applyTo` then becomes "issue the corresponding intent" rather than "poke the
one mechanism the command names", and the pairwise "does command X interrupt mechanism Y"
matrix collapses to "issuing any intent clears the last one".

### 1.2 Why this is worth doing

- **RTS target.** `docs/framework-vision-and-roadmap.md` §1 decision 8 commits to RTS / MOBA
  / tower-defense. Every RTS engine has a unit-order concept; "move cancels attack",
  "attack-move", "patrol", "hold position", order queueing and the command card all sit on
  it. #39 is the first of many order-interaction bugs the framework will otherwise field
  one at a time.
- **One source of truth.** Today "what is this unit doing" is spread across
  `Actor.movement` (a `Movement` strategy) and `Alive`'s private `AttackState` +
  `attackTarget` + `attackProgress`, with no object that says "the player ordered an
  attack". Adding abilities (Phase 6) adds a third. An intent layer is where they compose.
- **Consumer ergonomics.** MyGameServer#6 currently keeps a two-line `cancelAttack()`
  wrapper around move commands as local policy. Under an intent model that policy is the
  library's, and it is one rule rather than one-per-command.
- **Client rendering.** A serialized intent (`"moving"` / `"attacking"` / `"building"`)
  gives the client project a clean signal for unit state and animation without inferring it
  from movement deltas. (Whether to serialize it is Open Decision D.)

### 1.3 Current-state facts (verified against `master` at PR #41)

- `Actor` (`gametools-core/.../gameobjects/Actor.kt`): `var movement: Movement` (Targeting /
  Persistent / Directional / Homing), `var destination: Point` (setter re-aims `angle`),
  `internal var hasSettled`. No order/intent concept.
- `Alive` (`.../gameobjects/Alive.kt`): private `enum AttackState { NONE, ISSUED, INPROGRESS }`,
  private `attackTarget: Alive?`, `attackProgress: Double`. `fun issueAttack(target)`,
  `fun cancelAttack()` (public, no-op when `NONE`, fires `onAttackCancelled` +
  `GameEvent.AttackCancelled`). `considerAttack()` runs each tick from `onUpdate()` and,
  crucially, **sets `destination = attackTarget.location` while closing to range** — the
  attack mechanism already drives movement. `endAttackIfTargetLost()` clears the attack from
  inside `core` when the target dies or leaves the world, firing `GameEvent.AttackEnded`.
- Attack capability can be suppressed by a `Buff`; the current contract is "order kept, swing
  progress frozen until the suppression lifts".
- `ClientCommand.applyTo` (`gametools-net/.../command/StandardCommandApplier.kt`): a `when`
  over the six standard commands; `onActor` now also calls `(actor as? Alive)?.cancelAttack()`
  on the success path (the #39 fix).
- Event bus: `GameEvent` sealed hierarchy in `gametools-core/.../event/GameEvent.kt`
  (`AttackIssued`, `AttackCancelled`, `AttackEnded`, `AttackLanded`, `DamageDealt`,
  `EntityDied`, …). `World.events.publish(...)`.
- Snapshots: `ActorSnapshot` (speed, destination), `AliveSnapshot` (health, faction, owner,
  combat stats). Neither carries anything order-shaped.

---

## 2. Design

### 2.1 Correcting the Kotlin shape — not an "open enum"

A Kotlin `enum` cannot be what the idea needs:

1. **Enums are final.** `Alive` cannot add an `ATTACK` constant to an `Intent` enum declared
   on `Actor`. There is no "open enum".
2. **A `sealed` hierarchy does not survive the module split.** `sealed` permits subtypes only
   in the same module. Phase 2 moves `Alive` into `gametools-combat` while `Actor` (and so
   `Intent`) stays in `gametools-core`, and consumers declare their own intents from a third
   module. A sealed `Intent` blocks both.
3. **Intents carry operands.** `Move` needs a destination (and which of the three movement
   modes); `AttackIntent` needs the target `Alive`. Enum constants are singletons and hold no
   per-actor state.

The shape that works — and matches how `ClientCommand` was deliberately left **open** for the
same reasons:

```kotlin
/** A unit's current standing order. Exactly one is active on an [Actor] at a time. */
abstract class Intent {
    /** Wire up the mechanism this order drives. Called by [Actor.issue] after the previous intent's [clear]. */
    protected abstract fun issue(actor: Actor)
    /** Stop the mechanism and undo any side effect. Called when this intent is replaced or explicitly cleared. */
    protected abstract fun clear(actor: Actor)
}
```

Library-provided: `Idle`, `Move` (in `core`); `AttackIntent` (in `core` today, moving to
`combat` in Phase 2). Consumer intents implement the same base.

`Actor` gains:

```kotlin
var intent: Intent = Idle
    private set

fun issue(next: Intent) { intent.clear(this); intent = next; next.issue(this) /* + event */ }
fun clearIntent() = issue(Idle)
```

### 2.2 Two implementation strategies

| | **A — intent owns the mechanism** | **B — intent orchestrates existing mechanisms** |
|---|---|---|
| `Move.issue` | sets `actor.movement` + `destination` | same |
| `AttackIntent.issue` | calls `alive.issueAttack(target)` | same |
| `AttackIntent.clear` | calls `alive.cancelAttack()` | same |
| `Actor.movement` / `Alive` attack cycle | become internal, driven only through an intent | stay public, intent is an extra coordinating layer |
| `considerAttack()` driving `destination` | `AttackIntent` owns that movement | unchanged |
| Migration cost | high — reshapes `Actor`/`Alive` public surface | low — additive |
| End state clarity | one knob per unit | three knobs that must agree |

**Recommendation: ship B first, converge on A at the Phase 2 combat reshape** (which is a
Major release and already reshapes `Alive`, so the breaking `Actor`/`Alive` surface change is
free there).

### 2.3 `ClientCommand.applyTo` under the intent model

| Command | Becomes |
|---|---|
| `MoveTo` / `MoveDir` / `Follow` | `actor.issue(Move(...))` |
| `Stop` | `actor.issue(Idle)` (or a dedicated `Halt` intent that also pins `destination`) |
| `Attack` | `alive.issue(AttackIntent(target))` |
| `StopAttack` | `alive.clearIntent()` |

The #39 special-case in `onActor` is deleted: issuing `Move` runs `AttackIntent.clear()`,
which calls `cancelAttack()`. The behavior is identical; the rule is now general.

### 2.4 Self-clearing when a mechanism ends on its own

`AttackIntent` must return the unit to `Idle` when `endAttackIfTargetLost()` fires inside
`core`. Options: (a) `Alive` calls back into `intent` from that path — tight coupling from
`core` internals; (b) the intent subscribes to `GameEvent.AttackEnded` on the world bus and
clears itself — loose, consistent with "the event bus is the spine" (roadmap §2.2). Lean (b),
but it needs the intent to have a bus handle, which `Idle`/`Move` do not need — Open
Decision C.

---

## 3. File-by-file (indicative only — shape not settled)

`gametools-core`:
- **new** `gameobjects/Intent.kt` — `Intent` base, `Idle`, `Move`.
- **modified** `gameobjects/Actor.kt` — `intent` property, `issue` / `clearIntent`.
- **modified** `gameobjects/Alive.kt` — `AttackIntent` (until Phase 2 moves it); `issueAttack`
  / `cancelAttack` stay but become the mechanism `AttackIntent` drives.
- **modified** `event/GameEvent.kt` — `IntentIssued` / `IntentCleared` (Open Decision E).
- **modified** `gameobjects/ActorSnapshot` / `AliveSnapshot` — only if Open Decision D says
  serialize.

`gametools-net`:
- **modified** `networking/command/StandardCommandApplier.kt` — route the six standard
  commands through `issue`; delete the #39 `cancelAttack()` line from `onActor`.
- **modified** `networking/command/ClientCommand.kt` — KDoc: each command "issues the …
  intent".

Docs: `README.md`, `CHANGELOG.md`, `docs/framework-vision-and-roadmap.md` (add the intent
layer to the Phase 2 / Phase 4 scope and §2 architecture).

---

## 4. Test plan (5-level hierarchy)

- **L2 component** (`testing.component.gameobjects`): `issue` runs the previous intent's
  `clear` then the new intent's `issue`, in that order; `clearIntent` returns to `Idle`;
  issuing the same intent type twice still clears-then-issues; `AttackIntent.clear` calls
  `cancelAttack`. Mock the world bus.
- **L3 integration**: `AttackIntent` self-clears on `GameEvent.AttackEnded` when the target
  dies mid-tick (real `World`, no sockets).
- **L4a deterministic** (`testing.deterministic.networking.command`): the existing
  `StandardCommandApplierTest` cases, re-expressed against `actor.intent` — `MoveTo` leaves
  `intent == Move`, and an `Alive` mid-`AttackIntent` transitions to `Move` with an
  `AttackCancelled` event. The #39 tests become intent-transition assertions.
- **L4b e2e**: extend `ClientServerRoundTripTest` — a `COMMAND` that issues an intent, the
  next `STATE` broadcast reflects it (needs Open Decision D).
- **L4c nonfunctional**: issuing/clearing intents every tick for 10k actors stays within the
  medium-scale tick budget.

---

## 5. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Third "source of truth" (`intent` vs `movement` vs `AttackState`) drifts | Strategy A collapses them; until then, `intent` is authoritative and `movement`/attack cycle are documented as "set via an intent" |
| Scope creep into order **queues** (shift-click) | Explicitly out of scope for v1 — single slot; a `Deque<Intent>` is a later, source-compatible addition |
| `core` ↔ intent coupling for self-clearing | Route it through the event bus, not a direct call |
| Serializing `intent` is a wire-protocol change | Defer to Phase 3 unless Open Decision D pulls a minimal read-only field forward |
| Churn on the just-shipped #39 fix | The #39 fix is a single line in `onActor`; deleting it when intents land is trivial and behavior-preserving |
| Naming (`Intent` overloads the `//region 2. Intended Function` convention; "Order" is the RTS term) | Open Decision F |

---

## 6. Interaction with the shipped #39 fix

PR #41 adds `(actor as? Alive)?.cancelAttack()` to `onActor` in `StandardCommandApplier.kt`,
on the success path only. When the intent layer lands:

1. `onActor` stops calling `cancelAttack` directly.
2. The six standard commands call `issue(...)` / `clearIntent()`.
3. `Move`/`Idle` replacing an `AttackIntent` runs `AttackIntent.clear()` → `cancelAttack()`.

Observable behavior is unchanged, so no consumer re-migration and no CHANGELOG "Changed"
entry beyond "internal: movement commands now route through `Actor.intent`".

---

## 7. Breaking-change & cross-repo analysis

- **Strategy B**: additive on `Actor`/`Alive` (new `intent` property + methods); `applyTo`
  behavior-preserving. Ships as a **Feature** release.
- **Strategy A**: `movement` / attack-cycle surface becomes internal → **Major**, batched into
  the Phase 2 combat reshape.
- **MyGameServer** (consumer): can drop its move-command `cancelAttack()` wrapper once #41
  ships (independent of this); gains `command → intent` mapping if it wants to read unit
  orders. No issue is filed against it (standing guidance — downstream consumer).
- **Client project**: benefits only if `intent` is serialized (Open Decision D); otherwise
  unaffected.
- **GameGraphics**: client-side, no `applyTo` — unaffected.

---

## 8. Version-control approach

Not schedulable until the open decisions below are closed and it is slotted into the roadmap.
When it is: one `feature/<issue#>-actor-intent` branch for Strategy B (Feature release), or
folded into the Phase 2 branch series for Strategy A. Conventional Commits, PR-per-change,
semi-linear merge, per `CONTRIBUTING.md`.

---

## 9. Open decisions

| ID | Decision | Notes / lean |
|----|----------|--------------|
| A | **Strategy A (intent owns mechanism) vs B (intent orchestrates).** | Lean B now, A at Phase 2. |
| B | **`Move` vs `Movement`.** Is `Move` intent redundant with the `Movement` strategy? Does `Move` own a `Movement`, or is `Movement` still the public knob? | Lean: `Move` owns the `Movement` mode + destination; `Movement` becomes an internal detail under Strategy A. |
| C | **Self-clearing transport.** Direct `Alive → intent` callback vs event-bus subscription for "attack ended, return to Idle". | Lean event bus (roadmap §2.2). Needs intents to optionally hold a bus handle. |
| D | **Serialize `intent`?** Read-only tag on `ActorSnapshot`/`AliveSnapshot` now, full open `@Serializable` hierarchy in Phase 3, or not at all. | Lean: minimal read-only string tag now if cheap; full treatment in Phase 3. |
| E | **Event bus.** `GameEvent.IntentIssued` / `IntentCleared`? | Lean yes — parity with the rest of the framework. |
| F | **Name.** `Intent` vs `Order` vs `Directive`. | `Order` is the RTS term and avoids the "Intended Function" region-comment overload; `Intent` reads fine in code. Undecided. |
| G | **Order queue.** Single slot v1, `Deque<Intent>` later — confirm v1 is single-slot. | Lean single slot; queue is a source-compatible follow-on. |
| H | **Capability suppression.** A suppressed intent (e.g. attack capability off via `Buff`) — held or cleared? | Lean held + frozen, matching today's attack behavior. |
| I | **Roadmap slot.** Phase 2 (combat reshape), Phase 4 (AI/orders), or a dedicated mini-slice between them. | Lean: core `Intent` + `AttackIntent` into Phase 2; `AttackMove` / `Patrol` / `HoldPosition` into Phase 4. |
| J | **`Stop` semantics.** Does `Stop` map to `Idle`, or to a distinct `Halt` intent that pins `destination`? | Lean `Halt` — `Idle` should mean "no order", `Stop` means "stop *here*". |

---

## 10. Constants

- Five-level test tree per module (`com.spartanlabs.gaming.testing.<level>`).
- `README.md` and any module README updated with the phase that ships this.
- `.aiassistant/rules/CLAUDE.md`: KDoc on every public declaration, `Result` for expected
  failures, structured slf4j logging, region-grouped imports, one test class per file.
- This document is superseded by its eventual approved implementation plan.
