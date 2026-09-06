package com.spartanlabs.gaming.testing.e2e

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.ActorSnapshot
import com.spartanlabs.gaming.gameobjects.DrawableSnapshot
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.VisibleObjectSnapshot
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.networking.GameServer
import com.spartanlabs.gaming.networking.MouseAction
import com.spartanlabs.gaming.networking.MouseActionType
import com.spartanlabs.gaming.testing.integration.networking.FakeClientHarness
//endregion

//region 2. Intended Function
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.concurrent.Volatile
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Level 4b - end-to-end system integration. Drives the whole stack in one flow: a fake
 * client handshakes over real loopback sockets, sends an `INPUT` datagram, the server decodes
 * it and feeds a [World], an external tick advances the simulation, the server snapshots and
 * broadcasts that world as polymorphic JSON, and the client decodes the `STATE` datagram back
 * into [DrawableSnapshot]s whose values are asserted against what the simulation produced.
 *
 * The single-hop pieces are covered in [com.spartanlabs.gaming.testing.integration.networking];
 * this is about them composing correctly across the full client -> server -> simulation ->
 * client loop.
 *
 * Needs the fixed common UDP ports free, so a running `GameServer` from another process (e.g.
 * a live game server) makes these fail to bind - the same environmental constraint the
 * integration tests carry.
 */
class ClientServerRoundTripTest {

    private val world = World()
    private val hero = Actor(location = Point(0.0, 0.0)).also(world::add)

    // Written on the server's per-player listener thread, read on the test thread.
    @Volatile private var lastInput: MouseAction? = null
    @Volatile private var lastMessage: String? = null

    private val server = GameServer(
        maxConnections = 4,
        onPlayerMessage = { _, message -> lastMessage = message },
        onPlayerInput = { _, action ->
            lastInput = action
            hero.destination = Point(action.x, action.y)
        }
    )

    private val harness = FakeClientHarness()

    @AfterTest
    fun tearDown() {
        harness.close()
        server.shutDown()
    }

    /** Handshakes a player and waits for the server to admit it. */
    private fun connect(name: String) {
        harness.handshake(name).getOrThrow()
        assertTrue(await { server.playerCount == 1 }, "the handshaken player was never admitted")
    }

    /** Ticks the world once and broadcasts its visible objects, the way an external game loop would. */
    private fun simulateAndBroadcastFrame() {
        world.tick()
        server.broadcast(world.gameObjects.filterIsInstance<VisibleObject>()).getOrThrow()
    }

    /** Reads one `STATE` datagram off [harness] and decodes its world-state payload. */
    private fun FakeClientHarness.receiveWorldState(): List<DrawableSnapshot> {
        val message = receive().getOrThrow()
        val prefix = "${GameServer.STATE_VERB} "
        assertTrue(message.startsWith(prefix), "expected a STATE broadcast but got: $message")
        return Json.decodeFromString<List<DrawableSnapshot>>(message.removePrefix(prefix))
    }

    private fun await(timeoutMillis: Long = 4000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(15)
        }
        return condition()
    }

    @Test
    fun `a mouse input travels the full stack into the simulation and back out as world state`() {
        connect("hero")

        harness.send(GameServer.inputMessage(MouseAction(MouseActionType.MOVE, button = -1, x = 100.0, y = 0.0)))
            .getOrThrow()

        assertTrue(await { lastInput != null }, "the input never reached the server's onPlayerInput")
        assertEquals(100.0, hero.destination.x, "the decoded input should have re-aimed the hero")

        simulateAndBroadcastFrame()

        val snapshot = assertIs<ActorSnapshot>(harness.receiveWorldState().single())
        // one tick at speed 10 from (0,0) towards (100,0)
        assertEquals(10.0, snapshot.visibleObject.gameObject.location.x, absoluteTolerance = 1e-9)
        assertEquals(0.0, snapshot.visibleObject.gameObject.location.y, absoluteTolerance = 1e-9)
        assertEquals(100.0, snapshot.destination.x, "the broadcast should carry the hero's destination")
    }

    @Test
    fun `a hidden object never reaches the client through the full pipe`() {
        connect("hero")
        world.add(VisibleObject(texture = "hud.png").apply { visible = false })
        world.add(VisibleObject(texture = "wall.png"))

        simulateAndBroadcastFrame()

        val state = harness.receiveWorldState()
        val textures = state.filterIsInstance<VisibleObjectSnapshot>().map { it.texture }
        assertEquals(2, state.size, "only the hero and the wall are visible")
        assertTrue("wall.png" in textures, "the visible wall should be broadcast")
        assertFalse("hud.png" in textures, "the hidden HUD must never be broadcast")
    }

    @Test
    fun `the simulation runs to completion over many frames and the final broadcast shows the hero arrived`() {
        connect("hero")

        harness.send(GameServer.inputMessage(MouseAction(MouseActionType.PRESS, button = 0, x = 35.0, y = 0.0)))
            .getOrThrow()
        assertTrue(await { hero.destination.x == 35.0 }, "the input never reached the simulation")

        lateinit var finalState: List<DrawableSnapshot>
        repeat(10) {
            simulateAndBroadcastFrame()
            finalState = harness.receiveWorldState()
        }

        val snapshot = assertIs<ActorSnapshot>(finalState.single())
        assertEquals(35.0, snapshot.visibleObject.gameObject.location.x, absoluteTolerance = 1e-9)
        assertEquals(35.0, hero.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `every broadcast object carries a stable id that survives objects spawning around it`() {
        connect("hero")

        val firstState = run { simulateAndBroadcastFrame(); harness.receiveWorldState() }
        val heroId = assertIs<ActorSnapshot>(firstState.single()).id
        assertTrue(heroId != 0L, "the broadcast hero should carry a world-assigned id")
        assertEquals(hero.entityId.raw, heroId, "the wire id should match the simulation's EntityId")

        // A reinforcement spawns before the next frame - the hero's list position shifts, its id must not.
        world.add(Actor(location = Point(500.0, 500.0)))
        simulateAndBroadcastFrame()
        val secondState = harness.receiveWorldState()

        assertEquals(2, secondState.size)
        val heroAfter = secondState.map { it.id }.single { it == heroId }
        assertEquals(heroId, heroAfter, "the hero keeps its id after another object joins the world")
        assertSame(hero, world.byId(hero.entityId), "the server can still resolve the hero by that id")
    }

    @Test
    fun `a malformed INPUT frame is dropped without breaking the connection or the frames after it`() {
        connect("hero")

        harness.send("${GameServer.INPUT_VERB} {this is not json}").getOrThrow()
        harness.send("chat hello").getOrThrow()

        assertTrue(await { lastMessage == "chat hello" }, "a normal message sent after a bad INPUT was not delivered")
        assertEquals(null, lastInput, "a malformed INPUT must not reach onPlayerInput")
        assertEquals(1, server.playerCount, "the player should still be connected after a bad frame")

        harness.send(GameServer.inputMessage(MouseAction(MouseActionType.MOVE, button = -1, x = 5.0, y = 6.0)))
            .getOrThrow()
        assertTrue(await { lastInput != null }, "a valid INPUT after a malformed one was not processed")
    }
}
