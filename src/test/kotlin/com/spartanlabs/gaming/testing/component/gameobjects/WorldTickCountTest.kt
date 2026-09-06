package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
//endregion

/** Covers [World.tickCount] and [World.seed] / [World.rng] wiring. */
class WorldTickCountTest {

    @Test
    fun `tickCount starts at zero and counts every tick`() {
        val world = World(seed = 1L)
        assertEquals(0L, world.tickCount)

        repeat(5) { world.tick() }

        assertEquals(5L, world.tickCount)
    }

    @Test
    fun `the world exposes the seed it was built with`() {
        assertEquals(1234L, World(seed = 1234L).seed)
    }

    @Test
    fun `two worlds with the same seed draw the same random sequence`() {
        val a = World(seed = 55L)
        val b = World(seed = 55L)
        assertEquals(List(50) { a.rng.nextDouble() }, List(50) { b.rng.nextDouble() })
    }
}
