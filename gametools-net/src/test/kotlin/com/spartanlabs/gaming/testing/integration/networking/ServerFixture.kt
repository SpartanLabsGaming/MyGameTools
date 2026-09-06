package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.networking.GameServer
import com.spartanlabs.gaming.networking.MouseAction
//endregion

//region 2. Intended Function
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
//endregion

/**
 * Owns the [GameServer] under test together with every socket the fake clients opened
 * against it, and tears all of them down again afterwards.
 *
 * Teardown is not optional housekeeping: a [GameServer] binds the fixed common port in its
 * constructor, so a test class that leaves one running makes every later test class fail to
 * start one. Each test therefore closes its fixture in an `@AfterTest`.
 */
internal class ServerFixture : AutoCloseable {

    /** The server under test, once [startServer] has been called. */
    private var server: GameServer? = null

    /** Every harness handed out by [client], closed in [close]. */
    private val harnesses = mutableListOf<FakeClientHarness>()

    /** Every `(player, message)` pair the server reported, in arrival order. */
    private val playerMessages = LinkedBlockingQueue<Pair<String, String>>()

    /** Every `(player, MouseAction)` pair the server decoded from an `INPUT` message. */
    private val playerInputs = LinkedBlockingQueue<Pair<String, MouseAction>>()

    /**
     * Starts the server under test, recording everything its players say so that tests can
     * await it with [awaitPlayerMessage] or [awaitPlayerInput].
     *
     * @param maxConnections the cap to give the server
     * @return the started server
     */
    fun startServer(maxConnections: Int): GameServer =
        GameServer(
            maxConnections,
            onPlayerMessage = { name, message -> playerMessages.put(name to message) },
            onPlayerInput = { name, input -> playerInputs.put(name to input) }
        ).also { started -> server = started }

    /** @return a fake client harness that will be closed with this fixture */
    fun client(): FakeClientHarness = FakeClientHarness().also { harness -> harnesses.add(harness) }

    /**
     * Waits for the server's roster to reach [expected].
     *
     * The handshake reply is sent before the server admits the player, so a test that has
     * merely completed a handshake cannot assume the roster has caught up yet.
     *
     * @param expected the player count to wait for
     * @param timeoutMillis how long to wait before giving up
     * @return `true` if the roster reached [expected] within the timeout
     */
    fun awaitPlayers(expected: Int, timeoutMillis: Long = ROSTER_TIMEOUT_MILLIS): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline && server?.playerCount != expected) {
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        return server?.playerCount == expected
    }

    /**
     * Gives the server time to act on a handshake that is expected *not* to change the
     * roster, so that a refusal cannot be confused with one that simply has not landed yet.
     */
    fun settle() = Thread.sleep(SETTLE_MILLIS)

    /**
     * Takes the next message the server reported from a player.
     * @param timeoutMillis how long to wait for one to arrive
     * @return the `(player, message)` pair, or `null` if none arrived in time
     */
    fun awaitPlayerMessage(timeoutMillis: Long = MESSAGE_TIMEOUT_MILLIS): Pair<String, String>? =
        playerMessages.poll(timeoutMillis, TimeUnit.MILLISECONDS)

    /**
     * Takes the next [MouseAction] the server decoded from a player's `INPUT` message.
     * @param timeoutMillis how long to wait for one to arrive
     * @return the `(player, MouseAction)` pair, or `null` if none arrived in time
     */
    fun awaitPlayerInput(timeoutMillis: Long = MESSAGE_TIMEOUT_MILLIS): Pair<String, MouseAction>? =
        playerInputs.poll(timeoutMillis, TimeUnit.MILLISECONDS)

    /** Closes every harness, then shuts the server down and frees the common port. */
    override fun close() {
        harnesses.forEach(FakeClientHarness::close)
        server?.shutDown()
        server = null
    }

    companion object {
        /** How long [awaitPlayers] waits for the roster to catch up. */
        private const val ROSTER_TIMEOUT_MILLIS = 4000L

        /** How long [awaitPlayerMessage] waits for a player's message to surface. */
        private const val MESSAGE_TIMEOUT_MILLIS = 4000L

        /** How often [awaitPlayers] re-reads the roster. */
        private const val POLL_INTERVAL_MILLIS = 20L

        /** How long [settle] allows for a handshake that should change nothing. */
        private const val SETTLE_MILLIS = 500L
    }
}
