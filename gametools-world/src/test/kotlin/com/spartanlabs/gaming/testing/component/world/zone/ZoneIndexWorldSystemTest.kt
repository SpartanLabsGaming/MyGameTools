package com.spartanlabs.gaming.testing.component.world.zone

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
import com.spartanlabs.gaming.gameobjects.WorldSystem
import com.spartanlabs.gaming.world.zone.EntityChangedZone
import com.spartanlabs.gaming.world.zone.UnzonedEntityException
import com.spartanlabs.gaming.world.zone.ZoneIndex
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Level 2 - component. [ZoneIndex] *as a [WorldSystem]*: its slot and role, installing without
 * publishing, the pre-install failure of [ZoneIndex.step], lookup through [World.systemOf], the
 * slot rejection of a second index, the for-life binding, retained state across re-install, and
 * one [com.spartanlabs.gaming.world.zone.ZoneGrid] backing several indexes.
 */
@OptIn(ExperimentalGameToolsApi::class)
class ZoneIndexWorldSystemTest {

    /** A consumer's own ZONE-slot system that declares no role - the substitute case. */
    private class RoleLessZoneSystem : AbstractWorldSystem() {
        override val coreSlot: CoreSystemSlot = CoreWorldSystemSlot.ZONE
    }

    /** The installed ZoneIndex found by role, or null when the lookup's Result is a failure. */
    private fun lookup(world: World): ZoneIndex? = world.systemOf<ZoneIndex>().getOrNull()

    /** Asserts [id] is unzoned: zoneOf is a failure carrying an UnzonedEntityException for exactly [id]. */
    private fun ZoneIndex.assertUnzoned(id: EntityId) {
        val result = zoneOf(id)
        assertTrue(result.isFailure, "expected $id to be unzoned, but zoneOf returned ${result.getOrNull()}")
        assertEquals(id, assertIs<UnzonedEntityException>(result.exceptionOrNull()).entityId)
    }

    @Test
    fun `coreSlot is CoreWorldSystemSlot ZONE`() {
        assertEquals(CoreWorldSystemSlot.ZONE, ZoneIndex(fixtureGrid()).coreSlot)
    }

    @Test
    fun `uniqueRole is ZoneIndex itself`() {
        assertEquals(ZoneIndex::class, ZoneIndex(fixtureGrid()).uniqueRole)
    }

    @Test
    fun `installing publishes nothing - the first stepSystems places the entity`() {
        val world = World()
        val grid = fixtureGrid()
        val events = recorder(world)
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        val index = ZoneIndex(grid)

        world.installSystem(index)

        assertTrue(events.isEmpty())
        index.assertUnzoned(actor.entityId)
        assertSame(world, index.world)
        assertTrue(index in world.installedSystems)

        world.stepSystems()

        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(listOf(EntityChangedZone(actor, null, zone)), events)
        assertEquals(zone, index.zoneOf(actor.entityId).getOrNull())
    }

    @Test
    fun `stepping an index that was never installed fails with IllegalStateException`() {
        val index = ZoneIndex(fixtureGrid())

        assertFailsWith<IllegalStateException> { index.step() }
    }

    @Test
    fun `systemOf returns the installed index as a success, and a failure before install and after uninstall`() {
        val world = World()
        val index = ZoneIndex(fixtureGrid())
        assertTrue(world.systemOf<ZoneIndex>().isFailure)
        assertNull(lookup(world))

        world.installSystem(index)
        assertSame(index, lookup(world))
        assertSame(index, world.systemOf(ZoneIndex::class).getOrNull()) // the KClass form gives the same answer

        world.uninstallSystem(index)
        assertTrue(world.systemOf<ZoneIndex>().isFailure)
    }

    @Test
    fun `a second ZoneIndex on the same World is rejected by the slot check naming ZONE`() {
        val world = World()
        val first = ZoneIndex(fixtureGrid()).also(world::installSystem)

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(ZoneIndex(fixtureGrid())) }

        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains("ZONE"))
        assertEquals(listOf<WorldSystem>(first), world.installedSystems)
        assertSame(first, lookup(world))
    }

    @Test
    fun `a ZoneIndex bound to one World is rejected by another, and the rejection changes nothing`() {
        val worldA = World()
        val worldB = World()
        val grid = fixtureGrid()
        val events = recorder(worldA)
        val actor = Actor(location = Point(5.0, 5.0)).also(worldA::add)
        val index = ZoneIndex(grid).also(worldA::installSystem)

        assertFailsWith<IllegalArgumentException> { worldB.installSystem(index) }

        assertTrue(worldB.installedSystems.isEmpty())
        worldA.stepSystems()
        val zone = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(listOf(EntityChangedZone(actor, null, zone)), events)
        assertEquals(zone, index.zoneOf(actor.entityId).getOrNull())
    }

    @Test
    fun `the binding is for life - rejected by another World even after uninstall - the bound World accepts it again`() {
        val worldA = World()
        val worldB = World()
        val index = ZoneIndex(fixtureGrid()).also(worldA::installSystem)
        worldA.uninstallSystem(index)

        assertFailsWith<IllegalArgumentException> { worldB.installSystem(index) }
        assertTrue(worldB.installedSystems.isEmpty())

        worldA.installSystem(index)
        assertTrue(index in worldA.installedSystems)
    }

    @Test
    fun `re-installing on the same World retains state and the next step publishes what changed meanwhile`() {
        val world = World()
        val grid = fixtureGrid()
        val actor = Actor(location = Point(5.0, 5.0)).also(world::add)
        val index = ZoneIndex(grid).also(world::installSystem)
        world.stepSystems()
        val zone1 = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        assertEquals(zone1, index.zoneOf(actor.entityId).getOrNull())

        world.uninstallSystem(index)
        val events = recorder(world)
        actor.location.setTo(15.0, 5.0)
        world.installSystem(index)
        world.stepSystems()

        val zone2 = grid.zoneAt(Point(15.0, 5.0)).getOrThrow()
        assertEquals(listOf(EntityChangedZone(actor, zone1, zone2)), events) // from is the retained zone
    }

    @Test
    fun `uninstalling a never-installed ZoneIndex is a no-op and binds nothing`() {
        val worldA = World()
        val worldB = World()
        val index = ZoneIndex(fixtureGrid())

        worldA.uninstallSystem(index) // must not throw

        worldB.installSystem(index) // still unbound, so another World accepts it
        assertSame(worldB, index.world)
    }

    @Test
    fun `one ZoneGrid backs two indexes on two Worlds without interference`() {
        val grid = fixtureGrid()
        val worldA = World()
        val worldB = World()
        val eventsA = recorder(worldA)
        val eventsB = recorder(worldB)
        val actorA = Actor(location = Point(5.0, 5.0)).also(worldA::add)
        val actorB = Actor(location = Point(35.0, 25.0)).also(worldB::add)
        assertEquals(actorA.entityId, actorB.entityId) // both Worlds number from the same start
        val indexA = ZoneIndex(grid).also(worldA::installSystem)
        val indexB = ZoneIndex(grid).also(worldB::installSystem)

        worldA.stepSystems()
        worldB.stepSystems()

        val zoneA = grid.zoneAt(Point(5.0, 5.0)).getOrThrow()
        val zoneB = grid.zoneAt(Point(35.0, 25.0)).getOrThrow()
        assertEquals(listOf(EntityChangedZone(actorA, null, zoneA)), eventsA)
        assertEquals(listOf(EntityChangedZone(actorB, null, zoneB)), eventsB)
        val id = actorA.entityId
        assertEquals(zoneA, indexA.zoneOf(id).getOrNull())
        assertEquals(zoneB, indexB.zoneOf(id).getOrNull())
        assertNotEquals(indexA.zoneOf(id).getOrNull(), indexB.zoneOf(id).getOrNull())
    }

    @Test
    fun `a substitute ZONE-slot system is not found under ZoneIndex's class, and blocks installing a ZoneIndex`() {
        val world = World()
        world.installSystem(RoleLessZoneSystem())

        assertTrue(world.systemOf<ZoneIndex>().isFailure)
        assertNull(lookup(world))
        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(ZoneIndex(fixtureGrid())) }
        assertTrue(ex.message!!.contains("ZONE"))
    }

    @Test
    fun `ZoneIndex declares no refresh member`() {
        assertTrue(ZoneIndex::class.java.declaredMethods.none { it.name.startsWith("refresh") })
    }
}
