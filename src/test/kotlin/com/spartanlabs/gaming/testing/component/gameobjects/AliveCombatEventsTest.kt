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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
//endregion

/** Covers the combat and death [GameEvent]s an [Alive] publishes on its [World]'s bus. */
class AliveCombatEventsTest {

    private val world = World()
    private val events = mutableListOf<GameEvent>().also { log -> world.events.subscribe { log += it } }

    private fun alive(x: Double, maxHealth: Double = 100.0) = Alive(
        location = Point(x, 0.0),
        dimensions = Dimensions(10.0, 10.0),
        maxHealth = maxHealth
    ).also(world::add)

    private inline fun <reified T : GameEvent> events(): List<T> = events.filterIsInstance<T>()

    @Test
    fun `issueAttack publishes AttackIssued`() {
        val attacker = alive(0.0)
        val target = alive(50.0)

        attacker.issueAttack(target)

        val issued = events<GameEvent.AttackIssued>().single()
        assertSame(attacker, issued.attacker)
        assertSame(target, issued.target)
    }

    @Test
    fun `a landed swing publishes AttackLanded and DamageDealt`() {
        val attacker = alive(0.0).apply { attackSpeed = ModularStat(100_000.0) }
        val target = alive(100.0)

        attacker.issueAttack(target)
        repeat(6) { world.tick() }

        assertTrue(events<GameEvent.AttackLanded>().isNotEmpty(), "a swing should have landed")
        val damage = events<GameEvent.DamageDealt>().first()
        assertSame(attacker, damage.source)
        assertSame(target, damage.target)
        assertEquals(10.0, damage.amount, absoluteTolerance = 1e-9)
    }

    @Test
    fun `a killing blow publishes EntityDied crediting the attacker`() {
        val attacker = alive(0.0).apply { attackSpeed = ModularStat(100_000.0) }
        val target = alive(20.0, maxHealth = 25.0)

        attacker.issueAttack(target)
        repeat(30) { world.tick() }

        val died = events<GameEvent.EntityDied>().single()
        assertSame(target, died.entity)
        assertSame(attacker, died.killer)
    }

    @Test
    fun `a death with no tracked attacker credits no killer`() {
        val victim = alive(0.0, maxHealth = 30.0)

        victim.health.current = 0.0
        world.tick()

        val died = events<GameEvent.EntityDied>().single()
        assertSame(victim, died.entity)
        assertNull(died.killer)
    }

    @Test
    fun `an Alive not in a world publishes nothing`() {
        val loner = Alive(location = Point(0.0, 0.0), dimensions = Dimensions(10.0, 10.0), maxHealth = 10.0)
        val target = Alive(location = Point(1.0, 0.0), dimensions = Dimensions(10.0, 10.0), maxHealth = 10.0)

        loner.issueAttack(target) // world is null - must be a no-op, not a crash

        assertTrue(events.isEmpty())
    }
}
