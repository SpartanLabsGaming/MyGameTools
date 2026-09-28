package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/**
 * Covers [WorldSystem]'s default (no-op) member bodies: each is called directly, against a
 * [World] the system is installed on, and must leave both that [World] and the system unchanged.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemDefaultsTest {

    private class MinimalSystem : WorldSystem {
        var installed = false
        override fun installOn(world: World) {
            installed = true
        }
    }

    /** The observable [World] state a default member body could plausibly disturb. */
    private data class WorldState(val gameObjects: List<GameObject>, val installedSystems: List<WorldSystem>, val tickCount: Long)

    private fun World.state() = WorldState(gameObjects.toList(), installedSystems, tickCount)

    private fun installedFixture(): Pair<World, MinimalSystem> {
        val world = World()
        world.add(Actor(location = Point(0.0, 0.0)))
        val system = MinimalSystem()
        world.installSystem(system)
        return world to system
    }

    @Test
    fun `uninstallFrom does nothing by default`() {
        val (world, system) = installedFixture()
        val before = world.state()

        system.uninstallFrom(world)

        assertEquals(before, world.state())
        assertTrue(system.installed)
    }

    @Test
    fun `step does nothing by default`() {
        val (world, system) = installedFixture()
        val before = world.state()

        system.step(world)

        assertEquals(before, world.state())
        assertTrue(system.installed)
    }

    @Test
    fun `coreSlot is null by default`() {
        assertNull(MinimalSystem().coreSlot)
    }
}
