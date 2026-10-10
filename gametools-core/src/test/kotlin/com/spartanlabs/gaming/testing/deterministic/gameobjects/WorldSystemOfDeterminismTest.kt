package com.spartanlabs.gaming.testing.deterministic.gameobjects

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
 * Level 4a - deterministic laws for [World.systemOf]: a lookup's answer depends only on which
 * roles are recorded, never on install order, on the history that led to the installed set, or
 * on any hash-ordered structure; and a lookup of an undeclared role is always the same failure.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemOfDeterminismTest {

    /** Each concrete subclass declares its own class as its role. */
    private abstract class Named(val name: String, override val coreSlot: CoreSystemSlot? = null) : AbstractWorldSystem()

    private class PhysicsLike : Named("physics", CoreWorldSystemSlot.PHYSICS) {
        override val uniqueRole: KClass<out WorldSystem> get() = PhysicsLike::class
    }

    private class ZoneLike : Named("zone", CoreWorldSystemSlot.ZONE) {
        override val uniqueRole: KClass<out WorldSystem> get() = ZoneLike::class
    }

    private class TierA : Named("tierA") {
        override val uniqueRole: KClass<out WorldSystem> get() = TierA::class
    }

    private class TierB : Named("tierB") {
        override val uniqueRole: KClass<out WorldSystem> get() = TierB::class
    }

    /** A role no system in this test ever declares. */
    private class Undeclared : AbstractWorldSystem()

    private val roles: List<KClass<out WorldSystem>> = listOf(PhysicsLike::class, ZoneLike::class, TierA::class, TierB::class)

    private fun freshSystems(): List<Named> = listOf(PhysicsLike(), ZoneLike(), TierA(), TierB())

    /** Every permutation of the indices `0..3` (`4!` = 24). */
    private fun permutationsOfFour(): List<List<Int>> {
        fun permute(remaining: List<Int>): List<List<Int>> =
            if (remaining.isEmpty()) listOf(emptyList())
            else remaining.flatMap { pick -> permute(remaining - pick).map { listOf(pick) + it } }
        return permute(listOf(0, 1, 2, 3))
    }

    /** The name each role resolves to (or `"miss"`), in [roles] order, plus the undeclared lookup. */
    private fun lookupTrace(world: World): List<String> =
        roles.map { role -> world.systemOf(role).fold({ (it as Named).name }, { "miss" }) } +
            world.systemOf(Undeclared::class).fold({ "hit" }, { "miss" })

    @Test
    fun `for every install permutation each role finds the system that declared it, and an undeclared role is the same failure`() {
        permutationsOfFour().forEach { permutation ->
            val world = World()
            val systems = freshSystems()
            permutation.map { systems[it] }.forEach(world::installSystem)

            roles.forEachIndexed { i, role ->
                assertSame(systems[i], world.systemOf(role).getOrNull(), "permutation $permutation, role ${role.java.name}")
            }
            val miss = world.systemOf(Undeclared::class)
            assertTrue(miss.isFailure, "permutation $permutation")
            assertEquals(Undeclared::class, assertIs<MissingWorldSystemException>(miss.exceptionOrNull()).role)
        }
    }

    @Test
    fun `two identically driven Worlds give identical lookup traces`() {
        fun drive(world: World): List<List<String>> {
            val traces = mutableListOf<List<String>>()
            val systems = freshSystems()
            world.installSystem(systems[2])
            world.installSystem(systems[0])
            traces += lookupTrace(world)
            world.uninstallSystem(systems[2])
            world.installSystem(systems[3])
            world.installSystem(systems[1])
            traces += lookupTrace(world)
            world.stepSystems()
            traces += lookupTrace(world)
            return traces
        }

        assertEquals(drive(World()), drive(World()))
    }

    @Test
    fun `lookups depend only on the recorded roles, not on the uninstall and re-install history`() {
        // Three different histories that all end with the same four systems installed.
        fun historyA(world: World, s: List<Named>) = s.forEach(world::installSystem)

        fun historyB(world: World, s: List<Named>) {
            s.reversed().forEach(world::installSystem)
            world.uninstallSystem(s[1])
            world.uninstallSystem(s[3])
            world.installSystem(s[3])
            world.installSystem(s[1])
        }

        fun historyC(world: World, s: List<Named>) {
            world.installSystem(s[2])
            world.uninstallSystem(s[2])
            world.installSystem(s[0])
            world.installSystem(s[2])
            world.installSystem(s[3])
            world.uninstallSystem(s[0])
            world.installSystem(s[1])
            world.installSystem(s[0])
        }

        val traces = listOf(::historyA, ::historyB, ::historyC).map { history ->
            World().also { history(it, freshSystems()) }.let(::lookupTrace)
        }

        assertEquals(listOf("physics", "zone", "tierA", "tierB", "miss"), traces.first())
        assertTrue(traces.all { it == traces.first() }, "traces differ: $traces")
    }
}
