package com.spartanlabs.gaming.testing.integration.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.map.StaticGeometry
import com.spartanlabs.gaming.world.map.TerrainLayer
import com.spartanlabs.gaming.world.map.TerrainType
import com.spartanlabs.gaming.world.map.TiledMap
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.Zone
import com.spartanlabs.gaming.world.zone.ZoneGrid
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
//endregion

/**
 * Level 3 - integration. A real [ZoneIndex] stepped by the real [World] registry
 * ([World.installSystem] / [World.stepSystems] / [World.uninstallSystem]) beside fake systems:
 * the `PHYSICS` -> `ZONE` -> tier-2 step order whatever the install order, a `PHYSICS`-slot move
 * observed by the index in the same pass, uninstall stopping further steps, and a tier-2 system
 * that holds only its [World] finding the index through [World.systemOf].
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexOrderingIntegrationTest {

    private val grass = TerrainType(walkable = true, movementCost = 1.0, heightLevel = 0, blocksVision = false)

    /** A 4x3 tile, all-grass map - 40x30 world units, tileSize 10. */
    private fun fixtureMap(): TiledMap = TiledMap(
        widthTiles = 4,
        heightTiles = 3,
        tileSize = 10.0,
        terrain = TerrainLayer(widthTiles = 4, heightTiles = 3, tileTypeIndices = List(12) { 0 }, palette = listOf(grass)),
        staticGeometry = StaticGeometry(emptyList()),
    )

    /** Appends [name] to [trace] on every [step], then runs [onStep]. */
    private class TracingSystem(
        private val name: String,
        override val coreSlot: CoreSystemSlot?,
        private val trace: MutableList<String>,
        var onStep: () -> Unit = {},
    ) : AbstractWorldSystem() {
        override fun step() {
            trace += name
            onStep()
        }
    }

    /** A tier-2 system that reads this frame's zone of one actor through the World alone. */
    private class PeerReader(private val actorId: () -> EntityId, private val sink: MutableList<Zone?>) : AbstractWorldSystem() {
        override fun step() {
            sink += lookup(world)?.zoneOf(actorId())?.getOrNull()
        }
    }

    /** The index cannot write to a trace itself, so every zone event appends "zone". */
    private fun traceZoneEvents(world: World, trace: MutableList<String>) {
        world.events.subscribe { if (it is EntityChangedZone) trace += "zone" }
    }

    private fun recorder(world: World): MutableList<EntityChangedZone> =
        mutableListOf<EntityChangedZone>().also { log -> world.events.subscribe { if (it is EntityChangedZone) log += it } }

    @Test
    fun `a PHYSICS-slot system installed after ZoneIndex still steps before it`() {
        val world = World().apply { space = fixtureMap() }
        val trace = mutableListOf<String>()
        traceZoneEvents(world, trace)
        world.installSystem(ZoneIndex(ZoneGrid(fixtureMap(), columns = 4, rows = 3)))
        world.installSystem(TracingSystem("physics", CoreWorldSystemSlot.PHYSICS, trace))
        world.add(Actor(location = Point(5.0, 5.0)))

        world.stepSystems()

        assertEquals(listOf("physics", "zone"), trace)
    }

    @Test
    fun `a tier-2 system installed before ZoneIndex still steps after it`() {
        val world = World().apply { space = fixtureMap() }
        val trace = mutableListOf<String>()
        traceZoneEvents(world, trace)
        world.installSystem(TracingSystem("tier2", null, trace))
        world.installSystem(ZoneIndex(ZoneGrid(fixtureMap(), columns = 4, rows = 3)))
        world.add(Actor(location = Point(5.0, 5.0)))

        world.stepSystems()

        assertEquals(listOf("zone", "tier2"), trace)
    }

    @Test
    fun `a PHYSICS-slot move that crosses a zone boundary is observed by ZoneIndex in the same stepSystems call`() {
        val map = fixtureMap()
        val grid = ZoneGrid(map, columns = 4, rows = 3)
        val world = World().apply { space = map }
        val index = ZoneIndex(grid).also(world::installSystem) // installed first on purpose
        val physics = TracingSystem("physics", CoreWorldSystemSlot.PHYSICS, mutableListOf())
        world.installSystem(physics)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        val events = recorder(world)
        val zoneA = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        val zoneB = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()

        world.stepSystems() // physics idle: places the actor in zone A
        assertEquals(listOf(EntityChangedZone(actor, null, zoneA)), events)
        events.clear()

        physics.onStep = { actor.location.setTo(15.0, 5.0) }
        world.stepSystems()

        assertEquals(listOf(EntityChangedZone(actor, zoneA, zoneB)), events) // this call, not one later
        assertEquals(zoneB, index.zoneOf(actor.entityId).getOrNull())
    }

    @Test
    fun `uninstallSystem removes ZoneIndex from installedSystems and stops further steps`() {
        val map = fixtureMap()
        val grid = ZoneGrid(map, columns = 4, rows = 3)
        val world = World().apply { space = map }
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        val events = recorder(world)
        world.stepSystems()
        assertEquals(1, events.size)
        val zoneBefore = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()

        world.uninstallSystem(index)
        assertFalse(index in world.installedSystems)
        actor.location.setTo(15.0, 5.0)
        world.stepSystems()

        assertEquals(1, events.size)
        assertEquals(zoneBefore, index.zoneOf(actor.entityId).getOrNull())
    }

    @Test
    fun `a tier-2 system holding only its World finds the ZoneIndex through systemOf and reads this frame's placement`() {
        val map = fixtureMap()
        val grid = ZoneGrid(map, columns = 4, rows = 3)
        val world = World().apply { space = map }
        val sink = mutableListOf<Zone?>()
        lateinit var actor: Actor
        world.installSystem(PeerReader({ actor.entityId }, sink)) // tier 2, installed BEFORE the index
        world.installSystem(ZoneIndex(grid))
        actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()
        assertEquals(listOf<Zone?>(grid.zoneAt(Point(5.0, 5.0)).getOrThrow()), sink)

        actor.location.setTo(15.0, 5.0)
        world.stepSystems()
        assertEquals(
            listOf<Zone?>(grid.zoneAt(Point(5.0, 5.0)).getOrThrow(), grid.zoneAt(Point(15.0, 5.0)).getOrThrow()),
            sink,
        )
    }
}

/** The installed ZoneIndex found by role, or null when the lookup's Result is a failure. */
@OptIn(ExperimentalGameToolsApi::class)
private fun lookup(world: World): ZoneIndex? = world.systemOf<ZoneIndex>().getOrNull()
