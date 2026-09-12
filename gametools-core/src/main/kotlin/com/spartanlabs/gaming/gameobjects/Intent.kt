package com.spartanlabs.gaming.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
//endregion

/**
 * A unit's current standing order. Exactly one is active on an [Actor] at a time
 * ([Actor.intent]); issuing a new one via [Actor.issue] always tears down the previous one
 * first via [clear].
 *
 * Not a Kotlin `enum` (final, so neither [Alive] nor a consumer could extend it) and not
 * `sealed` (an intent can carry operands - a [Move]'s destination, an `AttackIntent`'s target -
 * which enum constants cannot hold). A consumer adds its own subclass the same way it adds a
 * [com.spartanlabs.gaming.networking.command.ClientCommand].
 */
abstract class Intent {

    /** Stable wire label for this intent - see [ActorSnapshot.intent]. */
    abstract val label: String

    /** Wires up the mechanism this order drives. Called by [Actor.issue] after the previous intent's [clear]. */
    open fun issue(actor: Actor) {}

    /** Stops the mechanism and undoes any side effect. Called when this intent is replaced or explicitly cleared. */
    open fun clear(actor: Actor) {}
}

/** No standing order. The actor holds whatever [Actor.movement] / [Actor.destination] it last had. */
data object Idle : Intent() {
    override val label = "idle"
}

/**
 * Standing order: advance under [movement], optionally retargeting [destination] as it is
 * issued.
 *
 * [clear] is what actually halts an actor in place - not [Idle.issue] - so replacing a `Move`
 * (with another `Move`, an attack, or an explicit [Actor.clearIntent]) always leaves the actor
 * at rest under [Movement.Targeting] unless the next intent's own [issue] immediately sends it
 * somewhere else.
 *
 * @property movement the [Movement] strategy this order installs on [Actor.movement]
 * @property destination if given, the point [Actor.destination] is set to when this order is issued
 */
data class Move(val movement: Movement, val destination: Point? = null) : Intent() {
    override val label = "move"

    /** Retargets [destination] (if given) and installs [movement] on the actor. */
    override fun issue(actor: Actor) {
        destination?.let { actor.destination = it }
        actor.movement = movement
    }

    /** Undoes this order's mechanism: returns the actor to a neutral, stopped-in-place state. */
    override fun clear(actor: Actor) {
        actor.movement = Movement.Targeting
        actor.destination = Point(actor.location)
    }
}
