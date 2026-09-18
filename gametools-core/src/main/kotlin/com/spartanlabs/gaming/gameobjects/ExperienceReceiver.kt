package com.spartanlabs.gaming.gameobjects

import kotlin.math.pow

/**
 * Something that accrues [experience] toward [level]s, each requiring more experience than the
 * last.
 */
interface ExperienceReceiver {
    /** How many times this receiver has leveled up; starts at `1.0`. */
    var level: Double

    /** Experience accrued toward [nextLevelXPRequired] since the last level-up. */
    var experience: Double

    /** How much more [experience] is needed to reach the next [level]. */
    val nextLevelXPRequired: Double

    /**
     * Adds [experience] toward [nextLevelXPRequired], leveling up - possibly more than once -
     * for as much of it as crosses the threshold.
     *
     * @param experience the amount to add; must not be negative
     */
    fun receiveExperience(experience: Double)
}

/** [level] `1` needs `10.0 + 1^2 = 11.0` [experience]; the requirement grows with the square of [level]. */
class DefaultExperienceReceiver : ExperienceReceiver {
    override var level: Double = 1.0
    override var experience: Double = 0.0
    override val nextLevelXPRequired: Double
        get() = 10.0 + level.pow(2)

    override fun receiveExperience(experience: Double) {
        this.experience += experience
        // A loop, not a single check, so several small deposits that together cross more than
        // one threshold all land in the same call.
        while (this.experience >= nextLevelXPRequired) {
            this.experience -= nextLevelXPRequired
            level++
        }
    }
}
