package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.SpatialIndex
//endregion

/**
 * A [Projectile] that homes in on a single [Alive] and, the moment it collides with that
 * target, deals its [damage] once and deactivates.
 *
 * Collision is tested each tick rather than by reaching the target's exact location:
 * [index] is the broad phase (which objects are near the projectile) and
 * [VisibleObject.collidesWith] is the narrow phase (do their bounds actually overlap). The
 * caller owns [index] and is expected to keep it current - typically [World.spatialIndex],
 * reconciled once per frame by [World.tick] - before ticking this projectile.
 *
 * Deactivating also clears the projectile's `visible` flag (see [VisibleObject.active]), so a
 * spent projectile stops both simulating and being sent to clients.
 *
 * @param location where the projectile starts
 * @param dimensions the projectile's size
 * @param damage the health removed from [target] on impact
 * @param target the actor to chase and hit
 * @param index the spatial index of candidate collision targets, keyed by world position
 */
class HomingProjectile(
    location: Point,
    dimensions: Dimensions,
    damage: Double,
    private val target: Alive,
    private val index: SpatialIndex<VisibleObject>
) : Projectile(location = location, dimensions = dimensions, damage = damage) {

    /**
     * Source-compatible with callers that still construct a [HomingProjectile] against a bare
     * [Quadtree]: wraps it in a [QuadtreeSpatialIndex] and delegates.
     *
     * @param quadtree the spatial index of candidate collision targets, keyed by world position
     */
    constructor(
        location: Point,
        dimensions: Dimensions,
        damage: Double,
        target: Alive,
        quadtree: Quadtree<Double, VisibleObject>
    ) : this(location, dimensions, damage, target, QuadtreeSpatialIndex(quadtree))

    /** `true` once the payload has been delivered, so it is never applied twice. */
    private var hasHit = false

    init {
        movement = Movement.Homing(target)
    }

    /** Homes towards [target]; the tick it collides with it, deals [damage] once and deactivates. */
    override fun onUpdate() {
        super.onUpdate()
        if (!hasHit && hasCollidedWithTarget()) {
            dealDamageTo(target)
            hasHit = true
            active = false
            log.debug("A HomingProjectile hit its target and spent itself")
        }
    }

    /**
     * `true` when the projectile has run into [target].
     *
     * Broad phase: pull the objects near the projectile out of [index], so the check
     * scales to a world full of objects. Narrow phase: a hit needs [target] to be among them
     * and to actually overlap this projectile per [VisibleObject.collidesWith].
     */
    private fun hasCollidedWithTarget(): Boolean {
        val range = maxOf(
            (dimensions.width + target.dimensions.width) / 2.0,
            (dimensions.height + target.dimensions.height) / 2.0
        )
        return target in nearby(index, range) && collidesWith(target)
    }
}
