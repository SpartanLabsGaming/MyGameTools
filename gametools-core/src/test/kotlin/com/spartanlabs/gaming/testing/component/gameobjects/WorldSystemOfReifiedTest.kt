package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.MissingWorldSystemException
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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers the reified `World.systemOf<T>()`: it adds no logic of its own, so these tests pin its
 * equivalence with the `KClass` form, hit and miss alike.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemOfReifiedTest {

    private class Probe : AbstractWorldSystem() {
        override val uniqueRole: KClass<out WorldSystem> get() = Probe::class
    }

    private open class Base(override val uniqueRole: KClass<out WorldSystem>? = null) : AbstractWorldSystem()

    private class Derived(uniqueRole: KClass<out WorldSystem>? = null) : Base(uniqueRole)

    /** Looks itself up with the reified form from inside its own [onInstalled]. */
    private class SelfFinder : AbstractWorldSystem() {
        override val uniqueRole: KClass<out WorldSystem> get() = SelfFinder::class
        var found: SelfFinder? = null

        override fun onInstalled() {
            found = world.systemOf<SelfFinder>().getOrNull()
        }
    }

    @Test
    fun `on a hit the reified form returns the same instance as the KClass form`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        assertSame(system, world.systemOf<Probe>().getOrThrow())
        assertSame(world.systemOf(Probe::class).getOrThrow(), world.systemOf<Probe>().getOrThrow())
    }

    @Test
    fun `on a miss the reified form returns the same failure as the KClass form`() {
        val world = World()

        val reified = world.systemOf<Probe>()
        val byClass = world.systemOf(Probe::class)

        assertTrue(reified.isFailure)
        val reifiedMiss = assertIs<MissingWorldSystemException>(reified.exceptionOrNull())
        val byClassMiss = assertIs<MissingWorldSystemException>(byClass.exceptionOrNull())
        assertEquals(byClassMiss.role, reifiedMiss.role)
        assertEquals(byClassMiss.message, reifiedMiss.message)
    }

    @Test
    fun `the reified form uses the exact declared key - Base finds a Derived declaring Base, Derived does not`() {
        val world = World()
        val derived = Derived(uniqueRole = Base::class)
        world.installSystem(derived)

        assertSame(derived, world.systemOf<Base>().getOrThrow())
        assertTrue(world.systemOf<Derived>().isFailure)
    }

    @Test
    fun `the reified form is typed by the expected type`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        val found: Result<Probe> = world.systemOf()

        assertSame(system, found.getOrThrow())
    }

    @Test
    fun `the reified form finds a system from inside its own onInstalled`() {
        val world = World()
        val system = SelfFinder()

        world.installSystem(system)

        assertSame(system, system.found)
    }
}
