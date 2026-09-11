package com.spartanlabs.gaming.networking.command

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AttackIntent
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.GameObject
import com.spartanlabs.gaming.gameobjects.Move
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
 * operands through [World.byId] and issuing the [com.spartanlabs.gaming.gameobjects.Intent] the
 * command names:
 *
 * | Command | Intent issued |
 * |---|---|
 * | [MoveTo] | [Move] with [Movement.Targeting] and the given destination |
 * | [MoveDir] | sets [com.spartanlabs.gaming.gameobjects.VisibleObject.angle], then [Move] with [Movement.Directional] |
 * | [Follow] | [Move] with [Movement.Homing] on the resolved target |
 * | [Stop] | [com.spartanlabs.gaming.gameobjects.Actor.clearIntent] |
 * | [Attack] | [AttackIntent] naming the resolved target |
 * | [StopAttack] | [com.spartanlabs.gaming.gameobjects.Actor.clearIntent] |
 *
 * ### Issuing a new intent clears the previous one
 *
 * [com.spartanlabs.gaming.gameobjects.Actor.issue] always tears down whatever intent was
 * previously active before installing the next one (see [com.spartanlabs.gaming.gameobjects.Intent.clear]).
 * So a [MoveTo], [MoveDir] or [Follow] issued on an [Alive] that was attacking calls off that
 * attack as a consequence of [AttackIntent.clear] - not a special case here - and a [Stop]
 * issued on an [Alive] that was only attacking (no active [Move]) cancels the attack the same
 * way, without additionally pinning a fresh destination. This runs only on the success path
 * (a [Follow] whose target does not resolve leaves the previous intent untouched).
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
    is MoveTo -> onActor(world, actor) { it.issue(Move(Movement.Targeting, destination = Point(x, y))) }

    is MoveDir -> onActor(world, actor) { mover ->
        mover.angle = angleDegrees
        mover.issue(Move(Movement.Directional))
    }

    is Follow -> onActor(world, actor) { mover ->
        val chased = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        mover.issue(Move(Movement.Homing(chased)))
    }

    is Stop -> onActor(world, actor) { it.clearIntent() }

    is Attack -> onAlive(world, attacker) { aggressor ->
        val victim = world.byId(target) ?: return ApplyResult.TargetMissing(target)
        val victimAlive = victim as? Alive ?: return ApplyResult.WrongType(target, Alive::class)
        aggressor.issue(AttackIntent(victimAlive))
    }

    is StopAttack -> onAlive(world, alive) { it.clearIntent() }

    else -> ApplyResult.Unhandled
}

/**
 * Resolves [id] to an [Actor] and runs [action] on it - or returns the failure that stopped
 * that. Inline so [action] can `return` an [ApplyResult] straight out of [applyTo] when a
 * secondary operand (a [Follow] target) cannot be resolved.
 */
private inline fun onActor(world: World, id: EntityId, action: (Actor) -> Unit): ApplyResult {
    val resolved = world.byId(id) ?: return ApplyResult.TargetMissing(id)
    val actor = resolved as? Actor ?: return ApplyResult.WrongType(id, Actor::class)
    action(actor)
    return ApplyResult.Applied
}

/** [onActor]'s counterpart for the [Alive]-only commands. */
private inline fun onAlive(world: World, id: EntityId, action: (Alive) -> Unit): ApplyResult {
    val resolved = world.byId(id) ?: return ApplyResult.TargetMissing(id)
    val alive = resolved as? Alive ?: return ApplyResult.WrongType(id, Alive::class)
    action(alive)
    return ApplyResult.Applied
}
