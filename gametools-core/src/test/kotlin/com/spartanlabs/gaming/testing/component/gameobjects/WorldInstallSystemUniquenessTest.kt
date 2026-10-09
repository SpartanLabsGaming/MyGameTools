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
import kotlin.test.assertNull
import kotlin.test.assertTrue
//endregion

/**
 * Covers [WorldSystem.uniqueRole] as [World.installSystem] enforces it: one holder per role, the
 * supertype requirement, family roles, release on uninstall and roll-back, the slot-before-role
 * check order, and the read-once rule.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemUniquenessTest {

    /** A family base type, used as a shared role by [Alpha] and [Beta]. */
    private abstract class Family : AbstractWorldSystem()

    private class Alpha(override val uniqueRole: KClass<out WorldSystem>? = null) : Family() {
        var installCount = 0
        override fun onInstalled() {
            installCount++
        }
    }

    private class Beta(override val uniqueRole: KClass<out WorldSystem>? = null) : Family() {
        var installCount = 0
        override fun onInstalled() {
            installCount++
        }
    }

    /** A configurable system: slot, role, a throwing [onInstalled], a step counter. */
    private class Configurable(
        override val coreSlot: CoreSystemSlot? = null,
        override val uniqueRole: KClass<out WorldSystem>? = null,
        private val failInstall: Boolean = false,
    ) : AbstractWorldSystem() {
        var stepCount = 0
        override fun onInstalled() {
            if (failInstall) error("boom")
        }

        override fun step() {
            stepCount++
        }
    }

    @Test
    fun `a second system with the same uniqueRole is rejected with a message naming the role's Java name`() {
        val world = World()
        val first = Alpha(uniqueRole = Alpha::class)
        world.installSystem(first)
        val second = Alpha(uniqueRole = Alpha::class)

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(second) }

        assertTrue(ex.message!!.contains(Alpha::class.java.name))
        // Guards against KClass.toString()'s fallback text when kotlin-reflect is absent.
        assertFalse(ex.message!!.contains("Kotlin reflection is not available"))
        assertEquals(0, second.installCount)
        assertNull(second.boundWorldOrNull())
        assertEquals(listOf<WorldSystem>(first), world.installedSystems)
    }

    @Test
    fun `a uniqueRole that is not a supertype of the system is rejected and nothing is recorded`() {
        val world = World()
        val system = Alpha(uniqueRole = Beta::class)

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(system) }

        assertTrue(ex.message!!.contains(Beta::class.java.name))
        assertTrue(world.installedSystems.isEmpty())
        assertEquals(0, system.installCount)
    }

    @Test
    fun `two different classes declaring the same family role cannot coexist`() {
        val world = World()
        val alpha = Alpha(uniqueRole = Family::class)
        world.installSystem(alpha)
        val beta = Beta(uniqueRole = Family::class)

        assertFailsWith<IllegalArgumentException> { world.installSystem(beta) }

        assertEquals(listOf<WorldSystem>(alpha), world.installedSystems)
    }

    @Test
    fun `systems declaring different roles coexist`() {
        val world = World()
        val alpha = Alpha(uniqueRole = Alpha::class)
        val beta = Beta(uniqueRole = Beta::class)

        world.installSystem(alpha)
        world.installSystem(beta)

        assertEquals(listOf<WorldSystem>(alpha, beta), world.installedSystems)
    }

    @Test
    fun `a null role allows any number of distinct instances of one class`() {
        val world = World()
        val systems = List(5) { Alpha() }

        systems.forEach(world::installSystem)

        assertEquals(systems, world.installedSystems)
    }

    @Test
    fun `uninstallSystem frees the role`() {
        val world = World()
        val first = Alpha(uniqueRole = Alpha::class)
        world.installSystem(first)
        world.uninstallSystem(first)
        val second = Alpha(uniqueRole = Alpha::class)

        world.installSystem(second) // must not throw

        assertEquals(listOf<WorldSystem>(second), world.installedSystems)
    }

    @Test
    fun `a rolled-back install frees the role`() {
        val world = World()
        assertFailsWith<IllegalStateException> { world.installSystem(Configurable(uniqueRole = Configurable::class, failInstall = true)) }
        val next = Configurable(uniqueRole = Configurable::class)

        world.installSystem(next) // must not throw

        assertEquals(listOf<WorldSystem>(next), world.installedSystems)
    }

    @Test
    fun `the slot is checked before the role`() {
        val world = World()
        world.installSystem(Configurable(coreSlot = CoreWorldSystemSlot.PHYSICS, uniqueRole = Configurable::class))
        val second = Configurable(coreSlot = CoreWorldSystemSlot.PHYSICS, uniqueRole = Configurable::class)

        val ex = assertFailsWith<IllegalArgumentException> { world.installSystem(second) }

        assertTrue(ex.message!!.contains("slot"))
        assertTrue(ex.message!!.contains(CoreWorldSystemSlot.PHYSICS.toString()))
        assertFalse(ex.message!!.contains("uniqueRole"))
    }

    @Test
    fun `the first holder stays installed and keeps stepping after a second is rejected, and other systems still install`() {
        val world = World()
        val first = Configurable(uniqueRole = Configurable::class)
        world.installSystem(first)
        assertFailsWith<IllegalArgumentException> { world.installSystem(Configurable(uniqueRole = Configurable::class)) }

        val unrelated = Alpha(uniqueRole = Alpha::class)
        world.installSystem(unrelated)
        world.stepSystems()

        assertEquals(listOf<WorldSystem>(first, unrelated), world.installedSystems)
        assertEquals(1, first.stepCount)
    }

    @Test
    fun `uniqueRole is read exactly once across install, step and uninstall`() {
        var reads = 0
        val system = object : AbstractWorldSystem() {
            override val uniqueRole: KClass<out WorldSystem>?
                get() {
                    reads++
                    return null
                }
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
    fun `changing what uniqueRole returns after install changes nothing`() {
        var role: KClass<out WorldSystem>? = Family::class
        val world = World()
        val shifty = object : Family() {
            override val uniqueRole: KClass<out WorldSystem>?
                get() = role
        }
        world.installSystem(shifty)

        role = null

        // Still holds Family: a second Family holder is rejected...
        assertFailsWith<IllegalArgumentException> { world.installSystem(Alpha(uniqueRole = Family::class)) }
        // ...and the installed system is untouched.
        assertEquals(listOf<WorldSystem>(shifty), world.installedSystems)
    }
}
