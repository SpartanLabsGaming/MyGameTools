# Plan: Typed, library-owned client→server command protocol (`ClientCommand`)

## Header / Association

- **Covers:** GitHub issue [SpartanLabsGaming/MyGameTools#31](https://github.com/SpartanLabsGaming/MyGameTools/issues/31)
  — *"Typed, library-owned client→server command protocol (ClientCommand)"*
  (labels: `type: feature`, `area: networking`, `area: serialization`). Instruction from
  Spartak Singh via a Claude Code session on 2026-09-06: *"Ship the mechanism, include a
  fixed command set that makes sense for mechanisms already in the library (move for actor,
  attack for alive). Allow additions by consumers. Go ahead and plan."* Follow-up decisions
  (same session) recorded in §8.
- **Status:** planning only. No source, test, or build file has been modified.
- **Baseline:** GameTools `4.0.0`, three-module build (`gametools-core`, `gametools-net`,
  `gametools` umbrella), `master` clean.
- **Target version:** `4.0.0` → **`5.0.0`** (one Major release). The command mechanism and
  standard set are additive, but decision **8-D** retypes `DrawableSnapshot.id` from `Long`
  to `EntityId` — a source-breaking change for every consumer that reads a snapshot's id, so
  the whole change ships under a major bump. See §7.
- **Open GitHub issues addressed:** #31 closes in this change. Not a blocker for and does not
  block MyGameServer#6 / GameGraphics#1 (see §1.3).
- **Upstream dependency:** none. Reuses the existing `kotlinx-serialization-json:1.7.3`
  (`api` dependency of `gametools-core`) and the WebTools UDP transport already under
  `GameServer`. No WebTools surface touched.
- **Downstream consumers:** MyGameServer and GameGraphics both hand-roll this protocol today
  and would migrate onto it after release (§9). Per standing guidance no GitHub issues are
  filed against consumer repos; the migration is tracked only in this document.
- **Related docs:** `docs/framework-vision-and-roadmap.md` (§7 transport boundary; this is
  the library-hardening theme of #21/#22/#23); `docs/phase-0-foundations-plan.md`
  (`EntityId`, `World.byId` — the identity layer this builds on); `docs/module-split-plan.md`
  (why the wire types live in `gametools-net`).

---

## 1. Context

### 1.1 What exists today (verified against `master`)

- **STATE (server→client) is library-owned and typed.** `DrawableSnapshot` is a
  `@Serializable sealed interface` in `gametools-core`
  (`gameobjects/VisibleObject.kt:146`), with `VisibleObjectSnapshot` / `ActorSnapshot` /
  `AliveSnapshot` variants carrying a `type` discriminator. `GameServer.broadcast(...)`
  (`gametools-net/.../networking/GameServer.kt:216`) serializes a `List<DrawableSnapshot>`
  and sends it as `STATE <json>`.
- **INPUT (client→server) is library-owned and typed, but single-purpose.** `MouseAction`
  (`networking/MouseAction.kt`) is a `@Serializable data class`; the client sends
  `INPUT <json>` (built by `GameServer.inputMessage(MouseAction)`), and
  `GameServer.dispatch()` (`GameServer.kt:155`) decodes it and calls `onPlayerInput`.
  A payload that will not parse is logged and dropped.
- **COMMAND (client→server) does not exist in the library.** Any datagram whose verb is not
  `INPUT` falls through verbatim to `onPlayerMessage(playerName, trimmed)`
  (`GameServer.kt:167`). Each consumer hand-rolls a verb+positional-string grammar on top of
  that raw callback (`message.split(" ")` in MyGameServer; a `ProtocolParsing` object and
  `NetworkClient.sendCommand("SET_DEST $x $y")` in GameGraphics) — two independent
  implementations of an unwritten spec.
- **`Json` in `GameServer` is the default instance** (`GameServer.kt:16`,
  `Json.decodeFromString` / `Json.encodeToString` with no configured instance). The command
  path needs its own configured `Json` (a `SerializersModule` for open polymorphism, and a
  `type` class discriminator to match STATE's convention).

### 1.2 Identity and the mechanisms the standard commands target

- **`EntityId`** (`gametools-core/.../gameobjects/EntityId.kt`) — a `@JvmInline value class`
  wrapping `raw: Long`. **Not `@Serializable` today.** `DrawableSnapshot.id` is a bare
  `Long` (`EntityId.raw`), not the value class. The command protocol wants `EntityId`
  operands (issue: *"'index or id' becomes unrepresentable"*), so `EntityId` gains a
  serializer — see §2.1.
- **`World.byId(id: EntityId): GameObject?`** (`World.kt:116`) — the resolution path. Returns
  `null` for an id that has died or been removed, *"exactly the signal a command handler
  wants"* (its KDoc). Objects added through `World.add` are resolvable immediately; objects
  added by mutating `gameObjects` directly are resolvable from the next `tick`.
- **Move — `Actor`** (`gametools-core/.../gameobjects/Actor.kt`). `Actor.destination` is a
  `var Point`; assigning it re-aims `angle`, clears `hasSettled`, and the default
  `Movement.Targeting` strategy walks the actor there and stops. `Actor.capabilities`
  contains `CoreCapability.MOVE`. A `MoveTo` command = assign `destination`.
- **Attack — `Alive`** (`gametools-core/.../gameobjects/Alive.kt`). `Alive.issueAttack(target: Alive)`
  starts the close-then-swing loop; `Alive.cancelAttack()` (the caller's exit from it) stops
  a pending or in-progress attack and *keeps* the current destination. `Alive.capabilities`
  adds `CoreCapability.ATTACK`. An `Attack` command = `issueAttack`; a `Stop` command =
  `cancelAttack()` plus halt movement.

### 1.3 Sequencing

Independent of MyGameServer#6 / GameGraphics#1. Those can ship ids-on-the-wire under the
current string grammar now; this issue is the follow-up that makes the grammar typed and
library-owned. Nothing here blocks on them.

---

## 2. Design

Three layers, all new, all opt-in:

1. **Mechanism** — a `ClientCommand` marker interface, a configured codec with an
   `INPUT`/`STATE`-style `COMMAND <json>` envelope, and `GameServer` routing to a new
   `onCommand` callback. Consumers can register their own command types.
2. **Standard set** — six `@Serializable` implementations of `ClientCommand`, each split by
   the capability it needs (decision **8-A**):
   - **Actor-capability commands** (target an `Actor`): `MoveTo` → `Actor.destination`;
     `MoveDir` → `Actor.angle` + `Movement.Directional`; `Follow` → `Movement.Homing`;
     `Stop` → halt movement (`destination ← location`, and clear a `Homing`/`Directional`
     strategy back to `Targeting`). `Stop` does **not** touch the attack cycle.
   - **Alive-capability commands** (target an `Alive`): `Attack` → `Alive.issueAttack`;
     `StopAttack` → `Alive.cancelAttack`.
   All ship in the built-in `SerializersModule`.
3. **Applier (opt-in)** — a `World`-aware helper that resolves a standard command's
   `EntityId`s through `World.byId` and calls the mechanism. Performs **no** authorization —
   the app gates ownership before calling it (issue's scope boundary: game-authorization
   logic stays in the app, not the netcode).

### 2.1 `EntityId` becomes serializable — `gametools-core`

`EntityId` gains `@Serializable` backed by an explicit `KSerializer<EntityId>` that
reads/writes the underlying `Long` (a primitive-descriptor serializer delegating to
`Long.serializer()`). The emitted JSON is byte-for-byte the bare `Long`, so retyping
`DrawableSnapshot.id` to `EntityId` (§2.1a) changes no wire bytes, and a client can feed a
snapshot's `id` straight into a command.

- This is a `gametools-core` change (the class lives there). `EntityId`'s public shape is
  unchanged; it just becomes usable as a `@Serializable` property type.

### 2.1a `DrawableSnapshot.id` retyped `Long` → `EntityId` — `gametools-core` (decision 8-D)

- `DrawableSnapshot.id`, and the `id` of every variant (`VisibleObjectSnapshot` /
  `ActorSnapshot` / `AliveSnapshot`), change type `Long` → `EntityId`. The default
  `DrawableSnapshot.UNIDENTIFIED: Long = 0L` becomes `EntityId.UNASSIGNED`; the `from(...)`
  factories change `id = obj.entityId.raw` to `id = obj.entityId`.
- **Wire-compatible** (serializer emits the same `Long`), **source-breaking** — any consumer
  reading `snapshot.id` as a `Long` now handles `EntityId`. This is the reason for the major
  bump (§7).
- Makes STATE and COMMAND symmetric: the id a client reads off a snapshot is the exact type
  it puts in a `MoveTo` / `Attack`.
- `DrawableSnapshotTest` and `ClientServerRoundTripTest` (the only `id` readers in the test
  tree) update accordingly.

### 2.2 Command types — `gametools-net`, new package `com.spartanlabs.gaming.networking.command`

```kotlin
/**
 * A single client→server order. The library ships the six standard commands below; a consumer
 * adds its own by implementing this interface with an @Serializable type and registering it
 * in a SerializersModule handed to [ClientCommandCodec] (see the KDoc there).
 */
interface ClientCommand

// --- Actor-capability commands: the operand must resolve to an Actor ---

@Serializable @SerialName("gametools.moveTo")
data class MoveTo(val actor: EntityId, val x: Double, val y: Double) : ClientCommand

@Serializable @SerialName("gametools.moveDir")
data class MoveDir(val actor: EntityId, val angleDegrees: Int) : ClientCommand

@Serializable @SerialName("gametools.follow")
data class Follow(val actor: EntityId, val target: EntityId) : ClientCommand

@Serializable @SerialName("gametools.stop")
data class Stop(val actor: EntityId) : ClientCommand          // halts movement only

// --- Alive-capability commands: the operand must resolve to an Alive ---

@Serializable @SerialName("gametools.attack")
data class Attack(val attacker: EntityId, val target: EntityId) : ClientCommand

@Serializable @SerialName("gametools.stopAttack")
data class StopAttack(val alive: EntityId) : ClientCommand
```

- **Split by capability (decision 8-A).** `Stop` takes an `actor` and stops *movement* only
  (`Actor` has `CoreCapability.MOVE`, not necessarily `ATTACK`); ending an attack is
  `StopAttack`, which takes an `alive`. The applier rejects a `Stop` whose id resolves to a
  non-`Actor` and a `StopAttack`/`Attack` whose id resolves to a non-`Alive` (§2.5).
- **Non-sealed interface** — a `sealed` hierarchy cannot be extended across the
  consumer/library module boundary, and the issue's core benefit (a field added to a command
  breaks both repos' builds until updated) holds for any `@Serializable data class`
  regardless of sealing. Consumers get a `when (command)` with an `else` branch; that is the
  accepted cost of extensibility.
- **`SerialName`s are namespaced** (`gametools.*`) so a consumer's discriminator can never
  silently collide with a library one.
- **Entity operands are `EntityId`**, never `Int`/`Long`. `MoveTo` carries raw `x`/`y` (a
  world point, not an entity); `MoveDir` carries a whole-degree `angleDegrees` matching
  `VisibleObject.angle`.

### 2.3 Codec + envelope — `gametools-net`, `command/ClientCommandCodec.kt`

```kotlin
/**
 * Encodes and decodes [ClientCommand]s for the wire. One instance is shared by a client (to
 * build datagrams) and its server (to read them); both MUST be built with the same
 * [appCommands] module or a consumer command will not round-trip.
 *
 * @param appCommands polymorphic registrations for the consumer's own [ClientCommand] types,
 *   e.g. SerializersModule { polymorphic(ClientCommand::class) { subclass(BuildStructure.serializer()) } }.
 *   The library's own six standard commands are always registered; omit this for the
 *   standard set alone.
 */
class ClientCommandCodec(appCommands: SerializersModule = EmptySerializersModule()) {
    private val json = Json {
        serializersModule = StandardClientCommands + appCommands
        classDiscriminator = "type"          // matches DrawableSnapshot's convention
        ignoreUnknownKeys = true
    }
    /** The full `COMMAND <json>` datagram text for [command]. */
    fun encode(command: ClientCommand): String = "$COMMAND_VERB ${json.encodeToString(PolymorphicSerializer(ClientCommand::class), command)}"
    /** [command] decoded from the text after the `COMMAND` verb, or [Result.failure] if it will not parse. */
    fun decode(payload: String): Result<ClientCommand> =
        runCatching { json.decodeFromString(PolymorphicSerializer(ClientCommand::class), payload) }

    companion object { const val COMMAND_VERB = "COMMAND" }
}
```

- `StandardClientCommands: SerializersModule` — a `public val` registering all six standard
  commands under `polymorphic(ClientCommand::class)`, so a consumer who builds their own
  `Json` can reuse it.
- Envelope reuses the existing JSON-over-UDP path exactly like `INPUT` / `STATE`: one verb,
  one space, one JSON body. No transport change.

### 2.4 `GameServer` wiring — `gametools-net`, `GameServer.kt`

Add two constructor parameters, both defaulted so every existing call site compiles unchanged:

```kotlin
class GameServer(
    val maxConnections: Int,
    private val onPlayerMessage: (playerName: String, message: String) -> Unit = { _, _ -> },
    private val onPlayerInput: (playerName: String, input: MouseAction) -> Unit = { _, _ -> },
    private val commandCodec: ClientCommandCodec? = null,
    private val onCommand: (playerName: String, command: ClientCommand) -> Unit = { _, _ -> },
)
```

`dispatch()` gains one branch, mirroring the `INPUT` branch precisely:

```kotlin
ClientCommandCodec.COMMAND_VERB -> {
    val codec = commandCodec
    if (codec == null) onPlayerMessage(playerName, trimmed)   // back-compat: no codec ⇒ raw
    else codec.decode(payload)
        .onSuccess { command -> onCommand(playerName, command) }
        .onFailure { cause -> log.warn("Ignoring malformed {} from '{}': {}", ClientCommandCodec.COMMAND_VERB, playerName, cause.message) }
}
```

- `onCommand` shares `onPlayerMessage`'s threading contract (called on the player's listener
  thread; must return quickly; must be concurrency-safe across players) — documented in the
  class KDoc alongside the existing callbacks.
- With no `commandCodec`, a `COMMAND …` datagram still reaches `onPlayerMessage` exactly as
  today — pure addition, no behaviour change for current consumers.

### 2.5 Standard-command applier — `gametools-net`, `command/StandardCommandApplier.kt`

```kotlin
/**
 * Applies a library-standard [ClientCommand] to [world] by resolving its [EntityId] operands
 * through [World.byId] and calling the matching mechanism:
 *  - [MoveTo]     → sets [Actor.destination]
 *  - [MoveDir]    → sets [VisibleObject.angle] and [Actor.movement] = [Movement.Directional]
 *  - [Follow]     → sets [Actor.movement] = [Movement.Homing] on the resolved target
 *  - [Stop]       → [Movement.Targeting] with destination ← current location (movement only)
 *  - [Attack]     → [Alive.issueAttack] on the resolved target
 *  - [StopAttack] → [Alive.cancelAttack]
 *
 * Does NOT check ownership, range, faction or capability-suppression — the caller is expected
 * to have authorized the command first.
 */
fun ClientCommand.applyTo(world: World): ApplyResult
```

`ApplyResult` = `Applied` / `TargetMissing(id: EntityId)` (no live object) /
`WrongType(id: EntityId, expected: KClass<*>)` (resolved, but e.g. `Stop` on a
non-`Actor` or `Attack` on a non-`Alive`) / `Unhandled` (a consumer command — not one of the
six). Kept a free function so it is trivially ignorable; apps with their own dispatch never
touch it.

- **`Follow` resolves the target to a `GameObject`** and passes it into
  `Movement.Homing(target)`. If the target id is missing → `TargetMissing`.
- **Capability check is the caller's job**, but `WrongType` still fires on a *class* mismatch
  (`Stop`/`MoveTo`/`MoveDir`/`Follow` need an `Actor`; `Attack`/`StopAttack` need an
  `Alive`) — that is a protocol error, not an authorization one.

### 2.6 Client side

The library has no client class (the client lives in GameGraphics). `ClientCommandCodec.encode`
is the whole client-facing API — GameGraphics' `NetworkClient` calls
`codec.encode(MoveTo(...))` and sends the string, replacing its hand-rolled formatter. This
mirrors how `GameServer.inputMessage` is the only client-facing INPUT helper.

---

## 3. File-by-file change list

### `gametools-core`

| File | Change |
|---|---|
| `gameobjects/EntityId.kt` | Add `@Serializable(with = EntityIdSerializer::class)`; add `object EntityIdSerializer : KSerializer<EntityId>` delegating to `Long.serializer()`. KDoc: note the emitted JSON is a bare `Long`. Import-group per §6. |
| `gameobjects/VisibleObject.kt` | **BREAKING.** `DrawableSnapshot.id`, `VisibleObjectSnapshot.id` → `EntityId`; drop `const val UNIDENTIFIED` (8-F), `id` default → `EntityId.UNASSIGNED`; `from` factory `id = obj.entityId`; reword the 2 KDoc refs. |
| `gameobjects/Actor.kt`, `gameobjects/Alive.kt` | `ActorSnapshot.id` / `AliveSnapshot.id` → `EntityId`; `from` factories `id = actor.entityId`. |

### `gametools-net`

| File | Change |
|---|---|
| `networking/command/ClientCommand.kt` (new) | The `ClientCommand` interface + the six standard command data classes. |
| `networking/command/ClientCommandCodec.kt` (new) | `ClientCommandCodec` class, `COMMAND_VERB`, `StandardClientCommands` module. |
| `networking/command/StandardCommandApplier.kt` (new) | `ClientCommand.applyTo(World)` + `ApplyResult`. |
| `networking/GameServer.kt` | Two new defaulted ctor params (`commandCodec`, `onCommand`); `@JvmOverloads` on the constructor; one new `when` branch in `dispatch()`; class KDoc updated for the new callback + its threading contract. |

### Docs / build

| File | Change |
|---|---|
| `README.md` (root) | Wire-protocol overview: add `COMMAND` alongside `STATE` / `INPUT`; the typed command set; note `snapshot.id` is now `EntityId`. |
| `CHANGELOG.md` | `## [Unreleased]` → **Changed (BREAKING):** `DrawableSnapshot.id` & variants are `EntityId` not `Long`; **Added:** `ClientCommand` protocol, six standard commands, `GameServer.onCommand`, `EntityId` is `@Serializable`. Migration note (map `.id` reads, `EntityId(x)` / `.raw` at the boundary). |
| `gametools-core/build.gradle.kts`, `gametools-net/build.gradle.kts`, `gametools/build.gradle.kts` | Version `4.0.0` → `5.0.0` (all three coordinates move together — precedent: 4.0.0). |
| `docs/framework-vision-and-roadmap.md` | One line under the transport/hardening section: the command direction is now library-owned; `EntityId` is first-class on both wire directions. |

No new dependency. `kotlinx-serialization-json` (with `PolymorphicSerializer`,
`SerializersModule`, `Json { classDiscriminator }`) is already `api` on `gametools-core`.

---

## 4. Test plan (5-Level Hierarchy — packages under `gametools-net/src/test/kotlin`, mirroring production)

| Level | Package | Coverage |
|---|---|---|
| **L2 component** | `testing.component.networking.command` | `ClientCommandCodec` round-trips each standard command; unknown discriminator → `Result.failure`; malformed JSON → `Result.failure`; a fake consumer command registered via `appCommands` round-trips; the same command **fails** to decode on a codec built without that module (proves the "same module both ends" contract). |
| **L2 component** | `testing.component.gameobjects` (core) | `EntityIdSerializer` round-trips; an `EntityId` field decodes from the same JSON as the equivalent bare `Long` (wire-equivalence). Update `DrawableSnapshotTest` for the retyped `id`; add a decode test proving a pre-retype payload (`"id": 0`) still decodes to `EntityId.UNASSIGNED`. |
| **L3 integration** | `testing.integration.networking` | Extend `ServerFixture` with an `onCommand` queue + `awaitCommand`. A `COMMAND <json>` datagram from a `FakeClientHarness` reaches `onCommand` tagged with the right player. `COMMAND` on a server built with **no** `commandCodec` still reaches `onPlayerMessage`. A malformed `COMMAND` payload is dropped — server stays up, no callback fires. Unknown verb still falls through to `onPlayerMessage` (regression guard). |
| **L4a deterministic** | `testing.deterministic.networking.command` | `ClientCommand.applyTo(World)` for all six: `MoveTo`→`destination`; `MoveDir`→`angle`+`Movement.Directional`; `Follow`→`Movement.Homing` on the resolved target; `Stop`→`Targeting` + halted, attack untouched; `Attack`→`issueAttack` (assert via `GameEvent.AttackIssued`); `StopAttack`→`cancelAttack`. `TargetMissing` for an unresolvable id; `WrongType` for `Stop` on a bare `VisibleObject` / `Attack` on a plain `Actor`; `Unhandled` for a consumer command. |
| **L4b e2e** | `testing.e2e` | Extend `ClientServerRoundTripTest`: fake client `Iam`s, sends `codec.encode(MoveTo(id, x, y))` (id taken from a received snapshot), server `applyTo`s it against a live `World`, next `broadcast` carries the actor's new `destination`. One full STATE↔COMMAND loop over loopback, `EntityId` round-tripping both ways. |
| **L4c non-functional** | (none new) | Payload sizes are trivial; no perf/security surface beyond "malformed input is dropped, not fatal", covered at L3. |
| **L5 UAT** | manual, post-merge | GameGraphics issuing a real move order through the typed path — belongs to the GameGraphics migration (§9), not this repo. |

L1 gating: the standard `./gradlew :gametools-net:test :gametools-core:test` run before the
PR (per CONTRIBUTING). No new gating harness needed.

---

## 5. Documentation (Audience-Reach model)

- **Component Ring (KDoc)** — every new public type and function gets a full block:
  `ClientCommand` (with the consumer-extension recipe), each standard command (which
  mechanism it drives), `ClientCommandCodec` (the "same module both ends" contract, the
  `COMMAND <json>` envelope shape), `applyTo` / `ApplyResult` (the no-authorization
  warning), the two new `GameServer` params (threading contract).
- **Boundary Ring** — the `COMMAND` verb documented next to `STATE` / `INPUT` in the
  `GameServer` class KDoc and the root README wire-protocol section: verb, JSON body,
  malformed-payload handling, the fact that it is one shared UDP socket.
- **Inner Core** — `//region` grouping in the new files per the house style; import groups
  per §6.
- **Architectural** — the roadmap-doc line in §3.

---

## 6. Version control approach

- **Branch:** `feat/31-client-command-protocol` off `master` (CONTRIBUTING requires the
  `<issue#>` prefix).
- **Commits** (Conventional Commits, `serialization` / `networking` scopes):
  1. `feat(serialization): make EntityId @Serializable`
  2. `feat(serialization)!: type DrawableSnapshot ids as EntityId, not Long`
     (`BREAKING CHANGE:` footer — the source-breaking retype; drives the major bump)
  3. `feat(networking): add ClientCommand protocol, codec and COMMAND envelope`
  4. `feat(networking): add the six standard commands and the World applier`
  5. `feat(networking): route COMMAND datagrams to a typed GameServer.onCommand callback`
     (includes `@JvmOverloads` on the constructor)
  6. `test(networking): cover the client command protocol at L2–L4b`
  7. `docs: document the COMMAND verb and the typed command set; bump to 5.0.0`
  (Squash/reorder during `rebase -i` before review as needed; the PR is the unit that
  matters.)
- **PR:** one PR, `Closes #31`, base `master`, semi-linear merge per the repo workflow. The
  `!` / `BREAKING CHANGE:` in commit 2 is what the release tooling keys the major bump on.
- **Release:** the `5.0.0` tag `v5.0.0` and the Maven Central publish are **not** part of
  this plan — the user runs the vanniktech publish explicitly. The PR merge is the
  deliverable here; releasing is a separate, user-initiated step.
- **Issue report:** on merge, a short comment on #31 summarising what shipped and pointing
  at the consumer-migration follow-up.

---

## 7. Breaking-change analysis

| Change | Breaking? | Notes |
|---|---|---|
| **`DrawableSnapshot.id` & all variant ids `Long` → `EntityId`** | **Yes — source** | Wire bytes unchanged (serializer emits the same `Long`), but every consumer reading `snapshot.id` as a `Long` must adapt (`EntityId` at the call site, `.raw` where a `Long` is still needed). This is the sole driver of the major bump. |
| `EntityId` gains `@Serializable` | No | Public shape unchanged; only adds a capability. |
| New `ClientCommand` / codec / applier types | No | All new public surface. |
| `GameServer` two new ctor params | No | Both defaulted; Kotlin call sites (positional or named) compile unchanged. `@JvmOverloads` is added in commit 5 so a Java consumer's existing 3-arg `new GameServer(...)` keeps resolving. |
| `dispatch()` new `COMMAND` branch | No | Only affects datagrams whose verb is literally `COMMAND`; today those hit `onPlayerMessage`, and with no codec configured they still do. |
| Version `4.0.0 → 5.0.0` | Major bump | Driven by the `id` retype above; §6 nomenclature. |

---

## 8. Decisions (resolved 2026-09-06)

- **A. `Stop` scope — RESOLVED.** `Stop` takes an `actor` and stops **movement only** (an
  `Actor` has `MOVE`, not necessarily `ATTACK`). Ending an attack is a separate
  `StopAttack(alive)` → `Alive.cancelAttack()`. Applier `WrongType`s a `Stop` whose id is
  not an `Actor` and a `StopAttack`/`Attack` whose id is not an `Alive`.
- **B. Standard set size — RESOLVED.** Ship all six: `MoveTo`, `MoveDir`, `Follow`, `Stop`,
  `Attack`, `StopAttack`. `Follow` → `Movement.Homing`; the applier resolves the target id
  to the `GameObject` `Homing` needs.
- **C. `onCommand` separate from `onPlayerInput` — RESOLVED: keep distinct.** `INPUT` is raw
  device events, `COMMAND` is semantic orders — different verbs, callbacks, decode types.
- **D. Retype `DrawableSnapshot.id` to `EntityId` — RESOLVED: do it, in this change.**
  Source-breaking → the whole change ships as `5.0.0` (§7). Makes STATE/COMMAND symmetric.
- **E. `ClientCommandCodec` package — RESOLVED: new `networking.command` sub-package.**

- **F. `DrawableSnapshot.UNIDENTIFIED` — RESOLVED: drop it.** `EntityId.UNASSIGNED` is the
  one canonical "not addressable" sentinel. Every `id` default becomes `EntityId.UNASSIGNED`;
  the three KDoc references are reworded. Consumers comparing `snapshot.id ==
  DrawableSnapshot.UNIDENTIFIED` switch to `EntityId.UNASSIGNED` (called out in the CHANGELOG
  migration note).

---

## 9. Cross-repo follow-up (no issues filed — downstream consumers)

After `5.0.0` is released:

- **MyGameServer** — (1) adapt to `snapshot.id: EntityId` wherever it reads snapshot ids;
  (2) replace `handleClientMessage`'s `message.split(" ")` with a
  `GameServer(commandCodec = ClientCommandCodec(myModule), onCommand = ::authorizeAndApply)`
  wiring — `authorizeAndApply` does the ownership check, then `command.applyTo(world)`,
  logging any `TargetMissing`/`WrongType`/`Unhandled`. Delete the hand-rolled verb parser.
  App-specific verbs (`SET_SPEED`, demo-only) become app `ClientCommand` types in `myModule`.
- **GameGraphics** — (1) adapt to `snapshot.id: EntityId` in its client-side snapshot
  handling; (2) replace `ProtocolParsing` + `NetworkClient.sendCommand("SET_DEST …")` with
  `ClientCommandCodec.encode(MoveTo(...))`, feeding the `EntityId` it now reads off snapshots
  straight back. Bump the GameTools dependency `4.0.0 → 5.0.0` (GameGraphics has lagged
  GameTools versions before — call this out in that PR).
- Both migrations are tracked here only; per standing guidance no issues are filed against
  consumer repos.

---

## 10. Risks

- **Version-coupling tightens.** A future command-shape change forces a lockstep GameTools
  bump on both consumers. This is the trade the issue accepts: an invisible app↔app coupling
  becomes a visible, compiler-checked library one.
- **Open polymorphism loses `when` exhaustiveness** for `ClientCommand`. Mitigated by
  namespaced `SerialName`s and the `applyTo` `Unhandled` result; consumers writing their own
  dispatch carry an `else`.
- **`@JvmOverloads` gap** on `GameServer`'s constructor (§7) — easy to miss; it is an
  explicit line item in commit 5.
- **The `id` retype touches four core files and their tests** (§3) and every consumer.
  Wire compatibility means a stale consumer still *talks* to a `5.0.0` server, but won't
  *compile* against the new artifact — the CHANGELOG migration note must be explicit and
  copy-pasteable.
- **`Json` instance divergence.** `GameServer` currently uses the default `Json`; the codec
  uses a configured one. They are used for different payloads (`STATE`/`INPUT` vs `COMMAND`)
  so this is fine, but a note in the code prevents a later "why two Jsons" refactor that
  would break the polymorphic module wiring.
