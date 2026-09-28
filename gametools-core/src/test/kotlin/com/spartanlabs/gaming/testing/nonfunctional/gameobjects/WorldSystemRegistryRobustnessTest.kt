package com.spartanlabs.gaming.testing.nonfunctional.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.system.measureNanoTime
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional validation for [World]'s installed-systems registry: many
 * install/uninstall cycles and many steps stay within a sane time budget (matching
 * `WorldTickThroughputTest`'s own "sane time budget" framing, not a hard numeric SLA), and a
 * system that throws on every step never corrupts the registry or leaves the
 * [World.stepSystems] guard stuck.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemRegistryRobustnessTest {

    private class NoOpSystem : WorldSystem {
        override fun installOn(world: World) {}
    }

    /**
     * Thrown by a failing [WorldSystem.step]. Deliberately not an [IllegalStateException], which is
     * what a stuck [World.stepSystems] re-entrancy guard would throw - so each call below can tell
     * "the step failed as planned" apart from "the guard never reset".
     */
    private class StepFailure : RuntimeException("boom")

    @Test
    fun `many install-uninstall cycles leave the registry empty`() {
        val world = World()
        val system = NoOpSystem()

        repeat(10_000) {
            world.installSystem(system)
            world.uninstallSystem(system)
        }

        assertTrue(world.installedSystems.isEmpty())
    }

    @Test
    fun `roughly 1000 no-op tier-2 systems each step roughly 1000 times within a generous time budget`() {
        val world = World()
        repeat(1_000) { world.installSystem(NoOpSystem()) }
        world.stepSystems() // warm up

        val elapsedMillis = measureNanoTime { repeat(1_000) { world.stepSystems() } } / 1_000_000

        assertTrue(elapsedMillis < 10_000, "1000 stepSystems() calls over 1000 systems took ${elapsedMillis}ms")
    }

    @Test
    fun `a system that throws on every step does not corrupt the registry`() {
        val world = World()
        val thrower = object : WorldSystem {
            override fun installOn(world: World) {}
            override fun step(world: World) = throw StepFailure()
        }
        val normal = NoOpSystem()
        world.installSystem(thrower)
        world.installSystem(normal)
        val expected = world.installedSystems

        repeat(5) {
            // StepFailure, not IllegalStateException: proves the guard was reset after the previous failure
            assertFailsWith<StepFailure> { world.stepSystems() }
            assertEquals(expected, world.installedSystems)
        }

        world.uninstallSystem(thrower)
        world.stepSystems() // must not throw - the stepping guard is not stuck true
    }
}
