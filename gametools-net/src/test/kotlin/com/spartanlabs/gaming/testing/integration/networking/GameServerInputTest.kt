package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.networking.GameServer
import com.spartanlabs.gaming.networking.MouseAction
import com.spartanlabs.gaming.networking.MouseActionType
//endregion

//region 2. Intended Function
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/** Covers how [GameServer] routes a player's datagrams: `INPUT` to [MouseAction], the rest raw. */
class GameServerInputTest {

    private val fixture = ServerFixture()

    @AfterTest
    fun tearDown() = fixture.close()

    @Test
    fun `inputMessage round-trips through the INPUT verb`() {
        val action = MouseAction(MouseActionType.MOVE, button = -1, x = 4.5, y = 6.5)

        val message = GameServer.inputMessage(action)

        assertTrue(message.startsWith("${GameServer.INPUT_VERB} "))
        assertEquals(
            action,
            Json.decodeFromString<MouseAction>(message.removePrefix("${GameServer.INPUT_VERB} "))
        )
    }

    @Test
    fun `an INPUT datagram is decoded into a MouseAction for the sending player`() {
        val (client, _) = connectedClient("alice")
        val action = MouseAction(MouseActionType.PRESS, button = 0, x = 12.0, y = 34.0)

        assertTrue(client.send(GameServer.inputMessage(action)).isSuccess)

        assertEquals("alice" to action, fixture.awaitPlayerInput())
        assertNull(fixture.awaitPlayerMessage(NEGATIVE_TIMEOUT_MILLIS), "INPUT must not also fire onPlayerMessage")
    }

    @Test
    fun `a malformed INPUT payload is dropped rather than reaching either callback`() {
        val (client, _) = connectedClient("alice")

        assertTrue(client.send("${GameServer.INPUT_VERB} not-json").isSuccess)

        assertNull(fixture.awaitPlayerInput(NEGATIVE_TIMEOUT_MILLIS), "a payload that will not parse must be dropped")
        assertNull(fixture.awaitPlayerMessage(NEGATIVE_TIMEOUT_MILLIS), "a malformed INPUT must not fall through to onPlayerMessage")
    }

    @Test
    fun `a non-INPUT datagram is passed verbatim to onPlayerMessage`() {
        val (client, _) = connectedClient("alice")

        assertTrue(client.send("CHAT hello there").isSuccess)

        assertEquals("alice" to "CHAT hello there", fixture.awaitPlayerMessage())
        assertNull(fixture.awaitPlayerInput(NEGATIVE_TIMEOUT_MILLIS), "a non-INPUT message must not fire onPlayerInput")
    }

    @Test
    fun `a keepalive datagram from a player is swallowed rather than reaching onPlayerMessage`() {
        val (client, server) = connectedClient("alice")

        assertTrue(client.sendKeepAlive().isSuccess)

        assertNull(fixture.awaitPlayerMessage(NEGATIVE_TIMEOUT_MILLIS), "KA must never reach onPlayerMessage")
        assertNull(fixture.awaitPlayerInput(NEGATIVE_TIMEOUT_MILLIS), "KA must never reach onPlayerInput")
        assertEquals(1, server.playerCount, "a keepalive must not affect the roster")
    }

    /** Handshakes [name] against a freshly started server and waits for the roster to catch up. */
    private fun connectedClient(name: String): Pair<FakeClientHarness, GameServer> {
        val server = fixture.startServer(maxConnections = 4)
        val client = fixture.client()
        assertTrue(client.handshake(name).isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")
        return client to server
    }

    companion object {
        /** Short wait for a callback that is expected *not* to fire. */
        private const val NEGATIVE_TIMEOUT_MILLIS = 500L
    }
}
