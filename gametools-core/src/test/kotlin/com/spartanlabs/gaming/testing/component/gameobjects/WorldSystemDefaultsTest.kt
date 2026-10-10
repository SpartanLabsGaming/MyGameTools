package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
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
import kotlin.test.assertSame
//endregion

/**
 * Covers [WorldSystem]'s default (no-op) member bodies: each is called directly, against a
 * [World] the system is installed on, and must leave both that [World] and the system's binding
 * unchanged.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemDefaultsTest {

    private class MinimalSystem : AbstractWorldSystem()

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
    fun `onInstalled does nothing by default`() {
        val (world, system) = installedFixture()
        val before = world.state()

        system.onInstalled()

        assertEquals(before, world.state())
        assertSame(world, system.world)
    }

    @Test
    fun `onUninstalled does nothing by default`() {
        val (world, system) = installedFixture()
        val before = world.state()

        system.onUninstalled()

        assertEquals(before, world.state())
        assertSame(world, system.world)
    }

    @Test
    fun `step does nothing by default`() {
        val (world, system) = installedFixture()
        val before = world.state()

        system.step()

        assertEquals(before, world.state())
        assertSame(world, system.world)
    }

    @Test
    fun `coreSlot is null by default`() {
        assertNull(MinimalSystem().coreSlot)
    }

    @Test
    fun `uniqueRole is null by default`() {
        assertNull(MinimalSystem().uniqueRole)
    }

    @Test
    fun `a direct implementor inherits the no-op hooks and null coreSlot and uniqueRole`() {
        val world = World()
        // Inside the anonymous object, `world` resolves to the object's own property, so `get() = world` would recurse.
        val host = world
        val direct = object : WorldSystem {
            override val world: World get() = host
        }
        world.installSystem(direct)
        val before = world.state()

        direct.onInstalled()
        direct.step()
        direct.onUninstalled()

        assertEquals(before, world.state())
        assertNull(direct.coreSlot)
        assertNull(direct.uniqueRole)
    }
}
