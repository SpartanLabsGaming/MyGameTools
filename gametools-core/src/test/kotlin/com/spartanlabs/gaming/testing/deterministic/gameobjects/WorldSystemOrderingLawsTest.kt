package com.spartanlabs.gaming.testing.deterministic.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/**
 * Level 4a - deterministic ordering laws for [World.installSystem]/[World.stepSystems]: the
 * step order and [World.installedSystems] are the same rule-derived result no matter what order
 * a set of fakes is installed in, and two identically-driven [World]s produce identical traces.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemOrderingLawsTest {

    private class NamedSystem(
        val name: String,
        override val coreSlot: CoreSystemSlot? = null,
        private val trace: MutableList<String>? = null,
    ) : AbstractWorldSystem() {
        override fun step() {
            trace?.add(name)
        }
    }

    /** Every permutation of the indices `0..3`, as a property-test's input space (`4!` = 24). */
    private fun permutationsOfFour(): List<List<Int>> {
        fun permute(remaining: List<Int>): List<List<Int>> =
            if (remaining.isEmpty()) listOf(emptyList())
            else remaining.flatMap { pick -> permute(remaining - pick).map { listOf(pick) + it } }
        return permute(listOf(0, 1, 2, 3))
    }

    @Test
    fun `step order and installedSystems are the same law-shaped result for every install permutation`() {
        permutationsOfFour().forEach { permutation ->
            val world = World()
            val trace = mutableListOf<String>()
            val physics = NamedSystem("physics", CoreWorldSystemSlot.PHYSICS, trace)
            val zone = NamedSystem("zone", CoreWorldSystemSlot.ZONE, trace)
            val tier2A = NamedSystem("tier2A", trace = trace)
            val tier2B = NamedSystem("tier2B", trace = trace)
            val systems = listOf(physics, zone, tier2A, tier2B)

            val installOrder = permutation.map { systems[it] }
            installOrder.forEach(world::installSystem)

            // The rule itself, not a hard-coded expectation: tier 1 by CoreSystemSlot.order
            // (fixed, regardless of install order), then tier 2 in this permutation's own
            // relative install order.
            val expected = listOf(physics, zone) + installOrder.filter { it.coreSlot == null }

            assertEquals(expected, world.installedSystems, "install order $installOrder")

            world.stepSystems()
            assertEquals(expected.map { it.name }, trace, "install order $installOrder")
        }
    }

    @Test
    fun `identical install-uninstall-step call sequences on two different Worlds produce identical traces`() {
        fun runSequence(world: World): List<String> {
            val trace = mutableListOf<String>()
            val physics = NamedSystem("physics", CoreWorldSystemSlot.PHYSICS, trace)
            val tier2A = NamedSystem("tier2A", trace = trace)
            val tier2B = NamedSystem("tier2B", trace = trace)

            world.installSystem(tier2A)
            world.installSystem(physics)
            world.stepSystems()
            world.uninstallSystem(tier2A)
            world.installSystem(tier2B)
            world.stepSystems()

            return trace
        }

        assertEquals(runSequence(World()), runSequence(World()))
    }
}
