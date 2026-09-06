package com.spartanlabs.gaming.networking

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.webtools.MultiConnectionUDPServer
import com.spartanlabs.webtools.Connection
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.DrawableSnapshot
import com.spartanlabs.gaming.gameobjects.VisibleObject
//endregion

//region 2. Intended Function
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//endregion

/** Shared slf4j logger for the game networking layer. */
private val log: Logger = LoggerFactory.getLogger("GameServer")

/**
 * A [MultiConnectionUDPServer] that speaks the game's protocol.
 *
 * The base class does all of the socket work: it listens on
 * [MultiConnectionUDPServer.COMMON_LISTEN_PORT] for `Iam <name>` handshakes (a trailing
 * address token is accepted but ignored), replies - from that same common socket, straight back
 * to the datagram's source address and port - with the bare token `REGISTERED`, and then calls
 * [onClientConnect]. From then on every player's traffic - application data, `STATE` broadcasts,
 * and the player's own `KA` keepalives - is multiplexed over that same shared socket; there is
 * no per-player dedicated port pair. A player is expected to send a bare `KA` datagram on an
 * idle interval (WebTools recommends ~20s) from the socket it handshook on, to keep its NAT
 * mapping warm; the base class consumes `KA` silently and never routes it to [onPlayerMessage]
 * or [onPlayerInput]. This class supplies the game-specific half of that contract:
 *
 * - it accepts at most [maxConnections] players and refuses the rest,
 * - it starts listening on every accepted player's connection and routes their
 *   messages by verb: an `INPUT <json>` datagram is decoded into a [MouseAction] and handed
 *   to [onPlayerInput], and everything else is passed verbatim to [onPlayerMessage], each
 *   tagged with the player it came from,
 * - it serializes world state to JSON and broadcasts it with [broadcast].
 *
 * Construction starts the server: the base class spawns its handshake thread from its own
 * `init` block, so players may begin connecting as soon as the constructor returns. Only one
 * instance can exist per JVM at a time, since the common port is fixed - construct a second
 * one before calling [shutDown] on the first and the bind will fail.
 *
 * @property maxConnections the largest number of players allowed on the server at once
 * @param onPlayerMessage invoked with the sending player's name and the raw message text for
 * every datagram that is not a recognised `INPUT` message. Called on that player's listener
 * thread, so it should return quickly and must be safe to call concurrently for different
 * players.
 * @param onPlayerInput invoked with the sending player's name and the decoded [MouseAction]
 * for every well-formed `INPUT <json>` datagram. A malformed `INPUT` payload is logged and
 * dropped rather than reaching either callback. Shares [onPlayerMessage]'s threading contract.
 */
class GameServer(
    val maxConnections: Int,
    private val onPlayerMessage: (playerName: String, message: String) -> Unit = { _, _ -> },
    private val onPlayerInput: (playerName: String, input: MouseAction) -> Unit = { _, _ -> }
) : MultiConnectionUDPServer() {

    /**
     * The players that finished the handshake, were accepted, and are being listened to,
     * keyed by the name they handshook with.
     *
     * Concurrent because it is written from the base class's handshake thread while the game
     * thread reads it to [broadcast] and [push].
     */
    private val players = ConcurrentHashMap<String, Connection>()

    /** How many players are connected right now. */
    val playerCount: Int get() = players.size

    /** The names of the players connected right now, as a detached copy. */
    val playerNames: Set<String> get() = players.keys.toSet()

    /**
     * Accepts a freshly handshaken client as a player, unless the server is already full.
     *
     * The base class has, by this point, already told the client it is `REGISTERED`, so a
     * refusal cannot be a handshake rejection - the connection is terminated instead, which
     * unbinds its message handler; the refused client is left believing it is connected, but
     * nothing on the server answers it again.
     *
     * A player that handshakes under a name that is already connected replaces it, and the
     * stale connection is terminated so it stops holding a message handler.
     *
     * @param connection the connection the base class just registered
     */
    override fun onClientConnect(connection: Connection) {
        admit(connection)
            .andThen { admitted -> listenTo(admitted) }
            .onFailure { cause ->
                // The single failure path for both a refusal and a connection that could not be
                // listened to. Removing by value leaves an existing player of the same name
                // untouched, and is a no-op when this connection was never admitted.
                log.warn("Dropping '{}': {}", connection.name, cause.message)
                players.remove(connection.name, connection)
                connection.terminate()
            }
    }

    /**
     * Decides whether a freshly handshaken client may join.
     *
     * A player returning under a name that is already connected is always admitted - they
     * replace their previous connection rather than counting a second time against the cap.
     *
     * @param connection the connection the base class just registered
     * @return the admitted connection, or [Result.failure] carrying the reason it was refused
     */
    private fun admit(connection: Connection): Result<Connection> = when {
        !isFullyConstructed ->
            Result.failure(IllegalStateException("it handshook before the server finished starting up"))

        players.size >= maxConnections && !players.containsKey(connection.name) ->
            Result.failure(IllegalStateException("the server is full (${players.size}/$maxConnections)"))

        else -> Result.success(connection)
    }

    /**
     * Registers an admitted [connection] as a player and starts listening to it, terminating
     * the stale connection of a player who is reconnecting so it stops holding a message handler.
     *
     * @param connection the connection [admit] accepted
     * @return [Result.success] once the player is being listened to, or the failure that prevented it
     */
    private fun listenTo(connection: Connection): Result<Unit> {
        players.put(connection.name, connection)?.let { stale ->
            log.info("'{}' reconnected, terminating their previous connection", connection.name)
            stale.terminate()
        }
        log.info("'{}' joined ({}/{})", connection.name, players.size, maxConnections)
        return connection.actuate { message -> dispatch(connection.name, message) }
    }

    /**
     * Routes one datagram from [playerName] to the right callback.
     *
     * A message whose first token is [INPUT_VERB] has its remainder decoded as a [MouseAction]
     * and handed to [onPlayerInput]; a payload that will not parse is logged and dropped.
     * Every other message is passed verbatim (trimmed) to [onPlayerMessage].
     *
     * @param playerName the name the datagram's sender handshook with
     * @param message the raw datagram text
     */
    private fun dispatch(playerName: String, message: String) {
        val trimmed = message.trim()
        val (verb, payload) = trimmed.split(" ", limit = 2).let { parts ->
            parts[0] to parts.getOrElse(1) { "" }
        }
        when (verb) {
            INPUT_VERB -> decodeInput(payload)
                .onSuccess { input -> onPlayerInput(playerName, input) }
                .onFailure { cause ->
                    log.warn("Ignoring malformed {} from '{}': {}", INPUT_VERB, playerName, cause.message)
                }

            else -> onPlayerMessage(playerName, trimmed)
        }
    }

    /**
     * Decodes the JSON body of an `INPUT` message into a [MouseAction].
     * @param json the text after the [INPUT_VERB] token
     * @return the decoded action, or [Result.failure] if [json] is not a valid [MouseAction]
     */
    private fun decodeInput(json: String): Result<MouseAction> =
        runCatching { Json.decodeFromString<MouseAction>(json) }

    /**
     * Whether this instance's own fields have been assigned yet.
     *
     * [MultiConnectionUDPServer] starts accepting handshakes from its `init` block, which the
     * JVM runs before a subclass's fields are initialized. A client already retrying `Iam` when
     * the server boots can therefore reach [onClientConnect] while [players] is still `null`,
     * so that window is detected and refused instead of failing on the handshake thread.
     */
    @Suppress("SENSELESS_COMPARISON")
    private val isFullyConstructed: Boolean get() = players != null

    /**
     * Snapshots and broadcasts the given world state to every connected player. Each object is
     * snapshotted as the most specific kind that fits it - [com.spartanlabs.gaming.gameobjects.AliveSnapshot],
     * [com.spartanlabs.gaming.gameobjects.ActorSnapshot], or plain
     * [com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot].
     *
     * Objects whose [VisibleObject.visible] flag is `false` are skipped, so hidden objects
     * are never sent to clients.
     *
     * @param visibleObjects the objects the players should be told about
     * @return [Result.success] if the state reached every player, or the first failure encountered
     */
    fun broadcast(visibleObjects: Iterable<VisibleObject>): Result<Unit> =
        broadcast(visibleObjects.filter { it.visible }.map { visibleObject -> DrawableSnapshot from visibleObject })

    /**
     * Broadcasts already-taken snapshots to every connected player as a `STATE <json>` message.
     *
     * The list serializes polymorphically: each entry carries a `type` field, so an
     * [com.spartanlabs.gaming.gameobjects.AliveSnapshot] or
     * [com.spartanlabs.gaming.gameobjects.ActorSnapshot] is distinguishable from a plain
     * [com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot] on the wire.
     *
     * @param snapshots the world state to serialize and send
     * @return [Result.success] if the state reached every player, or the first failure encountered
     */
    fun broadcast(snapshots: List<DrawableSnapshot>): Result<Unit> =
        runCatching { Json.encodeToString(snapshots) }
            .onFailure { cause -> log.error("Could not serialize {} snapshot(s)", snapshots.size, cause) }
            .andThen { json -> pushToAllPlayers("$STATE_VERB $json") }

    /**
     * Sends a message to every player this [GameServer] currently admits, over WebTools' shared
     * common socket - each send addresses the player's own post-NAT [Connection.peer].
     *
     * This is deliberately narrower than the inherited [pushToAll]: [pushToAll] reaches every
     * connection WebTools has ever registered for this server instance, including a handshake
     * this class refused for being over [maxConnections] and the stale connection of a player who
     * has since reconnected from a new origin - WebTools never prunes a registration once a
     * handshake completes (only [stop] tears every one of them down at once). Prefer this method
     * over [pushToAll] whenever "every player" is meant to mean "every player I currently track".
     *
     * @param message the text to send to all players
     * @return [Result.success] if the message reached every player, or the first failure encountered
     */
    fun pushToAllPlayers(message: String): Result<Unit> {
        log.debug("Pushing a message to all {} player(s)", players.size)
        return players.values.fold(Result.success(Unit)) { pushed, connection ->
            // Every player is pushed to regardless of their predecessors' outcome; only the
            // reported Result short-circuits, not the delivery.
            connection.push(message).let { outcome -> pushed.andThen { outcome } }
        }
    }

    /**
     * Sends a message to a single player, over WebTools' shared common socket, addressed to
     * their own [Connection.peer].
     * @param playerName the name the player handshook with
     * @param message the text to send
     * @return [Result.success] if the datagram was sent, or [Result.failure] if no such player
     * is connected or the send failed
     */
    fun push(playerName: String, message: String): Result<Unit> =
        players[playerName]?.push(message)
            ?: Result.failure(NoSuchElementException("No connected player named '$playerName'"))

    /**
     * Disconnects a single player, unbinding their message handler on WebTools' shared socket.
     * They are free to handshake again afterwards.
     * @param playerName the name the player handshook with
     * @return [Result.success] if their handler was unbound, or [Result.failure] if no such
     * player is connected or the release failed
     */
    fun disconnect(playerName: String): Result<Unit> =
        players.remove(playerName)?.let { connection ->
            log.info("Disconnecting '{}'", playerName)
            connection.terminate()
        } ?: Result.failure(NoSuchElementException("No connected player named '$playerName'"))

    /**
     * Shuts the server down, disconnecting every player and releasing the common handshake
     * port. Once called, this instance should be discarded - there is no restart.
     *
     * The inherited [stop] does the actual teardown; this wrapper exists to also forget the
     * players, so it should be preferred over calling [stop] directly.
     *
     * @return [Result.success] if every step succeeded, or the first failure encountered
     */
    fun shutDown(): Result<Unit> {
        log.info("Shutting down the game server and its {} player(s)", players.size)
        players.clear()
        return stop()
    }

    companion object {
        /** The verb that opens a world-state broadcast sent by [broadcast]. */
        const val STATE_VERB = "STATE"

        /** The verb that opens a client input message carrying a serialized [MouseAction]. */
        const val INPUT_VERB = "INPUT"

        /** Builds the `INPUT <json>` datagram a client sends to deliver [input] to the server. */
        fun inputMessage(input: MouseAction): String = "$INPUT_VERB ${Json.encodeToString(input)}"
    }
}

/**
 * Chains a [Result]-returning [transform] onto this result, short-circuiting on failure.
 *
 * WebTools keeps its own `flatMap` for this `internal`, so the composition the networking
 * layer is written in is reproduced here rather than reached for across the module boundary.
 *
 * @param transform applied to the encapsulated value if this result is a success
 * @return [transform]'s result if this is a success, otherwise this failure unchanged
 */
private inline fun <T, R> Result<T>.andThen(transform: (T) -> Result<R>): Result<R> =
    fold(onSuccess = transform, onFailure = { cause -> Result.failure(cause) })
