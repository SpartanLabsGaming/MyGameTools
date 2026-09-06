package com.spartanlabs.gaming.gameobjects
//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
import com.spartanlabs.geometry.serializations.PointSnapshot
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.spatial.Quadtree
//endregion

//region 2. Intended Function
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
//endregion

/**
 * A [VisibleObject] that moves under its own power.
 *
 * What it does each [tick] is decided by its [movement] strategy ([Movement.Targeting] by
 * default): head for a [destination] and stop, pursue one persistently, travel along a fixed
 * [angle], or home in on another [GameObject]. Whichever is chosen, the actor advances at
 * most [speed] units per tick. Assigning [destination] also re-aims [angle] at that point.
 *
 * @param location the actor's starting position; also its initial [destination].
 * @param dimensions the actor's size.
 */
open class Actor(
    location : Point = Point(),
    dimensions: Dimensions = Dimensions(),
):VisibleObject(location = location, dimensions = dimensions) {

    //region CAPABILITIES
    /** An actor adds [CoreCapability.MOVE] to whatever its supertypes provide. */
    override val capabilities: Set<Capability> = super.capabilities + CoreCapability.MOVE

    /** An actor exposes its [speed] under the key `"speed"`, on top of its supertypes' stats. */
    override val stats: Map<String, Moddable> get() = super.stats + ("speed" to speed)
    //endregion

    /**
     * The actor's movement rate in units per tick.
     *
     * A [ModularStat] so that hastes and slows can be layered on as [StatMod]s rather than
     * folded into one multiplier: adjust [ModularStat.base] for a permanent change, or
     * [ModularStat.applyMod] / [ModularStat.removeMod] for temporary ones. Its effective
     * [ModularStat.value] (base `10.0`) is what each [Movement] strategy advances the actor by,
     * and what [ActorSnapshot] captures. A negative value simply runs the actor backwards; it
     * is not rejected.
     */
    var speed: ModularStat = ModularStat(base = 10.0)

    /**
     * `true` once a [Movement.Targeting] actor has reached its [destination] and stopped.
     * Cleared whenever a new [destination] is assigned or [movement] changes, so the actor
     * resumes moving.
     */
    internal var hasSettled = false

    /**
     * How this actor decides where to move each [tick]. Defaults to [Movement.Targeting].
     *
     * @see Movement.Targeting
     * @see Movement.Persistent
     * @see Movement.Directional
     * @see Movement.Homing
     */
    var movement: Movement = Movement.Targeting
        set(value) {
            field = value
            hasSettled = false
            log.debug("Actor movement set to {}.", value)
        }

    /**
     * The point the actor is moving towards.
     *
     * Assigning a new destination also updates [angle] to face it, measured in
     * whole degrees counter-clockwise from the positive x-axis and normalised to
     * `0..359`. The angle is computed from the actor's position at assignment time;
     * it is not re-derived as the actor moves. If the new destination coincides with
     * the actor's current position, or holds a NaN coordinate, [angle] is left
     * unchanged (there is no direction to face).
     */
    var destination = Point(location)
        set(value) {
            field = value
            hasSettled = false
            val dx = value.x - location.x
            val dy = value.y - location.y
            when {
                dx.isNaN() || dy.isNaN() ->
                    log.warn("Destination {} has a NaN coordinate; angle left at {} degrees.", value, angle)
                dx == 0.0 && dy == 0.0 ->
                    log.debug("Destination {} matches current position; angle left at {} degrees.", value, angle)
                else -> {
                    angle = Math.toDegrees(atan2(dy, dx)).roundToInt().mod(360)
                    log.debug("Destination set to {}; angle now {} degrees.", value, angle)
                }
            }
        }

    /** `true` once the actor has reached its [destination]. */
    val isAtDestination get() = location == destination

    /**
     * `true` when the actor is close enough to reach its [destination] in a single step.
     *
     * Carries the failed [Result] from [Point.distanceFrom] (rather than throwing) when
     * [location] or [destination] holds a NaN coordinate.
     */
    val isOneStepAway : Result<Boolean> get() = location.distanceFrom(destination)
        .map { distance -> distance < speed }

    /**
     * The displacement to apply this tick: a vector of length [speed] pointing from
     * [location] towards [destination].
     */
    val locmod get() =
        hypot(location.x - destination.x, location.y - destination.y).let { hypotenuse ->
            Point(
                speed * (destination.x - location.x) / hypotenuse,
                speed * (destination.y - location.y) / hypotenuse
            )
        }

    /**
     * Runs the base per-frame work, then - while [CoreCapability.MOVE] is usable - applies
     * this tick's [movement]. An actor whose move capability is suppressed by a [Buff] holds
     * position for that tick.
     */
    override fun onUpdate() {
        super.onUpdate()
        if (can(CoreCapability.MOVE))
            move().onFailure { cause -> log.error("Actor was not moved this tick.", cause) }
        else
            log.debug("Actor held in place this tick; its move capability is suppressed.")
    }

    /** Performs this tick's movement by delegating to the current [movement] strategy. */
    internal fun move(): Result<Unit> = movement.step(this)

    /**
     * Steps the actor towards [destination], snapping onto it once within one step.
     *
     * @return [Result.success] once the step is applied (including the no-op case where
     * the actor is already at its [destination]), or the failed [Result] from
     * [isOneStepAway] when the distance to [destination] cannot be measured.
     */
    internal fun stepTowardsDestination(): Result<Unit> =
        if (isAtDestination) Result.success(Unit)
        else isOneStepAway.map { oneStepAway ->
            if (oneStepAway) {
                location.setTo(destination)
                log.debug("Arrived at destination {}.", destination)
            } else location += locmod
        }

    /**
     * Steps the actor [speed] units along its current [angle].
     *
     * @return always [Result.success]; a whole-degree [angle] is always a valid heading.
     */
    internal fun stepAlongAngle(): Result<Unit> {
        val radians = Math.toRadians(angle.toDouble())
        location += Point(speed * cos(radians), speed * sin(radians))
        log.debug("Stepped {} units along {} degrees to {}.", speed, angle, location)
        return Result.success(Unit)
    }

    /**
     * The objects indexed in [quadtree] whose position is within [range] of this actor's
     * [location] on both axes - a square broad-phase window, this actor included when it is
     * itself in the tree.
     *
     * @param quadtree the spatial index to query; the caller is responsible for keeping it current
     * @param range half the width and height of the window centred on [location]
     */
    fun nearby(quadtree: Quadtree<Double, VisibleObject>, range: Double): List<VisibleObject> =
        quadtree.retrieveBox(
            location.x - range, location.y - range,
            location.x + range, location.y + range
        )
}

/**
 * An immutable, serializable copy of an [Actor]'s state, layered on its [VisibleObjectSnapshot]:
 * how fast it moves and where it is headed. Sent in place of a plain [VisibleObjectSnapshot]
 * whenever a broadcast object is an [Actor] (see [DrawableSnapshot]).
 *
 * @property id the actor's stable [EntityId.raw] ([DrawableSnapshot.UNIDENTIFIED] if unowned)
 * @property visibleObject the underlying [VisibleObjectSnapshot] - position, size, drawable state, sub-objects
 * @property speed the actor's effective movement rate ([Actor.speed]'s [ModularStat.value]) in
 *   units per tick at snapshot time
 * @property destination the point the actor was moving towards at snapshot time
 */
@Serializable
@SerialName("actor")
data class ActorSnapshot(
    override val id: Long = DrawableSnapshot.UNIDENTIFIED,
    val visibleObject: VisibleObjectSnapshot,
    val speed: Double,
    val destination: PointSnapshot) : DrawableSnapshot {

    /** The actor's sub-object snapshots - the same list as [visibleObject]'s. */
    override val subObjects: List<DrawableSnapshot> get() = visibleObject.subObjects

    companion object {
        /** Takes a snapshot of [actor]'s movement state along with its drawable state and sub-objects. */
        infix fun from(actor: Actor): ActorSnapshot = ActorSnapshot(
            id = actor.entityId.raw,
            visibleObject = VisibleObjectSnapshot.from(actor),
            speed = actor.speed.value,
            destination = PointSnapshot.from(actor.destination)
        )
    }
}
