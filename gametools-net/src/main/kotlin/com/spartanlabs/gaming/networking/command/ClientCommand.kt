package com.spartanlabs.gaming.networking.command

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.Movement
//endregion

//region 2. Intended Function
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
//endregion

/**
 * One decoded client&rarr;server order.
 *
 * GameTools owns the STATE (server&rarr;client) wire as a closed set of
 * [com.spartanlabs.gaming.gameobjects.DrawableSnapshot] types; `ClientCommand` gives the
 * command direction the same treatment. The library ships six standard commands - [MoveTo],
 * [MoveDir], [Follow], [Stop], [Attack] and [StopAttack] - each naming a mechanism that
 * already exists on [Actor] or [Alive], with [EntityId] operands so "which object" can never
 * be an ambiguous bare integer.
 *
 * ### Adding your own commands
 *
 * `ClientCommand` is deliberately **not** `sealed`: a game declares its own commands by
 * implementing this interface with an `@Serializable` type and registering it in a
 * [kotlinx.serialization.modules.SerializersModule] handed to [ClientCommandCodec]:
 *
 * ```
 * @Serializable @SerialName("mygame.buildStructure")
 * data class BuildStructure(val builder: EntityId, val kind: String, val x: Double, val y: Double) : ClientCommand
 *
 * val codec = ClientCommandCodec(SerializersModule {
 *     polymorphic(ClientCommand::class) { subclass(BuildStructure::class) }
 * })
 * ```
 *
 * The **client and its server must build their codec with the same module**, or a command
 * one end sends will not decode on the other. Give every custom command a `@SerialName`
 * prefixed with your own namespace so it can never collide with a `gametools.*` one.
 *
 * @see ClientCommandCodec for the wire envelope and the encode/decode entry points
 * @see applyTo for turning a standard command into the [com.spartanlabs.gaming.gameobjects.World] mutation it names
 */
interface ClientCommand

// --- Actor-capability commands: the operand must resolve to an Actor ---------------------

/**
 * Send [actor] to the point `(`[x]`, `[y]`)` and stop there: assigns [Actor.destination],
 * which the default [Movement.Targeting] strategy walks the actor to and settles on.
 *
 * @property actor the actor to move
 * @property x the destination's world x coordinate
 * @property y the destination's world y coordinate
 */
@Serializable
@SerialName("gametools.moveTo")
data class MoveTo(val actor: EntityId, val x: Double, val y: Double) : ClientCommand

/**
 * Send [actor] travelling in a straight line along [angleDegrees] forever: sets the actor's
 * facing and switches it to [Movement.Directional]. [Actor.destination] is ignored while this
 * strategy is active.
 *
 * @property actor the actor to move
 * @property angleDegrees the heading in whole degrees counter-clockwise from the positive
 *   x-axis, matching [com.spartanlabs.gaming.gameobjects.VisibleObject.angle]
 */
@Serializable
@SerialName("gametools.moveDir")
data class MoveDir(val actor: EntityId, val angleDegrees: Int) : ClientCommand

/**
 * Have [actor] chase [target]: switches the actor to [Movement.Homing] on the object [target]
 * resolves to, so it re-points at the target's current position every tick.
 *
 * @property actor the actor that gives chase
 * @property target the object to home in on
 */
@Serializable
@SerialName("gametools.follow")
data class Follow(val actor: EntityId, val target: EntityId) : ClientCommand

/**
 * Halt [actor]'s movement: clears any [Movement.Directional] / [Movement.Homing] strategy
 * back to [Movement.Targeting] and pins [Actor.destination] to the actor's current location.
 *
 * This is a movement order only - it does not touch an [Alive]'s attack cycle. To call off an
 * attack, send [StopAttack].
 *
 * @property actor the actor to halt
 */
@Serializable
@SerialName("gametools.stop")
data class Stop(val actor: EntityId) : ClientCommand

// --- Alive-capability commands: the operand must resolve to an Alive ---------------------

/**
 * Order [attacker] to attack [target]: calls [Alive.issueAttack], which closes to attack
 * range and then swings on a loop until told otherwise.
 *
 * @property attacker the alive that attacks
 * @property target the alive to attack
 */
@Serializable
@SerialName("gametools.attack")
data class Attack(val attacker: EntityId, val target: EntityId) : ClientCommand

/**
 * Call off [alive]'s pending or in-progress attack: calls [Alive.cancelAttack]. The actor
 * keeps its current [Actor.destination] - send [Stop] as well to also halt it.
 *
 * @property alive the alive whose attack to cancel
 */
@Serializable
@SerialName("gametools.stopAttack")
data class StopAttack(val alive: EntityId) : ClientCommand
