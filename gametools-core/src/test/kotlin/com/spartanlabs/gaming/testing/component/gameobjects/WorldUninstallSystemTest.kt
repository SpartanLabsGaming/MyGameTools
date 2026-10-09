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

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/** Covers [World.uninstallSystem]'s remove-then-notify ordering, idempotency, and kept binding. */
@OptIn(ExperimentalGameToolsApi::class)
class WorldUninstallSystemTest {

    /** A [WorldSystem] whose [onUninstalled] is fully configurable, for exercising one behaviour at a time. */
    private open class RecordingSystem(
        override val coreSlot: CoreSystemSlot? = null,
        private val onUninstall: (RecordingSystem) -> Unit = {},
    ) : AbstractWorldSystem() {
        var installCount = 0
            private set
        var uninstallCount = 0
            private set

        override fun onInstalled() {
            installCount++
        }

        override fun onUninstalled() {
            uninstallCount++
            onUninstall(this)
        }
    }

    @Test
    fun `the system is removed from installedSystems before onUninstalled runs`() {
        val world = World()
        var sawSelfAbsent = false
        val system = RecordingSystem(onUninstall = { self -> sawSelfAbsent = self !in self.world.installedSystems })
        world.installSystem(system)

        world.uninstallSystem(system)

        assertTrue(sawSelfAbsent)
    }

    @Test
    fun `uninstalling a system that was never installed is a no-op`() {
        val world = World()
        val system = RecordingSystem()

        world.uninstallSystem(system) // must not throw

        assertEquals(0, system.uninstallCount)
    }

    @Test
    fun `uninstallSystem is idempotent`() {
        val world = World()
        val system = RecordingSystem()
        world.installSystem(system)

        world.uninstallSystem(system)
        world.uninstallSystem(system)

        assertEquals(1, system.uninstallCount)
    }

    @Test
    fun `if onUninstalled throws, the system stays removed and the exception propagates`() {
        val world = World()
        val system = RecordingSystem(onUninstall = { error("boom") })
        world.installSystem(system)

        assertFailsWith<IllegalStateException> { world.uninstallSystem(system) }

        assertFalse(system in world.installedSystems)
    }

    @Test
    fun `uninstalling a tier-1 system frees its slot for a later install`() {
        val world = World()
        val first = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)
        world.installSystem(first)
        world.uninstallSystem(first)
        val second = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)

        world.installSystem(second) // must not throw

        assertTrue(second in world.installedSystems)
    }

    @Test
    fun `a system can be uninstalled and reinstalled`() {
        val world = World()
        val system = RecordingSystem()
        world.installSystem(system)
        world.uninstallSystem(system)

        world.installSystem(system)

        assertTrue(system in world.installedSystems)
        assertEquals(2, system.installCount)
        assertSame(world, system.world)
    }
}
