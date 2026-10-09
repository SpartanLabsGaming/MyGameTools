package com.spartanlabs.gaming.testing.component.world.zone

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.UnzonedEntityException
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [ZoneIndex.step]'s recompute-and-diff bookkeeping, driven through the
 * real registry ([World.installSystem] + [World.stepSystems]): zone placement, boundary
 * crossings, skipping unnumbered entities, entities leaving the grid, entities leaving the
 * [World], [ZoneIndex.zoneOf]'s `Result` shape, and [ZoneIndex.entitiesIn]'s defensive-copy
 * contract.
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexTest {

    /** Asserts [id] is unzoned: zoneOf is a failure carrying an UnzonedEntityException for exactly [id]. */
    private fun ZoneIndex.assertUnzoned(id: EntityId) {
        val result = zoneOf(id)
        assertTrue(result.isFailure, "expected $id to be unzoned, but zoneOf returned ${result.getOrNull()}")
        assertEquals(id, assertIs<UnzonedEntityException>(result.exceptionOrNull()).entityId)
    }

    @Test
    fun `a fresh ZoneIndex before any step reports every entity unzoned and every zone empty`() {
        val grid = fixtureGrid()
        val index = ZoneIndex(grid) // never installed: the reads must not touch world

        index.assertUnzoned(EntityId(1L))
        assertTrue(index.entitiesIn(grid.zones.first()).isEmpty())
    }

    @Test
    fun `one step places entities in the expected zones`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(15.0, 5.0)).also(world::add)

        world.stepSystems()

        val expectedZone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        assertEquals(expectedZone, index.zoneOf(actor.entityId).getOrNull())
        assertEquals(setOf(actor.entityId), index.entitiesIn(expectedZone))
    }

    @Test
    fun `a second step after crossing a zone boundary updates both the old and new zone`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()
        val oldZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(setOf(actor.entityId), index.entitiesIn(oldZone))

        actor.location.setTo(15.0, 5.0)
        world.stepSystems()
        val newZone = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()

        assertEquals(newZone, index.zoneOf(actor.entityId).getOrNull())
        assertTrue(index.entitiesIn(oldZone).isEmpty())
        assertEquals(setOf(actor.entityId), index.entitiesIn(newZone))
    }

    @Test
    fun `an entity still at EntityId UNASSIGNED is skipped, not indexed`() {
        val world = World()
        val index = ZoneIndex(fixtureGrid()).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0))
        world.gameObjects += actor // not numbered yet - neither World.add nor World.tick has run

        world.stepSystems()

        assertEquals(EntityId.UNASSIGNED, actor.entityId)
        index.assertUnzoned(actor.entityId) // the miss carries EntityId.UNASSIGNED
    }

    @Test
    fun `an entity moved outside the grid's extent is dropped from entitiesIn and reported unzoned`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()
        val originalZone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()

        actor.location.setTo(-100.0, -100.0)
        world.stepSystems()

        index.assertUnzoned(actor.entityId)
        assertTrue(index.entitiesIn(originalZone).isEmpty())
    }

    @Test
    fun `a removed entity is dropped from the index on the next step`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(setOf(actor.entityId), index.entitiesIn(zone))

        world.removeList += actor
        world.tick()
        world.stepSystems()

        index.assertUnzoned(actor.entityId)
        assertTrue(index.entitiesIn(zone).isEmpty())
    }

    @Test
    fun `entitiesIn returns a copy - mutating it does not affect the index`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)

        world.stepSystems()
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()

        val copy = index.entitiesIn(zone).toMutableSet()
        copy.clear()

        assertEquals(setOf(actor.entityId), index.entitiesIn(zone))
    }

    @Test
    fun `a scripted multi-actor path publishes exactly the expected EntityChangedZone sequence`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val events = recorder(world)
        val a0 = Actor(location = Point(5.0, 5.0)).also(world::add)
        val a1 = Actor(location = Point(35.0, 25.0)).also(world::add)
        fun zone(name: String) = grid.zones.single { it.name == name }
        val z00 = zone("zone-0-0")
        val z10 = zone("zone-1-0")
        val z11 = zone("zone-1-1")
        val z30 = zone("zone-3-0")
        val z22 = zone("zone-2-2")
        val z02 = zone("zone-0-2")
        val z32 = zone("zone-3-2")

        // No world.tick(): nothing but the test moves the actors. One stepSystems() per tick.
        world.stepSystems() // tick 1: a0 (5,5), a1 (35,25)
        a0.location.setTo(15.0, 5.0)
        world.stepSystems() // tick 2: a0 crosses; a1 unchanged
        a0.location.setTo(15.0, 15.0)
        a1.location.setTo(-100.0, -100.0)
        world.stepSystems() // tick 3: a0 crosses; a1 leaves the extent
        index.assertUnzoned(a1.entityId)
        a1.location.setTo(25.0, 25.0)
        world.stepSystems() // tick 4: a0 unchanged; a1 re-enters from null
        a0.location.setTo(35.0, 5.0)
        a1.location.setTo(5.0, 25.0)
        world.stepSystems() // tick 5: both cross

        assertEquals(
            listOf(
                EntityChangedZone(a0, null, z00), EntityChangedZone(a1, null, z32),
                EntityChangedZone(a0, z00, z10),
                EntityChangedZone(a0, z10, z11), EntityChangedZone(a1, z32, null),
                EntityChangedZone(a1, null, z22),
                EntityChangedZone(a0, z11, z30), EntityChangedZone(a1, z22, z02),
            ),
            events,
        )
        assertEquals(z30, index.zoneOf(a0.entityId).getOrNull())
        assertEquals(z02, index.zoneOf(a1.entityId).getOrNull())
    }

    @Test
    fun `zoneOf an id no World ever assigned is a failure carrying exactly that id`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        world.stepSystems()

        index.assertUnzoned(EntityId(999L))

        // A miss for one id says nothing about another.
        assertEquals(grid.zoneAt(Point(5.0, 5.0)).getOrThrow(), index.zoneOf(actor.entityId).getOrNull())
    }

    @Test
    fun `a listener that spawns one object and removes another during a step does not disturb it, and the spawned object is placed on the next step`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val trigger = Actor(location = Point(5.0, 5.0)).also(world::add)
        val victim = Actor(location = Point(15.0, 5.0)).also(world::add)
        val spawned = Actor(location = Point(25.0, 5.0))
        var reacted = false
        world.events.subscribe { event ->
            if (event is EntityChangedZone && !reacted) {
                reacted = true
                world.add(spawned) // structural changes to World.gameObjects mid-step
                world.gameObjects.remove(victim)
            }
        }

        world.stepSystems() // must not throw: the step iterates a snapshot

        assertTrue(reacted)
        assertEquals(grid.zoneAt(Point(5.0, 5.0)).getOrThrow(), index.zoneOf(trigger.entityId).getOrNull())
        index.assertUnzoned(spawned.entityId) // added mid-step: not seen until the next step

        world.stepSystems()

        assertEquals(grid.zoneAt(Point(25.0, 5.0)).getOrThrow(), index.zoneOf(spawned.entityId).getOrNull())
        index.assertUnzoned(victim.entityId) // removed mid-step: reported as gone on the next step
    }

    @Test
    fun `a ZoneIndex still answers entitiesIn and zoneOf correctly after a zone's bounds are mutated in place`() {
        val world = World()
        val grid = fixtureGrid()
        val index = ZoneIndex(grid).also(world::installSystem)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        world.stepSystems()
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        val events = recorder(world)

        zone.bounds.location.setTo(500.0, 500.0) // callers should not, but it must not corrupt the index

        assertEquals(setOf(actor.entityId), index.entitiesIn(zone))
        assertEquals(zone, index.zoneOf(actor.entityId).getOrNull())

        actor.location.setTo(15.0, 5.0)
        world.stepSystems()
        val next = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()

        assertEquals(listOf(EntityChangedZone(actor, zone, next)), events)
        assertTrue(index.entitiesIn(zone).isEmpty())
        assertEquals(setOf(actor.entityId), index.entitiesIn(next))
    }
}
