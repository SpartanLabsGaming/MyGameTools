package com.spartanlabs.gaming.networking.command

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 3. Utility / Catch-all
// 3.2 Kotlin
// 3.2.1 Standard library
import kotlin.reflect.KClass
//endregion

/**
 * The outcome of [applyTo].
 *
 * [Applied] is the success case; the other three each mean the command was well-formed but
 * could not be carried out, and are returned rather than thrown so a command handler can log
 * and move on.
 */
sealed interface ApplyResult {

    /** The command's mechanism ran. */
    data object Applied : ApplyResult

    /**
     * No live object in the world has this id - it has died, been removed, or was never
     * added. Mirrors [World.byId] returning `null`.
     *
     * @property id the operand that did not resolve
     */
    data class TargetMissing(val id: EntityId) : ApplyResult

    /**
     * The id resolved, but to the wrong kind of object for this command - an [Actor]-only
     * command ([MoveTo], [MoveDir], [Follow], [Stop]) naming something that is not an [Actor],
     * or an [Alive]-only command ([Attack], [StopAttack]) naming something that is not an
     * [Alive]. This is a protocol mismatch, not an authorization failure.
     *
     * @property id the operand that resolved to the wrong type
     * @property expected the type the command needed
     */
    data class WrongType(val id: EntityId, val expected: KClass<out GameObject>) : ApplyResult

    /** Not one of GameTools' six standard commands - a consumer command this applier does not know. */
    data object Unhandled : ApplyResult
}

/**
 * Carries out a GameTools-standard [ClientCommand] against [world] by resolving its [EntityId]
 * operands through [World.byId] and calling the mechanism the command names:
 *
 * | Command | Mechanism |
 * |---|---|
 * | [MoveTo] | sets [Actor.destination] |
 * | [MoveDir] | sets [com.spartanlabs.gaming.gameobjects.VisibleObject.angle] and [Actor.movement] to [Movement.Directional] |
 * | [Follow] | sets [Actor.movement] to [Movement.Homing] on the resolved target |
 * | [Stop] | [Actor.movement] to [Movement.Targeting], destination pinned to current location |
 * | [Attack] | [Alive.issueAttack] on the resolved target |
 * | [StopAttack] | [Alive.cancelAttack] |
 *
 * ### A movement order calls off a pending attack
 *
 * Once a [MoveTo], [MoveDir], [Follow] or [Stop] has applied, this additionally calls
 * [Alive.cancelAttack] on the resolved actor when it is an [Alive] - a manual movement order
 * is a deliberate override of an in-progress auto-attack, the standard RTS expectation. It is
 * a no-op when the actor is not an [Alive] or is not attacking, and it runs only on the
 * success path (a [Follow] whose target does not resolve leaves the attack untouched).
 * [Attack] and [StopAttack] never touch movement.
 *
 * ### It does not authorize
 *
 * There is **no** ownership, faction, range or capability-suppression check here - by design.
 * Whether a given player may command a given object is game rules, and belongs in the app's
 * command handler, which should decide *before* calling this. What this does check is that
 * the operand resolves to the right *kind* of object (see [ApplyResult.WrongType]), because
 * that is a wire-protocol question, not a rules one.
 *
 * @receiver the command to carry out; a consumer command returns [ApplyResult.Unhandled]
 * @param world the world whose objects the command addresses
 * @return what happened - see [ApplyResult]
 */
fun ClientCommand.applyTo(world: World): ApplyResult = when (this) {
    is MoveTo -> onActor(world, actor) { it.destination = Point(x, y) }

    is MoveDir -> onActor(world, actor) { mover ->
        mover.angle = angleDegrees
        mover.movement = Movement.Directional
    }

    is Follow -> onActor(world, actor) { mover ->
        val chased = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        mover.movement = Movement.Homing(chased)
    }

    is Stop -> onActor(world, actor) { mover ->
        mover.movement = Movement.Targeting
        mover.destination = Point(mover.location)
    }

    is Attack -> onAlive(world, attacker) { aggressor ->
        val victim = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        val victimAlive = victim as? Alive ?: return ApplyResult.WrongType(target, Alive::class)
        aggressor.issueAttack(victimAlive)
    }

    is StopAttack -> onAlive(world, alive) { it.cancelAttack() }

    else -> ApplyResult.Unhandled
}

/**
 * Resolves [id] to an [Actor], runs the movement [action] on it, then calls off any attack it
 * has pending (see [applyTo]) - or returns the failure that stopped that. Inline so [action]
 * can `return` an [ApplyResult] straight out of [applyTo] when a secondary operand (a [Follow]
 * target) cannot be resolved; the attack is called off only once [action] has applied, so a
 * command that bailed out has no side effect.
 */
private inline fun onActor(world: World, id: EntityId, action: (Actor) -> Unit): ApplyResult {
    val resolved = world.byId(id) ?: return ApplyResult.TargetMissing(id)
    val actor = resolved as? Actor ?: return ApplyResult.WrongType(id, Actor::class)
    action(actor)
    (actor as? Alive)?.cancelAttack()
    return ApplyResult.Applied
}

/** [onActor]'s counterpart for the [Alive]-only commands. */
private inline fun onAlive(world: World, id: EntityId, action: (Alive) -> Unit): ApplyResult {
    val resolved = world.byId(id) ?: return ApplyResult.TargetMissing(id)
    val alive = resolved as? Alive ?: return ApplyResult.WrongType(id, Alive::class)
    action(alive)
    return ApplyResult.Applied
}
