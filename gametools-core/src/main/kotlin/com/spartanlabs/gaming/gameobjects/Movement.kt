package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

/**
 * How an [Actor] chooses where to move on each [Actor.tick].
 *
 * Assign one to [Actor.movement]; [Targeting] is the default. Every strategy advances the
 * actor at most [Actor.speed] units per tick and reports a [Result] so a step that cannot be
 * computed (for example a NaN coordinate) surfaces instead of throwing.
 */
sealed class Movement {

    /** Moves [actor] one step under this strategy. Called once per [Actor.tick]. */
    internal abstract fun step(actor: Actor): Result<Unit>

    /**
     * Head for [Actor.destination] and stop on arrival: once the actor reaches the
     * destination it holds position until a new [Actor.destination] is assigned. The default.
     */
    data object Targeting : Movement() {
        override fun step(actor: Actor): Result<Unit> =
            if (actor.hasSettled) Result.success(Unit)
            else actor.stepTowardsDestination()
                .onSuccess { if (actor.isAtDestination) actor.hasSettled = true }
    }

    /**
     * Head for [Actor.destination] every tick, resuming the approach whenever the actor is
     * displaced - so an actor that gets pushed keeps trying to get back to it.
     */
    data object Persistent : Movement() {
        override fun step(actor: Actor): Result<Unit> = actor.stepTowardsDestination()
    }

    /**
     * Travel forever in a straight line along the actor's current [Actor.angle],
     * [Actor.speed] units per tick. [Actor.destination] is ignored.
     */
    data object Directional : Movement() {
        override fun step(actor: Actor): Result<Unit> = actor.stepAlongAngle()
    }

    /**
     * Chase [target]: each tick the actor's [Actor.destination] is re-pointed at [target]'s
     * current location and then pursued as in [Persistent].
     *
     * @property target the game object to home in on
     */
    data class Homing(val target: GameObject) : Movement() {
        override fun step(actor: Actor): Result<Unit> {
            actor.destination = Point(target.location)
            return actor.stepTowardsDestination()
        }
    }
}
