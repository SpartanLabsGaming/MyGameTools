# Plan: `IntentSource` — a pluggable strategy for what an `Alive` should be doing

## Header / Association

- **Covers:** a feature request from Spartak Singh, made directly in a Claude Code planning
  session on 2026-09-17 (a GitHub issue is being filed by the coordinator against
  `SpartanLabsGaming/MyGameTools` immediately after this revision — see §9 Decision 1; the
  issue number is not yet known). Restated: *"Add an `IntentSource` abstraction that
  generalizes the three ways a unit's behavior gets (or should get) driven — single-player
  input, network commands, and NPC AI — into one pluggable per-`Alive` strategy:
  `IntentSource` with a single `fun decide(alive: Alive): Intent?`, polled once per tick and
  funnelled through `Alive.issue`. `Alive` becomes an `abstract class` with an `intentSource`
  property that is never null and must be initialized by every concrete subclass. Rework the
  network command path (`StandardCommandApplier`/`ClientCommand`) to be the network
  `IntentSource` implementation, and add a `Monster` proof-of-concept with a simple wandering
  AI. The wandering archetype the roadmap calls `Creep` is renamed — `Creep` is reserved for a
  future MOBA move+attack-goal archetype."*
- **Branch:** `feature/<issue#>-intent-source` — branch once the coordinator's issue exists;
  the number above is a placeholder, not yet assigned.
- **Commit:** TBD
- **PR:** TBD — this plan document is committed together with the first commit of Stage 1 (see
  §10), so `git log --follow` binds the two.
- **What this plans:** a new, **non-generic** `IntentSource` abstraction in `gametools-core`
  (§2.1, §3) — `fun interface IntentSource { fun decide(alive: Alive): Intent? }` — living
  entirely on `Alive`, not `Actor`; `Alive` becoming `abstract class` with a required
  `intentSource` property (§2.2, §5 — the plan's largest single cost); moving `Actor.intent` /
  `Actor.issue` / `Actor.clearIntent` / `Actor.world` **down** to `Alive` (§2.2 — this revises
  the already-landed-but-unreleased #42 work, not just this plan's new additions; `Actor` itself
  needs **no** abstract-class change and reverts to its pre-#42 shape); narrowing `Intent.issue`/
  `clear` and `GameEvent.IntentIssued`/`IntentCleared` from `Actor`-typed to `Alive`-typed
  (§2.3); a rework of `gametools-net`'s standard command path into `StandardCommandIntentSource`,
  now **also non-generic** and, as a consequence, requiring every one of the six standard
  commands (not only `Attack`/`StopAttack`) to resolve to an `Alive` (§2.4 — a real,
  newly-introduced behavioral constraint versus today's tested behavior, called out in §7);
  bootstrapping a new `gametools-ai` module to hold a `Monster` + `WanderIntentSource`
  proof-of-concept (§2.5, §3); a shared `gametools-core` test-fixtures artifact — now just one
  fixture class, `TestAlive` (§5.2); and a naming/scope correction to
  `docs/framework-vision-and-roadmap.md` (§2.6). Single-player mouse/keyboard input mapping is
  investigated and **deferred** (§2.7). Implementation proceeds now; the version-tag *cut* is
  deferred to batch with Phase 2 (§8, §9 Decision 2).
- **Status:** planning only. No source, test, or build file has been modified by this document.
- **Revision note:** this is the **second** revision of the first draft.
  - The first revision resolved four open decisions (§9 Decisions 1–4): file a GitHub issue,
    batch the Major release with Phase 2, bootstrap `gametools-ai` now, and add
    `java-test-fixtures` now.
  - This second revision reworks the design around a further architectural change, confirmed by
    the user after the coordinator's own recommendation: `Intent`/`IntentSource` move from
    `Actor` down to `Alive` entirely (§9 Decision 5 — new this round); `Movement`/`destination`/
    `speed`/`move()` stay on `Actor`, unchanged. This eliminates the whole contravariance/`widen()`
    problem the first draft solved for (there is nothing left to widen — §2.1), removes `Actor`'s
    abstract-class change and its one previously-claimed production-code edit (`Projectile.kt`)
    entirely, and — on investigation — also required moving `Actor.world` back down to `Alive`,
    undoing its own #42 promotion (§2.2). Every section below reflects this second architecture,
    not the first revision's.
- **Baseline:** GameTools `5.1.0` is the last tagged release. `master` additionally carries, in
  `[Unreleased]`: `Actor.intent`/`Idle`/`Move`/`AttackIntent`/`Actor.issue`/`Actor.world` (#42,
  landed but **unreleased** — this matters a great deal here: this plan's §2.2 relocates that
  exact work before it ever ships, so none of it is a compatibility break against any real
  consumer), the `gametools-world` module bootstrap and its map/zone types (#46–#48, #63, #66).
  This plan itself bootstraps a fifth module, `gametools-ai` (§2.5, §3), following the exact
  precedent of the `gametools-world` bootstrap (commit `d096847`, issue #48).
- **Related docs:** `docs/issue-42-actor-intent-plan.md` and `docs/actor-intent-orders-plan.md`
  (both describe the `Actor`-level placement §2.2 now revises before release — §2.8 specifies
  the exact correction note each needs); `docs/framework-vision-and-roadmap.md` (§3 Phase 2
  "rich combat" — moves `Alive` to `gametools-combat`, the separate Major release this plan's
  version *tag* is deferred to batch with, §8/§9 Decision 2; §3 Phase 4 "AI & pathfinding" — the
  `gametools-ai` module this plan bootstraps early and the `Creep` archetype, corrected in §2.6);
  `docs/client-command-protocol-plan.md` (the open-hierarchy pattern `ClientCommand` and `Intent`
  both follow).

---

## 1. Context

### 1.1 What exists today (verified against `master`, this session)

- `Actor.tick()` → `GameObject.tick()` (`GameObject.kt:84`) is **final**; `onUpdate()`
  (`GameObject.kt:98`) is `protected open`, default no-op. `Actor.onUpdate()` (`Actor.kt:180`)
  runs `super.onUpdate()` then, gated on `can(CoreCapability.MOVE)`, `move()`. `Alive.onUpdate()`
  (`Alive.kt:432`) runs `super.onUpdate()`, then death/health-bar handling, then — gated on
  `can(CoreCapability.ATTACK)` — `considerAttack()`.
- `Actor.intent: Intent` (`Actor.kt:55`, `private set`, default `Idle`) and
  `Actor.issue(next: Intent)` (`Actor.kt:66`) already exist (#42, landed, **unreleased**):
  `issue` unconditionally runs `previous.clear(this)`, installs `next`, runs `next.issue(this)`,
  then publishes `GameEvent.IntentCleared`/`IntentIssued` — **no deduplication**. `Actor.world:
  World?` (`Actor.kt:52`) also lives here today, with KDoc explicitly stating it was *"Promoted
  here from `Alive` so any `Actor` — not only an `Alive` — can publish
  `GameEvent.IntentIssued`/`IntentCleared` through `issue`"* — i.e. it was moved up specifically
  to support `Actor`-level `issue()`. `World.add()`'s own KDoc (`World.kt:200-202`) gives two
  reasons for setting `Actor.world`: "(1) a `Alive.DeathResponse.REMOVAL` death can reach
  `removeList`, (2) an `Actor.issue` can publish on `events`." **Both are investigated in §2.2
  and found to be `Alive`-only concerns.**
- `Intent` (`Intent.kt`) is `abstract class Intent` with `open fun issue(actor: Actor)`/
  `open fun clear(actor: Actor)` (open, not sealed, not an enum). Ships `Idle`, `Move`
  (`Intent.kt:47`), and `AttackIntent` (`Alive.kt:461`, already `Alive`-specific — it self-clears
  via an `EventBus` subscription on `GameEvent.EntityDied` and today internally guards with
  `actor as? Alive ?: return`, since its formal parameter is still `Actor`-typed).
- `GameEvent.IntentIssued`/`IntentCleared` (`GameEvent.kt:110,119`) are declared
  `data class IntentIssued(val actor: Actor, val intent: Intent)` /
  `data class IntentCleared(val actor: Actor, val previous: Intent)` — `Actor`-typed, unlike
  every other combat event in this file (`AttackIssued`, `AttackLanded`, `DamageDealt`,
  `EntityDied`, `AttackCancelled`, `AttackEnded`), which are all already `Alive`-typed.
- `ActorSnapshot` (`Actor.kt:267`) carries `val intent: String = Idle.label`, set from
  `actor.intent.label`; `AliveSnapshot` does not have its own `intent` field today — it just
  wraps an `ActorSnapshot`.
- `Actor` and `Alive` are both `open class` — concretely instantiable today, not abstract.
  `Projectile` (`Projectile.kt:19`) is already `abstract class Projectile(...) : Actor(...)`,
  with two concrete leaves, `DirectionalProjectile` and `HomingProjectile`. **Neither ever calls
  `issue`/reads `intent` — both set `Actor.movement` directly** (`init { movement =
  Movement.Directional }` / `init { movement = Movement.Homing(target) }`). `Projectile` is the
  *only* non-`Alive` concrete `Actor` family anywhere in the codebase, confirmed by a repo-wide
  search — this is the central fact §2.1/§2.2 build on: `Intent` living on `Actor` today serves
  exactly one real consumer family, `Alive`.
- A repo-wide search for `.world`/`world?.`/`world =` usage in every module's `main` source
  finds **no** non-`Alive`, non-`World.kt`, non-`Actor.kt`-itself consumer of `Actor.world`
  anywhere — every real read/write is inside `Alive.kt`'s combat/death/evasion code, or
  `World.kt`'s own `add()` wiring. §2.2 makes the resulting call explicit.
- `StandardCommandApplier.kt` (`gametools-net/.../networking/command/StandardCommandApplier.kt`)
  carries out GameTools' six standard `ClientCommand`s (`ClientCommand.kt`) by resolving an
  `EntityId` operand through `World.byId` and calling `actor.issue(Move(...))` /
  `alive.issue(AttackIntent(...))` **synchronously, immediately** — `applyTo(world)` both
  validates and applies in one call, returning `ApplyResult` (`Applied` / `TargetMissing` /
  `WrongType` / `Unhandled`). Today, `MoveTo`/`MoveDir`/`Follow`/`Stop` resolve to any `Actor`;
  only `Attack`/`StopAttack` require an `Alive`. **This distinction disappears under this plan**
  (§2.4) — confirmed via `StandardCommandApplierTest.kt`'s own existing test, `"a movement
  command on a plain Actor applies without incident"`, that this Actor-scoped behavior is real,
  tested, and currently shipping (unreleased) — not something already narrowed to `Alive` today.
- `GameServer.kt` decodes `INPUT` datagrams into `MouseAction` (a bare wire DTO, zero logic —
  `MouseAction.kt`) and `COMMAND` datagrams into `ClientCommand`, handing both to app-supplied
  callbacks (`onPlayerInput`, `onCommand`); it never calls `applyTo` itself — that is left to
  the app. **No code anywhere maps a `MouseAction` to an `Intent`.**
- **No AI exists anywhere in the codebase**, and no `gametools-ai` module exists yet.
  `docs/framework-vision-and-roadmap.md` §3 Phase 4 names a `WanderIntent` + `Creep` archetype
  for that not-yet-created module (§2.6 quotes and corrects this).
- A repo-wide grep for direct `Alive(` construction (excluding `Alive.kt` itself and a
  `Player.kt` log-string false positive) finds **19 files** — the accurate, `Alive`-specific
  count this revision needs (§5), materially smaller than the 43-file `Actor`-*or*-`Alive` count
  the first revision worked from, because `Actor` no longer changes shape at all. A further
  targeted search (§5.1) finds **4 more files** that call `.issue()`/`Intent.issue()`/
  `.clearIntent()`/read `.intent` on a **plain `Actor`** today — these do not construct
  `Alive(...)` today but must switch to one, since those members move to `Alive` (§2.2). Full
  accounting in §5.
- `gametools-world`'s own bootstrap (commit `d096847`, issue #48) is this plan's precedent for
  §2.5/§3's `gametools-ai` bootstrap. No `Alive(` construction of any kind exists in
  `gametools-world` — confirmed by grep — so, unlike the first revision, `gametools-world` needs
  **no changes at all** anywhere in this plan.
- No `java-test-fixtures` (or any cross-module test-source sharing) exists anywhere in this
  build today. §5.2 is this build's first use of it.

### 1.2 What this plan closes

Three drivers of "what should this unit do right now" exist or should exist — a human's mouse
clicks, a remote client's `ClientCommand`s, and an NPC's own decision-making — and today they
are handled by three different, incompatible mechanisms (one doesn't exist, one calls `issue`
directly and synchronously from network code, one doesn't exist). `IntentSource` gives all
three the same one-method shape for `Alive`, so `Alive` never needs to know or care which is
driving it, and a fourth driver (a debug console, a replay system, a "possess this unit" cheat)
is just another implementation, exactly as `ClientCommand` and `Intent` are already open for
consumer extension.

---

## 2. Design

### 2.1 `IntentSource` shape — non-generic, `Alive`-only

```kotlin
// gameobjects/IntentSource.kt
/**
 * A pluggable strategy that decides what [Intent] an [Alive] should be under right now.
 *
 * [decide] is polled once per tick, from [Alive.onUpdate], for whichever unit currently owns
 * this source ([Alive.intentSource]). Returning `null` — the common case — means "leave the
 * current [Alive.intent] alone"; returning an [Intent] hands it straight to [Alive.issue],
 * which becomes this tick's standing order. A source never calls [Alive.issue] itself — it only
 * proposes; [Alive.onUpdate] is the single funnel that installs what is proposed.
 *
 * ### No deduplication
 *
 * [Alive.issue] does not check whether its argument equals the current [Alive.intent] — every
 * call tears the previous one down and installs the new one, publishing
 * [com.spartanlabs.gaming.event.GameEvent.IntentIssued] every time (see `IntentSourceTest` for
 * the concrete, visible cost of getting this wrong). A well-behaved [decide] returns `null` once
 * it has nothing new to say — e.g. "I already sent it here and it hasn't arrived yet" — rather
 * than re-proposing the same [Intent] every tick.
 *
 * ### Why this is not generic
 *
 * An earlier design made this `IntentSource<in A : Actor>`, so an AI reading `Alive`-only state
 * could be written directly against that narrower type. That generality turned out to be
 * unneeded: `Intent`/`intentSource` live only on [Alive] now (§2.2) — `Actor`'s only other
 * concrete family, `Projectile`, never calls `issue`/reads `intent` (it drives its behavior
 * entirely through `Actor.movement`, confirmed by inspecting `DirectionalProjectile`/
 * `HomingProjectile`), so there is no second consumer type to be generic *over*. A non-generic
 * `IntentSource` accepting `Alive` directly is simpler and needs no contravariance or widening
 * adapter; if a future non-`Alive` `Actor` family ever needs standing orders, `IntentSource` is
 * a fresh-enough interface to regenericize then without disturbing `Alive`'s own usage.
 *
 * `IntentSource` generalizes the three ways a unit's behavior is driven — a human's
 * mouse/keyboard input, a remote client's [com.spartanlabs.gaming.networking.command.ClientCommand]
 * (see `StandardCommandIntentSource` in `gametools-net`), and NPC/AI decision-making (see
 * `Monster` in `gametools-ai`) — as one interface each of them implements, the same way
 * `ClientCommand` is one interface every standard and consumer command implements. This is the
 * classic "unify human input and AI behind one interface" idea from the Command pattern
 * (Robert Nystrom, *Game Programming Patterns*,
 * ["Command"](https://gameprogrammingpatterns.com/command.html)): there it is player input and
 * AI emitting the same executable `Command`; here it is player input, network commands, and AI
 * emitting the same *proposed* [Intent] through the same decision method, with the actual
 * application still funnelled through [Alive.issue].
 */
fun interface IntentSource {
    /** Proposes a new [Intent] for [alive], or `null` to leave [Alive.intent] as it is. */
    fun decide(alive: Alive): Intent?

    companion object {
        /** Proposes nothing, ever. For a unit driven purely by direct [Alive.issue] calls. */
        val None: IntentSource = IntentSource { null }
    }
}
```

`fun interface` (a Kotlin SAM/"functional interface") is still chosen deliberately:
`IntentSource` has exactly one abstract member, and this lets a test, a demo, or a trivial
consumer source be written as a lambda — `IntentSource { null }`, `IntentSource { alive -> ... }`
— with no boilerplate class.

### 2.2 `Alive` becomes abstract; `Intent`, `issue`, `clearIntent`, and `world` move down from `Actor`

This is real code motion in already-landed, unreleased work, not just new-code placement — and,
per §9 Decision 5, the biggest change in this revision.

#### The `world` question, investigated and resolved

The coordinator's brief asked whether `Actor.world` should also move back to `Alive`, undoing
its #42 promotion entirely, or whether some real reason remains for `Actor` (or `Projectile`) to
carry it. **Resolution: move it back to `Alive`.** Both of `World.add()`'s documented reasons
for setting it (§1.1) are `Alive`-only — `DeathResponse.REMOVAL` is declared inside `Alive`, and
`issue()` is moving there too — and the repo-wide search in §1.1 found no other reader or writer
anywhere. Nothing in `Actor`'s or `Projectile`'s own logic ever touches `.world`, today or
per any roadmap item that names a concrete need. Keeping it on `Actor` "just in case" would be
carrying dead weight for a hypothetical: cheap to re-promote later (it is one property plus one
`World.add()` type check) if a real non-`Alive` consumer ever appears, and per YAGNI there is no
reason to pay that cost now. The net effect of #42 promoting `world` and this plan demoting it
again, before either ever ships, is that `Actor` ends this plan with **exactly the shape it had
before #42** — invisible to every consumer, since nothing between those two points ever released
(§8 covers the `CHANGELOG.md` consequence).

#### `Actor.kt` — reverts

- `var world: World? = null` — **removed**.
- `var intent: Intent = Idle`, `fun issue(next: Intent)`, `fun clearIntent()` — **removed**.
- `onUpdate()` reverts to its pre-#42 body: `super.onUpdate(); if (can(CoreCapability.MOVE))
  move() else log.debug(...)` — no `Intent`/`IntentSource` involvement at all.
- `ActorSnapshot` loses its `intent` field; `ActorSnapshot.from(actor)` reverts to
  `id, visibleObject, speed, destination` only.
- Class KDoc drops the `intent`/`IntentSource` paragraph added by #42/the first revision — back
  to describing only `movement` as the mechanism.
- `Actor` stays `open class Actor` — **no abstract-class change of any kind.**

#### `Alive.kt` — gains everything

```kotlin
abstract class Alive(
    location: Point,
    dimensions: Dimensions,
    maxHealth: Double
) : Actor(location = location, dimensions = dimensions) {

    /** The [World] this unit belongs to, or `null` when it is not in one. Moved back here from
     * `Actor` (§2.2) — only an [Alive] ever needed it, for [DeathResponse.REMOVAL] and [issue]. */
    var world: World? = null

    /** The unit's current standing order. [Idle] until [issue] is called. */
    var intent: Intent = Idle
        private set

    /**
     * What decides this unit's [intent] each tick. Never `null` — every concrete `Alive`
     * subtype must assign one at construction, even if that is [IntentSource.None] ("driven
     * purely by direct [issue] calls", today's implicit behavior). Mutable, like `Actor.movement`,
     * so a game can swap control schemes at runtime — e.g. a `Monster` that becomes
     * player-controlled once "captured".
     */
    abstract var intentSource: IntentSource

    /**
     * Replaces [intent] with [next]: runs the outgoing intent's [Intent.clear], installs
     * [next], then runs its [Intent.issue] — so orders are mutually exclusive by construction.
     * Publishes [GameEvent.IntentCleared] when [next] is [Idle], otherwise
     * [GameEvent.IntentIssued].
     */
    fun issue(next: Intent) {
        val previous = intent
        previous.clear(this)
        intent = next
        next.issue(this)
        log.debug("Alive intent changed from {} to {}", previous.label, next.label)
        world?.events?.publish(
            if (next === Idle) GameEvent.IntentCleared(this, previous)
            else GameEvent.IntentIssued(this, next)
        )
    }

    /** Returns this unit to [Idle] — the general form of "cancel whatever order is active." */
    fun clearIntent() = issue(Idle)

    /**
     * Polls [intentSource] first (before [super.onUpdate], deliberately — see below), then runs
     * the rest of this class's per-tick work.
     */
    override fun onUpdate() {
        intentSource.decide(this)?.let { next ->
            log.debug("IntentSource proposed {}; issuing it.", next.label)
            issue(next)
        }
        super.onUpdate()
        contemplateLife()
        updateHealthBar()
        if (can(CoreCapability.ATTACK)) considerAttack()
        else log.debug("An Alive's attack is frozen this tick; its attack capability is suppressed.")
    }
    // ... rest of Alive.kt (stats, combat, death, health bar) unchanged
}
```

**Why the poll runs *before* `super.onUpdate()`**, contrary to `GameObject.onUpdate`'s usual
"call `super` first" guidance: `super.onUpdate()` is what actually calls `Actor`'s `move()` this
tick. If the poll ran after, a freshly-decided `Move`'s `movement`/`destination` change would sit
unused until *next* tick's `move()` call — a one-tick lag with no counterpart on the attack side
(`considerAttack()` already runs later in this same override, so an `AttackIntent` decided this
tick takes effect this tick regardless of poll placement). Polling first keeps both mechanisms
consistent: whatever `intentSource` decides this tick is visible to every mechanism this same
tick. This does not skip any ancestor behavior — `GameObject.tick()` already ran buff-aging
before `onUpdate()` was ever called, so there is nothing in `super.onUpdate()`'s own work that
the poll depends on.

`World.add()` (`World.kt:208`) reverts its type check from `if (gameObject is Actor)
gameObject.world = this` back to `if (gameObject is Alive) gameObject.world = this`; its KDoc
reverts to citing only `Alive`-specific reasons.

`AliveSnapshot` gains the `intent` field `ActorSnapshot` loses: `val intent: String =
Idle.label`, set from `alive.intent.label` in `AliveSnapshot.from`.

#### 2.2.1 `Actor` and `Projectile` need **no** change at all

Unlike the first revision (where `Projectile` needed one line, `override var intentSource:
IntentSource<Actor> = IntentSource.None`), `Projectile` needs **nothing** now: `intentSource`
only exists on `Alive`, and `Projectile` is not an `Alive`. `DirectionalProjectile` and
`HomingProjectile` are completely unaffected. This removes the plan's only previously-claimed
production-code change outside the new `IntentSource.kt` — with `Intent`/`IntentSource` living
entirely on `Alive`, **zero production files** need editing beyond `Actor.kt`, `Alive.kt`,
`Intent.kt`, `GameEvent.kt`, and `World.kt` themselves.

### 2.3 `Intent.issue`/`clear` and `GameEvent.IntentIssued`/`IntentCleared` narrow to `Alive`

**Resolution: narrow both.** `Intent`'s base signatures become:

```kotlin
abstract class Intent {
    abstract val label: String
    open fun issue(alive: Alive) {}
    open fun clear(alive: Alive) {}
}
```

Every call site that installs an intent now literally always passes an `Alive` (`Alive.issue`'s
own body calls `next.issue(this)` where `this: Alive`), so narrowing the parameter is not a
speculative choice — it is what the type already is at the only call site that exists. `Move`
(`destination`/`movement` are `Actor`-level members, still reachable through an `Alive` since
`Alive` *is* an `Actor`) costs nothing from the narrowing; `Idle` is unaffected (no body).
`AttackIntent` — already `Alive`-specific, living in `Alive.kt` — is simplified by it, not
just unaffected:

```kotlin
class AttackIntent(val target: Alive) : Intent() {
    override val label = "attack"
    private var subscription: EventBus.Subscription? = null

    override fun issue(alive: Alive) {
        alive.issueAttack(target)
        subscription = alive.world?.events?.subscribe { event ->
            if (event is GameEvent.EntityDied && event.entity === target) alive.clearIntent()
        }
    }

    override fun clear(alive: Alive) {
        subscription?.cancel()
        subscription = null
        alive.cancelAttack()
    }
}
```

The `actor as? Alive ?: return` guard in `issue` and the `(actor as? Alive)?.cancelAttack()` cast
in `clear` — both artifacts of `Intent`'s old `Actor`-typed signature — are gone; `alive` is
already the right type.

**`GameEvent.IntentIssued`/`IntentCleared` narrow the same way**, matching every other combat
event in `GameEvent.kt` (`AttackIssued`, `DamageDealt`, `EntityDied`, …), all of which are
already `Alive`-typed and none of which use a generic `actor` property name:

```kotlin
/** An [Alive] was given a new standing order via [Alive.issue].
 * @property alive the unit the order was issued to
 * @property intent the standing order it was given */
data class IntentIssued(val alive: Alive, val intent: Intent) : GameEvent

/** An [Alive]'s standing order was cleared back to [Idle] via [Alive.issue] / [Alive.clearIntent].
 * @property alive the unit whose order was cleared
 * @property previous the standing order that was active before it was cleared */
data class IntentCleared(val alive: Alive, val previous: Intent) : GameEvent
```

(Property renamed `actor` → `alive` throughout; the two existing test assertions that read
`.actor` off these events — in `IntentTest.kt`, §5.1 — update to `.alive`.)

### 2.4 The network path: `StandardCommandIntentSource` — now `Alive`-only for all six commands

Today `ClientCommand.applyTo(world)` resolves an operand and calls `actor.issue(...)`/
`alive.issue(...)` synchronously, in the same call (already `Intent`-routed by #42). Reworked as
*the* network `IntentSource`, the resolve/validate step stays synchronous (an app still gets an
immediate `ApplyResult`), but the actual installation is deferred to the addressed unit's own
next `decide()` poll — the same funnel every other source uses.

```kotlin
// gametools-net/.../networking/command/StandardCommandIntentSource.kt
/**
 * The `gametools-net` [IntentSource] for GameTools' six standard commands. Assign one instance
 * — not shared — to [Alive.intentSource] for every unit a game wants players to be able to
 * command over the network; [applyTo] fails with [ApplyResult.NotNetworkControlled] for an
 * [Alive] that does not have one, and [ApplyResult.WrongType] for an operand that is not an
 * [Alive] at all — see below, this now applies to every one of the six standard commands, not
 * only [Attack]/[StopAttack].
 *
 * [enqueue]'s intent is installed on the unit's *next* tick, through the same [Alive.onUpdate]
 * → [IntentSource.decide] → [Alive.issue] path every other source uses — not synchronously when
 * the command arrives. If more than one command lands for this unit between two ticks, only the
 * most recent one's intent survives; there is no order queue (matching [Alive.intent]'s own
 * single-slot v1 design).
 */
class StandardCommandIntentSource : IntentSource {
    private val pending: ArrayDeque<Intent> = ArrayDeque()

    /** Queues [intent] to be issued on this unit's next tick, discarding anything already queued. */
    internal fun enqueue(intent: Intent) { pending.addLast(intent) }

    /** Drains every intent queued since the last tick and returns only the most recent, or `null` if none arrived. */
    override fun decide(alive: Alive): Intent? {
        var latest: Intent? = null
        while (pending.isNotEmpty()) latest = pending.removeFirst()
        return latest
    }
}
```

```kotlin
// StandardCommandApplier.kt — applyTo's when(), reworked; one helper for all six commands now
fun ClientCommand.applyTo(world: World): ApplyResult = when (this) {
    is MoveTo -> onNetworkedAlive(world, actor) { _, source ->
        source.enqueue(Move(Movement.Targeting, destination = Point(x, y)))
    }
    is MoveDir -> onNetworkedAlive(world, actor) { alive, source ->
        alive.angle = angleDegrees   // facing is not part of Intent's own state; applied immediately, as today
        source.enqueue(Move(Movement.Directional))
    }
    is Follow -> onNetworkedAlive(world, actor) { _, source ->
        val chased = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        source.enqueue(Move(Movement.Homing(chased)))
    }
    is Stop -> onNetworkedAlive(world, actor) { _, source -> source.enqueue(Idle) }
    is Attack -> onNetworkedAlive(world, attacker) { _, source ->
        val victim = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        val victimAlive = victim as? Alive ?: return ApplyResult.WrongType(target, Alive::class)
        source.enqueue(AttackIntent(victimAlive))
    }
    is StopAttack -> onNetworkedAlive(world, alive) { _, source -> source.enqueue(Idle) }
    else -> ApplyResult.Unhandled
}

private inline fun onNetworkedAlive(world: World, id: EntityId, action: (Alive, StandardCommandIntentSource) -> Unit): ApplyResult {
    val resolved = world.byId(id) ?: return ApplyResult.TargetMissing(id)
    val alive = resolved as? Alive ?: return ApplyResult.WrongType(id, Alive::class)
    val source = alive.intentSource as? StandardCommandIntentSource ?: return ApplyResult.NotNetworkControlled(id)
    action(alive, source)
    return ApplyResult.Applied
}
```

This **replaces** the previous revision's two-helper split (`onNetworkedActor` for the four
movement commands, `onNetworkedAlive` for the two attack commands) with a single helper, since
there is no longer an Actor-scoped/Alive-scoped distinction to preserve — a real simplification,
not just a rename.

**A real, newly-introduced constraint versus today's tested behavior — flagged here and in §7,
not glossed over:** today, `MoveTo`/`MoveDir`/`Follow`/`Stop` can target *any* `Actor` — proven
by `StandardCommandApplierTest.kt`'s own existing, currently-passing test, `"a movement command
on a plain Actor applies without incident"`. Under this design, since `intentSource` exists only
on `Alive`, **none** of the six standard commands can ever target a plain (non-`Alive`) `Actor`
again — that test's premise flips outright (§5.1, §6 specify the exact replacement assertion).
This is a direct, unavoidable consequence of the confirmed architecture (§9 Decision 5), not a
free-standing design choice this plan is making independently — there is no dual-path
alternative that would preserve plain-`Actor` network movement without reintroducing the
"some commands instant, others deferred" inconsistency this design deliberately avoids (§2.4 of
the first revision already rejected that shape on its own terms). It is called out this
prominently because it is a genuine regression against currently-shipping-but-unreleased,
explicitly-tested behavior, and deserves to be visible rather than discovered later.

`ClientCommand.kt`'s KDoc for all six commands updates accordingly: the `ApplyResult.WrongType`
KDoc's old "an `Actor`-only command... or an `Alive`-only command..." distinction collapses to
one sentence — every standard command's operand must resolve to an `Alive`.

**Deliberate, documented behavior/timing change (unrelated to the above, carried from the first
revision):** a command still takes effect on the addressed unit's *next* tick, not the instant
`applyTo` returns. `GameServer.kt` itself needs **no change** — it never called `applyTo`; that
has always been app-level.

### 2.5 `Monster` + `WanderIntentSource` — bootstrapping `gametools-ai`

Per the coordinator's decision (§9 Decision 3), `gametools-ai` is bootstrapped now — empty except
for `Monster.kt` — rather than holding `Monster` in `gametools-core`. This is a real
module-bootstrap task, following the exact precedent of `gametools-world`'s own bootstrap
(commit `d096847`, issue #48, §1.1): a new Gradle module registered in `settings.gradle.kts`, its
own `build.gradle.kts` on the `gametools.published-library` convention plugin, wired into the
root Dokka aggregation and the umbrella's `api`/sources/Dokka re-export (§3 has the exact
file-by-file shape). Nothing about this section changes because of §9 Decision 5 beyond
`WanderIntentSource`'s own shape, below.

#### Dependency direction — a documented, temporary deviation from the roadmap

`docs/framework-vision-and-roadmap.md` §2.1 states `gametools-ai` depends on `world` + `combat`.
Neither is available to depend on for what `Monster` needs today: `gametools-combat` does not
exist (Phase 2, still unscheduled — §9 Decision 2), and `Alive`/`IntentSource` still live in
`gametools-core`. `gametools-ai/build.gradle.kts` therefore depends on `gametools-core` directly:

```kotlin
dependencies {
    api(project(":gametools-core"))
}
```

**This is a known, explicit, temporary deviation from the roadmap's stated target graph, not a
silent contradiction of it.** `gametools-ai/build.gradle.kts` carries a header comment saying
so (§3), and `docs/framework-vision-and-roadmap.md` §2.1's dependency-direction paragraph gains
a trailing sentence: *"`gametools-ai` depends directly on `gametools-core` from its
`docs/intent-source-plan.md` bootstrap (`Monster`/`WanderIntentSource`) until Phase 2 moves
`Alive` into `gametools-combat`, at which point it re-points to `world` + `combat` as stated
above — a deliberate, temporary deviation, not a correction to this table."* `gametools-ai`
does **not** yet depend on `gametools-world` either — `Monster`'s wander logic (below) uses no
map/zone/vision type, so there is nothing in `world` for it to need yet; that dependency arrives
whenever `gametools-ai`'s first real `NavProvider` work does (Phase 4, unaffected by this plan).

Re-pointing `gametools-ai` from `gametools-core` to `gametools-combat` once Phase 2 lands is
listed as a named follow-up in §11 — it is Phase 2's responsibility, not this plan's.

#### `Monster.kt` (in `gametools-ai`, package `com.spartanlabs.gaming.ai`)

```kotlin
/**
 * A simple wandering [Alive]: while under [WanderIntentSource] (its default), it walks to a
 * random point within [homeRadius] of [home], and picks a new one once it arrives. A minimal
 * [IntentSource] consumer proving the mechanism, not a full AI archetype — contrast the future
 * `Creep` (§ `docs/framework-vision-and-roadmap.md` Phase 4), a move-to-point-then-attack-target
 * goal AI for MOBA lane creeps, which `Monster` is deliberately not. `Monster` also bootstraps
 * this module (`gametools-ai`) ahead of the rest of Phase 4 — see `docs/intent-source-plan.md` §2.5.
 */
open class Monster(
    location: Point,
    dimensions: Dimensions = Dimensions(),
    maxHealth: Double = 100.0,
    home: Point = Point(location),
    homeRadius: Double = 200.0,
) : Alive(location, dimensions, maxHealth) {
    override var intentSource: IntentSource = WanderIntentSource(home, homeRadius)
}

/**
 * Picks a random point within [homeRadius] of [home] to walk to, and picks another once the
 * unit arrives (or is not already under a [Move] at all — e.g. fresh off construction, or just
 * back from an [AttackIntent]). Only reads members [Alive] inherits from `Actor` (`intent`,
 * `isAtDestination`, `movement`, `destination`), which no longer matters for typing — `decide`
 * takes an [Alive] regardless, unconditionally (§2.1's "why this is not generic").
 */
class WanderIntentSource(
    private val home: Point,
    private val homeRadius: Double,
) : IntentSource {
    override fun decide(alive: Alive): Intent? =
        if (alive.intent is Move && !alive.isAtDestination) null
        else Move(Movement.Targeting, destination = randomPointInHome(alive))

    private fun randomPointInHome(alive: Alive): Point {
        val rng = alive.world?.rng ?: fallbackRng(alive)
        // implementer's choice of distribution (uniform-in-disc recommended); seeded via the
        // world's RandomSource for determinism, mirroring Alive.evasionRng()'s fallback pattern
        ...
    }
}
```

### 2.6 Roadmap correction: `Creep` → `Monster`, the mechanism, and the new module's early start

`docs/framework-vision-and-roadmap.md` currently reads, at §2.1's module table:

> ```
> gametools-ai           NavProvider (grid A* + flow fields), path following, aggro / target
>                        selection helpers built on the threat table + vision, and a
>                        wandering `Creep` archetype.
> ```

and at §3 Phase 4, item 5:

> ```
> 5. **Wander / idle behavior.** A `WanderIntent` (same shape as `Move`/`AttackIntent` — picks
>    a random destination inside a bounded home area, re-issues on arrival) and a `Creep`
>    archetype (an `Alive` subclass defaulting to `WanderIntent` when idle) as the module's
>    first consumer-facing AI-driven unit.
> ```

Both predate this session's naming decision and describe a mechanism (`WanderIntent`,
"re-issues on arrival") this plan supersedes with the real one (`IntentSource`/`decide()`), and
both predate this plan's module bootstrap now landing ahead of the rest of Phase 4. **As part of
this plan's implementation** (not by Planner directly — no source or doc file is touched by this
document itself), update:

- The module blurb to: `NavProvider (grid A* + flow fields), path following, aggro / target
  selection helpers built on the threat table + vision, the future `Creep` archetype
  (move-to-point-then-attack-target goal AI, MOBA lane creeps), and `Monster` (simple wander
  behavior via `IntentSource`), which bootstraps this module ahead of the rest of Phase 4
  (`docs/intent-source-plan.md`). Depends on `gametools-core` directly until Phase 2 moves
  `Alive` to `gametools-combat` — a temporary deviation from the dependency row below (see
  `docs/intent-source-plan.md` §2.5).`
- Phase 4 item 5 to: `**Target-seeking AI.** The `Creep` archetype: a
  move-to-point-then-attack-target goal `IntentSource` for MOBA lane creeps, built on the
  threat table + vision (distinct from `Monster`'s simple wander behavior, already shipped in
  this module ahead of the rest of Phase 4 via `docs/intent-source-plan.md`'s `IntentSource`
  mechanism and module bootstrap).`
- §2.1's dependency-direction paragraph gains the trailing sentence quoted in §2.5 above, noting
  the temporary `gametools-ai` → `gametools-core` dependency.

This is a rename **and** a scope correction: `gametools-ai` itself, and wander behavior with it,
are pulled forward ahead of Phase 4, and `Creep` is freed to mean only the future MOBA
archetype.

### 2.7 Single-player input mapping: investigated, recommended **deferred**

Unaffected by §9 Decision 5 beyond the obvious: any future single-player `IntentSource` would
read/write an `Alive`, not an arbitrary `Actor`. The recommendation itself stands as originally
proposed:

- The network path already had a mechanism to rework (`StandardCommandApplier`); input mapping
  has **nothing** to rework — it would be new API surface invented from scratch, on top of a
  wire DTO (`MouseAction`) explicitly documented as "zero logic."
- `docs/framework-vision-and-roadmap.md` §1 decision 5 and §3 Phase 3 item 3 already commit to a
  **generic, named, bindable action-map** input vocabulary as the framework's real input model —
  "mouse and keyboard are just sources." A one-off `MouseIntentSource` shipped now would either
  be thrown away at Phase 3 or lock in a worse API early.
- Nothing downstream in *this* plan (`Monster`, the network rework) needs it to exist to be
  useful or testable.

`IntentSource`'s own KDoc (§2.1) sketches — in prose, not shipped code — what a single-player
source would look like (`IntentSource { alive -> ... }` reading the last `MouseAction`), so the
concept is documented without committing to a premature concrete API. Building it for real is
listed as a named follow-up in §11.

### 2.8 Cross-references, and the note `#42`'s two docs now need

`docs/issue-42-actor-intent-plan.md` §7 and `docs/actor-intent-orders-plan.md` §1.2 both already
flag `AttackMove`/`Patrol`/`Hold` as future consumer `Intent`s attaching at "Phase 4." Neither
says *what decides when to issue them* — that gap is exactly `IntentSource`. **Recommendation:**
add one cross-reference sentence to each (implementation work, not this document): to
`issue-42-actor-intent-plan.md` §7, under each of the three bullets, "an `IntentSource` on
`Alive` is what would decide when to issue this, once built (`docs/intent-source-plan.md`)"; to
`actor-intent-orders-plan.md`'s already-historical §1.2, a trailing note that the "what decides"
question these consumer intents leave open is answered by `docs/intent-source-plan.md`.

**New this revision:** both documents describe `Actor.intent`/`Actor.issue`/`Actor.world` as
already-landed fact, written before §2.2's relocation was decided. Since none of it has shipped,
each gets one additional short note, non-structural, matching how the rest of §2.8 already
handles cross-references:

- `docs/issue-42-actor-intent-plan.md`: a one-line note at the top of §2.2 ("`Actor` gains
  `world` and `intent`") reading: *"Superseded before release: `docs/intent-source-plan.md`
  moves `intent`/`issue`/`clearIntent`/`world` down to `Alive` and narrows `Intent`'s own
  signatures to match, once it became clear `Alive` is `Intent`'s only real consumer. The design
  captured here shipped as source, briefly, but never as a release — treat this section as
  historical."*
- `docs/actor-intent-orders-plan.md`: since it is already marked superseded by the document
  above, one line appended to its own existing superseded-by note: *"...and that document's own
  `Actor`-level placement was itself superseded, before release, by
  `docs/intent-source-plan.md` — `Intent` lives on `Alive`, not `Actor`, in the shipped shape."*

---

## 3. File-by-file changes

### `gametools-core`

**New — `gameobjects/IntentSource.kt`**
- `fun interface IntentSource { fun decide(alive: Alive): Intent? }`, companion `None` (§2.1).
- Full KDoc per the Level-2 standard; region-grouped imports.

**Modified — `gameobjects/Actor.kt`** (reverts — §2.2)
- Remove `var world: World? = null`.
- Remove `var intent`, `fun issue`, `fun clearIntent`.
- `onUpdate()` reverts to its pre-#42 body (no `Intent`/`IntentSource` involvement).
- `ActorSnapshot` loses its `intent` field and constructor default; `ActorSnapshot.from` reverts.
- Class KDoc drops the `intent`/`intentSource` paragraph.
- **No abstract-class change.** `Actor` stays `open class`.

**Modified — `gameobjects/Alive.kt`** (gains everything — §2.2)
- `open class Alive` → `abstract class Alive`.
- Add `var world: World? = null` (moved back from `Actor`).
- Add `var intent: Intent = Idle; private set`, `fun issue(next: Intent)`, `fun clearIntent()`
  (moved from `Actor`).
- Add `abstract var intentSource: IntentSource` (new — this plan's core requirement).
- `onUpdate()` gains the `intentSource.decide(this)?.let(::issue)` poll as its **first**
  statement, before `super.onUpdate()` (§2.2 explains why).
- `AttackIntent` simplifies: `issue(alive: Alive)`/`clear(alive: Alive)`, no more `as? Alive`
  casts (§2.3).
- `AliveSnapshot` gains `val intent: String = Idle.label`; `AliveSnapshot.from` sets it.

**Modified — `gameobjects/Intent.kt`**
- `Intent.issue(actor: Actor)`/`clear(actor: Actor)` → `issue(alive: Alive)`/`clear(alive: Alive)`
  (§2.3). `Idle`/`Move` updated to match (parameter rename only; no logic change — both already
  only touch `Actor`-level members, reachable through an `Alive`).
- Import `com.spartanlabs.gaming.gameobjects.Alive`... (same-package, no import needed — noted
  for completeness).

**Modified — `event/GameEvent.kt`**
- `IntentIssued(val actor: Actor, val intent: Intent)` → `IntentIssued(val alive: Alive, val
  intent: Intent)`; `IntentCleared` narrows the same way (§2.3). KDoc updated to reference
  `Alive.issue`/`Alive.clearIntent`.

**Modified — `gameobjects/World.kt`**
- `World.add()`: type check reverts from `if (gameObject is Actor) gameObject.world = this` back
  to `if (gameObject is Alive) gameObject.world = this`; KDoc reverts to citing only `Alive`
  reasons (§2.2).

**Unmodified — `gameobjects/Projectile.kt`** (§2.2.1) — no change at all, confirmed.

**Modified — `build.gradle.kts` (`gametools-core`)**
- Add `id("java-test-fixtures")` to the `plugins {}` block (§5.2, per §9 Decision 4).

**New — `src/testFixtures/kotlin/com/spartanlabs/gaming/testing/support/TestAlive.kt`**
- The **only** fixture class needed now (no `TestActor` — `Actor` stays concrete). Minimal
  concrete `Alive`, public (not `internal` — required for cross-module visibility, see §5.2),
  assigning `intentSource = IntentSource.None`.

### `gametools-net`

**New — `networking/command/StandardCommandIntentSource.kt`**
- `class StandardCommandIntentSource : IntentSource` (§2.4).

**Modified — `networking/command/StandardCommandApplier.kt`**
- `applyTo`'s `when` rewritten per §2.4's table; the previous revision's `onNetworkedActor`/
  `onNetworkedAlive` pair collapses into **one** `onNetworkedAlive` helper, used by all six
  branches (§2.4).
- `ApplyResult` gains `data class NotNetworkControlled(val id: EntityId) : ApplyResult`.
- `ApplyResult.WrongType`'s KDoc simplifies: no more Actor-only/Alive-only distinction — every
  standard command's operand must resolve to an `Alive`.

**Modified — `networking/command/ClientCommand.kt`**
- KDoc on all six commands: every one now requires its operand to resolve to an `Alive`, not
  only `Attack`/`StopAttack` (§2.4). No `@Serializable` shape change — wire-compatible.

**Modified — `build.gradle.kts` (`gametools-net`)**
- Add `testImplementation(testFixtures(project(":gametools-core")))` to `dependencies {}`
  (§5.2).

### `gametools-world`

**No changes at all.** No production or test code in this module constructs `Alive` or calls
`.issue()`/`.intent` (confirmed §1.1) — this module drops out of this plan's migration entirely,
unlike the first revision.

### `gametools-ai` (new module, bootstrapped by this plan — §2.5, §9 Decision 3)

**New — `gametools-ai/build.gradle.kts`**, mirroring `gametools-world`'s bootstrap
(`d096847`) exactly in shape:
```kotlin
// Phase 4 module (docs/framework-vision-and-roadmap.md §3 Phase 4), bootstrapped early by
// docs/intent-source-plan.md to hold Monster/WanderIntentSource today. Depends on
// gametools-core directly for now - Alive/IntentSource still live there; the roadmap's target
// dependency (world + combat) applies once Phase 2 moves Alive to gametools-combat, at which
// point this repoints there. Deliberate, temporary deviation - see docs/intent-source-plan.md §2.5.

plugins {
    id("gametools.published-library")
}

dependencies {
    api(project(":gametools-core"))
}

mavenPublishing {
    coordinates("io.github.spartanlabsgaming", "gametools-ai", "5.1.0")
    pom {
        name.set("GameTools AI")
        description.set("NPC decision-making: Monster and other IntentSource-driven archetypes.")
    }
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://github.com/SpartanLabsGaming/MyGameTools/blob/master/gametools-ai/src/main/kotlin")
            remoteLineSuffix.set("#L")
        }
    }
}
```
(The `"5.1.0"` literal is `master`'s current baseline, matching every other module's
`coordinates(...)` today — it does **not** get bumped by this plan's own commits; see §8.)

**New — `gametools-ai/src/main/kotlin/com/spartanlabs/gaming/ai/Monster.kt`**
- `open class Monster(...) : Alive(...)` and `class WanderIntentSource(...) : IntentSource`
  (§2.5).

**Modified — `settings.gradle.kts`**
- `include("gametools-core", "gametools-net", "gametools-world", "gametools-ai", "gametools")`.

**Modified — `build.gradle.kts` (root)**
- Add `dokka(project(":gametools-ai"))` to the root Dokka aggregation.

**Modified — `gametools/build.gradle.kts`**
- Add `api(project(":gametools-ai"))`, `reexportedModuleSources(project(path = ":gametools-ai", configuration = "sourcesElements"))`,
  and `dokka(project(":gametools-ai"))`.

### Docs

- `README.md`: the architecture overview and "typed command protocol" paragraph gain a sentence
  each introducing `IntentSource` (on `Alive`) and noting every standard command now requires an
  `Alive` operand. The module table gains a `gametools-ai` row.
- `CONTRIBUTING.md`: the module-layout table gains a `gametools-ai` row and its "depends on"
  column, mirroring the exact edit `d096847` made for `gametools-world`.
- `CHANGELOG.md`: amend the existing `[Unreleased]` `#42` entries in place, plus new entries for
  this plan (§8 — full detail, including the exact amendment).
- `docs/framework-vision-and-roadmap.md`: the corrections in §2.6.
- `docs/issue-42-actor-intent-plan.md`, `docs/actor-intent-orders-plan.md`: the notes specified
  in §2.8.

---

## 4. Design calls resolved directly (not left open)

### 4.1 Call-site placement is now trivial: `Alive.onUpdate`, polled first

With `intentSource` existing only on `Alive`, the "where does the poll live" question the first
revision spent real space on (`Actor` vs `Alive`, `Player`/`Projectile` considerations)
dissolves: there is exactly one type that can have a poll at all. `Player.kt` was never an
`Actor` in the first place; `Projectile` never had `intentSource` to begin with (§2.2.1). The
only design decision that remains is *where within* `Alive.onUpdate` the poll runs — resolved in
§2.2 (before `super.onUpdate()`, so the tick's move step sees a freshly-decided `Move`
immediately).

### 4.2 No `CoreCapability` gate on the poll itself

`Alive.onUpdate` already gates the *mechanisms* (`move()` via `super.onUpdate()`,
`considerAttack()`) on `can(CoreCapability.MOVE)`/`ATTACK)` — a suppressed capability freezes
what a mechanism does, not what orders exist (`docs/issue-42-actor-intent-plan.md` §2.4,
"Decision H," already established this for direct `issue()` calls). `IntentSource.decide` is
polled unconditionally, for the same reason `StandardCommandApplier.applyTo` was always
documented as *not* doing capability checks: a stunned unit whose network queue drains a `Move`
still gets `intent = Move(...)` installed (the *order* is recorded), it simply does not move
this tick because `move()` itself is still gated — exactly today's behavior for a direct
`issue()` call during a suppression, now generalized to every source uniformly.

### 4.3 `fun interface` (SAM conversion)

Confirmed appropriate (§2.1) — `IntentSource` has exactly one abstract member, and it lets
`IntentSourceTest` and any consumer's trivial source be one-line lambdas instead of anonymous
classes. (No generic type parameter to worry about now, either — SAM conversion is even simpler
than in the first revision.)

---

## 5. The migration: `Alive` going abstract

`Actor` does not change shape at all in this revision — it is not becoming `abstract`, and no
file that constructs a plain `Actor` for movement-only purposes is affected. This section is
correspondingly smaller than the first revision's and is retitled to say so precisely.

### 5.1 What breaks, and how much

**Zero production files** need editing beyond the five already named in §3
(`Actor.kt`/`Alive.kt`/`Intent.kt`/`GameEvent.kt`/`World.kt`) — not even `Projectile.kt` (§2.2.1).

Test code: a direct grep for `Alive(` construction (excluding `Alive.kt` itself and `Player.kt`'s
one log-string false positive) finds **19 files**. A second, targeted search for `.issue(`/
`.clearIntent(`/`.intent` usage finds **4 more files** that construct a plain `Actor` today but
call members now living only on `Alive` — these would not show up in a naive "grep for `Alive(`"
count, and catching them is exactly the kind of thing a mechanical grep alone would miss:

| # | File | Why it needs to change |
|---|---|---|
| — | 17 `gametools-core` + 2 `gametools-net` files listed below | Already construct `Alive(...)` directly — straightforward `TestAlive` swap. |
| 20 | `gametools-core`'s `IntentTest.kt` | Calls `mover.issue(...)`/`mover.clearIntent()` on a plain `Actor()` today (`Actor.issue` existed pre-this-plan); needs `Alive`/`TestAlive`, and its `TrackingIntent`'s `issue(actor: Actor)`/`clear(actor: Actor)` overrides narrow to `Alive` (§2.3). Its own class KDoc, currently "Covers `Actor.issue`/`Actor.clearIntent`," updates to `Alive.issue`/`Alive.clearIntent`. |
| 21 | `gametools-core`'s `MoveIntentTest.kt` | Calls `Move(...).issue(mover)`/`.clear(mover)` directly on a plain `Actor()`; needs `Alive`/`TestAlive` purely because `Intent`'s signature narrowed (§2.3) — `Move`'s own logic still only touches `Actor`-level members. |
| 22 | `gametools-core`'s `WorldTickThroughputTest.kt` | Its second test, `"reissuing a Move intent on 10k drifting actors every tick..."`, calls `.issue(Move(...))` on `actors: List<Actor>` drawn from a plain-`Actor` `driftingWorld`. Needs a **new**, separate `driftingAliveWorld(actorCount)` helper (constructing `TestAlive`) for this one test; the file's *other*, unrelated test (the plain 5,000-actor movement-throughput one) is untouched and keeps using plain `Actor`. |
| 23 | `gametools-net`'s `ClientServerRoundTripTest.kt` | `hero` is a plain `Actor(...)` today. It must become an `Alive`/`TestAlive` for **two** independent reasons: `AliveSnapshot.intent` is the only surviving home for the `intent` wire field the test asserts on (§2.2 moves it off `ActorSnapshot`), and `StandardCommandIntentSource` can only be assigned to `Alive.intentSource` at all (§2.4) — without both, `MoveTo`/`Stop` can no longer be sent to `hero`. Assertions change from `assertIs<ActorSnapshot>` to `assertIs<AliveSnapshot>`. |

The 19 already-`Alive`-constructing files, for completeness (unchanged reasoning from the direct
grep — one-line swap or one added line each, same as any earlier revision would have said for
these specific files):

`gametools-core` (17): `AliveAttackLifecycleTest.kt`, `AliveBuffTest.kt`,
`AliveCombatEventsTest.kt`, `AliveCombatTest.kt`, `AliveDeathTest.kt`, `AliveSnapshotTest.kt`,
`AliveTest.kt`, `CapabilityTest.kt`, `DirectionalProjectileTest.kt` (constructs an `Alive` as the
projectile's damage target — the projectile itself is untouched), `DrawableSnapshotTest.kt`,
`GameObjectActiveTest.kt`, `HomingProjectileTest.kt` (same reason as `DirectionalProjectileTest`),
`PlayerTest.kt`, `WorldEntityRegistryTest.kt`, `SeededCombatDeterminismTest.kt`,
`SnapshotRoundTripTest.kt`, `IntentSelfClearIntegrationTest.kt`.

`gametools-net` (2): `StandardCommandApplierTest.kt` (its `actor()` helper is retired in favor of
`alive()`/`TestAlive` everywhere, since all six commands are now `Alive`-only — §2.4, §6),
`GameServerBroadcastTest.kt` (migration-only; unrelated to `Intent`).

**Total: 23 files**, all test code — 20 in `gametools-core`, 3 in `gametools-net`, **0** in
`gametools-world`. This replaces the first revision's 43-file, three-module estimate; the
reduction (and `gametools-world` dropping out entirely) is the direct, expected consequence of
`Actor` no longer changing shape.

No test in this list needs unrelated *assertions* rewritten beyond what §2.2/§2.3/§2.4 already
force (property renames, snapshot-type changes, the one flipped `StandardCommandApplierTest`
case) — the underlying behaviors under test (`destination`, `health`, combat lifecycle, …) are
otherwise unaffected by the concrete type becoming a fixture subclass.

### 5.2 The fixture: a single shared `gametools-core` test-fixtures artifact — now just `TestAlive`

Per the coordinator's decision (§9 Decision 4), `gametools-core` publishes test fixtures via
Gradle `java-test-fixtures`, consumed by `gametools-net`'s test source set. §9 Decision 5 removes
half of what would have been published: **there is no `TestActor`** — only `TestAlive`.
`gametools-world` no longer consumes anything (§5.1) — its own `build.gradle.kts` is untouched.

```kotlin
// gametools-core/build.gradle.kts
plugins {
    id("gametools.published-library")
    id("java-test-fixtures")
}
```

```kotlin
// gametools-core/src/testFixtures/kotlin/com/spartanlabs/gaming/testing/support/TestAlive.kt
/** The minimal concrete [Alive] for tests that need one but exercise no [IntentSource] of their own. */
open class TestAlive(
    location: Point,
    dimensions: Dimensions,
    maxHealth: Double,
) : Alive(location, dimensions, maxHealth) {
    override var intentSource: IntentSource = IntentSource.None
}
```

**Visibility**, unchanged reasoning from the first revision: `TestAlive` is `open`/public, not
`internal` — a Gradle `testFixtures` source set compiles as its own Kotlin module, distinct from
a *different* Gradle project's `test` source set, so `internal` would not be visible across that
project boundary.

```kotlin
// gametools-net/build.gradle.kts — gains:
dependencies {
    testImplementation(testFixtures(project(":gametools-core")))
}
```

No change to the shared `gametools.kotlin-library`/`gametools.published-library` convention
plugins. Test fixtures are **not** part of any module's published Maven artifact.

`TrackingAlive` (`AliveAttackLifecycleTest.kt`) is left as its own private class — it has bespoke
hook overrides `TestAlive` does not need — and simply gains the required `intentSource` line, or
is changed to extend the now-public `TestAlive` for consistency; either compiles and behaves
identically.

### 5.3 Existing classes evaluated for adoption, not just breakage

| Class | Adopts `IntentSource` now? | Why |
|---|---|---|
| `Actor` (all non-`Alive` uses) | N/A — `IntentSource` does not reach `Actor` at all | Confirmed: no non-`Alive` `Actor` family has any use for it (§1.1, §2.1). |
| `Projectile` (+ `DirectionalProjectile`, `HomingProjectile`) | No — never gets an `intentSource` at all, not even a defaulted one (§2.2.1) | Ballistic, not order-driven; nothing to decide, and not even eligible now that the property lives only on `Alive`. |
| `Player` | N/A | Does not extend `Actor`. |
| `TrackingAlive` (test double) | No — `IntentSource.None` | Exists to record attack-lifecycle hook calls; adding AI would be an unrelated behavior change to an existing, narrowly-scoped test fixture. |
| The network path (`StandardCommandApplier`) | **Yes — this plan's core scope (§2.4)** | Explicitly named in the brief. |
| Single-player input | Investigated, **deferred** (§2.7) | No existing mechanism to rework; premature ahead of Phase 3's action-map design. |
| A new `Monster` | **Yes — this plan's proof of concept (§2.5), shipped from a bootstrapped `gametools-ai`** | Explicitly named in the brief. |
| Anything in `gametools-world` | No | Zone/map domain objects; none extend `Actor`/`Alive`; no code there touches `Intent` at all (§1.1). |

No existing class is silently left out without an explicit determination.

---

## 6. Test plan (5-level hierarchy)

All new/modified tests use `com.spartanlabs.gaming.testing.<level>`, mirroring the production
package, one class per file, per `.aiassistant/rules/CLAUDE.md` / `CONTRIBUTING.md`.

### Level 2 — component (`gametools-core/.../testing/component/gameobjects/`)

**New — `IntentSourceTest.kt`**
- `decide()` returning `null` leaves `alive.intent` unchanged and issues no `GameEvent`.
- `decide()` returning an `Intent` is issued via the tick's `Alive.onUpdate` poll — assert
  `alive.intent` changes and `GameEvent.IntentIssued` fires, without calling `issue` directly.
- **The dedup-cost demonstration**: a naive source that returns the *same* `Move` every tick
  causes `GameEvent.IntentCleared` **and** `IntentIssued` to fire every tick it runs (not just
  once).
- `IntentSource.None.decide(anyAlive)` always returns `null`.
- (`IntentSourceWidenTest.kt` from the first revision is **removed entirely** — there is no
  `widen()` anymore, §9 Decision 5.)

**New — `gametools-ai`'s `testing/component/ai/MonsterWanderTest.kt`** (new package, this
module's first tests)
- A fresh `Monster` (no external command) ticks itself into a `Move` intent whose destination
  falls within `homeRadius` of `home` — seeded `World`/`RandomSource` for determinism.
- Once at its destination, the next tick issues a *new* `Move` to a different point.
- While mid-`Move` and not yet arrived, repeated ticks propose nothing — no event spam.

**Modified — `IntentTest.kt`** (§5.1, item 20)
- Construction swaps to `TestAlive`; `TrackingIntent`'s overrides narrow to `Alive`; class KDoc
  and the two `GameEvent.IntentIssued/Cleared` assertions rename `.actor` → `.alive` (§2.3). Test
  *behavior* coverage (order of `clear`/`issue`, `Idle` default, no short-circuit on repeated
  intent types) is unchanged.

**Modified — `MoveIntentTest.kt`** (§5.1, item 21)
- Construction swaps to `TestAlive`. No coverage change — `Move.issue`/`clear`'s own behavior is
  identical, only reachable through a different receiver type now.

**Modified — `AliveAttackLifecycleTest.kt`**
- `TrackingAlive` gains its required `intentSource` assignment. No behavioral test changes.

### Level 3 — integration (`gametools-core/.../testing/integration/gameobjects/`)

**New — `IntentSourcePollingIntegrationTest.kt`**
- A real `World`, an `Alive` with a custom `IntentSource` wired in: driving the world via
  `world.tick()` (not calling `decide`/`issue` directly) visibly changes the unit's movement —
  proves the framework's own tick loop is what calls the poll. Placed alongside the existing
  `IntentSelfClearIntegrationTest.kt`.

### Level 4a — deterministic (`gametools-net/.../testing/deterministic/networking/command/`)

**Heavily modified — `StandardCommandApplierTest.kt`**
- Every existing assertion that checked `alive.intent`/`destination`/`movement` immediately
  after `applyTo` now needs a `world.tick()` first (the one-tick-latency change, §2.4).
- Its `actor()` helper — used only by the four previously-Actor-scoped commands' tests — is
  **retired**; every test uses `alive()`/`TestAlive` now.
- **Flipped test**: `"a movement command on a plain Actor applies without incident"` becomes
  `"a movement command naming a plain (non-Alive) Actor is WrongType"` — the direct,
  explicitly-called-out consequence of §2.4's new constraint, asserting `ApplyResult.WrongType(id,
  Alive::class)` where the old test asserted success.
- New: `applyTo` on an `Alive` whose `intentSource` is not a `StandardCommandIntentSource` (e.g.
  a `Monster`) returns `ApplyResult.NotNetworkControlled`.
- New: two commands enqueued for the same unit before a single `world.tick()` collapse to only
  the second one taking effect (the "drain to latest" contract).
- New: `MoveDir`'s `angle` assignment is still visible *before* the next tick (applied
  immediately, unlike the `Move` intent itself).

### Level 4b — e2e (`gametools-net/.../testing/e2e/`)

**Modified — `ClientServerRoundTripTest.kt`** (§5.1, item 23)
- `hero` becomes a `TestAlive` (from `gametools-core`'s `testFixtures`) with a
  `StandardCommandIntentSource` assigned, replacing the plain `Actor(...)`.
- Assertions switch from `assertIs<ActorSnapshot>` to `assertIs<AliveSnapshot>` when reading
  `.intent` off the decoded broadcast.
- The full client → server → `applyTo` → tick → broadcast flow still gains the one extra
  `world.tick()` needed for a `COMMAND`'s effect to reach the next `STATE` broadcast.

**Migration-only — `GameServerBroadcastTest.kt`**: fixture swap only.

### Level 4c — nonfunctional (`gametools-core/.../testing/nonfunctional/`)

**Modified — `WorldTickThroughputTest.kt`** (§5.1, item 22)
- New `driftingAliveWorld(actorCount)` helper (constructs `TestAlive`, each with
  `IntentSource.None` by default so the plain-movement path is unaffected) feeds the existing
  `"reissuing a Move intent on 10k drifting actors..."` test, replacing its plain-`Actor`
  `driftingWorld` — this is what makes `.issue(Move(...))` compile again now that it lives on
  `Alive`. The 5,000-actor plain-movement-throughput test is untouched.

### Level 5 — UAT

Not automatable: whether `Monster`'s wander motion "reads" as natural is a design-review
judgment call for whoever builds on this next (the eventual `Creep` and `gametools-ai`'s
`NavProvider`-driven archetypes).

---

## 7. Risks & edge cases

| Risk | Mitigation / note |
|---|---|
| **Breaking change**: `Alive(...)` no longer compiles anywhere, including in any downstream consumer | Deliberate, user-confirmed. Version-*tag* timing is batched with Phase 2 (§8, §9 Decision 2), but the compile-time break itself lands with Stage 1 regardless. `Actor`/`Projectile` are entirely unaffected — a smaller blast radius than the first revision's `Actor`-abstract design. |
| **New**: all six standard commands, not only `Attack`/`StopAttack`, now require the resolved object to be an `Alive` — a plain `Actor` can never again be network-commanded | This is a real regression against today's tested, currently-shipping-but-unreleased behavior (`StandardCommandApplierTest`'s `"a movement command on a plain Actor applies without incident"`), not an already-true constraint being merely documented. It is a direct, unavoidable consequence of §9 Decision 5 (confirmed architecture), with no dual-path alternative that avoids the "some commands instant, others deferred" inconsistency this design already rejected. Called out here, in §2.4, and covered by a flipped test (§6, L4a) rather than silently absorbed. |
| A non-deduplicating `IntentSource` spams `GameEvent.IntentIssued`/`IntentCleared` | Documented prominently in `IntentSource`'s own KDoc (§2.1) and directly demonstrated by a dedicated test (§6, L2). |
| Network commands now take one tick to apply instead of applying instantly | Deliberate consequence of making the network path a real `IntentSource` (§2.4); tested explicitly (§6, L4a/L4b). |
| An `Alive` addressed by a network command has no `StandardCommandIntentSource` assigned | New `ApplyResult.NotNetworkControlled` reports it rather than silently dropping or throwing. |
| `gametools-ai` temporarily depends on `gametools-core` instead of the roadmap's stated `world` + `combat` | Explicit, documented deviation in both `gametools-ai/build.gradle.kts`'s header comment and the roadmap doc itself (§2.5, §2.6). Re-pointed at Phase 2 (§11 follow-up). |
| `TestAlive` is `public`, not `internal`, so `testFixtures` can cross the `gametools-core`/`gametools-net` module boundary | Documented in §5.2 as a required, low-risk consequence of Kotlin's per-compiler-module `internal` visibility — this fixture carries no state or invariant worth hiding. |
| Reverting `Actor.world`/`ActorSnapshot.intent` and moving `Alive.intent`/`issue`/`clearIntent` undoes pieces of the already-landed #42 work | Zero consumer-facing cost — none of it has ever released (§ Header baseline). `docs/issue-42-actor-intent-plan.md`/`docs/actor-intent-orders-plan.md` get a short superseded-before-release note each (§2.8) so the historical record stays honest. |
| Wire compatibility | **No wire/schema change at all** beyond `intent` moving from `ActorSnapshot` to `AliveSnapshot` — itself pre-release, so no real client has ever decoded it from the old location. `ClientCommand`'s `@Serializable` shapes are untouched. |
| Cross-repo impact | See below. |

### Cross-repo impact

- **MyGameServer** (consumer, `SpartanLabsGaming/MyGameServer`): near-certain to directly
  subclass or construct `Alive` for its own game-specific units — a **required, coordinated
  update** on their side (add an `intentSource` assignment to every such subtype;
  `IntentSource.None` reproduces today's exact behavior with zero functional change). Any
  non-`Alive` `Actor` subtype they have is **entirely unaffected** — a smaller footprint than
  the first revision would have required. Per standing guidance, no issue is filed against a
  downstream consumer; the eventual Major-version bump and `CHANGELOG.md`'s `BREAKING CHANGE`
  entries are this repo's existing mechanism for notifying consumers.
- **The separate client project**: unaffected — no wire/schema change beyond an already-unreleased
  field's location.
- **GameGraphics**: client-side rendering only — unaffected.
- **WebTools**: no transport-layer involvement — unaffected.

---

## 8. Versioning (`CONTRIBUTING.md`)

Unchanged from the first revision's conclusion and §9 Decision 2's resolution: `Alive` going
from `open class` to `abstract class` is unambiguously a **Major release** on its own terms;
implementation proceeds now (Stages 1–3, §10, land on `master`, which stays green throughout),
but the version-***tag*** cut is deferred until Phase 2's `Alive` → `gametools-combat` migration
also lands, shipping both breaking changes in one Major bump. None of this plan's module
`coordinates(...)` literals change in Stages 1–3.

### `CHANGELOG.md` — amend the existing `#42` entries, don't layer a second description on top

Since `#42`'s `Actor.intent`/`Actor.issue`/`Actor.world` entries are still sitting **unreleased**
in `[Unreleased]`, and nothing has ever shipped to a consumer, there is no reason to document a
"placed here, then immediately corrected" sequence — that would only confuse a reader of the
eventual release notes. **Recommendation, applied here: amend the existing entries in place** to
already describe the shipped (`Alive`-level) shape.

**Amend this existing `Added` bullet** (currently describing `Actor.intent`/`Actor.issue`/
`Actor.clearIntent`/`ActorSnapshot.intent`) to read:

```md
- `Alive.intent` — a unit's current standing order (`Idle` by default), with `Alive.issue(Intent)`
  and `Alive.clearIntent()`. Issuing a new intent always tears down the previous one first, so
  orders are mutually exclusive by construction. GameTools ships `Idle`, `Move`, and
  `AttackIntent`; a consumer adds its own the same way it adds a `ClientCommand`.
  `GameEvent.IntentIssued` / `IntentCleared` report every change on the world bus.
  `AliveSnapshot` gains a read-only `intent` string tag (`"idle"` / `"move"` / `"attack"`).
  (#42)
```

**Delete this existing `Changed` bullet entirely** (it describes `world`'s promotion to `Actor`,
which this plan undoes before release — see §2.2):

```md
- `Alive.world` is now declared on `Actor` (inherited by `Alive`, source-compatible) so any
  `Actor` can publish through the world event bus, not only an `Alive`. (#42)
```

The other existing `#42` `Changed` bullet (`"The six standard commands now apply by issuing the
corresponding Intent..."`) needs **no edit** — it accurately describes the Intent-routing
mechanism as it stands today, independent of *where* `Intent` lives; this plan's own new entry
(below) layers the further, genuinely new change (deferred queueing, and the Alive-only
constraint) on top of it, which is a different axis of change, not a restatement.

**New entries this plan adds** (unaffected in substance by §9 Decision 5, beyond wording):

```md
### Added
- `IntentSource` — a pluggable strategy that decides an `Alive`'s next `Intent`, polled once per
  tick from `Alive.onUpdate` and funnelled through the existing `Alive.issue`. `IntentSource.None`
  is the no-op default.
- `gametools-net`'s `StandardCommandIntentSource` — the network `IntentSource` implementation;
  assign one to a unit's `intentSource` to make it player-commandable.
- `gametools-ai` — a new module, bootstrapped for `Monster` and `WanderIntentSource`, a
  wandering `Alive` proof-of-concept; depends on `gametools-core` directly for now, pending
  Phase 2 moving `Alive` to `gametools-combat`.

### Changed
- **BREAKING:** `Alive` is now `abstract class`; direct `Alive(...)` construction no longer
  compiles. Every concrete subtype must assign `intentSource` (use `IntentSource.None` to
  reproduce prior behavior exactly). `Actor` is unaffected — it stays concretely instantiable.
- **BREAKING:** `ClientCommand.applyTo`'s six standard commands now queue their `Intent` onto
  the addressed unit's `StandardCommandIntentSource`, taking effect on that unit's *next* tick
  rather than instantly; every command's operand must now resolve to an `Alive` (previously only
  `Attack`/`StopAttack` required one — `MoveTo`/`MoveDir`/`Follow`/`Stop` could target any
  `Actor`). A unit without a `StandardCommandIntentSource` assigned returns the new
  `ApplyResult.NotNetworkControlled`.
```

---

## 9. Decisions

The plan's first draft raised four open decisions; the first revision resolved all four (rows
1–4 below, unchanged from that revision). This second revision adds a fifth, sourced from a
further architectural change the user confirmed after the coordinator's own recommendation —
not one of the original four options, but the biggest single design change across both
revisions.

| # | Decision | Planner's original recommendation | **Resolution** |
|---|---|---|---|
| 1 | File a GitHub issue first? | File one before branching. | **Yes — matches the recommendation.** The coordinator files it; Planner does not. |
| 2 | Ship standalone now, or batch the Major release with Phase 2's combat reshape? | Ship standalone now. | **Batch with Phase 2 — against the recommendation.** Implementation proceeds now; the version-*tag* cut is deferred until Phase 2 also lands. `CHANGELOG.md` entries are written now regardless (§8). |
| 3 | `Monster`'s home: `gametools-core`, or bootstrap `gametools-ai` now? | Stay in `gametools-core`. | **Bootstrap `gametools-ai` now, empty, and put `Monster`/`WanderIntentSource` there — against the recommendation.** Full module-bootstrap design in §2.5/§3, including the temporary `gametools-core`-not-`world`+`combat` dependency deviation. |
| 4 | Accept `TestActor`/`TestAlive` duplication, or invest in `java-test-fixtures`? | Accept the duplication. | **Add `java-test-fixtures` now — against the recommendation.** (Narrowed by Decision 5: only `TestAlive` exists at all now, §5.2.) |
| 5 | **New this revision:** should `Intent`/`IntentSource` live on `Actor` (as the plan originally designed, generic over the actor type) or move down to `Alive` entirely, non-generic? | Not originally a decision this plan raised — the first draft's own design put it on `Actor`. | **Moved to `Alive`, non-generic — confirmed by the user after the coordinator's recommendation.** `Movement`/`destination`/`speed`/`move()` stay on `Actor`, unchanged. This eliminates the first revision's entire §2.2 (contravariance/`widen()`), removes `Actor`'s abstract-class change and `Projectile.kt`'s one-line edit, requires moving `Actor.world` back to `Alive` too (investigated and resolved in §2.2), narrows `Intent`'s and `GameEvent.IntentIssued`/`IntentCleared`'s signatures (§2.3), and makes all six standard commands `Alive`-only (§2.4, §7 — a real, called-out behavior regression versus today's tested `Actor`-scoped movement commands). |

---

## 10. Version control

- **Branch(es):** the coordinator's issue does not exist yet at the time of this revision;
  branch as `feature/<issue#>-intent-source` (and the two follow-on slugs below) once it does.
- **Stage 1 — the abstraction + the `Alive`-abstract migration (must land atomically; `master`
  must always compile):**
  1. `feat(gameobjects): add IntentSource and None` — `IntentSource.kt`.
  2. `refactor(gameobjects): move Intent, issue, clearIntent, and world down from Actor to Alive`
     — `Actor.kt` (reverts), `Alive.kt` (gains `intent`/`issue`/`clearIntent`/`world`, not yet
     `abstract`), `Intent.kt` (signatures narrow to `Alive`), `GameEvent.kt` (`IntentIssued`/
     `IntentCleared` narrow to `Alive`), `World.kt` (`add()`'s type check reverts to `Alive`),
     `Actor.kt`/`Alive.kt` (`ActorSnapshot` loses, `AliveSnapshot` gains, the `intent` field).
     No `BREAKING CHANGE` footer needed on this commit specifically — nothing here has ever
     released (§2.2, §7), so there is no real compatibility break yet, only code motion.
  3. `feat(gameobjects)!: make Alive abstract; require intentSource` — `abstract class Alive`,
     `abstract var intentSource`; `BREAKING CHANGE:` footer — this is the commit with the real,
     consumer-relevant break. Includes this plan doc in the same commit.
  4. `build: publish gametools-core's TestAlive test fixture via java-test-fixtures` —
     `gametools-core/build.gradle.kts` (`id("java-test-fixtures")`) + the new
     `src/testFixtures/kotlin/.../TestAlive.kt`; `gametools-net/build.gradle.kts` gains
     `testImplementation(testFixtures(project(":gametools-core")))`. Lands before the next
     commit, which depends on this fixture existing.
  5. `test: migrate Alive test construction onto TestAlive, and update Intent/Move/WorldTickThroughput` —
     the 23-file migration (§5.1), including `IntentTest.kt`/`MoveIntentTest.kt`'s switch to
     `Alive` and `WorldTickThroughputTest.kt`'s new `driftingAliveWorld` helper.
  6. `docs: update README, CONTRIBUTING, CHANGELOG (amend the #42 entries), the roadmap
     Creep→Monster correction, and the #42-superseded notes in its two plan docs` — §2.6, §2.8,
     §3 Docs, §8.
- **Stage 2 — the network rework (lands after Stage 1 merges):**
  1. `feat(networking)!: rework StandardCommandApplier as StandardCommandIntentSource` —
     `StandardCommandIntentSource.kt`, `StandardCommandApplier.kt` (single `onNetworkedAlive`
     helper for all six commands), `ClientCommand.kt` KDoc, `ApplyResult.NotNetworkControlled`;
     `BREAKING CHANGE:` footer — both for the timing change and for the new "every command is
     `Alive`-only" constraint (§2.4, §7).
  2. `test: cover StandardCommandIntentSource's queue-and-drain, flip the plain-Actor test, and
     update the round trip` — `StandardCommandApplierTest.kt` (including the flipped
     "plain Actor is WrongType" case), `ClientServerRoundTripTest.kt` (`hero` becomes `TestAlive`
     — depends on both this stage's `StandardCommandIntentSource` and Stage 1's `AliveSnapshot`
     changes), `GameServerBroadcastTest.kt`.
- **Stage 3 — bootstrap `gametools-ai` and land `Monster` (lands after Stage 1; independent of
  Stage 2):**
  1. `feat(build): bootstrap the gametools-ai module` — `settings.gradle.kts`,
     `gametools-ai/build.gradle.kts`, the root `build.gradle.kts` Dokka wiring,
     `gametools/build.gradle.kts` `api`/sources/Dokka wiring, `README.md`/`CONTRIBUTING.md`
     module-table rows, `CHANGELOG.md` entry — mirrors `d096847` exactly (§2.5, §3).
  2. `feat(ai): add Monster and WanderIntentSource` — `Monster.kt`.
  3. `test: cover Monster's wander behavior` — `MonsterWanderTest.kt` (`gametools-ai`).
- Each commit body references `Refs #<issue>`; the PR(s) close it (`Closes #<issue>`).
  Conventional Commits, PR-per-change, CI green, semi-linear merge (`--no-ff`), rebase-only
  locally — all per `CONTRIBUTING.md`, unchanged by this plan.
- This is not a `release/*` branch; per §8/§9 Decision 2, no release branch is cut for this
  plan's changes alone.

---

## 11. Sequencing & follow-ups

1. Coordinator files the GitHub issue (§9 Decision 1); branch names in §10 get their real
   number.
2. Land Stage 1 (§10) — the only strictly load-bearing prerequisite for everything else here.
3. Land Stage 2 (network rework) and Stage 3 (bootstrap `gametools-ai` + `Monster`) in either
   order, or in parallel PRs, once Stage 1 is on `master`.
4. **Do not cut a release for this plan's changes alone** (§8/§9 Decision 2) — they sit in
   `master`'s `[Unreleased]` alongside whatever else lands, including Phase 2, until both are
   ready and a single Major release is cut covering both.
5. Once Phase 2 moves `Alive` to `gametools-combat`, re-point `gametools-ai/build.gradle.kts`'s
   dependency from `gametools-core` to `gametools-world` + `gametools-combat`, per the roadmap's
   original target graph (§2.5) — Phase 2's responsibility, not a standalone task.
6. **Deliberately deferred:** a real single-player input-mapping `IntentSource` (§2.7), to
   follow Phase 3's action-map input design; the `Creep` MOBA archetype itself (Phase 4,
   corrected scope per §2.6) — only its home module (`gametools-ai`) and `Monster`'s presence in
   it are pulled forward, not `Creep` itself.
7. **New follow-up from this revision:** if a future non-`Alive` `Actor` family ever needs a
   standing order of its own, revisit `IntentSource`'s non-generic shape (§2.1) — regenericizing
   it later is a source-compatible addition for every existing `Alive`-based caller, not a
   breaking one, so nothing here should be read as permanently foreclosing that.
