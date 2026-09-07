package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.networking.GameServer
import com.spartanlabs.gaming.networking.command.ClientCommandCodec
import com.spartanlabs.gaming.networking.command.MoveTo
import com.spartanlabs.gaming.networking.command.Stop
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/** Covers how [GameServer] routes a player's `COMMAND` datagrams: to [ClientCommandCodec] when one is set, raw otherwise. */
class GameServerCommandTest {

    private val fixture = ServerFixture()
    private val codec = ClientCommandCodec()

    @AfterTest
    fun tearDown() = fixture.close()

    @Test
    fun `a COMMAND datagram is decoded into a ClientCommand for the sending player`() {
        val client = connectedClient("alice", codec)
        val command = MoveTo(EntityId(3), x = 10.0, y = 20.0)

        assertTrue(client.send(codec.encode(command)).isSuccess)

        assertEquals("alice" to command, fixture.awaitCommand())
        assertNull(fixture.awaitPlayerMessage(NEGATIVE_TIMEOUT_MILLIS), "COMMAND must not also fire onPlayerMessage")
    }

    @Test
    fun `a malformed COMMAND payload is dropped rather than reaching either callback`() {
        val client = connectedClient("alice", codec)

        assertTrue(client.send("${ClientCommandCodec.COMMAND_VERB} not-json").isSuccess)

        assertNull(fixture.awaitCommand(NEGATIVE_TIMEOUT_MILLIS), "a payload that will not parse must be dropped")
        assertNull(
            fixture.awaitPlayerMessage(NEGATIVE_TIMEOUT_MILLIS),
            "a malformed COMMAND must not fall through to onPlayerMessage"
        )
    }

    @Test
    fun `a COMMAND datagram reaches onPlayerMessage verbatim when no command codec is configured`() {
        val client = connectedClient("alice", commandCodec = null)
        val raw = codec.encode(Stop(EntityId(5)))

        assertTrue(client.send(raw).isSuccess)

        assertEquals("alice" to raw.trim(), fixture.awaitPlayerMessage())
        assertNull(fixture.awaitCommand(NEGATIVE_TIMEOUT_MILLIS), "with no codec there is no command to decode")
    }

    @Test
    fun `a non-COMMAND datagram still falls through to onPlayerMessage on a command-aware server`() {
        val client = connectedClient("alice", codec)

        assertTrue(client.send("CHAT hello there").isSuccess)

        assertEquals("alice" to "CHAT hello there", fixture.awaitPlayerMessage())
        assertNull(fixture.awaitCommand(NEGATIVE_TIMEOUT_MILLIS), "a non-COMMAND message must not fire onCommand")
    }

    /** Handshakes [name] against a freshly started server (optionally command-aware) and waits for the roster. */
    private fun connectedClient(name: String, commandCodec: ClientCommandCodec?): FakeClientHarness {
        fixture.startServer(maxConnections = 4, commandCodec = commandCodec)
        val client = fixture.client()
        assertTrue(client.handshake(name).isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")
        return client
    }

    companion object {
        /** Short wait for a callback that is expected *not* to fire. */
        private const val NEGATIVE_TIMEOUT_MILLIS = 500L
    }
}
