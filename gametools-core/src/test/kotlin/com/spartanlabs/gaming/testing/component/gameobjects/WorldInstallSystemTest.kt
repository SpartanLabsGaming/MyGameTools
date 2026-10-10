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
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [World.installSystem]'s duplicate/slot checks, its record-then-notify ordering, the
 * roll-back of a throwing [WorldSystem.onInstalled], and re-entrant calls from inside that hook.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemTest {

    /** A [WorldSystem] whose [onInstalled] is fully configurable, for exercising one behaviour at a time. */
    private open class RecordingSystem(
        override val coreSlot: CoreSystemSlot? = null,
        override val uniqueRole: KClass<out WorldSystem>? = null,
        private val onInstall: (RecordingSystem) -> Unit = {},
    ) : AbstractWorldSystem() {
        var installCount = 0
            private set
        var uninstallCount = 0
            private set

        override fun onInstalled() {
            installCount++
            onInstall(this)
        }

        override fun onUninstalled() {
            uninstallCount++
        }
    }

    private data class EqualDataClassSystem(val tag: String) : AbstractWorldSystem()

    @Test
    fun `onInstalled is called exactly once, and world is the installing World`() {
        val world = World()
        var seen: World? = null
        val system = RecordingSystem(onInstall = { self -> seen = self.world })

        world.installSystem(system)

        assertEquals(1, system.installCount)
        assertSame(world, seen)
    }

    @Test
    fun `installedSystems contains the system while onInstalled is running`() {
        val world = World()
        var sawSelf = false
        val system = RecordingSystem(onInstall = { self -> sawSelf = self in self.world.installedSystems })

        world.installSystem(system)

        assertTrue(sawSelf)
    }

    @Test
    fun `installing the same instance twice throws IllegalArgumentException and does not call onInstalled again`() {
        val world = World()
        val system = RecordingSystem()
        world.installSystem(system)

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertEquals(1, system.installCount)
    }

    @Test
    fun `installing a second system claiming an occupied slot throws IllegalArgumentException naming the slot and the occupant, and does not call its onInstalled`() {
        val world = World()
        val first = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)
        world.installSystem(first)
        val second = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(second) }

        assertTrue(ex.message!!.contains(CoreWorldSystemSlot.PHYSICS.toString()))
        assertTrue(ex.message!!.contains(first.toString()))
        assertEquals(0, second.installCount)
    }

    @Test
    fun `if onInstalled throws, nothing is recorded and the exception propagates`() {
        val world = World()
        val boom = IllegalStateException("boom")
        val system = RecordingSystem(onInstall = { throw boom })

        val thrown = assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertSame(boom, thrown)
        assertFalse(system in world.installedSystems)
    }

    @Test
    fun `a system whose onInstalled threw can be installed again later`() {
        val world = World()
        var shouldThrow = true
        val system = RecordingSystem(onInstall = { if (shouldThrow) error("boom") })

        assertFailsWith<IllegalStateException> { world.installSystem(system) }
        shouldThrow = false
        world.installSystem(system)

        assertTrue(system in world.installedSystems)
    }

    @Test
    fun `a tier-1 system whose onInstalled threw releases its slot for another claimant`() {
        val world = World()
        val failing = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS, onInstall = { error("boom") })
        assertFailsWith<IllegalStateException> { world.installSystem(failing) }

        val next = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)
        world.installSystem(next)

        assertEquals(listOf<WorldSystem>(next), world.installedSystems)
    }

    @Test
    fun `two distinct instances of an equal data class WorldSystem are both installed`() {
        val world = World()
        val a = EqualDataClassSystem("same")
        val b = EqualDataClassSystem("same")
        assertEquals(a, b) // same by equals, proving the duplicate check must use identity, not equals

        world.installSystem(a)
        world.installSystem(b)

        assertEquals(listOf<WorldSystem>(a, b), world.installedSystems)
    }

    @Test
    fun `coreSlot is read exactly once`() {
        var reads = 0
        val world = World()
        // Inside the anonymous object, `world` resolves to the object's own property, so `get() = world` would recurse.
        val host = world
        val system = object : WorldSystem {
            override val world: World get() = host

            override val coreSlot: CoreSystemSlot?
                get() {
                    reads++
                    return null
                }
        }

        world.installSystem(system)
        assertEquals(1, reads)

        world.stepSystems()
        assertEquals(1, reads)

        world.uninstallSystem(system)
        assertEquals(1, reads)
    }

    @Test
    fun `changing what coreSlot returns after install has no effect on the installed system`() {
        var slot: CoreSystemSlot? = CoreWorldSystemSlot.PHYSICS
        val world = World()
        // Inside the anonymous object, `world` resolves to the object's own property, so `get() = world` would recurse.
        val host = world
        val shifty = object : WorldSystem {
            override val world: World get() = host

            override val coreSlot: CoreSystemSlot?
                get() = slot
        }
        world.installSystem(shifty)

        slot = CoreWorldSystemSlot.ZONE

        // Still holds PHYSICS: a second PHYSICS claimant is rejected...
        assertFailsWith<IllegalArgumentException> { world.installSystem(RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)) }
        // ...and ZONE is free, stepping after it in slot order.
        val zone = RecordingSystem(coreSlot = CoreWorldSystemSlot.ZONE)
        world.installSystem(zone)
        assertEquals(listOf(shifty, zone), world.installedSystems)
    }

    @Test
    fun `a system that re-entrantly installs itself from its own onInstalled throws IllegalArgumentException, and the outer install is rolled back`() {
        val world = World()
        val system = RecordingSystem(onInstall = { self -> self.world.installSystem(self) })

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertTrue(world.installedSystems.isEmpty())
    }

    @Test
    fun `a system that re-entrantly installs a second claimant of its own slot throws IllegalArgumentException, and the outer install is rolled back`() {
        val world = World()
        val outer = RecordingSystem(
            coreSlot = CoreWorldSystemSlot.PHYSICS,
            onInstall = { self -> self.world.installSystem(RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)) },
        )

        assertFailsWith<IllegalArgumentException> { world.installSystem(outer) }

        assertTrue(world.installedSystems.isEmpty())
    }

    @Test
    fun `a system that installs an unrelated helper from its own onInstalled succeeds, and the helper is recorded after the outer system`() {
        val world = World()
        val helper = RecordingSystem()
        val outer = RecordingSystem(onInstall = { self -> self.world.installSystem(helper) })

        world.installSystem(outer)

        assertEquals(listOf<WorldSystem>(outer, helper), world.installedSystems)
    }

    @Test
    fun `a system that uninstalls itself from inside its own onInstalled genuinely uninstalls`() {
        val world = World()
        var uninstallsSeenBeforeReturn = -1
        val system = RecordingSystem(onInstall = { self ->
            self.world.uninstallSystem(self)
            uninstallsSeenBeforeReturn = self.uninstallCount
        })

        world.installSystem(system) // returns normally

        assertFalse(system in world.installedSystems)
        assertEquals(1, uninstallsSeenBeforeReturn) // onUninstalled ran before onInstalled returned
        assertEquals(1, system.uninstallCount)
        assertSame(world, system.boundWorldOrNull())
    }

    @Test
    fun `if onInstalled installs a helper and then throws, the helper stays installed and the outer system is not`() {
        val world = World()
        val helper = RecordingSystem()
        val outer = RecordingSystem(onInstall = { self ->
            self.world.installSystem(helper)
            error("boom")
        })

        assertFailsWith<IllegalStateException> { world.installSystem(outer) }

        assertEquals(listOf<WorldSystem>(helper), world.installedSystems)
    }
}
