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
import kotlin.test.assertTrue
//endregion

/**
 * Covers the legal-but-discouraged calls a system may make on its [World] from inside its own
 * [WorldSystem.onInstalled]: [World.stepSystems] (outside a step pass) and
 * [World.uninstallSystem] with itself. No in-flight guard exists for either.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemReentrancyTest {

    /** Appends its hook calls to [trace]; [onInstall] runs between `install-begin` and `install-end`. */
    private class Traced(
        private val name: String,
        private val trace: MutableList<String>,
        override val coreSlot: CoreSystemSlot? = null,
        override val uniqueRole: KClass<out WorldSystem>? = null,
        private val onInstall: (Traced) -> Unit = {},
        var onStep: (Traced) -> Unit = {},
    ) : AbstractWorldSystem() {
        override fun onInstalled() {
            trace += "$name:install-begin"
            onInstall(this)
            trace += "$name:install-end"
        }

        override fun onUninstalled() {
            trace += "$name:uninstall"
        }

        override fun step() {
            trace += "$name:step"
            onStep(this)
        }
    }

    @Test
    fun `stepSystems from onInstalled, with no pass running, steps the just-recorded system and its peers`() {
        val world = World()
        val trace = mutableListOf<String>()
        world.installSystem(Traced("peer", trace))
        trace.clear()
        val system = Traced("self", trace, onInstall = { self -> self.world.stepSystems() })

        world.installSystem(system) // must not throw

        assertEquals(listOf("self:install-begin", "peer:step", "self:step", "self:install-end"), trace)
    }

    @Test
    fun `stepSystems from an onInstalled reached inside a step pass throws IllegalStateException, rolls the install back, and resets the guard`() {
        val world = World()
        val trace = mutableListOf<String>()
        val inner = Traced("inner", trace, onInstall = { self -> self.world.stepSystems() })
        var installedOnce = false
        val installer = Traced("installer", trace, onStep = { self ->
            if (!installedOnce) {
                installedOnce = true
                self.world.installSystem(inner)
            }
        })
        world.installSystem(installer)

        assertFailsWith<IllegalStateException> { world.stepSystems() }

        assertFalse(inner in world.installedSystems)
        trace.clear()
        world.stepSystems() // the guard was reset
        assertEquals(listOf("installer:step"), trace)
    }

    @Test
    fun `uninstallSystem with itself from onInstalled removes it and runs onUninstalled before onInstalled returns, freeing slot and role`() {
        val world = World()
        val trace = mutableListOf<String>()
        val system = Traced(
            "self", trace,
            coreSlot = CoreWorldSystemSlot.PHYSICS,
            uniqueRole = Traced::class,
            onInstall = { self -> self.world.uninstallSystem(self) },
        )

        world.installSystem(system) // must not throw

        assertEquals(listOf("self:install-begin", "self:uninstall", "self:install-end"), trace)
        assertFalse(system in world.installedSystems)
        world.installSystem(Traced("next", trace, coreSlot = CoreWorldSystemSlot.PHYSICS, uniqueRole = Traced::class))
        assertEquals(1, world.installedSystems.size)
    }

    @Test
    fun `installedSystems contains the system inside its own onInstalled`() {
        val world = World()
        var sawSelf = false
        val system = Traced("self", mutableListOf(), onInstall = { self -> sawSelf = self in self.world.installedSystems })

        world.installSystem(system)

        assertTrue(sawSelf)
    }

    @Test
    fun `no in-flight guard exists - stepping and self-uninstall from onInstalled both complete without throwing`() {
        val world = World()
        val trace = mutableListOf<String>()
        val system = Traced("self", trace, onInstall = { self ->
            self.world.stepSystems()
            self.world.uninstallSystem(self)
        })

        world.installSystem(system) // must not throw

        assertEquals(listOf("self:install-begin", "self:step", "self:uninstall", "self:install-end"), trace)
    }
}
