package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers how [World.installSystem] binds a [WorldSystem] to its [World]: an [AbstractWorldSystem]
 * is bound before its [WorldSystem.onInstalled] runs and for life, a direct implementor must
 * report this [World] as its [WorldSystem.world], and every binding rejection changes nothing.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemBindingTest {

    /** An [AbstractWorldSystem] that records what it saw from its hooks. */
    private class Probe(
        override val coreSlot: CoreSystemSlot? = null,
        override val uniqueRole: KClass<out WorldSystem>? = null,
    ) : AbstractWorldSystem() {
        var installCount = 0
        var worldSeenOnInstall: World? = null
        var worldSeenOnStep: World? = null

        override fun onInstalled() {
            installCount++
            worldSeenOnInstall = world
        }

        override fun step() {
            worldSeenOnStep = world
        }
    }

    /** A direct implementor whose [world] getter is configurable and counts its reads. */
    private class Direct(
        private val supplier: () -> World,
        private val slot: CoreSystemSlot? = null,
    ) : WorldSystem {
        var worldReads = 0
        var slotReads = 0
        var roleReads = 0
        var installCount = 0

        override val world: World
            get() {
                worldReads++
                return supplier()
            }

        override val coreSlot: CoreSystemSlot?
            get() {
                slotReads++
                return slot
            }

        override val uniqueRole: KClass<out WorldSystem>?
            get() {
                roleReads++
                return null
            }

        override fun onInstalled() {
            installCount++
        }
    }

    @Test
    fun `the system is bound before onInstalled runs`() {
        val world = World()
        val system = Probe()

        world.installSystem(system)

        assertSame(world, system.worldSeenOnInstall)
    }

    @Test
    fun `world is readable from step`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        world.stepSystems()

        assertSame(world, system.worldSeenOnStep)
    }

    @Test
    fun `a system bound to one World is rejected by another, and the rejection changes nothing`() {
        val worldA = World()
        val worldB = World()
        val system = Probe(coreSlot = CoreWorldSystemSlot.PHYSICS)
        worldA.installSystem(system)

        val ex = assertFailsWith<IllegalArgumentException> { worldB.installSystem(system) }

        assertTrue(ex.message!!.contains("different World"))
        assertTrue(worldB.installedSystems.isEmpty())
        assertEquals(1, system.installCount)
        worldB.installSystem(Probe(coreSlot = CoreWorldSystemSlot.PHYSICS)) // B's slot is free
        assertSame(worldA, system.world)
    }

    @Test
    fun `the binding check runs before the slot check`() {
        val worldA = World()
        val worldB = World()
        val system = Probe(coreSlot = CoreWorldSystemSlot.PHYSICS)
        worldA.installSystem(system)
        worldB.installSystem(Probe(coreSlot = CoreWorldSystemSlot.PHYSICS))

        val ex = assertFailsWith<IllegalArgumentException> { worldB.installSystem(system) }

        assertTrue(ex.message!!.contains("different World"))
    }

    @Test
    fun `the binding is for life - re-install on the same World works, another World is still rejected`() {
        val worldA = World()
        val worldB = World()
        val system = Probe()
        worldA.installSystem(system)
        worldA.uninstallSystem(system)

        assertFailsWith<IllegalArgumentException> { worldB.installSystem(system) }
        worldA.installSystem(system)

        assertTrue(system in worldA.installedSystems)
        assertSame(worldA, system.world)
        assertEquals(2, system.installCount)
        assertTrue(worldB.installedSystems.isEmpty())
    }

    @Test
    fun `a direct implementor whose world is this World installs`() {
        val world = World()
        val system = Direct({ world })

        world.installSystem(system)

        assertEquals(listOf<WorldSystem>(system), world.installedSystems)
        assertEquals(1, system.installCount)
    }

    @Test
    fun `a direct implementor whose world is another World is rejected with IllegalArgumentException`() {
        val world = World()
        val other = World()
        val system = Direct({ other })

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertTrue(world.installedSystems.isEmpty())
        assertEquals(0, system.installCount)
    }

    @Test
    fun `a direct implementor whose world getter throws propagates that very exception, records nothing, and reads neither coreSlot nor uniqueRole`() {
        val world = World()
        val failure = IllegalStateException("no world yet")
        val system = Direct({ throw failure }, slot = CoreWorldSystemSlot.PHYSICS)

        val thrown = assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertSame(failure, thrown)
        assertTrue(world.installedSystems.isEmpty())
        assertEquals(0, system.slotReads)
        assertEquals(0, system.roleReads)
        assertEquals(0, system.installCount)
    }

    @Test
    fun `the identity check runs before the binding check - an installed direct implementor is rejected as already installed without reading world`() {
        val world = World()
        var getterThrows = false
        val system = Direct({ if (getterThrows) throw IllegalStateException("world must not be read") else world })
        world.installSystem(system)
        val readsAfterInstall = system.worldReads
        val before = world.installedSystems
        getterThrows = true

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertTrue(ex.message!!.contains("already installed"))
        assertEquals(readsAfterInstall, system.worldReads)
        assertEquals(before, world.installedSystems)
    }

    @Test
    fun `a direct implementor's world is read on each install attempt`() {
        val world = World()
        val system = Direct({ world })

        world.installSystem(system)
        world.uninstallSystem(system)
        world.installSystem(system)

        assertEquals(2, system.worldReads)
    }

    @Test
    fun `a system rejected as already installed keeps its existing binding unchanged`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertSame(world, system.boundWorldOrNull())
    }

    @Test
    fun `a system rejected by the slot check stays unbound`() {
        val world = World()
        world.installSystem(Probe(coreSlot = CoreWorldSystemSlot.PHYSICS))
        val rejected = Probe(coreSlot = CoreWorldSystemSlot.PHYSICS)

        assertFailsWith<IllegalArgumentException> { world.installSystem(rejected) }

        assertNull(rejected.boundWorldOrNull())
    }

    @Test
    fun `a system rejected by the role check stays unbound`() {
        val world = World()
        world.installSystem(Probe(uniqueRole = Probe::class))
        val rejected = Probe(uniqueRole = Probe::class)

        assertFailsWith<IllegalArgumentException> { world.installSystem(rejected) }

        assertNull(rejected.boundWorldOrNull())
    }
}
