package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.networking.GameServer
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Covers [GameServer.broadcast] leaving non-visible objects out of the world-state message and
 * tagging an [Actor] / [Alive] with its own snapshot type on the wire.
 */
class GameServerBroadcastTest {

    private val fixture = ServerFixture()

    @AfterTest
    fun tearDown() = fixture.close()

    @Test
    fun `a non-visible object is left out of the broadcast`() {
        val server = fixture.startServer(maxConnections = 4)
        val client = fixture.client()
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")

        val shown = VisibleObject(texture = "shown.png")
        val hidden = VisibleObject(texture = "hidden.png").apply { visible = false }
        assertTrue(server.broadcast(listOf(shown, hidden)).isSuccess)

        val message = client.receive().getOrThrow()
        assertTrue(message.contains("shown.png"), "the visible object should be broadcast")
        assertFalse(message.contains("hidden.png"), "the hidden object must not be broadcast")
    }

    @Test
    fun `an Actor is broadcast as an actor snapshot`() {
        val server = fixture.startServer(maxConnections = 4)
        val client = fixture.client()
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")

        val actor = Actor(location = Point(5.0, 6.0)).apply { destination = Point(20.0, 6.0) }
        assertTrue(server.broadcast(listOf(actor)).isSuccess)

        val message = client.receive().getOrThrow()
        assertTrue(message.contains("\"type\":\"actor\""), "expected an actor-tagged snapshot: $message")
        assertTrue(message.contains("destination"), "actor snapshots carry a destination: $message")
    }

    @Test
    fun `an Alive is broadcast as an alive snapshot`() {
        val server = fixture.startServer(maxConnections = 4)
        val client = fixture.client()
        assertTrue(client.handshake("alice").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 1), "the player was never admitted")

        val unit = Alive(location = Point(5.0, 6.0), dimensions = Dimensions(4.0, 4.0), maxHealth = 100.0)
            .apply { faction = "red" }
        assertTrue(server.broadcast(listOf(unit)).isSuccess)

        val message = client.receive().getOrThrow()
        assertTrue(message.contains("\"type\":\"alive\""), "expected an alive-tagged snapshot: $message")
        assertTrue(message.contains("\"faction\":\"red\""), "alive snapshots carry the faction: $message")
    }

    @Test
    fun `two players each receive only the message pushed to them`() {
        val server = fixture.startServer(maxConnections = 4)

        val p1 = fixture.client()
        val p2 = fixture.client()
        assertTrue(p1.handshake("p1").isSuccess)
        assertTrue(p2.handshake("p2").isSuccess)
        assertTrue(fixture.awaitPlayers(expected = 2), "both players were never admitted")

        assertTrue(server.push("p1", "for-p1").isSuccess)
        assertTrue(server.push("p2", "for-p2").isSuccess)

        assertEquals("for-p1", p1.receive().getOrThrow(), "p1 gets only p1's message")
        assertEquals("for-p2", p2.receive().getOrThrow(), "p2 gets only p2's message")
    }
}
