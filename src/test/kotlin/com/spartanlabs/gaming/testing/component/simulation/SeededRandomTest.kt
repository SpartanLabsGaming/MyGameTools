package com.spartanlabs.gaming.testing.component.simulation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.simulation.SeededRandom
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
//endregion

/** Covers [SeededRandom] reproducing its sequence for a given seed and diverging for a different one. */
class SeededRandomTest {

    private fun draw(seed: Long, count: Int = 200): List<Double> =
        SeededRandom(seed).let { rng -> List(count) { rng.nextDouble() } }

    @Test
    fun `the same seed yields the same sequence`() {
        assertEquals(draw(seed = 99L), draw(seed = 99L))
    }

    @Test
    fun `a different seed yields a different sequence`() {
        assertTrue(draw(seed = 1L) != draw(seed = 2L))
    }

    @Test
    fun `nextInt stays in range`() {
        val rng = SeededRandom(7L)
        repeat(1_000) { assertTrue(rng.nextInt(10) in 0..9) }
    }

    @Test
    fun `nextBoolean produces both values`() {
        val rng = SeededRandom(7L)
        val draws = List(200) { rng.nextBoolean() }
        assertTrue(draws.any { it } && draws.any { !it })
    }
}
