package com.spartanlabs.gaming.testing.e2e.world.map

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.map.MapLoader
import com.spartanlabs.gaming.world.map.TiledMap
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
//endregion

/**
 * Level 4b - end-to-end system integration. Loads the `fixture-map.json` fixture through
 * [MapLoader.fromJson], assigns the result to a real [World.space], adds a couple of [Actor]s at
 * named spawn points, and runs several [World.tick]s with no [com.spartanlabs.gaming.simulation.SimulationLoop]
 * and no `GameServer` involved - proving the whole new surface composes with [World] without the
 * wire/loop layers.
 *
 * Deliberately does **not** assert any movement-blocked-by-`isWalkable` behaviour: that
 * enforcement does not exist until physics (issue #49) implements it, and [World.space] is
 * inert data until a system chooses to consult it (see [World.space]'s KDoc).
 */
class MapDrivenWorldE2ETest {

    /** Reads `src/test/resources/fixture-map.json` off the test classpath. */
    private fun fixtureJson(): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixture-map.json")) { "fixture-map.json not found on the test classpath" }
            .bufferedReader()
            .readText()

    @Test
    fun `a TiledMap-backed World ticks normally and its space still answers queries correctly afterward`() {
        val map: TiledMap = MapLoader.fromJson(fixtureJson()).getOrThrow()
        val world = World()
        world.space = map

        val redSpawn = map.spawnPoint("red-spawn")
        val blueSpawn = map.spawnPoint("blue-spawn")
        checkNotNull(redSpawn)
        checkNotNull(blueSpawn)

        val hero = Actor(location = redSpawn.position).also(world::add)
        val ally = Actor(location = blueSpawn.position).also(world::add)

        repeat(5) { world.tick() }

        assertEquals(5L, world.tickCount)
        assertTrue(hero in world.gameObjects)
        assertTrue(ally in world.gameObjects)
        assertTrue(world.spatialIndex.queryBox(-1.0, -1.0, 41.0, 31.0).containsAll(listOf<VisibleObject>(hero, ally)))

        // world.space still answers exactly as it did before any tick ran - purely descriptive data
        val sameMap = world.space
        checkNotNull(sameMap)
        assertTrue(sameMap === map)
        assertTrue((sameMap as TiledMap).isWalkable(redSpawn.position))
        assertFalse(sameMap.isWalkable(Point(15.0, 15.0)))
        assertEquals(redSpawn, sameMap.spawnPoint("red-spawn"))
    }
}
