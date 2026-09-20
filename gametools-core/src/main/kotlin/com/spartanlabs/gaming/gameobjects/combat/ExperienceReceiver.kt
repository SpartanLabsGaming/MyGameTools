package com.spartanlabs.gaming.gameobjects.combat

import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.log
import com.spartanlabs.geometry.Point
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

    /** How far away this receiver can receive [experience] from. */
    val experienceReceptionRange: Double
    val myLoc: Point
    infix fun isWithinXPRXRange(grantorLocation: Point): Boolean {
        val distance = (myLoc distanceFrom grantorLocation).getOrElse {
            log.warn("Failed to calculate distance between ${myLoc} and ${grantorLocation} during xp granting")
            return false
        }
        return distance <= experienceReceptionRange
    }
    /**
     * Adds [experience] toward [nextLevelXPRequired], leveling up - possibly more than once -
     * for as much of it as crosses the threshold.
     *
     * @param experience the amount to add; must not be negative
     */
    fun receiveExperience(experience: Double, source: GameEvent.EntityDied)
}

/**
 * The default implementation of [ExperienceReceiver].
 * Completed but not usable on its own!!!
 * Requires the using class to implement [myLoc].
 */
open class DefaultExperienceReceiver : ExperienceReceiver {
    override var level: Double = 1.0
    override var experience: Double = 0.0
    /** [level] `1` needs `10.0 + 1^2 = 11.0` [experience]; the requirement grows with the square of [level]. */
    override val nextLevelXPRequired: Double
        get() = 10.0 + level.pow(2)
    override val experienceReceptionRange: Double = 1000.0
    override val myLoc get() = Point()

    override fun receiveExperience(experience: Double, source: GameEvent.EntityDied) {
        if(this isWithinXPRXRange source.entity.location)
        this.experience += experience
        // A loop, not a single check, so several small deposits that together cross more than
        // one threshold all land in the same call.
        while (this.experience >= nextLevelXPRequired) {
            this.experience -= nextLevelXPRequired
            level++
        }
    }
}
