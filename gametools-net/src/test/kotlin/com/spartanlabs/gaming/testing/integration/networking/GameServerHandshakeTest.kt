package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.networking.GameServer
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers how [GameServer] turns a completed `Iam` handshake into a tracked player. */
class GameServerHandshakeTest {

    private val fixture = ServerFixture()

    @AfterTest
    fun tearDown() = fixture.close()

    @Test
    fun `a client that completes the handshake becomes a tracked player`() {
        val server = fixture.startServer(maxConnections = 4)

        val handshake = fixture.client().handshake("alice")

        assertTrue(handshake.isSuccess, "handshake failed: ${handshake.exceptionOrNull()}")
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")
        assertEquals(1, server.playerCount)
        assertEquals(setOf("alice"), server.playerNames)
    }

    @Test
    fun `reconnecting under the same name from a new origin does not count twice`() {
        val server = fixture.startServer(maxConnections = 4)

        assertTrue(fixture.client().handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))
        assertTrue(fixture.client().handshake("alice").isSuccess, "a returning player should be let back in")
        fixture.settle()

        assertEquals(1, server.playerCount, "a reconnect replaces the old connection")
        assertEquals(setOf("alice"), server.playerNames)
    }

    @Test
    fun `a retransmitted Iam from the same origin does not register a second player`() {
        val server = fixture.startServer(maxConnections = 4)
        val client = fixture.client()

        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))
        assertTrue(client.handshake("alice").isSuccess)
        fixture.settle()

        assertEquals(1, server.playerCount, "a retransmit registers nothing new")
        assertEquals(setOf("alice"), server.playerNames)
    }

    @Test
    fun `an unrecognised message is ignored rather than admitting a player`() {
        val server = fixture.startServer(maxConnections = 4)

        // "Iam" is the only verb the handshake port acts on; anything else is noise.
        val client = fixture.client()
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))

        assertEquals(1, server.playerCount, "only the real handshake should have registered")
    }
}
