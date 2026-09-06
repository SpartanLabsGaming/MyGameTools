package com.spartanlabs.gaming.simulation

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.random.Random
//endregion

/**
 * The simulation's source of randomness.
 *
 * Every random choice the engine makes - an evasion roll, later a crit or a loot pick - goes
 * through one of these rather than [Math.random] or [Random.Default], so that a
 * [com.spartanlabs.gaming.gameobjects.World] built from a fixed seed and fed a fixed sequence
 * of inputs produces the same result every run. That reproducibility is what makes a desync
 * debuggable and a test stable; match-replay tooling is a separate, later concern.
 *
 * An implementation is not required to be thread-safe: drive one world, and its
 * [com.spartanlabs.gaming.gameobjects.World.random], from a single thread.
 */
interface RandomSource {

    /** A uniformly distributed value in `[0.0, 1.0)`. */
    fun nextDouble(): Double

    /**
     * A uniformly distributed non-negative value below [untilExclusive].
     * @param untilExclusive the exclusive upper bound; must be positive
     */
    fun nextInt(untilExclusive: Int): Int

    /** `true` or `false` with equal probability. */
    fun nextBoolean(): Boolean
}

/**
 * The default [RandomSource]: a thin wrapper over [kotlin.random.Random] seeded from [seed], so
 * two instances built with the same seed yield the same sequence.
 *
 * @param seed the seed the underlying generator is initialised from
 */
class SeededRandom(seed: Long) : RandomSource {

    private val random: Random = Random(seed)

    override fun nextDouble(): Double = random.nextDouble()

    override fun nextInt(untilExclusive: Int): Int = random.nextInt(untilExclusive)

    override fun nextBoolean(): Boolean = random.nextBoolean()
}
