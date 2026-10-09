package com.spartanlabs.gaming.testing.e2e.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.simulation.SimulationLoop
import com.spartanlabs.gaming.world.map.MapLoader
import com.spartanlabs.gaming.world.map.TiledMap
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
//endregion

/**
 * Level 4b - end-to-end system integration. The same scenario as [ZoneDrivenSimulationE2ETest],
 * driven instead by a real [SimulationLoop] whose `onTick` calls [World.stepSystems] - advanced
 * with [SimulationLoop.advance] (no thread, deterministic) - with the [ZoneIndex] installed on the
 * [World] and also reached through [World.systemOf].
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexSimulationLoopE2ETest {

    /** Reads `src/test/resources/fixture-map.json` off the test classpath. */
    private fun fixtureJson(): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("fixture-map.json")) { "fixture-map.json not found on the test classpath" }
            .bufferedReader()
            .readText()

    private fun recorder(world: World): MutableList<EntityChangedZone> =
        mutableListOf<EntityChangedZone>().also { log -> world.events.subscribe { if (it is EntityChangedZone) log += it } }

    /** The installed ZoneIndex found by role, or null when the lookup's Result is a failure. */
    private fun lookup(world: World): ZoneIndex? = world.systemOf<ZoneIndex>().getOrNull()

    @Test
    fun `a ZoneIndex installed on a World and driven by SimulationLoop advance tracks two spawned actors through several ticks`() {
        val map: TiledMap = MapLoader.fromJson(fixtureJson()).getOrThrow()
        val grid = ZoneGrid(map, columns = 4, rows = 3) // 40x30 map, 10x10 zones
        val world = World().apply { space = map }
        val index = ZoneIndex(grid).also(world::installSystem)
        val events = recorder(world)

        val redSpawn = checkNotNull(map.spawnPoint("red-spawn"))
        val blueSpawn = checkNotNull(map.spawnPoint("blue-spawn"))

        // Hero travels +x, 10 units/tick, crossing three zones; ally holds its spawn position.
        val hero = Actor(location = Point(redSpawn.position)).apply { movement = Movement.Directional; angle = 0 }
        val ally = Actor(location = Point(blueSpawn.position))
        world.add(hero)
        world.add(ally)

        val heroZone10 = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        val heroZone20 = grid.zoneAt(Point(25.0, 5.0)).getOrThrow()
        val heroZone30 = grid.zoneAt(Point(35.0, 5.0)).getOrThrow()
        val allyZone = grid.zoneAt(blueSpawn.position).getOrThrow()

        val loop = SimulationLoop(world, onTick = { world.stepSystems() })
        val nanosPerTick = (1_000_000_000.0 / loop.settings.tickRateHz).toLong()
        repeat(3) { loop.advance(nanosPerTick) }

        assertSame(index, lookup(world))
        assertEquals(heroZone30, index.zoneOf(hero.entityId).getOrNull())
        assertEquals(allyZone, index.zoneOf(ally.entityId).getOrNull())
        assertEquals(setOf(hero.entityId), index.entitiesIn(heroZone30))
        assertEquals(setOf(ally.entityId), index.entitiesIn(allyZone))

        assertContentEquals(
            listOf(
                EntityChangedZone(hero, null, heroZone10),
                EntityChangedZone(ally, null, allyZone),
                EntityChangedZone(hero, heroZone10, heroZone20),
                EntityChangedZone(hero, heroZone20, heroZone30),
            ),
            events,
        )
    }
}
