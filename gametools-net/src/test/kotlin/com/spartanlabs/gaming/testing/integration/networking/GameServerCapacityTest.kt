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

/**
 * Covers the `maxConnections` cap.
 *
 * The cap can only ever be enforced after the fact: `MultiConnectionUDPServer` registers the
 * connection and answers the handshake before it consults [GameServer], so an over-cap player
 * still receives a `REGISTERED` reply and is dropped immediately afterwards. These tests assert on
 * the roster rather than on the reply for exactly that reason.
 */
class GameServerCapacityTest {

    private val fixture = ServerFixture()

    @AfterTest
    fun tearDown() = fixture.close()

    @Test
    fun `players beyond maxConnections are refused`() {
        val server = fixture.startServer(maxConnections = 2)

        assertTrue(fixture.client().handshake("p1").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))
        assertTrue(fixture.client().handshake("p2").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 2))
        assertTrue(fixture.client().handshake("p3").isSuccess, "the server still answers a doomed handshake")
        fixture.settle()

        assertEquals(2, server.playerCount, "the cap should have held")
        assertEquals(setOf("p1", "p2"), server.playerNames, "the refused player must not be tracked")
    }

    @Test
    fun `a refusal does not evict the players already connected`() {
        val server = fixture.startServer(maxConnections = 1)

        assertTrue(fixture.client().handshake("keeper").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))
        assertTrue(fixture.client().handshake("intruder").isSuccess)
        fixture.settle()

        assertEquals(setOf("keeper"), server.playerNames, "the sitting player should be untouched")
    }

    @Test
    fun `a returning player is admitted even when the server is full`() {
        val server = fixture.startServer(maxConnections = 1)

        assertTrue(fixture.client().handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1))
        assertTrue(fixture.client().handshake("alice").isSuccess, "a reconnect takes her own slot back")
        fixture.settle()

        assertEquals(1, server.playerCount)
        assertEquals(setOf("alice"), server.playerNames)
    }

    @Test
    fun `a server with no capacity admits nobody`() {
        val server = fixture.startServer(maxConnections = 0)

        assertTrue(fixture.client().handshake("alice").isSuccess)
        fixture.settle()

        assertEquals(0, server.playerCount)
        assertEquals(emptySet(), server.playerNames)
    }
}
