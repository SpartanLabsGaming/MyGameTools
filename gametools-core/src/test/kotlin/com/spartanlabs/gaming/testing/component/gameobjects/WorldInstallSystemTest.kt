package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
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
import kotlin.test.assertTrue
//endregion

/**
 * Covers [World.installSystem]'s duplicate/slot checks, failure-atomicity, and its reservation
 * guard against re-entrant calls (R1).
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemTest {

    /** A [WorldSystem] whose [installOn] is fully configurable, for exercising one behaviour at a time. */
    private open class RecordingSystem(
        override val coreSlot: CoreSystemSlot? = null,
        private val onInstall: (World, RecordingSystem) -> Unit = { _, _ -> },
    ) : WorldSystem {
        var installCount = 0
            private set

        override fun installOn(world: World) {
            installCount++
            onInstall(world, this)
        }
    }

    private data class EqualDataClassSystem(val tag: String) : WorldSystem {
        override fun installOn(world: World) {}
    }

    @Test
    fun `installOn is called exactly once, with the installing World`() {
        val world = World()
        var seen: World? = null
        val system = RecordingSystem(onInstall = { w, _ -> seen = w })

        world.installSystem(system)

        assertEquals(1, system.installCount)
        assertEquals(world, seen)
    }

    @Test
    fun `installedSystems excludes the system while installOn is running`() {
        val world = World()
        var sawSelf = true
        val system = RecordingSystem(onInstall = { w, self -> sawSelf = self in w.installedSystems })

        world.installSystem(system)

        assertFalse(sawSelf)
    }

    @Test
    fun `installing the same instance twice throws IllegalArgumentException and does not call installOn again`() {
        val world = World()
        val system = RecordingSystem()
        world.installSystem(system)

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertEquals(1, system.installCount)
    }

    @Test
    fun `installing a second system claiming an occupied slot throws IllegalArgumentException naming the slot and the occupant, and does not call its installOn`() {
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
    fun `if installOn throws, nothing is recorded and the exception propagates`() {
        val world = World()
        val system = RecordingSystem(onInstall = { _, _ -> error("boom") })

        assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertFalse(system in world.installedSystems)
    }

    @Test
    fun `a system whose installOn threw can be installed again later`() {
        val world = World()
        var shouldThrow = true
        val system = RecordingSystem(onInstall = { _, _ -> if (shouldThrow) error("boom") })

        assertFailsWith<IllegalStateException> { world.installSystem(system) }
        shouldThrow = false
        world.installSystem(system)

        assertTrue(system in world.installedSystems)
    }

    @Test
    fun `a tier-1 system whose installOn threw releases its slot for another claimant`() {
        val world = World()
        val failing = RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS, onInstall = { _, _ -> error("boom") })
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
        val system = object : WorldSystem {
            override val coreSlot: CoreSystemSlot?
                get() {
                    reads++
                    return null
                }

            override fun installOn(world: World) {}
        }
        val world = World()

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
        val shifty = object : WorldSystem {
            override val coreSlot: CoreSystemSlot?
                get() = slot

            override fun installOn(world: World) {}
        }
        val world = World()
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
    fun `a system that re-entrantly installs itself from its own installOn throws IllegalArgumentException`() {
        val world = World()
        val system = RecordingSystem(onInstall = { w, self -> w.installSystem(self) })

        assertFailsWith<IllegalArgumentException> { world.installSystem(system) }
    }

    @Test
    fun `a system that re-entrantly installs a second claimant of its own in-flight slot throws IllegalArgumentException`() {
        val world = World()
        val outer = RecordingSystem(
            coreSlot = CoreWorldSystemSlot.PHYSICS,
            onInstall = { w, _ -> w.installSystem(RecordingSystem(coreSlot = CoreWorldSystemSlot.PHYSICS)) },
        )

        assertFailsWith<IllegalArgumentException> { world.installSystem(outer) }
    }

    @Test
    fun `a system that installs an unrelated helper from its own installOn succeeds, and the helper is recorded before the outer system`() {
        val world = World()
        val helper = RecordingSystem()
        val outer = RecordingSystem(onInstall = { w, _ -> w.installSystem(helper) })

        world.installSystem(outer)

        assertEquals(listOf<WorldSystem>(helper, outer), world.installedSystems)
    }

    @Test
    fun `a system that uninstalls itself from inside its own installOn is a no-op, and the outer install still succeeds`() {
        val world = World()
        val system = RecordingSystem(onInstall = { w, self -> w.uninstallSystem(self) })

        world.installSystem(system)

        assertTrue(system in world.installedSystems)
    }

    @Test
    fun `if installOn installs a helper and then throws, the helper stays installed and the outer system is not`() {
        val world = World()
        val helper = RecordingSystem()
        val outer = RecordingSystem(onInstall = { w, _ ->
            w.installSystem(helper)
            error("boom")
        })

        assertFailsWith<IllegalStateException> { world.installSystem(outer) }

        assertEquals(listOf<WorldSystem>(helper), world.installedSystems)
    }
}
