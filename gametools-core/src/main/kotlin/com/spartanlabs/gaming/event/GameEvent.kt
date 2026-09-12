package com.spartanlabs.gaming.event

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Intent
import com.spartanlabs.gaming.gameobjects.World
//endregion

/**
 * Something that happened in the simulation, published on a [World.events] bus so any number
 * of systems - the networking layer building a client update, a score counter, aggro logic -
 * can react to it without being wired into the code that caused it.
 *
 * Events are delivered synchronously, on the thread that called [EventBus.publish] (which for
 * the built-in events is the thread running [World.tick]), in the order they were published.
 *
 * This is the Phase 0 set; combat, ability, and item events are added by later phases.
 */
sealed interface GameEvent {

    /**
     * A [GameObject] was taken into a [World] - either through [World.add], or found in
     * [World.gameObjects] at the top of the first [World.tick] after a direct list addition.
     * Fires again if the same instance leaves the world and is re-added.
     *
     * @property entity the object that joined
     */
    data class EntitySpawned(val entity: GameObject) : GameEvent

    /**
     * A [GameObject] left its [World] - it was dropped via [World.removeList] at the end of a
     * [World.tick]. After this fires, [World.byId] no longer resolves it.
     *
     * @property entity the object that left
     */
    data class EntityRemoved(val entity: GameObject) : GameEvent

    /**
     * An [Alive] was ordered to attack another via [Alive.issueAttack].
     *
     * @property attacker the actor given the order
     * @property target the actor it was told to attack
     */
    data class AttackIssued(val attacker: Alive, val target: Alive) : GameEvent

    /**
     * An [Alive]'s swing connected with its target (before evasion is rolled - a landed swing
     * is not necessarily a hit).
     *
     * @property attacker the swinging actor
     * @property target the actor swung at
     * @property damage the attacker's effective [Alive.damage] at swing time
     */
    data class AttackLanded(val attacker: Alive, val target: Alive, val damage: Double) : GameEvent

    /**
     * Health was removed from an [Alive].
     *
     * @property source the actor that dealt it, or `null` for damage with no [Alive] behind it
     * @property target the actor that lost health
     * @property amount the health change applied after mitigation - positive removes health
     */
    data class DamageDealt(val source: Alive?, val target: Alive, val amount: Double) : GameEvent

    /**
     * An [Alive]'s health reached zero and its [Alive.DeathResponse] was applied. Fires once
     * per death; a respawning actor that dies again fires it again.
     *
     * @property entity the actor that died
     * @property killer the actor whose damage last reduced [entity]'s health, or `null` when
     *   that is not known (for example a kill dealt by a projectile or by direct health edits)
     */
    data class EntityDied(val entity: Alive, val killer: Alive?) : GameEvent

    /**
     * An [Alive]'s attack was called off from outside via [Alive.cancelAttack].
     *
     * @property attacker the actor that had been attacking
     * @property formerTarget the actor it had been attacking, or `null` if none was set
     */
    data class AttackCancelled(val attacker: Alive, val formerTarget: Alive?) : GameEvent

    /**
     * An [Alive]'s attack stopped on its own because the target could no longer be fought -
     * it died, or left the world - rather than because [Alive.cancelAttack] was called.
     *
     * @property attacker the actor that had been attacking
     * @property formerTarget the actor it had been attacking
     * @property reason why the attack ended
     */
    data class AttackEnded(
        val attacker: Alive,
        val formerTarget: Alive?,
        val reason: Alive.AttackEndReason,
    ) : GameEvent

    /**
     * An [Actor] was given a new standing order via [Actor.issue].
     *
     * @property actor the actor the order was issued to
     * @property intent the standing order it was given
     */
    data class IntentIssued(val actor: Actor, val intent: Intent) : GameEvent

    /**
     * An [Actor]'s standing order was cleared back to [com.spartanlabs.gaming.gameobjects.Idle]
     * via [Actor.issue] / [Actor.clearIntent].
     *
     * @property actor the actor whose order was cleared
     * @property previous the standing order that was active before it was cleared
     */
    data class IntentCleared(val actor: Actor, val previous: Intent) : GameEvent
}
