package com.spartanlabs.gaming.testing.component.gameobjects

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.geometry.Dimensions
import com.spartanlabs.geometry.Point
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.event.GameEvent
import com.spartanlabs.gaming.gameobjects.Alive
import com.spartanlabs.gaming.gameobjects.ModularStat
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 4. Programming Infrastructure and Support
// 4.3 Testing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/**
 * Covers [Alive]'s exits from the attack loop: [Alive.cancelAttack] from outside (issue #1) and
 * the automatic stop when a target dies or leaves the world (issue #2).
 */
class AliveAttackLifecycleTest {

    /** An [Alive] that records which lifecycle hooks fired. */
    private class TrackingAlive(location: Point) : Alive(location, Dimensions(10.0, 10.0), maxHealth = 100.0) {
        var cancelledHookCalls = 0
            private set
        val endReasons = mutableListOf<AttackEndReason>()

        public override fun onAttackCancelled() { cancelledHookCalls++ }
        public override fun onAttackEnded(reason: AttackEndReason) { endReasons += reason }
    }

    private val world = World(seed = 1L)
    private val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }

    private fun trackingAlive(x: Double) = TrackingAlive(Point(x, 0.0)).also(world::add)
    private fun alive(x: Double, maxHealth: Double = 100.0) =
        Alive(Point(x, 0.0), Dimensions(10.0, 10.0), maxHealth).also(world::add)

    private inline fun <reified T : GameEvent> events(): List<T> = events.filterIsInstance<T>()

    @Test
    fun `cancelAttack on an idle actor is a no-op`() {
        val idle = trackingAlive(0.0)

        idle.cancelAttack()

        assertEquals(0, idle.cancelledHookCalls)
        assertTrue(events<GameEvent.AttackCancelled>().isEmpty())
    }

    @Test
    fun `cancelAttack stops the actor swinging and fires the hook and event`() {
        val attacker = trackingAlive(0.0).apply { attackSpeed = ModularStat(100_000.0) }
        val target = alive(20.0) // within attackRange

        attacker.issueAttack(target)
        repeat(6) { world.tick() }
        val healthWhenCancelled = target.health.current
        assertTrue(healthWhenCancelled < 100.0, "the attacker should have landed some hits first")

        attacker.cancelAttack()
        repeat(20) { world.tick() }

        assertEquals(healthWhenCancelled, target.health.current, "no further damage after cancel")
        assertEquals(1, attacker.cancelledHookCalls)
        val cancelled = events<GameEvent.AttackCancelled>().single()
        assertSame(attacker, cancelled.attacker)
        assertSame(target, cancelled.formerTarget)
    }

    @Test
    fun `after cancelAttack a fresh move order sticks`() {
        val attacker = trackingAlive(0.0)
        val target = alive(5_000.0)
        attacker.issueAttack(target)
        world.tick()

        attacker.cancelAttack()
        attacker.destination = Point(30.0, 0.0)
        repeat(5) { world.tick() }

        assertEquals(30.0, attacker.location.x, absoluteTolerance = 1e-9)
    }

    @Test
    fun `an attacker stops within a tick of its target dying`() {
        val attacker = trackingAlive(0.0).apply { attackSpeed = ModularStat(100_000.0) }
        val target = alive(20.0, maxHealth = 25.0)

        attacker.issueAttack(target)
        repeat(20) { world.tick() }

        assertFalse(target.isAlive)
        assertContentEquals(listOf(Alive.AttackEndReason.TARGET_DIED), attacker.endReasons)
        val ended = events<GameEvent.AttackEnded>().single()
        assertSame(attacker, ended.attacker)
        assertSame(target, ended.formerTarget)
        assertEquals(Alive.AttackEndReason.TARGET_DIED, ended.reason)
    }

    @Test
    fun `an attacker stops when its target leaves the world alive`() {
        val attacker = trackingAlive(0.0)
        val target = alive(5_000.0)
        attacker.issueAttack(target)
        world.tick()

        world.removeList += target
        world.tick() // target drops out of the world, still alive
        world.tick() // attacker notices

        assertTrue(target.isAlive)
        assertContentEquals(listOf(Alive.AttackEndReason.TARGET_REMOVED), attacker.endReasons)
        assertEquals(1, events<GameEvent.AttackEnded>().size)
    }

    @Test
    fun `cancelAttack works on an actor that is not in a world`() {
        val loner = Alive(Point(0.0, 0.0), Dimensions(10.0, 10.0), maxHealth = 10.0)
        val target = Alive(Point(1.0, 0.0), Dimensions(10.0, 10.0), maxHealth = 10.0)
        loner.issueAttack(target)

        loner.cancelAttack() // must not throw

        assertNull((loner.world))
    }
}
