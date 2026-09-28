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
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
//endregion

/** Covers [World.installedSystems]'s emptiness, copy semantics, and step-order reflection. */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstalledSystemsTest {

    private class NoOpSystem(override val coreSlot: CoreSystemSlot? = null) : WorldSystem {
        override fun installOn(world: World) {}
    }

    @Test
    fun `installedSystems is empty for a World with nothing installed`() {
        assertTrue(World().installedSystems.isEmpty())
    }

    @Test
    fun `installedSystems is a fresh copy on every read`() {
        val world = World()
        world.installSystem(NoOpSystem())

        val first = world.installedSystems
        val second = world.installedSystems

        assertEquals(first, second)
        assertNotSame(first, second)
    }

    @Test
    fun `installedSystems reflects step order - tier 1 by order, then tier 2 by install order`() {
        val world = World()
        val tier2First = NoOpSystem()
        val zone = NoOpSystem(CoreWorldSystemSlot.ZONE)
        val tier2Second = NoOpSystem()
        val physics = NoOpSystem(CoreWorldSystemSlot.PHYSICS)

        world.installSystem(tier2First)
        world.installSystem(zone)
        world.installSystem(tier2Second)
        world.installSystem(physics)

        assertEquals(
            listOf<WorldSystem>(physics, zone, tier2First, tier2Second),
            world.installedSystems,
        )
    }
}
