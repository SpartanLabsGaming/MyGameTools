# API Openness Decisions — closed surfaces reviewed for `6.0.0`

> **Note — 2026-09-22, World Systems Implementation (issues #76–#80).** Three rulings below are
> touched by that work; none is reversed.
>
> - **D1 / D3.** The tier annotations these rulings assume now have a concrete home, created by #76
>   in `com.spartanlabs.gaming.annotation`: `@SupportedExtension` (parameterless; D1's `Movement`
>   applies it in `6.0.0`), and the library-wide Experimental marker `@ExperimentalGameToolsApi`
>   (`@RequiresOptIn(level = ERROR)`), the marker D3's "Experimental (`@RequiresOptIn`)" tier would
>   use.
> - **D4.** Its compositional answer to "run my own systems each frame" named Phase 1's
>   `WorldSystems.step()`, which was never built. The need is now met by `World`'s own opt-in
>   registry (`installSystem`/`stepSystems`, #76). That is composition, not subclassing, so D4's
>   ruling to keep `World` `final` is unaffected, if anything reinforced.
>
> Architecture: `docs/world-systems-implementation-architecture.md`.

## Header / Association

- **Covers:** a per-surface ruling on each already-closed (`sealed` / `final`) public type in
  `gametools-core`, against the global "Library Design — Open-Ended by Default" standard in
  `~/.claude/CLAUDE.md`.
- **Instruction:** Spartak Singh via a Claude Code session on 2026-09-19 — the library-design
  standard was stated globally, then, asked whether it applied retroactively: *"Consider the
  pros and cons of making the design choice. How much benefit does opening provide to the
  consumer relative to the stability of keeping them closed. Make a decision for each one.
  Wait until 6.0.0 to make the changes you're decided on."*
- **Status:** decisions only. No source, test or build file has been modified by this document.
  Nothing here ships before the Phase 2 Major, `6.0.0`.
- **Baseline:** `master` at `56ae9bc`. Verified against the working tree on 2026-09-19.
- **Mechanism rule applied:** domain / entity types extend by **inheritance**; systems and
  infrastructure extend by **interface + supplied default implementation**.
- **Related:** `docs/framework-vision-and-roadmap.md` (Phase 2 is the Major that absorbs
  breaking changes); `docs/phase-1-map-and-space-plan.md` §9 Open Decision 4 (the `Movement`
  delta refactor, already deferred to `6.0.0` — see D1 below).

---

## Summary

| # | Surface | Today | Ruling | Confidence |
|---|---------|-------|--------|-----------|
| D1 | `Movement` | `sealed class` | **Open** — become an `interface` | High |
| D2 | `Player` | `final class` | **Open** — become `open class` | High |
| D3 | `EventBus` | `final class` | **Open by interface extraction**, not subclassing | Medium |
| D4 | `World` | `final class` | **Keep closed** | High |
| D5 | `SimulationLoop` | `final class` | **Keep closed** | High |
| D6 | `MapLoader` | `object` | **Keep as-is** — but parameterise its decode strictness | High |

Two surfaces were checked and need no action: `GameEvent` is already a plain `interface`
(Phase 1 Open Decision 5 resolved this), and `CoreCapability` is an `enum` implementing the
open `Capability` interface, so a consumer can already define their own capabilities.

---

## D1 — `Movement`: open it

**Today.** `sealed class Movement` with four `data object` / `data class` strategies. Its
`step(actor: Actor): Result<Unit>` is `internal abstract`, and the helpers a strategy needs —
`Actor.stepTowardsDestination()`, `Actor.stepAlongAngle()`, `Actor.hasSettled` — are all
`internal` too.

**Benefit of opening: high.** This is a textbook Strategy pattern that is sealed shut. A
consumer cannot write patrol, flee, wander, waypoint-follow, formation-keeping or steering
behaviour without forking the module. For an RTS/MOBA framework that is close to the single
most likely thing a game wants to customise.

**Cost of keeping it closed: low.** `sealed` buys a contract only where an exhaustive `when`
is part of the API. There is no such `when` anywhere in production code across all three
modules — the only match is a test asserting `shot.movement is Movement.Homing`. So the seal
is protecting nothing.

**Ruling: open.** `sealed class Movement` → `interface Movement`; `step` becomes public API;
`Targeting` / `Persistent` / `Directional` / `Homing` stay as shipped default implementations.
The `internal` helpers on `Actor` must be published alongside it, or the open interface is
useless — that is the bulk of the work, not the `sealed` keyword.

**Sequencing.** Phase 1 Open Decision 4 already defers a `Movement.step`-returns-a-delta
refactor to `6.0.0` so physics can reconcile proposed motion. That touches the same signature
in the same release — do both as one coordinated change so `step`'s shape breaks exactly once.
The physics work (#49) should therefore be planned knowing this is coming.

**Tier: Supported Extension** (`@SupportedExtension`). Custom movement strategies are the
textbook likely-but-non-core need, and `Targeting` / `Persistent` / `Directional` / `Homing`
become the worked examples of the seam — exactly what the tier is for. Same semver guarantee as
the stable core.

---

## D2 — `Player`: open it

**Today.** `final class Player(val name: String)` holding a roster of `Alive`.

**Benefit of opening: medium-high.** `Player` carries a name and a roster and nothing else.
Essentially every real game needs per-player state hung off it — resources, tech/upgrade
state, score, team, colour, a connection handle. Today a consumer must wrap it and maintain a
side map from `Player` to their own data, which is pure friction for a domain/entity type.

**Cost of opening: low.** The one invariant is that the roster stays in step with
`Alive.owner`, and it is maintained by `addToRoster` / `removeFromRoster`, which are already
`internal` — a subclass outside the module cannot reach them. Opening the class exposes no way
to corrupt it.

**Ruling: open.** `open class Player`. Keep `own` / `disown` **final**, since they own the
invariant; keep the roster mutators `internal`. A subclass adds state and behaviour only. This
is a domain/entity type, so inheritance is the right mechanism — consistent with `Actor`,
`Alive`, `Buff` and `Projectile` already being open.

**Note.** If the real need turns out to be *reacting* to ownership changes rather than storing
state, the better answer is an ownership `GameEvent` rather than overridable `own`/`disown`.
Worth adding at the same time.

**Tier: Stable Core.** `Player` is core domain functionality, and opening it adds no separate
contract — a subclass only adds state on top of an invariant the base still owns. There is no
distinct seam here to mark as an extension.

---

## D3 — `EventBus`: open by interface extraction

**Benefit of opening: medium.** Plausible consumer implementations are a recording/replay bus,
a metered bus, and a per-player scoped bus for Phase 3 interest filtering. Deterministic replay
is an explicit goal of this framework — the seeded `World.rng` exists for it — so this is not
purely hypothetical. But no consumer has asked yet.

**Cost of opening by subclassing: high.** `publish` maintains a documented re-entrancy
guarantee: a listener that publishes during delivery gets queued rather than recursing, which
is what keeps event ordering deterministic and the stack flat. An overridable `publish` lets a
subclass silently destroy exactly the property the netcode and the determinism tests rely on.

**Ruling: open by interface extraction, not subclassing** — the layer rule says infrastructure
extends by substitution. Extract a minimal interface (`subscribe`, `publish`), keep the current
class as the shipped default implementation retaining the re-entrancy guarantee, and let
`World` take one by constructor rather than always constructing its own. The guarantee then
lives in the default impl and a substituting consumer knowingly takes responsibility for it.

**Confidence: medium — re-verify at `6.0.0`.** This is the one ruling here driven by the
project's stated direction rather than a consumer request. If nothing has materialised by then,
it is legitimate to defer it rather than add surface speculatively.

**Tier: Experimental** (`@RequiresOptIn`) until a real consumer has built against it. It is not
Supported Extension: the need is inferred from this project's own direction rather than
demonstrated, which is the distinction between the two tiers.

---

## D4 — `World`: keep closed

**Benefit of opening: low.** The only plausible override is `tick()`, to add a per-frame phase.
That need is already met properly by composition and met better: `spatialIndex` and `space` are
pluggable ports, `events` gives an observation hook, and Phase 1's `WorldSystems.step()` is
purpose-built for "run my own systems each frame."

**Cost of opening: high.** `World` owns the `EntityId` allocation, the `byId` index, the
`announced` set, the spatial-index reconcile and the removal drain, with a documented tick
order. A subclass overriding `tick()` or `add()` breaks those invariants silently. This is the
fragile-base-class case in its purest form, and `World` is the one type every consumer and both
other modules touch.

**Ruling: keep `final`.** Extension stays compositional. If a consumer genuinely needs a
different container, the answer is the `GameWorld` wrapper already contemplated for Phase 5
(`docs/phase-1-map-and-space-plan.md` §1.2, Option B) — not subclassing `World`.

No interface is extracted either: nothing has asked to substitute the container, and the
compositional ports already cover the need that would motivate one.

---

## D5 — `SimulationLoop`: keep closed

**Benefit of opening: low — it is already open-ended by composition.** The class is explicitly
opt-in and its KDoc says so: `World.tick()` stays directly callable, and the public
`advance(realElapsedNanos)` lets a caller drive the timestep from their own loop without using
the thread at all. A consumer wanting different pacing writes their own driver and loses
nothing. That is a better extension mechanism than subclassing, and it already exists.

**Cost of opening: medium.** Thread lifecycle, the `@Volatile thread` handle and the catch-up
accumulator are easy to corrupt from a subclass, for no gain over the escape hatch above.

**Ruling: keep `final`.** No change. If pacing policy needs to vary further, widen
`LoopSettings` or the `onTick` callback — that is parameterising policy, which the standard
already prefers, and it is not a closed-surface problem.

---

## D6 — `MapLoader`: keep as-is, but parameterise decode strictness

**Reviewed at Spartak's instruction on 2026-09-19**, ahead of the other `-net` / `-world`
surfaces, because an earlier claim in this session — that `MapLoader` being an `object` blocks
the Tiled `.tmx` importer contemplated in `phase-1-map-and-space-plan.md` §9 Open Decision 1 —
needed checking. **That claim was wrong.** `fromDefinition(MapDefinition): Result<TiledMap>`
is public, and `MapDefinition` is the extension seam: a `.tmx` importer produces a
`MapDefinition` and hands it to `fromDefinition`. Nothing about the format pipeline is closed.

**Benefit of opening: low.** `MapLoader` is stateless and pure, and the format seam already
exists one level up. Making it substitutable would mainly serve consumer test doubles, which
have little to gain from faking a deterministic pure function.

**Ruling: keep it an `object`.** No `6.0.0` change.

**But it hardcodes a policy a consumer may reasonably disagree with**, which the standard says
to parameterise. `private val json = Json { ignoreUnknownKeys = true }` is fixed, and the
class's own KDoc flags the consequence as a gotcha: *"a JSON key that is not one of
[MapDefinition]'s properties — including a typo'd one — is silently dropped rather than
rejected; it does not surface as a [Result.failure]."* A consumer shipping authored map content
would very likely prefer a typo to fail loudly, and today has no way to ask for that.

**Proposed fix (additive, no major needed):** give `fromJson` an optional strictness parameter —
default lenient so existing behaviour is unchanged, with a strict mode that surfaces unknown
keys as `Result.failure`. This is parameterising policy, not opening a closed surface, so it
can ship in any feature release rather than waiting for `6.0.0`.

---

## Follow-up

- **A wider openness review is still due.** This doc covers `gametools-core`'s five closed
  surfaces plus D6. **Not yet reviewed:** `GameServer`, `ClientCommandCodec` and `ApplyResult`
  (`gametools-net`); `TiledMap`, `TerrainLayer`, `StaticGeometry`, `ZoneGrid` and `ZoneIndex`
  (`gametools-world`). Spartak deferred these on 2026-09-19 and asked to be reminded that the
  review is owed — do it before `6.0.0` is planned.
- D1–D3 land in `6.0.0` only. D6's strictness parameter is additive and needs no major.
- D1's `Movement` change must be coordinated with Phase 1 Open Decision 4 (the delta refactor).
  Whoever plans issue #49 (physics) should read D1 first.
- D3 is to be re-verified at `6.0.0` rather than treated as settled.
- The `6.0.0` release notes should list D1–D3 under `### Changed` with migration notes, and
  state which seams ship experimental.
