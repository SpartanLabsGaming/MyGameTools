package com.spartanlabs.gaming.event

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.GameObject
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

    // Attack-lifecycle events (AttackCancelled, AttackEnded) are added with the
    // Alive.cancelAttack work - see issues #1 / #2.
}
