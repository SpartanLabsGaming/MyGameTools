package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.gameobjects.WorldSystem
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
//endregion

/**
 * Covers [World.stepSystems]'s step order, mid-pass install/uninstall/reinstall semantics,
 * throw propagation, and its re-entrancy guard (R2, R9).
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldStepSystemsTest {

    /** A [WorldSystem] that appends [name] to [trace] on every [step], with a configurable extra action. */
    private open class RecordingSystem(
        val name: String,
        override val coreSlot: CoreSystemSlot? = null,
        private val trace: MutableList<String>? = null,
        private val onStep: (World) -> Unit = {},
    ) : WorldSystem {
        var stepCount = 0
            private set

        override fun installOn(world: World) {}

        override fun step(world: World) {
            stepCount++
            trace?.add(name)
            onStep(world)
        }
    }

    @Test
    fun `stepSystems on an empty World does nothing and does not throw`() {
        World().stepSystems() // must not throw
    }

    @Test
    fun `stepSystems steps every installed system once, tier 1 by order then tier 2 by install order`() {
        val world = World()
        val trace = mutableListOf<String>()
        val zone = RecordingSystem("zone", CoreWorldSystemSlot.ZONE, trace)
        val tier2A = RecordingSystem("tier2A", trace = trace)
        val physics = RecordingSystem("physics", CoreWorldSystemSlot.PHYSICS, trace)
        val tier2B = RecordingSystem("tier2B", trace = trace)

        listOf(zone, tier2A, physics, tier2B).forEach(world::installSystem)
        world.stepSystems()

        assertEquals(listOf("physics", "zone", "tier2A", "tier2B"), trace)
    }

    @Test
    fun `a system uninstalled by an earlier system's step is skipped for the rest of that pass`() {
        val world = World()
        val trace = mutableListOf<String>()
        val victim = RecordingSystem("victim", trace = trace)
        val remover = RecordingSystem("remover", trace = trace, onStep = { w -> w.uninstallSystem(victim) })
        world.installSystem(remover)
        world.installSystem(victim)

        world.stepSystems()

        assertEquals(listOf("remover"), trace)
    }

    @Test
    fun `a system that uninstalls itself from its own step is removed, and the rest of that pass still runs`() {
        val world = World()
        val trace = mutableListOf<String>()
        lateinit var quitter: RecordingSystem
        quitter = RecordingSystem("quitter", trace = trace, onStep = { w -> w.uninstallSystem(quitter) })
        val after = RecordingSystem("after", trace = trace)
        world.installSystem(quitter)
        world.installSystem(after)

        world.stepSystems()
        assertEquals(listOf("quitter", "after"), trace)
        assertEquals(listOf<WorldSystem>(after), world.installedSystems)

        trace.clear()
        world.stepSystems()
        assertEquals(listOf("after"), trace)
    }

    @Test
    fun `a system installed by an earlier system's step is not stepped until the next stepSystems call`() {
        val world = World()
        val trace = mutableListOf<String>()
        val installed = RecordingSystem("installed", trace = trace)
        var installedOnce = false
        val installer = RecordingSystem("installer", trace = trace, onStep = { w ->
            if (!installedOnce) {
                installedOnce = true
                w.installSystem(installed)
            }
        })
        world.installSystem(installer)

        world.stepSystems()
        assertEquals(listOf("installer"), trace)

        trace.clear()
        world.stepSystems()
        assertEquals(listOf("installer", "installed"), trace)
    }

    @Test
    fun `a system uninstalled and reinstalled mid-pass is not stepped again this pass, and steps normally next call`() {
        val world = World()
        val trace = mutableListOf<String>()
        val victim = RecordingSystem("victim", trace = trace)
        var churnedOnce = false
        val churner = RecordingSystem("churner", trace = trace, onStep = { w ->
            if (!churnedOnce) {
                churnedOnce = true
                w.uninstallSystem(victim)
                w.installSystem(victim)
            }
        })
        world.installSystem(churner)
        world.installSystem(victim)

        world.stepSystems()
        assertEquals(listOf("churner"), trace) // victim's snapshot record was removed, then a new one appended - not stepped this pass

        trace.clear()
        world.stepSystems()
        assertEquals(listOf("churner", "victim"), trace) // steps normally next call
    }

    @Test
    fun `if a step throws, later systems in the pass do not step, the exception propagates, and the registry is unchanged`() {
        val world = World()
        val trace = mutableListOf<String>()
        val before = RecordingSystem("before", trace = trace)
        val thrower = RecordingSystem("thrower", trace = trace, onStep = { error("boom") })
        val after = RecordingSystem("after", trace = trace)
        listOf(before, thrower, after).forEach(world::installSystem)

        assertFailsWith<IllegalStateException> { world.stepSystems() }

        assertEquals(listOf("before", "thrower"), trace)
        assertEquals(listOf<WorldSystem>(before, thrower, after), world.installedSystems)
    }

    @Test
    fun `the next stepSystems call after a throwing step runs normally`() {
        val world = World()
        val trace = mutableListOf<String>()
        var shouldThrow = true
        val system = RecordingSystem("system", trace = trace, onStep = { if (shouldThrow) error("boom") })
        world.installSystem(system)

        assertFailsWith<IllegalStateException> { world.stepSystems() }
        trace.clear()
        shouldThrow = false

        world.stepSystems() // must not throw
        assertEquals(listOf("system"), trace)
    }

    @Test
    fun `a re-entrant stepSystems call throws IllegalStateException, and the stepping guard resets afterward`() {
        val world = World()
        var reentered = false
        val system = RecordingSystem("system", onStep = { w ->
            if (!reentered) {
                reentered = true
                w.stepSystems()
            }
        })
        world.installSystem(system)

        assertFailsWith<IllegalStateException> { world.stepSystems() }

        world.stepSystems() // a following, non-nested call succeeds
    }

    @Test
    fun `World tick() never steps installed systems`() {
        val world = World()
        val system = RecordingSystem("system")
        world.installSystem(system)

        repeat(3) { world.tick() }

        assertEquals(0, system.stepCount)
    }
}
