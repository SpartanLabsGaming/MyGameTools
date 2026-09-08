# Changelog

All notable changes to **GameTools** are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Versions follow the project's `Major.Feature.MinorChange` scheme (see
[`.aiassistant/rules/CLAUDE.md`](.aiassistant/rules/CLAUDE.md) §6); a trailing letter marks a
bug-fix release. Releases are tagged `vX.Y.Z` and published to
[Maven Central](https://central.sonatype.com/artifact/io.github.spartanlabsgaming/gametools)
(as of `4.0.0` the library ships as three coordinates — `gametools`, `gametools-core`,
`gametools-net`; earlier releases were the single `GameTools` artifact).

## [Unreleased]

### Changed
- Applying a movement command (`MoveTo`, `MoveDir`, `Follow`, `Stop`) through
  `ClientCommand.applyTo` now calls off the actor's pending attack when the actor is an
  `Alive` — a manual movement order is treated as a deliberate override of an in-progress
  auto-attack, the standard RTS expectation. Previously the attack cycle kept running and the
  unit resumed closing on its target. `Attack` / `StopAttack` are unchanged, and the cancel
  runs only on the success path (a `Follow` whose target does not resolve leaves the attack
  untouched). Consumers that wrapped every move command with a manual `Alive.cancelAttack()`
  can drop that wrapper. (#39)

### Fixed
- **Every module's published `-javadoc.jar` was empty.** `gametools-core-5.0.0-javadoc.jar`,
  `gametools-net-5.0.0-javadoc.jar` and the `gametools` umbrella javadoc jar all shipped with
  no API pages — Dokka could not see the Kotlin plugin through the convention-plugin
  classloader split ("could not load KotlinBasePlugin"). The Kotlin and Dokka plugins are now
  applied consistently from the root build, so every module's Dokka publication renders real
  HTML. (#40)
- **The `io.github.spartanlabsgaming:gametools` umbrella published empty `-sources.jar` and
  `-javadoc.jar`.** They now bundle the Kotlin source and the combined Dokka API documentation
  of both `gametools-core` and `gametools-net`. IDE "Go to declaration" and quick-doc on a
  GameTools type reached through the umbrella coordinate now resolve. Binary, POM and wire
  protocol are unchanged. (#40)

## [5.0.0] — 2026-09-07

### Changed
- **BREAKING — a world-state snapshot's `id` is now an `EntityId`, not a `Long`.**
  `DrawableSnapshot.id` and the `id` of every variant (`VisibleObjectSnapshot`,
  `ActorSnapshot`, `AliveSnapshot`) change type `Long` → `EntityId`. The **wire form is
  unchanged** — an `EntityId` serializes as its bare `raw` `Long` — so a `5.0.0` server and
  an older client still exchange STATE payloads; only source that reads `snapshot.id` needs
  updating.

  **Migration:**
  - `val id: Long = snapshot.id` → `val id: EntityId = snapshot.id`, then `id.raw` wherever a
    `Long` is still required.
  - `snapshot.id == DrawableSnapshot.UNIDENTIFIED` → `snapshot.id == EntityId.UNASSIGNED`.
    The `DrawableSnapshot.UNIDENTIFIED` constant is **removed** — `EntityId.UNASSIGNED` is the
    single "not addressable" sentinel.

### Added
- **Typed, library-owned client→server command protocol** (`gametools-net`, package
  `com.spartanlabs.gaming.networking.command`), giving the command direction the same
  treatment `DrawableSnapshot` already gives STATE:
  - `ClientCommand` — a non-`sealed` marker interface. A game adds its own commands by
    implementing it with an `@Serializable` type and registering it in a `SerializersModule`
    passed to `ClientCommandCodec`.
  - Six standard commands, each naming a mechanism that already exists on `Actor` / `Alive`:
    `MoveTo`, `MoveDir`, `Follow`, `Stop` (movement only) — and `Attack`, `StopAttack`. All
    entity operands are `EntityId`.
  - `ClientCommandCodec` — encodes/decodes commands over a `COMMAND <json>` envelope,
    alongside the existing `STATE <json>` / `INPUT <json>`.
  - `ClientCommand.applyTo(World)` — resolves a standard command's `EntityId`s via
    `World.byId` and calls the mechanism, returning an `ApplyResult` (`Applied` /
    `TargetMissing` / `WrongType` / `Unhandled`). Performs **no** authorization — gate
    ownership in the app before calling it.
  - `GameServer` gains two (defaulted, `@JvmOverloads`) constructor parameters —
    `commandCodec` and `onCommand`. With no codec, a `COMMAND` datagram falls through to
    `onPlayerMessage` exactly as before, so wiring the protocol in is additive.
- `EntityId` is now `@Serializable` (as its bare `raw` `Long`).

## [4.0.0] — 2026-09-06

### Changed
- **BREAKING — the library is now a multi-module build, published as three Maven
  coordinates.** The single `io.github.spartanlabsgaming:GameTools` artifact is replaced by:
  - `io.github.spartanlabsgaming:gametools` — the **umbrella** (note the lowercase id). No
    source of its own; `api`-re-exports every module, so its transitive contents are
    identical to the pre-split artifact.
  - `io.github.spartanlabsgaming:gametools-core` — the object model, stats & buffs, the
    `Quadtree` spatial index, `EntityId`, `World`, the typed event bus, the seeded
    deterministic tick and the opt-in `SimulationLoop`, plus the `@Serializable` geometry
    DTOs. No networking dependency.
  - `io.github.spartanlabsgaming:gametools-net` — the UDP `GameServer` and the `MouseAction`
    wire type; depends on `gametools-core`.

  **Migration:** replace the one dependency line
  (`io.github.spartanlabsgaming:GameTools:<old>` → `io.github.spartanlabsgaming:gametools:4.0.0`),
  or depend on `gametools-core` alone if you don't use `GameServer`. No package or import
  changes — every type keeps its `com.spartanlabs.*` name. The old `GameTools` coordinate
  receives no further releases.

### Added
- `gametools-core` and `gametools-net` as independently consumable artifacts, for projects
  that want the simulation layer without pulling in the networking stack (or vice-versa).

### Build
- Multi-module Gradle build; shared configuration extracted to `build-logic/` convention
  plugins (`gametools.kotlin-library`, `gametools.published-library`). Root project reduced
  to the Dokka aggregator. CI check names and the release flow are unchanged.

## [3.1.0] — 2026-09-06

### Added
- **Stable entity identity.** Every `GameObject` a `World` owns is assigned an `EntityId`
  (in acquisition order, from `1`, never reused), reachable via `GameObject.entityId`.
  `World.byId(id)` resolves an owned object and returns `null` once it has left the world.
  Ids are allocated per `World` and also cover an object's `subObjects` tree.
- **Stable id on world-state snapshots.** Every `DrawableSnapshot` variant now carries an
  `id: Long` (the object's `EntityId`, or `0` when unowned / for a pre-3.1 payload), so
  clients can address objects by id instead of by list position (closes #3). The field is
  decode-defaulted, so an older payload without it still deserializes.
- **Typed event bus.** `World.events` is an `EventBus` that publishes a `GameEvent` stream -
  `EntitySpawned` / `EntityRemoved` as objects join and leave, and `AttackIssued`,
  `AttackLanded`, `DamageDealt`, `EntityDied` (with the killer, when known) from combat.
  Delivery is synchronous, single-threaded, in subscription order, and a listener that throws
  is caught and logged without disrupting the tick.
- **Deterministic tick.** `RandomSource` / `SeededRandom` and a new `World(seed: Long = …)`
  constructor: `World.rng` is the single source of engine randomness, so a fixed seed plus a
  fixed sequence of calls reproduces a run exactly. `Alive`'s evasion roll now goes through
  it instead of `Math.random()`. `World.tickCount` counts frames, and `World.tick()` now
  documents its exact order of operations. The seed is logged on construction.
- **`Alive.cancelAttack()`** plus the `onAttackCancelled()` / `onAttackEnded(reason)` hooks
  and the `AttackEndReason` enum: a caller can now break off a pending or in-progress attack
  (the actor keeps its current destination), and the `AttackCancelled` / `AttackEnded` events
  report both exits (closes #1).
- **Opt-in `SimulationLoop`.** `com.spartanlabs.gaming.simulation.SimulationLoop` drives
  `World.tick()` on a daemon thread at a fixed timestep, with a bounded catch-up after a
  stall; `LoopSettings` (`tickRateHz`, `maxCatchUpTicks`) is live-tunable from any thread.
  `SimulationLoop.advance()` exposes the timestep step for callers driving their own loop.
  Nothing in the engine depends on it - `World.tick()` remains the primitive.

### Fixed
- **`Alive` no longer keeps attacking a target that has died or left the world.** The attack
  cycle now checks its target at the top of every tick and ends itself - firing
  `onAttackEnded` and `GameEvent.AttackEnded` - instead of walking to the target's last
  position and swinging forever (closes #2).

## [3.0.0] — 2026-09-04

### Changed
- **BREAKING — Maven Central coordinates.** The library is now published under
  `io.github.spartanlabsgaming:GameTools` (matching the SpartanLabsGaming GitHub
  organization and its verified Maven Central namespace) instead of
  `io.github.spartanlaboratories:GameTools`. Artifact contents and the `2.0.0`
  version are unchanged; consumers must update their dependency coordinates. The
  old coordinates receive no further releases. `WebTools` and `GeneralTools`
  continue to resolve from `io.github.spartanlaboratories`.
- **BREAKING — GameServer wire protocol (WebTools 2.0.0c).** The handshake reply is now the
  bare token `REGISTERED` (was `TXRXON <sendPort> <receivePort>`). Players no longer get a
  dedicated UDP port pair - every player's traffic (application data, `STATE`/`INPUT`
  messages, and broadcasts) is multiplexed over the single shared handshake socket
  (`MultiConnectionUDPServer.COMMON_LISTEN_PORT`). A player must send the bare token `KA` on
  that same socket roughly every 20s to keep its NAT mapping warm; the server consumes it
  silently and never routes it to `onPlayerMessage` / `onPlayerInput`.

### Dependencies
- WebTools `2.0.0b` → `2.0.0c`.

## [2.0.0] — 2026-09-04

### Changed
- **BREAKING — GameServer wire protocol (WebTools 2.0.0b).** Clients now send `Iam <name>`;
  the old trailing `<address>` token is accepted but ignored. The server's handshake reply
  is now a bare `TXRXON <sendPort> <receivePort>` (no leading address) sent back to the UDP
  source of the `Iam` datagram instead of `<clientAddress>:9999`. A client must therefore
  read the reply — and any common-channel broadcast — on the same socket it sent `Iam`
  from. `COMMON_SEND_PORT` is gone. This makes the handshake work through NAT.

### Dependencies
- WebTools `2.0.0` → `2.0.0b`; dropped the now-redundant `GeneralTools` exclude
  (WebTools 2.0.0b depends on `GeneralTools:2.0.1` directly).

## [1.9.0] — 2026-09-03

### Added
- **Capabilities on the `GameObject` tree.** `Capability` interface + `CoreCapability` enum
  (`MOVE`, `ATTACK`); each subtype declares `capabilities` (`Actor` adds `MOVE`, `Alive` adds
  `ATTACK`). `GameObject.can(capability)` reports whether one is usable right now, accounting
  for suppressing buffs. `Actor` gates movement on `can(MOVE)`; `Alive` gates its attack cycle
  on `can(ATTACK)`.
- **Buffs on the `GameObject` tree.** `Buff` (name, duration, stat mods, suppressed
  capabilities) with `onApplied` / `onTick` / `onExpired` hooks. `GameObject` gains `buffs`,
  `applyBuff`, `removeBuff`, `dispel`; `tick()` ages and reverts them.
- `Moddable` interface unifying `ModularStat` and `CombinedStat` as `StatMod` targets;
  `GameObject.stats` exposes stats by name.
- `BuffSnapshot` carried on `GameObjectSnapshot`, so active buffs reach every
  `DrawableSnapshot`.

### Changed
- `ModularStat.applyMod` / `removeMod` now return `Unit` (were `ModularStat`); no caller
  used the result.

## [1.8.0] — 2026-09-02

Pre-changelog release. See the [`v1.8.0`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.8.0)
tag.

## [1.7.0] — 2026-09-01

Pre-changelog release. See the [`v1.7.0`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.7.0) tag.

## [1.6.0] — 2026-08-30

Pre-changelog release. See the [`v1.6.0`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.6.0) tag.

## [1.5.2a] — 2026-08-30

Pre-changelog bug-fix release. See the [`v1.5.2a`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.5.2a) tag.

## [1.5.2] — 2026-08-30

Pre-changelog release. See the [`v1.5.2`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.5.2) tag.

## [1.5.1] — 2026-08-30

Pre-changelog release. See the [`v1.5.1`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.5.1) tag.

## [1.5.0] — 2026-08-30

Pre-changelog release. See the [`v1.5.0`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.5.0) tag.

## [1.4.0] — 2026-08-30

Pre-changelog release. First tagged release. See the [`v1.4.0`](https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.4.0) tag.

## [1.3.0] — 2026-08-30

Pre-changelog release (untagged). Actor angle/`Result` rework, `MouseAction` input routing,
`GameServer` implementation and networking tests.

[Unreleased]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v5.0.0...HEAD
[5.0.0]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v4.0.0...v5.0.0
[4.0.0]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v3.1.0...v4.0.0
[3.1.0]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v2.0.0...v3.0.0
[2.0.0]: https://github.com/SpartanLabsGaming/MyGameTools/compare/v1.9.0...v2.0.0
[1.9.0]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.6.0...v1.7.0
[1.6.0]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.5.2a...v1.6.0
[1.5.2a]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.5.2...v1.5.2a
[1.5.2]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.5.1...v1.5.2
[1.5.1]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/SpartanLaboratories/MyGameTools/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.4.0
[1.3.0]: https://github.com/SpartanLaboratories/MyGameTools/releases/tag/v1.4.0
