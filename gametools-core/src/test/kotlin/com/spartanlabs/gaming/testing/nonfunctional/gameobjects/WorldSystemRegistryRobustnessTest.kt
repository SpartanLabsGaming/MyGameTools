package com.spartanlabs.gaming.testing.nonfunctional.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.MissingWorldSystemException
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
import kotlin.system.measureNanoTime
//endregion

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
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

    private class NoOpSystem : AbstractWorldSystem()

    /** A family that claims [CoreWorldSystemSlot.PHYSICS] and declares itself as its [uniqueRole]. */
    private abstract class PhysicsLike : AbstractWorldSystem() {
        override val coreSlot: CoreSystemSlot? get() = CoreWorldSystemSlot.PHYSICS
        override val uniqueRole: KClass<out WorldSystem>? get() = PhysicsLike::class
    }

    private class FailingPhysicsLike : PhysicsLike() {
        override fun onInstalled() = throw InstallFailure()
    }

    private class HealthyPhysicsLike : PhysicsLike()

    /** The one role-bearing system among many role-less ones in the lookup-budget test. */
    private class Findable : AbstractWorldSystem() {
        override val uniqueRole: KClass<out WorldSystem> get() = Findable::class
    }

    /** A role nothing declares, so every lookup of it is a miss. */
    private class Absent : AbstractWorldSystem()

    /**
     * Thrown by a failing [WorldSystem.step]. Deliberately not an [IllegalStateException], which is
     * what a stuck [World.stepSystems] re-entrancy guard would throw - so each call below can tell
     * "the step failed as planned" apart from "the guard never reset".
     */
    private class StepFailure : RuntimeException("boom")

    /** Thrown by a failing [WorldSystem.onInstalled], so the roll-back path runs on every attempt. */
    private class InstallFailure : RuntimeException("boom")

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
        val thrower = object : AbstractWorldSystem() {
            override fun step() = throw StepFailure()
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

    @Test
    fun `many throwing installs leave the registry empty and the slot and role free`() {
        val world = World()
        // Each roll-back logs one WARN line; silence them for this loop only, then restore.
        val logger = LoggerFactory.getLogger("com.spartanlabs.gaming.gameobjects") as Logger
        val previousLevel = logger.level
        logger.level = Level.ERROR
        try {
            repeat(10_000) {
                assertFailsWith<InstallFailure> { world.installSystem(FailingPhysicsLike()) }
            }
        } finally {
            logger.level = previousLevel
        }

        assertTrue(world.installedSystems.isEmpty())
        val healthy = HealthyPhysicsLike()
        world.installSystem(healthy) // same slot, same role - must not throw
        assertEquals(listOf<WorldSystem>(healthy), world.installedSystems)
    }

    @Test
    fun `systemOf over roughly 1000 installed systems stays within a generous time budget`() {
        val world = World()
        repeat(1_000) { world.installSystem(NoOpSystem()) }
        val findable = Findable()
        world.installSystem(findable) // last, so every hit scans the whole list
        var misses = 0

        val elapsedMillis = measureNanoTime {
            repeat(10_000) { assertSame(findable, world.systemOf(Findable::class).getOrNull()) }
            repeat(10_000) {
                // Each miss allocates one stackless MissingWorldSystemException.
                val e = assertIs<MissingWorldSystemException>(world.systemOf(Absent::class).exceptionOrNull())
                assertTrue(e.stackTrace.isEmpty())
                misses++
            }
        } / 1_000_000

        assertEquals(10_000, misses)
        assertTrue(elapsedMillis < 10_000, "10000 hits and 10000 misses over 1001 systems took ${elapsedMillis}ms")
    }
}
