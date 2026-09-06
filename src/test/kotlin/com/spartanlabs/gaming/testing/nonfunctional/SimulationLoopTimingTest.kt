package com.spartanlabs.gaming.testing.nonfunctional

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.simulation.LoopSettings
import com.spartanlabs.gaming.simulation.SimulationLoop
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertTrue
//endregion

/**
 * Level 4c - non-functional. Runs a real [SimulationLoop] driver thread against the wall clock
 * and checks the tick rate lands in a sane band (wide, to tolerate CI scheduling jitter) and
 * that raising [LoopSettings.tickRateHz] mid-run is picked up.
 */
class SimulationLoopTimingTest {

    @Test
    fun `the driver thread ticks at roughly the configured rate`() {
        val world = World(seed = 1L)
        val loop = SimulationLoop(world, LoopSettings(tickRateHz = 50.0))

        loop.start().getOrThrow()
        Thread.sleep(600)
        loop.stop().getOrThrow()

        // 600ms at 50 Hz is ~30 ticks; allow a very wide band for a loaded CI box.
        assertTrue(world.tickCount in 5..80, "50 Hz for 600ms produced ${world.tickCount} ticks")
    }

    @Test
    fun `raising the tick rate mid-run speeds the loop up`() {
        val world = World(seed = 2L)
        val settings = LoopSettings(tickRateHz = 10.0)
        val loop = SimulationLoop(world, settings)

        loop.start().getOrThrow()
        Thread.sleep(300)
        val slow = world.tickCount
        settings.tickRateHz = 200.0
        Thread.sleep(300)
        val fast = world.tickCount - slow
        loop.stop().getOrThrow()

        assertTrue(fast > slow, "after raising 10 Hz -> 200 Hz, the second window ($fast) should out-tick the first ($slow)")
    }

    @Test
    fun `stop halts ticking`() {
        val world = World(seed = 3L)
        val loop = SimulationLoop(world, LoopSettings(tickRateHz = 100.0))

        loop.start().getOrThrow()
        Thread.sleep(150)
        loop.stop().getOrThrow()
        val afterStop = world.tickCount
        Thread.sleep(150)

        assertTrue(world.tickCount == afterStop, "the world kept ticking after stop()")
    }
}
