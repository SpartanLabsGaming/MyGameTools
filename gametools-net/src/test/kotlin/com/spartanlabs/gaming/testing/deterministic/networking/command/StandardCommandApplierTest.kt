package com.spartanlabs.gaming.testing.deterministic.networking.command

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.AttackIntent
import com.spartanlabs.gaming.gameobjects.EntityId
import com.spartanlabs.gaming.gameobjects.Idle
import com.spartanlabs.gaming.gameobjects.Move
import com.spartanlabs.gaming.gameobjects.Movement
import com.spartanlabs.gaming.gameobjects.VisibleObject
import com.spartanlabs.gaming.gameobjects.World
import com.spartanlabs.gaming.networking.command.ApplyResult
import com.spartanlabs.gaming.networking.command.Attack
import com.spartanlabs.gaming.networking.command.ClientCommand
import com.spartanlabs.gaming.networking.command.Follow
import com.spartanlabs.gaming.networking.command.MoveDir
import com.spartanlabs.gaming.networking.command.MoveTo
import com.spartanlabs.gaming.networking.command.Stop
import com.spartanlabs.gaming.networking.command.StopAttack
import com.spartanlabs.gaming.networking.command.applyTo
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [applyTo]: each standard command issues the [com.spartanlabs.gaming.gameobjects.Intent]
 * it names, issuing a movement intent on an [Alive] that was attacking calls off that attack as
 * a consequence (not a special case), and an operand that is missing, the wrong kind, or a
 * consumer command is reported rather than thrown.
 */
class StandardCommandApplierTest {

    private val world = World(seed = 1L)
    private val events = mutableListOf<GameEvent>()

    init {
        world.events.subscribe { event -> events.add(event) }
    }

    private fun actor(x: Double = 0.0, y: Double = 0.0) =
        Actor(location = Point(x, y), dimensions = Dimensions(2.0, 2.0)).also(world::add)

    private fun alive(x: Double = 0.0, y: Double = 0.0) =
        Alive(location = Point(x, y), dimensions = Dimensions(2.0, 2.0), maxHealth = 100.0).also(world::add)

    @Test
    fun `MoveTo sets the actor's destination`() {
        val mover = actor()

        assertEquals(ApplyResult.Applied, MoveTo(mover.entityId, x = 40.0, y = 50.0).applyTo(world))

        assertEquals(40.0, mover.destination.x)
        assertEquals(50.0, mover.destination.y)
    }

    @Test
    fun `MoveDir sets the heading and switches to Directional movement`() {
        val mover = actor()

        assertEquals(ApplyResult.Applied, MoveDir(mover.entityId, angleDegrees = 90).applyTo(world))

        assertEquals(90, mover.angle)
        assertEquals(Movement.Directional, mover.movement)
    }

    @Test
    fun `Follow switches the actor to Homing on the resolved target`() {
        val mover = actor()
        val quarry = actor(x = 100.0)

        assertEquals(ApplyResult.Applied, Follow(mover.entityId, target = quarry.entityId).applyTo(world))

        val homing = assertIs<Movement.Homing>(mover.movement)
        assertSame(quarry, homing.target)
    }

    @Test
    fun `Stop clears intent to Idle and calls off a pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, Stop(aggressor.entityId).applyTo(world))

        assertSame(Idle, aggressor.intent)
        assertTrue(events.any { it is GameEvent.AttackCancelled }, "a standing order clears the previous one")
    }

    @Test
    fun `Stop on an actor whose intent is Move halts it in place`() {
        val mover = actor()
        mover.issue(Move(Movement.Directional))

        assertEquals(ApplyResult.Applied, Stop(mover.entityId).applyTo(world))

        assertSame(Idle, mover.intent)
        assertEquals(Movement.Targeting, mover.movement)
        assertTrue(mover.isAtDestination, "the actor's destination should be pinned to where it is")
    }

    @Test
    fun `Stop on an Alive whose intent is AttackIntent only cancels the attack, leaving movement and destination alone`() {
        val aggressor = alive().apply {
            movement = Movement.Directional
            destination = Point(999.0, 999.0)
        }
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, Stop(aggressor.entityId).applyTo(world))

        assertSame(Idle, aggressor.intent)
        assertEquals(Movement.Directional, aggressor.movement, "Stop while only attacking must not touch movement")
        assertEquals(999.0, aggressor.destination.x, "Stop while only attacking must not pin a fresh destination")
        assertTrue(events.any { it is GameEvent.AttackCancelled })
    }

    @Test
    fun `MoveTo issues a Move intent and calls off the actor's pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, MoveTo(aggressor.entityId, x = 40.0, y = 50.0).applyTo(world))

        assertIs<Move>(aggressor.intent)
        assertEquals(40.0, aggressor.destination.x, "the move still applies")
        assertTrue(events.any { it is GameEvent.AttackCancelled }, "a manual move order overrides an auto-attack")
    }

    @Test
    fun `MoveTo forces Movement Targeting even when the actor was previously Directional`() {
        val mover = actor()
        mover.issue(Move(Movement.Directional))

        assertEquals(ApplyResult.Applied, MoveTo(mover.entityId, x = 40.0, y = 0.0).applyTo(world))

        assertEquals(Movement.Targeting, mover.movement)
        world.tick()
        assertEquals(10.0, mover.location.x, absoluteTolerance = 1e-9, message = "MoveTo should now actually move it")
    }

    @Test
    fun `MoveDir issues a Move intent and calls off the actor's pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, MoveDir(aggressor.entityId, angleDegrees = 90).applyTo(world))

        assertIs<Move>(aggressor.intent)
        assertEquals(Movement.Directional, aggressor.movement, "the move still applies")
        assertTrue(events.any { it is GameEvent.AttackCancelled }, "a manual move order overrides an auto-attack")
    }

    @Test
    fun `Follow issues a Move intent and calls off the actor's pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, Follow(aggressor.entityId, target = victim.entityId).applyTo(world))

        assertIs<Move>(aggressor.intent)
        assertIs<Movement.Homing>(aggressor.movement)
        assertTrue(events.any { it is GameEvent.AttackCancelled }, "a manual move order overrides an auto-attack")
    }

    @Test
    fun `a movement command on a plain Actor applies without incident`() {
        val mover = actor()

        assertEquals(ApplyResult.Applied, MoveTo(mover.entityId, x = 5.0, y = 6.0).applyTo(world))

        assertEquals(5.0, mover.destination.x)
    }

    @Test
    fun `a Follow whose target is gone leaves a pending attack running`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(
            ApplyResult.TargetMissing(EntityId(999)),
            Follow(aggressor.entityId, target = EntityId(999)).applyTo(world)
        )

        assertFalse(
            events.any { it is GameEvent.AttackCancelled },
            "a command that could not be carried out has no side effect"
        )
        assertIs<AttackIntent>(aggressor.intent, "the pending attack intent should be untouched")
    }

    @Test
    fun `Attack issues an AttackIntent naming the resolved target`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)

        assertEquals(ApplyResult.Applied, Attack(aggressor.entityId, target = victim.entityId).applyTo(world))

        val intent = assertIs<AttackIntent>(aggressor.intent)
        assertSame(victim, intent.target)
        val issued = assertIs<GameEvent.AttackIssued>(events.single { it is GameEvent.AttackIssued })
        assertSame(aggressor, issued.attacker)
        assertSame(victim, issued.target)
    }

    @Test
    fun `StopAttack clears intent to Idle and cancels a pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issue(AttackIntent(victim))
        events.clear()

        assertEquals(ApplyResult.Applied, StopAttack(aggressor.entityId).applyTo(world))

        assertSame(Idle, aggressor.intent)
        assertTrue(events.any { it is GameEvent.AttackCancelled })
    }

    @Test
    fun `an operand no live object resolves to is reported as TargetMissing`() {
        actor()

        assertEquals(ApplyResult.TargetMissing(EntityId(999)), MoveTo(EntityId(999), 1.0, 2.0).applyTo(world))
    }

    @Test
    fun `a Follow whose target is gone is reported as TargetMissing`() {
        val mover = actor()

        assertEquals(
            ApplyResult.TargetMissing(EntityId(999)),
            Follow(mover.entityId, target = EntityId(999)).applyTo(world)
        )
    }

    @Test
    fun `an Actor-only command naming a plain VisibleObject is WrongType`() {
        val plain = VisibleObject(width = 3.0, height = 3.0).also(world::add)

        assertEquals(ApplyResult.WrongType(plain.entityId, Actor::class), Stop(plain.entityId).applyTo(world))
    }

    @Test
    fun `an Alive-only command naming a plain Actor is WrongType`() {
        val plainActor = actor()
        val victim = alive(x = 10.0)

        assertEquals(
            ApplyResult.WrongType(plainActor.entityId, Alive::class),
            Attack(plainActor.entityId, target = victim.entityId).applyTo(world)
        )
    }

    @Test
    fun `a consumer command this applier does not know is Unhandled`() {
        val consumerCommand = object : ClientCommand {}

        assertEquals(ApplyResult.Unhandled, consumerCommand.applyTo(world))
    }
}
