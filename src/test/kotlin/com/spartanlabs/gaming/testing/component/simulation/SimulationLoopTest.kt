package com.spartanlabs.gaming.testing.component.simulation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.simulation.LoopSettings
import com.spartanlabs.gaming.simulation.SimulationLoop
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/**
 * Covers [SimulationLoop]'s fixed-timestep accounting through its [SimulationLoop.advance]
 * entry point - no driver thread, no real clock - plus its start/stop contract.
 */
class SimulationLoopTest {

    private val world = World(seed = 1L)

    private fun nanosPerTick(hz: Double): Long = (1_000_000_000.0 / hz).toLong()

    @Test
    fun `advancing by three ticks' worth of time runs three ticks`() {
        val settings = LoopSettings(tickRateHz = 20.0)
        val loop = SimulationLoop(world, settings)

        loop.advance(3 * nanosPerTick(20.0))

        assertEquals(3L, world.tickCount)
    }

    @Test
    fun `sub-tick time accumulates instead of stepping`() {
        val settings = LoopSettings(tickRateHz = 20.0)
        val loop = SimulationLoop(world, settings)
        val perTick = nanosPerTick(20.0)

        loop.advance(perTick / 3)
        loop.advance(perTick / 3)
        assertEquals(0L, world.tickCount)

        loop.advance(perTick) // now well over one tick's worth in total
        assertEquals(1L, world.tickCount)
    }

    @Test
    fun `the catch-up cap bounds a long stall`() {
        val settings = LoopSettings(tickRateHz = 20.0, maxCatchUpTicks = 5)
        val loop = SimulationLoop(world, settings)

        loop.advance(100 * nanosPerTick(20.0)) // a 5-second stall at 20 Hz

        assertEquals(5L, world.tickCount, "no more than maxCatchUpTicks should run in one advance")
    }

    @Test
    fun `raising the tick rate mid-run steps faster`() {
        val settings = LoopSettings(tickRateHz = 10.0, maxCatchUpTicks = 100)
        val loop = SimulationLoop(world, settings)
        val window = 100_000_000L // 0.1s

        loop.advance(window)                 // 10 Hz -> 1 tick
        assertEquals(1L, world.tickCount)

        settings.tickRateHz = 100.0
        loop.advance(window)                 // 100 Hz -> +10 ticks
        assertEquals(11L, world.tickCount)
    }

    @Test
    fun `onTick receives the running tick count after each step`() {
        val counts = mutableListOf<Long>()
        val loop = SimulationLoop(world, LoopSettings(tickRateHz = 20.0), onTick = { counts += it })

        loop.advance(3 * nanosPerTick(20.0))

        assertContentEquals(listOf(1L, 2L, 3L), counts)
    }

    @Test
    fun `start twice fails, stop is idempotent`() {
        val loop = SimulationLoop(world)

        assertTrue(loop.start().isSuccess)
        assertTrue(loop.isRunning)
        assertTrue(loop.start().isFailure, "a second start must fail")

        assertTrue(loop.stop().isSuccess)
        assertTrue(!loop.isRunning)
        assertTrue(loop.stop().isSuccess, "stopping an already-stopped loop is fine")
    }
}
