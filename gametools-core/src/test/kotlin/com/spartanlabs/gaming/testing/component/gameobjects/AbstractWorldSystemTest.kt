package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 3. Utility / Catch-all
// 3.1 Java Standard library
import java.lang.reflect.Modifier
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
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [AbstractWorldSystem]'s binding storage: the guarded [AbstractWorldSystem.world] getter,
 * the write-once `bindTo`, the never-throwing `boundWorldOrNull`, and the structural guarantees
 * (no public JVM field, a `final` getter, a constructor that reads no open member).
 */
@OptIn(ExperimentalGameToolsApi::class)
class AbstractWorldSystemTest {

    private class Plain : AbstractWorldSystem()

    /** Counts every read of its open members, to prove the base-class constructor reads none. */
    private class Counting : AbstractWorldSystem() {
        var slotReads = 0
        var roleReads = 0

        override val coreSlot: CoreSystemSlot?
            get() {
                slotReads++
                return null
            }

        override val uniqueRole: KClass<out WorldSystem>?
            get() {
                roleReads++
                return null
            }
    }

    @Test
    fun `world read before install throws IllegalStateException naming the class`() {
        val system = Plain()

        val ex = assertFailsWith<IllegalStateException> { system.world }

        assertTrue(ex.message!!.contains("Plain"))
        assertTrue(ex.message!!.contains("before"))
    }

    @Test
    fun `for an anonymous subclass the message falls back to the Java class name`() {
        val system = object : AbstractWorldSystem() {}

        val ex = assertFailsWith<IllegalStateException> { system.world }

        assertTrue(ex.message!!.contains(system::class.java.name))
        assertFalse(ex.message!!.startsWith("null"))
    }

    @Test
    fun `after install world is the installing World`() {
        val world = World()
        val system = Plain()

        world.installSystem(system)

        assertSame(world, system.world)
    }

    @Test
    fun `boundWorldOrNull is null before install and the World after, and never throws`() {
        val world = World()
        val system = Plain()

        assertNull(system.boundWorldOrNull())
        world.installSystem(system)

        assertSame(world, system.boundWorldOrNull())
    }

    @Test
    fun `the binding survives uninstall`() {
        val world = World()
        val system = Plain()
        world.installSystem(system)

        world.uninstallSystem(system)

        assertSame(world, system.world)
        assertSame(world, system.boundWorldOrNull())
    }

    @Test
    fun `bindTo the same World is a no-op`() {
        val world = World()
        val system = Plain()
        system.bindTo(world)

        system.bindTo(world) // must not throw

        assertSame(world, system.world)
    }

    @Test
    fun `bindTo a different World throws IllegalStateException and keeps the first binding`() {
        val first = World()
        val system = Plain()
        system.bindTo(first)

        assertFailsWith<IllegalStateException> { system.bindTo(World()) }

        assertSame(first, system.world)
    }

    @Test
    fun `an AbstractWorldSystem is constructible with no World`() {
        val system = Plain() // must not throw

        assertNull(system.boundWorldOrNull())
    }

    @Test
    fun `construction reads no open member`() {
        val system = Counting()

        assertEquals(0, system.slotReads)
        assertEquals(0, system.roleReads)
    }

    @Test
    fun `AbstractWorldSystem exposes no public JVM field`() {
        assertTrue(AbstractWorldSystem::class.java.fields.isEmpty())
    }

    @Test
    fun `the world getter is final`() {
        val getter = AbstractWorldSystem::class.java.getMethod("getWorld")

        assertTrue(Modifier.isFinal(getter.modifiers))
    }
}
