package com.spartanlabs.gaming.gameobjects.combat

/**
 * A named thing a [com.spartanlabs.gaming.gameobjects.GameObject] subtype is able to do - move, attack, cast - that can be
 * temporarily taken away by a [Buff].
 *
 * Each [com.spartanlabs.gaming.gameobjects.GameObject] subtype declares the capabilities it inherently has through
 * [com.spartanlabs.gaming.gameobjects.GameObject.capabilities]; whether one is *usable right now* is [com.spartanlabs.gaming.gameobjects.GameObject.can], which also
 * accounts for any [Buff] currently suppressing it. The built-in hierarchy uses
 * [CoreCapability]; a library consumer can implement this interface for its own subclasses'
 * abilities and gate their per-tick logic on [com.spartanlabs.gaming.gameobjects.GameObject.can] the same way.
 *
 * Implementations must have a stable, unique [id]: it is the identity used when a capability
 * crosses the wire in a [BuffSnapshot], so two different capabilities must never share one.
 *
 * @property id the stable key this capability is recognised by, locally and on the wire
 */
interface Capability {
    val id: String
}

/**
 * The capabilities the built-in [com.spartanlabs.gaming.gameobjects.GameObject] hierarchy defines.
 *
 * @property id the stable wire key, matching [Capability.id]
 */
enum class CoreCapability(override val id: String) : Capability {

    /** [com.spartanlabs.gaming.gameobjects.Actor]'s ability to advance itself each tick under its [com.spartanlabs.gaming.gameobjects.Movement] strategy. Suppress it to root an actor in place. */
    MOVE("move"),

    /** [Alive]'s ability to run its attack cycle each tick. Suppress it to disarm; suppress it with [MOVE] to stun. */
    ATTACK("attack")
}
