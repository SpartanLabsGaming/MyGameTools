# Draft: `ClientEventFeed` — server→client domain-event feed over an `EVENT` verb

> **Status: DRAFT — settled decisions only. Not planned, not scheduled.**
> Recorded 2026-09-26 from a design discussion about supporting an on-event sound system in a
> downstream server/client pair (the server tells clients *what happened*; the client reacts,
> e.g. by playing a sound effect). This is a networking-heavy change, so it must **not** be
> planned until the expected WebTools transport update has been adopted (WebTools
> [#8](https://github.com/SpartanLaboratories/WebTools/issues/8) binary path,
> [#9](https://github.com/SpartanLaboratories/WebTools/issues/9) datagram size,
> [#10](https://github.com/SpartanLaboratories/WebTools/issues/10) liveness/disconnect,
> [#11](https://github.com/SpartanLaboratories/WebTools/issues/11) handshake reject; see
> `framework-vision-and-roadmap.md` §7). It belongs to **Phase 3** and is referenced from that
> section of the roadmap. When planning starts, this document is the input to the `planner`,
> not a plan.

## 1. Motivation

Roadmap decision #11 already states that "the netcode derives deltas **and the client event
feed** from the same stream", but no Phase 3 item (#97–#103) delivers that feed. Today:

- The server side is sufficient: `EventBus` (`gametools-core/.../event/EventBus.kt`) is a
  synchronous FIFO pub/sub, and `GameEvent` is an open `interface`, so a game can add its own
  event types.
- Nothing carries events to clients. `GameServer` sends only `STATE <json>` server→client, and
  `GameEvent`s hold live `Alive` / `GameObject` references that cannot be serialized.

## 2. Settled decisions

### 2.1 Codec abstraction — one system for every verb

- Extract the generic half of `ClientCommandCodec` into a codec abstraction, following the
  library rule for infrastructure (interface + supplied default implementation):
  - `MessageCodec<T>` — interface: `verb`, `encode(T): String`, `decode(payload): Result<T>`.
  - `JsonMessageCodec<T>(verb, baseClass, standardModule, appModule)` — open JSON default
    holding what `ClientCommandCodec` does today (polymorphic serializer, `"type"`
    discriminator, `ignoreUnknownKeys`, app `SerializersModule` merged over the standard one).
- `ClientCommandCodec` becomes a subclass (`COMMAND`), and a new event codec is the second
  (`EVENT`). Each codec owns its verb, as `COMMAND_VERB` does today.
- **`STATE` moves onto the same system.** The inline `STATE` encoding in `GameServer`
  (`STATE_VERB`, `broadcast(List<DrawableSnapshot>)`'s hand-rolled `"$STATE_VERB $json"`) is
  removed and replaced entirely by a codec. Removing the public `GameServer.STATE_VERB` is
  API-breaking, so this rides Phase 3's Major release.

### 2.2 Wire form — `WireEvent`, produced by a projection

- `WireEvent` is the serializable, `EntityId`-keyed wire form of a domain event (with the
  position/kind data a client needs), analogous to `DrawableSnapshot` for state.
- **Client relevance is decided by the projection, not by a flag on the event.** A projector
  `(GameEvent) -> WireEvent?` both filters (`null` = not sent to clients) and converts.
  - Rejected: an `isClientRelevant` boolean on `GameEvent`. It would put a transport concern
    on a `gametools-core` domain type, hardcode per-game policy into the event's author, and
    cannot express per-player relevance.
  - A default projector ships for the built-in `GameEvent`s. Consumers replace or wrap it.
- Per-player audience is a separate seam (`WireEvent` → recipients, default "all tracked
  players"), ready for #101 interest filtering.

### 2.3 `ClientEventFeed` — the bus→wire relay (server side)

- Name: **`ClientEventFeed`**. Lives in `gametools-net`.
- Subscribes to a `World`'s / game's `EventBus`, projects each event, encodes it with the
  `EVENT` codec, and pushes it through `GameServer`.
- **Buffered, not immediate.** `EventBus` delivers synchronously on the publishing (tick)
  thread, so the feed's listener only **enqueues**; the consumer calls `flush()` (e.g. right
  after `broadcast(...)` each tick) to send. This keeps socket I/O out of the simulation tick,
  keeps each tick's events next to that tick's `STATE`, and fits "GameServer is a library, no
  loop": GameTools never drives the flush itself.
- **The buffer lives inside `ClientEventFeed`.** `EventBus`, `GameEvent`, and every publisher
  are unchanged; a game that doesn't construct a feed behaves exactly as before (additive,
  opt-in).
- **Project at enqueue time, not at flush.** The feed's listener runs while the event's
  `Alive` / `GameObject` references are live and current, so it projects straight to
  `WireEvent` and buffers that. Projecting at flush would read state that may have moved on
  (an entity removed or relocated later in the same tick).

### 2.4 Client-side dispatcher

- A small client-side component (GameTools' first) that takes an inbound line, recognises the
  `EVENT` verb, decodes it with the shared codec, and calls an `onEvent` listener or a passed-in
  function — mirroring `GameServer`'s `onCommand` parameter. Prefer a `fun interface` listener
  (Java-friendly, consistent with `EventBus.Listener`).
- The same codec instance / configuration is used on both ends, as with `ClientCommandCodec`.
- Everything downstream of `onEvent` (event→sound mapping, asset loading, spatial audio) is the
  client's / GameGraphics' concern, not GameTools'.

## 3. Open questions (settle during planning)

| # | Question | Notes |
|---|---|---|
| A | Is `MessageCodec<T>` the abstraction #97's `SnapshotCodec` builds on? | §2.1 puts `STATE` on the codec system, which implies yes; confirm so Phase 3 has one codec abstraction, not two. The binary implementation (#97, #121) then slots in as a second `MessageCodec` impl. |
| B | Stability tier of `ClientEventFeed`, `WireEvent`, the projector and audience seams. | Proposed: Experimental (`@RequiresOptIn`) until a real consumer (the sound-system game) builds against it. |
| C | Batching vs datagram size. | One `EVENT` per event, or batch per flush? Batching depends on WebTools #9 (receive buffer currently a fixed 1024 bytes). An oversized batch is truncated with no error. |
| D | Tick stamping / ordering against `STATE`. | Depends on #98 (server tick number on every message). |
| E | Reliability. | Plain UDP loses one-shot events silently. Fine for cosmetic sounds, not for critical cues. Depends on WebTools #14 (reliable-ordered sub-channel). Possibly a per-`WireEvent` reliability hint. |
| F | Default projector coverage. | Which built-in `GameEvent`s map to a `WireEvent`, and with what fields. |
| G | Client-side dispatcher name and module. | `gametools-net` is the obvious home; confirm. |
| H | `WireEvent` shape. | Open polymorphic hierarchy (`@Serializable` subclasses registered in a `SerializersModule`, like `ClientCommand`) is the expected default; confirm. |

## 4. Related defects affecting event correctness

- #81: projectile damage bypasses `Alive.takeDamage`, so kill credit is wrong for projectile
  kills, and anything reacting to that event (such as a sound) would get the wrong killer.
- #82: `Alive.die()` relocates a `RESPAWN` actor before publishing `EntityDied`, so a death
  event's position would be the respawn point.

These are independent fixes but should land before the default projector is built.
