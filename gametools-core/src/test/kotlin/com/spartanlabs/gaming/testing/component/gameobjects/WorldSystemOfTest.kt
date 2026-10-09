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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [World.systemOf]'s `KClass` form: the exact-key lookup on the role recorded at install,
 * the `Result` shape of a hit and a miss, what the live registry shows from hooks and mid-pass,
 * and the absence of side effects.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemOfTest {

    /** Declares itself as its role, and has a member only a correctly typed result can reach. */
    private class Probe : AbstractWorldSystem() {
        override val uniqueRole: KClass<out WorldSystem> get() = Probe::class
        fun probeOnlyMember() = "probe"
    }

    private open class Base(override val uniqueRole: KClass<out WorldSystem>? = null) : AbstractWorldSystem()

    private class Derived(uniqueRole: KClass<out WorldSystem>? = null) : Base(uniqueRole)

    private class Peer : AbstractWorldSystem() {
        override val uniqueRole: KClass<out WorldSystem> get() = Peer::class
    }

    /** A system whose hooks run configurable actions, and which counts its hook calls. */
    private class Hooked(
        override val uniqueRole: KClass<out WorldSystem>? = null,
        private val onInstall: (Hooked) -> Unit = {},
        private val onStep: (Hooked) -> Unit = {},
        private val onUninstall: (Hooked) -> Unit = {},
    ) : AbstractWorldSystem() {
        var installCount = 0
        var stepCount = 0
        var uninstallCount = 0

        override fun onInstalled() {
            installCount++
            onInstall(this)
        }

        override fun step() {
            stepCount++
            onStep(this)
        }

        override fun onUninstalled() {
            uninstallCount++
            onUninstall(this)
        }
    }

    /**
     * Hit -> the system; miss -> null. The failure itself is asserted only in `systemOf returns a
     * failure carrying MissingWorldSystemException when no installed system declared the role` and
     * `systemOf is callable from onInstalled, step and onUninstalled`.
     */
    private fun <T : WorldSystem> World.find(role: KClass<T>): T? = systemOf(role).getOrNull()

    @Test
    fun `systemOf returns the installed system that declared the role as a success`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        val result = world.systemOf(Probe::class)

        assertTrue(result.isSuccess)
        assertSame(system, result.getOrThrow())
    }

    @Test
    fun `systemOf returns the role-typed result without a cast`() {
        val world = World()
        world.installSystem(Probe())

        val found: Result<Probe> = world.systemOf(Probe::class)

        assertEquals("probe", found.getOrThrow().probeOnlyMember())
    }

    @Test
    fun `systemOf returns a failure carrying MissingWorldSystemException when no installed system declared the role`() {
        val world = World()

        val r = world.systemOf(Probe::class) // the call itself throws nothing

        assertTrue(r.isFailure)
        val e = assertIs<MissingWorldSystemException>(r.exceptionOrNull())
        assertEquals(Probe::class, e.role)
        assertTrue(e.message!!.contains(Probe::class.java.name))
        // Guards against KClass.toString()'s fallback text when kotlin-reflect is absent.
        assertFalse(e.message!!.contains("Kotlin reflection is not available"))
        assertTrue(e.stackTrace.isEmpty())
    }

    @Test
    fun `systemOf matches the exact declared key, not the subtype`() {
        val world = World()
        val derived = Derived(uniqueRole = Base::class)
        world.installSystem(derived)

        assertSame(derived, world.find(Base::class))
        assertTrue(world.systemOf(Derived::class).isFailure)
    }

    @Test
    fun `a system that declared no role is never found`() {
        val world = World()
        world.installSystem(Derived())

        assertTrue(world.systemOf(Derived::class).isFailure)
        assertTrue(world.systemOf(Base::class).isFailure)
        assertTrue(world.systemOf(AbstractWorldSystem::class).isFailure)
        assertTrue(world.systemOf(WorldSystem::class).isFailure)
    }

    @Test
    fun `systemOf finds a system from inside its own onInstalled`() {
        val world = World()
        var found: Hooked? = null
        val system = Hooked(uniqueRole = Hooked::class, onInstall = { self -> found = self.world.find(Hooked::class) })

        world.installSystem(system)

        assertSame(system, found)
    }

    @Test
    fun `systemOf no longer finds a system after its install was rolled back`() {
        val world = World()
        val system = Hooked(uniqueRole = Hooked::class, onInstall = { error("boom") })

        assertFailsWith<IllegalStateException> { world.installSystem(system) }

        assertNull(world.find(Hooked::class))
    }

    @Test
    fun `systemOf no longer finds a system after it is uninstalled`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)

        world.uninstallSystem(system)

        assertNull(world.find(Probe::class))
    }

    @Test
    fun `systemOf finds a system again after a re-install`() {
        val world = World()
        val system = Probe()
        world.installSystem(system)
        world.uninstallSystem(system)

        world.installSystem(system)

        assertSame(system, world.find(Probe::class))
    }

    @Test
    fun `systemOf sees the live registry, not a step pass snapshot`() {
        val world = World()
        val victim = Peer()
        val late = Hooked(uniqueRole = Hooked::class)
        var victimSeen: Peer? = null
        var lateSeen: Hooked? = null
        val remover = Hooked(onStep = { self ->
            if (self.stepCount == 1) {
                self.world.uninstallSystem(victim)
                self.world.installSystem(late)
            }
        })
        val observer = Hooked(onStep = { self ->
            if (self.stepCount == 1) {
                victimSeen = self.world.find(Peer::class)
                lateSeen = self.world.find(Hooked::class)
            }
        })
        world.installSystem(remover)
        world.installSystem(victim)
        world.installSystem(observer)

        world.stepSystems()

        assertNull(victimSeen) // uninstalled earlier in this pass
        assertSame(late, lateSeen) // installed mid-pass, found at once...
        assertEquals(0, late.stepCount) // ...but not stepped this pass

        world.stepSystems()
        assertEquals(1, late.stepCount)
    }

    @Test
    fun `systemOf is callable from onInstalled, step and onUninstalled`() {
        val world = World()
        val peer = Peer()
        world.installSystem(peer)
        var fromInstall: Peer? = null
        var fromStep: Peer? = null
        var selfFromUninstall: Hooked? = Hooked()
        val system = Hooked(
            uniqueRole = Hooked::class,
            onInstall = { self -> fromInstall = self.world.find(Peer::class) },
            onStep = { self -> fromStep = self.world.find(Peer::class) },
            onUninstall = { self -> selfFromUninstall = self.world.find(Hooked::class) },
        )

        world.installSystem(system)
        world.stepSystems()
        world.uninstallSystem(system)

        assertSame(peer, fromInstall) // the earlier peer is found from a later system's onInstalled
        assertSame(peer, fromStep)
        assertNull(selfFromUninstall) // removed before onUninstalled runs

        // The required-peer pattern: getOrThrow() in onInstalled rolls the install back when the peer is absent.
        val lonely = World()
        val dependant = Hooked(onInstall = { self -> self.world.systemOf(Peer::class).getOrThrow() })

        val e = assertFailsWith<MissingWorldSystemException> { lonely.installSystem(dependant) }

        assertEquals(Peer::class, e.role)
        assertTrue(e.message!!.contains(Peer::class.java.name))
        assertTrue(lonely.installedSystems.isEmpty())

        lonely.installSystem(Peer())
        lonely.installSystem(dependant) // with the peer installed first, the same system installs
        assertTrue(dependant in lonely.installedSystems)
    }

    @Test
    fun `systemOf uses the role recorded at install, not a re-read`() {
        var reads = 0
        var role: KClass<out WorldSystem> = Base::class
        val world = World()
        val shifty = object : Base() {
            override val uniqueRole: KClass<out WorldSystem>
                get() {
                    reads++
                    return role
                }
        }
        world.installSystem(shifty)

        role = shifty::class

        assertSame(shifty, world.find(Base::class))
        assertTrue(world.systemOf(shifty::class).isFailure)
        assertEquals(1, reads)
    }

    @Test
    fun `systemOf has no side effect`() {
        val world = World()
        val system = Hooked(uniqueRole = Hooked::class)
        world.installSystem(system)
        val before = world.installedSystems

        repeat(3) {
            world.systemOf(Hooked::class)
            world.systemOf(Probe::class)
        }

        assertEquals(before, world.installedSystems)
        assertEquals(1, system.installCount)
        assertEquals(0, system.stepCount)
        assertEquals(0, system.uninstallCount)
    }

    @Test
    fun `two Worlds hold independent roles`() {
        val worldA = World()
        val worldB = World()
        val a = Probe()
        val b = Probe()
        worldA.installSystem(a)
        worldB.installSystem(b)

        assertSame(a, worldA.find(Probe::class))
        assertSame(b, worldB.find(Probe::class))
    }
}
