package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
//endregion

/**
 * An [Actor] that travels until it connects with something and then deals [damage].
 *
 * A concrete subclass decides what the projectile pursues and how it recognises a hit
 * (see [HomingProjectile]); [dealDamageTo] applies the payload once it does.
 *
 * @param location where the projectile starts
 * @param dimensions the projectile's size
 * @param damage the health this projectile removes from whatever it hits
 */
abstract class Projectile(
    location: Point,
    dimensions: Dimensions,
    val damage: Double
) : Actor(location = location, dimensions = dimensions) {

    /**
     * Subtracts this projectile's [damage] from [target]'s health.
     *
     * Intended to be called exactly once, at the moment the projectile connects.
     *
     * @param target the actor taking the hit
     */
    protected fun dealDamageTo(target: Alive) {
        target.health.current -= damage
        log.debug(
            "A {} dealt {} damage; target health is now {}",
            this::class.simpleName, damage, target.health.current
        )
    }
}
