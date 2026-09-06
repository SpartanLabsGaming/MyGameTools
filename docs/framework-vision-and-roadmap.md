# GameTools — Target State & Phased Roadmap

## Header / Association

- **Covers:** A scoping interview with Spartak Singh, conducted via a Claude Code session on
  2026-09-05, on *"what the final state of this project should look like."* GameTools is a
  reusable framework for the common concepts behind the online games Spartak intends to build.
- **Status:** direction document only. No source, test, or build file has been modified. Each
  phase below is to be planned in its own `docs/` plan document before implementation, in the
  style of `docs/webtools-2.0.0c-upgrade-plan.md`.
- **Current baseline:** GameTools `3.0.0` — server-side core: `GameObject → VisibleObject →
  Actor → Alive` hierarchy, `ModularStat`/`CombinedStat`/`StatMod`, `Buff`/`Capability`,
  point-region `Quadtree`, `World` (external tick), and a UDP `GameServer` on WebTools 2.0.0c
  that broadcasts full-state JSON snapshots to every player.
- **Open GitHub issues at writing time:** #1 `Alive.cancelAttack()`, #2 `Alive` keeps
  attacking a dead/removed target, #3 broadcast snapshots have no stable identity. All three
  are folded into the roadmap below (#1/#2 in Phase 0, #3's root cause in Phase 0, its wire
  half in Phase 3).

---

## 1. Scope decisions (from the interview)

| # | Question | Decision |
|---|---|---|
| 1 | Client / UI | **Out of scope.** A separate project owns the client and rendering. GameTools ships the authoritative server plus the shared wire-protocol types both ends compile against. |
| 2 | Concurrent games / sessions | **One `World` per server process.** No lobby, matchmaking, or room manager. Running many games means running many processes — that is deployment's job. |
| 3 | Networking ambition | **Scalable authoritative.** Stable entity IDs, per-tick delta snapshots, per-player interest filtering, tick + input sequence numbers, server-authoritative movement. |
| 4 | Gameplay systems in the framework | **All four:** abilities & effects, inventory & items/equipment, AI & pathfinding, physics & collision response. |
| 5 | Input model | **Layered.** Keep the low-level datagram→typed-event routing; add an *optional* authoritative input-application layer on top. Input vocabulary is a **generic action map** — named, bindable actions with analog + digital values; mouse and keyboard are just sources. |
| 6 | Tick loop | **Framework provides an authoritative fixed-timestep loop** whose settings (tick rate, catch-up bounds) are changeable externally at runtime. Manual `World.tick()` stays supported. *(Supersedes the earlier "GameTools never runs its own loop" note — that constraint is now opt-out, not absolute.)* |
| 7 | Identity & persistence | **Define the seams, ship minimal implementations.** `AuthProvider` SPI (default reproduces today's `Iam <name>`), `PlayerId` value type, `SessionRegistry` with a reconnect grace window, persistence as **ports** (`PlayerStore`, `SnapshotStore`) with in-memory implementations only. No bundled accounts or database. |
| 8 | Target genres | **RTS / MOBA / tower-defense** and **MMO-lite / persistent world.** |
| 9 | Wire format | **Pluggable codec.** A `SnapshotCodec` seam with a JSON implementation (handshake + debugging) and a compact binary implementation (production). |
| 10 | Combat model depth | **Rich.** Damage types + resistances, crit, threat/aggro table, death→respawn lifecycle, kill-credit + XP/leveling hooks. |
| 11 | Event system | **Typed event bus.** One shared stream of domain events (`EntitySpawned`, `DamageDealt`, `EntityDied`, `AbilityCast`, …) consumed by both framework internals and game logic. The netcode derives deltas and the client event feed from the same stream. |
| 12 | Scale target | **Design for medium, don't wall off large.** Medium ≈ ≤200 players, ≤10k entities, 10–20 Hz. Large (500+ players, 50k+ entities, in-process simulation sharding) must stay reachable through the interfaces. |
| 13 | Playfield model | **Full map model.** Bounded / tiled map, terrain layers, static obstacle geometry, walkable regions, spawn points. Pathfinding and interest management build on it. |
| 14 | Persistent world | **Zones + save/load.** Partition the world into zones/chunks, each with its own interest scope; periodic snapshot persistence and load-on-start through `SnapshotStore`. |
| 15 | Determinism | **Deterministic simulation, no replay tooling.** Seeded RNG routed through the framework, defined tick/event ordering — but no tick-stamped input log or match-replay feature. |
| 16 | Vision / fog of war | **Core feature that drives the netcode.** Per-player / per-team vision (sight radius, terrain occlusion, reveal, stealth vs detection). The authoritative broadcast sends each player only what they can currently see — vision *is* the primary interest filter. |
| 17 | Pathfinding | **Pluggable `NavProvider`, grid-first** (recommendation — see §4). Ship tile-grid A* for individuals plus flow/vector fields for large groups converging on one target; navmesh can be added later behind the same interface. |
| 18 | Packaging | **Split modules** with an umbrella artifact that re-exports them. |
| 19 | Delivery | **Phased roadmap** (this document). |

---

## 2. Target architecture

### 2.1 Module layout

```
gametools-core         GameObject → VisibleObject → Actor, stats, buffs, capabilities,
                       entity registry + EntityId, typed event bus, seeded RandomSource,
                       deterministic tick order, fixed-timestep SimulationLoop.
gametools-world        Tiled map + terrain layers, static collision geometry, walkable
                       regions, spawn points, zones/chunks, spatial index, physics
                       (motion integration + collision detection & response), vision / LOS.
gametools-combat       Alive, projectiles, damage types + resistances, crit, threat table,
                       death → respawn lifecycle, kill-credit + XP/leveling hooks.
gametools-ai           NavProvider (grid A* + flow fields), path following, aggro / target
                       selection helpers built on the threat table + vision.
gametools-net          Authoritative server, SnapshotCodec (JSON + binary), stable-ID delta
                       protocol, per-player vision/zone/distance interest filtering,
                       action-map input decoding, tick + input sequence numbers, GameServer.
gametools-session      AuthProvider SPI (+ TrustingAuthProvider default), PlayerId,
                       SessionRegistry, mid-match reconnect.
gametools-persistence  PlayerStore / SnapshotStore ports + in-memory impls, zone save/load,
                       periodic snapshot scheduling.
gametools-abilities    Ability definitions, cooldowns, cast times, resource costs, targeting
                       types, extended status effects (DoT, slow, root, shield, stun).
gametools-items        Item definitions, inventory containers, equipment slots, gear stat
                       contributions via StatMod.
gametools              Umbrella — re-exports every module above for consumers that want it all.
```

Dependency direction: `core` depends on nothing internal; `world`, `session` depend on
`core`; `combat` depends on `core` + `world`; `ai` depends on `world` + `combat`;
`abilities`/`items` depend on `combat`; `net` depends on `world` + `combat` + `session`
(+ the protocol types — see Open Decision A); `persistence` depends on `core` + `world` +
`session`. No cycles.

### 2.2 Cross-cutting principles

- **Ports and adapters.** Anything that touches the outside world — auth, storage, the wire
  codec, navigation — is a framework-owned interface with a trivial default and room for a
  real implementation the consumer supplies.
- **The event bus is the spine.** Framework systems publish; the netcode, persistence, and
  game logic subscribe. A new reactive system should never require editing the code that
  causes the thing it reacts to.
- **Deterministic core.** All randomness goes through `core`'s seeded `RandomSource`; tick
  and event application order is defined and documented. This buys desync debugging and
  test stability now, and keeps replay/lockstep possible later without a rewrite.
- **The loop is opt-in.** `SimulationLoop` is a convenience over `World.tick()`, never a
  requirement. Its settings object is externally mutable and thread-safe.
- **Vision before the wire.** Interest management is computed from the vision model first,
  then narrowed by zone and distance. There is no "send everything" broadcast on the
  scalable path — only per-player views.

---

## 3. Phased roadmap

Each phase is independently shippable and maps to at least one release. Breaking changes are
batched at phase boundaries. Versioning follows `CONTRIBUTING.md` and the
`Major.Feature.Minor` + bugfix-letter nomenclature.

### Phase 0 — Foundations *(in `gametools-core`; also closes #1, #2)*

1. **Entity identity.** `EntityId` value type (stable, unique), an entity registry on
   `World`, an ID on every `GameObject`. Root cause of issue #3.
2. **Typed event bus.** `GameEvent` sealed hierarchy, an `EventBus`, and framework
   internals wired to publish lifecycle/combat events.
3. **Deterministic tick.** Seeded `RandomSource` threaded through the framework; documented
   tick + `removeList` + event-application ordering.
4. **Fixed-timestep loop.** `SimulationLoop` driving `World.tick()` with an externally
   mutable, thread-safe settings object (tick rate, max catch-up steps). Opt-in.
5. **Combat bug fixes.** `Alive.cancelAttack()` (#1); stop attacking a target that has died
   or left the world (#2). Small, self-contained, done here against the current `Alive`.

*Ships as a Feature release. No wire-protocol change yet.*

### Phase 1 — Map & space *(new `gametools-world` module)*

1. **Map model.** Bounded / tiled map, terrain layers, static collision geometry, walkable
   regions, named spawn points. Loadable from a data format.
2. **Zones / chunks.** Each zone owns an interest scope and a subset of the entity set.
3. **Spatial index rework.** Incremental-update spatial structure (no per-frame full
   rebuild) sized for the medium target; keep a `Quadtree` option, add a uniform grid.
4. **Physics.** Velocity / acceleration / force integration; collision detection *and*
   resolution (push-out / slide). Decide discrete vs continuous (Open Decision C).
5. **Vision / LOS.** Sight radius, terrain occlusion, reveal sources, per-player /
   per-team vision maps. Consumed later by the netcode.

*Ships as a Feature release. `World` gains a map and zones; likely a breaking constructor change.*

### Phase 2 — Rich combat *(new `gametools-combat` module)*

1. Move `Alive` + projectiles out of `core` into `combat` (Open Decision B).
2. Damage types + resistance / armor model; crit chance and multiplier.
3. Threat / aggro table per `Alive`.
4. Death → respawn lifecycle: configurable respawn delay, respawn at map spawn points,
   `EntityDied` / `EntityRespawned` events.
5. Kill-credit resolution + XP / leveling hooks (event-driven; curve pluggable —
   Open Decision E).

*Ships as a Major release (import paths move, combat API reshaped).*

### Phase 3 — Authoritative networking *(`gametools-net` + `gametools-session`)*

> **Upstream prerequisite.** This phase depends on WebTools transport changes that must land
> first — see §7. Filed 2026-09-05 as `SpartanLaboratories/WebTools` issues
> [#8](https://github.com/SpartanLaboratories/WebTools/issues/8) (binary datagram path),
> [#9](https://github.com/SpartanLaboratories/WebTools/issues/9) (datagram size),
> [#10](https://github.com/SpartanLaboratories/WebTools/issues/10) (liveness timeout +
> disconnect event) and [#11](https://github.com/SpartanLaboratories/WebTools/issues/11)
> (handshake reject + credential). Phase 3 planning starts only once those are released.


1. **`SnapshotCodec` seam.** JSON impl (current behavior, kept for debug/handshake) +
   compact binary impl for production.
2. **Stable-ID delta protocol.** Per-tick diffs keyed by `EntityId`; baseline + delta;
   server tick number on every message. Resolves the wire half of issue #3.
3. **Action-map input.** `INPUT` datagrams carry action states + a client input sequence
   number. Low-level typed-event routing stays; `MouseAction` becomes one source among many.
4. **Authoritative input application (optional layer).** Server validates and applies
   movement / actions; each snapshot carries the last-processed input sequence per player
   for client reconciliation.
5. **Per-player interest filtering.** Broadcast becomes per-player: vision → zone →
   distance. No more all-to-all full state.
6. **`gametools-session`.** `AuthProvider` SPI + `TrustingAuthProvider` default; `PlayerId`;
   `SessionRegistry` with a reconnect grace window that keeps a dropped player's entities
   alive briefly and rebinds on a valid resume token.
7. **`GameServer` rework** to sit on all of the above.

*Ships as a Major release (wire protocol replaced; coordinate with the client project and WebTools).*

### Phase 4 — AI & pathfinding *(new `gametools-ai` module)*

1. `NavProvider` interface; tile-grid A* implementation from the Phase 1 map.
2. Flow-field generator for large groups converging on a shared target.
3. Path following integrated with `Actor` movement strategies.
4. Aggro / target-selection helpers using the threat table + vision.

*Ships as a Feature release.*

### Phase 5 — Persistence & persistent world *(new `gametools-persistence` module)*

1. `PlayerStore`, `SnapshotStore` ports + in-memory implementations.
2. Zone save / load; periodic snapshot scheduler; load-on-start.
3. Session resume (Phase 3) wired to player-state restore.

*Ships as a Feature release.*

### Phase 6 — Abilities & items *(`gametools-abilities`, `gametools-items`)*

1. **Abilities.** `Ability` definitions, cooldowns, cast times, resource costs, targeting
   types (self / unit / ground / skillshot / aura). Extended status effects (DoT, slow,
   root, shield, stun) building on `Buff` + `Capability`.
2. **Items.** Item definitions, inventory containers, equipment slots, gear stat
   contributions via `StatMod`.

*Ships as one or two Feature releases.*

### Phase 7 — Scale hardening

1. In-process simulation sharding for the "don't wall off large" target (Open Decision F).
2. Binary codec + delta-compression tuning.
3. Level 4c non-functional tests at the medium target (200 players / 10k entities / 20 Hz)
   and a documented ceiling.

*Ships as a Feature release + a benchmark report.*

---

## 4. Recommendation: pathfinding

Given the RTS/MOBA + MMO-lite mix and the tiled-map decision:

- **Ship grid A* first.** A tile grid falls straight out of the Phase 1 map, is trivial to
  visualize and debug, and handles the MOBA/tower-defense case fully.
- **Add flow fields for group movement.** RTS-style "select 40 units, right-click" is a
  pathological case for per-unit A*; a single flow field toward the destination is O(map)
  once and O(1) per unit. This is the RTS-specific win.
- **Put both behind a `NavProvider` interface.** When an MMO zone needs organic terrain that
  a grid represents badly, a navmesh implementation drops in behind the same interface
  without touching movement code.

Not navmesh-first: it is more work to build and to author content for, and it buys little
until there is a large open-terrain zone that actually needs it.

---

## 5. Open decisions

| ID | Decision | Notes |
|----|----------|-------|
| A | **Where do the shared protocol / DTO types live?** A tiny client-safe `gametools-protocol` module with no server dependencies, or inside `gametools-net`? | The separate client project must depend on whatever holds them. A `gametools-protocol` module keeps the client off the server tree. Leaning toward the separate module. |
| B | **Does `Alive` move to `gametools-combat`?** | Clean layering says yes; it is a breaking import change for existing consumers. Batched into the Phase 2 Major release either way. |
| C | **Discrete vs continuous collision.** | At 10–20 Hz a fast projectile can tunnel through a thin wall. `DirectionalProjectile` already sweeps along a line; a swept-shape check for fast movers may be enough without full continuous physics. |
| D | **Binary codec: hand-rolled or a library?** | `kotlinx-serialization-protobuf`, FlatBuffers, or bespoke. Affects the dependency surface and the client project. |
| E | **How opinionated is XP / leveling?** | Pluggable curve function vs a fixed formula with parameters. Recommend a `LevelCurve` interface with a sensible default. |
| F | **In-process sharding model (Phase 7).** | Thread-per-zone, coroutine dispatcher per zone, or single-threaded with per-zone time budgeting. Defer until Phase 1 zones exist. |
| G | **Team / alliance model.** | Current `Faction` on `Alive` — extend to teams, alliances, friendly-fire rules, and shared vision (ties into Phase 1 vision + Phase 2 combat). |

---

## 6. Constants across all phases

- Every module carries its own five-level test tree (`com.spartanlabs.gaming.testing.<level>`).
- `README.md` and each module README stay current with every phase that changes the
  external shape (per the global README rule).
- Wire-protocol changes are coordinated with `SpartanLaboratories/WebTools` (transport) and
  the client project; the protocol carries an explicit version.
- Bugs / gaps found in `WebTools` or `GeneralTools` during a phase are raised as issues
  against the upstream repo before working around them.
- Each phase begins with its own `docs/<phase>-plan.md` in the established plan format.

---

## 7. WebTools / transport boundary

GameTools' UDP transport is `SpartanLaboratories/WebTools` (`MultiConnectionUDPServer` /
`MultiConnectionUDPClient` / `Connection`). Several roadmap items are transport-generic and
belong upstream in WebTools rather than in GameTools. Filed 2026-09-05 as WebTools issues
#8–#14; GameTools consumes the primitives once released.

### Belongs in WebTools

| Issue | Gap today | Upstream change | Needed by |
|-------|-----------|-----------------|-----------|
| [#8](https://github.com/SpartanLaboratories/WebTools/issues/8) | `Connection.push` / `MultiConnectionUDPClient.send` are `String`-only; every inbound datagram is `String(bytes).trim()`. The private channels already send `ByteArray` but it is not exposed and there is no bytes-in path. | Expose `push(ByteArray)` / `send(ByteArray)` and a raw-bytes inbound callback alongside the text one. | Phase 3 binary `SnapshotCodec` |
| [#9](https://github.com/SpartanLaboratories/WebTools/issues/9) | `RECEIVE_BUFFER_BYTES = 1024` on both receive loops silently truncates larger datagrams. | Configurable receive buffer, sane default (≥1200; up to 65507); optional fragment/reassembly helper. | Phase 3 delta snapshots at the medium scale target |
| [#10](https://github.com/SpartanLaboratories/WebTools/issues/10) | No per-connection last-seen tracking; no "client went silent" signal. `terminate()` is manual only. | Track last-inbound timestamp per `Connection`; add an `onClientTimeout` / `onDisconnect` hook after a configurable silence. | Phase 3 `SessionRegistry` reconnect grace window |
| [#11](https://github.com/SpartanLaboratories/WebTools/issues/11) | Base class always replies `REGISTERED`; an app-level refusal cannot be a handshake rejection (the client believes it connected). Handshake tokens after `<name>` are ignored. | Pre-accept hook that can refuse with `REFUSED <reason>`; pass an opaque credential string through from `Iam`. | Phase 3 `AuthProvider`; also fixes an existing `GameServer` wart |
| [#12](https://github.com/SpartanLaboratories/WebTools/issues/12) | Both sides require the caller to run their own ~20 s `KA` timer. | Opt-in built-in keepalive scheduler on client and server `Connection`. | Any long-lived session (quality of life) |
| [#13](https://github.com/SpartanLaboratories/WebTools/issues/13) | No RTT or packet-loss measurement. | Periodic RTT probe exposing `rttMillis` / loss rate per connection. | Client interpolation delay; server reconciliation |
| [#14](https://github.com/SpartanLaboratories/WebTools/issues/14) | No reliability layer — pure unreliable UDP. | A reliable-ordered sub-channel (acks + retransmit + sequencing) as a WebTools channel abstraction. Design/discussion issue only; large. | Phase 3+ reliable events/commands (ability cast, chat, spawn/despawn) |

### Stays in GameTools

Stable `EntityId`, baseline+delta diffing, the game snapshot schema, the `SnapshotCodec`
*interface*, interest management / vision filtering (needs the world + vision model), the
tick number and input sequence number *semantics* (they identify simulation state, distinct
from a transport datagram sequence in #6), the `AuthProvider` SPI, `SessionRegistry` session
semantics, and zone save/load.
