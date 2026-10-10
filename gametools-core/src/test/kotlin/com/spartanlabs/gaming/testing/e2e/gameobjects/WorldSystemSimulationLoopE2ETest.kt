package com.spartanlabs.gaming.testing.e2e.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.annotation.ExperimentalGameToolsApi
import com.spartanlabs.gaming.gameobjects.AbstractWorldSystem
import com.spartanlabs.gaming.gameobjects.CoreSystemSlot
import com.spartanlabs.gaming.gameobjects.CoreWorldSystemSlot
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.simulation.SimulationLoop
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
//endregion

/**
 * Level 4b - end-to-end: a real [SimulationLoop] driving [World.tick] and [World.stepSystems]
 * together, the way [World.stepSystems]'s own KDoc example wires them
 * (`SimulationLoop(world, onTick = { world.stepSystems() })`). The first e2e test in
 * `gametools-core`.
 */
@OptIn(ExperimentalGameToolsApi::class)
class WorldSystemSimulationLoopE2ETest {

    private class RecordingSystem(
        val name: String,
        override val coreSlot: CoreSystemSlot? = null,
        private val onStep: (World) -> Unit,
    ) : AbstractWorldSystem() {
        override fun step() = onStep(world)
    }

    @Test
    fun `a SimulationLoop with onTick equal to stepSystems steps installed systems once per tick, in tier order, after tick runs`() {
        val world = World()
        val order = mutableListOf<String>()
        val tickCountsSeen = mutableListOf<Long>()
        val tier1 = RecordingSystem("physics", CoreWorldSystemSlot.PHYSICS) { w ->
            order += "physics"
            tickCountsSeen += w.tickCount
        }
        val tier2 = RecordingSystem("tier2") { w ->
            order += "tier2"
            tickCountsSeen += w.tickCount
        }
        world.installSystem(tier1)
        world.installSystem(tier2)
        val loop = SimulationLoop(world, onTick = { world.stepSystems() })
        val nanosPerTick = (1_000_000_000.0 / loop.settings.tickRateHz).toLong()

        repeat(3) { loop.advance(nanosPerTick) }

        assertEquals(List(3) { listOf("physics", "tier2") }.flatten(), order)
        assertEquals(listOf(1L, 1L, 2L, 2L, 3L, 3L), tickCountsSeen)
    }

    @Test
    fun `uninstalling a system between two advance calls stops it from stepping on the next one`() {
        val world = World()
        val order = mutableListOf<String>()
        val system = RecordingSystem("system") { order += "system" }
        world.installSystem(system)
        val loop = SimulationLoop(world, onTick = { world.stepSystems() })
        val nanosPerTick = (1_000_000_000.0 / loop.settings.tickRateHz).toLong()

        loop.advance(nanosPerTick)
        assertEquals(listOf("system"), order)

        world.uninstallSystem(system)
        loop.advance(nanosPerTick)

        assertEquals(listOf("system"), order) // no further step recorded
    }

    @Test
    fun `a system built with no World is bound by installSystem, stepped by a SimulationLoop, survives uninstall and re-install on the same World`() {
        val worldsSeen = mutableListOf<World>()
        val tickCountsSeen = mutableListOf<Long>()
        val system = RecordingSystem("system") { w ->
            worldsSeen += w
            tickCountsSeen += w.tickCount
        } // constructed before any World exists
        val world = World()
        world.installSystem(system)
        val loop = SimulationLoop(world, onTick = { world.stepSystems() })
        val nanosPerTick = (1_000_000_000.0 / loop.settings.tickRateHz).toLong()

        repeat(2) { loop.advance(nanosPerTick) }
        world.uninstallSystem(system)
        repeat(2) { loop.advance(nanosPerTick) } // ticks 3 and 4: not installed, never stepped
        world.installSystem(system)
        repeat(2) { loop.advance(nanosPerTick) }

        assertSame(world, system.world)
        assertEquals(List(4) { world }, worldsSeen)
        assertEquals(listOf(1L, 2L, 5L, 6L), tickCountsSeen)
    }
}
