package com.spartanlabs.gaming.testing.deterministic

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Level 4a - deterministic logic. Pins the laws of [World]'s per-world [EntityId] allocation
 * over a generated build script rather than the hand-picked cases in
 * [com.spartanlabs.gaming.testing.component.gameobjects.WorldEntityRegistryTest].
 */
class EntityIdSequenceLawsTest {

    /** Runs [script] against a fresh [World] and returns every id it handed out, in order. */
    private fun idsFrom(script: World.() -> Unit): List<Long> {
        val world = World()
        world.script()
        world.tick() // fold in any direct-list additions
        return world.gameObjects.map { it.entityId.raw }
    }

    /** A build script: add 50 actors, remove every third, add 20 more. */
    private val script: World.() -> Unit = {
        val added = (1..50).map { Actor(location = Point(it.toDouble(), 0.0)) }
        added.forEach(::add)
        added.filterIndexed { index, _ -> index % 3 == 0 }.forEach { removeList += it }
        tick()
        repeat(20) { add(Actor(location = Point(-it.toDouble(), 0.0))) }
    }

    @Test
    fun `ids are assigned as one two three in acquisition order`() {
        val ids = idsFrom {
            repeat(5) { add(Actor(location = Point(it.toDouble(), 0.0))) }
        }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids)
    }

    @Test
    fun `a removed id is never handed out again`() {
        val world = World()
        val doomed = Actor(location = Point(0.0, 0.0)).also(world::add) // id 1
        world.add(Actor(location = Point(1.0, 0.0)))                    // id 2
        world.removeList += doomed
        world.tick()
        world.add(Actor(location = Point(2.0, 0.0)))                    // must be id 3, not a reused 1

        assertTrue(world.gameObjects.none { it.entityId.raw == 1L }, "the removed id 1 must not reappear")
        assertEquals(listOf(2L, 3L), world.gameObjects.map { it.entityId.raw })
    }

    @Test
    fun `the same build script produces the same id-to-object mapping every run`() {
        fun mappingSignature(): List<Pair<Long, Double>> {
            val world = World()
            world.script()
            world.tick()
            return world.gameObjects.map { it.entityId.raw to (it as Actor).location.x }.sortedBy { it.first }
        }
        assertEquals(mappingSignature(), mappingSignature())
    }

    @Test
    fun `EntityId ordering matches raw ordering across a generated batch`() {
        val world = World()
        val actors = (1..30).map { Actor(location = Point(it.toDouble(), 0.0)) }.onEach(world::add)
        for (i in 0 until actors.size - 1) {
            assertTrue(actors[i].entityId < actors[i + 1].entityId)
        }
        assertTrue(actors.map(GameObject::entityId) == actors.map(GameObject::entityId).sorted())
    }
}
