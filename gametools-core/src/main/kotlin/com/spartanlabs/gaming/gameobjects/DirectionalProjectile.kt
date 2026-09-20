package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.gaming.gameobjects.combat.Alive
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
import com.spartanlabs.gaming.spatial.QuadtreeSpatialIndex
import com.spartanlabs.gaming.spatial.SpatialIndex
//endregion

/**
 * A [Projectile] that flies in a straight line along a fixed heading for a limited number of
 * ticks, dealing its [damage] to every [com.spartanlabs.gaming.gameobjects.combat.Alive] it passes through - each one only once, so it
 * can pierce a whole line of targets.
 *
 * Collision uses the same broad-then-narrow phase as [HomingProjectile]: [Actor.nearby] over
 * [index] gathers candidates within [searchRadius], and [VisibleObject.collidesWith]
 * decides the actual overlap. When its lifetime runs out the projectile deactivates (which
 * also hides it; see [VisibleObject.active]).
 *
 * @param location where the projectile starts
 * @param dimensions the projectile's size
 * @param damage the health removed from each [com.spartanlabs.gaming.gameobjects.combat.Alive] it hits
 * @param directionAngle the heading to travel along, in whole degrees (normalised to `0..359`)
 * @param maxDuration how many ticks the projectile lives before deactivating; must be positive
 * @param index the spatial index of candidate targets, keyed by world position, kept current by the caller
 * @param searchRadius half-extent of the broad-phase window scanned for [com.spartanlabs.gaming.gameobjects.combat.Alive]s each tick;
 *        defaults to triple the projectile's width
 */
open class DirectionalProjectile(
    location: Point,
    dimensions: Dimensions,
    damage: Double,
    directionAngle: Int,
    private val maxDuration: Int,
    private val index: SpatialIndex<VisibleObject>,
    private val searchRadius: Double = dimensions.width * 3
) : Projectile(location = location, dimensions = dimensions, damage = damage) {

    /**
     * Source-compatible with callers that still construct a [DirectionalProjectile] against a
     * bare [Quadtree]: wraps it in a [QuadtreeSpatialIndex] and delegates.
     *
     * @param quadtree the spatial index of candidate targets, keyed by world position, kept current by the caller
     */
    constructor(
        location: Point,
        dimensions: Dimensions,
        damage: Double,
        directionAngle: Int,
        maxDuration: Int,
        quadtree: Quadtree<Double, VisibleObject>,
        searchRadius: Double = dimensions.width * 3
    ) : this(location, dimensions, damage, directionAngle, maxDuration, QuadtreeSpatialIndex(quadtree), searchRadius)

    /** The [com.spartanlabs.gaming.gameobjects.combat.Alive]s already damaged, so none is hit twice. */
    private val hitAlives = mutableSetOf<Alive>()

    /** Ticks elapsed since the projectile was created. */
    private var ticksLived = 0

    init {
        require(maxDuration > 0) { "maxDuration must be positive but was $maxDuration" }
        angle = directionAngle
        movement = Movement.Directional
    }

    /** Advances along [angle], damages any freshly touched [Alive], then expires once spent. */
    override fun onUpdate() {
        super.onUpdate()
        alivesInContact().forEach { alive ->
            if (hitAlives.add(alive)) dealDamageTo(alive)
        }
        if (++ticksLived >= maxDuration) {
            active = false
            log.debug("A DirectionalProjectile expired after {} ticks", ticksLived)
        }
    }

    /** The [Alive]s from [index] within [searchRadius] that this projectile currently overlaps. */
    private fun alivesInContact(): List<Alive> =
        nearby(index, searchRadius)
            .filterIsInstance<Alive>()
            .filter { collidesWith(it) }
}
