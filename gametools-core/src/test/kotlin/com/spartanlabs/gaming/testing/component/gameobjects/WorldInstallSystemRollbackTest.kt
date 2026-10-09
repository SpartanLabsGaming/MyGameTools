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
// 4.1 Logging
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers the roll-back of a throwing [WorldSystem.onInstalled]: the attempt's own record is
 * removed (slot and role released), [WorldSystem.onUninstalled] is not called, the binding is
 * kept, the original exception reaches the caller unchanged, and one `WARN` line reports it.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldInstallSystemRollbackTest {

    /** A system whose [onInstalled] runs [onInstall] (which may throw); counts every hook. */
    private class Hooked(
        override val coreSlot: CoreSystemSlot? = null,
        override val uniqueRole: KClass<out WorldSystem>? = null,
        private val onInstall: (Hooked) -> Unit = {},
    ) : AbstractWorldSystem() {
        var installCount = 0
        var uninstallCount = 0
        var stepCount = 0

        override fun onInstalled() {
            installCount++
            onInstall(this)
        }

        override fun onUninstalled() {
            uninstallCount++
        }

        override fun step() {
            stepCount++
        }
    }

    /** A custom [Error], to prove that even an [Error] is rolled back and rethrown unchanged. */
    private class CustomError : Error("custom")

    @Test
    fun `a throwing onInstalled leaves the system absent, never stepped, and onUninstalled not called`() {
        val world = World()
        val system = Hooked(onInstall = { error("boom") })

        assertFailsWith<IllegalStateException> { world.installSystem(system) }
        world.stepSystems()

        assertFalse(system in world.installedSystems)
        assertEquals(0, system.stepCount)
        assertEquals(0, system.uninstallCount)
    }

    @Test
    fun `the original RuntimeException instance reaches the caller`() {
        val failure = RuntimeException("boom")
        val thrown = assertFailsWith<RuntimeException> { World().installSystem(Hooked(onInstall = { throw failure })) }
        assertSame(failure, thrown)
    }

    @Test
    fun `the original IllegalStateException instance reaches the caller`() {
        val failure = IllegalStateException("boom")
        val thrown = assertFailsWith<IllegalStateException> { World().installSystem(Hooked(onInstall = { throw failure })) }
        assertSame(failure, thrown)
    }

    @Test
    fun `the original Error instance reaches the caller`() {
        val world = World()
        val failure = CustomError()
        val system = Hooked(onInstall = { throw failure })

        val thrown = assertFailsWith<CustomError> { world.installSystem(system) }

        assertSame(failure, thrown)
        assertFalse(system in world.installedSystems)
    }

    @Test
    fun `the binding is kept after a roll-back - another World still rejects it, the same World accepts a retry`() {
        val world = World()
        var shouldThrow = true
        val system = Hooked(onInstall = { if (shouldThrow) error("boom") })
        assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertSame(world, system.boundWorldOrNull())
        assertFailsWith<IllegalArgumentException> { World().installSystem(system) }

        shouldThrow = false
        world.installSystem(system)

        assertTrue(system in world.installedSystems)
        assertEquals(2, system.installCount)
    }

    @Test
    fun `a roll-back releases the slot and the role`() {
        val world = World()
        val failing = Hooked(coreSlot = CoreWorldSystemSlot.PHYSICS, uniqueRole = Hooked::class, onInstall = { error("boom") })
        assertFailsWith<IllegalStateException> { world.installSystem(failing) }

        val claimant = Hooked(coreSlot = CoreWorldSystemSlot.PHYSICS)
        world.installSystem(claimant)
        val holder = Hooked(uniqueRole = Hooked::class)
        world.installSystem(holder)

        assertEquals(listOf<WorldSystem>(claimant, holder), world.installedSystems)
    }

    @Test
    fun `a helper installed by the failing hook stays installed`() {
        val world = World()
        val helper = Hooked()
        val outer = Hooked(onInstall = { self ->
            self.world.installSystem(helper)
            error("boom")
        })

        assertFailsWith<IllegalStateException> { world.installSystem(outer) }

        assertEquals(listOf<WorldSystem>(helper), world.installedSystems)
    }

    @Test
    fun `a tier-1 roll-back leaves the other records in their order`() {
        val world = World()
        val tier2A = Hooked()
        val zone = Hooked(coreSlot = CoreWorldSystemSlot.ZONE)
        val tier2B = Hooked()
        listOf(tier2A, zone, tier2B).forEach(world::installSystem)

        assertFailsWith<IllegalStateException> {
            world.installSystem(Hooked(coreSlot = CoreWorldSystemSlot.PHYSICS, onInstall = { error("boom") }))
        }

        assertEquals(listOf<WorldSystem>(zone, tier2A, tier2B), world.installedSystems)
    }

    @Test
    fun `a hook that uninstalls itself and then throws removes nothing twice, and onUninstalled runs once`() {
        val world = World()
        val other = Hooked()
        world.installSystem(other)
        val failure = IllegalStateException("boom")
        val system = Hooked(onInstall = { self ->
            self.world.uninstallSystem(self)
            throw failure
        })

        val thrown = assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertSame(failure, thrown)
        assertEquals(1, system.uninstallCount)
        assertEquals(listOf<WorldSystem>(other), world.installedSystems)
    }

    @Test
    fun `a hook that uninstalls itself, re-installs itself, then throws leaves the newer record installed`() {
        val world = World()
        val failure = IllegalStateException("boom")
        val system = Hooked(onInstall = { self ->
            if (self.installCount == 1) { // only the outer call; the nested re-install returns normally
                self.world.uninstallSystem(self)
                self.world.installSystem(self)
                throw failure
            }
        })

        val thrown = assertFailsWith<IllegalStateException> { world.installSystem(system) }
        world.stepSystems()

        assertSame(failure, thrown)
        assertEquals(listOf<WorldSystem>(system), world.installedSystems)
        assertEquals(1, system.stepCount)
        assertEquals(2, system.installCount)
        assertEquals(1, system.uninstallCount)
    }

    @Test
    fun `uninstallSystem on a rolled-back system is a no-op`() {
        val world = World()
        val system = Hooked(onInstall = { error("boom") })
        assertFailsWith<IllegalStateException> { world.installSystem(system) }

        world.uninstallSystem(system)

        assertEquals(0, system.uninstallCount)
    }

    @Test
    fun `a roll-back logs one WARN event naming the class and the cause type`() {
        val logger = LoggerFactory.getLogger("com.spartanlabs.gaming.gameobjects") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            assertFailsWith<IllegalStateException> { World().installSystem(Hooked(onInstall = { error("boom") })) }
        } finally {
            logger.detachAppender(appender)
        }

        val warnings = appender.list.filter { it.level == Level.WARN }
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().formattedMessage.contains("Hooked"))
        assertTrue(warnings.single().formattedMessage.contains(IllegalStateException::class.java.name))
    }
}
