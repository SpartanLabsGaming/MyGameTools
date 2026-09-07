package com.spartanlabs.gaming.testing.deterministic.networking.command

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Actor
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.EntityId
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
 * Covers [applyTo]: each standard command drives the mechanism it names, and an operand that
 * is missing, the wrong kind, or a consumer command is reported rather than thrown.
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
    fun `Stop halts movement without touching the attack cycle`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issueAttack(victim)
        aggressor.movement = Movement.Directional
        events.clear()

        assertEquals(ApplyResult.Applied, Stop(aggressor.entityId).applyTo(world))

        assertEquals(Movement.Targeting, aggressor.movement)
        assertTrue(aggressor.isAtDestination, "the actor's destination should be pinned to where it is")
        assertFalse(events.any { it is GameEvent.AttackCancelled }, "Stop is a movement order, not an attack order")
    }

    @Test
    fun `Attack issues an attack on the resolved target`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)

        assertEquals(ApplyResult.Applied, Attack(aggressor.entityId, target = victim.entityId).applyTo(world))

        val issued = assertIs<GameEvent.AttackIssued>(events.single { it is GameEvent.AttackIssued })
        assertSame(aggressor, issued.attacker)
        assertSame(victim, issued.target)
    }

    @Test
    fun `StopAttack cancels a pending attack`() {
        val aggressor = alive()
        val victim = alive(x = 10.0)
        aggressor.issueAttack(victim)
        events.clear()

        assertEquals(ApplyResult.Applied, StopAttack(aggressor.entityId).applyTo(world))

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
